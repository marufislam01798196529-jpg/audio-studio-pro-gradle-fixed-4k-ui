package com.example.network

import com.example.data.local.ApiProfileEntity
import com.example.data.local.DiscoveredModelEntity
import com.example.data.model.ModelCategory
import com.example.data.model.ProviderFormat
import com.example.data.model.ReasoningLevel
import com.example.security.SecretRedactor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okio.BufferedSource
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

data class ConnectionTestResult(
    val success: Boolean,
    val latencyMs: Long,
    val message: String,
    val discoveredModels: List<DiscoveredModelEntity> = emptyList(),
    val supportsStreaming: Boolean = true,
    val supportsReasoningParam: Boolean = false,
)

data class ChatTurnMessage(
    val role: String, // system, user, assistant
    val content: String,
    val imageBase64DataUrl: String? = null,
)

sealed class StreamChunk {
    data class Delta(val text: String, val reasoningText: String? = null) : StreamChunk()
    data class Completed(
        val totalTokens: Int,
        val promptTokens: Int,
        val completionTokens: Int,
        val estimatedCostUsd: Double,
    ) : StreamChunk()
    data class Error(val message: String) : StreamChunk()
}

class AiProviderClient {

    private val client = OkHttpClient.Builder()
        .connectTimeout(25, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val activeCallRef = AtomicReference<Call?>(null)

    fun cancelActiveStream() {
        activeCallRef.getAndSet(null)?.cancel()
    }

    suspend fun testConnectionAndDiscoverModels(
        profile: ApiProfileEntity,
        apiKey: String,
    ): ConnectionTestResult = withContext(Dispatchers.IO) {
        val cleanBase = profile.baseUrl.trim().trimEnd('/')
        if (cleanBase.isBlank()) {
            return@withContext ConnectionTestResult(false, 0L, "Base URL is required.")
        }
        val format = runCatching { ProviderFormat.valueOf(profile.providerFormat) }
            .getOrDefault(ProviderFormat.OPENAI_COMPATIBLE)

        val start = System.currentTimeMillis()
        try {
            val requestBuilder = when (format) {
                ProviderFormat.GEMINI -> {
                    val url = if (cleanBase.endsWith("/models")) cleanBase else "$cleanBase/models"
                    Request.Builder()
                        .url(url)
                        .get()
                        .addHeader("x-goog-api-key", apiKey)
                }
                ProviderFormat.ANTHROPIC -> {
                    val url = if (cleanBase.endsWith("/models")) cleanBase else "$cleanBase/models"
                    Request.Builder()
                        .url(url)
                        .get()
                        .addHeader("x-api-key", apiKey)
                        .addHeader("anthropic-version", "2023-06-01")
                }
                ProviderFormat.OPENAI_COMPATIBLE, ProviderFormat.CUSTOM -> {
                    val url = if (cleanBase.endsWith("/models")) cleanBase else "$cleanBase/models"
                    Request.Builder()
                        .url(url)
                        .get()
                        .apply {
                            if (apiKey.isNotBlank()) addHeader("Authorization", "Bearer $apiKey")
                        }
                }
            }
            applyCustomHeaders(requestBuilder, profile.customHeadersJson)

            val response = client.newCall(requestBuilder.build()).execute()
            val latency = System.currentTimeMillis() - start
            val bodyStr = response.body?.string().orEmpty()

            if (!response.isSuccessful) {
                val parsedErr = extractApiError(bodyStr, response.code)
                return@withContext ConnectionTestResult(
                    success = false,
                    latencyMs = latency,
                    message = SecretRedactor.redact(parsedErr, listOf(apiKey)),
                )
            }

            val models = parseDiscoveredModels(profile.id, format, bodyStr)
            val anyReasoning = models.any { it.supportsReasoning }
            val msg = if (models.isNotEmpty()) {
                "Connected (${latency}ms) • ${models.size} models discovered"
            } else {
                "Connected (${latency}ms) • No model list returned; enter model ID manually"
            }
            ConnectionTestResult(
                success = true,
                latencyMs = latency,
                message = msg,
                discoveredModels = models,
                supportsStreaming = true,
                supportsReasoningParam = anyReasoning,
            )
        } catch (e: Exception) {
            val latency = System.currentTimeMillis() - start
            ConnectionTestResult(
                success = false,
                latencyMs = latency,
                message = SecretRedactor.redact(
                    e.message ?: "Network error connecting to provider",
                    listOf(apiKey),
                ),
            )
        }
    }

    fun streamChatCompletion(
        profile: ApiProfileEntity,
        apiKey: String,
        modelId: String,
        messages: List<ChatTurnMessage>,
        reasoningLevel: ReasoningLevel,
        supportsReasoningParam: Boolean,
    ): Flow<StreamChunk> = flow {
        val cleanBase = profile.baseUrl.trim().trimEnd('/')
        val format = runCatching { ProviderFormat.valueOf(profile.providerFormat) }
            .getOrDefault(ProviderFormat.OPENAI_COMPATIBLE)

        val effectiveMessages = if (!supportsReasoningParam && reasoningLevel != ReasoningLevel.MEDIUM) {
            val depthNote = when (reasoningLevel) {
                ReasoningLevel.LOW -> "Task depth: LOW. Be concise, direct, and fast."
                ReasoningLevel.HIGH -> "Task depth: HIGH. Provide thorough architecture analysis, edge-case checks, and explicit verification steps."
                ReasoningLevel.MAX -> "Task depth: MAX. Perform exhaustive architectural decomposition, dependency verification, security audit, and step-by-step testing plans."
                ReasoningLevel.MEDIUM -> ""
            }
            listOf(ChatTurnMessage("system", depthNote)) + messages
        } else {
            messages
        }

        val request = buildChatRequest(
            cleanBase = cleanBase,
            format = format,
            apiKey = apiKey,
            modelId = modelId,
            messages = effectiveMessages,
            reasoningLevel = reasoningLevel,
            supportsReasoningParam = supportsReasoningParam,
            customHeadersJson = profile.customHeadersJson,
        )

        val call = client.newCall(request)
        activeCallRef.set(call)

        try {
            val response = call.execute()
            if (!response.isSuccessful) {
                val errBody = response.body?.string().orEmpty()
                emit(
                    StreamChunk.Error(
                        SecretRedactor.redact(extractApiError(errBody, response.code), listOf(apiKey))
                    )
                )
                return@flow
            }

            val source: BufferedSource = response.body?.source() ?: run {
                emit(StreamChunk.Error("Empty response body from AI provider"))
                return@flow
            }

            var promptTokens = 0
            var completionTokens = 0
            var totalTokens = 0

            while (!source.exhausted()) {
                val line = source.readUtf8Line() ?: break
                if (line.isBlank()) continue

                if (line.startsWith("data:")) {
                    val data = line.removePrefix("data:").trim()
                    if (data == "[DONE]") break
                    parseSsePayload(format, data)?.let { parsed ->
                        if (parsed.textDelta.isNotEmpty() || !parsed.reasoningDelta.isNullOrEmpty()) {
                            emit(StreamChunk.Delta(parsed.textDelta, parsed.reasoningDelta))
                        }
                        if (parsed.promptTokens > 0) promptTokens = parsed.promptTokens
                        if (parsed.completionTokens > 0) completionTokens = parsed.completionTokens
                        if (parsed.totalTokens > 0) totalTokens = parsed.totalTokens
                    }
                } else if (line.trimStart().startsWith("{")) {
                    // Handle non-SSE single JSON fallback response gracefully
                    val fallback = parseSingleJsonFallback(format, line)
                    if (fallback != null) {
                        emit(StreamChunk.Delta(fallback.textDelta, fallback.reasoningDelta))
                        promptTokens = fallback.promptTokens
                        completionTokens = fallback.completionTokens
                        totalTokens = fallback.totalTokens
                    }
                }
            }

            if (totalTokens == 0 && (promptTokens > 0 || completionTokens > 0)) {
                totalTokens = promptTokens + completionTokens
            }
            val cost = estimateCost(modelId, promptTokens, completionTokens)
            emit(StreamChunk.Completed(totalTokens, promptTokens, completionTokens, cost))
        } catch (e: IOException) {
            if (call.isCanceled()) {
                emit(StreamChunk.Error("Generation stopped by user."))
            } else {
                emit(
                    StreamChunk.Error(
                        SecretRedactor.redact(e.message ?: "Streaming connection failed", listOf(apiKey))
                    )
                )
            }
        } catch (e: Exception) {
            emit(
                StreamChunk.Error(
                    SecretRedactor.redact(e.message ?: "Unexpected AI error", listOf(apiKey))
                )
            )
        } finally {
            activeCallRef.compareAndSet(call, null)
        }
    }.flowOn(Dispatchers.IO)

    private fun buildChatRequest(
        cleanBase: String,
        format: ProviderFormat,
        apiKey: String,
        modelId: String,
        messages: List<ChatTurnMessage>,
        reasoningLevel: ReasoningLevel,
        supportsReasoningParam: Boolean,
        customHeadersJson: String,
    ): Request {
        val jsonMedia = "application/json; charset=utf-8".toMediaType()
        val builder = Request.Builder()

        when (format) {
            ProviderFormat.ANTHROPIC -> {
                val url = if (cleanBase.endsWith("/messages")) cleanBase else "$cleanBase/messages"
                val systemPrompt = messages.filter { it.role == "system" }.joinToString("\n\n") { it.content }
                val turns = JSONArray()
                messages.filter { it.role != "system" }.forEach { msg ->
                    val turn = JSONObject().put("role", if (msg.role == "assistant") "assistant" else "user")
                    turn.put("content", msg.content)
                    turns.put(turn)
                }
                val body = JSONObject()
                    .put("model", modelId)
                    .put("max_tokens", 4096)
                    .put("stream", true)
                    .put("messages", turns)
                if (systemPrompt.isNotBlank()) body.put("system", systemPrompt)
                if (supportsReasoningParam && reasoningLevel != ReasoningLevel.LOW) {
                    val budget = when (reasoningLevel) {
                        ReasoningLevel.MEDIUM -> 2048
                        ReasoningLevel.HIGH -> 4096
                        ReasoningLevel.MAX -> 8192
                        ReasoningLevel.LOW -> 1024
                    }
                    body.put("thinking", JSONObject().put("type", "enabled").put("budget_tokens", budget))
                }
                builder.url(url)
                    .post(body.toString().toRequestBody(jsonMedia))
                    .addHeader("x-api-key", apiKey)
                    .addHeader("anthropic-version", "2023-06-01")
            }

            ProviderFormat.GEMINI -> {
                val url = "$cleanBase/models/$modelId:streamGenerateContent?alt=sse"
                val sysText = messages.filter { it.role == "system" }.joinToString("\n\n") { it.content }
                val contents = JSONArray()
                messages.filter { it.role != "system" }.forEach { msg ->
                    val parts = JSONArray().put(JSONObject().put("text", msg.content))
                    contents.put(
                        JSONObject()
                            .put("role", if (msg.role == "assistant") "model" else "user")
                            .put("parts", parts)
                    )
                }
                val body = JSONObject().put("contents", contents)
                if (sysText.isNotBlank()) {
                    body.put(
                        "systemInstruction",
                        JSONObject().put("parts", JSONArray().put(JSONObject().put("text", sysText)))
                    )
                }
                if (supportsReasoningParam) {
                    val budget = when (reasoningLevel) {
                        ReasoningLevel.LOW -> 0
                        ReasoningLevel.MEDIUM -> 1024
                        ReasoningLevel.HIGH -> 4096
                        ReasoningLevel.MAX -> 8192
                    }
                    body.put(
                        "generationConfig",
                        JSONObject().put("thinkingConfig", JSONObject().put("thinkingBudget", budget))
                    )
                }
                builder.url(url)
                    .post(body.toString().toRequestBody(jsonMedia))
                    .addHeader("x-goog-api-key", apiKey)
            }

            ProviderFormat.OPENAI_COMPATIBLE, ProviderFormat.CUSTOM -> {
                val url = if (cleanBase.endsWith("/chat/completions")) cleanBase else "$cleanBase/chat/completions"
                val msgArr = JSONArray()
                messages.forEach { msg ->
                    val obj = JSONObject().put("role", msg.role)
                    if (msg.imageBase64DataUrl != null) {
                        val contentArr = JSONArray()
                            .put(JSONObject().put("type", "text").put("text", msg.content))
                            .put(
                                JSONObject().put("type", "image_url")
                                    .put("image_url", JSONObject().put("url", msg.imageBase64DataUrl))
                            )
                        obj.put("content", contentArr)
                    } else {
                        obj.put("content", msg.content)
                    }
                    msgArr.put(obj)
                }
                val body = JSONObject()
                    .put("model", modelId)
                    .put("stream", true)
                    .put("messages", msgArr)
                if (supportsReasoningParam) {
                    body.put("reasoning_effort", reasoningLevel.apiValue)
                }
                builder.url(url)
                    .post(body.toString().toRequestBody(jsonMedia))
                    .apply {
                        if (apiKey.isNotBlank()) addHeader("Authorization", "Bearer $apiKey")
                    }
            }
        }
        applyCustomHeaders(builder, customHeadersJson)
        return builder.build()
    }

    private fun applyCustomHeaders(builder: Request.Builder, customHeadersJson: String) {
        if (customHeadersJson.isBlank() || customHeadersJson == "{}") return
        runCatching {
            val obj = JSONObject(customHeadersJson)
            obj.keys().forEach { key ->
                val v = obj.optString(key, "")
                if (key.isNotBlank() && v.isNotBlank()) {
                    builder.addHeader(key, v)
                }
            }
        }
    }

    private fun parseDiscoveredModels(
        profileId: String,
        format: ProviderFormat,
        bodyStr: String,
    ): List<DiscoveredModelEntity> {
        val result = mutableListOf<DiscoveredModelEntity>()
        runCatching {
            val root = JSONObject(bodyStr)
            when (format) {
                ProviderFormat.GEMINI -> {
                    val arr = root.optJSONArray("models") ?: return result
                    for (i in 0 until arr.length()) {
                        val item = arr.optJSONObject(i) ?: continue
                        val rawName = item.optString("name", "")
                        val id = rawName.removePrefix("models/")
                        if (id.isBlank()) continue
                        val display = item.optString("displayName", id)
                        val methods = item.optJSONArray("supportedGenerationMethods")?.toString().orEmpty()
                        if (methods.isNotEmpty() && !methods.contains("generateContent")) continue
                        val supportsThinking = item.optBoolean("thinking", false)
                        val category = classifyFromMetadata(
                            id = id,
                            description = item.optString("description", ""),
                            supportsThinking = supportsThinking,
                            supportsVision = true,
                        )
                        result.add(
                            DiscoveredModelEntity(
                                compositeId = "$profileId::$id",
                                profileId = profileId,
                                modelId = id,
                                displayName = display,
                                category = category.name,
                                supportsReasoning = supportsThinking,
                                supportsVision = true,
                                supportsStreaming = true,
                                isManual = false,
                            )
                        )
                    }
                }
                ProviderFormat.OPENAI_COMPATIBLE, ProviderFormat.ANTHROPIC, ProviderFormat.CUSTOM -> {
                    val arr = root.optJSONArray("data") ?: root.optJSONArray("models") ?: return result
                    for (i in 0 until arr.length()) {
                        val item = arr.optJSONObject(i) ?: continue
                        val id = item.optString("id").ifBlank { item.optString("name", "") }
                        if (id.isBlank()) continue
                        val display = item.optString("display_name", item.optString("name", id))
                        val caps = item.optJSONObject("capabilities")
                        val supportedParams = item.optJSONArray("supported_parameters")?.toString().orEmpty()
                        val supportsReasoning = caps?.optBoolean("reasoning", false) == true ||
                            supportedParams.contains("reasoning")
                        val supportsVision = caps?.optBoolean("vision", false) == true ||
                            item.optJSONObject("architecture")?.optString("modality", "")?.contains("image") == true
                        val category = classifyFromMetadata(
                            id = id,
                            description = item.optString("description", ""),
                            supportsThinking = supportsReasoning,
                            supportsVision = supportsVision,
                        )
                        result.add(
                            DiscoveredModelEntity(
                                compositeId = "$profileId::$id",
                                profileId = profileId,
                                modelId = id,
                                displayName = display,
                                category = category.name,
                                supportsReasoning = supportsReasoning,
                                supportsVision = supportsVision,
                                supportsStreaming = true,
                                isManual = false,
                            )
                        )
                    }
                }
            }
        }
        return result
    }

    private fun classifyFromMetadata(
        id: String,
        description: String,
        supportsThinking: Boolean,
        supportsVision: Boolean,
    ): ModelCategory {
        if (supportsThinking) return ModelCategory.REASONING
        val descLower = description.lowercase()
        return when {
            descLower.contains("code") || descLower.contains("coding") || descLower.contains("software") ->
                ModelCategory.CODING
            descLower.contains("fast") || descLower.contains("low latency") ->
                ModelCategory.FAST
            descLower.contains("lightweight") || descLower.contains("compact") || descLower.contains("edge") ->
                ModelCategory.LIGHTWEIGHT
            supportsVision && descLower.contains("vision") ->
                ModelCategory.VISION
            else -> ModelCategory.GENERAL
        }
    }

    private data class ParsedSseDelta(
        val textDelta: String = "",
        val reasoningDelta: String? = null,
        val promptTokens: Int = 0,
        val completionTokens: Int = 0,
        val totalTokens: Int = 0,
    )

    private fun parseSsePayload(format: ProviderFormat, data: String): ParsedSseDelta? {
        return runCatching {
            val json = JSONObject(data)
            when (format) {
                ProviderFormat.ANTHROPIC -> {
                    val type = json.optString("type")
                    when (type) {
                        "content_block_delta" -> {
                            val delta = json.optJSONObject("delta") ?: return null
                            val text = delta.optString("text", "")
                            val thinking = delta.optString("thinking", "")
                            ParsedSseDelta(
                                textDelta = text,
                                reasoningDelta = thinking.ifEmpty { null },
                            )
                        }
                        "message_delta" -> {
                            val usage = json.optJSONObject("usage")
                            ParsedSseDelta(completionTokens = usage?.optInt("output_tokens", 0) ?: 0)
                        }
                        else -> null
                    }
                }
                ProviderFormat.GEMINI -> {
                    val candidates = json.optJSONArray("candidates")
                    val textBuilder = StringBuilder()
                    if (candidates != null && candidates.length() > 0) {
                        val parts = candidates.optJSONObject(0)
                            ?.optJSONObject("content")
                            ?.optJSONArray("parts")
                        if (parts != null) {
                            for (i in 0 until parts.length()) {
                                textBuilder.append(parts.optJSONObject(i)?.optString("text", "").orEmpty())
                            }
                        }
                    }
                    val usage = json.optJSONObject("usageMetadata")
                    ParsedSseDelta(
                        textDelta = textBuilder.toString(),
                        promptTokens = usage?.optInt("promptTokenCount", 0) ?: 0,
                        completionTokens = usage?.optInt("candidatesTokenCount", 0) ?: 0,
                        totalTokens = usage?.optInt("totalTokenCount", 0) ?: 0,
                    )
                }
                ProviderFormat.OPENAI_COMPATIBLE, ProviderFormat.CUSTOM -> {
                    val choices = json.optJSONArray("choices")
                    var content = ""
                    var reasoning: String? = null
                    if (choices != null && choices.length() > 0) {
                        val delta = choices.optJSONObject(0)?.optJSONObject("delta")
                        if (delta != null) {
                            content = delta.optString("content", "")
                            val r = delta.optString("reasoning_content", delta.optString("reasoning", ""))
                            if (r.isNotEmpty()) reasoning = r
                        }
                    }
                    val usage = json.optJSONObject("usage")
                    ParsedSseDelta(
                        textDelta = content,
                        reasoningDelta = reasoning,
                        promptTokens = usage?.optInt("prompt_tokens", 0) ?: 0,
                        completionTokens = usage?.optInt("completion_tokens", 0) ?: 0,
                        totalTokens = usage?.optInt("total_tokens", 0) ?: 0,
                    )
                }
            }
        }.getOrNull()
    }

    private fun parseSingleJsonFallback(format: ProviderFormat, body: String): ParsedSseDelta? {
        return runCatching {
            val json = JSONObject(body)
            when (format) {
                ProviderFormat.OPENAI_COMPATIBLE, ProviderFormat.CUSTOM -> {
                    val msg = json.optJSONArray("choices")?.optJSONObject(0)?.optJSONObject("message")
                    val text = msg?.optString("content", "").orEmpty()
                    val usage = json.optJSONObject("usage")
                    ParsedSseDelta(
                        textDelta = text,
                        promptTokens = usage?.optInt("prompt_tokens", 0) ?: 0,
                        completionTokens = usage?.optInt("completion_tokens", 0) ?: 0,
                        totalTokens = usage?.optInt("total_tokens", 0) ?: 0,
                    )
                }
                else -> null
            }
        }.getOrNull()
    }

    private fun extractApiError(body: String, code: Int): String {
        val parsed = runCatching {
            val obj = JSONObject(body)
            obj.optJSONObject("error")?.optString("message")
                ?: obj.optString("message")
                ?: obj.optString("detail")
        }.getOrNull()?.takeIf { it.isNotBlank() }
        return if (parsed != null) "HTTP $code: $parsed" else "HTTP $code from provider"
    }

    private fun estimateCost(modelId: String, promptTokens: Int, completionTokens: Int): Double {
        if (promptTokens == 0 && completionTokens == 0) return 0.0
        val lower = modelId.lowercase()
        val (inPerMillion, outPerMillion) = when {
            lower.contains("mini") || lower.contains("flash") || lower.contains("haiku") -> 0.15 to 0.60
            lower.contains("opus") || lower.contains("pro") -> 3.0 to 15.0
            else -> 2.0 to 8.0
        }
        return (promptTokens * inPerMillion + completionTokens * outPerMillion) / 1_000_000.0
    }
}

package com.example.engine

import com.example.data.model.FileChangeProposal
import com.example.data.model.StructuredPlan
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import java.util.UUID

object AiResponseParser {

    private val moshi: Moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private val mapType = Types.newParameterizedType(
        Map::class.java,
        String::class.java,
        Any::class.java,
    )
    private val mapAdapter = moshi.adapter<Map<String, Any?>>(mapType)

    fun parseStructuredPlan(rawText: String): StructuredPlan? {
        val jsonBlock = extractJsonBlock(rawText) ?: return null
        return runCatching {
            val map = mapAdapter.fromJson(jsonBlock) ?: return null
            StructuredPlan(
                userRequestAnalysis = map["user_request_analysis"]?.toString().orEmpty(),
                projectUnderstanding = map["project_understanding"]?.toString().orEmpty(),
                requirements = anyToStringList(map["requirements"]),
                proposedArchitecture = map["proposed_architecture"]?.toString().orEmpty(),
                filesToCreate = anyToStringList(map["files_to_create"]),
                filesToModify = anyToStringList(map["files_to_modify"]),
                dependencies = anyToStringList(map["dependencies"]),
                implementationTasks = anyToStringList(map["implementation_tasks"]),
                testingStrategy = map["testing_strategy"]?.toString().orEmpty(),
                expectedFinalOutput = map["expected_final_output"]?.toString().orEmpty(),
                conciseReasoningSummary = map["concise_reasoning_summary"]?.toString().orEmpty(),
            )
        }.getOrNull()?.takeIf {
            it.userRequestAnalysis.isNotBlank() || it.implementationTasks.isNotEmpty()
        }
    }

    /**
     * Parses AI file emission blocks formatted as:
     * <<<FILE: path/to/file.ext>>>
     * ...code...
     * <<<END_FILE>>>
     */
    fun extractFileProposals(
        rawText: String,
        existingContentsProvider: (String) -> String?,
    ): List<FileChangeProposal> {
        val pattern = Regex("""<<<FILE:\s*([^\n>]+)>>>\r?\n([\s\S]*?)<<<END_FILE>>>""")
        return pattern.findAll(rawText).map { match ->
            val path = match.groupValues[1].trim().trimStart('/')
            var content = match.groupValues[2]
            if (content.startsWith("```")) {
                content = content.substringAfter('\n')
            }
            if (content.trimEnd().endsWith("```")) {
                content = content.trimEnd().removeSuffix("```").trimEnd() + "\n"
            }
            val existing = existingContentsProvider(path)
            FileChangeProposal(
                id = UUID.randomUUID().toString(),
                filePath = path,
                originalContent = existing.orEmpty(),
                proposedContent = content,
                rationale = if (existing == null) "New file proposed by AI Agent" else "Modified by AI Agent",
                isNewFile = existing == null,
                accepted = null,
            )
        }.toList()
    }

    private fun extractJsonBlock(text: String): String? {
        val fenced = Regex("""```json\s*([\s\S]*?)```""").find(text)?.groupValues?.getOrNull(1)
        if (!fenced.isNullOrBlank()) return fenced.trim()
        val firstBrace = text.indexOf('{')
        val lastBrace = text.lastIndexOf('}')
        if (firstBrace in 0 until lastBrace) {
            return text.substring(firstBrace, lastBrace + 1)
        }
        return null
    }

    private fun anyToStringList(value: Any?): List<String> {
        val list = value as? List<*> ?: return emptyList()
        return list.mapNotNull { it?.toString()?.trim()?.takeIf { s -> s.isNotEmpty() } }
    }
}

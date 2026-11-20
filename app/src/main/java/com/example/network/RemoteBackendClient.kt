package com.example.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

data class RemoteTerminalResult(
    val success: Boolean,
    val exitCode: Int,
    val stdout: String,
    val stderr: String,
)

data class RemoteBuildStatus(
    val jobId: String,
    val status: String, // QUEUED, RUNNING, SUCCESS, FAILED
    val logs: String,
    val errorCount: Int,
    val artifactDownloadPath: String?,
)

class RemoteBackendClient {

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    suspend fun checkHealth(baseUrl: String, authToken: String?): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val clean = baseUrl.trim().trimEnd('/')
            if (clean.isBlank()) error("Remote Cloud Build Backend URL is not configured.")
            val builder = Request.Builder().url("$clean/api/health").get()
            if (!authToken.isNullOrBlank()) builder.addHeader("Authorization", "Bearer $authToken")
            val resp = client.newCall(builder.build()).execute()
            val body = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) error("Backend health HTTP ${resp.code}")
            val json = JSONObject(body)
            "Online • Gradle=${json.optBoolean("gradleAvailable", false)} • ContainerIsolation=${json.optBoolean("isolated", true)}"
        }
    }

    suspend fun executeRemoteCommand(
        baseUrl: String,
        authToken: String?,
        projectId: String,
        workingDir: String,
        command: String,
    ): RemoteTerminalResult = withContext(Dispatchers.IO) {
        val clean = baseUrl.trim().trimEnd('/')
        if (clean.isBlank()) {
            return@withContext RemoteTerminalResult(
                success = false,
                exitCode = 126,
                stdout = "",
                stderr = "Remote container backend URL not configured. Set it in Settings -> Cloud Build to execute remote Gradle, npm, git, or test commands.",
            )
        }
        try {
            val payload = JSONObject()
                .put("projectId", projectId)
                .put("workingDir", workingDir)
                .put("command", command)
            val builder = Request.Builder()
                .url("$clean/api/terminal/exec")
                .post(payload.toString().toRequestBody("application/json".toMediaType()))
            if (!authToken.isNullOrBlank()) builder.addHeader("Authorization", "Bearer $authToken")
            val resp = client.newCall(builder.build()).execute()
            val body = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) {
                return@withContext RemoteTerminalResult(
                    success = false,
                    exitCode = resp.code,
                    stdout = "",
                    stderr = "Remote execution rejected (HTTP ${resp.code}): $body",
                )
            }
            val json = JSONObject(body)
            RemoteTerminalResult(
                success = json.optInt("exitCode", 1) == 0,
                exitCode = json.optInt("exitCode", 1),
                stdout = json.optString("stdout", ""),
                stderr = json.optString("stderr", ""),
            )
        } catch (e: Exception) {
            RemoteTerminalResult(
                success = false,
                exitCode = 1,
                stdout = "",
                stderr = "Remote container unreachable: ${e.message}",
            )
        }
    }

    suspend fun submitCloudBuild(
        baseUrl: String,
        authToken: String?,
        projectId: String,
        buildType: String,
        sourceZipFile: File,
    ): Result<RemoteBuildStatus> = withContext(Dispatchers.IO) {
        runCatching {
            val clean = baseUrl.trim().trimEnd('/')
            if (clean.isBlank()) error("Remote Cloud Build URL not configured in Settings -> Cloud Build.")
            val multipart = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("projectId", projectId)
                .addFormDataPart("buildType", buildType)
                .addFormDataPart(
                    "projectZip",
                    sourceZipFile.name,
                    sourceZipFile.asRequestBody("application/zip".toMediaType()),
                )
                .build()
            val builder = Request.Builder()
                .url("$clean/api/builds")
                .post(multipart)
            if (!authToken.isNullOrBlank()) builder.addHeader("Authorization", "Bearer $authToken")
            val resp = client.newCall(builder.build()).execute()
            val body = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) error("Build submission HTTP ${resp.code}: $body")
            val json = JSONObject(body)
            RemoteBuildStatus(
                jobId = json.getString("jobId"),
                status = json.optString("status", "QUEUED"),
                logs = json.optString("logs", ""),
                errorCount = json.optInt("errorCount", 0),
                artifactDownloadPath = json.optString("artifactUrl", "").ifBlank { null },
            )
        }
    }

    suspend fun pollBuildStatus(
        baseUrl: String,
        authToken: String?,
        jobId: String,
    ): Result<RemoteBuildStatus> = withContext(Dispatchers.IO) {
        runCatching {
            val clean = baseUrl.trim().trimEnd('/')
            val builder = Request.Builder().url("$clean/api/builds/$jobId").get()
            if (!authToken.isNullOrBlank()) builder.addHeader("Authorization", "Bearer $authToken")
            val resp = client.newCall(builder.build()).execute()
            val body = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) error("Poll HTTP ${resp.code}")
            val json = JSONObject(body)
            RemoteBuildStatus(
                jobId = json.optString("jobId", jobId),
                status = json.optString("status", "RUNNING"),
                logs = json.optString("logs", ""),
                errorCount = json.optInt("errorCount", 0),
                artifactDownloadPath = json.optString("artifactUrl", "").ifBlank { null },
            )
        }
    }

    suspend fun downloadBuildArtifact(
        baseUrl: String,
        authToken: String?,
        artifactPathOrUrl: String,
        destFile: File,
    ): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            val clean = baseUrl.trim().trimEnd('/')
            val fullUrl = if (artifactPathOrUrl.startsWith("http")) {
                artifactPathOrUrl
            } else {
                "$clean/${artifactPathOrUrl.trimStart('/')}"
            }
            val builder = Request.Builder().url(fullUrl).get()
            if (!authToken.isNullOrBlank()) builder.addHeader("Authorization", "Bearer $authToken")
            val resp = client.newCall(builder.build()).execute()
            if (!resp.isSuccessful) error("Download APK failed (HTTP ${resp.code})")
            val input = resp.body?.byteStream() ?: error("Empty artifact stream")
            input.use { inp ->
                FileOutputStream(destFile).use { out ->
                    inp.copyTo(out, bufferSize = 16 * 1024)
                }
            }
            destFile
        }
    }
}

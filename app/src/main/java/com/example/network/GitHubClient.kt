package com.example.network

import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

data class GitHubRepoItem(
    val fullName: String,
    val name: String,
    val owner: String,
    val defaultBranch: String,
    val isPrivate: Boolean,
    val description: String,
    val language: String,
)

data class GitHubCommitItem(
    val sha: String,
    val message: String,
    val author: String,
    val date: String,
)

class GitHubClient {

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    suspend fun verifyUser(token: String): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val req = Request.Builder()
                .url("https://api.github.com/user")
                .addHeader("Authorization", "Bearer $token")
                .addHeader("Accept", "application/vnd.github+json")
                .build()
            val resp = client.newCall(req).execute()
            val body = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) error("GitHub auth failed (HTTP ${resp.code})")
            JSONObject(body).getString("login")
        }
    }

    suspend fun listRepositories(token: String): Result<List<GitHubRepoItem>> = withContext(Dispatchers.IO) {
        runCatching {
            val req = Request.Builder()
                .url("https://api.github.com/user/repos?sort=updated&per_page=30")
                .addHeader("Authorization", "Bearer $token")
                .addHeader("Accept", "application/vnd.github+json")
                .build()
            val resp = client.newCall(req).execute()
            val body = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) error("Failed to load repositories (HTTP ${resp.code})")
            val arr = JSONArray(body)
            buildList {
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    add(
                        GitHubRepoItem(
                            fullName = obj.getString("full_name"),
                            name = obj.getString("name"),
                            owner = obj.getJSONObject("owner").getString("login"),
                            defaultBranch = obj.optString("default_branch", "main"),
                            isPrivate = obj.optBoolean("private", false),
                            description = obj.optString("description", ""),
                            language = obj.optString("language", "Code"),
                        )
                    )
                }
            }
        }
    }

    suspend fun createRepository(
        token: String,
        name: String,
        description: String,
        isPrivate: Boolean,
    ): Result<GitHubRepoItem> = withContext(Dispatchers.IO) {
        runCatching {
            val payload = JSONObject()
                .put("name", name)
                .put("description", description)
                .put("private", isPrivate)
                .put("auto_init", true)
            val req = Request.Builder()
                .url("https://api.github.com/user/repos")
                .post(payload.toString().toRequestBody("application/json".toMediaType()))
                .addHeader("Authorization", "Bearer $token")
                .addHeader("Accept", "application/vnd.github+json")
                .build()
            val resp = client.newCall(req).execute()
            val body = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) error("Create repo failed (HTTP ${resp.code}): $body")
            val obj = JSONObject(body)
            GitHubRepoItem(
                fullName = obj.getString("full_name"),
                name = obj.getString("name"),
                owner = obj.getJSONObject("owner").getString("login"),
                defaultBranch = obj.optString("default_branch", "main"),
                isPrivate = obj.optBoolean("private", false),
                description = obj.optString("description", ""),
                language = "Project",
            )
        }
    }

    suspend fun downloadZipball(
        token: String?,
        ownerRepo: String,
        branch: String,
        destFile: File,
    ): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            val url = "https://api.github.com/repos/$ownerRepo/zipball/$branch"
            val builder = Request.Builder()
                .url(url)
                .addHeader("Accept", "application/vnd.github+json")
            if (!token.isNullOrBlank()) {
                builder.addHeader("Authorization", "Bearer $token")
            }
            val resp = client.newCall(builder.build()).execute()
            if (!resp.isSuccessful) error("Download repository failed (HTTP ${resp.code})")
            val input = resp.body?.byteStream() ?: error("Empty archive stream")
            input.use { inp ->
                FileOutputStream(destFile).use { out ->
                    inp.copyTo(out, bufferSize = 16 * 1024)
                }
            }
            destFile
        }
    }

    suspend fun listCommits(
        token: String,
        ownerRepo: String,
        branch: String,
    ): Result<List<GitHubCommitItem>> = withContext(Dispatchers.IO) {
        runCatching {
            val req = Request.Builder()
                .url("https://api.github.com/repos/$ownerRepo/commits?sha=$branch&per_page=15")
                .addHeader("Authorization", "Bearer $token")
                .addHeader("Accept", "application/vnd.github+json")
                .build()
            val resp = client.newCall(req).execute()
            val body = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) error("Fetch commits failed (HTTP ${resp.code})")
            val arr = JSONArray(body)
            buildList {
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    val commit = obj.getJSONObject("commit")
                    add(
                        GitHubCommitItem(
                            sha = obj.getString("sha").take(7),
                            message = commit.optString("message", "").lineSequence().firstOrNull().orEmpty(),
                            author = commit.optJSONObject("author")?.optString("name", "unknown") ?: "unknown",
                            date = commit.optJSONObject("author")?.optString("date", "") ?: "",
                        )
                    )
                }
            }
        }
    }

    suspend fun pushFileContent(
        token: String,
        ownerRepo: String,
        filePath: String,
        content: String,
        commitMessage: String,
        branch: String,
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            // Check existing SHA if updating file
            val getReq = Request.Builder()
                .url("https://api.github.com/repos/$ownerRepo/contents/$filePath?ref=$branch")
                .addHeader("Authorization", "Bearer $token")
                .addHeader("Accept", "application/vnd.github+json")
                .build()
            val getResp = client.newCall(getReq).execute()
            val existingSha = if (getResp.isSuccessful) {
                JSONObject(getResp.body?.string().orEmpty()).optString("sha", "")
            } else {
                ""
            }

            val encoded = Base64.encodeToString(content.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
            val payload = JSONObject()
                .put("message", commitMessage)
                .put("content", encoded)
                .put("branch", branch)
            if (existingSha.isNotBlank()) payload.put("sha", existingSha)

            val putReq = Request.Builder()
                .url("https://api.github.com/repos/$ownerRepo/contents/$filePath")
                .put(payload.toString().toRequestBody("application/json".toMediaType()))
                .addHeader("Authorization", "Bearer $token")
                .addHeader("Accept", "application/vnd.github+json")
                .build()
            val putResp = client.newCall(putReq).execute()
            if (!putResp.isSuccessful) {
                error("Push failed (HTTP ${putResp.code}): ${putResp.body?.string().orEmpty()}")
            }
            "Committed $filePath to $ownerRepo ($branch)"
        }
    }

    suspend fun createPullRequest(
        token: String,
        ownerRepo: String,
        title: String,
        headBranch: String,
        baseBranch: String,
        bodyText: String,
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val payload = JSONObject()
                .put("title", title)
                .put("head", headBranch)
                .put("base", baseBranch)
                .put("body", bodyText)
            val req = Request.Builder()
                .url("https://api.github.com/repos/$ownerRepo/pulls")
                .post(payload.toString().toRequestBody("application/json".toMediaType()))
                .addHeader("Authorization", "Bearer $token")
                .addHeader("Accept", "application/vnd.github+json")
                .build()
            val resp = client.newCall(req).execute()
            val body = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) error("PR creation failed (HTTP ${resp.code})")
            JSONObject(body).optString("html_url", "Pull request created")
        }
    }
}

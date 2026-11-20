package com.example.workers

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.data.local.XThrexDatabase
import com.example.data.model.ProjectType
import com.example.data.model.TaskStatus
import com.example.engine.ProjectDiagnostics
import com.example.engine.WorkspaceManager
import com.example.network.RemoteBackendClient
import com.example.security.KeystoreSecretVault
import kotlinx.coroutines.delay

class BackgroundTaskWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val jobKind = inputData.getString(KEY_JOB_KIND) ?: return Result.failure()
        val projectId = inputData.getString(KEY_PROJECT_ID).orEmpty()
        val taskId = inputData.getString(KEY_TASK_ID).orEmpty()
        val buildId = inputData.getString(KEY_BUILD_ID).orEmpty()
        val remoteUrl = inputData.getString(KEY_REMOTE_URL).orEmpty()

        val db = XThrexDatabase.getInstance(applicationContext)
        val vault = KeystoreSecretVault(applicationContext)
        val workspaceManager = WorkspaceManager(applicationContext)

        return when (jobKind) {
            KIND_POLL_CLOUD_BUILD -> {
                val remoteJobId = inputData.getString(KEY_REMOTE_JOB_ID) ?: return Result.failure()
                val backendToken = vault.getSecret(KeystoreSecretVault.REMOTE_BACKEND_TOKEN_SLOT)
                val remoteClient = RemoteBackendClient()

                repeat(20) { attempt ->
                    val poll = remoteClient.pollBuildStatus(remoteUrl, backendToken, remoteJobId)
                    val status = poll.getOrNull()
                    if (status != null) {
                        val existing = db.buildJobDao().let { dao ->
                            // Update build record
                            com.example.data.local.BuildJobEntity(
                                id = buildId,
                                projectId = projectId,
                                buildType = inputData.getString(KEY_BUILD_TYPE) ?: "DEBUG_APK",
                                status = status.status,
                                logs = status.logs,
                                errorCount = status.errorCount,
                                artifactPath = status.artifactDownloadPath,
                                remoteJobId = remoteJobId,
                                completedAt = if (status.status == "SUCCESS" || status.status == "FAILED") {
                                    System.currentTimeMillis()
                                } else null,
                            )
                        }
                        db.buildJobDao().upsertBuild(existing)
                        if (status.status == "SUCCESS") {
                            NotificationHelper.notifyTaskEvent(
                                applicationContext,
                                NotificationHelper.CHANNEL_BUILDS,
                                buildId.hashCode(),
                                "APK Build Completed",
                                "Cloud build $remoteJobId succeeded and is ready to download.",
                            )
                            return Result.success()
                        }
                        if (status.status == "FAILED") {
                            NotificationHelper.notifyTaskEvent(
                                applicationContext,
                                NotificationHelper.CHANNEL_BUILDS,
                                buildId.hashCode(),
                                "Cloud Build Failed",
                                "Detected ${status.errorCount} compilation error(s). Tap to inspect logs with AI.",
                            )
                            return Result.success()
                        }
                    } else if (attempt > 3 && runAttemptCount < 2) {
                        return Result.retry()
                    }
                    delay(3000)
                }
                Result.success()
            }

            KIND_DIAGNOSTIC_SCAN -> {
                val project = db.projectDao().getProjectById(projectId) ?: return Result.failure()
                val task = db.agentTaskDao().getTaskById(taskId)
                if (task != null) {
                    db.agentTaskDao().upsertTask(
                        task.copy(
                            status = TaskStatus.IN_PROGRESS.name,
                            progress = 0.4f,
                            checkpointNote = "Scanning workspace files...",
                            updatedAt = System.currentTimeMillis(),
                        )
                    )
                }
                val pType = runCatching { ProjectType.valueOf(project.projectType) }
                    .getOrDefault(ProjectType.HTML_CSS_JS)
                val issues = ProjectDiagnostics.scanWorkspace(
                    workspaceManager.getProjectDir(projectId),
                    pType,
                    vault.getAllRegisteredSecretsForRedaction(),
                )
                if (task != null) {
                    db.agentTaskDao().upsertTask(
                        task.copy(
                            status = TaskStatus.COMPLETED.name,
                            progress = 1.0f,
                            checkpointNote = "Completed checkpoint",
                            resultSummary = "Background diagnostic found ${issues.size} issue(s).",
                            updatedAt = System.currentTimeMillis(),
                        )
                    )
                }
                NotificationHelper.notifyTaskEvent(
                    applicationContext,
                    NotificationHelper.CHANNEL_TASKS,
                    taskId.hashCode(),
                    "Project Analysis Completed",
                    "${project.name}: ${issues.size} diagnostic item(s) detected.",
                )
                Result.success()
            }

            else -> Result.success()
        }
    }

    companion object {
        const val KEY_JOB_KIND = "job_kind"
        const val KEY_PROJECT_ID = "project_id"
        const val KEY_TASK_ID = "task_id"
        const val KEY_BUILD_ID = "build_id"
        const val KEY_BUILD_TYPE = "build_type"
        const val KEY_REMOTE_URL = "remote_url"
        const val KEY_REMOTE_JOB_ID = "remote_job_id"

        const val KIND_POLL_CLOUD_BUILD = "poll_cloud_build"
        const val KIND_DIAGNOSTIC_SCAN = "diagnostic_scan"

        fun enqueueDiagnosticTask(context: Context, projectId: String, taskId: String) {
            val data = Data.Builder()
                .putString(KEY_JOB_KIND, KIND_DIAGNOSTIC_SCAN)
                .putString(KEY_PROJECT_ID, projectId)
                .putString(KEY_TASK_ID, taskId)
                .build()
            val req = OneTimeWorkRequestBuilder<BackgroundTaskWorker>()
                .setInputData(data)
                .build()
            WorkManager.getInstance(context).enqueue(req)
        }

        fun enqueueCloudBuildPoll(
            context: Context,
            projectId: String,
            buildId: String,
            buildType: String,
            remoteUrl: String,
            remoteJobId: String,
        ) {
            val data = Data.Builder()
                .putString(KEY_JOB_KIND, KIND_POLL_CLOUD_BUILD)
                .putString(KEY_PROJECT_ID, projectId)
                .putString(KEY_BUILD_ID, buildId)
                .putString(KEY_BUILD_TYPE, buildType)
                .putString(KEY_REMOTE_URL, remoteUrl)
                .putString(KEY_REMOTE_JOB_ID, remoteJobId)
                .build()
            val req = OneTimeWorkRequestBuilder<BackgroundTaskWorker>()
                .setInputData(data)
                .build()
            WorkManager.getInstance(context).enqueue(req)
        }
    }
}

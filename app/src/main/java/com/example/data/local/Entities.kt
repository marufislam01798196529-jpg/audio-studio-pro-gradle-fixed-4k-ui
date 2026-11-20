package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "api_profiles")
data class ApiProfileEntity(
    @PrimaryKey val id: String,
    val providerName: String,
    val customName: String,
    val providerFormat: String, // ProviderFormat.name
    val baseUrl: String,
    val modelId: String,
    val modelCategory: String, // ModelCategory.name
    val customHeadersJson: String = "{}",
    val isDefault: Boolean = false,
    val isEnabled: Boolean = true,
    val supportsReasoning: Boolean = false,
    val supportsVision: Boolean = false,
    val supportsStreaming: Boolean = true,
    val fallbackProfileId: String? = null,
    val lastStatus: String = "Not tested",
    val lastError: String? = null,
    val lastLatencyMs: Long = 0L,
    val totalTokensUsed: Long = 0L,
    val estimatedCostUsd: Double = 0.0,
    val createdAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "discovered_models")
data class DiscoveredModelEntity(
    @PrimaryKey val compositeId: String, // "${profileId}::${modelId}"
    val profileId: String,
    val modelId: String,
    val displayName: String,
    val category: String, // ModelCategory.name
    val supportsReasoning: Boolean = false,
    val supportsVision: Boolean = false,
    val supportsStreaming: Boolean = true,
    val isManual: Boolean = false,
)

@Entity(tableName = "projects")
data class ProjectEntity(
    @PrimaryKey val id: String,
    val name: String,
    val projectType: String, // ProjectType.name
    val rootPath: String,
    val description: String = "",
    val assignedProfileId: String? = null,
    val assignedModelId: String? = null,
    val activeSkillIdsJson: String = "[]",
    val githubRepoFullName: String? = null,
    val githubBranch: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "chat_sessions")
data class ChatSessionEntity(
    @PrimaryKey val id: String,
    val projectId: String?,
    val title: String,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "chat_messages")
data class ChatMessageEntity(
    @PrimaryKey val id: String,
    val sessionId: String,
    val role: String, // USER, ASSISTANT, SYSTEM
    val content: String,
    val reasoningSummary: String? = null,
    val modelUsed: String? = null,
    val tokensUsed: Int = 0,
    val attachmentsJson: String = "[]",
    val planJson: String? = null,
    val isError: Boolean = false,
    val timestamp: Long = System.currentTimeMillis(),
)

@Entity(tableName = "agent_tasks")
data class AgentTaskEntity(
    @PrimaryKey val id: String,
    val projectId: String,
    val title: String,
    val description: String,
    val status: String, // TaskStatus.name
    val agentRole: String, // AgentRole.name
    val progress: Float = 0f,
    val checkpointNote: String = "",
    val resultSummary: String = "",
    val orderIndex: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "skills")
data class SkillEntity(
    @PrimaryKey val id: String,
    val name: String,
    val description: String,
    val category: String,
    val markdownContent: String,
    val isEnabled: Boolean = true,
    val isTrusted: Boolean = true,
    val sourceType: String = "BUILTIN", // BUILTIN, CUSTOM, LOCAL_IMPORT, REMOTE_APPROVED
    val createdAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "build_jobs")
data class BuildJobEntity(
    @PrimaryKey val id: String,
    val projectId: String,
    val buildType: String, // DEBUG_APK, RELEASE_APK, AAB, TESTS
    val status: String, // QUEUED, RUNNING, SUCCESS, FAILED
    val logs: String = "",
    val errorCount: Int = 0,
    val artifactPath: String? = null,
    val remoteJobId: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val completedAt: Long? = null,
)

@Entity(tableName = "project_snapshots")
data class ProjectSnapshotEntity(
    @PrimaryKey val id: String,
    val projectId: String,
    val label: String,
    val backupZipPath: String,
    val fileCount: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
)

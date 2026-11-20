package com.example.ui.state

import com.example.data.local.AgentTaskEntity
import com.example.data.local.ApiProfileEntity
import com.example.data.local.BuildJobEntity
import com.example.data.local.ChatMessageEntity
import com.example.data.local.ChatSessionEntity
import com.example.data.local.DiscoveredModelEntity
import com.example.data.local.ProjectEntity
import com.example.data.local.ProjectSnapshotEntity
import com.example.data.local.SkillEntity
import com.example.data.local.UserSettings
import com.example.data.model.AgentRole
import com.example.data.model.ApkAnalysisReport
import com.example.data.model.DiagnosticIssue
import com.example.data.model.EditorTab
import com.example.data.model.FileChangeProposal
import com.example.data.model.StructuredPlan
import com.example.data.model.TerminalLine
import com.example.data.model.WorkspaceFileNode
import com.example.engine.ImportedSourceAnalysis
import com.example.engine.MemoryStatus
import com.example.network.GitHubCommitItem
import com.example.network.GitHubRepoItem

enum class MainNavTab(val label: String) {
    HOME("Home"),
    PROJECTS("Projects"),
    CHAT("AI Chat"),
    TASKS("Tasks"),
    SETTINGS("Settings"),
}

enum class WorkspacePanelTab(val label: String) {
    CHAT("CHAT"),
    FILES("FILES"),
    EDITOR("EDITOR"),
    PREVIEW("PREVIEW"),
    TERMINAL("TERMINAL"),
}

data class PendingAttachment(
    val id: String,
    val name: String,
    val kind: String, // FILE, IMAGE, ZIP, APK
    val summaryText: String,
    val imageBase64DataUrl: String? = null,
)

data class XThrexUiState(
    val settings: UserSettings = UserSettings(),
    val memoryStatus: MemoryStatus = MemoryStatus(0, 0, 0, 0, false, false),
    val apiProfiles: List<ApiProfileEntity> = emptyList(),
    val discoveredModels: List<DiscoveredModelEntity> = emptyList(),
    val projects: List<ProjectEntity> = emptyList(),
    val activeProject: ProjectEntity? = null,
    val workspaceFiles: List<WorkspaceFileNode> = emptyList(),
    val openTabs: List<EditorTab> = emptyList(),
    val activeTabPath: String? = null,
    val chatSessions: List<ChatSessionEntity> = emptyList(),
    val activeSessionId: String? = null,
    val chatMessages: List<ChatMessageEntity> = emptyList(),
    val chatPageSize: Int = 20,
    val chatSearchResults: List<ChatMessageEntity> = emptyList(),
    val streamingContent: String = "",
    val streamingReasoning: String = "",
    val isStreaming: Boolean = false,
    val planBeforeCoding: Boolean = true,
    val pendingPlan: StructuredPlan? = null,
    val pendingPlanRequest: String? = null,
    val pendingFileProposals: List<FileChangeProposal> = emptyList(),
    val tasks: List<AgentTaskEntity> = emptyList(),
    val skills: List<SkillEntity> = emptyList(),
    val builds: List<BuildJobEntity> = emptyList(),
    val snapshots: List<ProjectSnapshotEntity> = emptyList(),
    val diagnostics: List<DiagnosticIssue> = emptyList(),
    val lastApkReport: ApkAnalysisReport? = null,
    val lastSourceImportAnalysis: ImportedSourceAnalysis? = null,
    val terminalTabs: List<String> = listOf("main"),
    val activeTerminalTab: String = "main",
    val terminalLogs: Map<String, List<TerminalLine>> = mapOf(
        "main" to listOf(
            TerminalLine(1L, "X-THREX Isolated Workspace Terminal. Type 'help' for local commands or connect Remote Container Backend for gradle/npm/git/pytest."),
        )
    ),
    val terminalWorkingDir: String = ".",
    val terminalCommandRunning: Boolean = false,
    val previewConsoleLogs: List<String> = emptyList(),
    val previewRefreshCounter: Int = 0,
    val pendingAttachments: List<PendingAttachment> = emptyList(),
    val activeAgentsStatus: Map<AgentRole, String> = emptyMap(),
    val githubRepos: List<GitHubRepoItem> = emptyList(),
    val githubCommits: List<GitHubCommitItem> = emptyList(),
    val githubBusy: Boolean = false,
    val connectionTestBusy: Boolean = false,
    val connectionTestFeedback: String? = null,
    val remoteHealthFeedback: String? = null,
    val statusBanner: String? = null,
    val errorBanner: String? = null,
)

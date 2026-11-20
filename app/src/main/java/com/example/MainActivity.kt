package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Chat
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.local.ApiProfileEntity
import com.example.data.local.BuildJobEntity
import com.example.data.local.ProjectEntity
import com.example.data.model.QuickTaskAction
import com.example.ui.components.ApiProfileEditorDialog
import com.example.ui.components.AppRecreatorDialog
import com.example.ui.components.CloudBuildDialog
import com.example.ui.components.DiffProposalsDialog
import com.example.ui.components.NewProjectDialog
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.ProjectsScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.TasksScreen
import com.example.ui.screens.WorkspaceChatScreen
import com.example.ui.state.MainNavTab
import com.example.ui.state.WorkspacePanelTab
import com.example.ui.theme.XThrexTheme
import com.example.ui.viewmodel.XThrexViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val vm: XThrexViewModel = viewModel()
            val state by vm.uiState.collectAsStateWithLifecycle()

            XThrexTheme(darkTheme = state.settings.darkTheme) {
                XThrexRootApp(vm = vm, state = state)
            }
        }
    }
}

@Composable
private fun XThrexRootApp(
    vm: XThrexViewModel,
    state: com.example.ui.state.XThrexUiState,
) {
    var currentTab by remember { mutableStateOf(MainNavTab.HOME) }
    var activeWorkspacePanel by remember { mutableStateOf(WorkspacePanelTab.CHAT) }

    var showNewProjectDialog by remember { mutableStateOf(false) }
    var showApiProfileDialog by remember { mutableStateOf(false) }
    var editingApiProfile by remember { mutableStateOf<ApiProfileEntity?>(null) }
    var showRecreatorDialog by remember { mutableStateOf(false) }
    var showDiffDialog by remember { mutableStateOf(false) }
    var cloudBuildProject by remember { mutableStateOf<ProjectEntity?>(null) }

    var pendingExportProject by remember { mutableStateOf<ProjectEntity?>(null) }
    var pendingExportSingleFile by remember { mutableStateOf<String?>(null) }
    var pendingBuildDownload by remember { mutableStateOf<BuildJobEntity?>(null) }

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(state.statusBanner, state.errorBanner) {
        val msg = state.errorBanner ?: state.statusBanner
        if (!msg.isNullOrBlank()) {
            snackbarHostState.showSnackbar(msg)
            vm.clearBanners()
        }
    }

    // SAF & Photo Picker Launchers (zero broad storage permissions)
    val importSourceZipLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            vm.importSourceZip(uri, null)
            showRecreatorDialog = false
            currentTab = MainNavTab.CHAT
            activeWorkspacePanel = WorkspacePanelTab.FILES
        }
    }

    val importApkLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            vm.importAndAnalyzeApk(uri)
        }
    }

    val exportZipLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip")
    ) { uri ->
        if (uri != null) {
            pendingExportProject?.let { proj ->
                vm.selectProject(proj.id)
                vm.exportProjectZipToUri(uri)
            } ?: vm.exportProjectZipToUri(uri)
        }
        pendingExportProject = null
    }

    val exportSingleFileLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/plain")
    ) { uri ->
        val path = pendingExportSingleFile
        if (uri != null && path != null) {
            vm.exportSingleFileToUri(uri, path)
        }
        pendingExportSingleFile = null
    }

    val downloadArtifactLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/vnd.android.package-archive")
    ) { uri ->
        val build = pendingBuildDownload
        if (uri != null && build != null) {
            vm.downloadBuildArtifactToUri(build, uri)
        }
        pendingBuildDownload = null
    }

    val attachFileLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> if (uri != null) vm.attachUri(uri, "FILE") }

    val attachZipLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> if (uri != null) vm.attachUri(uri, "ZIP") }

    val attachApkLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> if (uri != null) vm.attachUri(uri, "APK") }

    val attachScreenshotLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri -> if (uri != null) vm.attachUri(uri, "IMAGE") }

    val importSkillMdLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> if (uri != null) vm.importSkillMarkdownFromUri(uri) }

    BackHandler(enabled = currentTab != MainNavTab.HOME) {
        currentTab = MainNavTab.HOME
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useNavRail = maxWidth >= 600.dp

        Scaffold(
            contentWindowInsets = WindowInsets.safeDrawing,
            snackbarHost = { SnackbarHost(snackbarHostState) },
            bottomBar = {
                if (!useNavRail) {
                    NavigationBar {
                        MainNavTab.entries.forEach { tab ->
                            val selected = currentTab == tab
                            NavigationBarItem(
                                selected = selected,
                                onClick = { currentTab = tab },
                                icon = {
                                    Icon(
                                        imageVector = navIconFor(tab, selected),
                                        contentDescription = tab.label,
                                    )
                                },
                                label = { Text(tab.label, fontSize = 10.sp, maxLines = 1) },
                            )
                        }
                    }
                }
            },
        ) { innerPadding ->
            Row(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
                if (useNavRail) {
                    NavigationRail {
                        MainNavTab.entries.forEach { tab ->
                            val selected = currentTab == tab
                            NavigationRailItem(
                                selected = selected,
                                onClick = { currentTab = tab },
                                icon = {
                                    Icon(
                                        imageVector = navIconFor(tab, selected),
                                        contentDescription = tab.label,
                                    )
                                },
                                label = { Text(tab.label, fontSize = 10.sp) },
                            )
                        }
                    }
                }

                Box(modifier = Modifier.weight(1f).fillMaxSize()) {
                    when (currentTab) {
                        MainNavTab.HOME -> HomeScreen(
                            state = state,
                            onNewProject = { showNewProjectDialog = true },
                            onRecreateExistingApp = { showRecreatorDialog = true },
                            onUploadSourceZip = { importSourceZipLauncher.launch(arrayOf("application/zip", "application/x-zip-compressed", "*/*")) },
                            onUploadApk = { showRecreatorDialog = true },
                            onOpenProject = { id ->
                                vm.selectProject(id)
                                currentTab = MainNavTab.CHAT
                                activeWorkspacePanel = WorkspacePanelTab.FILES
                            },
                            onConnectApi = { editingApiProfile = null; showApiProfileDialog = true },
                            onQuickAction = { action ->
                                when (action) {
                                    QuickTaskAction.BUILD_APK -> {
                                        cloudBuildProject = state.activeProject
                                    }
                                    QuickTaskAction.EXPORT_ZIP -> {
                                        val p = state.activeProject
                                        if (p != null) {
                                            pendingExportProject = p
                                            exportZipLauncher.launch("${p.name}.zip")
                                        }
                                    }
                                    else -> {
                                        currentTab = MainNavTab.CHAT
                                        activeWorkspacePanel = WorkspacePanelTab.CHAT
                                        vm.submitChatPrompt(action.promptPrefix + (state.activeProject?.name ?: "new project"))
                                    }
                                }
                            },
                            onOpenTasksTab = { currentTab = MainNavTab.TASKS },
                        )

                        MainNavTab.PROJECTS -> ProjectsScreen(
                            state = state,
                            onNewProject = { showNewProjectDialog = true },
                            onOpenWorkspace = { id ->
                                vm.selectProject(id)
                                currentTab = MainNavTab.CHAT
                                activeWorkspacePanel = WorkspacePanelTab.CHAT
                            },
                            onExportZip = { proj ->
                                pendingExportProject = proj
                                exportZipLauncher.launch("${proj.name}.zip")
                            },
                            onOpenCloudBuild = { proj ->
                                vm.selectProject(proj.id)
                                cloudBuildProject = proj
                            },
                            onDeleteProject = { vm.deleteProject(it) },
                            onAssignProfileToProject = { pid, profId, mId -> vm.assignProfileToProject(pid, profId, mId) },
                            onRestoreSnapshot = { vm.restoreProjectSnapshot(it) },
                            onConnectGitHub = { vm.connectGitHubToken(it) },
                            onDisconnectGitHub = { vm.disconnectGitHub() },
                            onRefreshGitHubRepos = { vm.loadGitHubRepos() },
                            onCloneGitHubRepo = { repo, br ->
                                vm.cloneGitHubRepo(repo, br)
                                currentTab = MainNavTab.CHAT
                                activeWorkspacePanel = WorkspacePanelTab.FILES
                            },
                            onToggleSkill = { vm.toggleSkill(it) },
                            onSaveSkill = { id, name, cat, desc, md -> vm.saveCustomSkill(id, name, cat, desc, md) },
                            onImportSkillMd = { importSkillMdLauncher.launch(arrayOf("text/markdown", "text/plain", "*/*")) },
                            onDeleteSkill = { vm.deleteSkill(it) },
                        )

                        MainNavTab.CHAT -> WorkspaceChatScreen(
                            state = state,
                            activePanel = activeWorkspacePanel,
                            onSelectPanel = { activeWorkspacePanel = it },
                            onSendPrompt = { vm.submitChatPrompt(it) },
                            onStopGeneration = { vm.stopGeneration() },
                            onRegenerate = { vm.regenerateLastResponse() },
                            onEditAndResubmit = { msg, txt -> vm.editAndResubmitMessage(msg, txt) },
                            onStartCodingFromPlan = { vm.startCodingFromPlan() },
                            onTogglePlanBeforeCoding = { vm.setPlanBeforeCoding(it) },
                            onSelectReasoningLevel = { vm.setReasoningLevel(it) },
                            onNewSession = { vm.createNewChatSession() },
                            onLoadOlderMessages = { vm.loadMoreChatMessages() },
                            onSearchChat = { vm.searchChatHistory(it) },
                            onAttachFile = { attachFileLauncher.launch(arrayOf("*/*")) },
                            onAttachZip = { attachZipLauncher.launch(arrayOf("application/zip", "*/*")) },
                            onAttachApk = { attachApkLauncher.launch(arrayOf("application/vnd.android.package-archive", "*/*")) },
                            onAttachScreenshot = {
                                attachScreenshotLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            },
                            onRemoveAttachment = { vm.removeAttachment(it) },
                            onOpenFile = { vm.openFileInEditor(it) },
                            onCreateFile = { vm.createNewFile(it) },
                            onCreateFolder = { vm.createNewFolder(it) },
                            onRenameEntry = { o, n -> vm.renameWorkspaceEntry(o, n) },
                            onDuplicateFile = { vm.duplicateWorkspaceFile(it) },
                            onDeleteEntry = { vm.deleteWorkspaceEntry(it) },
                            onExportSingleFile = { path ->
                                pendingExportSingleFile = path
                                exportSingleFileLauncher.launch(path.substringAfterLast('/'))
                            },
                            onUpdateEditorContent = { vm.updateActiveEditorContent(it) },
                            onSaveEditor = { vm.saveActiveEditorTab() },
                            onUndoEditor = { vm.undoEditor() },
                            onRedoEditor = { vm.redoEditor() },
                            onCloseTab = { vm.closeEditorTab(it) },
                            onChangeEditorFontSize = { vm.setEditorFontSize(it) },
                            onToggleEditorTheme = { vm.setEditorLightTheme(it) },
                            onAiCodeEdit = { inst, snippet -> vm.requestAiCodeEdit(inst, snippet) },
                            onOpenDiffDialog = { showDiffDialog = true },
                            onOpenCloudBuild = { cloudBuildProject = state.activeProject },
                            onRunDiagnostics = {
                                vm.runProjectDiagnostics()
                                currentTab = MainNavTab.TASKS
                            },
                            onExportProjectZip = {
                                val proj = state.activeProject
                                if (proj != null) {
                                    pendingExportProject = proj
                                    exportZipLauncher.launch("${proj.name}.zip")
                                }
                            },
                            onRunTerminalCmd = { vm.runTerminalCommand(it) },
                            onAddTerminalTab = { vm.addTerminalTab() },
                            onSelectTerminalTab = { vm.selectTerminalTab(it) },
                            onClearTerminal = { vm.clearActiveTerminal() },
                            onAskAiError = { vm.askAiAboutError(it) },
                            onConsoleLog = { vm.recordPreviewConsoleLog(it) },
                            onClearConsole = { vm.clearPreviewConsoleLogs() },
                        )

                        MainNavTab.TASKS -> TasksScreen(
                            state = state,
                            onAddTask = { title, desc, role -> vm.addTask(title, desc, role) },
                            onUpdateTaskStatus = { t, s -> vm.updateTaskStatus(t, s) },
                            onRunTaskBackground = { vm.runTaskInBackground(it) },
                            onDeleteTask = { vm.deleteTask(it) },
                            onRunDiagnostics = { vm.runProjectDiagnostics() },
                            onFixAllIssues = {
                                vm.fixAllDetectedIssues()
                                currentTab = MainNavTab.CHAT
                                activeWorkspacePanel = WorkspacePanelTab.CHAT
                            },
                            onSendTaskToAiAgent = { task ->
                                currentTab = MainNavTab.CHAT
                                activeWorkspacePanel = WorkspacePanelTab.CHAT
                                vm.submitChatPrompt("Execute task (${task.agentRole}): ${task.title}\n${task.description}")
                            },
                        )

                        MainNavTab.SETTINGS -> SettingsScreen(
                            state = state,
                            onAddApiProfile = { editingApiProfile = null; showApiProfileDialog = true },
                            onEditApiProfile = { prof -> editingApiProfile = prof; showApiProfileDialog = true },
                            onSetDefaultProfile = { vm.setDefaultApiProfile(it) },
                            onToggleProfileEnabled = { vm.toggleProfileEnabled(it) },
                            onDeleteProfile = { vm.deleteApiProfile(it) },
                            onSelectReasoningLevel = { vm.setReasoningLevel(it) },
                            onSetDarkTheme = { vm.setDarkTheme(it) },
                            onSetLowMemoryMode = { vm.setLowMemoryMode(it) },
                            onRefreshMemory = { vm.refreshMemoryStatus() },
                            onSetEditorFontSize = { vm.setEditorFontSize(it) },
                            onSetEditorLightTheme = { vm.setEditorLightTheme(it) },
                            onSetNotificationsEnabled = { vm.setNotificationsEnabled(it) },
                            onSetParallelAgents = { vm.setParallelAgentsEnabled(it) },
                            onSetRequireDeleteApproval = { vm.setRequireDeleteApproval(it) },
                            onSaveRemoteBackend = { url, token ->
                                vm.setRemoteBackendUrl(url)
                                if (token.isNotBlank()) vm.setRemoteBackendToken(token)
                            },
                            onCheckRemoteHealth = { vm.checkRemoteBackendHealth() },
                            onOpenSkillsTab = { currentTab = MainNavTab.PROJECTS },
                        )
                    }
                }
            }
        }
    }

    if (showNewProjectDialog) {
        NewProjectDialog(
            onDismiss = { showNewProjectDialog = false },
            onCreate = { name, type, desc ->
                vm.createProject(name, type, desc)
                showNewProjectDialog = false
                currentTab = MainNavTab.CHAT
                activeWorkspacePanel = WorkspacePanelTab.FILES
            },
        )
    }

    if (showApiProfileDialog) {
        ApiProfileEditorDialog(
            editingProfile = editingApiProfile,
            allProfiles = state.apiProfiles,
            discoveredModels = state.discoveredModels,
            connectionBusy = state.connectionTestBusy,
            connectionFeedback = state.connectionTestFeedback,
            onDismiss = { showApiProfileDialog = false },
            onSave = { id, prov, custom, fmt, url, key, model, cat, headers, fb, def ->
                vm.saveApiProfile(id, prov, custom, fmt, url, key, model, cat, headers, fb, def)
            },
            onTestAndDiscover = { prof, overrideKey -> vm.testAndDiscoverModels(prof, overrideKey) },
            onAddManualModel = { pid, mid, cat, reas -> vm.addManualModelToProfile(pid, mid, cat, reas, false) },
        )
    }

    if (showRecreatorDialog) {
        AppRecreatorDialog(
            lastApkReport = state.lastApkReport,
            onDismiss = { showRecreatorDialog = false },
            onPickSourceZip = { importSourceZipLauncher.launch(arrayOf("application/zip", "application/x-zip-compressed", "*/*")) },
            onPickApk = { importApkLauncher.launch(arrayOf("application/vnd.android.package-archive", "*/*")) },
            onCloneGitHub = { repo, branch ->
                vm.cloneGitHubRepo(repo, branch)
                showRecreatorDialog = false
                currentTab = MainNavTab.CHAT
                activeWorkspacePanel = WorkspacePanelTab.FILES
            },
            onOpenWorkspace = {
                currentTab = MainNavTab.CHAT
                activeWorkspacePanel = WorkspacePanelTab.FILES
            },
        )
    }

    if (showDiffDialog) {
        DiffProposalsDialog(
            proposals = state.pendingFileProposals,
            onAccept = { vm.acceptProposal(it) },
            onReject = { vm.rejectProposal(it) },
            onAcceptAll = { vm.acceptAllProposals() },
            onDismiss = { showDiffDialog = false },
        )
    }

    cloudBuildProject?.let { proj ->
        CloudBuildDialog(
            projectName = proj.name,
            remoteUrlConfigured = state.settings.remoteBackendUrl.isNotBlank(),
            builds = state.builds.filter { it.projectId == proj.id },
            onTriggerBuild = { buildType -> vm.triggerCloudApkBuild(buildType) },
            onDownloadArtifact = { build ->
                pendingBuildDownload = build
                downloadArtifactLauncher.launch("${proj.name}-${build.buildType.lowercase()}.apk")
            },
            onAskAiFixLogs = { logs ->
                vm.askAiAboutError(logs)
                currentTab = MainNavTab.CHAT
                activeWorkspacePanel = WorkspacePanelTab.CHAT
            },
            onDismiss = { cloudBuildProject = null },
        )
    }
}

private fun navIconFor(tab: MainNavTab, selected: Boolean) = when (tab) {
    MainNavTab.HOME -> if (selected) Icons.Filled.Home else Icons.Outlined.Home
    MainNavTab.PROJECTS -> if (selected) Icons.Filled.Folder else Icons.Outlined.Folder
    MainNavTab.CHAT -> if (selected) Icons.Filled.Chat else Icons.Outlined.Chat
    MainNavTab.TASKS -> if (selected) Icons.Filled.Checklist else Icons.Outlined.Checklist
    MainNavTab.SETTINGS -> if (selected) Icons.Filled.Settings else Icons.Outlined.Settings
}

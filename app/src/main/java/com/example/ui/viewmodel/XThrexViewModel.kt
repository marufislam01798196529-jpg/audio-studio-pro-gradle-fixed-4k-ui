package com.example.ui.viewmodel

import android.app.Application
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Base64
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.XThrexApp
import com.example.data.local.AgentTaskEntity
import com.example.data.local.ApiProfileEntity
import com.example.data.local.BuildJobEntity
import com.example.data.local.ChatMessageEntity
import com.example.data.local.ChatSessionEntity
import com.example.data.local.DiscoveredModelEntity
import com.example.data.local.ProjectEntity
import com.example.data.local.ProjectSnapshotEntity
import com.example.data.local.SkillEntity
import com.example.data.model.AgentRole
import com.example.data.model.DiagnosticIssue
import com.example.data.model.EditorTab
import com.example.data.model.FileChangeProposal
import com.example.data.model.ModelCategory
import com.example.data.model.ProjectType
import com.example.data.model.ProviderFormat
import com.example.data.model.ReasoningLevel
import com.example.data.model.StructuredPlan
import com.example.data.model.TaskStatus
import com.example.data.model.TerminalLine
import com.example.engine.AiResponseParser
import com.example.engine.ApkAnalyzer
import com.example.engine.MemoryGuard
import com.example.engine.ProjectDiagnostics
import com.example.engine.SkillCatalog
import com.example.network.AiProviderClient
import com.example.network.ChatTurnMessage
import com.example.network.GitHubClient
import com.example.network.RemoteBackendClient
import com.example.network.StreamChunk
import com.example.security.KeystoreSecretVault
import com.example.security.SecretRedactor
import com.example.ui.state.PendingAttachment
import com.example.ui.state.XThrexUiState
import com.example.workers.BackgroundTaskWorker
import com.example.workers.NotificationHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.util.UUID

class XThrexViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as XThrexApp
    private val db = app.database
    private val vault = app.secretVault
    private val prefs = app.preferences
    private val workspace = app.workspaceManager
    private val aiClient = AiProviderClient()
    private val gitHubClient = GitHubClient()
    private val remoteClient = RemoteBackendClient()
    private val apkAnalyzer = ApkAnalyzer(application, workspace)

    private val _uiState = MutableStateFlow(XThrexUiState())
    val uiState: StateFlow<XThrexUiState> = _uiState.asStateFlow()

    private val undoStacks = mutableMapOf<String, ArrayDeque<String>>()
    private val redoStacks = mutableMapOf<String, ArrayDeque<String>>()
    private var activeMessagesJob: Job? = null
    private var activeProjectObsJob: Job? = null
    private var activeStreamJob: Job? = null

    init {
        refreshMemoryStatus()
        observeCoreFlows()
        observeMemoryTrims()
    }

    private fun observeMemoryTrims() {
        viewModelScope.launch {
            app.trimMemoryEvents.collectLatest {
                refreshMemoryStatus()
                // Release non-active editor tabs when system reports low memory
                _uiState.update { state ->
                    val active = state.activeTabPath
                    val trimmedTabs = if (active != null) {
                        state.openTabs.filter { it.filePath == active }
                    } else {
                        emptyList()
                    }
                    state.copy(
                        openTabs = trimmedTabs,
                        statusBanner = "Low-memory cleanup released inactive tabs to protect stability.",
                    )
                }
            }
        }
    }

    private fun observeCoreFlows() {
        viewModelScope.launch {
            combine(
                prefs.settingsFlow,
                db.apiProfileDao().observeAllProfiles(),
                db.discoveredModelDao().observeAllModels(),
                db.projectDao().observeAllProjects(),
                db.skillDao().observeAllSkills(),
            ) { settings, profiles, models, projects, skills ->
                CoreSnapshot(settings, profiles, models, projects, skills)
            }.collectLatest { snap ->
                _uiState.update { state ->
                    val currentActive = state.activeProject?.id?.let { id ->
                        snap.projects.firstOrNull { it.id == id }
                    } ?: snap.projects.firstOrNull()
                    state.copy(
                        settings = snap.settings,
                        apiProfiles = snap.profiles,
                        discoveredModels = snap.models,
                        projects = snap.projects,
                        skills = snap.skills,
                        activeProject = currentActive,
                    )
                }
                _uiState.value.activeProject?.let { proj ->
                    if (activeProjectObsJob == null) {
                        selectProject(proj.id)
                    }
                }
            }
        }

        viewModelScope.launch {
            db.chatDao().observeSessions().collectLatest { sessions ->
                _uiState.update { state ->
                    val activeId = state.activeSessionId?.takeIf { id -> sessions.any { it.id == id } }
                        ?: sessions.firstOrNull()?.id
                    state.copy(chatSessions = sessions, activeSessionId = activeId)
                }
                _uiState.value.activeSessionId?.let { observeMessages(it) }
            }
        }

        viewModelScope.launch {
            db.agentTaskDao().observeAllTasks().collectLatest { allTasks ->
                _uiState.update { it.copy(tasks = allTasks) }
            }
        }

        viewModelScope.launch {
            db.buildJobDao().observeRecentBuilds().collectLatest { builds ->
                _uiState.update { it.copy(builds = builds) }
            }
        }
    }

    private data class CoreSnapshot(
        val settings: com.example.data.local.UserSettings,
        val profiles: List<ApiProfileEntity>,
        val models: List<DiscoveredModelEntity>,
        val projects: List<ProjectEntity>,
        val skills: List<SkillEntity>,
    )

    fun refreshMemoryStatus() {
        _uiState.update { it.copy(memoryStatus = MemoryGuard.inspect(app)) }
    }

    fun clearBanners() {
        _uiState.update { it.copy(statusBanner = null, errorBanner = null, connectionTestFeedback = null) }
    }

    // --- SETTINGS ---
    fun setDarkTheme(enabled: Boolean) = viewModelScope.launch { prefs.setDarkTheme(enabled) }
    fun setLowMemoryMode(enabled: Boolean) = viewModelScope.launch {
        prefs.setLowMemoryMode(enabled)
        refreshMemoryStatus()
    }
    fun setEditorFontSize(sp: Int) = viewModelScope.launch { prefs.setEditorFontSize(sp) }
    fun setEditorLightTheme(light: Boolean) = viewModelScope.launch { prefs.setEditorLightTheme(light) }
    fun setReasoningLevel(level: ReasoningLevel) = viewModelScope.launch { prefs.setReasoningLevel(level) }
    fun setNotificationsEnabled(enabled: Boolean) = viewModelScope.launch { prefs.setNotificationsEnabled(enabled) }
    fun setRemoteBackendUrl(url: String) = viewModelScope.launch { prefs.setRemoteBackendUrl(url) }
    fun setRemoteBackendToken(token: String) = viewModelScope.launch {
        vault.saveSecret(KeystoreSecretVault.REMOTE_BACKEND_TOKEN_SLOT, token)
        _uiState.update { it.copy(statusBanner = "Remote backend token encrypted in Android Keystore.") }
    }
    fun setParallelAgentsEnabled(enabled: Boolean) = viewModelScope.launch { prefs.setParallelAgentsEnabled(enabled) }
    fun setRequireDeleteApproval(req: Boolean) = viewModelScope.launch { prefs.setRequireApprovalForDeletes(req) }
    fun setPlanBeforeCoding(enabled: Boolean) { _uiState.update { it.copy(planBeforeCoding = enabled) } }

    fun checkRemoteBackendHealth() {
        viewModelScope.launch {
            val url = _uiState.value.settings.remoteBackendUrl
            val token = vault.getSecret(KeystoreSecretVault.REMOTE_BACKEND_TOKEN_SLOT)
            val res = remoteClient.checkHealth(url, token)
            _uiState.update {
                it.copy(
                    remoteHealthFeedback = res.fold(
                        onSuccess = { msg -> msg },
                        onFailure = { err -> "Offline / Unreachable: ${err.message}" },
                    )
                )
            }
        }
    }

    // --- API PROFILES & MODEL DISCOVERY ---
    fun saveApiProfile(
        existingId: String?,
        providerName: String,
        customName: String,
        providerFormat: ProviderFormat,
        baseUrl: String,
        apiKeyInput: String,
        modelId: String,
        modelCategory: ModelCategory,
        customHeadersJson: String,
        fallbackProfileId: String?,
        setAsDefault: Boolean,
    ) {
        viewModelScope.launch {
            val id = existingId ?: UUID.randomUUID().toString()
            val isFirst = _uiState.value.apiProfiles.isEmpty()
            val existing = existingId?.let { db.apiProfileDao().getProfileById(it) }
            val entity = ApiProfileEntity(
                id = id,
                providerName = providerName.trim(),
                customName = customName.trim().ifBlank { providerName.trim() },
                providerFormat = providerFormat.name,
                baseUrl = baseUrl.trim().trimEnd('/'),
                modelId = modelId.trim(),
                modelCategory = modelCategory.name,
                customHeadersJson = customHeadersJson.ifBlank { "{}" },
                isDefault = setAsDefault || isFirst || (existing?.isDefault == true),
                isEnabled = existing?.isEnabled ?: true,
                supportsReasoning = existing?.supportsReasoning ?: false,
                supportsVision = existing?.supportsVision ?: false,
                supportsStreaming = true,
                fallbackProfileId = fallbackProfileId?.takeIf { it != id },
                lastStatus = existing?.lastStatus ?: "Saved",
                lastLatencyMs = existing?.lastLatencyMs ?: 0L,
                totalTokensUsed = existing?.totalTokensUsed ?: 0L,
                estimatedCostUsd = existing?.estimatedCostUsd ?: 0.0,
            )
            db.apiProfileDao().upsertProfile(entity)
            if (apiKeyInput.isNotBlank()) {
                vault.saveSecret(KeystoreSecretVault.apiKeySlot(id), apiKeyInput.trim())
            }
            if (entity.isDefault) {
                db.apiProfileDao().setDefaultProfile(id)
            }
            if (modelId.isNotBlank()) {
                db.discoveredModelDao().upsertModels(
                    listOf(
                        DiscoveredModelEntity(
                            compositeId = "$id::${modelId.trim()}",
                            profileId = id,
                            modelId = modelId.trim(),
                            displayName = modelId.trim(),
                            category = modelCategory.name,
                            supportsReasoning = entity.supportsReasoning,
                            supportsVision = entity.supportsVision,
                            supportsStreaming = true,
                            isManual = true,
                        )
                    )
                )
            }
            _uiState.update { it.copy(statusBanner = "API profile '${entity.customName}' encrypted and saved.") }
        }
    }

    fun hasStoredApiKey(profileId: String): Boolean =
        !vault.getSecret(KeystoreSecretVault.apiKeySlot(profileId)).isNullOrBlank()

    fun setDefaultApiProfile(profileId: String) = viewModelScope.launch {
        db.apiProfileDao().setDefaultProfile(profileId)
    }

    fun toggleProfileEnabled(profile: ApiProfileEntity) = viewModelScope.launch {
        db.apiProfileDao().upsertProfile(profile.copy(isEnabled = !profile.isEnabled))
    }

    fun deleteApiProfile(profileId: String) = viewModelScope.launch {
        vault.deleteSecret(KeystoreSecretVault.apiKeySlot(profileId))
        db.apiProfileDao().deleteProfile(profileId)
    }

    fun testAndDiscoverModels(
        profile: ApiProfileEntity,
        overrideApiKey: String? = null,
    ) {
        viewModelScope.launch {
            _uiState.update { it.copy(connectionTestBusy = true, connectionTestFeedback = "Testing endpoint & discovering models...") }
            val key = overrideApiKey?.takeIf { it.isNotBlank() }
                ?: vault.getSecret(KeystoreSecretVault.apiKeySlot(profile.id)).orEmpty()
            val result = aiClient.testConnectionAndDiscoverModels(profile, key)
            if (result.success) {
                if (result.discoveredModels.isNotEmpty()) {
                    db.discoveredModelDao().clearDiscoveredForProfile(profile.id)
                    db.discoveredModelDao().upsertModels(result.discoveredModels)
                }
                val selectedModel = profile.modelId.ifBlank {
                    result.discoveredModels.firstOrNull()?.modelId.orEmpty()
                }
                val matchedModel = result.discoveredModels.firstOrNull { it.modelId == selectedModel }
                db.apiProfileDao().upsertProfile(
                    profile.copy(
                        modelId = selectedModel,
                        supportsReasoning = matchedModel?.supportsReasoning ?: result.supportsReasoningParam,
                        supportsVision = matchedModel?.supportsVision ?: profile.supportsVision,
                        lastStatus = "Connected",
                        lastError = null,
                        lastLatencyMs = result.latencyMs,
                    )
                )
            } else {
                db.apiProfileDao().upsertProfile(
                    profile.copy(
                        lastStatus = "Error",
                        lastError = result.message,
                        lastLatencyMs = result.latencyMs,
                    )
                )
            }
            _uiState.update {
                it.copy(
                    connectionTestBusy = false,
                    connectionTestFeedback = result.message,
                )
            }
        }
    }

    fun addManualModelToProfile(
        profileId: String,
        modelId: String,
        category: ModelCategory,
        supportsReasoning: Boolean,
        supportsVision: Boolean,
    ) {
        viewModelScope.launch {
            val clean = modelId.trim()
            if (clean.isBlank()) return@launch
            db.discoveredModelDao().upsertModels(
                listOf(
                    DiscoveredModelEntity(
                        compositeId = "$profileId::$clean",
                        profileId = profileId,
                        modelId = clean,
                        displayName = clean,
                        category = category.name,
                        supportsReasoning = supportsReasoning,
                        supportsVision = supportsVision,
                        supportsStreaming = true,
                        isManual = true,
                    )
                )
            )
            db.apiProfileDao().getProfileById(profileId)?.let { prof ->
                db.apiProfileDao().upsertProfile(
                    prof.copy(
                        modelId = clean,
                        modelCategory = category.name,
                        supportsReasoning = supportsReasoning,
                        supportsVision = supportsVision,
                    )
                )
            }
            _uiState.update { it.copy(statusBanner = "Model '$clean' (${category.label}) added & selected.") }
        }
    }

    fun selectModelForActiveProfile(model: DiscoveredModelEntity) {
        viewModelScope.launch {
            db.apiProfileDao().getProfileById(model.profileId)?.let { prof ->
                db.apiProfileDao().upsertProfile(
                    prof.copy(
                        modelId = model.modelId,
                        modelCategory = model.category,
                        supportsReasoning = model.supportsReasoning,
                        supportsVision = model.supportsVision,
                    )
                )
            }
        }
    }

    // --- PROJECTS & FILE MANAGER ---
    fun createProject(name: String, type: ProjectType, description: String) {
        viewModelScope.launch {
            val cleanName = name.trim().ifBlank { "UntitledProject" }
            val id = UUID.randomUUID().toString()
            val dir = workspace.getProjectDir(id)
            workspace.initializeTemplate(id, cleanName, type, description)
            val defaultProf = db.apiProfileDao().getDefaultProfile()
            val entity = ProjectEntity(
                id = id,
                name = cleanName,
                projectType = type.name,
                rootPath = dir.absolutePath,
                description = description.trim(),
                assignedProfileId = defaultProf?.id,
                assignedModelId = defaultProf?.modelId,
            )
            db.projectDao().upsertProject(entity)
            selectProject(id)
            createNewChatSession(id, "$cleanName Session")
            _uiState.update { it.copy(statusBanner = "Project '$cleanName' created.") }
        }
    }

    fun selectProject(projectId: String) {
        activeProjectObsJob?.cancel()
        activeProjectObsJob = viewModelScope.launch {
            val proj = db.projectDao().getProjectById(projectId) ?: return@launch
            _uiState.update {
                it.copy(
                    activeProject = proj,
                    openTabs = emptyList(),
                    activeTabPath = null,
                    diagnostics = emptyList(),
                    pendingFileProposals = emptyList(),
                )
            }
            refreshWorkspaceFiles(projectId)
            launch {
                db.snapshotDao().observeSnapshots(projectId).collectLatest { snaps ->
                    _uiState.update { it.copy(snapshots = snaps) }
                }
            }
            // Select or create chat session for this project
            val existingSession = _uiState.value.chatSessions.firstOrNull { it.projectId == projectId }
            if (existingSession != null) {
                selectChatSession(existingSession.id)
            }
        }
    }

    fun assignProfileToProject(projectId: String, profileId: String, modelId: String?) {
        viewModelScope.launch {
            val proj = db.projectDao().getProjectById(projectId) ?: return@launch
            db.projectDao().upsertProject(
                proj.copy(
                    assignedProfileId = profileId,
                    assignedModelId = modelId,
                    updatedAt = System.currentTimeMillis(),
                )
            )
        }
    }

    fun deleteProject(projectId: String) {
        viewModelScope.launch {
            workspace.deleteProjectDir(projectId)
            db.projectDao().deleteProject(projectId)
            _uiState.update { state ->
                val remaining = state.projects.filterNot { it.id == projectId }
                state.copy(
                    activeProject = remaining.firstOrNull(),
                    workspaceFiles = emptyList(),
                    openTabs = emptyList(),
                    activeTabPath = null,
                )
            }
        }
    }

    fun refreshWorkspaceFiles(projectId: String? = _uiState.value.activeProject?.id) {
        val pid = projectId ?: return
        viewModelScope.launch {
            val files = workspace.listFiles(pid)
            _uiState.update { it.copy(workspaceFiles = files) }
        }
    }

    fun openFileInEditor(relativePath: String) {
        val pid = _uiState.value.activeProject?.id ?: return
        val existing = _uiState.value.openTabs.firstOrNull { it.filePath == relativePath }
        if (existing != null) {
            _uiState.update { it.copy(activeTabPath = relativePath) }
            return
        }
        viewModelScope.launch {
            val lowRam = _uiState.value.settings.lowMemoryMode
            val (content, truncated) = workspace.readFileChunked(pid, relativePath, lowRam)
            val tab = EditorTab(
                filePath = relativePath,
                fileName = relativePath.substringAfterLast('/'),
                content = content,
                isDirty = false,
                isTruncatedForLowRam = truncated,
                totalFileBytes = File(workspace.getProjectDir(pid), relativePath).length(),
            )
            undoStacks[relativePath] = ArrayDeque()
            redoStacks[relativePath] = ArrayDeque()
            _uiState.update { state ->
                val maxTabs = if (lowRam) 2 else 6
                val updated = (state.openTabs + tab).takeLast(maxTabs)
                state.copy(openTabs = updated, activeTabPath = relativePath)
            }
        }
    }

    fun closeEditorTab(relativePath: String) {
        _uiState.update { state ->
            val remaining = state.openTabs.filterNot { it.filePath == relativePath }
            val nextActive = if (state.activeTabPath == relativePath) remaining.lastOrNull()?.filePath else state.activeTabPath
            state.copy(openTabs = remaining, activeTabPath = nextActive)
        }
    }

    fun updateActiveEditorContent(newContent: String) {
        val activePath = _uiState.value.activeTabPath ?: return
        val currentTab = _uiState.value.openTabs.firstOrNull { it.filePath == activePath } ?: return
        if (currentTab.content == newContent) return

        val undo = undoStacks.getOrPut(activePath) { ArrayDeque() }
        if (undo.size > 30) undo.removeFirst()
        undo.addLast(currentTab.content)
        redoStacks.getOrPut(activePath) { ArrayDeque() }.clear()

        _uiState.update { state ->
            state.copy(
                openTabs = state.openTabs.map {
                    if (it.filePath == activePath) it.copy(content = newContent, isDirty = true) else it
                }
            )
        }
    }

    fun undoEditor() {
        val path = _uiState.value.activeTabPath ?: return
        val undo = undoStacks[path] ?: return
        if (undo.isEmpty()) return
        val prev = undo.removeLast()
        val curr = _uiState.value.openTabs.firstOrNull { it.filePath == path }?.content ?: return
        redoStacks.getOrPut(path) { ArrayDeque() }.addLast(curr)
        _uiState.update { state ->
            state.copy(openTabs = state.openTabs.map { if (it.filePath == path) it.copy(content = prev, isDirty = true) else it })
        }
    }

    fun redoEditor() {
        val path = _uiState.value.activeTabPath ?: return
        val redo = redoStacks[path] ?: return
        if (redo.isEmpty()) return
        val next = redo.removeLast()
        val curr = _uiState.value.openTabs.firstOrNull { it.filePath == path }?.content ?: return
        undoStacks.getOrPut(path) { ArrayDeque() }.addLast(curr)
        _uiState.update { state ->
            state.copy(openTabs = state.openTabs.map { if (it.filePath == path) it.copy(content = next, isDirty = true) else it })
        }
    }

    fun saveActiveEditorTab() {
        val pid = _uiState.value.activeProject?.id ?: return
        val path = _uiState.value.activeTabPath ?: return
        val tab = _uiState.value.openTabs.firstOrNull { it.filePath == path } ?: return
        viewModelScope.launch {
            workspace.writeProjectFile(pid, path, tab.content)
            _uiState.update { state ->
                state.copy(
                    openTabs = state.openTabs.map { if (it.filePath == path) it.copy(isDirty = false) else it },
                    previewRefreshCounter = state.previewRefreshCounter + 1,
                    statusBanner = "Saved $path",
                )
            }
            refreshWorkspaceFiles(pid)
        }
    }

    fun createNewFile(relativePath: String, initialContent: String = "") {
        val pid = _uiState.value.activeProject?.id ?: return
        viewModelScope.launch {
            workspace.writeProjectFile(pid, relativePath.trim(), initialContent)
            refreshWorkspaceFiles(pid)
            openFileInEditor(relativePath.trim())
        }
    }

    fun createNewFolder(relativePath: String) {
        val pid = _uiState.value.activeProject?.id ?: return
        viewModelScope.launch {
            workspace.createFolder(pid, relativePath.trim())
            refreshWorkspaceFiles(pid)
        }
    }

    fun renameWorkspaceEntry(oldPath: String, newPath: String) {
        val pid = _uiState.value.activeProject?.id ?: return
        viewModelScope.launch {
            workspace.renameOrMove(pid, oldPath, newPath)
            refreshWorkspaceFiles(pid)
        }
    }

    fun duplicateWorkspaceFile(path: String) {
        val pid = _uiState.value.activeProject?.id ?: return
        viewModelScope.launch {
            workspace.duplicateFile(pid, path)
            refreshWorkspaceFiles(pid)
        }
    }

    fun deleteWorkspaceEntry(path: String) {
        val pid = _uiState.value.activeProject?.id ?: return
        viewModelScope.launch {
            createAutoBackup(pid, "Before delete $path")
            workspace.deleteEntry(pid, path)
            closeEditorTab(path)
            refreshWorkspaceFiles(pid)
        }
    }

    fun createAutoBackup(projectId: String, label: String) {
        viewModelScope.launch {
            val (zipPath, count) = workspace.createSnapshotBackup(
                projectId,
                label,
                vault.getAllRegisteredSecretsForRedaction(),
            )
            db.snapshotDao().insertSnapshot(
                ProjectSnapshotEntity(
                    id = UUID.randomUUID().toString(),
                    projectId = projectId,
                    label = label,
                    backupZipPath = zipPath,
                    fileCount = count,
                )
            )
        }
    }

    fun restoreProjectSnapshot(snapshot: ProjectSnapshotEntity) {
        viewModelScope.launch {
            val ok = workspace.restoreSnapshot(snapshot.projectId, snapshot.backupZipPath)
            if (ok) {
                _uiState.update {
                    it.copy(
                        openTabs = emptyList(),
                        activeTabPath = null,
                        previewRefreshCounter = it.previewRefreshCounter + 1,
                        statusBanner = "Restored snapshot: ${snapshot.label}",
                    )
                }
                refreshWorkspaceFiles(snapshot.projectId)
            }
        }
    }

    // --- ZIP IMPORT / EXPORT & APK RECONSTRUCTION ---
    fun importSourceZip(uri: Uri, projectNameInput: String?) {
        viewModelScope.launch {
            try {
                val displayName = resolveUriDisplayName(uri) ?: "ImportedProject.zip"
                val projName = projectNameInput?.takeIf { it.isNotBlank() }
                    ?: displayName.substringBeforeLast('.')
                val id = UUID.randomUUID().toString()
                val analysis = workspace.importZipFromUri(id, uri)
                val defaultProf = db.apiProfileDao().getDefaultProfile()
                val entity = ProjectEntity(
                    id = id,
                    name = projName,
                    projectType = analysis.detectedType.name,
                    rootPath = workspace.getProjectDir(id).absolutePath,
                    description = analysis.architectureSummary,
                    assignedProfileId = defaultProf?.id,
                    assignedModelId = defaultProf?.modelId,
                )
                db.projectDao().upsertProject(entity)
                selectProject(id)
                _uiState.update {
                    it.copy(
                        lastSourceImportAnalysis = analysis,
                        statusBanner = "Imported $projName (${analysis.fileCount} files, ${analysis.detectedType.label}).",
                    )
                }
                NotificationHelper.notifyTaskEvent(
                    app,
                    NotificationHelper.CHANNEL_TASKS,
                    id.hashCode(),
                    "Project Analysis Completed",
                    analysis.architectureSummary,
                )
            } catch (e: Exception) {
                _uiState.update { it.copy(errorBanner = "Source ZIP import failed: ${e.message}") }
            }
        }
    }

    fun importAndAnalyzeApk(uri: Uri) {
        viewModelScope.launch {
            try {
                val fileName = resolveUriDisplayName(uri) ?: "uploaded.apk"
                val id = UUID.randomUUID().toString()
                val report = apkAnalyzer.analyzeAndReconstruct(uri, fileName, id)
                val defaultProf = db.apiProfileDao().getDefaultProfile()
                val entity = ProjectEntity(
                    id = id,
                    name = "${report.applicationName} (APK)",
                    projectType = ProjectType.ANDROID_GRADLE.name,
                    rootPath = workspace.getProjectDir(id).absolutePath,
                    description = "Reconstructed from $fileName (${report.packageName} v${report.versionName})",
                    assignedProfileId = defaultProf?.id,
                    assignedModelId = defaultProf?.modelId,
                )
                db.projectDao().upsertProject(entity)
                selectProject(id)
                _uiState.update {
                    it.copy(
                        lastApkReport = report,
                        statusBanner = "APK analyzed & workspace reconstructed for ${report.packageName}.",
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(errorBanner = "APK analysis failed: ${e.message}") }
            }
        }
    }

    fun exportProjectZipToUri(uri: Uri, selectedOnly: Set<String>? = null) {
        val proj = _uiState.value.activeProject ?: return
        viewModelScope.launch {
            try {
                val pType = runCatching { ProjectType.valueOf(proj.projectType) }
                    .getOrDefault(ProjectType.HTML_CSS_JS)
                val count = withContext(Dispatchers.IO) {
                    app.contentResolver.openOutputStream(uri)?.use { out ->
                        workspace.exportProjectZip(
                            projectId = proj.id,
                            projectName = proj.name,
                            projectType = pType,
                            outputStream = out,
                            knownSecrets = vault.getAllRegisteredSecretsForRedaction(),
                            selectedFilesOnly = selectedOnly,
                        )
                    } ?: error("Cannot write to chosen output location.")
                }
                _uiState.update { it.copy(statusBanner = "Exported $count files into clean ZIP (secrets redacted).") }
                NotificationHelper.notifyTaskEvent(
                    app,
                    NotificationHelper.CHANNEL_TASKS,
                    proj.id.hashCode(),
                    "ZIP Export Ready",
                    "${proj.name}.zip exported cleanly with $count source files.",
                )
            } catch (e: Exception) {
                _uiState.update { it.copy(errorBanner = "ZIP export failed: ${e.message}") }
            }
        }
    }

    fun exportSingleFileToUri(uri: Uri, relativePath: String) {
        val pid = _uiState.value.activeProject?.id ?: return
        viewModelScope.launch {
            try {
                val (content, _) = workspace.readFileChunked(pid, relativePath, lowMemoryMode = false)
                val redacted = SecretRedactor.redact(content, vault.getAllRegisteredSecretsForRedaction())
                withContext(Dispatchers.IO) {
                    app.contentResolver.openOutputStream(uri)?.use { out ->
                        out.write(redacted.toByteArray(Charsets.UTF_8))
                    }
                }
                _uiState.update { it.copy(statusBanner = "Saved $relativePath") }
            } catch (e: Exception) {
                _uiState.update { it.copy(errorBanner = "File export failed: ${e.message}") }
            }
        }
    }

    // --- ATTACHMENTS ---
    fun attachUri(uri: Uri, kind: String) {
        viewModelScope.launch {
            try {
                val name = resolveUriDisplayName(uri) ?: "attachment"
                val id = UUID.randomUUID().toString()
                when (kind) {
                    "IMAGE" -> {
                        val b64 = withContext(Dispatchers.IO) {
                            val bytes = app.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                                ?: error("Empty image")
                            if (bytes.size > 2 * 1024 * 1024) error("Image exceeds 2MB attachment limit")
                            "data:image/jpeg;base64," + Base64.encodeToString(bytes, Base64.NO_WRAP)
                        }
                        _uiState.update {
                            it.copy(
                                pendingAttachments = it.pendingAttachments + PendingAttachment(
                                    id = id,
                                    name = name,
                                    kind = "IMAGE",
                                    summaryText = "[Screenshot attached: $name]",
                                    imageBase64DataUrl = b64,
                                )
                            )
                        }
                    }
                    else -> {
                        val snippet = withContext(Dispatchers.IO) {
                            app.contentResolver.openInputStream(uri)?.bufferedReader()?.use { reader ->
                                val chars = CharArray(8192)
                                val read = reader.read(chars)
                                if (read > 0) String(chars, 0, read) else ""
                            }.orEmpty()
                        }
                        _uiState.update {
                            it.copy(
                                pendingAttachments = it.pendingAttachments + PendingAttachment(
                                    id = id,
                                    name = name,
                                    kind = kind,
                                    summaryText = "Attached $kind `$name`:\n```\n${snippet.take(4000)}\n```",
                                )
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(errorBanner = "Attachment failed: ${e.message}") }
            }
        }
    }

    fun removeAttachment(id: String) {
        _uiState.update { state ->
            state.copy(pendingAttachments = state.pendingAttachments.filterNot { it.id == id })
        }
    }

    // --- AI CHAT, PLANNING & AGENT MODE ---
    fun createNewChatSession(projectId: String? = _uiState.value.activeProject?.id, title: String = "New Coding Chat") {
        viewModelScope.launch {
            val id = UUID.randomUUID().toString()
            val session = ChatSessionEntity(id = id, projectId = projectId, title = title)
            db.chatDao().upsertSession(session)
            selectChatSession(id)
        }
    }

    fun selectChatSession(sessionId: String) {
        _uiState.update { it.copy(activeSessionId = sessionId) }
        observeMessages(sessionId)
    }

    fun loadMoreChatMessages() {
        val next = _uiState.value.chatPageSize + 20
        _uiState.update { it.copy(chatPageSize = next) }
        _uiState.value.activeSessionId?.let { observeMessages(it) }
    }

    fun searchChatHistory(query: String) {
        viewModelScope.launch {
            if (query.isBlank()) {
                _uiState.update { it.copy(chatSearchResults = emptyList()) }
            } else {
                val hits = db.chatDao().searchMessages(query.trim())
                _uiState.update { it.copy(chatSearchResults = hits) }
            }
        }
    }

    private fun observeMessages(sessionId: String) {
        activeMessagesJob?.cancel()
        activeMessagesJob = viewModelScope.launch {
            val limit = _uiState.value.chatPageSize
            db.chatDao().observeRecentMessages(sessionId, limit).collectLatest { desc ->
                _uiState.update { it.copy(chatMessages = desc.reversed()) }
            }
        }
    }

    fun stopGeneration() {
        aiClient.cancelActiveStream()
        activeStreamJob?.cancel()
        _uiState.update {
            it.copy(
                isStreaming = false,
                activeAgentsStatus = emptyMap(),
                statusBanner = "Generation stopped.",
            )
        }
    }

    fun submitChatPrompt(promptText: String, forceDirectCoding: Boolean = false) {
        val cleanPrompt = promptText.trim()
        if (cleanPrompt.isEmpty() && _uiState.value.pendingAttachments.isEmpty()) return

        viewModelScope.launch {
            val profile = resolveActiveProfile() ?: run {
                _uiState.update {
                    it.copy(errorBanner = "No enabled AI API profile configured. Open Settings -> API Management to connect your API key and Base URL.")
                }
                return@launch
            }
            val apiKey = vault.getSecret(KeystoreSecretVault.apiKeySlot(profile.id)).orEmpty()
            val format = runCatching { ProviderFormat.valueOf(profile.providerFormat) }
                .getOrDefault(ProviderFormat.OPENAI_COMPATIBLE)
            if (apiKey.isBlank() && format != ProviderFormat.CUSTOM) {
                _uiState.update {
                    it.copy(errorBanner = "API key missing for '${profile.customName}'. Add your key in Settings -> API Management.")
                }
                return@launch
            }

            val sessionId = _uiState.value.activeSessionId ?: run {
                val sid = UUID.randomUUID().toString()
                db.chatDao().upsertSession(
                    ChatSessionEntity(sid, _uiState.value.activeProject?.id, cleanPrompt.take(40))
                )
                sid
            }

            val attachments = _uiState.value.pendingAttachments
            val attachmentContext = attachments.joinToString("\n\n") { it.summaryText }
            val fullUserText = buildString {
                append(cleanPrompt)
                if (attachmentContext.isNotBlank()) {
                    append("\n\n").append(attachmentContext)
                }
            }

            val userMsg = ChatMessageEntity(
                id = UUID.randomUUID().toString(),
                sessionId = sessionId,
                role = "USER",
                content = fullUserText,
                attachmentsJson = attachments.joinToString(",") { it.name },
            )
            db.chatDao().upsertMessage(userMsg)
            _uiState.update { it.copy(pendingAttachments = emptyList()) }

            val shouldPlanFirst = _uiState.value.planBeforeCoding && !forceDirectCoding &&
                looksLikeBuildOrEditRequest(cleanPrompt)

            if (shouldPlanFirst) {
                runPlanningStage(profile, apiKey, sessionId, fullUserText)
            } else {
                runCodingAgentStage(profile, apiKey, sessionId, fullUserText)
            }
        }
    }

    fun startCodingFromPlan() {
        val plan = _uiState.value.pendingPlan ?: return
        val origPrompt = _uiState.value.pendingPlanRequest ?: "Implement approved plan"
        viewModelScope.launch {
            val pid = _uiState.value.activeProject?.id
            if (pid != null) {
                createAutoBackup(pid, "Pre-coding snapshot")
                plan.implementationTasks.forEachIndexed { idx, taskTitle ->
                    db.agentTaskDao().upsertTask(
                        AgentTaskEntity(
                            id = UUID.randomUUID().toString(),
                            projectId = pid,
                            title = taskTitle,
                            description = "Generated from approved plan",
                            status = TaskStatus.PENDING.name,
                            agentRole = inferAgentRole(taskTitle).name,
                            orderIndex = idx,
                        )
                    )
                }
            }
            val profile = resolveActiveProfile() ?: return@launch
            val apiKey = vault.getSecret(KeystoreSecretVault.apiKeySlot(profile.id)).orEmpty()
            val sessionId = _uiState.value.activeSessionId ?: return@launch
            val promptWithPlan = """
                Execute the approved implementation plan for: $origPrompt
                Files to create: ${plan.filesToCreate.joinToString(", ")}
                Files to modify: ${plan.filesToModify.joinToString(", ")}
                Tasks: ${plan.implementationTasks.joinToString("; ")}

                Output every created or modified file using exact blocks:
                <<<FILE: relative/path.ext>>>
                <complete file content>
                <<<END_FILE>>>
            """.trimIndent()
            _uiState.update { it.copy(pendingPlan = null, pendingPlanRequest = null) }
            runCodingAgentStage(profile, apiKey, sessionId, promptWithPlan)
        }
    }

    fun editAndResubmitMessage(message: ChatMessageEntity, newContent: String) {
        viewModelScope.launch {
            db.chatDao().deleteMessagesAfter(message.sessionId, message.timestamp)
            db.chatDao().upsertMessage(message.copy(content = newContent))
            val profile = resolveActiveProfile() ?: return@launch
            val apiKey = vault.getSecret(KeystoreSecretVault.apiKeySlot(profile.id)).orEmpty()
            runCodingAgentStage(profile, apiKey, message.sessionId, newContent)
        }
    }

    fun regenerateLastResponse() {
        val msgs = _uiState.value.chatMessages
        val lastUser = msgs.lastOrNull { it.role == "USER" } ?: return
        viewModelScope.launch {
            db.chatDao().deleteMessagesAfter(lastUser.sessionId, lastUser.timestamp)
            val profile = resolveActiveProfile() ?: return@launch
            val apiKey = vault.getSecret(KeystoreSecretVault.apiKeySlot(profile.id)).orEmpty()
            runCodingAgentStage(profile, apiKey, lastUser.sessionId, lastUser.content)
        }
    }

    private suspend fun runPlanningStage(
        profile: ApiProfileEntity,
        apiKey: String,
        sessionId: String,
        userRequest: String,
    ) {
        _uiState.update {
            it.copy(
                activeAgentsStatus = mapOf(AgentRole.PLANNER to "Analyzing workspace & generating 10-point plan"),
            )
        }
        val workspaceSummary = buildWorkspaceContextSnippet()
        val skillsContext = buildActiveSkillsContext()
        val sysPrompt = """
            You are X-THREX Planner Agent. Analyze the request and existing workspace BEFORE coding.
            Respond with a concise summary followed by a valid ```json``` block containing these exact keys:
            {
              "user_request_analysis": "...",
              "project_understanding": "...",
              "requirements": ["..."],
              "proposed_architecture": "...",
              "files_to_create": ["..."],
              "files_to_modify": ["..."],
              "dependencies": ["..."],
              "implementation_tasks": ["..."],
              "testing_strategy": "...",
              "expected_final_output": "...",
              "concise_reasoning_summary": "..."
            }
            $skillsContext
            $workspaceSummary
        """.trimIndent()

        streamAiTurn(
            profile = profile,
            apiKey = apiKey,
            sessionId = sessionId,
            systemPrompt = sysPrompt,
            userPrompt = userRequest,
            onCompletedText = { fullText ->
                val parsed = AiResponseParser.parseStructuredPlan(fullText)
                _uiState.update {
                    it.copy(
                        pendingPlan = parsed,
                        pendingPlanRequest = if (parsed != null) userRequest else null,
                        activeAgentsStatus = emptyMap(),
                    )
                }
            },
        )
    }

    private suspend fun runCodingAgentStage(
        profile: ApiProfileEntity,
        apiKey: String,
        sessionId: String,
        userRequest: String,
    ) {
        val proj = _uiState.value.activeProject
        val role = when (proj?.projectType) {
            ProjectType.ANDROID_GRADLE.name, ProjectType.KOTLIN.name, ProjectType.JAVA.name -> AgentRole.ANDROID
            ProjectType.HTML_CSS_JS.name, ProjectType.WEBSITE.name, ProjectType.REACT.name -> AgentRole.FRONTEND
            else -> AgentRole.BACKEND
        }
        _uiState.update {
            it.copy(
                activeAgentsStatus = mapOf(
                    role to "Writing code & patches",
                    AgentRole.CODE_REVIEWER to "Queued for diff review",
                )
            )
        }

        val workspaceSummary = buildWorkspaceContextSnippet()
        val skillsContext = buildActiveSkillsContext()
        val sysPrompt = """
            You are X-THREX Autonomous Coding Agent.
            Always respect existing project files and never output placeholder or fake code.
            When creating or updating files, output complete file contents inside:
            <<<FILE: relative/path/to/file.ext>>>
            <code here>
            <<<END_FILE>>>
            Never embed API keys or secrets in code.
            $skillsContext
            $workspaceSummary
        """.trimIndent()

        streamAiTurn(
            profile = profile,
            apiKey = apiKey,
            sessionId = sessionId,
            systemPrompt = sysPrompt,
            userPrompt = userRequest,
            onCompletedText = { fullText ->
                val pid = proj?.id
                if (pid != null) {
                    val projectDir = workspace.getProjectDir(pid)
                    val proposals = AiResponseParser.extractFileProposals(fullText) { relPath ->
                        val f = File(projectDir, relPath)
                        if (f.exists() && f.isFile && f.length() < 256 * 1024) f.readText() else null
                    }
                    if (proposals.isNotEmpty()) {
                        _uiState.update { it.copy(pendingFileProposals = it.pendingFileProposals + proposals) }
                    }
                    // Advance first pending task if present
                    _uiState.value.tasks.firstOrNull {
                        it.projectId == pid && it.status == TaskStatus.PENDING.name
                    }?.let { task ->
                        viewModelScope.launch {
                            db.agentTaskDao().upsertTask(
                                task.copy(
                                    status = TaskStatus.COMPLETED.name,
                                    progress = 1f,
                                    resultSummary = "Proposals ready (${proposals.size} files)",
                                    updatedAt = System.currentTimeMillis(),
                                )
                            )
                        }
                    }
                }
                _uiState.update { it.copy(activeAgentsStatus = emptyMap()) }
                NotificationHelper.notifyTaskEvent(
                    app,
                    NotificationHelper.CHANNEL_TASKS,
                    sessionId.hashCode(),
                    "AI Task Completed",
                    "X-THREX finished generating response for ${proj?.name ?: "chat"}.",
                )
            },
        )
    }

    private suspend fun streamAiTurn(
        profile: ApiProfileEntity,
        apiKey: String,
        sessionId: String,
        systemPrompt: String,
        userPrompt: String,
        onCompletedText: (String) -> Unit,
    ) {
        val modelId = _uiState.value.activeProject?.assignedModelId?.takeIf { it.isNotBlank() }
            ?: profile.modelId.ifBlank {
                _uiState.update { it.copy(errorBanner = "No Model ID selected for '${profile.customName}'.") }
                return
            }

        val recentHistory = _uiState.value.chatMessages.takeLast(8).map {
            ChatTurnMessage(
                role = if (it.role == "ASSISTANT") "assistant" else "user",
                content = it.content,
            )
        }
        val turns = listOf(ChatTurnMessage("system", systemPrompt)) +
            recentHistory.dropLast(1) +
            ChatTurnMessage("user", userPrompt)

        _uiState.update {
            it.copy(isStreaming = true, streamingContent = "", streamingReasoning = "", errorBanner = null)
        }

        val textAcc = StringBuilder()
        val reasoningAcc = StringBuilder()
        var streamError: String? = null
        var tokensUsed = 0

        activeStreamJob?.cancel()
        activeStreamJob = viewModelScope.launch {
            aiClient.streamChatCompletion(
                profile = profile,
                apiKey = apiKey,
                modelId = modelId,
                messages = turns,
                reasoningLevel = _uiState.value.settings.reasoningLevel,
                supportsReasoningParam = profile.supportsReasoning,
            ).collect { chunk ->
                when (chunk) {
                    is StreamChunk.Delta -> {
                        textAcc.append(chunk.text)
                        if (!chunk.reasoningText.isNullOrEmpty()) {
                            reasoningAcc.append(chunk.reasoningText)
                        }
                        _uiState.update {
                            it.copy(
                                streamingContent = textAcc.toString(),
                                streamingReasoning = reasoningAcc.toString(),
                            )
                        }
                    }
                    is StreamChunk.Completed -> {
                        tokensUsed = chunk.totalTokens
                        if (chunk.totalTokens > 0) {
                            db.apiProfileDao().recordUsage(profile.id, chunk.totalTokens.toLong(), chunk.estimatedCostUsd)
                        }
                    }
                    is StreamChunk.Error -> {
                        streamError = chunk.message
                    }
                }
            }

            // Optional fallback provider failover if primary failed
            if (streamError != null && !profile.fallbackProfileId.isNullOrBlank()) {
                val fallback = db.apiProfileDao().getProfileById(profile.fallbackProfileId)
                val fbKey = fallback?.let { vault.getSecret(KeystoreSecretVault.apiKeySlot(it.id)) }.orEmpty()
                if (fallback != null && fallback.isEnabled && fallback.modelId.isNotBlank()) {
                    streamError = null
                    textAcc.clear()
                    aiClient.streamChatCompletion(
                        profile = fallback,
                        apiKey = fbKey,
                        modelId = fallback.modelId,
                        messages = turns,
                        reasoningLevel = _uiState.value.settings.reasoningLevel,
                        supportsReasoningParam = fallback.supportsReasoning,
                    ).collect { chunk ->
                        when (chunk) {
                            is StreamChunk.Delta -> {
                                textAcc.append(chunk.text)
                                _uiState.update { it.copy(streamingContent = textAcc.toString()) }
                            }
                            is StreamChunk.Completed -> tokensUsed = chunk.totalTokens
                            is StreamChunk.Error -> streamError = chunk.message
                        }
                    }
                }
            }

            val finalText = textAcc.toString().ifBlank { streamError ?: "No response received." }
            val assistantMsg = ChatMessageEntity(
                id = UUID.randomUUID().toString(),
                sessionId = sessionId,
                role = "ASSISTANT",
                content = finalText,
                reasoningSummary = reasoningAcc.toString().takeIf { it.isNotBlank() }?.take(600),
                modelUsed = modelId,
                tokensUsed = tokensUsed,
                isError = streamError != null,
            )
            db.chatDao().upsertMessage(assistantMsg)
            _uiState.update {
                it.copy(
                    isStreaming = false,
                    streamingContent = "",
                    streamingReasoning = "",
                    errorBanner = streamError,
                )
            }
            if (streamError == null) {
                onCompletedText(finalText)
            }
        }
    }

    // --- DIFF PROPOSALS (ACCEPT / REJECT) ---
    fun acceptProposal(proposalId: String) {
        val pid = _uiState.value.activeProject?.id ?: return
        val proposal = _uiState.value.pendingFileProposals.firstOrNull { it.id == proposalId } ?: return
        viewModelScope.launch {
            createAutoBackup(pid, "Before applying ${proposal.filePath}")
            workspace.writeProjectFile(pid, proposal.filePath, proposal.proposedContent)
            _uiState.update { state ->
                state.copy(
                    pendingFileProposals = state.pendingFileProposals.filterNot { it.id == proposalId },
                    previewRefreshCounter = state.previewRefreshCounter + 1,
                    statusBanner = "Applied changes to ${proposal.filePath}",
                )
            }
            refreshWorkspaceFiles(pid)
            openFileInEditor(proposal.filePath)
        }
    }

    fun rejectProposal(proposalId: String) {
        _uiState.update { state ->
            state.copy(pendingFileProposals = state.pendingFileProposals.filterNot { it.id == proposalId })
        }
    }

    fun acceptAllProposals() {
        val pid = _uiState.value.activeProject?.id ?: return
        val list = _uiState.value.pendingFileProposals
        if (list.isEmpty()) return
        viewModelScope.launch {
            createAutoBackup(pid, "Before applying ${list.size} AI file changes")
            list.forEach { prop ->
                workspace.writeProjectFile(pid, prop.filePath, prop.proposedContent)
            }
            _uiState.update {
                it.copy(
                    pendingFileProposals = emptyList(),
                    previewRefreshCounter = it.previewRefreshCounter + 1,
                    statusBanner = "Applied ${list.size} file change(s).",
                )
            }
            refreshWorkspaceFiles(pid)
        }
    }

    fun requestAiCodeEdit(instruction: String, selectedCode: String) {
        val path = _uiState.value.activeTabPath ?: return
        val fullContent = _uiState.value.openTabs.firstOrNull { it.filePath == path }?.content ?: return
        val prompt = """
            $instruction on file `$path`.
            Selected section:
            ```
            $selectedCode
            ```
            Current file content:
            ```
            ${fullContent.take(6000)}
            ```
            Modify ONLY `$path` and output the complete updated file inside <<<FILE: $path>>> ... <<<END_FILE>>>.
        """.trimIndent()
        submitChatPrompt(prompt, forceDirectCoding = true)
    }

    // --- DIAGNOSTICS & BUG FIXER ---
    fun runProjectDiagnostics() {
        val proj = _uiState.value.activeProject ?: return
        viewModelScope.launch {
            val pType = runCatching { ProjectType.valueOf(proj.projectType) }
                .getOrDefault(ProjectType.HTML_CSS_JS)
            val issues = ProjectDiagnostics.scanWorkspace(
                workspace.getProjectDir(proj.id),
                pType,
                vault.getAllRegisteredSecretsForRedaction(),
            )
            _uiState.update {
                it.copy(
                    diagnostics = issues,
                    statusBanner = if (issues.isEmpty()) "Diagnostics passed: 0 issues detected." else "Detected ${issues.size} issue(s).",
                )
            }
        }
    }

    fun fixAllDetectedIssues() {
        val proj = _uiState.value.activeProject ?: return
        val issues = _uiState.value.diagnostics
        if (issues.isEmpty()) {
            runProjectDiagnostics()
            return
        }
        viewModelScope.launch {
            createAutoBackup(proj.id, "Pre-bugfix backup")
            val issueSummary = issues.joinToString("\n") {
                "- [${it.severity}] ${it.filePath}:${it.line} (${it.category}): ${it.message}"
            }
            submitChatPrompt(
                "FIX ALL DETECTED ISSUES in project '${proj.name}'.\nDetected diagnostics:\n$issueSummary\nProvide minimal verified fixes using <<<FILE: path>>> blocks.",
                forceDirectCoding = true,
            )
        }
    }

    // --- TASKS MANAGEMENT ---
    fun addTask(title: String, description: String, agentRole: AgentRole) {
        val pid = _uiState.value.activeProject?.id ?: "global"
        viewModelScope.launch {
            val nextOrder = _uiState.value.tasks.count { it.projectId == pid }
            db.agentTaskDao().upsertTask(
                AgentTaskEntity(
                    id = UUID.randomUUID().toString(),
                    projectId = pid,
                    title = title.trim(),
                    description = description.trim(),
                    status = TaskStatus.PENDING.name,
                    agentRole = agentRole.name,
                    orderIndex = nextOrder,
                )
            )
        }
    }

    fun updateTaskStatus(task: AgentTaskEntity, newStatus: TaskStatus) {
        viewModelScope.launch {
            db.agentTaskDao().upsertTask(
                task.copy(
                    status = newStatus.name,
                    progress = if (newStatus == TaskStatus.COMPLETED) 1f else task.progress,
                    updatedAt = System.currentTimeMillis(),
                )
            )
        }
    }

    fun runTaskInBackground(task: AgentTaskEntity) {
        viewModelScope.launch {
            db.agentTaskDao().upsertTask(
                task.copy(
                    status = TaskStatus.IN_PROGRESS.name,
                    progress = 0.15f,
                    checkpointNote = "Enqueued in WorkManager",
                    updatedAt = System.currentTimeMillis(),
                )
            )
            BackgroundTaskWorker.enqueueDiagnosticTask(app, task.projectId, task.id)
            _uiState.update { it.copy(statusBanner = "Background WorkManager task started: ${task.title}") }
        }
    }

    fun deleteTask(taskId: String) {
        viewModelScope.launch { db.agentTaskDao().deleteTask(taskId) }
    }

    // --- SKILLS ---
    fun toggleSkill(skill: SkillEntity) = viewModelScope.launch {
        db.skillDao().upsertSkill(skill.copy(isEnabled = !skill.isEnabled))
    }

    fun saveCustomSkill(id: String?, name: String, category: String, description: String, markdown: String) {
        viewModelScope.launch {
            val warnings = SkillCatalog.inspectExternalSkillRisk(markdown)
            db.skillDao().upsertSkill(
                SkillEntity(
                    id = id ?: UUID.randomUUID().toString(),
                    name = name.trim(),
                    description = description.trim(),
                    category = category.trim().ifBlank { "Custom" },
                    markdownContent = markdown,
                    isEnabled = true,
                    isTrusted = warnings.isEmpty(),
                    sourceType = "CUSTOM",
                )
            )
            if (warnings.isNotEmpty()) {
                _uiState.update { it.copy(statusBanner = "Skill saved with security notice: ${warnings.first()}") }
            }
        }
    }

    fun importSkillMarkdownFromUri(uri: Uri) {
        viewModelScope.launch {
            try {
                val name = (resolveUriDisplayName(uri) ?: "ImportedSkill.md").substringBeforeLast('.')
                val content = withContext(Dispatchers.IO) {
                    app.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }.orEmpty()
                }
                val warnings = SkillCatalog.inspectExternalSkillRisk(content)
                db.skillDao().upsertSkill(
                    SkillEntity(
                        id = UUID.randomUUID().toString(),
                        name = name,
                        description = "Imported local Markdown skill (${warnings.size} security flag(s))",
                        category = "Imported",
                        markdownContent = content.take(12_000),
                        isEnabled = warnings.isEmpty(), // Require explicit enable if flagged
                        isTrusted = warnings.isEmpty(),
                        sourceType = "LOCAL_IMPORT",
                    )
                )
                _uiState.update {
                    it.copy(
                        statusBanner = if (warnings.isEmpty()) "Imported skill '$name'." else "Imported '$name' disabled pending review: ${warnings.first()}"
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(errorBanner = "Skill import failed: ${e.message}") }
            }
        }
    }

    fun deleteSkill(skillId: String) = viewModelScope.launch {
        db.skillDao().deleteSkill(skillId)
    }

    // --- TERMINAL ---
    fun addTerminalTab() {
        _uiState.update { state ->
            val nextName = "sh-${state.terminalTabs.size + 1}"
            state.copy(
                terminalTabs = state.terminalTabs + nextName,
                activeTerminalTab = nextName,
                terminalLogs = state.terminalLogs + (nextName to listOf(
                    TerminalLine(System.currentTimeMillis(), "Opened isolated terminal tab [$nextName].")
                )),
            )
        }
    }

    fun selectTerminalTab(tab: String) {
        _uiState.update { it.copy(activeTerminalTab = tab) }
    }

    fun clearActiveTerminal() {
        val tab = _uiState.value.activeTerminalTab
        _uiState.update { state ->
            state.copy(terminalLogs = state.terminalLogs + (tab to emptyList()))
        }
    }

    fun runTerminalCommand(rawCommand: String) {
        val cmd = rawCommand.trim()
        if (cmd.isEmpty()) return
        val tab = _uiState.value.activeTerminalTab
        appendTerminalLine(tab, "$ $cmd", isCommand = true)

        if (cmd == "clear") {
            clearActiveTerminal()
            return
        }
        if (cmd == "help") {
            appendTerminalLine(
                tab,
                "Local workspace commands: ls, pwd, tree, cat <file>, grep <term>, clear\nRemote container commands (requires Cloud Backend URL): gradle, ./gradlew, npm, node, python, pytest, git, flutter",
            )
            return
        }

        val pid = _uiState.value.activeProject?.id
        viewModelScope.launch {
            when {
                cmd == "pwd" -> {
                    appendTerminalLine(tab, "/workspace/${_uiState.value.activeProject?.name ?: "none"}")
                }
                cmd == "ls" || cmd == "tree" -> {
                    if (pid == null) {
                        appendTerminalLine(tab, "No active project selected.", isError = true)
                    } else {
                        val files = workspace.listFiles(pid)
                        val listing = files.joinToString("\n") {
                            val prefix = "  ".repeat(it.depth)
                            if (it.isDirectory) "$prefix[dir] ${it.name}/" else "$prefix${it.name} (${it.sizeBytes}B)"
                        }.ifBlank { "(empty workspace)" }
                        appendTerminalLine(tab, listing)
                    }
                }
                cmd.startsWith("cat ") -> {
                    val path = cmd.removePrefix("cat ").trim()
                    if (pid == null) {
                        appendTerminalLine(tab, "No active project.", isError = true)
                    } else {
                        val (txt, _) = workspace.readFileChunked(pid, path, _uiState.value.settings.lowMemoryMode)
                        appendTerminalLine(tab, txt.ifBlank { "File not found or empty: $path" })
                    }
                }
                cmd.startsWith("grep ") -> {
                    val q = cmd.removePrefix("grep ").trim()
                    if (pid != null) {
                        val hits = workspace.searchCode(pid, q)
                        appendTerminalLine(
                            tab,
                            hits.joinToString("\n") { "${it.first}: ${it.second}" }.ifBlank { "No matches for '$q'." },
                        )
                    }
                }
                else -> {
                    // Dispatch to isolated Remote Container Backend
                    _uiState.update { it.copy(terminalCommandRunning = true) }
                    val url = _uiState.value.settings.remoteBackendUrl
                    val token = vault.getSecret(KeystoreSecretVault.REMOTE_BACKEND_TOKEN_SLOT)
                    val res = remoteClient.executeRemoteCommand(
                        baseUrl = url,
                        authToken = token,
                        projectId = pid ?: "default",
                        workingDir = _uiState.value.terminalWorkingDir,
                        command = cmd,
                    )
                    if (res.stdout.isNotBlank()) appendTerminalLine(tab, res.stdout)
                    if (res.stderr.isNotBlank()) appendTerminalLine(tab, res.stderr, isError = true)
                    appendTerminalLine(tab, "[exit ${res.exitCode}]", isError = !res.success)
                    _uiState.update { it.copy(terminalCommandRunning = false) }
                }
            }
        }
    }

    private fun appendTerminalLine(tab: String, text: String, isError: Boolean = false, isCommand: Boolean = false) {
        _uiState.update { state ->
            val current = state.terminalLogs[tab].orEmpty()
            val updated = (current + TerminalLine(System.nanoTime(), text, isError, isCommand)).takeLast(250)
            state.copy(terminalLogs = state.terminalLogs + (tab to updated))
        }
    }

    fun askAiAboutError(errorText: String) {
        submitChatPrompt(
            "Inspect this error output from my workspace/terminal/build and propose a concrete fix:\n```\n${errorText.take(3000)}\n```",
            forceDirectCoding = true,
        )
    }

    // --- CLOUD APK BUILDER ---
    fun triggerCloudApkBuild(buildType: String) {
        val proj = _uiState.value.activeProject ?: return
        viewModelScope.launch {
            val buildId = UUID.randomUUID().toString()
            val pType = runCatching { ProjectType.valueOf(proj.projectType) }
                .getOrDefault(ProjectType.ANDROID_GRADLE)
            val issues = ProjectDiagnostics.scanWorkspace(
                workspace.getProjectDir(proj.id),
                pType,
                vault.getAllRegisteredSecretsForRedaction(),
            ).filter { it.severity == "ERROR" }

            if (issues.isNotEmpty()) {
                val logStr = "Pre-build validation failed:\n" + issues.joinToString("\n") { "${it.filePath}: ${it.message}" }
                db.buildJobDao().upsertBuild(
                    BuildJobEntity(
                        id = buildId,
                        projectId = proj.id,
                        buildType = buildType,
                        status = "FAILED",
                        logs = logStr,
                        errorCount = issues.size,
                        completedAt = System.currentTimeMillis(),
                    )
                )
                _uiState.update { it.copy(errorBanner = "Project has ${issues.size} validation error(s) before cloud build.") }
                return@launch
            }

            val remoteUrl = _uiState.value.settings.remoteBackendUrl
            val token = vault.getSecret(KeystoreSecretVault.REMOTE_BACKEND_TOKEN_SLOT)
            if (remoteUrl.isBlank()) {
                db.buildJobDao().upsertBuild(
                    BuildJobEntity(
                        id = buildId,
                        projectId = proj.id,
                        buildType = buildType,
                        status = "FAILED",
                        logs = "Remote Cloud Build Backend URL is not configured.\nConfigure your X-THREX build server URL in Settings -> Cloud Build (a deployable Node.js + Docker + Gradle backend is included in /backend).",
                        errorCount = 1,
                        completedAt = System.currentTimeMillis(),
                    )
                )
                _uiState.update { it.copy(errorBanner = "Configure Remote Cloud Build URL in Settings -> Cloud Build.") }
                return@launch
            }

            val tempZip = File(app.cacheDir, "build_${proj.id}.zip")
            try {
                withContext(Dispatchers.IO) {
                    tempZip.outputStream().use { out ->
                        workspace.exportProjectZip(
                            proj.id,
                            proj.name,
                            pType,
                            out,
                            vault.getAllRegisteredSecretsForRedaction(),
                        )
                    }
                }
                val res = remoteClient.submitCloudBuild(remoteUrl, token, proj.id, buildType, tempZip)
                res.fold(
                    onSuccess = { remote ->
                        db.buildJobDao().upsertBuild(
                            BuildJobEntity(
                                id = buildId,
                                projectId = proj.id,
                                buildType = buildType,
                                status = remote.status,
                                logs = remote.logs.ifBlank { "Queued on remote Android build container (job ${remote.jobId})..." },
                                errorCount = remote.errorCount,
                                artifactPath = remote.artifactDownloadPath,
                                remoteJobId = remote.jobId,
                            )
                        )
                        BackgroundTaskWorker.enqueueCloudBuildPoll(
                            app,
                            proj.id,
                            buildId,
                            buildType,
                            remoteUrl,
                            remote.jobId,
                        )
                        _uiState.update { it.copy(statusBanner = "Cloud $buildType job submitted (${remote.jobId}).") }
                    },
                    onFailure = { err ->
                        db.buildJobDao().upsertBuild(
                            BuildJobEntity(
                                id = buildId,
                                projectId = proj.id,
                                buildType = buildType,
                                status = "FAILED",
                                logs = "Submission error: ${err.message}",
                                errorCount = 1,
                                completedAt = System.currentTimeMillis(),
                            )
                        )
                    }
                )
            } finally {
                tempZip.delete()
            }
        }
    }

    fun downloadBuildArtifactToUri(build: BuildJobEntity, destUri: Uri) {
        val artifactPath = build.artifactPath ?: return
        val remoteUrl = _uiState.value.settings.remoteBackendUrl
        val token = vault.getSecret(KeystoreSecretVault.REMOTE_BACKEND_TOKEN_SLOT)
        viewModelScope.launch {
            val tmp = File(app.cacheDir, "artifact_${build.id}.apk")
            try {
                val res = remoteClient.downloadBuildArtifact(remoteUrl, token, artifactPath, tmp)
                res.onSuccess { file ->
                    withContext(Dispatchers.IO) {
                        app.contentResolver.openOutputStream(destUri)?.use { out ->
                            FileInputStream(file).use { it.copyTo(out, 16 * 1024) }
                        }
                    }
                    _uiState.update { it.copy(statusBanner = "Downloaded build artifact successfully.") }
                }.onFailure { err ->
                    _uiState.update { it.copy(errorBanner = "Artifact download failed: ${err.message}") }
                }
            } finally {
                tmp.delete()
            }
        }
    }

    // --- GITHUB ---
    fun connectGitHubToken(pat: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(githubBusy = true) }
            val res = gitHubClient.verifyUser(pat.trim())
            res.onSuccess { login ->
                vault.saveSecret(KeystoreSecretVault.GITHUB_TOKEN_SLOT, pat.trim())
                prefs.setGithubUsername(login)
                loadGitHubRepos()
                _uiState.update { it.copy(statusBanner = "Connected GitHub account @$login") }
            }.onFailure { err ->
                _uiState.update { it.copy(errorBanner = err.message) }
            }
            _uiState.update { it.copy(githubBusy = false) }
        }
    }

    fun disconnectGitHub() {
        viewModelScope.launch {
            vault.deleteSecret(KeystoreSecretVault.GITHUB_TOKEN_SLOT)
            prefs.setGithubUsername("")
            _uiState.update { it.copy(githubRepos = emptyList(), githubCommits = emptyList()) }
        }
    }

    fun loadGitHubRepos() {
        val token = vault.getSecret(KeystoreSecretVault.GITHUB_TOKEN_SLOT) ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(githubBusy = true) }
            gitHubClient.listRepositories(token)
                .onSuccess { repos -> _uiState.update { it.copy(githubRepos = repos) } }
                .onFailure { err -> _uiState.update { it.copy(errorBanner = err.message) } }
            _uiState.update { it.copy(githubBusy = false) }
        }
    }

    fun cloneGitHubRepo(ownerRepo: String, branch: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(githubBusy = true) }
            val token = vault.getSecret(KeystoreSecretVault.GITHUB_TOKEN_SLOT)
            val tmpZip = File(app.cacheDir, "gh_clone_${System.currentTimeMillis()}.zip")
            try {
                val dl = gitHubClient.downloadZipball(token, ownerRepo, branch, tmpZip)
                dl.onSuccess { zip ->
                    val projId = UUID.randomUUID().toString()
                    val analysis = workspace.importZipFile(projId, zip)
                    val repoShort = ownerRepo.substringAfterLast('/')
                    val defaultProf = db.apiProfileDao().getDefaultProfile()
                    db.projectDao().upsertProject(
                        ProjectEntity(
                            id = projId,
                            name = repoShort,
                            projectType = analysis.detectedType.name,
                            rootPath = workspace.getProjectDir(projId).absolutePath,
                            description = "Cloned from GitHub $ownerRepo ($branch)",
                            assignedProfileId = defaultProf?.id,
                            assignedModelId = defaultProf?.modelId,
                            githubRepoFullName = ownerRepo,
                            githubBranch = branch,
                        )
                    )
                    selectProject(projId)
                    _uiState.update { it.copy(statusBanner = "Cloned $ownerRepo (${analysis.fileCount} files).") }
                }.onFailure { err ->
                    _uiState.update { it.copy(errorBanner = err.message) }
                }
            } finally {
                tmpZip.delete()
                _uiState.update { it.copy(githubBusy = false) }
            }
        }
    }

    fun pushActiveFileToGitHub(commitMsg: String) {
        val proj = _uiState.value.activeProject ?: return
        val repo = proj.githubRepoFullName ?: return
        val branch = proj.githubBranch ?: "main"
        val tabPath = _uiState.value.activeTabPath ?: return
        val content = _uiState.value.openTabs.firstOrNull { it.filePath == tabPath }?.content ?: return
        val token = vault.getSecret(KeystoreSecretVault.GITHUB_TOKEN_SLOT) ?: return
        viewModelScope.launch {
            val cleanContent = SecretRedactor.redact(content, vault.getAllRegisteredSecretsForRedaction())
            gitHubClient.pushFileContent(token, repo, tabPath, cleanContent, commitMsg, branch)
                .onSuccess { msg -> _uiState.update { it.copy(statusBanner = msg) } }
                .onFailure { err -> _uiState.update { it.copy(errorBanner = err.message) } }
        }
    }

    fun recordPreviewConsoleLog(msg: String) {
        _uiState.update { state ->
            state.copy(previewConsoleLogs = (state.previewConsoleLogs + msg).takeLast(100))
        }
    }

    fun clearPreviewConsoleLogs() {
        _uiState.update { it.copy(previewConsoleLogs = emptyList()) }
    }

    private suspend fun resolveActiveProfile(): ApiProfileEntity? {
        val projProfileId = _uiState.value.activeProject?.assignedProfileId
        if (!projProfileId.isNullOrBlank()) {
            db.apiProfileDao().getProfileById(projProfileId)?.takeIf { it.isEnabled }?.let { return it }
        }
        return db.apiProfileDao().getDefaultProfile()
            ?: _uiState.value.apiProfiles.firstOrNull { it.isEnabled }
    }

    private suspend fun buildWorkspaceContextSnippet(): String {
        val proj = _uiState.value.activeProject ?: return ""
        val files = workspace.listFiles(proj.id).filter { !it.isDirectory }.take(30)
        val tree = files.joinToString("\n") { "- ${it.relativePath} (${it.sizeBytes}B)" }
        val openSnippet = _uiState.value.openTabs.firstOrNull()?.let { tab ->
            "\nActive Editor File (${tab.filePath}):\n```\n${tab.content.take(2500)}\n```"
        }.orEmpty()
        return "\nProject: ${proj.name} (${proj.projectType})\nWorkspace Files:\n$tree$openSnippet"
    }

    private suspend fun buildActiveSkillsContext(): String {
        val enabled = db.skillDao().getEnabledSkills().filter { it.isTrusted }
        if (enabled.isEmpty()) return ""
        return "\nActive Coding Skills:\n" + enabled.take(4).joinToString("\n") {
            "### ${it.name}\n${it.markdownContent.take(600)}"
        }
    }

    private fun looksLikeBuildOrEditRequest(prompt: String): Boolean {
        val lower = prompt.lowercase()
        val keywords = listOf("build", "create", "add", "implement", "fix", "refactor", "change", "generate", "rebuild", "app", "website", "game")
        return keywords.any { lower.contains(it) }
    }

    private fun inferAgentRole(taskTitle: String): AgentRole {
        val l = taskTitle.lowercase()
        return when {
            l.contains("test") -> AgentRole.TESTING
            l.contains("fix") || l.contains("debug") -> AgentRole.DEBUGGING
            l.contains("ui") || l.contains("css") || l.contains("html") || l.contains("screen") -> AgentRole.FRONTEND
            l.contains("android") || l.contains("gradle") || l.contains("manifest") -> AgentRole.ANDROID
            l.contains("doc") || l.contains("readme") -> AgentRole.DOCUMENTATION
            else -> AgentRole.BACKEND
        }
    }

    private fun resolveUriDisplayName(uri: Uri): String? {
        app.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (idx >= 0 && cursor.moveToFirst()) return cursor.getString(idx)
        }
        return uri.lastPathSegment?.substringAfterLast('/')
    }
}

package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Difference
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.ChatMessageEntity
import com.example.data.model.ReasoningLevel
import com.example.ui.components.CodeBlockCard
import com.example.ui.state.WorkspacePanelTab
import com.example.ui.state.XThrexUiState
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.CoralError
import com.example.ui.theme.EmeraldSuccess
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.NeonCyan

@Composable
fun WorkspaceChatScreen(
    state: XThrexUiState,
    activePanel: WorkspacePanelTab,
    onSelectPanel: (WorkspacePanelTab) -> Unit,
    onSendPrompt: (String) -> Unit,
    onStopGeneration: () -> Unit,
    onRegenerate: () -> Unit,
    onEditAndResubmit: (ChatMessageEntity, String) -> Unit,
    onStartCodingFromPlan: () -> Unit,
    onTogglePlanBeforeCoding: (Boolean) -> Unit,
    onSelectReasoningLevel: (ReasoningLevel) -> Unit,
    onNewSession: () -> Unit,
    onLoadOlderMessages: () -> Unit,
    onSearchChat: (String) -> Unit,
    onAttachFile: () -> Unit,
    onAttachZip: () -> Unit,
    onAttachApk: () -> Unit,
    onAttachScreenshot: () -> Unit,
    onRemoveAttachment: (String) -> Unit,
    onOpenFile: (String) -> Unit,
    onCreateFile: (String) -> Unit,
    onCreateFolder: (String) -> Unit,
    onRenameEntry: (String, String) -> Unit,
    onDuplicateFile: (String) -> Unit,
    onDeleteEntry: (String) -> Unit,
    onExportSingleFile: (String) -> Unit,
    onUpdateEditorContent: (String) -> Unit,
    onSaveEditor: () -> Unit,
    onUndoEditor: () -> Unit,
    onRedoEditor: () -> Unit,
    onCloseTab: (String) -> Unit,
    onChangeEditorFontSize: (Int) -> Unit,
    onToggleEditorTheme: (Boolean) -> Unit,
    onAiCodeEdit: (instruction: String, selectedCode: String) -> Unit,
    onOpenDiffDialog: () -> Unit,
    onOpenCloudBuild: () -> Unit,
    onRunDiagnostics: () -> Unit,
    onExportProjectZip: () -> Unit,
    onRunTerminalCmd: (String) -> Unit,
    onAddTerminalTab: () -> Unit,
    onSelectTerminalTab: (String) -> Unit,
    onClearTerminal: () -> Unit,
    onAskAiError: (String) -> Unit,
    onConsoleLog: (String) -> Unit,
    onClearConsole: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        // Workspace Header Bar
        Surface(
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 2.dp,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = state.activeProject?.name ?: "AI Coding Studio",
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        val activeProf = state.apiProfiles.firstOrNull {
                            it.id == state.activeProject?.assignedProfileId
                        } ?: state.apiProfiles.firstOrNull { it.isDefault }
                        Text(
                            text = activeProf?.let { "${it.customName} • ${it.modelId.ifBlank { "no model" }}" }
                                ?: "No API connected",
                            style = MaterialTheme.typography.labelSmall,
                            color = NeonCyan,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (state.pendingFileProposals.isNotEmpty()) {
                            IconButton(onClick = onOpenDiffDialog) {
                                BadgedBox(badge = { Badge { Text("${state.pendingFileProposals.size}") } }) {
                                    Icon(Icons.Default.Difference, contentDescription = "Review AI diffs", tint = AmberWarning)
                                }
                            }
                        }
                        IconButton(onClick = onRunDiagnostics) {
                            Icon(Icons.Default.BugReport, contentDescription = "Scan bugs")
                        }
                        IconButton(onClick = onOpenCloudBuild) {
                            Icon(Icons.Default.Build, contentDescription = "Cloud APK Builder")
                        }
                        IconButton(onClick = onExportProjectZip) {
                            Icon(Icons.Default.Archive, contentDescription = "Download project ZIP")
                        }
                    }
                }

                TabRow(selectedTabIndex = WorkspacePanelTab.entries.indexOf(activePanel)) {
                    WorkspacePanelTab.entries.forEach { tab ->
                        Tab(
                            selected = activePanel == tab,
                            onClick = { onSelectPanel(tab) },
                            text = { Text(tab.label, fontSize = 11.sp, maxLines = 1) },
                        )
                    }
                }
            }
        }

        when (activePanel) {
            WorkspacePanelTab.CHAT -> ChatPanel(
                state = state,
                onSendPrompt = onSendPrompt,
                onStopGeneration = onStopGeneration,
                onRegenerate = onRegenerate,
                onEditAndResubmit = onEditAndResubmit,
                onStartCodingFromPlan = onStartCodingFromPlan,
                onTogglePlanBeforeCoding = onTogglePlanBeforeCoding,
                onSelectReasoningLevel = onSelectReasoningLevel,
                onNewSession = onNewSession,
                onLoadOlderMessages = onLoadOlderMessages,
                onSearchChat = onSearchChat,
                onAttachFile = onAttachFile,
                onAttachZip = onAttachZip,
                onAttachApk = onAttachApk,
                onAttachScreenshot = onAttachScreenshot,
                onRemoveAttachment = onRemoveAttachment,
                onOpenDiffDialog = onOpenDiffDialog,
            )

            WorkspacePanelTab.FILES -> FileExplorerPanel(
                state = state,
                onOpenFile = { path ->
                    onOpenFile(path)
                    onSelectPanel(WorkspacePanelTab.EDITOR)
                },
                onCreateFile = onCreateFile,
                onCreateFolder = onCreateFolder,
                onRenameEntry = onRenameEntry,
                onDuplicateFile = onDuplicateFile,
                onDeleteEntry = onDeleteEntry,
                onExportSingleFile = onExportSingleFile,
            )

            WorkspacePanelTab.EDITOR -> CodeEditorPanel(
                state = state,
                onSelectTab = onOpenFile,
                onCloseTab = onCloseTab,
                onUpdateContent = onUpdateEditorContent,
                onSave = onSaveEditor,
                onUndo = onUndoEditor,
                onRedo = onRedoEditor,
                onChangeFontSize = onChangeEditorFontSize,
                onToggleEditorTheme = onToggleEditorTheme,
                onAiCodeEdit = { inst, snippet ->
                    onAiCodeEdit(inst, snippet)
                    onSelectPanel(WorkspacePanelTab.CHAT)
                },
            )

            WorkspacePanelTab.PREVIEW -> SandboxedWebPreviewPanel(
                project = state.activeProject,
                workspaceRootPath = state.activeProject?.rootPath,
                refreshCounter = state.previewRefreshCounter,
                consoleLogs = state.previewConsoleLogs,
                onConsoleLog = onConsoleLog,
                onClearConsole = onClearConsole,
                onAskAiError = { err ->
                    onAskAiError(err)
                    onSelectPanel(WorkspacePanelTab.CHAT)
                },
                onOpenCloudBuild = onOpenCloudBuild,
                onRunDiagnostics = onRunDiagnostics,
            )

            WorkspacePanelTab.TERMINAL -> WorkspaceTerminalPanel(
                state = state,
                onRunCommand = onRunTerminalCmd,
                onAddTab = onAddTerminalTab,
                onSelectTab = onSelectTerminalTab,
                onClear = onClearTerminal,
                onAskAiAboutError = { err ->
                    onAskAiError(err)
                    onSelectPanel(WorkspacePanelTab.CHAT)
                },
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChatPanel(
    state: XThrexUiState,
    onSendPrompt: (String) -> Unit,
    onStopGeneration: () -> Unit,
    onRegenerate: () -> Unit,
    onEditAndResubmit: (ChatMessageEntity, String) -> Unit,
    onStartCodingFromPlan: () -> Unit,
    onTogglePlanBeforeCoding: (Boolean) -> Unit,
    onSelectReasoningLevel: (ReasoningLevel) -> Unit,
    onNewSession: () -> Unit,
    onLoadOlderMessages: () -> Unit,
    onSearchChat: (String) -> Unit,
    onAttachFile: () -> Unit,
    onAttachZip: () -> Unit,
    onAttachApk: () -> Unit,
    onAttachScreenshot: () -> Unit,
    onRemoveAttachment: (String) -> Unit,
    onOpenDiffDialog: () -> Unit,
) {
    var input by remember { mutableStateOf("") }
    var showSearchBar by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var editingMessage by remember { mutableStateOf<ChatMessageEntity?>(null) }
    var editText by remember { mutableStateOf("") }
    val clipboard = LocalClipboardManager.current

    Column(modifier = Modifier.fillMaxSize()) {
        // Reasoning & Agent Status Controls
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        modifier = Modifier.weight(1f).horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        ReasoningLevel.entries.forEach { lvl ->
                            FilterChip(
                                selected = state.settings.reasoningLevel == lvl,
                                onClick = { onSelectReasoningLevel(lvl) },
                                label = { Text(lvl.label, fontSize = 10.sp) },
                            )
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Plan", fontSize = 11.sp)
                        Switch(checked = state.planBeforeCoding, onCheckedChange = onTogglePlanBeforeCoding)
                        IconButton(onClick = { showSearchBar = !showSearchBar }) {
                            Icon(Icons.Default.Search, contentDescription = "Search chat history")
                        }
                        IconButton(onClick = onNewSession) {
                            Icon(Icons.Default.Add, contentDescription = "New conversation")
                        }
                    }
                }
                if (state.activeAgentsStatus.isNotEmpty()) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    Text(
                        text = state.activeAgentsStatus.entries.joinToString(" | ") { "${it.key.label}: ${it.value}" },
                        style = MaterialTheme.typography.labelSmall,
                        color = NeonCyan,
                    )
                }
            }
        }

        if (showSearchBar) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it; onSearchChat(it) },
                    label = { Text("Search conversation history") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
            }
            if (state.chatSearchResults.isNotEmpty()) {
                Surface(color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        Text("Found ${state.chatSearchResults.size} matches:", style = MaterialTheme.typography.labelSmall)
                        state.chatSearchResults.take(4).forEach { m ->
                            Text("- ${m.content.take(90)}", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }

        // Message List
        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (state.chatMessages.size >= state.chatPageSize) {
                item {
                    TextButton(onClick = onLoadOlderMessages, modifier = Modifier.fillMaxWidth()) {
                        Text("Load Older Messages")
                    }
                }
            }

            if (state.chatMessages.isEmpty() && !state.isStreaming) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("X-THREX Coding Agent Ready", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "Ask me to architect a new app, edit existing workspace files, analyze an uploaded APK, or diagnose errors. Planning mode creates a 10-point blueprint before editing files.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            items(state.chatMessages, key = { it.id }) { msg ->
                ChatMessageCard(
                    msg = msg,
                    onCopy = { clipboard.setText(AnnotatedString(msg.content)) },
                    onEdit = { editingMessage = msg; editText = msg.content },
                )
            }

            // Streaming live turn
            if (state.isStreaming) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        modifier = Modifier.fillMaxWidth().border(1.dp, NeonCyan.copy(alpha = 0.4f), RoundedCornerShape(14.dp)),
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("X-THREX Agent (Streaming...)", style = MaterialTheme.typography.labelSmall, color = NeonCyan)
                            if (state.streamingReasoning.isNotBlank()) {
                                Text(
                                    "Reasoning summary: ${state.streamingReasoning.takeLast(280)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Text(
                                state.streamingContent.ifBlank { "Waiting for first token..." },
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                }
            }

            // 10-Part Planning Interface Card
            state.pendingPlan?.let { plan ->
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        modifier = Modifier.fillMaxWidth().border(1.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(16.dp)),
                    ) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("AI Implementation Plan (10-Point Blueprint)", style = MaterialTheme.typography.titleMedium, color = NeonCyan)
                            if (plan.conciseReasoningSummary.isNotBlank()) {
                                Text("Summary: ${plan.conciseReasoningSummary}", style = MaterialTheme.typography.bodySmall, color = EmeraldSuccess)
                            }
                            PlanSection("1. Request Analysis", plan.userRequestAnalysis)
                            PlanSection("2. Project Understanding", plan.projectUnderstanding)
                            PlanSection("3. Requirements", plan.requirements.joinToString("\n• ", prefix = "• "))
                            PlanSection("4. Proposed Architecture", plan.proposedArchitecture)
                            PlanSection("5. Files to Create", plan.filesToCreate.joinToString(", "))
                            PlanSection("6. Files to Modify", plan.filesToModify.joinToString(", "))
                            PlanSection("7. Dependencies", plan.dependencies.joinToString(", "))
                            PlanSection("8. Implementation Tasks", plan.implementationTasks.joinToString("\n• ", prefix = "• "))
                            PlanSection("9. Testing Strategy", plan.testingStrategy)
                            PlanSection("10. Expected Final Output", plan.expectedFinalOutput)

                            Button(
                                onClick = onStartCodingFromPlan,
                                modifier = Modifier.fillMaxWidth().testTag("start_coding_btn"),
                            ) {
                                Icon(Icons.Default.PlayArrow, contentDescription = null)
                                Spacer(Modifier.width(6.dp))
                                Text("Start Coding")
                            }
                        }
                    }
                }
            }

            if (state.pendingFileProposals.isNotEmpty()) {
                item {
                    Button(onClick = onOpenDiffDialog, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.Difference, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("Review ${state.pendingFileProposals.size} Proposed File Change(s)")
                    }
                }
            }
        }

        // Pending Attachments Bar
        if (state.pendingAttachments.isNotEmpty()) {
            FlowRow(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                state.pendingAttachments.forEach { att ->
                    AssistChip(
                        onClick = { onRemoveAttachment(att.id) },
                        label = { Text("${att.kind}: ${att.name}", fontSize = 10.sp) },
                        trailingIcon = { Icon(Icons.Default.Close, contentDescription = "Remove attachment", modifier = Modifier.size(14.dp)) },
                    )
                }
            }
        }

        // Composer Bar
        Surface(color = MaterialTheme.colorScheme.surface, tonalElevation = 4.dp) {
            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    AssistChip(onClick = onAttachFile, label = { Text("File", fontSize = 11.sp) }, leadingIcon = { Icon(Icons.Default.AttachFile, null, Modifier.size(15.dp)) })
                    AssistChip(onClick = onAttachZip, label = { Text("ZIP", fontSize = 11.sp) }, leadingIcon = { Icon(Icons.Default.Archive, null, Modifier.size(15.dp)) })
                    AssistChip(onClick = onAttachApk, label = { Text("APK", fontSize = 11.sp) }, leadingIcon = { Icon(Icons.Default.InsertDriveFile, null, Modifier.size(15.dp)) })
                    AssistChip(onClick = onAttachScreenshot, label = { Text("Screenshot", fontSize = 11.sp) }, leadingIcon = { Icon(Icons.Default.Image, null, Modifier.size(15.dp)) })
                    if (!state.isStreaming && state.chatMessages.any { it.role == "USER" }) {
                        AssistChip(onClick = onRegenerate, label = { Text("Regenerate", fontSize = 11.sp) }, leadingIcon = { Icon(Icons.Default.Refresh, null, Modifier.size(15.dp)) })
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedTextField(
                        value = input,
                        onValueChange = { input = it },
                        placeholder = { Text("Describe what to build, fix, or refactor...") },
                        maxLines = 4,
                        modifier = Modifier.weight(1f).testTag("chat_prompt_input"),
                    )
                    if (state.isStreaming) {
                        IconButton(onClick = onStopGeneration, modifier = Modifier.testTag("stop_generation_btn")) {
                            Icon(Icons.Default.Stop, contentDescription = "Stop generation", tint = CoralError)
                        }
                    } else {
                        IconButton(
                            enabled = input.isNotBlank() || state.pendingAttachments.isNotEmpty(),
                            onClick = {
                                onSendPrompt(input)
                                input = ""
                            },
                            modifier = Modifier.testTag("send_prompt_btn"),
                        ) {
                            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send prompt", tint = NeonCyan)
                        }
                    }
                }
            }
        }
    }

    editingMessage?.let { msg ->
        AlertDialog(
            onDismissRequest = { editingMessage = null },
            title = { Text("Edit Previous Request") },
            text = {
                OutlinedTextField(value = editText, onValueChange = { editText = it }, minLines = 3, modifier = Modifier.fillMaxWidth())
            },
            confirmButton = {
                Button(onClick = { onEditAndResubmit(msg, editText); editingMessage = null }) { Text("Resubmit") }
            },
            dismissButton = {
                TextButton(onClick = { editingMessage = null }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun PlanSection(title: String, body: String) {
    if (body.isBlank() || body == "• ") return
    Column {
        Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        Text(body, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun ChatMessageCard(
    msg: ChatMessageEntity,
    onCopy: () -> Unit,
    onEdit: () -> Unit,
) {
    val isUser = msg.role == "USER"
    val parts = remember(msg.content) { splitMarkdownCodeBlocks(msg.content) }

    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (isUser) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = if (isUser) "You" else "X-THREX (${msg.modelUsed ?: "Agent"})",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isUser) MaterialTheme.colorScheme.onPrimaryContainer else NeonCyan,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (msg.tokensUsed > 0) {
                        Text("${msg.tokensUsed} tok", style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(end = 6.dp))
                    }
                    if (isUser) {
                        IconButton(onClick = onEdit, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Default.Edit, contentDescription = "Edit request", modifier = Modifier.size(16.dp))
                        }
                    }
                    IconButton(onClick = onCopy, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.ContentCopy, contentDescription = "Copy message", modifier = Modifier.size(16.dp))
                    }
                }
            }
            parts.forEach { (isCode, lang, text) ->
                if (isCode) {
                    CodeBlockCard(code = text, languageLabel = lang)
                } else if (text.isNotBlank()) {
                    Text(text = text, style = MaterialTheme.typography.bodyMedium, color = if (msg.isError) CoralError else MaterialTheme.colorScheme.onSurface)
                }
            }
        }
    }
}

private fun splitMarkdownCodeBlocks(raw: String): List<Triple<Boolean, String, String>> {
    val regex = Regex("""```([A-Za-z0-9_+-]*)\r?\n([\s\S]*?)```""")
    val out = mutableListOf<Triple<Boolean, String, String>>()
    var lastEnd = 0
    regex.findAll(raw).forEach { match ->
        if (match.range.first > lastEnd) {
            out.add(Triple(false, "", raw.substring(lastEnd, match.range.first).trim()))
        }
        out.add(Triple(true, match.groupValues[1].ifBlank { "code" }, match.groupValues[2].trimEnd()))
        lastEnd = match.range.last + 1
    }
    if (lastEnd < raw.length) {
        out.add(Triple(false, "", raw.substring(lastEnd).trim()))
    }
    if (out.isEmpty()) out.add(Triple(false, "", raw))
    return out
}

@Composable
private fun FileExplorerPanel(
    state: XThrexUiState,
    onOpenFile: (String) -> Unit,
    onCreateFile: (String) -> Unit,
    onCreateFolder: (String) -> Unit,
    onRenameEntry: (String, String) -> Unit,
    onDuplicateFile: (String) -> Unit,
    onDeleteEntry: (String) -> Unit,
    onExportSingleFile: (String) -> Unit,
) {
    var search by remember { mutableStateOf("") }
    var newFilePath by remember { mutableStateOf("") }
    var showCreateFile by remember { mutableStateOf(false) }
    var showCreateFolder by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<String?>(null) }
    var renameValue by remember { mutableStateOf("") }
    var deleteTarget by remember { mutableStateOf<String?>(null) }

    val filtered = remember(state.workspaceFiles, search) {
        if (search.isBlank()) state.workspaceFiles
        else state.workspaceFiles.filter { it.relativePath.contains(search, ignoreCase = true) }
    }

    Column(modifier = Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = search,
                onValueChange = { search = it },
                label = { Text("Search files") },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = { newFilePath = ""; showCreateFile = true }) {
                Icon(Icons.Default.Add, contentDescription = "New file", tint = NeonCyan)
            }
            IconButton(onClick = { newFilePath = ""; showCreateFolder = true }) {
                Icon(Icons.Default.CreateNewFolder, contentDescription = "New folder")
            }
        }

        LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            items(filtered, key = { it.relativePath }) { node ->
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = !node.isDirectory) { onOpenFile(node.relativePath) },
                ) {
                    Row(
                        modifier = Modifier.padding(start = (10 + node.depth * 14).dp, end = 6.dp, top = 8.dp, bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = if (node.isDirectory) Icons.Default.Folder else Icons.Default.InsertDriveFile,
                            contentDescription = null,
                            tint = if (node.isDirectory) AmberWarning else NeonCyan,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(node.name, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            if (!node.isDirectory) {
                                Text("${node.sizeBytes} bytes", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        if (!node.isDirectory) {
                            IconButton(onClick = { onDuplicateFile(node.relativePath) }, modifier = Modifier.size(34.dp)) {
                                Icon(Icons.Default.ContentCopy, contentDescription = "Duplicate", modifier = Modifier.size(16.dp))
                            }
                            IconButton(onClick = { onExportSingleFile(node.relativePath) }, modifier = Modifier.size(34.dp)) {
                                Icon(Icons.Default.Save, contentDescription = "Export file", modifier = Modifier.size(16.dp))
                            }
                        }
                        IconButton(onClick = { renameTarget = node.relativePath; renameValue = node.relativePath }, modifier = Modifier.size(34.dp)) {
                            Icon(Icons.Default.Edit, contentDescription = "Rename", modifier = Modifier.size(16.dp))
                        }
                        IconButton(onClick = { deleteTarget = node.relativePath }, modifier = Modifier.size(34.dp)) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete", tint = CoralError, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
        }
    }

    if (showCreateFile || showCreateFolder) {
        AlertDialog(
            onDismissRequest = { showCreateFile = false; showCreateFolder = false },
            title = { Text(if (showCreateFile) "Create File" else "Create Folder") },
            text = {
                OutlinedTextField(
                    value = newFilePath,
                    onValueChange = { newFilePath = it },
                    label = { Text("Relative path (e.g. src/utils.kt)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                Button(onClick = {
                    if (newFilePath.isNotBlank()) {
                        if (showCreateFile) onCreateFile(newFilePath) else onCreateFolder(newFilePath)
                    }
                    showCreateFile = false
                    showCreateFolder = false
                }) { Text("Create") }
            },
            dismissButton = { TextButton(onClick = { showCreateFile = false; showCreateFolder = false }) { Text("Cancel") } },
        )
    }

    renameTarget?.let { oldPath ->
        AlertDialog(
            onDismissRequest = { renameTarget = null },
            title = { Text("Rename / Move") },
            text = { OutlinedTextField(value = renameValue, onValueChange = { renameValue = it }, singleLine = true, modifier = Modifier.fillMaxWidth()) },
            confirmButton = { Button(onClick = { onRenameEntry(oldPath, renameValue); renameTarget = null }) { Text("Apply") } },
            dismissButton = { TextButton(onClick = { renameTarget = null }) { Text("Cancel") } },
        )
    }

    deleteTarget?.let { path ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("Confirm Delete") },
            text = { Text("Delete '$path'? An automatic snapshot backup is created first.") },
            confirmButton = { Button(onClick = { onDeleteEntry(path); deleteTarget = null }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun CodeEditorPanel(
    state: XThrexUiState,
    onSelectTab: (String) -> Unit,
    onCloseTab: (String) -> Unit,
    onUpdateContent: (String) -> Unit,
    onSave: () -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onChangeFontSize: (Int) -> Unit,
    onToggleEditorTheme: (Boolean) -> Unit,
    onAiCodeEdit: (String, String) -> Unit,
) {
    val activeTab = state.openTabs.firstOrNull { it.filePath == state.activeTabPath }
    var showFindReplace by remember { mutableStateOf(false) }
    var findText by remember { mutableStateOf("") }
    var replaceText by remember { mutableStateOf("") }

    if (activeTab == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Open any file from the FILES tab to edit code.", style = MaterialTheme.typography.bodyMedium)
        }
        return
    }

    val editorBg = if (state.settings.editorLightTheme) Color(0xFFF8FAFC) else Color(0xFF070B14)
    val editorFg = if (state.settings.editorLightTheme) Color(0xFF0F172A) else Color(0xFFE2E8F0)

    Column(modifier = Modifier.fillMaxSize()) {
        // Editor Tabs
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 6.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            state.openTabs.forEach { tab ->
                val isAct = tab.filePath == activeTab.filePath
                Surface(
                    color = if (isAct) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(8.dp),
                    onClick = { onSelectTab(tab.filePath) },
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            text = if (tab.isDirty) "* ${tab.fileName}" else tab.fileName,
                            fontFamily = JetBrainsMonoFamily,
                            fontSize = 11.sp,
                        )
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Close tab",
                            modifier = Modifier.size(14.dp).clickable { onCloseTab(tab.filePath) },
                        )
                    }
                }
            }
        }

        // Toolbar: Save, Undo, Redo, Find/Replace, Font Size, Light/Dark
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onSave) { Icon(Icons.Default.Save, contentDescription = "Save file", tint = EmeraldSuccess) }
            IconButton(onClick = onUndo) { Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = "Undo") }
            IconButton(onClick = onRedo) { Icon(Icons.AutoMirrored.Filled.Redo, contentDescription = "Redo") }
            IconButton(onClick = { showFindReplace = !showFindReplace }) { Icon(Icons.Default.Search, contentDescription = "Find & replace") }
            TextButton(onClick = { onChangeFontSize(state.settings.editorFontSizeSp - 1) }) { Text("A-") }
            Text("${state.settings.editorFontSizeSp}sp", style = MaterialTheme.typography.labelSmall)
            TextButton(onClick = { onChangeFontSize(state.settings.editorFontSizeSp + 1) }) { Text("A+") }
            TextButton(onClick = { onToggleEditorTheme(!state.settings.editorLightTheme) }) {
                Text(if (state.settings.editorLightTheme) "Dark" else "Light")
            }
        }

        if (showFindReplace) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(value = findText, onValueChange = { findText = it }, label = { Text("Find") }, singleLine = true, modifier = Modifier.weight(1f))
                OutlinedTextField(value = replaceText, onValueChange = { replaceText = it }, label = { Text("Replace") }, singleLine = true, modifier = Modifier.weight(1f))
                Button(
                    enabled = findText.isNotEmpty(),
                    onClick = { onUpdateContent(activeTab.content.replace(findText, replaceText)) },
                ) { Text("All") }
            }
        }

        if (activeTab.isTruncatedForLowRam) {
            Text(
                "Low-RAM chunked view (${activeTab.totalFileBytes / 1024}KB total). Showing initial segment.",
                style = MaterialTheme.typography.labelSmall,
                color = AmberWarning,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 2.dp),
            )
        }

        // Line-numbered Code Editor
        val lineCount = remember(activeTab.content) { activeTab.content.count { it == '\n' } + 1 }
        Row(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(editorBg)
                .verticalScroll(rememberScrollState())
                .padding(8.dp),
        ) {
            Column(modifier = Modifier.padding(end = 10.dp)) {
                for (i in 1..lineCount) {
                    Text(
                        text = i.toString(),
                        fontFamily = JetBrainsMonoFamily,
                        fontSize = state.settings.editorFontSizeSp.sp,
                        lineHeight = (state.settings.editorFontSizeSp + 6).sp,
                        color = Color(0xFF64748B),
                    )
                }
            }
            BasicTextField(
                value = activeTab.content,
                onValueChange = onUpdateContent,
                textStyle = TextStyle(
                    fontFamily = JetBrainsMonoFamily,
                    fontSize = state.settings.editorFontSizeSp.sp,
                    lineHeight = (state.settings.editorFontSizeSp + 6).sp,
                    color = editorFg,
                ),
                cursorBrush = SolidColor(NeonCyan),
                modifier = Modifier
                    .weight(1f)
                    .horizontalScroll(rememberScrollState()),
            )
        }

        HorizontalDivider()
        // AI Code Editing Actions Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            listOf(
                "Fix this code",
                "Improve this function",
                "Change the design",
                "Add a new feature",
                "Explain this section",
                "Optimize this file",
            ).forEach { aiAction ->
                AssistChip(
                    onClick = { onAiCodeEdit(aiAction, activeTab.content.take(2000)) },
                    label = { Text(aiAction, fontSize = 11.sp) },
                )
            }
        }
    }
}

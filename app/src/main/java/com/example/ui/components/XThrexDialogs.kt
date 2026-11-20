package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.data.local.ApiProfileEntity
import com.example.data.local.BuildJobEntity
import com.example.data.local.DiscoveredModelEntity
import com.example.data.model.ApkAnalysisReport
import com.example.data.model.FileChangeProposal
import com.example.data.model.ModelCategory
import com.example.data.model.ProjectType
import com.example.data.model.ProviderFormat
import com.example.engine.DiffEngine
import com.example.engine.DiffLineKind
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.CoralError
import com.example.ui.theme.EmeraldSuccess
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.NeonCyan

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NewProjectDialog(
    onDismiss: () -> Unit,
    onCreate: (name: String, type: ProjectType, description: String) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var selectedType by remember { mutableStateOf(ProjectType.HTML_CSS_JS) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Create New Project", style = MaterialTheme.typography.titleLarge) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Project Name") },
                    placeholder = { Text("e.g. FinTrackApp") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("new_project_name_input"),
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Project Goal / Description") },
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text("Project Type", style = MaterialTheme.typography.titleSmall)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    ProjectType.entries.forEach { type ->
                        FilterChip(
                            selected = selectedType == type,
                            onClick = { selectedType = type },
                            label = { Text(type.label, fontSize = 11.sp) },
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onCreate(name.ifBlank { "NewProject" }, selectedType, description) },
                modifier = Modifier.testTag("confirm_create_project_btn"),
            ) {
                Text("Create")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ApiProfileEditorDialog(
    editingProfile: ApiProfileEntity?,
    allProfiles: List<ApiProfileEntity>,
    discoveredModels: List<DiscoveredModelEntity>,
    connectionBusy: Boolean,
    connectionFeedback: String?,
    onDismiss: () -> Unit,
    onSave: (
        id: String?,
        providerName: String,
        customName: String,
        format: ProviderFormat,
        baseUrl: String,
        apiKey: String,
        modelId: String,
        category: ModelCategory,
        customHeadersJson: String,
        fallbackId: String?,
        isDefault: Boolean,
    ) -> Unit,
    onTestAndDiscover: (ApiProfileEntity, String) -> Unit,
    onAddManualModel: (profileId: String, modelId: String, category: ModelCategory, supportsReasoning: Boolean) -> Unit,
) {
    val initFormat = editingProfile?.let {
        runCatching { ProviderFormat.valueOf(it.providerFormat) }.getOrNull()
    } ?: ProviderFormat.OPENAI_COMPATIBLE

    var providerName by remember { mutableStateOf(editingProfile?.providerName ?: "OpenAI") }
    var customName by remember { mutableStateOf(editingProfile?.customName ?: "") }
    var format by remember { mutableStateOf(initFormat) }
    var baseUrl by remember { mutableStateOf(editingProfile?.baseUrl ?: initFormat.defaultBaseUrl) }
    var apiKey by remember { mutableStateOf("") }
    var modelId by remember { mutableStateOf(editingProfile?.modelId ?: "") }
    var category by remember {
        mutableStateOf(
            editingProfile?.let { runCatching { ModelCategory.valueOf(it.modelCategory) }.getOrNull() }
                ?: ModelCategory.CODING
        )
    }
    var customHeaders by remember { mutableStateOf(editingProfile?.customHeadersJson ?: "{}") }
    var fallbackId by remember { mutableStateOf(editingProfile?.fallbackProfileId) }
    var setAsDefault by remember { mutableStateOf(editingProfile?.isDefault ?: false) }
    var manualSupportsReasoning by remember { mutableStateOf(editingProfile?.supportsReasoning ?: false) }

    val profileModels = remember(discoveredModels, editingProfile?.id) {
        if (editingProfile == null) emptyList() else discoveredModels.filter { it.profileId == editingProfile.id }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        if (editingProfile == null) "Connect AI API Profile" else "Edit API Profile",
                        style = MaterialTheme.typography.titleLarge,
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }

                Text("Provider Format", style = MaterialTheme.typography.titleSmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    ProviderFormat.entries.forEach { pf ->
                        FilterChip(
                            selected = format == pf,
                            onClick = {
                                format = pf
                                if (editingProfile == null || baseUrl.isBlank()) {
                                    baseUrl = pf.defaultBaseUrl
                                    providerName = pf.label
                                }
                            },
                            label = { Text(pf.label, fontSize = 11.sp) },
                        )
                    }
                }

                OutlinedTextField(
                    value = providerName,
                    onValueChange = { providerName = it },
                    label = { Text("Provider Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = customName,
                    onValueChange = { customName = it },
                    label = { Text("Custom Profile Label") },
                    placeholder = { Text("e.g. Work Coding Endpoint") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = baseUrl,
                    onValueChange = { baseUrl = it },
                    label = { Text("Base URL") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("api_base_url_input"),
                )
                OutlinedTextField(
                    value = apiKey,
                    onValueChange = { apiKey = it },
                    label = {
                        Text(
                            if (editingProfile == null) "API Key (Encrypted in Keystore)"
                            else "Replace API Key (leave blank to keep existing)"
                        )
                    },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("api_key_input"),
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedButton(
                        enabled = !connectionBusy && baseUrl.isNotBlank(),
                        onClick = {
                            val probe = ApiProfileEntity(
                                id = editingProfile?.id ?: "probe_temp",
                                providerName = providerName,
                                customName = customName.ifBlank { providerName },
                                providerFormat = format.name,
                                baseUrl = baseUrl,
                                modelId = modelId,
                                modelCategory = category.name,
                                customHeadersJson = customHeaders,
                            )
                            onTestAndDiscover(probe, apiKey)
                        },
                        modifier = Modifier.testTag("test_connection_btn"),
                    ) {
                        if (connectionBusy) {
                            CircularProgressIndicator(modifier = Modifier.height(16.dp).width(16.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(6.dp))
                        }
                        Text("Test & Discover Models")
                    }
                }

                if (!connectionFeedback.isNullOrBlank()) {
                    Text(
                        text = connectionFeedback,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (connectionFeedback.startsWith("Connected")) EmeraldSuccess else AmberWarning,
                    )
                }

                if (profileModels.isNotEmpty()) {
                    Text("Discovered Models (${profileModels.size})", style = MaterialTheme.typography.titleSmall)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        profileModels.take(18).forEach { m ->
                            FilterChip(
                                selected = modelId == m.modelId,
                                onClick = {
                                    modelId = m.modelId
                                    category = runCatching { ModelCategory.valueOf(m.category) }
                                        .getOrDefault(ModelCategory.GENERAL)
                                },
                                label = { Text("${m.modelId} • ${m.category}", fontSize = 10.sp) },
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = modelId,
                    onValueChange = { modelId = it },
                    label = { Text("Model ID (Discovered or Manual)") },
                    placeholder = { Text("e.g. gpt-4.1, claude-sonnet-4-5, gemini-2.5-pro") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("api_model_id_input"),
                )

                Text("Verified / User Model Category", style = MaterialTheme.typography.titleSmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    ModelCategory.entries.forEach { cat ->
                        FilterChip(
                            selected = category == cat,
                            onClick = { category = cat },
                            label = { Text(cat.label, fontSize = 11.sp) },
                        )
                    }
                }

                if (editingProfile != null && modelId.isNotBlank()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = manualSupportsReasoning, onCheckedChange = { manualSupportsReasoning = it })
                        Text("Model accepts reasoning_effort / thinking API param", style = MaterialTheme.typography.bodySmall)
                    }
                    OutlinedButton(
                        onClick = { onAddManualModel(editingProfile.id, modelId, category, manualSupportsReasoning) },
                    ) {
                        Text("Pin Manual Model to Profile")
                    }
                }

                OutlinedTextField(
                    value = customHeaders,
                    onValueChange = { customHeaders = it },
                    label = { Text("Custom Headers JSON (optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                if (allProfiles.any { it.id != editingProfile?.id }) {
                    Text("Optional Fallback Profile", style = MaterialTheme.typography.titleSmall)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FilterChip(
                            selected = fallbackId == null,
                            onClick = { fallbackId = null },
                            label = { Text("None", fontSize = 11.sp) },
                        )
                        allProfiles.filter { it.id != editingProfile?.id }.forEach { other ->
                            FilterChip(
                                selected = fallbackId == other.id,
                                onClick = { fallbackId = other.id },
                                label = { Text(other.customName, fontSize = 11.sp) },
                            )
                        }
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = setAsDefault, onCheckedChange = { setAsDefault = it })
                    Text("Set as Default Profile", style = MaterialTheme.typography.bodyMedium)
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = {
                            onSave(
                                editingProfile?.id,
                                providerName,
                                customName,
                                format,
                                baseUrl,
                                apiKey,
                                modelId,
                                category,
                                customHeaders,
                                fallbackId,
                                setAsDefault,
                            )
                            onDismiss()
                        },
                        modifier = Modifier.testTag("save_api_profile_btn"),
                    ) {
                        Text("Save Profile")
                    }
                }
            }
        }
    }
}

@Composable
fun AppRecreatorDialog(
    lastApkReport: ApkAnalysisReport?,
    onDismiss: () -> Unit,
    onPickSourceZip: () -> Unit,
    onPickApk: () -> Unit,
    onCloneGitHub: (ownerRepo: String, branch: String) -> Unit,
    onOpenWorkspace: () -> Unit,
) {
    var ownerConfirmed by remember { mutableStateOf(false) }
    var ghRepo by remember { mutableStateOf("") }
    var ghBranch by remember { mutableStateOf("main") }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text("RECREATE EXISTING APP", style = MaterialTheme.typography.titleLarge)
                Text(
                    "Import existing source code (ZIP / Android Studio / HTML / GitHub) or analyze an APK you own to reconstruct an editable workspace.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                HorizontalDivider()
                Text("Method A: Upload Source Code", style = MaterialTheme.typography.titleSmall, color = NeonCyan)
                Button(
                    onClick = onPickSourceZip,
                    modifier = Modifier.fillMaxWidth().testTag("pick_source_zip_btn"),
                ) {
                    Text("Select Project ZIP Archive")
                }

                OutlinedTextField(
                    value = ghRepo,
                    onValueChange = { ghRepo = it },
                    label = { Text("GitHub Repository (owner/repo)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = ghBranch,
                        onValueChange = { ghBranch = it },
                        label = { Text("Branch") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedButton(
                        enabled = ghRepo.contains("/"),
                        onClick = { onCloneGitHub(ghRepo.trim(), ghBranch.trim().ifBlank { "main" }) },
                    ) {
                        Text("Clone Repo")
                    }
                }

                HorizontalDivider()
                Text("Method B: Upload & Analyze APK", style = MaterialTheme.typography.titleSmall, color = NeonCyan)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = ownerConfirmed, onCheckedChange = { ownerConfirmed = it })
                    Text(
                        "I confirm I own this app or have explicit permission to inspect and reconstruct it.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                OutlinedButton(
                    enabled = ownerConfirmed,
                    onClick = onPickApk,
                    modifier = Modifier.fillMaxWidth().testTag("pick_apk_btn"),
                ) {
                    Text("Select .APK File")
                }

                if (lastApkReport != null) {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Text("APK Report: ${lastApkReport.packageName}", style = MaterialTheme.typography.titleSmall)
                            Text("Version: ${lastApkReport.versionName} (${lastApkReport.versionCode}) • minSdk ${lastApkReport.minSdk}", style = MaterialTheme.typography.bodySmall)
                            Text("DEX: ${lastApkReport.dexFiles.size} • Resources: ${lastApkReport.resourceFileCount} • Assets: ${lastApkReport.assetFileCount}", style = MaterialTheme.typography.bodySmall)
                            Text("Permissions: ${lastApkReport.permissions.size} • Activities: ${lastApkReport.activities.size}", style = MaterialTheme.typography.bodySmall)
                            Text(lastApkReport.limitationsNote, style = MaterialTheme.typography.labelSmall, color = AmberWarning)
                            Button(onClick = { onOpenWorkspace(); onDismiss() }, modifier = Modifier.fillMaxWidth()) {
                                Text("Open Reconstructed Workspace")
                            }
                        }
                    }
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("Close") }
                }
            }
        }
    }
}

@Composable
fun DiffProposalsDialog(
    proposals: List<FileChangeProposal>,
    onAccept: (String) -> Unit,
    onReject: (String) -> Unit,
    onAcceptAll: () -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("AI Code Diff Review (${proposals.size})", style = MaterialTheme.typography.titleLarge)
                    if (proposals.isNotEmpty()) {
                        Button(onClick = { onAcceptAll(); onDismiss() }) {
                            Text("Accept All")
                        }
                    }
                }

                LazyColumn(modifier = Modifier.heightIn(max = 420.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(proposals, key = { it.id }) { prop ->
                        val diffLines = remember(prop.id) {
                            DiffEngine.computeDiff(prop.originalContent, prop.proposedContent)
                        }
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Color(0xFF090D18),
                            modifier = Modifier.border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp)),
                        ) {
                            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(prop.filePath, style = MaterialTheme.typography.titleSmall, color = NeonCyan)
                                        Text(if (prop.isNewFile) "NEW FILE" else "MODIFIED", style = MaterialTheme.typography.labelSmall, color = AmberWarning)
                                    }
                                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        IconButton(onClick = { onReject(prop.id) }) {
                                            Icon(Icons.Default.Close, contentDescription = "Reject diff", tint = CoralError)
                                        }
                                        IconButton(onClick = { onAccept(prop.id) }) {
                                            Icon(Icons.Default.Check, contentDescription = "Accept diff", tint = EmeraldSuccess)
                                        }
                                    }
                                }
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .horizontalScroll(rememberScrollState())
                                ) {
                                    diffLines.take(80).forEach { dl ->
                                        val bg = when (dl.kind) {
                                            DiffLineKind.ADDED -> Color(0xFF064E3B).copy(alpha = 0.45f)
                                            DiffLineKind.REMOVED -> Color(0xFF7F1D1D).copy(alpha = 0.45f)
                                            DiffLineKind.UNCHANGED -> Color.Transparent
                                        }
                                        val prefix = when (dl.kind) {
                                            DiffLineKind.ADDED -> "+ "
                                            DiffLineKind.REMOVED -> "- "
                                            DiffLineKind.UNCHANGED -> "  "
                                        }
                                        Text(
                                            text = "$prefix${dl.text}",
                                            fontFamily = JetBrainsMonoFamily,
                                            fontSize = 11.sp,
                                            color = Color(0xFFE2E8F0),
                                            modifier = Modifier.background(bg).padding(horizontal = 4.dp),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("Close") }
                }
            }
        }
    }
}

@Composable
fun CloudBuildDialog(
    projectName: String,
    remoteUrlConfigured: Boolean,
    builds: List<BuildJobEntity>,
    onTriggerBuild: (String) -> Unit,
    onDownloadArtifact: (BuildJobEntity) -> Unit,
    onAskAiFixLogs: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier.padding(16.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.Build, contentDescription = null, tint = NeonCyan)
                    Text("Cloud APK Builder", style = MaterialTheme.typography.titleLarge)
                }
                Text("Target Project: $projectName", style = MaterialTheme.typography.titleSmall)

                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(10.dp),
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Default.Security, contentDescription = null, tint = AmberWarning)
                        Text(
                            "Signing Note: A newly generated release key cannot replace an existing app's original Play/distribution signing key when updating an already-installed APK.",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }

                if (!remoteUrlConfigured) {
                    Text(
                        "Remote Cloud Build Backend URL is empty. Configure your isolated Gradle/SDK container URL in Settings -> Cloud Build.",
                        style = MaterialTheme.typography.bodySmall,
                        color = AmberWarning,
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    Button(onClick = { onTriggerBuild("DEBUG_APK") }, modifier = Modifier.weight(1f)) {
                        Text("Debug APK", fontSize = 12.sp)
                    }
                    OutlinedButton(onClick = { onTriggerBuild("RELEASE_APK") }, modifier = Modifier.weight(1f)) {
                        Text("Release APK", fontSize = 12.sp)
                    }
                    OutlinedButton(onClick = { onTriggerBuild("AAB") }, modifier = Modifier.weight(1f)) {
                        Text("AAB", fontSize = 12.sp)
                    }
                }

                HorizontalDivider()
                Text("Recent Cloud Builds", style = MaterialTheme.typography.titleSmall)
                if (builds.isEmpty()) {
                    Text("No builds submitted for this project yet.", style = MaterialTheme.typography.bodySmall)
                } else {
                    builds.take(4).forEach { b ->
                        Surface(
                            color = Color(0xFF0A0F1D),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                ) {
                                    Text("${b.buildType} • ${b.status}", style = MaterialTheme.typography.titleSmall, color = if (b.status == "SUCCESS") EmeraldSuccess else CoralError)
                                    Text("Errors: ${b.errorCount}", style = MaterialTheme.typography.labelSmall)
                                }
                                if (b.logs.isNotBlank()) {
                                    Text(
                                        text = b.logs.take(450),
                                        fontFamily = JetBrainsMonoFamily,
                                        fontSize = 10.sp,
                                        color = Color(0xFFCBD5E1),
                                    )
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    if (b.status == "SUCCESS" && !b.artifactPath.isNullOrBlank()) {
                                        Button(onClick = { onDownloadArtifact(b) }) {
                                            Icon(Icons.Default.Download, contentDescription = null)
                                            Spacer(Modifier.width(4.dp))
                                            Text("Download APK")
                                        }
                                    }
                                    if (b.status == "FAILED" && b.logs.isNotBlank()) {
                                        OutlinedButton(onClick = { onAskAiFixLogs(b.logs); onDismiss() }) {
                                            Text("Fix Build Errors with AI")
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("Close") }
                }
            }
        }
    }
}

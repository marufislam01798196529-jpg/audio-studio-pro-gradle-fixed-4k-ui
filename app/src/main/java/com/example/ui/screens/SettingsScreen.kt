package com.example.ui.screens

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.ApiProfileEntity
import com.example.data.model.ReasoningLevel
import com.example.ui.state.XThrexUiState
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.CoralError
import com.example.ui.theme.EmeraldSuccess
import com.example.ui.theme.NeonCyan
import com.example.workers.NotificationHelper

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    state: XThrexUiState,
    onAddApiProfile: () -> Unit,
    onEditApiProfile: (ApiProfileEntity) -> Unit,
    onSetDefaultProfile: (String) -> Unit,
    onToggleProfileEnabled: (ApiProfileEntity) -> Unit,
    onDeleteProfile: (String) -> Unit,
    onSelectReasoningLevel: (ReasoningLevel) -> Unit,
    onSetDarkTheme: (Boolean) -> Unit,
    onSetLowMemoryMode: (Boolean) -> Unit,
    onRefreshMemory: () -> Unit,
    onSetEditorFontSize: (Int) -> Unit,
    onSetEditorLightTheme: (Boolean) -> Unit,
    onSetNotificationsEnabled: (Boolean) -> Unit,
    onSetParallelAgents: (Boolean) -> Unit,
    onSetRequireDeleteApproval: (Boolean) -> Unit,
    onSaveRemoteBackend: (url: String, token: String) -> Unit,
    onCheckRemoteHealth: () -> Unit,
    onOpenSkillsTab: () -> Unit,
) {
    val context = LocalContext.current
    var remoteUrl by remember(state.settings.remoteBackendUrl) {
        mutableStateOf(state.settings.remoteBackendUrl)
    }
    var remoteToken by remember { mutableStateOf("") }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        onSetNotificationsEnabled(granted)
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // 1. API Management
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.widthIn(max = 860.dp).fillMaxWidth(),
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Default.Key, contentDescription = null, tint = NeonCyan)
                            Text("API Management (${state.apiProfiles.size})", style = MaterialTheme.typography.titleLarge)
                        }
                        Button(onClick = onAddApiProfile, modifier = Modifier.testTag("settings_add_api_btn")) {
                            Icon(Icons.Default.Add, contentDescription = null)
                            Spacer(Modifier.width(4.dp))
                            Text("Add API")
                        }
                    }
                    Text(
                        "Keys are encrypted locally via Android Keystore AES-256-GCM and never written to project ZIPs or logs.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (state.apiProfiles.isEmpty()) {
                        Text("No API profiles saved yet.", style = MaterialTheme.typography.bodySmall, color = AmberWarning)
                    } else {
                        state.apiProfiles.forEach { prof ->
                            HorizontalDivider()
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            "${prof.customName}${if (prof.isDefault) " [DEFAULT]" else ""}",
                                            style = MaterialTheme.typography.titleSmall,
                                            color = if (prof.isDefault) NeonCyan else MaterialTheme.colorScheme.onSurface,
                                        )
                                        Text(
                                            "${prof.providerName} • ${prof.modelId.ifBlank { "No model" }} • ${prof.modelCategory}",
                                            style = MaterialTheme.typography.labelSmall,
                                        )
                                        Text(
                                            "Status: ${prof.lastStatus} (${prof.lastLatencyMs}ms) • Tokens: ${prof.totalTokensUsed} • Est: $${String.format("%.4f", prof.estimatedCostUsd)}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = if (prof.lastError != null) CoralError else EmeraldSuccess,
                                        )
                                    }
                                    Switch(checked = prof.isEnabled, onCheckedChange = { onToggleProfileEnabled(prof) })
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    if (!prof.isDefault) {
                                        TextButton(onClick = { onSetDefaultProfile(prof.id) }) { Text("Set Default") }
                                    }
                                    TextButton(onClick = { onEditApiProfile(prof) }) {
                                        Icon(Icons.Default.Edit, contentDescription = null)
                                        Spacer(Modifier.width(4.dp))
                                        Text("Edit / Discover")
                                    }
                                    IconButton(onClick = { onDeleteProfile(prof.id) }) {
                                        Icon(Icons.Default.Delete, contentDescription = "Delete profile", tint = CoralError)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // 2. Reasoning Level & Agent Mode
        item {
            Card(modifier = Modifier.widthIn(max = 860.dp).fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("AI Reasoning & Agent Controls", style = MaterialTheme.typography.titleLarge)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        ReasoningLevel.entries.forEach { lvl ->
                            FilterChip(
                                selected = state.settings.reasoningLevel == lvl,
                                onClick = { onSelectReasoningLevel(lvl) },
                                label = { Text(lvl.label) },
                            )
                        }
                    }
                    Text(
                        state.settings.reasoningLevel.description + " (Automatically falls back to prompt-based task depth if the provider model does not accept native reasoning parameters.)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("Allow Parallel Agents (when supported)", style = MaterialTheme.typography.bodyMedium)
                        Switch(checked = state.settings.parallelAgentsEnabled, onCheckedChange = onSetParallelAgents)
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("Require Approval Before File Deletion", style = MaterialTheme.typography.bodyMedium)
                        Switch(checked = state.settings.requireApprovalForDeletes, onCheckedChange = onSetRequireDeleteApproval)
                    }
                    OutlinedButton(onClick = onOpenSkillsTab) { Text("Manage Coding Skills (${state.skills.size})") }
                }
            }
        }

        // 3. Cloud Build & Container Terminal Backend
        item {
            Card(modifier = Modifier.widthIn(max = 860.dp).fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Cloud Build & Remote Container Backend", style = MaterialTheme.typography.titleLarge)
                    Text(
                        "Connect your isolated X-THREX build server (see /backend in project source) for remote Android Gradle APK builds and container terminal execution.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedTextField(
                        value = remoteUrl,
                        onValueChange = { remoteUrl = it },
                        label = { Text("Remote Backend Base URL") },
                        placeholder = { Text("https://build.yourdomain.com") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = remoteToken,
                        onValueChange = { remoteToken = it },
                        label = { Text("Backend Auth Token (Keystore Encrypted)") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { onSaveRemoteBackend(remoteUrl, remoteToken); remoteToken = "" }) {
                            Text("Save Backend Config")
                        }
                        OutlinedButton(onClick = onCheckRemoteHealth) { Text("Check Health") }
                    }
                    state.remoteHealthFeedback?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = NeonCyan)
                    }
                }
            }
        }

        // 4. 1GB RAM Optimization & Storage
        item {
            Card(modifier = Modifier.widthIn(max = 860.dp).fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.Memory, contentDescription = null, tint = EmeraldSuccess)
                        Text("1GB RAM Optimization & Telemetry", style = MaterialTheme.typography.titleLarge)
                    }
                    Text(
                        "Device RAM: ${state.memoryStatus.availableRamMb}MB free / ${state.memoryStatus.totalRamMb}MB total • App Heap: ${state.memoryStatus.appHeapUsedMb}MB / ${state.memoryStatus.appHeapMaxMb}MB",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Low-Memory Mode (1GB RAM)", style = MaterialTheme.typography.bodyMedium)
                            Text("Caps open tabs to 2, chunks large reads, paginates chat, and unloads background previews.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(checked = state.settings.lowMemoryMode, onCheckedChange = onSetLowMemoryMode)
                    }
                    OutlinedButton(onClick = onRefreshMemory) { Text("Refresh RAM Telemetry") }
                }
            }
        }

        // 5. Appearance, Editor & Notifications
        item {
            Card(modifier = Modifier.widthIn(max = 860.dp).fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Appearance, Editor & Notifications", style = MaterialTheme.typography.titleLarge)
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("Dark Navy Developer Theme", style = MaterialTheme.typography.bodyMedium)
                        Switch(checked = state.settings.darkTheme, onCheckedChange = onSetDarkTheme)
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("Light Code Editor Surface", style = MaterialTheme.typography.bodyMedium)
                        Switch(checked = state.settings.editorLightTheme, onCheckedChange = onSetEditorLightTheme)
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("Editor Font Size (${state.settings.editorFontSizeSp}sp)", style = MaterialTheme.typography.bodyMedium)
                        Row {
                            TextButton(onClick = { onSetEditorFontSize(state.settings.editorFontSizeSp - 1) }) { Text("A-") }
                            TextButton(onClick = { onSetEditorFontSize(state.settings.editorFontSizeSp + 1) }) { Text("A+") }
                        }
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(Icons.Default.Notifications, contentDescription = null)
                            Text("Background Task & Build Notifications", style = MaterialTheme.typography.bodyMedium)
                        }
                        Switch(
                            checked = state.settings.notificationsEnabled,
                            onCheckedChange = { wantEnable ->
                                if (wantEnable && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                                    !NotificationHelper.hasNotificationPermission(context)
                                ) {
                                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                } else {
                                    onSetNotificationsEnabled(wantEnable)
                                }
                            },
                        )
                    }
                }
            }
        }

        // 6. Privacy & Security Verification
        item {
            Card(modifier = Modifier.widthIn(max = 860.dp).fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.Security, contentDescription = null, tint = EmeraldSuccess)
                        Text("Privacy & Security Guarantees", style = MaterialTheme.typography.titleLarge)
                    }
                    listOf(
                        "Android Keystore AES-256-GCM encrypted API & GitHub token vault",
                        "Zero-permission Storage Access Framework & Photo Picker (Play Policy compliant)",
                        "Automatic secret redaction in logs, terminal output & exported ZIPs",
                        "Sandboxed WebView preview with file:// access disabled",
                    ).forEach { line ->
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = EmeraldSuccess)
                            Text(line, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    }
}

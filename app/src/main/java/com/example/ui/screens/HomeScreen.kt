package com.example.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.model.QuickTaskAction
import com.example.ui.state.XThrexUiState
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.EmeraldSuccess
import com.example.ui.theme.NeonCyan

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HomeScreen(
    state: XThrexUiState,
    onNewProject: () -> Unit,
    onRecreateExistingApp: () -> Unit,
    onUploadSourceZip: () -> Unit,
    onUploadApk: () -> Unit,
    onOpenProject: (String) -> Unit,
    onConnectApi: () -> Unit,
    onQuickAction: (QuickTaskAction) -> Unit,
    onOpenTasksTab: () -> Unit,
) {
    val activeProfile = state.apiProfiles.firstOrNull { it.isDefault && it.isEnabled }
        ?: state.apiProfiles.firstOrNull { it.isEnabled }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        item {
            Box(
                modifier = Modifier
                    .widthIn(max = 860.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(22.dp))
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(22.dp))
            ) {
                Image(
                    painter = painterResource(id = R.drawable.img_xthrex_hero),
                    contentDescription = "X-THREX Developer Workspace Banner",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.matchParentSize(),
                )
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(
                                    Color(0xFF0A0E1A).copy(alpha = 0.94f),
                                    Color(0xFF0A0E1A).copy(alpha = 0.78f),
                                    Color(0xFF0A0E1A).copy(alpha = 0.50f),
                                )
                            )
                        )
                )
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.ic_xthrex_emblem),
                            contentDescription = "X-THREX Logo",
                            modifier = Modifier
                                .size(46.dp)
                                .clip(RoundedCornerShape(12.dp)),
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "X-THREX",
                                style = MaterialTheme.typography.headlineMedium,
                                color = Color.White,
                            )
                            Text(
                                text = "Autonomous Mobile AI Coding Agent",
                                style = MaterialTheme.typography.bodySmall,
                                color = NeonCyan,
                            )
                        }
                        Surface(
                            color = Color(0xFF111726).copy(alpha = 0.85f),
                            shape = RoundedCornerShape(999.dp),
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp),
                            ) {
                                Icon(
                                    Icons.Default.Memory,
                                    contentDescription = "RAM telemetry",
                                    tint = if (state.settings.lowMemoryMode) NeonCyan else EmeraldSuccess,
                                    modifier = Modifier.size(14.dp),
                                )
                                Text(
                                    text = "${state.memoryStatus.availableRamMb}MB free",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.White,
                                )
                            }
                        }
                    }

                    Text(
                        text = if (activeProfile != null) {
                            "API Ready: ${activeProfile.customName} • ${activeProfile.modelId.ifBlank { "Select model" }} • Reasoning ${state.settings.reasoningLevel.label}"
                        } else {
                            "Connect your own API Key & Base URL to plan, write code, fix bugs, and build Android APKs remotely."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xFFCBD5E1),
                    )

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Button(
                            onClick = onNewProject,
                            modifier = Modifier.weight(1f).testTag("home_new_project_btn"),
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("New Project")
                        }
                        Button(
                            onClick = onRecreateExistingApp,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.secondary,
                                contentColor = MaterialTheme.colorScheme.onSecondary,
                            ),
                            modifier = Modifier.weight(1f).testTag("home_recreate_app_btn"),
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Recreate App", maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }

        // Core Launch Actions Grid
        item {
            Column(
                modifier = Modifier.widthIn(max = 860.dp).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text("Workspace Launchpad", style = MaterialTheme.typography.titleLarge)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    maxItemsInEachRow = 2,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    LaunchpadTile(
                        title = "Upload Source Code",
                        subtitle = "ZIP, GitHub, Android Studio, HTML",
                        icon = Icons.Default.UploadFile,
                        accent = NeonCyan,
                        onClick = onUploadSourceZip,
                        modifier = Modifier.weight(1f).testTag("home_upload_source_btn"),
                    )
                    LaunchpadTile(
                        title = "Upload APK",
                        subtitle = "Analyze manifest, DEX & reconstruct",
                        icon = Icons.Default.Android,
                        accent = EmeraldSuccess,
                        onClick = onUploadApk,
                        modifier = Modifier.weight(1f).testTag("home_upload_apk_btn"),
                    )
                    LaunchpadTile(
                        title = "Open Project",
                        subtitle = state.activeProject?.name ?: "Browse workspaces",
                        icon = Icons.Default.FolderOpen,
                        accent = MaterialTheme.colorScheme.primary,
                        onClick = {
                            val activeId = state.activeProject?.id ?: state.projects.firstOrNull()?.id
                            if (activeId != null) onOpenProject(activeId) else onNewProject()
                        },
                        modifier = Modifier.weight(1f).testTag("home_open_project_btn"),
                    )
                    LaunchpadTile(
                        title = "Connect API",
                        subtitle = if (activeProfile != null) "${state.apiProfiles.size} profile(s) saved" else "Add key & Base URL",
                        icon = Icons.Default.Key,
                        accent = AmberWarning,
                        onClick = onConnectApi,
                        modifier = Modifier.weight(1f).testTag("home_connect_api_btn"),
                    )
                }
            }
        }

        // Quick Coding Actions (all 14 prompt actions)
        item {
            Column(
                modifier = Modifier.widthIn(max = 860.dp).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("Quick Coding Actions", style = MaterialTheme.typography.titleLarge)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(QuickTaskAction.entries) { action ->
                        AssistChip(
                            onClick = { onQuickAction(action) },
                            label = { Text(action.label) },
                            leadingIcon = {
                                Icon(
                                    Icons.Default.AutoAwesome,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                )
                            },
                        )
                    }
                }
            }
        }

        // Recent Projects
        item {
            Column(
                modifier = Modifier.widthIn(max = 860.dp).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("Recent Projects", style = MaterialTheme.typography.titleLarge)
                if (state.projects.isEmpty()) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(
                            modifier = Modifier.padding(18.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text("No projects yet", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "Create a new Android, Web, Python, or Game project, or import an existing ZIP / APK to get started.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            OutlinedButton(onClick = onNewProject) { Text("Create First Project") }
                        }
                    }
                } else {
                    state.projects.take(4).forEach { proj ->
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onOpenProject(proj.id) },
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.primaryContainer),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(
                                        Icons.Default.Code,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    )
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(proj.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(
                                        "${proj.projectType} • ${proj.description.ifBlank { "Tap to open coding workspace" }}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Recent Tasks
        item {
            Column(
                modifier = Modifier.widthIn(max = 860.dp).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Recent Agent Tasks", style = MaterialTheme.typography.titleLarge)
                    OutlinedButton(onClick = onOpenTasksTab) { Text("Task Board") }
                }
                if (state.tasks.isEmpty()) {
                    Text(
                        "Tasks created from AI planning or the Task Board appear here with live checkpoints.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    state.tasks.take(3).forEach { t ->
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(t.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text("${t.agentRole} • ${t.status}", style = MaterialTheme.typography.labelSmall, color = NeonCyan)
                                }
                                Text("${(t.progress * 100).toInt()}%", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LaunchpadTile(
    title: String,
    subtitle: String,
    icon: ImageVector,
    accent: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = modifier
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(accent.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = title, tint = accent, modifier = Modifier.size(20.dp))
            }
            Text(title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

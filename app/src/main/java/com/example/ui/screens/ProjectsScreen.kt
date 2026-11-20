package com.example.ui.screens

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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.ProjectEntity
import com.example.data.local.SkillEntity
import com.example.ui.state.XThrexUiState
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.CoralError
import com.example.ui.theme.EmeraldSuccess
import com.example.ui.theme.NeonCyan

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ProjectsScreen(
    state: XThrexUiState,
    onNewProject: () -> Unit,
    onOpenWorkspace: (String) -> Unit,
    onExportZip: (ProjectEntity) -> Unit,
    onOpenCloudBuild: (ProjectEntity) -> Unit,
    onDeleteProject: (String) -> Unit,
    onAssignProfileToProject: (projectId: String, profileId: String, modelId: String?) -> Unit,
    onRestoreSnapshot: (com.example.data.local.ProjectSnapshotEntity) -> Unit,
    onConnectGitHub: (String) -> Unit,
    onDisconnectGitHub: () -> Unit,
    onRefreshGitHubRepos: () -> Unit,
    onCloneGitHubRepo: (String, String) -> Unit,
    onToggleSkill: (SkillEntity) -> Unit,
    onSaveSkill: (id: String?, name: String, category: String, desc: String, md: String) -> Unit,
    onImportSkillMd: () -> Unit,
    onDeleteSkill: (String) -> Unit,
) {
    var selectedSegment by remember { mutableIntStateOf(0) }
    var skillSearch by remember { mutableStateOf("") }
    var showSkillEditor by remember { mutableStateOf(false) }
    var editingSkill by remember { mutableStateOf<SkillEntity?>(null) }
    var githubTokenInput by remember { mutableStateOf("") }
    var deleteConfirmProject by remember { mutableStateOf<ProjectEntity?>(null) }

    Column(modifier = Modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = selectedSegment) {
            Tab(selected = selectedSegment == 0, onClick = { selectedSegment = 0 }, text = { Text("Projects (${state.projects.size})") })
            Tab(selected = selectedSegment == 1, onClick = { selectedSegment = 1 }, text = { Text("GitHub") })
            Tab(selected = selectedSegment == 2, onClick = { selectedSegment = 2 }, text = { Text("Skills (${state.skills.size})") })
        }

        when (selectedSegment) {
            0 -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    item {
                        Row(
                            modifier = Modifier.widthIn(max = 860.dp).fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("All Workspaces", style = MaterialTheme.typography.titleLarge)
                            Button(onClick = onNewProject, modifier = Modifier.testTag("projects_new_btn")) {
                                Icon(Icons.Default.Add, contentDescription = null)
                                Spacer(Modifier.width(4.dp))
                                Text("New Project")
                            }
                        }
                    }

                    if (state.projects.isEmpty()) {
                        item {
                            Card(modifier = Modifier.widthIn(max = 860.dp).fillMaxWidth()) {
                                Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text("No Projects Created", style = MaterialTheme.typography.titleMedium)
                                    Text("Tap 'New Project' or import an existing ZIP/APK/GitHub repo.", style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }

                    items(state.projects, key = { it.id }) { proj ->
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            modifier = Modifier.widthIn(max = 860.dp).fillMaxWidth(),
                        ) {
                            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(proj.name, style = MaterialTheme.typography.titleMedium)
                                        Text("${proj.projectType} • ${proj.githubRepoFullName ?: "Local Workspace"}", style = MaterialTheme.typography.labelSmall, color = NeonCyan)
                                    }
                                    IconButton(onClick = { deleteConfirmProject = proj }) {
                                        Icon(Icons.Default.Delete, contentDescription = "Delete project", tint = CoralError)
                                    }
                                }
                                if (proj.description.isNotBlank()) {
                                    Text(proj.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }

                                if (state.apiProfiles.isNotEmpty()) {
                                    Text("Assigned AI Profile:", style = MaterialTheme.typography.labelSmall)
                                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        state.apiProfiles.filter { it.isEnabled }.forEach { prof ->
                                            FilterChip(
                                                selected = proj.assignedProfileId == prof.id,
                                                onClick = { onAssignProfileToProject(proj.id, prof.id, prof.modelId) },
                                                label = { Text("${prof.customName} (${prof.modelId.ifBlank { "default" }})", fontSize = 10.sp) },
                                            )
                                        }
                                    }
                                }

                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    Button(onClick = { onOpenWorkspace(proj.id) }) {
                                        Icon(Icons.Default.FolderOpen, contentDescription = null)
                                        Spacer(Modifier.width(4.dp))
                                        Text("Open Workspace")
                                    }
                                    OutlinedButton(
                                        onClick = { onExportZip(proj) },
                                        modifier = Modifier.testTag("download_project_zip_btn"),
                                    ) {
                                        Icon(Icons.Default.Archive, contentDescription = null)
                                        Spacer(Modifier.width(4.dp))
                                        Text("DOWNLOAD PROJECT ZIP")
                                    }
                                    OutlinedButton(onClick = { onOpenCloudBuild(proj) }) {
                                        Icon(Icons.Default.Build, contentDescription = null)
                                        Spacer(Modifier.width(4.dp))
                                        Text("Cloud APK Build")
                                    }
                                }

                                val projSnapshots = state.snapshots.filter { it.projectId == proj.id }
                                if (projSnapshots.isNotEmpty()) {
                                    HorizontalDivider()
                                    Text("Backup Snapshots (${projSnapshots.size})", style = MaterialTheme.typography.labelSmall)
                                    projSnapshots.take(2).forEach { snap ->
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            Text("${snap.label} (${snap.fileCount} files)", style = MaterialTheme.typography.bodySmall)
                                            TextButton(onClick = { onRestoreSnapshot(snap) }) {
                                                Icon(Icons.Default.History, contentDescription = null)
                                                Spacer(Modifier.width(4.dp))
                                                Text("Restore")
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            1 -> {
                // GitHub Integration
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    item {
                        Card(modifier = Modifier.widthIn(max = 860.dp).fillMaxWidth()) {
                            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text("GitHub Integration", style = MaterialTheme.typography.titleLarge)
                                Text(
                                    if (state.settings.githubUsername.isNotBlank()) {
                                        "Connected as @${state.settings.githubUsername} (token isolated in Keystore vault)"
                                    } else {
                                        "Connect a GitHub Personal Access Token / OAuth token (stored separately from AI API keys)."
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (state.settings.githubUsername.isNotBlank()) EmeraldSuccess else MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                OutlinedTextField(
                                    value = githubTokenInput,
                                    onValueChange = { githubTokenInput = it },
                                    label = { Text("GitHub Token (ghp_... / OAuth)") },
                                    visualTransformation = PasswordVisualTransformation(),
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Button(
                                        enabled = githubTokenInput.isNotBlank() && !state.githubBusy,
                                        onClick = { onConnectGitHub(githubTokenInput); githubTokenInput = "" },
                                    ) {
                                        Text("Connect Account")
                                    }
                                    if (state.settings.githubUsername.isNotBlank()) {
                                        OutlinedButton(onClick = onRefreshGitHubRepos) { Text("Load Repos") }
                                        TextButton(onClick = onDisconnectGitHub) { Text("Disconnect") }
                                    }
                                }
                                if (state.githubBusy) CircularProgressIndicator()
                            }
                        }
                    }

                    items(state.githubRepos, key = { it.fullName }) { repo ->
                        Card(modifier = Modifier.widthIn(max = 860.dp).fillMaxWidth()) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(repo.fullName, style = MaterialTheme.typography.titleSmall)
                                    Text("${repo.language} • branch ${repo.defaultBranch}", style = MaterialTheme.typography.labelSmall, color = NeonCyan)
                                }
                                Button(onClick = { onCloneGitHubRepo(repo.fullName, repo.defaultBranch) }) {
                                    Text("Clone")
                                }
                            }
                        }
                    }
                }
            }

            else -> {
                // Skills Manager
                val filteredSkills = remember(state.skills, skillSearch) {
                    if (skillSearch.isBlank()) state.skills
                    else state.skills.filter {
                        it.name.contains(skillSearch, ignoreCase = true) ||
                            it.category.contains(skillSearch, ignoreCase = true)
                    }
                }
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    item {
                        Column(modifier = Modifier.widthIn(max = 860.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Skills System — Teach AI Coding", style = MaterialTheme.typography.titleLarge)
                            Text(
                                "Skills guide the AI agent via reusable rules, patterns, and documentation (does not modify base model weights). External skills are inspected before activation.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            OutlinedTextField(
                                value = skillSearch,
                                onValueChange = { skillSearch = it },
                                label = { Text("Search Installed Skills") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(onClick = { editingSkill = null; showSkillEditor = true }, modifier = Modifier.testTag("add_custom_skill_btn")) {
                                    Icon(Icons.Default.Add, contentDescription = null)
                                    Spacer(Modifier.width(4.dp))
                                    Text("Create Skill")
                                }
                                OutlinedButton(onClick = onImportSkillMd) {
                                    Icon(Icons.Default.UploadFile, contentDescription = null)
                                    Spacer(Modifier.width(4.dp))
                                    Text("Import .MD Skill")
                                }
                            }
                        }
                    }

                    items(filteredSkills, key = { it.id }) { skill ->
                        Card(modifier = Modifier.widthIn(max = 860.dp).fillMaxWidth()) {
                            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) {
                                        Icon(Icons.Default.Psychology, contentDescription = null, tint = NeonCyan)
                                        Column {
                                            Text(skill.name, style = MaterialTheme.typography.titleSmall)
                                            Text("${skill.category} • ${skill.sourceType}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }
                                    Switch(checked = skill.isEnabled, onCheckedChange = { onToggleSkill(skill) })
                                }
                                Text(skill.description, style = MaterialTheme.typography.bodySmall)
                                if (!skill.isTrusted) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Icon(Icons.Default.Security, contentDescription = null, tint = AmberWarning)
                                        Text("Untrusted external instructions — review before enabling", style = MaterialTheme.typography.labelSmall, color = AmberWarning)
                                    }
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    TextButton(onClick = { editingSkill = skill; showSkillEditor = true }) { Text("View / Edit") }
                                    if (skill.sourceType != "BUILTIN") {
                                        TextButton(onClick = { onDeleteSkill(skill.id) }) { Text("Delete", color = CoralError) }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showSkillEditor) {
        SkillEditorDialog(
            existing = editingSkill,
            onDismiss = { showSkillEditor = false },
            onSave = { name, cat, desc, md ->
                onSaveSkill(editingSkill?.id, name, cat, desc, md)
                showSkillEditor = false
            },
        )
    }

    deleteConfirmProject?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteConfirmProject = null },
            title = { Text("Delete Project?") },
            text = { Text("Delete '${target.name}' and its local workspace files?") },
            confirmButton = {
                Button(onClick = { onDeleteProject(target.id); deleteConfirmProject = null }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { deleteConfirmProject = null }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun SkillEditorDialog(
    existing: SkillEntity?,
    onDismiss: () -> Unit,
    onSave: (String, String, String, String) -> Unit,
) {
    var name by remember { mutableStateOf(existing?.name ?: "") }
    var category by remember { mutableStateOf(existing?.category ?: "Custom") }
    var desc by remember { mutableStateOf(existing?.description ?: "") }
    var md by remember { mutableStateOf(existing?.markdownContent ?: "# Custom Coding Skill\n- Follow strict clean architecture.") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) "Create Coding Skill" else "Edit Coding Skill") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Skill Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = category, onValueChange = { category = it }, label = { Text("Category") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = desc, onValueChange = { desc = it }, label = { Text("Description") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = md, onValueChange = { md = it }, label = { Text("Markdown Instructions & Rules") }, minLines = 5, maxLines = 10, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            Button(enabled = name.isNotBlank(), onClick = { onSave(name, category, desc, md) }) { Text("Save Skill") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

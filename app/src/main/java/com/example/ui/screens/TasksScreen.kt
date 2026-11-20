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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.AlertDialog
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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.AgentTaskEntity
import com.example.data.model.AgentRole
import com.example.data.model.TaskStatus
import com.example.ui.state.XThrexUiState
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.CoralError
import com.example.ui.theme.EmeraldSuccess
import com.example.ui.theme.NeonCyan

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TasksScreen(
    state: XThrexUiState,
    onAddTask: (title: String, desc: String, role: AgentRole) -> Unit,
    onUpdateTaskStatus: (AgentTaskEntity, TaskStatus) -> Unit,
    onRunTaskBackground: (AgentTaskEntity) -> Unit,
    onDeleteTask: (String) -> Unit,
    onRunDiagnostics: () -> Unit,
    onFixAllIssues: () -> Unit,
    onSendTaskToAiAgent: (AgentTaskEntity) -> Unit,
) {
    var showAddTask by remember { mutableStateOf(false) }
    var statusFilter by remember { mutableStateOf<TaskStatus?>(null) }

    val filteredTasks = remember(state.tasks, statusFilter) {
        if (statusFilter == null) state.tasks
        else state.tasks.filter { it.status == statusFilter?.name }
    }

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
                Column {
                    Text("Task Board & Error Center", style = MaterialTheme.typography.titleLarge)
                    Text("Workflow: Request -> Plan -> Code -> Test -> Fix", style = MaterialTheme.typography.labelSmall, color = NeonCyan)
                }
                Button(onClick = { showAddTask = true }, modifier = Modifier.testTag("add_task_btn")) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(Modifier.width(4.dp))
                    Text("Add Task")
                }
            }
        }

        // Bug Fixer & Diagnostics Card
        item {
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
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Default.BugReport, contentDescription = null, tint = AmberWarning)
                            Text("Diagnostics & Bug Fixer (${state.diagnostics.size})", style = MaterialTheme.typography.titleMedium)
                        }
                        OutlinedButton(onClick = onRunDiagnostics, modifier = Modifier.testTag("run_diagnostics_btn")) {
                            Text("Scan Project")
                        }
                    }

                    if (state.diagnostics.isEmpty()) {
                        Text(
                            "Run 'Scan Project' to check syntax, JSON/XML structure, missing Android manifests, broken local imports, and leaked secrets.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        state.diagnostics.take(8).forEach { issue ->
                            Text(
                                text = "[${issue.severity}] ${issue.filePath}:${issue.line} — ${issue.message}",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (issue.severity == "ERROR") CoralError else AmberWarning,
                            )
                        }
                        Button(
                            onClick = onFixAllIssues,
                            modifier = Modifier.fillMaxWidth().testTag("fix_all_issues_btn"),
                        ) {
                            Text("FIX ALL DETECTED ISSUES")
                        }
                    }
                }
            }
        }

        // Status filter chips
        item {
            FlowRow(
                modifier = Modifier.widthIn(max = 860.dp).fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                FilterChip(
                    selected = statusFilter == null,
                    onClick = { statusFilter = null },
                    label = { Text("All (${state.tasks.size})", fontSize = 11.sp) },
                )
                TaskStatus.entries.forEach { st ->
                    val count = state.tasks.count { it.status == st.name }
                    FilterChip(
                        selected = statusFilter == st,
                        onClick = { statusFilter = st },
                        label = { Text("${st.label} ($count)", fontSize = 11.sp) },
                    )
                }
            }
        }

        if (filteredTasks.isEmpty()) {
            item {
                Card(modifier = Modifier.widthIn(max = 860.dp).fillMaxWidth()) {
                    Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("No Tasks in Queue", style = MaterialTheme.typography.titleMedium)
                        Text("Add follow-up tasks anytime—even after earlier tasks complete—to iterate continuously on your project.", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }

        items(filteredTasks, key = { it.id }) { task ->
            val st = runCatching { TaskStatus.valueOf(task.status) }.getOrDefault(TaskStatus.PENDING)
            val statusColor = when (st) {
                TaskStatus.COMPLETED -> EmeraldSuccess
                TaskStatus.FAILED -> CoralError
                TaskStatus.NEEDS_APPROVAL -> AmberWarning
                TaskStatus.IN_PROGRESS, TaskStatus.PLANNING -> NeonCyan
                TaskStatus.PENDING -> MaterialTheme.colorScheme.onSurfaceVariant
            }

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
                            Text(task.title, style = MaterialTheme.typography.titleMedium)
                            Text("${task.agentRole} • ${st.label}", style = MaterialTheme.typography.labelSmall, color = statusColor)
                        }
                        IconButton(onClick = { onDeleteTask(task.id) }) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete task", tint = CoralError)
                        }
                    }
                    if (task.description.isNotBlank()) {
                        Text(task.description, style = MaterialTheme.typography.bodySmall)
                    }
                    if (task.progress > 0f && task.progress < 1f) {
                        LinearProgressIndicator(progress = { task.progress }, modifier = Modifier.fillMaxWidth())
                    }
                    if (task.checkpointNote.isNotBlank() || task.resultSummary.isNotBlank()) {
                        Text(
                            task.resultSummary.ifBlank { task.checkpointNote },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    HorizontalDivider()
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { onSendTaskToAiAgent(task) }) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null)
                            Spacer(Modifier.width(4.dp))
                            Text("Run with AI", fontSize = 11.sp)
                        }
                        OutlinedButton(onClick = { onRunTaskBackground(task) }) {
                            Icon(Icons.Default.Schedule, contentDescription = null)
                            Spacer(Modifier.width(4.dp))
                            Text("WorkManager Bg", fontSize = 11.sp)
                        }
                        if (st == TaskStatus.IN_PROGRESS) {
                            TextButton(onClick = { onUpdateTaskStatus(task, TaskStatus.PENDING) }) {
                                Icon(Icons.Default.Pause, contentDescription = null)
                                Text("Pause", fontSize = 11.sp)
                            }
                        } else if (st == TaskStatus.FAILED) {
                            TextButton(onClick = { onUpdateTaskStatus(task, TaskStatus.PENDING) }) {
                                Icon(Icons.Default.Refresh, contentDescription = null)
                                Text("Retry", fontSize = 11.sp)
                            }
                        }
                        if (st != TaskStatus.COMPLETED) {
                            TextButton(onClick = { onUpdateTaskStatus(task, TaskStatus.COMPLETED) }) {
                                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = EmeraldSuccess)
                                Text("Mark Done", fontSize = 11.sp)
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAddTask) {
        var title by remember { mutableStateOf("") }
        var desc by remember { mutableStateOf("") }
        var role by remember { mutableStateOf(AgentRole.ANDROID) }

        AlertDialog(
            onDismissRequest = { showAddTask = false },
            title = { Text("Add Project Task") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Task Title") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = desc, onValueChange = { desc = it }, label = { Text("Details") }, modifier = Modifier.fillMaxWidth())
                    Text("Assign Specialized Agent", style = MaterialTheme.typography.labelMedium)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        AgentRole.entries.forEach { r ->
                            FilterChip(selected = role == r, onClick = { role = r }, label = { Text(r.label, fontSize = 10.sp) })
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    enabled = title.isNotBlank(),
                    onClick = { onAddTask(title, desc, role); showAddTask = false },
                ) { Text("Add") }
            },
            dismissButton = { TextButton(onClick = { showAddTask = false }) { Text("Cancel") } },
        )
    }
}

package com.example.ui.screens

import android.annotation.SuppressLint
import android.webkit.ConsoleMessage
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.data.local.ProjectEntity
import com.example.data.model.ProjectType
import com.example.data.model.TerminalLine
import com.example.ui.state.XThrexUiState
import com.example.ui.theme.CoralError
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.NeonCyan
import java.io.File

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun SandboxedWebPreviewPanel(
    project: ProjectEntity?,
    workspaceRootPath: String?,
    refreshCounter: Int,
    consoleLogs: List<String>,
    onConsoleLog: (String) -> Unit,
    onClearConsole: () -> Unit,
    onAskAiError: (String) -> Unit,
    onOpenCloudBuild: () -> Unit,
    onRunDiagnostics: () -> Unit,
) {
    val pType = project?.let {
        runCatching { ProjectType.valueOf(it.projectType) }.getOrNull()
    }
    var desktopMode by remember { mutableStateOf(false) }
    var manualReloadKey by remember { mutableStateOf(0) }

    if (project == null || workspaceRootPath == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Select or create a project to preview.", style = MaterialTheme.typography.bodyMedium)
        }
        return
    }

    if (pType != null && !pType.supportsLiveWebPreview) {
        Column(
            modifier = Modifier.fillMaxSize().padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(Icons.Default.Android, contentDescription = null, tint = NeonCyan)
            Text("${pType.label} Project — Cloud Build Required", style = MaterialTheme.typography.titleLarge)
            Text(
                "Native Android/Kotlin/Java/Flutter projects cannot execute inside an HTML browser preview. Use Diagnostics & Cloud APK Builder to compile and inspect build output.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = onOpenCloudBuild) {
                    Icon(Icons.Default.Build, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Cloud APK Builder")
                }
                OutlinedButton(onClick = onRunDiagnostics) {
                    Icon(Icons.Default.BugReport, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Run Diagnostics")
                }
            }
        }
        return
    }

    val assembledHtml = remember(project.id, refreshCounter, manualReloadKey, desktopMode) {
        assembleSandboxedHtml(File(workspaceRootPath), desktopMode)
    }
    val context = LocalContext.current
    val webViewRef = remember { mutableStateOf<WebView?>(null) }

    DisposableEffect(project.id) {
        onDispose {
            // Release WebView resources when leaving preview tab (1GB RAM optimization)
            webViewRef.value?.apply {
                stopLoading()
                loadUrl("about:blank")
                destroy()
            }
            webViewRef.value = null
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedButton(onClick = { desktopMode = false }) {
                    Icon(Icons.Default.PhoneAndroid, contentDescription = "Mobile preview")
                    Spacer(Modifier.width(4.dp))
                    Text("Mobile", fontSize = 11.sp)
                }
                OutlinedButton(onClick = { desktopMode = true }) {
                    Icon(Icons.Default.Computer, contentDescription = "Desktop preview")
                    Spacer(Modifier.width(4.dp))
                    Text("Desktop", fontSize = 11.sp)
                }
            }
            Row {
                IconButton(onClick = { manualReloadKey++ }) {
                    Icon(Icons.Default.Refresh, contentDescription = "Reload preview")
                }
                IconButton(onClick = onClearConsole) {
                    Icon(Icons.Default.CleaningServices, contentDescription = "Clear JS console")
                }
            }
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.TopCenter,
        ) {
            AndroidView(
                factory = {
                    WebView(context).apply {
                        settings.javaScriptEnabled = true
                        settings.allowFileAccess = false
                        settings.allowContentAccess = false
                        settings.domStorageEnabled = false
                        settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                        settings.useWideViewPort = desktopMode
                        settings.loadWithOverviewMode = desktopMode
                        webViewClient = object : WebViewClient() {
                            override fun shouldOverrideUrlLoading(
                                view: WebView?,
                                request: WebResourceRequest?,
                            ): Boolean = true // Keep untrusted navigation locked inside sandbox
                        }
                        webChromeClient = object : WebChromeClient() {
                            override fun onConsoleMessage(cm: ConsoleMessage?): Boolean {
                                cm?.let {
                                    onConsoleLog("[${it.messageLevel()}] L${it.lineNumber()}: ${it.message()}")
                                }
                                return true
                            }
                        }
                        loadDataWithBaseURL("https://preview.xthrex.local/", assembledHtml, "text/html", "UTF-8", null)
                        webViewRef.value = this
                    }
                },
                update = { wv ->
                    wv.settings.useWideViewPort = desktopMode
                    wv.settings.loadWithOverviewMode = desktopMode
                    wv.loadDataWithBaseURL("https://preview.xthrex.local/", assembledHtml, "text/html", "UTF-8", null)
                },
                modifier = if (desktopMode) Modifier.fillMaxSize() else Modifier.width(380.dp).fillMaxSize(),
            )
        }

        // JS Console Output Drawer
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF070B14)),
            modifier = Modifier.fillMaxWidth().height(128.dp),
        ) {
            Column(modifier = Modifier.padding(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("JS Console (${consoleLogs.size})", style = MaterialTheme.typography.labelSmall, color = NeonCyan)
                    val errText = consoleLogs.lastOrNull { it.contains("ERROR", ignoreCase = true) }
                    if (errText != null) {
                        OutlinedButton(onClick = { onAskAiError(errText) }) {
                            Text("ASK AI ABOUT THIS ERROR", fontSize = 10.sp)
                        }
                    }
                }
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(consoleLogs) { log ->
                        Text(
                            text = log,
                            fontFamily = JetBrainsMonoFamily,
                            fontSize = 11.sp,
                            color = if (log.contains("ERROR", ignoreCase = true)) CoralError else Color(0xFFCBD5E1),
                        )
                    }
                }
            }
        }
    }
}

private fun assembleSandboxedHtml(rootDir: File, desktopMode: Boolean): String {
    val htmlFile = File(rootDir, "index.html")
    if (!htmlFile.exists()) {
        return "<html><body style='background:#0a0e1a;color:#94a3b8;font-family:sans-serif;padding:24px'><h3>No index.html found</h3><p>Create <code>index.html</code> in your workspace to live preview HTML/CSS/JS.</p></body></html>"
    }
    var html = htmlFile.readText(Charsets.UTF_8)
    // Inline local CSS & JS links safely without exposing file:// URLs
    val cssFile = File(rootDir, "styles.css").takeIf { it.exists() }
        ?: File(rootDir, "style.css").takeIf { it.exists() }
    if (cssFile != null) {
        html = html.replace(
            Regex("""<link[^>]*href=["'](?:\./)?(?:styles|style)\.css["'][^>]*>"""),
            "<style>\n${cssFile.readText(Charsets.UTF_8)}\n</style>",
        )
    }
    val jsFile = File(rootDir, "app.js").takeIf { it.exists() }
        ?: File(rootDir, "script.js").takeIf { it.exists() }
        ?: File(rootDir, "main.js").takeIf { it.exists() }
    if (jsFile != null) {
        html = html.replace(
            Regex("""<script[^>]*src=["'](?:\./)?(?:app|script|main)\.js["'][^>]*>\s*</script>"""),
            "<script>\n${jsFile.readText(Charsets.UTF_8)}\n</script>",
        )
    }
    if (desktopMode && !html.contains("width=1024")) {
        html = html.replace(
            "<head>",
            "<head><meta name=\"viewport\" content=\"width=1024, initial-scale=0.45\">",
        )
    }
    return html
}

@Composable
fun WorkspaceTerminalPanel(
    state: XThrexUiState,
    onRunCommand: (String) -> Unit,
    onAddTab: () -> Unit,
    onSelectTab: (String) -> Unit,
    onClear: () -> Unit,
    onAskAiAboutError: (String) -> Unit,
) {
    var cmdInput by remember { mutableStateOf("") }
    val activeLines: List<TerminalLine> = state.terminalLogs[state.activeTerminalTab].orEmpty()
    val lastErrorBlock = activeLines.filter { it.isError }.takeLast(4).joinToString("\n") { it.text }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF060A13))
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // Terminal tabs & actions
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                modifier = Modifier.weight(1f).horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                state.terminalTabs.forEach { tab ->
                    Surface(
                        color = if (tab == state.activeTerminalTab) MaterialTheme.colorScheme.primaryContainer else Color(0xFF111827),
                        shape = RoundedCornerShape(8.dp),
                        onClick = { onSelectTab(tab) },
                    ) {
                        Text(
                            text = "[$tab]",
                            fontFamily = JetBrainsMonoFamily,
                            fontSize = 11.sp,
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        )
                    }
                }
                IconButton(onClick = onAddTab) {
                    Icon(Icons.Default.Add, contentDescription = "New terminal tab", tint = NeonCyan)
                }
            }
            Row {
                IconButton(onClick = onClear) {
                    Icon(Icons.Default.CleaningServices, contentDescription = "Clear terminal", tint = Color(0xFF94A3B8))
                }
            }
        }

        // Quick safe commands
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            listOf("ls", "tree", "pwd", "./gradlew assembleDebug", "npm test", "help").forEach { preset ->
                AssistChip(
                    onClick = { onRunCommand(preset) },
                    label = { Text(preset, fontFamily = JetBrainsMonoFamily, fontSize = 10.sp) },
                )
            }
        }

        // Terminal output stream
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .border(1.dp, Color(0xFF1E293B), RoundedCornerShape(10.dp))
                .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            items(activeLines, key = { it.id }) { line ->
                Text(
                    text = line.text,
                    fontFamily = JetBrainsMonoFamily,
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                    color = when {
                        line.isError -> CoralError
                        line.isCommand -> NeonCyan
                        else -> Color(0xFFE2E8F0)
                    },
                )
            }
        }

        if (lastErrorBlock.isNotBlank()) {
            OutlinedButton(
                onClick = { onAskAiAboutError(lastErrorBlock) },
                modifier = Modifier.fillMaxWidth().testTag("ask_ai_terminal_error_btn"),
            ) {
                Text("ASK AI ABOUT THIS ERROR", color = NeonCyan)
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedTextField(
                value = cmdInput,
                onValueChange = { cmdInput = it },
                placeholder = { Text("Enter workspace or remote container command...", fontSize = 12.sp) },
                singleLine = true,
                modifier = Modifier.weight(1f).testTag("terminal_command_input"),
            )
            IconButton(
                enabled = !state.terminalCommandRunning && cmdInput.isNotBlank(),
                onClick = {
                    onRunCommand(cmdInput)
                    cmdInput = ""
                },
                modifier = Modifier.testTag("terminal_run_btn"),
            ) {
                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Execute command", tint = NeonCyan)
            }
        }
    }
}

package com.example.engine

import com.example.data.model.DiagnosticIssue
import com.example.data.model.ProjectType
import com.example.security.SecretRedactor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

/**
 * Real static project validator detecting syntax issues, malformed JSON/XML,
 * broken local HTML/JS asset links, missing Android Gradle manifests, and leaked secrets.
 */
object ProjectDiagnostics {

    suspend fun scanWorkspace(
        projectDir: File,
        projectType: ProjectType,
        knownSecrets: List<String>,
    ): List<DiagnosticIssue> = withContext(Dispatchers.IO) {
        val issues = mutableListOf<DiagnosticIssue>()
        val files = projectDir.walkTopDown().filter { it.isFile }.toList()
        val relativeSet = files.map { it.relativeTo(projectDir).path.replace(File.separatorChar, '/') }.toSet()

        if (files.isEmpty()) {
            issues.add(
                DiagnosticIssue(
                    id = UUID.randomUUID().toString(),
                    severity = "ERROR",
                    filePath = "(workspace)",
                    line = 1,
                    message = "Project workspace has no source files.",
                    category = "Missing Files",
                )
            )
            return@withContext issues
        }

        if (projectType.supportsCloudApkBuild) {
            val hasManifest = relativeSet.any { it.endsWith("AndroidManifest.xml") }
            val hasGradle = relativeSet.any { it.endsWith("build.gradle") || it.endsWith("build.gradle.kts") }
            if (!hasManifest && projectType != ProjectType.FLUTTER) {
                issues.add(
                    DiagnosticIssue(
                        id = UUID.randomUUID().toString(),
                        severity = "ERROR",
                        filePath = "app/src/main/AndroidManifest.xml",
                        line = 1,
                        message = "Missing AndroidManifest.xml required for APK builds.",
                        category = "Missing Files",
                    )
                )
            }
            if (!hasGradle && projectType != ProjectType.FLUTTER) {
                issues.add(
                    DiagnosticIssue(
                        id = UUID.randomUUID().toString(),
                        severity = "ERROR",
                        filePath = "app/build.gradle.kts",
                        line = 1,
                        message = "Missing Gradle build configuration.",
                        category = "Build Config",
                    )
                )
            }
        }

        files.filter { it.length() <= 256 * 1024 }.forEach { file ->
            val rel = file.relativeTo(projectDir).path.replace(File.separatorChar, '/')
            val ext = file.extension.lowercase()
            val text = runCatching { file.readText(Charsets.UTF_8) }.getOrNull() ?: return@forEach

            // Check leaked secrets
            val redacted = SecretRedactor.redact(text, knownSecrets)
            if (redacted != text) {
                issues.add(
                    DiagnosticIssue(
                        id = UUID.randomUUID().toString(),
                        severity = "ERROR",
                        filePath = rel,
                        line = 1,
                        message = "Potential API key or secret token found in source file.",
                        category = "Security",
                    )
                )
            }

            when (ext) {
                "json" -> {
                    val trimmed = text.trim()
                    runCatching {
                        if (trimmed.startsWith("[")) JSONArray(trimmed) else JSONObject(trimmed)
                    }.onFailure { err ->
                        issues.add(
                            DiagnosticIssue(
                                id = UUID.randomUUID().toString(),
                                severity = "ERROR",
                                filePath = rel,
                                line = 1,
                                message = "Invalid JSON syntax: ${err.message}",
                                category = "Syntax",
                            )
                        )
                    }
                }
                "kt", "java", "js", "ts", "jsx", "tsx", "css", "c", "cpp", "php" -> {
                    checkBracketBalance(rel, text)?.let { issues.add(it) }
                }
                "html", "htm" -> {
                    checkHtmlReferences(rel, text, relativeSet, issues)
                }
                "xml" -> {
                    if (!text.contains("<") || !text.contains(">")) {
                        issues.add(
                            DiagnosticIssue(
                                id = UUID.randomUUID().toString(),
                                severity = "ERROR",
                                filePath = rel,
                                line = 1,
                                message = "Malformed XML file (missing tags).",
                                category = "Syntax",
                            )
                        )
                    }
                }
            }
        }

        issues
    }

    private fun checkBracketBalance(relPath: String, content: String): DiagnosticIssue? {
        var curly = 0
        var paren = 0
        var square = 0
        var inSingleQuote = false
        var inDoubleQuote = false
        var escaped = false

        content.forEach { ch ->
            if (escaped) {
                escaped = false
                return@forEach
            }
            if (ch == '\\') {
                escaped = true
                return@forEach
            }
            if (ch == '"' && !inSingleQuote) {
                inDoubleQuote = !inDoubleQuote
                return@forEach
            }
            if (ch == '\'' && !inDoubleQuote) {
                inSingleQuote = !inSingleQuote
                return@forEach
            }
            if (!inSingleQuote && !inDoubleQuote) {
                when (ch) {
                    '{' -> curly++
                    '}' -> curly--
                    '(' -> paren++
                    ')' -> paren--
                    '[' -> square++
                    ']' -> square--
                }
            }
        }

        return when {
            curly != 0 -> DiagnosticIssue(
                id = UUID.randomUUID().toString(),
                severity = "ERROR",
                filePath = relPath,
                line = content.lines().size,
                message = "Unbalanced curly braces '{ }' (delta=$curly).",
                category = "Syntax",
            )
            paren != 0 -> DiagnosticIssue(
                id = UUID.randomUUID().toString(),
                severity = "ERROR",
                filePath = relPath,
                line = content.lines().size,
                message = "Unbalanced parentheses '( )' (delta=$paren).",
                category = "Syntax",
            )
            square != 0 -> DiagnosticIssue(
                id = UUID.randomUUID().toString(),
                severity = "WARNING",
                filePath = relPath,
                line = content.lines().size,
                message = "Unbalanced brackets '[ ]' (delta=$square).",
                category = "Syntax",
            )
            else -> null
        }
    }

    private fun checkHtmlReferences(
        relPath: String,
        html: String,
        workspaceFiles: Set<String>,
        out: MutableList<DiagnosticIssue>,
    ) {
        val dirPrefix = relPath.substringBeforeLast('/', "")
        val refRegex = Regex("""(?:src|href)\s*=\s*"([^"]+)"""")
        refRegex.findAll(html).forEach { match ->
            val target = match.groupValues[1]
            if (target.startsWith("http://") || target.startsWith("https://") ||
                target.startsWith("#") || target.startsWith("data:")
            ) {
                return@forEach
            }
            val resolved = if (dirPrefix.isBlank()) target.trimStart('/') else "$dirPrefix/${target.trimStart('/')}"
            if (resolved !in workspaceFiles && target !in workspaceFiles) {
                out.add(
                    DiagnosticIssue(
                        id = UUID.randomUUID().toString(),
                        severity = "WARNING",
                        filePath = relPath,
                        line = 1,
                        message = "Referenced local asset '$target' not found in workspace.",
                        category = "Broken Import",
                    )
                )
            }
        }
    }
}

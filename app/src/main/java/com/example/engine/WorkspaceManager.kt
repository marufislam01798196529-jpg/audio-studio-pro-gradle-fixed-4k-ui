package com.example.engine

import android.content.Context
import android.net.Uri
import com.example.data.model.ProjectType
import com.example.data.model.WorkspaceFileNode
import com.example.security.SecretRedactor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

data class ImportedSourceAnalysis(
    val detectedType: ProjectType,
    val languages: List<String>,
    val dependencies: List<String>,
    val architectureSummary: String,
    val buildConfigDetected: String,
    val issuesDetected: List<String>,
    val fileCount: Int,
)

class WorkspaceManager(private val context: Context) {

    private val workspacesRoot = File(context.filesDir, "workspaces").apply { mkdirs() }
    private val backupsRoot = File(context.filesDir, "backups").apply { mkdirs() }

    fun getProjectDir(projectId: String): File =
        File(workspacesRoot, projectId).apply { mkdirs() }

    fun deleteProjectDir(projectId: String) {
        File(workspacesRoot, projectId).deleteRecursively()
        File(backupsRoot, projectId).deleteRecursively()
    }

    suspend fun initializeTemplate(
        projectId: String,
        projectName: String,
        type: ProjectType,
        description: String,
    ) = withContext(Dispatchers.IO) {
        val dir = getProjectDir(projectId)
        when (type) {
            ProjectType.HTML_CSS_JS, ProjectType.WEBSITE -> {
                writeFile(dir, "index.html", """
                    <!DOCTYPE html>
                    <html lang="en">
                    <head>
                      <meta charset="UTF-8" />
                      <meta name="viewport" content="width=device-width, initial-scale=1.0" />
                      <title>$projectName</title>
                      <link rel="stylesheet" href="styles.css" />
                    </head>
                    <body>
                      <main class="container">
                        <span class="badge">X-THREX WORKSPACE</span>
                        <h1>$projectName</h1>
                        <p>${description.ifBlank { "Live mobile-first web project ready for AI editing." }}</p>
                        <button id="actionBtn">Run Interactive Check</button>
                        <p id="statusText" class="status"></p>
                      </main>
                      <script src="app.js"></script>
                    </body>
                    </html>
                """.trimIndent())
                writeFile(dir, "styles.css", """
                    :root {
                      --bg: #0a0e1a;
                      --card: #111726;
                      --accent: #06b6d4;
                      --violet: #8b5cf6;
                      --text: #f1f5f9;
                    }
                    * { box-sizing: border-box; margin: 0; padding: 0; }
                    body {
                      font-family: system-ui, -apple-system, sans-serif;
                      background: radial-gradient(circle at top right, #1e1b4b, var(--bg));
                      color: var(--text);
                      min-height: 100vh;
                      display: grid;
                      place-items: center;
                      padding: 20px;
                    }
                    .container {
                      background: var(--card);
                      border: 1px solid #2b3757;
                      border-radius: 18px;
                      padding: 28px;
                      max-width: 480px;
                      width: 100%;
                      box-shadow: 0 20px 50px rgba(0, 0, 0, 0.45);
                    }
                    .badge {
                      display: inline-block;
                      font-size: 11px;
                      letter-spacing: 0.1em;
                      color: var(--accent);
                      background: rgba(6, 182, 212, 0.12);
                      padding: 4px 10px;
                      border-radius: 999px;
                      margin-bottom: 14px;
                    }
                    h1 { font-size: 26px; margin-bottom: 10px; }
                    p { color: #94a3b8; line-height: 1.5; margin-bottom: 18px; }
                    button {
                      width: 100%;
                      padding: 12px 16px;
                      border: none;
                      border-radius: 12px;
                      background: linear-gradient(135deg, var(--violet), var(--accent));
                      color: white;
                      font-weight: 600;
                      cursor: pointer;
                    }
                    .status { margin-top: 14px; color: #34d399; font-size: 13px; }
                """.trimIndent())
                writeFile(dir, "app.js", """
                    const btn = document.getElementById('actionBtn');
                    const status = document.getElementById('statusText');
                    btn?.addEventListener('click', () => {
                      const ts = new Date().toLocaleTimeString();
                      status.textContent = 'Runtime verified at ' + ts;
                      console.log('[X-THREX Preview] Interactive check passed at', ts);
                    });
                """.trimIndent())
            }
            ProjectType.ANDROID_GRADLE, ProjectType.KOTLIN, ProjectType.JAVA -> {
                val pkg = "com.xthrex.${projectName.lowercase().replace(Regex("[^a-z0-9]"), "").ifBlank { "app" }}"
                val pkgPath = pkg.replace('.', '/')
                writeFile(dir, "settings.gradle.kts", """
                    rootProject.name = "$projectName"
                    include(":app")
                """.trimIndent())
                writeFile(dir, "build.gradle.kts", """
                    plugins {
                        id("com.android.application") version "8.7.0" apply false
                        id("org.jetbrains.kotlin.android") version "2.1.0" apply false
                    }
                """.trimIndent())
                writeFile(dir, "app/build.gradle.kts", """
                    plugins {
                        id("com.android.application")
                        id("org.jetbrains.kotlin.android")
                    }
                    android {
                        namespace = "$pkg"
                        compileSdk = 35
                        defaultConfig {
                            applicationId = "$pkg"
                            minSdk = 24
                            targetSdk = 35
                            versionCode = 1
                            versionName = "1.0"
                        }
                    }
                    dependencies {
                        implementation("androidx.core:core-ktx:1.15.0")
                        implementation("androidx.appcompat:appcompat:1.7.0")
                    }
                """.trimIndent())
                writeFile(dir, "app/src/main/AndroidManifest.xml", """
                    <?xml version="1.0" encoding="utf-8"?>
                    <manifest xmlns:android="http://schemas.android.com/apk/res/android">
                        <application
                            android:label="$projectName"
                            android:supportsRtl="true">
                            <activity
                                android:name=".MainActivity"
                                android:exported="true">
                                <intent-filter>
                                    <action android:name="android.intent.action.MAIN" />
                                    <category android:name="android.intent.category.LAUNCHER" />
                                </intent-filter>
                            </activity>
                        </application>
                    </manifest>
                """.trimIndent())
                writeFile(dir, "app/src/main/java/$pkgPath/MainActivity.kt", """
                    package $pkg

                    import android.os.Bundle
                    import android.widget.TextView
                    import androidx.appcompat.app.AppCompatActivity

                    class MainActivity : AppCompatActivity() {
                        override fun onCreate(savedInstanceState: Bundle?) {
                            super.onCreate(savedInstanceState)
                            val label = TextView(this).apply {
                                text = "$projectName initialized"
                                textSize = 20f
                                setPadding(48, 64, 48, 48)
                            }
                            setContentView(label)
                        }
                    }
                """.trimIndent())
            }
            ProjectType.PYTHON -> {
                writeFile(dir, "main.py", """
                    def main():
                        print("X-THREX Python workspace: $projectName")

                    if __name__ == "__main__":
                        main()
                """.trimIndent())
                writeFile(dir, "requirements.txt", "# Add Python dependencies here\n")
            }
            ProjectType.NODE_JS -> {
                writeFile(dir, "package.json", """
                    {
                      "name": "${projectName.lowercase().replace(" ", "-")}",
                      "version": "1.0.0",
                      "main": "index.js",
                      "scripts": {
                        "start": "node index.js"
                      }
                    }
                """.trimIndent())
                writeFile(dir, "index.js", """
                    console.log("X-THREX Node.js workspace ready: $projectName");
                """.trimIndent())
            }
            else -> {
                writeFile(dir, "README.md", "# $projectName\n\nProject Type: ${type.label}\n\n$description\n")
                writeFile(dir, "src/main.${type.primaryExt}", "// $projectName entry point\n")
            }
        }
        writeFile(
            dir,
            "README.md",
            "# $projectName\n\nType: ${type.label}\n\n$description\n",
        )
    }

    suspend fun listFiles(projectId: String): List<WorkspaceFileNode> = withContext(Dispatchers.IO) {
        val root = getProjectDir(projectId)
        val result = mutableListOf<WorkspaceFileNode>()
        fun walk(dir: File, depth: Int) {
            val children = dir.listFiles()?.sortedWith(
                compareBy<File> { !it.isDirectory }.thenBy { it.name.lowercase() }
            ) ?: return
            for (child in children) {
                val rel = child.relativeTo(root).path.replace(File.separatorChar, '/')
                result.add(
                    WorkspaceFileNode(
                        relativePath = rel,
                        name = child.name,
                        isDirectory = child.isDirectory,
                        sizeBytes = if (child.isDirectory) 0L else child.length(),
                        lastModified = child.lastModified(),
                        depth = depth,
                    )
                )
                if (child.isDirectory && result.size < 600) {
                    walk(child, depth + 1)
                }
            }
        }
        walk(root, 0)
        result
    }

    suspend fun readFileChunked(
        projectId: String,
        relativePath: String,
        lowMemoryMode: Boolean,
    ): Pair<String, Boolean> = withContext(Dispatchers.IO) {
        val file = resolveSafeFile(projectId, relativePath) ?: return@withContext "" to false
        if (!file.exists() || file.isDirectory) return@withContext "" to false
        val maxBytes = if (lowMemoryMode) 32 * 1024 else 192 * 1024
        val totalBytes = file.length()
        val truncated = totalBytes > maxBytes
        val buffer = ByteArray(minOf(totalBytes.toInt().coerceAtLeast(0), maxBytes))
        FileInputStream(file).use { input ->
            var offset = 0
            while (offset < buffer.size) {
                val read = input.read(buffer, offset, buffer.size - offset)
                if (read <= 0) break
                offset += read
            }
            String(buffer, 0, offset, Charsets.UTF_8) to truncated
        }
    }

    suspend fun writeProjectFile(
        projectId: String,
        relativePath: String,
        content: String,
    ): Boolean = withContext(Dispatchers.IO) {
        val target = resolveSafeFile(projectId, relativePath) ?: return@withContext false
        target.parentFile?.mkdirs()
        target.writeText(content, Charsets.UTF_8)
        true
    }

    suspend fun createFolder(projectId: String, relativePath: String): Boolean = withContext(Dispatchers.IO) {
        val target = resolveSafeFile(projectId, relativePath) ?: return@withContext false
        target.mkdirs()
    }

    suspend fun renameOrMove(
        projectId: String,
        oldRelPath: String,
        newRelPath: String,
    ): Boolean = withContext(Dispatchers.IO) {
        val src = resolveSafeFile(projectId, oldRelPath) ?: return@withContext false
        val dst = resolveSafeFile(projectId, newRelPath) ?: return@withContext false
        if (!src.exists()) return@withContext false
        dst.parentFile?.mkdirs()
        src.renameTo(dst)
    }

    suspend fun duplicateFile(projectId: String, relativePath: String): String? = withContext(Dispatchers.IO) {
        val src = resolveSafeFile(projectId, relativePath) ?: return@withContext null
        if (!src.exists() || src.isDirectory) return@withContext null
        val dot = relativePath.lastIndexOf('.')
        val copyPath = if (dot > 0) {
            "${relativePath.substring(0, dot)}_copy${relativePath.substring(dot)}"
        } else {
            "${relativePath}_copy"
        }
        val dst = resolveSafeFile(projectId, copyPath) ?: return@withContext null
        src.copyTo(dst, overwrite = true)
        copyPath
    }

    suspend fun deleteEntry(projectId: String, relativePath: String): Boolean = withContext(Dispatchers.IO) {
        val target = resolveSafeFile(projectId, relativePath) ?: return@withContext false
        if (!target.exists()) return@withContext false
        target.deleteRecursively()
    }

    suspend fun searchCode(projectId: String, query: String): List<Pair<String, String>> =
        withContext(Dispatchers.IO) {
            if (query.isBlank()) return@withContext emptyList()
            val root = getProjectDir(projectId)
            val hits = mutableListOf<Pair<String, String>>()
            root.walkTopDown()
                .filter { it.isFile && it.length() < 256 * 1024 }
                .forEach { file ->
                    if (hits.size >= 40) return@forEach
                    val rel = file.relativeTo(root).path.replace(File.separatorChar, '/')
                    runCatching {
                        file.bufferedReader().useLines { lines ->
                            lines.forEachIndexed { idx, line ->
                                if (line.contains(query, ignoreCase = true) && hits.size < 40) {
                                    hits.add(rel to "L${idx + 1}: ${line.trim().take(110)}")
                                }
                            }
                        }
                    }
                }
            hits
        }

    suspend fun createSnapshotBackup(
        projectId: String,
        label: String,
        knownSecrets: List<String>,
    ): Pair<String, Int> = withContext(Dispatchers.IO) {
        val projectDir = getProjectDir(projectId)
        val backupDir = File(backupsRoot, projectId).apply { mkdirs() }
        val snapFile = File(backupDir, "snap_${System.currentTimeMillis()}.zip")
        var count = 0
        ZipOutputStream(BufferedOutputStream(FileOutputStream(snapFile), 16 * 1024)).use { zos ->
            projectDir.walkTopDown().filter { it.isFile }.forEach { file ->
                val rel = file.relativeTo(projectDir).path.replace(File.separatorChar, '/')
                if (!SecretRedactor.isSensitiveExportFile(rel)) {
                    zos.putNextEntry(ZipEntry(rel))
                    copyFileSanitized(file, zos, knownSecrets)
                    zos.closeEntry()
                    count++
                }
            }
        }
        snapFile.absolutePath to count
    }

    suspend fun restoreSnapshot(projectId: String, backupZipPath: String): Boolean =
        withContext(Dispatchers.IO) {
            val zipFile = File(backupZipPath)
            if (!zipFile.exists()) return@withContext false
            val projectDir = getProjectDir(projectId)
            projectDir.deleteRecursively()
            projectDir.mkdirs()
            val canonicalRoot = projectDir.canonicalPath
            ZipInputStream(BufferedInputStream(FileInputStream(zipFile), 16 * 1024)).use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                    if (!entry.isDirectory) {
                        val outFile = File(projectDir, entry.name)
                        if (outFile.canonicalPath.startsWith(canonicalRoot)) {
                            outFile.parentFile?.mkdirs()
                            FileOutputStream(outFile).use { out ->
                                zis.copyTo(out, bufferSize = 16 * 1024)
                            }
                        }
                    }
                    zis.closeEntry()
                    entry = zis.nextEntry
                }
            }
            true
        }

    /**
     * Organized Project ZIP Export adhering to:
     * MyProject/app/, src/, assets/, res/, scripts/, documentation/, README.md, project-config.json, BUILD-INSTRUCTIONS.md
     * Always redacts API keys & excludes sensitive files.
     */
    suspend fun exportProjectZip(
        projectId: String,
        projectName: String,
        projectType: ProjectType,
        outputStream: OutputStream,
        knownSecrets: List<String>,
        selectedFilesOnly: Set<String>? = null,
    ): Int = withContext(Dispatchers.IO) {
        val projectDir = getProjectDir(projectId)
        val safeRootName = projectName.replace(Regex("[^A-Za-z0-9_\\-]"), "_").ifBlank { "XThrexProject" }
        var exportedCount = 0

        ZipOutputStream(BufferedOutputStream(outputStream, 16 * 1024)).use { zos ->
            val trackedPaths = mutableSetOf<String>()
            projectDir.walkTopDown().filter { it.isFile }.forEach { file ->
                val rel = file.relativeTo(projectDir).path.replace(File.separatorChar, '/')
                if (SecretRedactor.isSensitiveExportFile(rel)) return@forEach
                if (selectedFilesOnly != null && rel !in selectedFilesOnly) return@forEach

                val entryName = "$safeRootName/$rel"
                trackedPaths.add(rel)
                zos.putNextEntry(ZipEntry(entryName))
                copyFileSanitized(file, zos, knownSecrets)
                zos.closeEntry()
                exportedCount++
            }

            if (selectedFilesOnly == null) {
                if ("project-config.json" !in trackedPaths) {
                    val cfg = JSONObject()
                        .put("name", projectName)
                        .put("type", projectType.name)
                        .put("exportedBy", "X-THREX")
                        .put("secretsIncluded", false)
                        .toString(2)
                    zos.putNextEntry(ZipEntry("$safeRootName/project-config.json"))
                    zos.write(cfg.toByteArray(Charsets.UTF_8))
                    zos.closeEntry()
                }
                if ("BUILD-INSTRUCTIONS.md" !in trackedPaths) {
                    val buildDoc = """
                        # Build Instructions - $projectName

                        - Project Type: ${projectType.label}
                        - Exported safely with X-THREX (all API keys and signing secrets excluded).

                        ## Running / Building
                        ${if (projectType.supportsCloudApkBuild) "Run `./gradlew assembleDebug` with Android SDK & JDK 17+ configured, or submit via X-THREX Cloud APK Builder." else if (projectType.supportsLiveWebPreview) "Open `index.html` in a modern browser or serve the directory with any static web server." else "Install project dependencies listed in the manifest and execute the main entry file."}
                    """.trimIndent()
                    zos.putNextEntry(ZipEntry("$safeRootName/BUILD-INSTRUCTIONS.md"))
                    zos.write(buildDoc.toByteArray(Charsets.UTF_8))
                    zos.closeEntry()
                }
                if ("documentation/ARCHITECTURE.md" !in trackedPaths) {
                    val archDoc = "# Architecture Overview\n\nGenerated structure for `$projectName` (${projectType.label}).\nTotal exported source files: $exportedCount.\n"
                    zos.putNextEntry(ZipEntry("$safeRootName/documentation/ARCHITECTURE.md"))
                    zos.write(archDoc.toByteArray(Charsets.UTF_8))
                    zos.closeEntry()
                }
            }
        }
        exportedCount
    }

    suspend fun importZipFromUri(
        projectId: String,
        uri: Uri,
    ): ImportedSourceAnalysis = withContext(Dispatchers.IO) {
        val projectDir = getProjectDir(projectId)
        val input = context.contentResolver.openInputStream(uri)
            ?: error("Unable to open selected ZIP archive.")
        input.use { stream ->
            importZipFromFileStream(projectDir, stream)
        }
    }

    suspend fun importZipFile(
        projectId: String,
        zipFile: File,
    ): ImportedSourceAnalysis = withContext(Dispatchers.IO) {
        val projectDir = getProjectDir(projectId)
        FileInputStream(zipFile).use { stream ->
            importZipFromFileStream(projectDir, stream)
        }
    }

    private fun importZipFromFileStream(
        projectDir: File,
        stream: java.io.InputStream,
    ): ImportedSourceAnalysis {
        val canonicalRoot = projectDir.canonicalPath
        val rawPaths = mutableListOf<String>()
        var totalBytesWritten = 0L
        val maxTotalBytes = 48L * 1024L * 1024L // 48MB zip extraction cap for 1GB RAM safety

        ZipInputStream(BufferedInputStream(stream, 16 * 1024)).use { zis ->
            var entry = zis.nextEntry
            val buf = ByteArray(16 * 1024)
            while (entry != null) {
                val name = entry.name.replace('\\', '/').trimStart('/')
                if (!entry.isDirectory && name.isNotBlank() && !name.contains("..")) {
                    rawPaths.add(name)
                    val outFile = File(projectDir, name)
                    if (outFile.canonicalPath.startsWith(canonicalRoot)) {
                        outFile.parentFile?.mkdirs()
                        FileOutputStream(outFile).use { out ->
                            var read = zis.read(buf)
                            while (read > 0) {
                                totalBytesWritten += read
                                if (totalBytesWritten > maxTotalBytes) {
                                    error("Archive exceeds 48MB safe workspace limit.")
                                }
                                out.write(buf, 0, read)
                                read = zis.read(buf)
                            }
                        }
                    }
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }

        // Strip common single top-level wrapper folder (e.g. GitHub zipball root folder)
        val topDirs = projectDir.listFiles()?.toList().orEmpty()
        if (topDirs.size == 1 && topDirs[0].isDirectory) {
            val wrapper = topDirs[0]
            wrapper.listFiles()?.forEach { child ->
                child.renameTo(File(projectDir, child.name))
            }
            wrapper.deleteRecursively()
        }

        return analyzeWorkspaceDirectory(projectDir)
    }

    fun analyzeWorkspaceDirectory(projectDir: File): ImportedSourceAnalysis {
        val files = projectDir.walkTopDown().filter { it.isFile }.toList()
        val paths = files.map { it.relativeTo(projectDir).path.replace(File.separatorChar, '/') }
        val exts = files.map { it.extension.lowercase() }.toSet()

        val languages = buildList {
            if ("kt" in exts || "kts" in exts) add("Kotlin")
            if ("java" in exts) add("Java")
            if ("html" in exts || "htm" in exts) add("HTML")
            if ("css" in exts) add("CSS")
            if ("js" in exts || "mjs" in exts) add("JavaScript")
            if ("ts" in exts || "tsx" in exts) add("TypeScript")
            if ("py" in exts) add("Python")
            if ("dart" in exts) add("Dart / Flutter")
            if ("cpp" in exts || "c" in exts || "h" in exts) add("C / C++")
            if ("php" in exts) add("PHP")
            if ("xml" in exts) add("XML")
        }

        val hasGradle = paths.any { it.endsWith("build.gradle") || it.endsWith("build.gradle.kts") }
        val hasManifest = paths.any { it.endsWith("AndroidManifest.xml") }
        val hasPubspec = paths.any { it.endsWith("pubspec.yaml") }
        val hasPackageJson = paths.any { it.endsWith("package.json") }
        val hasRequirements = paths.any { it.endsWith("requirements.txt") || it.endsWith("pyproject.toml") }
        val hasIndexHtml = paths.any { it.equals("index.html", ignoreCase = true) || it.endsWith("/index.html") }

        val detectedType = when {
            hasPubspec -> ProjectType.FLUTTER
            hasGradle && hasManifest -> ProjectType.ANDROID_GRADLE
            "kt" in exts -> ProjectType.KOTLIN
            "java" in exts -> ProjectType.JAVA
            hasPackageJson && ("jsx" in exts || "tsx" in exts) -> ProjectType.REACT
            hasPackageJson -> ProjectType.NODE_JS
            hasIndexHtml -> ProjectType.HTML_CSS_JS
            hasRequirements || "py" in exts -> ProjectType.PYTHON
            "cpp" in exts || "c" in exts -> ProjectType.CPP
            "php" in exts -> ProjectType.PHP
            else -> ProjectType.HTML_CSS_JS
        }

        val dependencies = mutableListOf<String>()
        files.filter { it.name == "build.gradle" || it.name == "build.gradle.kts" }.take(3).forEach { gf ->
            gf.readLines().take(200).forEach { line ->
                val t = line.trim()
                if (t.startsWith("implementation(") || t.startsWith("implementation ")) {
                    dependencies.add(t.take(90))
                }
            }
        }
        files.firstOrNull { it.name == "package.json" }?.let { pf ->
            runCatching {
                val obj = JSONObject(pf.readText())
                obj.optJSONObject("dependencies")?.keys()?.forEach { dependencies.add("npm: $it") }
            }
        }
        files.firstOrNull { it.name == "requirements.txt" }?.let { rf ->
            rf.readLines().filter { it.isNotBlank() && !it.startsWith("#") }.take(20).forEach {
                dependencies.add("pip: $it")
            }
        }

        val buildConfig = when {
            hasGradle -> "Gradle Android Build (${paths.firstOrNull { it.contains("build.gradle") }})"
            hasPubspec -> "Flutter pubspec.yaml"
            hasPackageJson -> "Node / npm package.json"
            hasRequirements -> "Python pip requirements"
            hasIndexHtml -> "Static Web Bundle (index.html)"
            else -> "No explicit build manifest detected"
        }

        val issues = mutableListOf<String>()
        if (detectedType == ProjectType.ANDROID_GRADLE && !hasManifest) {
            issues.add("Missing AndroidManifest.xml")
        }
        if (files.isEmpty()) {
            issues.add("Workspace contains 0 files")
        }

        val archSummary = "Detected ${detectedType.label} project with ${files.size} files across ${languages.joinToString(", ").ifBlank { "plain text" }}."
        return ImportedSourceAnalysis(
            detectedType = detectedType,
            languages = languages,
            dependencies = dependencies.distinct().take(25),
            architectureSummary = archSummary,
            buildConfigDetected = buildConfig,
            issuesDetected = issues,
            fileCount = files.size,
        )
    }

    private fun resolveSafeFile(projectId: String, relativePath: String): File? {
        val root = getProjectDir(projectId)
        val clean = relativePath.replace('\\', '/').trimStart('/')
        if (clean.isBlank() || clean.contains("..")) return null
        val target = File(root, clean)
        if (!target.canonicalPath.startsWith(root.canonicalPath)) return null
        return target
    }

    private fun writeFile(root: File, relativePath: String, content: String) {
        val file = File(root, relativePath)
        file.parentFile?.mkdirs()
        file.writeText(content, Charsets.UTF_8)
    }

    private fun copyFileSanitized(
        file: File,
        out: OutputStream,
        knownSecrets: List<String>,
    ) {
        val textExts = setOf(
            "kt", "kts", "java", "xml", "html", "htm", "css", "js", "ts", "tsx", "jsx",
            "json", "md", "txt", "py", "dart", "gradle", "properties", "yaml", "yml", "c", "cpp", "h", "php"
        )
        if (file.extension.lowercase() in textExts && file.length() <= 512 * 1024) {
            val sanitized = SecretRedactor.redact(file.readText(Charsets.UTF_8), knownSecrets)
            out.write(sanitized.toByteArray(Charsets.UTF_8))
        } else {
            FileInputStream(file).use { it.copyTo(out, bufferSize = 16 * 1024) }
        }
    }
}

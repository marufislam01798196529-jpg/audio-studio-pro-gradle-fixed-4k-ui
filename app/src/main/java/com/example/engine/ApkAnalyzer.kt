package com.example.engine

import android.content.Context
import android.net.Uri
import com.example.data.model.ApkAnalysisReport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.ZipInputStream

/**
 * Inspects user-selected APK files safely on-device with strict size caps (1GB RAM friendly),
 * decodes Android Binary XML (AXML) AndroidManifest.xml, scans DEX package prefixes,
 * extracts recoverable resources/assets, and reconstructs an editable workspace skeleton.
 */
class ApkAnalyzer(
    private val context: Context,
    private val workspaceManager: WorkspaceManager,
) {

    suspend fun analyzeAndReconstruct(
        uri: Uri,
        displayName: String,
        targetProjectId: String,
    ): ApkAnalysisReport = withContext(Dispatchers.IO) {
        val projectDir = workspaceManager.getProjectDir(targetProjectId)
        val canonicalRoot = projectDir.canonicalPath

        var totalApkBytes = 0L
        val maxApkLimit = 120L * 1024L * 1024L // 120MB APK safety limit
        var manifestBytes: ByteArray? = null
        val dexFiles = mutableListOf<String>()
        val dexPrefixes = linkedSetOf<String>()
        val nativeAbis = linkedSetOf<String>()
        val signatureFiles = mutableListOf<String>()
        var resCount = 0
        var assetCount = 0

        val stream = context.contentResolver.openInputStream(uri)
            ?: error("Unable to read selected APK file.")

        stream.use { raw ->
            ZipInputStream(BufferedInputStream(raw, 16 * 1024)).use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                    val name = entry.name.replace('\\', '/')
                    if (!entry.isDirectory && !name.contains("..")) {
                        when {
                            name == "AndroidManifest.xml" -> {
                                manifestBytes = readLimitedEntry(zis, 2 * 1024 * 1024)
                                totalApkBytes += manifestBytes?.size ?: 0
                            }
                            name.startsWith("classes") && name.endsWith(".dex") -> {
                                dexFiles.add(name)
                                val dexSample = readLimitedEntry(zis, 512 * 1024)
                                totalApkBytes += dexSample.size
                                extractDexPackagePrefixes(dexSample, dexPrefixes)
                            }
                            name.startsWith("lib/") -> {
                                val abi = name.removePrefix("lib/").substringBefore('/')
                                if (abi.isNotBlank()) nativeAbis.add(abi)
                                totalApkBytes += skipAndCount(zis)
                            }
                            name.startsWith("META-INF/") && (
                                name.endsWith(".SF") || name.endsWith(".RSA") ||
                                    name.endsWith(".DSA") || name.endsWith(".EC")
                                ) -> {
                                signatureFiles.add(name)
                                totalApkBytes += skipAndCount(zis)
                            }
                            name.startsWith("assets/") -> {
                                assetCount++
                                // Extract small assets safely (< 1MB) into reconstructed workspace
                                val outFile = File(projectDir, "app/src/main/$name")
                                if (outFile.canonicalPath.startsWith(canonicalRoot) && assetCount <= 120) {
                                    outFile.parentFile?.mkdirs()
                                    val bytes = readLimitedEntry(zis, 1024 * 1024)
                                    totalApkBytes += bytes.size
                                    FileOutputStream(outFile).use { it.write(bytes) }
                                } else {
                                    totalApkBytes += skipAndCount(zis)
                                }
                            }
                            name.startsWith("res/") -> {
                                resCount++
                                if (resCount <= 180 && (name.endsWith(".png") || name.endsWith(".jpg") || name.endsWith(".webp"))) {
                                    val outFile = File(projectDir, "app/src/main/$name")
                                    if (outFile.canonicalPath.startsWith(canonicalRoot)) {
                                        outFile.parentFile?.mkdirs()
                                        val bytes = readLimitedEntry(zis, 512 * 1024)
                                        totalApkBytes += bytes.size
                                        FileOutputStream(outFile).use { it.write(bytes) }
                                    } else {
                                        totalApkBytes += skipAndCount(zis)
                                    }
                                } else {
                                    totalApkBytes += skipAndCount(zis)
                                }
                            }
                            else -> {
                                totalApkBytes += skipAndCount(zis)
                            }
                        }
                        if (totalApkBytes > maxApkLimit) {
                            error("APK exceeds 120MB safety cap for constrained devices.")
                        }
                    }
                    zis.closeEntry()
                    entry = zis.nextEntry
                }
            }
        }

        val decodedManifest = if (manifestBytes != null) {
            decodeBinaryManifest(manifestBytes!!)
        } else {
            DecodedManifest(
                packageName = "unknown.package",
                appName = displayName.substringBeforeLast('.'),
                versionCode = "1",
                versionName = "1.0",
                minSdk = "24",
                targetSdk = "35",
                permissions = emptyList(),
                activities = emptyList(),
                services = emptyList(),
                receivers = emptyList(),
                reconstructedXml = "<!-- AndroidManifest.xml missing or unreadable -->",
            )
        }

        val limitations = "APK analysis inspects binary manifest, DEX package descriptors, and packaged assets/resources. Original Kotlin/Java source code, comments, and original signing keys cannot be recovered from compiled APKs. Missing or obfuscated classes are marked [RECONSTRUCTED]."

        val report = ApkAnalysisReport(
            fileName = displayName,
            fileSizeBytes = totalApkBytes,
            packageName = decodedManifest.packageName,
            applicationName = decodedManifest.appName,
            versionCode = decodedManifest.versionCode,
            versionName = decodedManifest.versionName,
            minSdk = decodedManifest.minSdk,
            targetSdk = decodedManifest.targetSdk,
            permissions = decodedManifest.permissions,
            activities = decodedManifest.activities,
            services = decodedManifest.services,
            receivers = decodedManifest.receivers,
            dexFiles = dexFiles,
            detectedPackagePrefixes = dexPrefixes.take(25),
            resourceFileCount = resCount,
            assetFileCount = assetCount,
            nativeAbis = nativeAbis.toList(),
            signatureFiles = signatureFiles,
            manifestXmlReconstructed = decodedManifest.reconstructedXml,
            limitationsNote = limitations,
        )

        writeReconstructedWorkspace(targetProjectId, report)
        report
    }

    private suspend fun writeReconstructedWorkspace(projectId: String, report: ApkAnalysisReport) {
        workspaceManager.writeProjectFile(
            projectId,
            "app/src/main/AndroidManifest.xml",
            report.manifestXmlReconstructed,
        )
        workspaceManager.writeProjectFile(
            projectId,
            "app/build.gradle.kts",
            """
                // [RECONSTRUCTED] Generated from APK metadata — verify dependencies before cloud build
                plugins {
                    id("com.android.application")
                    id("org.jetbrains.kotlin.android")
                }
                android {
                    namespace = "${report.packageName}"
                    compileSdk = 35
                    defaultConfig {
                        applicationId = "${report.packageName}"
                        minSdk = ${report.minSdk.toIntOrNull() ?: 24}
                        targetSdk = ${report.targetSdk.toIntOrNull() ?: 35}
                        versionCode = ${report.versionCode.toIntOrNull() ?: 1}
                        versionName = "${report.versionName}"
                    }
                }
            """.trimIndent(),
        )

        val primaryActivity = report.activities.firstOrNull() ?: "${report.packageName}.MainActivity"
        val simpleClass = primaryActivity.substringAfterLast('.').ifBlank { "MainActivity" }
        val pkg = report.packageName.ifBlank { "com.reconstructed.app" }
        val pkgPath = pkg.replace('.', '/')
        workspaceManager.writeProjectFile(
            projectId,
            "app/src/main/java/$pkgPath/$simpleClass.kt",
            """
                package $pkg

                import android.os.Bundle
                import android.widget.TextView
                import androidx.appcompat.app.AppCompatActivity

                /**
                 * [RECONSTRUCTED] Entry activity stub generated from APK manifest inspection.
                 * Original compiled bytecode in ${report.dexFiles.joinToString(", ")} is not recoverable as full source.
                 */
                class $simpleClass : AppCompatActivity() {
                    override fun onCreate(savedInstanceState: Bundle?) {
                        super.onCreate(savedInstanceState)
                        val info = TextView(this).apply {
                            text = "${report.applicationName} (Reconstructed Workspace)"
                        }
                        setContentView(info)
                    }
                }
            """.trimIndent(),
        )

        val mdReport = buildString {
            appendLine("# APK Analysis Report: ${report.fileName}")
            appendLine()
            appendLine("- **Package Name**: `${report.packageName}`")
            appendLine("- **App Name**: `${report.applicationName}`")
            appendLine("- **Version**: `${report.versionName}` (code `${report.versionCode}`)")
            appendLine("- **SDK**: minSdk `${report.minSdk}` / targetSdk `${report.targetSdk}`")
            appendLine("- **DEX Files**: ${report.dexFiles.joinToString(", ").ifEmpty { "None" }}")
            appendLine("- **Native ABIs**: ${report.nativeAbis.joinToString(", ").ifEmpty { "None (Pure JVM/DEX)" }}")
            appendLine("- **Resources / Assets**: ${report.resourceFileCount} res entries, ${report.assetFileCount} asset entries")
            appendLine("- **Signatures Found**: ${report.signatureFiles.joinToString(", ").ifEmpty { "Unsigned" }}")
            appendLine()
            appendLine("## Permissions (${report.permissions.size})")
            report.permissions.forEach { appendLine("- `$it`") }
            appendLine()
            appendLine("## Activities (${report.activities.size})")
            report.activities.forEach { appendLine("- `$it`") }
            appendLine()
            appendLine("## Detected DEX Package Prefixes")
            report.detectedPackagePrefixes.forEach { appendLine("- `$it`") }
            appendLine()
            appendLine("## Important Limitations")
            appendLine(report.limitationsNote)
        }
        workspaceManager.writeProjectFile(projectId, "APK-ANALYSIS-REPORT.md", mdReport)
    }

    private fun readLimitedEntry(zis: ZipInputStream, maxBytes: Int): ByteArray {
        val baos = ByteArrayOutputStream()
        val buf = ByteArray(8 * 1024)
        var total = 0
        while (total < maxBytes) {
            val read = zis.read(buf, 0, minOf(buf.size, maxBytes - total))
            if (read <= 0) break
            baos.write(buf, 0, read)
            total += read
        }
        return baos.toByteArray()
    }

    private fun skipAndCount(zis: ZipInputStream): Long {
        val buf = ByteArray(8 * 1024)
        var total = 0L
        while (true) {
            val read = zis.read(buf)
            if (read <= 0) break
            total += read
        }
        return total
    }

    private fun extractDexPackagePrefixes(dexBytes: ByteArray, outPrefixes: MutableSet<String>) {
        // Scan ASCII descriptor tokens like Lcom/example/foo/Bar; safely without loading huge tables
        val text = String(dexBytes, Charsets.ISO_8859_1)
        val regex = Regex("""L([a-z][a-z0-9_]*(?:/[a-z0-9_]+){1,3})/""")
        regex.findAll(text).take(60).forEach { match ->
            val pkg = match.groupValues[1].replace('/', '.')
            if (!pkg.startsWith("android.") && !pkg.startsWith("java.") && !pkg.startsWith("kotlin.")) {
                outPrefixes.add(pkg)
            }
        }
    }

    private data class DecodedManifest(
        val packageName: String,
        val appName: String,
        val versionCode: String,
        val versionName: String,
        val minSdk: String,
        val targetSdk: String,
        val permissions: List<String>,
        val activities: List<String>,
        val services: List<String>,
        val receivers: List<String>,
        val reconstructedXml: String,
    )

    private fun decodeBinaryManifest(bytes: ByteArray): DecodedManifest {
        // Check if plaintext XML (e.g. test fixture or uncompiled manifest)
        val header = if (bytes.size >= 5) String(bytes, 0, 5, Charsets.UTF_8) else ""
        if (header.startsWith("<?xml") || header.startsWith("<mani")) {
            val xml = String(bytes, Charsets.UTF_8)
            val pkg = Regex("""package\s*=\s*"([^"]+)"""").find(xml)?.groupValues?.getOrNull(1) ?: "com.unknown.app"
            val vName = Regex("""versionName\s*=\s*"([^"]+)"""").find(xml)?.groupValues?.getOrNull(1) ?: "1.0"
            val vCode = Regex("""versionCode\s*=\s*"([^"]+)"""").find(xml)?.groupValues?.getOrNull(1) ?: "1"
            val perms = Regex("""uses-permission[^>]*android:name\s*=\s*"([^"]+)"""").findAll(xml).map { it.groupValues[1] }.toList()
            val acts = Regex("""<activity[^>]*android:name\s*=\s*"([^"]+)"""").findAll(xml).map { it.groupValues[1] }.toList()
            return DecodedManifest(pkg, pkg.substringAfterLast('.'), vCode, vName, "24", "35", perms, acts, emptyList(), emptyList(), xml)
        }

        // Parse standard Android Binary XML (AXML)
        return runCatching {
            val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
            val fileType = buf.short.toInt() and 0xFFFF
            if (fileType != 0x0003) error("Not AXML")
            buf.short // headerSize
            val totalSize = buf.int

            val strings = mutableListOf<String>()
            var packageName = "com.unknown.app"
            var appName = "Reconstructed App"
            var versionCode = "1"
            var versionName = "1.0"
            var minSdk = "24"
            var targetSdk = "35"
            val permissions = mutableListOf<String>()
            val activities = mutableListOf<String>()
            val services = mutableListOf<String>()
            val receivers = mutableListOf<String>()

            val xmlOut = StringBuilder("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n")
            var indent = 0

            while (buf.position() + 8 <= minOf(totalSize, bytes.size)) {
                val chunkStart = buf.position()
                val chunkType = buf.short.toInt() and 0xFFFF
                val headerSize = buf.short.toInt() and 0xFFFF
                val chunkSize = buf.int
                if (chunkSize < 8 || chunkStart + chunkSize > bytes.size) break

                when (chunkType) {
                    0x0001 -> { // STRING_POOL
                        val stringCount = buf.int
                        buf.int // styleCount
                        val flags = buf.int
                        val stringsStart = buf.int
                        buf.int // stylesStart
                        val isUtf8 = (flags and (1 shl 8)) != 0
                        val offsets = IntArray(stringCount) { buf.int }
                        val poolStart = chunkStart + stringsStart
                        for (i in 0 until stringCount) {
                            val pos = poolStart + offsets[i]
                            if (pos >= 0 && pos < chunkStart + chunkSize) {
                                strings.add(readStringFromPool(bytes, pos, isUtf8))
                            } else {
                                strings.add("")
                            }
                        }
                    }
                    0x0102 -> { // XML_START_ELEMENT
                        buf.int // lineNumber
                        buf.int // comment
                        buf.int // ns
                        val nameIdx = buf.int
                        buf.short // attrStart
                        buf.short // attrSize
                        val attrCount = buf.short.toInt() and 0xFFFF
                        buf.short // idIndex
                        buf.short // classIndex
                        buf.short // styleIndex

                        val tagName = strings.getOrNull(nameIdx).orEmpty()
                        xmlOut.append("  ".repeat(indent)).append("<").append(tagName)
                        val attrs = mutableMapOf<String, String>()
                        for (a in 0 until attrCount) {
                            val attrNs = buf.int
                            val attrNameIdx = buf.int
                            val attrRawValIdx = buf.int
                            buf.short // size
                            buf.get() // res0
                            val dataType = buf.get().toInt() and 0xFF
                            val data = buf.int

                            val attrName = strings.getOrNull(attrNameIdx).orEmpty()
                            val nsPrefix = if (attrNs >= 0 && strings.getOrNull(attrNs)?.contains("android") == true) "android:" else ""
                            val valueStr = when {
                                attrRawValIdx >= 0 -> strings.getOrNull(attrRawValIdx).orEmpty()
                                dataType == 0x10 || dataType == 0x11 -> data.toString()
                                dataType == 0x12 -> (data != 0).toString()
                                else -> "0x${data.toString(16)}"
                            }
                            attrs[attrName] = valueStr
                            xmlOut.append(" ").append(nsPrefix).append(attrName).append("=\"").append(valueStr).append("\"")
                        }
                        xmlOut.append(">\n")
                        indent++

                        when (tagName) {
                            "manifest" -> {
                                packageName = attrs["package"] ?: packageName
                                versionCode = attrs["versionCode"] ?: versionCode
                                versionName = attrs["versionName"] ?: versionName
                            }
                            "uses-sdk" -> {
                                minSdk = attrs["minSdkVersion"] ?: minSdk
                                targetSdk = attrs["targetSdkVersion"] ?: targetSdk
                            }
                            "uses-permission" -> {
                                attrs["name"]?.let { permissions.add(it) }
                            }
                            "application" -> {
                                attrs["label"]?.takeIf { !it.startsWith("0x") }?.let { appName = it }
                            }
                            "activity", "activity-alias" -> {
                                attrs["name"]?.let { activities.add(it) }
                            }
                            "service" -> {
                                attrs["name"]?.let { services.add(it) }
                            }
                            "receiver" -> {
                                attrs["name"]?.let { receivers.add(it) }
                            }
                        }
                    }
                    0x0103 -> { // XML_END_ELEMENT
                        buf.int // line
                        buf.int // comment
                        buf.int // ns
                        val nameIdx = buf.int
                        val tagName = strings.getOrNull(nameIdx).orEmpty()
                        indent = (indent - 1).coerceAtLeast(0)
                        xmlOut.append("  ".repeat(indent)).append("</").append(tagName).append(">\n")
                    }
                }
                buf.position(chunkStart + chunkSize)
            }

            DecodedManifest(
                packageName = packageName,
                appName = appName,
                versionCode = versionCode,
                versionName = versionName,
                minSdk = minSdk,
                targetSdk = targetSdk,
                permissions = permissions.distinct(),
                activities = activities.distinct(),
                services = services.distinct(),
                receivers = receivers.distinct(),
                reconstructedXml = xmlOut.toString(),
            )
        }.getOrElse {
            DecodedManifest(
                packageName = "com.reconstructed.apk",
                appName = "APK Project",
                versionCode = "1",
                versionName = "1.0",
                minSdk = "24",
                targetSdk = "35",
                permissions = emptyList(),
                activities = emptyList(),
                services = emptyList(),
                receivers = emptyList(),
                reconstructedXml = "<!-- Binary AndroidManifest.xml could not be fully decoded -->",
            )
        }
    }

    private fun readStringFromPool(bytes: ByteArray, offset: Int, isUtf8: Boolean): String {
        return runCatching {
            var pos = offset
            if (isUtf8) {
                // Skip UTF-16 char length
                if ((bytes[pos].toInt() and 0x80) != 0) pos += 2 else pos += 1
                // Read UTF-8 byte length
                var byteLen = bytes[pos].toInt() and 0x7F
                if ((bytes[pos].toInt() and 0x80) != 0) {
                    byteLen = ((bytes[pos].toInt() and 0x7F) shl 8) or (bytes[pos + 1].toInt() and 0xFF)
                    pos += 2
                } else {
                    pos += 1
                }
                val safeLen = byteLen.coerceIn(0, bytes.size - pos)
                String(bytes, pos, safeLen, Charsets.UTF_8)
            } else {
                var charLen = ((bytes[pos + 1].toInt() and 0xFF) shl 8) or (bytes[pos].toInt() and 0xFF)
                pos += 2
                if ((charLen and 0x8000) != 0) {
                    val low = ((bytes[pos + 1].toInt() and 0xFF) shl 8) or (bytes[pos].toInt() and 0xFF)
                    charLen = ((charLen and 0x7FFF) shl 16) or low
                    pos += 2
                }
                val safeBytes = (charLen * 2).coerceIn(0, bytes.size - pos)
                String(bytes, pos, safeBytes, Charsets.UTF_16LE)
            }
        }.getOrDefault("")
    }
}

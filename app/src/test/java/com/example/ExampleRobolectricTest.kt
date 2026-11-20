package com.example

import android.content.Context
import android.net.Uri
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import com.example.data.model.ProjectType
import com.example.engine.ApkAnalyzer
import com.example.engine.ProjectDiagnostics
import com.example.engine.WorkspaceManager
import com.example.security.KeystoreSecretVault
import com.example.security.SecretRedactor
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun `app name matches X-THREX`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        assertEquals("X-THREX", context.getString(R.string.app_name))
    }

    @Test
    fun `secret vault and redactor protect api keys from logs and zip exports`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val vault = KeystoreSecretVault(context)
        val fakeKey = "sk-test-secret-998877665544332211"
        vault.saveSecret(KeystoreSecretVault.apiKeySlot("p1"), fakeKey)
        assertEquals(fakeKey, vault.getSecret(KeystoreSecretVault.apiKeySlot("p1")))

        val manager = WorkspaceManager(context)
        val pid = "test_proj_sec"
        manager.initializeTemplate(pid, "SecApp", ProjectType.HTML_CSS_JS, "Test")
        manager.writeProjectFile(pid, "config.js", "const key = \"$fakeKey\";")
        manager.writeProjectFile(pid, ".env", "API_KEY=$fakeKey")

        val zipBytes = ByteArrayOutputStream()
        manager.exportProjectZip(pid, "SecApp", ProjectType.HTML_CSS_JS, zipBytes, listOf(fakeKey))

        var foundEnv = false
        var leakedKey = false
        ZipInputStream(zipBytes.toByteArray().inputStream()).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                if (entry.name.endsWith("/.env")) foundEnv = true
                val content = zis.readBytes().toString(Charsets.UTF_8)
                if (content.contains(fakeKey)) leakedKey = true
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }
        assertFalse(".env should be excluded from ZIP export", foundEnv)
        assertFalse("API key must be redacted from ZIP export", leakedKey)
        assertEquals("Token=[REDACTED_SECRET]", SecretRedactor.redact("Token=$fakeKey", listOf(fakeKey)))
    }

    @Test
    fun `workspace import export diagnostics and apk analyzer work end to end`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val manager = WorkspaceManager(context)
        val pid = "test_diag_proj"
        manager.initializeTemplate(pid, "BrokenWeb", ProjectType.HTML_CSS_JS, "Diag test")
        manager.writeProjectFile(pid, "broken.js", "function test() { return 1;") // missing closing brace

        val issues = ProjectDiagnostics.scanWorkspace(
            manager.getProjectDir(pid),
            ProjectType.HTML_CSS_JS,
            emptyList(),
        )
        assertTrue("Should detect unbalanced brace syntax error", issues.any { it.filePath == "broken.js" })

        // Create synthetic APK fixture with manifest + dex + asset
        val apkFile = File(context.cacheDir, "sample.apk")
        ZipOutputStream(FileOutputStream(apkFile)).use { zos ->
            zos.putNextEntry(ZipEntry("AndroidManifest.xml"))
            zos.write(
                """
                <?xml version="1.0" encoding="utf-8"?>
                <manifest package="com.demo.sample" versionCode="3" versionName="2.1">
                    <uses-permission android:name="android.permission.INTERNET" />
                    <application>
                        <activity android:name="com.demo.sample.MainActivity" />
                    </application>
                </manifest>
                """.trimIndent().toByteArray()
            )
            zos.closeEntry()
            zos.putNextEntry(ZipEntry("classes.dex"))
            zos.write("dex\n035\u0000Lcom/demo/sample/MainActivity;".toByteArray())
            zos.closeEntry()
            zos.putNextEntry(ZipEntry("assets/config.txt"))
            zos.write("hello".toByteArray())
            zos.closeEntry()
        }

        val analyzer = ApkAnalyzer(context, manager)
        val report = analyzer.analyzeAndReconstruct(Uri.fromFile(apkFile), "sample.apk", "reconstructed_apk")
        assertEquals("com.demo.sample", report.packageName)
        assertTrue(report.permissions.contains("android.permission.INTERNET"))
        assertTrue(report.dexFiles.contains("classes.dex"))
        assertTrue(File(manager.getProjectDir("reconstructed_apk"), "APK-ANALYSIS-REPORT.md").exists())
        apkFile.delete()
    }

    @Test
    fun `home screen renders and creates new project via UI`() {
        composeRule.waitForIdle()
        composeRule.onNodeWithText("X-THREX").assertIsDisplayed()
        composeRule.onNodeWithTag("home_new_project_btn").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("new_project_name_input").performTextInput("PocketIDE")
        composeRule.onNodeWithTag("confirm_create_project_btn").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText("PocketIDE").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("PocketIDE").assertIsDisplayed()
    }
}

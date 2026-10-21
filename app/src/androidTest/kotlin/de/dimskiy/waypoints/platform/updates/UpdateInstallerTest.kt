package de.dimskiy.waypoints.platform.updates

import android.content.Intent
import android.os.ParcelFileDescriptor
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.test.platform.app.InstrumentationRegistry
import de.dimskiy.waypoints.BuildConfig
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.security.MessageDigest
import javax.inject.Provider

/** Optional local fixture: a debug-signed .local APK at code 9 / version 0.97. Never install it. */
class UpdateInstallerTest {
    @Test fun verifiesActualApkAndOpensSystemConfirmationWithoutInstalling() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        assumeTrue(BuildConfig.APPLICATION_ID.endsWith(".local"))
        val input = runCatching { instrumentation.context.assets.open("update-candidate.apk") }.getOrNull()
        assumeTrue("Supply optional update-candidate.apk via poiCompatibilityAssets", input != null)
        val file = File(context.cacheDir, "app-updates/installer-test.apk").apply { parentFile!!.mkdirs() }
        input!!.use { stream -> file.outputStream().use { stream.copyTo(it) } }
        val digest = MessageDigest.getInstance("SHA-256").digest(file.readBytes()).joinToString("") { "%02x".format(it) }
        val release = AppRelease("0.97", 9, "test-only", "", "", file.length(), digest)
        val preferences = mockk<DataStore<Preferences>>()
        val updates = AppUpdates(context, preferences, Dispatchers.IO, Provider { System.currentTimeMillis() })
        fun shell(command: String): String = ParcelFileDescriptor.AutoCloseInputStream(
            instrumentation.uiAutomation.executeShellCommand(command)).bufferedReader().use { it.readText() }
        try {
            updates.verify(file, release) // Real API 35 archive, version and signingInfo parsing.
            val intent = updateInstallIntent(context, file)
            assertEquals("content", intent.data!!.scheme)
            assertTrue(intent.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
            shell("appops set ${context.packageName} REQUEST_INSTALL_PACKAGES allow")
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            val deadline = System.currentTimeMillis() + 20_000
            var installer = false
            while (!installer && System.currentTimeMillis() < deadline) {
                val root = instrumentation.uiAutomation.rootInActiveWindow
                fun hasUpdateButton(node: android.view.accessibility.AccessibilityNodeInfo?): Boolean {
                    if (node == null) return false
                    if (node.text?.toString().equals("Update", ignoreCase = true)) return true
                    return (0 until node.childCount).any { hasUpdateButton(node.getChild(it)) }
                }
                installer = root?.packageName?.toString()?.contains("packageinstaller") == true && hasUpdateButton(root)
                if (!installer) Thread.sleep(100)
            }
            assertTrue("System package installer did not open", installer)
            // Deliberately do not press Install: leave source/version and installed code unchanged.
        } finally {
            shell("input keyevent 4")
            // Revoking this app-op kills the instrumented process; the host resets it after the run.
            file.delete()
        }
    }
}

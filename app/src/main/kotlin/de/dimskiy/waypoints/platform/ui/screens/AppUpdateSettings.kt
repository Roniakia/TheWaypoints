package de.dimskiy.waypoints.platform.ui.screens

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import de.dimskiy.waypoints.platform.ui.components.FloatingBottomAction
import de.dimskiy.waypoints.BuildConfig
import de.dimskiy.waypoints.platform.updates.*
import java.text.DateFormat
import java.util.Date

@Composable
fun AppUpdateSettings(viewModel: AppUpdateViewModel) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val installer = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        viewModel.message("Installation handed to Android. If cancelled, you can tap Install update again.")
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        viewModel.message("Tap Install update to continue after allowing app installation.")
    }
    LaunchedEffect(viewModel) {
        viewModel.check()
        viewModel.installs.collect { file ->
            try {
                if (Build.VERSION.SDK_INT >= 26 && !context.packageManager.canRequestPackageInstalls()) {
                    viewModel.message("Allow TheWaypoints to install app updates, then return and tap Install update.")
                    permission.launch(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}")))
                } else {
                    installer.launch(updateInstallIntent(context, file))
                }
            } catch (_: Exception) {
                viewModel.message("This device cannot open the Android installer. Use the APK link below with Hammerhead Companion.")
            }
        }
    }
    AppUpdateSettingsContent(state, viewModel::channel, { viewModel.check() }, viewModel::downloadAndInstall,
        viewModel::cancel, viewModel::install)
}

@Composable
fun AppUpdateSettingsContent(state: UpdateUiState, channel: (UpdateChannel) -> Unit = {},
    check: () -> Unit = {}, download: () -> Unit = {}, cancel: () -> Unit = {}, install: () -> Unit = {},
    canInstallUpdates: Boolean = !BuildConfig.APPLICATION_ID.endsWith(".local")) {
    val busy = state.checking || state.downloading
    val actionLabel = when {
        state.checking -> "Checking…"
        state.downloading -> "Downloading… ${state.progress}%"
        state.release == null -> "Check for updates"
        state.apk != null -> "Install update"
        else -> "Download and install"
    }
    FloatingBottomAction(label = actionLabel,
        onClick = {
            when {
                state.release == null -> check()
                state.apk != null -> install()
                else -> download()
            }
        }, enabled = !busy && (state.release == null || canInstallUpdates)) { scrollPadding ->
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(scrollPadding).padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("App updates", style = MaterialTheme.typography.titleMedium)
            Text("Installed: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})", style = MaterialTheme.typography.bodySmall)
            Text("Checks on launch at most once a day. Opening Settings checks again.", style = MaterialTheme.typography.bodySmall)
            Text("Update channel", style = MaterialTheme.typography.labelLarge)
            Row(Modifier.fillMaxWidth().selectableGroup()) {
                UpdateChannel.values().forEach { value ->
                    Row(Modifier.weight(1f).heightIn(min = 48.dp).selectable(
                        selected = state.channel == value, enabled = !busy, role = Role.RadioButton,
                        onClick = { channel(value) }), verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = state.channel == value, enabled = !busy, onClick = null)
                        Text(if (value == UpdateChannel.STABLE) "Stable" else "Beta", modifier = Modifier.padding(start = 8.dp))
                    }
                }
            }
            Text(if (state.channel == UpdateChannel.STABLE) "Stable releases only." else "Stable and beta releases. Beta versions may have unfinished features.", style = MaterialTheme.typography.bodySmall)
            Text("Returning to Stable waits for a newer stable version; it does not downgrade the app.", style = MaterialTheme.typography.bodySmall)
            Text(state.lastChecked?.let { "Last checked: ${DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(it))}" } ?: "Not checked yet.", style = MaterialTheme.typography.bodySmall)
            if (state.checking) LinearProgressIndicator(Modifier.fillMaxWidth())
            state.message?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            state.release?.let { release ->
                HorizontalDivider()
                Text("Version ${release.version} · ${release.tag}", style = MaterialTheme.typography.titleSmall)
                Text("%.1f MB".format(release.bytes / 1_000_000.0), style = MaterialTheme.typography.bodySmall)
                if (release.notes.isNotBlank()) Text(release.notes, style = MaterialTheme.typography.bodySmall)
                if (!canInstallUpdates) {
                    Text("Local test build: production updates cannot be installed over this app.", style = MaterialTheme.typography.bodySmall)
                }
                if (state.downloading) {
                    LinearProgressIndicator(progress = { state.progress / 100f }, modifier = Modifier.fillMaxWidth())
                    Text("${state.progress}%")
                    TextButton(colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurface), onClick = cancel) { Text("Cancel download") }
                }
                Text("APK link for Companion:", style = MaterialTheme.typography.labelSmall)
                SelectionContainer { Text(release.url, style = MaterialTheme.typography.bodySmall) }
            }
        }

    }
}

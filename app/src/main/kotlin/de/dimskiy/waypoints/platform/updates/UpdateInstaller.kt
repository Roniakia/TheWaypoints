package de.dimskiy.waypoints.platform.updates

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File

/** The system installer owns confirmation and installation; no silent install. */
fun updateInstallIntent(context: Context, file: File): Intent {
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.updates", file)
    return Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/vnd.android.package-archive")
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
}

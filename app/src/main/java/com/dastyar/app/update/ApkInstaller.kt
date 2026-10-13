package com.dastyar.app.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
import java.io.File

/**
 * Hands a verified APK to Android's own package installer.
 *
 * The app never installs anything silently: it only starts the official system
 * installer, which shows its normal confirmation screens. On Android 8+ the
 * user must also grant "install unknown apps" to this app the first time; when
 * that is missing we send them straight to that toggle instead of failing.
 */
object ApkInstaller {

    /** True when this app is allowed to request a package install. */
    fun canInstall(ctx: Context): Boolean =
        ctx.packageManager.canRequestPackageInstalls()

    /** Opens the system setting that lets the user allow installs from this app. */
    fun openInstallPermissionSettings(ctx: Context) {
        val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
            data = Uri.parse("package:${ctx.packageName}")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            ctx.startActivity(intent)
        } catch (_: Exception) {
        }
    }

    /**
     * Launches the system installer for [apk]. Returns false when it could not
     * be started (e.g. no activity for the intent), so the caller can show a
     * message rather than leaving the user stuck.
     */
    fun install(ctx: Context, apk: File): Boolean {
        return try {
            val uri: Uri = FileProvider.getUriForFile(
                ctx,
                "${ctx.packageName}.fileprovider",
                apk
            )
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            ctx.startActivity(intent)
            true
        } catch (_: Exception) {
            false
        }
    }
}

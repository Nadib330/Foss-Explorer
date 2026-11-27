/*
 * Explorer: opening a downloaded file — and installing a downloaded APK.
 * Own code, GPL-3.0.
 *
 * 18.3.0 beta (item 8): a browser that can download an app has to be able to hand it to the
 * installer of Android. Before this version an `.apk` arrived as a `.bak` (see item 5) and could
 * not be tapped at all. Now the file keeps its name, and tapping it here opens the system's
 * installer with the file, after asking for the permission Android wants for that ("Install
 * unknown apps", from the browser).
 */
package com.rpooi3.explorer.downloads

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.core.content.FileProvider
import com.rpooi3.explorer.R
import com.rpooi3.explorer.view.CatToast
import java.io.File

object ApkInstaller {

    private const val TAG = "ApkInstaller"
    private const val PREFS = "apk_installer"
    private const val KEY_URI = "pending_uri"
    private const val KEY_MIME = "pending_mime"
    private const val KEY_NAME = "pending_name"
    const val MIME_APK = "application/vnd.android.package-archive"

    /** Whether [fileName] is an app package. */
    @JvmStatic
    fun isPackage(fileName: String?): Boolean =
        fileName?.lowercase()?.endsWith(".apk") == true ||
            fileName?.lowercase()?.endsWith(".apks") == true

    /** A `content://` address for a file the app wrote; the download folder is shared this way. */
    @JvmStatic
    fun uriOf(context: Context, file: File): Uri? = try {
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    } catch (e: IllegalArgumentException) {
        Log.w(TAG, "No address for ${file.name}: $e")
        null
    }

    /** The intent that opens (or installs) a file of [mime]. */
    @JvmStatic
    fun viewIntent(uri: Uri, mime: String): Intent =
        Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, mime)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)

    /**
     * Opens [uri]; an app package goes to the installer of Android.
     *
     * @return false if nothing on the device can open it
     */
    @JvmStatic
    fun open(context: Context, uri: Uri, mime: String, fileName: String?): Boolean {
        val installer = isPackage(fileName) || mime == MIME_APK
        if (installer && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !canInstall(context)) {
            // Android asks the user first: "Install unknown apps" for Explorer. Keep the tap so
            // returning from that screen continues the install instead of making the user find
            // the APK again.
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putString(KEY_URI, uri.toString())
                .putString(KEY_MIME, MIME_APK)
                .putString(KEY_NAME, fileName.orEmpty())
                .apply()
            askForPermission(context)
            return true
        }
        val intent = viewIntent(uri, if (installer) MIME_APK else mime)
            .addFlags(if (context !is android.app.Activity) Intent.FLAG_ACTIVITY_NEW_TASK else 0)
        return try {
            context.startActivity(intent)
            true
        } catch (e: ActivityNotFoundException) {
            Log.w(TAG, "Nothing opens $fileName", e)
            CatToast.show(context, R.string.downloads_no_app)
            false
        } catch (e: SecurityException) {
            Log.w(TAG, "Not allowed to open $fileName", e)
            CatToast.show(context, R.string.downloads_no_app)
            false
        }
    }

    /** Installs the package of [task]. */
    @JvmStatic
    fun install(context: Context, task: DownloadTask): Boolean {
        val uri = task.fileUri?.let { runCatching { Uri.parse(it) }.getOrNull() }
            ?: task.filePath?.let { uriOf(context, File(it)) }
            ?: return false
        return open(context, uri, MIME_APK, task.fileName)
    }

    /** Whether Explorer may ask Android to install a package. */
    @JvmStatic
    fun canInstall(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.O || context.packageManager.canRequestPackageInstalls()

    /**
     * Continues the APK install after Android's "Install unknown apps" screen returns.
     *
     * @return true when a pending install was handed to Android
     */
    @JvmStatic
    fun installPending(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val raw = prefs.getString(KEY_URI, null) ?: return false
        if (!canInstall(context)) return false
        val uri = runCatching { Uri.parse(raw) }.getOrNull() ?: return false
        val mime = prefs.getString(KEY_MIME, MIME_APK) ?: MIME_APK
        val name = prefs.getString(KEY_NAME, null)
        prefs.edit().clear().apply()
        return open(context, uri, mime, name)
    }

    /** The system screen where Explorer is allowed to install packages. */
    @JvmStatic
    fun askForPermission(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        try {
            context.startActivity(
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
            CatToast.show(context, R.string.download_install_permission)
        } catch (e: ActivityNotFoundException) {
            CatToast.show(context, R.string.downloads_no_app)
        }
    }
}

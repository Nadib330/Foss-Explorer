package com.rpooi3.explorer.compat

import android.Manifest
import android.app.Activity
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import com.rpooi3.explorer.downloads.DownloadLog
import android.content.pm.PackageManager
import android.media.MediaScannerConnection
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.annotation.RequiresApi
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.rpooi3.explorer.R
import com.rpooi3.explorer.view.CatToast
import java.io.File
import java.io.IOException

/**
 * Bridges the shared "Downloads" folder across Android versions.
 *
 * Upstream targets Android 10+, where scoped storage lets an app add files to
 * Downloads without any permission. Foss Explorer also runs on Android 8 and 9,
 * where the same operations need WRITE_EXTERNAL_STORAGE.
 */
object StorageCompat {

    private const val REQUEST_WRITE_STORAGE = 2601

    private val needsLegacyPermission: Boolean
        get() = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q

    /**
     * Returns `true` if files can be saved to Downloads right now. Otherwise
     * asks for the storage permission, tells the user to repeat the action and
     * returns `false`.
     */
    @JvmStatic
    fun ensureDownloadsWritable(activity: Activity): Boolean {
        if (!needsLegacyPermission) return true

        val granted = ContextCompat.checkSelfPermission(
            activity, Manifest.permission.WRITE_EXTERNAL_STORAGE
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) return true

        ActivityCompat.requestPermissions(
            activity, arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE), REQUEST_WRITE_STORAGE
        )
        CatToast.show(activity, R.string.toast_storage_permission)
        return false
    }

    /** Writes [bytes] as [fileName] into the shared Downloads folder. */
    @JvmStatic
    @Throws(IOException::class)
    fun writeToDownloads(context: Context, fileName: String, mimeType: String?, bytes: ByteArray) {
        val type = mimeType ?: "application/octet-stream"
        val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            writeWithMediaStore(context, fileName, type, bytes)
        } else {
            writeToPublicDirectory(context, fileName, type, bytes)
            null
        }
        // The system's download manager does not know about files written here, so the
        // downloads screen gets them from this list.
        DownloadLog.add(context, fileName, type, bytes.size.toLong(), uri)
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun writeWithMediaStore(context: Context, fileName: String, mimeType: String, bytes: ByteArray): Uri {
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
            put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
        }
        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            ?: throw IOException("MediaStore rejected $fileName")
        val stream = resolver.openOutputStream(uri)
            ?: throw IOException("Cannot open $uri for writing")
        stream.use {
            it.write(bytes)
            it.flush()
        }
        return uri
    }

    @Suppress("DEPRECATION") // The public directory API is the only option before Android 10.
    private fun writeToPublicDirectory(context: Context, fileName: String, mimeType: String, bytes: ByteArray) {
        val directory = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        if (!directory.isDirectory && !directory.mkdirs()) {
            throw IOException("Cannot create $directory")
        }
        val file = File(directory, fileName)
        file.outputStream().use {
            it.write(bytes)
            it.flush()
        }
        // Make the file show up in the system Downloads app.
        MediaScannerConnection.scanFile(context, arrayOf(file.absolutePath), arrayOf(mimeType), null)
    }
}

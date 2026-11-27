package com.rpooi3.explorer.downloads

import androidx.core.net.toUri
import android.app.DownloadManager
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.util.Log
import java.io.File

/**
 * Reads and deletes this browser's downloads.
 *
 * 18.3.0 beta (item 9): the downloads Explorer makes itself are the ones of [DownloadStore] — they
 * are the ones that can be paused and whose progress is known. The two older sources are still
 * read so that nothing that was downloaded before disappears from the screen: the files the
 * system's download manager fetched in earlier versions, and the `data:` files of [DownloadLog].
 */
object Downloads {
    private const val TAG = "Downloads"

    /** All downloads, newest first. */
    @JvmStatic
    fun list(context: Context): List<DownloadItem> {
        val mine = DownloadStore.list(context).map { it.toItem() }
        val ours = mine.map { it.sourceUrl }.toSet()
        val logged = DownloadLog.list(context)
        val before = fromManager(context).filterNot { it.sourceUrl != null && it.sourceUrl in ours }
        return (mine + logged + before).sortedByDescending { it.time }
    }

    /** The download Explorer is making itself, if this row is one of those. */
    @JvmStatic
    fun task(context: Context, item: DownloadItem): DownloadTask? =
        if (!item.engine) null else DownloadStore.find(context, item.id)

    /** A `content://` address for a row, if there is one. */
    @JvmStatic
    fun uriFor(context: Context, item: DownloadItem): Uri? {
        task(context, item)?.let { task ->
            task.fileUri?.let { return runCatching { Uri.parse(it) }.getOrNull() }
            task.filePath?.let { return ApkInstaller.uriOf(context, File(it)) }
        }
        item.uri?.let { uri ->
            // A file of the app's own folder is handed out through the FileProvider.
            if (uri.scheme == "file") {
                val path = uri.path
                if (path != null) ApkInstaller.uriOf(context, File(path))?.let { return it }
            }
            return uri
        }
        if (item.fromManager) {
            val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as? DownloadManager ?: return null
            return uriOf(manager, item.id)
        }
        return null
    }

    private fun fromManager(context: Context): List<DownloadItem> {
        val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as? DownloadManager ?: return emptyList()
        val items = ArrayList<DownloadItem>()
        try {
            val cursor = manager.query(DownloadManager.Query()) ?: return emptyList()
            try {
                while (cursor.moveToNext()) {
                    val id = cursor.long(DownloadManager.COLUMN_ID, -1)
                    if (id < 0) continue
                    val localUri = cursor.string(DownloadManager.COLUMN_LOCAL_URI)
                    val source = cursor.string(DownloadManager.COLUMN_URI)
                    val state = stateOf(cursor.long(DownloadManager.COLUMN_STATUS, 0).toInt())
                    items += DownloadItem(
                        id = id,
                        fromManager = true,
                        name = cursor.string(DownloadManager.COLUMN_TITLE)?.takeIf { it.isNotBlank() }
                            ?: fileName(localUri)
                            ?: fileName(source)
                            ?: id.toString(),
                        mimeType = cursor.string(DownloadManager.COLUMN_MEDIA_TYPE),
                        totalBytes = cursor.long(DownloadManager.COLUMN_TOTAL_SIZE_BYTES, -1),
                        downloadedBytes = cursor.long(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR, 0),
                        time = cursor.long(DownloadManager.COLUMN_LAST_MODIFIED_TIMESTAMP, 0),
                        state = state,
                        sourceUrl = source,
                        uri = if (state == DownloadItem.State.DONE) uriOf(manager, id) else null,
                    )
                }
            } finally {
                cursor.close()
            }
        } catch (e: Exception) {
            // Some devices ship without the download provider, or with it disabled.
            Log.w(TAG, "Download manager not readable: $e")
        }
        return items
    }

    private fun uriOf(manager: DownloadManager, id: Long): Uri? =
        try {
            manager.getUriForDownloadedFile(id)
        } catch (e: Exception) {
            null
        }

    internal fun stateOf(status: Int): DownloadItem.State = when (status) {
        DownloadManager.STATUS_SUCCESSFUL -> DownloadItem.State.DONE
        DownloadManager.STATUS_FAILED -> DownloadItem.State.FAILED
        DownloadManager.STATUS_RUNNING -> DownloadItem.State.RUNNING
        else -> DownloadItem.State.WAITING // pending or paused
    }

    internal fun fileName(uri: String?): String? =
        uri?.toUri()?.lastPathSegment?.takeIf { it.isNotBlank() }

    /**
     * Deletes a download together with its file.
     *
     * @return false if the file could not be removed
     */
    @JvmStatic
    fun delete(context: Context, item: DownloadItem): Boolean {
        task(context, item)?.let { task ->
            // One of the built-in manager's: the file goes, and the entry with it.
            DownloadEngine.cancel(context, task.id)
            task.filePath?.let { runCatching { File(it).delete() } }
            task.fileUri?.let { uri ->
                runCatching { context.contentResolver.delete(Uri.parse(uri), null, null) }
            }
            DownloadStore.remove(context, task.id)
            DownloadNotifications.clear(context, task.id)
            return true
        }
        if (item.fromManager) {
            val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as? DownloadManager ?: return false
            return manager.remove(item.id) > 0
        }
        // A file Explorer wrote before the built-in manager was there (`data:` and blob links).
        val uri = item.uri
        var removed = true
        if (uri != null) {
            removed = try {
                if (uri.scheme == "file") {
                    File(uri.path.orEmpty()).delete()
                } else {
                    context.contentResolver.delete(uri, null, null)
                    true
                }
            } catch (e: Exception) {
                Log.w(TAG, "Cannot delete $uri: $e")
                false
            }
        }
        // The entry goes either way: a file the system no longer lets us touch should not be listed forever.
        DownloadLog.remove(context, item.id)
        return removed
    }

    private fun Cursor.string(column: String): String? {
        val index = getColumnIndex(column)
        return if (index < 0) null else getString(index)
    }

    private fun Cursor.long(column: String, fallback: Long): Long {
        val index = getColumnIndex(column)
        return if (index < 0) fallback else getLong(index)
    }
}

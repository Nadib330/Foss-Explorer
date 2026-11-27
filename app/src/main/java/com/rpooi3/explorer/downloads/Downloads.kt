package com.rpooi3.explorer.downloads

import androidx.core.net.toUri
import android.app.DownloadManager
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.util.Log

/**
 * Reads and deletes this browser's downloads. They come from two places: the
 * system's download manager, which fetches everything that has a web address
 * and only ever reports the downloads this app started, and [DownloadLog]
 * for the files the browser wrote itself.
 */
object Downloads {
    private const val TAG = "Downloads"

    /** All downloads, newest first. */
    @JvmStatic
    fun list(context: Context): List<DownloadItem> =
        (fromManager(context) + DownloadLog.list(context)).sortedByDescending { it.time }

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
        if (item.fromManager) {
            val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as? DownloadManager ?: return false
            return manager.remove(item.id) > 0
        }
        var removed = true
        if (item.uri != null) {
            removed = try {
                context.contentResolver.delete(item.uri, null, null)
                true
            } catch (e: Exception) {
                Log.w(TAG, "Cannot delete ${item.uri}: $e")
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

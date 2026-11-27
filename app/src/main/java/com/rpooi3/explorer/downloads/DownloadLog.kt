package com.rpooi3.explorer.downloads

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.core.content.edit
import org.json.JSONArray
import org.json.JSONObject

/**
 * The files the browser wrote to the Downloads folder itself: pages that
 * hand over their data directly (`data:` and `blob:` links) instead of a web
 * address the system's download manager could fetch. The download manager
 * knows nothing about those, so they are remembered here for the downloads
 * screen. Only the entries are stored, never file contents.
 */
object DownloadLog {
    private const val PREFS = "downloads_log"
    private const val KEY_ITEMS = "items"
    private const val MAX_ENTRIES = 200
    private const val TAG = "DownloadLog"

    @JvmStatic
    @JvmOverloads
    fun add(
        context: Context,
        name: String,
        mimeType: String?,
        sizeBytes: Long,
        uri: Uri?,
        time: Long = System.currentTimeMillis(),
    ) {
        val entries = read(context)
        val id = (entries.maxOfOrNull { it.id } ?: 0L) + 1
        val item = DownloadItem(
            id = id,
            fromManager = false,
            name = name,
            mimeType = mimeType,
            totalBytes = sizeBytes,
            downloadedBytes = sizeBytes,
            time = time,
            state = DownloadItem.State.DONE,
            sourceUrl = null,
            uri = uri,
        )
        write(context, (entries + item).takeLast(MAX_ENTRIES))
    }

    @JvmStatic
    fun list(context: Context): List<DownloadItem> = read(context)

    /** Forgets an entry. The file itself is not touched. */
    @JvmStatic
    fun remove(context: Context, id: Long) {
        write(context, read(context).filter { it.id != id })
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun read(context: Context): List<DownloadItem> {
        val json = prefs(context).getString(KEY_ITEMS, null) ?: return emptyList()
        return try {
            val array = JSONArray(json)
            (0 until array.length()).map { index ->
                val entry = array.getJSONObject(index)
                val size = entry.optLong("size", -1)
                DownloadItem(
                    id = entry.getLong("id"),
                    fromManager = false,
                    name = entry.getString("name"),
                    mimeType = entry.optString("type").ifEmpty { null },
                    totalBytes = size,
                    downloadedBytes = size,
                    time = entry.optLong("time"),
                    state = DownloadItem.State.DONE,
                    sourceUrl = null,
                    uri = entry.optString("uri").ifEmpty { null }?.let(Uri::parse),
                )
            }
        } catch (e: Exception) {
            // A damaged list must not take the downloads screen down with it.
            Log.w(TAG, "Download list unreadable, starting a new one: $e")
            emptyList()
        }
    }

    private fun write(context: Context, entries: List<DownloadItem>) {
        val array = JSONArray()
        for (item in entries) {
            array.put(
                JSONObject()
                    .put("id", item.id)
                    .put("name", item.name)
                    .put("type", item.mimeType.orEmpty())
                    .put("size", item.totalBytes)
                    .put("time", item.time)
                    .put("uri", item.uri?.toString().orEmpty()),
            )
        }
        prefs(context).edit { putString(KEY_ITEMS, array.toString()) }
    }
}

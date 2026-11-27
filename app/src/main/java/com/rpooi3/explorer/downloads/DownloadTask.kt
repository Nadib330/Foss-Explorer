/*
 * Explorer: one download, as the built-in download manager knows it.
 * Own code, GPL-3.0.
 *
 * 18.3.0 beta (items 5 to 12): Explorer keeps its own downloads now, instead of handing them to
 * the system's download manager. The name is the name the server gave the file — nothing is
 * renamed and nothing gets a number of its own — and the file, its progress, whether it is paused
 * and where it went are all written down here.
 */
package com.rpooi3.explorer.downloads

import android.net.Uri
import org.json.JSONObject

data class DownloadTask(
    val id: Long,
    /** The web address the bytes come from. */
    val url: String,
    /** The name of the file, exactly as it was announced. */
    val fileName: String,
    val mimeType: String?,
    val totalBytes: Long,
    val downloadedBytes: Long,
    val state: State,
    val createdAt: Long,
    val updatedAt: Long,
    /** Where the file is, once it is written: a path for a folder, a URI for a folder of the user's. */
    val filePath: String?,
    val fileUri: String?,
    val userAgent: String?,
    val cookie: String?,
    val error: String?,
) {
    enum class State { QUEUED, RUNNING, PAUSED, DONE, FAILED, CANCELLED }

    /** Progress in percent, or null while the size is unknown. */
    val percent: Int?
        get() = if (totalBytes > 0) ((downloadedBytes.coerceIn(0, totalBytes) * 100) / totalBytes).toInt() else null

    val isActive: Boolean
        get() = state == State.QUEUED || state == State.RUNNING || state == State.PAUSED

    fun toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("url", url)
        .put("name", fileName)
        .put("mime", mimeType ?: JSONObject.NULL)
        .put("total", totalBytes)
        .put("done", downloadedBytes)
        .put("state", state.name)
        .put("created", createdAt)
        .put("updated", updatedAt)
        .put("path", filePath ?: JSONObject.NULL)
        .put("uri", fileUri ?: JSONObject.NULL)
        .put("ua", userAgent ?: JSONObject.NULL)
        .put("cookie", cookie ?: JSONObject.NULL)
        .put("error", error ?: JSONObject.NULL)

    /** The item the downloads screen shows. */
    fun toItem(): DownloadItem = DownloadItem(
        id = id,
        fromManager = false,
        name = fileName,
        mimeType = mimeType,
        totalBytes = totalBytes,
        downloadedBytes = downloadedBytes,
        time = updatedAt,
        state = when (state) {
            State.DONE -> DownloadItem.State.DONE
            State.FAILED, State.CANCELLED -> DownloadItem.State.FAILED
            State.PAUSED, State.QUEUED -> DownloadItem.State.PAUSED
            State.RUNNING -> DownloadItem.State.RUNNING
        },
        sourceUrl = url,
        uri = fileUri?.let { runCatching { Uri.parse(it) }.getOrNull() },
        engine = true,
    )

    companion object {
        @JvmStatic
        fun fromJson(json: JSONObject): DownloadTask? = try {
            val url = json.optString("url")
            if (url.isEmpty()) null else DownloadTask(
                id = json.optLong("id"),
                url = url,
                fileName = json.optString("name"),
                mimeType = json.optString("mime").takeIf { it.isNotEmpty() && it != "null" },
                totalBytes = json.optLong("total", -1),
                downloadedBytes = json.optLong("done", 0),
                state = runCatching { State.valueOf(json.optString("state")) }.getOrDefault(State.FAILED),
                createdAt = json.optLong("created"),
                updatedAt = json.optLong("updated"),
                filePath = json.optString("path").takeIf { it.isNotEmpty() && it != "null" },
                fileUri = json.optString("uri").takeIf { it.isNotEmpty() && it != "null" },
                userAgent = json.optString("ua").takeIf { it.isNotEmpty() && it != "null" },
                cookie = json.optString("cookie").takeIf { it.isNotEmpty() && it != "null" },
                error = json.optString("error").takeIf { it.isNotEmpty() && it != "null" },
            )
        } catch (e: Exception) {
            null
        }
    }
}

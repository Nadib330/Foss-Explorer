/*
 * Explorer: the downloads Explorer made itself, kept across restarts.
 * Own code, GPL-3.0.
 *
 * 18.3.0 beta (item 9): the list behind the built-in download manager and the downloads screen.
 * Only the entries are stored — the files stay where they were saved. Written as JSON, so that a
 * download that was paused when the browser was closed is still paused, with its name and its
 * progress, when it is opened again.
 */
package com.rpooi3.explorer.downloads

import android.content.Context
import android.util.Log
import androidx.core.content.edit
import org.json.JSONArray

object DownloadStore {

    private const val PREFS = "downloads_store"
    private const val KEY = "tasks"
    private const val MAX_ENTRIES = 300
    private const val TAG = "DownloadStore"

    @Volatile
    private var cache: MutableList<DownloadTask>? = null

    private fun prefs(context: Context) = context.applicationContext
        .getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** Every download, newest first. */
    @JvmStatic
    @Synchronized
    fun list(context: Context): List<DownloadTask> =
        (tasks(context)).sortedByDescending { it.updatedAt }

    @JvmStatic
    @Synchronized
    fun find(context: Context, id: Long): DownloadTask? = tasks(context).firstOrNull { it.id == id }

    /** The downloads that are still on their way: queued, running or paused. */
    @JvmStatic
    @Synchronized
    fun active(context: Context): List<DownloadTask> = tasks(context).filter { it.isActive }

    /** The next id; ids of the files written before are not handed out twice. */
    @JvmStatic
    @Synchronized
    fun nextId(context: Context): Long = (tasks(context).maxOfOrNull { it.id } ?: 0L) + 1

    @JvmStatic
    @Synchronized
    fun add(context: Context, task: DownloadTask) {
        val all = tasks(context)
        all.removeAll { it.id == task.id }
        all.add(task)
        write(context, all)
    }

    @JvmStatic
    @Synchronized
    fun update(context: Context, task: DownloadTask) {
        val all = tasks(context)
        val index = all.indexOfFirst { it.id == task.id }
        if (index >= 0) all[index] = task else all.add(task)
        write(context, all)
    }

    @JvmStatic
    @Synchronized
    fun remove(context: Context, id: Long) {
        val all = tasks(context)
        all.removeAll { it.id == id }
        write(context, all)
    }

    /** Forgets the finished downloads; the files stay. */
    @JvmStatic
    @Synchronized
    fun clearFinished(context: Context) {
        write(context, tasks(context).filter { it.isActive })
    }

    /**
     * Forgets every task record while leaving files alone. This is also useful to a future
     * "clear download history" action: cancelling active workers belongs to [DownloadEngine],
     * while the store only owns the records.
     */
    @JvmStatic
    @Synchronized
    fun clear(context: Context) {
        cache = mutableListOf()
        prefs(context).edit().remove(KEY).apply()
    }

    private fun tasks(context: Context): MutableList<DownloadTask> {
        cache?.let { return it }
        val loaded = ArrayList<DownloadTask>()
        try {
            val raw = prefs(context).getString(KEY, null)
            if (!raw.isNullOrEmpty()) {
                val json = JSONArray(raw)
                for (index in 0 until json.length()) {
                    val item = json.optJSONObject(index) ?: continue
                    DownloadTask.fromJson(item)?.let { loaded += it }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "The downloads could not be read: $e")
        }
        cache = loaded
        return loaded
    }

    private fun write(context: Context, tasks: List<DownloadTask>) {
        val kept = tasks.sortedByDescending { it.updatedAt }.take(MAX_ENTRIES)
        cache = kept.toMutableList()
        val json = JSONArray()
        for (task in kept) json.put(task.toJson())
        prefs(context).edit { putString(KEY, json.toString()) }
    }
}

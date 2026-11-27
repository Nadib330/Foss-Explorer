/*
 * Explorer: the built-in download manager.
 * Own code, GPL-3.0.
 *
 * 18.3.0 beta (items 5 to 11). Explorer used to hand every file to the download manager of
 * Android, and that is where three of the complaints came from: the file arrived under a name of
 * the manager's own (and an `.apk` came back as a `.bak`), it could not be paused, and the screen
 * of the browser knew nothing of its progress.
 *
 * The manager here is Explorer's own, shaped after the one in Chromium: a foreground service, one
 * worker per download, a notification in Chromium's layout, and a list that the downloads screen
 * and the download card both read. The file keeps **the name the server gave it**, extension and
 * all, and is written into the folder of Settings > Downloads.
 *
 * Pausing closes the connection and keeps what has arrived; resuming asks the server for the rest
 * with a `Range` request, so nothing is downloaded twice.
 */
package com.rpooi3.explorer.downloads

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.core.content.ContextCompat
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

object DownloadEngine {

    private const val TAG = "DownloadEngine"
    private const val BUFFER = 64 * 1024
    private const val NOTIFY_EVERY_MS = 500L

    /** The downloads that are on their way right now, by id. */
    private val workers = ConcurrentHashMap<Long, Worker>()

    /** Told about every change, for the notification and the screens. */
    private val listeners = CopyOnWriteArrayList<(DownloadTask) -> Unit>()

    @JvmStatic
    fun addListener(listener: (DownloadTask) -> Unit) {
        listeners += listener
    }

    @JvmStatic
    fun removeListener(listener: (DownloadTask) -> Unit) {
        listeners -= listener
    }

    /** Whether anything is being downloaded at this moment. */
    @JvmStatic
    fun isBusy(): Boolean = workers.isNotEmpty()

    /**
     * Starts a download.
     *
     * @param fileName the name the file will have, exactly as the server announced it
     * @return the id of the download, for pausing, resuming and the card
     */
    @JvmStatic
    @JvmOverloads
    fun enqueue(
        context: Context,
        url: String,
        fileName: String,
        mimeType: String?,
        userAgent: String? = null,
        cookie: String? = null,
    ): Long {
        val app = context.applicationContext
        val now = System.currentTimeMillis()
        val task = DownloadTask(
            id = DownloadStore.nextId(app),
            url = url,
            fileName = fileName,
            mimeType = mimeType,
            totalBytes = -1,
            downloadedBytes = 0,
            state = DownloadTask.State.QUEUED,
            createdAt = now,
            updatedAt = now,
            filePath = null,
            fileUri = null,
            userAgent = userAgent,
            cookie = cookie,
            error = null,
        )
        DownloadStore.add(app, task)
        announce(app, task)
        DownloadService.start(app)
        startWorker(app, task.id)
        return task.id
    }

    /** Holds a running download where it is. What has arrived is kept for the resume. */
    @JvmStatic
    fun pause(context: Context, id: Long) {
        val app = context.applicationContext
        val task = DownloadStore.find(app, id) ?: return
        val worker = workers[id]
        worker?.paused = true
        worker?.connection?.let { runCatching { it.disconnect() } }
        workers.remove(id)
        val paused = task.copy(state = DownloadTask.State.PAUSED, updatedAt = System.currentTimeMillis())
        DownloadStore.update(app, paused)
        announce(app, paused)
        DownloadService.refresh(app)
    }

    /** Carries on where a pause left off. */
    @JvmStatic
    fun resume(context: Context, id: Long) {
        val app = context.applicationContext
        val task = DownloadStore.find(app, id) ?: return
        if (task.state == DownloadTask.State.DONE || task.state == DownloadTask.State.CANCELLED) return
        DownloadStore.update(app, task.copy(state = DownloadTask.State.QUEUED, updatedAt = System.currentTimeMillis(), error = null))
        DownloadService.start(app)
        startWorker(app, id)
    }

    /** Gives up on a download; what has arrived is thrown away. */
    @JvmStatic
    fun cancel(context: Context, id: Long) {
        val app = context.applicationContext
        val worker = workers.remove(id)
        worker?.paused = true
        worker?.connection?.let { runCatching { it.disconnect() } }
        partFile(app, id).delete()
        DownloadStore.remove(app, id)
        DownloadNotifications.clear(app, id)
        DownloadService.refresh(app)
    }

    /** The file a download is written to before it is whole. */
    @JvmStatic
    fun partFile(context: Context, id: Long): File =
        File(File(context.applicationContext.cacheDir, "downloads").apply { if (!isDirectory) mkdirs() }, "$id.part")

    private fun startWorker(context: Context, id: Long) {
        if (workers.containsKey(id)) return
        val state = DownloadStore.find(context, id)?.state ?: return
        if (state != DownloadTask.State.QUEUED && state != DownloadTask.State.RUNNING) return
        val worker = Worker(context.applicationContext, id)
        workers[id] = worker
        Thread(worker, "download-$id").apply { isDaemon = true }.start()
    }

    private fun announce(context: Context, task: DownloadTask) {
        // The worker may report its first bytes before Android has finished creating the service.
        // Paint the per-download notice here as well as through the service listener; this keeps
        // Chromium's notification from losing the first (or last) state across that small race.
        runCatching {
            DownloadNotifications.update(context, task)
            DownloadNotifications.refreshSummary(context)
        }
        for (listener in listeners) {
            runCatching { listener(task) }
        }
    }

    /**
     * Re-attaches workers to tasks that were queued when the process was killed. A download must
     * not remain stuck in the list forever merely because Explorer was swiped away.
     */
    @JvmStatic
    fun restoreActive(context: Context) {
        val app = context.applicationContext
        for (task in DownloadStore.active(app)) {
            if (task.state == DownloadTask.State.QUEUED || task.state == DownloadTask.State.RUNNING) {
                if (task.state == DownloadTask.State.RUNNING) {
                    DownloadStore.update(
                        app,
                        task.copy(state = DownloadTask.State.QUEUED, updatedAt = System.currentTimeMillis()),
                    )
                }
                startWorker(app, task.id)
            }
        }
    }

    /** One download, on a thread of its own. */
    private class Worker(private val context: Context, private val id: Long) : Runnable {

        @Volatile
        var paused = false

        @Volatile
        var connection: HttpURLConnection? = null

        override fun run() {
            var task = DownloadStore.find(context, id) ?: return finish()
            val part = partFile(context, id)
            try {
                var startAt = if (part.isFile) part.length() else 0L
                val request = (URL(task.url).openConnection() as HttpURLConnection).apply {
                    instanceFollowRedirects = true
                    connectTimeout = 30_000
                    readTimeout = 30_000
                    task.userAgent?.let { setRequestProperty("User-Agent", it) }
                    task.cookie?.takeIf { it.isNotBlank() }?.let { setRequestProperty("Cookie", it) }
                    if (startAt > 0) setRequestProperty("Range", "bytes=$startAt-")
                }
                connection = request
                request.connect()
                if (paused) return
                val code = request.responseCode
                if (code !in 200..299) throw IOException("HTTP $code")
                // A server that does not know ranges sends the whole file again.
                if (code != 206 && startAt > 0) {
                    part.delete()
                    startAt = 0
                }
                val length = request.contentLengthLong
                val total = if (length > 0) startAt + length else -1
                var written = startAt
                task = task.copy(
                    state = DownloadTask.State.RUNNING,
                    totalBytes = total,
                    downloadedBytes = written,
                    updatedAt = System.currentTimeMillis(),
                )
                DownloadStore.update(context, task)
                announce(context, task)
                DownloadService.refresh(context)
                var last = 0L
                request.inputStream.use { input ->
                    FileOutputStream(part, true).use { output ->
                        val buffer = ByteArray(BUFFER)
                        while (true) {
                            if (paused) return
                            val read = input.read(buffer)
                            if (read < 0) break
                            output.write(buffer, 0, read)
                            written += read
                            val now = System.currentTimeMillis()
                            if (now - last >= NOTIFY_EVERY_MS) {
                                last = now
                                task = task.copy(downloadedBytes = written, updatedAt = now)
                                DownloadStore.update(context, task)
                                announce(context, task)
                            }
                        }
                    }
                }
                if (paused) return
                // Whole: the file takes its real name and goes to the folder of the settings.
                val (uri, path) = DownloadDirs.publish(context, task.fileName, part, task.mimeType)
                val done = task.copy(
                    state = DownloadTask.State.DONE,
                    totalBytes = if (total > 0) total else written,
                    downloadedBytes = if (total > 0) total else written,
                    updatedAt = System.currentTimeMillis(),
                    filePath = path,
                    fileUri = uri,
                )
                DownloadStore.update(context, done)
                part.delete()
                announce(context, done)
            } catch (e: Exception) {
                if (paused) return
                Log.w(TAG, "The download $id stopped: $e")
                val failed = (DownloadStore.find(context, id) ?: task).copy(
                    state = DownloadTask.State.FAILED,
                    updatedAt = System.currentTimeMillis(),
                    error = e.message ?: e.javaClass.simpleName,
                )
                DownloadStore.update(context, failed)
                announce(context, failed)
            } finally {
                finish()
            }
        }

        private fun finish() {
            connection = null
            workers.remove(id)
            DownloadService.refresh(context)
        }
    }

    /** Opens a finished download; an `.apk` goes to the installer of Android. */
    @JvmStatic
    fun open(context: Context, task: DownloadTask): Boolean {
        val uri: Uri = task.fileUri?.let { runCatching { Uri.parse(it) }.getOrNull() }
            ?: task.filePath?.let { ApkInstaller.uriOf(context, File(it)) }
            ?: return false
        val mime = task.mimeType ?: context.contentResolver.getType(uri) ?: "*/*"
        return ApkInstaller.open(context, uri, mime, task.fileName)
    }

    /** The service is started by the first download and stops itself when the last one is over. */
    internal fun startService(context: Context) = DownloadService.start(context)

    internal fun stopService(context: Context) = DownloadService.refresh(context)
}

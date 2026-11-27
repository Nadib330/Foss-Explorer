/*
 * Explorer: the notification of a download, laid out as Chromium lays it out.
 * Own code, GPL-3.0.
 *
 * 18.3.0 beta (item 10): Chromium's downloads notification has the name of the file as its title,
 * a line with the progress under it ("47% · 12.5 MB of 26.4 MB · 1.2 MB/s"), the bar of the
 * progress as its track, and a row of buttons — pause, and cancel — under it. When the file is
 * there the bar is gone, the title stays and the buttons become "Open" and (for a file that can be
 * shared) nothing else. Tapping a download that is still arriving opens the downloads screen, as
 * tapping Chrome's notification does.
 */
package com.rpooi3.explorer.downloads

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.text.format.Formatter
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.rpooi3.explorer.R
import java.io.File

object DownloadNotifications {

    const val CHANNEL = "downloads"

    /** Every download notification is this one, plus the id of its download. */
    private const val BASE_ID = 8_100

    @JvmStatic
    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        if (manager.getNotificationChannel(CHANNEL) != null) return
        val channel = NotificationChannel(
            CHANNEL,
            context.getString(R.string.download_channel_name),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = context.getString(R.string.download_channel_description)
            setShowBadge(false)
            enableVibration(false)
        }
        manager.createNotificationChannel(channel)
    }

    /** The notification of one download, as it looks at this moment. */
    @JvmStatic
    fun update(context: Context, task: DownloadTask) {
        ensureChannel(context)
        val notification = when (task.state) {
            DownloadTask.State.RUNNING, DownloadTask.State.QUEUED, DownloadTask.State.PAUSED ->
                running(context, task)
            DownloadTask.State.DONE -> done(context, task)
            DownloadTask.State.FAILED, DownloadTask.State.CANCELLED -> failed(context, task)
        }
        notify(context, task.id, notification)
    }

    private fun running(context: Context, task: DownloadTask): Notification {
        val percent = task.percent
        val builder = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(task.fileName)
            .setContentText(progressLine(context, task))
            .setOnlyAlertOnce(true)
            .setOngoing(task.state != DownloadTask.State.PAUSED)
            .setSilent(true)
            .setContentIntent(openDownloads(context))
            .setProgress(100, percent ?: 0, percent == null)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
        if (task.state == DownloadTask.State.PAUSED) {
            builder.addAction(
                android.R.drawable.ic_media_play,
                context.getString(R.string.download_action_resume),
                command(context, DownloadService.ACTION_RESUME, task.id),
            )
        } else {
            builder.addAction(
                android.R.drawable.ic_media_pause,
                context.getString(R.string.download_action_pause),
                command(context, DownloadService.ACTION_PAUSE, task.id),
            )
        }
        builder.addAction(
            android.R.drawable.ic_menu_close_clear_cancel,
            context.getString(R.string.download_action_cancel),
            command(context, DownloadService.ACTION_CANCEL, task.id),
        )
        return builder.build()
    }

    private fun done(context: Context, task: DownloadTask): Notification {
        val builder = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle(task.fileName)
            .setContentText(context.getString(R.string.download_notification_done))
            .setOnlyAlertOnce(false)
            .setAutoCancel(true)
            .setContentIntent(openDownloads(context))
            .setCategory(NotificationCompat.CATEGORY_STATUS)
        val open = openFile(context, task)
        if (open != null) {
            builder.addAction(android.R.drawable.ic_menu_view, context.getString(R.string.download_chip_open), open)
        }
        return builder.build()
    }

    private fun failed(context: Context, task: DownloadTask): Notification =
        NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle(task.fileName)
            .setContentText(context.getString(R.string.download_notification_failed))
            .setAutoCancel(true)
            .setContentIntent(openDownloads(context))
            .build()

    /** "47% · 12.5 MB of 26.4 MB", or "Waiting to start", or "Paused · 47%". */
    private fun progressLine(context: Context, task: DownloadTask): String {
        val percent = task.percent
        val done = Formatter.formatShortFileSize(context, task.downloadedBytes)
        val total = if (task.totalBytes > 0) Formatter.formatShortFileSize(context, task.totalBytes) else null
        return when {
            task.state == DownloadTask.State.QUEUED -> context.getString(R.string.download_notification_waiting)
            percent != null && task.state == DownloadTask.State.PAUSED ->
                context.getString(R.string.download_notification_paused_percent, percent, done)
            percent != null && total != null ->
                context.getString(R.string.download_notification_percent, percent, done, total)
            percent != null -> context.getString(R.string.downloads_running_percent, percent)
            else -> context.getString(R.string.downloads_running)
        }
    }

    /** The notice of the service itself: what is still arriving, and how much of it. */
    @JvmStatic
    fun summary(context: Context): Notification {
        ensureChannel(context)
        val active = DownloadStore.active(context)
        val running = active.firstOrNull { it.state == DownloadTask.State.RUNNING }
        val title = when {
            running != null -> running.fileName
            active.isNotEmpty() -> active.first().fileName
            else -> context.getString(R.string.download_channel_name)
        }
        val text = running?.let { progressLine(context, it) }
            ?: context.getString(R.string.download_notification_waiting)
        return NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(title)
            .setContentText(text)
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .setSilent(true)
            .setContentIntent(openDownloads(context))
            .setProgress(100, running?.percent ?: 0, running == null)
            .build()
    }

    /** Refreshes the foreground-service summary when a worker reports new bytes. */
    @JvmStatic
    fun refreshSummary(context: Context) {
        ensureChannel(context)
        try {
            NotificationManagerCompat.from(context).notify(DownloadService.NOTIFICATION_ID, summary(context))
        } catch (_: SecurityException) {
            // The file continues even when Android's notification permission is off.
        }
    }

    @JvmStatic
    fun cancelSummary(context: Context) {
        val manager = NotificationManagerCompat.from(context)
        runCatching { manager.cancel(DownloadService.NOTIFICATION_ID) }
    }

    @JvmStatic
    fun clear(context: Context, id: Long) {
        runCatching { NotificationManagerCompat.from(context).cancel(BASE_ID + id.toInt()) }
    }

    private fun notify(context: Context, id: Long, notification: Notification) {
        val manager = NotificationManagerCompat.from(context)
        try {
            manager.notify(BASE_ID + id.toInt(), notification)
        } catch (e: SecurityException) {
            // Without the notification permission the download still runs; only the notice is gone.
        }
    }

    private fun openDownloads(context: Context): PendingIntent {
        val intent = Intent(context, DownloadsActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun openFile(context: Context, task: DownloadTask): PendingIntent? {
        val uri: Uri = task.fileUri?.let { runCatching { Uri.parse(it) }.getOrNull() }
            ?: task.filePath?.let { ApkInstaller.uriOf(context, File(it)) }
            ?: return null
        val mime = task.mimeType ?: context.contentResolver.getType(uri) ?: "*/*"
        val intent = ApkInstaller.viewIntent(uri, mime).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return PendingIntent.getActivity(
            context,
            task.id.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun command(context: Context, action: String, id: Long): PendingIntent {
        val intent = Intent(context, DownloadService::class.java).setAction(action)
            .putExtra(DownloadService.EXTRA_ID, id)
        return PendingIntent.getService(
            context,
            (action.hashCode() + id.toInt()) and 0xFFFF,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}

/*
 * Explorer: the service that keeps the built-in downloads running.
 * Own code, GPL-3.0.
 *
 * 18.3.0 beta (item 9): Chromium downloads in a service of its own, so that a file keeps arriving
 * while the user reads another page, closes the tab it came from, or leaves the browser. This is
 * that service: a foreground service of the type "data sync", with the notification of
 * [DownloadNotifications] in the shade, which goes away by itself when nothing is left to
 * download.
 */
package com.rpooi3.explorer.downloads

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.content.ContextCompat

class DownloadService : Service() {

    private val listener: (DownloadTask) -> Unit = { task ->
        DownloadNotifications.update(this, task)
        if (!DownloadStore.active(this).any { it.state == DownloadTask.State.RUNNING || it.state == DownloadTask.State.QUEUED }) {
            // Nothing is on its way: the notification goes, and the service with it.
            if (!DownloadEngine.isBusy()) stop()
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        DownloadEngine.addListener(listener)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: ACTION_START
        val id = intent?.getLongExtra(EXTRA_ID, -1L) ?: -1L
        when (action) {
            ACTION_PAUSE -> if (id > 0) DownloadEngine.pause(this, id)
            ACTION_RESUME -> if (id > 0) DownloadEngine.resume(this, id)
            ACTION_CANCEL -> if (id > 0) DownloadEngine.cancel(this, id)
            ACTION_START -> DownloadEngine.restoreActive(this)
        }
        goForeground()
        if (!DownloadEngine.isBusy() && DownloadStore.active(this).isEmpty()) stop()
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        DownloadEngine.removeListener(listener)
        super.onDestroy()
    }

    private fun goForeground() {
        val notification = DownloadNotifications.summary(this)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (e: Exception) {
            // A service that may not stand in front still downloads; only its notice is missing.
            runCatching { startForeground(NOTIFICATION_ID, notification) }
        }
    }

    private fun stop() {
        DownloadNotifications.cancelSummary(this)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        stopSelf()
    }

    companion object {
        const val ACTION_START = "com.rpooi3.explorer.downloads.START"
        const val ACTION_PAUSE = "com.rpooi3.explorer.downloads.PAUSE"
        const val ACTION_RESUME = "com.rpooi3.explorer.downloads.RESUME"
        const val ACTION_CANCEL = "com.rpooi3.explorer.downloads.CANCEL"
        const val EXTRA_ID = "com.rpooi3.explorer.downloads.ID"
        const val NOTIFICATION_ID = 4711

        @JvmStatic
        fun start(context: Context) {
            val intent = Intent(context, DownloadService::class.java).setAction(ACTION_START)
            try {
                ContextCompat.startForegroundService(context, intent)
            } catch (e: Exception) {
                runCatching { context.startService(intent) }
            }
        }

        /** Tells the service to look at the downloads again; it leaves when they are all done. */
        @JvmStatic
        fun refresh(context: Context) {
            if (!DownloadEngine.isBusy() && DownloadStore.active(context).none {
                    it.state == DownloadTask.State.RUNNING || it.state == DownloadTask.State.QUEUED
                }
            ) {
                context.stopService(Intent(context, DownloadService::class.java))
            }
        }

        /** Pause, resume or cancel from the button in a notification. */
        @JvmStatic
        fun command(context: Context, action: String, id: Long) {
            when (action) {
                ACTION_PAUSE -> DownloadEngine.pause(context, id)
                ACTION_RESUME -> DownloadEngine.resume(context, id)
                ACTION_CANCEL -> DownloadEngine.cancel(context, id)
            }
        }
    }
}

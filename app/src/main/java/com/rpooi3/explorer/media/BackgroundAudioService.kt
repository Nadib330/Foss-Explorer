/*
 * Explorer: the service behind the sound that keeps playing in the background.
 * Own code, GPL-3.0.
 *
 * 18.0.0 beta 9 (item 1). A foreground service of the type `mediaPlayback`, with a `MediaSession`
 * that the phone's own media controls — the lock screen, the notification shade, Bluetooth
 * buttons — can play and pause, and a notification in the style of Android's media players. The
 * commands are handed to the tab that was in front when the browser was left; the WebView itself
 * keeps playing, which is the whole point of the service.
 */
package com.rpooi3.explorer.media

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.MediaMetadata
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import com.rpooi3.explorer.R
import com.rpooi3.explorer.view.NinjaWebView

class BackgroundAudioService : Service() {

    companion object {
        const val ACTION_START = "com.rpooi3.explorer.media.START"
        const val ACTION_STOP = "com.rpooi3.explorer.media.STOP"
        const val ACTION_TOGGLE = "com.rpooi3.explorer.media.TOGGLE"
        const val EXTRA_TITLE = "title"

        private const val CHANNEL = "explorer_playback"
        private const val NOTIFICATION_ID = 2

        /** The service that was started, so the browser can ask whether it is running. */
        private var running = false

        /** The tab the sound belongs to, so that the buttons of the notification reach it. */
        private var tab: NinjaWebView? = null

        @JvmStatic
        fun isRunning(): Boolean = running

        @JvmStatic
        fun attach(webView: NinjaWebView?) {
            tab = webView
        }
    }

    private var session: MediaSession? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var playing = true
    private var title: String? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopEverything()
                return START_NOT_STICKY
            }
            ACTION_TOGGLE -> {
                playing = !playing
                playInPage(playing)
                publish()
                return START_STICKY
            }
            else -> {
                title = intent?.getStringExtra(EXTRA_TITLE) ?: title
                start();
            }
        }
        return START_STICKY
    }

    private fun start() {
        running = true
        createChannel()
        startForegroundCompat()
        takeWakeLock()
        createSession()
        publish()
    }

    private fun startForegroundCompat() {
        val notification = buildNotification()
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (e: Throwable) {
            // Without the foreground state the sound still plays as long as the process lives.
            Log.w("ExplorerAudio", "The service did not come to the front: " + e)
        }
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL) != null) return
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL, getString(R.string.media_playback_channel), NotificationManager.IMPORTANCE_LOW).apply {
                description = getString(R.string.media_playback_channel_description)
                setShowBadge(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            },
        )
    }

    private fun buildNotification(): Notification {
        val open = PendingIntent.getActivity(
            this,
            0,
            Intent(this, BackgroundAudio.activityClass()).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val toggle = PendingIntent.getService(
            this,
            1,
            Intent(this, BackgroundAudioService::class.java).apply { action = ACTION_TOGGLE },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val stop = PendingIntent.getService(
            this,
            2,
            Intent(this, BackgroundAudioService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val where = title ?: getString(R.string.app_name)
        val style = android.app.Notification.MediaStyle().apply {
            session?.let { setMediaSession(it.sessionToken) }
            setShowActionsInCompactView(0, 1)
        }
        val actions = if (playing) {
            listOf(android.app.Notification.Action.Builder(
                android.graphics.drawable.Icon.createWithResource(this, R.drawable.icon_pause),
                getString(R.string.media_pause), toggle,
            ).build())
        } else {
            listOf(android.app.Notification.Action.Builder(
                android.graphics.drawable.Icon.createWithResource(this, R.drawable.icon_play),
                getString(R.string.media_play), toggle,
            ).build())
        }
        return Notification.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.icon_notification)
            .setContentTitle(where)
            .setContentText(getString(if (playing) R.string.media_playing_where else R.string.media_paused_where, where))
            .setContentIntent(open)
            .setDeleteIntent(stop)
            .setOngoing(playing)
            .setOnlyAlertOnce(true)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setStyle(style)
            .apply { actions.forEach { addAction(it) } }
            .build()
    }

    private fun createSession() {
        if (session != null) return
        val created = MediaSession(this, "Explorer").apply {
            setCallback(object : MediaSession.Callback() {
                override fun onPlay() {
                    playing = true
                    playInPage(true)
                    publish()
                }

                override fun onPause() {
                    playing = false
                    playInPage(false)
                    publish()
                }

                override fun onStop() {
                    playInPage(false)
                    stopEverything()
                }
            })
            isActive = true
        }
        session = created
    }

    /** Tells the tab in front to play or pause; the page does it for its own media elements. */
    private fun playInPage(play: Boolean) {
        val webView = tab ?: return
        try {
            webView.evaluateJavascript(BackgroundAudio.playScript(play), null)
        } catch (e: Throwable) {
            Log.w("ExplorerAudio", "The page did not answer: " + e)
        }
    }

    private fun publish() {
        val manager = getSystemService(NotificationManager::class.java)
        try {
            manager?.notify(NOTIFICATION_ID, buildNotification())
        } catch (e: Throwable) {
            Log.w("ExplorerAudio", "The notification stayed as it was: " + e)
        }
        session?.apply {
            val state = if (playing) PlaybackState.STATE_PLAYING else PlaybackState.STATE_PAUSED
            setPlaybackState(
                PlaybackState.Builder()
                    .setActions(PlaybackState.ACTION_PLAY or PlaybackState.ACTION_PAUSE or PlaybackState.ACTION_STOP)
                    .setState(state, PlaybackState.PLAYBACK_POSITION_UNKNOWN, 1f)
                    .build(),
            )
            val where = title ?: getString(R.string.app_name)
            setMetadata(
                MediaMetadata.Builder()
                    .putString(MediaMetadata.METADATA_KEY_TITLE, where)
                    .putString(MediaMetadata.METADATA_KEY_ARTIST, getString(R.string.app_name))
                    .build(),
            )
        }
    }

    private fun takeWakeLock() {
        if (wakeLock != null) return
        try {
            val power = getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return
            wakeLock = power.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "explorer:playback").apply {
                setReferenceCounted(false)
                acquire(60 * 60 * 1000L)
            }
        } catch (e: Throwable) {
            Log.w("ExplorerAudio", "No wake lock: " + e)
        }
    }

    private fun stopEverything() {
        running = false
        try {
            wakeLock?.let { if (it.isHeld) it.release() }
        } catch (e: Throwable) {
            // A lock that was already released.
        }
        wakeLock = null
        session?.apply {
            isActive = false
            release()
        }
        session = null
        stopForeground(true)
        stopSelf()
    }

    override fun onDestroy() {
        running = false
        try {
            wakeLock?.let { if (it.isHeld) it.release() }
        } catch (e: Throwable) {
            // Already released.
        }
        wakeLock = null
        session?.release()
        session = null
        super.onDestroy()
    }
}

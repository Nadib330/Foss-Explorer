/*
 * Foss Explorer: the notice of a download (18.0.0 beta 6, items 5 and 6).
 * Own code, GPL-3.0.
 */
package com.rpooi3.explorer.downloads

import android.app.Activity
import android.app.DownloadManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.core.view.isVisible
import com.google.android.material.button.MaterialButton
import com.rpooi3.explorer.R
import com.rpooi3.explorer.view.CatToast

/**
 * The small card at the foot of the page that says a download has started.
 *
 * 18.0.0 beta 6 follows Chrome:
 *
 *  * **item 5 — the animation of a download that starts.** A card rises from the bottom edge with
 *    the arrow of a download on it, the ring turns while the size is still unknown, then fills up
 *    with the bytes that have arrived, and the name of the file stands beside it. It is Chrome's
 *    download bubble, brought to a phone.
 *  * **item 6 — the way to the file once it is there.** When the download is done the ring gives
 *    way to a check mark, the card says "Downloaded", and an **Open** button starts the file in
 *    whatever app the device has for it — exactly what Chrome offers when its bubble turns green.
 *    APKs stay here until the user explicitly taps Open.
 *
 * The card is put over the page of the [BrowserActivity] it belongs to (`main_content`), it never
 * covers the bars, and it goes away by itself after a while or when the cross is tapped.
 */
object DownloadChip {

    private const val TAG = "DownloadChip"
    private const val POLL_MS = 400L
    private const val LINGER_MS = 8000L

    /** The card that is up, if any; one at a time, as Chrome keeps one bubble. */
    private var chip: View? = null
    private val handler = Handler(Looper.getMainLooper())
    private var polling: Runnable? = null

    /** The ring, then a check, then nothing: how a download ends. */
    private fun stopPolling() {
        polling?.let { handler.removeCallbacks(it) }
        polling = null
    }

    /**
     * 18.0.0 beta 6 (item 5): a download has started and the system's download manager knows it by
     * [id]. The card follows it to the end.
     */
    @JvmStatic
    fun started(activity: Activity?, name: String, id: Long) {
        // 18.4.0: the download is Explorer's own; the card reads it from there, and
        // the button of item 6 pauses it and carries it on.
        if (activity == null) return
        show(activity, name)
        follow(activity, id, name)
    }

    /** Follows a download of the built-in manager: the bar, the percentage, pause and resume. */
    private fun follow(activity: Activity, id: Long, name: String) {
        stopPolling()
        val pauseButton = chip?.findViewById<ImageButton>(R.id.download_chip_pause)
        pauseButton?.isVisible = true
        pauseButton?.setOnClickListener {
            val task = DownloadStore.find(activity, id) ?: return@setOnClickListener
            if (task.state == DownloadTask.State.PAUSED) DownloadEngine.resume(activity, id)
            else DownloadEngine.pause(activity, id)
        }
        val runnable = object : Runnable {
            override fun run() {
                val task = DownloadStore.find(activity, id)
                if (task == null) {
                    hide()
                    return
                }
                when (task.state) {
                    DownloadTask.State.DONE -> {
                        finished(activity, task.fileName, taskUri(activity, task))
                        return
                    }
                    DownloadTask.State.FAILED, DownloadTask.State.CANCELLED -> {
                        failed(activity, task.fileName)
                        return
                    }
                    else -> {
                        val chipView = chip ?: return
                        val progress = chipView.findViewById<ProgressBar>(R.id.download_chip_progress)
                        val status = chipView.findViewById<TextView>(R.id.download_chip_status)
                        val percent = task.percent
                        if (percent != null) {
                            progress.isIndeterminate = false
                            progress.max = 100
                            progress.progress = percent
                            status.text = if (task.state == DownloadTask.State.PAUSED) {
                                activity.getString(R.string.downloads_paused_percent, percent)
                            } else {
                                activity.getString(R.string.download_chip_percent, percent)
                            }
                        } else {
                            progress.isIndeterminate = true
                            status.text = activity.getString(R.string.download_chip_started)
                        }
                        // The button shows what the tap would do now.
                        val pause = chipView.findViewById<ImageButton>(R.id.download_chip_pause)
                        pause.isVisible = true
                        pause.setImageResource(
                            if (task.state == DownloadTask.State.PAUSED) R.drawable.icon_play else R.drawable.icon_pause,
                        )
                        pause.contentDescription = activity.getString(
                            if (task.state == DownloadTask.State.PAUSED) R.string.download_action_resume
                            else R.string.download_action_pause,
                        )
                        handler.postDelayed(this, POLL_MS)
                    }
                }
            }
        }
        polling = runnable
        handler.postDelayed(runnable, POLL_MS)
    }

    /** The address of a finished download, for the Open button. */
    private fun taskUri(context: Context, task: DownloadTask): Uri? =
        task.fileUri?.let { runCatching { Uri.parse(it) }.getOrNull() }
            ?: task.filePath?.let { ApkInstaller.uriOf(context, java.io.File(it)) }

    /** A download this app writes itself (`data:` and files from a script) has no manager id. */
    @JvmStatic
    fun startedLocal(activity: Activity?, name: String) {
        if (activity == null) return
        show(activity, name)
        setStatus(activity, activity.getString(R.string.download_chip_started))
    }

    /**
     * 18.0.0 beta 6 (item 6): the file is written. The card says so and offers to open it, and goes
     * by itself after a few seconds.
     */
    @JvmStatic
    fun finished(activity: Activity?, name: String, uri: Uri?) {
        if (activity == null) return
        stopPolling()
        if (null == chip) show(activity, name)
        val chipView = chip ?: return
        chipView.findViewById<ProgressBar>(R.id.download_chip_progress).isVisible = false
        val icon = chipView.findViewById<ImageView>(R.id.download_chip_icon)
        icon.setImageResource(R.drawable.icon_check)
        icon.isVisible = true
        // A short bounce, as Chrome's arrow settles when the file is there.
        icon.scaleX = 0.6f
        icon.scaleY = 0.6f
        icon.animate().scaleX(1f).scaleY(1f).setDuration(220L)
            .setInterpolator(OvershootInterpolator()).start()
        chipView.findViewById<TextView>(R.id.download_chip_status).text =
            activity.getString(R.string.download_chip_done)
        // 18.4.0: finishing an APK never launches Android's installer. The Open button is the
        // explicit user action that hands the package to Android, just like any other file.
        chipView.findViewById<ImageButton?>(R.id.download_chip_pause)?.isVisible = false
        val open = chipView.findViewById<MaterialButton>(R.id.download_chip_open)
        open.isVisible = true
        open.setOnClickListener { openFile(activity, uri) }
        handler.postDelayed({ hide() }, LINGER_MS)
    }

    /** The download did not arrive. */
    @JvmStatic
    fun failed(activity: Activity?, name: String) {
        if (activity == null) return
        stopPolling()
        if (null == chip) show(activity, name)
        val chipView = chip ?: return
        chipView.findViewById<ImageButton?>(R.id.download_chip_pause)?.isVisible = false
        chipView.findViewById<ProgressBar>(R.id.download_chip_progress).isVisible = false
        chipView.findViewById<TextView>(R.id.download_chip_status).text =
            activity.getString(R.string.download_chip_failed)
        handler.postDelayed({ hide() }, LINGER_MS / 2)
    }

    /** Takes the card away. */
    @JvmStatic
    fun hide() {
        stopPolling()
        val view = chip ?: return
        chip = null
        view.animate().alpha(0f).translationY(dp(view, 24)).setDuration(160L)
            .withEndAction { (view.parent as? ViewGroup)?.removeView(view) }
            .start()
    }

    /** Whether the card is on the screen; for the tests. */
    @JvmStatic
    fun isShowing(): Boolean = chip != null

    // --- the card itself -----------------------------------------------------------------------

    private fun show(activity: Activity, name: String) {
        // A card left over from another screen (or from a test that has ended) is not shown again.
        if (null != chip && null == chip?.parent) {
            chip = null
            stopPolling()
        }
        val parent = activity.findViewById<ViewGroup>(R.id.overlay_content) ?: return
        if (null == chip) {
            val view = activity.layoutInflater.inflate(R.layout.view_download_chip, parent, false)
            val params = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply { gravity = android.view.Gravity.BOTTOM }
            parent.addView(view, params)
            view.findViewById<TextView>(R.id.download_chip_title).text = name
            view.findViewById<View>(R.id.download_chip_close).setOnClickListener { hide() }
            // The card rises into place, as Chrome's bubble does.
            view.alpha = 0f
            view.translationY = dp(view, 32)
            view.animate().alpha(1f).translationY(0f).setDuration(220L)
                .setInterpolator(DecelerateInterpolator()).start()
            val icon = view.findViewById<ImageView>(R.id.download_chip_icon)
            icon.isVisible = false
            // Once the size is known the ring counts instead of turning.
            val progress = view.findViewById<ProgressBar>(R.id.download_chip_progress)
            progress.isVisible = true
            chip = view
        }
        setStatus(activity, activity.getString(R.string.download_chip_started))
        val open = chip?.findViewById<MaterialButton>(R.id.download_chip_open)
        open?.isVisible = false
        chip?.findViewById<ImageButton>(R.id.download_chip_pause)?.isVisible = false
    }

    private fun setStatus(activity: Activity, text: String) {
        chip?.findViewById<TextView>(R.id.download_chip_status)?.text = text
    }

    private fun poll(activity: Activity, manager: DownloadManager, id: Long, name: String) {
        stopPolling()
        val runnable = object : Runnable {
            override fun run() {
                val state = state(manager, id)
                when {
                    state == null -> {
                        // The entry is gone (deleted, or the provider is not there): nothing to say.
                        hide()
                    }
                    state.done -> finished(activity, name, manager.downloadUri(id))
                    state.failed -> failed(activity, name)
                    else -> {
                        val chipView = chip ?: return
                        val progress = chipView.findViewById<ProgressBar>(R.id.download_chip_progress)
                        val status = chipView.findViewById<TextView>(R.id.download_chip_status)
                        if (state.total > 0) {
                            val percent = (state.received * 100 / state.total).toInt().coerceIn(0, 100)
                            progress.isIndeterminate = false
                            progress.max = 100
                            progress.progress = percent
                            status.text = activity.getString(R.string.download_chip_percent, percent)
                        } else {
                            progress.isIndeterminate = true
                            status.text = activity.getString(R.string.download_chip_started)
                        }
                        handler.postDelayed(this, POLL_MS)
                    }
                }
            }
        }
        polling = runnable
        handler.postDelayed(runnable, POLL_MS)
    }

    /** What the download manager says about [id]; null when it knows nothing of it. */
    private class State(val total: Long, val received: Long, val done: Boolean, val failed: Boolean)

    private fun state(manager: DownloadManager, id: Long): State? = try {
        val cursor = manager.query(DownloadManager.Query().setFilterById(id))
        try {
            if (!cursor.moveToFirst()) {
                null
            } else {
                val status = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
                val total = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
                val received = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
                State(
                    total,
                    received,
                    status == DownloadManager.STATUS_SUCCESSFUL,
                    status == DownloadManager.STATUS_FAILED,
                )
            }
        } finally {
            cursor.close()
        }
    } catch (e: Exception) {
        Log.w(TAG, "Download $id is not readable: $e")
        null
    }

    /** 18.0.0 beta 6 (item 6): starts the file in the app the device has for it. */
    @JvmStatic
    fun openFile(activity: Activity, uri: Uri?) {
        if (uri == null) {
            CatToast.show(activity, R.string.download_chip_cannot_open)
            return
        }
        // 18.4.0: a package is handed to Android only after the user taps Open.
        if (uri.toString().endsWith(".apk", ignoreCase = true) || ApkInstaller.isPackage(uri.lastPathSegment)) {
            if (ApkInstaller.open(activity, uri, ApkInstaller.MIME_APK, uri.lastPathSegment)) {
                hide()
                return
            }
        }
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, activity.contentResolver.getType(uri) ?: "*/*")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            activity.startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            CatToast.show(activity, R.string.download_chip_cannot_open)
        } catch (e: SecurityException) {
            CatToast.show(activity, R.string.download_chip_cannot_open)
        }
        hide()
    }

    private fun dp(view: View, value: Int): Float =
        value * view.resources.displayMetrics.density

    /** The download manager's own address of a finished download, for the Open button. */
    private fun DownloadManager.downloadUri(id: Long): Uri? = try {
        getUriForDownloadedFile(id)
    } catch (e: Exception) {
        null
    }
}

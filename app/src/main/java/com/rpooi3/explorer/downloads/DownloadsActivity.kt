package com.rpooi3.explorer.downloads

import android.app.DownloadManager
import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.os.StatFs
import android.text.format.Formatter
import android.view.View
import com.rpooi3.explorer.view.MainMenuPopup
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.rpooi3.explorer.R
import com.rpooi3.explorer.downloads.DownloadItem.Kind
import com.rpooi3.explorer.downloads.DownloadItem.State
import com.rpooi3.explorer.library.DateGroups
import com.rpooi3.explorer.library.LibraryActivity
import com.rpooi3.explorer.unit.HelperUtils
import com.rpooi3.explorer.view.CatToast

/**
 * The downloads screen, laid out like Chrome's: filter chips for the kind of
 * file, a line about storage, and the files under a heading per day, newest
 * first. A row shows the file name with its size and the website it came
 * from, or the progress while it is still loading. A tap opens the file; the
 * three-dot button shares or deletes it.
 */
class DownloadsActivity : LibraryActivity() {

    override val titleRes = R.string.downloads_title
    override val searchHintRes = R.string.downloads_search_hint
    override val emptyTextRes = R.string.downloads_empty
    override val emptyIconRes = R.drawable.icon_download
    override val itemActionIconRes = R.drawable.icon_more_vert
    override val itemActionLabelRes = R.string.library_more_actions

    private var downloads: List<DownloadItem> = emptyList()
    private val handler = Handler(Looper.getMainLooper())
    private val refresh = Runnable { reload() }

    override fun filters(): List<Filter> {
        // Read once per reload; loadRows and infoText use the same snapshot.
        downloads = Downloads.list(this)
        if (downloads.isEmpty()) return emptyList()
        return listOf(
            Filter(FILTER_ALL, getString(R.string.library_filter_all)),
            Filter(Kind.VIDEO.ordinal.toLong(), getString(R.string.downloads_filter_video)),
            Filter(Kind.AUDIO.ordinal.toLong(), getString(R.string.downloads_filter_audio)),
            Filter(Kind.IMAGE.ordinal.toLong(), getString(R.string.downloads_filter_images)),
            Filter(Kind.DOCUMENT.ordinal.toLong(), getString(R.string.downloads_filter_documents)),
            Filter(Kind.OTHER.ordinal.toLong(), getString(R.string.downloads_filter_other)),
        )
    }

    override fun loadRows(query: String, filterId: Long): List<Row> {
        val shown = downloads
            .filter { filterId == FILTER_ALL || filterId == NO_FILTER || it.kind.ordinal.toLong() == filterId }
            .filter { query.isEmpty() || it.name.contains(query, ignoreCase = true) }
        val rows = ArrayList<Row>(shown.size + 8)
        val now = System.currentTimeMillis()
        val today = getString(R.string.date_today)
        val yesterday = getString(R.string.date_yesterday)
        var lastDay = Int.MIN_VALUE
        for (item in shown) {
            // A download without a timestamp is one that has only just been queued.
            val time = if (item.time > 0) item.time else now
            val day = DateGroups.dayKey(time)
            if (day != lastDay) {
                lastDay = day
                rows += Row.Header(DateGroups.heading(time, now, today, yesterday))
            }
            rows += Row.Item(item.name, describe(item), null, iconOf(item.kind), item)
        }
        scheduleRefresh()
        return rows
    }

    /** The second line of a row: size and origin, or what the download is doing. */
    fun describe(item: DownloadItem): String {
        val site = item.sourceUrl?.let { HelperUtils.domain(it) }?.takeIf { it.isNotBlank() }
        return when (item.state) {
            State.DONE -> listOfNotNull(
                if (item.totalBytes >= 0) Formatter.formatShortFileSize(this, item.totalBytes) else null,
                site,
            ).joinToString(SEPARATOR)
            // 18.3.0 beta (item 7): a download that is running says how far it is, in per cent
            // and in bytes, and a paused one says that it is paused.
            State.RUNNING -> item.percent?.let {
                getString(
                    R.string.downloads_running_detail,
                    it,
                    Formatter.formatShortFileSize(this, item.downloadedBytes),
                    Formatter.formatShortFileSize(this, item.totalBytes),
                )
            } ?: getString(R.string.downloads_running)
            State.PAUSED -> item.percent?.let { getString(R.string.downloads_paused_percent, it) }
                ?: getString(R.string.downloads_paused)
            State.WAITING -> getString(R.string.downloads_waiting)
            State.FAILED -> listOfNotNull(getString(R.string.downloads_failed), site).joinToString(SEPARATOR)
        }
    }

    private fun iconOf(kind: Kind): Int = when (kind) {
        Kind.IMAGE -> R.drawable.icon_image
        Kind.VIDEO -> R.drawable.icon_movie
        Kind.AUDIO -> R.drawable.icon_audio
        Kind.DOCUMENT -> R.drawable.icon_document
        Kind.OTHER -> R.drawable.icon_download
    }

    /** "12 MB downloaded · 20 GB free", like the storage line of Chrome's downloads. */
    override fun infoText(): CharSequence? {
        if (downloads.isEmpty()) return null
        val used = downloads.filter { it.state == State.DONE && it.totalBytes > 0 }.sumOf { it.totalBytes }
        val free = try {
            StatFs(Environment.getDataDirectory().path).availableBytes
        } catch (e: Exception) {
            return null
        }
        return getString(
            R.string.downloads_storage,
            Formatter.formatShortFileSize(this, used),
            Formatter.formatShortFileSize(this, free),
        )
    }

    /** While something is loading, the list follows its progress. */
    private fun scheduleRefresh() {
        handler.removeCallbacks(refresh)
        if (downloads.any { it.state == State.RUNNING || it.state == State.WAITING }) {
            handler.postDelayed(refresh, REFRESH_MS)
        }
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(refresh)
    }

    override fun onResume() {
        super.onResume()
        // Returning from Android's install-permission screen completes the APK tap.
        ApkInstaller.installPending(this)
    }

    override fun onItemClick(item: Row.Item) = open(item.payload as DownloadItem)

    override fun onItemAction(item: Row.Item, anchor: View) {
        val download = item.payload as DownloadItem
        val popup = MainMenuPopup(anchor) { itemId ->
            when (itemId) {
                MENU_OPEN -> open(download)
                MENU_SHARE -> share(download)
                MENU_COPY -> copyToClipboard(download.sourceUrl.orEmpty())
                MENU_PAUSE -> {
                    Downloads.task(this@DownloadsActivity, download)?.let { DownloadEngine.pause(this@DownloadsActivity, it.id) }
                    reload()
                }
                MENU_RESUME -> {
                    Downloads.task(this@DownloadsActivity, download)?.let { DownloadEngine.resume(this@DownloadsActivity, it.id) }
                    reload()
                }
                MENU_INSTALL -> install(download)
                MENU_DELETE -> confirmDelete(download)
            }
        }
        if (download.state == State.DONE) {
            popup.menu.add(0, MENU_OPEN, 0, R.string.downloads_open)
            // Built-in downloads in the app's folder carry a file path rather than a URI;
            // resolve that path through the FileProvider just as the Open action does.
            if (Downloads.uriFor(this@DownloadsActivity, download) != null) {
                popup.menu.add(0, MENU_SHARE, 1, R.string.downloads_share)
            }
        }
        // 18.3.0 beta (item 6): a download that is on its way can be paused and carried on.
        if (download.engine && download.state == State.RUNNING) {
            popup.menu.add(0, MENU_PAUSE, 5, R.string.download_action_pause)
        }
        if (download.engine && download.state == State.PAUSED) {
            popup.menu.add(0, MENU_RESUME, 5, R.string.download_action_resume)
        }
        // 18.3.0 beta (item 8): an app package is installed from here.
        if (download.state == State.DONE && download.isPackage) {
            popup.menu.add(0, MENU_INSTALL, 0, R.string.download_install)
        }
        if (download.sourceUrl != null) popup.menu.add(0, MENU_COPY, 2, R.string.library_copy_link)
        popup.menu.add(0, MENU_DELETE, 3, if (download.state == State.DONE || download.state == State.FAILED) R.string.menu_delete else R.string.downloads_cancel)
        popup.show()
    }

    /** 18.3.0 beta (item 8): hands a downloaded package to the installer of Android. */
    private fun install(download: DownloadItem) {
        Downloads.task(this, download)?.let { task ->
            ApkInstaller.install(this, task)
            return
        }
        val uri = Downloads.uriFor(this, download) ?: return
        ApkInstaller.open(this, uri, ApkInstaller.MIME_APK, download.name)
    }

    private fun open(download: DownloadItem) {
        when (download.state) {
            State.RUNNING, State.WAITING, State.PAUSED -> CatToast.show(this, R.string.downloads_not_finished)
            State.FAILED -> CatToast.show(this, R.string.downloads_failed)
            State.DONE -> {
                if (download.isPackage) {
                    install(download)
                    return
                }
                val uri = Downloads.uriFor(this, download)
                if (uri == null) {
                    // No handle on the file itself (Android 9 and older): the system's downloads app has it.
                    openSystemDownloads()
                    return
                }
                val view = Intent(Intent.ACTION_VIEW)
                    .setDataAndType(uri, download.mimeType ?: contentResolver.getType(uri) ?: "*/*")
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                try {
                    startActivity(view)
                } catch (e: ActivityNotFoundException) {
                    CatToast.show(this, R.string.downloads_no_app)
                } catch (e: SecurityException) {
                    openSystemDownloads()
                }
            }
        }
    }

    private fun openSystemDownloads() {
        try {
            startActivity(Intent(DownloadManager.ACTION_VIEW_DOWNLOADS))
        } catch (e: ActivityNotFoundException) {
            CatToast.show(this, R.string.downloads_no_app)
        }
    }

    private fun share(download: DownloadItem) {
        val uri = Downloads.uriFor(this, download) ?: return
        val send = Intent(Intent.ACTION_SEND)
            .setType(download.mimeType ?: "*/*")
            .putExtra(Intent.EXTRA_STREAM, uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        startActivity(Intent.createChooser(send, null))
    }

    private fun confirmDelete(download: DownloadItem) {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.downloads_delete_title)
            .setMessage(getString(R.string.downloads_delete_message, download.name))
            .setNegativeButton(R.string.app_cancel, null)
            .setPositiveButton(R.string.menu_delete) { _, _ -> delete(download) }
            .show()
    }

    /** Removes the download and its file from the device. */
    fun delete(download: DownloadItem) {
        if (!Downloads.delete(this, download)) CatToast.show(this, R.string.downloads_delete_failed)
        reload()
    }

    companion object {
        /** Id of the "All" chip. */
        const val FILTER_ALL = -2L

        private const val SEPARATOR = " • "
        private const val REFRESH_MS = 1000L
        private const val MENU_OPEN = 1
        private const val MENU_SHARE = 2
        private const val MENU_COPY = 3
        private const val MENU_DELETE = 4
        private const val MENU_PAUSE = 5
        private const val MENU_RESUME = 6
        private const val MENU_INSTALL = 7
    }
}

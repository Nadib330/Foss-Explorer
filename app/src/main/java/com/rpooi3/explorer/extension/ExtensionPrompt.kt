/*
 * Explorer: what an extension may do, before it is added.
 * Own code, GPL-3.0.
 *
 * Chrome does not add an extension the moment its button is pressed: it reads the manifest, and it
 * puts what the extension asked for in front of the person adding it — the name in quotes, "It
 * can:", and a line for each permission — with Cancel and Add extension under it. Explorer asked
 * nothing and installed at once (17.3.2 to 17.3.4). Since 17.4.0 it asks the same question, with
 * the words of the permissions after Chromium's install prompt, as Cromite carries them (the
 * `extension_permission_*` strings of Chromium, GPL-3.0, https://github.com/uazo/cromite):
 *
 *   Read and change all your data on all websites
 *   Replace the page you see when opening a new tab
 *   Display notifications
 *   …
 *
 * The install is in two steps for that reason: [ExtensionStore.stage] unpacks the .crx and reads
 * its manifest without adding anything, and [ExtensionStore.accept] moves the unpacked files into
 * the extension's own folder once the question has been answered.
 */
package com.rpooi3.explorer.extension

import android.app.Activity
import android.content.Context
import android.util.Log
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.rpooi3.explorer.R
import com.rpooi3.explorer.view.CatToast
import java.io.File
import java.util.concurrent.Executors

object ExtensionPrompt {

    private const val TAG = "ExplorerExtensions"

    private val worker = Executors.newSingleThreadExecutor()

    /** The permissions that say nothing a person needs to read, as in Chrome's prompt. */
    private val SILENT = setOf(
        "storage", "unlimitedStorage", "unlimited_storage", "contextMenus", "activeTab",
        "scripting", "alarms", "idle", "power", "background", "offscreen", "webNavigation",
        "declarativeContent", "fontSettings", "tts", "ttsEngine", "search", "menus",
    )

    /**
     * What [extension] asked for, in the words of Chromium's install prompt; empty for an
     * extension that asks for nothing worth a warning.
     */
    @JvmStatic
    fun warnings(context: Context, extension: WebExtension): List<String> {
        val out = LinkedHashSet<String>()
        val hosts = extension.hostPermissions
        val allSites = hosts.any {
            it == "<all_urls>" || it == "*://*/*" || it == "http://*/*" || it == "https://*/*" ||
                it == "http://*/" || it == "https://*/"
        }
        if (allSites) {
            out.add(context.getString(R.string.ext_warn_all_sites))
        } else if (hosts.isNotEmpty()) {
            out.add(context.getString(R.string.ext_warn_sites, hosts.take(3).joinToString(", ")))
        }
        if (extension.newTabPath != null) out.add(context.getString(R.string.ext_warn_newtab))
        for (permission in extension.permissions) {
            // A permission that says nothing worth reading (storage, alarms, …) warns nothing.
            if (permission in SILENT) continue
            val line = when (permission) {
                // As in Chromium: `tabs` is the list of open tabs, `history` the whole history.
                "tabs", "topSites", "sessions" -> R.string.ext_warn_history
                "history" -> R.string.ext_warn_history_devices
                "bookmarks" -> R.string.ext_warn_bookmarks
                "downloads" -> R.string.ext_warn_downloads
                "notifications" -> R.string.ext_warn_notifications
                "cookies" -> R.string.ext_warn_cookies
                "clipboardRead" -> R.string.ext_warn_clipboard_read
                "clipboardWrite" -> R.string.ext_warn_clipboard_write
                "management" -> R.string.ext_warn_management
                "tabCapture", "desktopCapture" -> R.string.ext_warn_capture
                "identity" -> R.string.ext_warn_identity
                "geolocation" -> R.string.ext_warn_location
                "proxy" -> R.string.ext_warn_proxy
                "browsingData" -> R.string.ext_warn_browsing_data
                "webRequest", "webRequestBlocking" -> R.string.ext_warn_network
                "declarativeNetRequest", "declarativeNetRequestWithHostAccess" -> R.string.ext_warn_blocking
                "audioCapture", "videoCapture" -> R.string.ext_warn_camera
                else -> 0
            }
            if (line != 0) out.add(context.getString(line))
        }
        return out.toList()
    }

    /**
     * The body of the question: "It can:" and a line for each permission, as the sheet of Chrome
     * has it — no description of the extension, no bullets, one permission per line.
     */
    @JvmStatic
    fun message(context: Context, extension: WebExtension): String {
        val lines = ArrayList<String>()
        lines.add(context.getString(R.string.extensions_prompt_can))
        val warnings = warnings(context, extension)
        if (warnings.isEmpty()) {
            lines.add(context.getString(R.string.extensions_prompt_nothing))
        } else {
            lines.addAll(warnings)
        }
        return lines.joinToString("\n\n")
    }

    /**
     * The question about an extension that is already unpacked in a staging folder: yes moves it
     * into its place, no throws it away.
     */
    @JvmStatic
    fun ask(
        activity: Activity,
        staged: WebExtension,
        onAnswer: ((WebExtension?) -> Unit)? = null,
    ) {
        if (activity.isFinishing || activity.isDestroyed) {
            onAnswer?.invoke(null)
            return
        }
        // 18.0.0 beta 8 (item 7): the question is there, so the waiting is over.
        InstallWait.dismiss()
        MaterialAlertDialogBuilder(activity)
            // The extension's own icon over the question, as in the sheet of Chrome.
            .setIcon(R.drawable.icon_extension)
            .setTitle(activity.getString(R.string.extensions_prompt_title, staged.name))
            .setMessage(message(activity, staged))
            .setNegativeButton(R.string.app_cancel) { _, _ ->
                ExtensionStore.discard(activity.applicationContext, staged.id)
                onAnswer?.invoke(null)
            }
            .setPositiveButton(R.string.extensions_prompt_add) { _, _ ->
                val application = activity.applicationContext
                worker.execute {
                    val installed = ExtensionStore.accept(application, staged)
                    main {
                        if (installed == null) {
                            CatToast.show(
                                activity,
                                activity.getString(R.string.extensions_install_failed, staged.name),
                                3,
                            )
                        } else {
                            CatToast.show(
                                activity,
                                activity.getString(R.string.extensions_installed, installed.name),
                                2,
                            )
                        }
                        onAnswer?.invoke(installed)
                    }
                }
            }
            .show()
    }

    /**
     * Fetches [extensionId] from the Chrome Web Store, reads what it asks for, and adds it only
     * after the question has been answered.
     *
     * @param onInstalled the extension, or null if it was not added; called on the main thread
     */
    @JvmStatic
    fun request(activity: Activity, extensionId: String, onInstalled: ((WebExtension?) -> Unit)? = null) {
        val id = CrxDownloader.extractExtensionId(extensionId)
        if (id == null) {
            onInstalled?.invoke(null)
            return
        }
        val already = ExtensionStore.find(activity, id)
        if (already != null) {
            InstallWait.dismiss()
            CatToast.show(activity, activity.getString(R.string.extensions_installed, already.name), 2)
            onInstalled?.invoke(already)
            return
        }
        CatToast.show(activity, activity.getString(R.string.extensions_installing, id), 1)
        // 18.0.0 beta 8 (item 7): the download takes a moment; it is shown, and it goes away by
        // itself when the question of the permissions opens (see [ask]).
        InstallWait.show(activity)
        val application = activity.applicationContext
        worker.execute {
            var staged: WebExtension? = null
            var reason: String? = null
            try {
                val crx = File(application.cacheDir, "$id.crx")
                try {
                    CrxDownloader.downloadCrx(id, crx)
                    staged = ExtensionStore.stage(application, crx, id)
                    if (staged == null) reason = application.getString(R.string.extensions_unreadable)
                } finally {
                    crx.delete()
                }
            } catch (e: Throwable) {
                Log.w(TAG, "Cannot fetch " + id + ": " + e)
                reason = e.message
            }
            val result = staged
            val why = reason
            main {
                InstallWait.dismiss()
                if (activity.isFinishing || activity.isDestroyed) return@main
                if (result == null) {
                    CatToast.show(
                        activity,
                        why ?: activity.getString(R.string.extensions_install_failed, id),
                        3,
                    )
                    onInstalled?.invoke(null)
                    return@main
                }
                ask(activity, result, onInstalled)
            }
        }
    }

    private fun main(action: () -> Unit) {
        android.os.Handler(android.os.Looper.getMainLooper()).post {
            try {
                action()
            } catch (e: Throwable) {
                Log.w(TAG, "After the question: " + e)
            }
        }
    }
}

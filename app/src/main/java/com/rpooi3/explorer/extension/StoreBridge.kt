/*
 * Explorer: the object a page of the Chrome Web Store finds as `ExplorerStoreBridge`.
 * Own code, GPL-3.0.
 *
 * 18.2.0 beta (item 4): the store's frontend asks the browser to install an extension through
 * `chrome.webstorePrivate.beginInstallWithManifest3`. The shim of that call (see [StoreHook])
 * forwards the id and the manifest here, and here they go to the installer of the browser — the
 * same one the three-dot menu uses, so the download, the permissions the extension asks for and
 * the unpacking are the ones of every other install.
 */
package com.rpooi3.explorer.extension

import android.content.Context
import android.util.Log
import android.webkit.JavascriptInterface

class StoreBridge(private val context: Context) {

    /**
     * `beginInstallWithManifest3` of the store: the id of the extension and the manifest it was
     * drawn from. The manifest is not needed — [CrxDownloader] fetches the `.crx` and reads the
     * real one from it — so only the id is used.
     */
    @JavascriptInterface
    fun beginInstall(extensionId: String?, manifest: String?) {
        val id = CrxDownloader.extractExtensionId(extensionId)
        if (id == null) {
            Log.w(TAG, "The store asked for something that is not an extension: " + extensionId)
            return
        }
        StoreHook.install(context, id)
    }

    private companion object {
        const val TAG = "ExplorerExtensions"
    }
}

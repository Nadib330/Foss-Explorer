package com.rpooi3.explorer.browser

import android.app.Dialog
import android.net.Uri
import android.view.View
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.rpooi3.explorer.database.Record
import com.rpooi3.explorer.view.AdapterRecord

/**
 * Callbacks from the web view layer into the hosting activity.
 *
 * Every reference parameter is nullable on purpose: the Java callers pass
 * `null` for several of them, and nullable types keep Kotlin from adding
 * runtime null checks that the Java version never had.
 */
interface BrowserController {
    fun updateProgress(progress: Int)

    /** The current tab may have entered or left the home page; show or hide it. */
    fun updateHomePage()
    fun showAlbum(albumController: AlbumController?)
    fun removeAlbum(albumController: AlbumController?)
    fun showFileChooser(filePathCallback: ValueCallback<Array<Uri>>?)
    fun onShowCustomView(view: View?, callback: WebChromeClient.CustomViewCallback?)
    fun hideOverview()
    fun shareLink(title: String?, url: String?)
    fun hideOverflow()
    fun hideSearch()
    fun reloadWebview()
    fun onHideCustomView()
    fun showDialogFastToggle(title: String?, url: String?, floatingActionButton: FloatingActionButton?)
    fun setProfileIcon(floatingActionButton: FloatingActionButton?, url: String?)
    fun showOverflow(
        dialog: Dialog?,
        view: View?,
        hideMenu: Int,
        title: String?,
        url: String?,
        adapterRecord: AdapterRecord?,
        recordList: MutableList<Record>?,
        location: Int,
    )
}

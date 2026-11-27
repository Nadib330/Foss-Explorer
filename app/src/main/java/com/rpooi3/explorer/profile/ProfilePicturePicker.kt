/*
 * Explorer: choosing a picture for a profile, from wherever the profile is edited.
 * Own code, GPL-3.0.
 *
 * A profile is named in a dialog, and that dialog can be opened from two places: the screen of the
 * profiles, and the sheet of the profiles in the browser (the sheet a profile is created from on the
 * first start). Both screens can open the picker of the system, so both hand the answer of the
 * picker to [handle] in their `onActivityResult`, and the dialog that asked is told what came back.
 *
 * Nothing of the picture leaves the device: it is squared, shrunk and written into the folder of the
 * profile that asked for it ([ProfilePictures]).
 */
package com.rpooi3.explorer.profile

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import com.rpooi3.explorer.view.CatToast

object ProfilePicturePicker {

    /** The request code both screens pass back to [handle]. */
    const val REQUEST = 4310

    private const val TAG = "ExplorerProfiles"

    /** What to do with the picture that comes back. One picker at a time. */
    private var pending: ((Uri?) -> Unit)? = null

    /**
     * Opens the system's picker for a picture.
     *
     * @param onPicked called with the picture, or with null when nothing was chosen
     */
    @JvmStatic
    fun pick(activity: Activity, onPicked: (Uri?) -> Unit) {
        pending = onPicked
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "image/*"
            putExtra(
                Intent.EXTRA_MIME_TYPES,
                arrayOf("image/png", "image/jpeg", "image/webp", "image/gif"),
            )
        }
        try {
            activity.startActivityForResult(intent, REQUEST)
        } catch (e: ActivityNotFoundException) {
            Log.i(TAG, "No picker for pictures: " + e)
            pending = null
            CatToast.show(activity, com.rpooi3.explorer.R.string.profiles_picture_failed, 1)
        }
    }

    /**
     * Passes the answer of the picker on, from the `onActivityResult` of a screen.
     *
     * @return whether the answer was one of this picker
     */
    @JvmStatic
    fun handle(requestCode: Int, resultCode: Int, data: Intent?): Boolean {
        if (requestCode != REQUEST) return false
        val callback = pending
        pending = null
        val uri = if (resultCode == Activity.RESULT_OK) data?.data else null
        callback?.invoke(uri)
        return true
    }

    /** A square preview of [uri], for the dialog that is naming a profile; null if unreadable. */
    @JvmStatic
    fun preview(context: Context, uri: Uri?, sideDp: Int): Bitmap? =
        if (uri == null) null else ProfilePictures.squareBitmap(context, uri, sidePx(context, sideDp))

    private fun sidePx(context: Context, sideDp: Int): Int =
        (sideDp * context.resources.displayMetrics.density).toInt().coerceAtLeast(1)
}

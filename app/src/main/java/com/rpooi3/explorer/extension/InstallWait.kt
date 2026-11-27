/*
 * Explorer: the "Please wait" of an extension that is being added.
 * Own code, GPL-3.0.
 *
 * 18.0.0 beta 8 (item 7): the way from the store's button to the question of Chrome's install
 * prompt is a download and an unpacking, and on a phone that takes a second or two of nothing at
 * all. The waiting is shown: a small pop-up with a turning circle that says "Please wait", which
 * goes away by itself the moment the question of the permissions is there (see
 * [ExtensionPrompt.request]), or with the reason the extension could not be fetched.
 *
 * It is one dialog for the whole app: an install is one at a time (the work runs on a single
 * thread), and a second one must never stack a second pop-up over the first.
 */
package com.rpooi3.explorer.extension

import android.app.Activity
import android.app.Dialog
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import com.google.android.material.color.MaterialColors
import com.google.android.material.progressindicator.CircularProgressIndicator
import com.rpooi3.explorer.R

object InstallWait {

    private var dialog: Dialog? = null

    /** Whether the pop-up of the waiting is on the screen. */
    @JvmStatic
    val isShowing: Boolean get() = dialog?.isShowing == true

    /** The text of the pop-up, for the tests. */
    @JvmStatic
    fun currentText(): String? =
        (dialog?.findViewById<View>(R.id.install_wait_text) as? TextView)?.text?.toString()

    private fun dp(activity: Activity, value: Int): Int =
        (value * activity.resources.displayMetrics.density).toInt()

    /**
     * Puts the "Please wait" on the screen of [activity]. Doing it twice does nothing; a pop-up
     * that is already there stays, and says what [message] says.
     */
    @JvmStatic
    fun show(activity: Activity, message: String? = null) {
        if (activity.isFinishing || activity.isDestroyed) return
        val existing = dialog
        if (existing != null && existing.isShowing) {
            (existing.findViewById<View>(R.id.install_wait_text) as? TextView)?.text =
                message ?: activity.getString(R.string.extensions_wait_title)
            return
        }
        val content = LinearLayout(activity).apply {
            id = R.id.install_wait
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(activity, 24), dp(activity, 24), dp(activity, 24), dp(activity, 22))
        }
        val spinner = CircularProgressIndicator(activity).apply {
            isIndeterminate = true
            trackThickness = dp(activity, 4)
        }
        content.addView(
            spinner,
            LinearLayout.LayoutParams(dp(activity, 40), dp(activity, 40)).apply {
                gravity = Gravity.CENTER_HORIZONTAL
            },
        )
        val label = TextView(activity).apply {
            id = R.id.install_wait_text
            text = message ?: activity.getString(R.string.extensions_wait_title)
            gravity = Gravity.CENTER_HORIZONTAL
            setTextColor(MaterialColors.getColor(activity, android.R.attr.textColorPrimary, Color.BLACK))
            textSize = 16f
            setPadding(0, dp(activity, 16), 0, 0)
        }
        content.addView(
            label,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ),
        )
        val note = TextView(activity).apply {
            text = activity.getString(R.string.extensions_wait_note)
            gravity = Gravity.CENTER_HORIZONTAL
            setTextColor(MaterialColors.getColor(activity, com.google.android.material.R.attr.colorOnSurfaceVariant, Color.GRAY))
            textSize = 13f
            setPadding(0, dp(activity, 6), 0, 0)
        }
        content.addView(note)
        content.background = GradientDrawable().apply {
            cornerRadius = dp(activity, 24).toFloat()
            setColor(MaterialColors.getColor(activity, com.google.android.material.R.attr.colorSurfaceContainerHigh, Color.WHITE))
        }

        val sheet = Dialog(activity).apply {
            requestWindowFeature(android.view.Window.FEATURE_NO_TITLE)
            setContentView(content)
            setCancelable(false)
            window?.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(Color.TRANSPARENT))
            window?.setLayout(dp(activity, 280), ViewGroup.LayoutParams.WRAP_CONTENT)
        }
        dialog = sheet
        try {
            sheet.show()
        } catch (e: Throwable) {
            // A pop-up that cannot be shown must not stop the install.
            dialog = null
        }
    }

    /** Takes the "Please wait" away: the question of the permissions is there, or the install failed. */
    @JvmStatic
    fun dismiss() {
        val sheet = dialog
        dialog = null
        try {
            sheet?.dismiss()
        } catch (e: Throwable) {
            // Already gone.
        }
    }
}

/*
 * Explorer: the notice that Explorer has to be started again.
 * Own code, GPL-3.0.
 *
 * 18.0.0 beta 8 (item 6) put the notice on the browsing screen: the browser saw that the theme
 * had been changed and asked when the user came back to it. 18.1.0 beta (item 5) shows it where
 * the change was made — in Settings, in front of the setting that was just changed — so that it
 * is answered on the spot and does not come back later, over a page the user is reading.
 */
package com.rpooi3.explorer.preferences

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.rpooi3.explorer.R
import com.rpooi3.explorer.activity.BrowserActivity
import com.rpooi3.explorer.profile.ProfilePrefs

object RestartPrompt {

    /** The preference that said "the browser must be started again" before this version. */
    const val KEY = "restart_changed"

    /**
     * 18.3.0 beta (item 3): the notice that is owed but not yet shown.
     *
     * Changing the theme rebuilds the screen it was changed on — the new colors have to be set
     * before the screen is built — and a dialog of the old screen goes away with it. That is why
     * going from the system theme to light, or from dark to light, showed no notice at all while
     * the other directions did. The debt is written down here, and the screen that comes out of
     * the rebuild pays it.
     */
    const val KEY_PENDING = "restart_notice_pending"

    /** Whether a notice is owed. */
    @JvmStatic
    fun isPending(context: Context): Boolean =
        ProfilePrefs.settings(context).getBoolean(KEY_PENDING, false)

    /**
     * Asks for the notice after a change of the theme.
     *
     * The change may rebuild this screen at any moment; when it does, the rebuilt screen shows
     * the notice (see [showPending]). When it does not, the notice is shown here.
     */
    @JvmStatic
    fun request(activity: Activity?) {
        if (activity == null) return
        ProfilePrefs.settings(activity).edit().putBoolean(KEY_PENDING, true).apply()
        val decor = activity.window?.decorView ?: return
        decor.postDelayed({
            if (!activity.isFinishing && !activity.isDestroyed) showPending(activity)
        }, 250)
    }

    /** Shows the notice if one is owed; the screen that survived the rebuild calls this. */
    @JvmStatic
    fun showPending(activity: Activity?) {
        if (activity == null || !isPending(activity)) return
        show(activity)
    }

    /** Shows the notice, if there is a screen to show it on. */
    @JvmStatic
    fun show(activity: Activity?) {
        if (activity == null || activity.isFinishing) return
        ProfilePrefs.settings(activity).edit().putBoolean(KEY_PENDING, false).apply()
        try {
            MaterialAlertDialogBuilder(activity)
                .setTitle(R.string.restart_notice_title)
                .setMessage(R.string.restart_notice_message)
                .setCancelable(true)
                .setPositiveButton(R.string.restart_notice_ok) { _, _ -> restart(activity) }
                .setNegativeButton(R.string.app_cancel, null)
                .show()
        } catch (e: RuntimeException) {
            // A screen that is on its way out cannot show a notice; the theme is applied at the
            // next start all the same.
        }
    }

    /** Starts Explorer again, from the top, with the colors that were chosen. */
    @JvmStatic
    fun restart(activity: Activity) {
        val context: Context = activity.applicationContext
        ProfilePrefs.settings(context).edit()
            .putBoolean("restoreOnRestart", true)
            .putInt(KEY, 0)
            .apply()
        val intent = Intent.makeRestartActivityTask(ComponentName(context, BrowserActivity::class.java))
        context.startActivity(intent)
        Runtime.getRuntime().exit(0)
    }
}

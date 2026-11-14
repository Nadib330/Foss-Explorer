package com.rpooi3.explorer.activity

import android.app.Activity
import android.content.Intent
import android.view.ViewGroup
import com.rpooi3.explorer.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Starts every screen on the oldest supported Android version and on a recent
 * one. This catches what a successful compile does not: missing classes in
 * layouts or preference XML, theme attributes that do not resolve, and crashes
 * in onCreate/onResume.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [26, 34])
class LaunchSmokeTest {

    @Test
    fun browser_starts() {
        // Started like the launcher starts it. Without the action the app opens no first tab.
        val launch = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        Robolectric.buildActivity(BrowserActivity::class.java, launch).setup().use { controller ->
            val activity = controller.get()

            assertFalse(activity.isFinishing)
            val content = activity.findViewById<ViewGroup>(R.id.main_content)
            assertNotNull(content)
            assertEquals("one tab is open", 1, content.childCount)
        }
    }

    @Test
    fun settingsScreens_start() {
        val screens: List<Class<out Activity>> = listOf(
            Settings_Activity::class.java,
            Settings_Backup::class.java,
            Settings_Clear::class.java,
            Settings_Filter::class.java,
            Settings_Gesture::class.java,
            Settings_Profile::class.java,
            Settings_ProfileList::class.java,
        )
        for (screen in screens) {
            Robolectric.buildActivity(screen).setup().use { controller ->
                assertFalse(screen.simpleName, controller.get().isFinishing)
            }
        }
    }
}

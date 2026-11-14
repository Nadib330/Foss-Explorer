package com.rpooi3.explorer.theme

import android.app.Application
import android.content.Context
import android.util.TypedValue
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import androidx.preference.PreferenceManager
import androidx.test.core.app.ApplicationProvider
import com.rpooi3.explorer.R
import com.rpooi3.explorer.activity.Settings_Activity
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
class ExplorerThemeTest {

    private val context: Application = ApplicationProvider.getApplicationContext()
    private val prefs = PreferenceManager.getDefaultSharedPreferences(context)

    @After
    fun resetNightMode() {
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_UNSPECIFIED)
    }

    @Test
    @Config(sdk = [26])
    fun android8_hasNoDynamicColour_evenIfRequested() {
        prefs.edit().putBoolean("useDynamicColor", true).commit()

        assertFalse(ExplorerTheme.usesDynamicColor(context))
    }

    @Test
    @Config(sdk = [26])
    fun android8_usesBrandPalette() {
        Robolectric.buildActivity(Settings_Activity::class.java).setup().use { controller ->
            val activity = controller.get()

            assertEquals(
                ContextCompat.getColor(activity, R.color.md_theme_primary),
                activity.resolveColor(androidx.appcompat.R.attr.colorPrimary)
            )
        }
    }

    @Test
    @Config(sdk = [34])
    fun android14_usesDynamicColourByDefault() {
        assertTrue(ExplorerTheme.usesDynamicColor(context))
    }

    @Test
    @Config(sdk = [34])
    fun android14_brandPaletteWhenDynamicColourIsSwitchedOff() {
        prefs.edit().putBoolean("useDynamicColor", false).commit()

        assertFalse(ExplorerTheme.usesDynamicColor(context))
        Robolectric.buildActivity(Settings_Activity::class.java).setup().use { controller ->
            val activity = controller.get()

            assertEquals(
                ContextCompat.getColor(activity, R.color.md_theme_primary),
                activity.resolveColor(androidx.appcompat.R.attr.colorPrimary)
            )
        }
    }

    @Test
    @Config(sdk = [26])
    fun nightMode_followsTheThemeSetting() {
        val expected = mapOf(
            "1" to AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM,
            "2" to AppCompatDelegate.MODE_NIGHT_NO,
            "3" to AppCompatDelegate.MODE_NIGHT_YES,
        )
        for ((value, mode) in expected) {
            prefs.edit().putString("sp_theme", value).commit()

            ExplorerTheme.applyNightMode(context)

            assertEquals("sp_theme=$value", mode, AppCompatDelegate.getDefaultNightMode())
        }
    }

    private fun Context.resolveColor(attr: Int): Int {
        val value = TypedValue()
        assertTrue(theme.resolveAttribute(attr, value, true))
        return value.data
    }
}

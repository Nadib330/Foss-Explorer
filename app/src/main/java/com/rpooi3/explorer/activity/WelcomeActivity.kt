package com.rpooi3.explorer.activity

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.PreferenceManager
import com.google.android.material.button.MaterialButton
import com.rpooi3.explorer.R
import com.rpooi3.explorer.theme.ExplorerTheme

/**
 * 16.1.0, item 4: the welcome Firefox for Android shows on the first start, in the same
 * shape: a few pages that are stepped through with "Next", a "Skip" in the corner, dots that
 * tell where the user is, and a single "Start browsing" at the end. The dialog of FOSS
 * Browser that used to greet the user is gone (item 3).
 *
 * The pages ask for nothing and change no setting. The last one offers the system's screen
 * for the default browser, as Firefox does, and can be stepped over.
 */
class WelcomeActivity : AppCompatActivity() {

    private lateinit var pages: List<View>
    private lateinit var dots: List<View>
    private lateinit var buttonNext: MaterialButton
    private lateinit var buttonStart: MaterialButton
    private var index = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        ExplorerTheme.applyTo(this)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_welcome)

        pages = listOf(
            findViewById(R.id.welcome_page_1),
            findViewById(R.id.welcome_page_2),
            findViewById(R.id.welcome_page_3),
            findViewById(R.id.welcome_page_4),
        )
        buttonNext = findViewById(R.id.welcome_next)
        buttonStart = findViewById(R.id.welcome_start)
        dots = listOf(
            findViewById(R.id.welcome_dot_1),
            findViewById(R.id.welcome_dot_2),
            findViewById(R.id.welcome_dot_3),
            findViewById(R.id.welcome_dot_4),
        )

        findViewById<View>(R.id.welcome_skip).setOnClickListener { finishWelcome() }
        buttonNext.setOnClickListener { showPage(index + 1) }
        buttonStart.setOnClickListener { finishWelcome() }
        findViewById<View>(R.id.welcome_default_browser).setOnClickListener { openDefaultApps() }

        showPage(0, animate = false)
    }

    /** Brings page [position] forward with the short fade and slide of a page turn. */
    private fun showPage(position: Int, animate: Boolean = true) {
        val target = position.coerceIn(0, pages.lastIndex)
        val forward = target >= index
        pages.forEachIndexed { i, page ->
            val visible = i == target
            if (visible && animate) {
                page.alpha = 0f
                page.translationX = (if (forward) 1 else -1) * 32 * resources.displayMetrics.density
                page.visibility = View.VISIBLE
                page.animate().alpha(1f).translationX(0f).setDuration(180L).start()
            } else {
                page.animate().cancel()
                page.alpha = 1f
                page.translationX = 0f
                page.visibility = if (visible) View.VISIBLE else View.GONE
            }
        }
        index = target
        val last = index == pages.lastIndex
        buttonNext.visibility = if (last) View.GONE else View.VISIBLE
        buttonStart.visibility = if (last) View.VISIBLE else View.GONE
        dots.forEachIndexed { i, dot ->
            val active = i == index
            dot.alpha = if (active) 1f else 0.38f
            dot.scaleX = if (active) 1.25f else 1f
            dot.scaleY = if (active) 1.25f else 1f
        }
    }

    private fun openDefaultApps() {
        val intent = Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            startActivity(intent)
        } catch (e: android.content.ActivityNotFoundException) {
            // Some devices have no such screen; the welcome goes on without it.
            Log.i(TAG, "No default apps screen: $e")
        }
    }

    private fun finishWelcome() {
        PreferenceManager.getDefaultSharedPreferences(this)
            .edit().putBoolean(KEY_SEEN, true).apply()
        finish()
    }

    /** Leaving with the back button counts as having seen the welcome, as in Firefox. */
    @Deprecated("The welcome is shown once, as in Firefox")
    override fun onBackPressed() {
        finishWelcome()
        @Suppress("DEPRECATION")
        super.onBackPressed()
    }

    companion object {
        /** The flag of the first start, in the default preferences of the app. */
        const val KEY_SEEN = "welcome_seen"
        private const val TAG = "WelcomeActivity"
    }
}

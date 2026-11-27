package com.rpooi3.explorer.activity

import com.rpooi3.explorer.profile.ProfilePrefs
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.rpooi3.explorer.R
import com.rpooi3.explorer.profile.ProfilePicturePicker
import com.rpooi3.explorer.profile.ProfilePictures
import com.rpooi3.explorer.profile.ProfileSetupDialog
import com.rpooi3.explorer.profile.Profiles
import com.rpooi3.explorer.preferences.HomeStyleSelector
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
    private var homeStylePromptShown = false

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
        findViewById<View>(R.id.welcome_notifications).setOnClickListener { requestNotifications() }
        updateNotificationButton()

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

    override fun onResume() {
        super.onResume()
        updateNotificationButton()
    }

    /** The last welcome page asks for the permission used by the built-in download manager. */
    private fun requestNotifications() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            updateNotificationButton()
            return
        }
        if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), REQUEST_NOTIFICATIONS)
        }
    }

    private fun updateNotificationButton() {
        val button = findViewById<View>(R.id.welcome_notifications) ?: return
        button.visibility = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            View.VISIBLE
        } else {
            View.GONE
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_NOTIFICATIONS) updateNotificationButton()
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
        ProfilePrefs.settings(this)
            .edit().putBoolean(KEY_SEEN, true).apply()
        // 18.0.0: as in Chrome, the welcome ends with the first profile: a name and a colour.
        // Nothing is asked that would identify the user; Explorer has no sign-in.
        if (!Profiles.setupDone(this)) {
            offerProfile()
            return
        }
        offerHomeStyle()
    }

    /**
     * The one profile the browser starts with; skipping keeps its name.
     *
     * 18.0.0 beta 3: a picture can be chosen here as well — it is the same row the settings show
     * when a profile is changed — and it is kept together with the name.
     */
    private fun offerProfile() {
        val first = Profiles.all(this).firstOrNull() ?: return offerHomeStyle()
        var picture: android.graphics.Bitmap? = null
        var pictureTakenAway = false
        ProfileSetupDialog(
            this,
            first,
            { name, colour ->
                Profiles.rename(this, first.id, name)
                Profiles.setColour(this, first.id, colour)
                val chosen = picture
                when {
                    chosen != null -> ProfilePictures.save(this, first, chosen)
                    pictureTakenAway -> ProfilePictures.clear(this, first)
                }
                Profiles.setSetupDone(this)
                Profiles.cacheCurrent(this)
                offerHomeStyle()
            },
            {
                Profiles.setSetupDone(this)
                Profiles.cacheCurrent(this)
                offerHomeStyle()
            },
            { chosen ->
                picture = chosen
                pictureTakenAway = chosen == null
            },
        ).show()
    }

    /**
     * The last part of "Make it yours": choose which built-in home page opens in a new tab.
     * This is deliberately a prompt in the first-run flow; the same choice remains available in
     * Settings > Home page customization.
     */
    private fun offerHomeStyle() {
        if (homeStylePromptShown || isFinishing || isDestroyed) return
        homeStylePromptShown = true
        val chooser = layoutInflater.inflate(R.layout.preference_home_style, null)
        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle(R.string.welcome_home_style_title)
            .setView(chooser)
            .create()
        HomeStyleSelector.bind(chooser, this) { dialog.dismiss() }
        dialog.setOnCancelListener { finish() }
        dialog.setOnDismissListener { if (!isFinishing) finish() }
        dialog.show()
    }

    /** The picture of the first profile is chosen in the dialog, so the picker answers here. */
    @Deprecated("The picker of the system answers here")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        @Suppress("DEPRECATION")
        super.onActivityResult(requestCode, resultCode, data)
        ProfilePicturePicker.handle(requestCode, resultCode, data)
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
        private const val REQUEST_NOTIFICATIONS = 18_300
    }
}

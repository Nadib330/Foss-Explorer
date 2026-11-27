/*
 * Explorer: the screen that keeps the profiles.
 * Own code, GPL-3.0.
 *
 * The same list as the sheet of Chrome, without the sheet: every profile with its picture (or its
 * letter) in its disc and its name, the profile in front marked, and for each one the ways of
 * changing it — rename it, colour it, give it a picture, delete it.
 *
 * 18.0.0 beta 3: the picture is not a button of its own any more. A row is the disc and the name,
 * and the pencil changes everything a profile is, its picture included, in the one dialog that also
 * makes a profile (issue 5: the little gallery icon on every row is gone).
 *
 * Deleting is guarded the way Chrome guards it: the profile in front cannot be deleted, the last
 * profile cannot be deleted, and the dialog names what goes with it (history, bookmarks, cookies,
 * site permissions, downloads) before anything is removed.
 */
package com.rpooi3.explorer.profile

import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.color.MaterialColors
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.rpooi3.explorer.R
import com.rpooi3.explorer.theme.ExplorerTheme

/** The profiles of the browser, and what can be done with them. */
class ProfilesActivity : AppCompatActivity() {

    private lateinit var list: LinearLayout

    /**
     * The picture that was chosen in the dialog that is open, before there is a profile to keep it
     * in: a profile is made when the dialog is answered, and the picture is kept right after.
     */
    private var chosenPicture: android.graphics.Bitmap? = null

    /** Whether the open dialog was told to take the picture away. */
    private var clearPicture = false

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        ExplorerTheme.applyTo(this)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_profiles)
        setSupportActionBar(findViewById<Toolbar>(R.id.profiles_toolbar))
        supportActionBar?.setTitle(R.string.profiles_title)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        findViewById<Toolbar>(R.id.profiles_toolbar)
            .setNavigationOnClickListener { finish() }
        list = findViewById(R.id.profiles_list)
        findViewById<MaterialButton>(R.id.profiles_add).setOnClickListener { addProfile() }
        findViewById<CheckBox>(R.id.profiles_always_ask).apply {
            isChecked = Profiles.askOnStartup(this@ProfilesActivity)
            setOnCheckedChangeListener { _, checked ->
                Profiles.setAskOnStartup(this@ProfilesActivity, checked)
            }
        }
        fill()
    }

    override fun onResume() {
        super.onResume()
        fill()
    }

    private fun fill() {
        list.removeAllViews()
        val active = Profiles.current(this)
        for (profile in Profiles.all(this)) list.addView(row(profile, profile.id == active.id))
        if (Profiles.isGuest(this)) list.addView(note(getString(R.string.profiles_guest_running)))
        list.addView(
            note(
                if (ProfileWebView.supported()) getString(R.string.profiles_isolation_on)
                else getString(R.string.profiles_isolation_off),
            ),
        )
    }

    private fun row(profile: BrowserProfile, active: Boolean): View {
        val card = MaterialCardView(this).apply {
            layoutParams = ViewGroup.MarginLayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply { bottomMargin = dp(12) }
            radius = dp(24).toFloat()
            cardElevation = 0f
            setCardBackgroundColor(ProfileColour.soft(this@ProfilesActivity, profile))
        }
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(12), dp(8), dp(12))
        }

        // The disc, and nothing else: the picture is changed from the pencil.
        val avatar = ProfileAvatarView(this, profile, 52).apply {
            markActive(active)
            contentDescription = profile.name
        }
        row.addView(avatar)

        val texts = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), 0, dp(8), 0)
        }
        texts.addView(
            TextView(this).apply {
                text = profile.name
                setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 18f)
                setTextColor(
                    MaterialColors.getColor(
                        this@ProfilesActivity,
                        com.google.android.material.R.attr.colorOnSurface,
                        0,
                    ),
                )
            },
        )
        texts.addView(
            TextView(this).apply {
                text = when {
                    active -> getString(R.string.profiles_current)
                    else -> getString(
                        R.string.profiles_colour_of,
                        Profiles.COLOUR_NAMES[profile.colour.coerceIn(0, Profiles.COLOUR_NAMES.lastIndex)],
                    )
                }
                setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 13f)
                setTextColor(
                    MaterialColors.getColor(
                        this@ProfilesActivity,
                        com.google.android.material.R.attr.colorOnSurfaceVariant,
                        0,
                    ),
                )
            },
        )
        row.addView(texts, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))

        // The pencil changes the name, the colour and the picture — for every profile, the one in
        // front too, which is where a picture is changed from.
        row.addView(
            ImageButton(this).apply {
                setImageResource(R.drawable.icon_edit)
                background = null
                contentDescription = getString(R.string.profiles_edit_title)
                setColorFilter(
                    MaterialColors.getColor(
                        this@ProfilesActivity,
                        com.google.android.material.R.attr.colorOnSurfaceVariant,
                        0,
                    ),
                )
                setOnClickListener { edit(profile) }
            },
        )
        row.addView(
            ImageButton(this).apply {
                setImageResource(R.drawable.icon_delete)
                background = null
                contentDescription = getString(R.string.profiles_delete)
                isEnabled = Profiles.canDelete(this@ProfilesActivity, profile.id)
                alpha = if (isEnabled) 1f else 0.38f
                setColorFilter(
                    MaterialColors.getColor(
                        this@ProfilesActivity,
                        com.google.android.material.R.attr.colorOnSurfaceVariant,
                        0,
                    ),
                )
                setOnClickListener { confirmDelete(profile) }
            },
        )
        card.addView(row)
        // Tapping a profile that is not in front makes it the one in front.
        if (!active) card.setOnClickListener { switchTo(profile) }
        return card
    }

    private fun note(text: String): View = TextView(this).apply {
        this.text = text
        setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 13f)
        setPadding(dp(8), dp(4), dp(8), dp(12))
        setTextColor(
            MaterialColors.getColor(
                this@ProfilesActivity,
                com.google.android.material.R.attr.colorOnSurfaceVariant,
                0,
            ),
        )
    }

    private fun addProfile() {
        chosenPicture = null
        clearPicture = false
        ProfileSetupDialog(
            this,
            null,
            { name, colour ->
                val created = Profiles.create(this, name, colour)
                if (created == null) {
                    MaterialAlertDialogBuilder(this)
                        .setMessage(R.string.profiles_too_many)
                        .setPositiveButton(R.string.app_ok, null)
                        .show()
                } else {
                    // The picture was chosen before the profile existed; it is kept now.
                    val picture = chosenPicture
                    if (picture != null && !ProfilePictures.save(this, created, picture)) {
                        com.rpooi3.explorer.view.CatToast.show(
                            this,
                            R.string.profiles_picture_failed,
                            1,
                        )
                    }
                    chosenPicture = null
                    fill()
                }
            },
            {},
            { picture ->
                chosenPicture = picture
                clearPicture = picture == null
            },
        ).show()
    }

    private fun edit(profile: BrowserProfile) {
        chosenPicture = null
        clearPicture = false
        ProfileSetupDialog(
            this,
            profile,
            { name, colour ->
                Profiles.rename(this, profile.id, name)
                Profiles.setColour(this, profile.id, colour)
                // The picture, if one was chosen or taken away in the same dialog.
                val picture = chosenPicture
                val failed = when {
                    picture != null -> !ProfilePictures.save(this, profile, picture)
                    clearPicture -> {
                        ProfilePictures.clear(this, profile)
                        false
                    }
                    else -> false
                }
                if (failed) {
                    com.rpooi3.explorer.view.CatToast.show(
                        this,
                        R.string.profiles_picture_failed,
                        1,
                    )
                }
                chosenPicture = null
                fill()
            },
            {},
            { picture ->
                chosenPicture = picture
                clearPicture = picture == null
            },
        ).show()
    }

    // The picture ------------------------------------------------------------------------------

    /**
     * The picture of a profile is chosen from the dialog that names it (issue 5/6), so this screen
     * only has to pass the answer of the picker on to [ProfilePicturePicker].
     */
    @Deprecated("The picker of the system answers here")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        @Suppress("DEPRECATION")
        super.onActivityResult(requestCode, resultCode, data)
        if (ProfilePicturePicker.handle(requestCode, resultCode, data)) fill()
    }

    // Deleting and switching --------------------------------------------------------------------

    private fun confirmDelete(profile: BrowserProfile) {
        if (!Profiles.canDelete(this, profile.id)) return
        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.profiles_delete_title, profile.name))
            .setMessage(R.string.profiles_delete_message)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.profiles_delete) { _, _ ->
                // The WebView profile is taken away with the rest, once nothing loads in it.
                ProfileWebView.delete(profile)
                com.rpooi3.explorer.database.places.PlacesDatabase.deleteFor(this, profile.id)
                Profiles.delete(this, profile.id)
                fill()
            }
            .show()
    }

    private fun switchTo(profile: BrowserProfile) {
        Profiles.releaseDatabases()
        Profiles.switchTo(this, profile.id)
        Profiles.cacheCurrent(this)
        // The switch answers the question of the start, so the browser does not ask it again.
        Profiles.setSkipsAskOnce(this, profile.name)
        val intent = Intent(this, com.rpooi3.explorer.activity.BrowserActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }

}

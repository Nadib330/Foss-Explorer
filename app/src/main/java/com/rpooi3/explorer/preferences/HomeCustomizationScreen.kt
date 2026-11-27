/*
 * Foss Explorer: Settings > Home page customization.
 * Own code, GPL-3.0.
 */
package com.rpooi3.explorer.preferences

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.net.Uri
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.content.edit
import androidx.appcompat.content.res.AppCompatResources
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceViewHolder
import androidx.preference.TwoStatePreference
import com.google.android.material.color.MaterialColors
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.rpooi3.explorer.R
import com.rpooi3.explorer.home.HomeAppearance
import com.rpooi3.explorer.home.HomePage
import com.rpooi3.explorer.view.CatToast
import java.util.concurrent.Executors

/** The rows of "Home page customization": what they show and what they do. */
object HomeCustomizationScreen {

    const val KEY_PICK = "home_wallpaper_pick"
    const val KEY_REMOVE = "home_wallpaper_remove"
    const val KEY_COLOR = "home_color"

    // 18.0.0 beta 7 (item 5): the user's own home page.
    const val KEY_CODE_EDIT = "home_code_edit"
    const val KEY_CODE_CLEAR = "home_code_clear"

    /**
     * Gives the rows of [fragment] their state and behaviour; does nothing on
     * other screens. [pickImage] opens Android's picker for a picture; its
     * answer goes to [onImagePicked].
     */
    @JvmStatic
    fun bind(fragment: PreferenceFragmentCompat, pickImage: Runnable) {
        val pick = fragment.findPreference<Preference>(KEY_PICK) ?: return
        val context = fragment.requireContext()
        pick.setOnPreferenceClickListener {
            pickImage.run()
            true
        }
        fragment.findPreference<Preference>(KEY_REMOVE)?.setOnPreferenceClickListener {
            HomeAppearance.clearWallpaper(context)
            refresh(fragment)
            true
        }
        fragment.findPreference<Preference>(KEY_COLOR)?.setOnPreferenceClickListener {
            showColors(fragment)
            true
        }
        // 18.0.0 beta 7 (item 5): write the page, or throw it away.
        fragment.findPreference<Preference>(KEY_CODE_EDIT)?.setOnPreferenceClickListener {
            HomeCodeEditor.show(context) { refresh(fragment) }
            true
        }
        fragment.findPreference<Preference>(KEY_CODE_CLEAR)?.setOnPreferenceClickListener {
            HomeAppearance.prefs(context).edit()
                .remove(HomeAppearance.KEY_CODE)
                .putBoolean(HomeAppearance.KEY_CODE_ON, false)
                .apply()
            CatToast.show(context, R.string.home_code_cleared, 1)
            refresh(fragment)
            true
        }
        fragment.findPreference<Preference>(HomeAppearance.KEY_CODE_ON)?.setOnPreferenceChangeListener { _, value ->
            // The switch cannot be turned on while there is no code to show.
            if (value == true && !HomeAppearance.hasCode(context)) {
                HomeCodeEditor.show(context) { refresh(fragment) }
                false
            } else {
                true
            }
        }
        refresh(fragment)
    }

    /** Brings the rows up to date with what is stored. */
    @JvmStatic
    fun refresh(fragment: PreferenceFragmentCompat) {
        val context = fragment.context ?: return
        // 18.3.0 beta (item 4): every switch shows what is stored, not what it showed last time.
        // A switch that was turned off used to come back on when the screen was opened again, and
        // the first tap then wrote "off" a second time instead of turning it on.
        for (key in SWITCHES) {
            val row = fragment.findPreference<Preference>(key) as? TwoStatePreference ?: continue
            row.isChecked = HomeAppearance.prefs(context).getBoolean(key, DEFAULT_ON.contains(key))
        }
        // The style row is a custom two-card selector, not a normal ListPreference. Rebind it
        // whenever Settings returns from another screen so its selected outline is never stale.
        (fragment.findPreference<Preference>(HomePage.KEY_STYLE) as? HomeStylePreference)
            ?.refreshSelection()
        val has = HomeAppearance.hasWallpaper(context)
        // 18.0.0 beta 6 (item 8): the new rows say what they are set to. The value rows of
        // AndroidX carry a summary provider of their own, so it is cleared before the text is set.
        fragment.findPreference<Preference>(HomeAppearance.KEY_COLUMNS)?.let { row ->
            row.setSummaryProvider(null)
            row.summary = context.getString(R.string.home_columns_set, HomeAppearance.shortcutColumns(context))
        }
        fragment.findPreference<Preference>(HomeAppearance.KEY_TITLE)?.let { row ->
            row.setSummaryProvider(null)
            val text = HomeAppearance.prefs(context).getString(HomeAppearance.KEY_TITLE, null)?.trim()
            row.summary = if (text.isNullOrEmpty()) context.getString(R.string.app_name) else text
        }
        fragment.findPreference<Preference>(KEY_PICK)?.setSummary(
            if (has) R.string.home_wallpaper_set else R.string.home_wallpaper_none,
        )
        fragment.findPreference<Preference>(KEY_REMOVE)?.isEnabled = has
        // 18.0.0 beta 7 (item 5): what stands ready for the user's own page.
        val hasCode = HomeAppearance.hasCode(context)
        fragment.findPreference<Preference>(HomeAppearance.KEY_CODE_ON)?.isEnabled = true
        fragment.findPreference<Preference>(KEY_CODE_EDIT)?.summary = if (hasCode) {
            context.getString(R.string.home_code_edit_summary)
        } else {
            context.getString(R.string.home_code_summary)
        }
        fragment.findPreference<Preference>(KEY_CODE_CLEAR)?.isEnabled = hasCode
        fragment.findPreference<Preference>(HomeAppearance.KEY_DIM)?.isEnabled = has
        fragment.findPreference<Preference>(KEY_COLOR)?.let { row ->
            // A wallpaper covers the colour; the row says so instead of looking as if it had no effect.
            row.summary = if (has) context.getString(R.string.home_color_covered, HomeAppearance.colorName(context))
            else HomeAppearance.colorName(context)
            // (The new text makes the list draw the row again, and with it the sample at its end.)
        }
    }

    /** Paints the sample of the chosen colour into the row "Background colour" when the list draws it. */
    @JvmStatic
    fun bindColorDot(pref: Preference, holder: PreferenceViewHolder) {
        if (pref.key != KEY_COLOR) return
        val dot = holder.findViewById(R.id.color_dot) ?: return
        val context = dot.context
        val color = HomeAppearance.color(HomeAppearance.prefs(context))
            ?: MaterialColors.getColor(context, com.google.android.material.R.attr.colorSurface, Color.WHITE)
        dot.background = dotDrawable(context, color)
    }

    private fun dotDrawable(context: Context, color: Int): LayerDrawable {
        val layers = AppCompatResources.getDrawable(context, R.drawable.bg_color_dot)!!.mutate() as LayerDrawable
        (layers.findDrawableByLayerId(R.id.color_dot_fill) as GradientDrawable).setColor(color)
        return layers
    }

    /** The picture the user picked becomes the wallpaper; null means nothing was picked. */
    @JvmStatic
    fun onImagePicked(fragment: PreferenceFragmentCompat, uri: Uri?) {
        if (uri == null) return
        val context = fragment.context?.applicationContext ?: return
        // Reading and scaling a photo takes a moment: off the main thread.
        executor.execute {
            val done = HomeAppearance.setWallpaper(context, uri)
            fragment.view?.post {
                if (!fragment.isAdded) return@post
                if (!done) CatToast.show(context, R.string.home_wallpaper_failed)
                refresh(fragment)
            }
        }
    }

    /** The colours to choose from, as round samples with their names. */
    @JvmStatic
    fun showColors(fragment: PreferenceFragmentCompat): AlertDialog {
        val context = fragment.requireContext()
        val prefs = HomeAppearance.prefs(context)
        val current = HomeAppearance.color(prefs)
        val density = context.resources.displayMetrics.density
        val grid = GridLayout(context).apply {
            columnCount = COLUMNS
            val pad = (16 * density).toInt()
            setPadding(pad, pad, pad, 0)
        }
        val dialog = MaterialAlertDialogBuilder(context)
            .setTitle(R.string.home_color)
            .setView(grid)
            .setNegativeButton(R.string.app_cancel, null)
            .create()
        val surface = MaterialColors.getColor(context, com.google.android.material.R.attr.colorSurface, Color.WHITE)
        for (swatch in HomeAppearance.PALETTE) {
            val shown = swatch.color ?: surface
            val cell = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL
                tag = swatch
                contentDescription = context.getString(swatch.name)
                isClickable = true
                isFocusable = true
                val pad = (6 * density).toInt()
                setPadding(pad, pad, pad, pad)
                setOnClickListener {
                    HomeAppearance.setColor(prefs, swatch.color)
                    dialog.dismiss()
                    refresh(fragment)
                }
            }
            val size = (44 * density).toInt()
            val dot = FrameLayout(context).apply { background = dotDrawable(context, shown) }
            if (swatch.color == current) {
                // The chosen colour carries a tick, light or dark as the colour needs it.
                dot.addView(
                    ImageView(context).apply {
                        setImageResource(R.drawable.icon_check)
                        imageTintList = android.content.res.ColorStateList.valueOf(
                            if (HomeAppearance.needsLightText(shown)) Color.WHITE else Color.BLACK,
                        )
                    },
                    FrameLayout.LayoutParams((24 * density).toInt(), (24 * density).toInt(), Gravity.CENTER),
                )
            }
            cell.addView(dot, LinearLayout.LayoutParams(size, size))
            cell.addView(
                TextView(context).apply {
                    setText(swatch.name)
                    gravity = Gravity.CENTER
                    maxLines = 1
                    textSize = 12f
                    setTextColor(MaterialColors.getColor(context, com.google.android.material.R.attr.colorOnSurfaceVariant, Color.GRAY))
                },
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    topMargin = (4 * density).toInt()
                },
            )
            grid.addView(
                cell,
                GridLayout.LayoutParams(
                    GridLayout.spec(GridLayout.UNDEFINED),
                    GridLayout.spec(GridLayout.UNDEFINED, 1f),
                ).apply { width = 0 },
            )
        }
        dialog.show()
        return dialog
    }

    /** The switches of the screen, and the ones that start switched off. */
    private val SWITCHES = listOf(
        HomeAppearance.KEY_SHOW_LOGO,
        HomeAppearance.KEY_SHOW_SEARCH,
        HomeAppearance.KEY_SHOW_SHORTCUTS,
        HomeAppearance.KEY_DIM,
        HomeAppearance.KEY_CODE_ON,
    )

    private val DEFAULT_ON = setOf(
        HomeAppearance.KEY_SHOW_LOGO,
        HomeAppearance.KEY_SHOW_SEARCH,
        HomeAppearance.KEY_SHOW_SHORTCUTS,
        HomeAppearance.KEY_DIM,
    )

    private const val COLUMNS = 4
    private val executor = Executors.newSingleThreadExecutor()
}

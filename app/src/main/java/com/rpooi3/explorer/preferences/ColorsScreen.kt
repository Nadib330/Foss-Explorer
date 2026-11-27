/*
 * Explorer: Settings > Appearance > Colors.
 * Own code, GPL-3.0.
 *
 * 18.0.0 beta 9 (item 7): the screen is the color picker of Chromium's "Customize Chrome" panel,
 * laid out for a phone: a row of round swatches, the one in use carrying a tick mark, with the
 * plain default in front of them; a tap chooses the color at once.
 *
 * 18.1.0 beta (item 2): the swatch is Chromium's — a circle of three tones, card apart from the
 * next, tick in the corner — instead of the gradient circle of beta 9.
 *
 * 18.1.0 beta (item 3): the last card is a color of the user's own, opened with the pencil: a
 * hue and a saturation, a preview of what they make, and the same three-tone swatch once chosen.
 *
 * The colors themselves are Chromium's (see `theme/ColorSchemes.kt`), and the notice of the
 * restart (item 5) is shown here, where the color was chosen.
 */
package com.rpooi3.explorer.preferences

import android.app.Activity
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.GridLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import com.google.android.material.color.MaterialColors
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.rpooi3.explorer.R
import com.rpooi3.explorer.profile.ProfilePrefs
import com.rpooi3.explorer.theme.ColorScheme
import com.rpooi3.explorer.theme.ColorSchemes
import com.rpooi3.explorer.theme.ColorSwatch

object ColorsScreen {

    /** How the row of Settings > Appearance is kept up to date. */
    @JvmStatic
    fun bind(fragment: PreferenceFragmentCompat) {
        val row = fragment.findPreference<Preference>("theme_colors") ?: return
        refresh(fragment, row)
        row.setOnPreferenceClickListener {
            show(fragment)
            true
        }
    }

    private fun refresh(fragment: PreferenceFragmentCompat, row: Preference) {
        val context = fragment.requireContext()
        val prefs = ProfilePrefs.settings(context)
        // The Appearance card has one concise line: the generic label before a choice, or the
        // preset name after one is selected. Do not append the old system-colour explanation.
        val chosen = ColorSchemes.chosen(prefs)
        row.title = if (chosen == null) context.getString(R.string.theme_colors) else ColorSchemes.name(context, chosen)
        row.setSummaryProvider(null)
        row.summary = null
    }

    // ------------------------------------------------------------------------------------------
    // The picker
    // ------------------------------------------------------------------------------------------

    /** The picker itself: a card for every color, four to a row, and the pencil at the end. */
    @JvmStatic
    fun show(fragment: PreferenceFragmentCompat) {
        val context = fragment.requireContext()
        val prefs = ProfilePrefs.settings(context)
        val chosen = ColorSchemes.chosen(prefs)
        val dp: (Int) -> Int = { (it * context.resources.displayMetrics.density).toInt() }

        val width = context.resources.displayMetrics.widthPixels
        val column = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(4), dp(16), dp(4))
        }
        // Four cards to a row, as in Chromium: the width of one is what is left of the screen
        // once the margins and the three gaps between them have been taken away.
        val gap = dp(8)
        val cardWidth = (width - dp(32) - 3 * gap) / 4

        column.addView(
            TextView(context).apply {
                text = context.getString(R.string.theme_colors_picker_note)
                setTextColor(MaterialColors.getColor(context, android.R.attr.textColorSecondary, Color.GRAY))
                textSize = 13f
                setPadding(dp(2), 0, dp(2), dp(10))
            },
        )

        val grid = GridLayout(context).apply {
            columnCount = 4
            id = R.id.theme_colors_grid
        }
        column.addView(grid)

        for (scheme in ColorSchemes.ALL) {
            grid.addView(
                card(context, dp, cardWidth, scheme, ColorSchemes.isChosen(prefs, scheme)) {
                    choose(fragment, scheme)
                },
                cellParams(gap),
            )
        }
        // The color of the user's own (item 3): the pencil, or its swatch once it is chosen.
        val custom = if (chosen != null && chosen.isCustom) chosen else null
        grid.addView(
            card(context, dp, cardWidth, custom, custom != null, customCard = true) { showCustom(fragment) },
            cellParams(gap),
        )

        MaterialAlertDialogBuilder(context)
            .setTitle(R.string.theme_colors_title)
            .setView(column)
            .setNegativeButton(R.string.app_cancel, null)
            .show()
    }

    private fun cellParams(gap: Int): GridLayout.LayoutParams =
        GridLayout.LayoutParams().apply {
            width = 0
            height = ViewGroup.LayoutParams.WRAP_CONTENT
            columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
            setMargins(gap / 2, gap / 2, gap / 2, gap / 2)
        }

    /**
     * One card of the picker: the rounded surface of Chromium's grid, the three-tone circle in it,
     * the name of the color as its description, and — when it is the one in use — Chromium's tick
     * in the corner.
     */
    private fun card(
        context: Context,
        dp: (Int) -> Int,
        width: Int,
        scheme: ColorScheme?,
        selected: Boolean,
        customCard: Boolean = false,
        onClick: () -> Unit,
    ): View {
        val cardHeight = (width * 0.86f).toInt()
        val box = android.widget.FrameLayout(context).apply {
            layoutParams = LinearLayout.LayoutParams(width, cardHeight)
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(16).toFloat()
                setColor(
                    MaterialColors.getColor(
                        context,
                        com.google.android.material.R.attr.colorSurfaceContainerHighest,
                        Color.LTGRAY,
                    ),
                )
            }
            isClickable = true
            isFocusable = true
            contentDescription = ColorSchemes.name(context, scheme)
            setOnClickListener { onClick() }
        }
        val circle = dp(56)
        val swatch = View(context).apply {
            layoutParams = android.widget.FrameLayout.LayoutParams(circle, circle)
                .apply { gravity = Gravity.CENTER }
        }
        if (scheme == null) {
            // The pencil of the color of the user's own (item 3).
            val pencil = ImageView(context).apply {
                layoutParams = android.widget.FrameLayout.LayoutParams(dp(24), dp(24))
                    .apply { gravity = Gravity.CENTER }
                setImageResource(R.drawable.icon_edit)
                imageTintList = ColorStateList.valueOf(
                    MaterialColors.getColor(context, com.google.android.material.R.attr.colorOnSurfaceVariant, Color.DKGRAY),
                )
            }
            box.addView(pencil)
        } else {
            swatch.background = ColorSwatch(ColorSchemes.swatchColors(scheme))
            box.addView(swatch)
        }
        if (selected) {
            // Chromium's tick: a dark disc with a white check, in the corner of the card.
            val badge = android.widget.FrameLayout(context).apply {
                layoutParams = android.widget.FrameLayout.LayoutParams(dp(22), dp(22))
                    .apply {
                        gravity = Gravity.TOP or Gravity.END
                        setMargins(0, dp(6), dp(6), 0)
                    }
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(0xFF202124.toInt())
                }
            }
            badge.addView(
                ImageView(context).apply {
                    layoutParams = android.widget.FrameLayout.LayoutParams(dp(14), dp(14))
                        .apply { gravity = Gravity.CENTER }
                    setImageResource(R.drawable.icon_check)
                    imageTintList = ColorStateList.valueOf(Color.WHITE)
                    id = R.id.theme_colors_tick
                },
            )
            box.addView(badge)
        }
        if (customCard) box.id = R.id.theme_colors_custom_card
        return box
    }

    /** Chooses [scheme] and asks for the restart, here in the settings (item 5). */
    @JvmStatic
    fun choose(fragment: PreferenceFragmentCompat, scheme: ColorScheme?) {
        val context = fragment.requireContext()
        val prefs = ProfilePrefs.settings(context)
        ColorSchemes.choose(prefs, scheme)
        val row = fragment.findPreference<Preference>("theme_colors")
        if (row != null) refresh(fragment, row)
        // The theme is read when a screen is made, so the colors show after the restart — asked
        // for here, where the color was chosen (18.1.0 beta, item 5).
        RestartPrompt.show(fragment.activity)
    }

    // ------------------------------------------------------------------------------------------
    // A color of the user's own (18.1.0 beta, item 3)
    // ------------------------------------------------------------------------------------------

    /** The pencil: a hue, a saturation, the swatch they make, and the way to keep it. */
    @JvmStatic
    fun showCustom(fragment: PreferenceFragmentCompat) {
        val context = fragment.requireContext()
        val dp: (Int) -> Int = { (it * context.resources.displayMetrics.density).toInt() }
        val prefs = ProfilePrefs.settings(context)
        val now = ColorSchemes.chosen(prefs)
        val startHue = if (now != null && now.isCustom) hueOf(now.seed) else 214f
        val startSaturation = if (now != null && now.isCustom) saturationOf(now.seed) else 0.6f

        var seed = seedOf(startHue, startSaturation)

        val column = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(8), dp(20), dp(4))
        }
        val preview = View(context).apply {
            layoutParams = LinearLayout.LayoutParams(dp(96), dp(96)).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                bottomMargin = dp(8)
            }
            id = R.id.theme_colors_preview
            background = ColorSwatch(ColorSchemes.swatchColors(ColorSchemes.customScheme(seed)))
        }
        column.addView(preview)

        val hueLabel = TextView(context).apply {
            text = context.getString(R.string.theme_colors_hue, startHue.toInt())
            setTextColor(MaterialColors.getColor(context, android.R.attr.textColorSecondary, Color.GRAY))
            textSize = 13f
        }
        val hue = SeekBar(context).apply {
            max = 360
            progress = startHue.toInt()
            id = R.id.theme_colors_hue
        }
        val saturationLabel = TextView(context).apply {
            text = context.getString(R.string.theme_colors_saturation, (startSaturation * 100).toInt())
            setTextColor(MaterialColors.getColor(context, android.R.attr.textColorSecondary, Color.GRAY))
            textSize = 13f
        }
        val saturation = SeekBar(context).apply {
            max = 100
            progress = (startSaturation * 100).toInt()
            id = R.id.theme_colors_saturation
        }
        val repaint = {
            seed = seedOf(hue.progress.toFloat(), saturation.progress / 100f)
            preview.background = ColorSwatch(
                ColorSchemes.swatchColors(ColorSchemes.customScheme(seed)),
            )
            hueLabel.text = context.getString(R.string.theme_colors_hue, hue.progress)
            saturationLabel.text = context.getString(R.string.theme_colors_saturation, saturation.progress)
        }
        hue.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(bar: SeekBar?, progress: Int, fromUser: Boolean) = repaint()
            override fun onStartTrackingTouch(bar: SeekBar?) = Unit
            override fun onStopTrackingTouch(bar: SeekBar?) = Unit
        })
        saturation.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(bar: SeekBar?, progress: Int, fromUser: Boolean) = repaint()
            override fun onStartTrackingTouch(bar: SeekBar?) = Unit
            override fun onStopTrackingTouch(bar: SeekBar?) = Unit
        })

        column.addView(hueLabel)
        column.addView(hue, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(40)))
        column.addView(saturationLabel)
        column.addView(saturation, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(40)))

        val dialog = MaterialAlertDialogBuilder(context)
            .setTitle(R.string.theme_colors_custom_title)
            .setView(column)
            .setNegativeButton(R.string.app_cancel, null)
            .setPositiveButton(R.string.theme_colors_use) { _, _ ->
                choose(fragment, ColorSchemes.customScheme(seed))
            }
            .create()
        dialog.show()
    }

    /** The seed of a hue and a saturation, as a color: the hue at full strength, then softened. */
    private fun seedOf(hue: Float, saturation: Float): Int = Color.HSVToColor(
        floatArrayOf(hue.coerceIn(0f, 360f), saturation.coerceIn(0f, 1f), 1f),
    )

    private fun hueOf(color: Int): Float {
        val hsv = FloatArray(3)
        Color.colorToHSV(color, hsv)
        return hsv[0]
    }

    private fun saturationOf(color: Int): Float {
        val hsv = FloatArray(3)
        Color.colorToHSV(color, hsv)
        return hsv[1]
    }
}

/*
 * Explorer: naming, colouring and picturing a profile.
 * Own code, GPL-3.0.
 *
 * The same dialog is used by the welcome (the first profile), by the sheet of the profiles (a new
 * profile, on any start of the browser) and by the settings (changing one). It holds everything a
 * profile is — its picture, its name and its colour — and nothing that asks who the user is,
 * because Explorer has no sign-in.
 *
 * 18.0.0 beta 3: the picture is chosen from here in both cases. It used to be possible only while
 * an existing profile was edited from the settings, which left a profile that was made on the home
 * screen with no way of getting a face at all.
 */
package com.rpooi3.explorer.profile

import android.app.Activity
import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.text.InputFilter
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.graphics.drawable.RoundedBitmapDrawableFactory
import com.google.android.material.color.MaterialColors
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.rpooi3.explorer.R

/**
 * The dialog that makes or changes a profile.
 *
 * @param existing the profile to change, or null to make a new one
 * @param onDone called with the name and the colour that were chosen
 * @param onSkipped called when the dialog is left without an answer
 * @param onPicture called with the picture that was chosen (or with null when it was taken away),
 *   before the profile is saved: a new profile has no folder to keep it in yet
 */
class ProfileSetupDialog @JvmOverloads constructor(
    private val activity: Activity,
    private val existing: BrowserProfile? = null,
    private val onDone: (name: String, colour: Int) -> Unit,
    private val onSkipped: () -> Unit = {},
    private val onPicture: ((Bitmap?) -> Unit)? = null,
) {

    private var answered = false
    private var colour: Int = existing?.colour ?: Profiles.nextColour(activity)
    private val swatches = ArrayList<View>()

    /** The picture as the dialog shows it: the kept one, or the one that was just chosen. */
    private var picture: Bitmap? = null
    private var preview: ImageView? = null
    private var removeButton: TextView? = null

    private fun dp(value: Int): Int = (value * activity.resources.displayMetrics.density).toInt()

    fun show() {
        val context: Context = activity
        // The picture the profile has now, if one is being changed.
        if (existing != null && ProfilePictures.has(context, existing)) {
            picture = ProfilePictures.bitmap(context, existing, 512)
        }

        val nameField = EditText(context).apply {
            id = R.id.profile_name_field
            hint = context.getString(R.string.profiles_name_hint)
            setText(existing?.name ?: "")
            isSingleLine = true
            imeOptions = EditorInfo.IME_ACTION_DONE
            filters = arrayOf(InputFilter.LengthFilter(32))
            setPadding(dp(4), dp(8), dp(4), dp(8))
        }

        val content = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(8), dp(24), 0)
            addView(pictureRow(nameField))
            addView(nameField)
            addView(
                TextView(context).apply {
                    text = context.getString(R.string.profiles_colour)
                    setPadding(0, dp(16), 0, dp(8))
                    setTextColor(
                        MaterialColors.getColor(
                            context,
                            com.google.android.material.R.attr.colorOnSurfaceVariant,
                            0,
                        ),
                    )
                },
            )
            addView(swatchRow())
        }

        val dialog = MaterialAlertDialogBuilder(context)
            .setTitle(if (existing == null) R.string.profiles_new_title else R.string.profiles_edit_title)
            .setView(content)
            .setNegativeButton(android.R.string.cancel) { _, _ ->
                answered = true
                onSkipped()
            }
            .setPositiveButton(
                if (existing == null) R.string.profiles_create else R.string.profiles_save,
            ) { _, _ ->
                val name = nameField.text.toString().trim()
                answered = true
                if (name.isNotEmpty()) onDone(name, colour) else onSkipped()
            }
            .create()
        dialog.setOnDismissListener { if (!answered) { answered = true; onSkipped() } }
        dialog.show()
        nameField.requestFocus()
    }

    // The picture ----------------------------------------------------------------------------

    /**
     * The disc of the profile and the two ways of changing it: choose a picture, or take the one
     * that is there away. It is the same row for a new profile and for one that exists.
     */
    private fun pictureRow(nameField: EditText): View {
        val context: Context = activity
        val column = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(0, dp(4), 0, dp(4))
        }
        val image = ImageView(context).apply {
            id = R.id.profile_picture_preview
            layoutParams = LinearLayout.LayoutParams(dp(72), dp(72))
            isClickable = true
            isFocusable = true
            contentDescription = context.getString(R.string.profiles_picture_choose)
            setOnClickListener { choosePicture(nameField) }
        }
        preview = image
        column.addView(image)
        column.addView(
            TextView(context).apply {
                text = context.getString(R.string.profiles_picture_choose)
                gravity = Gravity.CENTER_HORIZONTAL
                setPadding(0, dp(6), 0, 0)
                setTextColor(
                    MaterialColors.getColor(context, androidx.appcompat.R.attr.colorPrimary, 0),
                )
                isClickable = true
                setOnClickListener { choosePicture(nameField) }
            },
        )
        val remove = TextView(context).apply {
            id = R.id.profile_picture_remove
            text = context.getString(R.string.profiles_picture_remove)
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(0, dp(4), 0, 0)
            setTextColor(
                MaterialColors.getColor(
                    context,
                    com.google.android.material.R.attr.colorOnSurfaceVariant,
                    0,
                ),
            )
            isClickable = true
            setOnClickListener {
                picture = null
                onPicture?.invoke(null)
                paintPicture(nameField)
            }
        }
        removeButton = remove
        column.addView(remove)
        paintPicture(nameField)
        return column
    }

    /** Shows the picture that is chosen now, or the letter of the profile in its colour. */
    private fun paintPicture(nameField: EditText) {
        val context: Context = activity
        val image = preview ?: return
        val bitmap = picture
        if (bitmap != null) {
            image.setImageDrawable(
                RoundedBitmapDrawableFactory.create(context.resources, bitmap).apply {
                    isCircular = true
                    setAntiAlias(true)
                },
            )
        } else {
            val shown = (existing ?: BrowserProfile("new", typedName(nameField), colour))
                .withColour(colour)
            if (ProfilePictures.has(context, shown)) {
                image.setImageDrawable(ProfilePictures.drawable(context, shown, 72))
            } else {
                image.setImageDrawable(ProfileAvatarDrawable(shown))
            }
        }
        removeButton?.visibility = if (bitmap != null || (existing != null && ProfilePictures.has(context, existing))) {
            View.VISIBLE
        } else {
            View.GONE
        }
    }

    private fun typedName(nameField: EditText): String =
        nameField.text.toString().trim().ifEmpty { existing?.name ?: "Profile" }

    private fun choosePicture(nameField: EditText) {
        ProfilePicturePicker.pick(activity) { uri: Uri? ->
            if (uri == null) return@pick
            val chosen = ProfilePicturePicker.preview(activity, uri, 72)
            if (chosen == null) return@pick
            picture = chosen
            onPicture?.invoke(chosen)
            paintPicture(nameField)
        }
    }

    // The colour -----------------------------------------------------------------------------

    /** The nine colours of [Profiles.COLOURS], the one in use ringed. */
    private fun swatchRow(): View {
        val context: Context = activity
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.START
            setPadding(0, 0, 0, dp(8))
        }
        val size = dp(34)
        for (index in Profiles.COLOURS.indices) {
            val swatch = FrameLayout(context).apply {
                layoutParams = LinearLayout.LayoutParams(size, size).apply {
                    marginEnd = dp(6)
                    topMargin = dp(4)
                    bottomMargin = dp(4)
                }
                id = R.id.profile_colour_swatch
                contentDescription = Profiles.COLOUR_NAMES[index]
                isClickable = true
                isFocusable = true
                setOnClickListener { choose(index) }
            }
            swatch.addView(
                View(context).apply {
                    background = GradientDrawable().apply {
                        shape = GradientDrawable.OVAL
                        setColor(Profiles.COLOURS[index])
                    }
                    layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                    ).apply { setMargins(dp(3), dp(3), dp(3), dp(3)) }
                },
            )
            swatches.add(swatch)
            row.addView(swatch)
        }
        paintSelection()
        return row
    }

    private fun choose(index: Int) {
        colour = index
        paintSelection()
    }

    private fun paintSelection() {
        swatches.forEachIndexed { index, swatch ->
            swatch.background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                if (index == colour) {
                    setStroke(dp(2).coerceAtLeast(2), Profiles.COLOURS[index])
                } else {
                    setStroke(0, 0)
                }
            }
        }
    }
}

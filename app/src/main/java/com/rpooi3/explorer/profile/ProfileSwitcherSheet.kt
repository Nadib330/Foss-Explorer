/*
 * Explorer: the sheet that asks which profile to browse in.
 * Own code, GPL-3.0.
 *
 * Laid out like the profile panel of Chrome for Android, in Material 3 cards: the profile in front
 * at the top with its disc, its name and what it keeps; below it every other profile with the tab
 * count of the browser; then the guest, the two ways of making and managing profiles, and — at the
 * bottom, as the user asked for it — the checkbox "Always show on startup".
 *
 * No account is offered anywhere: Explorer has no sign-in, so a profile is a name and a colour, not
 * an identity at Google.
 */
package com.rpooi3.explorer.profile

import android.app.Activity
import android.content.Context
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.edit
import androidx.preference.PreferenceManager
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.color.MaterialColors
import com.rpooi3.explorer.R

/**
 * The sheet of the profiles.
 *
 * @param activity the screen the sheet belongs to
 * @param tabCount how many tabs the browser has open, for the line under the profile in front
 * @param listener what the sheet does when a profile, the guest, a new profile or the settings of
 *   the profiles are asked for
 */
class ProfileSwitcherSheet @JvmOverloads constructor(
    private val activity: Activity,
    private val tabCount: Int = 1,
    private val listener: Listener,
) {

    /** What the sheet asks the browser to do. */
    interface Listener {
        /** Makes [profile] the profile in front. */
        fun onSwitchProfile(profile: BrowserProfile)

        /** Opens or leaves the guest session. */
        fun onGuest()

        /** Makes a new profile. */
        fun onAddProfile()

        /** Opens the screen that keeps the profiles. */
        fun onManageProfiles()
    }

    private val context: Context = activity
    private var dialog: BottomSheetDialog? = null

    /** The sheet that is open, if one is. */
    var isShowing: Boolean = false
        private set

    private fun dp(value: Int): Int = (value * context.resources.displayMetrics.density).toInt()

    fun show() {
        if (isShowing) return
        val sheet = BottomSheetDialog(context)
        sheet.setContentView(createView())
        sheet.setOnDismissListener { isShowing = false }
        dialog = sheet
        isShowing = true
        sheet.show()
    }

    fun dismiss() {
        isShowing = false
        dialog?.dismiss()
        dialog = null
    }

    /** The content of the sheet, built without showing it. */
    fun createView(): View {
        val active = Profiles.current(context)
        val guestRunning = Profiles.isGuest(context)

        val root = LinearLayout(context).apply {
            id = R.id.profile_switcher_sheet
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(8))
        }

        root.addView(title(active))
        root.addView(activeCard(active))
        root.addView(otherProfiles(active))
        root.addView(guestCard(guestRunning))
        root.addView(actions())
        root.addView(alwaysAsk())
        return root
    }

    // The head of the sheet --------------------------------------------------------------------

    private fun title(active: BrowserProfile): View = TextView(context).apply {
        text = context.getString(R.string.profiles_title)
        setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 24f)
        setPadding(dp(8), dp(4), dp(8), dp(12))
        setTextColor(MaterialColors.getColor(context, com.google.android.material.R.attr.colorOnSurface, 0))
    }

    private fun activeCard(active: BrowserProfile): View {
        val card = card(ProfileColour.soft(context, active))
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(16))
        }
        val avatar = ProfileAvatarView(context, active, 56).apply { markActive(true) }
        row.addView(avatar)
        val texts = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), 0, 0, 0)
        }
        texts.addView(
            TextView(context).apply {
                text = active.name
                setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 22f)
                setTextColor(
                    MaterialColors.getColor(context, com.google.android.material.R.attr.colorOnSurface, 0),
                )
            },
        )
        texts.addView(
            TextView(context).apply {
                text = if (Profiles.isGuest(context)) {
                    context.getString(R.string.profiles_guest_active)
                } else {
                    context.getString(
                        R.string.profiles_current_with_tabs,
                        tabCount,
                    )
                }
                setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 14f)
                setTextColor(
                    MaterialColors.getColor(
                        context,
                        com.google.android.material.R.attr.colorOnSurfaceVariant,
                        0,
                    ),
                )
            },
        )
        row.addView(texts, LinearLayout.LayoutParams(0, WRAP, 1f))
        card.addView(row)
        // The profile in front leads to its own settings: name, picture, colour.
        card.isClickable = true
        card.setOnClickListener {
            dismiss()
            listener.onManageProfiles()
        }
        return card
    }

    // The other profiles -----------------------------------------------------------------------

    private fun otherProfiles(active: BrowserProfile): View {
        val card = card(
            MaterialColors.getColor(context, com.google.android.material.R.attr.colorSurfaceContainerHigh, 0),
        )
        val column = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        val others = Profiles.all(context).filter { it.id != active.id }
        if (others.isEmpty()) {
            column.addView(
                TextView(context).apply {
                    text = context.getString(R.string.profiles_only_note)
                    setPadding(dp(16), dp(14), dp(16), dp(14))
                    setTextColor(
                        MaterialColors.getColor(
                            context,
                            com.google.android.material.R.attr.colorOnSurfaceVariant,
                            0,
                        ),
                    )
                },
            )
        }
        for (profile in others) {
            column.addView(
                row(profile, context.getString(R.string.profiles_switch)) {
                    dismiss()
                    listener.onSwitchProfile(profile)
                },
            )
        }
        card.addView(column)
        return card
    }

    /** One row: disc, name, and what tapping it does. */
    private fun row(profile: BrowserProfile, note: String, onTap: () -> Unit): View =
        LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(12), dp(16), dp(12))
            isClickable = true
            isFocusable = true
            addView(ProfileAvatarView(context, profile))
            val texts = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(16), 0, 0, 0)
            }
            texts.addView(
                TextView(context).apply {
                    text = profile.name
                    setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 17f)
                    setTextColor(
                        MaterialColors.getColor(context, com.google.android.material.R.attr.colorOnSurface, 0),
                    )
                },
            )
            texts.addView(
                TextView(context).apply {
                    text = note
                    setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 13f)
                    setTextColor(
                        MaterialColors.getColor(
                            context,
                            com.google.android.material.R.attr.colorOnSurfaceVariant,
                            0,
                        ),
                    )
                },
            )
            addView(texts, LinearLayout.LayoutParams(0, WRAP, 1f))
            setOnClickListener { onTap() }
        }

    // The guest --------------------------------------------------------------------------------

    private fun guestCard(guestRunning: Boolean): View {
        val card = card(
            MaterialColors.getColor(context, com.google.android.material.R.attr.colorSurfaceContainerHigh, 0),
        )
        val guest = BrowserProfile("guest", context.getString(R.string.profiles_guest), 7, guest = true)
        card.addView(
            row(
                guest,
                context.getString(
                    if (guestRunning) R.string.profiles_guest_leave_note else R.string.profiles_guest_note,
                ),
            ) {
                dismiss()
                listener.onGuest()
            },
        )
        return card
    }

    // Making and managing ----------------------------------------------------------------------

    private fun actions(): View = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        setPadding(0, dp(12), 0, dp(4))
        addView(
            MaterialButton(context, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
                text = context.getString(R.string.profiles_add)
                setOnClickListener {
                    dismiss()
                    listener.onAddProfile()
                }
            },
            LinearLayout.LayoutParams(0, WRAP, 1f).apply { marginEnd = dp(8) },
        )
        addView(
            MaterialButton(context, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
                text = context.getString(R.string.profiles_settings)
                setOnClickListener {
                    dismiss()
                    listener.onManageProfiles()
                }
            },
            LinearLayout.LayoutParams(0, WRAP, 1f).apply { marginStart = dp(8) },
        )
    }

    /** The checkbox at the bottom of the sheet, as the user asked for it. */
    private fun alwaysAsk(): View = CheckBox(context).apply {
        id = R.id.profile_always_ask
        text = context.getString(R.string.profiles_always_ask)
        isChecked = Profiles.askOnStartup(context)
        setPadding(dp(8), dp(12), dp(8), dp(4))
        setOnCheckedChangeListener { _, checked -> Profiles.setAskOnStartup(context, checked) }
    }

    private fun card(colour: Int): MaterialCardView = MaterialCardView(context).apply {
        layoutParams = ViewGroup.MarginLayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        ).apply { bottomMargin = dp(12) }
        radius = dp(24).toFloat()
        cardElevation = 0f
        setCardBackgroundColor(colour)
        isClickable = false
    }

    private companion object {
        const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT
    }
}

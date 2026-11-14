package com.rpooi3.explorer.preferences

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceGroup
import androidx.preference.PreferenceGroupAdapter
import androidx.preference.PreferenceViewHolder
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.color.MaterialColors
import com.rpooi3.explorer.R

/**
 * Draws a preference screen as groups of rounded cards.
 *
 * Every setting is one row with a round, tinted icon, a title and an optional
 * summary. The rows of a [PreferenceCategory] form one block: the first row
 * has large top corners, the last one large bottom corners, with a thin gap
 * between rows and a wider gap between blocks.
 */
object SettingsCards {

    private const val EXTRA_TONE = "settings_card_tone"

    /** Icon colours as pairs of (circle, icon) theme attributes; rows cycle through them. */
    /** Material's strength for content that is switched off. */
    const val DISABLED_ALPHA = 0.38f

    private val TONES = arrayOf(
        com.google.android.material.R.attr.colorPrimaryContainer to
            com.google.android.material.R.attr.colorOnPrimaryContainer,
        com.google.android.material.R.attr.colorSurfaceContainerHighest to
            com.google.android.material.R.attr.colorOnSurfaceVariant,
        com.google.android.material.R.attr.colorTertiaryContainer to
            com.google.android.material.R.attr.colorOnTertiaryContainer,
        com.google.android.material.R.attr.colorSecondaryContainer to
            com.google.android.material.R.attr.colorOnSecondaryContainer,
    )

    /**
     * Prepares all preferences below [group] for the card look. Call before
     * the screen is attached to its list.
     */
    @JvmStatic
    fun style(group: PreferenceGroup) {
        var tone = 0
        fun visit(parent: PreferenceGroup) {
            for (i in 0 until parent.preferenceCount) {
                val pref = parent.getPreference(i)
                if (pref is PreferenceGroup) {
                    pref.layoutResource = R.layout.preference_card_group
                    visit(pref)
                } else {
                    if (pref !is OwnLayout) pref.layoutResource = R.layout.preference_card_row
                    // A row without an icon starts at the edge of its card, as in Chromium.
                    pref.isIconSpaceReserved = false
                    pref.extras.putInt(EXTRA_TONE, tone++ % TONES.size)
                    // Upstream put the label of its checkboxes into the summary.
                    // A row needs a title, so the text moves there.
                    // (Text between the cards is a description without a title, and stays one.)
                    if (pref !is PlainRow && pref.title.isNullOrEmpty() && pref.summaryProvider == null &&
                        !pref.summary.isNullOrEmpty()
                    ) {
                        pref.title = pref.summary
                        pref.summary = null
                    }
                }
            }
        }
        visit(group)
    }

    /** Whether [pref] matches the search text (title, summary or attached keywords, ignoring case). */
    @JvmStatic
    fun matches(pref: Preference, query: String): Boolean {
        val needle = query.trim()
        if (needle.isEmpty()) return true
        return pref.title?.contains(needle, ignoreCase = true) == true ||
            pref.summary?.contains(needle, ignoreCase = true) == true ||
            // A topic of the first screen also matches by the settings behind it.
            pref.peekExtras()?.getString(SettingsSections.EXTRA_KEYWORDS)?.contains(needle, ignoreCase = true) == true
    }

    /**
     * Shows only the settings below [group] that match [query] and for which
     * [isAvailable] is true; groups left without a row are hidden as well.
     * Returns the number of settings that remain visible.
     */
    @JvmStatic
    fun filter(group: PreferenceGroup, query: String, isAvailable: (Preference) -> Boolean): Int {
        var shown = 0
        for (i in 0 until group.preferenceCount) {
            val pref = group.getPreference(i)
            if (pref is PreferenceGroup) {
                val inside = filter(pref, query, isAvailable)
                pref.isVisible = inside > 0
                shown += inside
            } else {
                val visible = isAvailable(pref) && matches(pref, query)
                pref.isVisible = visible
                if (visible) shown++
            }
        }
        return shown
    }

    /** The list adapter that gives each row its card shape and icon colours. */
    @JvmStatic
    fun createAdapter(group: PreferenceGroup): RecyclerView.Adapter<*> = CardAdapter(group)

    /** Which corners of a row are the large outer ones. */
    internal fun isFirstInBlock(previous: Preference?): Boolean = 
        previous == null || previous is PreferenceGroup || previous is PlainRow

    internal fun isLastInBlock(next: Preference?): Boolean = 
        next == null || next is PreferenceGroup || next is PlainRow

    private fun cardBackground(context: Context, first: Boolean, last: Boolean): Drawable {
        val res = context.resources
        val outer = res.getDimension(R.dimen.settings_card_corner)
        val inner = res.getDimension(R.dimen.settings_card_corner_inner)
        val top = if (first) outer else inner
        val bottom = if (last) outer else inner
        val radii = floatArrayOf(top, top, top, top, bottom, bottom, bottom, bottom)
        val card = GradientDrawable().apply {
            cornerRadii = radii
            setColor(MaterialColors.getColor(context, com.google.android.material.R.attr.colorSurfaceBright, 0))
        }
        val mask = GradientDrawable().apply {
            cornerRadii = radii
            setColor(-0x1)
        }
        val ripple = MaterialColors.getColor(context, androidx.appcompat.R.attr.colorControlHighlight, 0)
        return RippleDrawable(ColorStateList.valueOf(ripple), card, mask)
    }

    // PreferenceGroupAdapter is public but marked for library use. It is the
    // only place where a row's neighbours are known at bind time.
    @SuppressLint("RestrictedApi")
    private class CardAdapter(group: PreferenceGroup) : PreferenceGroupAdapter(group) {

        private val handler = Handler(Looper.getMainLooper())

        init {
            // When rows appear or disappear (search, hidden settings), their
            // neighbours may have become the first or last row of a block.
            registerAdapterDataObserver(object : RecyclerView.AdapterDataObserver() {
                override fun onItemRangeInserted(positionStart: Int, itemCount: Int) = rebindAll()
                override fun onItemRangeRemoved(positionStart: Int, itemCount: Int) = rebindAll()
                override fun onItemRangeMoved(fromPosition: Int, toPosition: Int, itemCount: Int) = rebindAll()
            })
        }

        private fun rebindAll() {
            handler.post { notifyItemRangeChanged(0, itemCount) }
        }

        override fun onBindViewHolder(holder: PreferenceViewHolder, position: Int) {
            super.onBindViewHolder(holder, position)
            val pref = getItem(position) ?: return
            if (pref is PreferenceGroup || pref is PlainRow) return
            HomeCustomizationScreen.bindColorDot(pref, holder)

            val view = holder.itemView
            val context = view.context
            val first = isFirstInBlock(if (position > 0) getItem(position - 1) else null)
            val last = isLastInBlock(if (position < itemCount - 1) getItem(position + 1) else null)
            view.background = cardBackground(context, first, last)

            (view.layoutParams as? ViewGroup.MarginLayoutParams)?.let { lp ->
                val side = context.resources.getDimensionPixelSize(R.dimen.settings_card_margin)
                lp.marginStart = side
                lp.marginEnd = side
                lp.bottomMargin = context.resources.getDimensionPixelSize(R.dimen.settings_card_gap)
                view.layoutParams = lp
            }

            // Rows with a layout of their own (radio rows) colour their icon themselves.
            if (pref is OwnLayout) return
            // A setting that cannot be used is shown faded; the card behind it stays as it is.
            val strength = if (pref.isEnabled) 1f else DISABLED_ALPHA
            for (id in intArrayOf(android.R.id.title, android.R.id.summary, R.id.icon_frame, android.R.id.widget_frame)) {
                view.findViewById<View>(id)?.alpha = strength
            }
            val (circle, glyph) = TONES[pref.extras.getInt(EXTRA_TONE, 0) % TONES.size]
            holder.findViewById(R.id.icon_frame)?.let { frame: View ->
                frame.backgroundTintList = ColorStateList.valueOf(MaterialColors.getColor(context, circle, 0))
            }
            (holder.findViewById(android.R.id.icon) as? ImageView)?.imageTintList =
                ColorStateList.valueOf(MaterialColors.getColor(context, glyph, 0))
        }
    }
}

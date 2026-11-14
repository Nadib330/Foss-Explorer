/*
 * Foss Explorer: the rows that Chromium's settings screens are made of.
 * Own code, GPL-3.0. The row layouts follow Chromium's
 * radio_button_with_description.xml, binary_state_permission_preference.xml,
 * page_zoom_preference.xml and page_zoom_slider.xml (BSD, see
 * third_party/chromium-settings).
 */
package com.rpooi3.explorer.preferences

import android.content.Context
import android.content.res.ColorStateList
import android.util.AttributeSet
import android.view.View
import android.widget.ImageView
import android.widget.RadioButton
import android.widget.TextView
import androidx.annotation.StringRes
import androidx.preference.Preference
import androidx.preference.PreferenceViewHolder
import com.google.android.material.color.MaterialColors
import com.google.android.material.slider.Slider
import com.rpooi3.explorer.R
import kotlin.math.roundToInt

/** A row that brings its own layout; [SettingsCards] leaves the layout alone. */
interface OwnLayout

/** A row that is not drawn as a card: text between the cards. Cards before and after it are closed. */
interface PlainRow : OwnLayout

/**
 * Text between the cards, as Chromium's TextMessagePreference: the
 * description at the top of a screen, or a note. Not clickable.
 */
class TextMessagePreference @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) :
    Preference(context, attrs), PlainRow {
    init {
        layoutResource = R.layout.preference_text_message
        isSelectable = false
        isPersistent = false
    }
}

/**
 * One choice of a group, as Chromium's RadioButtonWithDescription: radio
 * button, optional icon, text, optional description. With [radioAtEnd] the
 * radio button stands at the end of the row, as on Chrome's "Search engine"
 * screen.
 */
class RadioRowPreference @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) :
    Preference(context, attrs), OwnLayout {

    /** The value this row stands for in its group. */
    var value: String = ""

    var radioAtEnd: Boolean = false
        set(v) {
            field = v
            notifyChanged()
        }

    /** Whether the icon is a picture of its own (a logo) and keeps its colours and a larger size. */
    var iconIsLogo: Boolean = false

    /** Whether the icon comes in its final colours; otherwise it is drawn in the colour of icons. */
    var iconHasColors: Boolean = false

    var isChecked: Boolean = false
        set(v) {
            if (field == v) return
            field = v
            notifyChanged()
        }

    init {
        layoutResource = R.layout.preference_radio_row
        isPersistent = false
    }

    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        super.onBindViewHolder(holder)
        val start = holder.findViewById(R.id.radio_start) as RadioButton
        val end = holder.findViewById(R.id.radio_end) as RadioButton
        start.visibility = if (radioAtEnd) View.GONE else View.VISIBLE
        end.visibility = if (radioAtEnd) View.VISIBLE else View.GONE
        start.isChecked = isChecked
        end.isChecked = isChecked
        start.isEnabled = isEnabled
        end.isEnabled = isEnabled
        (holder.findViewById(android.R.id.icon) as? ImageView)?.let { image ->
            val size = (context.resources.displayMetrics.density * (if (iconIsLogo) 40 else 24)).roundToInt()
            image.layoutParams = image.layoutParams.apply {
                width = size
                height = size
            }
            image.imageTintList = if (iconIsLogo || iconHasColors) null else ColorStateList.valueOf(
                MaterialColors.getColor(context, com.google.android.material.R.attr.colorOnSurfaceVariant, 0),
            )
            image.alpha = if (isEnabled) 1f else 0.38f
        }
        holder.itemView.alpha = 1f
        (holder.findViewById(android.R.id.title) as? TextView)?.alpha = if (isEnabled) 1f else 0.38f
    }

    companion object {
        /**
         * Makes [rows] one group: a tap on a row calls [onChoose] with its
         * value, and the row is checked if that returns true.
         */
        @JvmStatic
        fun group(rows: List<RadioRowPreference>, current: String?, onChoose: (String) -> Boolean) {
            for (row in rows) {
                row.isChecked = row.value == current
                row.setOnPreferenceClickListener {
                    if (!row.isChecked && onChoose(row.value)) {
                        for (other in rows) other.isChecked = other === row
                    }
                    true
                }
            }
        }
    }
}

/**
 * The heading of a list that opens and closes, as Chromium's
 * ExpandablePreferenceGroup ("Allowed", "Not Allowed"): title, number of
 * entries, arrow. The entries are the rows after it; the screen shows or hides them.
 */
class ExpandablePreference @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) :
    Preference(context, attrs) {

    var isExpanded: Boolean = true
        set(v) {
            if (field == v) return
            field = v
            notifyChanged()
        }

    init {
        widgetLayoutResource = R.layout.preference_expand_widget
        isPersistent = false
    }

    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        super.onBindViewHolder(holder)
        (holder.findViewById(R.id.expand_arrow) as? ImageView)?.let {
            it.rotation = if (isExpanded) 180f else 0f
            it.contentDescription = context.getString(
                if (isExpanded) R.string.settings_collapse else R.string.settings_expand,
            )
        }
    }
}

/**
 * Chromium's page zoom setting: "Default zoom" with the current value, minus
 * button, slider and plus button, and below it the preview text, which is
 * drawn in the chosen size.
 */
class PageZoomPreference @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) :
    Preference(context, attrs), PlainRow {

    /** Zoom in percent, [MIN]..[MAX]. */
    var zoom: Int = 100
        set(v) {
            val clamped = v.coerceIn(MIN, MAX)
            if (field == clamped) return
            field = clamped
            notifyChanged()
        }

    /** Called with the new zoom when the user changes it. */
    var onZoomChanged: ((Int) -> Unit)? = null

    @StringRes
    var titleRes: Int = R.string.cr_page_zoom_title

    init {
        layoutResource = R.layout.preference_page_zoom
        isSelectable = false
        isPersistent = false
    }

    private fun change(to: Int) {
        val clamped = to.coerceIn(MIN, MAX)
        if (clamped == zoom) return
        zoom = clamped
        onZoomChanged?.invoke(clamped)
    }

    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        super.onBindViewHolder(holder)
        holder.itemView.isClickable = false
        bind(holder.itemView, zoom, ::change)
        val scale = zoom / 100f
        (holder.findViewById(R.id.page_zoom_preview_large_text) as TextView).textSize = 18f * scale
        (holder.findViewById(R.id.page_zoom_preview_medium_text) as TextView).textSize = 15f * scale
        (holder.findViewById(R.id.page_zoom_preview_small_text) as TextView).textSize = 13f * scale
    }

    companion object {
        const val MIN = 50
        const val MAX = 300

        /** Chromium's zoom steps (HostZoomMap.AVAILABLE_ZOOM_FACTORS as percentages). */
        @JvmField
        val STEPS = intArrayOf(50, 67, 75, 80, 90, 100, 110, 125, 133, 150, 175, 200, 250, 300)

        @JvmStatic
        fun stepDown(zoom: Int): Int = STEPS.lastOrNull { it < zoom } ?: MIN

        @JvmStatic
        fun stepUp(zoom: Int): Int = STEPS.firstOrNull { it > zoom } ?: MAX

        /**
         * Fills Chromium's zoom row inside [root] (value text, minus, slider,
         * plus) with [zoom] and reports changes to [onChange].
         */
        @JvmStatic
        fun bind(root: View, zoom: Int, onChange: (Int) -> Unit) {
            val value = root.findViewById<TextView>(R.id.page_zoom_current_value_text)
            val slider = root.findViewById<Slider>(R.id.page_zoom_slider)
            val minus = root.findViewById<View>(R.id.page_zoom_decrease_zoom_button)
            val plus = root.findViewById<View>(R.id.page_zoom_increase_zoom_button)
            value.text = root.context.getString(R.string.cr_page_zoom_level, zoom)
            slider.clearOnChangeListeners()
            slider.valueFrom = MIN.toFloat()
            slider.valueTo = MAX.toFloat()
            slider.value = zoom.coerceIn(MIN, MAX).toFloat()
            slider.setLabelFormatter { root.context.getString(R.string.cr_page_zoom_level, it.roundToInt()) }
            slider.addOnChangeListener { _, v, fromUser ->
                if (fromUser) {
                    value.text = root.context.getString(R.string.cr_page_zoom_level, v.roundToInt())
                    onChange(v.roundToInt())
                }
            }
            minus.isEnabled = zoom > MIN
            plus.isEnabled = zoom < MAX
            minus.alpha = if (zoom > MIN) 1f else 0.38f
            plus.alpha = if (zoom < MAX) 1f else 0.38f
            minus.setOnClickListener { onChange(stepDown(zoom)) }
            plus.setOnClickListener { onChange(stepUp(zoom)) }
        }
    }
}

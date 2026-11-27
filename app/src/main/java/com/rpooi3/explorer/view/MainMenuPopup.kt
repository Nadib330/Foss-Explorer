package com.rpooi3.explorer.view

import androidx.core.graphics.drawable.toDrawable
import android.graphics.Color
import android.view.Gravity
import android.view.LayoutInflater
import android.view.Menu
import android.view.View
import android.view.ViewGroup
import android.widget.PopupWindow
import android.widget.TextView
import androidx.annotation.MenuRes
import androidx.appcompat.content.res.AppCompatResources
import androidx.appcompat.widget.PopupMenu
import androidx.core.view.ViewCompat
import androidx.core.view.forEach
import com.rpooi3.explorer.R

/**
 * The menu behind the three-dot button, drawn as a flat card with large
 * rounded corners (24dp) and no shadow.
 *
 * The entries come from a menu resource. [menu] gives access to them before
 * the menu is shown, e.g. to disable one. The platform's popup menu is not
 * used for drawing, because its list is not clipped to a rounded background:
 * with corners this large, the pressed state of the first and last entry
 * would stick out of them.
 */
class MainMenuPopup(
    private val anchor: View,
    @MenuRes menuRes: Int = 0,
    private val onItemSelected: (itemId: Int) -> Unit,
) {
    /** Programmatic-menu overload used by list context menus. */
    constructor(anchor: View, onItemSelected: (itemId: Int) -> Unit) :
        this(anchor, 0, onItemSelected)

    // Only the model: it parses the menu resource and is never shown itself.
    private val model = PopupMenu(anchor.context, anchor).apply {
        if (menuRes != 0) inflate(menuRes)
        setOnMenuItemClickListener { item ->
            // Not PopupMenu.dismiss(): that one is never shown.
            this@MainMenuPopup.dismiss()
            onItemSelected(item.itemId)
            true
        }
    }
    private var window: PopupWindow? = null

    /** The entries. Changes made after [show] do not appear until the menu is shown again. */
    val menu: Menu
        get() = model.menu

    /** The view of the open menu, or null while it is closed. */
    val contentView: View?
        get() = window?.contentView

    val isShowing: Boolean
        get() = window?.isShowing == true

    /** Called when the menu closes, for whatever reason. */
    var onDismiss: (() -> Unit)? = null

    /** Builds the menu view without opening a window. [show] uses it; it also serves previews and tests. */
    fun createView(parent: ViewGroup? = null): View {
        val inflater = LayoutInflater.from(anchor.context)
        val view = inflater.inflate(R.layout.popup_main_menu, parent, false)
        val list = view.findViewById<ViewGroup>(R.id.main_menu_items)
        menu.forEach { item ->
            if (!item.isVisible) return@forEach
            val row = inflater.inflate(R.layout.popup_main_menu_row, list, false) as TextView
            row.id = item.itemId
            row.text = item.title
            // A switch such as "Desktop site" carries its check box at the end of the row, as in Chrome.
            val box = when {
                !item.isCheckable -> null
                item.isChecked -> AppCompatResources.getDrawable(anchor.context, R.drawable.icon_checkbox_on)
                else -> AppCompatResources.getDrawable(anchor.context, R.drawable.icon_checkbox_off)
            }
            row.setCompoundDrawablesRelativeWithIntrinsicBounds(item.icon, null, box, null)
            if (item.isCheckable) {
                row.isSelected = item.isChecked
                row.stateDescriptionCompat(item.isChecked)
            }
            row.isEnabled = item.isEnabled
            row.alpha = if (item.isEnabled) 1f else DISABLED_ALPHA
            row.setOnClickListener { menu.performIdentifierAction(item.itemId, 0) }
            list.addView(row)
        }
        return view
    }

    fun show() {
        dismiss()
        val popup = PopupWindow(
            createView(),
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            true,
        )
        // A background (even an invisible one) is what makes a tap outside close the popup on every Android version.
        popup.setBackgroundDrawable(Color.TRANSPARENT.toDrawable())
        popup.isOutsideTouchable = true
        // Flat: neither the window nor the card casts a shadow.
        popup.elevation = 0f
        popup.setOnDismissListener {
            if (window === popup) window = null
            onDismiss?.invoke()
        }
        window = popup
        // Opens over the button, like Chrome's menu, instead of below it.
        popup.showAsDropDown(anchor, 0, -anchor.height, Gravity.END)
    }

    fun dismiss() {
        window?.dismiss()
    }

    private fun TextView.stateDescriptionCompat(checked: Boolean) {
        ViewCompat.setStateDescription(
            this,
            context.getString(if (checked) R.string.menu_state_on else R.string.menu_state_off),
        )
    }

    private companion object {
        const val DISABLED_ALPHA = 0.38f
    }
}

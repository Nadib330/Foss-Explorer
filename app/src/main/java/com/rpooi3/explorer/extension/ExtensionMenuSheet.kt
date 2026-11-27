/*
 * Explorer: the extension menu of a website.
 * Own code, GPL-3.0.
 *
 * The puzzle piece next to "Zoom" in the three-dot menu opens this sheet: the extensions that
 * are installed, each with its popup, and at the end the way to the extension settings and to
 * the Chrome Web Store. It is laid out like the main menu, because it is opened from it.
 */
package com.rpooi3.explorer.extension

import android.content.Context
import android.graphics.drawable.Drawable
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.isVisible
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.rpooi3.explorer.R

/**
 * The extensions of the site in front, as a sheet.
 *
 * @param url the page the menu was opened on, so a row can say whether it runs there
 * @param onOpenSettings the entry that leads to Settings > Extension
 * @param onOpenStore the entry that leads to the Chrome Web Store
 * @param onPopup what a tap on an extension does; the browser opens [ExtensionPopup]
 */
class ExtensionMenuSheet(
    private val context: Context,
    private val url: String?,
    private val onOpenSettings: () -> Unit,
    private val onOpenStore: () -> Unit,
    private val onPopup: (WebExtension) -> Unit,
    /**
     * 17.3.2: adding the extension of a Chrome Web Store page from here, which works whatever
     * the store's page does with its own button.
     */
    private val onAddFromPage: ((String) -> Unit)? = null,
) {

    private var dialog: BottomSheetDialog? = null

    val isShowing: Boolean get() = dialog?.isShowing == true

    /** The view of the open sheet, or null while it is closed. */
    val contentView: View? get() = dialog?.findViewById(R.id.extension_menu_sheet)

    /** What the sheet lists: the extensions that are turned on, and whether each runs here. */
    fun rows(): List<Pair<WebExtension, Boolean>> =
        ExtensionStore.enabled(context).map { it to it.worksOn(url) }

    fun show() {
        if (isShowing) return
        val sheet = BottomSheetDialog(context)
        val view = createView()
        sheet.setContentView(view)
        sheet.behavior.state = BottomSheetBehavior.STATE_EXPANDED
        sheet.behavior.skipCollapsed = true
        sheet.setOnDismissListener { if (dialog === sheet) dialog = null }
        dialog = sheet
        sheet.show()
        // The extension action menu follows the browser's flat menu treatment.
        sheet.window?.decorView?.elevation = 0f
        sheet.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)?.elevation = 0f
    }

    fun dismiss() {
        dialog?.dismiss()
    }

    /** Builds the sheet without showing it; the tests use it too. */
    fun createView(): View {
        val density = context.resources.displayMetrics.density
        val dp: (Int) -> Int = { value -> (value * density).toInt() }
        val inflater = LayoutInflater.from(context)

        val root = LinearLayout(context).apply {
            id = R.id.extension_menu_sheet
            orientation = LinearLayout.VERTICAL
        }
        root.addView(
            View(context).apply {
                layoutParams = LinearLayout.LayoutParams(dp(32), dp(4)).apply {
                    gravity = Gravity.CENTER_HORIZONTAL
                    topMargin = dp(8)
                    bottomMargin = dp(8)
                }
                setBackgroundResource(R.drawable.bg_sheet_handle)
            },
        )

        val groups = LinearLayout(context).apply {
            id = R.id.extension_menu_groups
            orientation = LinearLayout.VERTICAL
            setPadding(dp(8), dp(8), dp(8), dp(16))
        }
        root.addView(
            groups,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT),
        )

        // A page of the store for an extension that is not in yet: the way in that cannot fail.
        val pageId = CrxDownloader.extractExtensionId(url)
        if (url != null && StoreHook.isExtensionPage(url) && pageId != null &&
            ExtensionStore.find(context, pageId) == null && onAddFromPage != null
        ) {
            val page = addGroup(groups, dp)
            page.addView(
                row(inflater, R.drawable.icon_plus, context.getString(R.string.extensions_menu_add_page)) {
                    dismiss()
                    onAddFromPage.invoke(pageId)
                },
            )
        }

        val installed = rows()
        if (installed.isEmpty()) {
            // Nothing installed yet: the sheet says so and offers the store.
            val empty = addGroup(groups, dp)
            empty.addView(row(inflater, R.drawable.icon_extension, context.getString(R.string.extensions_none_in_menu), null))
        } else {
            val list = addGroup(groups, dp)
            installed.forEachIndexed { index, (extension, onThisSite) ->
                val view = row(inflater, extension, extension.name) { open(extension) }
                view.findViewById<TextView?>(R.id.menu_row_badge)?.let { badge ->
                    badge.isVisible = onThisSite
                    if (onThisSite) badge.text = context.getString(R.string.extensions_badge_site)
                }
                if (index > 0) {
                    (view.layoutParams as? LinearLayout.LayoutParams)?.topMargin =
                        context.resources.getDimensionPixelSize(R.dimen.menu_item_gap)
                }
                list.addView(view)
            }
        }

        // The two ways out of the sheet, in a group of their own as the main menu has them.
        val actions = addGroup(groups, dp)
        actions.addView(row(inflater, R.drawable.icon_settings, context.getString(R.string.extensions_settings)) {
            dismiss()
            onOpenSettings()
        })
        val store = row(inflater, R.drawable.icon_open_in_new, context.getString(R.string.extensions_open_store)) {
            dismiss()
            onOpenStore()
        }
        (store.layoutParams as? LinearLayout.LayoutParams)?.topMargin =
            context.resources.getDimensionPixelSize(R.dimen.menu_item_gap)
        actions.addView(store)
        return root
    }

    private fun open(extension: WebExtension) {
        dismiss()
        // An extension with a popup shows it; one without is set up in the settings.
        if (extension.popupUrl() != null) onPopup(extension) else onOpenSettings()
    }

    private fun addGroup(groups: LinearLayout, dp: (Int) -> Int): LinearLayout {
        val group = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundResource(R.drawable.bg_menu_group)
            clipToOutline = true
        }
        val params = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        )
        if (groups.childCount > 0) {
            params.topMargin = context.resources.getDimensionPixelSize(R.dimen.menu_group_gap)
        }
        groups.addView(group, params)
        return group
    }

    /** One entry: icon, label, and the badge that says it runs on this page. */
    private fun row(inflater: LayoutInflater, iconRes: Int, label: String, onClick: (() -> Unit)?): View {
        val view = inflater.inflate(R.layout.item_main_menu_row, null, false)
        view.findViewById<TextView>(R.id.menu_row_label).text = label
        view.findViewById<ImageView>(R.id.menu_row_icon).setImageResource(iconRes)
        view.setBackgroundResource(R.drawable.bg_menu_row)
        view.contentDescription = label
        if (onClick != null) {
            view.setOnClickListener { onClick.invoke() }
            view.isEnabled = true
        } else {
            view.isEnabled = false
            view.alpha = 1f
        }
        return view
    }

    /** A row of an extension: its own icon if the manifest named one, else the puzzle piece. */
    private fun row(
        inflater: LayoutInflater,
        extension: WebExtension,
        label: String,
        onClick: () -> Unit,
    ): View {
        val view = row(inflater, R.drawable.icon_extension, label, onClick)
        val icon = view.findViewById<ImageView>(R.id.menu_row_icon)
        val drawable = iconDrawable(extension)
        if (drawable != null) {
            icon.setImageDrawable(drawable)
            // The icon of an extension is a picture, not a tinted glyph.
            icon.imageTintList = null
        }
        return view
    }

    /** The icon as a bitmap, if the extension brought one. */
    private fun iconDrawable(extension: WebExtension): Drawable? =
        com.rpooi3.explorer.view.ExtensionIcons.drawable(context, extension)
}

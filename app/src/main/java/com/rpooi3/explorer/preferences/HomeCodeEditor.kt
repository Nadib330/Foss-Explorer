/*
 * Foss Explorer: the editor of the home page the user writes (18.0.0 beta 7, item 5).
 * Own code, GPL-3.0.
 */
package com.rpooi3.explorer.preferences

import android.app.Dialog
import android.content.Context
import android.view.View
import android.view.Window
import android.view.WindowManager
import android.widget.EditText
import androidx.core.content.edit
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.rpooi3.explorer.R
import com.rpooi3.explorer.home.HomeAppearance
import com.rpooi3.explorer.view.CatToast

/**
 * The field a home page is written in.
 *
 * It is a sheet over the whole screen — a page of code needs the room — with the code in a
 * monospace face, and two buttons: Save writes [HomeAppearance.KEY_CODE] and turns the user's page
 * on, Cancel throws the editing away. The code is stored exactly as it was typed: Explorer does not
 * tidy HTML, and what is saved is what the home page shows.
 */
object HomeCodeEditor {

    /** Opens the editor with what is stored, or with the template of a page that says so. */
    @JvmStatic
    @JvmOverloads
    fun show(context: Context, onSaved: Runnable? = null): Dialog {
        val sheet = BottomSheetDialog(context)
        val view = View.inflate(context, R.layout.dialog_home_code, null)
        val input = view.findViewById<EditText>(R.id.home_code_input)
        val prefs = HomeAppearance.prefs(context)
        input.setText(HomeAppearance.code(context) ?: HomeAppearance.codeTemplate(context))
        input.setSelection(0)

        view.findViewById<View>(R.id.home_code_cancel).setOnClickListener { sheet.dismiss() }
        view.findViewById<View>(R.id.home_code_save).setOnClickListener {
            prefs.edit {
                putString(HomeAppearance.KEY_CODE, input.text.toString())
                // Saving the code is the moment the user asks for it to be shown.
                putBoolean(HomeAppearance.KEY_CODE_ON, true)
            }
            sheet.dismiss()
            CatToast.show(context, R.string.home_code_saved, 1)
            onSaved?.run()
        }

        sheet.setContentView(view)
        sheet.show()
        // As tall as the screen: the sheet is opened whole, so the code has the room to be written.
        sheet.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)?.let { sheetView ->
            sheetView.layoutParams = sheetView.layoutParams.apply {
                height = android.view.ViewGroup.LayoutParams.MATCH_PARENT
            }
        }
        sheet.window?.let { window: Window ->
            window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        }
        return sheet
    }
}

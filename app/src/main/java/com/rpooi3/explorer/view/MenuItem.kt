package com.rpooi3.explorer.view

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes

/** An entry of the action sheet: its label and its icon. The icon also identifies the action. */
class MenuItem(
    @param:StringRes val titleRes: Int,
    @param:DrawableRes val iconResId: Int,
)

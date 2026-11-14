package com.rpooi3.explorer.view

/**
 * A titled entry of a grid or list dialog. [data] is an icon resource or a
 * filter id, depending on the dialog.
 *
 * The accessor names keep the Java record style (`title()`, `data()`) that the
 * remaining Java sources call.
 */
data class GridItem(
    @get:JvmName("title") val title: String?,
    @get:JvmName("data") val data: Int,
)

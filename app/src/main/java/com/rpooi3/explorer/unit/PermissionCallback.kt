package com.rpooi3.explorer.unit

fun interface PermissionCallback {
    fun onPermissionResult(isGranted: Boolean)
}

package com.rpooi3.explorer.unit

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat

object PermissionUtils {

    enum class PermissionType {
        LOCATION, CAMERA, MICROPHONE
    }

    @JvmStatic
    fun hasPermission(context: Context, type: PermissionType): Boolean =
        ContextCompat.checkSelfPermission(context, getManifestString(type)) ==
            PackageManager.PERMISSION_GRANTED

    @JvmStatic
    fun getManifestString(type: PermissionType): String = when (type) {
        PermissionType.LOCATION -> Manifest.permission.ACCESS_FINE_LOCATION
        PermissionType.CAMERA -> Manifest.permission.CAMERA
        PermissionType.MICROPHONE -> Manifest.permission.RECORD_AUDIO
    }
}

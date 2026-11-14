package com.rpooi3.explorer.site

import android.app.Activity
import android.webkit.GeolocationPermissions
import android.webkit.PermissionRequest
import androidx.annotation.StringRes
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.rpooi3.explorer.R
import com.rpooi3.explorer.activity.BrowserActivity
import com.rpooi3.explorer.unit.PermissionCallback

/**
 * Answers a site that wants to use something it has to ask for: from the site
 * settings where they decide, otherwise by asking the user, as Chromium does
 * ("example.org wants to use your camera — Block / Allow").
 */
object SitePermissions {
    /**
     * Calls [callback] with whether [origin] may use [category].
     *
     * @param remember whether the user's answer is kept for the site. Private
     *   tabs pass false: their answers hold for this one request.
     */
    @JvmStatic
    fun request(
        activity: Activity,
        category: SiteCategory,
        origin: String?,
        remember: Boolean,
        callback: PermissionCallback,
    ) {
        when (SiteSettings.state(activity, category, origin)) {
            SiteSettings.State.ALLOW -> callback.onPermissionResult(true)
            SiteSettings.State.BLOCK -> callback.onPermissionResult(false)
            SiteSettings.State.ASK -> ask(activity, category, origin, remember, callback)
        }
    }

    private fun ask(
        activity: Activity,
        category: SiteCategory,
        origin: String?,
        remember: Boolean,
        callback: PermissionCallback,
    ) {
        val site = SiteSettings.site(origin)
        if (site.isEmpty() || activity.isFinishing || activity.isDestroyed) {
            callback.onPermissionResult(false)
            return
        }
        var answered = false
        fun answer(allowed: Boolean, store: Boolean) {
            if (answered) return
            answered = true
            if (store && remember) SiteSettings.setException(SiteSettings.prefs(activity), category, site, allowed)
            callback.onPermissionResult(allowed)
        }
        MaterialAlertDialogBuilder(activity)
            .setIcon(category.icon)
            .setMessage(activity.getString(question(category), site))
            .setPositiveButton(R.string.site_prompt_allow) { _, _ -> answer(allowed = true, store = true) }
            .setNegativeButton(R.string.site_prompt_block) { _, _ -> answer(allowed = false, store = true) }
            // Closing the question without an answer refuses this once and decides nothing.
            .setOnDismissListener { answer(allowed = false, store = false) }
            .show()
    }

    @StringRes
    private fun question(category: SiteCategory): Int = when (category) {
        SiteCategory.LOCATION -> R.string.site_prompt_location
        SiteCategory.CAMERA -> R.string.site_prompt_camera
        SiteCategory.MICROPHONE -> R.string.site_prompt_microphone
        SiteCategory.AUTOMATIC_DOWNLOADS -> R.string.site_prompt_downloads
        SiteCategory.PROTECTED_CONTENT -> R.string.site_prompt_protected
        else -> throw IllegalArgumentException("Sites cannot ask for $category")
    }

    /**
     * A page asks for camera, microphone or protected content. The site
     * settings (and the user) decide first; Android's own permission is asked
     * for only when the site is allowed.
     */
    @JvmStatic
    fun handle(activity: BrowserActivity, request: PermissionRequest, remember: Boolean) {
        val origin = request.origin?.toString()
        val wanted = request.resources.orEmpty().toList()

        if (PermissionRequest.RESOURCE_PROTECTED_MEDIA_ID in wanted) {
            // Allowed, blocked, or (with "Sites can ask to play protected content") asked about.
            request(activity, SiteCategory.PROTECTED_CONTENT, origin, remember) { allowed ->
                if (allowed) request.grant(arrayOf(PermissionRequest.RESOURCE_PROTECTED_MEDIA_ID))
                else request.deny()
            }
            return
        }

        val granted = ArrayList<String>()
        fun finish() {
            if (granted.isEmpty()) request.deny() else request.grant(granted.toTypedArray())
        }
        fun microphone() {
            if (PermissionRequest.RESOURCE_AUDIO_CAPTURE !in wanted) return finish()
            request(activity, SiteCategory.MICROPHONE, origin, remember) { site ->
                if (!site) return@request finish()
                activity.requestMicrophonePermission { system ->
                    if (system) granted.add(PermissionRequest.RESOURCE_AUDIO_CAPTURE)
                    finish()
                }
            }
        }
        if (PermissionRequest.RESOURCE_VIDEO_CAPTURE !in wanted) return microphone()
        request(activity, SiteCategory.CAMERA, origin, remember) { site ->
            if (!site) return@request microphone()
            activity.requestCameraPermission { system ->
                if (system) granted.add(PermissionRequest.RESOURCE_VIDEO_CAPTURE)
                microphone()
            }
        }
    }

    /** A page asks where the device is. */
    @JvmStatic
    fun handleGeolocation(
        activity: BrowserActivity,
        origin: String?,
        remember: Boolean,
        callback: GeolocationPermissions.Callback,
    ) {
        request(activity, SiteCategory.LOCATION, origin, remember) { site ->
            if (!site) return@request callback.invoke(origin, false, false)
            activity.requestLocationPermission { system -> callback.invoke(origin, system, false) }
        }
    }
}

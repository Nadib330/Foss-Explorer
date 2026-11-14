package com.rpooi3.explorer.site

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.rpooi3.explorer.R

/**
 * One row of Settings > Site settings, in the order and grouping of Chromium
 * for Android, with the texts of the category's own screen.
 *
 * @property key name in the stored settings and in the row's key (`site_<key>`)
 * @property profileKeys suffixes of the profile settings that hold the general
 *   choice (`<profile>_<suffix>`); empty if the category has a setting of its own
 * @property defaultOn the general choice when nothing is stored
 * @property description the text at the top of the category's screen
 * @property onText, offText the two choices of the screen
 * @property offDescription what the second choice costs, shown below it; 0 if Chromium has none
 * @property addAllowText, addBlockText the text of the "Add a site" dialog; 0 for Chromium's general one
 */
enum class SiteCategory(
    val key: String,
    val group: Group,
    val kind: Kind,
    @StringRes val title: Int,
    @DrawableRes val icon: Int,
    val profileKeys: List<String>,
    val defaultOn: Boolean,
    @StringRes val description: Int,
    @StringRes val onText: Int,
    @StringRes val offText: Int,
    @StringRes val offDescription: Int = 0,
    @StringRes val addAllowText: Int = 0,
    @StringRes val addBlockText: Int = 0,
) {
    LOCATION(
        "location", Group.PERMISSIONS, Kind.ASK, R.string.site_location, R.drawable.icon_location,
        listOf("_location"), true,
        R.string.cr_site_location_description, R.string.cr_site_location_on, R.string.cr_site_location_off,
    ),
    CAMERA(
        "camera", Group.PERMISSIONS, Kind.ASK, R.string.site_camera, R.drawable.icon_camera,
        listOf("_camera"), true,
        R.string.cr_site_camera_description, R.string.cr_site_camera_on, R.string.cr_site_camera_off,
        R.string.cr_site_camera_off_description,
    ),
    MICROPHONE(
        "microphone", Group.PERMISSIONS, Kind.ASK, R.string.site_microphone, R.drawable.icon_microphone,
        listOf("_microphone"), true,
        R.string.cr_site_mic_description, R.string.cr_site_mic_on, R.string.cr_site_mic_off,
        R.string.cr_site_mic_off_description,
    ),
    APPS_ON_DEVICE(
        "apps_on_device", Group.PERMISSIONS, Kind.ALLOW, R.string.site_apps_on_device, R.drawable.icon_apps,
        emptyList(), false,
        // Chromium lets a site ask; Explorer cannot ask in the middle of a request, so it allows or blocks.
        R.string.cr_site_loopback_description, R.string.site_apps_on_device_on, R.string.cr_site_loopback_off,
    ),
    THIRD_PARTY_COOKIES(
        "third_party_cookies", Group.CONTENT, Kind.ALLOW, R.string.site_third_party_cookies, R.drawable.icon_cookie,
        listOf("_cookiesThirdParty"), false,
        R.string.cr_site_cookies_description, R.string.cr_site_cookies_on, R.string.cr_site_cookies_off,
        R.string.cr_site_cookies_off_description, R.string.cr_site_cookies_add,
    ),
    JAVASCRIPT(
        "javascript", Group.CONTENT, Kind.ALLOW, R.string.site_javascript, R.drawable.icon_java,
        listOf("_javascript"), true,
        R.string.cr_site_javascript_description, R.string.cr_site_javascript_on, R.string.cr_site_javascript_off,
        0, R.string.cr_site_javascript_add_allow, R.string.cr_site_javascript_add_block,
    ),
    POPUPS(
        "popups", Group.CONTENT, Kind.ALLOW, R.string.site_popups, R.drawable.icon_popup,
        listOf("_javascriptPopUp"), false,
        R.string.cr_site_popups_description, R.string.cr_site_popups_on, R.string.cr_site_popups_off,
    ),
    SOUND(
        "sound", Group.CONTENT, Kind.ALLOW, R.string.site_sound, R.drawable.icon_audio,
        emptyList(), true,
        R.string.cr_site_sound_description, R.string.cr_site_sound_on, R.string.cr_site_sound_off,
        R.string.cr_site_sound_off_description, R.string.cr_site_sound_add_allow, R.string.cr_site_sound_add_block,
    ),
    ADS(
        "ads", Group.CONTENT, Kind.ALLOW, R.string.site_ads, R.drawable.icon_adblock,
        // The profile setting says "block ads", the choice says "allow ads".
        listOf("_adBlock"), false,
        // Chromium blocks ads on sites known for intrusive ads; Explorer blocks by filter list, and says so.
        R.string.cr_site_ads_description, R.string.cr_site_ads_on, R.string.site_ads_off,
    ),
    PROTECTED_CONTENT(
        "protected_content", Group.CONTENT, Kind.ALLOW, R.string.site_protected_content, R.drawable.icon_protected,
        listOf("_drm"), true,
        R.string.cr_site_protected_description, R.string.cr_site_protected_allow, R.string.cr_site_protected_block,
    ),
    SITE_DATA(
        "site_data", Group.CONTENT, Kind.ALLOW, R.string.site_site_data, R.drawable.icon_database,
        listOf("_cookies", "_dom"), true,
        R.string.cr_site_data_description, R.string.cr_site_data_on, R.string.cr_site_data_off,
        0, R.string.cr_site_data_add_allow, R.string.cr_site_data_add_block,
    ),
    AUTOMATIC_DOWNLOADS(
        "automatic_downloads", Group.CONTENT, Kind.ASK, R.string.site_automatic_downloads, R.drawable.icon_download,
        emptyList(), true,
        R.string.cr_site_downloads_description, R.string.cr_site_downloads_on, R.string.cr_site_downloads_off,
        0, R.string.cr_site_downloads_add,
    );

    enum class Group { PERMISSIONS, CONTENT }

    enum class Kind {
        /** First choice: a site is asked about when it wants this; second: blocked. Sites answered once are listed. */
        ASK,

        /** First choice: allowed; second: blocked. Single sites can be set the other way. */
        ALLOW,

        /** Android System WebView does not offer the feature to sites, so there is nothing to choose. */
        UNAVAILABLE,
    }

    /** Key of the row on the Site settings screen. */
    val rowKey: String get() = "site_$key"

    /** Whether the profile setting is stored the other way round than the choice shows it. */
    internal val inverted: Boolean get() = this == ADS

    /**
     * Chromium's picture for the first choice of the category's screen. Where
     * this is null for [offIcon], the picture for "on" is shown crossed out.
     */
    @get:DrawableRes
    val onIcon: Int
        get() = when (this) {
            LOCATION -> R.drawable.cr_location_on_fill
            CAMERA -> R.drawable.cr_videocam_fill
            MICROPHONE -> R.drawable.cr_gm_filled_mic
            THIRD_PARTY_COOKIES -> R.drawable.cr_permission_cookie
            JAVASCRIPT -> R.drawable.cr_code
            POPUPS -> R.drawable.icon_open_in_new
            SOUND -> R.drawable.cr_volume_up_fill
            ADS -> R.drawable.icon_web_asset
            SITE_DATA -> R.drawable.cr_database_fill
            AUTOMATIC_DOWNLOADS -> R.drawable.cr_download
            else -> icon
        }

    /** Chromium's picture for the choice that switches the category off; null if it has none here. */
    val offIcon: Int?
        get() = when (this) {
            LOCATION -> R.drawable.cr_filled_location_off
            CAMERA -> R.drawable.cr_filled_videocam_off
            MICROPHONE -> R.drawable.cr_filled_mic_off
            JAVASCRIPT -> R.drawable.cr_code_off
            POPUPS -> R.drawable.cr_open_in_new_off
            SOUND -> R.drawable.cr_volume_off_fill
            ADS -> R.drawable.cr_ad_off
            SITE_DATA -> R.drawable.cr_filled_database_off
            AUTOMATIC_DOWNLOADS -> R.drawable.cr_file_download_off
            else -> null
        }

    /** Whether the screen has a third choice between the two: "Sites can ask" (Chromium: protected content). */
    val hasAskChoice: Boolean get() = this == PROTECTED_CONTENT

    companion object {
        @JvmStatic
        fun byKey(key: String?): SiteCategory? = entries.firstOrNull { it.key == key }
    }
}

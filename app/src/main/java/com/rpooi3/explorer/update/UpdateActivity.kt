/*
 * Explorer: the version page, and the manual check for a new one.
 * Own code, GPL-3.0.
 *
 * 18.3.0 beta (item 15): the second screenshot. The name of the browser, the version that is
 * installed under it, whether that is the newest one, and the two buttons of the bottom of the
 * page — with the GitHub one in place of the "Terms and conditions" of the screenshot. The back
 * arrow and the ⓘ sit where they sit there, and everything takes its colours from Explorer's theme.
 *
 * 18.3.0 beta (item 16): a check is shown the way an extension that is being added is shown — the
 * "Please wait" of [InstallWait], a turning circle in a small round card (18.0.0 beta 8, item 7).
 */
package com.rpooi3.explorer.update

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.rpooi3.explorer.R
import com.rpooi3.explorer.extension.InstallWait
import com.rpooi3.explorer.preferences.SettingsSections
import com.rpooi3.explorer.theme.ExplorerTheme
import com.rpooi3.explorer.unit.BrowserUnit
import com.rpooi3.explorer.view.CatToast
import androidx.core.net.toUri

class UpdateActivity : AppCompatActivity() {

    private lateinit var status: TextView
    private lateinit var version: TextView
    private var updateButton: MaterialButton? = null
    private var checking = false

    override fun onCreate(savedInstanceState: Bundle?) {
        ExplorerTheme.applyTo(this)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_update)
        findViewById<View>(R.id.update_back).setOnClickListener { finish() }
        // The circular information button is the Android application-info affordance, not another
        // in-app explanation dialog. Use Explorer's package URI so Android opens this app's page.
        findViewById<View>(R.id.update_info).setOnClickListener { openApplicationInfo() }
        version = findViewById(R.id.update_version)
        status = findViewById(R.id.update_status)
        updateButton = findViewById(R.id.update_action)

        val name = getString(R.string.app_name)
        findViewById<TextView>(R.id.update_title).text = name
        version.text = getString(R.string.update_version, SettingsSections.versionName(this))
        // item 15: the GitHub button where the screenshot has "Terms and conditions".
        findViewById<MaterialButton>(R.id.update_github).setOnClickListener {
            BrowserUnit.intentURL(this, SettingsSections.URL_SOURCE.toUri())
        }
        findViewById<MaterialButton>(R.id.update_licenses).setOnClickListener {
            // Reuse Explorer's existing legal-information screen; it contains the bundled
            // open-source licence texts used by Settings > About.
            startActivity(
                android.content.Intent(this, com.rpooi3.explorer.activity.Settings_Activity::class.java)
                    .putExtra(
                        com.rpooi3.explorer.activity.Settings_Activity.EXTRA_SECTION,
                        "section_legal",
                    ),
            )
        }
        updateButton?.setOnClickListener { onUpdateTapped() }
        show()
        // The page asks by itself, as Chromium's does when it is opened.
        check(manual = true)
    }

    override fun onResume() {
        super.onResume()
        com.rpooi3.explorer.downloads.ApkInstaller.installPending(this)
        show()
    }

    private fun show() {
        status.text = Updater.statusText(this)
        val newer = Updater.hasUpdate(this)
        updateButton?.visibility = if (newer) View.VISIBLE else View.GONE
        updateButton?.text = Updater.downloaded(this)?.let { getString(R.string.update_install) }
            ?: Updater.availableVersion(this)?.let { getString(R.string.update_download, it) }
            ?: getString(R.string.update_download_short)
    }

    /** The check itself, with the "Please wait" of an extension install over the page (item 16). */
    private fun check(manual: Boolean) {
        if (checking) return
        checking = true
        InstallWait.show(this, getString(R.string.update_checking))
        Updater.check(this) { result ->
            checking = false
            InstallWait.dismiss()
            if (isFinishing || isDestroyed) return@check
            show()
            if (result is Updater.Result.Failed && manual) {
                MaterialAlertDialogBuilder(this)
                    .setTitle(R.string.update_failed_title)
                    .setMessage(getString(R.string.update_failed_message, result.reason))
                    .setPositiveButton(R.string.app_ok, null)
                    .show()
            }
        }
    }

    private fun onUpdateTapped() {
        if (Updater.downloaded(this) != null) {
            Updater.install(this)
            return
        }
        if (Updater.runningDownload(this) != null) {
            CatToast.show(this, R.string.update_downloading)
            return
        }
        if (Updater.download(this) != null) {
            CatToast.show(this, R.string.update_download_started)
            show()
        }
    }

    /** Opens Explorer's entry in Android Settings > Apps > Explorer. */
    private fun openApplicationInfo() {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            .setData(Uri.parse("package:$packageName"))
        try {
            startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            // AOSP and supported Android builds provide this screen; keep the button harmless on
            // unusual devices that do not expose an application-details activity.
        }
    }

    companion object {
        /** Where the version page is opened from: Settings > About, and the menu. */
        @JvmStatic
        fun open(activity: android.app.Activity) {
            try {
                activity.startActivity(android.content.Intent(activity, UpdateActivity::class.java))
            } catch (e: ActivityNotFoundException) {
                // Nothing to open; the row stays silent rather than crashing the settings.
            }
        }
    }
}

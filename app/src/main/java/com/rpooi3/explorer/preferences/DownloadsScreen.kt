/*
 * Explorer: Settings > Downloads.
 * Own code, GPL-3.0.
 *
 * 18.3.0 beta (item 12): the tile under Site settings. It says where the browser puts the files
 * — the browser's own Download folder unless the user picks another one — and leads to the
 * downloads screen. The look follows Chromium's downloads settings: a row with the folder, a row
 * to go back to the default, and the one switch of the section.
 */
package com.rpooi3.explorer.preferences

import android.content.Intent
import android.net.Uri
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.rpooi3.explorer.R
import com.rpooi3.explorer.downloads.DownloadDirs
import com.rpooi3.explorer.downloads.DownloadsActivity
import com.rpooi3.explorer.view.CatToast
import java.util.WeakHashMap

object DownloadsScreen {

    const val KEY_LOCATION = "download_location"
    const val KEY_RESET = "download_location_reset"
    const val KEY_SHOW_ALL = "download_open_list"
    const val KEY_NOTIFICATION = "download_notification"

    /** The picker of a folder, one per live settings fragment. */
    private val pickers = WeakHashMap<PreferenceFragmentCompat, ActivityResultLauncher<Intent>>()
    private var picker: ActivityResultLauncher<Intent>? = null
    private var host: PreferenceFragmentCompat? = null

    @JvmStatic
    fun bind(fragment: PreferenceFragmentCompat) {
        val location = fragment.findPreference<Preference>(KEY_LOCATION) ?: return
        host = fragment
        // A PreferenceFragment is recreated when the settings theme changes. The launcher must
        // belong to that fragment, not to the fragment that was just destroyed; conversely, the
        // same fragment may be rebound from onResume, so it must not be registered twice after it
        // has reached STARTED.
        val launcher = pickers[fragment] ?: fragment.registerForActivityResult(
            ActivityResultContracts.StartActivityForResult(),
        ) { result ->
            val uri = result.data?.data
            if (result.resultCode == android.app.Activity.RESULT_OK && uri != null) {
                val context = fragment.context ?: return@registerForActivityResult
                // The permission is kept, so that a download may be written there later on.
                runCatching {
                    context.contentResolver.takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                    )
                }
                DownloadDirs.setTree(context, uri)
                refresh(fragment)
            }
        }.also { pickers[fragment] = it }
        picker = launcher
        host = fragment
        location.setOnPreferenceClickListener {
            MaterialAlertDialogBuilder(fragment.requireContext())
                .setTitle(R.string.download_location)
                .setMessage(DownloadDirs.path(fragment.requireContext()))
                .setNegativeButton(R.string.app_cancel, null)
                .setNeutralButton(R.string.download_location_reset) { _, _ ->
                    DownloadDirs.setTree(fragment.requireContext(), null)
                    refresh(fragment)
                }
                .setPositiveButton(R.string.download_location_choose) { _, _ -> chooseFolder() }
                .show()
            true
        }
        fragment.findPreference<Preference>(KEY_RESET)?.setOnPreferenceClickListener {
            DownloadDirs.setTree(fragment.requireContext(), null)
            CatToast.show(fragment.requireContext(), R.string.download_location_default_done)
            refresh(fragment)
            true
        }
        fragment.findPreference<Preference>(KEY_SHOW_ALL)?.setOnPreferenceClickListener {
            fragment.startActivity(Intent(fragment.requireContext(), DownloadsActivity::class.java))
            true
        }
        refresh(fragment)
    }

    /** Opens Android's own picker for a folder. */
    @JvmStatic
    fun chooseFolder() {
        val launcher = picker ?: return
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
            addFlags(
                Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
                    Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION,
            )
        }
        runCatching { launcher.launch(intent) }
    }

    /** The row says where the files go now, and whether it is the folder of Explorer. */
    @JvmStatic
    fun refresh(fragment: PreferenceFragmentCompat) {
        val context = fragment.context ?: return
        fragment.findPreference<Preference>(KEY_LOCATION)?.summary = DownloadDirs.path(context)
        fragment.findPreference<Preference>(KEY_RESET)?.isEnabled = DownloadDirs.tree(context) != null
    }

    /** The path in a row of its own, for the tests: `/storage/emulated/0/Android/data/...`. */
    @JvmStatic
    fun pathOf(context: android.content.Context): String = DownloadDirs.path(context)

    /** A small view that shows a path the way Chromium's settings show it. */
    @JvmStatic
    fun pathView(context: android.content.Context, path: String): View =
        TextView(context).apply {
            text = path
            textSize = 14f
            setPadding(0, 0, 0, 0)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            )
        }

    private fun Uri.asText(): String = toString()
}

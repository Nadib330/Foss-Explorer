/*
 * Explorer: the built-in update mechanism.
 * Own code, GPL-3.0.
 *
 * 18.3.0 beta (items 13 to 16). Chromite (https://github.com/uazo/cromite) updates itself, and so
 * does Explorer now: the releases of the project are read from GitHub, the newest one is compared
 * with the version that is installed, the package of the newest one is downloaded with Explorer's
 * own download manager, and Android's installer puts it in place. No store, no account, nothing in
 * between.
 *
 * What is where:
 *
 *  * [check] reads `https://api.github.com/repos/Nadib330/Foss-Explorer/releases` and keeps the
 *    answer in the preferences, so that the three-dot menu can show "Update Explorer" without
 *    asking GitHub again on every start (item 14);
 *  * [download] fetches the `.apk` of the release into the download folder, and [install] hands it
 *    to the installer (item 13);
 *  * [UpdateActivity] is the page of the second screenshot — the name, the version, whether it is
 *    the newest one, and the buttons under it (item 15);
 *  * the "Please wait" of a check is the one an extension install shows (item 16, [InstallWait]).
 */
package com.rpooi3.explorer.update

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.edit
import androidx.preference.PreferenceManager
import com.rpooi3.explorer.downloads.ApkInstaller
import com.rpooi3.explorer.downloads.DownloadDirs
import com.rpooi3.explorer.downloads.DownloadEngine
import com.rpooi3.explorer.downloads.DownloadStore
import com.rpooi3.explorer.downloads.DownloadTask
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

object Updater {

    /** Where the releases of Explorer are. */
    const val RELEASES_API = "https://api.github.com/repos/Nadib330/Foss-Explorer/releases"
    const val REPOSITORY = "https://github.com/Nadib330/Foss-Explorer"
    /** Chromite's release shape: the latest page redirects without requiring a GitHub API token. */
    const val LATEST_APK = "$REPOSITORY/releases/latest/download/app-release.apk"

    /** The newest release GitHub knows of. */
    data class Release(
        val tag: String,
        val version: String,
        val name: String,
        val notes: String,
        val pageUrl: String,
        val apkUrl: String?,
        val publishedAt: Long,
        /** True for a release GitHub marks as a pre-release: every beta of Explorer is one. */
        val preRelease: Boolean,
    )

    /** What a check found. */
    sealed class Result {
        data class Found(val release: Release, val newer: Boolean) : Result()
        data class Failed(val reason: String) : Result()
    }

    // --- the preferences the answer is kept in ------------------------------------------------

    private const val KEY_VERSION = "update_version"
    private const val KEY_URL = "update_url"
    private const val KEY_PAGE = "update_page"
    private const val KEY_NOTES = "update_notes"
    private const val KEY_CHECKED = "update_checked_at"
    private const val KEY_FAILED = "update_last_error"
    private const val KEY_AUTO = "update_check_automatic"
    private const val KEY_LAST_CHECK = "update_last_auto_check"
    private const val KEY_NOTIFIED_VERSION = "update_notified_version"
    private const val CHANNEL = "updates"
    private const val NOTIFICATION_ID = 4_712

    /** How long an automatic check waits before it asks GitHub again. */
    private const val AUTO_INTERVAL_MS = 12 * 60 * 60 * 1000L

    // The updater must not keep the browser process alive when Android is closing it.
    private val executor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "explorer-update-check").apply { isDaemon = true }
    }

    // Update state belongs to the installation, not to whichever browser profile is in front.
    private fun prefs(context: Context) = PreferenceManager.getDefaultSharedPreferences(context.applicationContext)

    /** The version of the browser that is installed, e.g. `18.3.0-beta`. */
    @JvmStatic
    fun installedVersion(context: Context): String = try {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty()
    } catch (e: Exception) {
        ""
    }

    /** The version a check found, if one was found. */
    @JvmStatic
    fun availableVersion(context: Context): String? =
        prefs(context).getString(KEY_VERSION, null)?.takeIf { it.isNotBlank() }

    /** The address of the package of that version. */
    @JvmStatic
    fun availableUrl(context: Context): String? =
        prefs(context).getString(KEY_URL, null)?.takeIf { it.isNotBlank() }

    /** Whether a newer version than the installed one is known. */
    @JvmStatic
    fun hasUpdate(context: Context): Boolean {
        val found = availableVersion(context) ?: return false
        return isNewer(found, installedVersion(context)) && availableUrl(context) != null
    }

    /** When GitHub was last asked, in milliseconds. */
    @JvmStatic
    fun lastChecked(context: Context): Long = prefs(context).getLong(KEY_CHECKED, 0L)

    /** The reason of the last failed check, if the last one failed. */
    @JvmStatic
    fun lastError(context: Context): String? =
        prefs(context).getString(KEY_FAILED, null)?.takeIf { it.isNotBlank() }

    /** Whether Explorer looks for a new version by itself. */
    @JvmStatic
    fun isAutomatic(context: Context): Boolean = prefs(context).getBoolean(KEY_AUTO, true)

    @JvmStatic
    fun setAutomatic(context: Context, automatic: Boolean) {
        prefs(context).edit { putBoolean(KEY_AUTO, automatic) }
    }

    // --- the check -----------------------------------------------------------------------------

    /**
     * Reads the releases of Explorer from GitHub. Not on the main thread: [callback] is called
     * there when the answer is in.
     */
    @JvmStatic
    @JvmOverloads
    fun check(context: Context, callback: ((Result) -> Unit)? = null) {
        val app = context.applicationContext
        executor.execute {
            val result = try {
                fetch(app)
            } catch (e: Exception) {
                Log.w(TAG, "GitHub could not be asked: $e")
                Result.Failed(e.message ?: e.javaClass.simpleName)
            }
            android.os.Handler(android.os.Looper.getMainLooper()).post { callback?.invoke(result) }
        }
    }

    /** A check that runs by itself at the start, at most every twelve hours. */
    @JvmStatic
    fun checkInBackground(context: Context) {
        val app = context.applicationContext
        if (!isAutomatic(app)) return
        val now = System.currentTimeMillis()
        if (now - prefs(app).getLong(KEY_LAST_CHECK, 0L) < AUTO_INTERVAL_MS) return
        prefs(app).edit { putLong(KEY_LAST_CHECK, now) }
        check(app, null)
    }

    /** Asks GitHub and writes the answer down. Runs off the main thread. */
    private fun fetch(context: Context): Result {
        val connection = (URL(RELEASES_API).openConnection() as HttpURLConnection).apply {
            connectTimeout = 20_000
            readTimeout = 20_000
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("User-Agent", "Explorer/" + installedVersion(context))
        }
        try {
            val code = connection.responseCode
            if (code !in 200..299) {
                // GitHub's unauthenticated API is rate-limited. Cromite does not need the API: its
                // latest APK endpoint gives the same tag through the release redirect, so use it
                // as the safe fallback rather than telling the user no update exists.
                return fetchLatestPage(context, "HTTP $code")
            }
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            val release = newest(JSONArray(body)) ?: return fetchLatestPage(context, "no release")
            val installed = installedVersion(context)
            val newer = isNewer(release.version, installed)
            prefs(context).edit {
                putString(KEY_VERSION, release.version)
                putString(KEY_URL, release.apkUrl)
                putString(KEY_PAGE, release.pageUrl)
                putString(KEY_NOTES, release.notes)
                putLong(KEY_CHECKED, System.currentTimeMillis())
                remove(KEY_FAILED)
            }
            if (newer) notifyAvailable(context, release)
            return Result.Found(release, newer)
        } finally {
            runCatching { connection.disconnect() }
        }
    }

    /**
     * Cromite's update indication is deliberately quiet: one low-priority notice for a release,
     * and the update tile in the three-dot menu. It never downloads until the user taps the page.
     */
    private fun notifyAvailable(context: Context, release: Release) {
        val preferences = prefs(context)
        if (preferences.getString(KEY_NOTIFIED_VERSION, null) == release.version) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
            if (manager.getNotificationChannel(CHANNEL) == null) {
                manager.createNotificationChannel(
                    NotificationChannel(
                        CHANNEL,
                        context.getString(com.rpooi3.explorer.R.string.update_notification_title),
                        NotificationManager.IMPORTANCE_LOW,
                    ).apply {
                        description = context.getString(com.rpooi3.explorer.R.string.update_notification_text, release.version)
                        setShowBadge(true)
                    },
                )
            }
        }
        val open = PendingIntent.getActivity(
            context,
            NOTIFICATION_ID,
            Intent(context, UpdateActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle(context.getString(com.rpooi3.explorer.R.string.update_notification_title))
            .setContentText(context.getString(com.rpooi3.explorer.R.string.update_notification_text, release.version))
            .setContentIntent(open)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .build()
        try {
            val manager = NotificationManagerCompat.from(context)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !manager.areNotificationsEnabled()) return
            manager.notify(NOTIFICATION_ID, notification)
            preferences.edit { putString(KEY_NOTIFIED_VERSION, release.version) }
        } catch (_: SecurityException) {
            // Android 13+: do not remember a notice that could not be delivered. A later check can
            // show it after the user allows notifications in Settings.
        }
    }

    /**
     * Fallback corresponding to Chromite's HEAD/redirect check. It avoids the GitHub API quota and
     * keeps the release package on the exact repository/tag rather than accepting a random APK.
     */
    private fun fetchLatestPage(context: Context, failure: String): Result {
        return try {
            val connection = (URL("$REPOSITORY/releases/latest").openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = true
                connectTimeout = 20_000
                readTimeout = 20_000
                setRequestProperty("User-Agent", "Explorer/" + installedVersion(context))
            }
            try {
                if (connection.responseCode !in 200..299) throw IOException("$failure; HTTP ${connection.responseCode}")
                val path = connection.url.path
                val tag = path.substringAfterLast("/tag/", "").takeIf { it.isNotBlank() }
                    ?: throw IOException("release tag missing")
                val version = versionOf(tag)
                val release = Release(
                    tag = tag,
                    version = version,
                    name = "Explorer $version",
                    notes = "",
                    pageUrl = "$REPOSITORY/releases/tag/$tag",
                    apkUrl = "$REPOSITORY/releases/download/$tag/app-release.apk",
                    publishedAt = 0L,
                    preRelease = version.contains('-'),
                )
                val newer = isNewer(version, installedVersion(context))
                prefs(context).edit {
                    putString(KEY_VERSION, version)
                    putString(KEY_URL, release.apkUrl)
                    putString(KEY_PAGE, release.pageUrl)
                    putString(KEY_NOTES, release.notes)
                    putLong(KEY_CHECKED, System.currentTimeMillis())
                    remove(KEY_FAILED)
                }
                if (newer) notifyAvailable(context, release)
                Result.Found(release, newer)
            } finally {
                runCatching { connection.disconnect() }
            }
        } catch (e: Exception) {
            val reason = e.message ?: failure
            prefs(context).edit { putString(KEY_FAILED, reason).putLong(KEY_CHECKED, System.currentTimeMillis()) }
            Result.Failed(reason)
        }
    }

    /** The newest release with a package, pre-releases (the betas of Explorer) included. */
    private fun newest(releases: JSONArray): Release? {
        var best: Release? = null
        for (index in 0 until releases.length()) {
            val json = releases.optJSONObject(index) ?: continue
            if (json.optBoolean("draft", false)) continue
            val release = releaseOf(json) ?: continue
            if (release.apkUrl == null) continue
            if (best == null || isNewer(release.version, best.version)) best = release
        }
        return best
    }

    private fun releaseOf(json: JSONObject): Release? {
        val tag = json.optString("tag_name").ifBlank { json.optString("name") }.trim()
        if (tag.isEmpty()) return null
        var apk: String? = null
        val assets = json.optJSONArray("assets")
        if (assets != null) {
            var anyApk: String? = null
            for (index in 0 until assets.length()) {
                val asset = assets.optJSONObject(index) ?: continue
                val name = asset.optString("name")
                if (!name.endsWith(".apk", ignoreCase = true)) continue
                val url = asset.optString("browser_download_url").takeIf { it.isNotBlank() } ?: continue
                if (name.equals("app-release.apk", ignoreCase = true) || name.startsWith("Explorer", ignoreCase = true)) {
                    apk = url
                    break
                }
                if (anyApk == null) anyApk = url
            }
            if (apk == null) apk = anyApk
        }
        return Release(
            tag = tag,
            version = versionOf(tag),
            name = json.optString("name").ifBlank { tag },
            notes = json.optString("body"),
            pageUrl = json.optString("html_url").ifBlank { REPOSITORY },
            apkUrl = apk,
            publishedAt = 0L,
            preRelease = json.optBoolean("prerelease", false),
        )
    }

    /** `18.3.0-beta` out of a tag like `v18.3.0-beta` or `18.3.0-beta`. */
    @JvmStatic
    fun versionOf(tag: String): String = tag.trim().removePrefix("v").removePrefix("V").trim()

    /**
     * Whether [candidate] is newer than [installed].
     *
     * The numeric core is compared first. Prereleases then use semver-like ordering: a stable
     * release is newer than a prerelease, beta.2 is newer than beta.1, and beta is older than
     * release candidate. GitHub tags in this project have used both `v18.3.0-beta` and
     * `18.3.0-beta.1`, so comparing only the presence of a suffix would miss a later beta.
     */
    @JvmStatic
    fun isNewer(candidate: String, installed: String): Boolean = compareVersions(candidate, installed) > 0

    @JvmStatic
    fun compareVersions(leftVersion: String, rightVersion: String): Int {
        val left = versionParts(leftVersion)
        val right = versionParts(rightVersion)
        for (index in 0 until maxOf(left.core.size, right.core.size)) {
            val a = left.core.getOrElse(index) { 0 }
            val b = right.core.getOrElse(index) { 0 }
            if (a != b) return a.compareTo(b)
        }
        if (left.suffix.isEmpty() || right.suffix.isEmpty()) {
            return when {
                left.suffix.isEmpty() && right.suffix.isEmpty() -> 0
                left.suffix.isEmpty() -> 1
                else -> -1
            }
        }
        for (index in 0 until maxOf(left.suffix.size, right.suffix.size)) {
            val a = left.suffix.getOrNull(index)
            val b = right.suffix.getOrNull(index)
            if (a == null || b == null) return if (a == null) -1 else 1
            val comparison = compareSuffixPart(a, b)
            if (comparison != 0) return comparison
        }
        return 0
    }

    private data class VersionParts(val core: List<Int>, val suffix: List<String>)

    private fun versionParts(version: String): VersionParts {
        val normalized = versionOf(version).substringBefore('+')
        val dash = normalized.indexOf('-')
        val coreText = if (dash < 0) normalized else normalized.substring(0, dash)
        val suffixText = if (dash < 0) "" else normalized.substring(dash + 1)
        val core = coreText.split('.').map { it.toIntOrNull() ?: 0 }
        val suffix = suffixText.split(Regex("[.\\-_]"))
            .filter { it.isNotEmpty() }
        return VersionParts(core, suffix)
    }

    private fun compareSuffixPart(left: String, right: String): Int {
        val leftNumber = left.toIntOrNull()
        val rightNumber = right.toIntOrNull()
        if (leftNumber != null && rightNumber != null) return leftNumber.compareTo(rightNumber)
        if (leftNumber != null) return -1
        if (rightNumber != null) return 1
        val ranks = mapOf("dev" to 0, "snapshot" to 0, "alpha" to 1, "a" to 1, "beta" to 2, "b" to 2, "rc" to 3)
        val leftRank = ranks[left.lowercase()]
        val rightRank = ranks[right.lowercase()]
        if (leftRank != null || rightRank != null) {
            if (leftRank == null) return 1
            if (rightRank == null) return -1
            if (leftRank != rightRank) return leftRank.compareTo(rightRank)
        }
        return left.lowercase().compareTo(right.lowercase())
    }

    // --- the download of the new version -------------------------------------------------------

    /** The download of the new version that is on its way, if any. */
    @JvmStatic
    fun runningDownload(context: Context): DownloadTask? =
        DownloadStore.list(context).firstOrNull {
            it.state == DownloadTask.State.RUNNING || it.state == DownloadTask.State.QUEUED ||
                it.state == DownloadTask.State.PAUSED
        }?.takeIf { it.url == availableUrl(context) }

    /** The new version, already downloaded. */
    @JvmStatic
    fun downloaded(context: Context): DownloadTask? =
        DownloadStore.list(context).firstOrNull {
            it.state == DownloadTask.State.DONE && it.url == availableUrl(context)
        }

    /** Fetches the package of the new version with the built-in download manager. */
    @JvmStatic
    fun download(context: Context): Long? {
        val url = availableUrl(context) ?: return null
        val version = availableVersion(context) ?: "Explorer"
        val name = "Explorer-$version.apk"
        return DownloadEngine.enqueue(
            context,
            url,
            name,
            ApkInstaller.MIME_APK,
            "Explorer/" + installedVersion(context),
        )
    }

    /** Hands the downloaded package to the installer of Android. */
    @JvmStatic
    fun install(context: Context): Boolean {
        val task = downloaded(context) ?: return false
        return ApkInstaller.install(context, task)
    }

    /** The folder the new version is put in; the same one as every other download. */
    @JvmStatic
    fun targetFolder(context: Context): String = DownloadDirs.path(context)

    /** The file of the new version, once it is downloaded. */
    @JvmStatic
    fun fileOf(context: Context): File? =
        downloaded(context)?.filePath?.let { File(it) }?.takeIf { it.isFile }

    /** The page of the release in a browser of the device. */
    @JvmStatic
    fun releasePage(context: Context): Intent =
        Intent(
            Intent.ACTION_VIEW,
            android.net.Uri.parse(prefs(context).getString(KEY_PAGE, null) ?: REPOSITORY),
        )

    /** "The latest version is installed." or "Version 18.4.0-beta is available." */
    @JvmStatic
    fun statusText(context: Context): String {
        val available = availableVersion(context)
        return when {
            available != null && isNewer(available, installedVersion(context)) ->
                context.getString(com.rpooi3.explorer.R.string.update_available, available)
            available != null -> context.getString(com.rpooi3.explorer.R.string.update_latest)
            lastError(context) != null -> context.getString(com.rpooi3.explorer.R.string.update_failed)
            else -> context.getString(com.rpooi3.explorer.R.string.update_not_checked)
        }
    }

    private const val TAG = "Updater"
}

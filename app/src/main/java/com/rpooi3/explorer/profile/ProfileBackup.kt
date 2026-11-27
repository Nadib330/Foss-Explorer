/*
 * Explorer: the backup of one profile.
 * Own code, GPL-3.0.
 *
 * 18.0.0 beta 3, issue 8: a backup belongs to the profile it is made in. Before this, the backup
 * took the databases of the whole app along — the history and the bookmarks of every profile — and
 * a restore put them back wherever the user happened to be, so two profiles could end up sharing
 * one history through a file. Now:
 *
 *  - what is written is the data of the profile in front: its databases (history, bookmarks,
 *    downloads, saved websites, site icons), its preferences file and its own folder (pictures,
 *    files, extensions);
 *  - the zip carries a small manifest saying which profile it came from;
 *  - a restore reads the manifest and puts what came back into the profile in front, renaming the
 *    files as it goes (`places-1a2b3c4d.sqlite` → `places-9f8e7d6c.sqlite`), and touches nothing of
 *    any other profile.
 *
 * A backup of a version before 18.0.0 has no manifest. It is read as the first profile's, which is
 * where its data came from anyway.
 */
package com.rpooi3.explorer.profile

import android.content.Context
import android.net.Uri
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

object ProfileBackup {

    private const val TAG = "ProfileBackup"

    /** The entry that says which profile a zip belongs to. */
    const val MANIFEST = "explorer-profile-backup.txt"

    /** The shape of a zip this version writes; 1 is every version before 18.0.0 beta 3. */
    const val FORMAT = 2

    /** The databases, by the name the first profile has for them. */
    private val DATABASES = arrayOf("places.sqlite", "favicon.db", "Ninja4.db")

    /** What a database file has beside it on disk. */
    private val JOURNALS = arrayOf("-wal", "-shm", "-journal")

    /** What a restore found and where it put it. */
    data class Restored(val fromProfile: String, val fromName: String?, val files: Int) {
        val found: Boolean get() = files > 0
    }

    // What a profile has ------------------------------------------------------------------------

    /**
     * The files of [id] that a backup takes: its databases and their journals, its preferences,
     * and — when [includeFolders] — everything in its folder.
     */
    @JvmStatic
    fun filesOf(context: Context, id: String, includeFolders: Boolean): List<File> {
        val data = dataDir(context)
        val files = ArrayList<File>()

        val dbDir = File(data, "databases")
        (dbDir.listFiles() ?: emptyArray()).forEach { file ->
            if (belongsToDatabase(file.name, id)) files.add(file)
        }

        val prefsDir = File(data, "shared_prefs")
        (prefsDir.listFiles() ?: emptyArray()).forEach { file ->
            if (belongsToPreferences(file.name, id)) files.add(file)
        }

        if (includeFolders) {
            val folder = Profiles.thingsOf(context, id)
            walk(folder, files, skipProfiles = id.isEmpty())
        }
        return files
    }

    // Writing -----------------------------------------------------------------------------------

    /**
     * Writes the backup of [profile] to [targetUri].
     *
     * @param includeFolders whether the profile's own folder goes in as well; the databases-only
     *   backup leaves it out, which keeps that file small
     */
    @JvmStatic
    fun backup(context: Context, targetUri: Uri, profile: BrowserProfile, includeFolders: Boolean): Boolean {
        val data = dataDir(context)
        if (!data.isDirectory) return false
        // Everything written up to now has to be on disk before the files are read.
        try {
            com.rpooi3.explorer.database.places.PlacesDatabase.get(context)
                .writableDatabase
                .rawQuery("PRAGMA wal_checkpoint(TRUNCATE)", null)
                .close()
        } catch (e: Exception) {
            Log.w(TAG, "The history could not be flushed: " + e)
        }
        try {
            android.webkit.CookieManager.getInstance().flush()
        } catch (e: Exception) {
            // A browser without cookies to flush.
        }

        val files = filesOf(context, profile.id, includeFolders)
        if (files.isEmpty()) {
            Log.w(TAG, "Nothing to back up for the profile " + profile.directory)
            return false
        }
        return try {
            val stream: OutputStream = context.contentResolver.openOutputStream(targetUri)
                ?: return false
            stream.use { out ->
                ZipOutputStream(out).use { zip ->
                    writeManifest(zip, profile)
                    for (file in files) {
                        val entryName = relativePath(data, file) ?: continue
                        if (file.isDirectory) {
                            zip.putNextEntry(ZipEntry(entryName + "/"))
                            zip.closeEntry()
                        } else {
                            zip.putNextEntry(ZipEntry(entryName))
                            file.inputStream().use { input -> input.copyTo(zip) }
                            zip.closeEntry()
                        }
                    }
                }
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "The backup of the profile could not be written", e)
            false
        }
    }

    private fun writeManifest(zip: ZipOutputStream, profile: BrowserProfile) {
        zip.putNextEntry(ZipEntry(MANIFEST))
        val text = buildString {
            append("format=").append(FORMAT).append('\n')
            append("profile=").append(profile.id).append('\n')
            append("name=").append(profile.name.replace('\n', ' ')).append('\n')
            append("saved=").append(System.currentTimeMillis()).append('\n')
        }
        zip.write(text.toByteArray(Charsets.UTF_8))
        zip.closeEntry()
    }

    // Reading -----------------------------------------------------------------------------------

    /**
     * Puts the backup at [sourceUri] into the profile in front. Nothing of another profile is
     * touched, and a file that came from another profile is renamed to this one's.
     */
    @JvmStatic
    fun restore(context: Context, sourceUri: Uri): Restored {
        val data = dataDir(context)
        val to = Profiles.currentId(context)

        val entries = ArrayList<Pair<String, ByteArray>>()
        var manifest: Map<String, String> = emptyMap()
        try {
            val stream: InputStream = context.contentResolver.openInputStream(sourceUri)
                ?: return Restored("", null, 0)
            stream.use { input ->
                ZipInputStream(input).use { zip ->
                    var entry: ZipEntry? = zip.nextEntry
                    while (entry != null) {
                        if (!entry.isDirectory) {
                            val bytes = zip.readBytes()
                            if (entry.name == MANIFEST) {
                                manifest = parseManifest(String(bytes, Charsets.UTF_8))
                            } else {
                                entries.add(entry.name to bytes)
                            }
                        }
                        zip.closeEntry()
                        entry = zip.nextEntry
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "The backup could not be read", e)
            return Restored("", null, 0)
        }

        val from = manifest["profile"] ?: ""
        val fromName = manifest["name"]
        if (entries.isEmpty()) return Restored(from, fromName, 0)

        // A database that is being read cannot be written under: the browser is told to let go.
        try {
            com.rpooi3.explorer.database.places.PlacesDatabase.release()
        } catch (e: Exception) {
            Log.w(TAG, "The history database could not be released: " + e)
        }
        dropDatabasesOf(context, to)

        var written = 0
        for ((entryName, bytes) in entries) {
            val target = mapPath(entryName, from, to) ?: continue
            val outFile = File(data, target)
            try {
                outFile.parentFile?.mkdirs()
                FileOutputStream(outFile).use { out -> out.write(bytes) }
                written++
            } catch (e: Exception) {
                Log.w(TAG, "Could not write " + target + ": " + e)
            }
        }

        if (written > 0) settleLegacyDatabase(context, to)
        return Restored(from, fromName, written)
    }

    /** The old `Ninja4.db` of a version before 15.0.0 is emptied the way the browser expects. */
    private fun settleLegacyDatabase(context: Context, to: String) {
        val legacy = File(File(dataDir(context), "databases"), databaseFileOf("Ninja4.db", to))
        if (legacy.isFile.not()) return
        try {
            val db = android.database.sqlite.SQLiteDatabase.openOrCreateDatabase(legacy, null)
            db.execSQL("DROP TABLE IF EXISTS GRID")
            db.execSQL("DROP TABLE IF EXISTS JAVASCRIPT")
            db.execSQL("DROP TABLE IF EXISTS COOKIE")
            db.delete("REMOTE", null, null)
            db.version = 5
            db.close()
        } catch (e: Exception) {
            Log.e(TAG, "The legacy database could not be cleaned after the restore", e)
        }
    }

    /** Takes the databases of [id] away before a restore writes over them. */
    private fun dropDatabasesOf(context: Context, id: String) {
        val dbDir = File(dataDir(context), "databases")
        (dbDir.listFiles() ?: emptyArray()).forEach { file ->
            if (belongsToDatabase(file.name, id) && !file.delete()) {
                Log.w(TAG, "The old database " + file.name + " stayed")
            }
        }
    }

    // Names -------------------------------------------------------------------------------------

    /**
     * Where a file of the profile [from] goes when it is restored into the profile [to].
     *
     * @return the path below the data folder, or null when the entry belongs to no profile or to
     *   another one (a zip of every version before 18.0.0 beta 3 holds the databases of all)
     */
    @JvmStatic
    fun mapPath(entry: String, from: String, to: String): String? {
        if (entry.isEmpty() || entry == MANIFEST) return null

        if (entry.startsWith("shared_prefs/")) {
            val file = entry.removePrefix("shared_prefs/")
            if (!file.endsWith(".xml")) return null
            if (!belongsToPreferences(file, from)) return null
            val name = file.removeSuffix(".xml")
            val base = if (from.isEmpty()) name else name.removeSuffix(ProfilePrefs.SEPARATOR + from)
            val mapped = if (to.isEmpty()) base else base + ProfilePrefs.SEPARATOR + to
            return "shared_prefs/" + mapped + ".xml"
        }

        if (entry.startsWith("files/")) {
            val fromPrefix = folderPrefix(from)
            if (!entry.startsWith(fromPrefix)) return null
            val rest = entry.removePrefix(fromPrefix)
            if (rest.isEmpty()) return null
            // The first profile's folder is `files/` itself, and `files/profiles/` is not its own.
            if (from.isEmpty() && rest.startsWith("profiles/")) return null
            return folderPrefix(to) + rest
        }

        if (entry.startsWith("databases/")) {
            val file = entry.removePrefix("databases/")
            val mapped = mapDatabaseName(file, from, to) ?: return null
            return "databases/" + mapped
        }
        return null
    }

    /**
     * `places-1a2b3c4d.sqlite` → `places-9f8e7d6c.sqlite`, and the same for its journals and for
     * the icons. A file of the old `Ninja4.db` exists only for the first profile.
     */
    @JvmStatic
    fun mapDatabaseName(name: String, from: String, to: String): String? {
        var stem = name
        var journal = ""
        for (suffix in JOURNALS) {
            if (stem.endsWith(suffix)) {
                journal = suffix
                stem = stem.removeSuffix(suffix)
                break
            }
        }
        val dot = stem.lastIndexOf('.')
        if (dot <= 0) return null
        val extension = stem.substring(dot)
        var base = stem.substring(0, dot)
        if (from.isEmpty()) {
            // A name with an id in it belongs to another profile.
            if (base.contains('-')) return null
            if (base == "Ninja4" && to.isNotEmpty()) return null
        } else {
            if (!base.endsWith("-" + from)) return null
            base = base.removeSuffix("-" + from)
        }
        return base + (if (to.isEmpty()) "" else "-" + to) + extension + journal
    }

    /** The manifest of a zip: `format=`, `profile=`, `name=`, `saved=`. */
    @JvmStatic
    fun parseManifest(text: String): Map<String, String> {
        val result = HashMap<String, String>()
        for (line in text.lineSequence()) {
            val at = line.indexOf('=')
            if (at <= 0) continue
            result[line.substring(0, at).trim()] = line.substring(at + 1).trim()
        }
        return result
    }

    /** Whether the database file [name] is [id]'s. */
    @JvmStatic
    fun belongsToDatabase(name: String, id: String): Boolean {
        for (base in DATABASES) {
            if (mapDatabaseName(name, id, id) == databaseFileOf(base, id)) return true
        }
        // A database this version does not know by name, but that carries the profile's id.
        return id.isNotEmpty() && name.contains("-" + id)
    }

    /** Whether the preferences file [name] is [id]'s. */
    @JvmStatic
    fun belongsToPreferences(name: String, id: String): Boolean {
        if (!name.endsWith(".xml")) return false
        val bare = name.removeSuffix(".xml")
        if (id.isEmpty()) return !bare.contains(ProfilePrefs.SEPARATOR)
        return bare.endsWith(ProfilePrefs.SEPARATOR + id)
    }

    /** `places.sqlite` for the first profile, `places-1a2b3c4d.sqlite` for the others. */
    @JvmStatic
    fun databaseFileOf(base: String, id: String): String = mapDatabaseName(base, id, id) ?: base

    // Files -------------------------------------------------------------------------------------

    @JvmStatic
    fun dataDir(context: Context): File =
        File(context.applicationContext.applicationInfo.dataDir)

    /** `files/` for the first profile, `files/profiles/<id>/` for the others. */
    private fun folderPrefix(id: String): String =
        if (id.isEmpty()) "files/" else "files/profiles/" + id + "/"

    private fun relativePath(data: File, file: File): String? {
        val root = data.absolutePath.trimEnd(File.separatorChar)
        val path = file.absolutePath
        if (!path.startsWith(root)) return null
        return path.substring(root.length).trimStart(File.separatorChar)
    }

    private fun walk(dir: File, into: MutableList<File>, skipProfiles: Boolean) {
        if (!dir.isDirectory) return
        (dir.listFiles() ?: emptyArray()).forEach { file ->
            if (skipProfiles && file.name == "profiles" && file.isDirectory) return@forEach
            if (file.isDirectory) walk(file, into, false) else into.add(file)
        }
    }
}

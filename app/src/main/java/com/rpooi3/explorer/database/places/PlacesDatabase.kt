/*
 * Foss Explorer: the database of history and bookmarks.
 * Own code, GPL-3.0. The database is laid out as Firefox's "Places" database:
 * its tables are created from Mozilla's own schema file
 * (assets/places/create_shared_schema.sql, MPL-2.0, from application-services,
 * see third_party/application-services), and filled by the rules of that
 * component.
 */
package com.rpooi3.explorer.database.places

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.util.Log
import androidx.core.database.sqlite.transaction
import java.io.File

/**
 * `places.sqlite`: history in `moz_places` and `moz_historyvisits`, bookmarks
 * in `moz_bookmarks`, as in Firefox for Android. Two small tables of
 * Explorer's own stand beside Mozilla's (`explorer_*`): the colour a bookmark
 * is filed under, and the lists of websites the site profiles keep.
 *
 * Firefox fills some columns with triggers that call functions of its own
 * code; SQLite on Android does not have those, so [Places] sets these
 * columns itself when it writes.
 */
class PlacesDatabase private constructor(
    private val appContext: Context,
    name: String,
) : SQLiteOpenHelper(appContext, name, null, VERSION) {

    override fun onConfigure(db: SQLiteDatabase) {
        db.setForeignKeyConstraintsEnabled(true)
    }

    override fun onCreate(db: SQLiteDatabase) {
        for (statement in statements(appContext.assets.open(SCHEMA_ASSET).bufferedReader().use { it.readText() })) {
            db.execSQL(statement)
        }
        createRoots(db)
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS $TABLE_BOOKMARK_EXTRAS (" +
                "guid TEXT PRIMARY KEY REFERENCES moz_bookmarks(guid) ON DELETE CASCADE, " +
                "filter INTEGER NOT NULL DEFAULT 0)",
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS $TABLE_SITE_LISTS (" +
                "list TEXT NOT NULL, domain TEXT NOT NULL, PRIMARY KEY (list, domain)) WITHOUT ROWID",
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // The first version of this database is Mozilla's schema 21; nothing older exists.
    }

    /** The roots of the bookmark tree, with the names and positions Firefox gives them. */
    private fun createRoots(db: SQLiteDatabase) {
        val now = System.currentTimeMillis()
        fun root(guid: String, title: String, parent: Long?, position: Int): Long = db.insertOrThrow(
            "moz_bookmarks", null,
            ContentValues().apply {
                put("type", Places.TYPE_FOLDER)
                if (parent != null) put("parent", parent)
                put("position", position)
                put("title", title)
                put("dateAdded", now)
                put("lastModified", now)
                put("guid", guid)
                put("syncStatus", Places.SYNC_STATUS_NEW)
            },
        )
        val top = root(Places.GUID_ROOT, "root", null, 0)
        root(Places.GUID_MENU, "menu", top, 0)
        root(Places.GUID_TOOLBAR, "toolbar", top, 1)
        root(Places.GUID_UNFILED, "unfiled", top, 2)
        root(Places.GUID_MOBILE, "mobile", top, 3)
    }

    companion object {
        const val NAME = "places.sqlite"

        /** The version of Mozilla's schema the tables follow (`VERSION` in components/places/src/db/schema.rs). */
        const val VERSION = 21
        const val SCHEMA_ASSET = "places/create_shared_schema.sql"
        const val TABLE_BOOKMARK_EXTRAS = "explorer_bookmark_extras"
        const val TABLE_SITE_LISTS = "explorer_site_lists"

        /** The database of versions before 15.0.0, FOSS Browser's. Its content is taken over once. */
        const val LEGACY_NAME = "Ninja4.db"
        private const val TAG = "PlacesDatabase"

        @Volatile
        private var instance: PlacesDatabase? = null

        /** The profile whose database is open; null before the first use. */
        @Volatile
        private var instanceProfile: String? = null

        /**
         * The database of the profile in front, opened on first use.
         *
         * 18.0.0: every profile has a database of its own, `places-<id>.sqlite`, so that history,
         * bookmarks and saved lists never mix. The first profile keeps `places.sqlite`, which is
         * where the history of every version before 18.0.0 is.
         */
        @JvmStatic
        fun get(context: Context): PlacesDatabase {
            val app = context.applicationContext
            val profile = com.rpooi3.explorer.profile.Profiles.currentId(app)
            // (One application, one database per profile. Tests start a new application for every test.)
            instance?.takeIf { it.appContext === app && instanceProfile == profile }?.let { return it }
            synchronized(this) {
                instance?.takeIf { it.appContext === app && instanceProfile == profile }?.let { return it }
                instance?.close()
                instance = null
                val created = PlacesDatabase(app, nameOf(app, profile))
                if (profile.isEmpty()) importLegacy(context.applicationContext, created.writableDatabase)
                instance = created
                instanceProfile = profile
                return created
            }
        }

        /** The file name of the database of [profile]: `places.sqlite` for the first one. */
        @JvmStatic
        fun nameOf(context: Context, profile: String): String {
            if (profile.isEmpty()) return NAME
            return NAME.removeSuffix(".sqlite") + "-" + profile + ".sqlite"
        }

        /**
         * Closes the database of the profile that is being left, so that another profile's file
         * can be opened in its place. Nothing is lost: SQLite has written everything already.
         */
        @JvmStatic
        fun closeForSwitch() {
            synchronized(this) {
                instance?.close()
                instance = null
                instanceProfile = null
            }
        }

        /** Closes the database, so that its file can be replaced (restoring a backup) or removed. */
        @JvmStatic
        fun release() {
            synchronized(this) {
                instance?.close()
                instance = null
                instanceProfile = null
            }
        }

        /**
         * Deletes the database of [profile], for a profile that is being taken away. The file of a
         * deleted profile never comes back: a new profile gets a new id.
         */
        @JvmStatic
        fun deleteFor(context: Context, profile: String) {
            if (profile.isEmpty()) return
            release()
            try {
                val file = context.applicationContext.getDatabasePath(nameOf(context, profile))
                if (file.isFile) file.delete()
                java.io.File(file.path + "-journal").delete()
                java.io.File(file.path + "-wal").delete()
                java.io.File(file.path + "-shm").delete()
            } catch (e: Exception) {
                Log.w(TAG, "The database of " + profile + " could not be deleted: " + e)
            }
        }

        /** The SQL statements of a schema file: comments dropped, split at the semicolons. */
        internal fun statements(sql: String): List<String> =
            sql.lineSequence().map { it.substringBefore("--") }.joinToString("\n")
                .split(';').map { it.trim() }.filter { it.isNotEmpty() }

        /**
         * Takes over history, bookmarks and the list of saved websites from
         * FOSS Browser's database, if one is there (an update from an earlier
         * version, or a backup of one that was just restored), and removes it.
         */
        internal fun importLegacy(context: Context, db: SQLiteDatabase) {
            val file = context.getDatabasePath(LEGACY_NAME)
            if (!file.isFile) return
            try {
                val old = SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY)
                try {
                    db.transaction {
                        fun rows(table: String, columns: Array<String>, take: (android.database.Cursor) -> Unit) {
                            try {
                                old.query(table, columns, null, null, null, null, null).use { c -> while (c.moveToNext()) take(c) }
                            } catch (e: android.database.SQLException) {
                                // A database without this table: nothing to take over from it.
                            }
                        }
                        rows("HISTORY", arrayOf("TITLE", "URL", "TIME")) { c ->
                            val url = c.getString(1)?.trim().orEmpty()
                            if (url.isNotEmpty()) Places.noteVisit(db, url, c.getString(0), c.getLong(2))
                        }
                        rows("BOOKAMRK", arrayOf("TITLE", "URL", "TIME")) { c ->
                            val url = c.getString(1)?.trim().orEmpty()
                            // FOSS Browser kept the colour of a bookmark in the column TIME.
                            if (url.isNotEmpty() && !Places.isBookmarked(db, url)) {
                                Places.addBookmark(db, url, c.getString(0).orEmpty(), c.getLong(2))
                            }
                        }
                        rows("REMOTE", arrayOf("DOMAIN")) { c ->
                            c.getString(0)?.takeIf { it.isNotBlank() }?.let { Places.addToSiteList(db, "REMOTE", it) }
                        }
                    }
                } finally {
                    old.close()
                }
            } catch (e: Exception) {
                Log.w(TAG, "Could not read the earlier database", e)
                return
            }
            for (suffix in listOf("", "-journal", "-wal", "-shm")) File(file.path + suffix).delete()
        }
    }
}

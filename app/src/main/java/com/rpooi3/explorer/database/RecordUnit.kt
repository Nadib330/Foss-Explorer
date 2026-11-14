package com.rpooi3.explorer.database

/**
 * The names by which [RecordAction] is told which list a call means. They
 * were table names of FOSS Browser's database; history and bookmarks are in
 * Firefox's Places tables now (database/places), and the names are kept as
 * they are because backups and the list of saved websites carry them.
 */
object RecordUnit {
    const val TABLE_BOOKMARK = "BOOKAMRK"
    const val TABLE_HISTORY = "HISTORY"
    const val TABLE_STANDARD = "REMOTE"
}

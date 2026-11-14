package com.rpooi3.explorer.database

import org.junit.Assert.assertEquals
import org.junit.Test

class RecordUnitTest {
    /**
     * The names of the lists are what backups and the list of saved websites
     * carry, the misspelling of upstream included; they must stay as they are.
     */
    @Test
    fun listNames_areTheOnesOnDisk() {
        assertEquals("BOOKAMRK", RecordUnit.TABLE_BOOKMARK)
        assertEquals("HISTORY", RecordUnit.TABLE_HISTORY)
        assertEquals("REMOTE", RecordUnit.TABLE_STANDARD)
    }
}

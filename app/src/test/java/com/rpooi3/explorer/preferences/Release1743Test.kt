package com.rpooi3.explorer.preferences

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.rpooi3.explorer.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * What 17.4.3 changes: the row **Open source components** and the text behind it are gone from
 * *Settings > About > Legal information*. The licences (GPL-3.0, AGPL-3.0, Chromium's BSD
 * 3-clause, the MPL 2.0) and the source rows stay; what was taken from which project, with the
 * licence of each part, is kept in `LICENSE.md` of the repository.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [26, 34])
class Release1743Test {

    private val context = ApplicationProvider.getApplicationContext<Application>()

    /** The screen of *Legal information*, as it is written in its layout. */
    private fun legalXml(): String = File("src/main/res/xml/preference_section_legal.xml").readText()

    // 1. The row is gone -----------------------------------------------------------------------

    @Test
    fun legalInformation_hasNoRowOfComponentsAnymore() {
        val xml = legalXml()
        assertFalse("the row is gone", xml.contains("about_third_party"))
        assertFalse("and the strings it used", xml.contains("about_components"))
        // Nothing else was taken away with it.
        for (key in listOf(
            "about_license_gpl", "about_license_agpl",
            "about_license_chromium", "about_license_mpl",
            "about_source_chromium", "about_source_cromite",
            "about_source_firefox", "about_source_upstream",
        )) {
            assertTrue("$key is still there", xml.contains(key))
        }
        // The screen still leads to that section from About.
        assertTrue(
            "the row of Legal information itself is untouched",
            File("src/main/res/xml/preference_section_about.xml").readText().contains("section_legal"),
        )
    }

    @Test
    fun theTextOfTheRow_isNotInTheAppAnyMore() {
        assertNull(
            "the list of components is not carried by the app",
            SettingsScreens.readAsset(context, "licenses/components.txt"),
        )
        // The licence texts the screen does show are still there.
        for (asset in listOf(
            "licenses/GPL-3.0.txt", "licenses/AGPL-3.0.txt",
            "licenses/Chromium-BSD-3-Clause.txt", "licenses/MPL-2.0.txt",
        )) {
            assertNotNull(asset, SettingsScreens.readAsset(context, asset))
        }
    }

    @Test
    fun noStringOfThatRow_isLeftInTheResources() {
        // A string nobody reads is a row that was only half removed.
        for (name in listOf("about_components_title", "about_components_summary")) {
            val id = context.resources.getIdentifier(name, "string", context.packageName)
            assertEquals("$name is not a resource any more", 0, id)
        }
        assertTrue(
            "the licences still have their titles",
            context.getString(R.string.about_license_chromium).contains("Chromium"),
        )
        assertTrue(
            "and the sources theirs",
            context.getString(R.string.about_source_cromite_title).contains("Cromite"),
        )
    }

    @Test
    fun theSummaryOfFOSSBrowser_doesNotPointAtARowThatIsGone() {
        val summary = context.getString(R.string.about_source_upstream_summary)
        assertFalse(
            "it pointed at the row that was removed: $summary",
            summary.contains("Open source components"),
        )
        assertTrue(summary, summary.contains("FOSS Browser"))
        assertTrue(summary, summary.contains("LICENSE.md"))
    }

    // 2. The licence assets and the file in the repository keep the notices -------------------

    @Test
    fun theRepository_keepsWhatTheRowSaid() {
        val notices = File("../LICENSE.md").readText()
        for (part in listOf(
            "FOSS Browser", "Cromite", "Chromium", "Firefox",
            "GNU General Public License, version 3",
            "GNU Affero General Public License, version 3 or later",
            "BSD 3-clause", "Mozilla Public License 2.0", "Apache License 2.0",
        )) {
            assertTrue(part, notices.contains(part))
        }
    }

    @Test
    fun theToolThatWroteThatText_doesNotWriteItBack() {
        val tool = File("../tools/build_license_assets.py").readText()
        assertFalse("components.txt is not built any more", tool.contains("'components.txt'"))
        assertTrue("the GNU licences still are", tool.contains("'GPL-3.0.txt'"))
        assertTrue(tool.contains("'AGPL-3.0.txt'"))
    }
}

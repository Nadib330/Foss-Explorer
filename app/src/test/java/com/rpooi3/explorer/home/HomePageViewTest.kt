package com.rpooi3.explorer.home

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.view.ContextThemeWrapper
import androidx.test.core.app.ApplicationProvider
import com.rpooi3.explorer.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [26, 34])
class HomePageViewTest {

    private val events = ArrayList<String>()
    private lateinit var root: View
    private lateinit var home: HomePageView

    @Before
    fun setUp() {
        val context = ContextThemeWrapper(ApplicationProvider.getApplicationContext(), R.style.AppTheme)
        val parent = FrameLayout(context)
        root = LayoutInflater.from(context).inflate(R.layout.include_home, parent, false)
        parent.addView(root)
        home = HomePageView(root, object : HomePageView.Listener {
            override fun onHomeSearch() { events += "search" }
            override fun onHomeMenu(anchor: View) { events += "menu" }
            override fun onHomeOpen(url: String) { events += "open $url" }
            override fun onHomeOpenInNewTab(url: String, foreground: Boolean) { events += "tab $url $foreground" }
        })
    }

    private val grid: ViewGroup get() = root.findViewById(R.id.home_shortcuts)
    private val card: View get() = root.findViewById(R.id.home_shortcuts_card)

    private fun sites(count: Int) = (1..count).map { Shortcut("Site $it", "https://site$it.example/") }

    @Test
    fun menuButton_asksForTheMenu() {
        root.findViewById<View>(R.id.home_menu).performClick()

        assertEquals(listOf("menu"), events)
    }

    @Test
    fun searchBox_asksForTheSearchDialog() {
        root.findViewById<View>(R.id.home_search).performClick()
        assertEquals(listOf("search"), events)
    }

    @Test
    fun tiles_areLaidOutFourToARow() {
        home.render(sites(6))

        assertEquals(View.VISIBLE, card.visibility)
        assertEquals(2, grid.childCount)
        // Every row has four cells so that the columns line up; the last row is padded.
        assertEquals(4, (grid.getChildAt(0) as ViewGroup).childCount)
        assertEquals(4, (grid.getChildAt(1) as ViewGroup).childCount)
        assertEquals(6, tiles().size)
    }

    @Test
    fun tile_showsNameAndInitial() {
        home.render(listOf(Shortcut("wikipedia", "https://www.wikipedia.org/")))

        val tile = tiles().single()
        assertEquals("wikipedia", tile.findViewById<TextView>(R.id.home_tile_title).text.toString())
        val icon = tile.findViewById<ImageView>(R.id.home_tile_icon).drawable as MonogramDrawable
        assertEquals("W", icon.letter)
    }

    @Test
    fun tappingATile_opensItsAddress() {
        home.render(sites(3))
        tiles()[1].performClick()
        assertEquals(listOf("open https://site2.example/"), events)
    }

    @Test
    fun noTiles_hidesTheCard() {
        home.render(sites(2))
        home.render(emptyList())

        assertEquals(View.GONE, card.visibility)
        assertEquals(0, grid.childCount)
    }

    @Test
    fun visibility_followsSetVisible() {
        assertFalse(home.isVisible)
        home.setVisible(true)
        assertTrue(home.isVisible)
        home.setVisible(false)
        assertEquals(View.GONE, root.visibility)
    }

    @Test
    fun monogram_fallsBackToQuestionMark() {
        assertEquals("?", MonogramDrawable(" - ", 0).letter)
        assertEquals("F", MonogramDrawable("f-droid", 0).letter)
    }

    private fun tiles(): List<View> {
        val result = ArrayList<View>()
        for (r in 0 until grid.childCount) {
            val row = grid.getChildAt(r) as ViewGroup
            for (c in 0 until row.childCount) {
                val cell = row.getChildAt(c)
                if (cell.findViewById<View>(R.id.home_tile_title) != null) result += cell
            }
        }
        return result
    }
}

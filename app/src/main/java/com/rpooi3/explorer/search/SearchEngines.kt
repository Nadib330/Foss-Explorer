/*
 * Foss Explorer: Settings > Search engine.
 * Own code, GPL-3.0.
 */
package com.rpooi3.explorer.search

import androidx.core.content.edit
import android.content.Context
import android.content.SharedPreferences
import androidx.annotation.DrawableRes
import androidx.appcompat.content.res.AppCompatResources
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import androidx.annotation.StringRes
import com.rpooi3.explorer.R

/** The search engines to choose from, in the order of the screen. */
object SearchEngines {

    /**
     * @property key name of the row (`engine_<key>`)
     * @property value what is stored in the setting `sp_search_engine`
     * @property searchUrl the address a search is appended to
     * @property logo the engine's round logo in front of the name; 0 if the app has none
     * @property color, letter the round sign that stands in for a missing logo
     * @property main whether the engine belongs to the first card (the list of Chrome's screen plus Startpage)
     */
    data class Engine(
        val key: String,
        val value: String,
        @StringRes val title: Int,
        val domain: String,
        val searchUrl: String,
        val color: Int,
        val letter: String,
        val main: Boolean,
        @DrawableRes val logo: Int = 0,
    )

    const val KEY_ENGINE = "sp_search_engine"
    const val KEY_CUSTOM_ON = "searchEngineSwitch"
    const val KEY_CUSTOM_URL = "sp_search_engine_custom"
    const val CUSTOM = "custom"

    @JvmField
    val ALL: List<Engine> = listOf(
        Engine("google", "9", R.string.engine_google, "google.com", "https://www.google.com/search?q=", 0xFF4285F4.toInt(), "G", true, R.drawable.engine_google),
        Engine("brave", "11", R.string.engine_brave, "search.brave.com", "https://search.brave.com/search?q=", 0xFFFB542B.toInt(), "B", true, R.drawable.engine_brave),
        Engine("bing", "12", R.string.engine_bing, "bing.com", "https://www.bing.com/search?q=", 0xFF0C8484.toInt(), "b", true, R.drawable.engine_bing),
        Engine("duckduckgo", "4", R.string.engine_duckduckgo, "duckduckgo.com", "https://duckduckgo.com/?q=", 0xFFDE5833.toInt(), "D", true, R.drawable.engine_duckduckgo),
        Engine("ecosia", "8", R.string.engine_ecosia, "ecosia.org", "https://www.ecosia.org/search?q=", 0xFF008009.toInt(), "E", true, R.drawable.engine_ecosia),
        Engine("startpage", "0", R.string.engine_startpage, "startpage.com", "https://startpage.com/do/search?query=", 0xFF6573FF.toInt(), "S", true, R.drawable.engine_startpage),
        Engine("startpage_de", "1", R.string.engine_startpage_de, "startpage.com", "https://startpage.com/do/search?lui=deu&language=deutsch&query=", 0xFF6573FF.toInt(), "S", false, R.drawable.engine_startpage),
        Engine("qwant", "7", R.string.engine_qwant, "qwant.com", "https://www.qwant.com/?q=", 0xFF5C97FF.toInt(), "Q", false, R.drawable.engine_qwant),
        Engine("mojeek", "10", R.string.engine_mojeek, "mojeek.com", "https://www.mojeek.com/search?q=", 0xFF7AB93C.toInt(), "M", false),
        Engine("searx", "6", R.string.engine_searx, "searx.be", "https://searx.be/?q=", 0xFF3050FF.toInt(), "S", false),
    )

    /** The engine stored as [value]; Startpage, the app's default, for anything unknown. */
    @JvmStatic
    fun byValue(value: String?): Engine = ALL.firstOrNull { it.value == value } ?: ALL.first { it.key == "startpage" }

    /** Key of the chosen row: an engine's key, or [CUSTOM]. */
    @JvmStatic
    fun selected(prefs: SharedPreferences): String =
        if (prefs.getBoolean(KEY_CUSTOM_ON, false) && !prefs.getString(KEY_CUSTOM_URL, "").isNullOrBlank()) CUSTOM
        else byValue(prefs.getString(KEY_ENGINE, "0")).key

    /** Chooses the engine with [key]. */
    @JvmStatic
    fun select(prefs: SharedPreferences, key: String) {
        val engine = ALL.firstOrNull { it.key == key } ?: return
        prefs.edit { putString(KEY_ENGINE, engine.value).putBoolean(KEY_CUSTOM_ON, false) }
    }

    /** Chooses the user's own search address; a search is appended to [url]. False if [url] is not an address. */
    @JvmStatic
    fun selectCustom(prefs: SharedPreferences, url: String?): Boolean {
        val address = url?.trim().orEmpty()
        if (!address.startsWith("http://") && !address.startsWith("https://")) return false
        prefs.edit { putString(KEY_CUSTOM_URL, address).putBoolean(KEY_CUSTOM_ON, true) }
        return true
    }

    /** The address that searches for [query] with the engine stored as [value]. */
    @JvmStatic
    fun searchUrl(value: String?, query: String): String = byValue(value).searchUrl + query

    /** The picture in front of [engine]: its logo, or the sign that stands in for one. */
    @JvmStatic
    fun icon(context: Context, engine: Engine): Drawable =
        (if (engine.logo != 0) AppCompatResources.getDrawable(context, engine.logo) else null)
            ?: Logo(engine.color, engine.letter)

    /**
     * A round sign for an engine the app has no logo of: its first letter on
     * the engine's colour.
     */
    class Logo(private val color: Int, private val letter: String) : Drawable() {
        private val circle = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = this@Logo.color }
        private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFFFFFFF.toInt()
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }

        override fun draw(canvas: Canvas) {
            val b = bounds
            val radius = minOf(b.width(), b.height()) / 2f
            canvas.drawCircle(b.exactCenterX(), b.exactCenterY(), radius, circle)
            text.textSize = radius * 1.05f
            canvas.drawText(letter, b.exactCenterX(), b.exactCenterY() - (text.descent() + text.ascent()) / 2f, text)
        }

        override fun setAlpha(alpha: Int) {
            circle.alpha = alpha
            text.alpha = alpha
        }

        override fun setColorFilter(colorFilter: ColorFilter?) {
            circle.colorFilter = colorFilter
        }

        @Deprecated("Deprecated in Java")
        override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
        override fun getIntrinsicWidth(): Int = -1
        override fun getIntrinsicHeight(): Int = -1
    }
}

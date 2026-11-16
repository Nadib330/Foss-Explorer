/*
 * Explorer: the icon of an installed extension.
 * Own code, GPL-3.0.
 *
 * An extension brings its own icon in its manifest; that picture is what the sheets and the
 * settings show. Explorer's puzzle piece stands in when it did not bring one.
 */
package com.rpooi3.explorer.view

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import com.rpooi3.explorer.R
import com.rpooi3.explorer.extension.WebExtension

object ExtensionIcons {

    /** The icon of [extension], or null if the manifest named none that is there. */
    @JvmStatic
    fun drawable(context: Context, extension: WebExtension): Drawable? {
        val file = try {
            extension.iconFile()
        } catch (e: Exception) {
            null
        } ?: return null
        return try {
            val bitmap = BitmapFactory.decodeFile(file.absolutePath) ?: return null
            BitmapDrawable(context.resources, bitmap)
        } catch (e: Exception) {
            null
        }
    }

    /** The icon of [extension], with Explorer's puzzle piece in its place. */
    @JvmStatic
    fun orDefault(context: Context, extension: WebExtension): Drawable? =
        drawable(context, extension)
            ?: androidx.appcompat.content.res.AppCompatResources.getDrawable(context, R.drawable.icon_extension)
}

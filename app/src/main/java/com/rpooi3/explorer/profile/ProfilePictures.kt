/*
 * Explorer: the picture of a profile.
 * Own code, GPL-3.0.
 *
 * A profile can carry a picture of its own, chosen from the user's files. It is copied into the
 * profile's folder (`avatar.png`, square and at most 512 px), so it belongs to that profile alone:
 * deleting the profile deletes its picture with everything else, and two profiles never share one.
 *
 * Nothing is uploaded anywhere — the file is read, squared, shrunk and written, and that is all.
 */
package com.rpooi3.explorer.profile

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.net.Uri
import android.util.Log
import androidx.core.graphics.drawable.RoundedBitmapDrawableFactory
import java.io.File

object ProfilePictures {

    /** The name the picture is kept under, in the folder of its profile. */
    const val FILE = "avatar.png"

    /** The largest side of the copy that is kept. */
    const val SIZE = 512

    private const val TAG = "Profiles"

    /** Where the picture of [profile] is, whether or not one was chosen. */
    @JvmStatic
    fun fileOf(context: Context, profile: BrowserProfile): File =
        File(Profiles.thingsOf(context, profile.id), FILE)

    /** Whether [profile] has a picture of its own. */
    @JvmStatic
    fun has(context: Context, profile: BrowserProfile): Boolean =
        fileOf(context, profile).let { it.isFile && it.length() > 0 }

    /**
     * Copies the picture at [uri] to [profile]. The picture is squared around its middle and
     * shrunk to [SIZE] px, so a 12-megapixel photo does not become a 12-megapixel avatar.
     *
     * @return whether a picture could be read and kept
     */
    @JvmStatic
    fun save(context: Context, profile: BrowserProfile, uri: Uri): Boolean = try {
        val bitmap = read(context, uri) ?: return false
        val square = square(bitmap)
        val file = fileOf(context, profile)
        file.parentFile?.mkdirs()
        file.outputStream().use { out -> square.compress(Bitmap.CompressFormat.PNG, 100, out) }
        if (square !== bitmap) bitmap.recycle()
        true
    } catch (e: Exception) {
        Log.w(TAG, "The picture of " + profile.id + " could not be kept: " + e)
        false
    }

    /**
     * The middle square of the picture at [uri], at most [side] px: what the dialog that names a
     * profile shows before the picture is kept.
     */
    @JvmStatic
    fun squareBitmap(context: Context, uri: Uri, side: Int = SIZE): Bitmap? = try {
        read(context, uri)?.let { square(it, side) }
    } catch (e: Exception) {
        Log.w(TAG, "The chosen picture could not be read: " + e)
        null
    }

    /** Keeps [bitmap] as the picture of [profile] (the picture the dialog was showing). */
    @JvmStatic
    fun save(context: Context, profile: BrowserProfile, bitmap: Bitmap): Boolean = try {
        val file = fileOf(context, profile)
        file.parentFile?.mkdirs()
        file.outputStream().use { out -> bitmap.compress(Bitmap.CompressFormat.PNG, 100, out) }
        true
    } catch (e: Exception) {
        Log.w(TAG, "The picture of " + profile.id + " could not be kept: " + e)
        false
    }

    /** Deletes the picture of [profile]; the profile keeps its letter. */
    @JvmStatic
    fun clear(context: Context, profile: BrowserProfile) {
        try {
            fileOf(context, profile).delete()
        } catch (e: Exception) {
            Log.w(TAG, "The picture of " + profile.id + " could not be deleted: " + e)
        }
    }

    /** The picture of [profile] as a square bitmap of at most [side] px, or null. */
    @JvmStatic
    fun bitmap(context: Context, profile: BrowserProfile, side: Int = SIZE): Bitmap? {
        val file = fileOf(context, profile)
        if (!file.isFile || file.length() <= 0L) return null
        return try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.path, bounds)
            var sample = 1
            while (bounds.outWidth / (sample * 2) >= side && bounds.outHeight / (sample * 2) >= side) sample *= 2
            val options = BitmapFactory.Options().apply { inSampleSize = sample }
            BitmapFactory.decodeFile(file.path, options)
        } catch (e: Exception) {
            Log.w(TAG, "The picture of " + profile.id + " could not be read: " + e)
            null
        }
    }

    /**
     * What stands for [profile] in [sizeDp] px: its picture in a circle if it has one, its letter
     * in a disc of its colour otherwise.
     */
    @JvmStatic
    fun drawable(context: Context, profile: BrowserProfile, sizeDp: Int): Drawable {
        val side = (sizeDp * context.resources.displayMetrics.density).toInt().coerceAtLeast(1)
        val picture = bitmap(context, profile, side)
        if (picture == null) return ProfileAvatarDrawable(profile)
        return RoundedBitmapDrawableFactory.create(context.resources, picture).apply {
            isCircular = true
            setAntiAlias(true)
        }
    }

    /** The same, as a drawable that can be put on a button without being shared with anyone. */
    @JvmStatic
    fun toolbarIcon(context: Context, profile: BrowserProfile, sizeDp: Int = 26): Drawable =
        drawable(context, profile, sizeDp)

    // Reading and squaring -------------------------------------------------------------------

    private fun read(context: Context, uri: Uri): Bitmap? = try {
        context.contentResolver.openInputStream(uri)?.use { stream ->
            // Read the bounds first, so that a large file is not loaded to its full size.
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            val bytes = stream.readBytes()
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            var sample = 1
            while (bounds.outWidth / (sample * 2) >= SIZE && bounds.outHeight / (sample * 2) >= SIZE) sample *= 2
            BitmapFactory.decodeByteArray(
                bytes,
                0,
                bytes.size,
                BitmapFactory.Options().apply { inSampleSize = sample },
            )
        }
    } catch (e: Exception) {
        Log.w(TAG, "The chosen picture could not be read: " + e)
        null
    }

    /** The middle square of [bitmap], shrunk to at most [side] px. */
    private fun square(bitmap: Bitmap, side: Int = SIZE): Bitmap {
        val edge = minOf(bitmap.width, bitmap.height)
        val source = Rect(
            (bitmap.width - edge) / 2,
            (bitmap.height - edge) / 2,
            (bitmap.width + edge) / 2,
            (bitmap.height + edge) / 2,
        )
        val target = minOf(edge, side, SIZE).coerceAtLeast(1)
        val out = Bitmap.createBitmap(target, target, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        canvas.drawBitmap(bitmap, source, Rect(0, 0, target, target), Paint(Paint.FILTER_BITMAP_FLAG))
        return out
    }

    /** The letter disc, as a plain drawable (used by tests and by the toolbar before a picture). */
    @JvmStatic
    fun letterDisc(profile: BrowserProfile): Drawable = ProfileAvatarDrawable(profile)

    /** A drawable around [bitmap], for callers that already have one. */
    @JvmStatic
    fun circular(context: Context, bitmap: Bitmap): Drawable =
        RoundedBitmapDrawableFactory.create(context.resources, bitmap).apply { isCircular = true }
}

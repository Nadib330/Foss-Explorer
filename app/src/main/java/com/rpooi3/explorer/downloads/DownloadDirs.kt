/*
 * Explorer: where the built-in download manager puts its files.
 * Own code, GPL-3.0.
 *
 * 18.3.0 beta (item 12): the folder of Settings > Downloads. Unless the user chose another one,
 * it is the browser's own Download folder,
 *
 *     /storage/emulated/0/Android/data/com.rpooi3.explorer/files/Download
 *
 * which needs no permission of any kind, which the downloads screen of Explorer can read, and
 * which the system's file picker shows. A folder of the user's own is kept as the tree the picker
 * hands out; the file is written into it through that tree.
 */
package com.rpooi3.explorer.downloads

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import androidx.core.content.edit
import androidx.documentfile.provider.DocumentFile
import java.io.File

object DownloadDirs {

    /** The tree of a folder the user chose, if he chose one. */
    const val KEY_TREE = "download_dir_tree"

    /** The name of the folder the app keeps its downloads in. */
    const val FOLDER = "Download"

    /** `.../Android/data/com.rpooi3.explorer/files/Download`, as the settings show it. */
    @JvmStatic
    fun defaultPath(context: Context): String = File(defaultDir(context), "").absolutePath
        .trimEnd('/')

    @JvmStatic
    fun defaultDir(context: Context): File =
        File(context.getExternalFilesDir(null), FOLDER).apply { if (!isDirectory) mkdirs() }

    @JvmStatic
    fun tree(context: Context): Uri? = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        .getString(KEY_TREE, null)?.let { runCatching { Uri.parse(it) }.getOrNull() }

    @JvmStatic
    fun setTree(context: Context, uri: Uri?) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit {
            if (uri == null) remove(KEY_TREE) else putString(KEY_TREE, uri.toString())
        }
    }

    /** The folder the downloads go to now, in the words of the settings screen. */
    @JvmStatic
    fun path(context: Context): String {
        val tree = tree(context) ?: return defaultPath(context)
        val name = runCatching {
            DocumentsContract.getTreeDocumentId(tree)?.substringAfterLast(':')
        }.getOrNull().orEmpty()
        return if (name.isEmpty()) tree.toString() else "/$name"
    }

    /**
     * Writes [part] into the download folder under [name]; the answer says where the file is.
     *
     * @return the URI of the file, and the path if it lies in a folder of the file system
     */
    @JvmStatic
    fun publish(context: Context, name: String, part: File, mimeType: String?): Pair<String?, String?> {
        val tree = tree(context)
        if (tree != null) {
            val folder = DocumentFile.fromTreeUri(context, tree)
            if (folder != null && folder.isDirectory) {
                val file = folder.findFile(name)?.takeIf { it.isFile }
                    ?: folder.createFile(mimeType ?: "application/octet-stream", name)
                if (file != null) {
                    context.contentResolver.openOutputStream(file.uri)?.use { out ->
                        part.inputStream().use { input -> input.copyTo(out) }
                    }
                    part.delete()
                    return file.uri.toString() to null
                }
            }
        }
        val dir = defaultDir(context)
        var target = File(dir, name)
        // A second file of the same name is kept beside the first, as Chromium numbers them.
        if (target.exists()) {
            var index = 1
            val dot = name.lastIndexOf('.')
            val base = if (dot > 0) name.substring(0, dot) else name
            val extension = if (dot > 0) name.substring(dot) else ""
            while (target.exists() && index < 1000) {
                target = File(dir, "$base ($index)$extension")
                index += 1
            }
        }
        try {
            val moved = part.renameTo(target)
            if (!moved) {
                part.inputStream().use { input -> target.outputStream().use { input.copyTo(it) } }
                part.delete()
            }
        } catch (e: Exception) {
            part.inputStream().use { input -> target.outputStream().use { input.copyTo(it) } }
            part.delete()
        }
        return null to target.absolutePath
    }

    private const val PREFS = "download_dirs"
}

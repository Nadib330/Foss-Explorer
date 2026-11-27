package com.rpooi3.explorer.downloads

import android.net.Uri
import java.util.Locale

/** One file on the downloads screen. */
data class DownloadItem(
    /** Id in the system's download manager, or in [DownloadLog] if [fromManager] is false. */
    val id: Long,
    val fromManager: Boolean,
    val name: String,
    val mimeType: String?,
    /** Size of the whole file in bytes, or -1 while it is not known. */
    val totalBytes: Long,
    val downloadedBytes: Long,
    /** When the download finished or last made progress, in milliseconds. */
    val time: Long,
    val state: State,
    /** The web address the file came from, if it came from one. */
    val sourceUrl: String?,
    /** Where the file can be opened from, once it is complete. */
    val uri: Uri?,
    /** 18.3.0 beta (item 9): true for a download of the built-in manager ([DownloadStore]). */
    val engine: Boolean = false,
) {
    enum class State { RUNNING, WAITING, PAUSED, FAILED, DONE }

    val kind: Kind
        get() = Kind.of(mimeType, name)

    /** 18.3.0 beta (item 8): an app package, which tapping installs. */
    val isPackage: Boolean
        get() = ApkInstaller.isPackage(name) || mimeType == ApkInstaller.MIME_APK

    /** Progress in percent, or null while the size is unknown. */
    val percent: Int?
        get() = if (totalBytes > 0) ((downloadedBytes.coerceIn(0, totalBytes) * 100) / totalBytes).toInt() else null

    /** What kind of file it is; the downloads screen filters by this. */
    enum class Kind {
        IMAGE, VIDEO, AUDIO, DOCUMENT, OTHER;

        companion object {
            private val imageExtensions = setOf("jpg", "jpeg", "png", "gif", "webp", "bmp", "svg", "heic", "avif", "ico")
            private val videoExtensions = setOf("mp4", "mkv", "webm", "avi", "mov", "3gp", "m4v", "ts")
            private val audioExtensions = setOf("mp3", "ogg", "oga", "opus", "wav", "flac", "m4a", "aac", "mid")
            private val documentExtensions = setOf(
                "pdf", "txt", "htm", "html", "mht", "mhtml", "md", "csv", "json", "xml", "epub", "rtf",
                "doc", "docx", "odt", "xls", "xlsx", "ods", "ppt", "pptx", "odp",
            )
            private val documentTypes = setOf(
                "application/pdf", "application/json", "application/xml", "application/rtf",
                "application/epub+zip", "application/msword", "application/xhtml+xml",
            )

            /**
             * The MIME type decides, if it says anything; "application/octet-stream" and a
             * missing type say nothing, and then the file extension decides.
             */
            @JvmStatic
            fun of(mimeType: String?, fileName: String?): Kind {
                val type = mimeType?.substringBefore(';')?.trim()?.lowercase(Locale.ROOT).orEmpty()
                when {
                    type.startsWith("image/") -> return IMAGE
                    type.startsWith("video/") -> return VIDEO
                    type.startsWith("audio/") -> return AUDIO
                    type.startsWith("text/") || type in documentTypes -> return DOCUMENT
                    type.startsWith("application/vnd.openxmlformats-officedocument") -> return DOCUMENT
                    type.startsWith("application/vnd.oasis.opendocument") -> return DOCUMENT
                    type.startsWith("application/vnd.ms-") -> return DOCUMENT
                }
                return when (fileName?.substringAfterLast('.', "")?.lowercase(Locale.ROOT).orEmpty()) {
                    in imageExtensions -> IMAGE
                    in videoExtensions -> VIDEO
                    in audioExtensions -> AUDIO
                    in documentExtensions -> DOCUMENT
                    else -> OTHER
                }
            }
        }
    }
}

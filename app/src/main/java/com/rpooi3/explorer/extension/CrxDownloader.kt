/*
 * Explorer: fetches a .crx from the Chrome Web Store.
 * Own code, GPL-3.0.
 *
 * The store's own install button belongs to Chrome, so Explorer asks the update service the way
 * Chrome does: the id of the extension in `x=id%3D<id>%26uc` and a desktop Chrome in the user
 * agent, and the answer is a redirect to the .crx. The file goes to the cache, is unpacked by
 * [CrxExtractor] and then deleted.
 */
package com.rpooi3.explorer.extension

import android.content.Context
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

object CrxDownloader {

    private const val TAG = "ExplorerExtensions"

    /**
     * The update service, as the Chrome Web Store's install button uses it.
     *
     * The id goes into `x=id%3D<id>%26uc` already encoded, so the address is put together by
     * hand: `String.format` would read the `%3D` and the `%26` of it as format specifiers and
     * throw instead of downloading anything (that is what 17.3.2 fixes).
     */
    private const val UPDATE_URL =
        "https://clients2.google.com/service/update2/crx?response=redirect&prodversion=130.0.0.0" +
            "&acceptformat=crx2,crx3&x=id%3D"

    private const val USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) " +
            "Chrome/130.0.0.0 Safari/537.36"

    /** How big a .crx may be, so that a broken answer cannot fill the phone. */
    private const val MAX_BYTES = 64L * 1024 * 1024

    /** How long to wait for the store. */
    private const val TIMEOUT_MS = 30_000

    /** An id is 32 letters from a to p; it is in any address of the store. */
    private val ID_PATTERN = Regex("([a-p]{32})")

    /** The addresses Chrome Web Store uses, so that a page of the store can be told apart. */
    private val STORE_HOSTS = listOf("chrome.google.com", "chromewebstore.google.com")

    /** The id of the extension in [storeUrl] (or in any text), or null. */
    @JvmStatic
    fun extractExtensionId(storeUrl: String?): String? {
        val text = storeUrl?.trim().orEmpty()
        if (text.isEmpty()) return null
        return ID_PATTERN.find(text)?.groupValues?.get(1)
    }

    /** Whether [url] is a page of the Chrome Web Store. */
    @JvmStatic
    fun isStoreUrl(url: String?): Boolean {
        if (url.isNullOrBlank()) return false
        val address = try {
            URL(url).host?.lowercase(java.util.Locale.US)
        } catch (e: Exception) {
            return false
        } ?: return false
        return STORE_HOSTS.any { address == it || address.endsWith("." + it) }
    }

    /** Whether [url] is a .crx download rather than a page. */
    @JvmStatic
    fun isCrxUrl(url: String?): Boolean {
        val text = url?.lowercase(java.util.Locale.US) ?: return false
        return text.contains(".crx") || text.contains("/service/update2/crx")
    }

    /** The address of the detail page of [extensionId], used by the extension settings. */
    @JvmStatic
    fun storePage(extensionId: String): String =
        "https://chromewebstore.google.com/detail/" + extensionId

    /** Where the .crx of [extensionId] comes from: the update service of the store. */
    @JvmStatic
    fun updateUrl(extensionId: String): String = UPDATE_URL + extensionId + "%26uc"

    /**
     * Downloads [extensionId] to `cacheDir/<id>.crx`.
     *
     * @throws IOException if the store has no such extension or the answer is not a CRX
     */
    @JvmStatic
    @Throws(IOException::class)
    fun downloadCrx(context: Context, extensionId: String): File {
        if (!extensionId.matches(Regex("[a-p]{32}"))) throw IOException("Not an extension id")
        val target = File(context.cacheDir, "$extensionId.crx")
        return try {
            downloadCrx(extensionId, target)
            target
        } catch (e: IOException) {
            target.delete()
            throw e
        }
    }

    /**
     * Downloads [extensionId] to [target], following the redirect of the update service.
     *
     * The redirect goes to another host (`clients2.googleusercontent.com`), which the WebView's
     * cookies and the connection of Android do not always follow on their own, so every 301, 302,
     * 303, 307 and 308 is read and followed here.
     */
    @JvmStatic
    @Throws(IOException::class)
    fun downloadCrx(extensionId: String, target: File): File {
        var address = updateUrl(extensionId)
        var hop = 0
        while (true) {
            var connection: HttpURLConnection? = null
            try {
                connection = (URL(address).openConnection() as HttpURLConnection).apply {
                    instanceFollowRedirects = false
                    connectTimeout = TIMEOUT_MS
                    readTimeout = TIMEOUT_MS
                    setRequestProperty("User-Agent", USER_AGENT)
                    setRequestProperty("Accept", "*/*")
                }
                val code = connection.responseCode
                if (code == HttpURLConnection.HTTP_MOVED_PERM || code == HttpURLConnection.HTTP_MOVED_TEMP ||
                    code == HttpURLConnection.HTTP_SEE_OTHER || code == 307 || code == 308
                ) {
                    val location = connection.getHeaderField("Location")
                    if (location.isNullOrBlank() || ++hop > 5) {
                        throw IOException("The store's redirect goes nowhere")
                    }
                    address = URL(URL(address), location).toString()
                    Log.d(TAG, "Following the redirect to " + address.take(120))
                    continue
                }
                if (code != HttpURLConnection.HTTP_OK) {
                    throw IOException("The Chrome Web Store answered " + code)
                }
                target.parentFile?.mkdirs()
                var total = 0L
                connection.inputStream.use { input ->
                    FileOutputStream(target).use { out ->
                        val buffer = ByteArray(16 * 1024)
                        while (true) {
                            val count = input.read(buffer)
                            if (count < 0) break
                            total += count
                            if (total > MAX_BYTES) throw IOException("The CRX is too large")
                            out.write(buffer, 0, count)
                        }
                    }
                }
                if (total == 0L) throw IOException("The store sent nothing")
                if (!CrxExtractor.isCrx(target)) {
                    // A saved .crx the store refused, or a page instead of a file.
                    Log.w(TAG, "The answer for " + extensionId + " is not a CRX (" + total + " bytes)")
                    throw IOException("The store did not send a CRX file")
                }
                return target
            } finally {
                connection?.disconnect()
            }
        }
    }

    /**
     * Downloads, unpacks and adds [extensionId] in one step.
     *
     * @return the extension, or null on failure; [error] says why in words a screen can show
     */
    @JvmStatic
    fun install(context: Context, extensionId: String, error: ((String) -> Unit)? = null): WebExtension? {
        return installFrom(
            context = context,
            extensionId = extensionId,
            download = { id, target -> downloadCrx(id, target) },
            error = error,
        )
    }

    /**
     * The part of [install] worth testing: how the download is made is up to [download].
     */
    @JvmStatic
    fun installFrom(
        context: Context,
        extensionId: String,
        download: (String, File) -> File,
        error: ((String) -> Unit)? = null,
    ): WebExtension? {
        if (extractExtensionId(extensionId) == null) {
            error?.invoke("Not an extension id")
            return null
        }
        val id = extractExtensionId(extensionId)!!
        val crx = File(context.cacheDir, "$id.crx")
        return try {
            download(id, crx)
            val installed = ExtensionStore.install(context, crx, id)
            if (installed == null) error?.invoke("The manifest could not be read")
            installed
        } catch (e: IOException) {
            Log.w(TAG, "Cannot download " + id + ": " + e)
            error?.invoke(e.message ?: "The download failed")
            null
        } catch (e: Exception) {
            Log.w(TAG, "Cannot install " + id + ": " + e)
            error?.invoke("The extension could not be installed")
            null
        } finally {
            crx.delete()
        }
    }
}

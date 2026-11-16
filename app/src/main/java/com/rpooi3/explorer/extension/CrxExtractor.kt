/*
 * Explorer: unpacks a .crx from the Chrome Web Store.
 * Own code, GPL-3.0.
 *
 * A .crx is a ZIP archive with a header in front of it:
 *
 *   "Cr24" | version (4 bytes, little endian) | ...
 *
 *   CRX3: 4-byte length of the protobuf header, the header itself, then the ZIP.
 *   CRX2: 4-byte length of the public key, 4-byte length of the signature, both, then the ZIP.
 *
 * Explorer does not check the signature (the Android System WebView has no key of Chrome's Web
 * Store), so what is unpacked here is exactly what the store served. The delete/overwrite rules
 * of the ZIP are not followed either: everything is written below the extension's own folder,
 * and an entry that would leave it is refused.
 */
package com.rpooi3.explorer.extension

import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.util.zip.ZipInputStream

object CrxExtractor {

    private const val MAGIC = "Cr24"

    /** How much may be unpacked, so that a hostile archive cannot fill the phone. */
    private const val MAX_ENTRIES = 5_000
    private const val MAX_BYTES = 64L * 1024 * 1024

    /**
     * Writes the files of [crxFile] below [destDir], which is emptied first.
     *
     * @throws IOException if the file is no CRX, no ZIP, or tries to leave [destDir]
     */
    @JvmStatic
    @Throws(IOException::class)
    fun unpack(crxFile: File, destDir: File) {
        destDir.deleteRecursively()
        if (!destDir.mkdirs()) throw IOException("Cannot create " + destDir.absolutePath)

        val stream = BufferedInputStream(crxFile.inputStream(), 8192)
        stream.use { input ->
            val zipStart = skipHeader(input)
            if (zipStart < 0) throw IOException("Not a CRX file")
            unzip(input, destDir)
        }
    }

    /**
     * Reads past the CRX header and returns how many bytes it was; -1 if the file does not begin
     * with "Cr24". The stream stands at the first byte of the ZIP archive afterwards.
     */
    @JvmStatic
    @Throws(IOException::class)
    fun skipHeader(input: InputStream): Long {
        val magic = ByteArray(4)
        readFully(input, magic)
        if (String(magic, Charsets.US_ASCII) != MAGIC) return -1
        val version = readInt(input)
        if (version == 3) {
            val headerLength = readInt(input)
            if (headerLength < 0 || headerLength > 4 * 1024 * 1024) throw IOException("Bad CRX3 header")
            skipFully(input, headerLength.toLong())
            return 12L + headerLength
        }
        if (version == 2) {
            val keyLength = readInt(input)
            val signatureLength = readInt(input)
            if (keyLength < 0 || signatureLength < 0 || keyLength + signatureLength > 4 * 1024 * 1024) {
                throw IOException("Bad CRX2 header")
            }
            skipFully(input, (keyLength + signatureLength).toLong())
            return 16L + keyLength + signatureLength
        }
        throw IOException("Unknown CRX version " + version)
    }

    /** Whether [file] begins like a CRX archive. */
    @JvmStatic
    fun isCrx(file: File): Boolean = try {
        file.inputStream().use { input ->
            val magic = ByteArray(4)
            val read = input.read(magic)
            read == 4 && String(magic, Charsets.US_ASCII) == MAGIC
        }
    } catch (e: IOException) {
        false
    }

    private fun readInt(input: InputStream): Int {
        val bytes = ByteArray(4)
        readFully(input, bytes)
        // Little endian, as Chrome writes it.
        return (bytes[0].toInt() and 0xFF) or
            ((bytes[1].toInt() and 0xFF) shl 8) or
            ((bytes[2].toInt() and 0xFF) shl 16) or
            ((bytes[3].toInt() and 0xFF) shl 24)
    }

    private fun readFully(input: InputStream, buffer: ByteArray) {
        var read = 0
        while (read < buffer.size) {
            val count = input.read(buffer, read, buffer.size - read)
            if (count < 0) throw IOException("Unexpected end of the CRX file")
            read += count
        }
    }

    private fun skipFully(input: InputStream, count: Long) {
        var left = count
        while (left > 0) {
            val skipped = input.skip(left)
            if (skipped > 0) {
                left -= skipped
                continue
            }
            // skip() may return 0 before the end: read instead.
            if (input.read() < 0) throw IOException("Unexpected end of the CRX file")
            left--
        }
    }

    @Throws(IOException::class)
    private fun unzip(input: InputStream, destDir: File) {
        val canonicalDest = destDir.canonicalPath
        var written = 0L
        var entries = 0
        ZipInputStream(input).use { zip ->
            var buffer = ByteArray(8192)
            while (true) {
                val entry = zip.nextEntry ?: break
                if (++entries > MAX_ENTRIES) throw IOException("Too many files in the CRX")
                val outFile = File(destDir, entry.name)
                // Zip slip: an entry may not leave the folder of the extension.
                if (!outFile.canonicalPath.startsWith(canonicalDest + File.separator)) {
                    throw SecurityException("Blocked Zip Slip entry: " + entry.name)
                }
                if (entry.isDirectory) {
                    outFile.mkdirs()
                } else {
                    outFile.parentFile?.mkdirs()
                    FileOutputStream(outFile).use { out ->
                        while (true) {
                            val count = zip.read(buffer)
                            if (count < 0) break
                            written += count
                            if (written > MAX_BYTES) throw IOException("The CRX is too large")
                            out.write(buffer, 0, count)
                        }
                    }
                }
                zip.closeEntry()
            }
        }
    }
}

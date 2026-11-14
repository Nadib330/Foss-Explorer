/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 *
 * Kotlin port of components/places/src/hash.rs of Mozilla's
 * application-services (commit 5f95eb6), the code Firefox for Android hashes
 * addresses with for the column moz_places.url_hash. Ported for Foss Explorer;
 * the original is in third_party/application-services.
 */
package com.rpooi3.explorer.database.places

/** Firefox's hash of a page address, as stored in `moz_places.url_hash`. */
object PlacesHash {
    private const val MAX_BYTES_TO_HASH = 1500
    private const val GOLDEN_RATIO = 0x9E3779B9.toInt()

    private fun addToHash(hash: Int, value: Int): Int = (Integer.rotateLeft(hash, 5) xor value) * GOLDEN_RATIO

    /** Gecko's `HashString` over the UTF-8 bytes of [bytes], as an unsigned 32-bit value. */
    private fun hashBytes(bytes: ByteArray, length: Int): Long {
        var hash = 0
        for (i in 0 until length) hash = addToHash(hash, bytes[i].toInt() and 0xFF)
        return hash.toLong() and 0xFFFFFFFFL
    }

    @JvmStatic
    fun hashString(string: String): Long = string.toByteArray(Charsets.UTF_8).let { hashBytes(it, it.size) }

    /**
     * The hash of the address [spec]: the lower 32 bits are the hash of the
     * address, the 16 bits above them the hash of its scheme ("https"), so
     * that addresses of one scheme lie together.
     */
    @JvmStatic
    fun hashUrl(spec: String): Long {
        val bytes = spec.toByteArray(Charsets.UTF_8)
        val hash = hashBytes(bytes, minOf(bytes.size, MAX_BYTES_TO_HASH))
        val head = minOf(bytes.size, 50)
        val colon = (0 until head).firstOrNull { bytes[it] == ':'.code.toByte() } ?: return hash
        val prefix = hashBytes(bytes, colon) and 0x0000FFFFL
        return (prefix shl 32) + hash
    }
}

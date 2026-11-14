package com.rpooi3.explorer.compat

import android.Manifest
import android.app.Activity
import android.app.Application
import android.os.Environment
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
class StorageCompatTest {

    private val application: Application = ApplicationProvider.getApplicationContext()

    @Test
    @Config(sdk = [28])
    fun android9_requestsStoragePermission_whenMissing() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()

        assertFalse(StorageCompat.ensureDownloadsWritable(activity))

        val request = shadowOf(activity).lastRequestedPermission
        assertArrayEquals(
            arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE),
            request.requestedPermissions
        )
    }

    @Test
    @Config(sdk = [28])
    fun android9_proceeds_oncePermissionIsGranted() {
        shadowOf(application).grantPermissions(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()

        assertTrue(StorageCompat.ensureDownloadsWritable(activity))
    }

    @Test
    @Config(sdk = [34])
    fun android14_needsNoPermission() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()

        assertTrue(StorageCompat.ensureDownloadsWritable(activity))
        assertEquals(null, shadowOf(activity).lastRequestedPermission)
    }

    @Test
    @Config(sdk = [26])
    fun android8_writesIntoPublicDownloads() {
        val payload = "hello explorer".toByteArray()

        StorageCompat.writeToDownloads(application, "note.txt", "text/plain", payload)

        @Suppress("DEPRECATION")
        val downloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        assertArrayEquals(payload, File(downloads, "note.txt").readBytes())
    }
}

package com.example

import android.Manifest
import com.example.security.RuntimePermissionContract
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RuntimePermissionContractTest {
    @Test
    fun mapsEveryAllowlistedRuntimePermissionToItsAndroidPermission() {
        assertEquals(Manifest.permission.POST_NOTIFICATIONS,
            RuntimePermissionContract.resolve("POST_NOTIFICATIONS", 34)?.platformPermission)
        assertEquals(Manifest.permission.CAMERA,
            RuntimePermissionContract.resolve("CAMERA", 34)?.platformPermission)
        assertEquals(Manifest.permission.ACCESS_FINE_LOCATION,
            RuntimePermissionContract.resolve("ACCESS_FINE_LOCATION", 34)?.platformPermission)
        assertEquals(Manifest.permission.READ_MEDIA_IMAGES,
            RuntimePermissionContract.resolve("READ_MEDIA_IMAGES", 34)?.platformPermission)
    }

    @Test
    fun mapsMediaPermissionToLegacyRuntimePermissionAndNotificationsAreNotRequired() {
        assertEquals(Manifest.permission.READ_EXTERNAL_STORAGE,
            RuntimePermissionContract.resolve("READ_MEDIA_IMAGES", 32)?.platformPermission)
        assertNull(RuntimePermissionContract.resolve("POST_NOTIFICATIONS", 32)?.platformPermission)
        assertTrue(RuntimePermissionContract.resolve("POST_NOTIFICATIONS", 32) != null)
    }

    @Test
    fun olderSupportedAndroidVersionsDoNotRequestNotificationPermission() {
        assertNull(RuntimePermissionContract.resolve("POST_NOTIFICATIONS", 26)?.platformPermission)
        assertEquals(Manifest.permission.CAMERA,
            RuntimePermissionContract.resolve("CAMERA", 26)?.platformPermission)
    }

    @Test
    fun rejectsAnythingOutsideTheExplicitRecipeAllowlist() {
        assertFalse(RuntimePermissionContract.isAllowlisted("READ_CONTACTS"))
        assertNull(RuntimePermissionContract.resolve("android.permission.READ_CONTACTS", 34))
        assertNull(RuntimePermissionContract.resolve("", 34))
    }
}

package com.example.security

import android.Manifest
import android.os.Build

data class RuntimePermissionSpec(
    val recipeName: String,
    val platformPermission: String?
) {
    val isRequiredAtRuntime: Boolean get() = platformPermission != null
}

/**
 * The complete runtime-permission surface allowed by repair-recipe-v1.
 * Unknown recipe strings never pass through to Android's permission APIs.
 */
object RuntimePermissionContract {
    private val supportedNames = setOf(
        "POST_NOTIFICATIONS",
        "CAMERA",
        "ACCESS_FINE_LOCATION",
        "READ_MEDIA_IMAGES"
    )

    fun isAllowlisted(recipeName: String): Boolean = recipeName in supportedNames

    fun resolve(recipeName: String, sdkInt: Int = Build.VERSION.SDK_INT): RuntimePermissionSpec? {
        if (!isAllowlisted(recipeName)) return null
        val platformPermission = when (recipeName) {
            "POST_NOTIFICATIONS" -> if (sdkInt >= Build.VERSION_CODES.TIRAMISU) {
                Manifest.permission.POST_NOTIFICATIONS
            } else null
            "CAMERA" -> Manifest.permission.CAMERA
            "ACCESS_FINE_LOCATION" -> Manifest.permission.ACCESS_FINE_LOCATION
            "READ_MEDIA_IMAGES" -> if (sdkInt >= Build.VERSION_CODES.TIRAMISU) {
                Manifest.permission.READ_MEDIA_IMAGES
            } else {
                Manifest.permission.READ_EXTERNAL_STORAGE
            }
            else -> return null
        }
        return RuntimePermissionSpec(recipeName, platformPermission)
    }
}

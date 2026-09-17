package com.jarvis.gensoftlab.agent

import android.content.Context
import android.os.Build
import com.google.gson.Gson
import com.jarvis.gensoftlab.permissions.PermissionCoordinator

enum class CapabilityStatus {
    FULL,
    PARTIAL,
    UNAVAILABLE,
    PERMISSION_REQUIRED,
    SPECIAL_ACCESS_REQUIRED,
    USER_INTERACTION_REQUIRED,
    DEVICE_OWNER_REQUIRED,
    API_LEVEL_REQUIRED,
    UNSUPPORTED
}

enum class PlatformFeature {
    ACCESSIBILITY,
    SCREEN_CAPTURE,
    NOTIFICATION_ACCESS,
    OVERLAY,
    MICROPHONE,
    CAMERA,
    CONTACTS,
    PHONE,
    SMS,
    LOCATION,
    EXACT_ALARM,
    APP_DISCOVERY,
    FILE_ACCESS,
    MEDIA_CONTROL,
    DEVICE_OWNER_FEATURE,
    UNKNOWN_PLATFORM_FEATURE,
    POST_NOTIFICATIONS
}

data class CapabilityInfo(
    val capability: String,
    val status: String,
    val available: Boolean
)

object CapabilityManager {

    fun getDeviceApiLevel(): Int = Build.VERSION.SDK_INT

    fun getCapabilityStatus(context: Context, feature: PlatformFeature): CapabilityStatus {
        return when (feature) {
            PlatformFeature.ACCESSIBILITY -> {
                if (PermissionCoordinator.isAccessibilityServiceEnabled(context)) CapabilityStatus.FULL
                else CapabilityStatus.SPECIAL_ACCESS_REQUIRED
            }
            PlatformFeature.SCREEN_CAPTURE -> {
                CapabilityStatus.USER_INTERACTION_REQUIRED
            }
            PlatformFeature.MICROPHONE -> {
                if (PermissionCoordinator.hasRuntimePermission(context, "android.permission.RECORD_AUDIO")) CapabilityStatus.FULL
                else CapabilityStatus.PERMISSION_REQUIRED
            }
            PlatformFeature.POST_NOTIFICATIONS -> {
                if (Build.VERSION.SDK_INT < 33) {
                    CapabilityStatus.UNSUPPORTED
                } else {
                    if (PermissionCoordinator.hasRuntimePermission(context, "android.permission.POST_NOTIFICATIONS")) CapabilityStatus.FULL
                    else CapabilityStatus.PERMISSION_REQUIRED
                }
            }
            PlatformFeature.EXACT_ALARM -> {
                CapabilityStatus.SPECIAL_ACCESS_REQUIRED
            }
            PlatformFeature.OVERLAY -> {
                if (PermissionCoordinator.isOverlayPermissionGranted(context)) CapabilityStatus.FULL
                else CapabilityStatus.SPECIAL_ACCESS_REQUIRED
            }
            PlatformFeature.DEVICE_OWNER_FEATURE -> {
                CapabilityStatus.DEVICE_OWNER_REQUIRED
            }
            PlatformFeature.UNKNOWN_PLATFORM_FEATURE -> {
                CapabilityStatus.UNSUPPORTED
            }
            PlatformFeature.CAMERA -> {
                if (PermissionCoordinator.hasRuntimePermission(context, "android.permission.CAMERA")) CapabilityStatus.FULL
                else CapabilityStatus.PERMISSION_REQUIRED
            }
            PlatformFeature.CONTACTS -> {
                if (PermissionCoordinator.hasRuntimePermission(context, "android.permission.READ_CONTACTS")) CapabilityStatus.FULL
                else CapabilityStatus.PERMISSION_REQUIRED
            }
            PlatformFeature.PHONE -> {
                if (PermissionCoordinator.hasRuntimePermission(context, "android.permission.CALL_PHONE")) CapabilityStatus.FULL
                else CapabilityStatus.PERMISSION_REQUIRED
            }
            PlatformFeature.NOTIFICATION_ACCESS -> {
                if (PermissionCoordinator.isNotificationListenerEnabled(context)) CapabilityStatus.FULL
                else CapabilityStatus.SPECIAL_ACCESS_REQUIRED
            }
            else -> CapabilityStatus.UNAVAILABLE
        }
    }

    fun getCapabilityMetadataJson(context: Context): String {
        val metadata = PlatformFeature.entries.associate {
            it.name to (getCapabilityStatus(context, it) == CapabilityStatus.FULL)
        }
        return Gson().toJson(metadata)
    }
}

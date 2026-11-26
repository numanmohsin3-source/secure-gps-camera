package com.securegps.camera.security

import android.os.Build
import android.os.Debug
import java.io.File

data class SecurityPosture(
    val isRootSuspected: Boolean,
    val isDebuggerAttached: Boolean,
    val isEmulatorSuspected: Boolean
)

object DeviceSecurityMonitor {

    fun inspect(): SecurityPosture {
        return SecurityPosture(
            isRootSuspected = checkRootIndicators(),
            isDebuggerAttached = Debug.isDebuggerConnected() || Debug.waitingForDebugger(),
            isEmulatorSuspected = checkEmulatorIndicators()
        )
    }

    private fun checkRootIndicators(): Boolean {
        val paths = listOf(
            "/system/app/Superuser.apk",
            "/sbin/su",
            "/system/bin/su",
            "/system/xbin/su",
            "/data/local/xbin/su",
            "/data/local/bin/su",
            "/system/sd/xbin/su",
            "/data/local/su",
            "/su/bin/su"
        )
        if (paths.any { File(it).exists() }) return true
        val tags = Build.TAGS
        return tags != null && tags.contains("test-keys")
    }

    private fun checkEmulatorIndicators(): Boolean {
        return (Build.BRAND.startsWith("generic") && Build.DEVICE.startsWith("generic")) ||
            Build.FINGERPRINT.startsWith("generic") ||
            Build.FINGERPRINT.startsWith("unknown") ||
            Build.HARDWARE.contains("goldfish") ||
            Build.HARDWARE.contains("ranchu") ||
            Build.MODEL.contains("google_sdk") ||
            Build.MODEL.contains("Emulator") ||
            Build.MODEL.contains("Android SDK built for x86") ||
            Build.PRODUCT.contains("sdk_gphone")
    }
}

package com.securegps.camera.security

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

sealed class PinVerifyResult {
    data object Success : PinVerifyResult()
    data class WrongPin(val attemptsRemaining: Int) : PinVerifyResult()
    data class LockedOut(val secondsRemaining: Int) : PinVerifyResult()
}

class SecurityPreferences(context: Context) {

    private val masterKey: MasterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs: SharedPreferences = EncryptedSharedPreferences.create(
        context,
        "secure_gps_prefs",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    fun hasPinConfigured(): Boolean {
        return !prefs.getString(KEY_PIN_HASH, null).isNullOrBlank()
    }

    fun setPin(pin: String) {
        require(pin.length in 4..6 && pin.all { it.isDigit() }) {
            "PIN must be 4 to 6 digits"
        }
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val hash = pbkdf2(pin, salt)
        prefs.edit()
            .putString(KEY_PIN_SALT, Base64.encodeToString(salt, Base64.NO_WRAP))
            .putString(KEY_PIN_HASH, Base64.encodeToString(hash, Base64.NO_WRAP))
            .putInt(KEY_FAILED_ATTEMPTS, 0)
            .putLong(KEY_LOCKOUT_UNTIL, 0L)
            .apply()
    }

    fun verifyPin(pin: String): PinVerifyResult {
        val now = System.currentTimeMillis()
        val lockoutUntil = prefs.getLong(KEY_LOCKOUT_UNTIL, 0L)
        if (now < lockoutUntil) {
            val sec = ((lockoutUntil - now) / 1000L).toInt().coerceAtLeast(1)
            return PinVerifyResult.LockedOut(sec)
        }

        val saltB64 = prefs.getString(KEY_PIN_SALT, null) ?: return PinVerifyResult.WrongPin(MAX_ATTEMPTS)
        val hashB64 = prefs.getString(KEY_PIN_HASH, null) ?: return PinVerifyResult.WrongPin(MAX_ATTEMPTS)

        val salt = Base64.decode(saltB64, Base64.NO_WRAP)
        val expectedHash = Base64.decode(hashB64, Base64.NO_WRAP)
        val candidateHash = pbkdf2(pin, salt)

        return if (MessageDigest.isEqual(expectedHash, candidateHash)) {
            prefs.edit()
                .putInt(KEY_FAILED_ATTEMPTS, 0)
                .putLong(KEY_LOCKOUT_UNTIL, 0L)
                .apply()
            PinVerifyResult.Success
        } else {
            val attempts = prefs.getInt(KEY_FAILED_ATTEMPTS, 0) + 1
            if (attempts >= MAX_ATTEMPTS) {
                val penaltyMs = if (attempts >= MAX_ATTEMPTS * 2) 120_000L else 30_000L
                prefs.edit()
                    .putInt(KEY_FAILED_ATTEMPTS, attempts)
                    .putLong(KEY_LOCKOUT_UNTIL, now + penaltyMs)
                    .apply()
                PinVerifyResult.LockedOut((penaltyMs / 1000L).toInt())
            } else {
                prefs.edit().putInt(KEY_FAILED_ATTEMPTS, attempts).apply()
                PinVerifyResult.WrongPin(MAX_ATTEMPTS - attempts)
            }
        }
    }

    fun getLockoutRemainingSeconds(): Int {
        val now = System.currentTimeMillis()
        val until = prefs.getLong(KEY_LOCKOUT_UNTIL, 0L)
        return if (now < until) ((until - now) / 1000L).toInt().coerceAtLeast(1) else 0
    }

    var isBiometricEnabled: Boolean
        get() = prefs.getBoolean(KEY_BIOMETRIC_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_BIOMETRIC_ENABLED, value).apply()

    var isScreenshotBlocked: Boolean
        get() = prefs.getBoolean(KEY_SCREENSHOT_BLOCK, true)
        set(value) = prefs.edit().putBoolean(KEY_SCREENSHOT_BLOCK, value).apply()

    var isBlockMockGpsEnabled: Boolean
        get() = prefs.getBoolean(KEY_BLOCK_MOCK_GPS, false)
        set(value) = prefs.edit().putBoolean(KEY_BLOCK_MOCK_GPS, value).apply()

    var autoLockSeconds: Int
        get() = prefs.getInt(KEY_AUTO_LOCK_SECONDS, 0)
        set(value) = prefs.edit().putInt(KEY_AUTO_LOCK_SECONDS, value).apply()

    var customNote: String
        get() = prefs.getString(KEY_CUSTOM_NOTE, "Captured by GPS Map Camera") ?: "Captured by GPS Map Camera"
        set(value) = prefs.edit().putString(KEY_CUSTOM_NOTE, value).apply()

    private fun pbkdf2(pin: String, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(pin.toCharArray(), salt, 120_000, 256)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        return factory.generateSecret(spec).encoded
    }

    companion object {
        private const val MAX_ATTEMPTS = 5
        private const val KEY_PIN_SALT = "pin_salt"
        private const val KEY_PIN_HASH = "pin_hash"
        private const val KEY_FAILED_ATTEMPTS = "failed_attempts"
        private const val KEY_LOCKOUT_UNTIL = "lockout_until"
        private const val KEY_BIOMETRIC_ENABLED = "biometric_enabled"
        private const val KEY_SCREENSHOT_BLOCK = "screenshot_block"
        private const val KEY_BLOCK_MOCK_GPS = "block_mock_gps"
        private const val KEY_AUTO_LOCK_SECONDS = "auto_lock_seconds"
        private const val KEY_CUSTOM_NOTE = "custom_note"
    }
}

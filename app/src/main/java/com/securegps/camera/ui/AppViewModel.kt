package com.securegps.camera.ui

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.securegps.camera.camera.WatermarkRenderer
import com.securegps.camera.data.GpsStampData
import com.securegps.camera.data.VaultPhotoMeta
import com.securegps.camera.location.LocationRepository
import com.securegps.camera.security.BiometricHelper
import com.securegps.camera.security.DeviceSecurityMonitor
import com.securegps.camera.security.EncryptedPhotoVault
import com.securegps.camera.security.PinVerifyResult
import com.securegps.camera.security.SecurityPosture
import com.securegps.camera.security.SecurityPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

data class AppUiState(
    val isPinSetupRequired: Boolean = false,
    val isLocked: Boolean = true,
    val pinError: String? = null,
    val lockoutSeconds: Int = 0,
    val gpsStamp: GpsStampData = GpsStampData(),
    val vaultPhotos: List<VaultPhotoMeta> = emptyList(),
    val customNote: String = "Captured by GPS Map Camera",
    val screenshotBlocked: Boolean = true,
    val biometricEnabled: Boolean = true,
    val biometricAvailable: Boolean = false,
    val blockMockGps: Boolean = false,
    val autoLockSeconds: Int = 0,
    val isCapturing: Boolean = false,
    val statusMessage: String? = null,
    val securityPosture: SecurityPosture = DeviceSecurityMonitor.inspect()
)

class AppViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = SecurityPreferences(application)
    val vault = EncryptedPhotoVault(application)
    private val locationRepo = LocationRepository(application)

    private val _uiState = MutableStateFlow(
        AppUiState(
            isPinSetupRequired = !prefs.hasPinConfigured(),
            isLocked = true,
            lockoutSeconds = prefs.getLockoutRemainingSeconds(),
            customNote = prefs.customNote,
            screenshotBlocked = prefs.isScreenshotBlocked,
            biometricEnabled = prefs.isBiometricEnabled,
            biometricAvailable = BiometricHelper.isBiometricAvailable(application),
            blockMockGps = prefs.isBlockMockGpsEnabled,
            autoLockSeconds = prefs.autoLockSeconds,
            gpsStamp = GpsStampData(note = prefs.customNote)
        )
    )
    val uiState: StateFlow<AppUiState> = _uiState.asStateFlow()

    private var locationJob: Job? = null
    private var clockJob: Job? = null
    private var backgroundTimestampMs: Long = 0L
    private var suppressNextAutoLock: Boolean = false

    init {
        startClockTicker()
        if (prefs.getLockoutRemainingSeconds() > 0) {
            startLockoutCountdown()
        }
    }

    fun startLocationUpdates() {
        if (locationJob?.isActive == true) return
        locationJob = viewModelScope.launch {
            locationRepo.observeGpsData(viewModelScope) { _uiState.value.customNote }
                .collect { stamp ->
                    _uiState.update { it.copy(gpsStamp = stamp) }
                }
        }
    }

    private fun startClockTicker() {
        clockJob?.cancel()
        clockJob = viewModelScope.launch {
            while (true) {
                delay(15_000L)
                _uiState.update { state ->
                    state.copy(
                        gpsStamp = state.gpsStamp.copy(
                            timestampMillis = System.currentTimeMillis(),
                            note = state.customNote
                        )
                    )
                }
            }
        }
    }

    fun setupInitialPin(pin: String) {
        prefs.setPin(pin)
        _uiState.update {
            it.copy(
                isPinSetupRequired = false,
                isLocked = false,
                pinError = null
            )
        }
        refreshVault()
    }

    fun submitPin(pin: String) {
        when (val result = prefs.verifyPin(pin)) {
            is PinVerifyResult.Success -> {
                _uiState.update {
                    it.copy(isLocked = false, pinError = null, lockoutSeconds = 0)
                }
                refreshVault()
            }
            is PinVerifyResult.WrongPin -> {
                _uiState.update {
                    it.copy(pinError = "Wrong PIN (${result.attemptsRemaining} attempts left)")
                }
            }
            is PinVerifyResult.LockedOut -> {
                _uiState.update {
                    it.copy(
                        pinError = "Too many attempts. Locked for ${result.secondsRemaining}s",
                        lockoutSeconds = result.secondsRemaining
                    )
                }
                startLockoutCountdown()
            }
        }
    }

    fun unlockWithBiometric() {
        _uiState.update {
            it.copy(isLocked = false, pinError = null, lockoutSeconds = 0)
        }
        refreshVault()
    }

    fun lockNow() {
        if (!_uiState.value.isPinSetupRequired) {
            vault.cleanupShareCache()
            _uiState.update { it.copy(isLocked = true, pinError = null) }
        }
    }

    fun onAppBackgrounded() {
        backgroundTimestampMs = System.currentTimeMillis()
    }

    fun onAppForegrounded() {
        if (suppressNextAutoLock) {
            suppressNextAutoLock = false
            return
        }
        val delaySec = _uiState.value.autoLockSeconds
        if (!_uiState.value.isPinSetupRequired && delaySec >= 0 && backgroundTimestampMs > 0L) {
            val elapsed = System.currentTimeMillis() - backgroundTimestampMs
            if (elapsed >= delaySec * 1000L) {
                lockNow()
            }
        }
    }

    fun suppressAutoLockOnce() {
        suppressNextAutoLock = true
    }

    fun updateNote(newNote: String) {
        val clean = newNote.trim().ifBlank { "Captured by GPS Map Camera" }
        prefs.customNote = clean
        _uiState.update {
            it.copy(
                customNote = clean,
                gpsStamp = it.gpsStamp.copy(note = clean)
            )
        }
    }

    fun setScreenshotBlocked(enabled: Boolean) {
        prefs.isScreenshotBlocked = enabled
        _uiState.update { it.copy(screenshotBlocked = enabled) }
    }

    fun setBiometricEnabled(enabled: Boolean) {
        prefs.isBiometricEnabled = enabled
        _uiState.update { it.copy(biometricEnabled = enabled) }
    }

    fun setBlockMockGps(enabled: Boolean) {
        prefs.isBlockMockGpsEnabled = enabled
        _uiState.update { it.copy(blockMockGps = enabled) }
    }

    fun setAutoLockSeconds(seconds: Int) {
        prefs.autoLockSeconds = seconds
        _uiState.update { it.copy(autoLockSeconds = seconds) }
    }

    fun changePin(currentPin: String, newPin: String): Boolean {
        return when (prefs.verifyPin(currentPin)) {
            is PinVerifyResult.Success -> {
                prefs.setPin(newPin)
                setStatusMessage("PIN updated successfully")
                true
            }
            else -> {
                setStatusMessage("Current PIN is incorrect")
                false
            }
        }
    }

    fun processAndSaveCapturedImage(tempRawFile: File, secondTempFile: File? = null) {
        val currentState = _uiState.value
        if (currentState.blockMockGps && currentState.gpsStamp.isMockLocation) {
            tempRawFile.delete()
            secondTempFile?.delete()
            setStatusMessage("Capture blocked: Mock/Fake GPS location detected!")
            return
        }

        _uiState.update { it.copy(isCapturing = true) }
        viewModelScope.launch {
            try {
                val stamp = _uiState.value.gpsStamp.copy(
                    timestampMillis = System.currentTimeMillis(),
                    note = _uiState.value.customNote
                )
                withContext(Dispatchers.Default) {
                    val baseBitmap = if (secondTempFile != null) {
                        val firstBmp = WatermarkRenderer.loadRotatedBitmap(tempRawFile)
                        val secondBmp = WatermarkRenderer.loadRotatedBitmap(secondTempFile)
                        val merged = WatermarkRenderer.mergeDualBitmaps(firstBmp, secondBmp)
                        firstBmp.recycle()
                        secondBmp.recycle()
                        merged
                    } else {
                        WatermarkRenderer.loadRotatedBitmap(tempRawFile)
                    }

                    val stamped = WatermarkRenderer.stampGpsOverlay(baseBitmap, stamp)
                    val meta = vault.saveCapturedPhoto(stamped, stamp)
                    // Auto-save to phone gallery as requested
                    vault.exportToPublicGallery(meta.id)

                    if (stamped !== baseBitmap) baseBitmap.recycle()
                    stamped.recycle()
                }
                refreshVault()
                setStatusMessage("Photo saved to Gallery & Vault")
            } catch (e: Exception) {
                setStatusMessage("Capture error: ${e.message ?: "Unknown error"}")
            } finally {
                tempRawFile.delete()
                secondTempFile?.delete()
                _uiState.update { it.copy(isCapturing = false) }
            }
        }
    }

    fun refreshVault() {
        viewModelScope.launch {
            val items = vault.listPhotos()
            _uiState.update { it.copy(vaultPhotos = items) }
        }
    }

    suspend fun loadThumbnail(id: String): Bitmap? = vault.loadThumbnail(id)

    suspend fun loadFullPhoto(id: String): Bitmap? = vault.loadFullPhoto(id)

    suspend fun verifyPhotoIntegrity(id: String): Boolean = vault.verifyIntegrity(id)

    fun exportPhotoToGallery(id: String) {
        viewModelScope.launch {
            val uri = vault.exportToPublicGallery(id)
            setStatusMessage(
                if (uri != null) "Exported to Pictures/GPSMapCamera"
                else "Failed to export photo"
            )
        }
    }

    suspend fun prepareShareUri(id: String): Uri? {
        suppressAutoLockOnce()
        return vault.prepareTempShareUri(id)
    }

    fun deletePhoto(id: String) {
        viewModelScope.launch {
            vault.deletePhoto(id)
            refreshVault()
            setStatusMessage("Photo permanently deleted from Vault")
        }
    }

    fun wipeAllVaultPhotos() {
        viewModelScope.launch {
            vault.wipeVault()
            refreshVault()
            setStatusMessage("All vault photos wiped")
        }
    }

    fun setStatusMessage(msg: String?) {
        _uiState.update { it.copy(statusMessage = msg) }
    }

    private fun startLockoutCountdown() {
        viewModelScope.launch {
            while (true) {
                val rem = prefs.getLockoutRemainingSeconds()
                _uiState.update { it.copy(lockoutSeconds = rem) }
                if (rem <= 0) break
                delay(1000L)
            }
        }
    }
}

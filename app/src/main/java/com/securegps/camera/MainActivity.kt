package com.securegps.camera

import android.os.Bundle
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.securegps.camera.security.BiometricHelper
import com.securegps.camera.ui.AppViewModel
import com.securegps.camera.ui.screens.CameraScreen
import com.securegps.camera.ui.screens.PinLockScreen
import com.securegps.camera.ui.screens.SettingsScreen
import com.securegps.camera.ui.screens.VaultScreen
import com.securegps.camera.ui.theme.SecureGpsTheme

private enum class AppScreen {
    CAMERA, VAULT, SETTINGS
}

class MainActivity : FragmentActivity() {

    private val viewModel: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            val state by viewModel.uiState.collectAsState()
            val lifecycleOwner = LocalLifecycleOwner.current
            var currentScreen by remember { mutableStateOf(AppScreen.CAMERA) }

            // Enforce or relax FLAG_SECURE screenshot & screen-recording protection dynamically
            LaunchedEffect(state.screenshotBlocked) {
                if (state.screenshotBlocked) {
                    window.setFlags(
                        WindowManager.LayoutParams.FLAG_SECURE,
                        WindowManager.LayoutParams.FLAG_SECURE
                    )
                } else {
                    window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                }
            }

            // Show transient feedback messages cleanly
            LaunchedEffect(state.statusMessage) {
                val msg = state.statusMessage ?: return@LaunchedEffect
                Toast.makeText(this@MainActivity, msg, Toast.LENGTH_SHORT).show()
                viewModel.setStatusMessage(null)
            }

            // Auto-lock and clean share cache on background/foreground transitions
            DisposableEffect(lifecycleOwner) {
                val observer = LifecycleEventObserver { _, event ->
                    when (event) {
                        Lifecycle.Event.ON_STOP -> viewModel.onAppBackgrounded()
                        Lifecycle.Event.ON_START -> viewModel.onAppForegrounded()
                        else -> Unit
                    }
                }
                lifecycleOwner.lifecycle.addObserver(observer)
                onDispose {
                    lifecycleOwner.lifecycle.removeObserver(observer)
                }
            }

            SecureGpsTheme {
                if (state.isPinSetupRequired || state.isLocked) {
                    PinLockScreen(
                        isSetupMode = state.isPinSetupRequired,
                        pinError = state.pinError,
                        lockoutSeconds = state.lockoutSeconds,
                        biometricAvailable = state.biometricAvailable,
                        biometricEnabled = state.biometricEnabled,
                        onRequestBiometric = {
                            BiometricHelper.authenticate(
                                activity = this@MainActivity,
                                onSuccess = { viewModel.unlockWithBiometric() },
                                onError = { err -> viewModel.setStatusMessage(err) }
                            )
                        },
                        onSubmitPin = { pin -> viewModel.submitPin(pin) },
                        onSetupComplete = { pin -> viewModel.setupInitialPin(pin) }
                    )
                } else {
                    BackHandler(enabled = currentScreen != AppScreen.CAMERA) {
                        currentScreen = AppScreen.CAMERA
                    }

                    when (currentScreen) {
                        AppScreen.CAMERA -> {
                            CameraScreen(
                                gpsStamp = state.gpsStamp,
                                latestPhoto = state.vaultPhotos.firstOrNull(),
                                loadThumbnail = { id -> viewModel.loadThumbnail(id) },
                                customNote = state.customNote,
                                isCapturing = state.isCapturing,
                                onStartLocation = { viewModel.startLocationUpdates() },
                                onPhotoCaptured = { rawFile ->
                                    viewModel.processAndSaveCapturedImage(rawFile)
                                },
                                onUpdateNote = { note -> viewModel.updateNote(note) },
                                onOpenVault = {
                                    viewModel.refreshVault()
                                    currentScreen = AppScreen.VAULT
                                },
                                onOpenSettings = { currentScreen = AppScreen.SETTINGS },
                                onLockNow = { viewModel.lockNow() }
                            )
                        }

                        AppScreen.VAULT -> {
                            VaultScreen(
                                photos = state.vaultPhotos,
                                loadThumbnail = { id -> viewModel.loadThumbnail(id) },
                                loadFullPhoto = { id -> viewModel.loadFullPhoto(id) },
                                verifyIntegrity = { id -> viewModel.verifyPhotoIntegrity(id) },
                                prepareShareUri = { id -> viewModel.prepareShareUri(id) },
                                onExportToGallery = { id -> viewModel.exportPhotoToGallery(id) },
                                onDeletePhoto = { id -> viewModel.deletePhoto(id) },
                                onBack = { currentScreen = AppScreen.CAMERA },
                                onLockNow = { viewModel.lockNow() }
                            )
                        }

                        AppScreen.SETTINGS -> {
                            SettingsScreen(
                                screenshotBlocked = state.screenshotBlocked,
                                biometricEnabled = state.biometricEnabled,
                                biometricAvailable = state.biometricAvailable,
                                blockMockGps = state.blockMockGps,
                                autoLockSeconds = state.autoLockSeconds,
                                customNote = state.customNote,
                                securityPosture = state.securityPosture,
                                onToggleScreenshotBlock = { viewModel.setScreenshotBlocked(it) },
                                onToggleBiometric = { viewModel.setBiometricEnabled(it) },
                                onToggleBlockMockGps = { viewModel.setBlockMockGps(it) },
                                onSelectAutoLock = { viewModel.setAutoLockSeconds(it) },
                                onUpdateNote = { viewModel.updateNote(it) },
                                onChangePin = { oldPin, newPin -> viewModel.changePin(oldPin, newPin) },
                                onWipeVault = { viewModel.wipeAllVaultPhotos() },
                                onBack = { currentScreen = AppScreen.CAMERA }
                            )
                        }
                    }
                }
            }
        }
    }
}

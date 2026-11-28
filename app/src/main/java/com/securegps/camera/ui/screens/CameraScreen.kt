package com.securegps.camera.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.FlashAuto
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.VerticalSplit
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.securegps.camera.data.GpsStampData
import com.securegps.camera.data.VaultPhotoMeta
import com.securegps.camera.ui.components.GpsOverlayCard
import java.io.File
import kotlin.math.roundToInt

@Composable
fun CameraScreen(
    gpsStamp: GpsStampData,
    latestPhoto: VaultPhotoMeta?,
    loadThumbnail: suspend (String) -> Bitmap?,
    customNote: String,
    isCapturing: Boolean,
    onStartLocation: () -> Unit,
    onPhotoCaptured: (File, File?) -> Unit,
    onSharePhoto: (String) -> Unit,
    onUpdateNote: (String) -> Unit,
    onOpenVault: () -> Unit,
    onOpenSettings: () -> Unit,
    onLockNow: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var hasCameraPerm by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    var hasLocationPerm by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED
        )
    }

    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        hasCameraPerm = grants[Manifest.permission.CAMERA] == true ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        hasLocationPerm = grants[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            grants[Manifest.permission.ACCESS_COARSE_LOCATION] == true ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
    }

    LaunchedEffect(Unit) {
        if (!hasCameraPerm || !hasLocationPerm) {
            permLauncher.launch(
                arrayOf(
                    Manifest.permission.CAMERA,
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    LaunchedEffect(hasLocationPerm) {
        if (hasLocationPerm) {
            onStartLocation()
        }
    }

    var lensFacing by remember { mutableIntStateOf(CameraSelector.LENS_FACING_BACK) }
    var flashMode by remember { mutableIntStateOf(ImageCapture.FLASH_MODE_OFF) }
    var showNoteDialog by remember { mutableStateOf(false) }

    // Dual-screen capture mode state
    var isDualMode by remember { mutableStateOf(false) }
    var dualFirstFile by remember { mutableStateOf<File?>(null) }
    var dualFirstBitmap by remember { mutableStateOf<Bitmap?>(null) }

    var latestThumb by remember { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(latestPhoto?.id) {
        latestThumb = if (latestPhoto != null) loadThumbnail(latestPhoto.id) else null
    }

    val previewView = remember { PreviewView(context) }
    val imageCapture = remember(flashMode) {
        ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
            .setFlashMode(flashMode)
            .build()
    }

    LaunchedEffect(hasCameraPerm, lensFacing, imageCapture) {
        if (!hasCameraPerm) return@LaunchedEffect
        val providerFuture = ProcessCameraProvider.getInstance(context)
        providerFuture.addListener({
            val cameraProvider = providerFuture.get()
            val preview = Preview.Builder().build().also {
                it.surfaceProvider = previewView.surfaceProvider
            }
            val selector = CameraSelector.Builder()
                .requireLensFacing(lensFacing)
                .build()
            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(lifecycleOwner, selector, preview, imageCapture)
            } catch (_: Exception) {
            }
        }, ContextCompat.getMainExecutor(context))
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        if (hasCameraPerm) {
            if (isDualMode && dualFirstBitmap != null) {
                // Split Screen: Left half = Frozen 1st Photo, Right half = Live running camera
                Row(modifier = Modifier.fillMaxSize()) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxSize()
                            .background(Color.Black)
                    ) {
                        Image(
                            bitmap = dualFirstBitmap!!.asImageBitmap(),
                            contentDescription = "First Captured Photo",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                        // Badge indicating frozen shot
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .padding(start = 12.dp, top = 60.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xCC0F172A))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text("1st Photo", color = Color(0xFF38BDF8), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }

                        // Retake button for 1st shot
                        IconButton(
                            onClick = {
                                dualFirstFile?.delete()
                                dualFirstFile = null
                                dualFirstBitmap?.recycle()
                                dualFirstBitmap = null
                            },
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(8.dp)
                                .clip(CircleShape)
                                .background(Color(0xAA000000))
                        ) {
                            Icon(Icons.Default.Replay, contentDescription = "Retake 1st photo", tint = Color.White)
                        }
                    }

                    // Divider line
                    Box(
                        modifier = Modifier
                            .width(2.dp)
                            .fillMaxSize()
                            .background(Color.White)
                    )

                    // Right half: Live Camera preview keeps running!
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxSize()
                    ) {
                        AndroidView(
                            factory = { previewView },
                            modifier = Modifier.fillMaxSize()
                        )
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .padding(start = 8.dp, top = 60.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xCC0F172A))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text("2nd (Live Camera)", color = Color(0xFF10B981), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            } else {
                AndroidView(
                    factory = { previewView },
                    modifier = Modifier.fillMaxSize()
                )

                // If Dual Mode is active but first photo not yet taken, show guide line
                if (isDualMode) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .width(1.5.dp)
                            .fillMaxSize()
                            .background(Color(0x66FFFFFF))
                    )
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Default.Shield,
                    contentDescription = null,
                    tint = Color(0xFF38BDF8),
                    modifier = Modifier.size(56.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Camera & GPS Permissions Required",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Allow Camera and Precise Location access so photos can be stamped with live GPS coordinates and encrypted into your vault.",
                    color = Color(0xFF94A3B8),
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(20.dp))
                Button(
                    onClick = {
                        permLauncher.launch(
                            arrayOf(
                                Manifest.permission.CAMERA,
                                Manifest.permission.ACCESS_FINE_LOCATION,
                                Manifest.permission.ACCESS_COARSE_LOCATION
                            )
                        )
                    }
                ) {
                    Text("Grant Permissions")
                }
            }
        }

        // Compact GPS & Protection status pill (top-left)
        Row(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(top = 24.dp, start = 14.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(
                    if (gpsStamp.isMockLocation) Color(0xCC991B1B)
                    else Color(0x99111827)
                )
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (gpsStamp.isMockLocation) Icons.Default.Warning else Icons.Default.Shield,
                contentDescription = null,
                tint = when {
                    gpsStamp.isMockLocation -> Color(0xFFFCA5A5)
                    gpsStamp.hasLocationFix -> Color(0xFF10B981)
                    else -> Color(0xFFF59E0B)
                },
                modifier = Modifier.size(14.dp)
            )
            Spacer(modifier = Modifier.width(5.dp))
            Text(
                text = when {
                    gpsStamp.isMockLocation -> "Mock GPS!"
                    gpsStamp.hasLocationFix -> "GPS ±${gpsStamp.accuracyMeters.roundToInt()}m"
                    else -> "Fixing GPS..."
                },
                color = Color.White,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium
            )
        }

        // Top banner for Dual Mode instructions
        if (isDualMode) {
            Row(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 22.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color(0xDD0F172A))
                    .padding(horizontal = 14.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.VerticalSplit,
                    contentDescription = null,
                    tint = Color(0xFF38BDF8),
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (dualFirstBitmap == null) "Double Screen: Tap Shutter for 1st Photo" else "1st Photo Saved! Frame 2nd & Tap Shutter",
                    color = Color.White,
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        // Right-side vertical floating control rail: Flash, Camera Rotate, Double Screen, Lock, Settings
        Column(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 24.dp, end = 12.dp)
                .clip(RoundedCornerShape(28.dp))
                .background(Color(0x880B1018))
                .padding(vertical = 6.dp, horizontal = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            IconButton(
                onClick = {
                    flashMode = when (flashMode) {
                        ImageCapture.FLASH_MODE_OFF -> ImageCapture.FLASH_MODE_ON
                        ImageCapture.FLASH_MODE_ON -> ImageCapture.FLASH_MODE_AUTO
                        else -> ImageCapture.FLASH_MODE_OFF
                    }
                }
            ) {
                Icon(
                    imageVector = when (flashMode) {
                        ImageCapture.FLASH_MODE_ON -> Icons.Default.FlashOn
                        ImageCapture.FLASH_MODE_AUTO -> Icons.Default.FlashAuto
                        else -> Icons.Default.FlashOff
                    },
                    contentDescription = "Flash",
                    tint = if (flashMode == ImageCapture.FLASH_MODE_OFF) Color.White else Color(0xFFFBBF24)
                )
            }

            IconButton(
                onClick = {
                    lensFacing = if (lensFacing == CameraSelector.LENS_FACING_BACK) {
                        CameraSelector.LENS_FACING_FRONT
                    } else {
                        CameraSelector.LENS_FACING_BACK
                    }
                }
            ) {
                Icon(
                    imageVector = Icons.Default.Cameraswitch,
                    contentDescription = "Switch Camera",
                    tint = Color.White
                )
            }

            // Double Screen / Dual Capture Toggle
            IconButton(
                onClick = {
                    isDualMode = !isDualMode
                    if (!isDualMode) {
                        dualFirstFile?.delete()
                        dualFirstFile = null
                        dualFirstBitmap?.recycle()
                        dualFirstBitmap = null
                    }
                }
            ) {
                Icon(
                    imageVector = Icons.Default.VerticalSplit,
                    contentDescription = "Double Screen",
                    tint = if (isDualMode) Color(0xFF38BDF8) else Color.White
                )
            }

            IconButton(onClick = onLockNow) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = "Lock Vault",
                    tint = Color(0xFF38BDF8)
                )
            }

            IconButton(onClick = onOpenSettings) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = "Settings",
                    tint = Color.White
                )
            }
        }

        // Bottom region: Live GPS Card + Shutter Bar
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .padding(horizontal = 10.dp, vertical = 12.dp)
        ) {
            GpsOverlayCard(
                stamp = gpsStamp,
                onNoteClick = { showNoteDialog = true }
            )

            Spacer(modifier = Modifier.height(14.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Vault gallery thumbnail button
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF1E293B))
                        .border(1.5.dp, Color(0xFF475569), RoundedCornerShape(12.dp))
                        .clickable(onClick = onOpenVault),
                    contentAlignment = Alignment.Center
                ) {
                    val thumb = latestThumb
                    if (thumb != null && !thumb.isRecycled) {
                        Image(
                            bitmap = thumb.asImageBitmap(),
                            contentDescription = "Vault",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.PhotoLibrary,
                            contentDescription = "Vault",
                            tint = Color.White,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }

                // Share button (shares latest photo to WhatsApp, Facebook, etc.)
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (latestPhoto != null) Color(0xFF1E293B) else Color(0x661E293B))
                        .clickable(enabled = latestPhoto != null) {
                            latestPhoto?.let { onSharePhoto(it.id) }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Share,
                        contentDescription = "Share",
                        tint = if (latestPhoto != null) Color(0xFF38BDF8) else Color(0xFF64748B),
                        modifier = Modifier.size(22.dp)
                    )
                }

                // Shutter button (supports Single Shot & Double Screen 2-in-1 merge)
                Box(
                    modifier = Modifier
                        .size(76.dp)
                        .clip(CircleShape)
                        .border(4.dp, Color.White, CircleShape)
                        .padding(6.dp)
                        .clip(CircleShape)
                        .background(
                            when {
                                isCapturing -> Color(0xFF38BDF8)
                                isDualMode && dualFirstBitmap != null -> Color(0xFF10B981)
                                isDualMode -> Color(0xFF0284C7)
                                else -> Color.White
                            }
                        )
                        .clickable(enabled = hasCameraPerm && !isCapturing) {
                            val rawTemp = File.createTempFile("raw_gps_", ".jpg", context.cacheDir)
                            val opts = ImageCapture.OutputFileOptions.Builder(rawTemp).build()
                            imageCapture.takePicture(
                                opts,
                                ContextCompat.getMainExecutor(context),
                                object : ImageCapture.OnImageSavedCallback {
                                    override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                                        if (isDualMode) {
                                            if (dualFirstFile == null) {
                                                // Step 1: 1st side captured! Freeze it on left side.
                                                dualFirstFile = rawTemp
                                                dualFirstBitmap = com.securegps.camera.camera.WatermarkRenderer.loadRotatedBitmap(rawTemp)
                                            } else {
                                                // Step 2: 2nd side captured! Combine both into 1 photo.
                                                val first = dualFirstFile!!
                                                dualFirstFile = null
                                                dualFirstBitmap?.recycle()
                                                dualFirstBitmap = null
                                                onPhotoCaptured(first, rawTemp)
                                            }
                                        } else {
                                            onPhotoCaptured(rawTemp, null)
                                        }
                                    }

                                    override fun onError(exception: ImageCaptureException) {
                                        rawTemp.delete()
                                    }
                                }
                            )
                        },
                    contentAlignment = Alignment.Center
                ) {
                    if (isCapturing) {
                        CircularProgressIndicator(
                            color = Color.Black,
                            strokeWidth = 3.dp,
                            modifier = Modifier.size(28.dp)
                        )
                    } else if (isDualMode) {
                        Text(
                            text = if (dualFirstBitmap == null) "1/2" else "2/2",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                    }
                }

                // Quick note editor button
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF1E293B))
                        .clickable { showNoteDialog = true },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.EditNote,
                        contentDescription = "Edit Note",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }
    }

    if (showNoteDialog) {
        var noteInput by remember { mutableStateOf(customNote) }
        AlertDialog(
            onDismissRequest = { showNoteDialog = false },
            title = { Text("Watermark Note") },
            text = {
                OutlinedTextField(
                    value = noteInput,
                    onValueChange = { noteInput = it },
                    label = { Text("Note line on photo") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onUpdateNote(noteInput)
                        showNoteDialog = false
                    }
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showNoteDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

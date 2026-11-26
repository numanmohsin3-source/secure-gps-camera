package com.securegps.camera.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.securegps.camera.security.SecurityPosture

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    screenshotBlocked: Boolean,
    biometricEnabled: Boolean,
    biometricAvailable: Boolean,
    blockMockGps: Boolean,
    autoLockSeconds: Int,
    customNote: String,
    securityPosture: SecurityPosture,
    onToggleScreenshotBlock: (Boolean) -> Unit,
    onToggleBiometric: (Boolean) -> Unit,
    onToggleBlockMockGps: (Boolean) -> Unit,
    onSelectAutoLock: (Int) -> Unit,
    onUpdateNote: (String) -> Unit,
    onChangePin: (String, String) -> Boolean,
    onWipeVault: () -> Unit,
    onBack: () -> Unit
) {
    var noteDraft by remember(customNote) { mutableStateOf(customNote) }
    var showChangePin by remember { mutableStateOf(false) }
    var showWipeConfirm by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Privacy & Security", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF0F172A),
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White
                )
            )
        },
        containerColor = Color(0xFF090D14)
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            SectionCard(title = "Screen & App Protection") {
                SettingToggleRow(
                    title = "Block Screenshots & Screen Recording",
                    subtitle = "Enables Android FLAG_SECURE and hides recent-app preview",
                    checked = screenshotBlocked,
                    onCheckedChange = onToggleScreenshotBlock
                )

                if (biometricAvailable) {
                    SettingToggleRow(
                        title = "Fingerprint / Biometric Unlock",
                        subtitle = "Allow fast unlock using device biometrics",
                        checked = biometricEnabled,
                        onCheckedChange = onToggleBiometric
                    )
                }

                SettingToggleRow(
                    title = "Strict Anti-Fake-GPS Guard",
                    subtitle = "Block photo capture when Mock Location app is detected",
                    checked = blockMockGps,
                    onCheckedChange = onToggleBlockMockGps
                )

                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Auto-Lock When Minimized",
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(
                        0 to "Instant",
                        30 to "30s",
                        60 to "1m",
                        -1 to "Manual"
                    ).forEach { (sec, label) ->
                        FilterChip(
                            selected = autoLockSeconds == sec,
                            onClick = { onSelectAutoLock(sec) },
                            label = { Text(label, fontSize = 12.sp) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))
                Button(
                    onClick = { showChangePin = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Change Vault PIN")
                }
            }

            SectionCard(title = "GPS Watermark Note") {
                OutlinedTextField(
                    value = noteDraft,
                    onValueChange = { noteDraft = it },
                    label = { Text("Default Photo Note") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = { onUpdateNote(noteDraft) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Save Note")
                }
            }

            SectionCard(title = "Security & Integrity Diagnostics") {
                PostureRow("Vault Encryption", "AES-256-GCM (Android Keystore)", true)
                PostureRow("PIN Hashing", "PBKDF2-HMAC-SHA256 (120k)", true)
                PostureRow("Cloud Backup", "Disabled (allowBackup=false)", true)
                PostureRow(
                    "Root / Test-Keys",
                    if (securityPosture.isRootSuspected) "Detected" else "Clean",
                    !securityPosture.isRootSuspected
                )
                PostureRow(
                    "Debugger Attached",
                    if (securityPosture.isDebuggerAttached) "Active" else "None",
                    !securityPosture.isDebuggerAttached
                )
            }

            SectionCard(title = "Danger Zone") {
                Button(
                    onClick = { showWipeConfirm = true },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF991B1B)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Wipe All Vault Photos", color = Color.White)
                }
            }
        }
    }

    if (showChangePin) {
        var oldPin by remember { mutableStateOf("") }
        var newPin by remember { mutableStateOf("") }
        var err by remember { mutableStateOf<String?>(null) }

        AlertDialog(
            onDismissRequest = { showChangePin = false },
            title = { Text("Change Vault PIN") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = oldPin,
                        onValueChange = { if (it.length <= 6 && it.all(Char::isDigit)) oldPin = it },
                        label = { Text("Current PIN") },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = newPin,
                        onValueChange = { if (it.length <= 6 && it.all(Char::isDigit)) newPin = it },
                        label = { Text("New PIN (4–6 digits)") },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        singleLine = true
                    )
                    if (err != null) {
                        Text(err!!, color = Color(0xFFEF4444), fontSize = 12.sp)
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (newPin.length !in 4..6) {
                            err = "New PIN must be 4–6 digits"
                        } else if (onChangePin(oldPin, newPin)) {
                            showChangePin = false
                        } else {
                            err = "Current PIN is wrong"
                        }
                    }
                ) {
                    Text("Update")
                }
            },
            dismissButton = {
                TextButton(onClick = { showChangePin = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showWipeConfirm) {
        AlertDialog(
            onDismissRequest = { showWipeConfirm = false },
            title = { Text("Wipe Entire Vault?") },
            text = { Text("This will permanently erase every encrypted photo stored in this app.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showWipeConfirm = false
                        onWipeVault()
                    }
                ) {
                    Text("Wipe Everything", color = Color(0xFFEF4444))
                }
            },
            dismissButton = {
                TextButton(onClick = { showWipeConfirm = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun SectionCard(
    title: String,
    content: @Composable () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xFF111827))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = title,
            color = Color(0xFF38BDF8),
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold
        )
        content()
    }
}

@Composable
private fun SettingToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            Text(subtitle, color = Color(0xFF94A3B8), fontSize = 12.sp)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun PostureRow(label: String, value: String, isHealthy: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = Color(0xFFCBD5E1), fontSize = 13.sp)
        Text(
            text = value,
            color = if (isHealthy) Color(0xFF10B981) else Color(0xFFF59E0B),
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

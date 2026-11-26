package com.securegps.camera.ui.screens

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun PinLockScreen(
    isSetupMode: Boolean,
    pinError: String?,
    lockoutSeconds: Int,
    biometricAvailable: Boolean,
    biometricEnabled: Boolean,
    onRequestBiometric: () -> Unit,
    onSubmitPin: (String) -> Unit,
    onSetupComplete: (String) -> Unit
) {
    var enteredPin by remember { mutableStateOf("") }
    var firstSetupPin by remember { mutableStateOf<String?>(null) }
    var localError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(isSetupMode, biometricAvailable, biometricEnabled, lockoutSeconds) {
        if (!isSetupMode && biometricAvailable && biometricEnabled && lockoutSeconds == 0) {
            onRequestBiometric()
        }
    }

    val title = when {
        isSetupMode && firstSetupPin == null -> "Create Vault PIN"
        isSetupMode && firstSetupPin != null -> "Confirm Vault PIN"
        else -> "Encrypted Vault Locked"
    }

    val subtitle = when {
        isSetupMode && firstSetupPin == null -> "Enter a 4–6 digit PIN to protect your GPS photos"
        isSetupMode && firstSetupPin != null -> "Re-enter your PIN to confirm"
        else -> "Enter your PIN or use biometrics to unlock"
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF090D14))
            .padding(horizontal = 24.dp, vertical = 36.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF132238)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isSetupMode) Icons.Default.Shield else Icons.Default.Lock,
                    contentDescription = null,
                    tint = Color(0xFF38BDF8),
                    modifier = Modifier.size(32.dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0xFF94A3B8),
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(28.dp))

            // PIN dots
            val dotCount = if (isSetupMode && firstSetupPin != null) firstSetupPin!!.length else 6
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                for (i in 0 until dotCount) {
                    val filled = i < enteredPin.length
                    Box(
                        modifier = Modifier
                            .size(16.dp)
                            .clip(CircleShape)
                            .background(if (filled) Color(0xFF38BDF8) else Color.Transparent)
                            .border(
                                width = 1.5.dp,
                                color = if (filled) Color(0xFF38BDF8) else Color(0xFF475569),
                                shape = CircleShape
                            )
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))
            val errToShow = localError ?: pinError
            if (errToShow != null) {
                Text(
                    text = errToShow,
                    color = Color(0xFFEF4444),
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center
                )
            } else if (lockoutSeconds > 0) {
                Text(
                    text = "Try again in ${lockoutSeconds}s",
                    color = Color(0xFFF59E0B),
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }

        // Numeric Keypad
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            val rows = listOf(
                listOf("1", "2", "3"),
                listOf("4", "5", "6"),
                listOf("7", "8", "9")
            )
            for (row in rows) {
                Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                    for (digit in row) {
                        KeypadButton(
                            label = digit,
                            enabled = lockoutSeconds == 0,
                            onClick = {
                                localError = null
                                if (enteredPin.length < 6) {
                                    enteredPin += digit
                                }
                            }
                        )
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                // Bottom-left: Biometric button (or empty spacer during setup)
                Box(
                    modifier = Modifier.size(72.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (!isSetupMode && biometricAvailable && biometricEnabled && lockoutSeconds == 0) {
                        Box(
                            modifier = Modifier
                                .size(72.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF132238))
                                .clickable { onRequestBiometric() },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Fingerprint,
                                contentDescription = "Fingerprint Unlock",
                                tint = Color(0xFF10B981),
                                modifier = Modifier.size(34.dp)
                            )
                        }
                    }
                }

                KeypadButton(
                    label = "0",
                    enabled = lockoutSeconds == 0,
                    onClick = {
                        localError = null
                        if (enteredPin.length < 6) {
                            enteredPin += "0"
                        }
                    }
                )

                // Bottom-right: Backspace
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF1E293B))
                        .clickable(enabled = enteredPin.isNotEmpty()) {
                            enteredPin = enteredPin.dropLast(1)
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Backspace,
                        contentDescription = "Delete",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Button(
                onClick = {
                    if (enteredPin.length < 4) {
                        localError = "Enter at least 4 digits"
                        return@Button
                    }
                    if (isSetupMode) {
                        val first = firstSetupPin
                        if (first == null) {
                            firstSetupPin = enteredPin
                            enteredPin = ""
                        } else if (first == enteredPin) {
                            onSetupComplete(enteredPin)
                            enteredPin = ""
                        } else {
                            localError = "PINs did not match. Start again."
                            firstSetupPin = null
                            enteredPin = ""
                        }
                    } else {
                        onSubmitPin(enteredPin)
                        enteredPin = ""
                    }
                },
                enabled = enteredPin.length >= 4 && lockoutSeconds == 0,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
            ) {
                Text(
                    text = when {
                        isSetupMode && firstSetupPin == null -> "Continue"
                        isSetupMode -> "Save & Unlock"
                        else -> "Unlock Vault"
                    },
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp
                )
            }
        }
    }
}

@Composable
private fun KeypadButton(
    label: String,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(72.dp)
            .clip(CircleShape)
            .background(Color(0xFF172133))
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            fontSize = 24.sp,
            fontWeight = FontWeight.Medium,
            color = if (enabled) Color.White else Color(0xFF475569)
        )
    }
}

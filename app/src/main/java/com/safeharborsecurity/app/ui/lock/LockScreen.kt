package com.safeharborsecurity.app.ui.lock

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.safeharborsecurity.app.ui.theme.*

@Composable
fun LockScreen(
    onPinVerified: () -> Unit,
    onBiometricRequest: () -> Unit,
    biometricAvailable: Boolean,
    errorMessage: String? = null,
    onPinSubmit: (String) -> Boolean
) {
    var pin by remember { mutableStateOf("") }
    var shakeError by remember { mutableStateOf(false) }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = NavyBlue
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                Icons.Default.Security,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = WarmGold
            )

            Spacer(Modifier.height(16.dp))

            Text(
                "Welcome back to\nSafe Companion",
                style = MaterialTheme.typography.headlineMedium,
                color = Color.White,
                textAlign = TextAlign.Center,
                fontWeight = FontWeight.Bold
            )

            Spacer(Modifier.height(8.dp))

            Text(
                "Please confirm it's you",
                style = MaterialTheme.typography.bodyLarge,
                color = Color.White.copy(alpha = 0.7f)
            )

            Spacer(Modifier.height(32.dp))

            // PIN dots
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                repeat(4) { index ->
                    Box(
                        modifier = Modifier
                            .size(20.dp)
                            .clip(CircleShape)
                            .background(
                                if (index < pin.length) WarmGold
                                else Color.White.copy(alpha = 0.3f)
                            )
                    )
                }
            }

            if (errorMessage != null) {
                Spacer(Modifier.height(12.dp))
                Text(
                    errorMessage,
                    style = MaterialTheme.typography.bodyMedium,
                    color = ScamRed
                )
            }

            Spacer(Modifier.height(32.dp))

            // Number pad
            val numbers = listOf(
                listOf("1", "2", "3"),
                listOf("4", "5", "6"),
                listOf("7", "8", "9"),
                listOf("", "0", "DEL")
            )

            numbers.forEach { row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    row.forEach { key ->
                        if (key.isEmpty()) {
                            Spacer(Modifier.size(80.dp))
                        } else if (key == "DEL") {
                            Box(
                                modifier = Modifier
                                    .size(80.dp)
                                    .clip(CircleShape)
                                    .clickable {
                                        if (pin.isNotEmpty()) pin = pin.dropLast(1)
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.Backspace,
                                    contentDescription = "Delete",
                                    tint = Color.White,
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                        } else {
                            Box(
                                modifier = Modifier
                                    .size(80.dp)
                                    .clip(CircleShape)
                                    .background(Color.White.copy(alpha = 0.1f))
                                    .clickable {
                                        if (pin.length < 4) {
                                            pin += key
                                            if (pin.length == 4) {
                                                if (onPinSubmit(pin)) {
                                                    onPinVerified()
                                                } else {
                                                    shakeError = true
                                                    pin = ""
                                                }
                                            }
                                        }
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    key,
                                    style = MaterialTheme.typography.headlineMedium,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
            }

            if (biometricAvailable) {
                Spacer(Modifier.height(16.dp))
                OutlinedButton(
                    onClick = onBiometricRequest,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.height(56.dp)
                ) {
                    Icon(Icons.Default.Fingerprint, contentDescription = null, tint = WarmGold)
                    Spacer(Modifier.width(8.dp))
                    Text("Use fingerprint", color = Color.White)
                }
            }
        }
    }
}

@Composable
fun PinSetupScreen(
    onPinSet: (String) -> Unit,
    onSkip: () -> Unit,
    heading: String = "Let's protect Safe Companion",
    body: String = "Set a 4-number PIN so only you can open Safe Companion"
) {
    var pin by remember { mutableStateOf("") }
    var confirmPin by remember { mutableStateOf("") }
    var isConfirming by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = WarmWhite
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                Icons.Default.Lock,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = NavyBlue
            )

            Spacer(Modifier.height(16.dp))

            Text(
                heading,
                style = MaterialTheme.typography.headlineSmall,
                color = NavyBlue,
                textAlign = TextAlign.Center,
                fontWeight = FontWeight.Bold
            )

            Spacer(Modifier.height(8.dp))

            Text(
                if (isConfirming) "Enter the same PIN again to confirm"
                else body,
                style = MaterialTheme.typography.bodyLarge,
                color = TextSecondary,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(32.dp))

            // PIN dots
            val currentPin = if (isConfirming) confirmPin else pin
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                repeat(4) { index ->
                    Box(
                        modifier = Modifier
                            .size(20.dp)
                            .clip(CircleShape)
                            .background(
                                if (index < currentPin.length) NavyBlue
                                else NavyBlue.copy(alpha = 0.2f)
                            )
                    )
                }
            }

            if (error != null) {
                Spacer(Modifier.height(12.dp))
                Text(error!!, style = MaterialTheme.typography.bodyMedium, color = ScamRed)
            }

            Spacer(Modifier.height(32.dp))

            // Number pad
            val numbers = listOf(
                listOf("1", "2", "3"),
                listOf("4", "5", "6"),
                listOf("7", "8", "9"),
                listOf("", "0", "DEL")
            )

            numbers.forEach { row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    row.forEach { key ->
                        if (key.isEmpty()) {
                            Spacer(Modifier.size(80.dp))
                        } else if (key == "DEL") {
                            Box(
                                modifier = Modifier
                                    .size(80.dp)
                                    .clip(CircleShape)
                                    .clickable {
                                        if (isConfirming) {
                                            if (confirmPin.isNotEmpty()) confirmPin = confirmPin.dropLast(1)
                                        } else {
                                            if (pin.isNotEmpty()) pin = pin.dropLast(1)
                                        }
                                        error = null
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.Backspace,
                                    contentDescription = "Delete",
                                    tint = NavyBlue,
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                        } else {
                            Box(
                                modifier = Modifier
                                    .size(80.dp)
                                    .clip(CircleShape)
                                    .background(NavyBlue.copy(alpha = 0.1f))
                                    .clickable {
                                        error = null
                                        if (isConfirming) {
                                            if (confirmPin.length < 4) {
                                                confirmPin += key
                                                if (confirmPin.length == 4) {
                                                    if (confirmPin == pin) {
                                                        onPinSet(pin)
                                                    } else {
                                                        error = "PINs don't match. Try again."
                                                        confirmPin = ""
                                                        isConfirming = false
                                                        pin = ""
                                                    }
                                                }
                                            }
                                        } else {
                                            if (pin.length < 4) {
                                                pin += key
                                                if (pin.length == 4) {
                                                    isConfirming = true
                                                }
                                            }
                                        }
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    key,
                                    style = MaterialTheme.typography.headlineMedium,
                                    color = NavyBlue,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
            }

            Spacer(Modifier.height(16.dp))

            TextButton(onClick = onSkip) {
                Text(
                    "Skip — I'll set this up later",
                    style = MaterialTheme.typography.bodyLarge,
                    color = TextSecondary
                )
            }

            Spacer(Modifier.height(4.dp))

            Text(
                "Without a PIN, anyone who picks up your phone\ncan open Safe Companion",
                style = MaterialTheme.typography.bodySmall,
                color = WarningAmber,
                textAlign = TextAlign.Center
            )
        }
    }
}

package com.safeharborsecurity.app.ui.digest

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.safeharborsecurity.app.ui.theme.*

/**
 * Item 4 (option a): The weekly digest detail screen.
 *
 * Shows the redacted summary text the WeeklyDigestWorker built last Sunday
 * evening, plus a "Share with family" button when the user has opted into
 * family sharing during onboarding.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WeeklyDigestScreen(
    onNavigateBack: () -> Unit,
    viewModel: WeeklyDigestViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Your weekly safety report", color = NavyBlue) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = NavyBlue)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = WarmWhite)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(WarmWhite)
                .padding(padding)
                .padding(horizontal = 20.dp, vertical = 16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            if (state.content.isBlank()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = LightSurface),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        "No report yet. Safe Companion sends a fresh weekly " +
                            "summary every Sunday evening — your first one will " +
                            "appear here next week.",
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.bodyLarge,
                        color = TextPrimary
                    )
                }
            } else {
                // Body of the digest text. Rendered as monospace-leaning prose
                // since the worker uses bullet-style indenting that reads
                // better with consistent line breaks.
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = LightSurface),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        state.content,
                        modifier = Modifier.padding(20.dp),
                        style = MaterialTheme.typography.bodyLarge.copy(lineHeight = 24.sp),
                        color = TextPrimary
                    )
                }

                // Share + Email buttons are always visible now (they used to
                // be gated by an onboarding sub-checkbox; the user decides per
                // report instead). Share opens the system share sheet so the
                // user can pick *any* app — Messages, WhatsApp, Gmail. The
                // Email button is the same content but routed through
                // ACTION_SENDTO mailto: so the email composer opens directly
                // pre-filled, no app picker needed.
                Spacer(Modifier.height(20.dp))
                Button(
                    onClick = {
                        val send = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_SUBJECT, "My weekly Safe Companion report")
                            putExtra(Intent.EXTRA_TEXT, state.content)
                        }
                        context.startActivity(Intent.createChooser(send, "Share this report"))
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = NavyBlue),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Icon(Icons.Default.Share, contentDescription = null, tint = Color.White)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Share this report",
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Spacer(Modifier.height(10.dp))

                OutlinedButton(
                    onClick = {
                        val email = Intent(Intent.ACTION_SENDTO).apply {
                            data = android.net.Uri.parse("mailto:")
                            putExtra(Intent.EXTRA_SUBJECT, "My weekly Safe Companion report")
                            putExtra(Intent.EXTRA_TEXT, state.content)
                        }
                        try {
                            context.startActivity(email)
                        } catch (_: android.content.ActivityNotFoundException) {
                            android.widget.Toast.makeText(
                                context,
                                "No email app is set up on this phone.",
                                android.widget.Toast.LENGTH_LONG
                            ).show()
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Icon(Icons.Default.Email, contentDescription = null, tint = NavyBlue)
                    Spacer(Modifier.width(8.dp))
                    Text("Email this report", color = NavyBlue, fontWeight = FontWeight.SemiBold)
                }

                Spacer(Modifier.height(12.dp))

                OutlinedButton(
                    onClick = {
                        viewModel.dismiss()
                        onNavigateBack()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Icon(Icons.Default.Delete, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Dismiss this report")
                }

                Spacer(Modifier.height(20.dp))

                Text(
                    "This report includes the senders and dates so you can " +
                        "spot anything that was flagged by mistake. It stays " +
                        "on your phone until you tap Share or Email — your " +
                        "email app will show you the full content and let you " +
                        "edit before sending.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
            }
        }
    }
}

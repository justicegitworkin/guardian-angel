package com.guardianangel.app.ui.onboarding

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.guardianangel.app.ui.theme.*

/**
 * Onboarding page 1 — Privacy Promise.
 * Shown after the welcome page, before permissions, so the user understands
 * exactly what data stays on-device and what may be sent to the cloud.
 */
@Composable
fun PrivacyPage(onNext: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Spacer(Modifier.height(24.dp))

        Text("🔒", fontSize = 64.sp, textAlign = TextAlign.Center)

        Text(
            "Your Privacy Matters",
            style = MaterialTheme.typography.headlineMedium,
            color = NavyBlue,
            textAlign = TextAlign.Center
        )

        Text(
            "Guardian Angel was designed from day one to protect both your safety AND your privacy.",
            style = MaterialTheme.typography.bodyLarge,
            color = TextSecondary,
            textAlign = TextAlign.Center
        )

        // Promise cards
        PrivacyPromiseCard(
            emoji = "📵",
            title = "No message content ever stored",
            body = "When Guardian reads an SMS or listens on a call, only the AI's verdict is saved — never the actual words."
        )

        PrivacyPromiseCard(
            emoji = "📱",
            title = "On-device option available",
            body = "On supported devices (Pixel 8+), Guardian can analyse messages entirely on your phone without sending anything to the internet."
        )

        PrivacyPromiseCard(
            emoji = "☁️",
            title = "Cloud AI: Anthropic Claude",
            body = "When cloud AI is used, your message is sent securely to Anthropic's Claude API. Anthropic does not use API data to train their models."
        )

        PrivacyPromiseCard(
            emoji = "🔑",
            title = "API key encrypted on-device",
            body = "Your Claude API key is stored using Android's hardware-backed Keystore — it never leaves your device in readable form."
        )

        Text(
            "You can change these settings at any time in Settings → Privacy & Security.",
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(8.dp))

        Button(
            onClick = onNext,
            modifier = Modifier
                .fillMaxWidth()
                .height(60.dp),
            colors = ButtonDefaults.buttonColors(containerColor = NavyBlue),
            shape = RoundedCornerShape(16.dp)
        ) {
            Text(
                "I Understand — Continue",
                style = MaterialTheme.typography.titleMedium,
                color = Color.White
            )
            Spacer(Modifier.width(8.dp))
            Icon(Icons.Default.ArrowForward, contentDescription = null, tint = Color.White)
        }

        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun PrivacyPromiseCard(emoji: String, title: String, body: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = SafeGreenLight),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.Top
        ) {
            Text(emoji, fontSize = 28.sp)
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, style = MaterialTheme.typography.titleSmall, color = NavyBlue)
                Text(body, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
            }
        }
    }
}

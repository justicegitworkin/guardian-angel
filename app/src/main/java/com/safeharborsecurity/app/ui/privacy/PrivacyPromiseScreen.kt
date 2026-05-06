package com.safeharborsecurity.app.ui.privacy

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.safeharborsecurity.app.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrivacyPromiseScreen(
    isOnboarding: Boolean = false,
    onAcknowledge: () -> Unit = {},
    onNavigateBack: (() -> Unit)? = null
) {
    var isChecked by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = WarmWhite,
        topBar = {
            if (onNavigateBack != null) {
                TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = NavyBlue),
                    navigationIcon = {
                        IconButton(onClick = onNavigateBack) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                        }
                    },
                    title = {
                        Text("Our Privacy Promise", style = MaterialTheme.typography.titleLarge, color = Color.White)
                    }
                )
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                Icons.Default.Shield,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = NavyBlue
            )

            Spacer(Modifier.height(12.dp))

            Text(
                "Our Privacy Promise to You",
                style = MaterialTheme.typography.headlineSmall,
                color = NavyBlue,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(4.dp))

            Text(
                "Safe Companion will always be honest about how your information is used.",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(20.dp))

            PromiseCard(
                icon = Icons.Default.Message,
                title = "What Safe Companion sees",
                body = "Safe Companion reads your text messages and call information to check for scams. " +
                    "This happens on your phone — your messages are sent to the Claude AI service only to be analysed, " +
                    "then immediately forgotten. We do not store your messages."
            )

            PromiseCard(
                icon = Icons.Default.Key,
                title = "Your API key",
                body = "Your Claude AI connection key is stored securely on your phone using military-grade encryption. " +
                    "We never see it, store it on our servers, or share it with anyone."
            )

            PromiseCard(
                icon = Icons.Default.Person,
                title = "Your personal information",
                body = "Your name and phone contacts stay on your phone. " +
                    "We do not upload them to any server. Ever."
            )

            PromiseCard(
                icon = Icons.Default.FamilyRestroom,
                title = "Family alerts",
                body = "When Safe Companion sends alerts to your family, those messages go directly from your phone via SMS. " +
                    "They do not pass through our servers."
            )

            PromiseCard(
                icon = Icons.Default.Block,
                title = "No advertising. Ever.",
                body = "Safe Companion does not show ads. We do not sell your data to advertisers. " +
                    "We do not share your information with third parties for marketing. Your data is yours."
            )

            PromiseCard(
                icon = Icons.Default.MicOff,
                title = "No hidden listening",
                body = "Safe Companion only uses your microphone when you are actively speaking to it. " +
                    "It does not listen in the background."
            )

            PromiseCard(
                icon = Icons.Default.Settings,
                title = "You are in control",
                body = "You can delete all Safe Companion data at any time from Settings. " +
                    "This removes everything from your phone immediately."
            )

            if (isOnboarding) {
                Spacer(Modifier.height(20.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Checkbox(
                        checked = isChecked,
                        onCheckedChange = { isChecked = it },
                        colors = CheckboxDefaults.colors(checkedColor = NavyBlue)
                    )
                    Text(
                        "I understand and I trust Safe Companion with my privacy",
                        style = MaterialTheme.typography.bodyLarge,
                        color = TextPrimary
                    )
                }

                Spacer(Modifier.height(16.dp))

                Button(
                    onClick = onAcknowledge,
                    enabled = isChecked,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(60.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = NavyBlue,
                        disabledContainerColor = NavyBlue.copy(alpha = 0.4f)
                    ),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text("Continue", style = MaterialTheme.typography.titleMedium, color = Color.White)
                }
            }

            Spacer(Modifier.height(24.dp))

            Text(
                "Questions? Email us at privacy@safeharborsecurity.app",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun PromiseCard(icon: ImageVector, title: String, body: String) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(icon, contentDescription = null, tint = SafeGreen, modifier = Modifier.size(24.dp))
                Text(title, style = MaterialTheme.typography.titleSmall, color = NavyBlue, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(8.dp))
            Text(body, style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
        }
    }
}

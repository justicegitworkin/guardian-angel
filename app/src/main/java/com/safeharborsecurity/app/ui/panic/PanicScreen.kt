package com.safeharborsecurity.app.ui.panic

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.telephony.SmsManager
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.safeharborsecurity.app.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PanicScreen(
    onNavigateBack: () -> Unit,
    onNavigateToChat: (String) -> Unit,
    viewModel: PanicViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var currentStep by remember { mutableIntStateOf(0) }
    var sentMoney by remember { mutableStateOf<Boolean?>(null) }

    LaunchedEffect(Unit) {
        viewModel.recordPanicEvent()
    }

    Scaffold(
        containerColor = WarmWhite,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = NavyBlue),
                title = { Text("Need Help?", style = MaterialTheme.typography.titleLarge, color = Color.White) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color.White)
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            when (currentStep) {
                0 -> PanicStep1 { currentStep = 1 }
                1 -> PanicStep2 { currentStep = 2 }
                2 -> PanicStep3(
                    onYes = { sentMoney = true; currentStep = 3 },
                    onNo = { sentMoney = false; currentStep = 4 }
                )
                3 -> PanicStep3a(
                    bankNumber = state.bankPhoneNumber,
                    context = context,
                    onContinue = { currentStep = 4 }
                )
                4 -> PanicStep4(
                    context = context,
                    userName = state.userName,
                    familyContacts = state.familyContactNumbers,
                    onContinue = { currentStep = 5 }
                )
                5 -> PanicStep5(
                    context = context,
                    onContinue = { currentStep = 6 }
                )
                6 -> PanicStep6(
                    onDone = onNavigateBack,
                    onAskSafeHarbor = { onNavigateToChat("I think I may have been scammed. Can you help me figure out what to do next?") }
                )
            }

            // Progress dots
            if (currentStep < 6) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(top = 16.dp)
                ) {
                    val totalSteps = if (sentMoney == true) 7 else 6
                    repeat(totalSteps) { i ->
                        Surface(
                            modifier = Modifier.size(if (i == currentStep) 12.dp else 8.dp),
                            shape = RoundedCornerShape(50),
                            color = if (i <= currentStep) NavyBlue else TextSecondary.copy(alpha = 0.3f)
                        ) {}
                    }
                }
            }
        }
    }
}

@Composable
private fun PanicStep1(onNext: () -> Unit) {
    Spacer(Modifier.height(20.dp))
    Text("💙", fontSize = 64.sp)
    Spacer(Modifier.height(8.dp))
    Text(
        "Don't worry — you're not alone",
        style = MaterialTheme.typography.headlineMedium,
        color = NavyBlue, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center
    )
    Text(
        "We're going to help you step by step. Take a deep breath. Everything will be okay.",
        style = MaterialTheme.typography.bodyLarge,
        color = TextSecondary, textAlign = TextAlign.Center, lineHeight = 26.sp
    )
    Spacer(Modifier.height(16.dp))
    Button(
        onClick = onNext,
        modifier = Modifier.fillMaxWidth().height(60.dp),
        colors = ButtonDefaults.buttonColors(containerColor = NavyBlue),
        shape = RoundedCornerShape(16.dp)
    ) {
        Text("Let's get started", style = MaterialTheme.typography.titleMedium, color = Color.White)
    }
}

@Composable
private fun PanicStep2(onNext: () -> Unit) {
    Text("🛑", fontSize = 64.sp)
    Spacer(Modifier.height(8.dp))
    Text("Stop all contact", style = MaterialTheme.typography.headlineMedium, color = NavyBlue, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
    Text(
        "If you are still talking to the person, hang up the phone or stop texting them right now. Do not reply to any more messages from them.",
        style = MaterialTheme.typography.bodyLarge, color = TextSecondary, textAlign = TextAlign.Center, lineHeight = 26.sp
    )
    Spacer(Modifier.height(16.dp))
    Button(
        onClick = onNext,
        modifier = Modifier.fillMaxWidth().height(60.dp),
        colors = ButtonDefaults.buttonColors(containerColor = NavyBlue),
        shape = RoundedCornerShape(16.dp)
    ) {
        Text("OK, I've done that", style = MaterialTheme.typography.titleMedium, color = Color.White)
    }
}

@Composable
private fun PanicStep3(onYes: () -> Unit, onNo: () -> Unit) {
    Text("💰", fontSize = 64.sp)
    Spacer(Modifier.height(8.dp))
    Text("Did you send any money?", style = MaterialTheme.typography.headlineMedium, color = NavyBlue, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
    Text(
        "This includes bank transfers, gift cards, cryptocurrency, or any other payment.",
        style = MaterialTheme.typography.bodyLarge, color = TextSecondary, textAlign = TextAlign.Center, lineHeight = 26.sp
    )
    Spacer(Modifier.height(16.dp))
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Button(
            onClick = onYes,
            modifier = Modifier.weight(1f).height(60.dp),
            colors = ButtonDefaults.buttonColors(containerColor = ScamRed),
            shape = RoundedCornerShape(16.dp)
        ) {
            Text("Yes", style = MaterialTheme.typography.titleMedium, color = Color.White)
        }
        Button(
            onClick = onNo,
            modifier = Modifier.weight(1f).height(60.dp),
            colors = ButtonDefaults.buttonColors(containerColor = SafeGreen),
            shape = RoundedCornerShape(16.dp)
        ) {
            Text("No", style = MaterialTheme.typography.titleMedium, color = Color.White)
        }
    }
}

@Composable
private fun PanicStep3a(bankNumber: String, context: Context, onContinue: () -> Unit) {
    Text("🏦", fontSize = 64.sp)
    Spacer(Modifier.height(8.dp))
    Text("Call your bank right away", style = MaterialTheme.typography.headlineMedium, color = NavyBlue, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
    Text(
        "Tell them you think you've been scammed. They may be able to stop the payment or protect your account.",
        style = MaterialTheme.typography.bodyLarge, color = TextSecondary, textAlign = TextAlign.Center, lineHeight = 26.sp
    )
    Spacer(Modifier.height(16.dp))

    if (bankNumber.isNotBlank()) {
        Button(
            onClick = {
                val intent = Intent(Intent.ACTION_DIAL).apply { data = Uri.parse("tel:$bankNumber") }
                context.startActivity(intent)
            },
            modifier = Modifier.fillMaxWidth().height(60.dp),
            colors = ButtonDefaults.buttonColors(containerColor = ScamRed),
            shape = RoundedCornerShape(16.dp)
        ) {
            Icon(Icons.Default.Phone, null, tint = Color.White)
            Spacer(Modifier.width(8.dp))
            Text("Call My Bank Now", style = MaterialTheme.typography.titleMedium, color = Color.White)
        }
    } else {
        Text(
            "Tip: Add your bank's phone number in Settings so you can call them with one tap next time.",
            style = MaterialTheme.typography.bodyMedium, color = WarningAmber, textAlign = TextAlign.Center
        )
    }

    Spacer(Modifier.height(8.dp))
    OutlinedButton(
        onClick = onContinue,
        modifier = Modifier.fillMaxWidth().height(52.dp),
        shape = RoundedCornerShape(16.dp)
    ) {
        Text("Continue to next step", style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun PanicStep4(context: Context, userName: String, familyContacts: List<String>, onContinue: () -> Unit) {
    Text("👨‍👩‍👧", fontSize = 64.sp)
    Spacer(Modifier.height(8.dp))
    Text("Tell a family member", style = MaterialTheme.typography.headlineMedium, color = NavyBlue, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
    Text(
        "It helps to let someone you trust know what happened. They can help you with next steps.",
        style = MaterialTheme.typography.bodyLarge, color = TextSecondary, textAlign = TextAlign.Center, lineHeight = 26.sp
    )
    Spacer(Modifier.height(16.dp))

    if (familyContacts.isNotEmpty()) {
        Button(
            onClick = {
                val displayName = if (userName.isNotBlank()) userName else "Your family member"
                val message = "Hi, this is a message from Safe Companion. $displayName needs your help — they may have been targeted by a scam. Please call them as soon as you can."
                familyContacts.forEach { number ->
                    try {
                        @Suppress("DEPRECATION")
                        SmsManager.getDefault().sendTextMessage(number, null, message, null, null)
                    } catch (_: Exception) { }
                }
                android.widget.Toast.makeText(context, "Message sent to your family contacts", android.widget.Toast.LENGTH_LONG).show()
            },
            modifier = Modifier.fillMaxWidth().height(60.dp),
            colors = ButtonDefaults.buttonColors(containerColor = NavyBlue),
            shape = RoundedCornerShape(16.dp)
        ) {
            Icon(Icons.Default.Sms, null, tint = Color.White)
            Spacer(Modifier.width(8.dp))
            Text("Send message to family", style = MaterialTheme.typography.titleMedium, color = Color.White)
        }
    } else {
        Text(
            "Tip: Add family contacts in Settings so Safe Companion can alert them automatically.",
            style = MaterialTheme.typography.bodyMedium, color = WarningAmber, textAlign = TextAlign.Center
        )
    }

    Spacer(Modifier.height(8.dp))
    OutlinedButton(
        onClick = onContinue,
        modifier = Modifier.fillMaxWidth().height(52.dp),
        shape = RoundedCornerShape(16.dp)
    ) {
        Text("Continue to next step", style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun PanicStep5(context: Context, onContinue: () -> Unit) {
    Text("📋", fontSize = 64.sp)
    Spacer(Modifier.height(8.dp))
    Text("Report it", style = MaterialTheme.typography.headlineMedium, color = NavyBlue, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
    Text(
        "Reporting helps protect others from the same scam. You can do one or both of these.",
        style = MaterialTheme.typography.bodyLarge, color = TextSecondary, textAlign = TextAlign.Center, lineHeight = 26.sp
    )
    Spacer(Modifier.height(16.dp))

    Button(
        onClick = {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://reportfraud.ftc.gov/"))
            context.startActivity(intent)
        },
        modifier = Modifier.fillMaxWidth().height(60.dp),
        colors = ButtonDefaults.buttonColors(containerColor = NavyBlue),
        shape = RoundedCornerShape(16.dp)
    ) {
        Text("Report to FTC (online)", style = MaterialTheme.typography.titleMedium, color = Color.White)
    }

    Button(
        onClick = {
            val intent = Intent(Intent.ACTION_DIAL).apply { data = Uri.parse("tel:911") }
            context.startActivity(intent)
        },
        modifier = Modifier.fillMaxWidth().height(60.dp),
        colors = ButtonDefaults.buttonColors(containerColor = WarningAmber),
        shape = RoundedCornerShape(16.dp)
    ) {
        Icon(Icons.Default.Phone, null, tint = Color.White)
        Spacer(Modifier.width(8.dp))
        Text("Call local police (non-emergency)", style = MaterialTheme.typography.titleMedium, color = Color.White)
    }

    Spacer(Modifier.height(8.dp))
    OutlinedButton(
        onClick = onContinue,
        modifier = Modifier.fillMaxWidth().height(52.dp),
        shape = RoundedCornerShape(16.dp)
    ) {
        Text("Continue to next step", style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun PanicStep6(onDone: () -> Unit, onAskSafeHarbor: () -> Unit) {
    Text("✅", fontSize = 64.sp)
    Spacer(Modifier.height(8.dp))
    Text("You've taken the right steps", style = MaterialTheme.typography.headlineMedium, color = NavyBlue, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)

    Card(
        colors = CardDefaults.cardColors(containerColor = SafeGreenLight),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Checklist", style = MaterialTheme.typography.titleSmall, color = SafeGreen, fontWeight = FontWeight.Bold)
            Text("✅ Stopped contact with the scammer", style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
            Text("✅ Contacted your bank (if needed)", style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
            Text("✅ Told a family member", style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
            Text("✅ Reported the scam", style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
        }
    }

    Spacer(Modifier.height(8.dp))

    Button(
        onClick = onAskSafeHarbor,
        modifier = Modifier.fillMaxWidth().height(60.dp),
        colors = ButtonDefaults.buttonColors(containerColor = NavyBlue),
        shape = RoundedCornerShape(16.dp)
    ) {
        Text("Talk to Safe Companion about this", style = MaterialTheme.typography.titleMedium, color = Color.White)
    }

    OutlinedButton(
        onClick = onDone,
        modifier = Modifier.fillMaxWidth().height(52.dp),
        shape = RoundedCornerShape(16.dp)
    ) {
        Text("Done", style = MaterialTheme.typography.labelLarge)
    }
}

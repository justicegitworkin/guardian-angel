package com.safeharborsecurity.app.ui.safety

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.safeharborsecurity.app.data.model.NfcRiskLevel
import com.safeharborsecurity.app.data.model.NfcTagAnalysis
import com.safeharborsecurity.app.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NfcWarningScreen(
    tagAnalysis: NfcTagAnalysis?,
    onNavigateBack: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("NFC Tag Check", color = Color.White, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = NavyBlue)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (tagAnalysis == null) {
                Spacer(Modifier.height(80.dp))
                Icon(Icons.Default.Contactless, "NFC", tint = NavyBlue, modifier = Modifier.size(80.dp))
                Spacer(Modifier.height(16.dp))
                Text(
                    "No NFC Tag Detected",
                    style = MaterialTheme.typography.headlineSmall,
                    color = NavyBlue,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "Hold your phone near an NFC tag to check it.",
                    fontSize = 16.sp,
                    color = Color.Gray,
                    textAlign = TextAlign.Center
                )
                return@Scaffold
            }

            // Risk level header
            val (bgColor, iconColor, statusIcon) = when (tagAnalysis.riskLevel) {
                NfcRiskLevel.SAFE -> Triple(SafeGreen.copy(alpha = 0.1f), SafeGreen, Icons.Default.CheckCircle)
                NfcRiskLevel.CAUTION -> Triple(WarningAmber.copy(alpha = 0.1f), WarningAmber, Icons.Default.Warning)
                NfcRiskLevel.DANGEROUS -> Triple(ScamRed.copy(alpha = 0.1f), ScamRed, Icons.Default.Dangerous)
                NfcRiskLevel.UNKNOWN -> Triple(Color.Gray.copy(alpha = 0.1f), Color.Gray, Icons.Default.HelpOutline)
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = bgColor),
                shape = RoundedCornerShape(20.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(statusIcon, "Status", tint = iconColor, modifier = Modifier.size(64.dp))
                    Text(
                        tagAnalysis.riskLevel.label,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = iconColor
                    )
                    Text(
                        tagAnalysis.summary,
                        fontSize = 18.sp,
                        color = NavyBlue,
                        textAlign = TextAlign.Center
                    )
                }
            }

            // Details card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("What we found", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = NavyBlue)
                    Text(tagAnalysis.details, fontSize = 16.sp, color = Color.DarkGray)
                }
            }

            // Payloads card
            if (tagAnalysis.payloads.isNotEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    shape = RoundedCornerShape(16.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Tag Contents", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = NavyBlue)
                        tagAnalysis.payloads.forEach { payload ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.Top,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                val payloadIcon = if (payload.isSuspicious) Icons.Default.Warning else Icons.Default.Info
                                val payloadColor = if (payload.isSuspicious) WarningAmber else NavyBlue
                                Icon(payloadIcon, payload.type, tint = payloadColor, modifier = Modifier.size(20.dp))
                                Column {
                                    Text(payload.type, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = payloadColor)
                                    Text(payload.content, fontSize = 14.sp, color = Color.DarkGray)
                                }
                            }
                        }
                    }
                }
            }

            // Recommendation card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = NavyBlue.copy(alpha = 0.05f)),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("What to do", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = NavyBlue)
                    Text(tagAnalysis.recommendation, fontSize = 16.sp, color = Color.DarkGray)
                }
            }

            // Technical details
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Technical Details", fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = Color.Gray)
                    Text("Tag ID: ${tagAnalysis.tagId}", fontSize = 13.sp, color = Color.Gray)
                    Text("Technologies: ${tagAnalysis.techList.joinToString(", ")}", fontSize = 13.sp, color = Color.Gray)
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

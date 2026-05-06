package com.safeharborsecurity.app.ui.home

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun QuickCallFamilyButton(
    familyContactName: String?,
    familyContactPhone: String?,
    onNoContactSetup: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val safeGreen = Color(0xFF4CAF50)

    Button(
        onClick = {
            if (familyContactPhone.isNullOrBlank()) {
                onNoContactSetup()
            } else {
                val dialIntent = Intent(Intent.ACTION_DIAL).apply {
                    data = Uri.parse("tel:$familyContactPhone")
                }
                context.startActivity(dialIntent)
            }
        },
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = safeGreen,
            contentColor = Color.White
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.Phone,
                contentDescription = null,
                modifier = Modifier.size(22.dp)
            )
            Icon(
                Icons.Default.Favorite,
                contentDescription = null,
                modifier = Modifier.size(16.dp)
            )
            Text(
                text = if (familyContactName.isNullOrBlank()) "Call My Family" else "Call $familyContactName",
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

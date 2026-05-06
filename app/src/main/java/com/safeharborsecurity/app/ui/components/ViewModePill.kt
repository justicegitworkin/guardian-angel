package com.safeharborsecurity.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val PillTeal = Color(0xFF00897B)
private val PillBackground = Color.White.copy(alpha = 0.10f)
private val ActiveText = Color.White
private val InactiveText = Color.White.copy(alpha = 0.45f)

@Composable
fun ViewModePill(
    isSimpleMode: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(PillBackground)
    ) {
        // "Focused" option — active when isSimpleMode == true
        PillOption(
            label = "Focused",
            isActive = isSimpleMode,
            onClick = { if (!isSimpleMode) onToggle() }
        )
        // "Show All" option — active when isSimpleMode == false
        PillOption(
            label = "Show All",
            isActive = !isSimpleMode,
            onClick = { if (isSimpleMode) onToggle() }
        )
    }
}

@Composable
private fun PillOption(
    label: String,
    isActive: Boolean,
    onClick: () -> Unit
) {
    Text(
        text = label,
        fontSize = 13.sp,
        fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
        color = if (isActive) ActiveText else InactiveText,
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .then(
                if (isActive) Modifier.background(PillTeal)
                else Modifier
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 16.dp, vertical = 7.dp)
    )
}

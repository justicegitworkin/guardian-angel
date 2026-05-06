package com.safeharborsecurity.app.ui.chat.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.safeharborsecurity.app.data.model.VoiceTier

@Composable
fun VoiceWaveformIndicator(
    isPlaying: Boolean,
    tier: VoiceTier,
    modifier: Modifier = Modifier
) {
    val color = when (tier) {
        VoiceTier.ELEVEN_LABS -> Color(0xFF4A90D9)  // Blue
        VoiceTier.GOOGLE_NEURAL -> Color(0xFF2ECC71) // Green
        VoiceTier.ANDROID_TTS -> Color(0xFF95A5A6)   // Grey
    }

    val infiniteTransition = rememberInfiniteTransition(label = "waveform")

    Row(
        modifier = modifier.height(24.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(4) { index ->
            val animatedHeight by infiniteTransition.animateFloat(
                initialValue = if (isPlaying) 6f else 4f,
                targetValue = if (isPlaying) 20f else 4f,
                animationSpec = infiniteRepeatable(
                    animation = tween(
                        durationMillis = 400 + index * 100,
                        easing = FastOutSlowInEasing
                    ),
                    repeatMode = RepeatMode.Reverse,
                    initialStartOffset = StartOffset(index * 80)
                ),
                label = "bar_$index"
            )

            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(animatedHeight.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(if (isPlaying) color else color.copy(alpha = 0.3f))
            )
        }
    }
}

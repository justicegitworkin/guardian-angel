package com.safeharborsecurity.app.ui.points

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.safeharborsecurity.app.data.local.entity.PointsBalanceEntity

private val Teal = Color(0xFF00897B)
private val Gold = Color(0xFFE8A833)

@Composable
fun PointsCard(
    onNavigateToPoints: () -> Unit,
    viewModel: PointsViewModel = hiltViewModel()
) {
    val balance by viewModel.balance.collectAsStateWithLifecycle(initialValue = null)

    PointsCardContent(
        balance = balance,
        onClick = onNavigateToPoints
    )
}

@Composable
fun PointsCardContent(
    balance: PointsBalanceEntity?,
    onClick: () -> Unit
) {
    val totalPoints = balance?.totalEarned ?: 0
    val streak = balance?.currentStreak ?: 0

    // Calculate progress to next milestone
    val nextMilestone = when {
        totalPoints < 100 -> 100L
        totalPoints < 500 -> 500L
        totalPoints < 1_000 -> 1_000L
        totalPoints < 2_500 -> 2_500L
        totalPoints < 5_000 -> 5_000L
        totalPoints < 10_000 -> 10_000L
        totalPoints < 25_000 -> 25_000L
        totalPoints < 50_000 -> 50_000L
        else -> totalPoints // Already at max
    }
    val progress = if (nextMilestone > 0) {
        (totalPoints.toFloat() / nextMilestone.toFloat()).coerceIn(0f, 1f)
    } else 1f

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Circular progress ring
            val trackColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.15f)
            Box(
                modifier = Modifier.size(52.dp),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.size(52.dp)) {
                    // Track
                    drawArc(
                        color = trackColor,
                        startAngle = -90f,
                        sweepAngle = 360f,
                        useCenter = false,
                        style = Stroke(width = 5.dp.toPx(), cap = StrokeCap.Round)
                    )
                    // Progress
                    drawArc(
                        color = Teal,
                        startAngle = -90f,
                        sweepAngle = 360f * progress,
                        useCenter = false,
                        style = Stroke(width = 5.dp.toPx(), cap = StrokeCap.Round)
                    )
                }
                Text(
                    text = "\uD83D\uDEE1\uFE0F",
                    fontSize = 20.sp
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "%,d pts".format(totalPoints),
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Spacer(modifier = Modifier.height(2.dp))

                if (streak > 1) {
                    Text(
                        text = "\uD83D\uDD25 $streak day streak",
                        style = MaterialTheme.typography.bodySmall,
                        color = Gold,
                        fontWeight = FontWeight.SemiBold
                    )
                } else {
                    Text(
                        text = "Safety Points",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

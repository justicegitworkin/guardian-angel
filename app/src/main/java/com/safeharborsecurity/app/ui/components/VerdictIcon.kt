package com.safeharborsecurity.app.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val DangerousRed = Color(0xFFD32F2F)
private val SuspiciousYellow = Color(0xFFFFC107)
private val SuspiciousBorder = Color(0xFFE65100)
private val SafeGreenIcon = Color(0xFF2E7D32)
private val CheckingGrey = Color(0xFF9E9E9E)
private val UnknownGrey = Color(0xFF757575)

enum class Verdict {
    DANGEROUS, SUSPICIOUS, SAFE, CHECKING, UNKNOWN
}

fun String?.toVerdict(): Verdict = when (this?.uppercase()) {
    "DANGEROUS", "SCAM", "HIGH" -> Verdict.DANGEROUS
    "SUSPICIOUS", "WARNING", "MEDIUM" -> Verdict.SUSPICIOUS
    "SAFE", "LOW" -> Verdict.SAFE
    "CHECKING", "PENDING" -> Verdict.CHECKING
    else -> Verdict.UNKNOWN
}

@Composable
fun VerdictIcon(
    verdict: Verdict,
    size: Dp = 28.dp,
    showLabel: Boolean = true,
    modifier: Modifier = Modifier
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.width(IntrinsicSize.Min)
    ) {
        when (verdict) {
            Verdict.DANGEROUS -> DangerousIcon(size)
            Verdict.SUSPICIOUS -> SuspiciousIcon(size)
            Verdict.SAFE -> SafeIcon(size)
            Verdict.CHECKING -> CheckingIcon(size)
            Verdict.UNKNOWN -> UnknownIcon(size)
        }

        if (showLabel) {
            Spacer(Modifier.height(2.dp))
            val (label, color) = when (verdict) {
                Verdict.DANGEROUS -> "Likely scam" to DangerousRed
                Verdict.SUSPICIOUS -> "Be careful" to SuspiciousBorder
                Verdict.SAFE -> "Looks safe" to SafeGreenIcon
                Verdict.CHECKING -> "Checking..." to CheckingGrey
                Verdict.UNKNOWN -> "Not checked" to UnknownGrey
            }
            Text(
                text = label,
                fontSize = 11.sp,
                color = color,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun DangerousIcon(size: Dp) {
    Canvas(modifier = Modifier.size(size)) {
        // Red circle
        drawCircle(color = DangerousRed, radius = this.size.minDimension / 2f)
        // White X
        val pad = this.size.minDimension * 0.3f
        val cx = this.size.width / 2f
        val cy = this.size.height / 2f
        val half = (this.size.minDimension / 2f) - pad
        drawLine(Color.White, Offset(cx - half, cy - half), Offset(cx + half, cy + half), strokeWidth = 3.dp.toPx(), cap = StrokeCap.Round)
        drawLine(Color.White, Offset(cx + half, cy - half), Offset(cx - half, cy + half), strokeWidth = 3.dp.toPx(), cap = StrokeCap.Round)
    }
}

@Composable
private fun SuspiciousIcon(size: Dp) {
    Canvas(modifier = Modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val pad = w * 0.05f
        // Triangle path
        val path = Path().apply {
            moveTo(w / 2f, pad)
            lineTo(w - pad, h - pad)
            lineTo(pad, h - pad)
            close()
        }
        // Yellow fill
        drawPath(path, SuspiciousYellow, style = Fill)
        // Dark orange border
        drawPath(path, SuspiciousBorder, style = Stroke(width = 1.5.dp.toPx()))
        // Black exclamation mark
        val cx = w / 2f
        val lineTop = h * 0.35f
        val lineBot = h * 0.6f
        drawLine(Color.Black, Offset(cx, lineTop), Offset(cx, lineBot), strokeWidth = 2.5.dp.toPx(), cap = StrokeCap.Round)
        // Dot
        drawCircle(Color.Black, radius = 1.5.dp.toPx(), center = Offset(cx, h * 0.72f))
    }
}

@Composable
private fun SafeIcon(size: Dp) {
    Canvas(modifier = Modifier.size(size)) {
        // Green circle
        drawCircle(color = SafeGreenIcon, radius = this.size.minDimension / 2f)
        // White checkmark
        val cx = this.size.width / 2f
        val cy = this.size.height / 2f
        val r = this.size.minDimension / 2f * 0.5f
        drawLine(Color.White, Offset(cx - r * 0.6f, cy), Offset(cx - r * 0.1f, cy + r * 0.5f), strokeWidth = 2.5.dp.toPx(), cap = StrokeCap.Round)
        drawLine(Color.White, Offset(cx - r * 0.1f, cy + r * 0.5f), Offset(cx + r * 0.7f, cy - r * 0.5f), strokeWidth = 2.5.dp.toPx(), cap = StrokeCap.Round)
    }
}

@Composable
private fun CheckingIcon(size: Dp) {
    CircularProgressIndicator(
        modifier = Modifier.size(size),
        color = CheckingGrey,
        strokeWidth = 2.5.dp
    )
}

@Composable
private fun UnknownIcon(size: Dp) {
    Canvas(modifier = Modifier.size(size)) {
        drawCircle(color = UnknownGrey, radius = this.size.minDimension / 2f)
        // White question mark — simplified as two strokes + dot
        val cx = this.size.width / 2f
        val cy = this.size.height / 2f
        val r = this.size.minDimension / 2f * 0.35f
        // Arc approximation with lines
        drawLine(Color.White, Offset(cx - r * 0.5f, cy - r * 0.8f), Offset(cx + r * 0.3f, cy - r * 0.8f), strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round)
        drawLine(Color.White, Offset(cx + r * 0.3f, cy - r * 0.8f), Offset(cx + r * 0.3f, cy - r * 0.1f), strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round)
        drawLine(Color.White, Offset(cx + r * 0.3f, cy - r * 0.1f), Offset(cx, cy + r * 0.2f), strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round)
        // Dot
        drawCircle(Color.White, radius = 1.5.dp.toPx(), center = Offset(cx, cy + r * 0.9f))
    }
}

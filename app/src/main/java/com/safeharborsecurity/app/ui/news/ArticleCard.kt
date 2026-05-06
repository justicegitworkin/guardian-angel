package com.safeharborsecurity.app.ui.news

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.safeharborsecurity.app.data.local.entity.NewsArticleEntity
import com.safeharborsecurity.app.ui.theme.*

@Composable
fun ArticleCard(
    article: NewsArticleEntity,
    sourceColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val accentColor = if (!article.isRead) sourceColor else Color.Transparent

    Card(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .drawBehind {
                // Unread left border accent
                if (!article.isRead) {
                    drawRect(
                        color = sourceColor,
                        topLeft = Offset.Zero,
                        size = androidx.compose.ui.geometry.Size(4.dp.toPx(), size.height)
                    )
                }
            },
        colors = CardDefaults.cardColors(
            containerColor = if (!article.isRead) Color.White else Color(0xFFF5F5F5)
        ),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = if (!article.isRead) 2.dp else 0.dp)
    ) {
        Column(
            modifier = Modifier.padding(start = 12.dp, end = 16.dp, top = 12.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // Source pill + timestamp
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = sourceColor.copy(alpha = 0.15f)
                ) {
                    Text(
                        article.source,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = sourceColor
                    )
                }
                Text(
                    formatRelativeTime(article.pubDate),
                    fontSize = 12.sp,
                    color = TextSecondary
                )
            }

            // Title
            Text(
                article.title,
                fontSize = 17.sp,
                fontWeight = if (!article.isRead) FontWeight.Bold else FontWeight.Normal,
                color = if (!article.isRead) NavyBlue else TextPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                lineHeight = 22.sp
            )

            // Summary
            if (article.summary.isNotBlank()) {
                Text(
                    article.summary,
                    fontSize = 14.sp,
                    color = TextSecondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 20.sp
                )
            }
        }
    }
}

fun formatRelativeTime(timestamp: Long): String {
    val now = System.currentTimeMillis()
    val diff = now - timestamp
    val minutes = diff / 60_000
    val hours = minutes / 60
    val days = hours / 24

    return when {
        minutes < 1 -> "Just now"
        minutes < 60 -> "${minutes}m ago"
        hours < 24 -> "${hours}h ago"
        days < 1 -> "Yesterday"
        days < 7 -> "${days}d ago"
        days < 30 -> "${days / 7}w ago"
        else -> "${days / 30}mo ago"
    }
}

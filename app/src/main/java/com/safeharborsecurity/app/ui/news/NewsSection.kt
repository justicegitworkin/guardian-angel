package com.safeharborsecurity.app.ui.news

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.safeharborsecurity.app.data.local.entity.NewsArticleEntity
import com.safeharborsecurity.app.ui.theme.*

@Composable
fun NewsSection(
    articles: List<NewsArticleEntity>,
    onArticleClick: (NewsArticleEntity) -> Unit,
    onSeeAll: () -> Unit,
    onClearAll: () -> Unit = {},
    getSourceColor: (String) -> Long,
    modifier: Modifier = Modifier
) {
    var showClearConfirm by remember { mutableStateOf(false) }
    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            title = { Text("Clear all news articles?") },
            text = {
                Text(
                    "This removes every saved scam-news article from your phone. " +
                        "Fresh ones will appear on the next sync (every six hours, " +
                        "or right now if you're online)."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    onClearAll()
                    showClearConfirm = false
                }) { Text("Clear All", color = ScamRed) }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirm = false }) { Text("Cancel") }
            }
        )
    }

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "What Scammers Are Up To",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = NavyBlue
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (articles.isNotEmpty()) {
                    TextButton(onClick = { showClearConfirm = true }) {
                        Text("Clear All", style = MaterialTheme.typography.bodyLarge, color = ScamRed)
                    }
                }
                TextButton(onClick = onSeeAll) {
                    Text("See all", style = MaterialTheme.typography.bodyLarge, color = WarmGold)
                }
            }
        }

        if (articles.isEmpty()) {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFFF5F5F5)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    "Loading latest scam alerts...",
                    modifier = Modifier.padding(16.dp),
                    fontSize = 15.sp,
                    color = TextSecondary
                )
            }
        } else {
            articles.take(5).forEach { article ->
                ArticleCard(
                    article = article,
                    sourceColor = Color(getSourceColor(article.source)),
                    onClick = { onArticleClick(article) }
                )
            }
        }
    }
}

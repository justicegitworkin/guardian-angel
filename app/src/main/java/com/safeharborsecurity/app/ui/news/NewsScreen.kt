@file:Suppress("DEPRECATION")

package com.safeharborsecurity.app.ui.news

import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.safeharborsecurity.app.data.repository.NewsRepository
import com.safeharborsecurity.app.ui.theme.*
import java.text.SimpleDateFormat
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewsScreen(
    onNavigateBack: () -> Unit,
    viewModel: NewsViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    Scaffold(
        containerColor = WarmWhite,
        topBar = {
            TopAppBar(
                title = { Text("Security News", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (state.unreadCount > 0) {
                        IconButton(onClick = { viewModel.markAllAsRead() }) {
                            Icon(Icons.Default.DoneAll, contentDescription = "Mark all read")
                        }
                    }
                    IconButton(onClick = { viewModel.refresh() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = WarmWhite)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Filter chips
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = state.selectedSource == null,
                    onClick = { viewModel.selectSource(null) },
                    label = { Text("All") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = NavyBlue,
                        selectedLabelColor = Color.White
                    )
                )
                NewsRepository.SOURCES.forEach { source ->
                    FilterChip(
                        selected = state.selectedSource == source.name,
                        onClick = { viewModel.selectSource(source.name) },
                        label = { Text(source.name) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Color(source.color),
                            selectedLabelColor = Color.White
                        )
                    )
                }
            }

            if (state.isRefreshing) {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth(),
                    color = NavyBlue
                )
            }

            if (state.articles.isEmpty() && !state.isRefreshing) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("No articles yet", fontSize = 18.sp, color = TextSecondary)
                        Spacer(Modifier.height(8.dp))
                        Button(
                            onClick = { viewModel.refresh() },
                            colors = ButtonDefaults.buttonColors(containerColor = NavyBlue)
                        ) {
                            Text("Load articles")
                        }
                    }
                }
            } else {
                // Group by date
                val grouped = state.articles.groupBy { article ->
                    val sdf = SimpleDateFormat("EEEE, MMMM d", Locale.US)
                    sdf.format(article.pubDate)
                }

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    grouped.forEach { (dateLabel, articles) ->
                        item {
                            Text(
                                dateLabel,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = TextSecondary,
                                modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                            )
                        }
                        items(articles, key = { it.id }) { article ->
                            ArticleCard(
                                article = article,
                                sourceColor = Color(viewModel.getSourceColor(article.source)),
                                onClick = {
                                    viewModel.markAsRead(article.id)
                                    try {
                                        val intent = CustomTabsIntent.Builder()
                                            .setShowTitle(true)
                                            .build()
                                        intent.launchUrl(context, Uri.parse(article.link))
                                    } catch (e: Exception) {
                                        // Fallback to browser
                                        try {
                                            val browserIntent = android.content.Intent(
                                                android.content.Intent.ACTION_VIEW,
                                                Uri.parse(article.link)
                                            )
                                            context.startActivity(browserIntent)
                                        } catch (_: Exception) { }
                                    }
                                }
                            )
                        }
                    }
                    item { Spacer(Modifier.height(60.dp)) }
                }
            }
        }
    }
}

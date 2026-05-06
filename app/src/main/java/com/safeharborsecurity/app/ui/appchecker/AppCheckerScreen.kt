package com.safeharborsecurity.app.ui.appchecker

import android.text.format.DateUtils
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.safeharborsecurity.app.data.model.InstallSource
import com.safeharborsecurity.app.data.model.InstalledAppInfo
import com.safeharborsecurity.app.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppCheckerScreen(
    onNavigateBack: () -> Unit,
    onAppSelected: (String) -> Unit,
    viewModel: AppCheckerViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        containerColor = WarmWhite,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = NavyBlue),
                title = { Text("Check an App", style = MaterialTheme.typography.titleLarge, color = Color.White) },
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
        ) {
            // Search bar
            OutlinedTextField(
                value = state.searchQuery,
                onValueChange = { viewModel.setSearchQuery(it) },
                placeholder = { Text("Search by app name...", color = TextSecondary) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = TextSecondary) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = LightSurface,
                    unfocusedContainerColor = LightSurface,
                    focusedBorderColor = WarmGold,
                    unfocusedBorderColor = Color.Transparent,
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary
                )
            )

            // Filter chips
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChipItem("All Apps", state.selectedFilter == AppFilter.ALL) {
                    viewModel.setFilter(AppFilter.ALL)
                }
                FilterChipItem("Recently Installed", state.selectedFilter == AppFilter.RECENTLY_INSTALLED) {
                    viewModel.setFilter(AppFilter.RECENTLY_INSTALLED)
                }
                FilterChipItem("Not from Play Store", state.selectedFilter == AppFilter.NOT_FROM_PLAY_STORE) {
                    viewModel.setFilter(AppFilter.NOT_FROM_PLAY_STORE)
                }
            }

            Spacer(Modifier.height(8.dp))

            if (state.isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = WarmGold)
                        Spacer(Modifier.height(16.dp))
                        Text("Loading your apps...", color = TextSecondary, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            } else if (state.filteredApps.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize().padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "No apps found matching your search",
                        color = TextSecondary,
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            } else {
                // App count
                Text(
                    "${state.filteredApps.size} apps",
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelMedium,
                    color = TextSecondary
                )

                LazyColumn(
                    contentPadding = PaddingValues(bottom = 24.dp)
                ) {
                    items(state.filteredApps, key = { it.packageName }) { app ->
                        AppListItem(
                            app = app,
                            onClick = { onAppSelected(app.packageName) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FilterChipItem(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label, fontSize = 13.sp) },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = NavyBlue,
            selectedLabelColor = Color.White,
            containerColor = LightSurface,
            labelColor = TextSecondary
        ),
        shape = RoundedCornerShape(20.dp),
        border = null
    )
}

@Composable
private fun AppListItem(app: InstalledAppInfo, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = LightSurface),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // App icon
            Image(
                bitmap = app.icon.toBitmap(width = 48, height = 48).asImageBitmap(),
                contentDescription = app.appName,
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
            )

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    app.appName,
                    style = MaterialTheme.typography.titleSmall,
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                if (app.developerName != null) {
                    Text(
                        "by ${app.developerName}",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    InstallSourceBadge(app.installSource)

                    Text(
                        DateUtils.getRelativeTimeSpanString(
                            app.firstInstallTime,
                            System.currentTimeMillis(),
                            DateUtils.DAY_IN_MILLIS,
                            DateUtils.FORMAT_ABBREV_RELATIVE
                        ).toString(),
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary
                    )
                }
            }

            Icon(
                Icons.Default.ChevronRight,
                contentDescription = "Check app",
                tint = WarmGold,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

@Composable
fun InstallSourceBadge(source: InstallSource) {
    val (bgColor, textColor) = when (source) {
        InstallSource.PLAY_STORE, InstallSource.SAMSUNG_STORE -> Pair(SafeGreenLight, SafeGreen)
        InstallSource.PRE_INSTALLED, InstallSource.AMAZON_STORE -> Pair(WarningAmberLight, WarningAmber)
        InstallSource.UNKNOWN_SOURCE, InstallSource.SIDELOADED -> Pair(ScamRedLight, ScamRed)
    }

    Surface(
        color = bgColor,
        shape = RoundedCornerShape(8.dp)
    ) {
        Text(
            source.label,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelSmall,
            color = textColor,
            fontWeight = FontWeight.Bold
        )
    }
}

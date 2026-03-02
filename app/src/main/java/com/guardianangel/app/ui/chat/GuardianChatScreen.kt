package com.guardianangel.app.ui.chat

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.guardianangel.app.data.local.entity.MessageEntity
import com.guardianangel.app.ui.theme.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

private const val GREETING = "Hello! I'm your Guardian Angel. I'm here to keep you safe and answer any questions. What's on your mind?"

@Composable
fun GuardianChatScreen(
    initialContext: String = "",
    onNavigateBack: () -> Unit,
    viewModel: GuardianChatViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    // Scroll to bottom when new messages arrive
    LaunchedEffect(state.messages.size) {
        if (state.messages.isNotEmpty()) {
            scope.launch { listState.animateScrollToItem(state.messages.size - 1) }
        }
    }

    // Pre-fill context from notification deep-link
    LaunchedEffect(initialContext) {
        if (initialContext.isNotBlank()) {
            viewModel.onInputChange(initialContext)
        }
    }

    Scaffold(
        containerColor = WarmWhite,
        topBar = {
            GuardianChatTopBar(
                isSpeaking = state.isSpeaking,
                onBack = onNavigateBack,
                onStopSpeaking = viewModel::stopSpeaking
            )
        },
        bottomBar = {
            ChatInputBar(
                text = state.inputText,
                onTextChange = viewModel::onInputChange,
                onSend = { viewModel.sendMessage() },
                isListening = state.isListening,
                isLoading = state.isLoading,
                onMicClick = {
                    if (state.isListening) viewModel.stopListening()
                    else viewModel.startListening()
                }
            )
        }
    ) { padding ->
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(top = 16.dp, bottom = 8.dp)
        ) {
            // Greeting bubble always first
            item {
                GuardianBubble(text = GREETING)
            }

            items(state.messages, key = { it.id }) { message ->
                if (message.isFromUser) {
                    UserBubble(message)
                } else {
                    GuardianBubble(
                        text = message.content,
                        timestamp = message.timestamp,
                        onSpeak = { viewModel.speakText(message.content) }
                    )
                }
            }

            if (state.isLoading) {
                item {
                    GuardianThinkingBubble()
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GuardianChatTopBar(
    isSpeaking: Boolean,
    onBack: () -> Unit,
    onStopSpeaking: () -> Unit
) {
    TopAppBar(
        colors = TopAppBarDefaults.topAppBarColors(containerColor = NavyBlue),
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
            }
        },
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(WarmGold),
                    contentAlignment = Alignment.Center
                ) {
                    Text("😇", fontSize = 22.sp)
                }
                Column {
                    Text("Guardian Angel", style = MaterialTheme.typography.titleMedium, color = Color.White)
                    Text("Your protective companion", style = MaterialTheme.typography.bodySmall, color = WarmGoldLight)
                }
            }
        },
        actions = {
            if (isSpeaking) {
                IconButton(onClick = onStopSpeaking) {
                    Icon(Icons.Default.VolumeOff, contentDescription = "Stop speaking", tint = WarmGold)
                }
            }
        }
    )
}

@Composable
private fun GuardianBubble(
    text: String,
    timestamp: Long? = null,
    onSpeak: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Start,
        verticalAlignment = Alignment.Bottom
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(WarmGold),
            contentAlignment = Alignment.Center
        ) {
            Text("😇", fontSize = 18.sp)
        }

        Spacer(Modifier.width(8.dp))

        Column {
            Card(
                colors = CardDefaults.cardColors(containerColor = GuardianBubble),
                shape = RoundedCornerShape(topStart = 4.dp, topEnd = 16.dp, bottomStart = 16.dp, bottomEnd = 16.dp),
                border = BorderStroke(1.dp, GuardianBubbleBorder)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = text,
                        style = MaterialTheme.typography.bodyLarge,
                        color = TextPrimary
                    )
                    if (onSpeak != null) {
                        Spacer(Modifier.height(6.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            timestamp?.let {
                                Text(
                                    formatChatTime(it),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TextSecondary
                                )
                            }
                            IconButton(
                                onClick = onSpeak,
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    Icons.Default.VolumeUp,
                                    contentDescription = "Read aloud",
                                    tint = WarmGold,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun UserBubble(message: MessageEntity) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = UserBubble),
            shape = RoundedCornerShape(topStart = 16.dp, topEnd = 4.dp, bottomStart = 16.dp, bottomEnd = 16.dp)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text(
                    text = message.content,
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color.White
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = formatChatTime(message.timestamp),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.7f),
                    textAlign = TextAlign.End,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
private fun GuardianThinkingBubble() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Start,
        verticalAlignment = Alignment.Bottom
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(WarmGold),
            contentAlignment = Alignment.Center
        ) {
            Text("😇", fontSize = 18.sp)
        }

        Spacer(Modifier.width(8.dp))

        Card(
            colors = CardDefaults.cardColors(containerColor = GuardianBubble),
            shape = RoundedCornerShape(topStart = 4.dp, topEnd = 16.dp, bottomStart = 16.dp, bottomEnd = 16.dp),
            border = BorderStroke(1.dp, GuardianBubbleBorder)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                repeat(3) { i ->
                    val infiniteTransition = rememberInfiniteTransition(label = "dot$i")
                    val alpha by infiniteTransition.animateFloat(
                        initialValue = 0.3f,
                        targetValue = 1f,
                        animationSpec = infiniteRepeatable(
                            animation = androidx.compose.animation.core.tween(600, delayMillis = i * 200),
                            repeatMode = androidx.compose.animation.core.RepeatMode.Reverse
                        ),
                        label = "dot_alpha$i"
                    )
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(WarmGold.copy(alpha = alpha))
                    )
                }
            }
        }
    }
}

@Composable
private fun ChatInputBar(
    text: String,
    onTextChange: (String) -> Unit,
    onSend: () -> Unit,
    isListening: Boolean,
    isLoading: Boolean,
    onMicClick: () -> Unit
) {
    Surface(
        color = Color.White,
        shadowElevation = 8.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .navigationBarsPadding()
                .imePadding(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = text,
                onValueChange = onTextChange,
                modifier = Modifier.weight(1f),
                placeholder = {
                    Text(
                        "Type your message…",
                        style = MaterialTheme.typography.bodyLarge,
                        color = TextSecondary
                    )
                },
                textStyle = MaterialTheme.typography.bodyLarge,
                maxLines = 4,
                shape = RoundedCornerShape(24.dp)
            )

            // Mic button
            IconButton(
                onClick = onMicClick,
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(if (isListening) ScamRed else NavyBlue)
            ) {
                Icon(
                    if (isListening) Icons.Default.MicOff else Icons.Default.Mic,
                    contentDescription = if (isListening) "Stop listening" else "Start voice input",
                    tint = Color.White,
                    modifier = Modifier.size(26.dp)
                )
            }

            // Send button
            IconButton(
                onClick = onSend,
                enabled = text.isNotBlank() && !isLoading,
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(
                        if (text.isNotBlank() && !isLoading) WarmGold else LightSurface
                    )
            ) {
                Icon(
                    Icons.Default.Send,
                    contentDescription = "Send message",
                    tint = if (text.isNotBlank() && !isLoading) NavyBlue else TextSecondary,
                    modifier = Modifier.size(26.dp)
                )
            }
        }
    }
}

private fun formatChatTime(timestamp: Long): String {
    val sdf = SimpleDateFormat("h:mm a", Locale.getDefault())
    return sdf.format(Date(timestamp))
}

package com.safeharborsecurity.app.ui.chat

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.automirrored.filled.VolumeMute
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.safeharborsecurity.app.data.local.entity.MessageEntity
import com.safeharborsecurity.app.data.model.ChatPersona
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

// Chat accent colors — used alongside MaterialTheme.colorScheme
private val AccentTeal = Color(0xFF00897B)
private val AccentBlue = Color(0xFF1565C0)
private val AccentPurple = Color(0xFF6200EA)
private val MicBlue = Color(0xFF1E88E5)
private val UserBubbleColor = Color(0xFF1E88E5)

/**
 * Issue 1.5 — Visible scrollbar for the voice-assistant message area.
 * Compose has no built-in scrollbar on verticalScroll, so we draw one ourselves
 * on the right edge so AARP-aged users can see the content scrolls.
 *
 * The thumb auto-sizes to content length and tracks the current scroll position.
 * If content fits without scrolling, no thumb is drawn.
 */
private fun Modifier.verticalScrollbar(
    scrollState: ScrollState,
    width: Dp = 6.dp,
    minThumbHeight: Dp = 28.dp,
    color: Color = Color.Gray.copy(alpha = 0.55f),
    trackColor: Color = Color.Gray.copy(alpha = 0.15f)
): Modifier = this.drawWithContent {
    drawContent()
    val maxScroll = scrollState.maxValue.toFloat()
    if (maxScroll <= 0f) return@drawWithContent  // nothing to scroll
    val viewportHeight = size.height
    val totalContentHeight = viewportHeight + maxScroll
    val rawThumbHeight = viewportHeight * viewportHeight / totalContentHeight
    val thumbHeight = rawThumbHeight.coerceAtLeast(minThumbHeight.toPx())
    val thumbY = (scrollState.value.toFloat() / maxScroll) * (viewportHeight - thumbHeight)
    val widthPx = width.toPx()
    val xLeft = size.width - widthPx
    // Track (full height behind the thumb so users see "there is a scrollbar here")
    drawRect(
        color = trackColor,
        topLeft = Offset(xLeft, 0f),
        size = Size(widthPx, viewportHeight)
    )
    drawRect(
        color = color,
        topLeft = Offset(xLeft, thumbY),
        size = Size(widthPx, thumbHeight)
    )
}

/**
 * Same scrollbar drawn over a LazyColumn (chat history list) so the user
 * also sees a scrollbar in the longer-form view.
 */
private fun Modifier.verticalScrollbar(
    listState: LazyListState,
    width: Dp = 6.dp,
    minThumbHeight: Dp = 28.dp,
    color: Color = Color.Gray.copy(alpha = 0.55f),
    trackColor: Color = Color.Gray.copy(alpha = 0.15f)
): Modifier = this.drawWithContent {
    drawContent()
    val info = listState.layoutInfo
    val totalItems = info.totalItemsCount
    if (totalItems == 0) return@drawWithContent
    val visibleItems = info.visibleItemsInfo
    if (visibleItems.isEmpty()) return@drawWithContent
    if (visibleItems.size >= totalItems && info.viewportEndOffset - info.viewportStartOffset
        >= visibleItems.sumOf { it.size }) return@drawWithContent
    val viewportHeight = size.height
    val proportionVisible = visibleItems.size.toFloat() / totalItems.toFloat()
    val thumbHeight = (viewportHeight * proportionVisible).coerceAtLeast(minThumbHeight.toPx())
    val firstVisible = visibleItems.first().index
    val proportionScrolled = firstVisible.toFloat() / totalItems.toFloat()
    val thumbY = proportionScrolled * (viewportHeight - thumbHeight)
    val widthPx = width.toPx()
    val xLeft = size.width - widthPx
    drawRect(
        color = trackColor,
        topLeft = Offset(xLeft, 0f),
        size = Size(widthPx, viewportHeight)
    )
    drawRect(
        color = color,
        topLeft = Offset(xLeft, thumbY),
        size = Size(widthPx, thumbHeight)
    )
}

@Composable
fun SafeHarborChatScreen(
    initialContext: String = "",
    onNavigateBack: () -> Unit,
    onNavigateToSafetyChecker: () -> Unit = {},
    viewModel: SafeHarborChatViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    // Show chat history or voice agent view
    var showHistory by remember { mutableStateOf(false) }

    // Scroll to bottom when new messages arrive
    LaunchedEffect(state.messages.size) {
        if (state.messages.isNotEmpty() && showHistory) {
            scope.launch { listState.animateScrollToItem(state.messages.size - 1) }
        }
    }

    // Auto-submit the alert context when launched from a "Tell Me More" tap.
    // Previous behaviour only stuffed the raw text into the input box, leaving
    // the user to tap Send themselves — which testers didn't realise they had
    // to do, so the agent answered with a generic "tell me what the message
    // said" prompt. Now we frame the alert content as a help request and
    // submit it automatically with speakResponse=true so the agent reads its
    // analysis aloud.
    var contextSubmitted by remember { mutableStateOf(false) }
    LaunchedEffect(initialContext) {
        if (initialContext.isNotBlank() && !contextSubmitted) {
            contextSubmitted = true
            val framed = buildString {
                append("I just received this and I'm worried it might be a scam. ")
                append("Can you walk me through whether this is dangerous, ")
                append("what kind of scam this looks like, and what I should do? ")
                append("Here is exactly what it said:\n\n")
                append("\"")
                append(initialContext.trim())
                append("\"")
            }
            viewModel.sendMessage(text = framed, speakResponse = true)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Top bar
            ChatDarkTopBar(
                persona = state.persona,
                isSpeaking = state.isSpeaking,
                showHistory = showHistory,
                onBack = onNavigateBack,
                onStopSpeaking = viewModel::stopSpeaking,
                onOpenSafetyChecker = onNavigateToSafetyChecker,
                onToggleHistory = { showHistory = !showHistory },
                onTogglePersonaPicker = viewModel::togglePersonaPicker,
                onToggleAutoSpeak = viewModel::toggleAutoSpeak,
                autoSpeak = state.autoSpeak,
                onClearChat = viewModel::clearChat
            )

            if (showHistory) {
                // Chat history view (scrollable messages)
                ChatHistoryView(
                    state = state,
                    listState = listState,
                    onSpeakText = viewModel::speakText,
                    onDismissAction = viewModel::dismissAction,
                    onNavigateToSafetyChecker = onNavigateToSafetyChecker,
                    modifier = Modifier.weight(1f)
                )
            } else {
                // Interactive voice agent view. Tap-the-face uses the unified
                // talk-action handler below so it picks up any context
                // pre-loaded into the textbox (e.g., scam content forwarded
                // from a notification deep-link) instead of starting a fresh
                // voice turn and losing it.
                VoiceAgentView(
                    state = state,
                    onRetryMic = { viewModel.startVoiceTurn() },
                    onAvatarTap = { handleTalkAction(state, viewModel) },
                    modifier = Modifier.weight(1f)
                )
            }

            // Bottom input area
            ChatDarkInputBar(
                text = state.inputText,
                onTextChange = viewModel::onInputChange,
                onSend = { viewModel.sendMessage(speakResponse = true) },
                agentState = state.agentState,
                onMicClick = { handleTalkAction(state, viewModel) },
                isContinuousMode = state.isContinuousMode,
                onToggleContinuousMode = viewModel::toggleContinuousMode
            )
        }

        // Persona picker overlay
        if (state.showPersonaPicker) {
            PersonaPickerSheet(
                currentPersona = state.persona,
                onSelectPersona = viewModel::setPersona,
                onPreviewVoice = viewModel::previewPersonaVoice,
                onDismiss = viewModel::togglePersonaPicker
            )
        }
    }
}

@Composable
private fun VoiceAgentView(
    state: ChatUiState,
    onRetryMic: () -> Unit = {},
    onAvatarTap: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Animated avatar circle — also a tap target. Tapping the persona
        // face is the most accessible way to start/stop talking for older
        // users; the mic button stays as a secondary affordance.
        Box(
            modifier = Modifier.clickable(
                enabled = state.agentState != AgentState.PROCESSING,
                onClick = onAvatarTap
            )
        ) {
            AnimatedAvatarCircle(
                persona = state.persona,
                agentState = state.agentState
            )
        }

        Spacer(Modifier.height(24.dp))

        // Status text
        Text(
            text = state.statusText,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
            fontSize = 18.sp
        )

        // Part 3: Patient subtext below status
        if (state.patientSubtext.isNotBlank()) {
            Spacer(Modifier.height(6.dp))
            Text(
                text = state.patientSubtext,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
                lineHeight = 20.sp
            )
        }

        // Fix 31: Interrupt hint during agent speech
        if (state.showInterruptHint) {
            Spacer(Modifier.height(6.dp))
            Text(
                text = "Tap the face above (or the stop button) to interrupt",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f),
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }

        Spacer(Modifier.height(12.dp))

        // Animated waveform while listening or speaking
        AnimatedVisibility(
            visible = state.agentState == AgentState.LISTENING || state.agentState == AgentState.SPEAKING,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            WaveformIndicator(
                color = when (state.agentState) {
                    AgentState.LISTENING -> AccentTeal
                    AgentState.SPEAKING -> AccentPurple
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
        }

        // Partial transcript while listening
        AnimatedVisibility(
            visible = state.partialSpeechText.isNotBlank() && state.agentState == AgentState.LISTENING,
            enter = fadeIn() + slideInVertically(),
            exit = fadeOut()
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text(
                    text = "\"${state.partialSpeechText}\"",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                )
            }
        }

        // Last agent message (accessibility — always show last response as text)
        if (state.lastAgentMessage.isNotBlank() && state.agentState != AgentState.LISTENING) {
            Spacer(Modifier.height(16.dp))
            // Issue 1.5: Hoist the ScrollState so we can draw a visible scrollbar
            // on the same Card. AARP-aged users won't know to swipe without one.
            val agentScrollState = rememberScrollState()
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 220.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text(
                    text = state.lastAgentMessage,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(agentScrollState)
                        .verticalScrollbar(agentScrollState)
                        .padding(start = 16.dp, top = 16.dp, end = 22.dp, bottom = 16.dp)
                )
            }
        }

        // Part 4: Retry button when silence detected (not an error)
        if (state.showRetryButton && state.agentState == AgentState.IDLE) {
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = onRetryMic,
                colors = ButtonDefaults.buttonColors(containerColor = AccentTeal),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.height(48.dp)
            ) {
                Icon(Icons.Default.Mic, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text("Try Again", color = Color.White, fontSize = 16.sp)
            }
        }

        // Interrupt hint when agent is speaking in continuous mode
        if (state.isContinuousMode && state.agentState == AgentState.SPEAKING) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Tap to interrupt",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f),
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun AnimatedAvatarCircle(
    persona: ChatPersona,
    agentState: AgentState
) {
    val infiniteTransition = rememberInfiniteTransition(label = "avatar")

    // Pulsing outer ring for speaking state
    val ringScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "ring_scale"
    )

    val ringAlpha by infiniteTransition.animateFloat(
        initialValue = 0.6f,
        targetValue = 0.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "ring_alpha"
    )

    val gradientColors = when (agentState) {
        AgentState.IDLE -> listOf(Color(0xFF2A2A3E), Color(0xFF1A1A2E))
        AgentState.LISTENING -> listOf(AccentTeal, Color(0xFF004D40))
        AgentState.PROCESSING -> listOf(AccentBlue, Color(0xFF0D47A1))
        AgentState.SPEAKING -> listOf(AccentPurple, Color(0xFF4A148C))
        AgentState.SPEAK_COOLDOWN -> listOf(Color(0xFF2A2A3E), Color(0xFF1A1A2E))
    }

    val ringColor = when (agentState) {
        AgentState.SPEAKING -> AccentPurple
        AgentState.LISTENING -> AccentTeal
        AgentState.PROCESSING -> AccentBlue
        else -> Color.Transparent
    }

    Box(contentAlignment = Alignment.Center) {
        // Outer pulsing ring (only when speaking or listening)
        if (agentState == AgentState.SPEAKING || agentState == AgentState.LISTENING) {
            Box(
                modifier = Modifier
                    .size(200.dp)
                    .scale(ringScale)
                    .clip(CircleShape)
                    .background(ringColor.copy(alpha = ringAlpha))
            )
        }

        // Main avatar circle
        Box(
            modifier = Modifier
                .size(180.dp)
                .clip(CircleShape)
                .background(Brush.verticalGradient(gradientColors)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = persona.emoji,
                fontSize = 72.sp
            )
        }

        // Thinking spinner overlay
        if (agentState == AgentState.PROCESSING) {
            CircularProgressIndicator(
                modifier = Modifier.size(200.dp),
                color = AccentBlue.copy(alpha = 0.5f),
                strokeWidth = 3.dp
            )
        }
    }
}

@Composable
private fun WaveformIndicator(color: Color) {
    val infiniteTransition = rememberInfiniteTransition(label = "waveform")

    Row(
        modifier = Modifier
            .width(120.dp)
            .height(40.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(5) { i ->
            val height by infiniteTransition.animateFloat(
                initialValue = 8f,
                targetValue = 32f,
                animationSpec = infiniteRepeatable(
                    animation = tween(400 + i * 80, easing = EaseInOutSine),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "bar_$i"
            )
            Box(
                modifier = Modifier
                    .width(6.dp)
                    .height(height.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(color)
            )
        }
    }
}

@Composable
private fun ContinuousConversationButton(
    isActive: Boolean,
    onClick: () -> Unit
) {
    // Disabled for now — continuous conversation causes self-interruption issues.
    // Button stays visible but greyed out and non-functional.
    val buttonColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)

    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(contentAlignment = Alignment.TopEnd) {
            IconButton(
                onClick = { /* disabled — continuous mode temporarily unavailable */ },
                enabled = false,
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(buttonColor)
            ) {
                Column(
                    modifier = Modifier.size(28.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Three lines of decreasing width
                    val lineColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
                    Box(
                        Modifier
                            .width(22.dp)
                            .height(3.dp)
                            .clip(RoundedCornerShape(1.5.dp))
                            .background(lineColor)
                    )
                    Spacer(Modifier.height(3.dp))
                    Box(
                        Modifier
                            .width(16.dp)
                            .height(3.dp)
                            .clip(RoundedCornerShape(1.5.dp))
                            .background(lineColor)
                    )
                    Spacer(Modifier.height(3.dp))
                    Box(
                        Modifier
                            .width(10.dp)
                            .height(3.dp)
                            .clip(RoundedCornerShape(1.5.dp))
                            .background(lineColor)
                    )
                }
            }
        }
    }
}

@Composable
private fun ChatHistoryView(
    state: ChatUiState,
    listState: androidx.compose.foundation.lazy.LazyListState,
    onSpeakText: (String) -> Unit,
    onDismissAction: () -> Unit,
    onNavigateToSafetyChecker: () -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        state = listState,
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp)
            .verticalScrollbar(listState),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 8.dp, end = 6.dp)
    ) {
        // Greeting bubble
        item {
            AgentBubble(
                text = state.persona.greeting,
                personaEmoji = state.persona.emoji
            )
        }

        items(state.messages, key = { it.id }) { message ->
            if (message.isFromUser) {
                UserMessageBubble(message)
            } else {
                AgentBubble(
                    text = message.content,
                    timestamp = message.timestamp,
                    personaEmoji = state.persona.emoji,
                    onSpeak = { onSpeakText(message.content) }
                )
            }
        }

        if (state.isLoading) {
            item {
                ThinkingBubble(personaEmoji = state.persona.emoji)
            }
        }

        // Smart action card
        state.pendingAction?.let { action ->
            item {
                SmartActionCard(
                    action = action,
                    onDismiss = onDismissAction,
                    onNavigateToSafetyChecker = onNavigateToSafetyChecker
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChatDarkTopBar(
    persona: ChatPersona,
    isSpeaking: Boolean,
    showHistory: Boolean,
    onBack: () -> Unit,
    onStopSpeaking: () -> Unit,
    onOpenSafetyChecker: () -> Unit,
    onToggleHistory: () -> Unit,
    onTogglePersonaPicker: () -> Unit,
    onToggleAutoSpeak: () -> Unit,
    autoSpeak: Boolean,
    onClearChat: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }

    TopAppBar(
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = MaterialTheme.colorScheme.onBackground)
            }
        },
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.clickable(onClick = onTogglePersonaPicker)
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(AccentPurple.copy(alpha = 0.3f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(persona.emoji, fontSize = 20.sp)
                }
                Column {
                    Text(persona.displayName, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onBackground)
                    Text(persona.subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        },
        actions = {
            // Toggle between voice agent and chat history
            IconButton(onClick = onToggleHistory) {
                Icon(
                    if (showHistory) Icons.Default.Mic else Icons.Default.History,
                    contentDescription = if (showHistory) "Voice mode" else "Chat history",
                    tint = MaterialTheme.colorScheme.onBackground
                )
            }
            IconButton(onClick = onOpenSafetyChecker) {
                Icon(Icons.Default.CameraAlt, contentDescription = "Check a photo", tint = MaterialTheme.colorScheme.onBackground)
            }
            if (isSpeaking) {
                IconButton(onClick = onStopSpeaking) {
                    Icon(Icons.Default.Stop, contentDescription = "Stop speaking", tint = AccentPurple)
                }
            }
            IconButton(onClick = { showMenu = true }) {
                Icon(Icons.Default.MoreVert, contentDescription = "More options", tint = MaterialTheme.colorScheme.onBackground)
            }
            DropdownMenu(
                expanded = showMenu,
                onDismissRequest = { showMenu = false },
                modifier = Modifier.background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                DropdownMenuItem(
                    text = { Text(if (autoSpeak) "Auto-speak: ON" else "Auto-speak: OFF", color = MaterialTheme.colorScheme.onBackground) },
                    onClick = { onToggleAutoSpeak(); showMenu = false },
                    leadingIcon = {
                        Icon(
                            if (autoSpeak) Icons.AutoMirrored.Filled.VolumeUp else Icons.AutoMirrored.Filled.VolumeMute,
                            contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                )
                DropdownMenuItem(
                    text = { Text("Change persona", color = MaterialTheme.colorScheme.onBackground) },
                    onClick = { onTogglePersonaPicker(); showMenu = false },
                    leadingIcon = { Icon(Icons.Default.Face, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant) }
                )
                DropdownMenuItem(
                    text = { Text("Clear chat", color = MaterialTheme.colorScheme.onBackground) },
                    onClick = { onClearChat(); showMenu = false },
                    leadingIcon = { Icon(Icons.Default.DeleteSweep, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant) }
                )
            }
        }
    )
}

@Composable
private fun ChatDarkInputBar(
    text: String,
    onTextChange: (String) -> Unit,
    onSend: () -> Unit,
    agentState: AgentState,
    onMicClick: () -> Unit,
    isContinuousMode: Boolean = false,
    onToggleContinuousMode: () -> Unit = {}
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
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
            // Text input
            OutlinedTextField(
                value = text,
                onValueChange = onTextChange,
                modifier = Modifier.weight(1f),
                placeholder = {
                    Text(
                        "Type a message...",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onBackground),
                maxLines = 4,
                shape = RoundedCornerShape(24.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = AccentBlue,
                    unfocusedBorderColor = MaterialTheme.colorScheme.surfaceVariant,
                    cursorColor = AccentBlue,
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            )

            // Continuous conversation mode button
            ContinuousConversationButton(
                isActive = isContinuousMode,
                onClick = onToggleContinuousMode
            )

            // Large mic/action FAB
            val micSize = 56.dp
            val (micColor, micIcon) = when (agentState) {
                AgentState.LISTENING -> Pair(AccentTeal, Icons.Default.Stop)
                AgentState.SPEAKING -> Pair(AccentPurple, Icons.Default.Stop)
                AgentState.PROCESSING -> Pair(AccentBlue, Icons.Default.HourglassTop)
                AgentState.SPEAK_COOLDOWN -> Pair(MicBlue, Icons.Default.Mic)
                AgentState.IDLE -> Pair(MicBlue, Icons.Default.Mic)
            }

            // Glow animation for listening or continuous idle
            val shouldGlow = agentState == AgentState.LISTENING ||
                (isContinuousMode && agentState == AgentState.IDLE)
            val infiniteTransition = rememberInfiniteTransition(label = "mic_glow")
            val glowAlpha by infiniteTransition.animateFloat(
                initialValue = 0f,
                targetValue = if (shouldGlow) 0.4f else 0f,
                animationSpec = infiniteRepeatable(
                    animation = tween(if (isContinuousMode && agentState == AgentState.IDLE) 1200 else 800),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "glow"
            )

            Box(contentAlignment = Alignment.Center) {
                // Glow behind mic button
                if (shouldGlow) {
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .clip(CircleShape)
                            .background(
                                (if (isContinuousMode) AccentTeal else AccentTeal).copy(alpha = glowAlpha)
                            )
                    )
                }

                IconButton(
                    onClick = onMicClick,
                    enabled = agentState != AgentState.PROCESSING,
                    modifier = Modifier
                        .size(micSize)
                        .clip(CircleShape)
                        .background(micColor)
                ) {
                    Icon(
                        micIcon,
                        contentDescription = when (agentState) {
                            AgentState.LISTENING -> "Stop listening"
                            AgentState.SPEAKING -> "Stop speaking"
                            AgentState.PROCESSING -> "Thinking..."
                            AgentState.SPEAK_COOLDOWN -> "Getting ready..."
                            AgentState.IDLE -> "Start voice input"
                        },
                        tint = Color.White,
                        modifier = Modifier.size(26.dp)
                    )
                }
            }

            // Send button
            IconButton(
                onClick = onSend,
                enabled = text.isNotBlank() && agentState != AgentState.PROCESSING,
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(
                        if (text.isNotBlank() && agentState != AgentState.PROCESSING) AccentBlue else MaterialTheme.colorScheme.surfaceVariant
                    )
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.Send,
                    contentDescription = "Send message",
                    tint = if (text.isNotBlank()) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }
}

@Composable
private fun PersonaPickerSheet(
    currentPersona: ChatPersona,
    onSelectPersona: (ChatPersona) -> Unit,
    onPreviewVoice: (ChatPersona) -> Unit,
    onDismiss: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.7f))
            .clickable(onClick = onDismiss),
        contentAlignment = Alignment.BottomCenter
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = false, onClick = {}),
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    "Choose Your Companion",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    "Each companion has a different personality and voice.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(4.dp))

                ChatPersona.entries.forEach { persona ->
                    val isSelected = persona == currentPersona
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelectPersona(persona) },
                        colors = CardDefaults.cardColors(
                            containerColor = if (isSelected) AccentPurple.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surfaceVariant
                        ),
                        border = if (isSelected) BorderStroke(2.dp, AccentPurple) else null,
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (isSelected) AccentPurple.copy(alpha = 0.3f)
                                        else MaterialTheme.colorScheme.surface
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(persona.emoji, fontSize = 24.sp)
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    persona.displayName,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onBackground
                                )
                                Text(
                                    persona.subtitle,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            // Preview voice button
                            IconButton(
                                onClick = { onPreviewVoice(persona) },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    Icons.AutoMirrored.Filled.VolumeUp,
                                    contentDescription = "Preview voice",
                                    tint = if (isSelected) AccentPurple else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            if (isSelected) {
                                Icon(
                                    Icons.Default.CheckCircle,
                                    contentDescription = "Selected",
                                    tint = AccentPurple,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun SmartActionCard(
    action: SmartAction,
    onDismiss: () -> Unit,
    onNavigateToSafetyChecker: () -> Unit
) {
    val (icon, label, description) = when (action) {
        is SmartAction.CheckUrl -> Triple(
            Icons.Default.Link,
            "Check this website",
            "Tap to scan \"${action.url}\" for safety"
        )
        is SmartAction.BlockNumber -> Triple(
            Icons.Default.Block,
            "Block this number",
            "Tap to block ${action.number}"
        )
        SmartAction.Panic -> Triple(
            Icons.Default.Warning,
            "Emergency help",
            "Tap for immediate steps to protect yourself"
        )
        SmartAction.CheckWifi -> Triple(
            Icons.Default.Wifi,
            "Check WiFi security",
            "Tap to scan your current WiFi connection"
        )
        SmartAction.PrivacyScan -> Triple(
            Icons.Default.Security,
            "Privacy scan",
            "Tap to check which apps might be listening"
        )
        SmartAction.OpenSafetyChecker -> Triple(
            Icons.Default.Shield,
            "Safety checker",
            "Tap to open the safety checker"
        )
        SmartAction.OpenPrivacyMonitor -> Triple(
            Icons.Default.PrivacyTip,
            "Privacy monitor",
            "Tap to view your privacy monitor"
        )
        SmartAction.OpenSettings -> Triple(
            Icons.Default.Settings,
            "Settings",
            "Tap to open settings"
        )
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = AccentTeal.copy(alpha = 0.15f)),
        border = BorderStroke(1.dp, AccentTeal),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(icon, contentDescription = null, tint = AccentTeal, modifier = Modifier.size(28.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground)
                Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.Close, contentDescription = "Dismiss", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
private fun AgentBubble(
    text: String,
    timestamp: Long? = null,
    personaEmoji: String = "\uD83D\uDE07",
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
                .background(AccentPurple.copy(alpha = 0.3f)),
            contentAlignment = Alignment.Center
        ) {
            Text(personaEmoji, fontSize = 18.sp)
        }

        Spacer(Modifier.width(8.dp))

        Column {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                shape = RoundedCornerShape(topStart = 4.dp, topEnd = 16.dp, bottomStart = 16.dp, bottomEnd = 16.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = text,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onBackground
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
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            IconButton(onClick = onSpeak, modifier = Modifier.size(32.dp)) {
                                Icon(
                                    Icons.AutoMirrored.Filled.VolumeUp,
                                    contentDescription = "Read aloud",
                                    tint = AccentPurple,
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
private fun UserMessageBubble(message: MessageEntity) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = UserBubbleColor),
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
private fun ThinkingBubble(personaEmoji: String = "\uD83D\uDE07") {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Start,
        verticalAlignment = Alignment.Bottom
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(AccentPurple.copy(alpha = 0.3f)),
            contentAlignment = Alignment.Center
        ) {
            Text(personaEmoji, fontSize = 18.sp)
        }

        Spacer(Modifier.width(8.dp))

        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            shape = RoundedCornerShape(topStart = 4.dp, topEnd = 16.dp, bottomStart = 16.dp, bottomEnd = 16.dp)
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
                            animation = tween(600, delayMillis = i * 200),
                            repeatMode = RepeatMode.Reverse
                        ),
                        label = "dot_alpha$i"
                    )
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(AccentPurple.copy(alpha = alpha))
                    )
                }
            }
        }
    }
}

/**
 * Unified handler for both the mic button (in the input bar) and the
 * persona-avatar tap (in the voice-agent view). Behaviour:
 *
 *   - LISTENING → stop the current voice turn (cancel mic).
 *   - SPEAKING  → stop the agent mid-sentence (interrupt).
 *   - PROCESSING → no-op (don't interrupt a Claude call mid-flight).
 *   - IDLE / SPEAK_COOLDOWN:
 *       * If the textbox has content (e.g., scam text forwarded from a
 *         notification deep-link, or something the user typed), submit
 *         that text as the next user message. The agent responds in voice.
 *         This prevents the previously-reported bug where tapping the
 *         avatar after a "Tell Me More" notification started a fresh voice
 *         turn and lost the scam context the deep link had pre-filled.
 *       * If the textbox is empty, start a normal voice turn.
 *
 * Both buttons calling the same handler also makes them visually
 * equivalent, which is what we tell users in the on-screen hint
 * ("Tap my face or the microphone to talk").
 */
private fun handleTalkAction(
    state: ChatUiState,
    viewModel: SafeHarborChatViewModel
) {
    when (state.agentState) {
        AgentState.LISTENING -> viewModel.stopVoiceTurn()
        AgentState.SPEAKING -> viewModel.stopSpeaking()
        AgentState.PROCESSING -> { /* don't interrupt mid-call */ }
        AgentState.IDLE, AgentState.SPEAK_COOLDOWN -> {
            if (state.inputText.isNotBlank()) {
                viewModel.sendMessage(speakResponse = true)
            } else {
                viewModel.startVoiceTurn()
            }
        }
    }
}

private fun formatChatTime(timestamp: Long): String {
    val sdf = SimpleDateFormat("h:mm a", Locale.getDefault())
    return sdf.format(Date(timestamp))
}

private val EaseInOutSine: Easing = CubicBezierEasing(0.37f, 0f, 0.63f, 1f)

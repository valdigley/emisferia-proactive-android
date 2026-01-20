package com.emisferia.proactive.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.emisferia.proactive.ui.components.*
import com.emisferia.proactive.ui.theme.*
import com.emisferia.proactive.viewmodel.*
import com.emisferia.proactive.service.UpdateChecker
import kotlinx.coroutines.delay

/**
 * Minimal proactive interface
 * Only voice interaction - no buttons, no menus
 * Just the neural wave visualization and conversation
 */
@Composable
fun MainScreen(
    viewModel: MainViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val audioLevel by viewModel.voiceService.audioLevel.collectAsState()
    val messages by viewModel.conversationHistory.collectAsState()

    // Auto-scroll to latest message
    val listState = rememberLazyListState()
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    // Auto-listening enabled - continuous voice interaction
    var autoListenEnabled by remember { mutableStateOf(false) }

    // Continuous listening mode - always listening when not speaking
    LaunchedEffect(uiState.isListening, uiState.isSpeaking, uiState.isProcessing, autoListenEnabled) {
        if (autoListenEnabled && !uiState.isListening && !uiState.isSpeaking && !uiState.isProcessing) {
            delay(2000) // Pause before restarting listening
            try {
                viewModel.startListening()
            } catch (e: Exception) {
                // Ignore errors
            }
        }
    }

    // Start listening after app initializes
    LaunchedEffect(Unit) {
        delay(3000) // Wait for everything to initialize
        autoListenEnabled = true
        try {
            viewModel.startListening()
        } catch (e: Exception) {
            // Ignore errors
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                // Tap anywhere to interrupt/restart
                if (uiState.isSpeaking) {
                    viewModel.stopSpeaking()
                }
            }
    ) {
        // Ambient glow based on state
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            when {
                                uiState.isSpeaking -> NeonPurple.copy(alpha = 0.06f)
                                uiState.isListening -> NeonCyan.copy(alpha = 0.06f)
                                uiState.isProcessing -> NeonPink.copy(alpha = 0.04f)
                                else -> Color.Transparent
                            },
                            Color.Transparent
                        ),
                        radius = 1000f
                    )
                )
        )

        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Minimal connection indicator (top right)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                contentAlignment = Alignment.TopEnd
            ) {
                ConnectionDot(isConnected = uiState.isConnected)
            }

            // Conversation history
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(vertical = 8.dp)
            ) {
                items(messages) { message ->
                    ConversationBubble(
                        text = message.text,
                        isUser = message.isUser
                    )
                }

                // Processing indicator
                if (uiState.isProcessing) {
                    item {
                        ThinkingIndicator()
                    }
                }
            }

            // Central Neural Wave - tap to start listening
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(300.dp)
                    .clickable {
                        if (!uiState.isListening && !uiState.isSpeaking) {
                            try {
                                viewModel.startListening()
                            } catch (e: Exception) {
                                // Ignore
                            }
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                NeuralWaveView(
                    modifier = Modifier.size(220.dp),
                    isListening = uiState.isListening,
                    isSpeaking = uiState.isSpeaking,
                    audioAmplitude = audioLevel
                )

                // Subtle status indicator
                StatusGlow(
                    isListening = uiState.isListening,
                    isSpeaking = uiState.isSpeaking,
                    isProcessing = uiState.isProcessing,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 24.dp)
                )
            }

            // Live transcription (what user is saying)
            AnimatedVisibility(
                visible = uiState.currentText.isNotEmpty() && uiState.isListening,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                LiveTranscription(
                    text = uiState.currentText,
                    modifier = Modifier.padding(horizontal = 32.dp, vertical = 8.dp)
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
        }

        // Update Dialog
        if (uiState.showUpdateDialog && uiState.updateInfo != null) {
            UpdateDialog(
                updateInfo = uiState.updateInfo!!,
                onDismiss = { viewModel.dismissUpdateDialog() },
                onUpdate = { viewModel.downloadUpdate() }
            )
        }
    }
}

/**
 * Minimal connection dot
 */
@Composable
private fun ConnectionDot(isConnected: Boolean) {
    val color = if (isConnected) NeonGreen else StatusError

    val infiniteTransition = rememberInfiniteTransition(label = "conn")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2500),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    Box(
        modifier = Modifier
            .size(6.dp)
            .clip(RoundedCornerShape(3.dp))
            .background(color.copy(alpha = alpha))
    )
}

/**
 * Conversation message bubble
 */
@Composable
private fun ConversationBubble(
    text: String,
    isUser: Boolean
) {
    val accentColor = if (isUser) NeonGreen else NeonCyan

    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = if (isUser) Alignment.CenterEnd else Alignment.CenterStart
    ) {
        Box(
            modifier = Modifier
                .widthIn(max = 320.dp)
                .clip(
                    RoundedCornerShape(
                        topStart = 20.dp,
                        topEnd = 20.dp,
                        bottomStart = if (isUser) 20.dp else 4.dp,
                        bottomEnd = if (isUser) 4.dp else 20.dp
                    )
                )
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            accentColor.copy(alpha = 0.12f),
                            accentColor.copy(alpha = 0.04f)
                        )
                    )
                )
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.bodyLarge,
                color = TextPrimary,
                lineHeight = 24.sp
            )
        }
    }
}

/**
 * Thinking/processing indicator
 */
@Composable
private fun ThinkingIndicator() {
    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.CenterStart
    ) {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(16.dp))
                .background(NeonCyan.copy(alpha = 0.08f))
                .padding(horizontal = 20.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val infiniteTransition = rememberInfiniteTransition(label = "dots")

            repeat(3) { index ->
                val alpha by infiniteTransition.animateFloat(
                    initialValue = 0.3f,
                    targetValue = 1f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(500, delayMillis = index * 150),
                        repeatMode = RepeatMode.Reverse
                    ),
                    label = "dot_$index"
                )

                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(NeonCyan.copy(alpha = alpha))
                )
            }
        }
    }
}

/**
 * Subtle status glow under the neural wave
 */
@Composable
private fun StatusGlow(
    isListening: Boolean,
    isSpeaking: Boolean,
    isProcessing: Boolean,
    modifier: Modifier = Modifier
) {
    val color = when {
        isSpeaking -> NeonPurple
        isProcessing -> NeonPink
        isListening -> NeonGreen
        else -> Color.Transparent
    }

    val infiniteTransition = rememberInfiniteTransition(label = "glow")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.8f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow_alpha"
    )

    AnimatedVisibility(
        visible = isListening || isSpeaking || isProcessing,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = modifier
    ) {
        Box(
            modifier = Modifier
                .width(60.dp)
                .height(3.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(color.copy(alpha = alpha))
        )
    }
}

/**
 * Live transcription of what user is saying
 */
@Composable
private fun LiveTranscription(
    text: String,
    modifier: Modifier = Modifier
) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = NeonGreen.copy(alpha = 0.5f),
        textAlign = TextAlign.Center,
        modifier = modifier
    )
}

/**
 * Update available dialog
 */
@Composable
private fun UpdateDialog(
    updateInfo: UpdateChecker.UpdateInfo,
    onDismiss: () -> Unit,
    onUpdate: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DarkSurface,
        titleContentColor = TextPrimary,
        textContentColor = TextSecondary,
        title = {
            Text(
                text = "Atualização Disponível",
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Nova versão ${updateInfo.latestVersion} disponível!",
                    style = MaterialTheme.typography.bodyLarge
                )
                Text(
                    text = "Versão atual: ${updateInfo.currentVersion}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )
                if (updateInfo.releaseNotes.isNotEmpty()) {
                    Text(
                        text = updateInfo.releaseNotes.take(200) +
                            if (updateInfo.releaseNotes.length > 200) "..." else "",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary.copy(alpha = 0.7f)
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onUpdate,
                colors = ButtonDefaults.textButtonColors(
                    contentColor = NeonCyan
                )
            ) {
                Text("Atualizar")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                colors = ButtonDefaults.textButtonColors(
                    contentColor = TextSecondary
                )
            ) {
                Text("Depois")
            }
        }
    )
}

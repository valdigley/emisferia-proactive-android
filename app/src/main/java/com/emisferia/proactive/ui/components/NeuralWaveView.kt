package com.emisferia.proactive.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.emisferia.proactive.ui.theme.*
import kotlin.math.*

/**
 * NeuralWaveView - Central animated neural wave visualization
 * Symbolizes EmisferIA with flowing neon wave patterns
 */
@Composable
fun NeuralWaveView(
    modifier: Modifier = Modifier,
    isListening: Boolean = false,
    isSpeaking: Boolean = false,
    audioAmplitude: Float = 0f, // 0-1 amplitude from voice input
    waveCount: Int = 5
) {
    // Animation states
    val infiniteTransition = rememberInfiniteTransition(label = "neural_wave")

    // Base wave animation - continuous flow
    val wavePhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 2f * PI.toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(4000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "wave_phase"
    )

    // Glow pulse animation
    val glowPulse by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow_pulse"
    )

    // Rotation for orbital particles
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(10000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )

    // Animated amplitude based on activity
    val targetAmplitude = when {
        isSpeaking -> 0.8f + (audioAmplitude * 0.2f)
        isListening -> 0.4f + (audioAmplitude * 0.6f)
        else -> 0.2f
    }

    val animatedAmplitude by animateFloatAsState(
        targetValue = targetAmplitude,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "amplitude"
    )

    // Color shift based on state
    val primaryColor = when {
        isSpeaking -> NeonPurple
        isListening -> NeonCyan
        else -> NeonCyan.copy(alpha = 0.7f)
    }

    val secondaryColor = when {
        isSpeaking -> NeonPink
        isListening -> NeonPurple
        else -> NeonPurple.copy(alpha = 0.5f)
    }

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Canvas(
            modifier = Modifier.fillMaxSize()
        ) {
            val centerX = size.width / 2
            val centerY = size.height / 2
            val maxRadius = minOf(size.width, size.height) / 2 * 0.85f

            // Draw outer glow
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        primaryColor.copy(alpha = 0.15f * glowPulse),
                        primaryColor.copy(alpha = 0.05f * glowPulse),
                        Color.Transparent
                    ),
                    center = Offset(centerX, centerY),
                    radius = maxRadius * 1.5f
                ),
                radius = maxRadius * 1.5f,
                center = Offset(centerX, centerY)
            )

            // Draw multiple wave layers
            for (i in 0 until waveCount) {
                val progress = i.toFloat() / waveCount
                val radius = maxRadius * (0.3f + progress * 0.7f)
                val alpha = 1f - (progress * 0.6f)
                val phaseOffset = i * (PI.toFloat() / waveCount)

                drawNeuralWave(
                    center = Offset(centerX, centerY),
                    radius = radius,
                    phase = wavePhase + phaseOffset,
                    amplitude = animatedAmplitude * (1f - progress * 0.5f),
                    color = if (i % 2 == 0) primaryColor else secondaryColor,
                    alpha = alpha,
                    strokeWidth = 3f - (progress * 1.5f)
                )
            }

            // Draw center core
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        primaryColor.copy(alpha = 0.8f),
                        primaryColor.copy(alpha = 0.4f),
                        Color.Transparent
                    ),
                    center = Offset(centerX, centerY),
                    radius = maxRadius * 0.15f
                ),
                radius = maxRadius * 0.15f,
                center = Offset(centerX, centerY)
            )

            // Draw orbital particles
            val particleCount = 8
            for (i in 0 until particleCount) {
                val angle = (rotation + (i * 360f / particleCount)) * (PI.toFloat() / 180f)
                val orbitRadius = maxRadius * 0.7f
                val particleX = centerX + cos(angle) * orbitRadius
                val particleY = centerY + sin(angle) * orbitRadius

                drawCircle(
                    color = if (i % 2 == 0) primaryColor else secondaryColor,
                    radius = 4.dp.toPx() * (0.5f + glowPulse * 0.5f),
                    center = Offset(particleX, particleY)
                )
            }

            // Inner pulsing ring
            drawCircle(
                color = primaryColor.copy(alpha = 0.5f * glowPulse),
                radius = maxRadius * 0.25f,
                center = Offset(centerX, centerY),
                style = Stroke(width = 2.dp.toPx())
            )
        }
    }
}

/**
 * Draw a single neural wave as a distorted circle
 */
private fun DrawScope.drawNeuralWave(
    center: Offset,
    radius: Float,
    phase: Float,
    amplitude: Float,
    color: Color,
    alpha: Float,
    strokeWidth: Float
) {
    val path = Path()
    val points = 360
    val waveFrequency = 6 // Number of wave peaks

    for (i in 0..points) {
        val angle = (i.toFloat() / points) * 2 * PI.toFloat()

        // Multiple wave components for organic feel
        val wave1 = sin(angle * waveFrequency + phase) * amplitude * radius * 0.15f
        val wave2 = sin(angle * (waveFrequency + 2) - phase * 0.5f) * amplitude * radius * 0.08f
        val wave3 = sin(angle * (waveFrequency - 1) + phase * 1.5f) * amplitude * radius * 0.05f

        val waveOffset = wave1 + wave2 + wave3
        val currentRadius = radius + waveOffset

        val x = center.x + cos(angle) * currentRadius
        val y = center.y + sin(angle) * currentRadius

        if (i == 0) {
            path.moveTo(x, y)
        } else {
            path.lineTo(x, y)
        }
    }
    path.close()

    // Draw glow layer
    drawPath(
        path = path,
        color = color.copy(alpha = alpha * 0.3f),
        style = Stroke(
            width = strokeWidth * 4,
            cap = StrokeCap.Round,
            join = StrokeJoin.Round
        )
    )

    // Draw main wave
    drawPath(
        path = path,
        color = color.copy(alpha = alpha),
        style = Stroke(
            width = strokeWidth,
            cap = StrokeCap.Round,
            join = StrokeJoin.Round
        )
    )
}

/**
 * Status indicator showing current state
 */
@Composable
fun StatusIndicator(
    modifier: Modifier = Modifier,
    isListening: Boolean = false,
    isSpeaking: Boolean = false,
    isConnected: Boolean = true
) {
    val color = when {
        !isConnected -> StatusError
        isSpeaking -> NeonPurple
        isListening -> NeonGreen
        else -> NeonCyan
    }

    val infiniteTransition = rememberInfiniteTransition(label = "status")

    val pulse by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = if (isListening || isSpeaking) 500 else 2000,
                easing = FastOutSlowInEasing
            ),
            repeatMode = RepeatMode.Reverse
        ),
        label = "status_pulse"
    )

    Canvas(modifier = modifier.size(12.dp)) {
        // Outer glow
        drawCircle(
            color = color.copy(alpha = 0.3f * pulse),
            radius = size.minDimension / 2 * 1.5f
        )
        // Main dot
        drawCircle(
            color = color.copy(alpha = pulse),
            radius = size.minDimension / 2
        )
    }
}

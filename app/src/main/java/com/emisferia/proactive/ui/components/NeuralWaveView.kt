package com.emisferia.proactive.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.*
import androidx.compose.ui.unit.dp
import com.emisferia.proactive.ui.theme.*
import kotlin.math.*
import kotlin.random.Random

/**
 * Neural Brain Visualization
 * Central brain with radiating neural connections
 * Inspired by futuristic AI visualization
 */
@Composable
fun NeuralWaveView(
    modifier: Modifier = Modifier,
    isListening: Boolean = false,
    isSpeaking: Boolean = false,
    audioAmplitude: Float = 0f
) {
    // Animation for pulsing effect
    val infiniteTransition = rememberInfiniteTransition(label = "neural")

    val pulsePhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 2f * PI.toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(3000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "pulse"
    )

    val glowIntensity by infiniteTransition.animateFloat(
        initialValue = 0.6f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow"
    )

    val neuronPhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "neuron"
    )

    // Color based on state
    val primaryColor = when {
        isSpeaking -> NeonPurple
        isListening -> NeonCyan
        else -> NeonCyan.copy(alpha = 0.7f)
    }

    val secondaryColor = when {
        isSpeaking -> NeonPink
        isListening -> NeonGreen
        else -> NeonPurple.copy(alpha = 0.5f)
    }

    val accentColor = Color(0xFFFFAA00) // Gold/orange accent

    // Generate neural nodes (stable across recompositions)
    val neuralNodes = remember {
        generateNeuralNodes(24)
    }

    val innerNodes = remember {
        generateNeuralNodes(12, radiusRange = 0.2f..0.4f)
    }

    Canvas(modifier = modifier.fillMaxSize()) {
        val centerX = size.width / 2
        val centerY = size.height / 2
        val maxRadius = minOf(size.width, size.height) / 2

        // Amplitude factor
        val ampFactor = 1f + (audioAmplitude * 0.5f)

        // Draw outer glow
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    primaryColor.copy(alpha = 0.15f * glowIntensity),
                    primaryColor.copy(alpha = 0.05f * glowIntensity),
                    Color.Transparent
                ),
                center = Offset(centerX, centerY),
                radius = maxRadius * 1.2f * ampFactor
            ),
            radius = maxRadius * 1.2f * ampFactor,
            center = Offset(centerX, centerY)
        )

        // Draw neural connections (outer)
        neuralNodes.forEachIndexed { index, node ->
            val nodeRadius = maxRadius * node.radius * ampFactor
            val angle = node.angle + pulsePhase * 0.1f
            val nodeX = centerX + cos(angle) * nodeRadius
            val nodeY = centerY + sin(angle) * nodeRadius

            // Connection line with gradient
            val connectionAlpha = (0.3f + 0.4f * sin(pulsePhase + index * 0.5f)) * glowIntensity
            drawLine(
                brush = Brush.linearGradient(
                    colors = listOf(
                        primaryColor.copy(alpha = connectionAlpha * 0.8f),
                        secondaryColor.copy(alpha = connectionAlpha * 0.3f)
                    ),
                    start = Offset(centerX, centerY),
                    end = Offset(nodeX, nodeY)
                ),
                start = Offset(centerX, centerY),
                end = Offset(nodeX, nodeY),
                strokeWidth = 1.5f + audioAmplitude * 2f,
                cap = StrokeCap.Round
            )

            // Neural node (glowing dot)
            val nodeGlow = (0.5f + 0.5f * sin(pulsePhase * 2 + index * 0.8f))
            val nodeColor = if (index % 3 == 0) accentColor else primaryColor

            // Outer glow
            drawCircle(
                color = nodeColor.copy(alpha = 0.3f * nodeGlow),
                radius = node.size * maxRadius * 0.04f * ampFactor,
                center = Offset(nodeX, nodeY)
            )

            // Inner bright dot
            drawCircle(
                color = nodeColor.copy(alpha = 0.8f * nodeGlow + 0.2f),
                radius = node.size * maxRadius * 0.015f * ampFactor,
                center = Offset(nodeX, nodeY)
            )
        }

        // Draw inner connections
        innerNodes.forEachIndexed { index, node ->
            val nodeRadius = maxRadius * node.radius * ampFactor
            val angle = node.angle - pulsePhase * 0.15f
            val nodeX = centerX + cos(angle) * nodeRadius
            val nodeY = centerY + sin(angle) * nodeRadius

            val connectionAlpha = (0.4f + 0.3f * sin(pulsePhase + index)) * glowIntensity
            drawLine(
                color = secondaryColor.copy(alpha = connectionAlpha),
                start = Offset(centerX, centerY),
                end = Offset(nodeX, nodeY),
                strokeWidth = 1f + audioAmplitude,
                cap = StrokeCap.Round
            )

            drawCircle(
                color = secondaryColor.copy(alpha = 0.6f * glowIntensity),
                radius = node.size * maxRadius * 0.012f * ampFactor,
                center = Offset(nodeX, nodeY)
            )
        }

        // Draw traveling pulses along connections
        if (isListening || isSpeaking) {
            neuralNodes.take(8).forEachIndexed { index, node ->
                val nodeRadius = maxRadius * node.radius
                val angle = node.angle + pulsePhase * 0.1f
                val nodeX = centerX + cos(angle) * nodeRadius
                val nodeY = centerY + sin(angle) * nodeRadius

                val pulsePos = (neuronPhase + index * 0.125f) % 1f
                val pulseX = centerX + (nodeX - centerX) * pulsePos
                val pulseY = centerY + (nodeY - centerY) * pulsePos

                drawCircle(
                    color = accentColor.copy(alpha = 0.8f * (1f - pulsePos)),
                    radius = 4f + audioAmplitude * 3f,
                    center = Offset(pulseX, pulseY)
                )
            }
        }

        // Draw brain core rings
        val coreRadius = maxRadius * 0.35f * ampFactor

        // Outer ring glow
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    primaryColor.copy(alpha = 0.4f * glowIntensity),
                    primaryColor.copy(alpha = 0.1f),
                    Color.Transparent
                ),
                center = Offset(centerX, centerY),
                radius = coreRadius * 1.3f
            ),
            radius = coreRadius * 1.3f,
            center = Offset(centerX, centerY)
        )

        // Main brain circle
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    primaryColor.copy(alpha = 0.2f),
                    Color(0xFF0A1628).copy(alpha = 0.9f),
                    Color(0xFF050A14)
                ),
                center = Offset(centerX, centerY),
                radius = coreRadius
            ),
            radius = coreRadius,
            center = Offset(centerX, centerY)
        )

        // Brain outline ring
        drawCircle(
            color = primaryColor.copy(alpha = 0.6f * glowIntensity),
            radius = coreRadius,
            center = Offset(centerX, centerY),
            style = Stroke(width = 2f + audioAmplitude * 3f)
        )

        // Inner ring
        drawCircle(
            color = secondaryColor.copy(alpha = 0.4f * glowIntensity),
            radius = coreRadius * 0.7f,
            center = Offset(centerX, centerY),
            style = Stroke(width = 1.5f)
        )

        // Draw brain waves inside core
        val waveCount = 5
        for (i in 0 until waveCount) {
            val waveOffset = (pulsePhase + i * 0.5f) % (2f * PI.toFloat())
            val waveY = centerY + sin(waveOffset) * coreRadius * 0.3f * (1f - i * 0.15f)

            val path = Path().apply {
                moveTo(centerX - coreRadius * 0.5f, waveY)
                for (x in 0..20) {
                    val px = centerX - coreRadius * 0.5f + (coreRadius * x / 20f)
                    val py = waveY + sin(x * 0.5f + pulsePhase * 2 + i) * coreRadius * 0.08f * ampFactor
                    lineTo(px, py)
                }
            }

            drawPath(
                path = path,
                color = primaryColor.copy(alpha = 0.3f - i * 0.05f),
                style = Stroke(width = 1.5f, cap = StrokeCap.Round)
            )
        }

        // Central "AI" glow point
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color.White.copy(alpha = 0.9f * glowIntensity),
                    primaryColor.copy(alpha = 0.5f),
                    Color.Transparent
                ),
                center = Offset(centerX, centerY),
                radius = coreRadius * 0.25f * ampFactor
            ),
            radius = coreRadius * 0.25f * ampFactor,
            center = Offset(centerX, centerY)
        )

        // Circuit patterns at corners
        drawCircuitPattern(
            center = Offset(centerX - maxRadius * 0.6f, centerY - maxRadius * 0.6f),
            color = primaryColor.copy(alpha = 0.15f * glowIntensity),
            size = maxRadius * 0.3f
        )

        drawCircuitPattern(
            center = Offset(centerX + maxRadius * 0.6f, centerY + maxRadius * 0.6f),
            color = secondaryColor.copy(alpha = 0.15f * glowIntensity),
            size = maxRadius * 0.3f
        )
    }
}

private fun DrawScope.drawCircuitPattern(
    center: Offset,
    color: Color,
    size: Float
) {
    val step = size / 4

    // Horizontal lines
    for (i in 0..4) {
        drawLine(
            color = color,
            start = Offset(center.x - size / 2, center.y - size / 2 + i * step),
            end = Offset(center.x + size / 2, center.y - size / 2 + i * step),
            strokeWidth = 0.5f
        )
    }

    // Vertical lines
    for (i in 0..4) {
        drawLine(
            color = color,
            start = Offset(center.x - size / 2 + i * step, center.y - size / 2),
            end = Offset(center.x - size / 2 + i * step, center.y + size / 2),
            strokeWidth = 0.5f
        )
    }

    // Circuit nodes
    for (i in 0..4 step 2) {
        for (j in 0..4 step 2) {
            drawCircle(
                color = color,
                radius = 2f,
                center = Offset(center.x - size / 2 + i * step, center.y - size / 2 + j * step)
            )
        }
    }
}

/**
 * Neural node data class
 */
private data class NeuralNode(
    val angle: Float,
    val radius: Float,
    val size: Float
)

/**
 * Generate neural nodes with random positions
 */
private fun generateNeuralNodes(
    count: Int,
    radiusRange: ClosedFloatingPointRange<Float> = 0.5f..0.9f
): List<NeuralNode> {
    val random = Random(42) // Fixed seed for consistency
    return (0 until count).map { i ->
        NeuralNode(
            angle = (2f * PI.toFloat() * i / count) + random.nextFloat() * 0.3f,
            radius = radiusRange.start + random.nextFloat() * (radiusRange.endInclusive - radiusRange.start),
            size = 0.8f + random.nextFloat() * 0.4f
        )
    }
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

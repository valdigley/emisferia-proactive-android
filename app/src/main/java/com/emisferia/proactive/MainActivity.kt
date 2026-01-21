package com.emisferia.proactive

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.emisferia.proactive.ui.components.NeuralWaveView
import com.emisferia.proactive.ui.theme.DarkBackground
import com.emisferia.proactive.ui.theme.NeonCyan
import com.emisferia.proactive.ui.theme.TextPrimary
import com.emisferia.proactive.ui.theme.TextSecondary
import com.emisferia.proactive.viewmodel.MainViewModel

class MainActivity : ComponentActivity() {

    companion object {
        private const val TAG = "MainActivity"
    }

    private val requiredPermissions = arrayOf(
        Manifest.permission.RECORD_AUDIO
    )

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.values.all { it }
        Log.d(TAG, "Permissions result: $allGranted")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "onCreate started")

        // Request permissions first
        checkAndRequestPermissions()

        setContent {
            // Simple dark background without theme to isolate issues
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF0A0A0F))
            ) {
                SimpleProactiveScreen()
            }
        }
        Log.d(TAG, "setContent completed")
    }

    private fun checkAndRequestPermissions() {
        val permissionsToRequest = requiredPermissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (permissionsToRequest.isNotEmpty()) {
            permissionLauncher.launch(permissionsToRequest.toTypedArray())
        }
    }
}

/**
 * Simple proactive screen - minimal version to test stability
 */
@Composable
fun SimpleProactiveScreen() {
    val viewModel: MainViewModel = viewModel()
    val uiState by viewModel.uiState.collectAsState()
    val audioLevel by viewModel.audioLevel.collectAsState()

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxSize()
        ) {
            Spacer(modifier = Modifier.height(60.dp))

            // Title
            Text(
                text = "EmisferIA",
                color = Color(0xFF00FFFF),
                fontSize = 32.sp
            )

            Text(
                text = "Proactive",
                color = Color(0xFF9D00FF),
                fontSize = 18.sp
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Neural wave visualization
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .clickable {
                        if (!uiState.isListening && !uiState.isSpeaking) {
                            viewModel.startListening()
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                NeuralWaveView(
                    modifier = Modifier.size(300.dp),
                    isListening = uiState.isListening,
                    isSpeaking = uiState.isSpeaking,
                    audioAmplitude = audioLevel
                )
            }

            // Status text
            Text(
                text = when {
                    uiState.isSpeaking -> "Falando..."
                    uiState.isListening -> "Ouvindo..."
                    uiState.isProcessing -> "Processando..."
                    else -> "Toque para falar"
                },
                color = Color(0xFF00FFFF),
                fontSize = 16.sp
            )

            Spacer(modifier = Modifier.height(40.dp))
        }
    }
}

@Composable
fun ErrorScreen(message: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0A0A0F)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Erro ao iniciar",
                color = Color.Red,
                fontSize = 24.sp
            )
            Text(
                text = message,
                color = Color.White,
                fontSize = 14.sp
            )
        }
    }
}

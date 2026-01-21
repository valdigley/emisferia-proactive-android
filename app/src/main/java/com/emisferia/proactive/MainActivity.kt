package com.emisferia.proactive

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.emisferia.proactive.viewmodel.MainViewModel
import com.emisferia.proactive.ui.components.NeuralWaveView

/**
 * VERSION 1.3.4 - Safe ViewModel with error display
 */
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
        Log.d(TAG, "onCreate started - VERSION 1.3.4")

        checkAndRequestPermissions()

        setContent {
            SafeProactiveScreen()
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
 * Safe screen - ViewModel handles its own errors internally
 */
@Composable
fun SafeProactiveScreen() {
    val viewModel: MainViewModel = viewModel()
    val uiState by viewModel.uiState.collectAsState()

    // ViewModel error is shown on screen instead of crashing
    if (uiState.errorMessage != null) {
        ErrorDisplayScreen(error = uiState.errorMessage!!)
    } else {
        FunctionalProactiveScreen(viewModel = viewModel)
    }
}

@Composable
fun FunctionalProactiveScreen(viewModel: MainViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    val audioLevel by viewModel.audioLevel.collectAsState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0A0A0F)),
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

            Text(
                text = "v1.3.4",
                color = Color(0xFF444444),
                fontSize = 10.sp
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Neural wave visualization - tap to speak
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
                    uiState.errorMessage != null -> "Erro: ${uiState.errorMessage}"
                    uiState.isSpeaking -> "Falando..."
                    uiState.isListening -> "Ouvindo..."
                    uiState.isProcessing -> "Processando..."
                    !uiState.isConnected -> "Sem conexão"
                    else -> "Toque para falar"
                },
                color = if (uiState.errorMessage != null || !uiState.isConnected)
                    Color(0xFFFF4444) else Color(0xFF00FFFF),
                fontSize = 16.sp
            )

            // Connection status
            if (!uiState.isConnected) {
                TextButton(onClick = { viewModel.refreshConnection() }) {
                    Text("Reconectar", color = Color(0xFF00FFFF))
                }
            }

            Spacer(modifier = Modifier.height(40.dp))
        }
    }
}

@Composable
fun LoadingScreen() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0A0A0F)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            CircularProgressIndicator(color = Color(0xFF00FFFF))
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Iniciando...",
                color = Color(0xFF00FFFF),
                fontSize = 16.sp
            )
        }
    }
}

@Composable
fun ErrorDisplayScreen(error: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0A0A0F)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp)
        ) {
            Text(
                text = "EmisferIA",
                color = Color(0xFF00FFFF),
                fontSize = 32.sp
            )

            Spacer(modifier = Modifier.height(40.dp))

            Text(
                text = "Erro ao iniciar",
                color = Color(0xFFFF4444),
                fontSize = 20.sp
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = error,
                color = Color(0xFFAAAAAA),
                fontSize = 14.sp
            )

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "v1.3.4 - Por favor reporte este erro",
                color = Color(0xFF666666),
                fontSize = 12.sp
            )
        }
    }
}

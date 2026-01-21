package com.emisferia.proactive

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import com.emisferia.proactive.service.PorcupineWakeWordService
import com.emisferia.proactive.ui.screens.MainScreen
import com.emisferia.proactive.ui.theme.DarkBackground
import com.emisferia.proactive.ui.theme.EmisferiaProactiveTheme

class MainActivity : ComponentActivity() {

    companion object {
        var startListeningOnResume = false
    }

    private val requiredPermissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        arrayOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.INTERNET,
            Manifest.permission.POST_NOTIFICATIONS
        )
    } else {
        arrayOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.INTERNET
        )
    }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.values.all { it }
        if (allGranted) {
            // Start Porcupine wake word service if configured
            startPorcupineWakeWordService()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Check and request permissions
        checkAndRequestPermissions()

        // Check if activated by wake word
        handleWakeWordIntent(intent)

        setContent {
            EmisferiaProactiveTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = DarkBackground
                ) {
                    MainScreen()
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleWakeWordIntent(intent)
    }

    private fun handleWakeWordIntent(intent: Intent?) {
        if (intent?.getBooleanExtra("WAKE_WORD_ACTIVATED", false) == true) {
            // User said "Assistente" - start listening immediately
            startListeningOnResume = true
        }
    }

    private fun checkAndRequestPermissions() {
        val permissionsToRequest = requiredPermissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (permissionsToRequest.isNotEmpty()) {
            permissionLauncher.launch(permissionsToRequest.toTypedArray())
        } else {
            // All permissions already granted, start wake word service
            startPorcupineWakeWordService()
        }
    }

    /**
     * Start Porcupine wake word service (offline, no beeps)
     * Requires PORCUPINE_ACCESS_KEY to be configured
     */
    private fun startPorcupineWakeWordService() {
        // Only start if access key is configured
        if (BuildConfig.PORCUPINE_ACCESS_KEY.isBlank()) {
            android.util.Log.w("MainActivity", "Porcupine access key not configured - wake word disabled")
            android.util.Log.w("MainActivity", "Get your free key at https://console.picovoice.ai/")
            return
        }

        if (!PorcupineWakeWordService.isRunning) {
            val serviceIntent = Intent(this, PorcupineWakeWordService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent)
            } else {
                startService(serviceIntent)
            }
        }
    }

    fun stopWakeWordService() {
        val serviceIntent = Intent(this, PorcupineWakeWordService::class.java)
        stopService(serviceIntent)
    }
}

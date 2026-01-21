package com.emisferia.proactive.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import androidx.core.app.NotificationCompat
import com.emisferia.proactive.MainActivity
import com.emisferia.proactive.R
import java.util.*

/**
 * Background service that listens for the wake word "assistente"
 * When detected, opens the app and activates voice input
 */
class WakeWordService : Service() {

    companion object {
        private const val TAG = "WakeWordService"
        private const val CHANNEL_ID = "wake_word_channel"
        private const val NOTIFICATION_ID = 1001
        private const val WAKE_WORD = "assistente"

        var isRunning = false
            private set
    }

    private var speechRecognizer: SpeechRecognizer? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private var isListening = false
    private var restartDelay = 500L

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "WakeWordService created")
        createNotificationChannel()
        isRunning = true
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "WakeWordService started")

        // Start as foreground service with notification
        startForeground(NOTIFICATION_ID, createNotification())

        // Initialize speech recognition
        initializeSpeechRecognizer()

        // Start listening for wake word
        startListeningForWakeWord()

        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "WakeWordService destroyed")
        isRunning = false
        stopListening()
        speechRecognizer?.destroy()
        speechRecognizer = null
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Assistente de Voz",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Escutando pelo comando 'Assistente'"
                setShowBadge(false)
            }

            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, WakeWordService::class.java).apply {
            action = "STOP"
        }
        val stopPendingIntent = PendingIntent.getService(
            this, 1, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("EmisferIA Assistente")
            .setContentText("Diga \"Assistente\" para ativar")
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(pendingIntent)
            .addAction(R.drawable.ic_notification, "Parar", stopPendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun initializeSpeechRecognizer() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            Log.e(TAG, "Speech recognition not available")
            stopSelf()
            return
        }

        mainHandler.post {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
            speechRecognizer?.setRecognitionListener(wakeWordListener)
        }
    }

    private fun startListeningForWakeWord() {
        if (isListening) return

        mainHandler.post {
            try {
                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, "pt-BR")
                    putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                    putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
                    // Short timeout for wake word detection
                    putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1500)
                    putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 1500)
                }

                speechRecognizer?.startListening(intent)
                isListening = true
                Log.d(TAG, "Started listening for wake word")
            } catch (e: Exception) {
                Log.e(TAG, "Error starting speech recognition: ${e.message}")
                scheduleRestart()
            }
        }
    }

    private fun stopListening() {
        isListening = false
        mainHandler.post {
            try {
                speechRecognizer?.stopListening()
                speechRecognizer?.cancel()
            } catch (e: Exception) {
                Log.e(TAG, "Error stopping speech recognition: ${e.message}")
            }
        }
    }

    private fun scheduleRestart() {
        mainHandler.postDelayed({
            if (isRunning) {
                startListeningForWakeWord()
            }
        }, restartDelay)
    }

    private val wakeWordListener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {
            Log.d(TAG, "Ready for speech")
        }

        override fun onBeginningOfSpeech() {
            Log.d(TAG, "Beginning of speech")
        }

        override fun onRmsChanged(rmsdB: Float) {}

        override fun onBufferReceived(buffer: ByteArray?) {}

        override fun onEndOfSpeech() {
            Log.d(TAG, "End of speech")
            isListening = false
        }

        override fun onError(error: Int) {
            isListening = false
            val errorMsg = when (error) {
                SpeechRecognizer.ERROR_AUDIO -> "Audio error"
                SpeechRecognizer.ERROR_CLIENT -> "Client error"
                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Insufficient permissions"
                SpeechRecognizer.ERROR_NETWORK -> "Network error"
                SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network timeout"
                SpeechRecognizer.ERROR_NO_MATCH -> "No match"
                SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Recognizer busy"
                SpeechRecognizer.ERROR_SERVER -> "Server error"
                SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Speech timeout"
                else -> "Unknown error: $error"
            }
            Log.d(TAG, "Recognition error: $errorMsg")

            // Restart listening after a delay
            scheduleRestart()
        }

        override fun onResults(results: Bundle?) {
            isListening = false
            val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            Log.d(TAG, "Results: $matches")

            processResults(matches)
            scheduleRestart()
        }

        override fun onPartialResults(partialResults: Bundle?) {
            val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            Log.d(TAG, "Partial results: $matches")

            // Check partial results for wake word (faster response)
            if (checkForWakeWord(matches)) {
                stopListening()
                activateAssistant()
            }
        }

        override fun onEvent(eventType: Int, params: Bundle?) {}
    }

    private fun processResults(matches: List<String>?) {
        if (checkForWakeWord(matches)) {
            activateAssistant()
        }
    }

    private fun checkForWakeWord(matches: List<String>?): Boolean {
        matches?.forEach { result ->
            val normalized = result.lowercase(Locale.getDefault()).trim()
            if (normalized.contains(WAKE_WORD) ||
                normalized.contains("assistente") ||
                normalized.contains("a]sistente") ||
                normalized.contains("asistente")) {
                Log.d(TAG, "Wake word detected: $result")
                return true
            }
        }
        return false
    }

    private fun activateAssistant() {
        Log.d(TAG, "Activating assistant!")

        // Launch MainActivity with flag to start listening immediately
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("WAKE_WORD_ACTIVATED", true)
            putExtra("START_LISTENING", true)
        }
        startActivity(intent)
    }
}

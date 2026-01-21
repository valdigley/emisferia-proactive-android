package com.emisferia.proactive.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.emisferia.proactive.BuildConfig
import com.emisferia.proactive.MainActivity
import com.emisferia.proactive.R
import ai.picovoice.porcupine.Porcupine
import ai.picovoice.porcupine.PorcupineException
import ai.picovoice.porcupine.PorcupineManager
import ai.picovoice.porcupine.PorcupineManagerCallback

/**
 * Wake Word Service using Porcupine (Picovoice)
 *
 * Advantages over Android SpeechRecognizer:
 * - Works completely offline
 * - No beeps or sounds
 * - Very low power consumption
 * - Always-on listening without draining battery
 *
 * To use custom wake word "Assistente":
 * 1. Sign up at https://console.picovoice.ai/
 * 2. Get your Access Key
 * 3. Train custom wake word "Assistente" for Portuguese
 * 4. Download the .ppn file
 * 5. Add to assets folder and update this service
 */
class PorcupineWakeWordService : Service() {

    companion object {
        private const val TAG = "PorcupineWakeWord"
        private const val CHANNEL_ID = "wake_word_channel"
        private const val NOTIFICATION_ID = 1001

        var isRunning = false
            private set
    }

    private var porcupineManager: PorcupineManager? = null

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "PorcupineWakeWordService created")
        createNotificationChannel()
        isRunning = true
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "PorcupineWakeWordService started")

        // Handle stop action
        if (intent?.action == "STOP") {
            stopSelf()
            return START_NOT_STICKY
        }

        // Start as foreground service
        startForeground(NOTIFICATION_ID, createNotification())

        // Initialize Porcupine
        initializePorcupine()

        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "PorcupineWakeWordService destroyed")
        isRunning = false
        stopPorcupine()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Assistente de Voz",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Escutando pelo comando de voz"
                setShowBadge(false)
                setSound(null, null)
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

        val stopIntent = Intent(this, PorcupineWakeWordService::class.java).apply {
            action = "STOP"
        }
        val stopPendingIntent = PendingIntent.getService(
            this, 1, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("EmisferIA Assistente")
            .setContentText("Diga \"Computer\" para ativar")
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(pendingIntent)
            .addAction(R.drawable.ic_notification, "Parar", stopPendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setSilent(true)
            .build()
    }

    private fun initializePorcupine() {
        val accessKey = BuildConfig.PORCUPINE_ACCESS_KEY

        if (accessKey.isBlank()) {
            Log.e(TAG, "Porcupine access key not configured!")
            Log.e(TAG, "Get your free key at https://console.picovoice.ai/")
            stopSelf()
            return
        }

        try {
            // Using built-in "COMPUTER" keyword
            // For custom "Assistente", you need to train at https://console.picovoice.ai/
            // Then use: porcupineManager = PorcupineManager.Builder()
            //     .setAccessKey(accessKey)
            //     .setKeywordPath("assistente_pt_android.ppn") // from assets
            //     .setSensitivity(0.7f)
            //     .build(this, wakeWordCallback)

            porcupineManager = PorcupineManager.Builder()
                .setAccessKey(accessKey)
                .setKeyword(Porcupine.BuiltInKeyword.COMPUTER)
                .setSensitivity(0.7f)
                .build(this, wakeWordCallback)

            porcupineManager?.start()
            Log.d(TAG, "Porcupine started - listening for 'Computer'")

        } catch (e: PorcupineException) {
            Log.e(TAG, "Failed to initialize Porcupine: ${e.message}")
            stopSelf()
        }
    }

    private val wakeWordCallback = PorcupineManagerCallback { keywordIndex ->
        Log.d(TAG, "Wake word detected! Index: $keywordIndex")
        activateAssistant()
    }

    private fun stopPorcupine() {
        try {
            porcupineManager?.stop()
            porcupineManager?.delete()
            porcupineManager = null
            Log.d(TAG, "Porcupine stopped")
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping Porcupine: ${e.message}")
        }
    }

    private fun activateAssistant() {
        Log.d(TAG, "Activating assistant!")

        // Launch MainActivity with flag to start listening
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("WAKE_WORD_ACTIVATED", true)
            putExtra("START_LISTENING", true)
        }
        startActivity(intent)
    }
}

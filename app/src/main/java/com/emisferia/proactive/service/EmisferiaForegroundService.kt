package com.emisferia.proactive.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import androidx.core.app.NotificationCompat
import com.emisferia.proactive.MainActivity
import com.emisferia.proactive.R
import com.emisferia.proactive.api.EmisferiaRepository
import com.emisferia.proactive.api.ProactiveAlert
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.*

/**
 * Foreground Service for EmisferIA
 * - Runs continuously in background
 * - Checks for alerts periodically
 * - Speaks proactive notifications
 * - Maintains wake lock for reliability
 */
class EmisferiaForegroundService : Service() {

    companion object {
        private const val TAG = "EmisferiaService"
        private const val CHANNEL_ID = "emisferia_proactive"
        private const val NOTIFICATION_ID = 1
        private const val ALERT_CHECK_INTERVAL = 5 * 60 * 1000L // 5 minutes
        private const val CHECKIN_CHECK_INTERVAL = 60 * 1000L // 1 minute

        private val _isRunning = MutableStateFlow(false)
        val isRunning = _isRunning.asStateFlow()

        fun start(context: Context) {
            val intent = Intent(context, EmisferiaForegroundService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, EmisferiaForegroundService::class.java))
        }
    }

    private val repository = EmisferiaRepository()
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var wakeLock: PowerManager.WakeLock? = null

    private var alertCheckJob: Job? = null
    private var checkinCheckJob: Job? = null
    private var lastSpokenAlertIds = mutableSetOf<String>()

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Service created")

        createNotificationChannel()
        initializeTTS()
        acquireWakeLock()

        _isRunning.value = true
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "Service started")

        val notification = createNotification("EmisferIA ativo", "Monitorando alertas...")
        startForeground(NOTIFICATION_ID, notification)

        startAlertChecking()
        startCheckinMonitoring()

        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "Service destroyed")

        serviceScope.cancel()
        alertCheckJob?.cancel()
        checkinCheckJob?.cancel()

        tts?.stop()
        tts?.shutdown()

        releaseWakeLock()

        _isRunning.value = false
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "EmisferIA Proativo",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Notificações proativas do EmisferIA"
                setShowBadge(false)
            }

            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(title: String, content: String): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(content)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setSilent(true)
            .build()
    }

    private fun updateNotification(title: String, content: String) {
        val notification = createNotification(title, content)
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, notification)
    }

    private fun initializeTTS() {
        tts = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val locale = Locale("pt", "BR")
                val result = tts?.setLanguage(locale)
                ttsReady = result != TextToSpeech.LANG_MISSING_DATA &&
                           result != TextToSpeech.LANG_NOT_SUPPORTED

                if (ttsReady) {
                    tts?.setSpeechRate(1.0f)
                    tts?.setPitch(1.0f)
                    Log.d(TAG, "TTS initialized successfully")
                } else {
                    Log.e(TAG, "TTS language not supported")
                }
            } else {
                Log.e(TAG, "TTS initialization failed")
            }
        }
    }

    private fun speak(text: String, onComplete: (() -> Unit)? = null) {
        if (!ttsReady || tts == null) {
            Log.w(TAG, "TTS not ready, skipping: $text")
            onComplete?.invoke()
            return
        }

        val utteranceId = UUID.randomUUID().toString()

        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(id: String?) {}
            override fun onDone(id: String?) {
                if (id == utteranceId) {
                    onComplete?.invoke()
                }
            }
            override fun onError(id: String?) {
                if (id == utteranceId) {
                    onComplete?.invoke()
                }
            }
        })

        tts?.speak(text, TextToSpeech.QUEUE_ADD, null, utteranceId)
    }

    private fun acquireWakeLock() {
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "EmisferIA::ProactiveService"
        ).apply {
            acquire(10 * 60 * 1000L) // 10 minutes, will be renewed
        }
    }

    private fun releaseWakeLock() {
        wakeLock?.let {
            if (it.isHeld) {
                it.release()
            }
        }
        wakeLock = null
    }

    private fun startAlertChecking() {
        alertCheckJob?.cancel()
        alertCheckJob = serviceScope.launch {
            while (isActive) {
                try {
                    checkForAlerts()
                } catch (e: Exception) {
                    Log.e(TAG, "Error checking alerts", e)
                }
                delay(ALERT_CHECK_INTERVAL)
            }
        }
    }

    private fun startCheckinMonitoring() {
        checkinCheckJob?.cancel()
        checkinCheckJob = serviceScope.launch {
            while (isActive) {
                try {
                    checkForCheckin()
                } catch (e: Exception) {
                    Log.e(TAG, "Error checking checkin", e)
                }
                delay(CHECKIN_CHECK_INTERVAL)
            }
        }
    }

    private suspend fun checkForAlerts() {
        Log.d(TAG, "Checking for alerts...")

        repository.getAlerts().onSuccess { alerts ->
            val highPriorityAlerts = alerts.filter {
                it.priority == "high" && !lastSpokenAlertIds.contains(it.type + it.title)
            }

            if (highPriorityAlerts.isNotEmpty()) {
                Log.d(TAG, "Found ${highPriorityAlerts.size} new high priority alerts")

                updateNotification(
                    "Alerta importante!",
                    highPriorityAlerts.first().title
                )

                // Speak alerts
                highPriorityAlerts.forEach { alert ->
                    val message = buildAlertMessage(alert)
                    withContext(Dispatchers.Main) {
                        speak(message)
                    }
                    lastSpokenAlertIds.add(alert.type + alert.title)
                }

                // Keep only last 50 alert IDs to prevent memory growth
                if (lastSpokenAlertIds.size > 50) {
                    lastSpokenAlertIds = lastSpokenAlertIds.toList().takeLast(50).toMutableSet()
                }
            }
        }.onFailure {
            Log.e(TAG, "Failed to get alerts: ${it.message}")
        }
    }

    private fun buildAlertMessage(alert: ProactiveAlert): String {
        return when (alert.type) {
            "urgent_task" -> "Atenção! Tarefa urgente: ${alert.message}"
            "overdue_payment" -> "Atenção! ${alert.message}"
            "upcoming_event" -> "Lembrete: ${alert.message}"
            "new_message" -> "Nova mensagem: ${alert.message}"
            else -> alert.message
        }
    }

    private suspend fun checkForCheckin() {
        val calendar = Calendar.getInstance()
        val hour = calendar.get(Calendar.HOUR_OF_DAY)
        val minute = calendar.get(Calendar.MINUTE)

        // Morning checkin: 9:00
        if (hour == 9 && minute == 0) {
            repository.getMorningCheckin().onSuccess { checkin ->
                withContext(Dispatchers.Main) {
                    speak(checkin.message)
                }
                updateNotification("Bom dia!", "Checkin matinal")
            }
        }

        // Afternoon checkin: 14:00
        if (hour == 14 && minute == 0) {
            repository.getAfternoonCheckin().onSuccess { checkin ->
                withContext(Dispatchers.Main) {
                    speak(checkin.message)
                }
                updateNotification("Boa tarde!", "Checkin da tarde")
            }
        }

        // Evening checkin: 18:00
        if (hour == 18 && minute == 0) {
            repository.getEveningCheckin().onSuccess { checkin ->
                withContext(Dispatchers.Main) {
                    speak(checkin.message)
                }
                updateNotification("Boa noite!", "Checkin noturno")
            }
        }
    }
}

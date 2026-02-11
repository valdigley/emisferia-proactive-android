package com.emisferia.proactive.service

import android.app.Notification
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import androidx.core.app.NotificationCompat
import com.emisferia.proactive.EmisferiaApp
import com.emisferia.proactive.R
import java.util.*

/**
 * Foreground service that keeps TTS alive even when app is in background.
 * Started by EmisferiaMessagingService when a voice notification arrives.
 * Auto-stops after speech completes.
 */
class TtsForegroundService : Service() {

    companion object {
        private const val TAG = "TtsForeground"
        const val ACTION_SPEAK = "com.emisferia.proactive.ACTION_SPEAK"
        const val EXTRA_TEXT = "text"
        private const val NOTIFICATION_ID = 9001
        private const val STOP_DELAY_MS = 3000L
    }

    private var tts: TextToSpeech? = null
    private var ttsReady = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Service created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_SPEAK) {
            val text = intent.getStringExtra(EXTRA_TEXT)
            if (text.isNullOrBlank()) {
                stopSelf()
                return START_NOT_STICKY
            }

            // Start as foreground service to prevent process death
            startForeground(NOTIFICATION_ID, createForegroundNotification())

            speakText(text)
        } else {
            stopSelf()
        }
        return START_NOT_STICKY
    }

    private fun speakText(text: String) {
        if (tts != null && ttsReady) {
            doSpeak(text)
            return
        }

        tts = TextToSpeech(applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val result = tts?.setLanguage(Locale("pt", "BR"))
                if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    tts?.setLanguage(Locale("pt"))
                }
                tts?.setPitch(1.0f)
                tts?.setSpeechRate(1.0f)
                ttsReady = true

                tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {
                        Log.d(TAG, "TTS started speaking")
                    }

                    override fun onDone(utteranceId: String?) {
                        Log.d(TAG, "TTS done speaking")
                        // Small delay then stop service
                        android.os.Handler(mainLooper).postDelayed({
                            stopForeground(STOP_FOREGROUND_REMOVE)
                            stopSelf()
                        }, STOP_DELAY_MS)
                    }

                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) {
                        Log.e(TAG, "TTS error")
                        stopForeground(STOP_FOREGROUND_REMOVE)
                        stopSelf()
                    }

                    override fun onError(utteranceId: String?, errorCode: Int) {
                        Log.e(TAG, "TTS error code: $errorCode")
                        stopForeground(STOP_FOREGROUND_REMOVE)
                        stopSelf()
                    }
                })

                doSpeak(text)
            } else {
                Log.e(TAG, "TTS init failed with status: $status")
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
    }

    private fun doSpeak(text: String) {
        val utteranceId = UUID.randomUUID().toString()
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
        Log.d(TAG, "Speaking: ${text.take(80)}...")
    }

    private fun createForegroundNotification(): Notification {
        return NotificationCompat.Builder(this, EmisferiaApp.CHANNEL_ID_TTS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("EmisferIA")
            .setContentText("Falando...")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setSilent(true)
            .build()
    }

    override fun onDestroy() {
        super.onDestroy()
        tts?.stop()
        tts?.shutdown()
        tts = null
        ttsReady = false
        Log.d(TAG, "Service destroyed")
    }
}

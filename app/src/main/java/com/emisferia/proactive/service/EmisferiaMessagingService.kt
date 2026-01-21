package com.emisferia.proactive.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import androidx.core.app.NotificationCompat
import com.emisferia.proactive.BuildConfig
import com.emisferia.proactive.MainActivity
import com.emisferia.proactive.R
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.*

/**
 * Firebase Messaging Service for push notifications
 * Handles both foreground and background notifications
 * Can speak notifications aloud using TTS
 */
class EmisferiaMessagingService : FirebaseMessagingService() {

    companion object {
        private const val TAG = "EmisferiaFCM"
        private const val CHANNEL_ID = "emisferia_proactive"
        private const val CHANNEL_NAME = "EmisferIA Proactive"
        private const val CHANNEL_DESCRIPTION = "Notificacoes proativas do EmisferIA"
    }

    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private val mainHandler = Handler(Looper.getMainLooper())

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d(TAG, "New FCM token: ${token.take(20)}...")
        registerTokenWithServer(token)
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)

        Log.d(TAG, "Message received from: ${remoteMessage.from}")

        // Check if message contains a notification payload
        remoteMessage.notification?.let { notification ->
            Log.d(TAG, "Notification: ${notification.title} - ${notification.body}")
            showNotification(notification.title, notification.body, remoteMessage.data)
        }

        // Check if message contains a data payload
        if (remoteMessage.data.isNotEmpty()) {
            Log.d(TAG, "Data payload: ${remoteMessage.data}")
            handleDataMessage(remoteMessage.data)
        }
    }

    private fun handleDataMessage(data: Map<String, String>) {
        val type = data["type"]
        val title = data["title"]
        val body = data["body"]
        val shouldSpeak = data["speak"] == "true" || type == "voice_reminder" || type == "contextual_reminder"

        when (type) {
            "payment" -> {
                val paymentType = data["paymentType"]
                val amount = data["amount"]
                Log.d(TAG, "Payment notification: $paymentType - $amount")
            }
            "checkin" -> {
                val checkinType = data["checkinType"]
                Log.d(TAG, "Check-in notification: $checkinType")
            }
            "alert" -> {
                val priority = data["priority"]
                Log.d(TAG, "Alert notification: $priority")
            }
            "voice_reminder", "contextual_reminder" -> {
                Log.d(TAG, "Voice reminder notification - will speak")
            }
        }

        // Show notification if title and body are in data payload
        if (title != null && body != null) {
            showNotification(title, body, data)

            // Speak the notification if requested
            if (shouldSpeak) {
                val textToSpeak = data["speakText"] ?: "$title. $body"
                speakNotification(cleanForSpeech(textToSpeak))
            }
        }
    }

    /**
     * Speak notification content using TTS
     */
    private fun speakNotification(text: String) {
        mainHandler.post {
            if (tts == null) {
                // Initialize TTS
                tts = TextToSpeech(applicationContext) { status ->
                    if (status == TextToSpeech.SUCCESS) {
                        val result = tts?.setLanguage(Locale("pt", "BR"))
                        if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                            tts?.setLanguage(Locale("pt"))
                        }
                        tts?.setPitch(1.0f)
                        tts?.setSpeechRate(1.0f)
                        ttsReady = true

                        // Now speak
                        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, UUID.randomUUID().toString())
                        Log.d(TAG, "TTS speaking: ${text.take(50)}...")
                    } else {
                        Log.e(TAG, "TTS initialization failed")
                    }
                }
            } else if (ttsReady) {
                tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, UUID.randomUUID().toString())
                Log.d(TAG, "TTS speaking: ${text.take(50)}...")
            }
        }
    }

    /**
     * Clean text for TTS (remove emojis and markdown)
     */
    private fun cleanForSpeech(text: String): String {
        var result = text
            // Remove markdown
            .replace(Regex("\\*\\*(.+?)\\*\\*"), "$1")
            .replace(Regex("\\*(.+?)\\*"), "$1")
            .replace(Regex("_(.+?)_"), "$1")
            .replace(Regex("^#{1,6}\\s*", RegexOption.MULTILINE), "")
            .replace(Regex("^[\\-\\*•]\\s*", RegexOption.MULTILINE), "")

        // Remove emojis
        val sb = StringBuilder()
        var i = 0
        while (i < result.length) {
            val codePoint = result.codePointAt(i)
            val isEmoji = when {
                codePoint in 0x1F600..0x1F64F -> true
                codePoint in 0x1F300..0x1F5FF -> true
                codePoint in 0x1F680..0x1F6FF -> true
                codePoint in 0x1F900..0x1F9FF -> true
                codePoint in 0x1FA00..0x1FAFF -> true
                codePoint in 0x2700..0x27BF -> true
                codePoint in 0x2600..0x26FF -> true
                codePoint in 0x2B00..0x2BFF -> true
                codePoint in 0x2300..0x23FF -> true
                codePoint == 0xFE0F || codePoint == 0x200D -> true
                else -> false
            }
            if (!isEmoji) {
                sb.appendCodePoint(codePoint)
            }
            i += Character.charCount(codePoint)
        }

        return sb.toString().replace(Regex(" {2,}"), " ").trim()
    }

    override fun onDestroy() {
        super.onDestroy()
        tts?.stop()
        tts?.shutdown()
        tts = null
    }

    private fun showNotification(title: String?, body: String?, data: Map<String, String> = emptyMap()) {
        createNotificationChannel()

        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            // Pass data to activity
            data.forEach { (key, value) ->
                putExtra(key, value)
            }
        }

        val pendingIntent = PendingIntent.getActivity(
            this,
            System.currentTimeMillis().toInt(),
            intent,
            PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE
        )

        val defaultSoundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

        val notificationBuilder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title ?: "EmisferIA")
            .setContentText(body ?: "")
            .setAutoCancel(true)
            .setSound(defaultSoundUri)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))

        // Add custom color based on notification type
        val type = data["type"]
        val colorRes = when (type) {
            "payment" -> if (data["paymentType"] == "received") 0xFF00E676.toInt() else 0xFFFF5722.toInt()
            "alert" -> 0xFFFF1744.toInt()
            else -> 0xFF00BCD4.toInt()
        }
        notificationBuilder.setColor(colorRes)

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(System.currentTimeMillis().toInt(), notificationBuilder.build())
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = CHANNEL_DESCRIPTION
                enableLights(true)
                enableVibration(true)
                setShowBadge(true)
            }

            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun registerTokenWithServer(token: String) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val client = OkHttpClient()
                val json = JSONObject().apply {
                    put("token", token)
                    put("platform", "android")
                    put("appVersion", BuildConfig.VERSION_NAME)
                }

                val body = json.toString().toRequestBody("application/json".toMediaType())
                val request = Request.Builder()
                    .url("${BuildConfig.API_URL}/devices/register")
                    .post(body)
                    .build()

                val response = client.newCall(request).execute()
                if (response.isSuccessful) {
                    Log.d(TAG, "Token registered successfully")
                } else {
                    Log.e(TAG, "Failed to register token: ${response.code}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error registering token: ${e.message}")
            }
        }
    }
}

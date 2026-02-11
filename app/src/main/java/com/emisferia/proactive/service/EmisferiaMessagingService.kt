package com.emisferia.proactive.service

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.emisferia.proactive.BuildConfig
import com.emisferia.proactive.EmisferiaApp
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

/**
 * Firebase Messaging Service for push notifications.
 * Shows heads-up notifications in status bar and speaks via TtsForegroundService.
 */
class EmisferiaMessagingService : FirebaseMessagingService() {

    companion object {
        private const val TAG = "EmisferiaFCM"
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d(TAG, "New FCM token: ${token.take(20)}...")
        registerTokenWithServer(token)
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)
        Log.d(TAG, "Message received from: ${remoteMessage.from}")

        // Handle notification payload (when app is in foreground)
        remoteMessage.notification?.let { notification ->
            Log.d(TAG, "Notification: ${notification.title} - ${notification.body}")
            showNotification(notification.title, notification.body, remoteMessage.data)
        }

        // Handle data payload (works in both foreground and background)
        if (remoteMessage.data.isNotEmpty()) {
            Log.d(TAG, "Data payload: ${remoteMessage.data}")
            handleDataMessage(remoteMessage.data)
        }
    }

    private fun handleDataMessage(data: Map<String, String>) {
        val type = data["type"]
        val title = data["title"]
        val body = data["body"]
        val shouldSpeak = data["speak"] == "true" ||
                type == "voice_reminder" ||
                type == "contextual_reminder"

        when (type) {
            "payment" -> Log.d(TAG, "Payment: ${data["paymentType"]} - ${data["amount"]}")
            "checkin" -> Log.d(TAG, "Check-in: ${data["checkinType"]}")
            "alert" -> Log.d(TAG, "Alert: ${data["priority"]}")
            "voice_reminder", "contextual_reminder" -> Log.d(TAG, "Voice reminder")
        }

        // Show notification
        if (title != null || body != null) {
            showNotification(title, body, data)
        }

        // Speak via foreground service (survives background process)
        if (shouldSpeak) {
            val textToSpeak = data["speakText"] ?: "$title. $body"
            val cleanText = cleanForSpeech(textToSpeak)
            startTtsService(cleanText)
        }
    }

    /**
     * Start TtsForegroundService to speak text aloud.
     * Using a foreground service ensures TTS completes even with app closed.
     */
    private fun startTtsService(text: String) {
        try {
            val intent = Intent(this, TtsForegroundService::class.java).apply {
                action = TtsForegroundService.ACTION_SPEAK
                putExtra(TtsForegroundService.EXTRA_TEXT, text)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }
            Log.d(TAG, "TTS service started for: ${text.take(50)}...")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start TTS service: ${e.message}")
        }
    }

    private fun showNotification(title: String?, body: String?, data: Map<String, String> = emptyMap()) {
        val type = data["type"]
        val priority = data["priority"]
        val shouldSpeak = data["speak"] == "true" ||
                type == "voice_reminder" ||
                type == "contextual_reminder"

        // Use voice channel for voice notifications, alert channel for everything else
        val channelId = if (shouldSpeak) EmisferiaApp.CHANNEL_ID_VOICE else EmisferiaApp.CHANNEL_ID_ALERTS

        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            data.forEach { (key, value) -> putExtra(key, value) }
        }

        val pendingIntent = PendingIntent.getActivity(
            this,
            System.currentTimeMillis().toInt(),
            intent,
            PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE
        )

        val defaultSoundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

        // Determine notification color by type
        val color = when (type) {
            "payment" -> if (data["paymentType"] == "received") 0xFF00E676.toInt() else 0xFFFF5722.toInt()
            "alert" -> when (priority) {
                "urgent" -> 0xFFFF1744.toInt()
                "high" -> 0xFFFF5722.toInt()
                else -> 0xFFFF9800.toInt()
            }
            "checkin" -> 0xFF00BCD4.toInt()
            "voice_reminder", "contextual_reminder" -> 0xFF7C4DFF.toInt()
            else -> 0xFF00BCD4.toInt()
        }

        // Determine category for system behavior
        val category = when {
            priority == "urgent" || type == "voice_reminder" -> NotificationCompat.CATEGORY_ALARM
            type == "alert" -> NotificationCompat.CATEGORY_REMINDER
            else -> NotificationCompat.CATEGORY_MESSAGE
        }

        val notificationBuilder = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title ?: "EmisferIA")
            .setContentText(body ?: "")
            .setAutoCancel(true)
            .setSound(defaultSoundUri)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(category)
            .setDefaults(Notification.DEFAULT_VIBRATE or Notification.DEFAULT_LIGHTS)
            .setContentIntent(pendingIntent)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setColor(color)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)

        // Full-screen intent for urgent/voice - pops over lockscreen
        if (priority == "urgent" || type == "voice_reminder") {
            val fullScreenIntent = PendingIntent.getActivity(
                this,
                System.currentTimeMillis().toInt() + 1,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            notificationBuilder.setFullScreenIntent(fullScreenIntent, true)
        }

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(System.currentTimeMillis().toInt(), notificationBuilder.build())
    }

    /**
     * Clean text for TTS (remove emojis and markdown)
     */
    private fun cleanForSpeech(text: String): String {
        var result = text
            .replace(Regex("\\*\\*(.+?)\\*\\*"), "$1")
            .replace(Regex("\\*(.+?)\\*"), "$1")
            .replace(Regex("_(.+?)_"), "$1")
            .replace(Regex("^#{1,6}\\s*", RegexOption.MULTILINE), "")
            .replace(Regex("^[\\-\\*\u2022]\\s*", RegexOption.MULTILINE), "")
            .replace(Regex("\\[!]"), "")
            .replace(Regex("\\[x]", RegexOption.IGNORE_CASE), "")
            .replace(Regex("\\[\\s?]"), "")

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
                codePoint in 0x2190..0x21FF -> true
                codePoint in 0x25A0..0x25FF -> true
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

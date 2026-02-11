package com.emisferia.proactive

import android.app.NotificationChannel
import android.app.NotificationManager
import android.media.AudioAttributes
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

class EmisferiaApp : android.app.Application() {

    companion object {
        const val CHANNEL_ID_ALERTS = "emisferia_alerts_v2"
        const val CHANNEL_ID_VOICE = "emisferia_voice"
        const val CHANNEL_ID_TTS = "emisferia_tts_service"
        private const val TAG = "EmisferiaApp"
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
        initializeFirebase()
    }

    private fun initializeFirebase() {
        FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
            if (!task.isSuccessful) {
                Log.w(TAG, "Fetching FCM registration token failed", task.exception)
                return@addOnCompleteListener
            }
            val token = task.result
            Log.d(TAG, "FCM Token: ${token.take(20)}...")
            registerTokenWithServer(token)
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
                    Log.d(TAG, "FCM token registered with server")
                } else {
                    Log.e(TAG, "Failed to register FCM token: ${response.code}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error registering FCM token: ${e.message}")
            }
        }
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = getSystemService(NotificationManager::class.java)

            // Delete old channel with wrong importance
            notificationManager.deleteNotificationChannel("emisferia_proactive")

            // Channel 1: Alerts (heads-up, sound, vibration)
            val alertChannel = NotificationChannel(
                CHANNEL_ID_ALERTS,
                "EmisferIA Alertas",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Check-ins, alertas, pagamentos e lembretes"
                enableLights(true)
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 300, 200, 300)
                setShowBadge(true)
                setSound(
                    Settings.System.DEFAULT_NOTIFICATION_URI,
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
            }
            notificationManager.createNotificationChannel(alertChannel)

            // Channel 2: Voice reminders (highest priority for heads-up + TTS)
            val voiceChannel = NotificationChannel(
                CHANNEL_ID_VOICE,
                "EmisferIA Voz",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Lembretes com voz - fala automaticamente"
                enableLights(true)
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 500, 300, 500)
                setShowBadge(true)
                lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
            }
            notificationManager.createNotificationChannel(voiceChannel)

            // Channel 3: TTS service (low importance - just keeps service alive)
            val ttsChannel = NotificationChannel(
                CHANNEL_ID_TTS,
                "EmisferIA TTS",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Servico de voz em segundo plano"
                setShowBadge(false)
                setSound(null, null)
            }
            notificationManager.createNotificationChannel(ttsChannel)
        }
    }
}

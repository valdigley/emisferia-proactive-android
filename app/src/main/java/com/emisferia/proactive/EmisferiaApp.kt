package com.emisferia.proactive

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
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

class EmisferiaApp : Application() {

    companion object {
        const val NOTIFICATION_CHANNEL_ID = "emisferia_proactive"
        const val NOTIFICATION_CHANNEL_NAME = "EmisferIA Proactive"
        private const val TAG = "EmisferiaApp"
    }

    override fun onCreate() {
        super.onCreate()

        // Create notification channel for Android 8.0+
        createNotificationChannel()

        // Initialize Firebase and register token
        initializeFirebase()
    }

    private fun initializeFirebase() {
        FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
            if (!task.isSuccessful) {
                Log.w(TAG, "Fetching FCM registration token failed", task.exception)
                return@addOnCompleteListener
            }

            // Get new FCM registration token
            val token = task.result
            Log.d(TAG, "FCM Token: ${token.take(20)}...")

            // Register token with server
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

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                NOTIFICATION_CHANNEL_NAME,
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Notificacoes proativas do EmisferIA"
                enableLights(true)
                enableVibration(true)
            }

            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }
}

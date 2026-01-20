package com.emisferia.proactive

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build

class EmisferiaApp : Application() {

    companion object {
        const val NOTIFICATION_CHANNEL_ID = "emisferia_proactive"
        const val NOTIFICATION_CHANNEL_NAME = "EmisferIA Proactive"
    }

    override fun onCreate() {
        super.onCreate()

        // Create notification channel for Android 8.0+
        createNotificationChannel()
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

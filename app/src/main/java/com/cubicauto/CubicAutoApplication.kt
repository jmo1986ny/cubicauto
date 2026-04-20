package com.cubicauto

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build

class CubicAutoApplication : Application() {
    companion object {
        const val MEDIA_CHANNEL_ID   = "cubicauto_media"
        const val MEDIA_CHANNEL_NAME = "CubicAuto Media Playback"
        const val NOTIFICATION_ID    = 1001
    }
    override fun onCreate() {
        super.onCreate()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                MEDIA_CHANNEL_ID, MEDIA_CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "CubicAuto media playback controls"
                setShowBadge(false)
            }
            getSystemService(NotificationManager::class.java)
                .createNotificationChannel(channel)
        }
    }
}

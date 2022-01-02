package com.lahsuak.flashlight.util

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build

class App : Application() {
    companion object {
        var flashlightExist = true
        const val CHANNEL_ID = "com.lahsuak.flashlight.FLASHLIGHT"
    }

    var manager: NotificationManager? = null

    override fun onCreate() {
        super.onCreate()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            manager = getSystemService(NotificationManager::class.java)
        } else if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) { //Added new condition for lollipop 5,5.1 versions
            manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        }
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel1 = NotificationChannel(
                CHANNEL_ID,
                "Channel Flashlight",
                NotificationManager.IMPORTANCE_DEFAULT
            )
            channel1.description = "Flashlight channel"
            manager!!.createNotificationChannel(channel1)
        }
    }
}

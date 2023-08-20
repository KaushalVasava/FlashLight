package com.lahsuak.apps.flashlight.util

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build

class FlashLightApp : Application() {

    companion object {
        var counter = 0
        var isTorchOn = false
        var flashlightExist = true
        lateinit var appContext: Context
    }

    private var manager: NotificationManager? = null

    override fun onCreate() {
        super.onCreate()
        appContext = this
        manager = getSystemService(NotificationManager::class.java)
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                AppConstants.NOTIFICATION_CHANNEL_ID,
                AppConstants.NOTIFICATION_CHANNEL_NAME,
                NotificationManager.IMPORTANCE_DEFAULT
            )
            channel.description = AppConstants.NOTIFICATION_DETAILS
            manager?.createNotificationChannel(channel)
        }
    }
}

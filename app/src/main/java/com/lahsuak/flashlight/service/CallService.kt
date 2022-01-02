package com.lahsuak.flashlight.service

import android.app.PendingIntent.*
import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import android.util.Log
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat
import com.lahsuak.flashlight.MainActivity
import com.lahsuak.flashlight.R
import com.lahsuak.flashlight.`interface`.LightListener
import com.lahsuak.flashlight.receiver.CallReceiver
import com.lahsuak.flashlight.util.App.Companion.CHANNEL_ID

class CallService : Service() {
    private var isTorchOn = false
    private var lightListener: LightListener? = null

    private val binder: IBinder = MyBinder()

    override fun onBind(intent: Intent): IBinder {
        return binder
    }

    inner class MyBinder : Binder() {
        val service: CallService
            get() = this@CallService
    }

    fun setCallBack(lightListener: LightListener?) {
        this.lightListener = lightListener
    }


    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val actionName = intent!!.getBooleanExtra("myActionName", false)
        Log.d("TAG", "onStartCommand:$actionName")
        if (actionName == false) {
            isTorchOn = false
        } else {
            isTorchOn = true
        }
        onLightClick(isTorchOn)
        return super.onStartCommand(intent, flags, startId)
    }

    private fun onLightClick(isPlay: Boolean) {
        if (lightListener != null) {
            lightListener!!.onTorchClick(isPlay)
        }
    }

    fun showNotification(
        isPlay: Boolean
    ) {
        val intent = Intent(this, MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        val contentIntent = getActivity(this, 0, intent, 0)
        var text = "Play"

        if (!isPlay) {
            text = "Play"//pause to play
        } else {
            text = "Pause"
        }
        Log.d("TAG", "showNotification: $text")

        val playIntent =
            Intent(this, CallReceiver::class.java).setAction(text)
//                playIntent.putExtra("Pause",true)
//        playIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        val playPendingIntent =
            getBroadcast(this, 0, playIntent, 0)

        //new custom notification
        val notificationView = RemoteViews(packageName, R.layout.notification_layout)
        notificationView.setOnClickPendingIntent(
            R.id.flash_light,
            playPendingIntent
        )
        //Util.hapticFeedback(notificationView as View)

        if (isPlay) {
            notificationView.setImageViewResource(R.id.flash_light, R.drawable.ic_flashlight_on)
        } else {
            notificationView.setImageViewResource(R.id.flash_light, R.drawable.ic_flashlight_off)
        }

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_flashlight_on)
            .setContent(notificationView)
            .setCustomContentView(notificationView)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT) //changed from HIGH TO DEFAULT
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(contentIntent)

        startForeground(3, notification.build())
    }

    override fun onDestroy() {
        super.onDestroy()
        stopForeground(true)
        stopSelf()
    }

}
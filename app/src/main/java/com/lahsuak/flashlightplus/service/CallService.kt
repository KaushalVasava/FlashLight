package com.lahsuak.flashlightplus.service

import android.app.PendingIntent.*
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.Binder
import android.os.IBinder
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.RemoteViews
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationCompat
import androidx.preference.PreferenceManager
import com.lahsuak.flashlightplus.ui.activity.MainActivity
import com.lahsuak.flashlightplus.R
import com.lahsuak.flashlightplus.`interface`.LightListener
import com.lahsuak.flashlightplus.receiver.CallReceiver
import com.lahsuak.flashlightplus.util.*
import com.lahsuak.flashlightplus.util.App.Companion.CHANNEL_ID
import com.lahsuak.flashlightplus.util.Constants.FLASH_EXIST
import com.lahsuak.flashlightplus.util.Constants.FLASH_ON_START
import com.lahsuak.flashlightplus.util.Constants.SETTING_DATA

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
        val prefSetting = PreferenceManager.getDefaultSharedPreferences(baseContext)
        val checkStartUpFlash = prefSetting.getBoolean(FLASH_ON_START, false)
        val actionName = intent!!.getBooleanExtra("myActionName", checkStartUpFlash)
        isTorchOn = actionName != false
        onLightClick(isTorchOn)

        return super.onStartCommand(intent, flags, startId)
    }

    private fun onLightClick(isPlay: Boolean) {
        if (lightListener != null) {
            lightListener!!.onTorchClick(isPlay)
        }
    }

    fun torchSwitch(turnON: Boolean, view1: ImageView, view2: ImageButton) {
        val isFlashAvailableOnDevice =
            baseContext.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_FLASH)
        if (!isFlashAvailableOnDevice) {
            App.flashlightExist = false
            Util.notifyUser(baseContext, "Your device doesn't support flash light")
        } else {
            val cameraManager =
                baseContext.getSystemService(AppCompatActivity.CAMERA_SERVICE) as CameraManager
            App.flashlightExist = true
            try {
                val cameraId = cameraManager.cameraIdList[0]
                cameraManager.setTorchMode(cameraId, turnON)
                if (cameraManager.getCameraCharacteristics(cameraManager.cameraIdList[1]).get(
                        CameraCharacteristics.FLASH_INFO_AVAILABLE
                    ) == true
                ) {
                    cameraManager.setTorchMode(cameraManager.cameraIdList[1], turnON)
                }
                // state = turnON
                if (turnON) {
                    view1.setImageResource(R.drawable.ic_pause)
                    view2.setImageResource(R.drawable.ic_flashlight_on)
                } else {
                    view1.setImageResource(R.drawable.ic_play)
                    view2.setImageResource(R.drawable.ic_flashlight_off)
                }
            } catch (e: CameraAccessException) {
            }
        }
        val pref = baseContext.getSharedPreferences(SETTING_DATA, MODE_PRIVATE).edit()
        pref.putBoolean(FLASH_EXIST, App.flashlightExist)
        pref.apply()
    }

    fun showNotification(
        isPlay: Boolean
    ) {
        val intent = Intent(this, MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        val contentIntent = getActivity(this, 0, intent, 0)
        var text = "Play"

        if (!isPlay) {
            text = "Play"
        } else {
            text = "Pause"
        }
        val playIntent =
            Intent(this, CallReceiver::class.java).setAction(text)
        val playPendingIntent =
            getBroadcast(this, 0, playIntent, 0)

        //new custom notification
        val notificationView = RemoteViews(packageName, R.layout.notification_layout)
        notificationView.setOnClickPendingIntent(
            R.id.flash_light,
            playPendingIntent
        )

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
package com.lahsuak.flashlightplus.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraManager
import android.os.Handler
import android.os.Looper
import android.telephony.TelephonyManager
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.PreferenceManager
import com.lahsuak.flashlightplus.service.CallService
import com.lahsuak.flashlightplus.util.App.Companion.flashlightExist
import com.lahsuak.flashlightplus.util.CALL_NOTIFICATION
import com.lahsuak.flashlightplus.util.SHOW_NOTIFICATION

class CallReceiver : BroadcastReceiver() {
    private var handler1: Handler? = Handler(Looper.getMainLooper())
    private var isPause = false

    companion object {
        private var onEverySecond: Runnable? = null
    }

    override fun onReceive(context: Context, intent: Intent) {
        val serviceIntent = Intent(context, CallService::class.java)

        if (intent.action != null) {
            if (flashlightExist) {
                if (intent.action == "Pause") {
                    serviceIntent.putExtra("myActionName", false)
                    context.startService(serviceIntent)
                } else if (intent.action == "Play") {
                    serviceIntent.putExtra("myActionName", true)
                    context.startService(serviceIntent)
                }
            }
        }
        val pref = PreferenceManager.getDefaultSharedPreferences(context)
        val callNot = pref.getBoolean(CALL_NOTIFICATION, true)
        val appNot = pref.getBoolean(SHOW_NOTIFICATION, true)

        val isAllow = callNot && appNot
        if (intent.getStringExtra(TelephonyManager.EXTRA_STATE)
                .equals(TelephonyManager.EXTRA_STATE_RINGING)
        ) {
            if (isAllow)
                switchingFlash(context)
        } else if (intent.getStringExtra(TelephonyManager.EXTRA_STATE)
                .equals(TelephonyManager.EXTRA_STATE_OFFHOOK)
        ) {
            if (isAllow) {
                turnFlash(context, false)
                isPause = true
                switchingFlash(context)
            }
        } else if (intent.getStringExtra(TelephonyManager.EXTRA_STATE)
                .equals(TelephonyManager.EXTRA_STATE_IDLE)
        ) {
            if (isAllow) {
                turnFlash(context, false)
                isPause = true
                switchingFlash(context)
            }
        }
    }

    private fun switchingFlash(context: Context) {
        var flashOn = true
        onEverySecond = Runnable {
            if (isPause) {
                handler1!!.removeCallbacks(onEverySecond!!)
                turnFlash(context, false)
            } else {
                flashOn = !flashOn
                handler1!!.postDelayed(onEverySecond!!, 300)
                turnFlash(context, flashOn)
            }
        }
        handler1!!.postDelayed(onEverySecond!!, 300)
    }

    private fun turnFlash(context: Context, isCheck: Boolean) {
        val isFlashAvailableOnDevice =
            context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_FLASH)
        if (!isFlashAvailableOnDevice) {
            Toast.makeText(context, "Your device doesn't support flash light", Toast.LENGTH_SHORT)
                .show()
        } else {
            val cameraManager =
                context.getSystemService(AppCompatActivity.CAMERA_SERVICE) as CameraManager
            try {
                val cameraId = cameraManager.cameraIdList[0]
                cameraManager.setTorchMode(cameraId, isCheck)
            } catch (e: CameraAccessException) {
            }
        }
    }

}
package com.lahsuak.apps.flashlight.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraManager
import android.os.Handler
import android.os.Looper
import android.telephony.TelephonyManager
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.PreferenceManager
import com.lahsuak.apps.flashlight.R
import com.lahsuak.apps.flashlight.service.CallService
import com.lahsuak.apps.flashlight.util.AppConstants
import com.lahsuak.apps.flashlight.util.FlashLightApp.Companion.flashlightExist
import com.lahsuak.apps.flashlight.util.AppConstants.CALL_NOTIFICATION
import com.lahsuak.apps.flashlight.util.AppConstants.SHOW_NOTIFICATION
import com.lahsuak.apps.flashlight.util.logError
import com.lahsuak.apps.flashlight.util.toast

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
                if (intent.action == AppConstants.PAUSE) {
                    serviceIntent.putExtra(AppConstants.ACTION_NAME, false)
                    context.startService(serviceIntent)
                } else if (intent.action == AppConstants.PLAY) {
                    serviceIntent.putExtra(AppConstants.ACTION_NAME, true)
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
                handler1!!.postDelayed(onEverySecond!!, AppConstants.BLINK_DELAY)
                turnFlash(context, flashOn)
            }
        }
        handler1?.postDelayed(onEverySecond!!, AppConstants.BLINK_DELAY)
    }

    private fun turnFlash(context: Context, isCheck: Boolean) {
        val isFlashAvailableOnDevice =
            context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_FLASH)
        if (!isFlashAvailableOnDevice) {
            context.toast {
                context.getString(R.string.device_doesn_t_support_flash_light)
            }
        } else {
            val cameraManager =
                context.getSystemService(AppCompatActivity.CAMERA_SERVICE) as CameraManager
            try {
                val cameraId = cameraManager.cameraIdList[0]
                cameraManager.setTorchMode(cameraId, isCheck)
            } catch (e: CameraAccessException) {
                e.logError()
            }
        }
    }
}
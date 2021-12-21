package com.lahsuak.flashlightplus.util

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.SurfaceTexture
import android.hardware.Camera
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.telephony.TelephonyManager
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import java.io.IOException

class CallReceiver : BroadcastReceiver() {
    private var handler1: Handler? = Handler(Looper.getMainLooper())
    private var isPause = false

    companion object {
        private var onEverySecond: Runnable? = null
    }

    override fun onReceive(context: Context?, intent: Intent?) {
        if (intent != null) {
            if (intent.getStringExtra(TelephonyManager.EXTRA_STATE)
                    .equals(TelephonyManager.EXTRA_STATE_RINGING)
            ) {
                switchingFlash(context!!)
            } else if (intent.getStringExtra(TelephonyManager.EXTRA_STATE)
                    .equals(TelephonyManager.EXTRA_STATE_OFFHOOK)
            ) {

                turnFlash(context!!, false)
                isPause = true
                switchingFlash(context)
//                    if (handler1 != null)
//                        handler1!!.removeCallbacks(onEverySecond!!)
            } else if (intent.getStringExtra(TelephonyManager.EXTRA_STATE)
                    .equals(TelephonyManager.EXTRA_STATE_IDLE)
            ) {
                turnFlash(context!!, false)
                isPause = true
                switchingFlash(context)
//                    if (handler1 != null)
//                        handler1!!.removeCallbacks(onEverySecond!!)

                //Toast.makeText(context, "call end", Toast.LENGTH_SHORT).show()
            }
        }
//        val telephoneManager =
//            context?.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
//
//        telephoneManager.listen(object : PhoneStateListener() {
//            override fun onCallStateChanged(state: Int, phoneNumber: String?) {
//                super.onCallStateChanged(state, phoneNumber)
//                //state 0 = rejected
//                //state 1 = incoming call
//                //state 2 = received
//
//                if (state == 1) {
//                    switchingFlash(context)
//                } else {
//                    turnFlash(context, false)
//                    if(handler1!=null && onEverySecond!=null)
//                        handler1!!.removeCallbacks(onEverySecond!!)
//                }
//            }
//        }, PhoneStateListener.LISTEN_CALL_STATE)
    }

    private fun switchingFlash(context: Context) {
        var flashOn = true
        onEverySecond = Runnable {
            if (isPause) {
                handler1!!.removeCallbacks(onEverySecond!!)
                turnFlash(context, false)
            } else {
                flashOn = !flashOn
                handler1!!.postDelayed(onEverySecond!!, 500)
                turnFlash(context, flashOn)
            }
        }
        handler1!!.postDelayed(onEverySecond!!, 500)
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
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    cameraManager.setTorchMode(cameraId, isCheck)
                } else if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
                    val mCam = Camera.open()
                    val p: Camera.Parameters = mCam.parameters
                    p.flashMode = Camera.Parameters.FLASH_MODE_TORCH
                    mCam.parameters = p
                    val mPreviewTexture = SurfaceTexture(0)
                    try {
                        mCam.setPreviewTexture(mPreviewTexture)
                    } catch (ex: IOException) {
                        // Ignore
                    }
                    mCam.startPreview()
                }
            } catch (e: CameraAccessException) {
            }
        }
    }

}
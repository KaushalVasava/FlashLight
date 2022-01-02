package com.lahsuak.flashlight.util

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Context.MODE_PRIVATE
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.SurfaceTexture
import android.hardware.Camera
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.lahsuak.flashlight.BuildConfig
import com.lahsuak.flashlight.R
import com.lahsuak.flashlight.util.App.Companion.flashlightExist
import java.io.IOException

object Util {
    var SHAKE_THRESHOLD = 3.25f
    fun torchSwitch(context: Context, turnON: Boolean, view1: ImageView, view2: ImageButton) {
        val isFlashAvailableOnDevice =
            context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_FLASH)
        if (!isFlashAvailableOnDevice) {
            flashlightExist = false
            notifyUser(context, "Your device doesn't support flash light")
        } else {
            val cameraManager =
                context.getSystemService(AppCompatActivity.CAMERA_SERVICE) as CameraManager
            flashlightExist = true
            try {
                val cameraId = cameraManager.cameraIdList[0]
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    cameraManager.setTorchMode(cameraId, turnON)
                    if (cameraManager.getCameraCharacteristics(cameraManager.cameraIdList[1]).get(
                            CameraCharacteristics.FLASH_INFO_AVAILABLE
                        ) == true
                    ) {
                        cameraManager.setTorchMode(cameraManager.cameraIdList[1], turnON)
                    }
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
        val pref= context.getSharedPreferences("EXIST_FLASH",MODE_PRIVATE).edit()
        pref.putBoolean("flash_exist",flashlightExist)
        pref.apply()
    }

    fun playSound(context: Context) {
        val mediaPlayer = MediaPlayer.create(context, R.raw.click_sound)
        mediaPlayer.start()
    }

    fun hapticFeedback(view: View) {
        view.performHapticFeedback(
            HapticFeedbackConstants.VIRTUAL_KEY,
            HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING // Ignore device's setting. Otherwise, you can use FLAG_IGNORE_VIEW_SETTING to ignore view's setting.
        )
    }

    fun moreApp(context: Context) {
        try {
            context.startActivity(
                Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse(context.getString(R.string.market_string))
                )
            )
        } catch (e: ActivityNotFoundException) {
            context.startActivity(
                Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse(context.getString(R.string.market_developer_string))
                )
            )
        }
    }

    fun shareApp(context: Context) {
        try {
            val intent = Intent(Intent.ACTION_SEND)
            intent.type = "text/plain"
            intent.putExtra(Intent.EXTRA_SUBJECT, "Share this App")
            val shareMsg =
                context.getString(R.string.play_store_share) + BuildConfig.APPLICATION_ID + "\n\n"
            intent.putExtra(Intent.EXTRA_TEXT, shareMsg)
            context.startActivity(Intent.createChooser(intent, "Share by"))
        } catch (e: Exception) {
            notifyUser(
                context,
                "Some thing went wrong!!"
            )
        }
    }

    fun sendFeedbackMail(context: Context) {
        try {
            val emailIntent = Intent(Intent.ACTION_SENDTO).apply {
                data = Uri.parse("mailto:") // only email apps should handle this
                putExtra(Intent.EXTRA_EMAIL, arrayOf(context.getString(R.string.feedback_email)))
                val info = Build.MODEL + "," + Build.MANUFACTURER
                putExtra(Intent.EXTRA_TEXT, "Please write your suggestions or issues")
                putExtra(Intent.EXTRA_SUBJECT, "Feedback From FlashLight, $info")
            }
            context.startActivity(emailIntent)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    fun appRating(context: Context) {
        val uri = Uri.parse("market://details?id=" + context.packageName)
        val goToMarket = Intent(Intent.ACTION_VIEW, uri)
        try {
            context.startActivity(goToMarket)
        } catch (e: ActivityNotFoundException) {
            notifyUser(context, "Sorry for inconvenience")
        }
    }

    fun notifyUser(context: Context, message: String) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }
}
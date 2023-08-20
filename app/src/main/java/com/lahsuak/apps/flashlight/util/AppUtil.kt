package com.lahsuak.apps.flashlight.util

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.TypedValue
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.animation.Animation
import android.view.animation.AnimationUtils
import android.widget.Toast
import com.lahsuak.apps.flashlight.BuildConfig
import com.lahsuak.apps.flashlight.R

object AppUtil {
    var shakeThreshold = 32.5f
    fun playSound(context: Context) {
        val mediaPlayer = MediaPlayer.create(context, R.raw.click_sound)
        mediaPlayer.start()
    }

    fun hapticFeedback(view: View) {
        view.performHapticFeedback(
            HapticFeedbackConstants.VIRTUAL_KEY,
            HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING
            // Ignore device's setting. Otherwise, you can use FLAG_IGNORE_VIEW_SETTING to ignore view's setting.
        )
    }

    //settings methods
    fun openMoreApp(context: Context) {
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

    fun shareApp(context: Context?) {
        context ?: return
        try {
            val intent = Intent(Intent.ACTION_SEND)
            intent.type = AppConstants.IMAGE_MIME_TYPE
            intent.putExtra(Intent.EXTRA_SUBJECT, context.getString(R.string.share_this_app))
            val shareMsg =
                context.getString(R.string.play_store_share) + BuildConfig.APPLICATION_ID + "\n\n"
            intent.putExtra(Intent.EXTRA_TEXT, shareMsg)
            context.startActivity(Intent.createChooser(intent, AppConstants.SHARE_BY))
        } catch (e: Exception) {
            context.toast {
                context.getString(R.string.something_went_wrong)
            }
        }
    }

    fun sendFeedbackMail(context: Context) {
        try {
            val emailIntent = Intent(Intent.ACTION_SENDTO).apply {
                data = Uri.parse(AppConstants.MAIL_TO) // only email apps should handle this
                putExtra(
                    Intent.EXTRA_EMAIL,
                    arrayOf(context.getString(R.string.feedback_email))
                )
                val info = Build.MODEL + "," + Build.MANUFACTURER
                putExtra(Intent.EXTRA_TEXT, context.getString(R.string.please_write_suggestions))
                putExtra(
                    Intent.EXTRA_SUBJECT,
                    context.getString(R.string.feedback_from_flashlight, info)
                )
            }
            try {
                context.startActivity(emailIntent)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun appRating(context: Context) {
        val uri = Uri.parse(AppConstants.MARKET_DETAILS_ID + context.packageName)
        val goToMarket = Intent(Intent.ACTION_VIEW, uri)
        try {
            context.startActivity(goToMarket)
        } catch (e: ActivityNotFoundException) {
            context.toast {
                context.getString(R.string.something_went_wrong)
            }
        }
    }

    fun openWebsite(context: Context?, url: String) {
        context ?: return
        try {
            val intent = Intent(Intent.ACTION_VIEW)
            intent.data = Uri.parse(url)
            context.startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            e.logError()
            context.toast {
                context.getString(R.string.no_application_found)
            }
        } catch (e: Throwable) {
            e.logError()
            context.toast { context.getString(R.string.something_went_wrong) }
        }
    }

    fun openSettingsPage(activity: Activity?) {
        activity ?: return
        activity.runActivityCatching {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            val uri: Uri =
                Uri.fromParts(AppConstants.PACKAGE, FlashLightApp.appContext.packageName, null)
            intent.data = uri
            activity.startActivity(intent)
        }
    }

    private inline fun Context?.runActivityCatching(block: () -> Unit) {
        this ?: return
        try {
            block()
        } catch (e: ActivityNotFoundException) {
            e.logError()
            this.toast { this.getString(R.string.no_application_found) }
        }
    }
}

inline fun Context?.toast(crossinline msgProvider: () -> String) {
    this ?: return
    val block = {
        Toast.makeText(this, msgProvider(), Toast.LENGTH_SHORT).show()
    }
    postBlockInMainLooper(block)
}

fun postBlockInMainLooper(block: () -> Unit) {
    if (Looper.getMainLooper() == Looper.myLooper()) {
        block()
    } else {
        Handler(Looper.getMainLooper()).post(block)
    }
}

fun Context.getAnime(): Animation {
    return AnimationUtils.loadAnimation(this, R.anim.fade_out)
}
fun Context.setSensor(listener: SensorEventListener): SensorManager {
    val sensorManager = this.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    val sensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    if (sensor != null) {
        sensorManager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_NORMAL)
    }
    return sensorManager
}

fun Context.getAttribute(resId: Int): Int {
    val value = TypedValue()
    this.theme.resolveAttribute(resId, value, true)
    return value.data
}

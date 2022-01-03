package com.lahsuak.flashlight

import android.Manifest
import android.content.*
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.*
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.slider.Slider
import com.lahsuak.flashlight.util.*
import com.lahsuak.flashlight.util.BIG_FLASH_AS_SWITCH
import com.lahsuak.flashlight.util.FLASH_ON_START
import com.lahsuak.flashlight.util.HAPTIC_FEEDBACK
import com.lahsuak.flashlight.util.SETTING_DATA
import com.lahsuak.flashlight.util.SOS_NUMBER
import com.lahsuak.flashlight.util.TOUCH_SOUND
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import java.text.NumberFormat
import kotlin.math.roundToInt
import android.hardware.*
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import kotlin.math.pow
import kotlin.math.sqrt

import android.content.Intent
import android.os.IBinder
import com.lahsuak.flashlight.databinding.FragmentSettingBinding

import android.view.animation.AnimationUtils
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.MobileAds
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.play.core.review.ReviewManager
import com.google.android.play.core.review.ReviewManagerFactory
import com.lahsuak.flashlight.`interface`.LightListener
import com.lahsuak.flashlight.databinding.ActivityMainBinding
import com.lahsuak.flashlight.databinding.BottomsheetDialogBinding
import com.lahsuak.flashlight.service.CallService
import com.lahsuak.flashlight.util.App.Companion.flashlightExist
import com.lahsuak.flashlight.util.Util.SHAKE_THRESHOLD
import com.lahsuak.flashlight.util.Util.appRating
import com.lahsuak.flashlight.util.Util.hapticFeedback
import com.lahsuak.flashlight.util.Util.moreApp
import com.lahsuak.flashlight.util.Util.notifyUser
import com.lahsuak.flashlight.util.Util.playSound
import com.lahsuak.flashlight.util.Util.sendFeedbackMail
import com.lahsuak.flashlight.util.Util.shareApp

class MainActivity : AppCompatActivity()
    , SensorEventListener, ServiceConnection, LightListener {
    private lateinit var binding: ActivityMainBinding
    private lateinit var settingBinding: FragmentSettingBinding
    private var mLastShakeTime: Long = 0
    private lateinit var sensorManager: SensorManager
    private var service: CallService? = null

    private lateinit var settingFragment: BottomSheetDialog

    //in-app review and in-app update
    private lateinit var reviewManager: ReviewManager

    //extra
    private var job: Job? = null
    private var flashState = false
    private var screenState = false // new for screen brightness
    var checkLight = true // true for flashlight and false for screen light
    private var onOrOff = false
    private var isRunning = false
    private var isNotificationEnable = true
    private var isHapticFeedBackEnable = true
    private var isSoundEnable = false
    private var flashOnAtStartUpEnable = false
    private var bigFlashAsSwitchEnable = false
    private var shakeToLightEnable = true

    override fun onResume() {
        super.onResume()
        if (flashlightExist) {
            val intent = Intent(this, CallService::class.java)
            bindService(intent, this, BIND_AUTO_CREATE)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val pref = getSharedPreferences("EXIST_FLASH", MODE_PRIVATE)
        flashlightExist = pref.getBoolean("flash_exist", true)
        reviewManager = ReviewManagerFactory.create(this)

        val myAnim = AnimationUtils.loadAnimation(this, R.anim.fade_out)
        binding.playBtn.animation = myAnim
        binding.sosBtn.animation = myAnim
        binding.phoneBtn.animation = myAnim
        binding.torchBtn.animation = myAnim

        //this is for SOS settings
        settingBinding = FragmentSettingBinding.inflate(layoutInflater)

        settingFragment = BottomSheetDialog(this)
        settingFragment.setContentView(settingBinding.root)
        settingFragment.behavior.state = BottomSheetBehavior.STATE_EXPANDED

        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager

        MobileAds.initialize(this) { }
        val adRequest = AdRequest.Builder().build()
        binding.adView.loadAd(adRequest)

        //Shake to turn ON/OFF flashlight
        val sensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        if (sensor != null) {
            sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_NORMAL)
        }

        //check permissions
        checkBothPermissions()
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M)
            checkCameraPermission()

        //Flash light fragment methods
        //flashlight blinking time
        checkSwitch()

        binding.blinkingLabel.text = getString(R.string.blinking_speed, 0)
        if (flashOnAtStartUpEnable) {
            turnFlash(true)
        }

        binding.settingBtn.setOnClickListener {
            val dialogBinding = BottomsheetDialogBinding.inflate(layoutInflater)
            val bottomSheetDialog = BottomSheetDialog(this)
            bottomSheetDialog.setContentView(dialogBinding.root)
            dialogBinding.settingText.setOnClickListener {
                settingFragment.show()
                bottomSheetDialog.dismiss()
            }
            dialogBinding.moreApps.setOnClickListener {
                moreApp(this)
                bottomSheetDialog.dismiss()
            }
            dialogBinding.shareApp.setOnClickListener {
                shareApp(this)
                bottomSheetDialog.dismiss()
            }
            dialogBinding.appRating.setOnClickListener {
                appRating(this)
                bottomSheetDialog.dismiss()
            }
            dialogBinding.feedback.setOnClickListener {
                sendFeedbackMail(this)
                bottomSheetDialog.dismiss()
            }
            bottomSheetDialog.show()
        }
        binding.screenFlashlight.setOnClickListener {
            //binding.lightSlider.valueTo=100.0f
            binding.blinkingLabel.text = getString(R.string.brightness_level, 0)
            binding.lightSlider.value = 0f
            binding.screenFlashlight.animation = myAnim
            it.startAnimation(myAnim)
            screenClick()
        }
        binding.phoneBtn.setOnClickListener {
            it.startAnimation(myAnim)
            checkPhoneStatePermission()
        }
        binding.sosBtn.setOnClickListener {
            it.startAnimation(myAnim)
            checkPermission()
            if (isSoundEnable) {
                playSound(this)
            }
            if (isHapticFeedBackEnable) {
                hapticFeedback(binding.sosBtn)
            }
        }
        binding.torchBtn.setOnClickListener {
            if (bigFlashAsSwitchEnable) {
                it.startAnimation(myAnim)
                startFlash()
            }
        }
        binding.playBtn.setOnClickListener {
            //binding.lightSlider.valueTo=90.0f
            binding.mainLayout.setBackgroundColor(ContextCompat.getColor(this, R.color.blue))
            binding.torchBtn.visibility = View.VISIBLE
            binding.blinkingLabel.text = getString(R.string.blinking_speed, 0)
            binding.lightSlider.value = 0f
            checkLight = true
            val layout = window.attributes
            layout.screenBrightness = -1.0f
            window.attributes = layout
            binding.screenFlashlight.setImageResource(R.drawable.ic_device)
            it.startAnimation(myAnim)
            startFlash()
        }

        binding.lightSlider.addOnSliderTouchListener(
            object : Slider.OnSliderTouchListener {
                override fun onStartTrackingTouch(slider: Slider) {
                }

                override fun onStopTrackingTouch(slider: Slider) {
                    if (isHapticFeedBackEnable) {
                        hapticFeedback(binding.lightSlider)
                    }
                    if (isSoundEnable) {
                        playSound(this@MainActivity)
                    }
                    if (checkLight) {
                        // slider.valueTo = 100.0f
                        binding.blinkingLabel.text =
                            getString(R.string.blinking_speed, slider.value.roundToInt() / 10)
                        if (isRunning) {
                            lifecycleScope.launch {
                                onOrOff = true
                                delay(500)
                                if (slider.value.roundToInt() > 0) {
                                    switchingFlash(slider.value.roundToInt() / 10)
                                } else if (slider.value.roundToInt() == 0) {
                                    turnFlash(true)
                                }
                            }
                        } else {
                            onOrOff = false
                            if (slider.value.roundToInt() > 0) {
                                switchingFlash(slider.value.roundToInt() / 10)
                            } else if (slider.value.roundToInt() == 0) {
                                turnFlash(true)
                            }
                        }
                    } else {
                        //slider.valueTo = 100.0f
                        binding.blinkingLabel.text =
                            getString(R.string.brightness_level, slider.value.toInt())
                        screenLight(true, slider.value / 100)
                        binding.screenFlashlight.setImageResource(R.drawable.ic_device_on)
                    }
                }
            })

        binding.lightSlider.setLabelFormatter { value: Float ->
            val format = NumberFormat.getInstance()
            format.maximumFractionDigits = 0
            //format.currency = Currency.getInstance("USD")
            format.format(value.toDouble() / 10)
        }

        //Settings methods

        settingBinding.sensitivity.addOnChangeListener(Slider.OnChangeListener { _, value, _ ->
            SHAKE_THRESHOLD = value
            settingBinding.txtSensitivity.text = getString(R.string.sensitivity, SHAKE_THRESHOLD)
            if (isHapticFeedBackEnable) {
                hapticFeedback(settingBinding.sensitivity)
            }
            if (isSoundEnable) {
                playSound(this)
            }
        })

        settingBinding.hapticFeedback.setOnCheckedChangeListener { _, isChecked ->
            if (!isChecked) {
                settingBinding.hapticFeedback.isChecked = false
                settingBinding.hapticFeedback.text = getString(R.string.haptic_feedback_disable)
                isHapticFeedBackEnable = false
            } else {
                settingBinding.hapticFeedback.isChecked = true
                settingBinding.hapticFeedback.text = getString(R.string.haptic_feedback_enable)
                isHapticFeedBackEnable = true
            }
        }

        settingBinding.stopNotification.setOnCheckedChangeListener { _, isChecked ->

            if (!isChecked) {
                if (service != null) {
                    service!!.stopForeground(true)
                    service!!.stopSelf()
                }
                settingBinding.stopNotification.isChecked = false
                settingBinding.stopNotification.text = getString(R.string.notification_disable)
                isNotificationEnable = false
            } else {
                if (service != null) {
                    val intent = Intent(this, CallService::class.java)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        this.startForegroundService(intent)
                    } else
                        this.startService(intent)
                    service!!.showNotification(flashState)
                }
                settingBinding.stopNotification.isChecked = true
                settingBinding.stopNotification.text = getString(R.string.notification_enable)
                isNotificationEnable = true
            }
        }
        settingBinding.soundFeedback.setOnCheckedChangeListener { _, isChecked ->
            if (!isChecked) {
                settingBinding.soundFeedback.isChecked = false
                settingBinding.soundFeedback.text = getString(R.string.touch_sound_disable)
                isSoundEnable = false
            } else {
                settingBinding.soundFeedback.isChecked = true
                settingBinding.soundFeedback.text = getString(R.string.touch_sound_enable)
                isSoundEnable = true
            }
        }
        settingBinding.startFlash.setOnCheckedChangeListener { _, isChecked ->
            if (!isChecked) {
                settingBinding.startFlash.isChecked = false
                settingBinding.startFlash.text = getString(R.string.flash_off_at_start)
                flashOnAtStartUpEnable = false
            } else {
                settingBinding.startFlash.isChecked = true
                settingBinding.startFlash.text = getString(R.string.flash_on_at_start)
                flashOnAtStartUpEnable = true
            }
        }
        settingBinding.bigFlashBtn.setOnCheckedChangeListener { _, isChecked ->
            if (!isChecked) {
                settingBinding.bigFlashBtn.isChecked = false
                settingBinding.bigFlashBtn.text = getString(R.string.big_flash_disable)
                bigFlashAsSwitchEnable = false
            } else {
                settingBinding.bigFlashBtn.isChecked = true
                settingBinding.bigFlashBtn.text = getString(R.string.big_flash_enable)
                bigFlashAsSwitchEnable = true
            }
        }
        settingBinding.shakeToLight.setOnCheckedChangeListener { _, isChecked ->
            if (!isChecked) {
                settingBinding.shakeToLight.isChecked = false
                settingBinding.shakeToLight.text = getString(R.string.shake_to_light_disable)
                shakeToLightEnable = false
            } else {
                settingBinding.shakeToLight.isChecked = true
                settingBinding.shakeToLight.text = getString(R.string.shake_to_light_enable)
                shakeToLightEnable = true
            }
        }

        settingBinding.addBtn.setOnClickListener {
            val preference = getSharedPreferences(SETTING_DATA, MODE_PRIVATE)
            val sosNo = preference.getString(SOS_NUMBER, null)

            if (!settingBinding.sosNumber.text.isNullOrEmpty() &&
                settingBinding.sosNumber.text.toString().length == 10
            ) {
                saveSetting()
                if (sosNo != settingBinding.sosNumber.text.toString()) {
                    notifyUser(this, "Contact is successfully added")
                    checkBothPermissions()
                    binding.sosBtn.setImageResource(R.drawable.ic_sos)
                }
                settingFragment.dismiss()

            } else
                notifyUser(this, "Please enter SOS Number!")
        }

        settingBinding.cancelBtn.setOnClickListener {
            settingFragment.dismiss()
        }

    }

    //permissions check methods
    private fun checkBothPermissions() {
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.CALL_PHONE
            ) == PackageManager.PERMISSION_GRANTED
            || ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.READ_PHONE_STATE
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            binding.phoneBtn.setImageResource(R.drawable.ic_call)
            val preference = getSharedPreferences(SETTING_DATA, MODE_PRIVATE)
            val sosNo = preference.getString(SOS_NUMBER, null)
            if (sosNo != null) {
                binding.sosBtn.setImageResource(R.drawable.ic_sos)
            } else {
                binding.sosBtn.setImageResource(R.drawable.ic_sos_off)
            }
        }
    }

    private fun showRateApp() {
        val request = reviewManager.requestReviewFlow()
        request.addOnCompleteListener { task ->
            if (task.isSuccessful) {
                val reviewInfo = task.result
                val flow = reviewManager.launchReviewFlow(this, reviewInfo)
                flow.addOnCompleteListener { task1 ->
                    //nothing to do It will handle by library
                    // The flow has finished. The API does not indicate whether the user
                    // reviewed or not, or even whether the review dialog was shown. Thus, no
                    // matter the result, we continue our app flow.
                }
            } else {
                //just exit from the app
            }
        }
    }

    private fun checkPhoneStatePermission() {
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.READ_PHONE_STATE
            ) == PackageManager.PERMISSION_DENIED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.READ_PHONE_STATE),
                READ_PHONE_STATE_REQUEST_CODE
            )
        } else {
            notifyUser(this, getString(R.string.blinking_toast))
            binding.phoneBtn.setImageResource(R.drawable.ic_call)
        }
    }

    private fun checkCameraPermission() {
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_DENIED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.CAMERA),
                CAMERA_REQUEST_CODE
            )
        } else {
            //dff
            notifyUser(this, "Please give camera permission for flashlight")
        }
    }

    private fun checkPermission() {
        // Checking if permission is not granted
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.CALL_PHONE
            ) == PackageManager.PERMISSION_DENIED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.CALL_PHONE),
                PHONE_REQUEST_CODE
            )
        } else {
            phoneCall()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PHONE_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                phoneCall()
            } else {
                notifyUser(this, getString(R.string.phone_call_denied_toast))
            }
        }
        if (requestCode == READ_PHONE_STATE_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                binding.phoneBtn.setImageResource(R.drawable.ic_call)
                notifyUser(this, getString(R.string.blinking_toast))
            } else {
                notifyUser(this, getString(R.string.phone_denied_toast))
            }
        }
        if (requestCode == CAMERA_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                notifyUser(this, getString(R.string.flash_camera_toast))
            } else {
                notifyUser(this, getString(R.string.camera_denied_toast))
            }
        }
    }

    //screen light methods
    private fun screenLight(screenON: Boolean, screenLight: Float) {
        val layout = window.attributes
        if (screenON) {
            screenState = true
        } else
            screenState = false
        layout.screenBrightness = screenLight
        window.attributes = layout
    }

    //flashlight methods
    private fun switchingFlash(noOfTimes: Int) {
        isRunning = true
        onOrOff = false
        var flashOn = true
        binding.playBtn.setImageResource(R.drawable.ic_flashlight_on)
        turnFlash(flashOn)

        job = lifecycleScope.launch {
            for (count in 0 until 10000) {
                val delayTime = (1000 / noOfTimes).toDouble()
                flashOn = !flashOn
                delay(delayTime.toLong())
                turnFlash(flashOn)
                //check whether user pause the timer or not
                if (onOrOff) {
                    turnFlash(false)
                    onOrOff = false
                    break
                }
            }
        }
    }

    private fun turnFlash(isCheck: Boolean) {
        if (flashlightExist && service != null) {
            if (isNotificationEnable)
                service!!.showNotification(isCheck)
        }
        Util.torchSwitch(this, isCheck, binding.torchBtn, binding.playBtn)
        flashState = isCheck
    }

    private fun runFlashlight() {
        if (!flashState) {
            turnFlash(true)
        } else {
            turnFlash(false)
        }

    }

    private fun startFlash() {
        if (isSoundEnable) {
            playSound(this)
        }
        if (isHapticFeedBackEnable) {
            hapticFeedback(binding.playBtn)
        }
        runFlashlight()
        if (isRunning) {
            if (onOrOff) {
                onOrOff = false
            } else {
                onOrOff = true
                binding.playBtn.setImageResource(R.drawable.ic_flashlight_off)
            }
            isRunning = false
            job?.cancel()
            turnFlash(false)
            binding.playBtn.setImageResource(R.drawable.ic_flashlight_off)
        }
    }

    private fun checkSwitch() {
        //check haptic feedback is ON or OFF and set haptic feedback option item according to this value
        val preference = getSharedPreferences(SETTING_DATA, MODE_PRIVATE)
        isNotificationEnable = preference.getBoolean(SHOW_NOTIFICATION, true)
        isHapticFeedBackEnable = preference.getBoolean(HAPTIC_FEEDBACK, true)
        isSoundEnable = preference.getBoolean(TOUCH_SOUND, false)
        flashOnAtStartUpEnable = preference.getBoolean(FLASH_ON_START, false)
        bigFlashAsSwitchEnable = preference.getBoolean(BIG_FLASH_AS_SWITCH, false)
        shakeToLightEnable = preference.getBoolean(SHAKE_TO_LIGHT, true)
        SHAKE_THRESHOLD = preference.getFloat(SHAKE_SENSITIVITY, 3.5f)
        val sosNo = preference.getString(SOS_NUMBER, null)
        settingBinding.sosNumber.setText(sosNo)

        if (isNotificationEnable) {
            settingBinding.stopNotification.isChecked = true
            settingBinding.stopNotification.text = getString(R.string.notification_enable)
        } else {
            settingBinding.stopNotification.isChecked = false
            settingBinding.stopNotification.text = getString(R.string.notification_disable)
        }
        if (isHapticFeedBackEnable) {
            settingBinding.hapticFeedback.isChecked = true
            settingBinding.hapticFeedback.text = getString(R.string.haptic_feedback_enable)
        } else {
            settingBinding.hapticFeedback.isChecked = false
            settingBinding.hapticFeedback.text = getString(R.string.haptic_feedback_disable)
        }
        if (isSoundEnable) {
            settingBinding.soundFeedback.isChecked = true
            settingBinding.soundFeedback.text = getString(R.string.touch_sound_enable)
        } else {
            settingBinding.soundFeedback.isChecked = false
            settingBinding.soundFeedback.text = getString(R.string.touch_sound_disable)
        }
        if (flashOnAtStartUpEnable) {
            settingBinding.startFlash.isChecked = true
            settingBinding.startFlash.text = getString(R.string.flash_on_at_start)
        } else {
            settingBinding.startFlash.isChecked = false
            settingBinding.startFlash.text = getString(R.string.flash_off_at_start)
        }
        if (bigFlashAsSwitchEnable) {
            settingBinding.bigFlashBtn.isChecked = true
            settingBinding.bigFlashBtn.text = getString(R.string.big_flash_enable)
        } else {
            settingBinding.bigFlashBtn.isChecked = false
            settingBinding.bigFlashBtn.text = getString(R.string.big_flash_disable)
        }
        if (shakeToLightEnable) {
            settingBinding.shakeToLight.isChecked = true
            settingBinding.shakeToLight.text = getString(R.string.shake_to_light_enable)
        } else {
            settingBinding.shakeToLight.isChecked = false
            settingBinding.shakeToLight.text = getString(R.string.shake_to_light_disable)
        }
        settingBinding.txtSensitivity.text = getString(R.string.sensitivity, SHAKE_THRESHOLD)
        settingBinding.sensitivity.value = SHAKE_THRESHOLD
    }

    private fun phoneCall() {
        val pref = getSharedPreferences(SETTING_DATA, MODE_PRIVATE)
        val phNo1 = pref.getString(SOS_NUMBER, null)
        if (phNo1.isNullOrEmpty()) {
            notifyUser(this, getString(R.string.sos_toast))
        } else {
            binding.sosBtn.setImageResource(R.drawable.ic_sos)
            switchingFlash(10)
            val callIntent = Intent(Intent.ACTION_CALL)
            callIntent.data = Uri.parse("tel: $phNo1")
            startActivity(callIntent)
        }
    }

    //save app settings
    private fun saveSetting() {
        val editor = getSharedPreferences(SETTING_DATA, MODE_PRIVATE).edit()
        editor.putBoolean(HAPTIC_FEEDBACK, isHapticFeedBackEnable)
        editor.putBoolean(SHOW_NOTIFICATION, isNotificationEnable)
        editor.putBoolean(TOUCH_SOUND, isSoundEnable)
        editor.putBoolean(FLASH_ON_START, flashOnAtStartUpEnable)
        editor.putBoolean(BIG_FLASH_AS_SWITCH, bigFlashAsSwitchEnable)
        editor.putBoolean(SHAKE_TO_LIGHT, shakeToLightEnable)
        editor.putString(SOS_NUMBER, settingBinding.sosNumber.text.toString())
        editor.putFloat(SHAKE_SENSITIVITY, SHAKE_THRESHOLD)
        editor.apply()
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event != null) {
            if (event.sensor.type == Sensor.TYPE_ACCELEROMETER) {
                val curTime = System.currentTimeMillis()
                if ((curTime - mLastShakeTime) > MIN_TIME_BETWEEN_SHAKES_MILLIsECS) {
                    val x = event.values[0]
                    val y = event.values[1]
                    val z = event.values[2]
                    val acceleration =
                        sqrt(x.pow(2) + y.pow(2) + z.pow(2)) - SensorManager.GRAVITY_EARTH
                    if (shakeToLightEnable) {
                        if (acceleration > SHAKE_THRESHOLD) {
                            mLastShakeTime = curTime
                            if (flashState)
                                turnFlash(false)
                            else
                                turnFlash(true)
                        }
                    }
                }
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
    }

    //callback method
    override fun onTorchClick(flashON: Boolean) {
        // screenState =screenON
        turnFlash(flashON)
    }

    private fun screenClick() {
        checkLight = false
        if (!screenState) {
            screenLight(true, 0.5f)
            binding.screenFlashlight.setImageResource(R.drawable.ic_device_on)
            binding.mainLayout.setBackgroundColor(ContextCompat.getColor(this, R.color.white))
            binding.torchBtn.visibility = View.INVISIBLE
        } else {
            screenLight(false, -1.0f)
            binding.screenFlashlight.setImageResource(R.drawable.ic_device)
            binding.mainLayout.setBackgroundColor(ContextCompat.getColor(this, R.color.blue))
            binding.torchBtn.visibility = View.VISIBLE
        }
        if (isSoundEnable) {
            playSound(this)
        }
        if (isHapticFeedBackEnable) {
            hapticFeedback(binding.sosBtn)
        }
        binding.playBtn.setImageResource(R.drawable.ic_flashlight_off)
        flashState = false
        turnFlash(flashState)
    }

    override fun onServiceConnected(name: ComponentName, iBinder: IBinder) {
        val binder = iBinder as CallService.MyBinder
        if (flashlightExist) {
            if (isNotificationEnable) {
                val intent = Intent(this, CallService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    this.startForegroundService(intent)
                } else
                    this.startService(intent)
            }
            service = binder.service
            service!!.setCallBack(this)
        }
    }

    override fun onServiceDisconnected(name: ComponentName) {
        if (flashlightExist)
            service = null
    }

    override fun onBackPressed() {
        super.onBackPressed()
        showRateApp()
    }

    override fun onDestroy() {
        super.onDestroy()
        sensorManager.unregisterListener(this)
    }
}
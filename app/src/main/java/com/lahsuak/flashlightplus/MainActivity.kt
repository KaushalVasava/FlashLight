package com.lahsuak.flashlightplus

import android.Manifest
import android.content.*
import android.content.pm.PackageManager
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraManager
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.*
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.slider.Slider
import com.lahsuak.flashlightplus.util.*
import com.lahsuak.flashlightplus.util.BIG_FLASH_AS_SWITCH
import com.lahsuak.flashlightplus.util.FLASH_ON_START
import com.lahsuak.flashlightplus.util.HAPTIC_FEEDBACK
import com.lahsuak.flashlightplus.util.SETTING_DATA
import com.lahsuak.flashlightplus.util.SOS_NUMBER
import com.lahsuak.flashlightplus.util.TOUCH_SOUND
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import java.text.NumberFormat
import kotlin.math.roundToInt
import android.graphics.SurfaceTexture
import android.hardware.*
import android.util.Log
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import java.io.IOException
import kotlin.math.pow
import kotlin.math.sqrt

import android.content.Intent
import com.lahsuak.flashlightplus.databinding.FragmentSettingBinding
import android.widget.Toast

import android.view.animation.AnimationUtils
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.lahsuak.flashlightplus.databinding.ActivityMainBinding

private const val TAG = "MainActivity"

class MainActivity : AppCompatActivity(), SensorEventListener{
    private lateinit var binding: ActivityMainBinding
    private lateinit var settingBinding: FragmentSettingBinding
    private var SHAKE_THRESOLD = 3.25f
    private val MIN_TIME_BETWEEN_SHAKES_MILLIsECS = 1000
    private var mLastShakeTime: Long = 0
    private lateinit var sensorManager: SensorManager

    private lateinit var bottomSheetDialog: BottomSheetDialog

    //extra
    private var job: Job? = null
    private var state = false
    private var onOrOff = false
    private var isRunning = false
    private var isHapticFeedBackEnable = true
    private var isSoundEnable = false
    private var flashOnAtStartUpEnable = false
    private var bigFlashAsSwitchEnable = false
    private var shakeToLightEnable = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val myAnim = AnimationUtils.loadAnimation(this, R.anim.fade_out)
        binding.playBtn.animation = myAnim
        binding.sosBtn.animation = myAnim
        binding.phoneBtn.animation = myAnim
        binding.torchBtn.animation = myAnim

        //this is for SOS settings
        settingBinding = FragmentSettingBinding.inflate(layoutInflater)

        bottomSheetDialog = BottomSheetDialog(this)
        bottomSheetDialog.setContentView(settingBinding.root)
        bottomSheetDialog.behavior.state = BottomSheetBehavior.STATE_EXPANDED

        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager

        //Shake to turn ON/OFF flashlight
        val sensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        if (sensor != null) {
            sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_NORMAL)
        }

        //check permissions
        checkBothPermissions()
        if(Build.VERSION.SDK_INT<Build.VERSION_CODES.M)
            checkCameraPermission()

        //Flash light fragment methods
        //flashlight blinking time
        checkSwitch()

        binding.blinkingLabel.text = getString(R.string.blinking_speed,0)
        if (flashOnAtStartUpEnable) {
            turnFlash(true)
        }

        binding.phoneBtn.setOnClickListener {
            it.startAnimation(myAnim)
            checkPhoneStatePermission()
        }
        binding.sosBtn.setOnClickListener {
            it.startAnimation(myAnim)
            checkPermission()
            if (isSoundEnable) {
                playSound()
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
            it.startAnimation(myAnim)
            startFlash()
        }

        binding.lightSlider.addOnSliderTouchListener(
            object : Slider.OnSliderTouchListener {
                override fun onStartTrackingTouch(slider: Slider) {
                }

                override fun onStopTrackingTouch(slider: Slider) {
                    binding.blinkingLabel.text = getString(R.string.blinking_speed,slider.value.roundToInt()/10)
                    if (isHapticFeedBackEnable) {
                        hapticFeedback(binding.lightSlider)
                    }
                    if (isSoundEnable) {
                        playSound()
                    }
                    if (isRunning) {
                        lifecycleScope.launch {
                            onOrOff = true
                            delay(500)
                            if (slider.value.roundToInt() > 0) {
                                switchingFlash(slider.value.roundToInt()/10)
                            } else if (slider.value.roundToInt() == 0) {
                                turnFlash(true)
                            }
                        }
                    } else {
                        onOrOff = false
                        if (slider.value.roundToInt() > 0) {
                            switchingFlash(slider.value.roundToInt()/10)
                        } else if (slider.value.roundToInt() == 0) {
                            turnFlash(true)
                        }
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
            SHAKE_THRESOLD = value
            settingBinding.txtSensitivity.text = getString(R.string.sensitivity,SHAKE_THRESOLD)
            if (isHapticFeedBackEnable) {
                hapticFeedback(settingBinding.sensitivity)
            }
            if (isSoundEnable) {
                playSound()
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
                bottomSheetDialog.dismiss()

            } else
                notifyUser(this, "Please enter SOS Number!")
        }
        settingBinding.cancelBtn.setOnClickListener {
            bottomSheetDialog.dismiss()
        }

    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.app_menu, menu)
        menu?.getItem(4)?.title = "Current Version ${BuildConfig.VERSION_NAME}"
        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.app_manager -> {
                bottomSheetDialog.show()
            }
            R.id.more_apps -> {
                moreApp()
            }
            R.id.share_app -> {
                shareApp()
            }
            R.id.feedback -> {
                sendFeedbackMail()
            }
        }
        return super.onOptionsItemSelected(item)
    }

    private fun switchingFlash(noOfTimes: Int) {
        isRunning = true
        onOrOff = false
        var flashOn = true
        binding.playBtn.setImageResource(R.drawable.ic_pause)
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
        val isFlashAvailableOnDevice =
            packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_FLASH)
        if (!isFlashAvailableOnDevice) {
            notifyUser(this, "Your device doesn't support flash light")
        } else {
            val cameraManager = getSystemService(CAMERA_SERVICE) as CameraManager
            try {
                val cameraId = cameraManager.cameraIdList[0]
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    cameraManager.setTorchMode(cameraId, isCheck)
                } else if(Build.VERSION.SDK_INT < Build.VERSION_CODES.M){

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
                state = isCheck
                if (isCheck) {
                    binding.torchBtn.setImageResource(R.drawable.light_bulb)
                    // if (!isRunning)
                    binding.playBtn.setImageResource(R.drawable.ic_pause)
                } else {
                    binding.torchBtn.setImageResource(R.drawable.light_bulb_off)
                    // if (!isRunning)
                    binding.playBtn.setImageResource(R.drawable.ic_play)
                }
            } catch (e: CameraAccessException) {
            }
        }
    }

    private fun runFlashlight() {
        if (!state) {
            turnFlash(true)
        } else {
            turnFlash(false)
        }

    }

    private fun startFlash() {
        if (isSoundEnable) {
            playSound()
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
                binding.playBtn.setImageResource(R.drawable.ic_play)
            }
            isRunning = false
            job?.cancel()
            turnFlash(false)
            binding.playBtn.setImageResource(R.drawable.ic_play)
        }
    }

    private fun checkSwitch() {
        //check haptic feedback is ON or OFF and set haptic feedback option item according to this value
        val preference = getSharedPreferences(SETTING_DATA, MODE_PRIVATE)
        isHapticFeedBackEnable = preference.getBoolean(HAPTIC_FEEDBACK, true)
        isSoundEnable = preference.getBoolean(TOUCH_SOUND, false)
        flashOnAtStartUpEnable = preference.getBoolean(FLASH_ON_START, false)
        bigFlashAsSwitchEnable = preference.getBoolean(BIG_FLASH_AS_SWITCH, false)
        shakeToLightEnable = preference.getBoolean(SHAKE_TO_LIGHT, true)
        SHAKE_THRESOLD = preference.getFloat(SHAKE_SENSITIVITY,3.25f)
        val sosNo = preference.getString(SOS_NUMBER, null)
        settingBinding.sosNumber.setText(sosNo)

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
        settingBinding.txtSensitivity.text = getString(R.string.sensitivity,SHAKE_THRESOLD)
        settingBinding.sensitivity.value = SHAKE_THRESOLD
    }

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
            }else{
                binding.sosBtn.setImageResource(R.drawable.ic_sos_off)
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
            notifyUser(this,getString(R.string.blinking_taost))
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
                arrayOf(Manifest.permission.READ_PHONE_STATE),
                CAMERA_REQUEST_CODE
            )
        } else {
          //dff
            Log.d(TAG, "checkCameraPermission: allowed")
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
                notifyUser(this, getString(R.string.blinking_taost))
            } else {
                notifyUser(this, getString(R.string.phone_denied_toast))
            }
        }
        if(requestCode== CAMERA_REQUEST_CODE){
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                notifyUser(this, getString(R.string.flash_camera_toast))
            } else {
                notifyUser(this, getString(R.string.camera_denied_toast))
            }
        }
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

    private fun playSound() {
        val mediaPlayer = MediaPlayer.create(this, R.raw.click_sound)
        mediaPlayer.start()
    }

    private fun hapticFeedback(view: View) {
        view.performHapticFeedback(
            HapticFeedbackConstants.VIRTUAL_KEY,
            HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING // Ignore device's setting. Otherwise, you can use FLAG_IGNORE_VIEW_SETTING to ignore view's setting.
        )
    }

    private fun moreApp() {
        try {
            startActivity(
                Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse(getString(R.string.market_string))
                )
            )
        } catch (e: ActivityNotFoundException) {
            startActivity(
                Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse(getString(R.string.market_developer_string))
                )
            )
        }
    }

    private fun shareApp() {
        try {
            val intent = Intent(Intent.ACTION_SEND)
            intent.type = "text/plain"
            intent.putExtra(Intent.EXTRA_SUBJECT, "Share this App")
            val shareMsg =
                getString(R.string.play_store_share) + BuildConfig.APPLICATION_ID + "\n\n"
            intent.putExtra(Intent.EXTRA_TEXT, shareMsg)
            startActivity(Intent.createChooser(intent, "Share by"))
        } catch (e: Exception) {
            notifyUser(
                this,
                "Some thing went wrong!!"
            )
        }
    }

    private fun sendFeedbackMail() {
        try {
            val emailIntent = Intent(Intent.ACTION_SENDTO).apply {
                data = Uri.parse("mailto:")
                putExtra(Intent.EXTRA_EMAIL, arrayOf(getString(R.string.feedback_email)))
                putExtra(Intent.EXTRA_TEXT, "Please write your suggestions or issues")
                putExtra(Intent.EXTRA_SUBJECT, "Feedback From FlashLight")
            }
            startActivity(emailIntent)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    //save app settings
    private fun saveSetting() {
        val editor = getSharedPreferences(SETTING_DATA, MODE_PRIVATE).edit()
        editor.putBoolean(HAPTIC_FEEDBACK, isHapticFeedBackEnable)
        editor.putBoolean(TOUCH_SOUND, isSoundEnable)
        editor.putBoolean(FLASH_ON_START, flashOnAtStartUpEnable)
        editor.putBoolean(BIG_FLASH_AS_SWITCH, bigFlashAsSwitchEnable)
        editor.putBoolean(SHAKE_TO_LIGHT, shakeToLightEnable)
        editor.putString(SOS_NUMBER, settingBinding.sosNumber.text.toString())
        editor.putFloat(SHAKE_SENSITIVITY,SHAKE_THRESOLD)
        editor.apply()
    }

    private fun notifyUser(context: Context, message: String) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
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
                        if (acceleration > SHAKE_THRESOLD) {
                            mLastShakeTime = curTime
                            if (state)
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

    override fun onDestroy() {
        super.onDestroy()
        sensorManager.unregisterListener(this)
    }

}
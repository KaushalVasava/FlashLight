package com.lahsuak.flashlightplus.ui.fragments

import android.Manifest
import android.app.Activity
import android.content.ComponentName
import android.content.Context.*
import android.content.Intent
import android.content.IntentSender
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.graphics.Color
import android.hardware.*
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AnimationUtils
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.preference.PreferenceManager
import com.flask.colorpicker.ColorPickerView
import com.flask.colorpicker.builder.ColorPickerDialogBuilder
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.slider.Slider
import com.google.android.material.snackbar.Snackbar
import com.google.android.play.core.appupdate.AppUpdateManager
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.install.InstallStateUpdatedListener
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.InstallStatus
import com.google.android.play.core.install.model.UpdateAvailability
import com.lahsuak.flashlightplus.R
import com.lahsuak.flashlightplus.`interface`.LightListener
import com.lahsuak.flashlightplus.databinding.FragmentHomeBinding
import com.lahsuak.flashlightplus.databinding.SosDialogBinding
import com.lahsuak.flashlightplus.service.CallService
import com.lahsuak.flashlightplus.util.FlashLightApp.Companion.flashlightExist
import com.lahsuak.flashlightplus.util.AppConstants.BIG_FLASH_AS_SWITCH
import com.lahsuak.flashlightplus.util.AppConstants.CALL_NOTIFICATION
import com.lahsuak.flashlightplus.util.AppConstants.FLASH_EXIST
import com.lahsuak.flashlightplus.util.AppConstants.FLASH_ON_START
import com.lahsuak.flashlightplus.util.AppConstants.HAPTIC_FEEDBACK
import com.lahsuak.flashlightplus.util.AppConstants.MIN_TIME_BETWEEN_SHAKES_MILLIsECS
import com.lahsuak.flashlightplus.util.AppConstants.SETTING_DATA
import com.lahsuak.flashlightplus.util.AppConstants.SHAKE_SENSITIVITY
import com.lahsuak.flashlightplus.util.AppConstants.SHAKE_TO_LIGHT
import com.lahsuak.flashlightplus.util.AppConstants.SHOW_NOTIFICATION
import com.lahsuak.flashlightplus.util.AppConstants.TEL
import com.lahsuak.flashlightplus.util.AppConstants.TOUCH_SOUND
import com.lahsuak.flashlightplus.util.AppConstants.UPDATE_REQUEST_CODE
import com.lahsuak.flashlightplus.util.PermissionUtil
import com.lahsuak.flashlightplus.util.SharedPrefConstants
import com.lahsuak.flashlightplus.util.SharedPrefConstants.SOS_NUMBER_KEY
import com.lahsuak.flashlightplus.util.Util.ShakeThreshold
import com.lahsuak.flashlightplus.util.Util.hapticFeedback
import com.lahsuak.flashlightplus.util.Util.playSound
import com.lahsuak.flashlightplus.util.toast
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.NumberFormat
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sqrt

class HomeFragment : Fragment(), SensorEventListener, ServiceConnection, LightListener {
    private lateinit var binding: FragmentHomeBinding
    private var mLastShakeTime: Long = 0

    private lateinit var sensorManager: SensorManager
    private var service: CallService? = null

    //extra
    private var job: Job? = null
    private var flashState = false
    var checkLight = true // true for flashlight and false for screen light
    private var onOrOff = false
    private var isRunning = false
    private var isNotificationEnable = true
    private var isCallNotificationEnable = true
    private var isHapticFeedBackEnable = true
    private var isSoundEnable = false
    private var flashOnAtStartUpEnable = false
    private var bigFlashAsSwitchEnable = false
    private var shakeToLightEnable = false
    private var appUpdateManager: AppUpdateManager? = null

    private val permissionResultLauncher: ActivityResultLauncher<Array<String>> =
        registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { permissions ->
            if (permissions.all { it.value }) {
                /* no-op */
            } else {
                context.toast {
                    getString(R.string.user_cancelled_the_operation)
                }
            }
        }

    private fun checkPermission() {
        if (Build.VERSION_CODES.TIRAMISU <= Build.VERSION.SDK_INT) {
            PermissionUtil.checkAndLaunchPermission(
                fragment = this,
                permissions = arrayOf(
                    Manifest.permission.POST_NOTIFICATIONS
                ),
                permissionLauncher = permissionResultLauncher,
                showRationaleUi = {
                    PermissionUtil.showSettingsSnackBar(
                        requireActivity(),
                        requireView(),
                    )
                },
                lazyBlock = {},
            )
        }
    }

    companion object {
        var isStartUpOn = false
        var sosNumber: String? = null
        var screenState = false // new for screen brightness
        private var sliderValue = 0f
        private var layoutColor = Color.WHITE
    }

    override fun onResume() {
        super.onResume()
        if (flashlightExist) {
            val intent = Intent(requireContext(), CallService::class.java)
            requireContext().bindService(intent, this, BIND_AUTO_CREATE)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        (activity as AppCompatActivity).supportActionBar!!.hide()
        return inflater.inflate(R.layout.fragment_home, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding = FragmentHomeBinding.bind(view)
        checkPermission()
        val pref = requireContext().getSharedPreferences(SETTING_DATA, MODE_PRIVATE)
        flashlightExist = pref.getBoolean(FLASH_EXIST, true)

        appUpdateManager = AppUpdateManagerFactory.create(requireContext())
        //checking update of application
        checkUpdate()
        appUpdateManager!!.registerListener(appUpdateListener)

        val prefNew = requireContext().getSharedPreferences(SETTING_DATA, MODE_PRIVATE)
        val firstTime = prefNew.getBoolean(SharedPrefConstants.FIRST_TIME_USE_KEY, false)
        if (!firstTime) {
            val builder = MaterialAlertDialogBuilder(requireContext())
                .setTitle(getString(R.string.allow_perm))
                .setMessage(getString(R.string.permission_desc))
                .setPositiveButton(getString(R.string.ok)) { dialog, _ ->
                    //checkBothPermissions()
                    bothPermission()
                    prefNew.edit().putBoolean(SharedPrefConstants.FIRST_TIME_USE_KEY, true).apply()
                    dialog.dismiss()
                }
            val dialogShow = builder.create()
            dialogShow.show()
        }
        val myAnim = AnimationUtils.loadAnimation(requireContext(), R.anim.fade_out)
        binding.playBtn.animation = myAnim
        binding.sosBtn.animation = myAnim
        //binding.phoneBtn.animation = myAnim
        binding.torchBtn.animation = myAnim

        sensorManager = requireContext().getSystemService(SENSOR_SERVICE) as SensorManager

        //Shake to turn ON/OFF flashlight
        val sensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        if (sensor != null) {
            sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_NORMAL)
        }

        //Flash light fragment methods
        getAllSettings()
        if (screenState) {
            binding.blinkingLabel.text =
                String.format(getString(R.string.brightness_level), sliderValue.toInt() / 10)
            binding.lightSlider.value = sliderValue
            screenLight(true, sliderValue / 100)
            binding.screenFlashlight.setImageResource(R.drawable.ic_device_on)
            binding.root.setBackgroundColor(layoutColor)

            binding.sosBtn.visibility = View.GONE
            binding.screenColor.setColorFilter(layoutColor)
            binding.screenColor.visibility = View.VISIBLE
            binding.torchBtn.visibility = View.INVISIBLE
        } else {
            binding.blinkingLabel.text =
                String.format(getString(R.string.blinking_speed), 0)
            binding.lightSlider.value = 0f
            screenState = false
            checkLight = true
        }
        if (flashOnAtStartUpEnable) {
            turnFlash(true)
            isStartUpOn = true
        }
        binding.screenFlashlight.setOnClickListener {
            binding.blinkingLabel.text = String.format(getString(R.string.brightness_level), 5)
            binding.lightSlider.value = 50f
            binding.screenFlashlight.animation = myAnim
            sliderValue = 50f
            it.startAnimation(myAnim)
            screenClick()
        }
        binding.screenColor.setOnClickListener {
            showColorDialog()
        }

        binding.sosBtn.setOnClickListener {
            if (job != null) {
                job!!.cancel()
                turnFlash(false)
            }
            it.startAnimation(myAnim)
            checkCallPermission()
            if (isSoundEnable) {
                playSound(requireContext())
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
        binding.settingBtn.setOnClickListener {
            val action = HomeFragmentDirections.actionHomeFragmentToSettingsFragment()
            findNavController().navigate(action)
        }

        binding.playBtn.setOnClickListener {
            binding.root.setBackgroundColor(
                ContextCompat.getColor(
                    requireContext(),
                    R.color.blue
                )
            )
            binding.screenColor.visibility = View.GONE
            binding.sosBtn.visibility = View.VISIBLE
            binding.torchBtn.visibility = View.VISIBLE
            binding.blinkingLabel.text =
                String.format(getString(R.string.blinking_speed), 0)
            binding.lightSlider.value = 0f
            checkLight = true
            val layout = requireActivity().window.attributes
            layout.screenBrightness = -1.0f
            requireActivity().window.attributes = layout
            binding.screenFlashlight.setImageResource(R.drawable.ic_device)
            it.startAnimation(myAnim)
            startFlash()
            if (!flashState)
                isStartUpOn = false
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
                        playSound(requireContext())
                    }
                    if (checkLight) {
                        binding.blinkingLabel.text =
                            String.format(
                                getString(R.string.blinking_speed), slider.value.roundToInt() / 10
                            )
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
                        binding.blinkingLabel.text =
                            String.format(
                                getString(R.string.brightness_level),
                                slider.value.toInt() / 10
                            )
                        screenLight(true, slider.value / 100)
                        binding.screenFlashlight.setImageResource(R.drawable.ic_device_on)
                        sliderValue = slider.value
                    }
                }
            })

        binding.lightSlider.setLabelFormatter { value: Float ->
            val format = NumberFormat.getInstance()
            format.maximumFractionDigits = 0
            format.format(value.toDouble() / 10)
        }
    }

    private val bothPermissionsResultCallback = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        var permissionGranted = false
        permissions.entries.forEach {
            val isGranted = it.value
            permissionGranted = isGranted
        }
        if (permissionGranted) {
            val preference = requireActivity().getSharedPreferences(SETTING_DATA, MODE_PRIVATE)
            val sosNo = preference.getString(SOS_NUMBER_KEY, null)
            if (sosNo != null) {
                binding.sosBtn.setImageResource(R.drawable.ic_sos)
            } else {
                binding.sosBtn.setImageResource(R.drawable.ic_sos_off)
            }
        }
    }
    private val callPermissionsResultCallback = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {
        when (it) {
            true -> {
                if (sosNumber == null)
                    showSOSDialog()
                else
                    binding.sosBtn.setImageResource(R.drawable.ic_sos)
                phoneCall()
            }

            false -> {
                Toast.makeText(
                    requireContext(),
                    getString(R.string.phone_call_denied_toast),
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun bothPermission() {
        val array = arrayOf(
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.CALL_PHONE
        )
        val permission = ContextCompat.checkSelfPermission(
            requireContext(),
            array.toString()
        )
        if (permission != PackageManager.PERMISSION_GRANTED) {
            bothPermissionsResultCallback.launch(array)
        }
    }

    private fun checkCallPermission() {
        val array = Manifest.permission.CALL_PHONE

        val permission = ContextCompat.checkSelfPermission(
            requireContext(),
            array
        )
        if (permission != PackageManager.PERMISSION_GRANTED) {
            callPermissionsResultCallback.launch(array)
        } else {
            if (sosNumber == null)
                showSOSDialog()
            else
                binding.sosBtn.setImageResource(R.drawable.ic_sos)
            phoneCall()
        }
    }

    private fun showSOSDialog() {
        val sosBinding = SosDialogBinding.inflate(layoutInflater)
        val builder = MaterialAlertDialogBuilder(requireContext())

        builder.setView(sosBinding.root)
            .setTitle(getString(R.string.sos_number))
            .setPositiveButton(getString(R.string.save)) { dialog, _ ->
                val preference = requireActivity().getSharedPreferences(SETTING_DATA, MODE_PRIVATE)
                sosNumber = preference.getString(SOS_NUMBER_KEY, null)

                if (!sosBinding.sosNumber.text.isNullOrEmpty() &&
                    sosBinding.sosNumber.text.toString().length == 10
                ) {
                    if (sosNumber != sosBinding.sosNumber.text.toString()) {
                        context.toast { getString(R.string.contact_is_successfully_added) }
                        bothPermission()
                        binding.sosBtn.setImageResource(R.drawable.ic_sos)
                        sosNumber = sosBinding.sosNumber.text.toString()
                    }
                    saveSetting()
                    dialog.dismiss()
                } else {
                    context.toast { getString(R.string.please_enter_sos_number) }
                }
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    //screen light methods
    private fun screenLight(screenON: Boolean, screenLight: Float) {
        val layout = requireActivity().window.attributes
        screenState = screenON
        layout.screenBrightness = screenLight

        requireActivity().window.attributes = layout
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

    private fun sosFlash() {
        isRunning = true
        onOrOff = false
        var flashOn = true
        binding.playBtn.setImageResource(R.drawable.ic_flashlight_on)
        turnFlash(flashOn)

        job = lifecycleScope.launch {
            for (count in 1 until 10000) {
                flashOn = !flashOn
                delay(300)
                if (count % 6 == 0) {
                    delay(2000)
                }
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
        if (service != null)
            service!!.torchSwitch(isCheck, binding.torchBtn, binding.playBtn)
        flashState = isCheck
    }

    private fun runFlashlight() {
        if (!flashState) {
            turnFlash(true)
        } else {
            turnFlash(false)
        }
    }

    private fun showColorDialog() {
        ColorPickerDialogBuilder
            .with(requireContext())
            .setTitle(getString(R.string.screen_colors))
            .initialColor(Color.WHITE)
            .wheelType(ColorPickerView.WHEEL_TYPE.FLOWER)
            .density(12)
            .setOnColorChangedListener { color ->
                binding.root.setBackgroundColor(color)
                binding.screenColor.setColorFilter(color)
                layoutColor = color
            }
            .setNegativeButton(
                getString(R.string.ok)
            ) { _, _ -> }
            .build()
            .show()
    }

    private fun startFlash() {
        if (isSoundEnable) {
            playSound(requireContext())
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

    private fun getAllSettings() {
        val prefSetting = PreferenceManager.getDefaultSharedPreferences(requireContext())
        ShakeThreshold =
            (prefSetting.getInt(SHAKE_SENSITIVITY, (ShakeThreshold * 10f).toInt()).toFloat() / 10f)
        isNotificationEnable = prefSetting.getBoolean(SHOW_NOTIFICATION, true)
        isHapticFeedBackEnable = prefSetting.getBoolean(HAPTIC_FEEDBACK, true)
        isSoundEnable = prefSetting.getBoolean(TOUCH_SOUND, false)
        isStartUpOn = prefSetting.getBoolean(FLASH_ON_START, false)
        bigFlashAsSwitchEnable = prefSetting.getBoolean(BIG_FLASH_AS_SWITCH, false)
        shakeToLightEnable = prefSetting.getBoolean(SHAKE_TO_LIGHT, true)
        isCallNotificationEnable = prefSetting.getBoolean(CALL_NOTIFICATION, true)

        val pref = requireContext().getSharedPreferences(SETTING_DATA, MODE_PRIVATE)
        sosNumber = pref.getString(SOS_NUMBER_KEY, null)

        if (!isNotificationEnable) {
            if (service != null) {
                service!!.stopForeground(true)
                service!!.stopSelf()
            }
        } else {
            if (service != null) {
                val intent = Intent(requireContext(), CallService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    requireActivity().startForegroundService(intent)
                } else
                    requireActivity().startService(intent)
                service!!.showNotification(flashState)
            }
        }
    }

    private fun phoneCall() {
        val pref = requireActivity().getSharedPreferences(SETTING_DATA, MODE_PRIVATE)
        val phNo1 = pref.getString(SOS_NUMBER_KEY, null)
        if (phNo1.isNullOrEmpty()) {
            context.toast {
                getString(R.string.sos_toast)
            }
        } else {
            binding.sosBtn.setImageResource(R.drawable.ic_sos)
            sosFlash()
            val prefManager = PreferenceManager.getDefaultSharedPreferences(requireContext())
            val allowed = prefManager.getBoolean(SharedPrefConstants.SOS_CALL_KEY, false)
            if (allowed) {
                val callIntent = Intent(Intent.ACTION_CALL)
                callIntent.data = Uri.parse(TEL + "$phNo1")
                startActivity(callIntent)
            }
        }
    }

    //save app settings
    private fun saveSetting() {
        val editor = requireActivity().getSharedPreferences(SETTING_DATA, MODE_PRIVATE).edit()
        editor.putString(SOS_NUMBER_KEY, sosNumber)
        editor.putFloat(SHAKE_SENSITIVITY, ShakeThreshold)
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
                        if (acceleration > ShakeThreshold) {
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
        turnFlash(flashON)
    }

    private fun screenClick() {
        checkLight = false
        if (!screenState) {
            screenLight(true, 0.5f)
            binding.screenFlashlight.setImageResource(R.drawable.ic_device_on)
            binding.root.setBackgroundColor(
                ContextCompat.getColor(
                    requireContext(),
                    R.color.white
                )
            )
            binding.sosBtn.visibility = View.GONE
            binding.screenColor.setColorFilter(Color.WHITE)
            binding.screenColor.visibility = View.VISIBLE
            binding.torchBtn.visibility = View.INVISIBLE
            layoutColor = Color.WHITE
        } else {
            screenLight(false, -1.0f)
            binding.screenFlashlight.setImageResource(R.drawable.ic_device)
            binding.root.setBackgroundColor(
                ContextCompat.getColor(
                    requireContext(),
                    R.color.blue
                )
            )
            binding.sosBtn.visibility = View.VISIBLE
            binding.screenColor.visibility = View.GONE
            binding.torchBtn.visibility = View.VISIBLE
        }
        if (isSoundEnable) {
            playSound(requireContext())
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
                val intent = Intent(requireContext(), CallService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    requireContext().startForegroundService(intent)
                } else
                    requireContext().startService(intent)
            }
            service = binder.service
            service!!.setCallBack(this)
            if (isStartUpOn) {
                turnFlash(true)
            }
        }
    }

    override fun onServiceDisconnected(name: ComponentName) {
        if (flashlightExist)
            service = null
    }

    private fun checkUpdate() {
        val appUpdateInfoTask = appUpdateManager!!.appUpdateInfo

        appUpdateInfoTask.addOnSuccessListener { appUpdateInfo ->
            if (appUpdateInfo.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE
                && appUpdateInfo.isUpdateTypeAllowed(AppUpdateType.FLEXIBLE)
            ) {
                try {
                    appUpdateManager!!.startUpdateFlowForResult(
                        appUpdateInfo, AppUpdateType.FLEXIBLE,
                        requireActivity(), UPDATE_REQUEST_CODE
                    )
                } catch (exception: IntentSender.SendIntentException) {
                    context.toast { exception.message.toString() }
                }
            }
        }
    }

    private val appUpdateListener = InstallStateUpdatedListener { state ->
        if (state.installStatus() == InstallStatus.DOWNLOADED) {
            Snackbar.make(
                requireView(),
                getString(R.string.new_app_is_ready),
                Snackbar.LENGTH_INDEFINITE
            )
                .setAction(getString(R.string.restart)) {
                    appUpdateManager?.completeUpdate()
                }.show()
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        @Suppress("deprecation")
        super.onActivityResult(requestCode, resultCode, data)
        if (data == null) return
        if (requestCode == UPDATE_REQUEST_CODE) {
            context.toast {
                getString(R.string.downloading_start)
            }
            if (resultCode != Activity.RESULT_OK) {
                context.toast { getString(R.string.update_failed) }
            }
        }
    }
}
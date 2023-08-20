package com.lahsuak.apps.flashlight.ui.fragments

import android.Manifest
import android.app.Service
import android.content.ComponentName
import android.content.Context.BIND_AUTO_CREATE
import android.content.Context.MODE_PRIVATE
import android.content.Intent
import android.content.ServiceConnection
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.Color
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.preference.PreferenceManager
import com.flask.colorpicker.ColorPickerView
import com.flask.colorpicker.builder.ColorPickerDialogBuilder
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.slider.Slider
import com.lahsuak.apps.flashlight.R
import com.lahsuak.apps.flashlight.databinding.FragmentHomeBinding
import com.lahsuak.apps.flashlight.databinding.SosDialogBinding
import com.lahsuak.apps.flashlight.`interface`.LightListener
import com.lahsuak.apps.flashlight.service.CallService
import com.lahsuak.apps.flashlight.util.AppConstants.BIG_FLASH_AS_SWITCH
import com.lahsuak.apps.flashlight.util.AppConstants.CALL_NOTIFICATION
import com.lahsuak.apps.flashlight.util.AppConstants.FLASH_EXIST
import com.lahsuak.apps.flashlight.util.AppConstants.FLASH_ON_START
import com.lahsuak.apps.flashlight.util.AppConstants.HAPTIC_FEEDBACK
import com.lahsuak.apps.flashlight.util.AppConstants.MAX_COUNTER_FOR_MORE_APPS
import com.lahsuak.apps.flashlight.util.AppConstants.MIN_TIME_BETWEEN_SHAKES_MILLIsECS
import com.lahsuak.apps.flashlight.util.AppConstants.MORE_APPS_DELAY
import com.lahsuak.apps.flashlight.util.AppConstants.SETTING_DATA
import com.lahsuak.apps.flashlight.util.AppConstants.SHAKE_SENSITIVITY
import com.lahsuak.apps.flashlight.util.AppConstants.SHAKE_TO_LIGHT
import com.lahsuak.apps.flashlight.util.AppConstants.SHOW_NOTIFICATION
import com.lahsuak.apps.flashlight.util.AppConstants.TEL
import com.lahsuak.apps.flashlight.util.AppConstants.TOUCH_SOUND
import com.lahsuak.apps.flashlight.util.AppUtil
import com.lahsuak.apps.flashlight.util.AppUtil.shakeThreshold
import com.lahsuak.apps.flashlight.util.AppUtil.hapticFeedback
import com.lahsuak.apps.flashlight.util.AppUtil.playSound
import com.lahsuak.apps.flashlight.util.FlashLightApp
import com.lahsuak.apps.flashlight.util.FlashLightApp.Companion.flashlightExist
import com.lahsuak.apps.flashlight.util.PermissionUtil
import com.lahsuak.apps.flashlight.util.SharedPrefConstants
import com.lahsuak.apps.flashlight.util.SharedPrefConstants.SOS_NUMBER_KEY
import com.lahsuak.apps.flashlight.util.getAnime
import com.lahsuak.apps.flashlight.util.setSensor
import com.lahsuak.apps.flashlight.util.toast
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

    private lateinit var settingPreference: SharedPreferences

    //extra
    private var flashState = false
    private var job: Job? = null
    private var checkLight = true // true for flashlight and false for screen light
    private var onOrOff = false
    private var isRunning = false
    private var isNotificationEnable = true
    private var isCallNotificationEnable = true
    private var isHapticFeedBackEnable = true
    private var isSoundEnable = false
    private var flashOnAtStartUpEnable = false
    private var bigFlashAsSwitchEnable = false
    private var shakeToLightEnable = false

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
            FlashLightApp.appContext.bindService(intent, this, BIND_AUTO_CREATE)
        }
    }

    override fun onDestroyView() {
        (activity as AppCompatActivity).supportActionBar?.show()
        super.onDestroyView()
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View? {
        (activity as AppCompatActivity).supportActionBar?.hide()
        return inflater.inflate(R.layout.fragment_home, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding = FragmentHomeBinding.bind(view)
        if (Build.VERSION_CODES.TIRAMISU <= Build.VERSION.SDK_INT) {
            checkPermissions(
                arrayOf(Manifest.permission.POST_NOTIFICATIONS)
            )
        }
        if (FlashLightApp.counter % MAX_COUNTER_FOR_MORE_APPS == 0) {
            viewLifecycleOwner.lifecycleScope.launch {
                binding.txtMoreApps.isVisible = true
                binding.btnMoreApps.isVisible = true
                delay(MORE_APPS_DELAY)
                binding.txtMoreApps.isGone = true
                binding.btnMoreApps.isGone = true
            }
        }
        FlashLightApp.counter++
        settingPreference = requireActivity().getSharedPreferences(SETTING_DATA, MODE_PRIVATE)
        getSharedPreference()
        requireContext().getAnime().apply {
            binding.btnPlay.animation = this
            binding.btnSos.animation = this
            //binding.phoneBtn.animation = myAnim
            binding.torchBtn.animation = this
        }
        //Shake to turn ON/OFF flashlight
        sensorManager = requireContext().setSensor(this)

        //Flash light fragment methods
        getAllSettings()

        if (screenState) {
            binding.txtBlinking.text =
                String.format(getString(R.string.brightness_level), sliderValue.toInt() / 10)
            binding.lightSlider.value = sliderValue
            setScreenLight(true, sliderValue / 100)
            binding.apply {
                btnScreenLight.setImageResource(R.drawable.ic_device_on)
                root.setBackgroundColor(layoutColor)
                btnSos.visibility = View.GONE
                screenColor.setColorFilter(layoutColor)
                screenColor.visibility = View.VISIBLE
                torchBtn.visibility = View.INVISIBLE
                torchBtn.setColorFilter(ContextCompat.getColor(requireContext(),R.color.yellow))
            }
        } else {
            binding.txtBlinking.text =
                String.format(getString(R.string.blinking_speed), 0)
            binding.lightSlider.value = 0f
            screenState = false
            checkLight = true
        }
        if (flashOnAtStartUpEnable) {
            turnFlash(true)
            isStartUpOn = true
        }
        setClickListeners()
        setSliderListeners()
    }

    private fun getSharedPreference() {
        flashlightExist = settingPreference.getBoolean(FLASH_EXIST, true)
        val isFirstTime =
            settingPreference.getBoolean(SharedPrefConstants.FIRST_TIME_USE_KEY, false)
        if (!isFirstTime) {
            showPermissionDialog()
        }
    }

    private fun showPermissionDialog() {
        val settingPref = requireContext().getSharedPreferences(SETTING_DATA, MODE_PRIVATE)
        val builder = MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.allow_perm))
            .setMessage(getString(R.string.permission_desc))
            .setPositiveButton(getString(R.string.ok)) { dialog, _ ->
                checkBothPermissions()
                settingPref.edit().putBoolean(SharedPrefConstants.FIRST_TIME_USE_KEY, true).apply()
                dialog.dismiss()
            }
        val dialogShow = builder.create()
        dialogShow.show()
    }

    private fun setSliderListeners() {
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
                        binding.txtBlinking.text =
                            String.format(
                                getString(R.string.blinking_speed), slider.value.roundToInt() / 10
                            )
                        if (isRunning) {
                            lifecycleScope.launch {
                                onOrOff = true
                                delay(500)
                                if (slider.value.roundToInt() > 0) {
                                    switchFlash(slider.value.roundToInt() / 10)
                                } else if (slider.value.roundToInt() == 0) {
                                    turnFlash(true)
                                }
                            }
                        } else {
                            onOrOff = false
                            if (slider.value.roundToInt() > 0) {
                                switchFlash(slider.value.roundToInt() / 10)
                            } else if (slider.value.roundToInt() == 0) {
                                turnFlash(true)
                            }
                        }
                    } else {
                        binding.txtBlinking.text =
                            String.format(
                                getString(R.string.brightness_level),
                                slider.value.toInt() / 10
                            )
                        setScreenLight(true, slider.value / 100)
                        binding.btnScreenLight.setImageResource(R.drawable.ic_device_on)
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

    private fun setClickListeners() {
        val myAnim = requireContext().getAnime()
        binding.btnScreenLight.setOnClickListener {
            binding.txtBlinking.text = String.format(getString(R.string.brightness_level), 5)
            binding.lightSlider.value = 50f
            binding.btnScreenLight.animation = myAnim
            sliderValue = 50f
            it.startAnimation(myAnim)
            setScreenClick()
        }

        binding.screenColor.setOnClickListener {
            showColorDialog()
        }

        binding.btnSos.setOnClickListener {
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
                hapticFeedback(binding.btnSos)
            }
        }

        binding.torchBtn.setOnClickListener {
            if (bigFlashAsSwitchEnable) {
                it.startAnimation(myAnim)
                startFlash()
            }
        }

        binding.btnSetting.setOnClickListener {
            val action = HomeFragmentDirections.actionHomeFragmentToSettingsFragment()
            findNavController().navigate(action)
        }

        binding.btnPlay.setOnClickListener {
            binding.root.setBackgroundColor(
                ContextCompat.getColor(
                    requireContext(),
                    R.color.blue
                )
            )
            binding.screenColor.visibility = View.GONE
            binding.btnSos.visibility = View.VISIBLE
            binding.torchBtn.visibility = View.VISIBLE
            binding.txtBlinking.text =
                String.format(getString(R.string.blinking_speed), 0)
            binding.lightSlider.value = 0f
            checkLight = true
            val layout = requireActivity().window.attributes
            layout.screenBrightness = -1.0f
            requireActivity().window.attributes = layout
            binding.btnScreenLight.setImageResource(R.drawable.ic_device)
            it.startAnimation(myAnim)
            startFlash()
            if (!flashState) {
                isStartUpOn = false
            }
        }
        binding.txtMoreApps.setOnClickListener {
            AppUtil.openMoreApp(requireContext())
        }
        binding.imgApps.setOnClickListener {
            AppUtil.openMoreApp(requireContext())
        }
        binding.btnMoreApps.setOnClickListener {
            AppUtil.openMoreApp(requireContext())
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
            val sosNo = settingPreference.getString(SOS_NUMBER_KEY, null)
            if (sosNo != null) {
                binding.btnSos.setImageResource(R.drawable.ic_sos)
            } else {
                binding.btnSos.setImageResource(R.drawable.ic_sos_off)
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
                    binding.btnSos.setImageResource(R.drawable.ic_sos)
                doPhoneCall()
            }

            false -> {
                context.toast {
                    getString(R.string.phone_call_denied_toast)
                }
            }
        }
    }
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

    private fun checkPermissions(permissions: Array<String>) {
        PermissionUtil.checkAndLaunchPermission(
            fragment = this,
            permissions = permissions,
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

    private fun checkBothPermissions() {
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
                binding.btnSos.setImageResource(R.drawable.ic_sos)
            doPhoneCall()
        }
    }

    private fun showSOSDialog() {
        val sosBinding = SosDialogBinding.inflate(layoutInflater)
        val builder = MaterialAlertDialogBuilder(requireContext())

        builder.setView(sosBinding.root)
            .setTitle(getString(R.string.sos_number))
            .setPositiveButton(getString(R.string.save)) { dialog, _ ->
                sosNumber = settingPreference.getString(SOS_NUMBER_KEY, null)

                if (!sosBinding.sosNumber.text.isNullOrEmpty() &&
                    sosBinding.sosNumber.text.toString().length == 10
                ) {
                    if (sosNumber != sosBinding.sosNumber.text.toString()) {
                        context.toast { getString(R.string.contact_is_successfully_added) }
                        checkBothPermissions()
                        binding.btnSos.setImageResource(R.drawable.ic_sos)
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
    private fun setScreenLight(screenON: Boolean, screenLight: Float) {
        val layout = requireActivity().window.attributes
        screenState = screenON
        layout.screenBrightness = screenLight
        requireActivity().window.attributes = layout
    }

    //flashlight methods
    private fun switchFlash(noOfTimes: Int) {
        isRunning = true
        onOrOff = false
        var flashOn = true
        binding.btnPlay.setImageResource(R.drawable.ic_flashlight_on)
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

    private fun setSOSFlash() {
        isRunning = true
        onOrOff = false
        var flashOn = true
        binding.btnPlay.setImageResource(R.drawable.ic_flashlight_on)
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
                service?.showNotification(isCheck)
        }
        service?.torchSwitch(isCheck, binding.torchBtn, binding.btnPlay)
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
            .setNegativeButton(getString(R.string.ok)) { _, _ -> }
            .build()
            .show()
    }

    private fun startFlash() {
        if (isSoundEnable) {
            playSound(requireContext())
        }
        if (isHapticFeedBackEnable) {
            hapticFeedback(binding.btnPlay)
        }
        runFlashlight()
        if (isRunning) {
            if (onOrOff) {
                onOrOff = false
            } else {
                onOrOff = true
                binding.btnPlay.setImageResource(R.drawable.ic_flashlight_off)
            }
            isRunning = false
            job?.cancel()
            turnFlash(false)
            binding.btnPlay.setImageResource(R.drawable.ic_flashlight_off)
        }
    }

    private fun getAllSettings() {
        PreferenceManager.getDefaultSharedPreferences(requireContext()).apply {
            shakeThreshold =
                getInt(SHAKE_SENSITIVITY, (shakeThreshold * 10f).toInt()).toFloat() / 10f
            isNotificationEnable = getBoolean(SHOW_NOTIFICATION, true)
            isHapticFeedBackEnable = getBoolean(HAPTIC_FEEDBACK, true)
            isSoundEnable = getBoolean(TOUCH_SOUND, false)
            isStartUpOn = getBoolean(FLASH_ON_START, false)
            bigFlashAsSwitchEnable = getBoolean(BIG_FLASH_AS_SWITCH, false)
            shakeToLightEnable = getBoolean(SHAKE_TO_LIGHT, false)
            isCallNotificationEnable = getBoolean(CALL_NOTIFICATION, true)
        }

        requireContext().getSharedPreferences(SETTING_DATA, MODE_PRIVATE).apply {
            sosNumber = getString(SOS_NUMBER_KEY, null)
        }

        if (!isNotificationEnable) {
            service?.let {
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
                    @Suppress("deprecation")
                    it.stopForeground(true)
                } else {
                    it.stopForeground(Service.STOP_FOREGROUND_REMOVE)
                }
                it.stopSelf()
            }
        } else {
            service?.let {
                val intent = Intent(requireContext(), CallService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    requireActivity().startForegroundService(intent)
                } else
                    requireActivity().startService(intent)
                it.showNotification(flashState)
            }
        }
    }

    private fun doPhoneCall() {
        val phNo = settingPreference.getString(SOS_NUMBER_KEY, null)
        if (phNo.isNullOrEmpty()) {
            context.toast { getString(R.string.sos_toast) }
        } else {
            binding.btnSos.setImageResource(R.drawable.ic_sos)
            setSOSFlash()
            val prefManager = PreferenceManager.getDefaultSharedPreferences(requireContext())
            val allowed = prefManager.getBoolean(SharedPrefConstants.SOS_CALL_KEY, false)
            if (allowed) {
                val callIntent = Intent(Intent.ACTION_CALL)
                callIntent.data = Uri.parse(TEL + "$phNo")
                startActivity(callIntent)
            }
        }
    }

    //save app settings
    private fun saveSetting() {
        settingPreference.edit().apply {
            putString(SOS_NUMBER_KEY, sosNumber)
            putFloat(SHAKE_SENSITIVITY, shakeThreshold)
        }.apply()
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
                        if (acceleration > shakeThreshold) {
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
        /* no-op */
    }

    //callback method
    override fun onTorchClick(flashON: Boolean) {
        turnFlash(flashON)
    }

    private fun setScreenClick() {
        checkLight = false
        if (!screenState) {
            setScreenLight(true, 0.5f)
            binding.btnScreenLight.setImageResource(R.drawable.ic_device_on)
            binding.root.setBackgroundColor(
                ContextCompat.getColor(
                    requireContext(),
                    R.color.white
                )
            )
            binding.btnSos.visibility = View.GONE
            binding.screenColor.setColorFilter(Color.WHITE)
            binding.screenColor.visibility = View.VISIBLE
            binding.torchBtn.visibility = View.INVISIBLE
            layoutColor = Color.WHITE
        } else {
            setScreenLight(false, -1.0f)
            binding.btnScreenLight.setImageResource(R.drawable.ic_device)
            binding.root.setBackgroundColor(
                ContextCompat.getColor(
                    requireContext(),
                    R.color.blue
                )
            )
            binding.btnSos.visibility = View.VISIBLE
            binding.screenColor.visibility = View.GONE
            binding.torchBtn.visibility = View.VISIBLE
        }
        if (isSoundEnable) {
            playSound(requireContext())
        }
        if (isHapticFeedBackEnable) {
            hapticFeedback(binding.btnSos)
        }
        binding.btnPlay.setImageResource(R.drawable.ic_flashlight_off)
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
            service?.setCallBack(this)
            if (isStartUpOn) {
                turnFlash(true)
            }
        }
    }

    override fun onServiceDisconnected(name: ComponentName) {
        if (flashlightExist)
            service = null
    }
}
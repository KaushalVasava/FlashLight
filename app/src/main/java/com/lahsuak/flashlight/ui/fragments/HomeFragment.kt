package com.lahsuak.flashlight.ui.fragments

import android.Manifest
import android.app.Activity
import android.app.Dialog
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
import android.widget.Button
import android.widget.Toast
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
import com.google.android.material.textfield.TextInputEditText
import com.google.android.play.core.appupdate.AppUpdateManager
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.install.InstallStateUpdatedListener
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.InstallStatus
import com.google.android.play.core.install.model.UpdateAvailability
import com.lahsuak.flashlight.R
import com.lahsuak.flashlight.`interface`.LightListener
import com.lahsuak.flashlight.databinding.FragmentHomeBinding
import com.lahsuak.flashlight.service.CallService
import com.lahsuak.flashlight.util.*
import com.lahsuak.flashlight.util.App.Companion.flashlightExist
import com.lahsuak.flashlight.util.MIN_TIME_BETWEEN_SHAKES_MILLIsECS
import com.lahsuak.flashlight.util.SETTING_DATA
import com.lahsuak.flashlight.util.SOS_NUMBER
import com.lahsuak.flashlight.util.Util.SHAKE_THRESHOLD
import com.lahsuak.flashlight.util.Util.hapticFeedback
import com.lahsuak.flashlight.util.Util.notifyUser
import com.lahsuak.flashlight.util.Util.playSound
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
    private var screenState = false // new for screen brightness
    var checkLight = true // true for flashlight and false for screen light
    private var onOrOff = false
    private var isRunning = false
    private var isNotificationEnable = true
    private var isCallNotificationEnable = true
    private var isHapticFeedBackEnable = true
    private var isSoundEnable = false
    private var flashOnAtStartUpEnable = false
    private var bigFlashAsSwitchEnable = false
    private var shakeToLightEnable = true
    private var appUpdateManager: AppUpdateManager? = null

    companion object {
        var isStartUpOn = false
        var sos_number: String? = null
    }

    override fun onResume() {
        super.onResume()
        if (flashlightExist) {
            val intent = Intent(requireContext(), CallService::class.java)
            requireContext().bindService(intent, this, BIND_AUTO_CREATE)
        }
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
                    notifyUser(requireContext(), exception.message.toString())
                }
            }
        }
    }
    private val appUpdateListener = InstallStateUpdatedListener { state ->
        if (state.installStatus() == InstallStatus.DOWNLOADED) {
            Snackbar.make(requireView(), "New app is ready", Snackbar.LENGTH_INDEFINITE)
                .setAction("Restart") {
                    appUpdateManager!!.completeUpdate()
                }.show()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        @Suppress("deprecation")
        super.onActivityResult(requestCode, resultCode, data)
        if (data == null) return
        if (requestCode == UPDATE_REQUEST_CODE) {
            notifyUser(requireContext(), "Downloading start")
            if (resultCode != Activity.RESULT_OK) {
                notifyUser(requireActivity().applicationContext, "Update failed")
            }
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

        val pref = requireContext().getSharedPreferences(SETTING_DATA, MODE_PRIVATE)
        flashlightExist = pref.getBoolean(FLASH_EXIST, true)

        appUpdateManager = AppUpdateManagerFactory.create(requireContext())
        //checking update of application
        checkUpdate()
        appUpdateManager!!.registerListener(appUpdateListener)

        val prefNew = requireContext().getSharedPreferences(SETTING_DATA, MODE_PRIVATE)
        val firstTime = prefNew.getBoolean("first_time", false)
        if (!firstTime) {
            val builder = MaterialAlertDialogBuilder(requireContext())
                .setTitle(getString(R.string.allow_perm))
                .setMessage(getString(R.string.permission_desc))
                .setPositiveButton(getString(R.string.ok)) { dialog, _ ->
                    //checkBothPermissions()
                    bothPermission()
                    prefNew.edit().putBoolean("first_time", true).apply()
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

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M)
            checkCameraPermission()

        //Flash light fragment methods
        getAllSettings()

        binding.blinkingLabel.text = getString(R.string.blinking_speed, 0)
        if (flashOnAtStartUpEnable) {
            turnFlash(true)
            isStartUpOn = true
        }
        binding.screenFlashlight.setOnClickListener {
            binding.blinkingLabel.text = getString(R.string.brightness_level, 0)
            binding.lightSlider.value = 0f
            binding.screenFlashlight.animation = myAnim
            it.startAnimation(myAnim)
            screenClick()
        }
        binding.screenColor.setOnClickListener {
            showColorDialog()
        }
        binding.sosBtn.setOnClickListener {
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
            binding.screenColor.visibility=View.GONE
            binding.sosBtn.visibility= View.VISIBLE
            binding.torchBtn.visibility = View.VISIBLE
            binding.blinkingLabel.text = getString(R.string.blinking_speed, 0)
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
            format.format(value.toDouble() / 10)
        }
    }

    private val bothPermissionsResultCallback = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        var permissionGranted = false
        permissions.entries.forEach {
            val isGranted = it.value
            if (isGranted) {
                permissionGranted = true
            } else {
                permissionGranted = false
            }
        }
        if (permissionGranted) {
            val preference = requireActivity().getSharedPreferences(SETTING_DATA, MODE_PRIVATE)
            val sosNo = preference.getString(SOS_NUMBER, null)
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
                if (sos_number == null)
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
    private val cameraPermissionsResultCallback = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {
        if(!it) {
            notifyUser(requireContext(), "Please give camera permission for flashlight")
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

    private fun checkCameraPermission() {
        val array = Manifest.permission.CAMERA
        val permission = ContextCompat.checkSelfPermission(
            requireContext(),
            array
        )
        if (permission != PackageManager.PERMISSION_GRANTED) {
            cameraPermissionsResultCallback.launch(array)
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
            if (sos_number == null)
                showSOSDialog()
            else
                binding.sosBtn.setImageResource(R.drawable.ic_sos)
            phoneCall()
        }
    }

    private fun showSOSDialog() {
        val dialog = Dialog(requireContext())
        dialog.setContentView(R.layout.sos_dialog)
        dialog.window?.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        dialog.setCancelable(false)

        val sosNumber = dialog.findViewById<TextInputEditText>(R.id.sos_number)
        val save = dialog.findViewById<Button>(R.id.saveBtn)
        val cancel = dialog.findViewById<Button>(R.id.cancelBtn)

        save.setOnClickListener {
            val preference = requireActivity().getSharedPreferences(SETTING_DATA, MODE_PRIVATE)
            sos_number = preference.getString(SOS_NUMBER, null)

            if (!sosNumber.text.isNullOrEmpty() &&
                sosNumber.text.toString().length == 10
            ) {
                if (sos_number != sosNumber.text.toString()) {
                    notifyUser(requireContext(), "Contact is successfully added")
                    bothPermission()
                    binding.sosBtn.setImageResource(R.drawable.ic_sos)
                    sos_number = sosNumber.text.toString()
                }
                saveSetting()
            } else {
                notifyUser(requireContext(), "Please enter SOS Number!")
            }
            dialog.dismiss()
        }
        cancel.setOnClickListener {
            dialog.dismiss()
        }
        dialog.show()
    }

    //screen light methods
    private fun screenLight(screenON: Boolean, screenLight: Float) {
        val layout = requireActivity().window.attributes
        if (screenON) {
            screenState = true
        } else
            screenState = false
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

    private fun showColorDialog(){
        ColorPickerDialogBuilder
            .with(requireContext())
            .setTitle("Screen Colors")
            .initialColor(Color.WHITE)
            .wheelType(ColorPickerView.WHEEL_TYPE.FLOWER)
            .density(12)
            .setOnColorChangedListener { color ->
                binding.root.setBackgroundColor(color)
                binding.screenColor.setColorFilter(color)
            }
            .setNegativeButton(
                "OK"
            ){ _, _ -> }
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
        SHAKE_THRESHOLD = (prefSetting.getInt(SHAKE_SENSITIVITY, (SHAKE_THRESHOLD*10f).toInt()).toFloat()/10f)
        isNotificationEnable = prefSetting.getBoolean(SHOW_NOTIFICATION, true)
        isHapticFeedBackEnable = prefSetting.getBoolean(HAPTIC_FEEDBACK, true)
        isSoundEnable = prefSetting.getBoolean(TOUCH_SOUND, false)
        isStartUpOn = prefSetting.getBoolean(FLASH_ON_START, false)
        bigFlashAsSwitchEnable = prefSetting.getBoolean(BIG_FLASH_AS_SWITCH, false)
        shakeToLightEnable = prefSetting.getBoolean(SHAKE_TO_LIGHT, true)
        isCallNotificationEnable = prefSetting.getBoolean(CALL_NOTIFICATION, true)
        val pref = requireContext().getSharedPreferences(SETTING_DATA, MODE_PRIVATE)
        sos_number = pref.getString(SOS_NUMBER, null)

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
        val phNo1 = pref.getString(SOS_NUMBER, null)
        if (phNo1.isNullOrEmpty()) {
            notifyUser(requireContext(), getString(R.string.sos_toast))
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
        val editor = requireActivity().getSharedPreferences(SETTING_DATA, MODE_PRIVATE).edit()
        editor.putString(SOS_NUMBER, sos_number)
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
        //   if(isStartUpOn)
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
            binding.screenColor.visibility=View.VISIBLE
            binding.torchBtn.visibility = View.INVISIBLE
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

}
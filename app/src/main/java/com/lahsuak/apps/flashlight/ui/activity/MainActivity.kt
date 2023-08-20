package com.lahsuak.apps.flashlight.ui.activity

import android.app.Activity
import android.content.IntentSender
import android.os.Bundle
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupActionBarWithNavController
import com.google.android.material.snackbar.Snackbar
import com.google.android.play.core.appupdate.AppUpdateManager
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.appupdate.AppUpdateOptions
import com.google.android.play.core.common.IntentSenderForResultStarter
import com.google.android.play.core.install.InstallStateUpdatedListener
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.InstallStatus
import com.google.android.play.core.install.model.UpdateAvailability
import com.google.android.play.core.review.ReviewInfo
import com.google.android.play.core.review.ReviewManager
import com.google.android.play.core.review.ReviewManagerFactory
import com.lahsuak.apps.flashlight.R
import com.lahsuak.apps.flashlight.databinding.ActivityMainBinding
import com.lahsuak.apps.flashlight.ui.fragments.HomeFragment.Companion.screenState
import com.lahsuak.apps.flashlight.util.AppConstants.UPDATE_REQUEST_CODE
import com.lahsuak.apps.flashlight.util.FlashLightApp
import com.lahsuak.apps.flashlight.util.toast

class MainActivity : AppCompatActivity() {
    private var _binding: ActivityMainBinding ?= null
    private val binding: ActivityMainBinding
        get() = _binding!!
    private lateinit var navController: NavController
    private var appUpdateManager: AppUpdateManager? = null
    private var reviewInfo: ReviewInfo? = null
    private lateinit var reviewManager: ReviewManager

    private val updateLauncher = registerForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        // handle callback
        if (result.data == null) return@registerForActivityResult
        if (result.resultCode == UPDATE_REQUEST_CODE) {
            toast { getString(R.string.downloading_start) }
            if (result.resultCode != Activity.RESULT_OK) {
                FlashLightApp.appContext.toast { getString(R.string.update_failed) }
            }
        }
    }

    private val updateResultStarter =
        IntentSenderForResultStarter { intent, _, fillInIntent, flagsMask, flagsValues, _, _ ->
            val request = IntentSenderRequest.Builder(intent)
                .setFillInIntent(fillInIntent)
                .setFlags(flagsValues, flagsMask)
                .build()

            updateLauncher.launch(request)
        }

    private val appUpdateListener = InstallStateUpdatedListener { state ->
        if (state.installStatus() == InstallStatus.DOWNLOADED) {
            Snackbar.make(
                binding.root,
                getString(R.string.new_app_is_ready),
                Snackbar.LENGTH_INDEFINITE
            ).setAction(getString(R.string.restart)) {
                appUpdateManager!!.completeUpdate()
            }.show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        _binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        reviewManager = ReviewManagerFactory.create(this)
        activateReviewInfo()

        val navHostFragment =
            supportFragmentManager.findFragmentById(R.id.container) as NavHostFragment
        navController = navHostFragment.navController
        setupActionBarWithNavController(navController)//,appBarConfiguration)

        appUpdateManager = AppUpdateManagerFactory.create(this)
        //checking update of application
        checkUpdate()
        appUpdateManager?.registerListener(appUpdateListener)
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (isEnabled) {
                    isEnabled = false
                    if (navController.currentDestination?.id == R.id.homeFragment) {
                        finish()
                    } else {
                        startReviewFlow()
                    }
                }
            }
        })
    }

    override fun onSupportNavigateUp(): Boolean {
        return navController.navigateUp() || super.onSupportNavigateUp()
    }

    private fun checkUpdate() {
        val appUpdateInfoTask = appUpdateManager!!.appUpdateInfo

        appUpdateInfoTask.addOnSuccessListener { appUpdateInfo ->
            if (appUpdateInfo.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE
                && appUpdateInfo.isUpdateTypeAllowed(AppUpdateType.FLEXIBLE)
            ) {
                try {
                    appUpdateManager?.startUpdateFlowForResult(
                        appUpdateInfo,
                        updateResultStarter,
                        AppUpdateOptions.newBuilder(AppUpdateType.FLEXIBLE).build(),
                        UPDATE_REQUEST_CODE
                    )
                } catch (exception: IntentSender.SendIntentException) {
                    toast { exception.message.toString() }
                }
            }
        }
    }

    private fun activateReviewInfo() {
        reviewManager = ReviewManagerFactory.create(this)
        val reviewTask = reviewManager.requestReviewFlow()
        reviewTask.addOnCompleteListener {
            if (it.isSuccessful) {
                reviewInfo = it.result
            }
        }
    }

    private fun startReviewFlow() {
        if (reviewInfo != null) {
            reviewManager.launchReviewFlow(this, reviewInfo!!)
        }
    }

    override fun onDestroy() {
        reviewInfo = null
        appUpdateManager = null
        _binding = null
        screenState = false
        super.onDestroy()
    }
}
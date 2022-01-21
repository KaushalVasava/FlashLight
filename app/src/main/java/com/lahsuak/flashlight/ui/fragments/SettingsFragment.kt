package com.lahsuak.flashlight.ui.fragments

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.fragment.findNavController
import androidx.preference.*
import com.google.android.play.core.review.ReviewManager
import com.google.android.play.core.review.ReviewManagerFactory
import com.lahsuak.flashlight.BuildConfig
import com.lahsuak.flashlight.R
import com.lahsuak.flashlight.ui.fragments.HomeFragment.Companion.sos_number
import com.lahsuak.flashlight.util.SETTING_DATA
import com.lahsuak.flashlight.util.SOS_NUMBER
import com.lahsuak.flashlight.util.Util.appRating
import com.lahsuak.flashlight.util.Util.moreApp
import com.lahsuak.flashlight.util.Util.sendFeedbackMail
import com.lahsuak.flashlight.util.Util.shareApp
import java.util.regex.Matcher
import com.lahsuak.flashlight.ui.activity.MainActivity
import com.lahsuak.flashlight.util.Util.notifyUser
import java.util.regex.Pattern


class SettingsFragment : PreferenceFragmentCompat() {

    //in-app review
    private lateinit var reviewManager: ReviewManager

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        (activity as AppCompatActivity).supportActionBar?.show()
        reviewManager = ReviewManagerFactory.create(requireContext())

        requireActivity().onBackPressedDispatcher.addCallback(
            viewLifecycleOwner,
            backPressedDispatcher
        )
        return super.onCreateView(inflater, container, savedInstanceState)
    }


    private val backPressedDispatcher = object : OnBackPressedCallback(true) {
        override fun handleOnBackPressed() {
            this@SettingsFragment.onBackPressed()
        }
    }

    private fun showRateApp() {
        val request = reviewManager.requestReviewFlow()
        request.addOnCompleteListener { task ->
            if (task.isSuccessful) {
                val reviewInfo = task.result
                val activity = MainActivity()
                val flow = reviewManager.launchReviewFlow(activity, reviewInfo)
                flow.addOnCompleteListener {
                    //nothing to do
                }
            }
        }
    }

    private fun onBackPressed() {
        showRateApp()
        findNavController().popBackStack()
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.root_preferences, rootKey)

        val prefFeedback = findPreference<Preference>("feedback")
        val prefShare = findPreference<Preference>("share")
        val prefMoreApp = findPreference<Preference>("more_app")
        val prefVersion = findPreference<Preference>("app_version")
        val prefRating = findPreference<Preference>("rating")
        val prefSosNumber = findPreference<EditTextPreference>("sos_number")

        if(sos_number!=null) {
            prefSosNumber?.text = sos_number
            prefSosNumber?.summary = sos_number
        }else{
            prefSosNumber?.summary = getString(R.string.enter_sos_number)
        }
        prefSosNumber?.setOnPreferenceChangeListener { _, newValue ->
            val pattern =
                "^\\s*(?:\\+?(\\d{1,3}))?[-. (]*(\\d{3})[-. )]*(\\d{3})[-. ]*(\\d{4})(?: *x(\\d+))?\\s*$"
            val m: Matcher
            val r = Pattern.compile(pattern)
            if ((newValue as String).isNotEmpty() && newValue.length==10) {
                m = r.matcher(newValue.trim())
                if (m.find()) {
                    sos_number = newValue
                    prefSosNumber.summary = sos_number
                    val editor = requireActivity().getSharedPreferences(SETTING_DATA, Context.MODE_PRIVATE).edit()
                    editor.putString(SOS_NUMBER, sos_number)
                    editor.apply()
                }
                else{
                    notifyUser(requireContext(),getString(R.string.sos_toast))
                }
            } else {
                notifyUser(requireContext(),getString(R.string.sos_toast))
            }
            true
        }
        prefVersion!!.summary = BuildConfig.VERSION_NAME

        prefFeedback?.setOnPreferenceClickListener {
            sendFeedbackMail(requireContext())
            true
        }
        prefShare?.setOnPreferenceClickListener {
            shareApp(requireContext())
            true
        }
        prefMoreApp?.setOnPreferenceClickListener {
            moreApp(requireContext())
            true
        }
        prefRating?.setOnPreferenceClickListener {
            appRating(requireContext())
            true
        }
    }
}
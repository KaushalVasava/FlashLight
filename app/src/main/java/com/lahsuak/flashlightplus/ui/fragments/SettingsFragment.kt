package com.lahsuak.flashlightplus.ui.fragments

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.EditTextPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import com.lahsuak.flashlightplus.BuildConfig
import com.lahsuak.flashlightplus.R
import com.lahsuak.flashlightplus.ui.fragments.HomeFragment.Companion.sosNumber
import com.lahsuak.flashlightplus.util.AppConstants
import com.lahsuak.flashlightplus.util.AppConstants.SETTING_DATA
import com.lahsuak.flashlightplus.util.SharedPrefConstants.APP_VERSION_KEY
import com.lahsuak.flashlightplus.util.SharedPrefConstants.FEEDBACK_KEY
import com.lahsuak.flashlightplus.util.SharedPrefConstants.MORE_APP_KEY
import com.lahsuak.flashlightplus.util.SharedPrefConstants.RATING_KEY
import com.lahsuak.flashlightplus.util.SharedPrefConstants.SHARE_KEY
import com.lahsuak.flashlightplus.util.SharedPrefConstants.SOS_NUMBER_KEY
import com.lahsuak.flashlightplus.util.Util.appRating
import com.lahsuak.flashlightplus.util.Util.moreApp
import com.lahsuak.flashlightplus.util.Util.sendFeedbackMail
import com.lahsuak.flashlightplus.util.Util.shareApp
import com.lahsuak.flashlightplus.util.toast
import java.util.regex.Pattern

class SettingsFragment : PreferenceFragmentCompat() {

    //in-app review
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        (activity as AppCompatActivity).supportActionBar?.show()
        return super.onCreateView(inflater, container, savedInstanceState)
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.setting_preferences, rootKey)

        val prefFeedback = findPreference<Preference>(FEEDBACK_KEY)
        val prefShare = findPreference<Preference>(SHARE_KEY)
        val prefMoreApp = findPreference<Preference>(MORE_APP_KEY)
        val prefVersion = findPreference<Preference>(APP_VERSION_KEY)
        val prefRating = findPreference<Preference>(RATING_KEY)
        val prefSosNumber = findPreference<EditTextPreference>(SOS_NUMBER_KEY)

        if (sosNumber != null) {
            prefSosNumber?.text = sosNumber
            prefSosNumber?.summary = sosNumber
        } else {
            prefSosNumber?.summary = getString(R.string.enter_sos_number)
        }
        prefSosNumber?.setOnPreferenceChangeListener { _, newValue ->
            try {
                val r = Pattern.compile(AppConstants.PHONE_NUMBER_PATTERN)
                if ((newValue as String).isNotEmpty() && newValue.length == 10) {
                    val m = r.matcher(newValue.trim())
                    if (m.find()) {
                        sosNumber = newValue
                        prefSosNumber.summary = sosNumber
                        val editor = requireActivity().getSharedPreferences(
                            SETTING_DATA,
                            Context.MODE_PRIVATE
                        ).edit()
                        editor.putString(SOS_NUMBER_KEY, sosNumber)
                        editor.apply()
                    } else {
                        context.toast { getString(R.string.sos_toast) }
                    }
                } else {
                    context.toast { getString(R.string.sos_toast) }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
            true
        }
        prefVersion?.summary = BuildConfig.VERSION_NAME

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



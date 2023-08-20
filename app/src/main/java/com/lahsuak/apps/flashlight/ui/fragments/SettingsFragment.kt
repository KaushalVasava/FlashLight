package com.lahsuak.apps.flashlight.ui.fragments

import android.content.Context
import android.os.Bundle
import androidx.preference.EditTextPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import com.lahsuak.apps.flashlight.BuildConfig
import com.lahsuak.apps.flashlight.R
import com.lahsuak.apps.flashlight.ui.fragments.HomeFragment.Companion.sosNumber
import com.lahsuak.apps.flashlight.util.AppConstants
import com.lahsuak.apps.flashlight.util.AppConstants.SETTING_DATA
import com.lahsuak.apps.flashlight.util.AppUtil
import com.lahsuak.apps.flashlight.util.AppUtil.appRating
import com.lahsuak.apps.flashlight.util.AppUtil.openMoreApp
import com.lahsuak.apps.flashlight.util.AppUtil.sendFeedbackMail
import com.lahsuak.apps.flashlight.util.AppUtil.shareApp
import com.lahsuak.apps.flashlight.util.SharedPrefConstants.APP_VERSION_KEY
import com.lahsuak.apps.flashlight.util.SharedPrefConstants.DEVELOPER
import com.lahsuak.apps.flashlight.util.SharedPrefConstants.FEEDBACK_KEY
import com.lahsuak.apps.flashlight.util.SharedPrefConstants.MORE_APP_KEY
import com.lahsuak.apps.flashlight.util.SharedPrefConstants.RATING_KEY
import com.lahsuak.apps.flashlight.util.SharedPrefConstants.SHARE_KEY
import com.lahsuak.apps.flashlight.util.SharedPrefConstants.SOS_NUMBER_KEY
import com.lahsuak.apps.flashlight.util.toast
import java.util.regex.Pattern

class SettingsFragment : PreferenceFragmentCompat() {

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.setting_preferences, rootKey)

        val prefFeedback = findPreference<Preference>(FEEDBACK_KEY)
        val prefShare = findPreference<Preference>(SHARE_KEY)
        val prefMoreApp = findPreference<Preference>(MORE_APP_KEY)
        val prefVersion = findPreference<Preference>(APP_VERSION_KEY)
        val prefRating = findPreference<Preference>(RATING_KEY)
        val prefSosNumber = findPreference<EditTextPreference>(SOS_NUMBER_KEY)
        val prefDeveloper = findPreference<Preference>(DEVELOPER)

        prefSosNumber?.summary = if (sosNumber != null) {
            prefSosNumber?.text = sosNumber
            sosNumber
        } else {
            getString(R.string.enter_sos_number)
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
            openMoreApp(requireContext())
            true
        }
        prefRating?.setOnPreferenceClickListener {
            appRating(requireContext())
            true
        }
        prefDeveloper?.setOnPreferenceClickListener {
            AppUtil.openWebsite(context, AppConstants.WEBSITE)
            true
        }
    }
}



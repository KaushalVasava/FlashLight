package com.lahsuak.flashlightplus.ui.fragments

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.*
import com.lahsuak.flashlightplus.BuildConfig
import com.lahsuak.flashlightplus.R
import com.lahsuak.flashlightplus.ui.fragments.HomeFragment.Companion.sos_number
import com.lahsuak.flashlightplus.util.Constants.SETTING_DATA
import com.lahsuak.flashlightplus.util.Constants.SOS_NUMBER
import com.lahsuak.flashlightplus.util.Util.appRating
import com.lahsuak.flashlightplus.util.Util.moreApp
import com.lahsuak.flashlightplus.util.Util.sendFeedbackMail
import com.lahsuak.flashlightplus.util.Util.shareApp
import java.util.regex.Matcher
import com.lahsuak.flashlightplus.util.Util.notifyUser
import java.lang.Exception
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
            try {
                val pattern =
                    "^\\s*(?:\\+?(\\d{1,3}))?[-. (]*(\\d{3})[-. )]*(\\d{3})[-. ]*(\\d{4})(?: *x(\\d+))?\\s*$"
                val m: Matcher
                val r = Pattern.compile(pattern)
                if ((newValue as String).isNotEmpty() && newValue.length == 10) {
                    m = r.matcher(newValue.trim())
                    if (m.find()) {
                        sos_number = newValue
                        prefSosNumber.summary = sos_number
                        val editor = requireActivity().getSharedPreferences(
                            SETTING_DATA,
                            Context.MODE_PRIVATE
                        ).edit()
                        editor.putString(SOS_NUMBER, sos_number)
                        editor.apply()
                    } else {
                        notifyUser(requireContext(), getString(R.string.sos_toast))
                    }
                } else {
                    notifyUser(requireContext(), getString(R.string.sos_toast))
                }
            }catch(e: Exception){
                e.printStackTrace()
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



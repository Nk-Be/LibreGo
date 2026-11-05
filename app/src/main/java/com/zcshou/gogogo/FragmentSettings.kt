package com.zcshou.gogogo

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.InputType
import androidx.preference.EditTextPreference
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SwitchPreferenceCompat
import com.elvishew.xlog.XLog
import com.zcshou.utils.GoUtils

/**
 * Settings Preference Fragment in Kotlin for LibreGo.
 */
class FragmentSettings : PreferenceFragmentCompat() {

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        addPreferencesFromResource(R.xml.preferences_main)

        // Joystick type
        findPreference<ListPreference>("setting_joystick_type")?.apply {
            summaryProvider = Preference.SummaryProvider<ListPreference> { pref ->
                pref.entry ?: ""
            }
            setOnPreferenceChangeListener { _, newValue ->
                newValue.toString().trim().isNotEmpty()
            }
        }

        // Decimal numeric preferences
        setupDecimalEditTextPreference(findPreference("setting_walk"))
        setupDecimalEditTextPreference(findPreference("setting_run"))
        setupDecimalEditTextPreference(findPreference("setting_bike"))
        setupDecimalEditTextPreference(findPreference("setting_altitude"))
        setupDecimalEditTextPreference(findPreference("setting_lat_max_offset"))
        setupDecimalEditTextPreference(findPreference("setting_lon_max_offset"))
        setupDecimalEditTextPreference(findPreference("setting_history_expiration"))

        // Log toggle
        findPreference<SwitchPreferenceCompat>("setting_log_off")?.apply {
            setOnPreferenceChangeListener { preference, newValue ->
                val isChecked = (preference as SwitchPreferenceCompat).isChecked
                val willCheck = newValue as Boolean
                if (isChecked != willCheck) {
                    XLog.d("${preference.key}: $willCheck")
                    true
                } else {
                    false
                }
            }
        }

        // Version name
        val verName = GoUtils.getVersionName(requireContext())
        findPreference<Preference>("setting_version")?.summary = verName

        // GitHub link
        findPreference<Preference>("setting_github")?.apply {
            summary = "https://github.com/Nk-Be/LibreGo"
            setOnPreferenceClickListener {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/Nk-Be/LibreGo"))
                startActivity(intent)
                true
            }
        }
    }

    private fun setupDecimalEditTextPreference(preference: EditTextPreference?) {
        preference ?: return
        preference.summaryProvider = EditTextPreference.SimpleSummaryProvider.getInstance()
        preference.setOnBindEditTextListener { editText ->
            editText.inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
            editText.setSelection(editText.length())
        }
        preference.setOnPreferenceChangeListener { _, newValue ->
            if (newValue.toString().trim().isEmpty()) {
                GoUtils.DisplayToast(context, getString(R.string.app_error_input_null))
                false
            } else {
                true
            }
        }
    }
}

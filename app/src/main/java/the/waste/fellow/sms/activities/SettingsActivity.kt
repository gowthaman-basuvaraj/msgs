package the.waste.fellow.sms.activities

import android.os.Bundle
import android.text.InputType
import android.view.MenuItem
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.EditTextPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import the.waste.fellow.sms.R
import the.waste.fellow.sms.sync.HttpSmsSyncRepository
import the.waste.fellow.sms.utils.AppSettings

class SettingsActivity : AppCompatActivity() {


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.settings_layout)
        if (supportActionBar != null) supportActionBar!!.setDisplayHomeAsUpEnabled(true)
        supportFragmentManager.beginTransaction().replace(R.id.container,
                SettingsFragment()).commit()

    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) finish()
        return super.onOptionsItemSelected(item)
    }


    class SettingsFragment : PreferenceFragmentCompat() {
        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            setPreferencesFromResource(R.xml.pref_general, rootKey)

            // Mask the bearer token input.
            (findPreference<EditTextPreference>(AppSettings.KEY_SYNC_TOKEN))
                ?.setOnBindEditTextListener { editText ->
                    editText.inputType =
                        InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
                }
        }

        override fun onPreferenceTreeClick(preference: Preference): Boolean {
            if (preference.key == "sync_now") {
                val ctx = requireContext()
                if (AppSettings(ctx).syncConfigured) {
                    HttpSmsSyncRepository.scheduleSync(ctx, immediate = true)
                    Toast.makeText(ctx, "Sync scheduled", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(ctx, "Set server URL and username first", Toast.LENGTH_SHORT).show()
                }
                return true
            }
            return super.onPreferenceTreeClick(preference)
        }
    }
}

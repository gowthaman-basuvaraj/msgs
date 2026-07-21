package the.waste.fellow.sms.activities

import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.view.MenuItem
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.EditTextPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import the.waste.fellow.sms.R
import the.waste.fellow.sms.auth.AuthManager
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

            // Mask the fallback token input.
            (findPreference<EditTextPreference>(AppSettings.KEY_SYNC_TOKEN))
                ?.setOnBindEditTextListener { editText ->
                    editText.inputType =
                        InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
                }
        }

        override fun onResume() {
            super.onResume()
            refreshSignInState()
        }

        private fun refreshSignInState() {
            val signIn = findPreference<Preference>("sync_sign_in") ?: return
            val auth = AuthManager(requireContext())
            if (auth.isAuthorized) {
                signIn.title = "Sign out"
                signIn.summary = "Signed in as ${auth.userName ?: "user"} — tap to sign out"
            } else {
                signIn.title = "Sign in"
                signIn.summary = "Log in with Keycloak; the app then refreshes tokens automatically"
            }
        }

        override fun onPreferenceTreeClick(preference: Preference): Boolean {
            val ctx = requireContext()
            when (preference.key) {
                "sync_now" -> {
                    if (AppSettings(ctx).syncConfigured) {
                        HttpSmsSyncRepository.scheduleSync(ctx, immediate = true)
                        Toast.makeText(ctx, "Sync scheduled", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(ctx, "Enable sync and set the server URL first", Toast.LENGTH_SHORT).show()
                    }
                    return true
                }
                "sync_sign_in" -> {
                    val auth = AuthManager(ctx)
                    if (auth.isAuthorized) {
                        auth.signOut()
                        Toast.makeText(ctx, "Signed out", Toast.LENGTH_SHORT).show()
                        refreshSignInState()
                    } else {
                        startActivity(Intent(ctx, LoginActivity::class.java))
                    }
                    return true
                }
            }
            return super.onPreferenceTreeClick(preference)
        }
    }
}

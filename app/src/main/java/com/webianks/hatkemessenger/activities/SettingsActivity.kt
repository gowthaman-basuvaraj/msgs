package com.webianks.hatkemessenger.activities

import android.app.ProgressDialog
import android.os.Bundle
import android.preference.Preference
import android.preference.Preference.OnPreferenceClickListener
import android.preference.PreferenceFragment
import android.view.MenuItem
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.webianks.hatkemessenger.R
import com.webianks.hatkemessenger.constants.Constants

class SettingsActivity : AppCompatActivity() {

    private var progressDialog: ProgressDialog? = null


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.settings_layout)
        if (supportActionBar != null) supportActionBar!!.setDisplayHomeAsUpEnabled(true)
        fragmentManager.beginTransaction().replace(R.id.container,
                MyPreferenceFragment()).commit()

    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) finish()
        return super.onOptionsItemSelected(item)
    }

    class MyPreferenceFragment : PreferenceFragment() {
        private var drivePref: Preference? = null
        private var restorePref: Preference? = null
        private val TAG = MyPreferenceFragment::class.java.simpleName
        override fun onCreate(savedInstanceState: Bundle?) {
            super.onCreate(savedInstanceState)
            addPreferencesFromResource(R.xml.pref_general)
            drivePref = findPreference(Constants.BACKUP)
            drivePref?.onPreferenceClickListener = OnPreferenceClickListener {
                true
            }
            restorePref = findPreference(Constants.RESTORE)
            restorePref?.onPreferenceClickListener = OnPreferenceClickListener { //backup dialog
                true
            }
        }

    }


}
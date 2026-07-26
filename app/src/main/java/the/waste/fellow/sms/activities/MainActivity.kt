package the.waste.fellow.sms.activities

import android.Manifest
import android.app.SearchManager
import android.app.role.RoleManager
import android.content.*
import android.content.pm.PackageManager
import android.database.Cursor
import android.os.Build
import android.os.Bundle
import android.provider.Telephony
import android.text.TextUtils
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SearchView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.loader.app.LoaderManager
import androidx.loader.content.CursorLoader
import androidx.loader.content.Loader
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import the.waste.fellow.sms.R
import the.waste.fellow.sms.SMS
import the.waste.fellow.sms.adapters.AllConversationAdapter
import the.waste.fellow.sms.adapters.ItemCLickListener
import the.waste.fellow.sms.constants.Constants
import the.waste.fellow.sms.constants.SmsContract
import the.waste.fellow.sms.databinding.ActivityMainBinding
import the.waste.fellow.sms.utils.PersonLookup
import java.util.*
import kotlin.collections.ArrayList

class MainActivity : AppCompatActivity(),
        View.OnClickListener,
        ItemCLickListener,
        LoaderManager.LoaderCallbacks<Cursor?>,
        SearchView.OnQueryTextListener {

    private lateinit var binding: ActivityMainBinding
    private var allConversationAdapter: AllConversationAdapter? = null
    private var mCurFilter: String? = null
    private var data: MutableList<SMS> = arrayListOf()
    private var mReceiver: BroadcastReceiver? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        init()
    }

    private fun init() {
        binding.recyclerview.layoutManager = LinearLayoutManager(this)

        // Show the cached conversations immediately (instant on reopen); the loader then
        // refreshes them in the background. Create the adapter once and update it in place.
        data = cache.toMutableList()
        allConversationAdapter = AllConversationAdapter(this, data)
        allConversationAdapter?.setItemClickListener(this)
        binding.recyclerview.adapter = allConversationAdapter
        binding.progressBar.visibility = if (cache.isEmpty()) View.VISIBLE else View.GONE

        binding.fabNew.setOnClickListener(this)
        requestNotificationPermission()
        if (checkDefaultSettings()) checkPermissions()
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this, arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                Constants.MY_PERMISSIONS_REQUEST_POST_NOTIFICATIONS
            )
        }
    }

    private fun checkPermissions() {
        if (ContextCompat.checkSelfPermission(this,
                        Manifest.permission.READ_SMS)
                != PackageManager.PERMISSION_GRANTED) {

            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.READ_SMS),
                    Constants.MY_PERMISSIONS_REQUEST_READ_SMS)
        } else {
            if (ContextCompat.checkSelfPermission(this,
                            Manifest.permission.READ_CONTACTS)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.READ_CONTACTS),
                        Constants.MY_PERMISSIONS_REQUEST_READ_CONTACTS)
            } else {
                LoaderManager.getInstance(this).initLoader(Constants.ALL_SMS_LOADER, null, this)
            }
        }
    }

    private fun checkDefaultSettings(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = getSystemService(RoleManager::class.java)!!
            val b = roleManager.isRoleAvailable(RoleManager.ROLE_SMS) && roleManager.isRoleHeld(RoleManager.ROLE_SMS)
            if (!b) {
                val intent = roleManager.createRequestRoleIntent(RoleManager.ROLE_SMS)
                startActivityForResult(intent, Constants.MY_PERMISSIONS_REQUEST_READ_SMS)
            }
            b
        } else {
            if (Telephony.Sms.getDefaultSmsPackage(this) != packageName) {
                val builder = MaterialAlertDialogBuilder(this@MainActivity)
                builder.setMessage("This app is not set as your default messaging app. Do you want to set it as default?")
                        .setCancelable(false)
                        .setNegativeButton("No") { dialog: DialogInterface, _: Int ->
                            dialog.dismiss()
                            checkPermissions()
                        }
                        .setPositiveButton("Yes") { _, _ ->
                            val intent = Intent(Telephony.Sms.Intents.ACTION_CHANGE_DEFAULT)
                            intent.putExtra(Telephony.Sms.Intents.EXTRA_PACKAGE_NAME, packageName)
                            startActivity(intent)
                            checkPermissions()
                        }
                builder.show()
                false
            } else {
                true
            }
        }
    }

    override fun onClick(view: View) {
        if (view.id == R.id.fab_new) {
            startActivity(Intent(this, NewSMSActivity::class.java))
        }
    }

    override fun onResume() {
        super.onResume()

        val intentFilter = IntentFilter("android.intent.action.MAIN")

        mReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                val new_sms = intent.getBooleanExtra("new_sms", false)
                if (new_sms) supportLoaderManager.restartLoader(Constants.ALL_SMS_LOADER, null, this@MainActivity)
            }
        }
        ContextCompat.registerReceiver(
            this, mReceiver, intentFilter, ContextCompat.RECEIVER_NOT_EXPORTED
        )
        // initLoader (not restart): if the loader already exists it delivers its cached
        // result instantly and only re-queries when the SMS provider changes.
        supportLoaderManager.initLoader(Constants.ALL_SMS_LOADER, null, this@MainActivity)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.home_menu, menu)
        val searchManager = getSystemService(Context.SEARCH_SERVICE) as SearchManager
        val searchView = menu.findItem(R.id.ic_search).actionView as SearchView
        searchView.setSearchableInfo(searchManager.getSearchableInfo(componentName))
        searchView.setIconifiedByDefault(true)
        searchView.setOnQueryTextListener(this)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == R.id.ic_settings) {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        when (requestCode) {
            Constants.MY_PERMISSIONS_REQUEST_READ_SMS -> {
                run {
                    if (grantResults.isNotEmpty()
                            && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                        if (ContextCompat.checkSelfPermission(this,
                                        Manifest.permission.READ_CONTACTS)
                                != PackageManager.PERMISSION_GRANTED) {
                            if (ActivityCompat.shouldShowRequestPermissionRationale(this,
                                            Manifest.permission.READ_CONTACTS)) {
                            } else {
                                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.READ_CONTACTS),
                                        Constants.MY_PERMISSIONS_REQUEST_READ_CONTACTS)
                            }
                        } else LoaderManager.getInstance(this@MainActivity).initLoader(Constants.ALL_SMS_LOADER, null, this)
                    } else {
                        Toast.makeText(applicationContext,
                                "Can't access messages.", Toast.LENGTH_LONG).show()
                        return
                    }
                }
                run {
                    if (grantResults.isNotEmpty()
                            && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                        LoaderManager.getInstance(this@MainActivity).initLoader(Constants.ALL_SMS_LOADER, null, this)
                    } else {
                        Toast.makeText(applicationContext,
                                "Can't access messages.", Toast.LENGTH_LONG).show()
                        return
                    }
                }
            }
            Constants.MY_PERMISSIONS_REQUEST_READ_CONTACTS -> {
                if (grantResults.isNotEmpty()
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    LoaderManager.getInstance(this@MainActivity).initLoader(Constants.ALL_SMS_LOADER, null, this)
                } else {
                    LoaderManager.getInstance(this@MainActivity).initLoader(Constants.ALL_SMS_LOADER, null, this)
                    return
                }
            }
        }
    }

    override fun itemClicked(color: Int, contact: String?, savedContactName: String?, id: Long, read: String?) {
        val intent = Intent(this, SmsDetailedView::class.java)
        intent.putExtra(Constants.CONTACT_NAME, contact)
        intent.putExtra(Constants.SAVED_CONTACT_NAME, savedContactName);
        intent.putExtra(Constants.COLOR, color)
        intent.putExtra(Constants.SMS_ID, id)
        intent.putExtra(Constants.READ, read)
        startActivity(intent)
    }

    override fun onCreateLoader(id: Int, args: Bundle?): Loader<Cursor?> {
        var selection: String? = null
        var selectionArgs: Array<String>? = null
        if (mCurFilter != null) {
            selection = SmsContract.SMS_SELECTION_SEARCH
            selectionArgs = arrayOf("%$mCurFilter%", "%$mCurFilter%")
        }
        return CursorLoader(this,
                SmsContract.INBOX_URI,
                null,
                selection,
                selectionArgs,
                SmsContract.SORT_DESC + " limit 2500")
    }

    override fun onLoadFinished(loader: Loader<Cursor?>, cursor: Cursor?) {
        if (cursor == null) {
            binding.progressBar.visibility = View.GONE
            return
        }
        // Read the cursor quickly on the main thread, then do the heavy work (contact
        // lookups + dedup) off-thread so the UI never janks. The old list stays visible
        // until the new one is ready — no clear, no flash.
        val rows = extractRows(cursor)
        val searching = mCurFilter != null
        Thread {
            val processed = processRows(rows)
            runOnUiThread {
                if (isFinishing) return@runOnUiThread
                if (!searching) cache = processed
                data = processed.toMutableList()
                allConversationAdapter?.submit(processed)
                binding.progressBar.visibility = View.GONE
            }
        }.start()
    }

    override fun onLoaderReset(loader: Loader<Cursor?>) {
        // Keep the cached list visible; nothing to clear.
    }

    override fun onQueryTextSubmit(query: String): Boolean {
        mCurFilter = if (!TextUtils.isEmpty(query)) query else null
        supportLoaderManager.restartLoader(Constants.ALL_SMS_LOADER, null, this)
        return true
    }

    override fun onQueryTextChange(newText: String): Boolean {
        mCurFilter = if (!TextUtils.isEmpty(newText)) newText else null
        supportLoaderManager.restartLoader(Constants.ALL_SMS_LOADER, null, this)
        return true
    }

    override fun onPause() {
        super.onPause()
        unregisterReceiver(mReceiver)
        // Keep the loader alive so reopening shows data instantly (no reload/flash).
    }

    private val lookup = PersonLookup(this)

    private class RawRow(
        val id: Long, val address: String?, val body: String?,
        val read: String?, val date: Long, val type: String?
    )

    /** Fast, main-thread copy of the cursor rows (no contact lookups here). */
    private fun extractRows(c: Cursor): List<RawRow> {
        val rows = ArrayList<RawRow>(c.count)
        if (c.moveToFirst()) {
            val idI = c.getColumnIndexOrThrow("_id")
            val addrI = c.getColumnIndexOrThrow("address")
            val bodyI = c.getColumnIndexOrThrow("body")
            val readI = c.getColumnIndex("read")
            val dateI = c.getColumnIndexOrThrow("date")
            val typeI = c.getColumnIndex("type")
            do {
                rows.add(
                    RawRow(
                        c.getLong(idI),
                        c.getString(addrI),
                        c.getString(bodyI),
                        if (readI >= 0) c.getString(readI) else "1",
                        c.getLong(dateI),
                        if (typeI >= 0) c.getString(typeI) else "1"
                    )
                )
            } while (c.moveToNext())
        }
        // Don't close the cursor — the CursorLoader owns it.
        return rows
    }

    /** Off-thread: normalize senders, resolve names, and dedup to one row per sender. */
    private fun processRows(rows: List<RawRow>): List<SMS> {
        val list = ArrayList<SMS>(rows.size)
        for (r in rows) {
            val sms = SMS()
            sms.id = r.id
            val lp = lookup.lookupPerson(r.address)
            sms.address = r.address
            sms.normAddress = lp?.normPhone ?: r.address
            sms.displayName = lp?.name ?: r.address
            sms.msg = r.body
            sms.readState = r.read
            sms.time = r.date
            sms.folderName = if (r.type?.contains("1") == true) "inbox" else "sent"
            list.add(sms)
        }
        return ArrayList(LinkedHashSet(list))   // dedup by normAddress (SMS.equals)
    }

    companion object {
        // Processed conversation list cached in-memory so reopening is instant.
        private var cache: List<SMS> = emptyList()
    }
}
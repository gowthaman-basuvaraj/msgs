package the.waste.fellow.sms.activities

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.database.Cursor
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.Toast
import the.waste.fellow.sms.utils.SenderNormalizer
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import the.waste.fellow.sms.utils.SmsSender
import androidx.loader.app.LoaderManager
import androidx.loader.content.CursorLoader
import androidx.loader.content.Loader
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import the.waste.fellow.sms.R
import the.waste.fellow.sms.adapters.SingleGroupAdapter
import the.waste.fellow.sms.constants.Constants
import the.waste.fellow.sms.constants.SmsContract
import the.waste.fellow.sms.receivers.DeliverReceiver
import the.waste.fellow.sms.receivers.SentReceiver
import the.waste.fellow.sms.services.UpdateSMSService

class SmsDetailedView : AppCompatActivity(),
        LoaderManager.LoaderCallbacks<Cursor?>,
        View.OnClickListener {

    private var contact: String? = null
    private var savedContactName: String? = null
    private var singleGroupAdapter: SingleGroupAdapter? = null
    private var recyclerView: RecyclerView? = null
    private var etMessage: EditText? = null
    private var btSend: View? = null
    private var replyBar: View? = null
    private var isPersonal = false
    private var message: String? = null
    private var from_reciever = false

    private var _Id: Long = 0
    private var color = 0
    private var read: String? = "1"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_sms_detailed_view)
        supportActionBar!!.setDisplayHomeAsUpEnabled(true)
        init()
    }

    private fun init() {
        val intent = intent
        contact = intent.getStringExtra(Constants.CONTACT_NAME)
        savedContactName = intent.getStringExtra(Constants.SAVED_CONTACT_NAME)
        _Id = intent.getLongExtra(Constants.SMS_ID, -123)
        color = intent.getIntExtra(Constants.COLOR, 0)
        read = intent.getStringExtra(Constants.READ)
        from_reciever = intent.getBooleanExtra(Constants.FROM_SMS_RECIEVER, false)
        if (supportActionBar != null) {
            if (savedContactName == null)
                supportActionBar!!.setTitle(contact)
            else
                supportActionBar!!.setTitle(savedContactName)
        }
        // Only real phone numbers get a reply box + chat bubbles; alphanumeric sender ids
        // (banks, OTPs) are one-way and shown full-width.
        isPersonal = SenderNormalizer.isPhoneNumber(contact)

        recyclerView = findViewById(R.id.recyclerview)
        val linearLayoutManager = LinearLayoutManager(this)
        linearLayoutManager.reverseLayout = true
        recyclerView?.layoutManager = linearLayoutManager
        etMessage = findViewById(R.id.etMessage)
        btSend = findViewById(R.id.btSend)
        btSend?.setOnClickListener(this)
        replyBar = findViewById(R.id.replyBar)

        if (!isPersonal) {
            replyBar?.visibility = View.GONE
            // Reclaim the space the reply bar would have occupied.
            (recyclerView?.layoutParams as? FrameLayout.LayoutParams)?.let {
                it.bottomMargin = 0
                recyclerView?.layoutParams = it
            }
        }

        setRecyclerView(null)
        if (read != null && read == "0") setReadSMS()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            if (from_reciever) startActivity(Intent(this, MainActivity::class.java))
            finish()
        }
        return super.onOptionsItemSelected(item)
    }


    private fun setRecyclerView(cursor: Cursor?) {
        singleGroupAdapter = SingleGroupAdapter(this, cursor, isPersonal)
        recyclerView!!.adapter = singleGroupAdapter
    }

    override fun onResume() {
        super.onResume()
        LoaderManager.getInstance(this).initLoader(Constants.CONVERSATION_LOADER, null, this)
    }

    override fun onCreateLoader(id: Int, args: Bundle?): Loader<Cursor?> {
        val selectionArgs = arrayOf(contact)
        return CursorLoader(this,
                SmsContract.CONVERSATION_URI,
                null,
                SmsContract.SMS_SELECTION,
                selectionArgs,
                SmsContract.SORT_ASC)
    }

    override fun onLoadFinished(loader: Loader<Cursor?>, cursor: Cursor?) {
        if (cursor != null && cursor.count > 0) {
            singleGroupAdapter!!.swapCursor(cursor)
        } //no sms
    }

    private fun setReadSMS() {
        val intent = Intent(this, UpdateSMSService::class.java)
        intent.putExtra("id", _Id)
        startService(intent)
    }

    override fun onLoaderReset(loader: Loader<Cursor?>) {
        singleGroupAdapter!!.swapCursor(null)
    }

    override fun onClick(view: View) {
        if (view.id == R.id.btSend) {
            sendSMSMessage()
        }
    }

    protected fun sendSMSMessage() {
        message = etMessage!!.text.toString()
        if (message!!.trim { it <= ' ' }.isNotEmpty())
            requestPermissions() else etMessage!!.error = getString(R.string.please_write_message)
    }

    private fun requestPermissions() = if (ContextCompat.checkSelfPermission(this,
                    Manifest.permission.SEND_SMS)
            != PackageManager.PERMISSION_GRANTED) {
        if (ActivityCompat.shouldShowRequestPermissionRationale(this,
                        Manifest.permission.SEND_SMS)) {
        } else {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.SEND_SMS),
                    Constants.MY_PERMISSIONS_REQUEST_SEND_SMS)
        }
    } else {
        sendSMSNow()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        if (requestCode == Constants.MY_PERMISSIONS_REQUEST_SEND_SMS) {
            if (grantResults.isNotEmpty()
                    && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                sendSMSNow()
            } else {
                Toast.makeText(applicationContext,
                        "SMS failed, please try again.", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun sendSMSNow() {
        val sendBroadcastReceiver: BroadcastReceiver = SentReceiver()
        val deliveryBroadcastReceiver: BroadcastReceiver = DeliverReceiver()
        ContextCompat.registerReceiver(
            this, sendBroadcastReceiver, IntentFilter(SmsSender.ACTION_SENT),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        ContextCompat.registerReceiver(
            this, deliveryBroadcastReceiver, IntentFilter(SmsSender.ACTION_DELIVERED),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        try {
            SmsSender.send(this, contact!!, message!!)
            etMessage?.text?.clear()
        } catch (e: Exception) {
            Toast.makeText(this, getString(R.string.cant_send), Toast.LENGTH_SHORT).show()
        }
    }

    override fun onBackPressed() {
        if (from_reciever) {
            startActivity(Intent(this, MainActivity::class.java))
        } else super.onBackPressed()
    }
}
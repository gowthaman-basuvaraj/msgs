package the.waste.fellow.sms.services

import android.app.IntentService
import android.content.ContentValues
import android.content.Intent
import the.waste.fellow.sms.constants.SmsContract
import the.waste.fellow.sms.sync.SmsSync
import the.waste.fellow.sms.sync.SyncDirection
import the.waste.fellow.sms.sync.SyncableSms

/**
 * Created by R Ankit on 26-12-2016.
 * Persists an incoming SMS to the provider and hands it to the sync hook.
 */
class SaveSmsService : IntentService("SaveService") {
    override fun onHandleIntent(intent: Intent?) {
        val senderNo = intent!!.getStringExtra("sender_no")
        val message = intent.getStringExtra("message")
        val time = intent.getLongExtra("date", 0)
        val values = ContentValues()
        values.put("address", senderNo)
        values.put("body", message)
        values.put("date_sent", time)
        contentResolver.insert(SmsContract.INBOX_URI, values)

        // Phase-2 sync hook (no-op by default).
        SmsSync.repository.enqueueInbound(
            SyncableSms(senderNo.orEmpty(), message.orEmpty(), time, SyncDirection.INBOUND)
        )

        val i = Intent("android.intent.action.MAIN")
            .setPackage(packageName)
            .putExtra("new_sms", true)
        this.sendBroadcast(i)
    }
}

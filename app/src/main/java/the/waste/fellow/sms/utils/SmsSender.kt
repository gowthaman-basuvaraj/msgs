package the.waste.fellow.sms.utils

import android.app.PendingIntent
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Telephony
import android.telephony.SmsManager
import the.waste.fellow.sms.constants.SmsContract
import the.waste.fellow.sms.sync.SmsSync
import the.waste.fellow.sms.sync.SyncDirection
import the.waste.fellow.sms.sync.SyncableSms
import java.util.concurrent.atomic.AtomicInteger

/**
 * One place that actually sends an SMS the way a default SMS app should:
 *  - modern [SmsManager] acquisition (system service on API 31+, getDefault() fallback),
 *  - multipart splitting for long messages,
 *  - immutable PendingIntents with unique request codes so concurrent sends don't clobber
 *    each other's result callbacks,
 *  - persisting a copy to content://sms/sent so it shows up in the conversation thread,
 *  - handing the outbound message to the sync hook.
 */
object SmsSender {

    const val ACTION_SENT = "the.waste.fellow.sms.SMS_SENT"
    const val ACTION_DELIVERED = "the.waste.fellow.sms.SMS_DELIVERED"

    private const val DELIVERED_OFFSET = 100_000
    private val requestCode = AtomicInteger(0)

    private val immutableFlag =
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE

    /** Sends [message] to [address], persists the sent copy, and returns nothing. Throws on failure. */
    fun send(context: Context, address: String, message: String) {
        val app = context.applicationContext
        val smsManager = smsManager(app)
        val parts = smsManager.divideMessage(message)

        val base = requestCode.getAndAdd(parts.size)
        val sentIntents = ArrayList<PendingIntent>(parts.size)
        val deliveredIntents = ArrayList<PendingIntent>(parts.size)
        for (i in parts.indices) {
            sentIntents.add(broadcast(app, base + i, ACTION_SENT))
            deliveredIntents.add(broadcast(app, DELIVERED_OFFSET + base + i, ACTION_DELIVERED))
        }

        if (parts.size > 1) {
            smsManager.sendMultipartTextMessage(address, null, parts, sentIntents, deliveredIntents)
        } else {
            smsManager.sendTextMessage(address, null, message, sentIntents[0], deliveredIntents[0])
        }

        persistSent(app, address, message)
    }

    private fun broadcast(context: Context, code: Int, action: String): PendingIntent {
        val intent = Intent(action).setPackage(context.packageName)
        return PendingIntent.getBroadcast(context, code, intent, immutableFlag)
    }

    private fun persistSent(context: Context, address: String, message: String) {
        val now = System.currentTimeMillis()
        val values = ContentValues().apply {
            put(Telephony.Sms.ADDRESS, address)
            put(Telephony.Sms.BODY, message)
            put(Telephony.Sms.DATE, now)
            put(Telephony.Sms.READ, 1)
            put(Telephony.Sms.TYPE, Telephony.Sms.MESSAGE_TYPE_SENT)
        }
        runCatching { context.contentResolver.insert(SmsContract.SENT_URI, values) }

        SmsSync.repository.enqueueOutbound(
            SyncableSms(address, message, now, SyncDirection.OUTBOUND)
        )

        // Nudge the list/thread to refresh.
        context.sendBroadcast(
            Intent("android.intent.action.MAIN")
                .setPackage(context.packageName)
                .putExtra("new_sms", true)
        )
    }

    @Suppress("DEPRECATION")
    private fun smsManager(context: Context): SmsManager =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(SmsManager::class.java)
        } else {
            SmsManager.getDefault()
        }
}

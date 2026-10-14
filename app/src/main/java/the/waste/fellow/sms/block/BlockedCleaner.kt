package the.waste.fellow.sms.block

import android.content.Context
import android.content.Intent
import the.waste.fellow.sms.constants.SmsContract
import the.waste.fellow.sms.sync.PendingSyncStore
import the.waste.fellow.sms.utils.AppSettings

/**
 * Cleanup that runs when a sender is blocked. Incoming messages are dropped before they are
 * ever stored (see [the.waste.fellow.sms.receivers.SmsReceiver]), so this only deals with
 * what already landed before the block: messages in the SMS provider and entries still
 * sitting in the upload queue.
 */
object BlockedCleaner {

    /**
     * Deletes every stored message whose sender normalizes to [sender]. Matching is done on
     * the normalized form so raw headers (AX-INDPOST-S, VM-INDPOST) are covered too.
     * Returns the number of messages deleted. Call off the main thread.
     */
    fun deleteMessagesFrom(context: Context, sender: String): Int {
        if (sender.isEmpty()) return 0
        val settings = AppSettings(context)
        val ids = ArrayList<Long>()
        context.contentResolver.query(
            SmsContract.CONVERSATION_URI,
            arrayOf(SmsContract.COLUMN_ID, "address"),
            null,
            null,
            null
        )?.use { c ->
            val idIdx = c.getColumnIndexOrThrow(SmsContract.COLUMN_ID)
            val addrIdx = c.getColumnIndexOrThrow("address")
            while (c.moveToNext()) {
                val address = c.getString(addrIdx) ?: continue
                if (settings.normalizeSender(address) == sender) ids.add(c.getLong(idIdx))
            }
        }
        if (ids.isEmpty()) return 0

        var deleted = 0
        ids.chunked(400).forEach { chunk ->
            deleted += context.contentResolver.delete(
                SmsContract.CONVERSATION_URI,
                "${SmsContract.COLUMN_ID} IN (${chunk.joinToString(",")})",
                null
            )
        }
        return deleted
    }

    /**
     * Drops anything from [sender] that was queued for upload before the block, so blocking
     * also stops messages that haven't been flushed to the server yet.
     */
    fun purgeQueuedSync(context: Context, sender: String): Int {
        val store = PendingSyncStore(context)
        val stale = store.all().filter { it.sender == sender }.map { it.id }
        store.remove(stale)
        return stale.size
    }

    /** Tells the conversation list to reload (same signal the receive path sends). */
    fun notifyListChanged(context: Context) {
        val app = context.applicationContext
        app.sendBroadcast(
            Intent("android.intent.action.MAIN")
                .setPackage(app.packageName)
                .putExtra("new_sms", true)
        )
    }
}

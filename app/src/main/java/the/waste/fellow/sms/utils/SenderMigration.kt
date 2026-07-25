package the.waste.fellow.sms.utils

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.provider.Telephony
import android.util.Log

/**
 * One-off migration that rewrites the `address` of already-stored messages to the grouped
 * (normalized) form — e.g. AX-INDPOST-S → INDPOST. New messages are already saved
 * normalized by SmsReceiver; this only fixes history received before the app was updated
 * (or by a previous SMS app), so a per-sender chat shows every message across prefix/suffix
 * variants and the conversation list stays consistent.
 *
 * Only alphanumeric sender ids change — real phone numbers normalize to themselves and are
 * left untouched. Runs off the main thread.
 */
object SenderMigration {

    private const val TAG = "SenderMigration"
    private val ALL_SMS: Uri = Uri.parse("content://sms")

    /** @return the number of messages whose address was rewritten. */
    fun run(context: Context): Int {
        val settings = AppSettings(context)
        val resolver = context.contentResolver
        var updated = 0

        resolver.query(
            ALL_SMS,
            arrayOf(Telephony.Sms._ID, Telephony.Sms.ADDRESS),
            null, null, null
        )?.use { cursor ->
            val idIndex = cursor.getColumnIndexOrThrow(Telephony.Sms._ID)
            val addressIndex = cursor.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)
            while (cursor.moveToNext()) {
                val id = cursor.getLong(idIndex)
                val address = cursor.getString(addressIndex) ?: continue
                val normalized = settings.normalizeSender(address)
                if (normalized.isNotEmpty() && normalized != address) {
                    val values = ContentValues().apply { put(Telephony.Sms.ADDRESS, normalized) }
                    updated += runCatching {
                        resolver.update(Uri.withAppendedPath(ALL_SMS, id.toString()), values, null, null)
                    }.getOrElse {
                        Log.w(TAG, "Failed to update message $id", it)
                        0
                    }
                }
            }
        }
        Log.d(TAG, "Normalized $updated message addresses")
        return updated
    }
}

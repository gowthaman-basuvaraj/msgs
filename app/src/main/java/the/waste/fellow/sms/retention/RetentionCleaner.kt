package the.waste.fellow.sms.retention

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import the.waste.fellow.sms.constants.SmsContract
import the.waste.fellow.sms.utils.AppSettings
import java.util.concurrent.TimeUnit

/**
 * Trims each capped sender down to its newest N messages, deleting the rest. Only senders
 * with an explicit cap are touched; everything else is kept in full (the default).
 *
 * A message's sender is matched by normalizing its stored address with the current settings,
 * so a cap set on the grouped id (e.g. "INDPOST") covers every raw header that maps to it
 * (AX-INDPOST-S, VM-INDPOST, …) whether or not the SMS database was normalized in place.
 */
object RetentionCleaner {

    /** Runs a cleanup pass synchronously; returns the number of messages deleted. */
    fun run(context: Context): Int {
        val rules = SenderRetentionPrefs(context).all()
        if (rules.isEmpty()) return 0

        val settings = AppSettings(context)
        // Walk newest → oldest; keep the first `keep` per sender, delete the rest.
        val kept = HashMap<String, Int>()
        val ids = ArrayList<Long>()
        context.contentResolver.query(
            SmsContract.CONVERSATION_URI,
            arrayOf(SmsContract.COLUMN_ID, "address"),
            null,
            null,
            SmsContract.SORT_DESC
        )?.use { c ->
            val idIdx = c.getColumnIndexOrThrow(SmsContract.COLUMN_ID)
            val addrIdx = c.getColumnIndexOrThrow("address")
            while (c.moveToNext()) {
                val address = c.getString(addrIdx) ?: continue
                val sender = settings.normalizeSender(address)
                val keep = rules[sender] ?: continue
                val soFar = kept.getOrDefault(sender, 0)
                if (soFar >= keep) ids.add(c.getLong(idIdx)) else kept[sender] = soFar + 1
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

    /** One-off cleanup (e.g. right after a rule changes). Coalesces with a pending pass. */
    fun scheduleNow(context: Context) {
        WorkManager.getInstance(context).enqueueUniqueWork(
            RetentionWorker.WORK_NAME,
            ExistingWorkPolicy.KEEP,
            OneTimeWorkRequestBuilder<RetentionWorker>().build()
        )
    }

    /** Hourly background cleanup, so retention applies even when the app is rarely opened. */
    fun schedulePeriodic(context: Context) {
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            RetentionWorker.PERIODIC_NAME,
            // UPDATE (not KEEP) so a change to the interval replaces any previously-scheduled
            // work instead of being ignored.
            ExistingPeriodicWorkPolicy.UPDATE,
            PeriodicWorkRequestBuilder<RetentionWorker>(1, TimeUnit.HOURS).build()
        )
    }
}

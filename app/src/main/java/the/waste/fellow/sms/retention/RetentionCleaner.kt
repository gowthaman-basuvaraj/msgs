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
 * Deletes messages that have outlived their sender's retention window. Only senders with an
 * explicit window are touched; everything else is kept forever (the default).
 *
 * A message's sender is matched by normalizing its stored address with the current settings,
 * so a rule set on the grouped id (e.g. "INDPOST") covers every raw header that maps to it
 * (AX-INDPOST-S, VM-INDPOST, …) whether or not the SMS database was normalized in place.
 */
object RetentionCleaner {

    private const val DAY_MS = 86_400_000L

    /** Runs a cleanup pass synchronously; returns the number of messages deleted. */
    fun run(context: Context): Int {
        val rules = SenderRetentionPrefs(context).all()
        if (rules.isEmpty()) return 0

        val settings = AppSettings(context)
        val now = System.currentTimeMillis()
        // A message can only qualify if it is older than the strictest window, so pre-filter
        // to that horizon and decide per-message against its own sender's rule.
        val newestCutoff = now - rules.values.min() * DAY_MS

        val ids = ArrayList<Long>()
        context.contentResolver.query(
            SmsContract.CONVERSATION_URI,
            arrayOf(SmsContract.COLUMN_ID, "address", "date"),
            "date < ?",
            arrayOf(newestCutoff.toString()),
            null
        )?.use { c ->
            val idIdx = c.getColumnIndexOrThrow(SmsContract.COLUMN_ID)
            val addrIdx = c.getColumnIndexOrThrow("address")
            val dateIdx = c.getColumnIndexOrThrow("date")
            while (c.moveToNext()) {
                val address = c.getString(addrIdx) ?: continue
                val days = rules[settings.normalizeSender(address)] ?: continue
                if (c.getLong(dateIdx) < now - days * DAY_MS) ids.add(c.getLong(idIdx))
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

    /** Daily background cleanup, so retention applies even when the app is rarely opened. */
    fun schedulePeriodic(context: Context) {
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            RetentionWorker.PERIODIC_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<RetentionWorker>(1, TimeUnit.DAYS)
                .setInitialDelay(1, TimeUnit.HOURS)
                .build()
        )
    }
}

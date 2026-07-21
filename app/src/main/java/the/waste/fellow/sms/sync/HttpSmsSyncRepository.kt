package the.waste.fellow.sms.sync

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import the.waste.fellow.sms.utils.AppSettings
import java.util.concurrent.TimeUnit

/**
 * Real sync implementation: queues received messages and lets [SyncWorker] upload them to
 * the sms_web_api server. Outbound (sent) messages are intentionally not synced — the
 * server models a received-SMS forwarder, grouped by sender.
 *
 * Enqueuing is cheap and offline-safe: it appends to [PendingSyncStore] and schedules a
 * network-constrained worker; nothing blocks the SMS receive path.
 */
class HttpSmsSyncRepository(context: Context) : SmsSyncRepository {

    private val app = context.applicationContext
    private val store = PendingSyncStore(app)
    private val settings = AppSettings(app)

    override fun enqueueInbound(message: SyncableSms) {
        if (!settings.syncEnabled) return
        store.add(message.address, message.body, message.date)
        scheduleSync(app)
    }

    override fun enqueueOutbound(message: SyncableSms) {
        // Inbound-only by design (server is a received-SMS forwarder).
    }

    override fun pendingCount(): Int = store.size()

    companion object {
        /**
         * Enqueues a network-constrained, coalesced sync pass.
         *
         * @param immediate when true (the "Sync now" button), REPLACE any pending/backing-off
         *   work so it runs right away; otherwise KEEP — a new message rides an already-pending
         *   pass rather than resetting its backoff.
         */
        fun scheduleSync(context: Context, immediate: Boolean = false) {
            val request = OneTimeWorkRequestBuilder<SyncWorker>()
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build()
            val policy = if (immediate) ExistingWorkPolicy.REPLACE else ExistingWorkPolicy.KEEP
            WorkManager.getInstance(context)
                .enqueueUniqueWork(SyncWorker.WORK_NAME, policy, request)
        }
    }
}

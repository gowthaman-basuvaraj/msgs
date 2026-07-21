package the.waste.fellow.sms.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

/**
 * Phase-2 hook: background worker that will flush queued messages to the server.
 *
 * Deliberately a no-op stub — it is NOT scheduled anywhere yet. When server sync is built:
 *   1. Provide a real [SmsSyncRepository] and assign it to [SmsSync.repository].
 *   2. Drain the queue here and POST to the endpoint.
 *   3. Enqueue this worker (e.g. periodic + on-connectivity constraints) from Application.
 */
class SyncWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        // TODO(sync): drain SmsSync.repository queue and upload to the personal server.
        return Result.success()
    }

    companion object {
        const val WORK_NAME = "sms-sync"
    }
}

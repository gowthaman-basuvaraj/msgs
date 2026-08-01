package the.waste.fellow.sms.retention

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Runs [RetentionCleaner] off the main thread, on demand and on a daily schedule. */
class RetentionWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val deleted = RetentionCleaner.run(applicationContext)
        if (deleted > 0) Log.i(TAG, "Deleted $deleted message(s) past retention")
        Result.success()
    }

    companion object {
        const val WORK_NAME = "retention_cleanup"
        const val PERIODIC_NAME = "retention_cleanup_periodic"
        private const val TAG = "RetentionWorker"
    }
}

package the.waste.fellow.sms.sync

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import the.waste.fellow.sms.auth.AuthManager
import the.waste.fellow.sms.utils.AppSettings

/**
 * Drains [PendingSyncStore], uploading each queued message to the sms_web_api server via
 * [SyncApi]. Successful and permanently-failed entries are removed; transient failures
 * (offline, expired token, 5xx) are kept and the worker asks WorkManager to retry with
 * backoff — so once the user pastes a fresh token or regains connectivity, the queue flushes.
 */
class SyncWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val settings = AppSettings(applicationContext)
        if (!settings.syncConfigured) return@withContext Result.success()

        val store = PendingSyncStore(applicationContext)
        val entries = store.all()
        if (entries.isEmpty()) return@withContext Result.success()

        // Prefer a fresh OIDC access token (auto-refreshed); fall back to a pasted token.
        val authManager = AuthManager(applicationContext)
        val token = authManager.freshAccessToken() ?: settings.syncToken

        // Account username: explicit setting, else the signed-in user's preferred_username.
        val userName = settings.syncUserName.ifBlank { authManager.userName.orEmpty() }
        if (userName.isBlank()) {
            Log.w(TAG, "No username (set one or sign in) — keeping ${entries.size} queued")
            return@withContext Result.retry()
        }

        val done = mutableListOf<Long>()
        var retryNeeded = false

        for (entry in entries) {
            val outcome = SyncApi.postSendMessage(
                baseUrl = settings.syncBaseUrl,
                token = token,
                userName = userName,
                sender = entry.sender,
                text = entry.text,
                sim = settings.syncSim,
            )
            when (outcome) {
                SyncApi.Outcome.SUCCESS,
                SyncApi.Outcome.PERMANENT_FAILURE -> done.add(entry.id)
                SyncApi.Outcome.TRANSIENT_FAILURE -> {
                    retryNeeded = true
                    // Stop on the first transient error to preserve FIFO order and avoid
                    // hammering an unreachable/unauthorized server.
                    break
                }
            }
        }

        store.remove(done)
        Log.d(TAG, "Uploaded ${done.size}, ${store.size()} remaining, retry=$retryNeeded")

        if (retryNeeded) Result.retry() else Result.success()
    }

    companion object {
        private const val TAG = "SyncWorker"
        const val WORK_NAME = "sms-sync"
    }
}

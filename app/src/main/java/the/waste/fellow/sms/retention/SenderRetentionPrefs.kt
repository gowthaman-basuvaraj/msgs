package the.waste.fellow.sms.retention

import android.content.Context

/**
 * Per-sender message retention, as the number of newest messages to keep. The default is
 * "keep everything" — a sender only has a cap if the user sets one. 0 (or an unset key) means
 * keep everything.
 *
 * Keys are the normalized sender id (same key space as [the.waste.fellow.sms.notify.SenderNotifyPrefs]).
 */
class SenderRetentionPrefs(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** How many of the newest messages to keep for [sender], or 0 for "keep everything". */
    fun keepCount(sender: String): Int = prefs.getInt(sender, 0)

    fun set(sender: String, keepCount: Int) {
        prefs.edit().apply {
            if (keepCount <= 0) remove(sender) else putInt(sender, keepCount)
        }.apply()
    }

    /** Only the senders that actually have a cap (keep count > 0). */
    fun all(): Map<String, Int> =
        prefs.all.entries.mapNotNull { (key, value) ->
            (value as? Int)?.takeIf { it > 0 }?.let { key to it }
        }.toMap()

    companion object {
        private const val PREFS_NAME = "sender_retention"
    }
}

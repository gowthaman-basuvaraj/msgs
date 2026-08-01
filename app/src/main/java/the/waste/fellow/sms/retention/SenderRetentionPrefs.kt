package the.waste.fellow.sms.retention

import android.content.Context

/**
 * Per-sender message retention, in days. The default is "keep forever" — a sender only has a
 * finite window if the user sets one. 0 (or an unset key) means forever.
 *
 * Keys are the normalized sender id (same key space as [the.waste.fellow.sms.notify.SenderNotifyPrefs]).
 */
class SenderRetentionPrefs(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** Retention window for [sender] in days, or 0 for "keep forever". */
    fun days(sender: String): Int = prefs.getInt(sender, 0)

    fun set(sender: String, days: Int) {
        prefs.edit().apply {
            if (days <= 0) remove(sender) else putInt(sender, days)
        }.apply()
    }

    /** Only the senders that actually have a retention window (days > 0). */
    fun all(): Map<String, Int> =
        prefs.all.entries.mapNotNull { (key, value) ->
            (value as? Int)?.takeIf { it > 0 }?.let { key to it }
        }.toMap()

    companion object {
        private const val PREFS_NAME = "sender_retention"
    }
}

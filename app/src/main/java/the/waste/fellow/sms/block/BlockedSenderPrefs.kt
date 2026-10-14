package the.waste.fellow.sms.block

import android.content.Context

/**
 * The set of blocked senders. A blocked sender's incoming messages are dropped the moment
 * they arrive: never written to the SMS provider, never notified, never synced to the server.
 *
 * Keys are the normalized sender id (same key space as
 * [the.waste.fellow.sms.notify.SenderNotifyPrefs] and
 * [the.waste.fellow.sms.retention.SenderRetentionPrefs]), so a block on the grouped id
 * (e.g. "INDPOST") covers every raw header that maps to it (AX-INDPOST-S, VM-INDPOST, …).
 * Callers pass an already-normalized sender; the stored value is when the block was added.
 */
class BlockedSenderPrefs(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun isBlocked(sender: String?): Boolean =
        !sender.isNullOrEmpty() && prefs.contains(sender)

    fun block(sender: String) {
        if (sender.isEmpty()) return
        prefs.edit().putLong(sender, System.currentTimeMillis()).apply()
    }

    fun unblock(sender: String) {
        prefs.edit().remove(sender).apply()
    }

    /** Blocked senders paired with the time each was blocked, most recently blocked first. */
    fun all(): List<Pair<String, Long>> =
        prefs.all.entries
            .mapNotNull { (key, value) -> (value as? Long)?.let { key to it } }
            .sortedByDescending { it.second }

    fun count(): Int = prefs.all.size

    companion object {
        private const val PREFS_NAME = "sender_blocked"
    }
}

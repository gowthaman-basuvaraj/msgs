package the.waste.fellow.sms.notify

import android.content.Context

/**
 * Per-sender notification preference (three states):
 *  - [MUTED]   : never notify — everything from this sender is silent.
 *  - [UNMUTED] : notify for every message.
 *  - [DEFAULT] : neither muted nor unmuted — notify only when the message contains an OTP.
 */
enum class NotifyState { MUTED, DEFAULT, UNMUTED }

class SenderNotifyPrefs(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun state(sender: String): NotifyState = when (prefs.getString(sender, null)) {
        VALUE_MUTED -> NotifyState.MUTED
        VALUE_UNMUTED -> NotifyState.UNMUTED
        else -> NotifyState.DEFAULT
    }

    fun set(sender: String, state: NotifyState) {
        prefs.edit().apply {
            when (state) {
                NotifyState.DEFAULT -> remove(sender)
                NotifyState.MUTED -> putString(sender, VALUE_MUTED)
                NotifyState.UNMUTED -> putString(sender, VALUE_UNMUTED)
            }
        }.apply()
    }

    companion object {
        private const val PREFS_NAME = "sender_notify"
        private const val VALUE_MUTED = "muted"
        private const val VALUE_UNMUTED = "unmuted"
    }
}

package the.waste.fellow.sms.utils

import android.content.Context
import androidx.preference.PreferenceManager

/**
 * Thin synchronous settings facade over default SharedPreferences. Synchronous access
 * keeps it usable from BroadcastReceivers and RecyclerView adapters without coroutines.
 * The settings UI (see pref_general.xml / RulesActivity) writes the same keys.
 */
class AppSettings(context: Context) {

    private val prefs = PreferenceManager.getDefaultSharedPreferences(context.applicationContext)

    /** Category-suffix letters eligible for stripping, e.g. "GSPT". */
    val suffixLetters: Set<Char>
        get() = (prefs.getString(KEY_SUFFIX_LETTERS, DEFAULT_SUFFIX_STRING) ?: DEFAULT_SUFFIX_STRING)
            .uppercase()
            .filter { it.isLetter() }
            .toSet()

    /** Best-effort stripping of a glued (non-hyphenated) trailing category letter. */
    val stripGluedSuffix: Boolean
        get() = prefs.getBoolean(KEY_STRIP_GLUED, false)

    /**
     * Whether senders notify by default. When false (the default), everything is muted
     * unless whitelisted or forced on by a keyword rule.
     */
    val defaultNotify: Boolean
        get() = prefs.getBoolean(KEY_DEFAULT_NOTIFY, false)

    /** Normalizes a raw sender address using the current settings. */
    fun normalizeSender(raw: String?): String =
        SenderNormalizer.normalize(raw, suffixLetters, stripGluedSuffix)

    // ---- Server sync (sms_web_api) ----

    /** Master switch for forwarding received messages to the server. */
    val syncEnabled: Boolean
        get() = prefs.getBoolean(KEY_SYNC_ENABLED, false)

    /** Base URL of the sms_web_api server, e.g. https://host:3000 (no trailing slash needed). */
    val syncBaseUrl: String
        get() = prefs.getString(KEY_SYNC_BASE_URL, "")?.trim()?.trimEnd('/').orEmpty()

    /** The server-side account (users.username) the messages are stored under. */
    val syncUserName: String
        get() = prefs.getString(KEY_SYNC_USER_NAME, "")?.trim().orEmpty()

    /** Keycloak Bearer token (pasted manually for now; expires ~hourly). */
    val syncToken: String
        get() = prefs.getString(KEY_SYNC_TOKEN, "")?.trim().orEmpty()

    /** Optional SIM label attached to each forwarded message. */
    val syncSim: String
        get() = prefs.getString(KEY_SYNC_SIM, "")?.trim().orEmpty()

    /** True only when sync is on and the minimum connection details are present. */
    val syncConfigured: Boolean
        get() = syncEnabled && syncBaseUrl.isNotEmpty() && syncUserName.isNotEmpty()

    companion object {
        const val KEY_SUFFIX_LETTERS = "suffix_letters"
        const val KEY_STRIP_GLUED = "strip_glued_suffix"
        const val KEY_DEFAULT_NOTIFY = "default_notify"
        const val KEY_SYNC_ENABLED = "sync_enabled"
        const val KEY_SYNC_BASE_URL = "sync_base_url"
        const val KEY_SYNC_USER_NAME = "sync_user_name"
        const val KEY_SYNC_TOKEN = "sync_token"
        const val KEY_SYNC_SIM = "sync_sim"
        private const val DEFAULT_SUFFIX_STRING = "GSPT"
    }
}

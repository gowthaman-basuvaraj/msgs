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

    companion object {
        const val KEY_SUFFIX_LETTERS = "suffix_letters"
        const val KEY_STRIP_GLUED = "strip_glued_suffix"
        const val KEY_DEFAULT_NOTIFY = "default_notify"
        private const val DEFAULT_SUFFIX_STRING = "GSPT"
    }
}

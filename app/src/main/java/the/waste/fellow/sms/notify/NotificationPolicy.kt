package the.waste.fellow.sms.notify

import android.content.Context
import androidx.preference.PreferenceManager
import org.json.JSONArray
import org.json.JSONObject

/**
 * Central decision point for "should this incoming message raise a notification?".
 *
 * Layers, highest priority first:
 *   1. A matching [SenderRule] with [RuleAction.NOTIFY]  -> notify
 *   2. A matching [SenderRule] with [RuleAction.MUTE]    -> suppress
 *   3. The sender's whitelist/default state ([baselineNotify]) -> as-is
 *
 * Rules are persisted as a JSON array in default SharedPreferences so both the receiver
 * and the settings UI (RulesActivity) share one source of truth.
 */
class NotificationPolicy(context: Context) {

    private val prefs = PreferenceManager.getDefaultSharedPreferences(context.applicationContext)

    fun rules(): List<SenderRule> = parse(prefs.getString(KEY_RULES, null))

    fun saveRules(rules: List<SenderRule>) {
        prefs.edit().putString(KEY_RULES, serialize(rules)).apply()
    }

    fun addRule(rule: SenderRule) {
        saveRules(rules() + rule)
    }

    fun removeRuleAt(index: Int) {
        val current = rules().toMutableList()
        if (index in current.indices) {
            current.removeAt(index)
            saveRules(current)
        }
    }

    /**
     * @param normalizedSender the grouped sender id.
     * @param message full message body.
     * @param baselineNotify the sender's default state (whitelist / global default) used
     *   when no keyword rule matches.
     */
    fun shouldNotify(normalizedSender: String, message: String, baselineNotify: Boolean): Boolean {
        val matching = rules().filter { it.matches(normalizedSender, message) }
        if (matching.any { it.action == RuleAction.NOTIFY }) return true
        if (matching.any { it.action == RuleAction.MUTE }) return false
        return baselineNotify
    }

    companion object {
        const val KEY_RULES = "notification_rules"

        internal fun serialize(rules: List<SenderRule>): String {
            val array = JSONArray()
            rules.forEach { rule ->
                val obj = JSONObject()
                obj.put("sender", rule.sender)
                obj.put("action", rule.action.name)
                obj.put("keywords", JSONArray(rule.keywords))
                array.put(obj)
            }
            return array.toString()
        }

        internal fun parse(json: String?): List<SenderRule> {
            if (json.isNullOrBlank()) return emptyList()
            return try {
                val array = JSONArray(json)
                (0 until array.length()).mapNotNull { i ->
                    val obj = array.optJSONObject(i) ?: return@mapNotNull null
                    val sender = obj.optString("sender").ifBlank { SenderRule.WILDCARD }
                    val action = runCatching {
                        RuleAction.valueOf(obj.optString("action", RuleAction.NOTIFY.name))
                    }.getOrDefault(RuleAction.NOTIFY)
                    val kwArray = obj.optJSONArray("keywords")
                    val keywords = if (kwArray == null) emptyList() else
                        (0 until kwArray.length()).map { kwArray.optString(it) }.filter { it.isNotBlank() }
                    SenderRule(sender, keywords, action)
                }
            } catch (e: Exception) {
                emptyList()
            }
        }
    }
}

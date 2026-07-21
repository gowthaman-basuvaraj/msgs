package the.waste.fellow.sms.notify

/** What a matching rule does to a message's notification. */
enum class RuleAction { NOTIFY, MUTE }

/**
 * A per-sender keyword classification rule.
 *
 * @param sender normalized sender id (see [the.waste.fellow.sms.utils.SenderNormalizer]),
 *   or "*" to match every sender.
 * @param keywords case-insensitive substrings; ANY match triggers the rule. An empty list
 *   matches every message from [sender].
 * @param action [RuleAction.NOTIFY] forces a notification, [RuleAction.MUTE] suppresses it.
 *
 * Precedence when several rules match one message: NOTIFY wins over MUTE, and rules win
 * over the sender's default whitelist state. This is what lets the same sender mute on
 * keyword X yet still notify on keyword Y.
 */
data class SenderRule(
    val sender: String,
    val keywords: List<String>,
    val action: RuleAction,
) {
    fun matches(normalizedSender: String, message: String): Boolean {
        val senderMatch = sender == WILDCARD || sender.equals(normalizedSender, ignoreCase = true)
        if (!senderMatch) return false
        if (keywords.isEmpty()) return true
        val haystack = message.lowercase()
        return keywords.any { it.isNotBlank() && haystack.contains(it.lowercase()) }
    }

    companion object {
        const val WILDCARD = "*"
    }
}

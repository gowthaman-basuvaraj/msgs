package the.waste.fellow.sms.utils

import java.util.Locale

/**
 * Normalizes Indian DLT alphanumeric sender headers so that the same logical sender
 * groups together regardless of which network prefix it arrived through and which
 * category suffix (TRAI: -G govt, -S service, -P promo, -T transactional) it carries.
 *
 * Examples (default config):
 *   AX-INDPOST     -> INDPOST
 *   VM-HDFCBK-S    -> HDFCBK
 *   AD-SBIINB      -> SBIINB
 *   JD-AMFL-S      -> AMFL
 *   +919876543210  -> +919876543210   (real number, never stripped)
 *   9876543210     -> 9876543210      (real number, never stripped)
 *
 * The no-hyphen "glued suffix" case (e.g. INDPOSTS) is genuinely ambiguous — a real
 * name may legitimately end in G/S/P/T — so glued-suffix stripping is opt-in
 * ([stripGluedSuffix], default false). Hyphen-delimited prefixes and suffixes are
 * always handled because they are structurally unambiguous.
 */
object SenderNormalizer {

    /** Default TRAI category-suffix letters. Configurable via settings. */
    val DEFAULT_SUFFIX_LETTERS: Set<Char> = setOf('G', 'S', 'P', 'T')

    private val PHONE_REGEX = Regex("^\\+?\\d{6,}$")

    /** True for a real phone number (personal/P2P), false for an alphanumeric sender id. */
    fun isPhoneNumber(raw: String?): Boolean =
        !raw.isNullOrBlank() && PHONE_REGEX.matches(raw.trim())

    /**
     * @param raw the raw originating address as delivered by the network.
     * @param suffixLetters category-suffix letters eligible for glued-suffix stripping.
     * @param stripGluedSuffix strip a trailing category letter when there is no hyphen
     *   delimiter (best-effort; off by default to avoid mangling names ending in G/S/P/T).
     */
    fun normalize(
        raw: String?,
        suffixLetters: Set<Char> = DEFAULT_SUFFIX_LETTERS,
        stripGluedSuffix: Boolean = false,
    ): String {
        if (raw.isNullOrBlank()) return ""
        val trimmed = raw.trim()

        // Real phone numbers (P2P / contacts) must never be stripped.
        if (PHONE_REGEX.matches(trimmed)) return trimmed

        val upper = trimmed.uppercase(Locale.ROOT)

        var core: String
        var removedHyphenSuffix = false

        if (upper.contains('-')) {
            val parts = upper.split('-').filter { it.isNotEmpty() }.toMutableList()

            // Strip a leading access-code prefix segment (2-char network code, e.g. AX/VM/AD).
            if (parts.size >= 2 && parts.first().length <= 2) {
                parts.removeAt(0)
            }
            // Strip a trailing single-letter category suffix segment (e.g. -S / -G).
            if (parts.size >= 2 && parts.last().length == 1) {
                parts.removeAt(parts.size - 1)
                removedHyphenSuffix = true
            }
            core = parts.joinToString("")
        } else {
            core = upper
        }

        // Opt-in best-effort glued-suffix stripping, only when we didn't already remove a
        // structural hyphen suffix, and only when enough characters remain to be a real name.
        if (stripGluedSuffix && !removedHyphenSuffix &&
            core.length >= 5 && core.last() in suffixLetters
        ) {
            core = core.dropLast(1)
        }

        return core.ifEmpty { upper }
    }
}

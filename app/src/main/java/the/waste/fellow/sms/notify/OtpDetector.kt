package the.waste.fellow.sms.notify

import kotlin.math.abs

/** Result of scanning a message for a one-time passcode. */
data class OtpResult(val isOtp: Boolean, val code: String?)

/**
 * Detects OTP/verification codes. Improves on the original logic by (a) fixing the 8-digit
 * pattern (was mistakenly `\d{6}`), and (b) choosing the digit run nearest an OTP keyword
 * rather than blindly taking the first run (which often grabbed amounts, ref numbers, etc.).
 */
object OtpDetector {

    private val OTP_KEYWORDS = listOf(
        "otp", "one time", "one-time", "onetime", "password", "passcode",
        "pin", "verification", "verify", "auth code", "security code", "secret code", "code is"
    )

    // 4 to 8 digit runs, not embedded in a longer number.
    private val CODE = Regex("(?<![0-9])[0-9]{4,8}(?![0-9])")

    fun extract(message: String): OtpResult {
        val lower = message.lowercase()
        val keywordIndex = OTP_KEYWORDS
            .map { lower.indexOf(it) }
            .filter { it >= 0 }
            .minOrNull() ?: return OtpResult(false, null)

        val matches = CODE.findAll(message).toList()
        if (matches.isEmpty()) return OtpResult(false, null)

        val best = matches.minByOrNull { abs(it.range.first - keywordIndex) }
        return OtpResult(true, best?.value)
    }
}

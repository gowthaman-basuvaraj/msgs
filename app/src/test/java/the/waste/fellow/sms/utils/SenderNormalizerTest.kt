package the.waste.fellow.sms.utils

import org.junit.Assert.assertEquals
import org.junit.Test

class SenderNormalizerTest {

    private fun norm(raw: String?, glued: Boolean = false) =
        SenderNormalizer.normalize(raw, stripGluedSuffix = glued)

    @Test
    fun stripsTwoCharPrefix() {
        assertEquals("INDPOST", norm("AX-INDPOST"))
        assertEquals("SBIINB", norm("AD-SBIINB"))
    }

    @Test
    fun stripsPrefixAndHyphenSuffix() {
        assertEquals("HDFCBK", norm("VM-HDFCBK-S"))
        assertEquals("AMFL", norm("JD-AMFL-S"))
        assertEquals("INDPOST", norm("AX-INDPOST-G"))
    }

    @Test
    fun variableNameLengthPreserved() {
        assertEquals("AMFL", norm("JD-AMFL"))          // 4-char name
        assertEquals("INDPOST", norm("AX-INDPOST"))    // 7-char name
    }

    @Test
    fun caseInsensitiveAndTrimmed() {
        assertEquals("HDFCBK", norm("  vm-hdfcbk-s  "))
    }

    @Test
    fun realPhoneNumbersNeverStripped() {
        assertEquals("9876543210", norm("9876543210"))
        assertEquals("+919876543210", norm("+919876543210"))
        assertEquals("1234567", norm("1234567"))
    }

    @Test
    fun hyphenSuffixNotStrippedWhenNoPrefixMangling() {
        // A hyphenated name ending in a suffix letter must not be over-stripped by the
        // glued heuristic even when it is enabled.
        assertEquals("INDPOST", norm("AX-INDPOST-S", glued = true))
    }

    @Test
    fun gluedSuffixOffByDefaultKeepsWholeName() {
        // Default: no hyphen -> leave the token intact (safe grouping).
        assertEquals("INDPOSTS", norm("INDPOSTS"))
        assertEquals("INDPOSTS", norm("AX-INDPOSTS"))
    }

    @Test
    fun gluedSuffixStrippedWhenEnabled() {
        assertEquals("INDPOST", norm("INDPOSTS", glued = true))
        assertEquals("INDPOST", norm("AX-INDPOSTS", glued = true))
    }

    @Test
    fun gluedSuffixGuardsShortTokens() {
        // Too short to risk stripping (< 5 chars) -> keep intact even when enabled.
        assertEquals("ABCS", norm("ABCS", glued = true))
    }

    @Test
    fun blankAndNullHandled() {
        assertEquals("", norm(null))
        assertEquals("", norm("   "))
    }
}

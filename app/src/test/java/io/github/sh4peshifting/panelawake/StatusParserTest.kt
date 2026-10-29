package io.github.sh4peshifting.panelawake

import org.junit.Assert.*
import org.junit.Test

class StatusParserTest {
    private val power = "  mWakefulness=Awake\n  mIsPowered=true\n"
    private fun panel(mode: String) = "+ DisplayDevice{12345, internal, primary, \"\"}\n   powerMode=$mode\n"
    @Test fun readsObservedAndroid12Format() {
        val state = StatusParser.parse(power, panel("On (2)"), 7)
        assertEquals(2, state.panelMode); assertEquals("12345", state.physicalId)
        assertTrue(state.awake); assertEquals(true, state.powered)
    }
    @Test fun physicalOffAndAndroidAwakeAreIndependent() {
        val state = StatusParser.parse(power, panel("Off (0)"), 7)
        assertEquals(0, state.panelMode); assertTrue(state.awake)
    }
    @Test fun doesNotMistakeExternalPanelForInternal() {
        val surface = "+ DisplayDevice{55, external, primary, \"HDMI\"}\n powerMode=2\n" +
            "+ DisplayDevice{66, internal, \"phone\"}\n powerMode=0\n"
        val state = StatusParser.parse(power, surface, 7)
        assertEquals("66", state.physicalId); assertEquals(0, state.panelMode)
    }
    @Test fun acceptsNumericAndNamedPowerModes() {
        assertEquals(0, StatusParser.parse(power, panel("0"), 0).panelMode)
        assertEquals(3, StatusParser.parse(power, panel("DOZE_SUSPEND"), 0).panelMode)
    }
    @Test fun missingOrAmbiguousStatusIsUnknown() {
        assertEquals(-1, StatusParser.parse(power, "permission denied", 7).panelMode)
        assertNull(StatusParser.parse("", "", 0).powered)
        assertFalse(StatusParser.parse("", "", 0).awake)
        assertEquals(-1, StatusParser.parse(power, panel("0") + panel("2"), 7).panelMode)
    }
    @Test fun aLaterExternalDeviceCannotOverwriteInternalStatus() {
        val surface = panel("Off (0)") + "+ DisplayDevice{66, external, \"hdmi\"}\n powerMode=On (2)\n"
        assertEquals(0, StatusParser.parse(power, surface, 7).panelMode)
    }
    @Test fun namesContainingInternalDoNotBecomeDisplayFlags() {
        val surface = "+ DisplayDevice{55, external, primary, \"internal\"}\n powerMode=2\n"
        assertEquals(-1, StatusParser.parse(power, surface, 7).panelMode)
    }
}

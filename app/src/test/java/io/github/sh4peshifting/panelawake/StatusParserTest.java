package io.github.sh4peshifting.panelawake;

import org.junit.Test;
import static org.junit.Assert.*;

public class StatusParserTest {
    private static final String POWER = "  mWakefulness=Awake\n  mIsPowered=true\n";
    private static String panel(String mode) {
        return "+ DisplayDevice{12345, internal, primary, \"\"}\n   powerMode=" + mode + "\n";
    }

    @Test public void readsObservedAndroid12Format() {
        StatusParser.State s = StatusParser.parse(POWER, panel("On (2)"), 7);
        assertEquals(2, s.panelMode()); assertEquals("12345", s.physicalId());
        assertTrue(s.awake()); assertEquals(Boolean.TRUE, s.powered());
    }
    @Test public void physicalOffAndAndroidAwakeAreIndependent() {
        StatusParser.State s = StatusParser.parse(POWER, panel("Off (0)"), 7);
        assertEquals(0, s.panelMode()); assertTrue(s.awake());
    }
    @Test public void doesNotMistakeExternalPanelForInternal() {
        String surface = "+ DisplayDevice{55, external, primary, \"HDMI\"}\n powerMode=2\n"
                + "+ DisplayDevice{66, internal, \"phone\"}\n powerMode=0\n";
        StatusParser.State s = StatusParser.parse(POWER, surface, 7);
        assertEquals("66", s.physicalId()); assertEquals(0, s.panelMode());
    }
    @Test public void acceptsNumericAndNamedPowerModes() {
        assertEquals(0, StatusParser.parse(POWER, panel("0"), 0).panelMode());
        assertEquals(3, StatusParser.parse(POWER, panel("DOZE_SUSPEND"), 0).panelMode());
    }
    @Test public void missingOrAmbiguousStatusIsUnknown() {
        assertEquals(-1, StatusParser.parse(POWER, "permission denied", 7).panelMode());
        assertNull(StatusParser.parse("", "", 0).powered());
        assertFalse(StatusParser.parse("", "", 0).awake());
        assertEquals(-1, StatusParser.parse(POWER, panel("0") + panel("2"), 7).panelMode());
    }
    @Test public void aLaterExternalDeviceCannotOverwriteInternalStatus() {
        String surface = panel("Off (0)") + "+ DisplayDevice{66, external, \"hdmi\"}\n powerMode=On (2)\n";
        assertEquals(0, StatusParser.parse(POWER, surface, 7).panelMode());
    }
}

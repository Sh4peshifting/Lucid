package io.github.sh4peshifting.panelawake;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

/** Android uses ICU regex rather than the desktop JDK regex engine. */
@RunWith(AndroidJUnit4.class)
public class AndroidStatusParserTest {
    @Test public void parsesPhysicalPanelAndIndependentWakeStateOnAndroid() {
        String power = "  mWakefulness=Awake\n  mIsPowered=true\n";
        String display = "+ DisplayDevice{123, internal, primary, \"\"}\n powerMode=Off (0)\n";
        StatusParser.State state = StatusParser.parse(power, display, 7);
        assertEquals(0, state.panelMode());
        assertTrue(state.awake());
        assertEquals(Boolean.TRUE, state.powered());
        assertEquals("123", state.physicalId());
        assertEquals(-1, StatusParser.parse(power, "permission denied", 7).panelMode());
    }
}

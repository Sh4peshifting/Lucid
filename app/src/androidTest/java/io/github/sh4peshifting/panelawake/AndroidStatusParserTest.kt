package io.github.sh4peshifting.panelawake

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AndroidStatusParserTest {
    @Test fun parsesPhysicalPanelAndIndependentWakeStateOnAndroid() {
        val power = "  mWakefulness=Awake\n  mIsPowered=true\n"
        val display = "+ DisplayDevice{123, internal, primary, \"\"}\n powerMode=Off (0)\n"
        val state = StatusParser.parse(power, display, 7)
        assertEquals(0, state.panelMode); assertTrue(state.awake)
        assertEquals(true, state.powered); assertEquals("123", state.physicalId)
        assertEquals(-1, StatusParser.parse(power, "permission denied", 7).panelMode)
    }
}

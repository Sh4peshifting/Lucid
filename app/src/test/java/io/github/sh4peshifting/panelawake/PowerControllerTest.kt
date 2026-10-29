package io.github.sh4peshifting.panelawake

import org.junit.Assert.*
import org.junit.Test

class PowerControllerTest {
    private class FakePlatform : PowerController.Platform {
        var stay = 0
        var mode = 2
        var wakes = 0
        var panelWrites = 0
        var powered = true
        var failWrite = false
        var failPanel = false
        var failRead = false
        var wakeState = "Awake"
        override fun read(): StatusParser.State {
            check(!failRead) { "cannot read status" }
            return StatusParser.State(wakeState, powered, stay, "1", mode)
        }
        override fun readStay() = stay
        override fun writeStay(value: Int) { check(!failWrite) { "denied" }; stay = value }
        override fun wake() { wakes++; wakeState = "Awake"; mode = 2 }
        override fun setPanelPower(on: Boolean, physicalId: String) {
            check(!failPanel) { "panel denied" }; panelWrites++; mode = if (on) 2 else 0
        }
    }
    @Test fun doesNotChangeDeviceBeforeUserEnablesControls() {
        val p = FakePlatform(); val c = PowerController(p)
        c.initialize(false, false, -1); c.tick()
        assertEquals(0, p.stay); assertEquals(0, p.panelWrites)
    }
    @Test fun keepAwakeRestoresOriginalValue() {
        val p = FakePlatform().apply { stay = 2 }; val c = PowerController(p)
        c.setKeepAwake(true, -1); assertEquals(7, p.stay)
        c.setKeepAwake(false, -1); assertEquals(2, p.stay)
    }
    @Test fun doesNotOverwriteAnExternalChangeDuringRestore() {
        val p = FakePlatform(); val c = PowerController(p)
        c.setKeepAwake(true, -1); p.stay = 1
        c.setKeepAwake(false, -1); assertEquals(1, p.stay)
    }
    @Test fun reassertsPanelOffAfterChargingWake() {
        val p = FakePlatform(); val c = PowerController(p)
        c.initialize(true, true, -1); p.wakeState = "Asleep"; p.mode = 0
        c.tick(); assertEquals(1, p.wakes); assertEquals(0, p.mode); assertTrue(c.state.awake)
    }
    @Test fun unpluggedDeviceIsAllowedToSleep() {
        val p = FakePlatform(); val c = PowerController(p)
        c.initialize(true, true, -1); p.powered = false; p.wakeState = "Asleep"
        c.tick(); assertEquals(0, p.wakes); assertEquals("Asleep", c.state.wakefulness)
    }
    @Test fun failedSettingDoesNotClaimGuardEnabledOrHideFailure() {
        val p = FakePlatform().apply { failWrite = true }; val c = PowerController(p)
        c.setKeepAwake(true, -1)
        assertFalse(c.keepsAwake); assertTrue(c.error.contains("denied"))
    }
    @Test fun failedPanelRequestIsReportedAndSwitchStaysOff() {
        val p = FakePlatform().apply { failPanel = true }; val c = PowerController(p)
        c.setPanelOff(true)
        assertFalse(c.keepsPanelOff); assertTrue(c.error.contains("panel denied"))
    }
    @Test fun savedBaselineSurvivesServiceRecreation() {
        val p = FakePlatform().apply { stay = 7 }; val c = PowerController(p)
        c.initialize(true, false, 0); c.restore(); assertEquals(0, p.stay)
    }
    @Test fun reattachmentDoesNotOverrideLiveServicePolicy() {
        val p = FakePlatform(); val c = PowerController(p)
        c.initialize(true, true, -1); c.initialize(false, false, -1)
        assertTrue(c.keepsAwake); assertTrue(c.keepsPanelOff)
    }
    @Test fun restoreBothControlsAndNoMorePanelEnforcement() {
        val p = FakePlatform(); val c = PowerController(p)
        c.initialize(true, true, -1); c.restore(); c.tick()
        assertEquals(0, p.stay); assertEquals(2, p.mode)
        assertFalse(c.keepsAwake); assertFalse(c.keepsPanelOff)
    }
    @Test fun readFailureInvalidatesPreviouslyObservedStatus() {
        val p = FakePlatform(); val c = PowerController(p)
        c.initialize(false, false, -1); assertTrue(c.state.awake)
        p.failRead = true; c.tick()
        assertEquals("Unknown", c.state.wakefulness); assertEquals(-1, c.state.panelMode)
        assertEquals(0L, c.updatedAt); assertTrue(c.error.contains("cannot read status"))
    }
    @Test fun restoreDoesNotHideAFailedPanelRestore() {
        val p = FakePlatform(); val c = PowerController(p)
        c.initialize(true, true, -1); p.failPanel = true; c.restore()
        assertTrue(c.keepsPanelOff); assertFalse(c.keepsAwake)
        assertTrue(c.error.contains("panel denied"))
    }
}

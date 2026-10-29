package io.github.sh4peshifting.panelawake;

import org.junit.Test;
import static org.junit.Assert.*;

public class PowerControllerTest {
    static final class FakePlatform implements PowerController.Platform {
        int stay, mode = 2, wakes, panelWrites;
        boolean powered = true, failWrite, failPanel, failRead;
        String wake = "Awake";
        @Override public StatusParser.State read() throws Exception {
            if (failRead) throw new Exception("cannot read status");
            return new StatusParser.State(wake, powered, stay, "1", mode);
        }
        @Override public int readStay() { return stay; }
        @Override public void writeStay(int value) throws Exception {
            if (failWrite) throw new Exception("denied");
            stay = value;
        }
        @Override public void wake() { wakes++; wake = "Awake"; mode = 2; }
        @Override public void setPanelPower(boolean on, String id) throws Exception {
            if (failPanel) throw new Exception("panel denied");
            panelWrites++; mode = on ? 2 : 0;
        }
    }
    @Test public void doesNotChangeDeviceBeforeUserEnablesControls() {
        FakePlatform p = new FakePlatform(); PowerController c = new PowerController(p);
        c.initialize(false, false, -1); c.tick();
        assertEquals(0, p.stay); assertEquals(0, p.panelWrites);
    }
    @Test public void keepAwakeRestoresOriginalValue() {
        FakePlatform p = new FakePlatform(); p.stay = 2;
        PowerController c = new PowerController(p);
        c.setKeepAwake(true, -1); assertEquals(7, p.stay);
        c.setKeepAwake(false, -1); assertEquals(2, p.stay);
    }
    @Test public void doesNotOverwriteAnExternalChangeDuringRestore() {
        FakePlatform p = new FakePlatform(); PowerController c = new PowerController(p);
        c.setKeepAwake(true, -1); p.stay = 1;
        c.setKeepAwake(false, -1); assertEquals(1, p.stay);
    }
    @Test public void reassertsPanelOffAfterChargingWake() {
        FakePlatform p = new FakePlatform(); PowerController c = new PowerController(p);
        c.initialize(true, true, -1); p.wake = "Asleep"; p.mode = 0;
        c.tick(); assertEquals(1, p.wakes); assertEquals(0, p.mode); assertTrue(c.state().awake());
    }
    @Test public void unpluggedDeviceIsAllowedToSleep() {
        FakePlatform p = new FakePlatform(); PowerController c = new PowerController(p);
        c.initialize(true, true, -1); p.powered = false; p.wake = "Asleep";
        c.tick(); assertEquals(0, p.wakes); assertEquals("Asleep", c.state().wakefulness());
    }
    @Test public void failedSettingDoesNotClaimGuardEnabledOrHideFailure() {
        FakePlatform p = new FakePlatform(); p.failWrite = true;
        PowerController c = new PowerController(p); c.setKeepAwake(true, -1);
        assertFalse(c.keepsAwake()); assertTrue(c.error().contains("denied"));
    }
    @Test public void failedPanelRequestIsReportedAndSwitchStaysOff() {
        FakePlatform p = new FakePlatform(); p.failPanel = true;
        PowerController c = new PowerController(p); c.setPanelOff(true);
        assertFalse(c.keepsPanelOff()); assertTrue(c.error().contains("panel denied"));
    }
    @Test public void savedBaselineSurvivesServiceRecreation() {
        FakePlatform p = new FakePlatform(); p.stay = 7;
        PowerController c = new PowerController(p); c.initialize(true, false, 0);
        c.restore(); assertEquals(0, p.stay);
    }
    @Test public void reattachmentDoesNotOverrideLiveServicePolicy() {
        FakePlatform p = new FakePlatform(); PowerController c = new PowerController(p);
        c.initialize(true, true, -1); c.initialize(false, false, -1);
        assertTrue(c.keepsAwake()); assertTrue(c.keepsPanelOff());
    }
    @Test public void restoreBothControlsAndNoMorePanelEnforcement() {
        FakePlatform p = new FakePlatform(); PowerController c = new PowerController(p);
        c.initialize(true, true, -1); c.restore(); c.tick();
        assertEquals(0, p.stay); assertEquals(2, p.mode);
        assertFalse(c.keepsAwake()); assertFalse(c.keepsPanelOff());
    }
    @Test public void readFailureInvalidatesPreviouslyObservedStatus() {
        FakePlatform p = new FakePlatform(); PowerController c = new PowerController(p);
        c.initialize(false, false, -1); assertTrue(c.state().awake());
        p.failRead = true; c.tick();
        assertEquals("Unknown", c.state().wakefulness());
        assertEquals(-1, c.state().panelMode()); assertEquals(0, c.updatedAt());
        assertTrue(c.error().contains("cannot read status"));
    }
    @Test public void restoreDoesNotHideAFailedPanelRestore() {
        FakePlatform p = new FakePlatform(); PowerController c = new PowerController(p);
        c.initialize(true, true, -1); p.failPanel = true; c.restore();
        assertTrue(c.keepsPanelOff()); assertFalse(c.keepsAwake());
        assertTrue(c.error().contains("panel denied"));
    }
}

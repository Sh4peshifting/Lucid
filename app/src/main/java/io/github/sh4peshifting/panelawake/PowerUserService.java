package io.github.sh4peshifting.panelawake;

import android.os.Bundle;
import android.os.Process;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public final class PowerUserService extends IPowerService.Stub {
    private final PowerController controller = new PowerController(new AndroidPlatform());
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    public PowerUserService() {
        scheduler.scheduleWithFixedDelay(controller::tick, 0, 5, TimeUnit.SECONDS);
    }

    @Override public Bundle initialize(boolean keep, boolean off, int restore) {
        controller.initialize(keep, off, restore);
        return getStatus();
    }

    @Override public Bundle getStatus() {
        synchronized (controller) {
            StatusParser.State s = controller.state();
            Bundle result = new Bundle();
            result.putString("wakefulness", s.wakefulness());
            result.putString("powered", s.powered() == null ? "unknown" : s.powered().toString());
            result.putInt("panelMode", s.panelMode());
            result.putInt("staySetting", s.staySetting());
            result.putBoolean("keepAwake", controller.keepsAwake());
            result.putBoolean("panelOff", controller.keepsPanelOff());
            result.putInt("restoreSetting", controller.restoreSetting());
            result.putString("error", controller.error());
            result.putLong("updatedAt", controller.updatedAt());
            result.putInt("uid", Process.myUid());
            return result;
        }
    }

    @Override public Bundle setKeepAwake(boolean enabled, int restore) {
        controller.setKeepAwake(enabled, restore);
        return getStatus();
    }

    @Override public Bundle setPanelOff(boolean enabled) {
        controller.setPanelOff(enabled);
        return getStatus();
    }

    @Override public Bundle restore() { controller.restore(); return getStatus(); }

    @Override public void destroy() {
        scheduler.shutdownNow();
        controller.restore();
        System.exit(0);
    }
}

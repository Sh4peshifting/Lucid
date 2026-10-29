package io.github.sh4peshifting.panelawake;
import android.os.Bundle;
interface IPowerService {
    Bundle initialize(boolean keepAwake, boolean panelOff, int restoreSetting);
    Bundle getStatus();
    Bundle setKeepAwake(boolean enabled, int restoreSetting);
    Bundle setPanelOff(boolean enabled);
    Bundle restore();
    void destroy() = 16777114;
}

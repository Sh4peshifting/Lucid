package io.github.sh4peshifting.panelawake;
import android.os.Bundle;
interface IPowerService {
    Bundle initialize(boolean keepAwake, boolean panelOff, int restoreSetting) = 0;
    Bundle getStatus() = 1;
    Bundle setKeepAwake(boolean enabled, int restoreSetting) = 2;
    Bundle setPanelOff(boolean enabled) = 3;
    Bundle restore() = 4;
    void destroy() = 16777114;
}

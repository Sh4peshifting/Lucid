package io.github.sh4peshifting.panelawake;

/** Stateful policy, independent of Android so failure/restore behavior can be tested. */
public final class PowerController {
    public interface Platform {
        StatusParser.State read() throws Exception;
        int readStay() throws Exception;
        void writeStay(int value) throws Exception;
        void wake() throws Exception;
        void setPanelPower(boolean on, String physicalId) throws Exception;
    }

    private final Platform platform;
    private boolean initialized;
    private boolean keepAwake;
    private boolean panelOff;
    private int restoreSetting = -1;
    private StatusParser.State state = new StatusParser.State("Unknown", null, -1, "", -1);
    private String error = "";
    private long updatedAt;

    public PowerController(Platform platform) { this.platform = platform; }

    public synchronized void initialize(boolean keep, boolean off, int original) {
        if (initialized) return;
        initialized = true;
        refresh();
        if (keep) setKeepAwake(true, original);
        if (off) setPanelOff(true);
    }

    public synchronized void setKeepAwake(boolean enabled, int original) {
        error = "";
        try {
            if (enabled && !keepAwake) {
                int current = platform.readStay();
                if (current < 0) throw new IllegalStateException("无法读取原来的充电唤醒设置");
                int baseline = original >= 0 ? original : current;
                platform.writeStay(7);
                restoreSetting = baseline;
                keepAwake = true;
            } else if (!enabled && keepAwake) {
                // Respect a setting another application changed since we last controlled it.
                if (platform.readStay() == 7) platform.writeStay(restoreSetting);
                keepAwake = false;
                restoreSetting = -1;
            }
        } catch (Exception e) { error = describe(e); refresh(); return; }
        tick();
    }

    public synchronized void setPanelOff(boolean enabled) {
        error = "";
        try {
            state = observe();
            // A fresh physical id prevents applying a stale token after display changes.
            platform.setPanelPower(!enabled, state.physicalId());
            panelOff = enabled;
        } catch (Exception e) { error = describe(e); }
        refresh();
    }

    public synchronized void tick() {
        try {
            StatusParser.State before = observe();
            state = before;
            boolean changed = false;
            if (keepAwake) {
                if (before.staySetting() != 7) { platform.writeStay(7); changed = true; }
                if (Boolean.TRUE.equals(before.powered()) && !before.awake()
                        && !"Unknown".equals(before.wakefulness())) {
                    platform.wake();
                    changed = true;
                    // A wake request can power the panel on before its state is observable.
                    if (panelOff) platform.setPanelPower(false, before.physicalId());
                }
            }
            if (panelOff && before.panelMode() > 0) {
                platform.setPanelPower(false, before.physicalId());
                changed = true;
            }
            if (changed) state = observe();
            updatedAt = System.currentTimeMillis();
            error = "";
        } catch (Exception e) { error = describe(e); }
    }

    public synchronized void refresh() {
        try { state = observe(); updatedAt = System.currentTimeMillis(); }
        catch (Exception e) { error = describe(e); }
    }

    public synchronized void restore() {
        String failures = "";
        if (panelOff) setPanelOff(false);
        if (!error.isEmpty()) failures = error;
        if (keepAwake) setKeepAwake(false, -1);
        if (!failures.isEmpty()) error = failures + (error.isEmpty() ? "" : "\n" + error);
    }

    public synchronized StatusParser.State state() { return state; }
    public synchronized boolean keepsAwake() { return keepAwake; }
    public synchronized boolean keepsPanelOff() { return panelOff; }
    public synchronized int restoreSetting() { return restoreSetting; }
    public synchronized String error() { return error; }
    public synchronized long updatedAt() { return updatedAt; }

    private StatusParser.State observe() throws Exception {
        try { return platform.read(); }
        catch (Exception e) {
            state = new StatusParser.State("Unknown", null, -1, "", -1);
            updatedAt = 0;
            throw e;
        }
    }

    private static String describe(Exception e) {
        Throwable cause = e;
        while (cause.getCause() != null) cause = cause.getCause();
        return cause.getClass().getSimpleName() + ": " + String.valueOf(cause.getMessage());
    }
}

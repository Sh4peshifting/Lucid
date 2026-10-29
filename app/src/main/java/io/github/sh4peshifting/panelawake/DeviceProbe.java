package io.github.sh4peshifting.panelawake;

/** Shell-only CI diagnostics. Not exported as an Android component. No writes unless --exercise. */
public final class DeviceProbe {
    private DeviceProbe() {}

    public static void main(String[] args) throws Exception {
        AndroidPlatform platform = new AndroidPlatform();
        StatusParser.State before = platform.read();
        System.out.println("wakefulness=" + before.wakefulness() + " powered=" + before.powered()
                + " panelMode=" + before.panelMode() + " staySetting=" + before.staySetting());
        if ("Unknown".equals(before.wakefulness()) || before.panelMode() < 0) {
            throw new IllegalStateException("Required status could not be read");
        }
        if (args.length == 0 || !"--exercise".equals(args[0])) return;
        PowerController controller = new PowerController(platform);
        try {
            controller.initialize(false, false, -1);
            controller.setKeepAwake(true, -1);
            check(controller.keepsAwake(), controller.error());
            controller.setPanelOff(true);
            Thread.sleep(800);
            controller.tick();
            check(controller.error().isEmpty(), controller.error());
            check(controller.state().panelMode() == 0, "Panel did not turn off");
            check(controller.state().awake(), "System did not remain awake");
            controller.setPanelOff(false);
            Thread.sleep(800);
            controller.tick();
            check(controller.state().panelMode() == 2, "Panel did not turn on");
            System.out.println("EXERCISE_OK: physical panel OFF + Android Awake + panel ON verified");
        } finally {
            controller.restore();
            platform.writeStay(before.staySetting());
            platform.setPanelPower(before.panelMode() != 0, before.physicalId());
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}

package io.github.sh4peshifting.panelawake

/** Shell-only CI entry point; no writes without --exercise. */
object DeviceProbe {
    @JvmStatic fun main(args: Array<String>) {
        val platform = AndroidPlatform()
        val before = platform.read()
        println("wakefulness=${before.wakefulness} powered=${before.powered} panelMode=${before.panelMode} staySetting=${before.staySetting}")
        check(before.wakefulness != "Unknown" && before.panelMode >= 0) { "Required status could not be read" }
        if (args.firstOrNull() != "--exercise") return
        val controller = PowerController(platform)
        try {
            controller.initialize(false, false, -1)
            controller.setKeepAwake(true, -1)
            check(controller.keepsAwake) { controller.error }
            controller.setPanelOff(true)
            Thread.sleep(800)
            controller.tick()
            check(controller.error.isEmpty()) { controller.error }
            check(controller.state.panelMode == 0) { "Panel did not turn off" }
            check(controller.state.awake) { "System did not remain awake" }
            controller.setPanelOff(false)
            Thread.sleep(800)
            controller.tick()
            check(controller.state.panelMode == 2) { "Panel did not turn on" }
            println("EXERCISE_OK: physical panel OFF + Android Awake + panel ON verified")
        } finally {
            controller.restore()
            platform.writeStay(before.staySetting)
            platform.setPanelPower(before.panelMode != 0, before.physicalId)
        }
    }
}

package io.github.sh4peshifting.panelawake

/** Pure Kotlin policy; no Android dependencies, shared by the daemon and tests. */
class PowerController(private val platform: Platform) {
    interface Platform {
        fun read(): StatusParser.State
        fun readStay(): Int
        fun writeStay(value: Int)
        fun wake()
        fun setPanelPower(on: Boolean, physicalId: String)
    }

    private var initialized = false
    var keepsAwake = false
        private set
    var keepsPanelOff = false
        private set
    var restoreSetting = -1
        private set
    var state = StatusParser.State()
        private set
    var error = ""
        private set
    var updatedAt = 0L
        private set

    @Synchronized fun initialize(keep: Boolean, off: Boolean, original: Int) {
        if (initialized) return
        initialized = true
        refresh()
        if (keep) setKeepAwake(true, original)
        if (off) setPanelOff(true)
    }

    @Synchronized fun setKeepAwake(enabled: Boolean, original: Int) {
        error = ""
        try {
            if (enabled && !keepsAwake) {
                val current = platform.readStay()
                check(current >= 0) { "无法读取原来的充电唤醒设置" }
                val baseline = original.takeIf { it >= 0 } ?: current
                platform.writeStay(7)
                restoreSetting = baseline
                keepsAwake = true
            } else if (!enabled && keepsAwake) {
                if (platform.readStay() == 7) platform.writeStay(restoreSetting)
                keepsAwake = false
                restoreSetting = -1
            }
        } catch (e: Exception) {
            error = describe(e)
            refresh()
            return
        }
        tick()
    }

    @Synchronized fun setPanelOff(enabled: Boolean) {
        error = ""
        try {
            state = observe()
            platform.setPanelPower(!enabled, state.physicalId)
            keepsPanelOff = enabled
        } catch (e: Exception) { error = describe(e) }
        refresh()
    }

    @Synchronized fun tick() {
        try {
            val before = observe()
            state = before
            var changed = false
            if (keepsAwake) {
                if (before.staySetting != 7) { platform.writeStay(7); changed = true }
                if (before.powered == true && !before.awake && before.wakefulness != "Unknown") {
                    platform.wake()
                    changed = true
                    if (keepsPanelOff) platform.setPanelPower(false, before.physicalId)
                }
            }
            if (keepsPanelOff && before.panelMode > 0) {
                platform.setPanelPower(false, before.physicalId)
                changed = true
            }
            if (changed) state = observe()
            updatedAt = System.currentTimeMillis()
            error = ""
        } catch (e: Exception) { error = describe(e) }
    }

    @Synchronized fun refresh() {
        try { state = observe(); updatedAt = System.currentTimeMillis() }
        catch (e: Exception) { error = describe(e) }
    }

    @Synchronized fun restore() {
        if (keepsPanelOff) setPanelOff(false)
        val failures = error
        if (keepsAwake) setKeepAwake(false, -1)
        if (failures.isNotEmpty()) error = listOf(failures, error).filter(String::isNotEmpty).distinct().joinToString("\n")
    }

    private fun observe(): StatusParser.State = try { platform.read() }
    catch (e: Exception) { state = StatusParser.State(); updatedAt = 0; throw e }

    private fun describe(e: Exception): String {
        val cause = generateSequence<Throwable>(e) { it.cause }.last()
        return "${cause.javaClass.simpleName}: ${cause.message}"
    }
}

package io.github.sh4peshifting.panelawake

import android.os.Bundle
import android.os.Process
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.system.exitProcess

class PowerUserService : IPowerService.Stub() {
    private val controller = PowerController(AndroidPlatform())
    private val scheduler = Executors.newSingleThreadScheduledExecutor()

    init { scheduler.scheduleWithFixedDelay(controller::tick, 0, 5, TimeUnit.SECONDS) }

    override fun initialize(keep: Boolean, off: Boolean, restore: Int): Bundle {
        controller.initialize(keep, off, restore)
        return getStatus()
    }

    override fun getStatus(): Bundle = synchronized(controller) {
        val state = controller.state
        Bundle().apply {
            putString("wakefulness", state.wakefulness)
            putString("powered", state.powered?.toString() ?: "unknown")
            putInt("panelMode", state.panelMode)
            putInt("staySetting", state.staySetting)
            putBoolean("keepAwake", controller.keepsAwake)
            putBoolean("panelOff", controller.keepsPanelOff)
            putInt("restoreSetting", controller.restoreSetting)
            putString("error", controller.error)
            putLong("updatedAt", controller.updatedAt)
            putInt("uid", Process.myUid())
        }
    }

    override fun setKeepAwake(enabled: Boolean, restore: Int): Bundle {
        controller.setKeepAwake(enabled, restore)
        return getStatus()
    }
    override fun setPanelOff(enabled: Boolean): Bundle {
        controller.setPanelOff(enabled)
        return getStatus()
    }
    override fun restore(): Bundle { controller.restore(); return getStatus() }
    override fun destroy() { scheduler.shutdownNow(); controller.restore(); exitProcess(0) }
}

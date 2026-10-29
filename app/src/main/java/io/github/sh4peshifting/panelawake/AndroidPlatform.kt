package io.github.sh4peshifting.panelawake

import android.annotation.SuppressLint
import android.os.IBinder
import android.os.Process
import java.io.ByteArrayOutputStream
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/** Instantiated only by a shell/root Shizuku UserService or shell-only diagnostic. */
class AndroidPlatform : PowerController.Platform {
    init { check(Process.myUid() == 0 || Process.myUid() == 2000) { "电源接口只允许在 Shizuku 的 shell/root 进程中运行" } }

    override fun read(): StatusParser.State = StatusParser.parse(
        command("/system/bin/dumpsys", "-t", "3", "power"),
        command("/system/bin/dumpsys", "-t", "3", "SurfaceFlinger"), readStay(),
    )

    override fun readStay(): Int = command("/system/bin/settings", "get", "global", "stay_on_while_plugged_in")
        .trim().let { if (it == "null") 0 else it.toInt() }

    override fun writeStay(value: Int) {
        require(value in 0..15) { "无效的充电唤醒设置" }
        command("/system/bin/settings", "put", "global", "stay_on_while_plugged_in", value.toString())
        check(readStay() == value) { "系统拒绝更新充电唤醒设置" }
    }

    override fun wake() {
        command("/system/bin/input", "keyevent", "224") // WAKEUP, never the toggling POWER key.
        Thread.sleep(700)
    }

    // Shell/root app_process is exempt from ordinary app hidden-API restrictions.
    @SuppressLint("BlockedPrivateApi", "SoonBlockedPrivateApi", "PrivateApi")
    override fun setPanelPower(on: Boolean, physicalId: String) {
        val surface = Class.forName("android.view.SurfaceControl")
        val tokens = try { surface.getDeclaredMethod("getPhysicalDisplayIds"); surface }
        catch (_: NoSuchMethodException) { Class.forName("com.android.server.display.DisplayControl") }
        val displays = tokens.getDeclaredMethod("getPhysicalDisplayIds").invoke(null) as? LongArray
        check(displays != null && displays.isNotEmpty()) { "没有可用的实体屏幕" }
        val selected = when {
            physicalId.isNotEmpty() -> java.lang.Long.parseUnsignedLong(physicalId).also {
                check(it in displays) { "内置屏幕标识已改变，请刷新后重试" }
            }
            displays.size == 1 -> displays[0]
            else -> error("存在多个实体屏幕，无法安全识别内置屏幕")
        }
        val token = tokens.getDeclaredMethod("getPhysicalDisplayToken", Long::class.javaPrimitiveType)
            .invoke(null, selected) as? IBinder ?: error("无法取得实体屏幕控制接口")
        surface.getDeclaredMethod("setDisplayPowerMode", IBinder::class.java, Int::class.javaPrimitiveType)
            .invoke(null, token, if (on) 2 else 0)
    }

    companion object {
        private val readers = Executors.newCachedThreadPool { runnable ->
            Thread(runnable, "command-output").apply { isDaemon = true }
        }
        private fun command(vararg args: String): String {
            val process = ProcessBuilder(*args).redirectErrorStream(true).start()
            val output = readers.submit(Callable {
                process.inputStream.use { stream ->
                    ByteArrayOutputStream().use { bytes ->
                        val buffer = ByteArray(8192)
                        while (true) {
                            val count = stream.read(buffer)
                            if (count < 0) break
                            check(bytes.size() + count <= 4 * 1024 * 1024) { "系统状态输出过大" }
                            bytes.write(buffer, 0, count)
                        }
                        bytes.toByteArray().toString(Charsets.UTF_8)
                    }
                }
            })
            return try {
                check(process.waitFor(8, TimeUnit.SECONDS)) { "系统命令超时: ${args[0]}" }
                val result = output.get(2, TimeUnit.SECONDS)
                check(process.exitValue() == 0) { "系统命令失败: ${args[0]}" }
                result
            } finally { process.destroyForcibly(); output.cancel(true) }
        }
    }
}

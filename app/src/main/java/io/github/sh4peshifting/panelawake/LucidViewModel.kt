package io.github.sh4peshifting.panelawake

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku

data class LucidUiState(
    val connected: Boolean = false,
    val connecting: Boolean = false,
    val busy: Boolean = false,
    val connection: String = "启动 Shizuku 后连接",
    val wakefulness: String = "Unknown",
    val powered: Boolean? = null,
    val panelMode: Int = -1,
    val staySetting: Int = -1,
    val keepAwake: Boolean = false,
    val panelOff: Boolean = false,
    val restoreSetting: Int = -1,
    val updatedAt: Long = 0,
    val uid: Int = -1,
    val error: String = "",
) {
    val controlsEnabled get() = connected && !busy
}

class LucidViewModel(application: Application) : AndroidViewModel(application) {
    // Preserve the previous app's preferences, package, signing key and daemon identity.
    private val preferences = application.getSharedPreferences("guardian", Context.MODE_PRIVATE)
    private val mutableState = MutableStateFlow(savedState())
    val state = mutableState.asStateFlow()
    private val handler = Handler(Looper.getMainLooper())
    private val requests = Mutex()
    private var remote: IPowerService? = null
    private var bound = false
    private var cleared = false
    private var screenVisible = false
    private val serviceArgs = Shizuku.UserServiceArgs(ComponentName(application, PowerUserService::class.java))
        .tag("panel-awake").daemon(true).processNameSuffix("guardian")
        .debuggable(BuildConfig.DEBUG).version(BuildConfig.VERSION_CODE)

    private val received = Shizuku.OnBinderReceivedListener { handler.post { connectIfGranted() } }
    private val dead = Shizuku.OnBinderDeadListener { handler.post { disconnect("Shizuku 已停止，请重新连接") } }
    private val permission = Shizuku.OnRequestPermissionResultListener { _, result ->
        handler.post {
            if (result == PackageManager.PERMISSION_GRANTED) connectIfGranted()
            else disconnect("授权被拒绝，请在 Shizuku 中允许 Lucid")
        }
    }
    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, binder: IBinder) {
            handler.post {
                if (cleared) return@post
                val service = IPowerService.Stub.asInterface(binder)
                remote = service
                invoke(persist = true) {
                    it.initialize(preferences.getBoolean("keepAwake", false),
                        preferences.getBoolean("panelOff", false), preferences.getInt("restoreSetting", -1))
                }
            }
        }
        override fun onServiceDisconnected(name: ComponentName) {
            handler.post { disconnect("守护服务已断开，请重新连接") }
        }
    }

    init {
        Shizuku.addBinderReceivedListenerSticky(received)
        Shizuku.addBinderDeadListener(dead)
        Shizuku.addRequestPermissionResultListener(permission)
        connectIfGranted()
        viewModelScope.launch {
            while (isActive) {
                delay(2000)
                if (screenVisible && remote != null && !mutableState.value.busy && !requests.isLocked) {
                    invoke(persist = false, busy = false) { it.status }
                }
            }
        }
    }

    fun setScreenVisible(visible: Boolean) { screenVisible = visible }

    fun requestConnection() {
        try {
            if (Shizuku.pingBinder() && Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
                Shizuku.requestPermission(1)
            } else connectIfGranted()
        } catch (e: RuntimeException) { disconnect("无法请求授权：${e.message}") }
    }

    private fun connectIfGranted() {
        if (cleared || remote != null || mutableState.value.connecting) return
        try {
            when {
                !Shizuku.pingBinder() -> disconnect("Shizuku 未运行，请先启动 Shizuku")
                Shizuku.getVersion() < 13 -> disconnect("需要 Shizuku 13 或更新版本")
                Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED -> disconnect("Shizuku 已就绪，点击连接并授权")
                else -> {
                    mutableState.update { it.copy(connecting = true, connection = "正在连接独立守护…") }
                    bound = true
                    Shizuku.bindUserService(serviceArgs, connection)
                    handler.postDelayed({
                        if (mutableState.value.connecting && remote == null) disconnect("连接超时，请重试")
                    }, 15000)
                }
            }
        } catch (e: RuntimeException) { disconnect("连接失败：${e.message}") }
    }

    fun setKeepAwake(enabled: Boolean) {
        val current = mutableState.value
        if (!current.controlsEnabled) return
        var original = current.restoreSetting
        if (enabled && !current.keepAwake) {
            original = current.staySetting
            if (original < 0) {
                mutableState.update { it.copy(error = "尚未读取原来的唤醒设置，请稍后再试") }
                return
            }
            if (!preferences.edit().putInt("restoreSetting", original).commit()) {
                mutableState.update { it.copy(error = "无法保存原设置，未执行更改") }
                return
            }
        }
        invoke(persist = true) { it.setKeepAwake(enabled, original) }
    }

    fun setPanelOff(enabled: Boolean) {
        if (mutableState.value.controlsEnabled) invoke(persist = true) { it.setPanelOff(enabled) }
    }
    fun restore() { if (mutableState.value.controlsEnabled) invoke(persist = true) { it.restore() } }

    private fun invoke(persist: Boolean, busy: Boolean = true, call: (IPowerService) -> Bundle) {
        val service = remote ?: return
        if (busy) mutableState.update { it.copy(busy = true) }
        viewModelScope.launch {
            requests.withLock {
                try {
                    val result = withContext(Dispatchers.IO) { call(service) }
                    if (remote !== service || cleared) return@withLock
                    if (persist) preferences.edit()
                        .putBoolean("keepAwake", result.getBoolean("keepAwake"))
                        .putBoolean("panelOff", result.getBoolean("panelOff"))
                        .putInt("restoreSetting", result.getInt("restoreSetting", -1)).apply()
                    mutableState.value = LucidUiState(
                        connected = true, busy = if (busy) false else mutableState.value.busy, connection = "独立守护已连接",
                        wakefulness = result.getString("wakefulness", "Unknown"),
                        powered = result.getString("powered")?.toBooleanStrictOrNull(),
                        panelMode = result.getInt("panelMode", -1), staySetting = result.getInt("staySetting", -1),
                        keepAwake = result.getBoolean("keepAwake"), panelOff = result.getBoolean("panelOff"),
                        restoreSetting = result.getInt("restoreSetting", -1), updatedAt = result.getLong("updatedAt"),
                        uid = result.getInt("uid", -1), error = result.getString("error", ""),
                    )
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    if (remote === service) disconnect("服务不可用：${e.message}")
                }
            }
        }
    }

    private fun savedState() = LucidUiState(
        keepAwake = preferences.getBoolean("keepAwake", false), panelOff = preferences.getBoolean("panelOff", false),
        restoreSetting = preferences.getInt("restoreSetting", -1),
    )
    private fun disconnect(reason: String) {
        if (cleared) return
        remote = null
        mutableState.value = savedState().copy(connection = reason)
    }

    override fun onCleared() {
        cleared = true
        Shizuku.removeBinderReceivedListener(received)
        Shizuku.removeBinderDeadListener(dead)
        Shizuku.removeRequestPermissionResultListener(permission)
        handler.removeCallbacksAndMessages(null)
        if (bound) runCatching { Shizuku.unbindUserService(serviceArgs, connection, false) }
        super.onCleared()
    }
}

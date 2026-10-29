package io.github.sh4peshifting.panelawake;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.view.View;
import android.view.WindowInsets;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;

import java.text.DateFormat;
import java.util.Date;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import rikka.shizuku.Shizuku;

/** The UI never invokes hidden Android APIs or privileged commands itself. */
public final class MainActivity extends Activity {
    private static final int TEXT = Color.rgb(232, 240, 247);
    private static final int MUTED = Color.rgb(160, 178, 196);
    private static final int ACCENT = Color.rgb(102, 227, 196);
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private IPowerService service;
    private SharedPreferences preferences;
    private TextView connectionText, panelText, wakeText, powerText, detailText, errorText;
    private Switch keepSwitch, panelSwitch;
    private Button connectButton, restoreButton;
    private boolean rendering, pending, binding, destroyed;
    private Bundle latest;
    private final Shizuku.OnBinderReceivedListener received = () -> main.post(this::connectIfGranted);
    private final Shizuku.OnBinderDeadListener dead = () -> main.post(() -> disconnected("Shizuku 已停止；守护状态无法确认，请重新连接"));
    private final Shizuku.OnRequestPermissionResultListener permission = (code, result) -> main.post(() -> {
        if (result == PackageManager.PERMISSION_GRANTED) connectIfGranted();
        else disconnected("Shizuku 授权被拒绝，请在 Shizuku 中允许 Panel Awake");
    });

    private Shizuku.UserServiceArgs args() {
        return new Shizuku.UserServiceArgs(new ComponentName(this, PowerUserService.class))
                .tag("panel-awake").daemon(true).processNameSuffix("guardian")
                .debuggable(BuildConfig.DEBUG).version(BuildConfig.VERSION_CODE);
    }

    private final ServiceConnection connection = new ServiceConnection() {
        @Override public void onServiceConnected(ComponentName name, IBinder binder) {
            main.post(() -> {
                if (destroyed) return;
                binding = false;
                service = IPowerService.Stub.asInterface(binder);
                call(s -> s.initialize(preferences.getBoolean("keepAwake", false),
                        preferences.getBoolean("panelOff", false), preferences.getInt("restoreSetting", -1)), true);
            });
        }
        @Override public void onServiceDisconnected(ComponentName name) {
            main.post(() -> disconnected("守护服务已断开，请重新连接"));
        }
    };

    private final Runnable poll = new Runnable() {
        @Override public void run() {
            if (service != null && !pending) call(IPowerService::getStatus, false);
            main.postDelayed(this, 2000);
        }
    };

    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        preferences = getSharedPreferences("guardian", MODE_PRIVATE);
        buildUi();
        Shizuku.addBinderReceivedListenerSticky(received);
        Shizuku.addBinderDeadListener(dead);
        Shizuku.addRequestPermissionResultListener(permission);
        connectIfGranted();
    }

    @Override public void onStart() { super.onStart(); main.post(poll); }
    @Override public void onStop() { main.removeCallbacks(poll); super.onStop(); }

    @Override public void onDestroy() {
        destroyed = true;
        Shizuku.removeBinderReceivedListener(received);
        Shizuku.removeBinderDeadListener(dead);
        Shizuku.removeRequestPermissionResultListener(permission);
        if (service != null || binding) {
            try { Shizuku.unbindUserService(args(), connection, false); }
            catch (RuntimeException ignored) { /* Shizuku may already have stopped. */ }
        }
        main.removeCallbacksAndMessages(null);
        worker.shutdownNow();
        super.onDestroy();
    }

    private void connectIfGranted() {
        if (destroyed || binding || service != null) return;
        try {
            if (!Shizuku.pingBinder()) { disconnected("Shizuku 未运行 · 请先启动 Shizuku"); return; }
            if (Shizuku.getVersion() < 13) { disconnected("需要 Shizuku 13 或更新版本"); return; }
            if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
                disconnected("Shizuku 已运行 · 等待授权"); return;
            }
            binding = true;
            connectionText.setText("正在连接独立守护服务…");
            Shizuku.bindUserService(args(), connection);
            main.postDelayed(() -> {
                if (binding && service == null) disconnected("连接超时，请确认 Shizuku 正常运行后重试");
            }, 15000);
        } catch (RuntimeException e) { disconnected("连接失败：" + e.getMessage()); }
    }

    private void requestConnection() {
        try {
            if (Shizuku.pingBinder() && Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
                Shizuku.requestPermission(1);
            } else connectIfGranted();
        } catch (RuntimeException e) { disconnected("无法请求授权：" + e.getMessage()); }
    }

    private interface RemoteCall { Bundle run(IPowerService remote) throws Exception; }

    private void call(RemoteCall operation, boolean persist) {
        IPowerService remote = service;
        if (remote == null || pending) return;
        pending = true;
        enableControls(false);
        worker.execute(() -> {
            try {
                Bundle result = operation.run(remote);
                main.post(() -> {
                    if (destroyed || service != remote) return;
                    pending = false;
                    latest = result;
                    if (persist) save(result);
                    render(result);
                });
            } catch (Exception e) {
                main.post(() -> { if (!destroyed) disconnected("服务不可用：" + e.getMessage()); });
            }
        });
    }

    private void save(Bundle result) {
        preferences.edit().putBoolean("keepAwake", result.getBoolean("keepAwake"))
                .putBoolean("panelOff", result.getBoolean("panelOff"))
                .putInt("restoreSetting", result.getInt("restoreSetting", -1)).apply();
    }

    private void render(Bundle status) {
        connectionText.setText("守护服务已连接 · " + (status.getInt("uid") == 0 ? "Root" : "ADB / Shell"));
        int mode = status.getInt("panelMode", -1);
        panelText.setText(switch (mode) {
            case 0 -> "实体屏幕已断电";
            case 1 -> "实体屏幕处于 Doze";
            case 2 -> "实体屏幕供电中";
            case 3 -> "实体屏幕 Doze Suspend";
            case 4 -> "实体屏幕 On Suspend";
            default -> "实体屏幕状态未知";
        });
        panelText.setTextColor(mode == 0 ? ACCENT : TEXT);
        String wake = status.getString("wakefulness", "Unknown");
        wakeText.setText(switch (wake) {
            case "Awake" -> "Android 已唤醒";
            case "Asleep" -> "Android 已休眠";
            case "Dozing" -> "Android 正在 Doze";
            case "Dreaming" -> "Android 正在屏保";
            default -> "Android 唤醒状态未知";
        });
        wakeText.setTextColor("Awake".equals(wake) ? ACCENT : TEXT);
        String powered = status.getString("powered", "unknown");
        powerText.setText("true".equals(powered) ? "● 已接通电源" : "false".equals(powered) ? "○ 未接通电源" : "电源状态未知");
        long updated = status.getLong("updatedAt");
        detailText.setText("充电唤醒设置：" + status.getInt("staySetting", -1)
                + "  ·  " + (updated == 0 ? "尚未读到状态" : "采样 " + DateFormat.getTimeInstance().format(new Date(updated))));
        String error = status.getString("error", "");
        errorText.setText(error.isEmpty()
                ? "独立守护约每 5 秒检查一次。关闭此页面后仍会继续运行。"
                    + (mode < 0 ? "\n当前系统未提供可解析的实体屏幕状态；请勿将开关目标视为已验证状态。" : "")
                : "操作或状态读取异常：" + error);
        rendering = true;
        keepSwitch.setChecked(status.getBoolean("keepAwake"));
        panelSwitch.setChecked(status.getBoolean("panelOff"));
        rendering = false;
        enableControls(true);
    }

    private void disconnected(String reason) {
        service = null; binding = false; pending = false;
        connectionText.setText(reason);
        panelText.setText("实体屏幕状态未知");
        wakeText.setText("Android 唤醒状态未知");
        powerText.setText("等待连接");
        panelText.setTextColor(MUTED); wakeText.setTextColor(MUTED);
        detailText.setText("未连接时，开关仅显示上次保存的目标");
        errorText.setText("需要已启动并授权的 Shizuku。服务恢复后重新连接即可继续。"
                + "\n服务中断时无法保证实体屏幕继续断电；充电唤醒设置可能仍保留。卸载前请连接并恢复设置。");
        rendering = true;
        keepSwitch.setChecked(preferences.getBoolean("keepAwake", false));
        panelSwitch.setChecked(preferences.getBoolean("panelOff", false));
        rendering = false;
        enableControls(false);
    }

    private void enableControls(boolean enabled) {
        keepSwitch.setEnabled(enabled); panelSwitch.setEnabled(enabled); restoreButton.setEnabled(enabled);
        connectButton.setEnabled(service == null && !binding);
    }

    private void buildUi() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(Color.rgb(12, 20, 32));
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(22), dp(24), dp(22), dp(24));
        scroll.addView(content);
        scroll.setOnApplyWindowInsetsListener((view, insets) -> {
            android.graphics.Insets bars = insets.getInsets(WindowInsets.Type.systemBars());
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return insets;
        });
        setContentView(scroll);
        text(content, "PANEL AWAKE", 13, ACCENT, true);
        text(content, "屏幕断电，系统继续运行", 26, TEXT, true);
        text(content, "为坏屏手机和远程控制准备的电源守护", 14, MUTED, false);

        LinearLayout connectionCard = card(content);
        connectionText = text(connectionCard, "正在检查 Shizuku…", 16, TEXT, true);
        connectButton = button(connectionCard, "授权 / 重新连接", this::requestConnection);

        LinearLayout states = card(content);
        text(states, "实际状态", 13, MUTED, false);
        panelText = text(states, "实体屏幕状态未知", 21, TEXT, true);
        text(states, "来自 SurfaceFlinger 的物理显示电源模式", 12, MUTED, false);
        wakeText = text(states, "Android 唤醒状态未知", 21, TEXT, true);
        text(states, "来自 PowerManager 的系统唤醒状态", 12, MUTED, false);
        powerText = text(states, "等待连接", 15, ACCENT, false);
        detailText = text(states, "", 12, MUTED, false);

        LinearLayout controls = card(content);
        text(controls, "守护开关", 13, MUTED, false);
        keepSwitch = toggle(controls, "接通电源时保持 Android 唤醒");
        text(controls, "通电时阻止闲置休眠；如果系统已休眠，将尝试唤醒。拔掉电源后不强制唤醒。关闭后恢复原来的充电唤醒设置。", 13, MUTED, false);
        panelSwitch = toggle(controls, "持续关闭实体屏幕供电");
        text(controls, "仅关闭物理显示输出，不发送锁屏指令。Android 和 RustDesk 可继续运行。关闭此开关会恢复屏幕供电。", 13, MUTED, false);
        keepSwitch.setOnCheckedChangeListener((view, enabled) -> {
            if (rendering || pending || latest == null) return;
            int baseline = preferences.getInt("restoreSetting", -1);
            if (enabled && !latest.getBoolean("keepAwake")) {
                baseline = latest.getInt("staySetting", -1);
                if (baseline < 0) { render(latest); return; }
                // Persist the baseline before the remote write, including a client crash window.
                preferences.edit().putInt("restoreSetting", baseline).apply();
            }
            int original = baseline;
            call(s -> s.setKeepAwake(enabled, original), true);
        });
        panelSwitch.setOnCheckedChangeListener((view, enabled) -> {
            if (!rendering && !pending) call(s -> s.setPanelOff(enabled), true);
        });

        LinearLayout footer = card(content);
        errorText = text(footer, "", 13, MUTED, false);
        restoreButton = button(footer, "停止守护并恢复设置", () -> new AlertDialog.Builder(this)
                .setTitle("停止守护？")
                .setMessage("将恢复实体屏幕供电，并恢复启用守护前的充电唤醒设置。坏屏手机可能重新出现显示故障恢复和卡顿。")
                .setNegativeButton("取消", null)
                .setPositiveButton("恢复", (dialog, which) -> call(IPowerService::restore, true)).show());
        text(content, "使用提醒：先确认 RustDesk 可连接，再关闭实体屏幕。系统重启或 Shizuku 停止后，请启动 Shizuku 并打开本 App 重新连接。\n\n保持唤醒不等于禁止厂商清理后台；RustDesk 仍需后台运行权限、屏幕共享授权和无障碍服务。\n\nv" + BuildConfig.VERSION_NAME + " · 无网络权限 · 无遥测", 12, MUTED, false);
        enableControls(false);
    }

    private LinearLayout card(LinearLayout parent) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL); card.setPadding(dp(18), dp(16), dp(18), dp(16));
        GradientDrawable background = new GradientDrawable();
        background.setColor(Color.rgb(22, 35, 50)); background.setCornerRadius(dp(18));
        card.setBackground(background);
        LinearLayout.LayoutParams layout = new LinearLayout.LayoutParams(-1, -2);
        layout.topMargin = dp(18); parent.addView(card, layout); return card;
    }

    private TextView text(LinearLayout parent, String value, int size, int color, boolean bold) {
        TextView text = new TextView(this); text.setText(value); text.setTextSize(size); text.setTextColor(color);
        text.setPadding(0, dp(5), 0, dp(5)); text.setLineSpacing(dp(3), 1);
        if (bold) text.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        parent.addView(text, new LinearLayout.LayoutParams(-1, -2)); return text;
    }

    private Button button(LinearLayout parent, String label, Runnable action) {
        Button button = new Button(this); button.setText(label); button.setAllCaps(false);
        button.setTextColor(Color.rgb(8, 31, 33)); button.setBackgroundTintList(ColorStateList.valueOf(ACCENT));
        button.setOnClickListener(view -> action.run()); parent.addView(button); return button;
    }

    private Switch toggle(LinearLayout parent, String label) {
        Switch toggle = new Switch(this); toggle.setText(label); toggle.setTextSize(16); toggle.setTextColor(TEXT);
        toggle.setPadding(0, dp(14), 0, dp(14)); toggle.setMinHeight(dp(64));
        parent.addView(toggle, new LinearLayout.LayoutParams(-1, -2)); return toggle;
    }

    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
}

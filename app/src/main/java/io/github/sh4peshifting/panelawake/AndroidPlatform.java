package io.github.sh4peshifting.panelawake;

import android.annotation.SuppressLint;
import android.os.IBinder;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/** Runs only inside the Shizuku shell/root UserService, never in the app process. */
public final class AndroidPlatform implements PowerController.Platform {
    public AndroidPlatform() {
        int uid = android.os.Process.myUid();
        if (uid != 0 && uid != 2000) throw new SecurityException("电源接口只允许在 Shizuku 的 shell/root 进程中运行");
    }

    private static final ExecutorService READERS = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "command-output"); t.setDaemon(true); return t;
    });

    @Override public StatusParser.State read() throws Exception {
        String power = command("/system/bin/dumpsys", "-t", "3", "power");
        String surface = command("/system/bin/dumpsys", "-t", "3", "SurfaceFlinger");
        return StatusParser.parse(power, surface, readStay());
    }

    @Override public int readStay() throws Exception {
        String value = command("/system/bin/settings", "get", "global", "stay_on_while_plugged_in").trim();
        return "null".equals(value) ? 0 : Integer.parseInt(value);
    }

    @Override public void writeStay(int value) throws Exception {
        if (value < 0 || value > 15) throw new IllegalArgumentException("无效的充电唤醒设置");
        command("/system/bin/settings", "put", "global", "stay_on_while_plugged_in", Integer.toString(value));
        if (readStay() != value) throw new IllegalStateException("系统拒绝更新充电唤醒设置");
    }

    @Override public void wake() throws Exception {
        // KEYCODE_WAKEUP requests system wakefulness; it never toggles the power key.
        command("/system/bin/input", "keyevent", "224");
        Thread.sleep(700);
    }

    // Shizuku UserService is a shell/root app_process, exempt from app hidden-API restrictions.
    // The constructor rejects ordinary app UIDs; never move this operation into the Activity.
    @SuppressLint({"BlockedPrivateApi", "SoonBlockedPrivateApi", "PrivateApi"})
    @Override public void setPanelPower(boolean on, String physicalId) throws Exception {
        Class<?> surface = Class.forName("android.view.SurfaceControl");
        Class<?> tokens = surface;
        Method ids;
        try { ids = surface.getDeclaredMethod("getPhysicalDisplayIds"); }
        catch (NoSuchMethodException moved) {
            tokens = Class.forName("com.android.server.display.DisplayControl");
            ids = tokens.getDeclaredMethod("getPhysicalDisplayIds");
        }
        long[] displays = (long[]) ids.invoke(null);
        if (displays == null || displays.length == 0) throw new IllegalStateException("没有可用的实体屏幕");
        long selected;
        if (!physicalId.isEmpty()) {
            selected = Long.parseUnsignedLong(physicalId);
            boolean found = false;
            for (long id : displays) if (id == selected) found = true;
            if (!found) throw new IllegalStateException("内置屏幕标识已改变，请刷新后重试");
        } else if (displays.length == 1) {
            selected = displays[0];
        } else {
            throw new IllegalStateException("存在多个实体屏幕，无法安全识别内置屏幕");
        }
        IBinder token = (IBinder) tokens.getDeclaredMethod("getPhysicalDisplayToken", long.class).invoke(null, selected);
        if (token == null) throw new IllegalStateException("无法取得实体屏幕控制接口");
        surface.getDeclaredMethod("setDisplayPowerMode", IBinder.class, int.class).invoke(null, token, on ? 2 : 0);
    }

    static String command(String... args) throws Exception {
        Process process = new ProcessBuilder(args).redirectErrorStream(true).start();
        Future<String> output = READERS.submit(() -> {
            try (InputStream stream = process.getInputStream(); ByteArrayOutputStream bytes = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[8192];
                int count;
                while ((count = stream.read(buffer)) != -1) {
                    if (bytes.size() + count > 4 * 1024 * 1024) throw new IllegalStateException("系统状态输出过大");
                    bytes.write(buffer, 0, count);
                }
                return new String(bytes.toByteArray(), StandardCharsets.UTF_8);
            }
        });
        try {
            if (!process.waitFor(8, TimeUnit.SECONDS)) throw new IllegalStateException("系统命令超时: " + args[0]);
            String result = output.get(2, TimeUnit.SECONDS);
            if (process.exitValue() != 0) throw new IllegalStateException("系统命令失败: " + args[0]);
            return result;
        } finally {
            process.destroyForcibly();
            output.cancel(true);
        }
    }
}

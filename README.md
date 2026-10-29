# Lucid · 清醒梦

通过 **Shizuku** 让 Android 在接通电源时保持唤醒，同时关闭实体屏幕供电。为坏屏手机继续使用 RustDesk、scrcpy 等远程控制工具而设计。

这是独立 Android App，不需要在电脑本地安装 Android SDK、Gradle 或 Android Studio。APK 由 GitHub Actions 编译和签名，发布在 [Releases](https://github.com/Sh4peshifting/Lucid/releases)。首次版本属于实验性预发布。

## 使用

1. 安装 Release 中的 `lucid-v0.2.0.apk`。需要 Android 12 或更新版本。
2. 安装并启动 [Shizuku](https://shizuku.rikka.app/download/)，Root 或无线调试启动均可。
3. 打开 Lucid，点击“连接”，允许 Shizuku 权限。
4. 先确认 RustDesk 可以远程操作，然后开启“保持清醒”和“让屏幕休息”。
5. 保持供电。可以离开 App、关闭 App 页面并断开电脑，独立 Shizuku UserService 会继续守护。

关闭“让屏幕休息”会恢复面板供电。关闭唤醒开关会恢复原来的充电唤醒设置；若另一个 App 已改变该设置，恢复时不会覆盖它。“停止守护并恢复设置”同时停止两个控制策略。**卸载前应先恢复设置。**

重启手机或停止 Shizuku 后，不保证守护服务仍在运行。请先启动 Shizuku，再打开本 App 重新连接；App 会应用上次保存的开关目标。非 Root 的 Shizuku 通常需要在每次重启后重新通过无线调试启动。没有静默绕过 Shizuku 授权或重启启动限制。

## 界面与状态来源

| 项目 | 来源 / 行为 |
| --- | --- |
| 实体屏幕电源状态 | 特权 `dumpsys SurfaceFlinger` 中内置物理显示的 `powerMode` |
| Android 唤醒状态 | `dumpsys power` 的 `mWakefulness` |
| 外接电源状态 | `dumpsys power` 的 `mIsPowered` |
| 通电保持唤醒 | 将 `stay_on_while_plugged_in` 设为 7（交流、USB、无线供电）；通电且休眠时用 `KEYCODE_WAKEUP` 请求唤醒 |
| 关闭面板 | 在 Shizuku UserService 中调用隐藏的 `SurfaceControl.setDisplayPowerMode(token, 0)`，不发送锁屏、电源键或休眠指令 |

开关代表**目标策略**，状态卡代表**实际采样结果**。状态约每 5 秒采样，屏幕电源读取失败时显示“未知”，不根据开关猜测成功。这里的实体屏幕状态是 Android 显示栈报告的电源模式，不是物理电压传感器测量。

守护在 shell/root 身份的独立进程中运行，不依赖 Activity 保活，也不申请网络、无障碍或设备管理员权限。没有遥测，不保存或上传完整系统转储。仅保存开关选择和启用前的充电唤醒设置。

## 兼容性与限制

- 主要目标为 Android 12、单内置屏幕手机；最初使用场景为 OnePlus 7T。系统私有 API 和 SurfaceFlinger 文本格式可能随 Android/OEM 改变。未知格式会显示“未知”；无法安全识别多个物理屏幕时拒绝控制。
- 仅关闭面板供电，**不保证所有 ROM 的 MediaProjection/RustDesk 都继续出画面**。RustDesk 应保持屏幕共享授权、无障碍和后台运行权限；系统唤醒不等于禁止厂商杀后台。
- 开启通电唤醒后，会尝试重新唤醒被主动休眠的系统。未接通电源时不主动唤醒，正常省电策略仍可生效。
- 系统重新点亮面板时，守护会在下次检查时再次关闭它，可能有不超过一个检查周期的供电窗口。
- Shizuku 或守护意外终止时，系统设置可能保留。重新打开本 App 可恢复；也可通过 ADB 手动设置 `settings put global stay_on_while_plugged_in <原值>`。恢复面板可使用 scrcpy 的 `MOD+Shift+O`，或重启设备。
- 本 App 不修复损坏的屏幕、排线或显示驱动，也不承诺永久无人值守。

## 构建与发布

所有编译在 GitHub Actions 的 Linux runner 中进行：Java 17、Gradle 8.11.1、Android Gradle Plugin 8.9.2、Android SDK 35。未把 SDK 或 Gradle 安装到用户电脑。

- 每次推送运行策略/解析单元测试、Android lint 和 APK 构建。
- Android 12 模拟器验证无 Shizuku 时界面启动/重建，并以 shell 身份实际检查“面板 OFF + Android Awake + 面板 ON”。模拟器测试不覆盖 Shizuku 授权窗口、真实 OEM 驱动或 RustDesk 长期稳定性。
- `v0.2.0` 标签在所有检查通过后自动发布已签名 APK、SHA-256 校验文件和签名证书指纹。
- 仓库 Secrets：`ANDROID_KEYSTORE_BASE64`、`ANDROID_KEYSTORE_PASSWORD`。签名别名为 `panel-awake`；私钥不进入 Git 或 Release，后续版本使用相同密钥才能覆盖升级。
- 没有签名 Secrets 的分支仍可运行测试并构建未签名 Release；发布标签必须有签名。

## 参考

- [Shizuku API / UserService](https://github.com/RikkaApps/Shizuku-API)：提供 shell/root 进程和权限授权。
- [AOSP SurfaceControl（Android 12）](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/android12-release/core/java/android/view/SurfaceControl.java)：物理显示电源接口。
- [Android STAY_ON_WHILE_PLUGGED_IN](https://developer.android.com/reference/android/provider/Settings.Global#STAY_ON_WHILE_PLUGGED_IN)。
- [scrcpy 设备电源功能](https://github.com/Genymobile/scrcpy/blob/master/doc/device.md)：`-S` 使用同类显示层操作。本项目为独立实现，没有打包 scrcpy。

## Kotlin 与 Material Design 3

App、Shizuku 服务、状态解析和测试均使用 Kotlin 2.2.21。界面采用 Jetpack Compose Material 3，支持跟随系统的浅色/深色主题与 Android 12 动态配色。使用 ViewModel、StateFlow 和协程处理连接与后台状态，Binder 操作不阻塞主线程。构建脚本仍使用 Gradle Groovy DSL，AIDL 接口由 Android 工具生成。

包名、签名密钥、原设置存储和服务标识保持兼容，v0.1.0 可直接覆盖升级。签名别名和证书名称沿用最初版本，以保证后续更新兼容。

MIT License.

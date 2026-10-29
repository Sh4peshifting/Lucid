package io.github.sh4peshifting.panelawake

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.text.DateFormat
import java.util.Date

@Composable
fun LucidScreen(
    state: LucidUiState,
    onConnect: () -> Unit,
    onKeepAwake: (Boolean) -> Unit,
    onPanelOff: (Boolean) -> Unit,
    onRestore: () -> Unit,
) {
    var confirmRestore by rememberSaveable { mutableStateOf(false) }
    var showHelp by rememberSaveable { mutableStateOf(false) }
    val colors = MaterialTheme.colorScheme
    Scaffold(containerColor = colors.background) { insets ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(insets).testTag("lucid_list"),
            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.weight(1f)) {
                        Text("Lucid", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(4.dp))
                        Text("清醒梦", style = MaterialTheme.typography.titleSmall, color = colors.primary)
                    }
                    Surface(shape = CircleShape, color = colors.primaryContainer, modifier = Modifier.size(60.dp)) {
                        Glyph("moon", colors.onPrimaryContainer, Modifier.padding(17.dp))
                    }
                }
                Spacer(Modifier.height(16.dp))
                Text("屏幕休息，系统清醒。", style = MaterialTheme.typography.titleLarge, color = colors.onSurfaceVariant)
            }
            item {
                Card(colors = CardDefaults.cardColors(containerColor = colors.secondaryContainer), shape = RoundedCornerShape(24.dp)) {
                    Row(Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(if (state.connected) "Shizuku 已连接" else if (state.connecting) "正在连接" else "连接 Shizuku",
                                style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
                            Spacer(Modifier.height(4.dp))
                            Text(if (state.connected) "关闭页面后继续守护" else state.connection,
                                style = MaterialTheme.typography.bodySmall, color = colors.onSecondaryContainer)
                        }
                        if (state.connecting) CircularProgressIndicator(Modifier.padding(start = 12.dp).size(24.dp), strokeWidth = 2.dp)
                        else if (state.connected) Badge(containerColor = colors.primary, contentColor = colors.onPrimary) {
                            Text(if (state.uid == 0) "ROOT" else "ADB", modifier = Modifier.padding(6.dp, 2.dp))
                        }
                        else FilledTonalButton(onClick = onConnect, modifier = Modifier.padding(start = 12.dp).testTag("connect")) { Text("连接") }
                    }
                }
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("此刻", style = MaterialTheme.typography.titleSmall, color = colors.onSurfaceVariant)
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        StateTile("实体屏幕", panelLabel(state.panelMode), "display", state.panelMode == 0,
                            Modifier.weight(1f).testTag("panel_status"))
                        StateTile("Android", wakeLabel(state.wakefulness), "spark", state.wakefulness == "Awake",
                            Modifier.weight(1f).testTag("wake_status"))
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Glyph("plug", colors.onSurfaceVariant, Modifier.size(16.dp))
                        Text(when (state.powered) { true -> "已接通电源"; false -> "未接通电源"; null -> "电源状态未知" },
                            style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                        Text("·", color = colors.outline)
                        Text(if (state.updatedAt == 0L) "等待状态采样" else DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(state.updatedAt)) + " 更新",
                            style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                    }
                }
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("守护", style = MaterialTheme.typography.titleSmall, color = colors.onSurfaceVariant)
                    Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = colors.surfaceContainer)) {
                        ControlRow("保持清醒", "接通电源时保持 Android 唤醒；拔掉电源后允许正常休眠。", "spark",
                            state.keepAwake, state.controlsEnabled, onKeepAwake, "keep_awake")
                        HorizontalDivider(Modifier.padding(horizontal = 20.dp), color = colors.outlineVariant)
                        ControlRow("让屏幕休息", "持续关闭实体屏幕供电，保留系统和远程控制运行。", "moon",
                            state.panelOff, state.controlsEnabled, onPanelOff, "panel_off")
                    }
                    Text(when {
                        state.busy -> "正在应用设置…"
                        state.connected -> "开关是守护目标，上方是实际采样状态。"
                        else -> "未连接时，开关仅显示上次保存的目标。"
                    }, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                }
            }
            if (state.error.isNotEmpty()) item {
                Card(colors = CardDefaults.cardColors(containerColor = colors.errorContainer)) {
                    Text(state.error, Modifier.padding(16.dp).testTag("operation_error"), color = colors.onErrorContainer,
                        style = MaterialTheme.typography.bodyMedium)
                }
            }
            item {
                OutlinedCard(onClick = { showHelp = !showHelp }, shape = RoundedCornerShape(20.dp)) {
                    Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Info, contentDescription = null, tint = colors.primary)
                        Text("使用与状态说明", Modifier.weight(1f).padding(start = 12.dp), style = MaterialTheme.typography.labelLarge)
                        Text(if (showHelp) "收起" else "展开", style = MaterialTheme.typography.labelMedium, color = colors.primary)
                    }
                    AnimatedVisibility(showHelp) {
                        Text("先确认 RustDesk 可以连接，再开启屏幕休息。两个开关可独立使用。\n\n"
                            + "实体屏幕状态由系统显示栈报告，不能测量硬件电压。显示未知时，无法确认面板已断电。\n\n"
                            + "状态约每 5 秒检查一次。Shizuku 停止或手机重启后，请启动 Shizuku 并重新打开 Lucid。\n\n"
                            + "保持清醒不能保证厂商永不清理后台；RustDesk 仍需屏幕共享、无障碍及后台运行权限。卸载前请先停止守护并恢复设置。",
                            Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp), style = MaterialTheme.typography.bodyMedium,
                            color = colors.onSurfaceVariant)
                    }
                }
            }
            item {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    TextButton(onClick = { confirmRestore = true }, enabled = state.controlsEnabled,
                        modifier = Modifier.testTag("restore")) { Text("停止守护并恢复设置") }
                    Text("Lucid ${BuildConfig.VERSION_NAME} · 无网络权限 · 无遥测", style = MaterialTheme.typography.labelSmall,
                        color = colors.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp, bottom = 12.dp))
                }
            }
        }
    }
    if (confirmRestore) AlertDialog(
        onDismissRequest = { confirmRestore = false },
        title = { Text("结束这场清醒梦？") },
        text = { Text("将停止两个守护策略，恢复实体屏幕供电及原来的充电唤醒设置。坏屏手机可能重新出现卡顿。") },
        confirmButton = { TextButton(onClick = { confirmRestore = false; onRestore() }) { Text("停止并恢复") } },
        dismissButton = { TextButton(onClick = { confirmRestore = false }) { Text("继续守护") } },
    )
}

@Composable
private fun StateTile(title: String, value: String, glyph: String, active: Boolean, modifier: Modifier) {
    val colors = MaterialTheme.colorScheme
    Card(modifier = modifier, shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = if (active) colors.primaryContainer else colors.surfaceContainerLow)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Glyph(glyph, if (active) colors.onPrimaryContainer else colors.onSurfaceVariant, Modifier.size(24.dp))
            Text(title, style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
private fun ControlRow(title: String, description: String, glyph: String, checked: Boolean, enabled: Boolean,
                       onCheckedChange: (Boolean) -> Unit, tag: String) {
    val colors = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth().testTag(tag).toggleable(checked, enabled, Role.Switch, onCheckedChange).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Glyph(glyph, colors.primary, Modifier.size(24.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            Text(description, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = null, enabled = enabled)
    }
}

private fun panelLabel(mode: Int) = when (mode) {
    0 -> "已断电"; 1 -> "低功耗"; 2 -> "供电中"; 3 -> "低功耗挂起"; 4 -> "供电挂起"; else -> "未知"
}
private fun wakeLabel(wake: String) = when (wake) {
    "Awake" -> "清醒中"; "Asleep" -> "已休眠"; "Dozing" -> "Doze 中"; "Dreaming" -> "屏保中"; else -> "未知"
}

/** Small native vector glyphs; no raster assets or runtime downloads. */
@Composable
private fun Glyph(kind: String, color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier.clip(RoundedCornerShape(2.dp))) {
        val unit = size.minDimension / 24f
        val stroke = Stroke(1.8f * unit, cap = StrokeCap.Round)
        fun point(x: Float, y: Float) = androidx.compose.ui.geometry.Offset(x * unit, y * unit)
        when (kind) {
            "moon" -> drawPath(Path().apply {
                moveTo(18 * unit, 15.5f * unit)
                cubicTo(8 * unit, 18 * unit, 4 * unit, 8 * unit, 10 * unit, 3 * unit)
                cubicTo(-2 * unit, 4 * unit, 0 * unit, 23 * unit, 14 * unit, 21 * unit)
                cubicTo(18 * unit, 20 * unit, 20 * unit, 17 * unit, 21 * unit, 13 * unit)
                cubicTo(20 * unit, 14 * unit, 19 * unit, 15 * unit, 18 * unit, 15.5f * unit)
                close()
            }, color)
            "display" -> {
                drawRoundRect(color, point(3f, 4f), androidx.compose.ui.geometry.Size(18 * unit, 13 * unit),
                    androidx.compose.ui.geometry.CornerRadius(2 * unit), style = stroke)
                drawLine(color, point(12f, 17f), point(12f, 21f), stroke.width, StrokeCap.Round)
                drawLine(color, point(8f, 21f), point(16f, 21f), stroke.width, StrokeCap.Round)
            }
            "plug" -> {
                drawLine(color, point(8f, 3f), point(8f, 9f), stroke.width, StrokeCap.Round)
                drawLine(color, point(16f, 3f), point(16f, 9f), stroke.width, StrokeCap.Round)
                drawRoundRect(color, point(6f, 8f), androidx.compose.ui.geometry.Size(12 * unit, 9 * unit),
                    androidx.compose.ui.geometry.CornerRadius(3 * unit), style = stroke)
                drawLine(color, point(12f, 17f), point(12f, 22f), stroke.width, StrokeCap.Round)
            }
            else -> drawPath(Path().apply {
                moveTo(13 * unit, 2 * unit); lineTo(5 * unit, 13 * unit); lineTo(11 * unit, 13 * unit)
                lineTo(10 * unit, 22 * unit); lineTo(19 * unit, 10 * unit); lineTo(13 * unit, 10 * unit); close()
            }, color, style = stroke)
        }
    }
}

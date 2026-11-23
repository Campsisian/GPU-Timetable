/*
 * 广职大课表 —— 广州职业技术大学教务系统课表客户端
 * Copyright (C) 2026 Campsisian
 *
 * 本程序是自由软件：你可以依据 GNU 通用公共许可证（GPLv3）的条款
 * 再分发和/或修改它。本程序按“原样”提供，不带任何担保。
 * 完整条款见项目根目录的 LICENSE 文件。
 */

package com.gzpyp.timetable.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.view.View
import android.widget.FrameLayout
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.gzpyp.timetable.AppViewModel
import com.gzpyp.timetable.BuildConfig
import com.gzpyp.timetable.data.AppSettings
import com.gzpyp.timetable.notify.Reminders
import com.gzpyp.timetable.widget.CourseCardWidget

/** 作者与项目地址，改这里就能一次性改掉「关于」里的所有链接 */
private const val AUTHOR = "Campsisian"
private const val GITHUB_URL = "https://github.com/Campsisian"
private const val REPO_URL = "https://github.com/Campsisian/GPU-Timetable"
private const val LICENSE_URL = "https://www.gnu.org/licenses/gpl-3.0.html"

@Composable
fun SettingsScreen(vm: AppViewModel) {
    val s = vm.settings
    var account by remember(s.account) { mutableStateOf(s.account) }
    var password by remember(s.password) { mutableStateOf(s.password) }
    var showPassword by remember { mutableStateOf(false) }
    var confirmClear by remember { mutableStateOf(false) }
    var importMsg by remember { mutableStateOf<String?>(null) }
    var testMsg by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current

    // 从系统设置页返回时要重新看一眼权限状态，不然「已授权」还停在旧值上
    val resumeTick = rememberResumeTick()
    val notifyOk = remember(resumeTick) { Reminders.canNotify(context) }
    val exactOk = remember(resumeTick) { Reminders.canExact(context) }
    val bgOk = remember(resumeTick) { Reminders.canRunInBackground(context) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        importMsg = if (granted) "通知权限已开启" else "通知权限被拒绝，提醒不会弹出"
    }

    val askNotify: () -> Unit = {
        if (Build.VERSION.SDK_INT >= 33) {
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            openSystemPage(
                context,
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            )
        }
    }

    Column(Modifier.fillMaxSize()) {
        AppTopBar(title = "设置")

        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            SettingGroup("教务账号") {
                Column(Modifier.padding(horizontal = 16.dp).padding(top = 8.dp)) {
                    OutlinedTextField(
                        value = account,
                        onValueChange = { account = it.trim() },
                        label = { Text("学号") },
                        singleLine = true,
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it.trim() },
                        label = { Text("密码") },
                        singleLine = true,
                        shape = MaterialTheme.shapes.medium,
                        visualTransformation = if (showPassword)
                            androidx.compose.ui.text.input.VisualTransformation.None
                        else androidx.compose.ui.text.input.PasswordVisualTransformation(),
                        trailingIcon = {
                            TextButton(onClick = { showPassword = !showPassword }) {
                                Text(
                                    if (showPassword) "隐藏" else "显示",
                                    style = MaterialTheme.typography.labelLarge
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = {
                                vm.updateSettings(s.copy(account = account, password = password))
                                vm.sync(true)
                            },
                            enabled = account.isNotBlank() && password.isNotBlank() && !vm.syncing
                        ) { Text("保存并更新") }
                        TextButton(
                            onClick = {
                                vm.updateSettings(s.copy(account = account, password = password))
                                importMsg = "已保存"
                            }
                        ) { Text("仅保存") }
                    }
                    if (vm.syncing) {
                        Text(
                            vm.syncMessage ?: "正在同步…",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                }
            }

            SettingGroup("自动更新") {
                SwitchRow(
                    title = "打开应用时自动更新",
                    subtitle = "每次启动静默抓取一次最新课表",
                    checked = s.autoSyncOnLaunch,
                    onChange = { vm.updateSettings(s.copy(autoSyncOnLaunch = it)) }
                )
                SettingDivider()
                SwitchRow(
                    title = "每天定时更新",
                    subtitle = "后台静默同步，不打扰你",
                    checked = s.dailySyncEnabled,
                    onChange = { vm.updateSettings(s.copy(dailySyncEnabled = it)) }
                )
                if (s.dailySyncEnabled) {
                    SettingDivider()
                    StepperRow(
                        title = "同步时间",
                        value = "${s.dailySyncHour}:00",
                        onMinus = {
                            val h = if (s.dailySyncHour <= 0) 23 else s.dailySyncHour - 1
                            vm.updateSettings(s.copy(dailySyncHour = h))
                        },
                        onPlus = {
                            val h = if (s.dailySyncHour >= 23) 0 else s.dailySyncHour + 1
                            vm.updateSettings(s.copy(dailySyncHour = h))
                        }
                    )
                }
                SettingDivider()
                Row(
                    Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = { vm.sync(true) }, enabled = !vm.syncing) { Text("立即更新") }
                    Spacer(Modifier.weight(1f))
                    Text(
                        "上次：${vm.formattedLastSync()}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                vm.syncMessage?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 10.dp)
                    )
                }
            }

            SettingGroup {
                SwitchRow(
                    title = "上课提醒",
                    checked = s.reminderEnabled,
                    onChange = {
                        vm.updateSettings(s.copy(reminderEnabled = it))
                        if (it && !notifyOk) askNotify()
                    }
                )
                if (s.reminderEnabled) {
                    SettingDivider()
                    StepperRow(
                        title = "提前",
                        value = "${s.reminderMinutes} 分钟",
                        onMinus = {
                            vm.updateSettings(
                                s.copy(reminderMinutes = (s.reminderMinutes - 5).coerceAtLeast(0))
                            )
                        },
                        onPlus = {
                            vm.updateSettings(
                                s.copy(reminderMinutes = (s.reminderMinutes + 5).coerceAtMost(60))
                            )
                        }
                    )
                    SettingDivider()
                    Row(
                        Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(
                            onClick = {
                                Reminders.testNotification(context)
                                testMsg = "已发一条；12 秒后还会有一条走闹钟，两条都到就说明整条链路正常"
                            },
                            contentPadding = ButtonDefaults.TextButtonContentPadding
                        ) { Text("发送测试通知", style = MaterialTheme.typography.labelLarge) }
                    }
                    testMsg?.let {
                        Text(
                            it,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 6.dp)
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    CheckRow(
                        label = "通知权限", ok = notifyOk,
                        okText = "已开启", badText = "未开启", action = "去开启",
                        onFix = askNotify
                    )
                    CheckRow(
                        label = "精确闹钟", ok = exactOk,
                        okText = "已授权", badText = "未授权", action = "去授权",
                        onFix = {
                            openSystemPage(
                                context,
                                Intent(
                                    Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                                    Uri.parse("package:${context.packageName}")
                                )
                            )
                        }
                    )
                    CheckRow(
                        label = "后台运行", ok = bgOk,
                        okText = "已允许", badText = "受限制", action = "去设置",
                        onFix = {
                            openSystemPage(
                                context,
                                Intent(
                                    Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                                    Uri.parse("package:${context.packageName}")
                                )
                            )
                        }
                    )
                    Spacer(Modifier.height(8.dp))
                }
            }

            SettingGroup("外观") {
                SettingRow(
                    title = "主题",
                    subtitle = when (s.themeMode) {
                        THEME_LIGHT -> "始终使用浅色"
                        THEME_DARK -> "始终使用深色"
                        else -> "跟随系统设置"
                    }
                ) {
                    SegmentedRow(
                        options = listOf("跟随系统", "浅色", "深色"),
                        selected = s.themeMode.coerceIn(0, 2),
                        onSelect = { vm.updateSettings(s.copy(themeMode = it)) }
                    )
                }
                SettingDivider()
                SwitchRow(
                    title = "跟随壁纸取色",
                    subtitle = if (Build.VERSION.SDK_INT >= 31) {
                        "Android 12 及以上：用壁纸配色替换应用绿色主题"
                    } else {
                        "需要 Android 12 及以上，当前系统不支持"
                    },
                    checked = s.dynamicColor && Build.VERSION.SDK_INT >= 31,
                    enabled = Build.VERSION.SDK_INT >= 31,
                    onChange = { vm.updateSettings(s.copy(dynamicColor = it)) }
                )
            }

            SettingGroup("启动页面") {
                SettingRow(title = "打开应用时先显示") {
                    SegmentedRow(
                        options = listOf("课表", "今日"),
                        selected = s.defaultTab.coerceIn(0, 1),
                        onSelect = { vm.updateSettings(s.copy(defaultTab = it)) }
                    )
                }
            }

            WidgetCard(vm)

            SettingGroup("课表") {
                InfoRow("学期", s.semester.ifBlank { "未获取" })
                InfoRow("第一周周一", vm.timetable.config.startDate)
                InfoRow("总周数", "${vm.timetable.config.totalWeeks} 周")
                InfoRow("课程数", "${vm.timetable.courses.size} 节")
                SettingDivider()
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    TextButton(onClick = {
                        val text = clipboard.getText()?.text
                        if (text.isNullOrBlank()) {
                            importMsg = "剪贴板是空的"
                        } else {
                            importMsg = if (vm.importTimetable(text)) "导入成功" else "不是有效的课表 JSON"
                        }
                    }) { Text("从剪贴板导入") }
                    TextButton(onClick = {
                        clipboard.setText(
                            androidx.compose.ui.text.AnnotatedString(vm.timetable.toJsonString())
                        )
                        importMsg = "已复制到剪贴板"
                    }) { Text("复制课表 JSON") }
                }
                importMsg?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 10.dp)
                    )
                }
            }

            SettingGroup("当前周") {
                SwitchRow(
                    title = "手动指定周次",
                    subtitle = if (s.weekOverride > 0) "已固定在第 ${s.weekOverride} 周"
                    else "按学期起始日自动计算",
                    checked = s.weekOverride > 0,
                    onChange = {
                        vm.updateSettings(
                            s.copy(weekOverride = if (it) vm.resolveWeek() else 0)
                        )
                    }
                )
                if (s.weekOverride > 0) {
                    SettingDivider()
                    StepperRow(
                        title = "周次",
                        value = "第 ${s.weekOverride} 周",
                        onMinus = {
                            vm.updateSettings(
                                s.copy(weekOverride = (s.weekOverride - 1).coerceAtLeast(1))
                            )
                        },
                        onPlus = {
                            vm.updateSettings(
                                s.copy(weekOverride = (s.weekOverride + 1).coerceAtMost(vm.totalWeeks))
                            )
                        }
                    )
                }
            }

            SettingGroup {
                SettingRow(
                    title = "清空本机数据",
                    subtitle = "删除课表、账号和登录会话，不可撤销",
                    onClick = { confirmClear = true },
                    trailing = {
                        Text(
                            "删除",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                )
            }

            AboutGroup()

            Spacer(Modifier.height(28.dp))
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("清空数据？") },
            text = { Text("将会删除本机保存的课表、账号和会话，操作不可撤销。") },
            confirmButton = {
                TextButton(onClick = {
                    vm.clearAll(); confirmClear = false
                }) { Text("确定清空", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }) { Text("取消") }
            }
        )
    }
}

/**
 * 「关于」：应用信息、作者、仓库、开源协议。
 * 原来贴在设置页最底部的那行小字（版本号 / 数据来源）挪到了这里。
 */
@Composable
private fun AboutGroup() {
    SettingGroup("关于") {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AppBadge()
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text("广职大课表", style = MaterialTheme.typography.titleMedium)
                Text(
                    "版本 ${BuildConfig.VERSION_NAME}（build ${BuildConfig.VERSION_CODE}）",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        SettingDivider()
        InfoRow("开发者", AUTHOR)
        InfoRow("数据来源", "教务系统 jiaowu.gzpyp.edu.cn")
        SettingDivider()
        LinkRow("GitHub 主页", "github.com/$AUTHOR", GITHUB_URL)
        SettingDivider()
        LinkRow("项目仓库", "github.com/$AUTHOR/GPU-Timetable", REPO_URL)
        SettingDivider()
        LinkRow("开源协议", "GNU General Public License v3.0", LICENSE_URL)
        SettingDivider()
        Text(
            "本应用为个人开发的非官方客户端，与学校及教务系统提供方无关。" +
                "账号密码与课表只保存在本机，验证码识别在本地完成，不上传任何服务器。" +
                "按 GPLv3 协议开源，欢迎自行修改和分发。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
        )
        Text(
            "Copyright © 2026 $AUTHOR",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 14.dp)
        )
    }
}

/**
 * 桌面小组件：外观设置 + 可选预览。
 * 预览是把真正的 RemoteViews 组件 inflate 出来再截图，所以和桌面上一致。
 */
@Composable
private fun WidgetCard(vm: AppViewModel) {
    val ctx = LocalContext.current
    val s = vm.settings
    val dark = LocalAppDark.current
    var previewOpen by remember { mutableStateOf(false) }
    var alpha by remember(s.widgetBgAlpha) { mutableFloatStateOf(s.widgetBgAlpha.toFloat()) }

    SettingGroup("桌面小组件") {
        // 渲染预览要真的 inflate 一遍 RemoteViews 再画位图，开销不小，所以默认折叠
        Row(
            Modifier
                .fillMaxWidth()
                .clickable { previewOpen = !previewOpen }
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                if (previewOpen) "收起渲染效果" else "查看渲染效果",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f)
            )
            Text(
                if (previewOpen) "▲" else "▼",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }

        if (previewOpen) {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                val todayBmp = remember(s, vm.timetable, dark) {
                    runCatching {
                        renderWidget(ctx, s, vm.timetable, isUpcoming = false, wDp = 250, hDp = 148)
                    }.getOrNull()
                }
                val upcomingBmp = remember(s, vm.timetable, dark) {
                    runCatching {
                        renderWidget(ctx, s, vm.timetable, isUpcoming = true, wDp = 250, hDp = 250)
                    }.getOrNull()
                }
                if (todayBmp != null) {
                    WidgetPreviewFrame(todayBmp, 250f / 148f)
                    Spacer(Modifier.height(10.dp))
                }
                if (upcomingBmp != null) {
                    WidgetPreviewFrame(upcomingBmp, 250f / 250f)
                }
                if (todayBmp == null && upcomingBmp == null) {
                    Text(
                        "预览生成失败",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }

        SettingDivider()
        Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            Text(
                "背景不透明度  ${alpha.toInt()}%",
                style = MaterialTheme.typography.bodyMedium
            )
            Slider(
                value = alpha,
                onValueChange = { alpha = it },
                onValueChangeFinished = { vm.updateSettings(s.copy(widgetBgAlpha = alpha.toInt())) },
                valueRange = 20f..100f,
                steps = 15
            )
        }

        SettingDivider()
        SettingRow(title = "字号") {
            SegmentedRow(
                options = listOf("小", "标准", "大", "特大"),
                selected = when {
                    s.widgetFontScale <= 85 -> 0
                    s.widgetFontScale >= 130 -> 3
                    s.widgetFontScale >= 115 -> 2
                    else -> 1
                },
                onSelect = { i ->
                    val v = intArrayOf(85, 100, 115, 130)[i]
                    vm.updateSettings(s.copy(widgetFontScale = v))
                }
            )
        }
        SettingDivider()
        SettingRow(title = "圆角") {
            SegmentedRow(
                options = listOf("小", "中", "大", "特大"),
                selected = s.widgetCorner.coerceIn(0, 3),
                onSelect = { vm.updateSettings(s.copy(widgetCorner = it)) }
            )
        }
        SettingDivider()
        SettingRow(title = "配色") {
            SegmentedRow(
                options = listOf("自动", "深色", "浅色"),
                selected = s.widgetTheme.coerceIn(0, 2),
                onSelect = { vm.updateSettings(s.copy(widgetTheme = it)) }
            )
        }
        SettingDivider()
        SwitchRow(
            title = "显示地点",
            subtitle = "卡片第二行显示上课教室",
            checked = s.widgetShowLocation,
            onChange = { vm.updateSettings(s.copy(widgetShowLocation = it)) }
        )
        SettingDivider()
        SwitchRow(
            title = "显示教师",
            subtitle = "卡片第三行显示任课老师",
            checked = s.widgetShowTeacher,
            onChange = { vm.updateSettings(s.copy(widgetShowTeacher = it)) }
        )
    }
}

@Composable
private fun WidgetPreviewFrame(bmp: Bitmap, aspect: Float) {
    Box(
        Modifier
            .fillMaxWidth()
            .aspectRatio(aspect)
            .background(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.shapes.medium)
    ) {
        Image(
            bitmap = bmp.asImageBitmap(),
            contentDescription = "桌面小组件预览",
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.FillBounds
        )
    }
}

/** 把真实的 RemoteViews 组件 inflate 出来，按指定 dp 尺寸画成位图 */
private fun renderWidget(
    ctx: Context,
    s: AppSettings,
    timetable: com.gzpyp.timetable.core.Timetable,
    isUpcoming: Boolean,
    wDp: Int,
    hDp: Int
): Bitmap {
    val views = CourseCardWidget.buildPreview(
        ctx = ctx,
        tt = timetable,
        settings = s,
        mode = if (isUpcoming) CourseCardWidget.MODE_UPCOMING else CourseCardWidget.MODE_TODAY,
        wDp = wDp,
        hDp = hDp
    )
    val host = FrameLayout(ctx)
    val view: View = views.apply(ctx, host)
    val density = ctx.resources.displayMetrics.density
    val w = (wDp * density).toInt().coerceAtLeast(1)
    val h = (hDp * density).toInt().coerceAtLeast(1)
    view.measure(
        View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY),
        View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY)
    )
    view.layout(0, 0, w, h)
    val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    view.draw(Canvas(bmp))
    return bmp
}

/** 页面每次回到前台就 +1，用来触发权限状态的重新读取 */
@Composable
private fun rememberResumeTick(): Int {
    val owner = LocalLifecycleOwner.current
    var tick by remember { mutableIntStateOf(0) }
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) tick++
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    return tick
}

/** 跳系统设置页；个别 ROM 没有对应页面时退回「应用信息」 */
private fun openSystemPage(ctx: Context, intent: Intent) {
    val full = intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { ctx.startActivity(full) }.onFailure {
        runCatching {
            ctx.startActivity(
                Intent(
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:${ctx.packageName}")
                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }
}

/*
 * 广职大课表 —— 广州职业技术大学教务系统课表客户端
 * Copyright (C) 2026 Campsisian
 *
 * 本程序是自由软件：你可以依据 GNU 通用公共许可证（GPLv3）的条款
 * 再分发和/或修改它。本程序按“原样”提供，不带任何担保。
 * 完整条款见项目根目录的 LICENSE 文件。
 */

package com.gzpyp.timetable.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 统一的顶栏。标题下可带一行小字（学期、日期之类），操作按钮放右侧。
 * 容器透明，内容滚到下面时由系统给的 scrolledContainerColor 兜底。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppTopBar(
    title: String,
    subtitle: String? = null,
    actions: @Composable RowScope.() -> Unit = {}
) {
    TopAppBar(
        title = {
            Column {
                Text(
                    title,
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (subtitle != null) {
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        },
        actions = actions,
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = Color.Transparent,
            scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer
        )
    )
}

/**
 * M3 的设置分组：外面一行小标题（primary 色），里面一张 surfaceContainerLow 的圆角卡片。
 * 用色调差而不是描边来区分层级，这是 M3 的推荐做法。
 */
@Composable
fun SettingGroup(
    title: String? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(Modifier.fillMaxWidth()) {
        if (title != null) {
            Text(
                title,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
            )
        }
        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(vertical = 4.dp), content = content)
        }
    }
}

/** 分组内的一条设置项：标题 / 说明 / 右侧控件 / 下方可展开内容 */
@Composable
fun SettingRow(
    title: String,
    subtitle: String? = null,
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    content: (@Composable ColumnScope.() -> Unit)? = null
) {
    val alpha = if (enabled) 1f else 0.45f
    Column(
        Modifier
            .fillMaxWidth()
            .then(
                if (onClick != null) Modifier.clickable(enabled = enabled, onClick = onClick)
                else Modifier
            )
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha)
                )
                if (subtitle != null) {
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha),
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }
            if (trailing != null) {
                Spacer(Modifier.width(12.dp))
                trailing()
            }
        }
        if (content != null) {
            Column(Modifier.padding(top = 10.dp), content = content)
        }
    }
}

/** 开关行 */
@Composable
fun SwitchRow(
    title: String,
    subtitle: String? = null,
    checked: Boolean,
    enabled: Boolean = true,
    onChange: (Boolean) -> Unit
) {
    SettingRow(
        title = title,
        subtitle = subtitle,
        enabled = enabled,
        trailing = { Switch(checked = checked, onCheckedChange = onChange, enabled = enabled) }
    )
}

/** 分组内分隔线，左侧留白对齐文字 */
@Composable
fun SettingDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(start = 16.dp),
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
    )
}

/**
 * 分档选择。用 M3 的段式按钮（Segmented Button），
 * 比原来「选中填色、未选中描边」的一排按钮更接近官方规范。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SegmentedRow(
    options: List<String>,
    selected: Int,
    modifier: Modifier = Modifier,
    onSelect: (Int) -> Unit
) {
    SingleChoiceSegmentedButtonRow(modifier.fillMaxWidth()) {
        options.forEachIndexed { i, label ->
            SegmentedButton(
                selected = i == selected,
                onClick = { onSelect(i) },
                shape = SegmentedButtonDefaults.itemShape(index = i, count = options.size),
                label = {
                    Text(
                        label,
                        style = MaterialTheme.typography.labelLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            )
        }
    }
}

/** 加减行，中间显示当前值 */
@Composable
fun StepperRow(
    title: String,
    value: String,
    onMinus: () -> Unit,
    onPlus: () -> Unit
) {
    SettingRow(
        title = title,
        trailing = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(
                    onClick = onMinus,
                    contentPadding = ButtonDefaults.TextButtonContentPadding
                ) { Text("−", fontSize = 18.sp) }
                Text(
                    value,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.widthIn(min = 72.dp)
                )
                TextButton(
                    onClick = onPlus,
                    contentPadding = ButtonDefaults.TextButtonContentPadding
                ) { Text("+", fontSize = 18.sp) }
            }
        }
    )
}

/** 只读信息行：左标签右值 */
@Composable
fun InfoRow(label: String, value: String) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(92.dp)
        )
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f)
        )
    }
}

/** 一行「某项权限 状态 [去设置]」，只在没到位时才给按钮 */
@Composable
fun CheckRow(
    label: String,
    ok: Boolean,
    okText: String,
    badText: String,
    action: String,
    onFix: () -> Unit
) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(92.dp)
        )
        Text(
            if (ok) okText else badText,
            style = MaterialTheme.typography.bodyMedium,
            color = if (ok) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
            modifier = Modifier.weight(1f)
        )
        if (!ok) {
            TextButton(onClick = onFix, contentPadding = ButtonDefaults.TextButtonContentPadding) {
                Text(action, style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

/** 可点击的链接行：右侧显示「打开 ↗」 */
@Composable
fun LinkRow(
    label: String,
    value: String,
    url: String
) {
    val ctx = LocalContext.current
    SettingRow(
        title = label,
        subtitle = value,
        onClick = { openUrl(ctx, url) },
        trailing = {
            Text(
                "打开",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )
        }
    )
}

/** 应用图标占位：圆角方块 + 一个字，避免依赖 mipmap 里的自适应图标 */
@Composable
fun AppBadge(size: Int = 56) {
    Surface(
        shape = RoundedCornerShape(size.dp * 0.28f),
        color = MaterialTheme.colorScheme.primaryContainer,
        modifier = Modifier.width(size.dp).height(size.dp)
    ) {
        Column(
            Modifier.fillMaxWidth().background(Color.Transparent),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                "广",
                fontSize = (size * 0.44).sp,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }
}

/** 用系统浏览器打开链接，没有浏览器时静默失败（不弹错误框） */
fun openUrl(ctx: Context, url: String) {
    try {
        ctx.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    } catch (_: ActivityNotFoundException) {
        // 设备上没有可处理 http 的应用，忽略
    }
}

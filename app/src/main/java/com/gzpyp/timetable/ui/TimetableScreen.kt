/*
 * 广职大课表 —— 广州职业技术大学教务系统课表客户端
 * Copyright (C) 2026 Campsisian
 *
 * 本程序是自由软件：你可以依据 GNU 通用公共许可证（GPLv3）的条款
 * 再分发和/或修改它。本程序按“原样”提供，不带任何担保。
 * 完整条款见项目根目录的 LICENSE 文件。
 */

package com.gzpyp.timetable.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gzpyp.timetable.AppViewModel
import com.gzpyp.timetable.core.Course
import com.gzpyp.timetable.core.TimeSlot
import com.gzpyp.timetable.core.TimeUtil
import java.time.LocalDate

private val TIME_COL_W = 32.dp
private val ROW_H = 56.dp

@Composable
fun TimetableScreen(vm: AppViewModel) {
    val dark = LocalAppDark.current
    val week = vm.week
    val monday = TimeUtil.mondayOf(vm.timetable.config.startDate, week)
    val today = LocalDate.now()
    var detail by remember { mutableStateOf<Course?>(null) }

    Column(Modifier.fillMaxSize()) {
        // 周次导航放进顶栏：标题是周次，右侧四个动作（上一周 / 下一周 / 回到本周 / 刷新）
        AppTopBar(
            title = "第 $week 周",
            subtitle = vm.timetable.semester.ifBlank { "未获取学期" },
            actions = {
                TopAction(Icons.Default.KeyboardArrowLeft, "上一周") { vm.shiftWeek(-1) }
                TopAction(Icons.Default.KeyboardArrowRight, "下一周") { vm.shiftWeek(1) }
                TopAction(Icons.Default.DateRange, "回到本周") { vm.jumpToToday() }
                if (vm.syncing) {
                    Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    }
                } else {
                    TopAction(Icons.Default.Refresh, "更新课表") { vm.sync(true) }
                }
            }
        )

        DayHeaderRow(monday, today)

        if (vm.timetable.courses.isEmpty()) {
            EmptyHint(
                title = "还没有课表",
                body = "到「设置」填好教务账号后点立即更新，或先手动导入一份课表 JSON。",
                modifier = Modifier.weight(1f)
            )
        } else {
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                BoxWithConstraints(
                    Modifier
                        .fillMaxWidth()
                        .height(ROW_H * vm.timetable.slots.size)
                ) {
                    val colW: Dp = (maxWidth - TIME_COL_W) / 7
                    // 行分隔线
                    for (i in 0..vm.timetable.slots.size) {
                        HorizontalDivider(
                            modifier = Modifier.offset(y = ROW_H * i),
                            thickness = 1.dp,
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                        )
                    }
                    // 时间列
                    for (i in vm.timetable.slots.indices) {
                        SectionLabel(vm.timetable.slots[i], Modifier.offset(y = ROW_H * i))
                    }
                    // 课程块
                    val courses = (1..7).flatMap { vm.coursesOf(it) }
                    for (c in courses) {
                        val idx = c.day - 1
                        val top = ROW_H * (c.startSection - 1)
                        val h = ROW_H * (c.endSection - c.startSection + 1) - 3.dp
                        CourseBlock(
                            course = c,
                            dark = dark,
                            modifier = Modifier
                                .offset(x = TIME_COL_W + colW * idx + 1.5.dp, y = top + 1.5.dp)
                                .width(colW - 3.dp)
                                .height(h)
                                .clickable { detail = c }
                        )
                    }
                }
            }
        }

        detail?.let { CourseDetailSheet(it) { detail = null } }
    }
}

/** 顶栏里的小图标按钮，40dp 宽以免四个动作把标题挤没 */
@Composable
private fun TopAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit
) {
    IconButton(onClick = onClick, modifier = Modifier.size(40.dp)) {
        Icon(icon, contentDescription = label, modifier = Modifier.size(22.dp))
    }
}

@Composable
private fun DayHeaderRow(monday: LocalDate, today: LocalDate) {
    Row(Modifier.fillMaxWidth().padding(bottom = 6.dp)) {
        Spacer(Modifier.width(TIME_COL_W))
        for (d in 1..7) {
            val date = monday.plusDays((d - 1).toLong())
            val isToday = date == today
            Column(
                Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // 今天用一个圆形底衬标出来，比只改文字颜色更醒目（M3 的 chip 思路）
                Surface(
                    shape = CircleShape,
                    color = if (isToday) MaterialTheme.colorScheme.primary
                    else Color.Transparent,
                    modifier = Modifier.size(22.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            DAY_NAMES[d - 1],
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                            color = if (isToday) MaterialTheme.colorScheme.onPrimary
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Text(
                    "${date.monthValue}/${date.dayOfMonth}",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isToday) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }
        }
    }
}

@Composable
private fun SectionLabel(slot: TimeSlot, modifier: Modifier = Modifier) {
    Column(
        modifier.width(TIME_COL_W).height(ROW_H),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            "${slot.number}",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            slot.start,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
        )
    }
}

@Composable
private fun CourseBlock(course: Course, dark: Boolean, modifier: Modifier = Modifier) {
    val (bg, fg) = courseColors(course.name, dark)
    Surface(color = bg, shape = MaterialTheme.shapes.small, modifier = modifier) {
        Column(
            Modifier.fillMaxSize().padding(horizontal = 4.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(1.dp)
        ) {
            Text(
                course.name,
                style = MaterialTheme.typography.labelMedium,
                lineHeight = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = fg,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
            if (course.position.isNotBlank()) {
                Text(
                    "@${course.position}",
                    style = MaterialTheme.typography.labelSmall,
                    color = fg.copy(alpha = 0.75f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

private val DAY_NAMES = listOf("一", "二", "三", "四", "五", "六", "日")

/** 空状态提示 */
@Composable
fun EmptyHint(title: String, body: String, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        Text(
            body,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** 课程详情弹窗 */
@Composable
private fun CourseDetailSheet(course: Course, onDismiss: () -> Unit) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("知道了") }
        },
        title = { Text(course.name) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                InfoLine("时间", "${DAY_FULL[course.day - 1]} 第 ${course.startSection}-${course.endSection} 节")
                if (course.position.isNotBlank()) InfoLine("地点", course.position)
                if (course.teacher.isNotBlank()) InfoLine("教师", course.teacher)
                InfoLine("周次", formatWeeks(course.weeks))
            }
        }
    )
}

@Composable
private fun InfoLine(label: String, value: String) {
    Row {
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(44.dp)
        )
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

private val DAY_FULL = listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")

/** [1,2,3,5,6,9..12] -> "1-3,5-6,9-12周" */
fun formatWeeks(weeks: List<Int>): String {
    if (weeks.isEmpty()) return "—"
    val sb = StringBuilder()
    var start = weeks[0]
    var prev = weeks[0]
    fun flush() {
        if (sb.isNotEmpty()) sb.append(',')
        sb.append(if (start == prev) "$start" else "$start-$prev")
    }
    for (i in 1 until weeks.size) {
        val w = weeks[i]
        if (w == prev + 1) prev = w
        else {
            flush(); start = w; prev = w
        }
    }
    flush()
    return sb.append("周").toString()
}

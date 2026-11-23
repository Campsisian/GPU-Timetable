/*
 * 广职大课表 —— 广州职业技术大学教务系统课表客户端
 * Copyright (C) 2026 Campsisian
 *
 * 本程序是自由软件：你可以依据 GNU 通用公共许可证（GPLv3）的条款
 * 再分发和/或修改它。本程序按“原样”提供，不带任何担保。
 * 完整条款见项目根目录的 LICENSE 文件。
 */

package com.gzpyp.timetable.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gzpyp.timetable.AppViewModel
import com.gzpyp.timetable.core.Course
import java.time.LocalDate
import java.time.LocalTime

@Composable
fun TodayScreen(vm: AppViewModel) {
    val dark = LocalAppDark.current
    val today = LocalDate.now()
    val list = vm.todayCourses()
    val now = LocalTime.now()

    Column(Modifier.fillMaxSize()) {
        AppTopBar(
            title = "${today.monthValue} 月 ${today.dayOfMonth} 日 · ${WEEK_CN[today.dayOfWeek.value - 1]}",
            subtitle = "第 ${vm.resolveWeek()} 周 · 今天 ${list.size} 节课"
        )

        if (list.isEmpty()) {
            EmptyHint("今天没有课", "好好休息，或者去「课表」看看这周的其他安排。", Modifier.weight(1f))
        } else {
            LazyColumn(
                Modifier.weight(1f),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(list) { c ->
                    TodayCard(c, now, dark, vm)
                }
            }
        }
    }
}

@Composable
private fun TodayCard(course: Course, now: LocalTime, dark: Boolean, vm: AppViewModel) {
    val slot = vm.timetable.slots.firstOrNull { it.number == course.startSection }
    val endSlot = vm.timetable.slots.firstOrNull { it.number == course.endSection }
    val (bg, fg) = courseColors(course.name, dark)

    val state = when {
        slot == null || endSlot == null -> CourseState.Normal
        now.isAfter(LocalTime.parse(endSlot.end)) -> CourseState.Finished
        !now.isBefore(LocalTime.parse(slot.start)) -> CourseState.Ongoing
        else -> CourseState.Upcoming
    }

    // 已结束的课整块压暗：底色和文字一起退到背景里，扫一眼就能看出哪节上完了
    val finished = state == CourseState.Finished
    val cardColor = if (finished) bg.copy(alpha = 0.32f) else bg
    val ink = if (finished) fg.copy(alpha = 0.45f) else fg

    Surface(
        color = cardColor,
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(Modifier.padding(16.dp)) {
            Column(Modifier.width(58.dp)) {
                Text(
                    slot?.start ?: "--:--",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = ink
                )
                Text(
                    endSlot?.end ?: "",
                    style = MaterialTheme.typography.labelSmall,
                    color = ink.copy(alpha = 0.6f)
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "第 ${course.startSection}-${course.endSection} 节",
                    style = MaterialTheme.typography.labelSmall,
                    color = ink.copy(alpha = 0.7f)
                )
            }
            Column(
                Modifier.weight(1f).padding(start = 4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    course.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = ink
                )
                if (course.position.isNotBlank()) {
                    MetaLine(Icons.Default.LocationOn, course.position, ink)
                }
                if (course.teacher.isNotBlank()) {
                    MetaLine(Icons.Default.Person, course.teacher, ink)
                }
            }
            Box(Modifier.align(Alignment.CenterVertically)) {
                StatusChip(state, fg, finished)
            }
        }
    }
}

/** 地点 / 教师这种次要信息：小图标 + 文字，比 emoji 更贴 M3 */
@Composable
private fun MetaLine(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String, ink: androidx.compose.ui.graphics.Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            icon,
            contentDescription = null,
            modifier = Modifier.size(13.dp),
            tint = ink.copy(alpha = 0.75f)
        )
        Spacer(Modifier.width(5.dp))
        Text(
            text,
            style = MaterialTheme.typography.bodySmall,
            color = ink.copy(alpha = 0.85f)
        )
    }
}

/** 状态胶囊：进行中用实心主色，待上课用淡底，已结束几乎融进背景 */
@Composable
private fun StatusChip(state: CourseState, fg: androidx.compose.ui.graphics.Color, finished: Boolean) {
    val (label, container, content) = when (state) {
        CourseState.Ongoing -> Triple(
            "进行中", fg.copy(alpha = 0.18f), fg
        )
        CourseState.Upcoming -> Triple(
            "待上课", fg.copy(alpha = 0.10f), fg.copy(alpha = 0.65f)
        )
        CourseState.Finished -> Triple(
            "已结束", fg.copy(alpha = 0.06f), fg.copy(alpha = 0.45f)
        )
        CourseState.Normal -> Triple("", fg, fg)
    }
    if (label.isEmpty()) return
    Surface(
        color = container,
        shape = MaterialTheme.shapes.small
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = content,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
        )
    }
}

private enum class CourseState { Ongoing, Upcoming, Finished, Normal }

private val WEEK_CN = listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")

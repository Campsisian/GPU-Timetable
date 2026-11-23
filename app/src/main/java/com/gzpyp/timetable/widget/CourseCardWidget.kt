/*
 * 广职大课表 —— 广州职业技术大学教务系统课表客户端
 * Copyright (C) 2026 Campsisian
 *
 * 本程序是自由软件：你可以依据 GNU 通用公共许可证（GPLv3）的条款
 * 再分发和/或修改它。本程序按“原样”提供，不带任何担保。
 * 完整条款见项目根目录的 LICENSE 文件。
 */

package com.gzpyp.timetable.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.View
import android.widget.RemoteViews
import com.gzpyp.timetable.R
import com.gzpyp.timetable.core.Course
import com.gzpyp.timetable.core.TimeUtil
import com.gzpyp.timetable.core.Timetable
import com.gzpyp.timetable.data.AppSettings
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * 小组件的两套内容都由这里拼装。
 *
 * 课表是变长的，部件却可能只有一两节高，所以桌面端一律用 ListView + RemoteViewsService，
 * 装不下的部分靠滑动看（见 [WidgetService]），不按高度砍课。
 * 文字全部是真实 TextView，任何 DPI 下都是系统字体，不会糊。
 */
object CourseCardWidget {

    const val MODE_TODAY = 0
    const val MODE_UPCOMING = 1

    /**
     * 「今日课程」最多往后看几天。
     * 假期里下一节课可能在五六天之后，部件上突然跳出个陌生日期比留空更让人迷惑，
     * 所以超过这个范围就当作「这几天没课」。
     */
    const val MAX_LOOKAHEAD = 3

    /** 列表里的一行：日期小标题，或一节课 */
    sealed class Row {
        data class Day(val text: String) : Row()
        data class Item(val course: Course) : Row()
    }

    /** 挑出来要展示的那一天 */
    data class Plan(
        val date: LocalDate,
        val offset: Int,          // 0 = 今天，1 = 明天
        val week: Int,
        val courses: List<Course>
    )

    /** 找出最近一天还有课的日子：今天剩下的课 → 明天 → 后天 → 大后天 */
    fun plan(tt: Timetable, now: LocalDateTime = LocalDateTime.now()): Plan {
        val today = now.toLocalDate()
        for (offset in 0..MAX_LOOKAHEAD) {
            val date = today.plusDays(offset.toLong())
            val week = TimeUtil.weekOf(tt.config.startDate, date)
            if (week > tt.config.totalWeeks) break
            val dayIdx = TimeUtil.dayIndex(date)
            var list = tt.courses
                .filter { it.day == dayIdx && it.activeIn(week) }
                .sortedBy { it.startSection }
            if (offset == 0) {
                // 今天的课只留还没下课的
                list = list.filter { c ->
                    val end = endTime(tt, c)
                    end == null || end > now.toLocalTime()
                }
            }
            if (list.isNotEmpty()) return Plan(date, offset, week, list)
        }
        // 这几天都没有课：回到「今天」，courses 为空，界面显示空状态
        return Plan(today, 0, TimeUtil.weekOf(tt.config.startDate, today), emptyList())
    }

    fun titleOf(p: Plan): String {
        val wd = WEEK_CN[TimeUtil.dayIndex(p.date) - 1]
        val md = "${p.date.monthValue}月${p.date.dayOfMonth}日"
        return when (p.offset) {
            0 -> "今天 $md $wd"
            1 -> "明天 $md $wd"
            2 -> "后天 $md $wd"
            else -> "${p.offset}天后 $md $wd"
        }
    }

    fun timeRange(tt: Timetable, c: Course): String {
        val s = tt.slots.firstOrNull { it.number == c.startSection }?.start ?: ""
        val e = tt.slots.firstOrNull { it.number == c.endSection }?.end ?: ""
        return when {
            s.isNotEmpty() && e.isNotEmpty() -> "$s-$e"
            s.isNotEmpty() -> s
            else -> ""
        }
    }

    fun sectionText(c: Course): String =
        if (c.startSection == c.endSection) "第${c.startSection}节"
        else "${c.startSection}-${c.endSection}节"

    /** 地点 · 教师，按设置裁掉不要的部分 */
    fun placeLine(c: Course, s: AppSettings): String {
        val parts = ArrayList<String>(2)
        if (s.widgetShowLocation && c.position.isNotBlank()) parts.add(c.position)
        if (s.widgetShowTeacher && c.teacher.isNotBlank()) parts.add(c.teacher)
        return parts.joinToString(" · ")
    }

    // ---------------------------------------------------------------- 列表数据

    /**
     * 把内容摊平成「若干行」交给 RemoteViewsFactory。
     * 今日课程 = 最近一天的课；近期课程 = 从今天起按天分组铺 8 天。
     */
    fun rowsOf(tt: Timetable, mode: Int, now: LocalDateTime = LocalDateTime.now()): List<Row> {
        val out = ArrayList<Row>()
        if (mode == MODE_UPCOMING) {
            val today = now.toLocalDate()
            for (offset in 0..7) {
                val date = today.plusDays(offset.toLong())
                val week = TimeUtil.weekOf(tt.config.startDate, date)
                if (week > tt.config.totalWeeks) break
                val dayIdx = TimeUtil.dayIndex(date)
                var list = tt.courses
                    .filter { it.day == dayIdx && it.activeIn(week) }
                    .sortedBy { it.startSection }
                if (offset == 0) {
                    list = list.filter { c -> endTime(tt, c)?.let { it > now.toLocalTime() } ?: true }
                }
                if (list.isEmpty()) continue
                out.add(Row.Day(groupTitle(date, offset)))
                list.forEach { out.add(Row.Item(it)) }
            }
        } else {
            plan(tt, now).courses.forEach { out.add(Row.Item(it)) }
        }
        return out
    }

    /** 标题行左边的文字 */
    fun headerTitle(tt: Timetable, mode: Int, now: LocalDateTime = LocalDateTime.now()): String =
        if (mode == MODE_UPCOMING) "近期课程" else titleOf(plan(tt, now))

    /** 标题行右边的文字 */
    fun headerMeta(tt: Timetable, mode: Int, now: LocalDateTime = LocalDateTime.now()): String {
        return if (mode == MODE_UPCOMING) {
            "第${TimeUtil.weekOf(tt.config.startDate, now.toLocalDate())}周"
        } else {
            val p = plan(tt, now)
            if (p.courses.isEmpty()) "" else "第${p.week}周 · ${p.courses.size}节"
        }
    }

    // ---------------------------------------------------------------- 部件根

    /**
     * 桌面部件用：ListView + RemoteViewsService，内容可滚动。
     * @param appWidgetId 每个部件实例唯一，用来隔离各自的列表数据
     */
    fun buildForWidget(
        ctx: Context,
        tt: Timetable,
        settings: AppSettings,
        mode: Int,
        appWidgetId: Int,
        hDp: Int,
        now: LocalDateTime = LocalDateTime.now()
    ): RemoteViews {
        val style = WidgetStyle(ctx, settings)
        val rv = RemoteViews(ctx.packageName, R.layout.widget_scroll)
        applyChrome(rv, style)
        // 列表内容要收在圆角边框里面：底部留出圆角半径，滚动时也不能越过（clipToPadding=true）
        val usable = (hDp - 9f - 22f * style.scale).coerceAtLeast(20f)
        rv.setViewPadding(R.id.wd_list, 0, 0, 0, bottomGapPx(ctx, style, usable))

        val p = if (mode == MODE_UPCOMING) null else plan(tt, now)
        rv.setTextViewText(R.id.wd_title, headerTitle(tt, mode, now))
        if (p != null && p.offset > 0) {
            // 今天课上完了、显示的是后面几天：标题换强调色并稍微放大，别让人误以为是今天的安排
            rv.setTextColor(R.id.wd_title, style.accent)
            WidgetStyle.sp(rv, R.id.wd_title, 12.5f * style.scale)
        }

        val rows = rowsOf(tt, mode, now)
        if (rows.isEmpty()) {
            rv.setViewVisibility(R.id.wd_list, View.GONE)
            rv.setViewVisibility(R.id.wd_meta, View.GONE)
            rv.setViewVisibility(R.id.wd_empty, View.VISIBLE)
            rv.setTextViewText(
                R.id.wd_empty,
                if (tt.courses.isEmpty()) "还没有课表\n打开 App 同步一次" else "最近几天都没有课"
            )
            return rv
        }

        rv.setViewVisibility(R.id.wd_list, View.VISIBLE)
        rv.setViewVisibility(R.id.wd_empty, View.GONE)
        rv.setViewVisibility(R.id.wd_meta, View.VISIBLE)
        rv.setTextViewText(R.id.wd_meta, headerMeta(tt, mode, now))

        val svc = Intent(ctx, WidgetService::class.java).apply {
            // data 带上 id 和模式，两个部件实例才不会共用一个列表
            data = Uri.parse("gzpyp://widget/$mode/$appWidgetId")
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            putExtra(WidgetService.EXTRA_MODE, mode)
        }
        rv.setRemoteAdapter(R.id.wd_list, svc)
        return rv
    }

    // ---------------------------------------------------------------- 设置页预览

    /**
     * 设置页预览用：本地 apply 起不了 RemoteViewsService，所以不能用 ListView，
     * 改成拿同一套卡片布局手工铺几行，样式和桌面一致，只是不可滚。
     */
    fun buildPreview(
        ctx: Context,
        tt: Timetable,
        settings: AppSettings,
        mode: Int,
        wDp: Int,
        hDp: Int,
        now: LocalDateTime = LocalDateTime.now()
    ): RemoteViews {
        val style = WidgetStyle(ctx, settings)
        val rv = RemoteViews(ctx.packageName, R.layout.widget_cards)
        applyChrome(rv, style)
        rv.removeAllViews(R.id.wd_cards)
        val d = ctx.resources.displayMetrics.density
        rv.setViewPadding(
            R.id.wd_body,
            (11 * d).toInt(), (9 * d).toInt(), (11 * d).toInt(),
            (minOf(style.rootRadiusDp, 24) * d).toInt()
        )
        rv.setTextViewText(R.id.wd_title, headerTitle(tt, mode, now))
        val p = if (mode == MODE_UPCOMING) null else plan(tt, now)
        if (p != null && p.offset > 0) {
            rv.setTextColor(R.id.wd_title, style.accent)
            WidgetStyle.sp(rv, R.id.wd_title, 12.5f * style.scale)
        }

        val rows = rowsOf(tt, mode, now)
        if (rows.isEmpty()) {
            rv.setViewVisibility(R.id.wd_meta, View.GONE)
            rv.setViewVisibility(R.id.wd_cards, View.GONE)
            rv.setViewVisibility(R.id.wd_empty, View.VISIBLE)
            rv.setTextViewText(
                R.id.wd_empty,
                if (tt.courses.isEmpty()) "还没有课表\n打开 App 同步一次" else "最近几天都没有课"
            )
            return rv
        }

        rv.setViewVisibility(R.id.wd_cards, View.VISIBLE)
        rv.setViewVisibility(R.id.wd_empty, View.GONE)
        rv.setViewVisibility(R.id.wd_meta, View.VISIBLE)
        rv.setTextViewText(R.id.wd_meta, headerMeta(tt, mode, now))

        // 预览高度有限，能放几行放几行（真实部件可滚动，不受这个限制）
        val avail = (hDp - 15f - 22f * style.scale).coerceAtLeast(20f)
        var used = 0f
        for (row in rows) {
            val h = if (row is Row.Day) 24f * style.scale else 67f * style.scale
            if (used + h > avail) break
            when (row) {
                is Row.Day -> rv.addView(R.id.wd_cards, groupRowOf(ctx, style, row))
                is Row.Item -> rv.addView(
                    R.id.wd_cards,
                    cardOf(ctx, tt, settings, style, row.course, now.toLocalTime(), showPlace = true)
                )
            }
            used += h
        }
        return rv
    }

    // ---------------------------------------------------------------- 共用

    /**
     * 列表底部要留多少，卡片才不会压到下方圆角：
     * 尽量等于圆角半径，但部件很矮时优先保证还能看到课，最多吃掉可用高度的 22%。
     */
    private fun bottomGapPx(ctx: Context, style: WidgetStyle, usableDp: Float): Int {
        val gap = minOf(style.rootRadiusDp.toFloat(), usableDp * 0.22f, 24f).coerceAtLeast(6f)
        return (gap * ctx.resources.displayMetrics.density).toInt()
    }

    private fun applyChrome(rv: RemoteViews, style: WidgetStyle) {
        rv.setInt(R.id.wd_bg, "setBackgroundResource", style.rootBg)
        rv.setFloat(R.id.wd_bg, "setAlpha", style.alpha)
        rv.setTextColor(R.id.wd_title, style.titleColor)
        rv.setTextColor(R.id.wd_meta, style.metaColor)
        rv.setTextColor(R.id.wd_empty, style.metaColor)
        WidgetStyle.sp(rv, R.id.wd_title, 11.5f * style.scale)
        WidgetStyle.sp(rv, R.id.wd_meta, 9.5f * style.scale)
        WidgetStyle.sp(rv, R.id.wd_empty, 11f * style.scale)
    }

    /** 日期小标题行 */
    fun groupRowOf(ctx: Context, style: WidgetStyle, row: Row.Day): RemoteViews {
        val g = RemoteViews(ctx.packageName, R.layout.widget_group_title)
        g.setTextViewText(R.id.group_title, row.text)
        g.setTextColor(R.id.group_title, style.metaColor)
        WidgetStyle.sp(g, R.id.group_title, 10.5f * style.scale)
        return g
    }

    /** 一节课的卡片 */
    fun cardOf(
        ctx: Context,
        tt: Timetable,
        settings: AppSettings,
        style: WidgetStyle,
        c: Course,
        nowTime: LocalTime,
        showPlace: Boolean
    ): RemoteViews {
        val item = RemoteViews(ctx.packageName, R.layout.widget_card_item)
        item.setInt(R.id.card_bg, "setBackgroundResource", style.cardBg)
        item.setFloat(R.id.card_bg, "setAlpha", style.alpha)

        item.setTextViewText(R.id.card_name, c.name)
        item.setTextColor(R.id.card_name, style.nameColor)
        WidgetStyle.sp(item, R.id.card_name, 13f * style.scale)

        val range = timeRange(tt, c)
        val ongoing = isOngoing(tt, c, nowTime)
        item.setTextViewText(
            R.id.card_time,
            buildString {
                if (range.isNotEmpty()) append(range).append(" · ")
                append(sectionText(c))
                if (ongoing) append(" · 正在上")
            }
        )
        item.setTextColor(R.id.card_time, style.timeColor)
        WidgetStyle.sp(item, R.id.card_time, 10.5f * style.scale)

        val place = placeLine(c, settings)
        if (!showPlace || place.isEmpty()) {
            item.setViewVisibility(R.id.card_place, View.GONE)
        } else {
            item.setViewVisibility(R.id.card_place, View.VISIBLE)
            item.setTextViewText(R.id.card_place, place)
            item.setTextColor(R.id.card_place, style.metaColor)
            WidgetStyle.sp(item, R.id.card_place, 10.5f * style.scale)
        }
        return item
    }

    fun groupTitle(date: LocalDate, offset: Int): String {
        val wd = WEEK_CN[TimeUtil.dayIndex(date) - 1]
        val md = "${date.monthValue}月${date.dayOfMonth}日"
        return when (offset) {
            0 -> "今天 · $md $wd"
            1 -> "明天 · $md $wd"
            2 -> "后天 · $md $wd"
            else -> "$wd · $md"
        }
    }

    private fun endTime(tt: Timetable, c: Course): LocalTime? =
        tt.slots.firstOrNull { it.number == c.endSection }?.end
            ?.let { runCatching { LocalTime.parse(it) }.getOrNull() }

    private fun isOngoing(tt: Timetable, c: Course, now: LocalTime): Boolean {
        val s = tt.slots.firstOrNull { it.number == c.startSection }?.start
            ?.let { runCatching { LocalTime.parse(it) }.getOrNull() } ?: return false
        val e = endTime(tt, c) ?: return false
        return now >= s && now < e
    }

    val WEEK_CN = listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")
}

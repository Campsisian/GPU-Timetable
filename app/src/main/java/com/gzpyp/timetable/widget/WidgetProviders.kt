/*
 * 广职大课表 —— 广州职业技术大学教务系统课表客户端
 * Copyright (C) 2026 Campsisian
 *
 * 本程序是自由软件：你可以依据 GNU 通用公共许可证（GPLv3）的条款
 * 再分发和/或修改它。本程序按“原样”提供，不带任何担保。
 * 完整条款见项目根目录的 LICENSE 文件。
 */

package com.gzpyp.timetable.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.RemoteViews
import com.gzpyp.timetable.R
import com.gzpyp.timetable.core.TimeUtil
import com.gzpyp.timetable.core.Timetable
import com.gzpyp.timetable.data.Repo
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/**
 * 两个桌面部件共用刷新流程，只是取数据的模式不同。
 * 内容本身交给 [WidgetService] 的 ListView 适配器，所以部件再小也能滚动看全。
 */
abstract class BaseWidgetProvider : AppWidgetProvider() {

    /** 取哪套数据：今日课程 / 近期课程 */
    protected abstract val mode: Int

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        ids.forEach { runCatching { updateOne(context, manager, it) } }
        WidgetTicker.schedule(context)
    }

    override fun onAppWidgetOptionsChanged(
        context: Context, manager: AppWidgetManager, id: Int, newOptions: Bundle?
    ) {
        runCatching { updateOne(context, manager, id) }
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        super.onDeleted(context, appWidgetIds)
        appWidgetIds.forEach { WidgetUpdater.forgetHeader(context, it) }
        // 桌面已经没有任何部件了就把定时刷新也停掉
        WidgetTicker.schedule(context)
    }

    fun updateOne(ctx: Context, manager: AppWidgetManager, id: Int) {
        val opts = manager.getAppWidgetOptions(id)
        val hDp = opts.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 110)
            .coerceAtLeast(50)
        val repo = Repo(ctx)
        val tt = repo.loadTimetable()
        // 标题和内容必须来自同一个时间点，否则会看到「标题写今天、列表是明天」
        val now = LocalDateTime.now()
        val title = CourseCardWidget.headerTitle(tt, mode, now)

        // 万一拼装过程出意外也要交一个合法的 RemoteViews，否则桌面会显示「无法加载小组件」
        val views = runCatching {
            CourseCardWidget.buildForWidget(
                ctx, tt, repo.loadSettings(), mode, id, hDp, now
            )
        }.getOrElse {
            RemoteViews(ctx.packageName, R.layout.widget_scroll).apply {
                setViewVisibility(R.id.wd_list, View.GONE)
                setViewVisibility(R.id.wd_meta, View.GONE)
                setViewVisibility(R.id.wd_empty, View.VISIBLE)
                setTextViewText(R.id.wd_empty, "打开 App 同步一次课表")
            }
        }

        openApp(ctx, id)?.let { pi ->
            // 列表项自己带 fill-in intent，点哪节课都回到 App
            views.setPendingIntentTemplate(R.id.wd_list, pi)
            views.setOnClickPendingIntent(R.id.widget_root, pi)
        }

        manager.updateAppWidget(id, views)
        // 课表或外观改过之后必须叫列表重读一次，否则滚动列表里还是旧数据
        manager.notifyAppWidgetViewDataChanged(id, R.id.wd_list)
        WidgetUpdater.saveHeader(ctx, id, title)
    }

    private fun openApp(ctx: Context, id: Int): PendingIntent? {
        val launch = ctx.packageManager.getLaunchIntentForPackage(ctx.packageName) ?: return null
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        // data 带上部件 id，多个部件各用各的 PendingIntent，不会互相覆盖
        launch.data = Uri.parse("gzpyp://open/$id")
        return PendingIntent.getActivity(
            ctx, id, launch,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}

/** 今日课程：今天（没课则往后几天）的课，一节一张卡片，多了可以滑 */
class TodayWidgetProvider : BaseWidgetProvider() {
    override val mode: Int = CourseCardWidget.MODE_TODAY
}

/**
 * 近期课程：从今天起按天分组铺开，尺寸大就多看几天，小了滑动看。
 *
 * 类名沿用早期的 WeekWidgetProvider：桌面上已经添加过的部件靠这个类名识别，
 * 改名会让它们变成「无法加载」，所以宁可名字不准也不动它。
 */
class WeekWidgetProvider : BaseWidgetProvider() {
    override val mode: Int = CourseCardWidget.MODE_UPCOMING
}

/** 课表或外观设置变化后，主动刷新所有已添加的部件 */
object WidgetUpdater {

    private val PROVIDERS = listOf(
        TodayWidgetProvider::class.java,
        WeekWidgetProvider::class.java
    )

    /** 每个部件上次构建标题行时用的文字，用来发现「标题和列表不是同一时刻」 */
    private const val PREF = "widget_state"

    fun refreshAll(ctx: Context) {
        for (cls in PROVIDERS) {
            val ids = idsOf(ctx, cls)
            if (ids.isEmpty()) continue
            runCatching { ctx.sendBroadcast(updateFor(ctx, cls, ids)) }
        }
        WidgetTicker.schedule(ctx)
    }

    /** 只刷某一个部件（列表适配器发现标题过期时用） */
    fun refreshOne(ctx: Context, id: Int) {
        val manager = AppWidgetManager.getInstance(ctx) ?: return
        for (cls in PROVIDERS) {
            val owner = runCatching { manager.getAppWidgetInfo(id)?.provider?.className }
                .getOrNull()
            if (owner != cls.name) continue
            runCatching { ctx.sendBroadcast(updateFor(ctx, cls, intArrayOf(id))) }
            return
        }
    }

    fun allIds(ctx: Context): List<Int> =
        PROVIDERS.flatMap { idsOf(ctx, it).toList() }

    private fun idsOf(ctx: Context, cls: Class<*>): IntArray {
        val manager = AppWidgetManager.getInstance(ctx) ?: return IntArray(0)
        return runCatching { manager.getAppWidgetIds(ComponentName(ctx, cls)) }
            .getOrDefault(IntArray(0))
    }

    private fun updateFor(ctx: Context, cls: Class<*>, ids: IntArray): Intent =
        Intent(ctx, cls).apply {
            action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
        }

    fun saveHeader(ctx: Context, id: Int, title: String) {
        ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit()
            .putString("hdr_$id", title).apply()
    }

    fun headerOf(ctx: Context, id: Int): String? =
        ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE).getString("hdr_$id", null)

    fun forgetHeader(ctx: Context, id: Int) {
        ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit().remove("hdr_$id").apply()
    }
}

/**
 * 部件自己不会「到点重算」：课间换场、跨天、最后一节课下课这些时刻，
 * 系统那 30 分钟一次的 updatePeriodMillis 太粗，会让人看到「课都上完了还写着今天」。
 * 这里在最近的课程边界（上课/下课）设一个闹钟，到点把部件整体重刷一次。
 */
object WidgetTicker {

    const val ACTION = "com.gzpyp.timetable.action.WIDGET_TICK"
    private const val REQUEST = 4242

    fun schedule(ctx: Context) {
        val am = ctx.getSystemService(AlarmManager::class.java) ?: return
        if (WidgetUpdater.allIds(ctx).isEmpty()) {
            pending(ctx, PendingIntent.FLAG_NO_CREATE)?.let { am.cancel(it); it.cancel() }
            return
        }
        val now = LocalDateTime.now()
        val at = nextBoundary(Repo(ctx).loadTimetable(), now)
            .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val pi = pending(ctx, PendingIntent.FLAG_UPDATE_CURRENT) ?: return
        runCatching {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || am.canScheduleExactAlarms()) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
            } else {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
            }
        }.onFailure {
            runCatching { am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi) }
        }
    }

    private fun pending(ctx: Context, flags: Int): PendingIntent? = runCatching {
        PendingIntent.getBroadcast(
            ctx, REQUEST, Intent(ctx, WidgetTickReceiver::class.java),
            flags or PendingIntent.FLAG_IMMUTABLE
        )
    }.getOrNull()

    /** 下一个需要重刷的时刻：今天最近的上/下课时间、跨天零点，最多一小时 */
    private fun nextBoundary(tt: Timetable, now: LocalDateTime): LocalDateTime {
        val today = now.toLocalDate()
        val marks = ArrayList<LocalDateTime>()
        val week = TimeUtil.weekOf(tt.config.startDate, today)
        if (week <= tt.config.totalWeeks) {
            val dayIdx = TimeUtil.dayIndex(today)
            for (c in tt.courses) {
                if (c.day != dayIdx || !c.activeIn(week)) continue
                tt.slots.firstOrNull { it.number == c.startSection }?.start?.let { s ->
                    runCatching { LocalDateTime.of(today, LocalTime.parse(s)) }
                        .getOrNull()?.let { marks.add(it) }
                }
                tt.slots.firstOrNull { it.number == c.endSection }?.end?.let { e ->
                    runCatching { LocalDateTime.of(today, LocalTime.parse(e)) }
                        .getOrNull()?.let { marks.add(it) }
                }
            }
        }
        marks.add(today.plusDays(1).atStartOfDay())

        val soon = now.plusSeconds(45)
        val next = marks.filter { it.isAfter(soon) }.minOrNull() ?: now.plusHours(1)
        // 兜底：最多睡一小时，课表或系统时间改了也不会一直停在旧状态
        return if (next.isAfter(now.plusHours(1))) now.plusHours(1) else next
    }
}

class WidgetTickReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != WidgetTicker.ACTION) return
        WidgetUpdater.refreshAll(context)
        WidgetTicker.schedule(context)
    }
}

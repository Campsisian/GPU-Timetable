/*
 * 广职大课表 —— 广州职业技术大学教务系统课表客户端
 * Copyright (C) 2026 Campsisian
 *
 * 本程序是自由软件：你可以依据 GNU 通用公共许可证（GPLv3）的条款
 * 再分发和/或修改它。本程序按“原样”提供，不带任何担保。
 * 完整条款见项目根目录的 LICENSE 文件。
 */

package com.gzpyp.timetable.widget

import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import com.gzpyp.timetable.R
import com.gzpyp.timetable.core.Timetable
import com.gzpyp.timetable.data.AppSettings
import com.gzpyp.timetable.data.Repo
import java.time.LocalTime

/**
 * 桌面部件里那个可滚动列表的数据源。
 * 系统（Launcher）会 bind 这个 Service，用 [WidgetFactory] 逐行取数据，
 * 所以课再多也不会被部件高度截断，滑一下就能看完。
 */
class WidgetService : RemoteViewsService() {

    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory =
        WidgetFactory(applicationContext, intent)

    companion object {
        const val EXTRA_MODE = "widget_mode"

        fun intentFor(ctx: Context, mode: Int, appWidgetId: Int): Intent =
            Intent(ctx, WidgetService::class.java).apply {
                putExtra(EXTRA_MODE, mode)
                putExtra(android.appwidget.AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            }
    }
}

/** 把课表摊成行：日期小标题 + 每节课一张卡片 */
class WidgetFactory(
    private val ctx: Context,
    intent: Intent
) : RemoteViewsService.RemoteViewsFactory {

    private val mode = intent.getIntExtra(WidgetService.EXTRA_MODE, CourseCardWidget.MODE_TODAY)
    private val appWidgetId = intent.getIntExtra(
        android.appwidget.AppWidgetManager.EXTRA_APPWIDGET_ID, -1
    )

    private var style: WidgetStyle? = null
    private var timetable: Timetable? = null
    private var settings: AppSettings? = null
    private var rows: List<CourseCardWidget.Row> = emptyList()

    override fun onCreate() = reload()

    override fun onDataSetChanged() = reload()

    override fun onDestroy() {
        rows = emptyList()
        style = null
        timetable = null
        settings = null
    }

    /** 每次列表要重新取数时读一遍本地存储；数据量很小，直接同步读 */
    private fun reload() {
        val repo = Repo(ctx)
        val tt = repo.loadTimetable()
        val s = repo.loadSettings()
        timetable = tt
        settings = s
        style = WidgetStyle(ctx, s)
        rows = CourseCardWidget.rowsOf(tt, mode)
        refreshHeaderIfStale(tt)
    }

    /**
     * 标题行是部件根布局里的，列表却由本工厂单独取数。
     * 启动器重新绑定（或进程被回收后重建）时本工厂拿到的是「现在」的数据，
     * 而标题还是上次构建时写的，于是会出现「标题写今天、列表是明天」。
     * 发现不一致就让部件整体重建一次——重建后标题会存成新值，不会来回抖动。
     */
    private fun refreshHeaderIfStale(tt: Timetable) {
        if (appWidgetId <= 0) return
        val expected = CourseCardWidget.headerTitle(tt, mode)
        val stored = WidgetUpdater.headerOf(ctx, appWidgetId) ?: return
        if (stored == expected) return
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(
            { runCatching { WidgetUpdater.refreshOne(ctx, appWidgetId) } }, 300
        )
    }

    override fun getCount(): Int = rows.size

    override fun getViewAt(position: Int): RemoteViews {
        val st = style
        val tt = timetable
        val s = settings
        val row = rows.getOrNull(position)
        if (st == null || tt == null || s == null || row == null) return blank()

        return when (row) {
            is CourseCardWidget.Row.Day -> CourseCardWidget.groupRowOf(ctx, st, row)
            is CourseCardWidget.Row.Item -> {
                val v = CourseCardWidget.cardOf(
                    ctx, tt, s, st, row.course, LocalTime.now(), showPlace = true
                )
                // 配合 provider 上的 setPendingIntentTemplate，点哪节课都打开 App
                v.setOnClickFillInIntent(R.id.card_root, Intent())
                v
            }
        }
    }

    private fun blank(): RemoteViews =
        RemoteViews(ctx.packageName, R.layout.widget_group_title).apply {
            setTextViewText(R.id.group_title, "")
        }

    override fun getLoadingView(): RemoteViews? = null

    /** 两种行：日期小标题 / 课程卡片 */
    override fun getViewTypeCount(): Int = 2

    override fun getItemId(position: Int): Long = position.toLong()

    override fun hasStableIds(): Boolean = false
}

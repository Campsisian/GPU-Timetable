/*
 * 广职大课表 —— 广州职业技术大学教务系统课表客户端
 * Copyright (C) 2026 Campsisian
 *
 * 本程序是自由软件：你可以依据 GNU 通用公共许可证（GPLv3）的条款
 * 再分发和/或修改它。本程序按“原样”提供，不带任何担保。
 * 完整条款见项目根目录的 LICENSE 文件。
 */

package com.gzpyp.timetable.sync

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.gzpyp.timetable.data.Repo
import com.gzpyp.timetable.notify.Reminders
import com.gzpyp.timetable.widget.WidgetUpdater

/**
 * 重启、改系统时间、升级安装后，系统会清空所有闹钟，这里统一补回来。
 * 顺带把桌面小组件刷一次（跨天的日期要更新）。
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action !in SUPPORTED) return

        val repo = Repo(context)
        val settings = repo.loadSettings()
        val timetable = repo.loadTimetable()

        Reminders.reschedule(context, timetable, settings)
        if (settings.dailySyncEnabled) SyncWorker.scheduleDaily(context, settings.dailySyncHour)
        WidgetUpdater.refreshAll(context)
    }

    private companion object {
        val SUPPORTED = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            // 用户在系统里打开「闹钟和提醒」权限后，把之前因为没权限排成不精确的闹钟重排一遍
            "android.app.action.SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED",
            "android.intent.action.QUICKBOOT_POWERON",
            "com.htc.intent.action.QUICKBOOT_POWERON"
        )
    }
}

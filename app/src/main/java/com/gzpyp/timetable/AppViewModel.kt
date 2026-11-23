/*
 * 广职大课表 —— 广州职业技术大学教务系统课表客户端
 * Copyright (C) 2026 Campsisian
 *
 * 本程序是自由软件：你可以依据 GNU 通用公共许可证（GPLv3）的条款
 * 再分发和/或修改它。本程序按“原样”提供，不带任何担保。
 * 完整条款见项目根目录的 LICENSE 文件。
 */

package com.gzpyp.timetable

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.gzpyp.timetable.core.Course
import com.gzpyp.timetable.core.TimeUtil
import com.gzpyp.timetable.core.Timetable
import com.gzpyp.timetable.data.AppSettings
import com.gzpyp.timetable.data.Repo
import com.gzpyp.timetable.notify.Reminders
import com.gzpyp.timetable.ocr.CaptchaOcr
import com.gzpyp.timetable.sync.SyncService
import com.gzpyp.timetable.sync.SyncWorker
import com.gzpyp.timetable.widget.WidgetUpdater
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class AppViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = Repo(app)

    var timetable: Timetable by mutableStateOf(repo.loadTimetable())
        private set
    var settings: AppSettings by mutableStateOf(repo.loadSettings())
        private set
    var week: Int by mutableStateOf(1)
        private set
    var syncing: Boolean by mutableStateOf(false)
        private set
    var syncMessage: String? by mutableStateOf(null)
        private set

    init {
        week = resolveWeek()
        Reminders.ensureChannel(app)
        if (settings.dailySyncEnabled) SyncWorker.scheduleDaily(app, settings.dailySyncHour)
        if (settings.autoSyncOnLaunch && settings.hasCredentials) sync(manual = false)
    }

    val totalWeeks: Int get() = timetable.config.totalWeeks

    fun resolveWeek(): Int = when {
        settings.weekOverride > 0 -> settings.weekOverride.coerceIn(1, totalWeeks.coerceAtLeast(1))
        timetable.courses.isEmpty() -> 1
        else -> TimeUtil.weekOf(timetable.config.startDate, LocalDate.now())
            .coerceIn(1, totalWeeks.coerceAtLeast(1))
    }

    fun goToWeek(w: Int) {
        week = w.coerceIn(1, totalWeeks.coerceAtLeast(1))
    }

    fun shiftWeek(delta: Int) = goToWeek(week + delta)

    fun jumpToToday() {
        if (settings.weekOverride > 0) {
            updateSettings(settings.copy(weekOverride = 0))
        } else {
            goToWeek(resolveWeek())
        }
    }

    fun coursesOf(day: Int): List<Course> =
        timetable.courses.filter { it.day == day && it.activeIn(week) }
            .sortedBy { it.startSection }

    fun todayCourses(): List<Course> {
        val dayIdx = TimeUtil.dayIndex(LocalDate.now())
        val currentWeek = resolveWeek()
        return timetable.courses
            .filter { it.day == dayIdx && it.activeIn(currentWeek) }
            .sortedBy { it.startSection }
    }

    fun updateSettings(new: AppSettings) {
        val old = settings
        settings = new
        repo.saveSettings(new)
        val app = getApplication<Application>()
        if (old.dailySyncEnabled != new.dailySyncEnabled || old.dailySyncHour != new.dailySyncHour) {
            if (new.dailySyncEnabled) SyncWorker.scheduleDaily(app, new.dailySyncHour)
            else SyncWorker.cancelDaily(app)
        }
        if (old.reminderEnabled != new.reminderEnabled || old.reminderMinutes != new.reminderMinutes) {
            Reminders.reschedule(app, timetable, new)
        }
        if (old.weekOverride != new.weekOverride) {
            week = resolveWeek()
            WidgetUpdater.refreshAll(app)
        }
        if (old.widgetBgAlpha != new.widgetBgAlpha ||
            old.widgetFontScale != new.widgetFontScale ||
            old.widgetCorner != new.widgetCorner ||
            old.widgetTheme != new.widgetTheme ||
            old.widgetShowTeacher != new.widgetShowTeacher ||
            old.widgetShowLocation != new.widgetShowLocation
        ) {
            WidgetUpdater.refreshAll(app)
        }
    }

    fun sync(manual: Boolean = true) {
        if (syncing) return
        val s = settings
        if (!s.hasCredentials) {
            syncMessage = "请先在设置里填学号和密码"
            return
        }
        syncing = true
        syncMessage = if (manual) "正在连接教务系统…" else null

        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                val ocr = runCatching { CaptchaOcr(getApplication<Application>()) }.getOrNull()
                try {
                    SyncService(repo).sync(s, ocr) { step ->
                        if (manual) syncMessage = step
                    }
                } finally {
                    ocr?.close()
                }
            }
            when (result) {
                is SyncService.Result.Ok -> {
                    timetable = result.timetable
                    val now = AppSettings.fromJsonString(settings.toJsonString()).copy(
                        semester = result.semester,
                        lastSyncAt = System.currentTimeMillis(),
                        lastSyncMessage = "更新成功：${result.timetable.courses.size} 节课"
                    )
                    settings = now
                    repo.saveSettings(now)
                    week = resolveWeek()
                    val app = getApplication<Application>()
                    Reminders.reschedule(app, timetable, now)
                    WidgetUpdater.refreshAll(app)
                    syncMessage = "已更新 ${result.timetable.courses.size} 节课"
                }
                is SyncService.Result.Failed -> {
                    val now = settings.copy(lastSyncMessage = result.message)
                    settings = now
                    repo.saveSettings(now)
                    syncMessage = result.message
                }
            }
            syncing = false
        }
    }

    fun clearAll() {
        repo.clearAll()
        timetable = Timetable()
        settings = AppSettings()
        syncMessage = null
        goToWeek(1)
        val app = getApplication<Application>()
        Reminders.cancelAll(app)
        WidgetUpdater.refreshAll(app)
    }

    /** 从剪贴板等外部来源导入课表 JSON */
    fun importTimetable(text: String): Boolean {
        val parsed = runCatching { Timetable.fromJsonString(text) }.getOrNull() ?: return false
        if (parsed.courses.isEmpty()) return false
        repo.saveTimetable(parsed)
        timetable = parsed
        week = resolveWeek()
        val app = getApplication<Application>()
        Reminders.reschedule(app, parsed, settings)
        WidgetUpdater.refreshAll(app)
        return true
    }

    fun formattedLastSync(): String {
        val at = settings.lastSyncAt
        if (at <= 0L) return "从未同步"
        val fmt = DateTimeFormatter.ofPattern("MM-dd HH:mm")
        return java.time.Instant.ofEpochMilli(at)
            .atZone(java.time.ZoneId.systemDefault())
            .format(fmt)
    }
}

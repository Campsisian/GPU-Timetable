/*
 * 广职大课表 —— 广州职业技术大学教务系统课表客户端
 * Copyright (C) 2026 Campsisian
 *
 * 本程序是自由软件：你可以依据 GNU 通用公共许可证（GPLv3）的条款
 * 再分发和/或修改它。本程序按“原样”提供，不带任何担保。
 * 完整条款见项目根目录的 LICENSE 文件。
 */

package com.gzpyp.timetable.sync

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.gzpyp.timetable.data.Repo
import com.gzpyp.timetable.notify.Reminders
import com.gzpyp.timetable.ocr.CaptchaOcr
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

/** 每日定时同步（WorkManager，设备有网时才会跑） */
class SyncWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val repo = Repo(applicationContext)
        val settings = repo.loadSettings()
        if (!settings.hasCredentials) return@withContext Result.success()

        val ocr = runCatching { CaptchaOcr(applicationContext) }.getOrNull()
        try {
            when (val r = SyncService(repo).sync(settings, ocr)) {
                is SyncService.Result.Ok -> {
                    repo.saveSettings(
                        settings.copy(
                            semester = r.semester,
                            lastSyncAt = System.currentTimeMillis(),
                            lastSyncMessage = "定时更新成功，共 ${r.timetable.courses.size} 节课"
                        )
                    )
                    Reminders.reschedule(applicationContext, r.timetable, settings)
                    Result.success()
                }
                is SyncService.Result.Failed -> {
                    repo.saveSettings(settings.copy(lastSyncMessage = "定时更新失败：${r.message}"))
                    if (runAttemptCount < 2) Result.retry() else Result.failure()
                }
            }
        } finally {
            ocr?.close()
        }
    }

    companion object {
        private const val UNIQUE_DAILY = "gzpyp_daily_sync"

        fun scheduleDaily(context: Context, hour: Int) {
            val request = PeriodicWorkRequestBuilder<SyncWorker>(1, TimeUnit.DAYS)
                .setInitialDelay(millisUntil(hour), TimeUnit.MILLISECONDS)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE_DAILY, ExistingPeriodicWorkPolicy.UPDATE, request
            )
        }

        fun cancelDaily(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_DAILY)
        }

        private fun millisUntil(hour: Int): Long {
            val now = java.time.LocalDateTime.now()
            var next = now.withHour(hour).withMinute(0).withSecond(0).withNano(0)
            if (!next.isAfter(now)) next = next.plusDays(1)
            return java.time.Duration.between(now, next).toMillis()
        }
    }
}

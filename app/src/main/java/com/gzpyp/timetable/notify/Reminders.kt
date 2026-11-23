/*
 * 广职大课表 —— 广州职业技术大学教务系统课表客户端
 * Copyright (C) 2026 Campsisian
 *
 * 本程序是自由软件：你可以依据 GNU 通用公共许可证（GPLv3）的条款
 * 再分发和/或修改它。本程序按“原样”提供，不带任何担保。
 * 完整条款见项目根目录的 LICENSE 文件。
 */

package com.gzpyp.timetable.notify

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.gzpyp.timetable.R
import com.gzpyp.timetable.core.Course
import com.gzpyp.timetable.core.TimeUtil
import com.gzpyp.timetable.core.Timetable
import com.gzpyp.timetable.data.AppSettings
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/**
 * 上课提醒：给未来一周的课各挂一个闹钟，到点发一条普通通知。
 *
 * 只用系统通知，不改静音/勿扰状态。闹钟被系统省电策略吃掉也不要紧——
 * 每次打开 App、重启、改时间、同步完都会重排一遍。
 */
object Reminders {

    const val CHANNEL_ID = "class_reminder"

    /** 「发送测试通知」用的固定 id，不会和真实课的闹钟撞上 */
    private const val TEST_ID = 99_999

    /** 提前排几天的课。排一周基本能保证长期不开 App 也不会漏提醒 */
    private const val WINDOW_DAYS = 7

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val mgr = context.getSystemService(NotificationManager::class.java) ?: return
        if (mgr.getNotificationChannel(CHANNEL_ID) != null) return
        val ch = NotificationChannel(CHANNEL_ID, "上课提醒", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "上课前提醒你下一节课的教室和时间"
            enableVibration(true)
            setShowBadge(true)
        }
        mgr.createNotificationChannel(ch)
    }

    fun canNotify(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                ContextCompat.checkSelfPermission(
                    context, "android.permission.POST_NOTIFICATIONS"
                ) == PackageManager.PERMISSION_GRANTED

    /**
     * 能不能排「准时」闹钟。
     * Android 12 起精确闹钟要单独授权，Android 14 起新装应用默认拒绝，
     * 被拒之后闹钟会退化成不精确的，系统在省电模式下可能拖到十几分钟后才响——
     * 这就是「上课提醒不准时」最常见的原因。
     */
    fun canExact(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        val am = context.getSystemService(AlarmManager::class.java) ?: return false
        return runCatching { am.canScheduleExactAlarms() }.getOrDefault(false)
    }

    /** 有没有被系统放进电池优化白名单（没放进去时后台的闹钟更容易被压） */
    fun canRunInBackground(context: Context): Boolean {
        val pm = context.getSystemService(android.os.PowerManager::class.java) ?: return true
        return runCatching { pm.isIgnoringBatteryOptimizations(context.packageName) }
            .getOrDefault(true)
    }

    /**
     * 测试通知：立刻弹一条，再隔十几秒走一遍真实的闹钟链路弹第二条。
     * 第一条只验证通知权限，第二条才验证「到点能不能叫醒」。
     */
    fun testNotification(context: Context, delaySeconds: Long = 12) {
        ensureChannel(context)
        notify(
            context, TEST_ID,
            "测试通知 · 提醒正常",
            "能看到这条，说明通知权限没问题；${delaySeconds} 秒后还有一条",
            "能看到这条，说明通知权限没问题。\n" +
                    "${delaySeconds} 秒后还会来一条，那条是走闹钟链路发出来的——" +
                    "它也到了，说明到点提醒能正常工作。"
        )

        val am = context.getSystemService(AlarmManager::class.java) ?: return
        val at = System.currentTimeMillis() + delaySeconds * 1000
        val pi = PendingIntent.getBroadcast(
            context, TEST_ID,
            Intent(context, ReminderReceiver::class.java).apply {
                putExtra("id", TEST_ID)
                putExtra("test", true)
                putExtra("title", "测试通知 · 闹钟链路正常")
                putExtra("text", "这条是从闹钟里发出来的")
                putExtra("big", "这条是从闹钟里发出来的，说明到点提醒能正常工作。")
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        schedule(am, at, pi)
    }

    fun notify(context: Context, id: Int, title: String, text: String, big: String = text) {
        if (!canNotify(context)) return
        ensureChannel(context)

        val tap = Intent(context, com.gzpyp.timetable.MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        val pi = PendingIntent.getActivity(
            context, id, tap,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val n = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notify)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(big))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setColor(0xFF186B4B.toInt())
            .setWhen(System.currentTimeMillis())
            .setShowWhen(true)
            .setContentIntent(pi)
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(notificationId(id), n) }
    }

    /** 通知 id 在 32 位以内，闹钟 id 可能上千，做个折叠映射 */
    private fun notificationId(alarmId: Int): Int = 10_000 + (alarmId % 10_000)

    /**
     * 重新安排未来一周里还没开始的课，先清空旧的避免重复提醒。
     */
    fun reschedule(context: Context, timetable: Timetable, settings: AppSettings) {
        cancelAll(context)
        if (!settings.reminderEnabled || timetable.courses.isEmpty()) return
        val am = context.getSystemService(AlarmManager::class.java) ?: return

        val now = LocalDateTime.now()
        for (offset in 0..WINDOW_DAYS) {
            val date = LocalDate.now().plusDays(offset.toLong())
            val week = TimeUtil.weekOf(timetable.config.startDate, date)
            if (week > timetable.config.totalWeeks) break
            val dayIdx = TimeUtil.dayIndex(date)
            for (c in timetable.courses) {
                if (c.day != dayIdx || !c.activeIn(week)) continue
                val slot = timetable.slots.firstOrNull { it.number == c.startSection } ?: continue
                val start = runCatching { LocalTime.parse(slot.start) }.getOrNull() ?: continue
                val trigger = LocalDateTime.of(date, start)
                    .minusMinutes(settings.reminderMinutes.toLong())
                if (trigger.isBefore(now)) continue
                val triggerMs = trigger.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

                val id = date.dayOfYear * 100 + c.startSection
                val intent = Intent(context, ReminderReceiver::class.java).apply {
                    putExtra("id", id)
                    putExtra("title", titleOf(settings.reminderMinutes))
                    putExtra("text", compactText(c, slot.start))
                    putExtra("big", detailText(c, slot.start, slot.end, timetable))
                }
                val pi = PendingIntent.getBroadcast(
                    context, id, intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                schedule(am, triggerMs, pi)
            }
        }
    }

    private fun schedule(am: AlarmManager, triggerMs: Long, pi: PendingIntent) {
        // 有权限就用精确闹钟，没有就退化成不精确的（最多晚几分钟）
        val exact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || am.canScheduleExactAlarms()
        runCatching {
            if (exact) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerMs, pi)
            else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerMs, pi)
        }.onFailure {
            runCatching { am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerMs, pi) }
        }
    }

    private fun titleOf(minutes: Int): String =
        if (minutes <= 0) "该上课了" else "还有 $minutes 分钟上课"

    private fun compactText(c: Course, start: String): String = buildString {
        append(c.name)
        if (c.position.isNotBlank()) append(" · ").append(c.position)
        append(" · ").append(start)
    }

    private fun detailText(c: Course, start: String, end: String, tt: Timetable): String = buildString {
        append(c.name).append('\n')
        append(start)
        if (end.isNotBlank()) append('-').append(end)
        append("  ").append(sectionLabel(c))
        if (c.position.isNotBlank()) append('\n').append("地点：").append(c.position)
        if (c.teacher.isNotBlank()) append('\n').append("教师：").append(c.teacher)
    }

    private fun sectionLabel(c: Course): String =
        if (c.startSection == c.endSection) "第${c.startSection}节"
        else "${c.startSection}-${c.endSection}节"

    fun cancelAll(context: Context) {
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        val today = LocalDate.now()
        for (offset in 0..WINDOW_DAYS) {
            val date = today.plusDays(offset.toLong())
            for (section in 1..13) {
                val id = date.dayOfYear * 100 + section
                val pi = PendingIntent.getBroadcast(
                    context, id, Intent(context, ReminderReceiver::class.java),
                    PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
                )
                if (pi != null) {
                    am.cancel(pi)
                    pi.cancel()
                }
            }
        }
    }
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val title = intent.getStringExtra("title") ?: return
        val text = intent.getStringExtra("text") ?: ""
        val big = intent.getStringExtra("big") ?: text
        Reminders.notify(context, intent.getIntExtra("id", 1), title, text, big)

        // 只排了未来一周的闹钟。每次提醒响完顺手把窗口往后推一周，
        // 这样即使一个月不开 App，接下来的课也一样有提醒（测试通知不算）。
        if (intent.getBooleanExtra("test", false)) return
        runCatching {
            val repo = com.gzpyp.timetable.data.Repo(context)
            Reminders.reschedule(context, repo.loadTimetable(), repo.loadSettings())
        }
    }
}

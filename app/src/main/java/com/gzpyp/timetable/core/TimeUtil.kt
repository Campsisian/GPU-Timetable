/*
 * 广职大课表 —— 广州职业技术大学教务系统课表客户端
 * Copyright (C) 2026 Campsisian
 *
 * 本程序是自由软件：你可以依据 GNU 通用公共许可证（GPLv3）的条款
 * 再分发和/或修改它。本程序按“原样”提供，不带任何担保。
 * 完整条款见项目根目录的 LICENSE 文件。
 */

package com.gzpyp.timetable.core

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

/** 学期起止与周次的换算（纯函数，方便单测） */
object TimeUtil {

    private val FMT: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE

    fun parseDate(s: String): LocalDate? =
        runCatching { LocalDate.parse(s, FMT) }.getOrNull()

    /** 某天属于第几周；第一周周一为 startDate */
    fun weekOf(startDate: String, date: LocalDate): Int {
        val start = parseDate(startDate) ?: return 1
        val days = ChronoUnit.DAYS.between(start, date)
        val w = Math.floorDiv(days, 7L) + 1
        return w.toInt().coerceAtLeast(1)
    }

    /** 第 week 周对应的周一 */
    fun mondayOf(startDate: String, week: Int): LocalDate {
        val start = parseDate(startDate) ?: LocalDate.now()
        return start.plusWeeks((week - 1).toLong())
    }

    /** 该日期是周几：1=周一 … 7=周日 */
    fun dayIndex(date: LocalDate): Int = ((date.dayOfWeek.value + 6) % 7) + 1

    fun today(): LocalDate = LocalDate.now()
}

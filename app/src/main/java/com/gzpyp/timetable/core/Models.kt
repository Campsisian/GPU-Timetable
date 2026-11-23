/*
 * 广职大课表 —— 广州职业技术大学教务系统课表客户端
 * Copyright (C) 2026 Campsisian
 *
 * 本程序是自由软件：你可以依据 GNU 通用公共许可证（GPLv3）的条款
 * 再分发和/或修改它。本程序按“原样”提供，不带任何担保。
 * 完整条款见项目根目录的 LICENSE 文件。
 */

package com.gzpyp.timetable.core

/** 一节课（一门课在某个星期几、某些周次、某些节次） */
data class Course(
    val name: String,
    val teacher: String = "",
    val position: String = "",
    val day: Int,                 // 1 = 周一 ... 7 = 周日
    val startSection: Int,
    val endSection: Int,
    val weeks: List<Int> = emptyList()
) {
    fun activeIn(week: Int) = weeks.contains(week)

    fun toJson(): Map<String, Any?> = mapOf(
        "name" to name, "teacher" to teacher, "position" to position,
        "day" to day, "startSection" to startSection, "endSection" to endSection,
        "weeks" to weeks
    )

    companion object {
        fun fromJson(m: Map<*, *>): Course = Course(
            name = m["name"] as? String ?: "",
            teacher = m["teacher"] as? String ?: "",
            position = m["position"] as? String ?: "",
            day = (m["day"] as? Number)?.toInt() ?: 1,
            startSection = (m["startSection"] as? Number)?.toInt() ?: 1,
            endSection = (m["endSection"] as? Number)?.toInt() ?: 1,
            weeks = (m["weeks"] as? List<*>)?.mapNotNull { (it as? Number)?.toInt() } ?: emptyList()
        )
    }
}

data class TimeSlot(val number: Int, val start: String, val end: String) {
    fun toJson() = mapOf<String, Any?>("n" to number, "s" to start, "e" to end)
    companion object {
        fun fromJson(m: Map<*, *>) = TimeSlot(
            (m["n"] as? Number)?.toInt() ?: 1,
            m["s"] as? String ?: "",
            m["e"] as? String ?: ""
        )
    }
}

/** 全校作息（广州职业技术大学，1-13 节） */
object SchoolTimeSlots {
    val DEFAULT: List<TimeSlot> = listOf(
        TimeSlot(1, "08:30", "09:15"), TimeSlot(2, "09:15", "10:00"),
        TimeSlot(3, "10:25", "11:10"), TimeSlot(4, "11:10", "11:55"),
        TimeSlot(5, "12:40", "13:25"), TimeSlot(6, "13:25", "14:10"),
        TimeSlot(7, "14:30", "15:15"), TimeSlot(8, "15:15", "16:00"),
        TimeSlot(9, "16:25", "17:10"), TimeSlot(10, "17:10", "17:55"),
        TimeSlot(11, "19:00", "19:45"), TimeSlot(12, "19:45", "20:30"),
        TimeSlot(13, "20:35", "21:20")
    )
}

/** 学期配置 */
data class SemesterConfig(
    val startDate: String = "2026-08-31",   // 第一周周一 yyyy-MM-dd
    val totalWeeks: Int = 20
) {
    fun toJson() = mapOf<String, Any?>("start" to startDate, "totalWeeks" to totalWeeks)
    companion object {
        fun fromJson(m: Map<*, *>) = SemesterConfig(
            startDate = m["start"] as? String ?: "2026-08-31",
            totalWeeks = (m["totalWeeks"] as? Number)?.toInt() ?: 20
        )
    }
}

/** 教务返回的学期列表项 */
data class SemesterOption(val value: String, val selected: Boolean)

/** 一整份课表数据 */
data class Timetable(
    val courses: List<Course> = emptyList(),
    val slots: List<TimeSlot> = SchoolTimeSlots.DEFAULT,
    val config: SemesterConfig = SemesterConfig(),
    val semester: String = "",
    val updatedAt: Long = 0L,
    val source: String = "local"
) {
    fun toJsonString(): String = Json.write(
        mapOf(
            "courses" to courses.map { it.toJson() },
            "slots" to slots.map { it.toJson() },
            "config" to config.toJson(),
            "semester" to semester,
            "updatedAt" to updatedAt,
            "source" to source
        )
    )

    companion object {
        fun fromJsonString(text: String): Timetable {
            val m = Json.parse(text) as? Map<*, *> ?: return Timetable()
            return Timetable(
                courses = (m["courses"] as? List<*>).orEmpty().mapNotNull {
                    (it as? Map<*, *>)?.let { c -> Course.fromJson(c) }
                },
                slots = (m["slots"] as? List<*>).orEmpty().mapNotNull {
                    (it as? Map<*, *>)?.let { c -> TimeSlot.fromJson(c) }
                }.ifEmpty { SchoolTimeSlots.DEFAULT },
                config = (m["config"] as? Map<*, *>)?.let { SemesterConfig.fromJson(it) } ?: SemesterConfig(),
                semester = m["semester"] as? String ?: "",
                updatedAt = (m["updatedAt"] as? Number)?.toLong() ?: 0L,
                source = m["source"] as? String ?: "local"
            )
        }
    }
}

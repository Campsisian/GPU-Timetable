package com.gzpyp.timetable.widget

import com.gzpyp.timetable.core.Course
import com.gzpyp.timetable.core.SemesterConfig
import com.gzpyp.timetable.core.Timetable
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

/**
 * 部件「今天课上完之后显示什么」的规则。
 * 2026-11-23 是周一，学期第一周周一为 2026-08-31。
 */
class PlanTest {

    private val weeks = (1..20).toList()

    private fun timetable(vararg courses: Course) = Timetable(
        courses = courses.toList(),
        config = SemesterConfig(startDate = "2026-08-31", totalWeeks = 20)
    )

    private fun course(day: Int, from: Int, to: Int) =
        Course(name = "课$day", day = day, startSection = from, endSection = to, weeks = weeks)

    @Test
    fun `今天课上完了就看明天`() {
        val tt = timetable(course(1, 1, 2), course(2, 3, 4))
        val p = CourseCardWidget.plan(tt, LocalDateTime.of(2026, 11, 23, 12, 0))

        assertEquals(1, p.offset)
        assertEquals(1, p.courses.size)  // 周二只有一门
        assertTrue(CourseCardWidget.titleOf(p).startsWith("明天"))
    }

    @Test
    fun `隔很多天的不显示`() {
        // 只有周一有课，现在是周三：下一节课在 5 天后，超出查看范围
        val tt = timetable(course(1, 1, 2))
        val p = CourseCardWidget.plan(tt, LocalDateTime.of(2026, 11, 25, 10, 0))

        assertEquals(0, p.offset)
        assertTrue(p.courses.isEmpty())
    }

    @Test
    fun `后天仍在范围内`() {
        val tt = timetable(course(1, 1, 2), course(3, 1, 2))
        val p = CourseCardWidget.plan(tt, LocalDateTime.of(2026, 11, 23, 20, 0))

        assertEquals(2, p.offset)
        assertTrue(CourseCardWidget.titleOf(p).startsWith("后天"))
    }

    @Test
    fun `正在上的课不会被过滤掉`() {
        val tt = timetable(course(1, 1, 2))
        val p = CourseCardWidget.plan(tt, LocalDateTime.of(2026, 11, 23, 9, 0))

        assertEquals(0, p.offset)
        assertEquals(1, p.courses.size)
        assertTrue(CourseCardWidget.titleOf(p).startsWith("今天"))
    }
}

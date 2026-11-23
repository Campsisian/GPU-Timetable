package com.gzpyp.timetable.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class JsxsdParserTest {

    private fun resource(name: String): String =
        javaClass.classLoader!!.getResourceAsStream(name)!!.bufferedReader(Charsets.UTF_8).readText()

    @Test
    fun `课表页能解析出全部课程`() {
        val html = resource("kb.html")
        val courses = JsxsdParser.parseSchedule(html)
        assertNotNull("应当识别出 kbtable", courses)
        // 用浏览器脚本同一份页面解析的结果是 39 条 / 14 门课
        assertEquals(39, courses!!.size)
        assertEquals(14, courses.map { it.name }.distinct().size)
        assertTrue(courses.all { it.weeks.isNotEmpty() })
        assertTrue(courses.all { it.day in 1..7 })
        assertTrue(courses.all { it.startSection <= it.endSection })
    }

    @Test
    fun `取的是完整版单元格所以有教师`() {
        val courses = JsxsdParser.parseSchedule(resource("kb.html"))!!
        val withTeacher = courses.count { it.teacher.isNotBlank() }
        assertTrue("含教师的比例应接近全部，实际 $withTeacher/${courses.size}", withTeacher >= courses.size - 2)
    }

    @Test
    fun `跨大节的课程按方括号里的真实节次`() {
        val courses = JsxsdParser.parseSchedule(resource("kb.html"))!!
        val c = courses.first { it.name == "电路分析基础" && it.teacher == "胡嘉沛" }
        assertEquals(2, c.day)
        assertEquals(1, c.startSection)
        assertEquals(2, c.endSection)
        assertEquals(listOf(9), c.weeks)
        assertTrue(c.position.contains("15209"))
    }

    @Test
    fun `周次解析支持区间与单双周`() {
        assertEquals(listOf(2, 3, 4, 6, 9, 10, 11, 12), JsxsdParser.parseWeeks("2-4,6,9-12"))
        assertEquals(listOf(1, 3, 5), JsxsdParser.parseWeeks("1-6(单)"))
        assertEquals(listOf(2, 4, 6), JsxsdParser.parseWeeks("2-6(双)"))
        assertEquals(listOf(3), JsxsdParser.parseWeeks("3(周)"))
    }

    @Test
    fun `节次范围解析`() {
        assertEquals(1 to 4, JsxsdParser.parseSectionRange("01-02-03-04"))
        assertEquals(3 to 3, JsxsdParser.parseSectionRange("03"))
    }

    @Test
    fun `学期下拉框只保留正规学期`() {
        val list = JsxsdParser.parseSemesterOptions(resource("kb.html"))
        assertTrue("至少一个学期", list.isNotEmpty())
        assertTrue(list.all { Regex("^\\d{4}-\\d{4}-[12]$").matches(it.value) })
    }

    @Test
    fun `首页能反推学期起始日`() {
        val info = JsxsdParser.parseSemesterInfo(resource("main.html"))
        assertNotNull("应能从首页取到周次信息", info)
        assertTrue(info!!.totalWeeks in 1..30)
        assertTrue(Regex("^\\d{4}-\\d{2}-\\d{2}$").matches(info.startDate))
    }
}

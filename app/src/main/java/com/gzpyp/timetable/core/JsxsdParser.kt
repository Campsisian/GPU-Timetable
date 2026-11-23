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

/**
 * 金智 jsxsd 课表页解析。
 * 移植自可用的浏览器适配脚本，逻辑保持一致：
 *  - 课程名/教师/教室/周次都取 div.kbcontent（完整版，含教师）；
 *    注意 div.kbcontent1 是精简版，class 前缀相同，不能误匹配。
 *  - 一格多门课用 5 个以上短横线分隔。
 *  - 周次 "2-4,6,9-20(周)"，节次在方括号里 "[01-02节]"（可能跨大节）。
 */
object JsxsdParser {

    /** 解析整张课表；返回 null 表示页面里没有课表（通常是未登录） */
    fun parseSchedule(html: String): List<Course>? {
        val table = Html.findElementByAttr(html, "table", "id", "kbtable") ?: return null
        val rows = Html.findElements(table, "tr")
        val merged = LinkedHashMap<String, Course>()

        for (row in rows) {
            val th = Html.findElements(row, "th").firstOrNull() ?: continue
            val thText = Html.text(th).replace(" ", "").replace("\u00a0", "")
            val m = Regex("^第(\\d+)-(\\d+)节$").find(thText) ?: continue
            val rowStart = m.groupValues[1].toInt()
            val rowEnd = m.groupValues[2].toInt()

            val tds = Html.findElements(row, "td")
            for ((di, td) in tds.withIndex()) {
                if (di >= 7) break
                val day = di + 1
                for (block in Html.findDivsByClass(td, "kbcontent")) {
                    for (c in parseCell(block, rowStart, rowEnd, day)) {
                        val key = listOf(c.name, c.teacher, c.position, c.day, c.startSection, c.endSection)
                            .joinToString("|")
                        val old = merged[key]
                        if (old == null) merged[key] = c
                        else merged[key] = old.copy(weeks = (old.weeks + c.weeks).distinct().sorted())
                    }
                }
            }
        }
        return merged.values.toList()
    }

    private fun parseCell(cellHtml: String, rowStart: Int, rowEnd: Int, day: Int): List<Course> {
        val out = ArrayList<Course>()
        val segs = Regex("-{5,}").split(cellHtml)
        for (raw in segs) {
            val seg = raw.replace(Regex("^\\s*(<br\\s*/?>)+", RegexOption.IGNORE_CASE), "")
            if (seg.isBlank()) continue

            val brIdx = Regex("<br\\s*/?>", RegexOption.IGNORE_CASE).find(seg)?.range?.first ?: -1
            val nameHtml = if (brIdx >= 0) seg.substring(0, brIdx) else seg
            val name = Html.text(nameHtml)
                .replace(Regex("\\s*[PO]$"), "")
                .replace("\u00a0", " ")
                .trim()
            if (name.isEmpty()) continue

            val weeksRaw = attrText(seg, "周次(节次)") ?: continue
            if (weeksRaw.isBlank()) continue

            val secMatch = Regex("\\[([\\d\\-\\s]+)节\\]").find(weeksRaw)
            val sec = secMatch?.let { parseSectionRange(it.groupValues[1]) }
            val weeksStr = weeksRaw.replace(Regex("\\[[^\\]]*\\]"), "").replace("(周)", "")
            val weeks = parseWeeks(weeksStr)
            if (weeks.isEmpty()) continue

            out.add(
                Course(
                    name = name,
                    teacher = (attrText(seg, "老师") ?: "").trim(),
                    position = (attrText(seg, "教室") ?: "").trim(),
                    day = day,
                    startSection = sec?.first ?: rowStart,
                    endSection = sec?.second ?: rowEnd,
                    weeks = weeks
                )
            )
        }
        return out
    }

    private fun attrText(seg: String, title: String): String? {
        val m = Regex(
            "<font[^>]*title\\s*=\\s*['\"]" + Regex.escape(title) + "['\"][^>]*>(.*?)</font>",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
        ).find(seg) ?: return null
        return Html.text(m.groupValues[1])
    }

    /** "2-4,6,9-20" -> [2,3,4,6,9..20]；支持全角逗号、单双周 */
    fun parseWeeks(input: String): List<Int> {
        var s = input.replace("，", ",")
        val onlyOdd = s.contains("单")
        val onlyEven = s.contains("双")
        s = s.replace(Regex("[^\\d,\\-]"), "")
        val set = sortedSetOf<Int>()
        for (part in s.split(",")) {
            val p = part.trim()
            if (p.isEmpty()) continue
            val r = Regex("^(\\d+)\\s*-\\s*(\\d+)$").find(p)
            if (r != null) {
                val a = r.groupValues[1].toInt()
                val b = r.groupValues[2].toInt()
                for (i in a..b) set.add(i)
            } else if (Regex("^\\d+$").matches(p)) {
                set.add(p.toInt())
            }
        }
        return set.filter {
            !(onlyOdd && it % 2 == 0) && !(onlyEven && it % 2 == 1)
        }.sorted()
    }

    /** "01-02-03-04" -> (1,4) */
    fun parseSectionRange(s: String): Pair<Int, Int>? {
        val nums = Regex("\\d+").findAll(s).map { it.value.toInt() }.toList()
        if (nums.isEmpty()) return null
        return nums.min() to nums.max()
    }

    /** 学期下拉框 */
    fun parseSemesterOptions(html: String): List<SemesterOption> {
        val sel = Html.findElementByAttr(html, "select", "id", "xnxq01id") ?: return emptyList()
        return Html.findElements(sel, "option").mapNotNull { opt ->
            val open = Html.openTag(opt) ?: return@mapNotNull null
            val v = Html.attr(open, "value") ?: return@mapNotNull null
            if (!Regex("^\\d{4}-\\d{4}-[12]$").matches(v)) return@mapNotNull null
            SemesterOption(v, open.contains("selected"))
        }
    }

    /** 从首页取「第N周/共M周」和服务器日期，反推学期第一周周一 */
    fun parseSemesterInfo(html: String, today: LocalDate = LocalDate.now()): SemesterInfo? {
        var raw = ""
        Html.findElementByAttr(html, "li", "id", "li_showWeek")?.let { raw = Html.text(it) }
        var m = Regex("第(\\d+)周/(\\d+)周").find(raw.replace(" ", ""))
        if (m == null) {
            m = Regex("第(\\d+)周/(\\d+)周").find(Html.text(html).replace(" ", ""))
        }
        if (m == null) return null
        val week = m.groupValues[1].toInt()
        val total = m.groupValues[2].toInt()

        var base = today
        val rq = Html.findElementByAttr(html, "input", "id", "rq")
        val rqVal = rq?.let { Html.attr(Html.openTag(it) ?: "", "value") }
        if (rqVal != null && Regex("^\\d{4}-\\d{2}-\\d{2}$").matches(rqVal)) {
            runCatching { base = LocalDate.parse(rqVal, DateTimeFormatter.ISO_LOCAL_DATE) }
        }

        val monday = base.minusDays(((base.dayOfWeek.value + 6) % 7).toLong())
        val start = monday.minusWeeks((week - 1).toLong())
        return SemesterInfo(start.toString(), total, week)
    }

    data class SemesterInfo(val startDate: String, val totalWeeks: Int, val currentWeek: Int)
}

/** 简单 HTML 扫描工具（不追求通用，只覆盖教务页面用到的结构） */
internal object Html {

    fun openTag(s: String, from: Int = 0): String? {
        val lt = s.indexOf('<', from)
        if (lt < 0) return null
        val gt = s.indexOf('>', lt)
        if (gt < 0) return null
        return s.substring(lt, gt + 1)
    }

    fun attr(openTag: String, name: String): String? {
        val m = Regex("(?i)\\b" + Regex.escape(name) + "\\s*=\\s*(['\"])(.*?)\\1").find(openTag)
        return m?.groupValues?.get(2)
            ?: Regex("(?i)\\b" + Regex.escape(name) + "\\s*=\\s*([^\\s>]+)").find(openTag)?.groupValues?.get(1)
    }

    /** 取标签名，如 "<td width=..>" -> "td" */
    fun tagName(openTag: String): String {
        val m = Regex("^<\\s*/?\\s*([a-zA-Z0-9]+)").find(openTag.trim()) ?: return ""
        return m.groupValues[1].lowercase()
    }

    /** 找第一个满足属性条件的元素（含完整内部内容），返回元素外 HTML */
    fun findElementByAttr(html: String, tag: String, attrName: String, attrValue: String): String? {
        var i = 0
        val re = Regex("<" + tag + "(?=[\\s>])", RegexOption.IGNORE_CASE)
        while (true) {
            val m = re.find(html, i) ?: return null
            val open = openTag(html, m.range.first) ?: return null
            val v = attr(open, attrName)
            if (v != null && v == attrValue) {
                val end = closeIndexOf(html, m.range.first, tag) ?: return null
                return html.substring(m.range.first, end)
            }
            i = m.range.first + 1
        }
    }

    /** 返回元素结束（含 </tag>）之后的下标 */
    fun closeIndexOf(html: String, start: Int, tag: String): Int? {
        val reOpen = Regex("<" + tag + "(?=[\\s>])", RegexOption.IGNORE_CASE)
        val reClose = Regex("</" + tag + "\\s*>", RegexOption.IGNORE_CASE)
        var depth = 0
        var i = start
        while (true) {
            val o = reOpen.find(html, i)
            val c = reClose.find(html, i)
            if (c == null) return null
            if (o != null && o.range.first < c.range.first) {
                depth++
                i = o.range.last + 1
            } else {
                depth--
                if (depth == 0) return c.range.last + 1
                i = c.range.last + 1
            }
        }
    }

    fun findElements(html: String, tag: String): List<String> {
        val out = ArrayList<String>()
        var i = 0
        val re = Regex("<" + tag + "(?=[\\s>])", RegexOption.IGNORE_CASE)
        while (true) {
            val m = re.find(html, i) ?: break
            val end = closeIndexOf(html, m.range.first, tag) ?: break
            out.add(html.substring(m.range.first, end))
            i = end
        }
        return out
    }

    /** 找 class 含指定 token 的 div（token 精确匹配，避免 kbcontent 命中 kbcontent1） */
    fun findDivsByClass(html: String, token: String): List<String> {
        val out = ArrayList<String>()
        var i = 0
        val re = Regex("<div(?=[\\s>])", RegexOption.IGNORE_CASE)
        while (true) {
            val m = re.find(html, i) ?: break
            val open = openTag(html, m.range.first) ?: break
            val cls = attr(open, "class") ?: ""
            val tokens = cls.split(Regex("\\s+")).filter { it.isNotEmpty() }
            if (tokens.contains(token)) {
                val end = closeIndexOf(html, m.range.first, "div") ?: break
                out.add(html.substring(m.range.first, end))
                i = end
            } else {
                i = m.range.first + 1
            }
        }
        return out
    }

    /** 去掉标签、解实体、压空白 */
    fun text(html: String): String {
        var s = html.replace(Regex("(?is)<(script|style)[^>]*>.*?</\\1>"), " ")
        s = s.replace(Regex("<br\\s*/?>", RegexOption.IGNORE_CASE), " ")
        s = s.replace(Regex("<[^>]+>"), " ")
        // 教务页面里常见不带分号的 &nbsp，必须容忍，否则课名后面会粘一个 "&nbsp"
        s = s.replace(Regex("&nbsp;?", RegexOption.IGNORE_CASE), " ")
        s = s.replace(Regex("&amp;?", RegexOption.IGNORE_CASE), "&")
        s = s.replace(Regex("&lt;?", RegexOption.IGNORE_CASE), "<")
        s = s.replace(Regex("&gt;?", RegexOption.IGNORE_CASE), ">")
        s = s.replace(Regex("&quot;?", RegexOption.IGNORE_CASE), "\"")
        s = s.replace(Regex("&#39;?", RegexOption.IGNORE_CASE), "'")
        s = s.replace(Regex("&#(\\d+);")) { it.groupValues[1].toIntOrNull()?.toChar()?.toString() ?: "" }
        return s.replace(Regex("\\s+"), " ").trim()
    }
}

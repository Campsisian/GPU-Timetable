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
import com.gzpyp.timetable.core.JsxsdClient
import com.gzpyp.timetable.core.JsxsdParser
import com.gzpyp.timetable.core.SchoolTimeSlots
import com.gzpyp.timetable.core.SemesterConfig
import com.gzpyp.timetable.core.Timetable
import com.gzpyp.timetable.data.AppSettings
import com.gzpyp.timetable.data.Repo
import com.gzpyp.timetable.ocr.CaptchaOcr

/**
 * 教务同步全流程：复用会话 → 必要时自动登录（含验证码识别）→ 抓课表 → 解析 → 落盘。
 * 阻塞式，调用方需放在 IO 线程。
 */
class SyncService(private val repo: Repo) {

    sealed interface Result {
        data class Ok(val timetable: Timetable, val semester: String) : Result
        /** needCaptcha = 需要用户手动看一眼（验证码/凭据问题） */
        data class Failed(val message: String, val needCaptcha: Boolean = false) : Result
    }

    private class Abort(message: String, val needCaptcha: Boolean = false) : Exception(message)

    fun sync(
        settings: AppSettings,
        ocr: CaptchaOcr?,
        onStep: (String) -> Unit = {}
    ): Result = try {
        val client = JsxsdClient()
        client.importCookies(repo.loadCookies())

        var reuse = false
        if (client.hasSession) {
            onStep("检查登录状态")
            reuse = client.isLoggedIn()
        }
        if (!reuse) {
            onStep("登录教务系统")
            login(client, settings, ocr, onStep)
        }

        onStep("抓取课表")
        var html = client.getHtml(client.scheduleUrl(settings.semester))
        var courses = html?.let { JsxsdParser.parseSchedule(it) }
        if (courses == null) {
            // 会话可能在服务端过期了，重新登录后重试一次
            onStep("会话已失效，重新登录")
            login(client, settings, ocr, onStep)
            html = client.getHtml(client.scheduleUrl(settings.semester))
            courses = html?.let { JsxsdParser.parseSchedule(it) }
        }
        if (courses == null || html == null) {
            throw Abort("课表页解析失败：可能学期参数不对或教务界面改版")
        }

        val options = JsxsdParser.parseSemesterOptions(html)
        val semester = options.firstOrNull { it.selected }?.value
            ?: settings.semester.ifBlank { options.firstOrNull()?.value ?: "" }

        onStep("校对待周次")
        val config = runCatching {
            client.getHtml(client.mainPageUrl())?.let { JsxsdParser.parseSemesterInfo(it) }
        }.getOrNull()?.let { SemesterConfig(it.startDate, it.totalWeeks) }
            ?: SemesterConfig()

        val timetable = Timetable(
            courses = courses,
            slots = SchoolTimeSlots.DEFAULT,
            config = config,
            semester = semester,
            updatedAt = System.currentTimeMillis(),
            source = "教务系统"
        )

        repo.saveTimetable(timetable)
        repo.saveCookies(client.exportCookies())
        onStep("完成")
        Result.Ok(timetable, semester)
    } catch (e: Abort) {
        Result.Failed(e.message ?: "同步失败", e.needCaptcha)
    } catch (e: Exception) {
        Result.Failed(e.message ?: e::class.java.simpleName)
    }

    private fun login(
        client: JsxsdClient,
        settings: AppSettings,
        ocr: CaptchaOcr?,
        onStep: (String) -> Unit
    ) {
        if (!settings.hasCredentials) throw Abort("还没填学号和密码", true)
        if (ocr == null) throw Abort("验证码识别器未能加载", true)

        var lastError = "登录未成功"
        for (attempt in 1..MAX_LOGIN_ATTEMPTS) {
            val img = if (attempt == 1) client.startSessionAndCaptcha() else client.fetchCaptcha()
            if (img == null || img.isEmpty()) {
                throw Abort("取不到验证码图片，检查网络或教务是否在维护")
            }
            val code = ocr.recognize(img)
            if (code == null || code.length != 4) {
                onStep("验证码没认出来，换一张")
                continue
            }
            when (val r = client.postLogin(settings.account, settings.password, code)) {
                is JsxsdClient.LoginAttempt.Ok -> {
                    repo.saveCookies(client.exportCookies())
                    return
                }
                is JsxsdClient.LoginAttempt.CaptchaError -> {
                    lastError = "验证码未通过"
                    onStep("验证码未通过，换一张重试")
                }
                is JsxsdClient.LoginAttempt.BadCredentials ->
                    throw Abort("学号或密码错误", true)
                is JsxsdClient.LoginAttempt.NetworkError ->
                    throw Abort("网络错误：${r.msg}")
                is JsxsdClient.LoginAttempt.Unknown -> {
                    lastError = r.raw
                    onStep("登录未成功，重试")
                }
            }
        }
        throw Abort("$lastError（已试 $MAX_LOGIN_ATTEMPTS 次）", true)
    }

    companion object {
        private const val MAX_LOGIN_ATTEMPTS = 3
        fun create(context: Context) = SyncService(Repo(context))
    }
}

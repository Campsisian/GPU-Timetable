/*
 * 广职大课表 —— 广州职业技术大学教务系统课表客户端
 * Copyright (C) 2026 Campsisian
 *
 * 本程序是自由软件：你可以依据 GNU 通用公共许可证（GPLv3）的条款
 * 再分发和/或修改它。本程序按“原样”提供，不带任何担保。
 * 完整条款见项目根目录的 LICENSE 文件。
 */

package com.gzpyp.timetable.core

import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Base64

/**
 * 金智 jsxsd 教务客户端。
 * 登录表单：POST /jsxsd/xk/LoginToXk
 *   userAccount / userPassword / RANDOMCODE / encoded
 *   encoded = base64(账号) + "%%%" + base64(密码)   ← 教务前端 conwork.js 的 encodeInp
 * 会话：登录成功后靠 JSESSIONID 维持，实测空闲 8 小时仍有效。
 */
class JsxsdClient(
    private val baseUrl: String = DEFAULT_BASE,
    private val timeoutMs: Int = 20000
) {

    companion object {
        const val DEFAULT_BASE = "https://jiaowu.gzpyp.edu.cn"
        const val UA = "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) " +
                "Chrome/124.0.0.0 Mobile Safari/537.36"
        fun encodeInp(s: String): String =
            Base64.getEncoder().encodeToString(s.toByteArray(Charsets.UTF_8))
    }

    /** cookie 表（domain 无关，单站即可） */
    private val cookies = LinkedHashMap<String, String>()

    var lastError: String? = null
        private set

    fun importCookies(pairs: Map<String, String>) {
        cookies.clear(); cookies.putAll(pairs)
    }

    fun exportCookies(): Map<String, String> = LinkedHashMap(cookies)

    val hasSession: Boolean get() = cookies.containsKey("JSESSIONID")

    private fun cookieHeader(): String =
        cookies.entries.joinToString("; ") { "${it.key}=${it.value}" }

    private fun absorbCookies(conn: HttpURLConnection) {
        val hs = conn.headerFields["Set-Cookie"] ?: conn.headerFields["set-cookie"] ?: return
        val bag = LinkedHashMap<String, String>()
        for (h in hs) {
            for (part in h.split(",")) {
                val kv = part.split(";")[0].trim()
                val eq = kv.indexOf('=')
                if (eq > 0) {
                    val k = kv.substring(0, eq).trim()
                    val v = kv.substring(eq + 1).trim()
                    if (k.isNotEmpty() && k.length < 40 && !v.contains("=")) bag[k] = v
                }
            }
        }
        cookies.putAll(bag)
    }

    private fun open(path: String, method: String = "GET"): HttpURLConnection {
        val url = if (path.startsWith("http")) path else baseUrl + path
        val c = URL(url).openConnection() as HttpURLConnection
        c.requestMethod = method
        c.connectTimeout = timeoutMs
        c.readTimeout = timeoutMs
        c.instanceFollowRedirects = false
        c.setRequestProperty("User-Agent", UA)
        c.setRequestProperty("Accept-Language", "zh-CN,zh;q=0.9")
        c.setRequestProperty(
            "Accept",
            "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8"
        )
        if (cookies.isNotEmpty()) c.setRequestProperty("Cookie", cookieHeader())
        return c
    }

    private fun readAll(c: HttpURLConnection): String {
        val code = c.responseCode
        val stream = if (code in 200..299) c.inputStream else (c.errorStream ?: return "")
        return stream.use { ByteArrayOutputStream().also { bo -> it.copyTo(bo) }.toString("UTF-8") }
    }

    fun getBytes(path: String): ByteArray? {
        val c = open(path)
        return try {
            val code = c.responseCode
            absorbCookies(c)
            if (code !in 200..299) {
                lastError = "HTTP $code"
                return null
            }
            c.inputStream.use { it.readBytes() }
        } catch (e: Exception) {
            lastError = e.message ?: e::class.java.simpleName
            null
        } finally {
            c.disconnect()
        }
    }

    fun getHtml(path: String): String? {
        val c = open(path)
        return try {
            absorbCookies(c)
            val body = readAll(c)
            if (body.isBlank()) {
                lastError = "HTTP ${c.responseCode}"
                null
            } else body
        } catch (e: Exception) {
            lastError = e.message ?: e::class.java.simpleName
            null
        } finally {
            c.disconnect()
        }
    }

    /** 建立会话并取一张验证码（必须先有 session，验证码才和 session 绑定） */
    fun startSessionAndCaptcha(): ByteArray? {
        getHtml("/jsxsd/")
        return getBytes("/jsxsd/verifycode.servlet")
    }

    fun fetchCaptcha(): ByteArray? = getBytes("/jsxsd/verifycode.servlet")

    /** 提交登录；返回 302 Location 或页面正文 */
    fun postLogin(account: String, password: String, captcha: String): LoginAttempt {
        val c = open("/jsxsd/xk/LoginToXk", "POST")
        val form = listOf(
            "userAccount" to account,
            "userPassword" to password,
            "RANDOMCODE" to captcha,
            "encoded" to (encodeInp(account) + "%%%" + encodeInp(password))
        ).joinToString("&") { (k, v) -> "$k=" + URLEncoder.encode(v, "UTF-8") }

        return try {
            c.doOutput = true
            c.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
            c.setRequestProperty("Origin", baseUrl)
            c.setRequestProperty("Referer", "$baseUrl/jsxsd/")
            c.outputStream.use { it.write(form.toByteArray(Charsets.UTF_8)) }

            val code = c.responseCode
            absorbCookies(c)
            val location = c.getHeaderField("Location")
            val body = readAll(c)
            val signal = (location ?: "") + " " + body

            when {
                // 成功：302 跳到学生选课/主框架页
                location != null && (location.contains("xsrkxz") || location.contains("xsMain")) ->
                    LoginAttempt.Ok
                // 明确的服务端提示优先判定（注意：登录页里也有「请输入验证码」的占位文字，不能当判据）
                signal.contains("验证码错误") || signal.contains("验证码不正确") ||
                        signal.contains("验证码有误") || signal.contains("验证码已失效") ->
                    LoginAttempt.CaptchaError("验证码未通过")
                signal.contains("用户名或密码") || signal.contains("密码错误") ||
                        signal.contains("用户不存在") || signal.contains("账号不存在") ->
                    LoginAttempt.BadCredentials
                // 又回到登录页：多半是验证码不对
                body.contains("LoginToXk") || body.contains("id=\"RANDOMCODE\"") ->
                    LoginAttempt.CaptchaError("回到登录页")
                else -> LoginAttempt.Unknown("HTTP $code" + (location?.let { " -> $it" } ?: ""))
            }
        } catch (e: Exception) {
            LoginAttempt.NetworkError(e.message ?: e::class.java.simpleName)
        } finally {
            c.disconnect()
        }
    }

    sealed class LoginAttempt {
        data object Ok : LoginAttempt()
        data class CaptchaError(val raw: String) : LoginAttempt()
        data object BadCredentials : LoginAttempt()
        data class Unknown(val raw: String) : LoginAttempt()
        data class NetworkError(val msg: String) : LoginAttempt()
    }

    fun scheduleUrl(semester: String?): String {
        val base = "/jsxsd/xskb/xskb_list.do"
        return if (semester.isNullOrBlank()) base else "$base?xnxq01id=" + URLEncoder.encode(semester, "UTF-8")
    }

    fun mainPageUrl(): String = "/jsxsd/framework/xsMain_new.jsp?t1=1"

    /** 判断当前会话是否还有效（课表页是否还能拿到 kbtable） */
    fun isLoggedIn(): Boolean {
        val html = getHtml(scheduleUrl(null)) ?: return false
        return html.contains("kbtable")
    }
}

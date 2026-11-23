/*
 * 广职大课表 —— 广州职业技术大学教务系统课表客户端
 * Copyright (C) 2026 Campsisian
 *
 * 本程序是自由软件：你可以依据 GNU 通用公共许可证（GPLv3）的条款
 * 再分发和/或修改它。本程序按“原样”提供，不带任何担保。
 * 完整条款见项目根目录的 LICENSE 文件。
 */

package com.gzpyp.timetable.data

import android.content.Context
import com.gzpyp.timetable.core.Json
import com.gzpyp.timetable.core.Timetable

/** 本地存储：课表、设置、教务会话 Cookie 各存一份 JSON 字符串。 */
class Repo(context: Context) {

    private val sp = context.applicationContext
        .getSharedPreferences("gzpyp_timetable", Context.MODE_PRIVATE)

    fun loadTimetable(): Timetable =
        Timetable.fromJsonString(sp.getString(KEY_TIMETABLE, null) ?: "")

    fun saveTimetable(t: Timetable) {
        sp.edit().putString(KEY_TIMETABLE, t.toJsonString()).apply()
    }

    fun loadSettings(): AppSettings =
        AppSettings.fromJsonString(sp.getString(KEY_SETTINGS, null) ?: "")

    fun saveSettings(s: AppSettings) {
        sp.edit().putString(KEY_SETTINGS, s.toJsonString()).apply()
    }

    fun loadCookies(): Map<String, String> {
        val raw = sp.getString(KEY_COOKIES, null) ?: return emptyMap()
        val m = Json.parse(raw) as? Map<*, *> ?: return emptyMap()
        return m.entries.mapNotNull { (k, v) ->
            val key = k as? String ?: return@mapNotNull null
            val value = v as? String ?: return@mapNotNull null
            key to value
        }.toMap()
    }

    fun saveCookies(cookies: Map<String, String>) {
        sp.edit().putString(KEY_COOKIES, Json.write(cookies)).apply()
    }

    fun clearAll() {
        sp.edit().clear().apply()
    }

    private companion object {
        const val KEY_TIMETABLE = "timetable_json"
        const val KEY_SETTINGS = "settings_json"
        const val KEY_COOKIES = "cookies_json"
    }
}

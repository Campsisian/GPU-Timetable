/*
 * 广职大课表 —— 广州职业技术大学教务系统课表客户端
 * Copyright (C) 2026 Campsisian
 *
 * 本程序是自由软件：你可以依据 GNU 通用公共许可证（GPLv3）的条款
 * 再分发和/或修改它。本程序按“原样”提供，不带任何担保。
 * 完整条款见项目根目录的 LICENSE 文件。
 */

package com.gzpyp.timetable.data

import com.gzpyp.timetable.core.Json

/** 用户设置。全部落在 SharedPreferences 里，导出的 JSON 便于备份。 */
data class AppSettings(
    val account: String = "",
    val password: String = "",
    val semester: String = "",          // 形如 2026-2027-1
    val autoSyncOnLaunch: Boolean = true,
    val dailySyncEnabled: Boolean = false,
    val dailySyncHour: Int = 7,
    val reminderEnabled: Boolean = true,
    val reminderMinutes: Int = 10,
    val weekOverride: Int = 0,          // 0 = 按学期起始日自动推算
    /** 打开应用默认停在哪个页面：0 = 课表，1 = 今日 */
    val defaultTab: Int = 0,
    /** 主题模式：0 跟随系统 / 1 浅色 / 2 深色 */
    val themeMode: Int = 0,
    /** Android 12+ 跟随壁纸取色（Monet） */
    val dynamicColor: Boolean = false,

    // ---- 桌面小组件外观 ----
    /** 背景不透明度 0-100，越小越透 */
    val widgetBgAlpha: Int = 70,
    /** 字号百分比：85 / 100 / 115 / 130 */
    val widgetFontScale: Int = 100,
    /** 圆角档位：0 小 1 中 2 大 3 特大 */
    val widgetCorner: Int = 1,
    /** 配色：0 跟随系统 1 深色 2 浅色 */
    val widgetTheme: Int = 0,
    val widgetShowTeacher: Boolean = true,
    val widgetShowLocation: Boolean = true,

    val lastSyncAt: Long = 0L,
    val lastSyncMessage: String = ""
) {
    val hasCredentials: Boolean get() = account.isNotBlank() && password.isNotBlank()

    fun toJsonString(): String = Json.write(
        mapOf(
            "account" to account,
            "password" to password,
            "semester" to semester,
            "autoSyncOnLaunch" to autoSyncOnLaunch,
            "dailySyncEnabled" to dailySyncEnabled,
            "dailySyncHour" to dailySyncHour,
            "reminderEnabled" to reminderEnabled,
            "reminderMinutes" to reminderMinutes,
            "weekOverride" to weekOverride,
            "defaultTab" to defaultTab,
            "themeMode" to themeMode,
            "dynamicColor" to dynamicColor,
            "widgetBgAlpha" to widgetBgAlpha,
            "widgetFontScale" to widgetFontScale,
            "widgetCorner" to widgetCorner,
            "widgetTheme" to widgetTheme,
            "widgetShowTeacher" to widgetShowTeacher,
            "widgetShowLocation" to widgetShowLocation,
            "lastSyncAt" to lastSyncAt,
            "lastSyncMessage" to lastSyncMessage
        )
    )

    companion object {
        fun fromJsonString(text: String): AppSettings {
            val m = Json.parse(text) as? Map<*, *> ?: return AppSettings()
            return AppSettings(
                account = m["account"] as? String ?: "",
                password = m["password"] as? String ?: "",
                semester = m["semester"] as? String ?: "",
                autoSyncOnLaunch = m["autoSyncOnLaunch"] as? Boolean ?: true,
                dailySyncEnabled = m["dailySyncEnabled"] as? Boolean ?: false,
                dailySyncHour = (m["dailySyncHour"] as? Number)?.toInt() ?: 7,
                reminderEnabled = m["reminderEnabled"] as? Boolean ?: true,
                reminderMinutes = (m["reminderMinutes"] as? Number)?.toInt() ?: 10,
                weekOverride = (m["weekOverride"] as? Number)?.toInt() ?: 0,
                defaultTab = (m["defaultTab"] as? Number)?.toInt() ?: 0,
                themeMode = (m["themeMode"] as? Number)?.toInt() ?: 0,
                dynamicColor = m["dynamicColor"] as? Boolean ?: false,
                widgetBgAlpha = (m["widgetBgAlpha"] as? Number)?.toInt() ?: 70,
                widgetFontScale = (m["widgetFontScale"] as? Number)?.toInt() ?: 100,
                widgetCorner = (m["widgetCorner"] as? Number)?.toInt() ?: 1,
                widgetTheme = (m["widgetTheme"] as? Number)?.toInt() ?: 0,
                widgetShowTeacher = m["widgetShowTeacher"] as? Boolean ?: true,
                widgetShowLocation = m["widgetShowLocation"] as? Boolean ?: true,
                lastSyncAt = (m["lastSyncAt"] as? Number)?.toLong() ?: 0L,
                lastSyncMessage = m["lastSyncMessage"] as? String ?: ""
            )
        }
    }
}

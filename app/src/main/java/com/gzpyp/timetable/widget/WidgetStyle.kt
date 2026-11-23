/*
 * 广职大课表 —— 广州职业技术大学教务系统课表客户端
 * Copyright (C) 2026 Campsisian
 *
 * 本程序是自由软件：你可以依据 GNU 通用公共许可证（GPLv3）的条款
 * 再分发和/或修改它。本程序按“原样”提供，不带任何担保。
 * 完整条款见项目根目录的 LICENSE 文件。
 */

package com.gzpyp.timetable.widget

import android.content.Context
import android.content.res.Configuration
import android.util.TypedValue
import com.gzpyp.timetable.R
import com.gzpyp.timetable.data.AppSettings

/**
 * 把设置里的几个数字换算成具体的资源、颜色和字号。
 * 小组件和设置页预览都走这里，保证「看到什么就是什么」。
 */
class WidgetStyle(ctx: Context, s: AppSettings) {

    /** 深色卡片（默认）还是浅色卡片 */
    val dark: Boolean = when (s.widgetTheme) {
        1 -> true
        2 -> false
        else -> (ctx.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                Configuration.UI_MODE_NIGHT_YES
    }

    /** 背景不透明度 0.2~1.0 */
    val alpha: Float = (s.widgetBgAlpha.coerceIn(20, 100)) / 100f

    /** 字号倍率 */
    val scale: Float = when {
        s.widgetFontScale <= 85 -> 0.85f
        s.widgetFontScale >= 130 -> 1.3f
        s.widgetFontScale >= 115 -> 1.15f
        else -> 1f
    }

    val corner: Int = s.widgetCorner.coerceIn(0, 3)

    /** 容器圆角半径（dp）：列表内容要在这里面收住，才不会糊到圆角外面 */
    val rootRadiusDp: Int = ROOT_RADIUS[corner]

    val rootBg: Int = if (dark) ROOT_DARK[corner] else ROOT_LIGHT[corner]
    val cardBg: Int = if (dark) CARD_DARK[corner] else CARD_LIGHT[corner]

    val titleColor: Int = if (dark) 0xFFC6CBD2.toInt() else 0xFF4A4F56.toInt()
    /** 强调色：标题显示的不是「今天」时用它，一眼能看出这是往后挪了 */
    val accent: Int = if (dark) 0xFF8FD5B2.toInt() else 0xFF186B4B.toInt()
    val nameColor: Int = 0xFFFFFFFF.toInt().let { if (dark) it else 0xFF17181A.toInt() }
    val metaColor: Int = if (dark) 0xFF9EA3AA.toInt() else 0xFF6A6F76.toInt()
    val timeColor: Int = if (dark) 0xFFC6CBD2.toInt() else 0xFF4A4F56.toInt()

    /** 卡片的横向内边距，字号大时同步放大 */
    fun padH(dp: Float): Int = (dp * scale).toInt()

    companion object {
        /** 与 wg_root_r0..r3 的 corners 一致 */
        val ROOT_RADIUS = intArrayOf(14, 20, 26, 32)

        private val ROOT_DARK = intArrayOf(
            R.drawable.wg_root_r0_dark, R.drawable.wg_root_r1_dark,
            R.drawable.wg_root_r2_dark, R.drawable.wg_root_r3_dark
        )
        private val ROOT_LIGHT = intArrayOf(
            R.drawable.wg_root_r0_light, R.drawable.wg_root_r1_light,
            R.drawable.wg_root_r2_light, R.drawable.wg_root_r3_light
        )
        private val CARD_DARK = intArrayOf(
            R.drawable.wg_card_r0_dark, R.drawable.wg_card_r1_dark,
            R.drawable.wg_card_r2_dark, R.drawable.wg_card_r3_dark
        )
        private val CARD_LIGHT = intArrayOf(
            R.drawable.wg_card_r0_light, R.drawable.wg_card_r1_light,
            R.drawable.wg_card_r2_light, R.drawable.wg_card_r3_light
        )

        fun sp(views: android.widget.RemoteViews, id: Int, sizeSp: Float) {
            views.setTextViewTextSize(id, TypedValue.COMPLEX_UNIT_SP, sizeSp)
        }
    }
}

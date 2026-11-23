/*
 * 广职大课表 —— 广州职业技术大学教务系统课表客户端
 * Copyright (C) 2026 Campsisian
 *
 * 本程序是自由软件：你可以依据 GNU 通用公共许可证（GPLv3）的条款
 * 再分发和/或修改它。本程序按“原样”提供，不带任何担保。
 * 完整条款见项目根目录的 LICENSE 文件。
 */

package com.gzpyp.timetable.core

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/**
 * 纯 Kotlin 图像处理，复刻 ddddocr 的预处理：
 *   RGB -> 灰度(ITU-R 601-2) -> 等比缩放到高 64（LANCZOS3）-> /255
 * 不用 android.graphics 是为了能在 JVM 单元测试里跑，验证与 Python 端一致。
 */
object ImageOps {

    /** ARGB 像素转灰度 0..255 */
    fun toGray(pixels: IntArray): FloatArray {
        val out = FloatArray(pixels.size)
        for (i in pixels.indices) {
            val p = pixels[i]
            val r = (p shr 16) and 0xFF
            val g = (p shr 8) and 0xFF
            val b = p and 0xFF
            out[i] = (r * 299 + g * 587 + b * 114) / 1000f
        }
        return out
    }

    private fun lanczos(x: Float): Float {
        val a = abs(x)
        if (a < 1e-6f) return 1f
        if (a >= 3f) return 0f
        val px = PI.toFloat() * x
        return (3f * sin(px) * sin(px / 3f)) / (px * px)
    }

    /** 单轴权重表：outLen 个输出像素，每个对应若干源像素和归一化权重 */
    private class Axis(outLen: Int, srcLen: Int) {
        val start = IntArray(outLen)
        val count = IntArray(outLen)
        val weights = ArrayList<FloatArray>(outLen)

        init {
            val scale = srcLen.toFloat() / outLen
            // 缩小图像时按比例扩大滤波器支撑，避免混叠（与 PIL 一致）
            val support = max(3f, 3f * scale)
            for (i in 0 until outLen) {
                val center = (i + 0.5f) * scale - 0.5f
                val left = kotlin.math.ceil(center - support).toInt()
                val right = kotlin.math.floor(center + support).toInt()
                val s = left
                val n = right - left + 1
                val w = FloatArray(n)
                var sum = 0f
                for (k in 0 until n) {
                    val src = (s + k).coerceIn(0, srcLen - 1)
                    val d = (s + k - center) / if (scale > 1f) scale else 1f
                    w[k] = lanczos(d)
                    if (src < 0 || src >= srcLen) w[k] = 0f
                    sum += w[k]
                }
                if (sum != 0f) for (k in 0 until n) w[k] /= sum
                start[i] = s
                count[i] = n
                weights.add(w)
            }
        }
    }

    /** LANCZOS3 分离式缩放，输入输出均为灰度 float 数组 */
    fun resizeLanczos(src: FloatArray, sw: Int, sh: Int, dw: Int, dh: Int): FloatArray {
        if (sw == dw && sh == dh) return src.copyOf()
        val ax = Axis(dw, sw)
        // 水平
        val tmp = FloatArray(dw * sh)
        for (y in 0 until sh) {
            val rowOff = y * sw
            for (x in 0 until dw) {
                var acc = 0f
                val st = ax.start[x]
                val w = ax.weights[x]
                for (k in 0 until ax.count[x]) {
                    val sx = (st + k).coerceIn(0, sw - 1)
                    acc += src[rowOff + sx] * w[k]
                }
                tmp[y * dw + x] = acc
            }
        }
        val ay = Axis(dh, sh)
        val out = FloatArray(dw * dh)
        for (y in 0 until dh) {
            val st = ay.start[y]
            val w = ay.weights[y]
            for (x in 0 until dw) {
                var acc = 0f
                for (k in 0 until ay.count[y]) {
                    val sy = (st + k).coerceIn(0, sh - 1)
                    acc += tmp[sy * dw + x] * w[k]
                }
                out[y * dw + x] = acc
            }
        }
        return out
    }

    /**
     * 生成 ddddocr 需要的输入张量：等比缩放到高 64，返回 (tensor, width)
     * tensor 为 CHW 布局并已 /255
     */
    fun toTensor(pixels: IntArray, w: Int, h: Int, targetHeight: Int = 64): Pair<FloatArray, Int> {
        val gray = toGray(pixels)
        val tw = max(1, (w * (targetHeight.toFloat() / h)).toInt())
        val resized = resizeLanczos(gray, w, h, tw, targetHeight)
        for (i in resized.indices) resized[i] = (resized[i] / 255f).coerceIn(0f, 1f)
        return resized to tw
    }
}

/** CTC 贪心解码：逐时间步取最大，去重、去 blank；只允许指定字符集 */
object CtcDecoder {

    /** 直接返回字符结果 */
    fun decodeText(
        logits: FloatArray, steps: Int, classes: Int,
        allowedIndices: IntArray, indexToChar: (Int) -> Char
    ): String {
        val sb = StringBuilder()
        var prev = -1
        for (t in 0 until steps) {
            val base = t * classes
            var best = 0
            var bestV = logits[base]
            for (i in allowedIndices) {
                val v = logits[base + i]
                if (v > bestV) { bestV = v; best = i }
            }
            if (best != 0 && best != prev) sb.append(indexToChar(best))
            prev = best
        }
        return sb.toString()
    }
}

/*
 * 广职大课表 —— 广州职业技术大学教务系统课表客户端
 * Copyright (C) 2026 Campsisian
 *
 * 本程序是自由软件：你可以依据 GNU 通用公共许可证（GPLv3）的条款
 * 再分发和/或修改它。本程序按“原样”提供，不带任何担保。
 * 完整条款见项目根目录的 LICENSE 文件。
 */

package com.gzpyp.timetable.ocr

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.gzpyp.timetable.core.CtcDecoder
import com.gzpyp.timetable.core.ImageOps
import java.nio.FloatBuffer

/**
 * 验证码识别：ddddocr 的 common_old.onnx（13MB，离线）+ 纯 Kotlin 预处理 + CTC 贪心解码。
 * 构造器拆成「模型字节流」与「Context」两种，前者让 JVM 单元测试能直接跑真实模型。
 */
class CaptchaOcr private constructor(
    modelBytes: ByteArray,
    charsetText: String
) : AutoCloseable {

    private val env: OrtEnvironment = OrtEnvironment.getEnvironment()
    private val session: OrtSession = createSession(modelBytes)
    private val charset: List<String> = parseCharset(charsetText)
    private val allowed: IntArray = if (charset.isEmpty()) IntArray(0) else allowedIndices(charset)
    private val inputName: String = session.inputNames.first()

    constructor(context: Context) : this(
        context.assets.open(MODEL_ASSET).use { it.readBytes() },
        context.assets.open(CHARSET_ASSET).bufferedReader(Charsets.UTF_8).use { it.readText() }
    )

    fun recognize(png: ByteArray): String? {
        val bmp = BitmapFactory.decodeByteArray(png, 0, png.size) ?: return null
        return try {
            recognize(bmp)
        } finally {
            bmp.recycle()
        }
    }

    fun recognize(bmp: Bitmap): String? {
        val w = bmp.width
        val h = bmp.height
        if (w <= 0 || h <= 0) return null
        val px = IntArray(w * h)
        bmp.getPixels(px, 0, w, 0, 0, w, h)
        return recognizePixels(px, w, h)
    }

    /** 直接吃 ARGB 像素，便于单元测试用 ImageIO 解码绕开 android.graphics */
    fun recognizePixels(px: IntArray, w: Int, h: Int): String? {
        val (tensor, tw) = ImageOps.toTensor(px, w, h)
        val shape = longArrayOf(1, 1, TARGET_HEIGHT.toLong(), tw.toLong())

        OnnxTensor.createTensor(env, FloatBuffer.wrap(tensor), shape).use { input ->
            session.run(mapOf(inputName to input)).use { result ->
                val out = result.get(0) as OnnxTensor
                val dims = out.info.shape
                @Suppress("UNCHECKED_CAST")
                val arr = out.value as Array<Array<FloatArray>>

                val timeFirst = dims.size == 3 && dims[0] != 1L
                val steps = if (timeFirst) arr.size else arr[0].size
                val classes = arr[0][0].size
                if (steps <= 0 || classes <= 0) return null

                val flat = FloatArray(steps * classes)
                for (t in 0 until steps) {
                    val row = if (timeFirst) arr[t][0] else arr[0][t]
                    System.arraycopy(row, 0, flat, t * classes, classes)
                }
                return CtcDecoder.decodeText(flat, steps, classes, allowed) { i -> charset[i][0] }
            }
        }
    }

    override fun close() {
        // 环境是进程级单例，只关 session，不关 env
        runCatching { session.close() }
    }

    companion object {
        private const val MODEL_ASSET = "ocr_captcha.onnx"
        private const val CHARSET_ASSET = "ocr_charset.txt"
        const val TARGET_HEIGHT = 64

        /** 单元测试入口：直接喂模型字节与字符集文本 */
        fun fromBytes(modelBytes: ByteArray, charsetText: String): CaptchaOcr =
            CaptchaOcr(modelBytes, charsetText)

        private fun createSession(modelBytes: ByteArray): OrtSession {
            val opts = OrtSession.SessionOptions().apply {
                setIntraOpNumThreads(2)
                setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
            }
            return OrtEnvironment.getEnvironment().createSession(modelBytes, opts)
        }

        private fun parseCharset(text: String): List<String> =
            text.split('\n').map { it.trimEnd('\r') }

        /** 本校验证码只含数字与小写字母；限定字符集可挡掉大写/中文误判 */
        private fun allowedIndices(charset: List<String>): IntArray = charset.indices.filter { i ->
            val c = charset[i]
            c.length == 1 && (c[0] in '0'..'9' || c[0] in 'a'..'z')
        }.toIntArray()
    }
}

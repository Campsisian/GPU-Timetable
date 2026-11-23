package com.gzpyp.timetable.ocr

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * 用本校 460 张真实验证码跑完整链路（Kotlin 预处理 + onnx 推理 + CTC 解码），量出识别率。
 *
 * 样本以裸像素存放：pixels.bin 为连续 ARGB int32（小端），order.txt 首行是宽高，之后是「编号 标签」。
 * 之所以不用 PNG：Android 单测的编译类路径里没有 javax.imageio。
 */
class OcrAccuracyTest {

    @Test
    fun measureAccuracy() {
        val modelFile = File("src/main/assets/ocr_captcha.onnx")
        val charsetFile = File("src/main/assets/ocr_charset.txt")
        val pixelsFile = File("src/test/resources/captcha/pixels.bin")
        val orderFile = File("src/test/resources/captcha/order.txt")
        assertTrue("缺少模型文件", modelFile.exists())
        assertTrue("缺少字符集", charsetFile.exists())
        assertTrue("缺少样本像素", pixelsFile.exists())
        assertTrue("缺少样本清单", orderFile.exists())

        val lines = orderFile.readLines().filter { it.isNotBlank() }
        val (w, h) = lines.first().trim().split(" ").map { it.toInt() }
        val samples = lines.drop(1).map {
            val p = it.trim().split(" ")
            p[0].toInt() to p[1]
        }

        val ocr = CaptchaOcr.fromBytes(modelFile.readBytes(), charsetFile.readText(Charsets.UTF_8))
        val buffer = ByteBuffer.wrap(pixelsFile.readBytes()).order(ByteOrder.LITTLE_ENDIAN)
        val ints = buffer.asIntBuffer()
        val stride = w * h
        val px = IntArray(stride)

        var wordOk = 0
        var charOk = 0
        var charTotal = 0
        val errors = ArrayList<String>()
        val begin = System.currentTimeMillis()

        for ((i, sample) in samples.withIndex()) {
            val (idx, expected) = sample
            ints.position(i * stride)
            ints.get(px)
            val actual = ocr.recognizePixels(px, w, h) ?: ""
            if (actual == expected) wordOk++ else if (errors.size < 12) {
                errors.add("#$idx 期望=$expected 实际=$actual")
            }
            charTotal += expected.length
            charOk += expected.indices.count { it < actual.length && actual[it] == expected[it] }
        }
        ocr.close()

        val total = samples.size
        val wordRate = wordOk * 100.0 / total
        val charRate = charOk * 100.0 / charTotal
        val ms = (System.currentTimeMillis() - begin) / total.coerceAtLeast(1)
        println("=== 验证码识别：整词 $wordOk/$total = %.1f%%，单字 %.1f%%，平均 %d ms/张"
            .format(wordRate, charRate, ms))
        errors.forEach { println("  $it") }

        assertTrue("样本太少", total >= 100)
        assertTrue("整词准确率仅 %.1f%%，达不到可用水平".format(wordRate), wordRate >= 80.0)
    }
}

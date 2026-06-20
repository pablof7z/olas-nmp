package io.f7z.olas.ui.components

import android.graphics.Bitmap
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sign

/**
 * Self-contained native BlurHash decoder — no external dependency.
 * Spec: https://github.com/woltapp/blurhash/blob/master/Algorithm.md
 *
 * Usage:
 *   val bitmap = BlurHashDecoder.decode("LGF5?xYk^6#M@-5c,1Ex@@or[j6o", 32, 32)
 */
object BlurHashDecoder {

    private const val BASE83_CHARS =
        "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz#\$%*+,-.:;=?@[]^_{|}~"

    /** Decode a blurhash string to an [ImageBitmap] of [width]×[height] pixels,
     *  or null if the hash is invalid. */
    fun decode(hash: String, width: Int = 32, height: Int = 32): ImageBitmap? {
        if (hash.length < 6) return null

        val sizeFlag = base83Decode(hash, 0, 1) ?: return null
        val numY = sizeFlag / 9 + 1
        val numX = sizeFlag % 9 + 1
        val expectedLength = 4 + 2 * numX * numY
        if (hash.length != expectedLength) return null

        val quantisedMaximum = base83Decode(hash, 1, 2) ?: return null
        val maxValue = (quantisedMaximum + 1).toFloat() / 166f

        val colorsR = FloatArray(numX * numY)
        val colorsG = FloatArray(numX * numY)
        val colorsB = FloatArray(numX * numY)

        for (i in 0 until numX * numY) {
            if (i == 0) {
                val v = base83Decode(hash, 2, 6) ?: return null
                decodeDC(v, colorsR, colorsG, colorsB, 0)
            } else {
                val off = 4 + i * 2
                val v = base83Decode(hash, off, off + 2) ?: return null
                decodeAC(v, maxValue, colorsR, colorsG, colorsB, i)
            }
        }

        val pixels = IntArray(width * height)
        for (y in 0 until height) {
            for (x in 0 until width) {
                var r = 0f; var g = 0f; var b = 0f
                for (j in 0 until numY) {
                    for (i in 0 until numX) {
                        val basis = cos((Math.PI * x * i) / width).toFloat() *
                                    cos((Math.PI * y * j) / height).toFloat()
                        val idx = j * numX + i
                        r += colorsR[idx] * basis
                        g += colorsG[idx] * basis
                        b += colorsB[idx] * basis
                    }
                }
                val ri = linearToSRGB(r)
                val gi = linearToSRGB(g)
                val bi = linearToSRGB(b)
                pixels[y * width + x] = (0xFF shl 24) or (ri shl 16) or (gi shl 8) or bi
            }
        }

        val bitmap = Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)
        return bitmap.asImageBitmap()
    }

    // ---- helpers ----------------------------------------------------------------

    private fun decodeDC(value: Int, r: FloatArray, g: FloatArray, b: FloatArray, idx: Int) {
        r[idx] = sRGBToLinear(value shr 16)
        g[idx] = sRGBToLinear((value shr 8) and 0xFF)
        b[idx] = sRGBToLinear(value and 0xFF)
    }

    private fun decodeAC(
        value: Int, maxValue: Float,
        r: FloatArray, g: FloatArray, b: FloatArray, idx: Int,
    ) {
        val rQ = value / (19 * 19)
        val gQ = (value / 19) % 19
        val bQ = value % 19
        r[idx] = signedPow((rQ - 9).toFloat(), 2f) * maxValue
        g[idx] = signedPow((gQ - 9).toFloat(), 2f) * maxValue
        b[idx] = signedPow((bQ - 9).toFloat(), 2f) * maxValue
    }

    private fun signedPow(x: Float, p: Float): Float = sign(x) * x.pow(p)

    private fun sRGBToLinear(value: Int): Float {
        val f = value / 255f
        return if (f <= 0.04045f) f / 12.92f else ((f + 0.055f) / 1.055f).pow(2.4f)
    }

    private fun linearToSRGB(value: Float): Int {
        val clamped = value.coerceIn(0f, 1f)
        val srgb = if (clamped <= 0.0031308f) clamped * 12.92f
                   else 1.055f * clamped.pow(1f / 2.4f) - 0.055f
        return (srgb * 255 + 0.5f).toInt().coerceIn(0, 255)
    }

    private fun base83Decode(str: String, start: Int, end: Int): Int? {
        var value = 0
        for (i in start until end) {
            val c = str.getOrNull(i) ?: return null
            val idx = BASE83_CHARS.indexOf(c)
            if (idx == -1) return null
            value = value * 83 + idx
        }
        return value
    }
}

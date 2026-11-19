package com.armx.chaoxing

import android.graphics.Bitmap
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * 真正的（近似）高斯模糊：先按比例降采样，再做多轮盒式模糊（三次盒式≈高斯），
 * 最后双线性放大。降采样是为了让半径覆盖更大范围又不至于太慢。
 */
fun blurBitmap(src: Bitmap, radiusPx: Int): Bitmap {
    if (radiusPx <= 0) return src
    val w = src.width
    val h = src.height
    if (w <= 1 || h <= 1) return src

    val down = max(1, radiusPx / 3)
    val sw = max(1, w / down)
    val sh = max(1, h / down)
    var small = if (sw == w && sh == h) src.copy(Bitmap.Config.ARGB_8888, false) else
        Bitmap.createScaledBitmap(src, sw, sh, true).copy(Bitmap.Config.ARGB_8888, false)

    val r = max(1, radiusPx / down)
    small = boxBlur(small, r, 3)
    val out = Bitmap.createScaledBitmap(small, w, h, true)
    if (small !== src) small.recycle()
    return out
}

private fun boxBlur(src: Bitmap, radius: Int, passes: Int): Bitmap {
    val w = src.width
    val h = src.height
    var pix = IntArray(w * h)
    src.getPixels(pix, 0, w, 0, 0, w, h)
    var buf = IntArray(w * h)

    repeat(passes) {
        // 横向
        horiz(pix, buf, w, h, radius)
        // 纵向
        vert(buf, pix, w, h, radius)
    }
    val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    out.setPixels(pix, 0, w, 0, 0, w, h)
    return out
}

private fun horiz(src: IntArray, dst: IntArray, w: Int, h: Int, r: Int) {
    val div = r * 2 + 1
    for (y in 0 until h) {
        val row = y * w
        var a = 0; var rr = 0; var gg = 0; var bb = 0
        for (i in -r..r) {
            val x = min(w - 1, max(0, i))
            val c = src[row + x]
            a += (c ushr 24) and 0xff
            rr += (c ushr 16) and 0xff
            gg += (c ushr 8) and 0xff
            bb += c and 0xff
        }
        for (x in 0 until w) {
            dst[row + x] = ((a / div) shl 24) or ((rr / div) shl 16) or ((gg / div) shl 8) or (bb / div)
            val addX = min(w - 1, x + r + 1)
            val subX = max(0, x - r)
            val ca = src[row + addX]
            val cs = src[row + subX]
            a += ((ca ushr 24) and 0xff) - ((cs ushr 24) and 0xff)
            rr += ((ca ushr 16) and 0xff) - ((cs ushr 16) and 0xff)
            gg += ((ca ushr 8) and 0xff) - ((cs ushr 8) and 0xff)
            bb += (ca and 0xff) - (cs and 0xff)
        }
    }
}

private fun vert(src: IntArray, dst: IntArray, w: Int, h: Int, r: Int) {
    val div = r * 2 + 1
    for (x in 0 until w) {
        var a = 0; var rr = 0; var gg = 0; var bb = 0
        for (i in -r..r) {
            val y = min(h - 1, max(0, i))
            val c = src[y * w + x]
            a += (c ushr 24) and 0xff
            rr += (c ushr 16) and 0xff
            gg += (c ushr 8) and 0xff
            bb += c and 0xff
        }
        for (y in 0 until h) {
            dst[y * w + x] = ((a / div) shl 24) or ((rr / div) shl 16) or ((gg / div) shl 8) or (bb / div)
            val addY = min(h - 1, y + r + 1)
            val subY = max(0, y - r)
            val ca = src[addY * w + x]
            val cs = src[subY * w + x]
            a += ((ca ushr 24) and 0xff) - ((cs ushr 24) and 0xff)
            rr += ((ca ushr 16) and 0xff) - ((cs ushr 16) and 0xff)
            gg += ((ca ushr 8) and 0xff) - ((cs ushr 8) and 0xff)
            bb += (ca and 0xff) - (cs and 0xff)
        }
    }
}

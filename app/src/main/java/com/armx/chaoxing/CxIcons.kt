package com.armx.chaoxing

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * 自绘的线性图标（24×24）。coui 1.1.0 只带 7 个基础图标，底栏与设置行需要自己提供，
 * 这里保持与 COUI 图标一致的 24dp 画布、纯色填充，由调用方给 tint。
 */
object CxIcons {

    private fun icon(name: String, block: PathBuilder.() -> Unit): ImageVector =
        ImageVector.Builder(
            name = name,
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            path(fill = SolidColor(Color.Black), pathFillType = PathFillType.NonZero) { block() }
        }.build()

    private fun iconEven(name: String, block: PathBuilder.() -> Unit): ImageVector =
        ImageVector.Builder(
            name = name,
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            path(fill = SolidColor(Color.Black), pathFillType = PathFillType.EvenOdd) { block() }
        }.build()

    private fun PathBuilder.rect(x: Float, y: Float, w: Float, h: Float, r: Float = 0f) {
        if (r <= 0f) {
            moveTo(x, y); lineTo(x + w, y); lineTo(x + w, y + h); lineTo(x, y + h); close()
        } else {
            moveTo(x + r, y)
            lineTo(x + w - r, y); arcToRelative(r, r, 0f, false, true, r, r)
            lineTo(x + w, y + h - r); arcToRelative(r, r, 0f, false, true, -r, r)
            lineTo(x + r, y + h); arcToRelative(r, r, 0f, false, true, -r, -r)
            lineTo(x, y + r); arcToRelative(r, r, 0f, false, true, r, -r)
            close()
        }
    }

    private fun PathBuilder.circle(cx: Float, cy: Float, r: Float) {
        moveTo(cx - r, cy)
        arcToRelative(r, r, 0f, true, true, r * 2, 0f)
        arcToRelative(r, r, 0f, true, true, -r * 2, 0f)
        close()
    }

    /** 任务：三行「方块 + 长条」。 */
    val Tasks: ImageVector by lazy {
        icon("cx_tasks") {
            for (i in 0..2) {
                val y = 5f + i * 5.75f
                rect(3f, y, 2.6f, 2.6f, 0.9f)
                rect(8f, y + 0.3f, 13f, 2f, 1f)
            }
        }
    }

    /** 进度：圆环 + 播放三角（用 EvenOdd 挖出孔洞）。 */
    val Progress: ImageVector by lazy {
        ImageVector.Builder(
            name = "cx_progress",
            defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f,
        ).apply {
            path(fill = SolidColor(Color.Black), pathFillType = PathFillType.EvenOdd) {
                circle(12f, 12f, 9f)
                circle(12f, 12f, 7f)
            }
            path(fill = SolidColor(Color.Black)) {
                moveTo(10f, 8.4f); lineTo(16.2f, 12f); lineTo(10f, 15.6f); close()
            }
        }.build()
    }

    /** 设置：三条滑轨 + 圆钮。 */
    val Settings: ImageVector by lazy {
        icon("cx_settings") {
            rect(3f, 6.1f, 18f, 1.8f, 0.9f)
            rect(3f, 11.1f, 18f, 1.8f, 0.9f)
            rect(3f, 16.1f, 18f, 1.8f, 0.9f)
            circle(15.5f, 7f, 2.9f)
            circle(8.5f, 12f, 2.9f)
            circle(17f, 17f, 2.9f)
        }
    }

    /** 用户：头像剪影。 */
    val Person: ImageVector by lazy {
        icon("cx_person") {
            circle(12f, 8f, 3.6f)
            moveTo(4.6f, 20f)
            curveTo(4.6f, 15.8f, 7.9f, 13.2f, 12f, 13.2f)
            curveTo(16.1f, 13.2f, 19.4f, 15.8f, 19.4f, 20f)
            close()
        }
    }

    /** 退出：门 + 右向箭头。 */
    val Logout: ImageVector by lazy {
        icon("cx_logout") {
            rect(4f, 3.4f, 2.4f, 17.2f, 1.1f)
            moveTo(11f, 6.4f); lineTo(12.9f, 6.4f); lineTo(17.4f, 12f); lineTo(12.9f, 17.6f)
            lineTo(11f, 17.6f); lineTo(15.1f, 13f); lineTo(7f, 13f); lineTo(7f, 11f)
            lineTo(15.1f, 11f); close()
        }
    }

    /** 背景图：相框 + 山 + 太阳。 */
    val Image: ImageVector by lazy {
        ImageVector.Builder(
            name = "cx_image",
            defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f,
        ).apply {
            path(fill = SolidColor(Color.Black), pathFillType = PathFillType.EvenOdd) {
                rect(3f, 4.5f, 18f, 15f, 3f)
                rect(5f, 6.5f, 14f, 11f, 1.6f)
            }
            path(fill = SolidColor(Color.Black)) {
                circle(9f, 10f, 1.7f)
                moveTo(6.2f, 17f); lineTo(11.4f, 11.4f); lineTo(14.4f, 14.6f)
                lineTo(16.2f, 12.8f); lineTo(17.8f, 17f); close()
            }
        }.build()
    }

    /** 模糊：三滴柔光。 */
    val Blur: ImageVector by lazy {
        icon("cx_blur") {
            circle(8.5f, 9f, 3.6f)
            circle(15.5f, 9f, 3.6f)
            circle(12f, 15.5f, 3.6f)
        }
    }

    /** 玻璃/外观：星芒。 */
    val Glass: ImageVector by lazy {
        icon("cx_glass") {
            moveTo(12f, 3f); lineTo(13.7f, 9.3f); lineTo(20f, 11f); lineTo(13.7f, 12.7f)
            lineTo(12f, 19f); lineTo(10.3f, 12.7f); lineTo(4f, 11f); lineTo(10.3f, 9.3f)
            close()
            circle(18f, 18f, 2.2f)
        }
    }

    /** 速度：闪电。 */
    val Speed: ImageVector by lazy {
        icon("cx_speed") {
            moveTo(13.4f, 2.8f); lineTo(6.4f, 13.4f); lineTo(10.9f, 13.4f)
            lineTo(9.9f, 21.2f); lineTo(17.4f, 10.4f); lineTo(12.8f, 10.4f); close()
        }
    }

    /** 并发：层叠。 */
    val Layers: ImageVector by lazy {
        icon("cx_layers") {
            moveTo(12f, 3.2f); lineTo(21f, 7.6f); lineTo(12f, 12f); lineTo(3f, 7.6f); close()
            moveTo(12f, 13.6f); lineTo(19.4f, 10f); lineTo(21f, 10.8f); lineTo(12f, 15.2f)
            lineTo(3f, 10.8f); lineTo(4.6f, 10f); close()
            moveTo(12f, 16.8f); lineTo(19.4f, 13.2f); lineTo(21f, 14f); lineTo(12f, 18.4f)
            lineTo(3f, 14f); lineTo(4.6f, 13.2f); close()
        }
    }

    /** 通知：铃铛。 */
    val Notify: ImageVector by lazy {
        icon("cx_notify") {
            moveTo(12f, 3f)
            curveTo(8.7f, 3f, 6.6f, 5.3f, 6.6f, 8.6f)
            lineTo(6.6f, 12.4f)
            lineTo(5.2f, 15.4f)
            lineTo(18.8f, 15.4f)
            lineTo(17.4f, 12.4f)
            lineTo(17.4f, 8.6f)
            curveTo(17.4f, 5.3f, 15.3f, 3f, 12f, 3f)
            close()
            rect(10.2f, 16.6f, 3.6f, 2.6f, 1.3f)
        }
    }

    /** 答案：对话气泡。 */
    val Answer: ImageVector by lazy {
        icon("cx_answer") {
            rect(3f, 4.5f, 18f, 13f, 4f)
            moveTo(8f, 16.6f); lineTo(8f, 21f); lineTo(12.6f, 16.6f); close()
        }
    }

    /** 显示密码：眼睛。 */
    val Eye: ImageVector by lazy {
        iconEven("cx_eye") {
            moveTo(2.4f, 12f)
            curveTo(5.6f, 6.2f, 9.0f, 4.6f, 12f, 4.6f)
            curveTo(15f, 4.6f, 18.4f, 6.2f, 21.6f, 12f)
            curveTo(18.4f, 17.8f, 15f, 19.4f, 12f, 19.4f)
            curveTo(9f, 19.4f, 5.6f, 17.8f, 2.4f, 12f)
            close()
            circle(12f, 12f, 3.2f)
        }
    }

    /** 隐藏密码：带斜线的眼睛。 */
    val EyeOff: ImageVector by lazy {
        iconEven("cx_eye_off") {
            moveTo(2.4f, 12f)
            curveTo(5.6f, 6.2f, 9.0f, 4.6f, 12f, 4.6f)
            curveTo(15f, 4.6f, 18.4f, 6.2f, 21.6f, 12f)
            curveTo(18.4f, 17.8f, 15f, 19.4f, 12f, 19.4f)
            curveTo(9f, 19.4f, 5.6f, 17.8f, 2.4f, 12f)
            close()
            circle(12f, 12f, 3.2f)
            moveTo(3.6f, 3.2f); lineTo(5.4f, 2f); lineTo(20.4f, 20.8f)
            lineTo(18.6f, 22f); close()
        }
    }

    /** 右向箭头：用于可点击行。 */
    val Chevron: ImageVector by lazy {
        icon("cx_chevron") {
            moveTo(9.2f, 5.6f); lineTo(11f, 4f); lineTo(18.4f, 12f); lineTo(11f, 20f)
            lineTo(9.2f, 18.4f); lineTo(15f, 12f); close()
        }
    }

    /** 保活：盾牌。 */
    val Battery: ImageVector by lazy {
        icon("cx_battery") {
            moveTo(12f, 2.6f); lineTo(20f, 6f); lineTo(20f, 12.2f)
            curveTo(20f, 17f, 16.6f, 20.2f, 12f, 21.6f)
            curveTo(7.4f, 20.2f, 4f, 17f, 4f, 12.2f)
            lineTo(4f, 6f); close()
        }
    }

    /** 采样点画一段「环带」，用于刷新图标的圆弧。角度以屏幕坐标计（0=右，顺时针为正）。 */
    private fun PathBuilder.arcBand(
        cx: Float,
        cy: Float,
        rOuter: Float,
        rInner: Float,
        startDeg: Float,
        endDeg: Float,
        steps: Int = 36,
    ) {
        fun px(deg: Float, r: Float): Float =
            (cx + r * Math.cos(Math.toRadians(deg.toDouble()))).toFloat()

        fun py(deg: Float, r: Float): Float =
            (cy + r * Math.sin(Math.toRadians(deg.toDouble()))).toFloat()

        moveTo(px(startDeg, rOuter), py(startDeg, rOuter))
        for (i in 1..steps) {
            val deg = startDeg + (endDeg - startDeg) * i / steps
            lineTo(px(deg, rOuter), py(deg, rOuter))
        }
        lineTo(px(endDeg, rInner), py(endDeg, rInner))
        for (i in steps - 1 downTo 0) {
            val deg = startDeg + (endDeg - startDeg) * i / steps
            lineTo(px(deg, rInner), py(deg, rInner))
        }
        close()
    }

    /** 全选：实心圆角方块 + 挖空的白勾（ColorOS 那种一眼可见的选中态）。 */
    val SelectAll: ImageVector by lazy {
        iconEven("cx_select_all") {
            rect(3f, 3f, 18f, 18f, 5f)
            moveTo(6.6f, 12.5f)
            lineTo(9.9f, 15.8f)
            lineTo(17.2f, 8.2f)
            lineTo(15.4f, 6.5f)
            lineTo(9.9f, 12.3f)
            lineTo(8.4f, 10.8f)
            close()
        }
    }

    /** 全不选：实心圆角方块 + 挖空的一横（= 清空）。 */
    val SelectNone: ImageVector by lazy {
        iconEven("cx_select_none") {
            rect(3f, 3f, 18f, 18f, 5f)
            rect(7.1f, 10.7f, 9.8f, 2.6f, 1.3f)
        }
    }

    /** 刷新：开口圆环 + 切向箭头（标准的「重新加载」符号）。 */
    val Refresh: ImageVector by lazy {
        icon("cx_refresh") {
            arcBand(12f, 12f, 8.6f, 5.7f, 32f, 298f)
            val t2 = Math.toRadians(298.0)
            val cs = Math.cos(t2).toFloat()
            val sn = Math.sin(t2).toFloat()
            val mid = 7.15f
            val baseOuter = 10.2f
            val baseInner = 4.1f
            moveTo(12f + mid * cs + 5.2f * -sn, 12f + mid * sn + 5.2f * cs)
            lineTo(12f + baseOuter * cs - 1.1f * -sn, 12f + baseOuter * sn - 1.1f * cs)
            lineTo(12f + baseInner * cs - 1.1f * -sn, 12f + baseInner * sn - 1.1f * cs)
            close()
        }
    }

    /** 开始：实心右三角。 */
    val Play: ImageVector by lazy {
        icon("cx_play") {
            moveTo(8f, 4.8f)
            lineTo(19.2f, 12f)
            lineTo(8f, 19.2f)
            close()
        }
    }

    /** 停止：圆角方块。 */
    val Stop: ImageVector by lazy {
        icon("cx_stop") {
            rect(6.6f, 6.6f, 10.8f, 10.8f, 2.6f)
        }
    }

    /** 返回：左向三角。 */
    val Back: ImageVector by lazy {
        icon("cx_back") {
            moveTo(15.5f, 4.5f)
            lineTo(15.5f, 19.5f)
            lineTo(6f, 12f)
            close()
        }
    }
}

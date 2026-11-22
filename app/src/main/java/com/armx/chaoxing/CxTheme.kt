package com.armx.chaoxing

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.suqi8.coui.kmp.blur.Backdrop
import io.github.suqi8.coui.kmp.theme.COUITheme
import io.github.suqi8.coui.kmp.theme.darkColorScheme
import io.github.suqi8.coui.kmp.theme.lightColorScheme
import androidx.compose.runtime.mutableFloatStateOf

/**
 * 悬浮底栏不占布局高度，页面需要用这个留白把最后一条内容顶上来，
 * 否则列表会被底栏盖住；因为是 contentPadding，滑动时内容会从底栏后面穿过，没有硬边。
 */
val LocalPageBottomInset = staticCompositionLocalOf { 0.dp }

/** 背景录制层：弹层想画材质模糊时需要它做采样源。 */
val LocalCxBackdrop = staticCompositionLocalOf<Backdrop?> { null }

/** 页面内容的录制层：只录卡片与文字，不含壁纸，供顶部模糊采样。 */
val LocalCxContentBackdrop = staticCompositionLocalOf<Backdrop?> { null }

/**
 * 全局手指状态：卡片靠它画「跟着手指走的弥散光」。
 * 位置是根坐标（屏幕坐标），卡片用自己在根里的位置换算出局部坐标。
 */
class CxPointerState {
    /** 手指在根布局里的位置（屏幕坐标）。 */
    var position by mutableStateOf(Offset.Unspecified)

    /** 现在有没有手指按在屏幕上。 */
    var down by mutableStateOf(false)

    /**
     * 手指正按在「不吃光」的控件上（按钮、下拉、开关行、底栏、执行键…）。
     * 这段时间卡片一律不亮，抬手即清空 —— 不再维护计数与「粘住」状态。
     */
    var blocked by mutableStateOf(false)
}

val LocalCxPointer = staticCompositionLocalOf { CxPointerState() }

/** 页面尺寸与间距，全部对齐 COUI / ColorOS 17 的实测值。 */
object CxMetrics {
    /** 底栏宽度：左右 12dp + 每项 70dp + 项间 8dp，再收 16dp。 */
    fun navWidthDp(items: Int): Int = 12 * 2 + 70 * items + 8 * (items - 1) - 16
}

/** 页面把「大标题折叠进度」写进这里，根容器据此绘制顶部高斯模糊遮挡层。 */
class CxScrollLink {
    var fraction by mutableFloatStateOf(0f)
}

val LocalCxScrollLink = staticCompositionLocalOf { CxScrollLink() }


/** 用滚动增量累加折叠量：不依赖列表项高度，也不会因为首个可见项变化而跳变。 */
fun scrollDepth(link: CxScrollLink, range: Float = 580f): androidx.compose.ui.input.nestedscroll.NestedScrollConnection =
    object : androidx.compose.ui.input.nestedscroll.NestedScrollConnection {
        override fun onPostScroll(
            consumed: androidx.compose.ui.geometry.Offset,
            available: androidx.compose.ui.geometry.Offset,
            source: androidx.compose.ui.input.nestedscroll.NestedScrollSource,
        ): androidx.compose.ui.geometry.Offset {
            link.fraction = (link.fraction - consumed.y / range).coerceIn(0f, 1f)
            return androidx.compose.ui.geometry.Offset.Zero
        }
    }

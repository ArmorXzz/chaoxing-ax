package com.armx.chaoxing

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.suqi8.coui.kmp.theme.COUITheme
import io.github.suqi8.coui.kmp.theme.darkColorScheme
import io.github.suqi8.coui.kmp.theme.lightColorScheme

/**
 * 悬浮底栏不占布局高度，页面需要用这个留白把最后一条内容顶上来，
 * 否则列表会被底栏盖住；因为是 contentPadding，滑动时内容会从底栏后面穿过，没有硬边。
 */
val LocalPageBottomInset = staticCompositionLocalOf { 0.dp }

/** 页面尺寸与间距，全部对齐 COUI / ColorOS 17 的实测值。 */
object CxMetrics {
    /** 底栏宽度：左右 12dp + 每项 70dp + 项间 8dp，再收 16dp。 */
    fun navWidthDp(items: Int): Int = 12 * 2 + 70 * items + 8 * (items - 1) - 16
}

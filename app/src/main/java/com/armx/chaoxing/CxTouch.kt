package com.armx.chaoxing

import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalView


/** 轻触感反馈：按 ROM 支持的常量逐个降级尝试，命中即止。 */
fun haptic(view: View) {
    val candidates = intArrayOf(
        HapticFeedbackConstants.CONTEXT_CLICK,
        HapticFeedbackConstants.CLOCK_TICK,
        HapticFeedbackConstants.KEYBOARD_TAP,
        HapticFeedbackConstants.VIRTUAL_KEY,
    )
    for (c in candidates) {
        val ok = runCatching { view.performHapticFeedback(c) }.getOrDefault(false)
        if (ok) return
    }
}

/**
 * 按压柔性反馈：按下缩小 + 轻震，松手回弹。
 * [onClick] 非空时由本修饰符自己响应点击（避免与外层 clickable 抢手势）。
 */
fun Modifier.flexible(
    scale: Float = 0.97f,
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null,
): Modifier = composed {
    if (!enabled) return@composed this
    val view = LocalView.current
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val s by animateFloatAsState(
        targetValue = if (pressed) scale else 1f,
        animationSpec = tween(durationMillis = 160, easing = FastOutSlowInEasing),
        label = "flexible",
    )
    LaunchedEffect(pressed) {
        if (pressed) haptic(view)
    }
    this
        .graphicsLayer {
            scaleX = s
            scaleY = s
        }
        .clickable(interactionSource = source, indication = null) { onClick?.invoke() }
}

/**
 * 底栏同款手感：与 COUI `SinkFeedback` 一致的「按下沉一下、松手弹性回弹」。
 * 参数取自 COUI 字节码默认值：下沉量 0.94、spring(dampingRatio = 0.8, stiffness = 600)。
 *
 * [interaction] 必须与内部控件（Button/Card）共用同一个 InteractionSource，
 * 否则收不到按压事件。COUI 自己的 `couiTouchMotion` 是 internal，这里按同样参数复刻。
 */
@Composable
fun Modifier.touchFeel(
    interaction: InteractionSource,
    sink: Float = 0.94f,
    enabled: Boolean = true,
): Modifier {
    if (!enabled) return this
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) sink else 1f,
        animationSpec = spring(dampingRatio = 0.8f, stiffness = 600f),
        label = "touchFeel",
    )
    return graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}

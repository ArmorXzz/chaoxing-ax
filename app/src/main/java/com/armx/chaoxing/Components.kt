package com.armx.chaoxing

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.suqi8.coui.kmp.basic.Button
import io.github.suqi8.coui.kmp.basic.ButtonDefaults
import io.github.suqi8.coui.kmp.basic.Card
import io.github.suqi8.coui.kmp.basic.CardColors
import io.github.suqi8.coui.kmp.squircle.SquircleShape
import io.github.suqi8.coui.kmp.squircle.isSquircleEnabled
import io.github.suqi8.coui.kmp.theme.COUIRoundCorner
import io.github.suqi8.coui.kmp.basic.CardDefaults
import io.github.suqi8.coui.kmp.utils.PressFeedbackType
import io.github.suqi8.coui.kmp.basic.Icon
import io.github.suqi8.coui.kmp.basic.SmallTitle
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.theme.COUITheme

/**
 * 统一的卡片：所有「框框」都走这里，拿到与底栏一致的按压回弹（COUI PressFeedback）、
 * 高光描边和材料阴影，而不是自己拼 Modifier。
 */
/** 页面大标题：直接放在列表首项，紧贴状态栏下方，避免 TopAppBar 预留的空白。 */
@Composable
fun PageTitle(title: String, subtitle: String) {
    val scheme = COUITheme.colorScheme
    Column(
        Modifier
            .fillMaxWidth()
            .padding(start = 22.dp, end = 22.dp, top = 70.dp, bottom = 6.dp),
    ) {
        Text(
            title,
            color = scheme.labelPrimary,
            fontSize = 30.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            subtitle,
            color = scheme.labelSecondaryVariant,
            style = COUITheme.textStyles.bodyS,
        )
    }
}

@Composable
fun CxCard(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 20.dp,
    insideMargin: Dp = 0.dp,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val scheme = COUITheme.colorScheme
    // 和 Card 内部用的是同一套形状（squircle），描边才能严丝合缝贴住圆角。
    val squircle = isSquircleEnabled()
    val shape: Shape = remember(cornerRadius, squircle) {
        if (squircle) SquircleShape(cornerRadius) else RoundedCornerShape(cornerRadius)
    }
    val stroke = scheme.labelPrimary.copy(alpha = 0.10f)
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    // 按压只做 1% 的微缩，避免原来 Sink(0.94) 那种「整卡往下陷」的过头感。
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.99f else 1f,
        animationSpec = spring(dampingRatio = 0.8f, stiffness = 600f),
        label = "cardScale",
    )
    val base = if (onClick != null) {
        modifier.clickable(interactionSource = interaction, indication = null, onClick = onClick)
    } else modifier

    Card(
        // 描边画在缩放层里面：跟着卡片一起缩，不会出现「卡缩了描边不动」。
        modifier = base
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .drawWithCache {
                val outline = shape.createOutline(size, layoutDirection, this)
                val width = 0.8.dp.toPx()
                onDrawWithContent {
                    drawContent()
                    when (outline) {
                        is Outline.Generic -> drawPath(outline.path, stroke, style = Stroke(width))
                        is Outline.Rounded -> drawRoundRect(
                            color = stroke,
                            cornerRadius = CornerRadius(cornerRadius.toPx()),
                            style = Stroke(width),
                        )
                        else -> Unit
                    }
                }
            },
        cornerRadius = cornerRadius,
        insideMargin = PaddingValues(insideMargin),
        colors = CxCardColors(),
        pressFeedbackType = PressFeedbackType.None,
        content = content,
    )
}

@Composable
fun Section(title: String, modifier: Modifier = Modifier) {
    SmallTitle(
        text = title,
        modifier = modifier,
        insideMargin = PaddingValues(start = 6.dp, end = 6.dp, top = 18.dp, bottom = 6.dp),
    )
}

/** 统计卡：COUI Card + 三行文本（无自绘背景）。 */
@Composable
fun StatCard(
    label: String,
    value: String,
    hint: String,
    accent: Color,
    modifier: Modifier = Modifier,
) {
    val scheme = COUITheme.colorScheme
    CxCard(
        modifier = modifier,
        cornerRadius = 20.dp,
        insideMargin = 16.dp,
        onClick = {},
    ) {
        Text(label, fontSize = 12.sp, color = scheme.labelSecondaryVariant)
        Text(
            value,
            fontSize = 30.sp,
            fontWeight = FontWeight.SemiBold,
            color = accent,
            modifier = Modifier.padding(top = 6.dp),
        )
        Text(
            hint,
            fontSize = 11.sp,
            color = scheme.labelSecondaryVariant,
            modifier = Modifier.padding(top = 4.dp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * 纯圆形图标按钮（课程列表的全选 / 全不选 / 刷新）。
 * 仍然用 COUI 的 Button，保留它的按压回弹与高光；只是把尺寸压成正方形 + 圆角取半径，
 * 所以渲染出来是正圆而不是胶囊。
 */
@Composable
fun CircleIcon(
    icon: ImageVector,
    enabled: Boolean = true,
    size: Int = 40,
    iconSize: Int = 20,
    onClick: () -> Unit,
) {
    val scheme = COUITheme.colorScheme
    // COUI 的 Button 在不传 cornerRadius（Dp.Unspecified）时会走「胶囊」形状，
    // 正方形按钮因此得到正圆；且不做任何裁剪，按压回弹/高光/材料阴影完整保留。
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.size(size.dp),
        minWidth = size.dp,
        minHeight = size.dp,
        insideMargin = PaddingValues(0.dp),
        colors = ButtonDefaults.buttonColors(
            color = scheme.cardPressed,
            contentColor = if (enabled) scheme.labelPrimary else scheme.disabledNeutral,
        ),
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = if (enabled) scheme.labelPrimary else scheme.disabledNeutral,
            modifier = Modifier.size(iconSize.dp),
        )
    }
}

/** 卡片统一半透明：让预先模糊过的壁纸透出来，浅色/深色同一套逻辑。 */
@Composable
fun CxCardColors(): CardColors {
    val scheme = COUITheme.colorScheme
    // 注意：不要替换 alpha。COUI 的 cardBackground 在深色下本身就是「低透明白」，
    // 直接 copy(alpha = 0.72f) 会把它变成 72% 的白，卡片就会发白。
    return CardColors(
        color = scheme.cardBackground,
        contentColor = scheme.labelPrimary,
        pressedColor = scheme.cardPressed,
    )
}

package com.armx.chaoxing

import androidx.compose.foundation.background
import io.github.suqi8.coui.kmp.overlay.OverlayDialog
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.composed
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
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
import kotlinx.coroutines.launch
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
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.draw.drawWithContent
import io.github.suqi8.coui.kmp.blur.Backdrop
import io.github.suqi8.coui.kmp.blur.couiViewBlur
import io.github.suqi8.coui.kmp.blur.ViewBlurDefaults
import io.github.suqi8.coui.kmp.blur.ViewBlurStyle

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

/**
 * 标记一块「可交互区域」：手指按在这上面时，卡片不显示弥散光，
 * 避免光穿透按钮/开关行。只观察手势，不消费。
 */
/** 把一块内容标成「挡光区」：操作它时不触发背后卡片的弥散光。 */
@Composable
fun CxNoLightBox(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(modifier.fillMaxWidth().cxNoLight()) { content() }
}

fun Modifier.cxNoLight(): Modifier = composed {
    val pointer = LocalCxPointer.current
    pointerInput(pointer) {
        awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false)
            pointer.blocked = true
            try {
                while (true) {
                    val e = awaitPointerEvent()
                    if (e.changes.none { it.pressed }) break
                }
            } finally {
                pointer.blocked = false
            }
        }
    }
}

/**
 * 行级弥散光：设置页里那些「内置答案库」「退出登录」这类整行按钮，
 * 自己带一团跟着手指走的光，同时把背后的卡片光挡住。
 */
@Composable
fun CxRowLight(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val scheme = COUITheme.colorScheme
    val pointer = LocalCxPointer.current
    var topLeft by remember { mutableStateOf(Offset.Zero) }
    var size by remember { mutableStateOf(androidx.compose.ui.unit.IntSize.Zero) }
    val light = remember { Animatable(0f) }
    val local = pointer.position - topLeft
    val inside = pointer.down && !pointer.blocked && pointer.position != Offset.Unspecified &&
        local.x >= 0f && local.y >= 0f &&
        local.x <= size.width.toFloat() && local.y <= size.height.toFloat()
    LaunchedEffect(inside) {
        light.animateTo(if (inside) 1f else 0f, tween(if (inside) 110 else 180))
    }
    Box(
        modifier
            .onGloballyPositioned {
                topLeft = it.positionInRoot()
                size = it.size
            }
            .cxNoLight()
            .drawWithCache {
                // 与 CxCard 同一套算法：0.62 × 短边，上限 200dp（行高很小时退化为宽度的 0.62 倍）
                val short = minOf(size.width.toFloat(), size.height * 3f)
                val r = minOf(short * 0.62f, 200.dp.toPx())
                onDrawBehind {
                    val a = light.value
                    if (a > 0.01f) {
                        drawCircle(
                            brush = Brush.radialGradient(
                                colorStops = arrayOf(
                                    0f to scheme.labelPrimary.copy(alpha = 0.40f * a),
                                    0.42f to scheme.labelPrimary.copy(alpha = 0.16f * a),
                                    1f to Color.Transparent,
                                ),
                                center = local,
                                radius = r,
                            ),
                            radius = r,
                            center = local,
                        )
                    }
                }
            },
    ) { content() }
}

@Composable
fun CxCard(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 20.dp,
    insideMargin: Dp = 0.dp,
    lightRadius: Dp? = null,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val scheme = COUITheme.colorScheme
    // 和 Card 内部用的同一套形状（squircle），描边才能严丝合缝贴住圆角。
    val squircle = isSquircleEnabled()
    val shape: Shape = remember(cornerRadius, squircle) {
        if (squircle) SquircleShape(cornerRadius) else RoundedCornerShape(cornerRadius)
    }
    val interaction = remember { MutableInteractionSource() }
    // 弥散光：跟着全局手指位置走，手指滑到卡片范围内就亮，离开就淡出。
    val pointer = LocalCxPointer.current
    var topLeft by remember { mutableStateOf(Offset.Zero) }
    var cardSize by remember { mutableStateOf(androidx.compose.ui.unit.IntSize.Zero) }
    val light = remember { Animatable(0f) }
    val local = remember(pointer.position, topLeft) { pointer.position - topLeft }
    // 极简规则：手指按在卡片范围内、且不是按在「不吃光」的控件上，就亮；其余情况灭。
    val inside = pointer.down && !pointer.blocked && pointer.position != Offset.Unspecified &&
        local.x >= 0f && local.y >= 0f &&
        local.x <= cardSize.width.toFloat() && local.y <= cardSize.height.toFloat()
    LaunchedEffect(inside) {
        light.animateTo(if (inside) 1f else 0f, tween(if (inside) 110 else 180))
    }
    val base = if (onClick != null) {
        modifier.clickable(interactionSource = interaction, indication = null, onClick = onClick)
    } else modifier

    Card(
        modifier = base
            .onGloballyPositioned {
                topLeft = it.positionInRoot()
                cardSize = it.size
            }
            .drawWithCache {
                val outline = shape.createOutline(size, layoutDirection, this)
                val path = when (outline) {
                    is Outline.Generic -> outline.path
                    is Outline.Rounded -> Path().apply { addRoundRect(outline.roundRect) }
                    else -> Path()
                }
                val width = 0.8.dp.toPx()
                onDrawWithContent {
                    // 光先画，再把内容盖上去：控件本身不会被蒙一层柔光
                    val a = light.value
                    if (a > 0.01f) {
                        // 默认按短边 1.15 倍（统计卡 ≈190dp）；需要收窄的卡片自己传 lightRadius。
                        val r = lightRadius?.toPx() ?: (size.minDimension * 1.15f)
                        clipPath(path) {
                            drawCircle(
                                brush = Brush.radialGradient(
                                    colorStops = arrayOf(
                                        0f to scheme.labelPrimary.copy(alpha = 0.40f * a),
                                        0.42f to scheme.labelPrimary.copy(alpha = 0.16f * a),
                                        1f to Color.Transparent,
                                    ),
                                    center = local,
                                    radius = r,
                                ),
                                radius = r,
                                center = local,
                            )
                        }
                    }
                    drawContent()
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
            color = cxSurface(scheme.cardPressed, 0.62f),
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
/** 当前是浅色主题吗（看背景亮度，跟随应用内主题而不是系统）。 */
@Composable
fun cxIsLight(): Boolean = COUITheme.colorScheme.background.luminance() > 0.5f

/**
 * 半透表面色：浅色模式下 COUI 给的卡片底色是不透明白，
 * 盖在壁纸上像一块实心板；这里按亮度把它调成半透明。
 */
@Composable
fun cxSurface(base: Color, lightAlpha: Float = 0.55f): Color {
    return if (cxIsLight()) base.copy(alpha = minOf(base.alpha, lightAlpha)) else base
}

/**
 * 设置页里的「整行按钮」：不带 COUI 那层按压白遮罩，只靠 [CxRowLight] 的弥散光提示。
 */
@Composable
fun CxArrowRow(
    title: String,
    summary: String? = null,
    tint: Color? = null,
    onClick: () -> Unit,
) {
    val scheme = COUITheme.colorScheme
    val color = tint ?: scheme.labelPrimary
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { onClick() }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = color, style = COUITheme.textStyles.bodyL)
            if (!summary.isNullOrEmpty()) {
                androidx.compose.foundation.layout.Spacer(Modifier.height(2.dp))
                Text(summary, color = scheme.labelSecondaryVariant, style = COUITheme.textStyles.bodyS)
            }
        }
        Icon(CxIcons.Chevron, contentDescription = null, tint = scheme.labelSecondaryVariant, modifier = Modifier.size(18.dp))
    }
}

@Composable
fun CxCardColors(): CardColors {
    val scheme = COUITheme.colorScheme
    // 注意：不要替换 alpha。COUI 的 cardBackground 在深色下本身就是「低透明白」，
    // 直接 copy(alpha = 0.72f) 会把它变成 72% 的白，卡片就会发白。
    return CardColors(
        color = cxSurface(scheme.cardBackground, 0.52f),
        contentColor = scheme.labelPrimary,
        pressedColor = cxSurface(scheme.cardPressed, 0.66f),
    )
}

/** 统一的单选弹层：半透背景 + 材质高斯模糊，替代 COUI 下拉的默认底色。 */
@Composable
fun CxChoiceDialog(
    show: Boolean,
    title: String,
    items: List<String>,
    selected: Int,
    onPick: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val scheme = COUITheme.colorScheme
    val backdrop = LocalCxBackdrop.current
    val dark = isSystemInDarkTheme()
    val blur = if (backdrop != null) dialogBlur(backdrop, dark) else Modifier
    OverlayDialog(
        show = show,
        title = title,
        backgroundColor = scheme.cardBackground.copy(alpha = 0.86f),
        backgroundModifier = blur,
        materialStroke = true,
        onDismissRequest = onDismiss,
    ) {
        Column(Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 8.dp)) {
            items.forEachIndexed { index, text ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { onPick(index) }
                        .padding(horizontal = 24.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text,
                        color = scheme.labelPrimary,
                        style = COUITheme.textStyles.bodyL,
                        modifier = Modifier.weight(1f),
                    )
                    if (index == selected) {
                        Text("✓", color = scheme.primary, style = COUITheme.textStyles.bodyL)
                    }
                }
            }
        }
    }
}

/**
 * 顶部遮挡层：把滚到大标题下方的内容用实时高斯模糊盖住。
 * 纯模糊，不叠任何渐变或底色；模糊节点是录制层的兄弟，避免自采样崩溃。
 */
/** 顶部标题区：严格按 COUI 的大标题折叠尺寸（110dp → 52dp、大标题 32sp→18sp、副标题随之下移）。 */
@Composable
fun CxHeader(
    title: String,
    subtitle: String,
    fraction: Float,
    dark: Boolean,
    modifier: Modifier = Modifier,
) {
    val scheme = COUITheme.colorScheme
    val backdrop = LocalCxContentBackdrop.current
    val f = fraction.coerceIn(0f, 1f)

    // COUI 的尺寸：展开 110dp、收起 52dp；大标题 32sp、收起标题 18sp
    val barH = 110.dp + (52.dp - 110.dp) * f
    val titleSize = 32f + (18f - 32f) * f
    val subSize = 14f + (11f - 14f) * f
    val shift = 58.dp * f

    Box(
        modifier
            .fillMaxWidth()
            .height(110.dp)
    ) {
        if (backdrop != null && f > 0.001f && io.github.suqi8.coui.kmp.shader.isRuntimeShaderSupported()) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(110.dp)
                    .couiViewBlur(
                        backdrop,
                        ViewBlurStyle(28f, Color.Transparent, BlendMode.SrcOver, Color.Transparent, BlendMode.SrcOver),
                        RectangleShape,
                    )
                    .background(
                        Brush.verticalGradient(
                            0f to scheme.backgroundWithCard.copy(alpha = 0.90f * f),
                            0.62f to scheme.backgroundWithCard.copy(alpha = 0.55f * f),
                            1f to Color.Transparent,
                        )
                    )
            )
        }
        Column(
            Modifier
                .align(Alignment.BottomStart)
                .padding(start = 22.dp, bottom = (110.dp - barH))
                .graphicsLayer { translationY = -shift.toPx() },
        ) {
            Text(
                text = title,
                color = scheme.labelPrimary,
                fontSize = titleSize.sp,
                fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                lineHeight = (titleSize * 1.15f).sp,
            )
            Text(
                text = subtitle,
                color = scheme.labelSecondaryVariant,
                fontSize = subSize.sp,
                lineHeight = (subSize * 1.25f).sp,
                modifier = Modifier.padding(top = (3.5.dp * (1f - f))),
            )
        }
    }
}

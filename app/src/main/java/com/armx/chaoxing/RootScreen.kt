package com.armx.chaoxing

import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import kotlinx.coroutines.CancellationException
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.activity.compose.BackHandler
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.ui.platform.LocalView
import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner
import androidx.navigationevent.compose.rememberNavigationEventDispatcherOwner
import io.github.suqi8.coui.kmp.basic.Button
import io.github.suqi8.coui.kmp.basic.ButtonDefaults
import io.github.suqi8.coui.kmp.basic.FloatingNavigationBar
import io.github.suqi8.coui.kmp.basic.Icon
import io.github.suqi8.coui.kmp.basic.FloatingNavigationBarItem
import io.github.suqi8.coui.kmp.basic.Scaffold
import io.github.suqi8.coui.kmp.basic.SnackbarHost
import io.github.suqi8.coui.kmp.basic.SnackbarHostState
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.blur.Backdrop
import io.github.suqi8.coui.kmp.blur.layerBackdrop
import io.github.suqi8.coui.kmp.blur.rememberLayerBackdrop
import io.github.suqi8.coui.kmp.effect.LocalMaterialBackdrop
import io.github.suqi8.coui.kmp.effect.LocalPopupMaterialBlur
import io.github.suqi8.coui.kmp.layout.DialogButtonBar
import io.github.suqi8.coui.kmp.layout.DialogButtonBarAction
import io.github.suqi8.coui.kmp.overlay.OverlayDialog
import io.github.suqi8.coui.kmp.theme.COUITheme
import io.github.suqi8.coui.kmp.theme.darkColorScheme
import io.github.suqi8.coui.kmp.theme.lightColorScheme

/**
 * 应用根容器：COUITheme + 背景录制层（供材质模糊采样）+ COUI Scaffold（弹层容器）+ 底栏 + 执行 Dock。
 * 每个页面内部再套一个 COUI Scaffold + TopAppBar（官方允许多 Scaffold 并存）。
 */
@Composable
fun CxRoot(
    controller: CxController,
    onPickBg: () -> Unit,
    onOpenNotif: () -> Unit,
    onOpenBattery: () -> Unit,
) {
    val dark = when (controller.theme) {
        1 -> false
        2 -> true
        else -> isSystemInDarkTheme()
    }

    COUITheme(colors = if (dark) darkColorScheme() else lightColorScheme()) {
        val scheme = COUITheme.colorScheme
        var confirmLogout by remember { mutableStateOf(false) }
        var answersOpen by remember { mutableStateOf(false) }
        var failuresOpen by remember { mutableStateOf(false) }
        val progress = if (controller.canExitOnBack) controller.backProgress else 0f

        // 背景录制层：layerBackdrop 只捕获这一层绘制的内容，供底栏 / 面板采样模糊。
        val backdrop = rememberLayerBackdrop {
            drawRect(scheme.backgroundWithCard)
            drawContent()
        }
        val blurOk = remember { blurSupported() }
        // Android 17 起改用更轻的模糊；Android 16 及以下保持原设计。
        val blurScale = if (android.os.Build.VERSION.SDK_INT >= 37) 0.8f else 1.6f
        val viewBlur = remember(backdrop) { CxViewBlurProvider(backdrop) }
        val materialBlur = remember(backdrop) { CxMaterialBlurProvider(backdrop) }
        val schemeBar = scheme

        val snackbar = remember { SnackbarHostState() }
        LaunchedEffect(controller.toastSeq) {
            val text = controller.toastText
            if (controller.toastSeq > 0 && !text.isNullOrEmpty()) {
                snackbar.showSnackbar(text)
            }
        }

        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val p = progress.coerceIn(0f, 1f)
                    scaleX = 1f - 0.06f * p
                    scaleY = 1f - 0.06f * p
                    alpha = 1f - 0.25f * p
                },
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .layerBackdrop(backdrop)
                    .background(scheme.backgroundWithCard),
            ) {
                val bmp = controller.bgBitmap
                if (controller.bgOn && bmp != null) {
                    Image(
                        bitmap = bmp.asImageBitmap(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxSize()
                            .blur((controller.blur.toFloat() * blurScale).dp),
                    )
                    Box(
                        Modifier
                            .fillMaxSize()
                            .background(Color(if (dark) 0x8C000000 else 0x80FFFFFF)),
                    )
                }
            }

            val page: @Composable () -> Unit = {
                val nev = rememberNavigationEventDispatcherOwner(parent = null)
                CompositionLocalProvider(LocalNavigationEventDispatcherOwner provides nev) {
                Scaffold(
                    containerColor = Color.Transparent,
                    snackbarHost = { SnackbarHost(snackbar) },
                ) { pad ->
                    val navBottom = WindowInsets.navigationBars
                        .asPaddingValues()
                        .calculateBottomPadding()
                    val top = (pad.calculateTopPadding() - 16.dp).coerceAtLeast(0.dp)
                    // 底栏与执行按钮改成「悬浮层」：不再占据布局高度，
                    // 页面就能整屏绘制、列表一直滑到底栏后面，不会被一条硬边截断。
                    val dockH = if (controller.logged && controller.tab == 0) 58.dp else 0.dp
                    val pageBottom = if (!controller.logged) 0.dp
                    else navBottom + 4.dp + 56.dp + 2.dp + dockH + 14.dp

                    // 二级页返回走预测式返回：手指拖动时系统会回放上一页，
                    // 松手完成才真正关闭，和桌面端手势一致。
                    // 二级页入场/返回都走这里：入场是「右侧滑入 + 轻微放大淡入」，
                    // 返回手势进行中由 slide 实时驱动页面右移，取消则弹簧回位。
                    val scope = rememberCoroutineScope()
                    val slide = remember { Animatable(0f) }
                    if (answersOpen || failuresOpen) {
                        PredictiveBackHandler(enabled = true) { events ->
                            try {
                                events.collect { e ->
                                    slide.snapTo(e.progress.coerceIn(0f, 1f))
                                }
                                // 手势完成：直接关闭二级页，回到「设置」。
                                if (failuresOpen) failuresOpen = false else answersOpen = false
                                slide.snapTo(0f)
                            } catch (e: CancellationException) {
                                // 手势取消：弹簧回弹到原位。
                                scope.launch {
                                    slide.animateTo(
                                        targetValue = 0f,
                                        animationSpec = spring(dampingRatio = 0.82f, stiffness = 520f),
                                    )
                                }
                                throw e
                            }
                        }
                    }
                    val pageKey = when {
                        answersOpen -> 100
                        failuresOpen -> 101
                        else -> controller.tab
                    }

                    Box(Modifier.fillMaxSize()) {
                        CompositionLocalProvider(LocalPageBottomInset provides pageBottom) {
                            if (pageKey >= 100 || !controller.logged) {
                                AnimatedContent(
                                    targetState = pageKey,
                                    transitionSpec = {
                                        if (targetState >= 100) {
                                            // 打开二级页：从右侧滑入（位移 1/5 屏宽）+ 0.96 放大淡入，
                                            // 曲线先快后缓，总时长 320ms（ONextBox 的观感）。
                                            val enter = slideInHorizontally(
                                                animationSpec = tween(320, easing = FastOutSlowInEasing),
                                            ) { it / 5 } + fadeIn(tween(220)) + scaleIn(
                                                initialScale = 0.96f,
                                                animationSpec = tween(320, easing = FastOutSlowInEasing),
                                            )
                                            val exit = fadeOut(tween(160)) + scaleOut(
                                                targetScale = 1.02f,
                                                animationSpec = tween(320, easing = FastOutSlowInEasing),
                                            )
                                            enter togetherWith exit
                                        } else {
                                            // 关闭二级页：原路向右滑出，后台页面轻微放大淡入。
                                            val enter = fadeIn(tween(220)) + scaleIn(
                                                initialScale = 0.98f,
                                                animationSpec = tween(300, easing = FastOutSlowInEasing),
                                            )
                                            val exit = slideOutHorizontally(
                                                animationSpec = tween(300, easing = FastOutSlowInEasing),
                                            ) { it / 5 } + fadeOut(tween(220)) + scaleOut(
                                                targetScale = 0.98f,
                                                animationSpec = tween(300),
                                            )
                                            enter togetherWith exit
                                        }
                                    },
                                    label = "page",
                                ) { key ->
                                    if (key == 100) {
                                        AnswersScreen(
                                            controller = controller,
                                            onBack = { answersOpen = false },
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .padding(top = top)
                                                // 预测式返回：跟手右移，同时淡出一点，露出背后的「设置」页。
                                                .graphicsLayer {
                                                    translationX = slide.value * size.width
                                                    alpha = 1f - 0.2f * slide.value
                                                },
                                        )
                                    } else if (key == 101) {
                                        FailuresScreen(
                                            controller = controller,
                                            onBack = { failuresOpen = false },
                                            modifier = Modifier.fillMaxSize().padding(top = top),
                                        )
                                    } else {
                                        LoginScreen(
                                            controller = controller,
                                            modifier = Modifier.fillMaxSize().padding(top = top),
                                        )
                                    }
                                }
                            } else {
                                // 底栏切页：跟桌面一样左右滑动，滑动结束时回写选中项。
                                val pager = rememberPagerState(initialPage = controller.tab, pageCount = { 3 })
                                // 只有滑动停稳后才回写选中项：动画经过中间页时不能改 tab，
                                // 否则「任务 → 设置」会被中间页打断，停在进度页。
                                LaunchedEffect(pager.settledPage, pager.isScrollInProgress) {
                                    if (!pager.isScrollInProgress && controller.tab != pager.settledPage) {
                                        controller.switchTab(pager.settledPage)
                                    }
                                }
                                LaunchedEffect(controller.tab) {
                                    if (!pager.isScrollInProgress && pager.currentPage != controller.tab) {
                                        // 总时长固定 380ms，只改缓动曲线（先快后缓）。
                                        pager.animateScrollToPage(
                                            controller.tab,
                                            animationSpec = tween(380, easing = FastOutSlowInEasing),
                                        )
                                    }
                                }
                                HorizontalPager(
                                    state = pager,
                                    modifier = Modifier.fillMaxSize(),
                                    beyondViewportPageCount = 1,
                                    // 只允许底栏切页：手指左右拖动不再翻页。
                                    userScrollEnabled = false,
                                ) { index ->
                                    when (index) {
                                        0 -> TasksScreen(
                                            controller = controller,
                                            onStart = { controller.startTask() },
                                            modifier = Modifier.fillMaxSize().padding(top = top),
                                        )
                                        1 -> ProgressScreen(
                                            controller = controller,
                                            onOpenFailures = { failuresOpen = true },
                                            modifier = Modifier.fillMaxSize().padding(top = top),
                                        )
                                        else -> SettingsScreen(
                                            controller = controller,
                                            onPickBg = onPickBg,
                                            onOpenNotif = onOpenNotif,
                                            onOpenBattery = onOpenBattery,
                                            onLogout = { confirmLogout = true },
                                            onOpenAnswers = { answersOpen = true },
                                            modifier = Modifier.fillMaxSize().padding(top = top),
                                        )
                                }
                            }
                            }
                        }

                        if (!answersOpen && !failuresOpen && controller.logged) {
                            Column(
                                Modifier
                                    .align(Alignment.BottomCenter)
                                    .fillMaxWidth()
                                    .navigationBarsPadding()
                                    .padding(bottom = 4.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                if (controller.tab == 0) {
                                    ActionDock(controller)
                                    Spacer(2.dp)
                                }
                                CxBottomBar(controller)
                            }
                        }
                    }

                    LogoutDialog(
                        show = confirmLogout,
                        dark = dark,
                        blurOk = blurOk,
                        backdrop = backdrop,
                        onConfirm = {
                            confirmLogout = false
                            controller.logout()
                        },
                        onDismiss = { confirmLogout = false },
                    )
                }
                }
            }

            if (blurOk) {
                CompositionLocalProvider(
                    LocalMaterialBackdrop provides viewBlur,
                    LocalPopupMaterialBlur provides materialBlur,
                ) { page() }
            } else {
                page()
            }
        }

    }
}

@Composable
private fun Spacer(height: Dp) {
    androidx.compose.foundation.layout.Spacer(Modifier.height(height))
}

@Composable
private fun CxBottomBar(controller: CxController) {
    if (!controller.logged) return
    val view = LocalView.current
    val scheme = COUITheme.colorScheme
    FloatingNavigationBar(
        horizontalOutSidePadding = 16.dp,
        horizontalAlignment = Alignment.CenterHorizontally,
        shadowElevation = 0.dp,
        backgroundModifier = Modifier,
        color = scheme.cardBackground.copy(alpha = 0.92f),
    ) {
        FloatingNavigationBarItem(
            selected = controller.tab == 0,
            onClick = { haptic(view); controller.switchTab(0) },
            icon = CxIcons.Tasks,
            label = "任务",
        )
        FloatingNavigationBarItem(
            selected = controller.tab == 1,
            onClick = { haptic(view); controller.switchTab(1) },
            icon = CxIcons.Progress,
            label = "进度",
        )
        FloatingNavigationBarItem(
            selected = controller.tab == 2,
            onClick = { haptic(view); controller.switchTab(2) },
            icon = CxIcons.Settings,
            label = "设置",
        )
    }
}

@Composable
private fun CircleAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    enabled: Boolean,
    primary: Boolean,
    onClick: () -> Unit,
    size: androidx.compose.ui.unit.Dp = 58.dp,
) {
    val scheme = COUITheme.colorScheme
    // 不传 cornerRadius：COUI 会按「胶囊」画形状，正方形就成了正圆，
    // 同时保留 Button 自己的按压回弹、高光描边与材料效果（没有任何裁剪层）。
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.size(size),
        colors = if (primary) ButtonDefaults.buttonColorsPrimary() else ButtonDefaults.buttonColors(
            color = scheme.cardPressed,
            contentColor = scheme.labelPrimary,
            disabledColor = Color.Transparent,
            disabledContentColor = scheme.disabledNeutral,
        ),
        minWidth = size,
        minHeight = size,
        insideMargin = PaddingValues(0.dp),
    ) {
        Icon(
            icon,
            contentDescription = label,
            tint = if (primary) scheme.labelOnColor
            else if (enabled) scheme.labelPrimary else scheme.disabledNeutral,
            modifier = Modifier.size(26.dp),
        )
    }
}

@Composable
private fun ActionDock(controller: CxController) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircleAction(
            icon = CxIcons.Play,
            label = "开始执行",
            enabled = !controller.running,
            primary = true,
            onClick = { controller.startTask() },
        )
        CircleAction(
            icon = CxIcons.Stop,
            label = "停止",
            enabled = controller.running,
            primary = false,
            onClick = { controller.stopTask() },
        )
    }
}

@Composable
private fun LogoutDialog(
    show: Boolean,
    dark: Boolean,
    blurOk: Boolean,
    backdrop: Backdrop,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val scheme = COUITheme.colorScheme
    OverlayDialog(
        show = show,
        title = "退出登录",
        summary = "退出后需要重新登录才能继续刷课",
        backgroundColor = if (blurOk) Color.Transparent else scheme.backgroundWithCard,
        backgroundModifier = if (blurOk) dialogBlur(backdrop, dark) else Modifier,
        materialStroke = blurOk,
        onDismissRequest = onDismiss,
    ) {
        DialogButtonBar(
            negative = DialogButtonBarAction(text = "取消", onClick = onDismiss),
            positive = DialogButtonBarAction(text = "退出", onClick = onConfirm),
        )
    }
}

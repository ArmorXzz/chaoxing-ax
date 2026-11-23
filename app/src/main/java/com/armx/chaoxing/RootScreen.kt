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
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.activity.compose.BackHandler
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.ui.platform.LocalDensity
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
import io.github.suqi8.coui.kmp.layout.DialogButtonBar
import io.github.suqi8.coui.kmp.layout.DialogButtonBarAction
import io.github.suqi8.coui.kmp.overlay.OverlayDialog
import androidx.compose.foundation.text.BasicText
import io.github.suqi8.coui.kmp.overlay.OverlayUserStatementDialog
import io.github.suqi8.coui.kmp.overlay.OverlayGuideDialog
import androidx.compose.foundation.layout.statusBarsPadding
import io.github.suqi8.coui.kmp.theme.COUITheme
import io.github.suqi8.coui.kmp.theme.darkColorScheme
import io.github.suqi8.coui.kmp.theme.lightColorScheme
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import kotlinx.coroutines.delay
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.layout.onSizeChanged

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
    // 二级页自己的滚动位置：关掉再开还是原位
    val answersList = rememberLazyListState()
        var failuresOpen by remember { mutableStateOf(false) }
    var aiOpen by remember { mutableStateOf(false) }
        val progress = if (controller.canExitOnBack) controller.backProgress else 0f

        // 背景录制层：layerBackdrop 只捕获这一层绘制的内容，供底栏 / 面板采样模糊。
        val backdrop = rememberLayerBackdrop {
            drawRect(scheme.backgroundWithCard)
            drawContent()
        }

        // 内容层录制：顶部遮挡要模糊的是「滚过去的列表内容」。
        // 注意这里不能先画不透明底色，否则模糊结果就是一块黑。
        val contentBackdrop = rememberLayerBackdrop {
            drawContent()
        }
        val scrollLink = remember { CxScrollLink() }
        LaunchedEffect(Unit) {
            var last = -1
            snapshotFlow { (scrollLink.fraction * 20).toInt() }
                .collect { if (it != last) { last = it; Crash.mark("scroll " + it * 5 + "%") } }
        }
        val headerH = 110.dp
        val blurOk = remember { blurSupported() }
        // Android 17 起改用更轻的模糊；Android 16 及以下保持原设计。
        // 20 档线性映射：Android 17 上限约 6dp，旧系统约 24dp
        val blurScale = if (android.os.Build.VERSION.SDK_INT >= 37) 0.14f else 0.50f
        val viewBlur = remember(backdrop) { CxViewBlurProvider(backdrop) }
        val materialBlur = remember(backdrop) { CxMaterialBlurProvider(backdrop) }
        val schemeBar = scheme

        val pointer = remember { CxPointerState() }
        val store0 = remember { Store(controller.app) }
        var agreed by remember { mutableStateOf(store0.agreed()) }
        var guided by remember { mutableStateOf(store0.guided()) }
        var aboutOpen by remember { mutableStateOf(false) }
        var quizOpen by remember { mutableStateOf(false) }
        var updateOpen by remember { mutableStateOf(false) }
        var switchOpen by remember { mutableStateOf(false) }
        val snack = remember { io.github.suqi8.coui.kmp.basic.SnackbarHostState() }
        LaunchedEffect(controller.toastSeq) {
            if (controller.toastSeq > 0 && controller.toastText.isNotEmpty()) {
                snack.showSnackbar(controller.toastText)
            }
        }

        Box(
            Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            val change = event.changes.firstOrNull() ?: continue
                            pointer.position = change.position
                            pointer.down = change.pressed
                        }
                    }
                }
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
                CompositionLocalProvider(
                    LocalNavigationEventDispatcherOwner provides nev,
                    LocalCxBackdrop provides backdrop,
                    LocalCxScrollLink provides scrollLink,
                ) {
                    Scaffold(
                        containerColor = Color.Transparent,
                        snackbarHost = {
                            io.github.suqi8.coui.kmp.basic.SnackbarHost(
                                snack,
                                modifier = Modifier.padding(bottom = 132.dp),
                            )
                        },
                    ) { pad ->
                        val navBottom = WindowInsets.navigationBars
                            .asPaddingValues()
                            .calculateBottomPadding()
                        val topInset = (pad.calculateTopPadding() - 16.dp).coerceAtLeast(0.dp)
                        val top = topInset + headerH
                        // 底栏与执行按钮是「悬浮层」：页面整屏绘制，列表滑到底栏后面。
                        val pageBottom = if (!controller.logged) 0.dp
                        else navBottom + 4.dp + 56.dp + 2.dp + 58.dp + 14.dp
                        val secondary = when {
                            updateOpen -> 105
                            quizOpen -> 104
                            aboutOpen -> 103
                            aiOpen -> 102
                            answersOpen -> 100
                            failuresOpen -> 101
                            else -> 0
                        }

                        BackHandler(enabled = secondary != 0) {
                            quizOpen = false
                            updateOpen = false
                            answersOpen = false
                            failuresOpen = false
                            aiOpen = false
                            aboutOpen = false
                        }

                        Box(Modifier.fillMaxSize()) {
                            // 壁纸画在录制层之外：模糊层只采内容，不把背景一起糊
                            val bg = controller.bgBitmap
                            if (controller.bgOn && bg != null) {
                                Image(
                                    bitmap = bg.asImageBitmap(),
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

                            CompositionLocalProvider(LocalCxContentBackdrop provides contentBackdrop) {
                            Box(Modifier.fillMaxSize().layerBackdrop(contentBackdrop)) {
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
                                } else {
                                    Box(Modifier.fillMaxSize().background(scheme.backgroundWithCard))
                                }
                                    AnimatedContent(
                                        targetState = secondary,
                                        transitionSpec = {
                                            if (targetState != 0) {
                                                // 打开二级页：从中心稍微放大 + 淡入，全程 280ms
                                                (
                                                    fadeIn(tween(240)) +
                                                        scaleIn(initialScale = 0.92f, animationSpec = tween(280))
                                                    ).togetherWith(
                                                    fadeOut(tween(200)) +
                                                        scaleOut(targetScale = 0.98f, animationSpec = tween(240)),
                                                )
                                            } else {
                                                // 关闭：对称收回去，不位移
                                                (
                                                    fadeIn(tween(240)) + scaleIn(initialScale = 0.98f, animationSpec = tween(280))
                                                    ).togetherWith(
                                                    fadeOut(tween(200)) + scaleOut(targetScale = 0.92f, animationSpec = tween(240)),
                                                )
                                            }
                                        },
                                        label = "secondary",
                                    ) { key ->
                                        if (key != 0) {
                                            if (key == 105) {
                                                UpdateScreen(
                                                    controller = controller,
                                                    onBack = { updateOpen = false },
                                                    modifier = Modifier.fillMaxSize().padding(top = topInset),
                                                )
                                            } else if (key == 104) {
                                                QuizScreen(
                                                    controller = controller,
                                                    onBack = { quizOpen = false },
                                                    modifier = Modifier.fillMaxSize().padding(top = topInset),
                                                )
                                            } else if (key == 103) {
                                                AboutScreen(
                                                    controller = controller,
                                                    onBack = { aboutOpen = false },
                                                    onOpenUpdate = { updateOpen = true },
                                                    onReplayGuide = { store0.saveGuided(false); guided = false },
                                                    modifier = Modifier.fillMaxSize().padding(top = topInset),
                                                )
                                            } else if (key == 102) {
                                                AiScreen(
                                                    controller = controller,
                                                    onBack = { aiOpen = false },
                                                    modifier = Modifier.fillMaxSize().padding(top = topInset),
                                                )
                                            } else if (key == 100) {
                                                AnswersScreen(
                                                    controller = controller,
                                                    onBack = { answersOpen = false },
                                                    modifier = Modifier.fillMaxSize().padding(top = topInset),
                                                    listState = answersList,
                                                )
                                            } else {
                                                FailuresScreen(
                                                    controller = controller,
                                                    onBack = { failuresOpen = false },
                                                    modifier = Modifier.fillMaxSize().padding(top = topInset),
                                                )
                                            }
                                        } else {
                                            CompositionLocalProvider(
                                                LocalCxBackdrop provides backdrop,
                                                LocalPageBottomInset provides pageBottom,
                                            ) {
                                                if (!controller.logged) {
                                                    LoginScreen(
                                                        controller = controller,
                                                        modifier = Modifier.fillMaxSize().padding(top = top),
                                                    )
                                                } else {
                                                    TabsPager(
                                                        controller = controller,
                                                        top = top,
                                                        onPickBg = onPickBg,
                                                        onOpenNotif = onOpenNotif,
                                                        onOpenBattery = onOpenBattery,
                                                        onLogout = { confirmLogout = true },
                                                        onOpenAnswers = { answersOpen = true },
                                                        onOpenFailures = { failuresOpen = true },
                                                        onOpenAi = { aiOpen = true },
                                                        onOpenAbout = { aboutOpen = true },
                                                        onOpenQuiz = { quizOpen = true },
                                                        onSwitchAccount = { switchOpen = true },
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            // 二级页把底栏「收进屏幕」，回来时弹一下（幅度很小）
                            AnimatedVisibility(
                                visible = secondary == 0 && controller.logged,
                                enter = slideInVertically(
                                    initialOffsetY = { it },
                                    animationSpec = spring(dampingRatio = 0.52f, stiffness = 340f),
                                ) + fadeIn(tween(120)),
                                exit = slideOutVertically(
                                    targetOffsetY = { it },
                                    animationSpec = tween(220, easing = FastOutSlowInEasing),
                                ) + fadeOut(tween(140)),
                                modifier = Modifier.align(Alignment.BottomCenter),
                            ) {
                                Column(
                                    Modifier
                                        .fillMaxWidth()
                                        .navigationBarsPadding()
                                        .padding(bottom = 4.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                ) {
                                    AnimatedVisibility(
                                        visible = controller.tab == 0,
                                        enter = fadeIn(tween(140)) +
                                            scaleIn(
                                                initialScale = 0.62f,
                                                animationSpec = spring(dampingRatio = 0.34f, stiffness = 620f),
                                            ),
                                        exit = fadeOut(tween(120)) +
                                            scaleOut(
                                                targetScale = 0.62f,
                                                animationSpec = spring(dampingRatio = 0.42f, stiffness = 700f),
                                            ),
                                    ) {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            ActionDock(controller)
                                            Spacer(2.dp)
                                        }
                                    }
                                    Box(Modifier.cxNoLight()) { CxBottomBar(controller) }
                                }
                            }

                        }


                        val accs = controller.savedAccounts()
                        io.github.suqi8.coui.kmp.overlay.OverlaySingleChoiceDialog(
                            show = switchOpen,
                            title = Loc.of("切换账号"),
                            items = accs.map {
                                io.github.suqi8.coui.kmp.layout.ListDialogItem(
                                    text = it.title(),
                                    summary = it.username,
                                )
                            } + io.github.suqi8.coui.kmp.layout.ListDialogItem(
                                text = Loc.of("添加新账号"),
                                summary = Loc.of("回到登录页手动输入"),
                            ),
                            selectedIndex = -1,
                            onSelectedIndexChange = { idx ->
                                switchOpen = false
                                if (idx in accs.indices) controller.switchAccount(accs[idx].username)
                                else { controller.clearPrefill(); controller.logout() }
                            },
                            onDismissRequest = { switchOpen = false },
                        )

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
                        if (!agreed) {
                            OverlayUserStatementDialog(
                                show = true,
                                onConfirm = {
                                    store0.saveAgreed(true)
                                    agreed = true
                                },
                                onExit = {
                                    (controller.app as? android.app.Activity)?.finish()
                                },
                                backgroundModifier = if (blurOk) dialogBlur(backdrop, dark) else Modifier,
                                logo = controller.appIcon?.let {
                                    androidx.compose.ui.graphics.painter.BitmapPainter(it.asImageBitmap())
                                },
                                title = Loc.of("欢迎使用"),
                                appName = "超星AX",
                                appMessage = Loc.of("课程视频、文档与测验的本地自动化客户端"),
                                statement = androidx.compose.ui.text.buildAnnotatedString {
                                    append(Loc.of("继续即表示你已阅读并同意以下条款。"))
                                },
                                protocolText = agreementText(),
                                confirmText = Loc.of("同意并继续"),
                                exitText = Loc.of("不同意，退出"),
                                backgroundColor = Color.Transparent,
                                materialStroke = blurOk,
                                enableWindowDim = true,
                            )
                        }

                        if (agreed && !guided) {
                            CxGuideDialog(
                                pages = guidePages(),
                                onFinish = {
                                    store0.saveGuided(true)
                                    guided = true
                                },
                            )
                        }
                    }
                }
            }

            if (blurOk) {
                CompositionLocalProvider(
                    LocalMaterialBackdrop provides viewBlur,
                    LocalCxPointer provides pointer,
                ) { page() }
            } else {
                CompositionLocalProvider(LocalCxPointer provides pointer) { page() }
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
        color = cxSurface(scheme.cardBackground, 0.80f),
    ) {
        FloatingNavigationBarItem(
            selected = controller.tab == 0,
            onClick = { haptic(view); controller.switchTab(0) },
            icon = CxIcons.Tasks,
            label = Loc.of("任务"),
        )
        FloatingNavigationBarItem(
            selected = controller.tab == 1,
            onClick = { haptic(view); controller.switchTab(1) },
            icon = CxIcons.Progress,
            label = Loc.of("进度"),
        )
        FloatingNavigationBarItem(
            selected = controller.tab == 2,
            onClick = { haptic(view); controller.switchTab(2) },
            icon = CxIcons.Settings,
            label = Loc.of("设置"),
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
    val pointer = LocalCxPointer.current
    var topLeft by remember { mutableStateOf(androidx.compose.ui.geometry.Offset.Zero) }
    // 不传 cornerRadius：COUI 会按「胶囊」画形状，正方形就成了正圆，
    // 同时保留 Button 自己的按压回弹、高光描边与材料效果（没有任何裁剪层）。
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .size(size)
            .cxNoLight()
            .onGloballyPositioned { topLeft = it.positionInRoot() }
            .drawWithContent {
                drawContent()
                // 内部光：手指按在这个圆键上时，键内的亮点跟着手指走（不画到键外）。
                val localPos = pointer.position - topLeft
                val inside = pointer.down &&
                    localPos.x >= 0f && localPos.y >= 0f &&
                    localPos.x <= this.size.width && localPos.y <= this.size.height
                if (inside) {
                    val r = this.size.minDimension * 0.62f
                    drawCircle(
                        brush = Brush.radialGradient(
                            colorStops = arrayOf(
                                0f to Color.White.copy(alpha = 0.30f),
                                0.55f to Color.White.copy(alpha = 0.10f),
                                1f to Color.Transparent,
                            ),
                            center = localPos,
                            radius = r,
                        ),
                        radius = r,
                        center = localPos,
                    )
                }
            },
        colors = if (primary) ButtonDefaults.buttonColorsPrimary() else ButtonDefaults.buttonColors(
            color = cxSurface(scheme.cardPressed, 0.62f),
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
            label = Loc.of("开始执行"),
            enabled = !controller.running,
            primary = true,
            onClick = { controller.startTask() },
        )
        CircleAction(
            icon = CxIcons.Stop,
            label = Loc.of("停止"),
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
        title = Loc.of("退出登录"),
        summary = Loc.of("退出后需要重新登录才能继续刷课"),
        backgroundColor = if (blurOk) Color.Transparent else scheme.backgroundWithCard,
        backgroundModifier = if (blurOk) dialogBlur(backdrop, dark) else Modifier,
        materialStroke = blurOk,
        onDismissRequest = onDismiss,
    ) {
        DialogButtonBar(
            negative = DialogButtonBarAction(text = Loc.of("取消"), onClick = onDismiss),
            positive = DialogButtonBarAction(text = Loc.of("退出"), onClick = onConfirm),
        )
    }
}


/** 三个主标签的横向分页：只由底栏驱动，手指横拖不翻页。 */
@Composable
private fun TabsPager(
    controller: CxController,
    top: Dp,
    onPickBg: () -> Unit,
    onOpenNotif: () -> Unit,
    onOpenBattery: () -> Unit,
    onLogout: () -> Unit,
    onOpenAnswers: () -> Unit,
    onOpenFailures: () -> Unit,
    onOpenAi: () -> Unit,
    onOpenAbout: () -> Unit,
    onOpenQuiz: () -> Unit,
    onSwitchAccount: () -> Unit,
) {
    // 三个标签各自的滚动位置提升到这里：打开/关闭二级页时列表不会被重新创建
    val tasksList = rememberLazyListState()
    val progressList = rememberLazyListState()
    val settingsList = rememberLazyListState()
    // 每个标签各持一份折叠进度：切到别的页面再回来，原来的折叠状态还在。
    val links = remember { List(3) { CxScrollLink() } }
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
        userScrollEnabled = false,
    ) { index ->
        CompositionLocalProvider(LocalCxScrollLink provides links[index]) {
        when (index) {
            0 -> TasksScreen(
                controller = controller,
                onStart = { controller.startTask() },
                modifier = Modifier.fillMaxSize(),
                contentTop = top,
                listState = tasksList,
            )
            1 -> ProgressScreen(
                controller = controller,
                onOpenFailures = onOpenFailures,
                onOpenQuiz = onOpenQuiz,
                modifier = Modifier.fillMaxSize(),
                contentTop = top,
                listState = progressList,
            )
            else -> SettingsScreen(
                controller = controller,
                onPickBg = onPickBg,
                onOpenNotif = onOpenNotif,
                onOpenBattery = onOpenBattery,
                onLogout = onLogout,
                onOpenAnswers = onOpenAnswers,
                onOpenAi = onOpenAi,
                onOpenAbout = onOpenAbout,
                onSwitchAccount = onSwitchAccount,
                modifier = Modifier.fillMaxSize(),
                contentTop = top,
                listState = settingsList,
            )
        }
        }
    }
}

/**
 * 根容器标题区：纯运动，不做交叉淡化。
 * 展开 → 折叠：标题字号 30sp→22sp 连续缩小，副标题从标题下方侧移到它右侧，
 * 全程同一套布局，位置由实测的字形尺寸推算，不存在两套排布对不齐的问题。
 */
@Composable
private fun PageHeader(
    title: String,
    subtitle: String,
    fraction: Float,
    topInset: Dp,
    modifier: Modifier = Modifier,
) {
    val scheme = COUITheme.colorScheme
    val f = fraction.coerceIn(0f, 1f)
    val density = LocalDensity.current
    var titlePx by remember { mutableStateOf(androidx.compose.ui.unit.IntSize.Zero) }
    val titleH = with(density) { titlePx.height.toDp() }
    val titleW = with(density) { titlePx.width.toDp() }

    Column(
        modifier
            .fillMaxWidth()
            .padding(start = 22.dp, end = 16.dp, top = topInset + 46.dp - 14.dp * f),
    ) {
        Text(
            title,
            color = scheme.labelPrimary,
            fontSize = androidx.compose.ui.util.lerp(30f, 22f, f).sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            onTextLayout = { titlePx = it.size },
        )
        Text(
            subtitle,
            color = scheme.labelSecondaryVariant,
            fontSize = androidx.compose.ui.util.lerp(13.5f, 12f, f).sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.offset(x = (titleW + 8.dp) * f, y = -(titleH - 6.dp) * f),
        )
    }
}

private fun agreementText() = androidx.compose.ui.text.buildAnnotatedString {
    append(Loc.of("一、本应用仅用于作者本人的课程学习辅助，使用者应自行确认所在学校的学习规范。\n\n"))
    append(Loc.of("二、账号信息、DeepSeek API Key、运行日志全部只保存在本机，不会上传到任何第三方服务器；随机壁纸会向公开图库发起请求。\n\n"))
    append(Loc.of("三、自动播放、自动阅读、自动作答可能违反课程平台的使用条款，由此产生的后果由使用者自行承担。\n\n"))
    append(Loc.of("四、内置题库仅覆盖「国家安全教育」的公开章节，AI 作答不保证正确，提交前请自行核对。\n\n"))
    append(Loc.of("五、本应用按「现状」提供，不提供任何形式的担保。"))
}

private fun guidePages() = listOf(
    Loc.of("先登录，再选课") to Loc.of("在任务页勾选要刷的课程，点「开始执行」，其余交给后台。"),
    Loc.of("进度与失败都能追") to Loc.of("进度页按视频合并任务点，失败的点可以点进去看原始报错。"),
    Loc.of("AI 只补题库没覆盖的题") to Loc.of("设置里打开「AI 自动答题」并填入 DeepSeek Key，默认只保存不提交。"),
)

@Composable
private fun CxGuideDialog(pages: List<Pair<String, String>>, onFinish: () -> Unit) {
    val scheme = COUITheme.colorScheme
    var page by remember { mutableStateOf(0) }
    val last = page >= pages.lastIndex
    OverlayDialog(
        show = true,
        title = Loc.of("快速上手"),
        summary = (page + 1).toString() + " / " + pages.size,
        onDismissRequest = onFinish,
        backgroundColor = scheme.backgroundWithCard.copy(alpha = 0.94f),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 6.dp),
        ) {
            Text(
                pages[page].first,
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                color = scheme.labelPrimary,
            )
            androidx.compose.foundation.layout.Spacer(Modifier.height(10.dp))
            Text(
                pages[page].second,
                style = COUITheme.textStyles.bodyS.copy(
                    color = scheme.labelSecondary,
                    lineHeight = 21.sp,
                ),
            )
            androidx.compose.foundation.layout.Spacer(Modifier.height(20.dp))
            DialogButtonBar(
                negative = DialogButtonBarAction(text = Loc.of("跳过"), onClick = onFinish),
                positive = DialogButtonBarAction(
                    text = if (last) Loc.of("开始使用") else Loc.of("下一步"),
                    onClick = { if (last) onFinish() else page += 1 },
                ),
            )
        }
    }
}

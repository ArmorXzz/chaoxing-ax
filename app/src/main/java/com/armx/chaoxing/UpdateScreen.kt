package com.armx.chaoxing

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.suqi8.coui.kmp.basic.Button
import io.github.suqi8.coui.kmp.basic.ButtonDefaults
import io.github.suqi8.coui.kmp.basic.COUIScrollBehavior
import io.github.suqi8.coui.kmp.basic.Scaffold
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.basic.TopAppBar
import io.github.suqi8.coui.kmp.basic.rememberTopAppBarState
import io.github.suqi8.coui.kmp.theme.COUITheme
import io.github.suqi8.coui.kmp.utils.overScrollVertical
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class UpPhase { Checking, Latest, Newer, Failed }

/** 软件更新：大号版本号 + 波浪头图 + 三态切换 + 更新说明。 */
@Composable
fun UpdateScreen(
    controller: CxController,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = COUITheme.colorScheme
    val ctx = controller.app
    val scope = rememberCoroutineScope()
    val behavior = COUIScrollBehavior(rememberTopAppBarState())

    var phase by remember { mutableStateOf(UpPhase.Checking) }
    var info by remember { mutableStateOf<Update.Info?>(null) }
    var err by remember { mutableStateOf("") }
    var notesOpen by remember { mutableStateOf(true) }
    var tried by remember { mutableStateOf(0) }

    fun check() {
        phase = UpPhase.Checking
        err = ""
        scope.launch {
            val r = withContext(Dispatchers.IO) { runCatching { Update.latest() } }
            r.onSuccess {
                info = it
                phase = if (it.code > Update.code(BuildInfo.VERSION)) UpPhase.Newer else UpPhase.Latest
            }.onFailure {
                err = it.message ?: Loc.of("网络错误")
                phase = UpPhase.Failed
            }
        }
    }

    androidx.compose.runtime.LaunchedEffect(tried) { if (tried == 0) check() }

    val big = remember { BuildInfo.VERSION.split(".").take(2).joinToString(".") }
    val hasNew = phase == UpPhase.Newer

    Scaffold(
        modifier = modifier,
        containerColor = Color.Transparent,
        contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = Loc.of("软件更新"),
                navigationIcon = {
                    CircleIcon(icon = CxIcons.Back, size = 40, iconSize = 20, onClick = onBack)
                },
                scrollBehavior = behavior,
                defaultWindowInsetsPadding = false,
                color = Color.Transparent,
                showDivider = false,
            )
        },
        bottomBar = {
            Box(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 10.dp)) {
                Button(
                    onClick = { info?.let { Update.open(ctx, it.url) } },
                    enabled = hasNew,
                    modifier = Modifier.fillMaxWidth(),
                    minHeight = 52.dp,
                    insideMargin = PaddingValues(vertical = 14.dp),
                    colors = if (hasNew) ButtonDefaults.buttonColorsPrimary()
                    else ButtonDefaults.buttonColors(
                        color = scheme.cardPressed,
                        contentColor = scheme.disabledNeutral,
                    ),
                ) {
                    Text(if (hasNew) Loc.of("下载安装包") else if (phase == UpPhase.Checking) Loc.of("正在检查更新") else Loc.of("已是最新版本"))
                }
            }
        },
    ) { pad ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().overScrollVertical()
            .nestedScroll(behavior.nestedScrollConnection),
            contentPadding = PaddingValues(
                top = pad.calculateTopPadding() + 4.dp,
                bottom = pad.calculateBottomPadding() + 8.dp,
            ),
        ) {
            item {
                Column(Modifier.padding(horizontal = 16.dp)) {
                    CxCard(cornerRadius = 26.dp, modifier = Modifier.animateContentSize(tween(260))) {
                        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 22.dp)) {
                            Text(big, fontSize = 64.sp, fontWeight = FontWeight.Bold, color = scheme.labelPrimary)
                            Spacer(Modifier.height(6.dp))
                            WaveDecor(height = 46.dp)
                            Spacer(Modifier.height(26.dp))
                            AnimatedContent(
                                targetState = phase,
                                transitionSpec = {
                                    (fadeIn(tween(220)) + scaleIn(animationSpec = tween(260), initialScale = 0.96f)) togetherWith
                                        (fadeOut(tween(160)) + scaleOut(animationSpec = tween(200), targetScale = 1.02f))
                                },
                                label = "phase",
                            ) { p ->
                                Column {
                                    when (p) {
                                        UpPhase.Checking -> Row(verticalAlignment = Alignment.CenterVertically) {
                                            Spin(18.dp)
                                            Spacer(Modifier.size(8.dp))
                                            Text(Loc.of("正在检查更新"), fontSize = 16.sp, color = scheme.labelPrimary)
                                        }
                                        UpPhase.Newer -> {
                                            Text(Loc.of("发现新版本"), fontSize = 16.sp, color = scheme.labelPrimary)
                                            Spacer(Modifier.height(2.dp))
                                            Text(
                                                "v${info?.tag?.trimStart('v', 'V') ?: "?"}",
                                                fontSize = 13.sp,
                                                color = scheme.labelSecondaryVariant,
                                            )
                                        }
                                        UpPhase.Latest -> {
                                            Text(Loc.of("已是最新版本"), fontSize = 16.sp, color = scheme.labelPrimary)
                                            Spacer(Modifier.height(2.dp))
                                            Text(
                                                "当前 v${BuildInfo.VERSION}（${BuildInfo.VERSION_CODE}）",
                                                fontSize = 13.sp,
                                                color = scheme.labelSecondaryVariant,
                                            )
                                        }
                                        UpPhase.Failed -> {
                                            Text(Loc.of("检查失败"), fontSize = 16.sp, color = Color(0xFFE5484D))
                                            Spacer(Modifier.height(2.dp))
                                            Text(err, fontSize = 12.5.sp, color = scheme.labelSecondaryVariant)
                                            Spacer(Modifier.height(8.dp))
                                            Text(
                                                Loc.of("点这里重试"),
                                                fontSize = 13.sp,
                                                color = scheme.primary,
                                                modifier = Modifier.clickable { tried++; check() },
                                            )
                                        }
                                    }
                                    Spacer(Modifier.height(14.dp))
                                    Text("超星AX", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = scheme.labelPrimary)
                                    Text("Android · COUI 17", fontSize = 12.sp, color = scheme.labelSecondaryVariant)
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(14.dp))

                    CxCard(cornerRadius = 20.dp) {
                        CardListRow {
                            Text(Loc.of("软件版本"), fontSize = 15.sp, color = scheme.labelPrimary)
                            Text(
                                buildString {
                                    append("v")
                                    append(info?.tag?.trimStart('v', 'V') ?: BuildInfo.VERSION)
                                    val sz = info?.size ?: 0L
                                    if (sz > 0) append("  ·  ${"%.1f".format(sz / 1024f / 1024f)} MB")
                                },
                                fontSize = 13.sp,
                                color = scheme.labelSecondaryVariant,
                            )
                        }
                    }

                    if (info?.notes?.isNotEmpty() == true) {
                        Spacer(Modifier.height(14.dp))
                        CxCard(cornerRadius = 20.dp) {
                            Column(Modifier.fillMaxWidth()) {
                                Row(
                                    Modifier
                                        .fillMaxWidth()
                                        .clickable { notesOpen = !notesOpen }
                                        .padding(horizontal = 16.dp, vertical = 14.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                ) {
                                    Text(Loc.of("更新说明"), fontSize = 15.sp, color = scheme.labelPrimary)
                                    Text(
                                        if (notesOpen) Loc.of("收起") else Loc.of("展开"),
                                        fontSize = 13.sp,
                                        color = scheme.primary,
                                    )
                                }
                                androidx.compose.animation.AnimatedVisibility(visible = notesOpen) {
                                    Column(Modifier.height(280.dp).verticalScroll(rememberScrollState())) {
                                        BasicText(
                                            info!!.notes,
                                            style = COUITheme.textStyles.bodyS.copy(color = scheme.labelSecondary),
                                            modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CardListRow(content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 13.dp)) { content() }
}

/** 两条贝塞尔波浪，用主题色淡淡铺一层。 */
@Composable
private fun WaveDecor(height: androidx.compose.ui.unit.Dp) {
    val scheme = COUITheme.colorScheme
    val c1 = scheme.primary.copy(alpha = 0.55f)
    val c2 = scheme.primary.copy(alpha = 0.22f)
    Canvas(Modifier.fillMaxWidth().height(height)) {
        val w = size.width
        val h = size.height
        val p1 = Path().apply {
            moveTo(0f, h * 0.62f)
            cubicTo(w * 0.28f, h * 0.05f, w * 0.62f, h * 1.05f, w, h * 0.34f)
        }
        val p2 = Path().apply {
            moveTo(0f, h * 0.90f)
            cubicTo(w * 0.34f, h * 0.40f, w * 0.70f, h * 1.20f, w, h * 0.60f)
        }
        drawPath(p1, c1, style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round))
        drawPath(p2, c2, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round))
    }
}

/** 极简旋转指示器（COUI 的加载指示器在此版本未稳定导出）。 */
@Composable
private fun Spin(size: androidx.compose.ui.unit.Dp) {
    val scheme = COUITheme.colorScheme
    val t = rememberInfiniteTransition(label = "spin")
    val a by t.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(900, easing = LinearEasing), RepeatMode.Restart),
        label = "angle",
    )
    Canvas(Modifier.size(size).graphicsLayer { rotationZ = a }) {
        drawArc(
            color = scheme.labelSecondaryVariant,
            startAngle = 20f,
            sweepAngle = 280f,
            useCenter = false,
            topLeft = Offset(1.5f, 1.5f),
            size = Size(this.size.width - 3f, this.size.height - 3f),
            style = Stroke(width = 2.2f, cap = StrokeCap.Round),
        )
    }
}

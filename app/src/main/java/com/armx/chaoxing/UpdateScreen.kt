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
import androidx.compose.foundation.background
import androidx.compose.runtime.mutableFloatStateOf
import io.github.suqi8.coui.kmp.basic.LinearProgressIndicator
import io.github.suqi8.coui.kmp.basic.ProgressIndicatorDefaults
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
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
import io.github.suqi8.coui.kmp.basic.InstallLoadProgress
import io.github.suqi8.coui.kmp.basic.InstallLoadState
import io.github.suqi8.coui.kmp.basic.InstallLoadStyle
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

    val big = remember { BuildInfo.VERSION.split(".").first() }
    val hasNew = phase == UpPhase.Newer

    var dl by remember { mutableStateOf(false) }
    var pct by remember { mutableFloatStateOf(0f) }
    var dlErr by remember { mutableStateOf("") }
    var notesOpen by remember { mutableStateOf(true) }

    fun startDownload() {
        if (dl) return
        val apk = info?.apk.orEmpty()
        if (apk.isEmpty()) {
            dlErr = Loc.of("这个版本没有可下载的安装包")
            return
        }
        dl = true
        pct = 0f
        dlErr = ""
        scope.launch {
            val r = withContext(Dispatchers.IO) {
                runCatching { ApkFile.download(ctx, Update.sources(apk)) { v -> pct = v } }
            }
            dl = false
            r.onSuccess { f ->
                runCatching { ApkFile.install(ctx, f) }
                    .onFailure { dlErr = Loc.of("无法拉起系统安装器") }
            }.onFailure { dlErr = it.message ?: Loc.of("下载失败") }
        }
    }

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
            Column(
                Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp)
                    .padding(top = 8.dp, bottom = 26.dp)
            ) {
                InstallLoadProgress(
                    text = when {
                        dl -> Loc.of("正在下载") + " " + (pct * 100).toInt() + "%"
                        hasNew -> Loc.of("下载安装包")
                        phase == UpPhase.Checking -> Loc.of("正在检查更新")
                        phase == UpPhase.Failed -> Loc.of("重新检查")
                        else -> Loc.of("已是最新版本")
                    },
                    onClick = {
                        if (!dl) {
                            if (hasNew) startDownload()
                            else if (phase == UpPhase.Failed) {
                                tried++
                                check()
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    state = when {
                        dl -> InstallLoadState.Loading
                        phase == UpPhase.Failed || dlErr.isNotEmpty() -> InstallLoadState.Failed
                        else -> InstallLoadState.Idle
                    },
                    progress = pct,
                    enabled = hasNew || phase == UpPhase.Failed,
                    style = InstallLoadStyle.Large,
                )
                if (hasNew) {
                    Spacer(Modifier.height(10.dp))
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        Text(
                            Loc.of("改用浏览器下载"),
                            fontSize = 12.5.sp,
                            color = scheme.labelSecondaryVariant,
                            modifier = Modifier.clickable { info?.let { Update.open(ctx, it.url) } },
                        )
                    }
                }
                if (dlErr.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Text(dlErr, fontSize = 12.sp, color = Color(0xFFE5484D))
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
                            Text(big, fontSize = 88.sp, fontWeight = FontWeight.Bold, color = Color(0xFFE5484D))
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
                                    Text(deviceLabel(), fontSize = 12.sp, color = scheme.labelSecondaryVariant)
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
                        CxCard(
                            cornerRadius = 20.dp,
                        ) {
                            Column(Modifier.fillMaxWidth()) {
                                Row(
                                    Modifier
                                        .fillMaxWidth()
                                        .clickable { notesOpen = !notesOpen }
                                        .padding(horizontal = 16.dp, vertical = 14.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        Loc.of("更新说明"),
                                        fontSize = 15.sp,
                                        color = scheme.labelPrimary,
                                        modifier = Modifier.weight(1f),
                                    )
                                    Text(
                                        if (notesOpen) Loc.of("收起") else Loc.of("展开"),
                                        fontSize = 13.sp,
                                        color = scheme.primary,
                                    )
                                }
                                androidx.compose.animation.AnimatedVisibility(
                                    visible = notesOpen,
                                    enter = androidx.compose.animation.expandVertically(tween(220)) +
                                        androidx.compose.animation.fadeIn(tween(160)),
                                    exit = androidx.compose.animation.shrinkVertically(tween(200)) +
                                        androidx.compose.animation.fadeOut(tween(120)),
                                ) {
                                    ReleaseNotes(
                                        info!!.notes,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(start = 16.dp, end = 16.dp, bottom = 18.dp),
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
    val phase = rememberInfiniteTransition(label = "wave").animateFloat(
        initialValue = 0f,
        targetValue = (2 * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(2600, easing = LinearEasing), RepeatMode.Restart),
        label = "wavePhase",
    )
    Canvas(Modifier.fillMaxWidth().height(height)) {
        val w = size.width
        val h = size.height
        // 整数个周期铺满整宽：x=0 与 x=w 的相位差恰为 2π 的整数倍，头尾天然对齐。
        fun sine(amp: Float, cycles: Int, midY: Float, color: Color, strokeDp: Float) {
            val p = Path()
            val step = 4f
            var x = 0f
            while (x <= w) {
                val theta = (2 * Math.PI).toFloat() * cycles * (x / w) - phase.value
                val y = midY + amp * kotlin.math.sin(theta)
                if (x == 0f) p.moveTo(x, y) else p.lineTo(x, y)
                x += step
            }
            drawPath(p, color, style = Stroke(width = strokeDp.dp.toPx(), cap = StrokeCap.Round))
        }
        sine(h * 0.20f, 1, h * 0.52f, c1, 3f)
        sine(h * 0.14f, 2, h * 0.74f, c2, 2f)
    }
}

private fun deviceLabel(): String {
    val raw = android.os.Build.MANUFACTURER ?: ""
    val model = android.os.Build.MODEL ?: ""
    val brand = when {
        raw.equals("OnePlus", true) -> "一加"
        raw.equals("Xiaomi", true) || raw.equals("Redmi", true) -> "小米"
        raw.equals("HUAWEI", true) -> "华为"
        raw.equals("HONOR", true) -> "荣耀"
        raw.equals("OPPO", true) -> "OPPO"
        raw.equals("vivo", true) -> "vivo"
        raw.equals("samsung", true) -> "三星"
        raw.equals("google", true) -> "Google"
        raw.isEmpty() -> ""
        else -> raw.replaceFirstChar { it.uppercase() }
    }
    val name = when {
        model.uppercase().startsWith("PJZ110") -> "13"
        model.uppercase().startsWith("PJZ") -> model.removePrefix("PJZ")
        else -> model
    }
    return when {
        brand.isEmpty() -> name
        name.isEmpty() -> brand
        else -> "$brand $name"
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

/** 轻量 Markdown：标题、列表、代码块、行内强调与反引号，够读更新说明就行。 */
private fun mdInline(raw: String): String {
    var t = raw
    t = t.replace(Regex("!?\\[([^\\]]*)]\\([^)]*\\)"), "$1")
    t = t.replace("**", "").replace("__", "")
    t = t.replace("`", "")
    t = t.replace(Regex("^\\s*>\\s?"), "")
    return t.trim()
}

@Composable
private fun ReleaseNotes(text: String, modifier: Modifier = Modifier) {
    val scheme = COUITheme.colorScheme
    val lines = remember(text) { text.replace("\r\n", "\n").split("\n") }
    Column(modifier) {
        var inCode = false
        for (raw in lines) {
            val line = raw.trimEnd()
            when {
                line.trimStart().startsWith("```") -> {
                    inCode = !inCode
                    Spacer(Modifier.height(4.dp))
                }
                inCode -> Text(
                    line,
                    fontSize = 11.5.sp,
                    color = scheme.labelSecondary,
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(scheme.cardPressed.copy(alpha = 0.55f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 8.dp, vertical = 3.dp),
                )
                line.startsWith("### ") -> {
                    Spacer(Modifier.height(8.dp))
                    Text(mdInline(line.removePrefix("### ")), fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = scheme.labelPrimary)
                }
                line.startsWith("## ") -> {
                    Spacer(Modifier.height(8.dp))
                    Text(mdInline(line.removePrefix("## ")), fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = scheme.labelPrimary)
                }
                line.startsWith("# ") -> {
                    Spacer(Modifier.height(10.dp))
                    Text(mdInline(line.removePrefix("# ")), fontSize = 16.sp, fontWeight = FontWeight.Bold, color = scheme.labelPrimary)
                }
                line.startsWith("- ") || line.startsWith("* ") -> Text(
                    "· " + mdInline(line.substring(2)),
                    fontSize = 13.sp,
                    lineHeight = 19.sp,
                    color = scheme.labelSecondary,
                    modifier = Modifier.padding(start = 2.dp, top = 2.dp),
                )
                line.isBlank() -> Spacer(Modifier.height(6.dp))
                else -> Text(
                    mdInline(line),
                    fontSize = 13.sp,
                    lineHeight = 19.sp,
                    color = scheme.labelSecondary,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
    }
}

package com.armx.chaoxing

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.suqi8.coui.kmp.basic.Card
import io.github.suqi8.coui.kmp.basic.CardDefaults
import io.github.suqi8.coui.kmp.basic.COUIScrollBehavior
import io.github.suqi8.coui.kmp.basic.EmptyState
import io.github.suqi8.coui.kmp.basic.Icon
import io.github.suqi8.coui.kmp.basic.IconButton
import io.github.suqi8.coui.kmp.basic.LinearProgressIndicator
import io.github.suqi8.coui.kmp.basic.ProgressIndicatorDefaults
import io.github.suqi8.coui.kmp.basic.Scaffold
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.basic.TopAppBar
import io.github.suqi8.coui.kmp.basic.rememberTopAppBarState
import io.github.suqi8.coui.kmp.theme.COUITheme
import io.github.suqi8.coui.kmp.utils.overScrollVertical
import kotlinx.coroutines.delay
import androidx.compose.runtime.DisposableEffect

private val OK_COLOR = Color(0xFF22C55E)
private val ERR_COLOR = Color(0xFFEF4444)
private val INFO_COLOR = Color(0xFF3482FF)
private val WARN_COLOR = Color(0xFFF59E0B)

/** 同一视频的多个任务点合并后的一张卡。 */
private class VGroup(val name: String, val items: List<Model.VTask>) {
    val failed: Int = items.count { it.failed() }
    val finished: Int = items.count { it.done() }
    val playing: Boolean = items.any { it.status == "播放中" }
    val settled: Boolean = items.any { it.status == "结算中" }
    val best: Model.VTask = items.maxByOrNull { it.progress } ?: items.first()
    val progress: Float = items.maxOfOrNull { it.progress.toFloat() }?.coerceIn(0f, 1f) ?: 0f
    val play: Double = items.maxOfOrNull { it.play } ?: 0.0
    val status: String = when {
        finished == items.size -> "完成"
        failed > 0 && !playing -> "失败"
        playing -> "播放中"
        settled -> "结算中"
        else -> items.last().status ?: "待开始"
    }
}

/** 进度页：状态行 + 总进度 + 合并后的视频卡（含实时读秒）。 */
@Composable
fun ProgressScreen(
    controller: CxController,
    onOpenFailures: () -> Unit,
    onOpenQuiz: () -> Unit,
    modifier: Modifier = Modifier,
    contentTop: Dp = 0.dp,
    listState: LazyListState = rememberLazyListState(),
) {
    val scheme = COUITheme.colorScheme
    val total = controller.total
    val done = controller.done
    val failed = controller.failed
    val videos = controller.videos
    val running = controller.running
    val groups = remember(videos) { videos.groupBy { it.name }.map { VGroup(it.key, it.value) } }
    val playing = groups.count { it.playing }

    val percent = if (total > 0) (done * 100) / total else 0
    val dotColor = when {
        total > 0 && done >= total && failed == 0 -> OK_COLOR
        failed > 0 && !running -> ERR_COLOR
        running -> INFO_COLOR
        else -> scheme.labelSecondaryVariant
    }
    val title = when {
        total == 0 && !running -> "等待任务启动"
        running && playing > 0 -> "正在播放 $playing 个视频"
        running -> "正在检测视频任务"
        total > 0 && failed > 0 -> "任务已结束"
        total > 0 -> Loc.of("全部完成")
        else -> "等待任务启动"
    }
    val subtitle = when {
        total == 0 -> Loc.of("回到「任务」页勾选课程并点「开始执行」")
        running -> "已完成 $done / $total · 失败 $failed"
        else -> "本轮共 $total 个任务点 · 失败 $failed"
    }

    var tick by remember { mutableStateOf(0L) }
    LaunchedEffect(running, videos.size) {
        while (true) {
            delay(200)
            tick++
        }
    }

    val scrollLink = LocalCxScrollLink.current
    // 折叠进度：列表滚过的距离映射到 0..1（220dp 走完）
    DisposableEffect(Unit) { onDispose { scrollLink.fraction = 0f } }

    val barState = rememberTopAppBarState()
    val behavior = COUIScrollBehavior(barState)
    val limitPx = with(androidx.compose.ui.platform.LocalDensity.current) { 58.dp.toPx() }
    androidx.compose.runtime.SideEffect {
        if (barState.heightOffsetLimit == 0f) barState.heightOffsetLimit = -limitPx
    }
    androidx.compose.runtime.SideEffect {
        val l = barState.heightOffsetLimit
        if (l < 0f) barState.heightOffset = l * scrollLink.fraction.coerceIn(0f, 1f)
    }
    // 自绘的滚动比例直接喂给 COUI：nestedScroll 这条链路在部分机型上收不到增量，
    Scaffold(
        modifier = modifier,
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                    title = Loc.of("进度"),
                    subtitle = Loc.of("实时显示所有并行视频的播放状态"),
                    scrollBehavior = behavior,
                    hideSubtitleOnCollapse = false,
                    color = Color.Transparent,
                    showDivider = false,
                )
        },
    ) { pad ->
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .overScrollVertical()
                .nestedScroll(behavior.nestedScrollConnection),
            contentPadding = PaddingValues(top = pad.calculateTopPadding(), bottom = LocalPageBottomInset.current + 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {

            if (total == 0 && !running) {
                item(key = "empty") {
                    // 用整块可用高度居中，避免空态被顶到标题栏上面
                    Box(Modifier.fillParentMaxSize().padding(top = 12.dp)) {
                        EmptyState(
                            title = Loc.of("还没有视频任务"),
                            subtitle = Loc.of("回到「任务」页勾选课程并点「开始执行」"),
                            actionText = Loc.of("去选课"),
                            onActionClick = { controller.switchTab(0) },
                            modifier = Modifier.align(Alignment.Center),
                        )
                    }
                }
                return@LazyColumn
            }

            item(key = "status") {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, top = 2.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier
                                .size(8.dp)
                                .clip(RoundedCornerShape(percent = 50))
                                .background(dotColor),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            title,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium,
                            color = scheme.labelPrimary,
                            modifier = Modifier.weight(1f),
                        )
                        Text("$percent%", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = dotColor)
                    }
                    Text(
                        subtitle,
                        fontSize = 12.sp,
                        color = scheme.labelSecondaryVariant,
                        modifier = Modifier.padding(start = 16.dp, top = 4.dp),
                    )
                    Spacer(Modifier.height(10.dp))
                    LinearProgressIndicator(
                        progress = if (total > 0) done.toFloat() / total else 0f,
                        modifier = Modifier.fillMaxWidth(),
                        height = 6.dp,
                        colors = ProgressIndicatorDefaults.progressIndicatorColors(
                            foregroundColor = dotColor,
                            backgroundColor = scheme.cardPressed,
                        ),
                    )
                }
            }

            item(key = "quiz") {
                Box(Modifier.padding(horizontal = 16.dp)) {
                    CxCard(cornerRadius = 18.dp, insideMargin = 14.dp, onClick = onOpenQuiz) {
                        Column(Modifier.fillMaxWidth()) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    Loc.of("答题进度"),
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = scheme.labelPrimary,
                                    modifier = Modifier.weight(1f),
                                )
                                Text(
                                    if (controller.quizTotal > 0) "${controller.quizDone}/${controller.quizTotal}" else "—",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (controller.quizTotal > 0 && controller.quizDone >= controller.quizTotal)
                                        Color(0xFF22C55E) else scheme.primary,
                                )
                            }
                            Text(
                                "已填 ${controller.answers} 题 · " +
                                    if (controller.autoSubmit) "自动提交已开启" else "只保存不提交",
                                fontSize = 12.sp,
                                color = scheme.labelSecondaryVariant,
                                modifier = Modifier.padding(top = 4.dp),
                            )
                            LinearProgressIndicator(
                                progress = if (controller.quizTotal > 0)
                                    controller.quizDone.toFloat() / controller.quizTotal else 0f,
                                modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                                height = 5.dp,
                                colors = ProgressIndicatorDefaults.progressIndicatorColors(
                                    foregroundColor = scheme.primary,
                                    backgroundColor = scheme.cardPressed,
                                ),
                            )
                        }
                    }
                }
            }

            item(key = "title") { Section(Loc.of("视频任务点")) }

            items(groups, key = { it.name }) { group ->
                Box(Modifier.padding(horizontal = 16.dp)) {
                    VideoCard(group, tick, controller.speed.toDouble(), onOpenFailures)
                }
            }
        }
    }
}

@Composable
private fun VideoCard(group: VGroup, tick: Long, speed: Double, onOpenFailures: () -> Unit) {
    val scheme = COUITheme.colorScheme
    val status = group.status
    val tint = when {
        status == "完成" -> OK_COLOR
        status == "失败" -> ERR_COLOR
        status == "播放中" -> INFO_COLOR
        status == "结算中" -> WARN_COLOR
        else -> scheme.labelSecondaryVariant
    }
    // 引擎每 700ms 才更新一次快照，播放中用 200ms 的本地计数器补间，读秒才会连续跳动
    var basePlay by remember(group.name) { mutableStateOf(group.play) }
    var baseTick by remember(group.name) { mutableStateOf(tick) }
    LaunchedEffect(group.play, status) {
        basePlay = group.play
        baseTick = tick
    }
    val duration = group.items.maxOfOrNull { it.duration } ?: 0
    val play = if (status == "播放中") {
        val elapsed = (tick - baseTick).coerceAtLeast(0) * 0.2
        (basePlay + elapsed * speed).coerceAtMost(duration.toDouble().coerceAtLeast(0.0))
    } else {
        group.play
    }
    val fraction = if (duration > 0) (play / duration).toFloat().coerceIn(0f, 1f) else group.progress

    CxCard(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = 16.dp,
        insideMargin = 14.dp,
        onClick = onOpenFailures,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                group.name,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = scheme.labelPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                status,
                fontSize = 11.sp,
                color = tint,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(tint.copy(alpha = 0.14f))
                    .padding(horizontal = 8.dp, vertical = 3.dp),
            )
        }
        Spacer(Modifier.height(10.dp))
        LinearProgressIndicator(
            progress = fraction,
            modifier = Modifier.fillMaxWidth(),
            height = 5.dp,
            colors = ProgressIndicatorDefaults.progressIndicatorColors(
                foregroundColor = tint,
                backgroundColor = scheme.cardPressed,
            ),
        )
        Row(
            Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "${clock(play)} / ${clock(duration.toDouble())}",
                fontSize = 12.sp,
                color = scheme.labelSecondaryVariant,
                modifier = Modifier.weight(1f),
            )
            val right = when {
                group.failed > 0 -> "重试 ${group.failed} 次 ›"
                group.items.size > 1 -> "共 ${group.items.size} 个任务点"
                else -> "${(fraction * 100).toInt()}%"
            }
            Text(right, fontSize = 12.sp, color = if (group.failed > 0) ERR_COLOR else tint)
        }
    }
}

/** 二级菜单：失败任务与调试信息（不直接堆在进度卡上）。 */
@Composable
fun FailuresScreen(controller: CxController, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val scheme = COUITheme.colorScheme
    val behavior = COUIScrollBehavior(rememberTopAppBarState())
    val rows = remember(controller.videos, controller.failed) {
        controller.videos.filter { it.failed() || it.detail.isNotEmpty() }
    }

    Scaffold(
        modifier = modifier,
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = Loc.of("失败详情"),
                largeTitle = Loc.of("失败详情"),
                subtitle = "共 ${rows.size} 条记录",
                scrollBehavior = behavior,
                defaultWindowInsetsPadding = false,
                color = Color.Transparent,
                showDivider = false,
                expandedHeight = 52.dp,
                largeTitleFontSize = 26.sp,
                navigationIcon = {
                    CircleIcon(icon = CxIcons.Back, size = 40, iconSize = 20, onClick = onBack)
                },
            )
        },
    ) { pad ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .overScrollVertical(),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = pad.calculateTopPadding(),
                bottom = 24.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (rows.isEmpty()) {
                item(key = "empty") {
                    Box(Modifier.fillMaxWidth().padding(top = 40.dp)) {
                        EmptyState(title = Loc.of("没有失败记录"), subtitle = Loc.of("当前所有任务点都正常"))
                    }
                }
                return@LazyColumn
            }
            items(rows, key = { it.tid }) { task ->
                CxCard(
                    modifier = Modifier.fillMaxWidth(),
                    cornerRadius = 16.dp,
                    insideMargin = 14.dp,
                    onClick = { },
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            task.name,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium,
                            color = scheme.labelPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            task.status ?: "",
                            fontSize = 11.sp,
                            color = ERR_COLOR,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(ERR_COLOR.copy(alpha = 0.14f))
                                .padding(horizontal = 8.dp, vertical = 3.dp),
                        )
                    }
                    if (task.detail.isNotEmpty()) {
                        Text(
                            task.detail,
                            fontSize = 12.sp,
                            color = scheme.labelSecondaryVariant,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                    Text(
                        "任务点 ${task.tid}",
                        fontSize = 11.sp,
                        color = scheme.labelTertiary,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
            }
        }
    }
}


private fun clock(seconds: Double): String {
    val s = seconds.toInt().coerceAtLeast(0)
    return "%02d:%02d".format(s / 60, s % 60)
}

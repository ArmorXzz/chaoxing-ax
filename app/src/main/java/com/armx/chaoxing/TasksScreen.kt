package com.armx.chaoxing

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.suqi8.coui.kmp.basic.Card
import io.github.suqi8.coui.kmp.basic.CardDefaults
import io.github.suqi8.coui.kmp.basic.CardListGroup
import io.github.suqi8.coui.kmp.basic.COUIScrollBehavior
import io.github.suqi8.coui.kmp.basic.EmptyState
import io.github.suqi8.coui.kmp.basic.Scaffold
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.basic.TopAppBar
import io.github.suqi8.coui.kmp.utils.overScrollVertical
import io.github.suqi8.coui.kmp.basic.rememberTopAppBarState
import io.github.suqi8.coui.kmp.preference.CheckboxPreference
import io.github.suqi8.coui.kmp.theme.COUITheme
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect

/** 任务页：COUI Scaffold + 大标题 + 列表（统计卡 / 课程全选 / 课程勾选）。 */
@Composable
fun TasksScreen(
    controller: CxController,
    onStart: () -> Unit,
    modifier: Modifier = Modifier,
    contentTop: Dp = 0.dp,
    listState: LazyListState = rememberLazyListState(),
) {
    val scheme = COUITheme.colorScheme

    val scrollLink = LocalCxScrollLink.current
    // 折叠进度：列表滚过的距离映射到 0..1（220dp 走完）
    DisposableEffect(Unit) { onDispose { scrollLink.fraction = 0f } }

    val barState = rememberTopAppBarState()
    val behavior = COUIScrollBehavior(barState)
    val limitPx = with(androidx.compose.ui.platform.LocalDensity.current) { 58.dp.toPx() }
    androidx.compose.runtime.SideEffect {
        if (barState.heightOffsetLimit != limitPx) barState.heightOffsetLimit = limitPx
    }
    androidx.compose.runtime.SideEffect {
        barState.heightOffset = limitPx * scrollLink.fraction.coerceIn(0f, 1f)
    }
    // 自绘的滚动比例直接喂给 COUI：nestedScroll 这条链路在部分机型上收不到增量，
    Scaffold(
        modifier = modifier,
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                    title = Loc.of("任务"),
                    subtitle = Loc.of("选择课程并启动自动化任务"),
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
            contentPadding = PaddingValues(
                top = pad.calculateTopPadding() + 6.dp,
                bottom = LocalPageBottomInset.current + 24.dp,
            ),
        ) {

            if (controller.total > 0 && controller.done == controller.total && !controller.running) {
                item(key = "complete") {
                    io.github.suqi8.coui.kmp.basic.CompleteState(
                        title = Loc.of("全部完成"),
                        subtitle = "共 ${controller.total} 个任务点都处理完了",
                        type = io.github.suqi8.coui.kmp.basic.CompleteStateType.Success,
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    )
                }
            }
            item(key = "stats") {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, top = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    StatCard(
                        label = Loc.of("课程"),
                        value = "${controller.courses.size}",
                        hint = "已选 ${controller.selected.size} 门",
                        accent = scheme.primary,
                        modifier = Modifier.weight(1f),
                    )
                    StatCard(
                        label = Loc.of("已完成"),
                        value = "${controller.done}/${controller.total}",
                        hint = if (controller.failed > 0) "失败 ${controller.failed} 个" else Loc.of("视频任务点"),
                        accent = if (controller.failed > 0) Color(0xFFE94634) else Color(0xFF22C55E),
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            item(key = "title") { Section(Loc.of("课程列表")) }

            item(key = "courses") {
                CxCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    cornerRadius = 20.dp,
                    lightRadius = 200.dp,
                    onClick = { },
                ) {
                    CardListGroup(showDividers = true) {
                        item(key = "header") {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 16.dp, end = 12.dp, top = 4.dp, bottom = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Text(
                                    "已选 ${controller.selected.size} / ${controller.courses.size}",
                                    fontSize = 12.sp,
                                    color = scheme.labelSecondaryVariant,
                                    modifier = Modifier.weight(1f),
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    CircleIcon(CxIcons.SelectAll) { controller.selectAll(true) }
                                    CircleIcon(CxIcons.SelectNone) { controller.selectAll(false) }
                                    CircleIcon(
                                        icon = CxIcons.Refresh,
                                        enabled = !controller.loadingCourses,
                                        onClick = { controller.refreshCourses() },
                                    )
                                }
                            }
                        }

                        if (controller.courses.isEmpty()) {
                            item(key = "empty") {
                                EmptyState(
                                    title = if (controller.loadingCourses) Loc.of("正在拉取课程…") else Loc.of("还没有课程"),
                                    subtitle = if (controller.loadingCourses) null else Loc.of("点「刷新」重新拉取课程列表"),
                                    actionText = if (controller.loadingCourses) null else Loc.of("刷新"),
                                    onActionClick = { controller.refreshCourses() },
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                        } else {
                            controller.courses.forEach { course ->
                                item(key = course.key()) {
                                    CheckboxPreference(
                                        title = course.title,
                                        summary = course.teacher.takeIf { it.isNotEmpty() },
                                        checked = controller.selected.contains(course.key()),
                                        onCheckedChange = { controller.toggleCourse(course.key()) },
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

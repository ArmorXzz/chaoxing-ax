package com.armx.chaoxing

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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

/** 任务页：COUI Scaffold + 大标题 + 列表（统计卡 / 课程全选 / 课程勾选）。 */
@Composable
fun TasksScreen(controller: CxController, onStart: () -> Unit, modifier: Modifier = Modifier) {
    val scheme = COUITheme.colorScheme

    Scaffold(
        modifier = modifier,
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {},
    ) { pad ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .overScrollVertical(),
            contentPadding = PaddingValues(
                top = pad.calculateTopPadding() + 4.dp,
                bottom = LocalPageBottomInset.current + 24.dp,
            ),
        ) {
            item(key = "head") { PageTitle("任务", "选择课程并启动自动化任务") }

            item(key = "stats") {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, top = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    StatCard(
                        label = "课程",
                        value = "${controller.courses.size}",
                        hint = "已选 ${controller.selected.size} 门",
                        accent = scheme.primary,
                        modifier = Modifier.weight(1f),
                    )
                    StatCard(
                        label = "已完成",
                        value = "${controller.done}/${controller.total}",
                        hint = if (controller.failed > 0) "失败 ${controller.failed} 个" else "视频任务点",
                        accent = if (controller.failed > 0) Color(0xFFE94634) else Color(0xFF22C55E),
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            item(key = "title") { Section("课程列表") }

            item(key = "courses") {
                CxCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    cornerRadius = 20.dp,
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
                                    title = if (controller.loadingCourses) "正在拉取课程…" else "还没有课程",
                                    subtitle = if (controller.loadingCourses) null else "点「刷新」重新拉取课程列表",
                                    actionText = if (controller.loadingCourses) null else "刷新",
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

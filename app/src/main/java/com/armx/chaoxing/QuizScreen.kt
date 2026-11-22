package com.armx.chaoxing

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.suqi8.coui.kmp.basic.COUIScrollBehavior
import io.github.suqi8.coui.kmp.basic.EmptyState
import io.github.suqi8.coui.kmp.basic.Scaffold
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.basic.TopAppBar
import io.github.suqi8.coui.kmp.basic.rememberTopAppBarState
import io.github.suqi8.coui.kmp.theme.COUITheme
import io.github.suqi8.coui.kmp.utils.overScrollVertical

/** 答题情况：本次运行里每个测验的作答结果。 */
@Composable
fun QuizScreen(
    controller: CxController,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = COUITheme.colorScheme
    val behavior = COUIScrollBehavior(rememberTopAppBarState())
    val recs = controller.quizRecords()
    val done = recs.count { !it.failed }
    val failed = recs.count { it.failed }

    Scaffold(
        modifier = modifier,
        containerColor = Color.Transparent,
        contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = Loc.of("答题情况"),
                subtitle = Loc.of("本次运行逐题结果"),
                navigationIcon = {
                    CircleIcon(icon = CxIcons.Back, size = 40, iconSize = 20, onClick = onBack)
                },
                scrollBehavior = behavior,
                defaultWindowInsetsPadding = false,
                color = Color.Transparent,
                showDivider = false,
            )
        },
    ) { pad ->
        if (recs.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(pad), contentAlignment = Alignment.Center) {
                EmptyState(
                    title = Loc.of("还没有作答记录"),
                    subtitle = Loc.of("回到任务页开始执行，处理过的测验会逐条记在这里"),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            return@Scaffold
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize().overScrollVertical(),
            contentPadding = PaddingValues(
                top = pad.calculateTopPadding() + 4.dp,
                bottom = LocalPageBottomInset.current + 24.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item(key = "sum") {
                Box(Modifier.padding(horizontal = 16.dp)) {
                    CxCard(cornerRadius = 18.dp, insideMargin = 14.dp) {
                        Column(Modifier.fillMaxWidth()) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    Loc.of("处理结果"),
                                    fontSize = 14.sp,
                                    color = scheme.labelPrimary,
                                    modifier = Modifier.weight(1f),
                                )
                                Text(
                                    "$done 成功 / $failed 失败",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (failed == 0) Color(0xFF22C55E) else scheme.primary,
                                )
                            }
                            Text(
                                "共 ${recs.size} 个测验 · 只保留本次运行",
                                fontSize = 12.sp,
                                color = scheme.labelSecondaryVariant,
                                modifier = Modifier.padding(top = 4.dp),
                            )
                        }
                    }
                }
            }
            recs.reversed().forEachIndexed { i, r ->
                item(key = "q$i") {
                    Box(Modifier.padding(horizontal = 16.dp)) {
                        CxCard(cornerRadius = 16.dp, insideMargin = 13.dp) {
                            Column(Modifier.fillMaxWidth()) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        r.title.ifEmpty { Loc.of("测验") },
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = scheme.labelPrimary,
                                        modifier = Modifier.weight(1f),
                                    )
                                    val (tint, label) = when {
                                        r.failed -> Color(0xFFEF4444) to "失败"
                                        r.submitted -> Color(0xFF22C55E) to Loc.of("已提交")
                                        else -> scheme.primary to Loc.of("已保存")
                                    }
                                    Text(label, fontSize = 12.sp, color = tint)
                                }
                                if (r.chapter.isNotEmpty()) {
                                    Text(
                                        r.chapter,
                                        fontSize = 11.5.sp,
                                        color = scheme.labelSecondaryVariant,
                                        modifier = Modifier.padding(top = 2.dp),
                                    )
                                }
                                Text(
                                    "填 ${r.filled}/${r.total} 题 · ${r.detail}",
                                    fontSize = 12.sp,
                                    color = scheme.labelSecondaryVariant,
                                    modifier = Modifier.padding(top = 4.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

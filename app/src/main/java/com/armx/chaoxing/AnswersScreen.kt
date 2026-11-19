package com.armx.chaoxing

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.suqi8.coui.kmp.basic.COUIScrollBehavior
import io.github.suqi8.coui.kmp.basic.Card
import io.github.suqi8.coui.kmp.basic.CardDefaults
import io.github.suqi8.coui.kmp.basic.CardListGroup
import io.github.suqi8.coui.kmp.basic.EmptyState
import io.github.suqi8.coui.kmp.basic.Icon
import io.github.suqi8.coui.kmp.basic.IconButton
import io.github.suqi8.coui.kmp.basic.Scaffold
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.basic.TopAppBar
import io.github.suqi8.coui.kmp.basic.rememberTopAppBarState
import io.github.suqi8.coui.kmp.theme.COUITheme
import io.github.suqi8.coui.kmp.utils.overScrollVertical

/** 一个章节的答案。 */
private data class AnswerChapter(val title: String, val items: List<Pair<String, String>>)

/**
 * 二级页面：内置答案库。
 * 独立 COUI Scaffold + TopAppBar（带返回），列表用 CardListGroup，行内是「题号 / 答案」。
 */
@Composable
fun AnswersScreen(
    controller: CxController,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val ctx = LocalContext.current
    val chapters = remember {
        runCatching {
            ctx.assets.open("answers_nation.txt").bufferedReader().useLines { lines ->
                val map = LinkedHashMap<String, MutableList<Pair<String, String>>>()
                lines.forEach { raw ->
                    val parts = raw.trim().split('\t')
                    if (parts.size >= 3) {
                        map.getOrPut(parts[0]) { mutableListOf() }.add(parts[1] to parts[2])
                    }
                }
                map.map { (title, list) -> AnswerChapter(title, list) }
            }
        }.getOrDefault(emptyList())
    }

    val scheme = COUITheme.colorScheme
    val behavior = COUIScrollBehavior(rememberTopAppBarState())
    val total = chapters.sumOf { it.items.size }

    Scaffold(
        modifier = modifier,
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = "内置答案库",
                largeTitle = "内置答案库",
                subtitle = "国家安全教育 · $total 题 / ${chapters.size} 章",
                scrollBehavior = behavior,
                color = Color.Transparent,
                expandedHeight = 52.dp,
                defaultWindowInsetsPadding = false,
                largeTitleFontSize = 26.sp,
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            CxIcons.Back,
                            contentDescription = "返回",
                            tint = scheme.labelPrimary,
                        )
                    }
                },
            )
        },
    ) { pad ->
        if (chapters.isEmpty()) {
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(top = pad.calculateTopPadding()),
                contentAlignment = Alignment.Center,
            ) {
                EmptyState(
                    title = "答案库是空的",
                    subtitle = "确认安装包里带了 assets/answers_nation.txt",
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(
                    top = pad.calculateTopPadding(),
                    bottom = LocalPageBottomInset.current + 24.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .overScrollVertical()
                    .nestedScroll(behavior.nestedScrollConnection),
            ) {
                items(chapters, key = { it.title }) { chapter ->
                    CxCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        cornerRadius = 20.dp,
                        onClick = { },
                    ) {
                        CardListGroup(showDividers = true) {
                            item(key = "head-${chapter.title}") {
                                Column(
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 8.dp),
                                ) {
                                    Text(
                                        chapter.title,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = scheme.labelPrimary,
                                    )
                                    Text(
                                        "${chapter.items.size} 题",
                                        fontSize = 11.sp,
                                        color = scheme.labelSecondaryVariant,
                                        modifier = Modifier.padding(top = 2.dp),
                                    )
                                }
                            }
                            chapter.items.forEach { (no, answer) ->
                                item(key = "${chapter.title}#$no") {
                                    Row(
                                        Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 16.dp, vertical = 9.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    ) {
                                        Text(
                                            "第 $no 题",
                                            fontSize = 13.sp,
                                            color = scheme.labelSecondaryVariant,
                                            modifier = Modifier.weight(1f),
                                        )
                                        Text(
                                            answer,
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = scheme.primary,
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

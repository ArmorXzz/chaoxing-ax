package com.armx.chaoxing

import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.suqi8.coui.kmp.basic.CardListGroup
import io.github.suqi8.coui.kmp.basic.Scaffold
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.basic.TopAppBar
import io.github.suqi8.coui.kmp.basic.rememberTopAppBarState
import io.github.suqi8.coui.kmp.basic.COUIScrollBehavior
import io.github.suqi8.coui.kmp.layout.DialogButtonBar
import io.github.suqi8.coui.kmp.layout.DialogButtonBarAction
import io.github.suqi8.coui.kmp.overlay.OverlayDialog
import io.github.suqi8.coui.kmp.preference.PreferenceCategory
import io.github.suqi8.coui.kmp.theme.COUITheme
import io.github.suqi8.coui.kmp.utils.overScrollVertical
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 关于页：应用信息 + 更新检查 + 协议/说明/开源。 */
@Composable
fun AboutScreen(
    controller: CxController,
    onBack: () -> Unit,
    onOpenUpdate: () -> Unit,
    onReplayGuide: () -> Unit,
    modifier: Modifier = Modifier,
    listState: LazyListState = rememberLazyListState(),
) {
    val scheme = COUITheme.colorScheme
    val scope = rememberCoroutineScope()
    val ctx = controller.app

    var dialog by remember { mutableStateOf(0) }   // 1 协议 2 说明 3 开源 4 更新
    var checking by remember { mutableStateOf(false) }
    var info by remember { mutableStateOf<Update.Info?>(null) }
    var failed by remember { mutableStateOf("") }

    val behavior = COUIScrollBehavior(rememberTopAppBarState())

    Scaffold(
        modifier = modifier,
        containerColor = Color.Transparent,
        contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = Loc.of("关于"),
                subtitle = Loc.of("版本、更新与声明"),
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
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().overScrollVertical()
                .nestedScroll(behavior.nestedScrollConnection),
            contentPadding = PaddingValues(
                top = pad.calculateTopPadding() + 4.dp,
                bottom = LocalPageBottomInset.current + 24.dp,
            ),
        ) {
            item {
                PreferenceCategory(title = Loc.of("应用"))
                CxCard(modifier = Modifier.padding(horizontal = 16.dp), cornerRadius = 22.dp) {
                    Column(
                        Modifier.fillMaxWidth().padding(vertical = 22.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        val bmp = controller.appIcon
                        if (bmp != null) {
                            Image(
                                bitmap = bmp.asImageBitmap(),
                                contentDescription = null,
                                contentScale = ContentScale.Fit,
                                modifier = Modifier.size(76.dp).clipToBounds2(18),
                            )
                        }
                        Spacer(Modifier.height(10.dp))
                        Text("超星AX", color = scheme.labelPrimary, fontSize = 26.sp, fontWeight = FontWeight.SemiBold)
                        Text(
                            "v${BuildInfo.VERSION}（${BuildInfo.VERSION_CODE}）",
                            color = scheme.labelSecondaryVariant,
                            fontSize = 13.sp,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            Loc.of("课程视频、文档与测验的本地自动化客户端"),
                            color = scheme.labelSecondaryVariant,
                            fontSize = 12.sp,
                        )
                    }
                }
            }

            item {
                PreferenceCategory(title = Loc.of("更新"))
                CxCard(modifier = Modifier.padding(horizontal = 16.dp), cornerRadius = 20.dp) {
                    CardListGroup {
                        item {
                            AboutRow(
                                title = if (checking) Loc.of("正在检查更新…") else Loc.of("检查更新"),
                                summary = when {
                                    failed.isNotEmpty() -> "检查失败：$failed"
                                    info != null -> "发现新版本 ${info!!.tag}"
                                    else -> "当前 v${BuildInfo.VERSION} · 从 GitHub 获取最新版本"
                                },
                                onClick = onOpenUpdate,
                            )
                        }
                        item { AboutRow(Loc.of("下载页"), Loc.of("在浏览器里打开 GitHub Releases"), onClick = { Update.open(ctx, "https://github.com/ArmorXzz/chaoxing-ax/releases/latest") }) }
                        item { AboutRow(Loc.of("开源仓库"), "ArmorXzz/chaoxing-ax", onClick = { Update.open(ctx, "https://github.com/ArmorXzz/chaoxing-ax") }) }
                    }
                }
            }

            item {
                PreferenceCategory(title = Loc.of("说明"))
                CxCard(modifier = Modifier.padding(horizontal = 16.dp), cornerRadius = 20.dp) {
                    CardListGroup {
                        item { AboutRow(Loc.of("使用协议"), Loc.of("首次启动时同意的五条"), onClick = { dialog = 1 }) }
                        item { AboutRow(Loc.of("用户说明"), Loc.of("怎么用、注意什么"), onClick = { dialog = 2 }) }
                        item { AboutRow(Loc.of("数据存储"), Loc.of("账号、Key、日志都只在本机"), onClick = { dialog = 2 }) }
                        item { AboutRow(Loc.of("重新阅读使用协议"), Loc.of("再次查看首次启动时的条款"), onClick = { dialog = 1 }) }
                        item { AboutRow(Loc.of("重看新手引导"), Loc.of("再走一遍三页引导"), onClick = onReplayGuide) }
                    }
                }
            }

            item {
                PreferenceCategory(title = Loc.of("开源"))
                CxCard(modifier = Modifier.padding(horizontal = 16.dp), cornerRadius = 20.dp) {
                    CardListGroup {
                        item { AboutRow("COUI", "Apache-2.0 · suqi8/coui", onClick = { Update.open(ctx, "https://github.com/suqi8/coui") }) }
                        item { AboutRow("Jetpack Compose", "Apache-2.0 · Google", onClick = { Update.open(ctx, "https://developer.android.com/jetpack/compose") }) }
                        item { AboutRow("jsoup", "MIT · Jonathan Hedley", onClick = { Update.open(ctx, "https://jsoup.org") }) }
                        item { AboutRow(Loc.of("第三方与许可"), Loc.of("查看全部声明"), onClick = { dialog = 3 }) }
                    }
                }
            }
        }
    }

    // ---- 弹层 ----
    if (dialog == 1) {
        io.github.suqi8.coui.kmp.overlay.OverlayUserStatementDialog(
            show = true,
            onConfirm = { dialog = 0 },
            onExit = { dialog = 0 },
            logo = controller.appIcon?.let {
                androidx.compose.ui.graphics.painter.BitmapPainter(it.asImageBitmap())
            },
            title = Loc.of("使用协议"),
            appName = "超星AX",
            appMessage = Loc.of("首次启动时同意的五条"),
            statement = buildAnnotatedString { append(Loc.of("以下为你在首次启动时同意的条款。")) },
            protocolText = agreementText2(),
            confirmText = Loc.of("知道了"),
            exitText = Loc.of("关闭"),
            backgroundColor = Color.Transparent,
            materialStroke = true,
            enableWindowDim = true,
        )
    }

    if (dialog == 2 || dialog == 3) {
        val title = when (dialog) {
            2 -> Loc.of("用户说明")
            else -> Loc.of("第三方与许可")
        }
        val body = when (dialog) {
            2 -> helpText()
            else -> licenseText()
        }
        OverlayDialog(show = true, title = title, onDismissRequest = { dialog = 0 }) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .height(320.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                BasicText(
                    body,
                    style = COUITheme.textStyles.bodyS.copy(color = scheme.labelSecondary),
                )
            }
        }
    }

    if (dialog == 4 && info != null) {
        val i = info!!
        OverlayDialog(
            show = true,
            title = "发现新版本 ${i.tag}",
            summary = if (i.size > 0) "安装包 ${i.size / 1024 / 1024} MB · 当前 v${BuildInfo.VERSION}" else "当前 v${BuildInfo.VERSION}",
            onDismissRequest = { dialog = 0 },
        ) {
            Column(
                Modifier.fillMaxWidth().height(260.dp).verticalScroll(rememberScrollState()),
            ) {
                BasicText(
                    i.notes.ifEmpty { Loc.of("这个版本没有写更新说明。") },
                    style = COUITheme.textStyles.bodyS.copy(color = scheme.labelSecondary),
                )
            }
            Spacer(Modifier.height(12.dp))
            DialogButtonBar(
                negative = DialogButtonBarAction(text = Loc.of("稍后"), onClick = { dialog = 0 }),
                positive = DialogButtonBarAction(text = Loc.of("去下载"), onClick = {
                    dialog = 0
                    Update.open(ctx, i.url)
                }),
            )
        }
    }
}

private fun Modifier.clipToBounds2(radius: Int): Modifier =
    this.clip(androidx.compose.foundation.shape.RoundedCornerShape(radius.dp))

@Composable
private fun AboutRow(title: String, summary: String, onClick: () -> Unit) {
    val scheme = COUITheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = scheme.labelPrimary, fontSize = 16.sp)
            Text(summary, color = scheme.labelSecondaryVariant, fontSize = 12.5.sp)
        }
        CircleIcon(icon = CxIcons.Chevron, size = 30, iconSize = 16, onClick = onClick)
    }
}

private fun helpText() = buildAnnotatedString {
    withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append(Loc.of("怎么用\n")) }
    append(Loc.of("1. 登录后到「任务」页勾选课程，点「开始执行」。\n"))
    append(Loc.of("2. 进度页按视频合并任务点，失败的点可以点进去看原始报错。\n"))
    append(Loc.of("3. 设置里可以调模糊、并发、倍速，也能开 AI 答题。\n\n"))
    withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append(Loc.of("注意\n")) }
    append(Loc.of("· 只支持学习通课程，其他平台不保证。\n"))
    append(Loc.of("· 自动作答默认只保存不提交，提交前请自己核对。\n"))
    append(Loc.of("· 崩溃日志在 Android/data/com.armx.chaoxing/files/boot.log。"))
}

private fun licenseText() = buildAnnotatedString {
    append("COUI（ColorOS UI for Compose）· Apache-2.0\nhttps://github.com/suqi8/coui\n\n")
    append("Jetpack Compose · Apache-2.0\nhttps://developer.android.com/jetpack/compose\n\n")
    append("jsoup · MIT\nhttps://jsoup.org\n\n")
    append(Loc.of("壁纸来源：t.alcy.cc、loliapi.com 的公开接口，版权归原作者。\n\n"))
    append(Loc.of("本应用按「现状」提供，不含任何形式的担保。"))
}

private fun agreementText2() = buildAnnotatedString {
    append(Loc.of("一、本应用仅用于作者本人的课程学习辅助，使用者应自行确认所在学校的学习规范。\n\n"))
    append(Loc.of("二、账号信息、DeepSeek API Key、运行日志全部只保存在本机，不会上传到任何第三方服务器；随机壁纸会向公开图库发起请求。\n\n"))
    append(Loc.of("三、自动播放、自动阅读、自动作答可能违反课程平台的使用条款，由此产生的后果由使用者自行承担。\n\n"))
    append(Loc.of("四、内置题库仅覆盖「国家安全教育」的公开章节，AI 作答不保证正确，提交前请自行核对。\n\n"))
    append(Loc.of("五、本应用按「现状」提供，不提供任何形式的担保。"))
}

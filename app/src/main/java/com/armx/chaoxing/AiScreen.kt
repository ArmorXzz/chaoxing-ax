package com.armx.chaoxing

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.suqi8.coui.kmp.basic.Button
import io.github.suqi8.coui.kmp.basic.ButtonDefaults
import io.github.suqi8.coui.kmp.basic.CardListGroup
import io.github.suqi8.coui.kmp.basic.Scaffold
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.basic.TextField
import io.github.suqi8.coui.kmp.basic.TopAppBar
import io.github.suqi8.coui.kmp.preference.PreferenceCategory
import io.github.suqi8.coui.kmp.preference.SwitchPreference
import io.github.suqi8.coui.kmp.theme.COUITheme

/** AI 答题配置：DeepSeek Key、模型、连通性测试。 */
@Composable
fun AiScreen(
    controller: CxController,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    listState: LazyListState = rememberLazyListState(),
) {
    val scheme = COUITheme.colorScheme
    var key by remember { mutableStateOf(controller.aiKey) }
    var bankKey by remember { mutableStateOf(controller.bankKey) }

    Scaffold(
        modifier = modifier,
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = Loc.of("AI 答题"),
                subtitle = Loc.of("使用Deepseek对题库未覆盖的任务作答"),
                navigationIcon = {
                    CircleIcon(icon = CxIcons.Back, size = 40, iconSize = 20, onClick = onBack)
                },
                defaultWindowInsetsPadding = false,
                color = Color.Transparent,
                showDivider = false,
            )
        },
    ) { pad ->
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = pad.calculateTopPadding() + 8.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            item {
                PreferenceCategory(title = Loc.of("开关"))
                AiGroup {
                    SwitchPreference(
                        checked = controller.aiOn,
                        onCheckedChange = { controller.applyAiOn(it) },
                        title = Loc.of("AI 自动答题"),
                        summary = Loc.of("题库里没有的题交给 DeepSeek 作答"),
                    )
                }
            }
            item {
                PreferenceCategory(title = Loc.of("接口"))
                AiGroup {
                    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
                        Text("API Key", color = scheme.labelSecondaryVariant, fontSize = 12.sp)
                        TextField(
                            value = key,
                            onValueChange = { key = it },
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        )
                        Text(
                            Loc.of("只保存在本机 SharedPreferences，不会上传到别处。"),
                            color = scheme.labelSecondaryVariant,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(top = 6.dp),
                        )
                    }
                    CxRowLight {
                        CxArrowRow(
                            title = Loc.of("测试连接"),
                            summary = Loc.of("发一句最小请求，确认 Key 与模型可用"),
                            onClick = {
                                controller.applyAiKey(key)
                                controller.testAi()
                            },
                        )
                    }
                    CxRowLight {
                        CxArrowRow(
                            title = Loc.of("保存"),
                            summary = Loc.of("保存 Key 到本机"),
                            onClick = {
                                controller.applyAiKey(key)
                                controller.toast(Loc.of("AI 配置已保存"))
                            },
                        )
                    }
                }
            }
            item {
                PreferenceCategory(title = Loc.of("远程题库（可选）"))
                AiGroup {
                    SwitchPreference(
                        checked = controller.bankOn,
                        onCheckedChange = { controller.applyBankOn(it) },
                        title = Loc.of("题库海精确搜索"),
                        summary = Loc.of("本地题库与 AI 都没覆盖时，向题库接口按题干精确检索"),
                    )
                    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
                        Text(Loc.of("题库 Key（卡密）"), color = scheme.labelSecondaryVariant, fontSize = 12.sp)
                        TextField(
                            value = bankKey,
                            onValueChange = { bankKey = it },
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        )
                        Text(
                            Loc.of("请求只发送题干与选项文本，不带账号信息；Key 只保存在本机。"),
                            color = scheme.labelSecondaryVariant,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(top = 6.dp),
                        )
                    }
                    CxRowLight {
                        CxArrowRow(
                            title = Loc.of("测试题库"),
                            summary = Loc.of("发一条最小检索，确认 Key 与额度"),
                            onClick = {
                                controller.applyBankKey(bankKey)
                                controller.testBank()
                            },
                        )
                    }
                    CxRowLight {
                        CxArrowRow(
                            title = Loc.of("保存题库 Key"),
                            summary = Loc.of("保存到本机，刷课时自动使用"),
                            onClick = {
                                controller.applyBankKey(bankKey)
                                controller.toast(Loc.of("题库配置已保存"))
                            },
                        )
                    }
                }
            }
            item {
                PreferenceCategory(title = Loc.of("说明"))
                AiGroup {
                    Text(
                        Loc.of("1. 需要打开「AI 自动答题」才会在刷课时生效。\n") +
                            Loc.of("2. 一次测验只发一次请求，题目按顺序编号；模型给出的字母还会按选项文本再校验一次。\n") +
                            Loc.of("3. 仍然只保存不提交，判分由你在页面上完成。\n") +
                            Loc.of("4. Key 失效或网络异常时自动跳过该测验，不会卡住其它任务。"),
                        color = scheme.labelSecondaryVariant,
                        fontSize = 12.sp,
                        lineHeight = 18.sp,
                        modifier = Modifier.padding(16.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun AiGroup(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        CxCard(cornerRadius = 20.dp, insideMargin = 0.dp) {
            CardListGroup(showDividers = true) {
                item(key = "ai-group") { content() }
            }
        }
    }
}
package com.armx.chaoxing
import io.github.suqi8.coui.kmp.basic.ButtonDefaults
import io.github.suqi8.coui.kmp.basic.Button
import androidx.compose.foundation.layout.Row

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.suqi8.coui.kmp.basic.COUIScrollBehavior
import io.github.suqi8.coui.kmp.basic.Card
import io.github.suqi8.coui.kmp.basic.CardListGroup
import io.github.suqi8.coui.kmp.basic.Scaffold
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.basic.TopAppBar
import io.github.suqi8.coui.kmp.utils.overScrollVertical
import io.github.suqi8.coui.kmp.basic.rememberTopAppBarState
import io.github.suqi8.coui.kmp.preference.ArrowPreference
import io.github.suqi8.coui.kmp.preference.OverlayDropdownPreference
import io.github.suqi8.coui.kmp.preference.PagerFooterPreference
import io.github.suqi8.coui.kmp.preference.PreferenceCategory
import io.github.suqi8.coui.kmp.preference.SliderPreference
import io.github.suqi8.coui.kmp.preference.SwitchPreference
import io.github.suqi8.coui.kmp.basic.SwitchColors
import io.github.suqi8.coui.kmp.theme.COUITheme
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import io.github.suqi8.coui.kmp.basic.TextField
import io.github.suqi8.coui.kmp.overlay.OverlayDialog
import io.github.suqi8.coui.kmp.layout.DialogButtonBar
import io.github.suqi8.coui.kmp.layout.DialogButtonBarAction
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect

/** 设置页：COUI PreferenceCategory + Card + CardListGroup + 各 Preference。 */
@Composable
fun SettingsScreen(
    controller: CxController,
    onPickBg: () -> Unit,
    onOpenNotif: () -> Unit,
    onOpenBattery: () -> Unit,
    onLogout: () -> Unit,
    onSwitchAccount: () -> Unit,
    onOpenAbout: () -> Unit,
    onOpenAnswers: () -> Unit,
    onOpenAi: () -> Unit,
    modifier: Modifier = Modifier,
    contentTop: Dp = 0.dp,
    listState: LazyListState = rememberLazyListState(),
) {
    var speed by remember { mutableFloatStateOf(controller.speed) }
    var parallel by remember { mutableIntStateOf(controller.parallel) }

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
                    title = Loc.of("设置"),
                    subtitle = Loc.of("调整运行参数与界面外观"),
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
            item { SettingsGroup(Loc.of("外观")) { SettingsAppearance(controller, onPickBg) } }
            item { SettingsGroup(Loc.of("后台")) { SettingsBackground(controller, onOpenNotif, onOpenBattery) } }
            item {
                SettingsGroup(Loc.of("刷课参数")) {
                    CardListGroup {
                        item {
                            SliderPreference(
                                value = speed,
                                onValueChange = { speed = it },
                                title = Loc.of("视频播放倍速"),
                                valueRange = 1f..2f,
                                steps = 9,
                                valueText = String.format("%.1fx", speed),
                            )
                        }
                        item {
                            SliderPreference(
                                value = parallel.toFloat(),
                                onValueChange = { parallel = it.toInt() },
                                title = Loc.of("最大并发数"),
                                valueRange = 0f..30f,
                                valueText = if (parallel == 0) Loc.of("全部") else "$parallel",
                            )
                        }
                        item {
                            CxRowLight {
                                CxArrowRow(
                                    title = Loc.of("保存运行参数"),
                                    summary = Loc.of("倍速与并发在下次开始任务时生效"),
                                    onClick = { controller.saveRunning(speed, parallel) },
                                )
                            }
                        }
                    }
                }
            }
            item {
                SettingsGroup(Loc.of("AI 答题（DeepSeek）")) {
                    CardListGroup {
                        item {
                            SwitchPreference(
                                checked = controller.aiOn,
                                onCheckedChange = { controller.applyAiOn(it) },
                                title = Loc.of("AI 自动答题"),
                                summary = if (controller.aiOn) Loc.of("题库没覆盖的题交给 DeepSeek") else Loc.of("关闭：只作答内置题库的题目"),
                            )
                        }
                        item {
                            CxRowLight {
                                CxArrowRow(
                                    title = Loc.of("接口配置"),
                                    summary = if (controller.aiKey.isEmpty()) Loc.of("尚未填写 API Key")
                                    else Loc.of("已配置 · ") + controller.aiModel,
                                    onClick = onOpenAi,
                                )
                            }
                        }
                    }
                }
            }
            item {
                SettingsGroup(Loc.of("答题")) {
                    CardListGroup {
                        item {
                            SwitchPreference(
                                checked = controller.autoSubmit,
                                onCheckedChange = { controller.applyAutoSubmit(it) },
                                title = Loc.of("自动提交作答答案"),
                                summary = if (controller.autoSubmit) Loc.of("填完直接提交判分") else Loc.of("只保存，不提交判分"),
                            )
                        }
                        item {
                            CxRowLight {
                                CxArrowRow(
                                    title = Loc.of("内置答案库"),
                                    summary = Loc.of("仅用于「国家安全教育」，按章节顺序自动填写并保存 · ") +
                                        if (controller.answers > 0) "${controller.answers} 题" else Loc.of("暂无"),
                                    onClick = onOpenAnswers,
                                )
                            }
                        }
                    }
                }
            }
            item {
                SettingsGroup(Loc.of("账号")) {
                    CardListGroup {
                        item {
                            CxRowLight {
                                CxArrowRow(
                                    title = Loc.of("切换账号"),
                                    summary = if (controller.logged) "${controller.user} · 共 ${controller.savedAccounts().size} 个已保存账号"
                                    else Loc.of("还没有保存过账号"),
                                    onClick = onSwitchAccount,
                                )
                            }
                        }
                        item {
                            Row(
                                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(Loc.of("退出登录"), color = Color(0xFFE5484D), fontSize = 16.sp)
                                    Text(
                                        Loc.of("需要重新输入账号密码"),
                                        color = COUITheme.colorScheme.labelSecondaryVariant,
                                        fontSize = 12.5.sp,
                                    )
                                }
                                Button(
                                    onClick = onLogout,
                                    minHeight = 30.dp,
                                    insideMargin = PaddingValues(horizontal = 14.dp, vertical = 5.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        color = Color(0xFFE5484D),
                                        contentColor = Color.White,
                                    ),
                                ) { Text(Loc.of("退出")) }
                            }
                        }
                    }
                }
            }
            item {
                androidx.compose.foundation.layout.Box(
                    Modifier
                        .fillMaxSize()
                        .padding(top = 10.dp, bottom = 8.dp),
                ) {
                    CxRowLight {
                CxArrowRow(
                    title = Loc.of("关于超星AX"),
                    summary = "版本 ${BuildInfo.VERSION}（${BuildInfo.VERSION_CODE}）· 使用协议与开源许可",
                    onClick = onOpenAbout,
                )
            }
                }
            }
        }
    }
}

@Composable
private fun SettingsAppearance(controller: CxController, onPickBg: () -> Unit) {
    val themes = listOf(Loc.of("跟随系统"), Loc.of("浅色"), Loc.of("深色"))
    val langs = listOf(Loc.of("简体中文"), Loc.of("繁體中文"), "English", Loc.of("日本語"))
    val walls = listOf(Loc.of("夏花"), Loc.of("魔王"), Loc.of("自定义"), Loc.of("随机壁纸"))
    CardListGroup {
        item {
            CxNoLightBox {
            OverlayDropdownPreference(
                items = langs,
                selectedIndex = controller.lang,
                title = Loc.of("语言"),
                onSelectedIndexChange = { controller.applyLang(it) },
            )
            }
        }
        item {
            CxNoLightBox {
            OverlayDropdownPreference(
                items = themes,
                selectedIndex = controller.theme,
                title = Loc.of("主题"),
                onSelectedIndexChange = { controller.applyTheme(it) },
            )
            }
        }
        item {
            CxNoLightBox {
            OverlayDropdownPreference(
                items = walls,
                selectedIndex = controller.wallpaper,
                title = Loc.of("背景图"),
                onSelectedIndexChange = { index ->
                    if (index == 2) onPickBg() else controller.applyWallpaper(index)
                },
            )
            }
        }
        item {
            SliderPreference(
                value = controller.blur.toFloat(),
                onValueChange = { controller.applyBlur(it.toInt()) },
                title = Loc.of("背景模糊"),
                valueRange = 0f..50f,
                steps = 0,
                valueText = "${controller.blur}",
            )
        }
    }

}

@Composable
private fun SettingsBackground(controller: CxController, onOpenNotif: () -> Unit, onOpenBattery: () -> Unit) {
    val scheme = COUITheme.colorScheme
    CardListGroup {
        item {
            SwitchPreference(
                switchColors = SwitchColors(
                    checkedThumbColor = Color.White,
                    uncheckedThumbColor = Color.White,
                    disabledCheckedThumbColor = Color.White.copy(alpha = 0.6f),
                    disabledUncheckedThumbColor = Color.White.copy(alpha = 0.6f),
                    checkedTrackColor = scheme.primary,
                    uncheckedTrackColor = cxSurface(scheme.cardPressed, 0.62f),
                    disabledCheckedTrackColor = scheme.primary.copy(alpha = 0.4f),
                    disabledUncheckedTrackColor = scheme.cardPressed.copy(alpha = 0.4f),
                ),
                title = Loc.of("自动登录"),
                summary = Loc.of("启动时用上次的登录状态直接进入"),
                checked = controller.autoLogin,
                onCheckedChange = { controller.applyAutoLogin(it) },
            )
        }
        item {
            CxRowLight {
                CxArrowRow(
                    title = Loc.of("后台保活"),
                    summary = Loc.of("把本应用加入电池优化白名单，降低被杀概率"),
                    onClick = onOpenBattery,
                )
            }
        }
        item {
            CxRowLight {
                CxArrowRow(
                    title = Loc.of("刷课进度通知"),
                    summary = if (controller.notifEnabled) Loc.of("已开启 · 仅刷课时显示") else Loc.of("未开启 · 点击前往系统设置"),
                    onClick = onOpenNotif,
                )
            }
        }
    }
}

@Composable
private fun SettingsGroup(title: String, content: @Composable () -> Unit) {
    androidx.compose.foundation.layout.Column {
        PreferenceCategory(title = title)
        CxCard(
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .padding(bottom = 4.dp),
            cornerRadius = 20.dp,
        ) {
            content()
        }
    }
}

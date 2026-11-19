package com.armx.chaoxing

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
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

/** 设置页：COUI PreferenceCategory + Card + CardListGroup + 各 Preference。 */
@Composable
fun SettingsScreen(
    controller: CxController,
    onPickBg: () -> Unit,
    onOpenNotif: () -> Unit,
    onOpenBattery: () -> Unit,
    onLogout: () -> Unit,
    onOpenAnswers: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var speed by remember { mutableFloatStateOf(controller.speed) }
    var parallel by remember { mutableIntStateOf(controller.parallel) }

    Scaffold(
        modifier = modifier,
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {},
    ) { pad ->
        LazyColumn(
            contentPadding = PaddingValues(
                top = pad.calculateTopPadding() + 4.dp,
                bottom = LocalPageBottomInset.current + 24.dp,
            ),
            modifier = Modifier
                .fillMaxSize()
                .overScrollVertical(),
        ) {
            item { PageTitle("设置", "调整运行参数与界面外观") }
            item { SettingsGroup("外观") { SettingsAppearance(controller, onPickBg) } }
            item { SettingsGroup("后台") { SettingsBackground(controller, onOpenNotif, onOpenBattery) } }
            item {
                SettingsGroup("刷课参数") {
                    CardListGroup {
                        item {
                            SliderPreference(
                                value = speed,
                                onValueChange = { speed = it },
                                title = "视频播放倍速",
                                valueRange = 1f..2f,
                                steps = 9,
                                valueText = String.format("%.1fx", speed),
                            )
                        }
                        item {
                            SliderPreference(
                                value = parallel.toFloat(),
                                onValueChange = { parallel = it.toInt() },
                                title = "最大并发数",
                                valueRange = 0f..30f,
                                valueText = if (parallel == 0) "全部" else "$parallel",
                            )
                        }
                        item {
                            ArrowPreference(
                                title = "保存运行参数",
                                summary = "倍速与并发在下次开始任务时生效",
                                onClick = { controller.saveRunning(speed, parallel) },
                            )
                        }
                    }
                }
            }
            item {
                SettingsGroup("答题") {
                    CardListGroup {
                        item {
                            ArrowPreference(
                                title = "内置答案库",
                                summary = "仅用于「国家安全教育」，按章节顺序自动填写并保存 · " +
                                    if (controller.answers > 0) "${controller.answers} 题" else "未内置",
                                onClick = onOpenAnswers,
                            )
                        }
                    }
                }
            }
            item {
                SettingsGroup("账号") {
                    CardListGroup {
                        item { ArrowPreference(title = "退出登录", summary = "需要重新输入账号密码", onClick = onLogout) }
                    }
                }
            }
            item {
                androidx.compose.foundation.layout.Box(
                    Modifier
                        .fillMaxSize()
                        .padding(top = 10.dp, bottom = 8.dp),
                ) {
                    PagerFooterPreference("超星AX · v${BuildInfo.VERSION}（${BuildInfo.VERSION_CODE}）\n构建于 ${BuildInfo.BUILD_TIME}\n所有数据仅保存在本机")
                }
            }
        }
    }
}

@Composable
private fun SettingsAppearance(controller: CxController, onPickBg: () -> Unit) {
    CardListGroup {
        item {
            OverlayDropdownPreference(
                items = listOf("跟随系统", "浅色", "深色"),
                selectedIndex = controller.theme,
                title = "主题",
                onSelectedIndexChange = { controller.applyTheme(it) },
            )
        }
        item {
            OverlayDropdownPreference(
                items = listOf("夏花", "魔王", "自定义"),
                selectedIndex = controller.wallpaper,
                title = "背景图",
                onSelectedIndexChange = { index ->
                    if (index == 2) onPickBg() else controller.applyWallpaper(index)
                },
            )
        }
        item {
            SliderPreference(
                value = controller.blur.toFloat(),
                onValueChange = { controller.applyBlur(it.toInt()) },
                title = "背景模糊",
                valueRange = if (android.os.Build.VERSION.SDK_INT >= 37) 0f..5f else 0f..50f,
                steps = if (android.os.Build.VERSION.SDK_INT >= 37) 4 else 0,
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
                    uncheckedTrackColor = scheme.cardPressed,
                    disabledCheckedTrackColor = scheme.primary.copy(alpha = 0.4f),
                    disabledUncheckedTrackColor = scheme.cardPressed.copy(alpha = 0.4f),
                ),
                title = "自动登录",
                summary = "启动时用上次的登录状态直接进入",
                checked = controller.autoLogin,
                onCheckedChange = { controller.applyAutoLogin(it) },
            )
        }
        item {
            ArrowPreference(
                title = "后台保活",
                summary = "把本应用加入电池优化白名单，降低被杀概率",
                onClick = onOpenBattery,
            )
        }
        item {
            ArrowPreference(
                title = "刷课进度通知",
                summary = if (controller.notifEnabled) "已开启 · 仅刷课时显示" else "未开启 · 点击前往系统设置",
                onClick = onOpenNotif,
            )
        }
    }
}

@Composable
private fun SettingsGroup(title: String, content: @Composable () -> Unit) {
    androidx.compose.foundation.layout.Column {
        PreferenceCategory(title = title)
        Card(
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .padding(bottom = 4.dp),
        ) {
            content()
        }
    }
}

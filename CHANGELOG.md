# CHANGELOG

只记功能性变更，界面微调与文案不计。

## 8.1.6 — 136

- `Engine.doWork`：提交前记录 `submit` 与 `pyFlag`，此前日志无法区分「提交」与「只保存」
- `Engine.checkSubmitted`：提交后回读页面，统计 `已完成 / 待批阅 / 查看作答 / btnSubmit / answerCard`
- 修正 `workNote`：原先无论 `pyFlag` 取值都写「还没提交」，改为按 `submit` 分支

## 8.1.5 — 135

- 修 `TasksScreen` / `ProgressScreen` / `SettingsScreen` 首帧崩溃：`TopAppBarState.heightOffsetLimit` 被写成正值，`setHeightOffset` 的 `coerceIn(-limit, 0)` 区间倒置
- 修 `UpdateScreen` 版本比较：`versionCode`（133）与 tag 语义版本（80104）不同量纲，改为比较 `Update.code(BuildInfo.VERSION)`

## 8.1.4 — 133

- `Loc` 词条扩到 223 条，覆盖标题、按钮、开关说明、空态、弹窗、Toast
- `魔王` 英文按用户要求改为 `BOSS`，`夏花` 取官方罗马字 `Nazuka` / `ナツカ`

## 8.1.3 — 132

- 新增 `Loc`：简体 / 繁體 / English / 日本語
- 繁體按字表逐字转换，EN / JA 走词条表，未命中回落简体

## 8.1.2 — 131

- 语言切换入口（设置 → 外观）
- `Loc.INSTANCE` 的调用注意：Java 侧访问 Kotlin object 需显式 `INSTANCE`

## 8.1.1 — 130

- `Work.cleanStem` / `cleanOption`：去题型标签、题号前缀、选项字母前缀
- `AnswerBank.find` 增 Dice 二元组相似度兜底（阈值 0.82，唯一最优才用）
- 新增 `RemoteBank`：题库海精确检索，只补空题，结果多条时不填

## 8.1.0 — 129

- 新增 `UpdateScreen`：查 GitHub Releases，比较语义版本
- 新增 `QuizScreen` + `Engine.QuizRec`：逐题结果留痕
- 设置页拆出「切换账号」（`OverlaySingleChoiceDialog`）与红色「退出」
- 折叠标题改为手动驱动 `TopAppBarState`

## 8.0.9 — 128

- 填空字段修正：`answerEditor{id}{n}` + `tiankongsize{id}`，替代 `answer{id}` / `blank{id}`
- `AboutScreen` 重做：应用卡、检查更新、协议与开源
- 新增 `Update`

## 8.0.4 — 123

- Toast 统一为 `SnackbarHost`
- 首次启动走 `OverlayUserStatementDialog`，新手指引走 `OverlayGuideDialog`（`Window*` 版本在独立 window 里没有 `NavigationEventDispatcherOwner`，会崩）
- 任务页全部完成时显示 `CompleteState`

# 超星AX

超星学习通任务点的 Android 客户端。逻辑层是原生 Java，界面用 Jetpack Compose + [COUI](https://github.com/suqi8/coui)（ColorOS 17 风格组件）。

> 仅供个人学习研究使用。自动化操作可能违反平台条款，风险自负。

## 现状

| 项 | 值 |
| --- | --- |
| 版本 | 8.1.6（versionCode 136） |
| minSdk / targetSdk / compileSdk | 26 / 36 / 37 |
| ABI | arm64-v8a、x86_64 |
| JDK / Gradle / AGP | 21 / 9.3.1 / 9.1 |

## 能做什么

- 账号登录，cookie 持久化，可选自动登录
- 课程 / 章节 / 任务点解析，视频任务点心跳上报与补点
- 视频结算复查：服务端 playTime 写入慢时不会直接判失败
- 文档、阅读类任务点
- 章节测验：内置答案库（国家安全教育 44 章 132 题）按章节顺序填写
- 判断题按选项文本判定，填空按 `answerEditor{id}{n}` + `tiankongsize{id}` 提交
- 与已有作答一致时不覆盖（`saved` / `changed` 比对）
- 可选：填完直接提交判分
- 可选：DeepSeek 补题库未覆盖的题（只保存，不提交）
- 可选：题库海远程精确检索（自备卡密）
- 前线服务 + 通知进度，任务在后台继续
- 简体 / 繁體 / English / 日本語

## 不做什么

- 不内置、不代理任何题库服务，远程题库需要自己填地址与卡密
- 不内置成人图源，随机壁纸只取公开的二次元壁纸接口
- 不上传账号、cookie、答题记录到任何服务器；AI 与远程题库请求只带题目文本和自己的 Key

## 环境

需要 Android SDK Platform 37（`platforms;android-37.0`）与 Build-Tools 36。JDK 21。

```bash
# 本机
echo "sdk.dir=$ANDROID_HOME" > local.properties
./gradlew :app:assembleDebug
```

签名是可选的：把 keystore 放 `app/cx-release.jks`，或用环境变量 `CX_KEYSTORE` / `CX_STORE_PASS` / `CX_KEY_ALIAS` / `CX_KEY_PASS` 指定。没有 keystore 时只出 debug 包。

CI 走 GitHub Actions，`ubuntu-latest` 预装 SDK，不再依赖第三方 setup action；`platforms;android-37.0` 需要显式安装。

## 目录

```
app/src/main/java/com/armx/chaoxing/
  Crypt.java        登录密码 AES-CBC（key/iv = u2oh6Vu^HWe4_AES）
  Http.java         HttpURLConnection 封装，cookie 管理，重定向上限 4
  Html.java         cards / 课程 / 章节解析，mArg 提取
  Engine.java       刷课主循环：视频打点、结算复查、测验、循环刷课
  Work.java         测验页面解析、答案组装、判断题与填空题处理
  AnswerBank.java   本地题库 + 章节匹配（精确 / 去编号 / 包含 / 宽松 / Dice）
  RemoteBank.java   远程题库（可选）
  Ai.java           DeepSeek 调用（可选）
  Store.java        SharedPreferences 配置
  CxFont.java       页面自定义字体的字形反查（font_map.txt）
  TaskService.java  前台服务与通知
  RootScreen.kt     根容器：主题、壁纸、底栏、二级页路由
  TasksScreen.kt / ProgressScreen.kt / SettingsScreen.kt
  AnswersScreen.kt / QuizScreen.kt / AboutScreen.kt / UpdateScreen.kt
  AiScreen.kt / LoginScreen.kt
  Components.kt     卡片、行级弥散光、圆形按钮等自绘组件
  Loc.kt            四语言词条
```

`assets/font_map.txt` 是字形轮廓 md5 → 码位的查表结果（1.1 MB），`assets/answers_nation.txt` 是规范化后的题库（44 章 132 题）。

## 实现要点

- 登录密码不是明文：`AES/CBC/PKCS5Padding`，key/iv 都是 `u2oh6Vu^HWe4_AES`，再 base64
- 视频打点签名：
  `enc = md5("[" + clazzId + "][" + uid + "][" + jobid + "][" + objectid + "][" + (int)play*1000 + "][d_yHJ!$pdA~5][" + (int)dur*1000 + "][0_" + dur + "]")`
- 心跳间隔 `20 + rnd(21)` 秒，`speed` 上限 2.0；播放完先查一次结算，未达标补点，最多 +180 秒
- 单选题题库里可能存的是「A」或「A.邓小平」，按字母取，字母对不上时按选项文本重新定位
- 填空题作答字段随版心变化，当前按真机抓到的 `answerEditor{id}{n}` 提交，旧字段一并带上做兼容

## 已知问题

- 只有一台 OnePlus PJZ110 / Android 17 的真机验证过，其他 ROM 的动画与通知表现未测
- 折叠标题是自己喂 `TopAppBarState.heightOffset` 实现的（`nestedScroll` 在部分机型收不到增量），COUI 升级后可能失效
- 简答题（`short`）只做空位填充，没有语义判断
- 初始化引导与协议文案仍是简体，未接入 `Loc`
- 壁纸 `bg.jpg` / `bg_mo.jpg` 不在仓库内（版权归《星塔旅人》/ 悠星），需要自备后重新打包

## 第三方式

| 组件 | 许可 |
| --- | --- |
| COUI（suqi8/coui） | Apache-2.0 |
| Jetpack Compose | Apache-2.0 |
| jsoup | MIT |

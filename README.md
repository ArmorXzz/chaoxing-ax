# 超星AX（Chaoxing AX）

一个把「全自动刷超星视频任务」做成原生 Android 应用的实现：登录、拉课程、跑任务点、答测验、进度通知，全部在手机本地完成，不依赖电脑端服务。

界面基于 [COUI](https://github.com/suqi8/coui)（ColorOS 17 / ColorOS 设计语言的 Compose 组件库，Apache-2.0）重写，逻辑层是 100% 原生 Java。

## 功能

- **账号**：手机号 + 密码登录，账号信息与 Cookie 只保存在本机；支持保存账号快速切换、密码可见小眼睛、自动登录开关。
- **课程**：拉取全部课程，卡片列表逐个勾选（默认全不选），全选 / 全不选 / 刷新。
- **刷课**：视频任务点自动播放并定时上报心跳，支持倍速、并发数设置；国家级安全教育课支持循环刷课直到章节全部完成。
- **测验**：读取 `mooc-ans/api/work` 的题目，按内置答案库顺序填写，`pyFlag=1` 只保存不提交；支持单选 / 多选 / 填空 / 判断 / 简答，题干文本会做去噪与多空拆分。
- **进度**：按视频合并的进度卡，实时读秒，失败可点进「失败详情」二级页查看原因与重试次数。
- **通知**：刷课期间在通知栏显示实时进度（含每路视频的读秒），可直接在通知里停止任务。
- **外观**：浅色 / 深色 / 跟随系统，背景壁纸（含自定义图片）、背景模糊强度、材质模糊（Android 13+ 生效）。

## 技术栈

| 层 | 选型 |
|---|---|
| 语言 | Kotlin（界面）+ Java（逻辑层） |
| UI | Jetpack Compose + COUI 2.0.0（`coui-ui` / `coui-preference` / `coui-icons` / `coui-blur`） |
| 构建 | AGP 9.1 + Gradle 9.3.1 + JDK 21，compileSdk 37 / targetSdk 36 / minSdk 26 |
| 网络 | `HttpURLConnection` + jsoup 解析 HTML |
| 存储 | `SharedPreferences` |
| 签名 | 自己的 keystore（仓库不含任何密钥） |

## 目录结构

```
app/src/main/java/com/armx/chaoxing/
├── Crypt.java          AES/CBC 登录参数加密
├── Http.java           Cookie 保持、重定向、超时
├── Html.java           课程 / 章节 / 任务点 / 题目解析
├── Engine.java         刷课主循环（视频心跳、结算、阅读、文档、测验）
├── Work.java           测验作答与答案清洗
├── AnswerBank.java     内置答案库读取与章节匹配
├── CxFont.java         字体反爬（字形 hash → 码位）
├── Model.java          Course / Point / VTask / Question
├── Store.java          本地设置与账号持久化
├── TaskService.java    前台服务 + 通知进度
├── MainActivity.java   容器
├── RootScreen.kt       根容器：Scaffold / 底栏 / 二级页 / 预测式返回
├── LoginScreen.kt      登录页
├── TasksScreen.kt      任务页
├── ProgressScreen.kt   进度页 + 失败详情
├── SettingsScreen.kt   设置页（Preference 组件）
├── AnswersScreen.kt    内置答案库
├── Components.kt       共用组件
└── ...
```


## 仓库里没有的东西

为了体积与版权，仓库**不包含**：

- `app/src/main/assets/web/icon/bg.jpg`、`bg_mo.jpg`（内置壁纸，插画版权归原作者）
- 任何 keystore / 签名文件、`local.properties`

没有壁纸时应用会退化成纯色背景，功能不受影响。想恢复内置壁纸，把自己的图片放成：

```
app/src/main/assets/web/icon/bg.jpg      # 「夏花」
app/src/main/assets/web/icon/bg_mo.jpg   # 「魔王」
```

大小建议 1240×1754 以内，JPEG。

## 构建

仓库没有附带 `gradle/wrapper/gradle-wrapper.jar`（二进制文件）。用 Android Studio 打开会自动生成；命令行可用已装的 Gradle 直接构建，或先执行 `gradle wrapper --gradle-version 9.3.1` 生成 wrapper。

```bash
# 1. 准备 JDK 21、Gradle 9.3.1 与 Android SDK（platform 37、build-tools 35+）
# 2. 在 local.properties 里写 sdk.dir=/path/to/Android/sdk
# 3. 生成自己的签名（仓库不含密钥）
keytool -genkeypair -v -keystore my.jks -alias cx -keyalg RSA -keysize 2048 \
        -validity 10000 -storepass 你的密码 -keypass 你的密码

# 4. 编译（有 wrapper 时用 ./gradlew）
gradle :app:assembleRelease
```

`app/build.gradle.kts` 里的 signingConfigs 需要改成你自己的 keystore 路径与口令。

## 免责声明

本项目仅供**个人学习与技术研究**使用（Android 逆向、Compose 自定义组件、HTTP 协议分析）。使用本工具刷课可能违反超星学习通的服务条款与所在学校的学术规范，由此产生的一切后果由使用者自行承担。请勿用于商业用途。

## 第三方

- [COUI](https://github.com/suqi8/coui) —— Apache-2.0，本项目界面组件与设计规范来源
- [jsoup](https://jsoup.org/) —— MIT
- 背景插画版权归原作者所有，仅作本地壁纸使用

## 许可证

Apache-2.0，详见 `LICENSE`。

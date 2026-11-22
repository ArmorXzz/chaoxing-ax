package com.armx.chaoxing

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.app.NotificationManagerCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * 全 App 的状态与动作。界面层只读 [CxController] 的状态、只调它的方法，
 * 数据层（Engine / Store / AnswerBank）仍沿用原来的 Java 实现。
 */
class CxController(val app: Context) {

    val engine: Engine = Engine.get(app)
    private val store: Store = Store(app)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    /** 任务开始时通知宿主去拉起前台服务。 */
    var onTaskStarted: (() -> Unit)? = null

    var logged by mutableStateOf(engine.isLoggedIn())
        private set
    var user by mutableStateOf(if (engine.isLoggedIn()) engine.userName() else "")
        private set

    var courses by mutableStateOf<List<Model.Course>>(emptyList())
        private set
    var selected by mutableStateOf<Set<String>>(emptySet())
        private set
    var loadingCourses by mutableStateOf(false)
        private set

    var videos by mutableStateOf<List<Model.VTask>>(emptyList())
        private set
    var running by mutableStateOf(false)
        private set
    var done by mutableIntStateOf(0)
        private set
    var total by mutableIntStateOf(0)
        private set
    var failed by mutableIntStateOf(0)
        private set
    var hint by mutableStateOf("")
        private set

    var answers by mutableIntStateOf(0)
    var quizTotal by mutableIntStateOf(0)
    var quizDone by mutableIntStateOf(0)
    var autoSubmit by mutableStateOf(store.autoSubmit())
        private set
    var busy by mutableStateOf(false)
        private set

    var speed by mutableStateOf(store.speed())
        private set
    var parallel by mutableIntStateOf(store.parallel())
        private set

    var theme by mutableIntStateOf(store.theme())
        private set
    var glass by mutableStateOf(store.glassMode() != 0)
        private set
    var blur by mutableIntStateOf(store.blur().coerceIn(0, 50))
        private set
    var bgOn by mutableStateOf(store.bg())
        private set
    var backAnim by mutableStateOf(store.backAnim())
        private set

    var notifEnabled by mutableStateOf(false)
        private set

    var bgBitmap by mutableStateOf<Bitmap?>(null)

    /** 自动登录：开启时用上次的 cookie 直接进入，关闭则每次显示登录页。 */
    var autoLogin by mutableStateOf(store.autoLogin())
    var lang by mutableStateOf(store.lang())
    var bankOn by mutableStateOf(store.bankOn())
    var bankKey by mutableStateOf(store.bankKey())
    var aiOn by mutableStateOf(store.aiOn())
    var aiKey by mutableStateOf(store.aiKey())
    var aiModel by mutableStateOf(store.aiModel())

    /** 背景图选择：0 = 夏花，1 = 魔王，2 = 自定义，3 = 随机壁纸。 */
    var wallpaper by mutableIntStateOf(store.wall())
        private set

    var tab by mutableIntStateOf(0)
        private set

    var toastText by mutableStateOf("")
        private set
    var toastSeq by mutableIntStateOf(0)
        private set

    var backProgress by mutableStateOf(0f)

    /** 只有停在「任务」页时返回才退出 App，其它页返回是切页，不该播放退出动画。 */
    val canExitOnBack: Boolean
        get() = logged && tab == 0

    // ---------------------------------------------------------------- lifecycle

    fun start() {
        notifEnabled = notificationsOn()
        answers = answerCount()
        loadBackground()
        if (wallpaper == 3) randomWall()
        scope.launch {
            restoreSession()
            while (true) {
                sync()
                delay(700)
            }
        }
    }

    fun applyAutoSubmit(on: Boolean) {
        autoSubmit = on
        store.saveAutoSubmit(on)
    }

    fun applyLang(v: Int) {
        lang = v.coerceIn(0, 3)
        store.saveLang(lang)
        Loc.set(lang)
    }

    fun applyBankOn(on: Boolean) {
        bankOn = on
        store.saveBankOn(on)
        if (on && bankKey.isEmpty()) toast(Loc.of("还没有填题库 Key，去「AI 答题 → 远程题库」里填一个"))
    }

    fun applyBankKey(key: String) {
        bankKey = key.trim()
        store.saveBankKey(bankKey)
    }

    fun testBank() {
        val k = bankKey
        if (k.isEmpty()) {
            toast(Loc.of("先填题库 Key"))
            return
        }
        applyBankKey(k)
        scope.launch {
            val msg = withContext(Dispatchers.IO) { runCatching { RemoteBank.ping(k) }.getOrElse { "题库请求失败：$it" } }
            toast(msg)
        }
    }

    fun applyAiOn(on: Boolean) {
        aiOn = on
        store.saveAiOn(on)
        if (on && aiKey.isEmpty()) toast(Loc.of("先去「接口配置」填一个 DeepSeek API Key"))
    }

    fun applyAiKey(key: String) {
        aiKey = key.trim()
        store.saveAiKey(aiKey)
    }

    fun applyAiModel(model: String) {
        aiModel = model
        store.saveAiModel(model)
    }

    /** 连通性自检：发一句最小的请求，把结果写进 toast。 */
    fun testAi() {
        val key = aiKey
        if (key.isEmpty()) {
            toast(Loc.of("还没填 API Key"))
            return
        }
        toast(Loc.of("正在测试 DeepSeek…"))
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    Ai.chat(key, "deepseek-chat", Loc.of("只回复 OK 两个字母"), Loc.of("连通性测试"), 20)
                }
            }
            result.onSuccess { toast(Loc.of("连接正常：") + Ai.shorten(it, 24)) }
                .onFailure { toast(Loc.of("连接失败：") + Ai.shorten(it.message ?: Loc.of("未知错误"), 60)) }
        }
    }

    fun applyAutoLogin(on: Boolean) {
        autoLogin = on
        store.saveAutoLogin(on)
    }

    private suspend fun restoreSession() {
        if (!autoLogin) return
        if (logged) {
            refreshCourses()
            return
        }
        val ok = withContext(Dispatchers.IO) {
            runCatching { engine.restore() }.getOrDefault(false)
        }
        if (ok && !logged) {
            logged = true
            user = engine.userName()
            refreshCourses()
            toast(Loc.of("已恢复上次登录"))
        }
    }

    private fun sync() {
        running = engine.isRunning()
        val s = engine.summary()
        total = s[0]
        done = s[1]
        failed = s[2]
        hint = engine.hint()
        videos = engine.snapshot()
        quizTotal = engine.workTotal()
        quizDone = engine.workFinished()
        answers = engine.answersFilled()
        if (!running && tab == 1 && total > 0 && done + failed >= total) {
            // 全部结算完就不再需要轮询刷新的提示，交给界面展示完成态
        }
    }

    /** 通知开关状态每次回到前台刷新。 */
    fun refreshNotif() {
        notifEnabled = notificationsOn()
    }

    fun notificationsOn(): Boolean = runCatching {
        NotificationManagerCompat.from(app).areNotificationsEnabled()
    }.getOrDefault(false)

    // ---------------------------------------------------------------- account

    fun login(username: String, password: String, remember: Boolean) {
        if (busy) return
        val u = username.trim()
        if (u.isEmpty() || password.isEmpty()) {
            toast(Loc.of("请输入账号和密码"))
            return
        }
        busy = true
        scope.launch {
            try {
                withContext(Dispatchers.IO) { engine.login(u, password) }
                if (remember) store.saveAccount(u, password, engine.userName())
                else store.removeAccount(u)
                logged = true
                user = engine.userName()
                busy = false
                answers = answerCount()
                refreshCourses()
            } catch (e: Exception) {
                busy = false
                toast(e.message ?: Loc.of("登录失败"))
            }
        }
    }

    fun logout() {
        engine.logout()
        logged = false
        user = ""
        courses = emptyList()
        selected = emptySet()
        videos = emptyList()
        running = false
        total = 0
        done = 0
        failed = 0
        tab = 0
    }

    /** 切换账号时用来预填登录页。 */
    var prefillUsername by mutableStateOf<String?>(null)
    var prefillPassword by mutableStateOf<String?>(null)
    var prefillSeq by mutableIntStateOf(0)

    fun switchAccount(username: String) {
        val acc = savedAccounts().firstOrNull { it.username == username } ?: return
        val pwd = decryptPassword(acc.password)
        logout()
        prefillUsername = acc.username
        prefillPassword = pwd
        prefillSeq++
    }

    fun clearPrefill() {
        prefillUsername = null
        prefillPassword = null
        prefillSeq++
    }

    fun quizRecords(): List<Engine.QuizRec> = engine.quizRecords()

    fun savedAccounts(): List<Model.Account> =
        runCatching { store.accounts() }.getOrDefault(emptyList())

    fun decryptPassword(cipher: String?): String =
        runCatching { if (cipher.isNullOrEmpty()) "" else Crypt.decrypt(cipher) ?: "" }
            .getOrDefault("")

    // ---------------------------------------------------------------- courses

    fun refreshCourses() {
        if (loadingCourses) return
        loadingCourses = true
        scope.launch {
            try {
                val list = withContext(Dispatchers.IO) { engine.loadCourses() }
                courses = list
                // 默认不勾选；刷新时只保留仍在列表里的已选项。
                selected = selected.intersect(list.map { it.key() }.toSet())
            } catch (e: IOException) {
                toast(e.message ?: "课程加载失败")
            } catch (e: Exception) {
                toast(e.message ?: "课程加载失败")
            } finally {
                loadingCourses = false
            }
        }
    }

    fun toggleCourse(key: String) {
        selected = if (selected.contains(key)) selected - key else selected + key
    }

    fun selectAll(on: Boolean) {
        selected = if (on) courses.map { it.key() }.toSet() else emptySet()
    }

    fun selectedCourses(): List<Model.Course> = courses.filter { selected.contains(it.key()) }

    // ---------------------------------------------------------------- task

    fun startTask() {
        val sel = selectedCourses()
        if (sel.isEmpty()) {
            toast(Loc.of("先在「任务」页勾选课程"))
            return
        }
        if (engine.isRunning()) {
            toast(Loc.of("任务已在运行，先停止再开始"))
            return
        }
        if (engine.start(sel)) {
            tab = 1
            total = sel.size
            done = 0
            failed = 0
            onTaskStarted?.invoke()
        } else {
            toast(Loc.of("启动失败，请重试"))
        }
    }

    fun stopTask() {
        engine.stop()
        toast(Loc.of("正在停止…"))
    }

    fun switchTab(i: Int) {
        tab = i
    }

    // ---------------------------------------------------------------- settings

    fun saveRunning(speedValue: Float, parallelValue: Int) {
        val s = speedValue.coerceIn(1f, 2f)
        val p = parallelValue.coerceIn(0, 30)
        store.saveRunning(s, p)
        speed = store.speed()
        parallel = store.parallel()
        toast(Loc.of("运行参数已保存"))
    }

    /** 由 MainActivity 注入：把「跟随系统/浅色/深色」同步给 AppCompat，否则 uiMode 不会变。 */
    var onApplyNightMode: ((Int) -> Unit)? = null

    fun applyTheme(v: Int) {
        theme = v
        store.saveTheme(v)
        onApplyNightMode?.invoke(v)
    }

    fun applyGlass(on: Boolean) {
        glass = on
        store.saveGlassMode(if (on) 1 else 0)
    }

    fun applyBlur(v: Int) {
        blur = v.coerceIn(0, 50)
        store.saveUi(bgOn, blur)
        loadBackground()
    }

    fun applyBgOn(on: Boolean) {
        bgOn = on
        store.saveUi(on, blur)
    }

    fun customBg(): Boolean = store.customBg()

    /**
     * 在线换一张随机壁纸：只把图解码进内存，不落盘。
     * girl = true 时用「二次元美少女」图源。
     */
    private var wallGen = 0

    fun randomWall() {
        val gen = ++wallGen
        scope.launch(Dispatchers.IO) {
            val bmp = fetchWall(GIRL_WALLS)
            withContext(Dispatchers.Main) {
                if (gen != wallGen) return@withContext   // 期间已切到自带壁纸，丢弃这次结果
                if (bmp != null) bgBitmap = bmp else toast(Loc.of("壁纸获取失败，已保留当前背景"))
            }
        }
    }

    /** 依次尝试图源，第一张能解码的就用。接口可能直接回图片，也可能回 JSON（wallhaven）。 */
    private fun fetchWall(sources: List<String>): Bitmap? {
        for (src in sources) {
            val got = runCatching {
                val conn = (URL(src).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 4_000
                    readTimeout = 6_000
                    instanceFollowRedirects = true
                    setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 15)")
                }
                val type = conn.contentType.orEmpty()
                val bytes = conn.inputStream.use { it.readBytes() }
                conn.disconnect()
                val text = String(bytes).replace("\\/", "/")
                val link = if (type.contains("json") || text.trimStart().startsWith("{") || text.trimStart().startsWith("[")) {
                    Regex("\"path\"\\s*:\\s*\"(https:[^\"]+)\"").find(text)?.groupValues?.get(1)
                        // 通用兜底：JSON 里出现的第一个图片直链（兼容 {data:[{urls:{original}}]} 这类嵌套结构）
                        ?: Regex("https?://[^\"\\\\\\s]+?\\.(?:jpg|jpeg|png|webp|gif)").find(text)?.value
                } else null
                if (link != null) decodeUrl(link) else decodeBytes(bytes)
            }.getOrNull()
            if (got != null) return got
        }
        return null
    }

    private fun decodeUrl(url: String): Bitmap? = runCatching {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 4_000
            readTimeout = 8_000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 15)")
        }
        val bytes = conn.inputStream.use { it.readBytes() }
        conn.disconnect()
        decodeBytes(bytes)
    }.getOrNull()

    private fun decodeBytes(bytes: ByteArray): Bitmap? = runCatching {
        val probe = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, probe)
        if (probe.outWidth < 300 || probe.outHeight < 300) return@runCatching null
        val opts = BitmapFactory.Options().apply { inSampleSize = sampleSize(probe.outWidth, probe.outHeight) }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
    }.getOrNull()

    fun applyWallpaper(index: Int) {
        wallpaper = index.coerceIn(0, 3)
        store.saveWall(wallpaper)
        if (wallpaper != 2) store.saveCustomBg(false)
        if (wallpaper == 3) randomWall() else { wallGen++; loadBackground() }
    }

    private companion object {
        /** 美少女壁纸图源：先试直出 CDN（快），失败再用 wallhaven 的竖屏高清原图。 */
        val GIRL_WALLS = listOf(
            "https://t.alcy.cc/moez",
            "https://www.loliapi.com/acg/pe",
            "https://t.alcy.cc/moe",
            "https://wallhaven.cc/api/v1/search?q=anime%20girl&categories=010&purity=100&ratios=9x16,10x16&sorting=random&atleast=1200x1800",
        )
    }

    fun applyBackAnim(on: Boolean) {
        backAnim = on
        store.saveBackAnim(on)
    }

    /** 启动图标（登录页用）。这里用 BitmapFactory 直接解 PNG，
     *  不能用 painterResource(R.mipmap.ic_launcher) —— 它在 API 26+ 会解析成
     *  <adaptive-icon> 的 XML，Compose 只支持 VectorDrawable 与 PNG/JPG/WEBP 位图，会抛异常。 */
    val appIcon: Bitmap? by lazy {
        runCatching { BitmapFactory.decodeResource(app.resources, R.drawable.ic_login) }.getOrNull()
    }

    // ---------------------------------------------------------------- background

    fun loadBackground() {
        val bmp = runCatching {
            when {
                wallpaper == 2 && store.customBg() -> decodeFile(store.bgFile())
                wallpaper == 3 -> bgBitmap
                else -> decodeAsset()
            }
        }.getOrNull()
        // API 31+ 交给 Modifier.blur 实时处理：改模糊值不必重新解码，也不会触发换图淡入。
        bgBitmap = if (bmp == null) null
        else if (android.os.Build.VERSION.SDK_INT >= 31) bmp
        else bmp.let { runCatching { blurBitmap(it, (blur * 6).coerceIn(0, 60)) }.getOrDefault(it) }
    }

    /**
     * CPU 侧柔化：反复「缩小 → 放大」，效果接近高斯模糊，
     * 但不依赖 RenderEffect（API 31 的全屏 blur 在部分 ROM 上会 native crash）。
     */
    private fun soften(src: Bitmap, level: Int): Bitmap {
        if (level <= 0) return src
        val ratio = (1f / (1f + level * 0.06f)).coerceIn(0.04f, 1f)
        var current = src
        repeat(if (level > 20) 3 else 2) {
            val w = (current.width * ratio).toInt().coerceAtLeast(6)
            val h = (current.height * ratio).toInt().coerceAtLeast(6)
            if (w >= current.width || h >= current.height) return@repeat
            val small = Bitmap.createScaledBitmap(current, w, h, true)
            val back = Bitmap.createScaledBitmap(small, src.width, src.height, true)
            if (small !== back) small.recycle()
            if (current !== src) current.recycle()
            current = back
        }
        return current
    }

    private fun maxPixels(): Int {
        val dm = app.resources.displayMetrics
        val w = if (dm.widthPixels > 0) dm.widthPixels else 1080
        val h = if (dm.heightPixels > 0) dm.heightPixels else 1920
        return w * h
    }

    private fun decodeFile(file: java.io.File): Bitmap? {
        if (!file.exists() || file.length() <= 0L) return decodeAsset()
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, opts)
        opts.inSampleSize = sampleSize(opts.outWidth, opts.outHeight)
        opts.inJustDecodeBounds = false
        return BitmapFactory.decodeFile(file.absolutePath, opts)
    }

    private fun assetName(): String = if (wallpaper == 1) "web/icon/bg_mo.jpg" else "web/icon/bg.jpg"

    private fun decodeAsset(): Bitmap? = runCatching {
        app.assets.open(assetName()).use { input ->
            val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeStream(input, null, opts)
            opts.inSampleSize = sampleSize(opts.outWidth, opts.outHeight)
            opts.inJustDecodeBounds = false
            app.assets.open(assetName()).use { again ->
                BitmapFactory.decodeStream(again, null, opts)
            }
        }
    }.getOrNull()

    private fun sampleSize(w: Int, h: Int): Int {
        val budget = maxPixels()
        var sample = 1
        var pixels = (w.toLong() * h.toLong()).coerceAtLeast(1L)
        while (pixels / 4 > budget && sample < 16) {
            sample *= 2
            pixels /= 4
        }
        return sample
    }

    // ---------------------------------------------------------------- misc

    fun answerCount(): Int = runCatching {
        val bank = AnswerBank.nation(app)
        maxOf(bank.size(), bank.chapterTotal())
    }.getOrDefault(0)

    fun toast(message: String) {
        toastText = message
        toastSeq++
    }

    /** Activity 销毁时停掉所有协程。 */
    fun dispose() {
        scope.cancel()
    }

    fun fmtClock(seconds: Double): String {
        val s = seconds.toInt().coerceAtLeast(0)
        return "%02d:%02d".format(s / 60, s % 60)
    }
}

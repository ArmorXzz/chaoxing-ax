package com.armx.chaoxing

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.BackEventCompat
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.ui.platform.ComposeView
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 唯一的 Activity：承载 Compose 根界面，负责选图、系统设置跳转、预测式返回与崩溃兜底。
 * 业务状态全部在 [CxController] 里，这里只做宿主。
 */
class MainActivity : AppCompatActivity() {

    private lateinit var controller: CxController
    private lateinit var store: Store

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            boot()
        } catch (t: Throwable) {
            Crash.log(this, t)
            showFatal(t)
        }
        // 上一次进程内发生过的崩溃（包括启动阶段、来不及进界面的那种）在这里补报
        try {
            showLastCrashIfAny()
        } catch (_: Throwable) {
        }
    }

    private fun boot() {
        trace("boot: start")
        store = Store(this)
        AppCompatDelegate.setDefaultNightMode(
            when (store.theme()) {
                1 -> AppCompatDelegate.MODE_NIGHT_NO
                2 -> AppCompatDelegate.MODE_NIGHT_YES
                else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            },
        )

        controller = CxController(this).also { c ->
            // 主题三种模式都要真正落到 AppCompat 的 night mode 上，
            // 否则「跟随系统」只是改了我们的状态，uiMode 仍然是上一次强制的值。
            c.onApplyNightMode = { applyNightMode(it) }
        }
        controller.onTaskStarted = {
            runCatching { startService(Intent(this, TaskService::class.java)) }
                .onFailure { Toast.makeText(this, Loc.of("通知服务启动失败"), Toast.LENGTH_SHORT).show() }
        }

        window.statusBarColor = 0x00000000
        window.navigationBarColor = 0x00000000

        val compose = ComposeView(this)
        compose.setContent {
            CxRoot(
                controller = controller,
                onPickBg = { pickBackground() },
                onOpenNotif = { openNotifSettings() },
                onOpenBattery = { openBatterySettings() },
            )
        }
        setContentView(compose)

        trace("boot: ui ready")
        controller.start()
        setupBack()
        trace("boot: done")
    }

    // ------------------------------------------------------------------ 系统跳转

    private fun openNotifSettings() {
        try {
            val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
            } else {
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                    .setData(Uri.parse("package:$packageName"))
            }
            startActivity(intent)
        } catch (t: Throwable) {
            Toast.makeText(this, Loc.of("无法打开系统设置"), Toast.LENGTH_SHORT).show()
        }
    }

    private fun openBatterySettings() {
        try {
            startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
        } catch (t: Throwable) {
            runCatching { startActivity(Intent(Settings.ACTION_SETTINGS)) }
                .onFailure { Toast.makeText(this, Loc.of("无法打开系统设置"), Toast.LENGTH_SHORT).show() }
        }
    }

    private fun pickBackground() {
        try {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT)
                .addCategory(Intent.CATEGORY_OPENABLE)
                .setType("image/*")
            startActivityForResult(intent, REQ_BG)
        } catch (t: Throwable) {
            Toast.makeText(this, Loc.of("无法打开图片选择器"), Toast.LENGTH_SHORT).show()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQ_BG || resultCode != Activity.RESULT_OK) return
        val uri = data?.data ?: return
        Thread({
            val ok = saveBackground(uri)
            runOnUiThread {
                if (ok) {
                    controller.applyWallpaper(2)
                    controller.toast(Loc.of("背景已更换"))
                } else {
                    controller.toast(Loc.of("这张图片读不出来，换一张试试"))
                }
            }
        }, "cx-bg").start()
    }

    private fun saveBackground(uri: Uri): Boolean {
        return try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return false

            var sample = 1
            val max = maxOf(bounds.outWidth, bounds.outHeight)
            while (max / (sample * 2) >= 2000 && sample < 16) sample *= 2

            val opts = BitmapFactory.Options().apply { inSampleSize = sample }
            var bmp = contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
                ?: return false

            val longest = maxOf(bmp.width, bmp.height)
            if (longest > 2000) {
                val scale = 2000f / longest
                val scaled = Bitmap.createScaledBitmap(
                    bmp,
                    maxOf(1, Math.round(bmp.width * scale)),
                    maxOf(1, Math.round(bmp.height * scale)),
                    true,
                )
                if (scaled !== bmp) bmp.recycle()
                bmp = scaled
            }

            val target: File = store.bgFile()
            target.parentFile?.mkdirs()
            FileOutputStream(target).use { out -> bmp.compress(Bitmap.CompressFormat.JPEG, 90, out) }
            bmp.recycle()
            store.saveCustomBg(true)
            store.saveWall(2)
            true
        } catch (t: Throwable) {
            Crash.log(t)
            false
        }
    }

    // ------------------------------------------------------------------ 预测式返回

    private fun setupBack() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackStarted(backEvent: BackEventCompat) {
                controller.backProgress = 0f
            }

            override fun handleOnBackProgressed(backEvent: BackEventCompat) {
                if (!controller.backAnim) return
                controller.backProgress = backEvent.progress
            }

            override fun handleOnBackCancelled() {
                controller.backProgress = 0f
            }

            override fun handleOnBackPressed() {
                controller.backProgress = 0f
                goBack()
            }
        })
    }

    private fun goBack() {
        if (!controller.logged) {
            finish()
            return
        }
        if (controller.tab != 0) {
            controller.switchTab(0)
            return
        }
        moveTaskToBack(true)
        Toast.makeText(this, Loc.of("已转入后台，任务将继续运行"), Toast.LENGTH_SHORT).show()
    }

    // ------------------------------------------------------------------ 生命周期

    override fun onResume() {
        super.onResume()
        if (::controller.isInitialized) controller.refreshNotif()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::controller.isInitialized) controller.dispose()
    }

    // ------------------------------------------------------------------ 启动轨迹

    /** 每一步都写一行到 /sdcard/Android/data/com.armx.chaoxing/files/boot.log，闪退时能靠它定位。 */
    private fun trace(msg: String) {
        Log.i("CXBOOT", msg)
        runCatching {
            val dir = getExternalFilesDir(null) ?: filesDir
            val file = File(dir, "boot.log")
            if (file.length() > 64 * 1024) file.delete()
            val line = SimpleDateFormat("MM-dd HH:mm:ss", Locale.ROOT).format(Date()) + "  " + msg + "\n"
            FileOutputStream(file, true).use { it.write(line.toByteArray()) }
        }
    }

    // ------------------------------------------------------------------ 崩溃兜底

    private fun showFatal(t: Throwable) {
        try {
            val sb = StringBuilder()
            sb.append(t.toString()).append('\n')
            t.stackTrace.forEach { sb.append("    at ").append(it).append('\n') }
            val text = if (sb.length > 2400) sb.substring(0, 2400) + "…" else sb.toString()

            val view = TextView(this).apply {
                setText("启动失败\n\n把下面这段内容发给开发者即可定位问题\n\n$text")
                setTextColor(0xFFFF6B6B.toInt())
                textSize = 12f
                setPadding(48, 96, 48, 96)
                setTextIsSelectable(true)
            }
            val scroll = ScrollView(this).apply { addView(view) }
            val box = FrameLayout(this).apply {
                setBackgroundColor(0xFF141416.toInt())
                addView(
                    scroll,
                    FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                    ),
                )
            }
            setContentView(box)
        } catch (ignored: Throwable) {
            runCatching {
                AlertDialog.Builder(this)
                    .setTitle(Loc.of("启动失败"))
                    .setMessage(t.toString())
                    .setPositiveButton(Loc.of("关闭")) { _, _ -> finish() }
                    .show()
            }.onFailure { finish() }
        }
    }

    /** 上一次进程里崩过（尤其是启动阶段崩的）就把记录摆出来，并且给个复制按钮。 */
    private fun showLastCrashIfAny() {
        val record = runCatching { Crash.read(this) }.getOrNull()
        if (record.isNullOrBlank()) return
        val body = if (record.length > 2400) record.substring(0, 2400) + "…" else record
        AlertDialog.Builder(this)
            .setTitle(Loc.of("上次启动时崩溃了"))
            .setMessage(body)
            .setPositiveButton(Loc.of("复制")) { _, _ ->
                runCatching {
                    val cm = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
                    cm.setPrimaryClip(ClipData.newPlainText("cx_crash", record))
                    Toast.makeText(this, Loc.of("已复制，发给开发者即可"), Toast.LENGTH_SHORT).show()
                }
                runCatching { Crash.clear(this) }
            }
            .setNegativeButton(Loc.of("知道了")) { _, _ ->
                runCatching { Crash.clear(this) }
            }
            .setCancelable(false)
            .show()
    }

    companion object {
        private const val REQ_BG = 0x1201

        /** CxApp 在 Application.onCreate 里调用，先把夜间模式定下来，避免首帧闪色。 */
        @JvmStatic
        fun applyNightMode(theme: Int) {
            AppCompatDelegate.setDefaultNightMode(
                when (theme) {
                    1 -> AppCompatDelegate.MODE_NIGHT_NO
                    2 -> AppCompatDelegate.MODE_NIGHT_YES
                    else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
                },
            )
        }
    }
}

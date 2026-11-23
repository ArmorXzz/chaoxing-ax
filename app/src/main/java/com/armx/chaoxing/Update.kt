package com.armx.chaoxing

import android.content.Context
import android.content.Intent
import android.net.Uri
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** GitHub Releases 更新检查。 */
object Update {
    private const val API = "https://api.github.com/repos/ArmorXzz/chaoxing-ax/releases/latest"

    /** 仓库根目录的静态清单，走 jsDelivr / ghfast 等国内可达的 CDN。 */
    private val MANIFEST = listOf(
        "https://cdn.jsdelivr.net/gh/ArmorXzz/chaoxing-ax@main/version.json",
        "https://gcore.jsdelivr.net/gh/ArmorXzz/chaoxing-ax@main/version.json",
        "https://ghfast.top/https://raw.githubusercontent.com/ArmorXzz/chaoxing-ax/main/version.json",
        "https://gh-proxy.com/https://raw.githubusercontent.com/ArmorXzz/chaoxing-ax/main/version.json",
    )

    /** 安装包的候选地址：官方优先，失败再走镜像。 */
    fun sources(url: String): List<String> {
        if (url.isEmpty()) return emptyList()
        return listOf(
            url,
            "https://ghfast.top/" + url,
            "https://gh-proxy.com/" + url,
            "https://gh.llkk.cc/" + url,
        )
    }

    data class Info(
        val tag: String,
        val name: String,
        val notes: String,
        val url: String,
        val size: Long,
        val code: Int,
        val apk: String,
    )

    /** 同步请求，调用方负责放到 IO 线程：先 GitHub API，失败再走国内 CDN 上的 version.json。 */
    @Throws(IOException::class)
    fun latest(): Info {
        var last: IOException? = null
        runCatching { fetch(API, true) }.onSuccess { return it }.onFailure {
            last = (it as? IOException) ?: IOException(it.message)
        }
        for (m in MANIFEST) {
            runCatching { fetch(m, false) }.onSuccess { return it }
        }
        throw last ?: IOException("检查更新失败")
    }

    private fun fetch(url: String, api: Boolean): Info {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 6000
            readTimeout = 10000
            instanceFollowRedirects = true
            setRequestProperty("Accept", if (api) "application/vnd.github+json" else "application/json")
            setRequestProperty("User-Agent", "chaoxing-ax/" + BuildInfo.VERSION)
        }
        try {
            val code = conn.responseCode
            if (code != 200) throw IOException("HTTP $code")
            return parse(conn.inputStream.bufferedReader().use { it.readText() }, api)
        } finally {
            conn.disconnect()
        }
    }

    private fun parse(text: String, api: Boolean): Info {
        val o = JSONObject(text)
        val tag = (if (api) o.optString("tag_name", "") else o.optString("tag", "")).ifEmpty { "?" }
        val name = o.optString("name", tag).ifEmpty { tag }
        val notes = o.optString(if (api) "body" else "notes", "")
        val url = o.optString("html_url", o.optString("url", "https://github.com/ArmorXzz/chaoxing-ax/releases"))
        var size = 0L
        var apk = ""
        val assets = o.optJSONArray("assets")
        if (assets != null) {
            for (i in 0 until assets.length()) {
                val a = assets.getJSONObject(i)
                if (a.optString("name").endsWith(".apk", true)) {
                    size = a.optLong("size", 0L)
                    apk = a.optString("browser_download_url", a.optString("apk", ""))
                    break
                }
            }
        }
        return Info(tag, name, notes, url, size, code(tag), apk)
    }

    /** tag 里的数字部分，用于比较。 */
    fun code(tag: String): Int {
        val m = Regex("(\\d+)\\.(\\d+)\\.(\\d+)").find(tag) ?: return 0
        return m.groupValues[1].toInt() * 10000 +
            m.groupValues[2].toInt() * 100 +
            m.groupValues[3].toInt()
    }

    fun open(ctx: Context, url: String) {
        runCatching {
            ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
        }
    }
}

/** 下载与安装：写外部私有目录 updates/update.apk，再用 FileProvider 交给系统安装器。 */
object ApkFile {
    private const val DIR = "updates"

    fun target(ctx: Context): java.io.File {
        val d = java.io.File(ctx.getExternalFilesDir(null), DIR)
        if (!d.exists()) d.mkdirs()
        return java.io.File(d, "update.apk")
    }

    /** 依次尝试候选地址（官方 → 国内镜像），第一个成功的写盘。 */
    @Throws(IOException::class)
    fun download(ctx: Context, urls: List<String>, onProgress: (Float) -> Unit): java.io.File {
        if (urls.isEmpty()) throw IOException("没有可下载的安装包")
        var last: IOException? = null
        for (u in urls) {
            val r = runCatching { downloadOne(ctx, u, onProgress) }
            r.getOrNull()?.let { return it }
            last = (r.exceptionOrNull() as? IOException) ?: IOException(r.exceptionOrNull()?.message)
        }
        throw last ?: IOException("下载失败")
    }

    private fun downloadOne(ctx: Context, url: String, onProgress: (Float) -> Unit): java.io.File {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 10000
            readTimeout = 30000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "chaoxing-ax/" + BuildInfo.VERSION)
        }
        try {
            val code = conn.responseCode
            if (code != 200) throw IOException("HTTP " + code)
            val total = conn.contentLengthLong
            val out = target(ctx)
            val tmp = java.io.File(out.parentFile, "update.tmp")
            conn.inputStream.use { input ->
                java.io.FileOutputStream(tmp).use { fos ->
                    val buf = ByteArray(64 * 1024)
                    var done = 0L
                    var last = -1
                    while (true) {
                        val n = input.read(buf)
                        if (n <= 0) break
                        fos.write(buf, 0, n)
                        done += n
                        if (total > 0) {
                            val p = (done * 100 / total).toInt()
                            if (p != last) {
                                last = p
                                onProgress((done.toFloat() / total).coerceIn(0f, 1f))
                            }
                        }
                    }
                    fos.flush()
                }
            }
            if (tmp.length() <= 0L) throw IOException("下载内容为空")
            if (out.exists()) out.delete()
            if (!tmp.renameTo(out)) throw IOException("无法写入安装包")
            return out
        } finally {
            conn.disconnect()
        }
    }

    fun install(ctx: Context, file: java.io.File) {
        val uri = androidx.core.content.FileProvider.getUriForFile(
            ctx, ctx.packageName + ".fileprovider", file,
        )
        val i = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        ctx.startActivity(i)
    }
}

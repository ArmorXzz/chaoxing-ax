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

    data class Info(val tag: String, val name: String, val notes: String, val url: String, val size: Long, val code: Int)

    /** 同步请求，调用方负责放到 IO 线程。 */
    @Throws(IOException::class)
    fun latest(): Info {
        val conn = (URL(API).openConnection() as HttpURLConnection).apply {
            connectTimeout = 8000
            readTimeout = 12000
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("User-Agent", "chaoxing-ax/" + BuildInfo.VERSION)
        }
        try {
            val code = conn.responseCode
            if (code != 200) throw IOException("HTTP $code")
            val text = conn.inputStream.bufferedReader().use { it.readText() }
            val o = JSONObject(text)
            val tag = o.optString("tag_name", "").ifEmpty { "?" }
            val name = o.optString("name", tag)
            val notes = o.optString("body", "")
            val url = o.optString("html_url", "https://github.com/ArmorXzz/chaoxing-ax/releases")
            var size = 0L
            val assets = o.optJSONArray("assets")
            if (assets != null) {
                for (i in 0 until assets.length()) {
                    val a = assets.getJSONObject(i)
                    if (a.optString("name").endsWith(".apk", true)) {
                        size = a.optLong("size", 0L)
                        break
                    }
                }
            }
            return Info(tag, name, notes, url, size, code(tag))
        } finally {
            conn.disconnect()
        }
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

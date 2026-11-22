package com.armx.chaoxing

/**
 * 系统「应用信息 → 语言」与本应用内语言索引的映射。
 * 索引与 Loc.lang 一致：0 简中 / 1 繁中 / 2 英文 / 3 日文。
 */
object CxLocale {

    private val TAGS = arrayOf("zh-CN", "zh-TW", "en", "ja")

    fun tagOf(index: Int): String = TAGS[index.coerceIn(0, TAGS.size - 1)]

    /** 从 AppCompatDelegate.getApplicationLocales().toLanguageTags() 的结果反推索引，认不出时返回 -1。 */
    fun indexOf(languageTags: String?): Int {
        if (languageTags.isNullOrEmpty()) return -1
        for (raw in languageTags.split(",")) {
            val t = raw.trim().lowercase()
            if (t.isEmpty()) continue
            when {
                t.startsWith("zh") && (t.contains("tw") || t.contains("hk") || t.contains("hant")) -> return 1
                t.startsWith("zh") -> return 0
                t.startsWith("en") -> return 2
                t.startsWith("ja") -> return 3
            }
        }
        return -1
    }
}

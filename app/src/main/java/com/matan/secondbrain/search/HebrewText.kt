package com.matan.secondbrain.search

/** Light Hebrew-aware text normalization: niqqud, final letters, common prefixes and plural suffixes. */
object HebrewText {
    private val niqqud = Regex("[֑-ׇ]")
    private val bidi = Regex("[‎‏‪-‮⁦-⁩]")
    private val splitter = Regex("[^\\p{L}\\p{N}]+")

    private val prefixes = listOf(
        "וכש", "שכש", "וש", "שב", "שה", "של", "וה", "וב", "ול", "ומ", "וכ", "כש", "מה", "לה", "בה", "כה",
        "ה", "ו", "ב", "ל", "מ", "ש", "כ",
    )
    private val suffixes = listOf("יים", "ים", "ות", "יה", "ה", "ת", "י")

    private val stopwords = setOf(
        "מה", "מי", "איך", "איפה", "מתי", "למה", "כמה", "איזה", "איזו", "אילו", "היה", "היתה", "היו",
        "של", "את", "עם", "על", "אל", "זה", "זאת", "זו", "הוא", "היא", "הם", "הן", "אני", "אתה", "לי", "לו",
        "שלי", "שם", "יש", "אין", "גם", "רק", "או", "אם", "כי", "אבל", "כל", "עוד", "פעם", "ששמרתי", "שמרתי",
        "רציתי", "ראיתי", "חשבתי", "תזכיר", "תמצא", "תראה", "בבקשה", "משהו", "דבר", "פה", "כאן",
        "the", "a", "an", "of", "to", "in", "on", "for", "is", "was", "what", "where", "when", "how", "my", "i",
        "and", "or", "that", "this", "it", "did", "do",
    )

    fun normalize(s: String): String {
        var t = bidi.replace(s, "")
        t = niqqud.replace(t, "")
        t = t.lowercase()
        val sb = StringBuilder(t.length)
        for (c in t) {
            sb.append(
                when (c) {
                    'ך' -> 'כ'; 'ם' -> 'מ'; 'ן' -> 'נ'; 'ף' -> 'פ'; 'ץ' -> 'צ'
                    '״' -> '"'; '׳' -> '\''
                    else -> c
                }
            )
        }
        return sb.toString()
    }

    fun tokens(s: String): List<String> =
        normalize(s).split(splitter).filter { it.length >= 2 }

    fun queryTokens(s: String): List<String> = tokens(s).filter { it !in stopwords }

    private fun isHebrew(c: Char) = c in 'א'..'ת'

    /** The token plus plausible stems (prefix and suffix stripped). */
    fun stems(token: String): Set<String> {
        val out = linkedSetOf(token)
        if (token.isEmpty() || !isHebrew(token[0])) {
            if (token.length > 4 && token.endsWith("s")) out.add(token.dropLast(1))
            return out
        }
        for (p in prefixes) {
            if (token.startsWith(p) && token.length - p.length >= 2) out.add(token.substring(p.length))
        }
        val base = out.toList()
        for (b in base) {
            for (suf in suffixes) {
                if (b.endsWith(suf) && b.length - suf.length >= 2) out.add(b.dropLast(suf.length))
            }
        }
        return out
    }
}

package com.matan.secondbrain.importer

import com.matan.secondbrain.capture.Ingest
import com.matan.secondbrain.data.Item
import com.matan.secondbrain.data.ItemType
import org.json.JSONObject
import java.security.MessageDigest
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId

/**
 * Turns a WhatsApp chat export (.txt, Android or iPhone format) or the Second Brain JSON
 * import format into items. Payment cards, ID numbers, codes and secrets are marked sensitive
 * so they go to the vault and are never sent to the AI.
 */
object ChatImporter {
    private val BIDI = Regex("[‎‏‪-‮⁦-⁩]")
    private val ANDROID_HDR = Regex("""^(\d{1,2})[./](\d{1,2})[./](\d{2,4}),?\s(\d{1,2}):(\d{2})(?::\d{2})?\s?([AaPp][Mm])?\s?[-–]\s(.*)$""")
    private val IOS_HDR = Regex("""^\[(\d{1,2})[./](\d{1,2})[./](\d{2,4}),?\s(\d{1,2}):(\d{2})(?::\d{2})?\s?([AaPp][Mm])?\]\s(.*)$""")
    private val SKIP = setOf(
        "<המדיה לא נכללה>", "<Media omitted>", "ההודעה נמחקה", "הודעה זו נמחקה", "This message was deleted",
        "You deleted this message", "מחקת את ההודעה הזו", "null",
    )
    private val EDITED = Regex("""\s*<(ההודעה נערכה|This message was edited)>\s*$""")

    data class Parsed(val items: List<Item>, val skippedMedia: Int)

    fun parseWhatsAppText(raw: String, zone: ZoneId = ZoneId.systemDefault()): Parsed {
        data class Msg(val at: Long, val sb: StringBuilder)

        val msgs = ArrayList<Msg>()
        var inSystem = false
        for (rawLine in raw.lineSequence()) {
            val line = BIDI.replace(rawLine, "")
            val m = ANDROID_HDR.matchEntire(line) ?: IOS_HDR.matchEntire(line)
            if (m != null) {
                val g = m.groupValues
                val day = g[1].toInt()
                val month = g[2].toInt()
                var year = g[3].toInt()
                if (year < 100) year += 2000
                var hour = g[4].toInt()
                val minute = g[5].toInt()
                val ampm = g[6].lowercase()
                if (ampm == "pm" && hour < 12) hour += 12
                if (ampm == "am" && hour == 12) hour = 0
                val at = runCatching {
                    LocalDateTime.of(year, month, day, hour, minute).atZone(zone).toInstant().toEpochMilli()
                }.getOrElse {
                    // Some exports put the month first.
                    runCatching { LocalDateTime.of(year, day, month, hour, minute).atZone(zone).toInstant().toEpochMilli() }
                        .getOrDefault(System.currentTimeMillis())
                }
                val rest = g[7]
                val sep = rest.indexOf(": ")
                if (sep > 0) {
                    msgs.add(Msg(at, StringBuilder(rest.substring(sep + 2))))
                    inSystem = false
                } else {
                    inSystem = true
                }
            } else if (!inSystem && msgs.isNotEmpty()) {
                msgs.last().sb.append('\n').append(line)
            }
        }

        var media = 0
        val items = ArrayList<Item>()
        for (msg in msgs) {
            val text = clean(msg.sb.toString())
            if (text.isEmpty()) continue
            if (text in SKIP || text.endsWith("(הקובץ מצורף)") || text.endsWith("(file attached)")) {
                media++
                continue
            }
            build(text, msg.at, "whatsapp")?.let(items::add)
        }
        return Parsed(items, media)
    }

    /** Reads the JSON format that the WhatsApp converter produced. */
    fun parseImportJson(raw: String): Parsed {
        val root = JSONObject(raw)
        val arr = root.optJSONArray("items") ?: return Parsed(emptyList(), 0)
        val vault = root.optBoolean("vault", false)
        val items = ArrayList<Item>()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            val text = clean(o.optString("text"))
            if (text.isEmpty()) continue
            val at = runCatching { OffsetDateTime.parse(o.optString("created_at")).toInstant().toEpochMilli() }
                .getOrDefault(System.currentTimeMillis())
            val tags = o.optJSONArray("tags")?.let { t -> (0 until t.length()).map { t.getString(it) } }.orEmpty()
                .filter { !it.contains('.') }
            val forcedSensitive = vault || o.has("sensitive")
            build(text, at, "whatsapp", tags, forcedSensitive)?.let(items::add)
        }
        return Parsed(items, 0)
    }

    private fun clean(s: String): String = EDITED.replace(BIDI.replace(s, ""), "").trim()

    private fun build(text: String, at: Long, source: String, extraTags: List<String> = emptyList(), forceSensitive: Boolean = false): Item? {
        if (text.length < 3 || text.none { it.isLetterOrDigit() }) return null
        val type = Ingest.classifyText(text)
        val sensitive = forceSensitive || Sensitive.detect(text).isNotEmpty()
        val tags = buildList {
            addAll(extraTags)
            if (type == ItemType.PHONE) add("אנשים וטלפונים")
            if (type == ItemType.CODE) add("עבודה וקוד")
        }
        return Item(
            sourceId = "wa-" + sha1("$at|$text").take(16),
            type = type,
            text = text,
            url = Ingest.firstUrl(text),
            tags = Item.joinList(tags),
            createdAt = at,
            sensitive = sensitive,
            source = source,
        )
    }

    private fun sha1(s: String): String =
        MessageDigest.getInstance("SHA-1").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }
}

object Sensitive {
    private val CARD = Regex("""(?<!\d)\d{13,19}(?!\d)""")
    private val NINE = Regex("""(?<!\d)\d{9}(?!\d)""")
    private val ID_WORDS = Regex("""ת[״"]ז|תעודת זהות""")
    private val TOKENS = Regex("""\b(sk-[\w-]{20,}|AKIA[0-9A-Z]{16}|gh[pousr]_\w{30,}|xox[abp]-[\w-]{10,}|AIza[\w-]{30,})|\b\d{8,10}:AA[\w-]{30,}""")
    private val BLOB = Regex("""(?<![\w/])[A-Za-z0-9+/]{28,}={0,2}(?![\w/])""")
    private val CODES = Regex("""(?<!\p{L})(קוד|קודים|סיסמה|סיסמא|סיסמאות|pass|pin)(?!\p{L})|כניסה לבנ|כניסה לבית|password""", RegexOption.IGNORE_CASE)

    fun detect(text: String): Set<String> {
        val out = HashSet<String>()
        val compact = text.replace(" ", "").replace("-", "")
        if (CARD.findAll(compact).any { luhn(it.value) }) out.add("payment_card")
        if (ID_WORDS.containsMatchIn(text) || NINE.findAll(text).any { !it.value.startsWith("05") && israeliId(it.value) }) out.add("id_number")
        if (TOKENS.containsMatchIn(text) || (BLOB.containsMatchIn(text) && !Ingest.URL_REGEX.containsMatchIn(text))) out.add("secret")
        if (CODES.containsMatchIn(text)) out.add("access_code")
        return out
    }

    private fun luhn(n: String): Boolean {
        var sum = 0
        n.reversed().forEachIndexed { i, c ->
            var d = c - '0'
            if (i % 2 == 1) { d *= 2; if (d > 9) d -= 9 }
            sum += d
        }
        return sum % 10 == 0
    }

    private fun israeliId(n: String): Boolean {
        var sum = 0
        n.forEachIndexed { i, c ->
            val v = (c - '0') * (1 + i % 2)
            sum += v / 10 + v % 10
        }
        return sum % 10 == 0
    }
}

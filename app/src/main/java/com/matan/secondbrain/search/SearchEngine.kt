package com.matan.secondbrain.search

import com.matan.secondbrain.data.Item
import kotlin.math.ln

/** In-memory ranked search over items. Fast enough for many thousands of notes. */
class SearchEngine {
    private class Doc(val item: Item, val stems: Set<String>, val flat: String)

    private var docs: List<Doc> = emptyList()
    private var docFreq: Map<String, Int> = emptyMap()

    fun rebuild(items: List<Item>) {
        val built = items.map { item ->
            val raw = buildString {
                append(item.title).append(' ')
                append(item.text).append(' ')
                append(item.userNote).append(' ')
                append(item.tags.replace('|', ' ')).append(' ')
                append(item.keywords.replace('|', ' ')).append(' ')
                append(item.summary).append(' ')
                append(item.ocrText).append(' ')
                item.url?.let { append(it) }
            }
            val stems = HashSet<String>()
            for (t in HebrewText.tokens(raw)) stems.addAll(HebrewText.stems(t))
            Doc(item, stems, HebrewText.normalize(raw))
        }
        val df = HashMap<String, Int>()
        for (d in built) for (s in d.stems) df[s] = (df[s] ?: 0) + 1
        docs = built
        docFreq = df
    }

    data class Hit(val item: Item, val score: Double)

    /**
     * @param extraTerms additional meaning-related terms (e.g. from AI query expansion).
     */
    fun search(query: String, extraTerms: List<String> = emptyList(), limit: Int = 60, includeSensitive: Boolean = false): List<Hit> {
        val q = query.trim()
        if (q.startsWith("#")) {
            val tag = q.removePrefix("#").trim()
            return docs.filter { (includeSensitive || !it.item.sensitive) && it.item.tagList().any { t -> t == tag } }
                .sortedByDescending { it.item.createdAt }
                .map { Hit(it.item, 1.0) }
        }

        val weighted = LinkedHashMap<String, Double>()
        val base = HebrewText.queryTokens(q).ifEmpty { HebrewText.tokens(q) }
        for (t in base) weighted[t] = 1.0
        for (t in base) for (e in Concepts.expand(t)) weighted.putIfAbsent(e, 0.45)
        for (term in extraTerms) for (t in HebrewText.tokens(term)) weighted.putIfAbsent(t, 0.7)
        if (weighted.isEmpty()) return emptyList()

        val n = docs.size.coerceAtLeast(1)
        val now = System.currentTimeMillis()
        val hits = ArrayList<Hit>()
        for (d in docs) {
            if (!includeSensitive && d.item.sensitive) continue
            var score = 0.0
            var baseMatched = 0
            for ((term, w) in weighted) {
                val stems = HebrewText.stems(term)
                val df = stems.maxOf { docFreq[it] ?: 0 }
                val idf = ln(1.0 + n.toDouble() / (1.0 + df))
                val matched = when {
                    stems.any { it in d.stems } -> 1.0
                    term.length >= 3 && d.flat.contains(term) -> 0.6
                    else -> 0.0
                }
                if (matched > 0) {
                    score += w * idf * matched
                    if (w >= 1.0) baseMatched++
                }
            }
            if (score <= 0) continue
            if (base.size > 1 && baseMatched == base.size) score *= 1.4
            val ageDays = (now - d.item.createdAt) / 86_400_000.0
            score *= 1.0 + 0.15 / (1.0 + ageDays / 30.0)
            hits.add(Hit(d.item, score))
        }
        return hits.sortedByDescending { it.score }.take(limit)
    }

    fun related(item: Item, limit: Int = 5): List<Item> {
        val me = docs.firstOrNull { it.item.id == item.id } ?: return emptyList()
        val myTags = item.tagList().toSet()
        val myKeys = item.keywordList().flatMap { HebrewText.tokens(it) }.flatMap { HebrewText.stems(it) }.toSet()
        return docs.asSequence()
            .filter { it.item.id != item.id && !it.item.sensitive }
            .map { d ->
                val tagScore = d.item.tagList().count { it in myTags } * 1.0
                val keyScore = myKeys.count { it in d.stems } * 0.5
                d to (tagScore + keyScore)
            }
            .filter { it.second >= 1.5 || (myTags.isEmpty() && it.second >= 1.0) }
            .sortedByDescending { it.second }
            .take(limit)
            .map { it.first.item }
            .toList()
            .also { if (me.stems.isEmpty()) return emptyList() }
    }

    fun tagCounts(): List<Pair<String, Int>> {
        val c = HashMap<String, Int>()
        for (d in docs) if (!d.item.sensitive) for (t in d.item.tagList()) c[t] = (c[t] ?: 0) + 1
        return c.entries.sortedByDescending { it.value }.map { it.key to it.value }
    }
}

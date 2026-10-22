package com.matan.secondbrain.search

/**
 * Small offline concept map so meaning-based search works a little even before AI enrichment.
 * If a query word belongs to a group, the whole group joins the search with a lower weight.
 */
object Concepts {
    private val groups: List<List<String>> = listOf(
        listOf("מסעדה", "restaurant", "ביסטרו", "אוכל", "ארוחה", "בית קפה", "קפה", "פיצה", "סושי", "בורגר", "המבורגר", "תפריט", "שף", "בר", "ראמן"),
        listOf("קניות", "לקנות", "סופר", "קילו", "חבילה", "רשימה", "מכולת", "shopping"),
        listOf("טלפון", "מספר", "נייד", "phone", "פלאפון", "להתקשר"),
        listOf("שגיאה", "error", "exception", "באג", "bug", "תקלה", "fix", "פתרון", "קריסה", "failed"),
        listOf("postgres", "postgresql", "psql", "pg", "sql", "שאילתה", "query", "deadlock"),
        listOf("redis", "cluster", "קלאסטר", "cache"),
        listOf("סרטון", "וידאו", "video", "youtube", "youtu", "tiktok", "reel", "instagram", "facebook", "ריל"),
        listOf("כתובת", "רחוב", "מיקום", "maps", "location", "מפה"),
        listOf("תור", "רופא", "שיננית", "בדיקה", "מרפאה", "mri", "בית חולים", "טיפול"),
        listOf("כסף", "שקל", "תשלום", "שכר", "ארנונה", "חשבון", "העברה", "חוב"),
        listOf("רעיון", "רעיונות", "אפליקציה", "app", "idea", "פרויקט", "לבנות"),
        listOf("מוזיקה", "שיר", "song", "suno", "spotify", "פלייליסט"),
        listOf("מתכון", "לבשל", "בישול", "recipe", "אפייה", "רכיבים"),
        listOf("קוקטייל", "משקה", "cocktail", "ג'ין", "וויסקי", "ליים", "ביטרס"),
        listOf("סרט", "movie", "film", "סדרה", "נטפליקס", "netflix"),
        listOf("לימודים", "קורס", "מטלה", "מבחן", "הרצאה", "סמסטר", "פסיכולוגיה"),
        listOf("אימון", "כושר", "חדר כושר", "workout", "ריצה", "gym"),
        listOf("אופנוע", "רכב", "מוסך", "טיפול", "צמיגים", "שמן"),
        listOf("כרטיס", "כרטיסים", "הופעה", "מופע", "ticket", "הזמנה"),
        listOf("משלוח", "חבילה", "דואר", "שליח", "איסוף"),
        listOf("דירה", "שכירות", "שכר דירה", "בעל הבית", "הובלה", "yad2"),
    )

    private val index: Map<String, Int> by lazy {
        val m = HashMap<String, Int>()
        groups.forEachIndexed { gi, words ->
            for (w in words) for (t in HebrewText.tokens(w)) for (s in HebrewText.stems(t)) m.putIfAbsent(s, gi)
        }
        m
    }

    /** Expansion terms for a query token (not including the token itself). */
    fun expand(token: String): Set<String> {
        val hits = HebrewText.stems(token).mapNotNull { index[it] }.toSet()
        if (hits.isEmpty()) return emptySet()
        val out = LinkedHashSet<String>()
        for (g in hits) for (w in groups[g]) out.addAll(HebrewText.tokens(w))
        out.remove(token)
        return out
    }
}

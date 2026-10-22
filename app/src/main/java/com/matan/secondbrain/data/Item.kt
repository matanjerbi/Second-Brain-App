package com.matan.secondbrain.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

object ItemType {
    const val NOTE = "note"
    const val LINK = "link"
    const val IMAGE = "image"
    const val AUDIO = "audio"
    const val PHONE = "phone"
    const val CODE = "code"
}

@Entity(tableName = "items", indices = [Index(value = ["sourceId"], unique = true)])
data class Item(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** Stable id from an import source, used to skip duplicates on re-import. */
    val sourceId: String? = null,
    val type: String = ItemType.NOTE,
    val text: String = "",
    val title: String = "",
    val url: String? = null,
    val imagePath: String? = null,
    val audioPath: String? = null,
    val userNote: String = "",
    /** Tags joined with '|'. */
    val tags: String = "",
    /** AI keywords joined with '|'. */
    val keywords: String = "",
    val summary: String = "",
    val ocrText: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val enrichedAt: Long? = null,
    val sensitive: Boolean = false,
    val source: String = "app",
) {
    fun tagList(): List<String> = tags.split("|").map { it.trim() }.filter { it.isNotEmpty() }
    fun keywordList(): List<String> = keywords.split("|").map { it.trim() }.filter { it.isNotEmpty() }

    fun displayTitle(): String {
        if (title.isNotBlank()) return title
        val first = text.lineSequence().map { it.trim() }.firstOrNull { it.isNotEmpty() }
        if (!first.isNullOrBlank()) return first.take(120)
        if (userNote.isNotBlank()) return userNote.take(120)
        return when (type) {
            ItemType.IMAGE -> "תמונה"
            ItemType.AUDIO -> "הקלטה"
            ItemType.LINK -> url ?: "קישור"
            else -> "פריט"
        }
    }

    companion object {
        fun joinList(list: List<String>): String =
            list.map { it.trim().replace("|", "/") }.filter { it.isNotEmpty() }.distinct().joinToString("|")
    }
}

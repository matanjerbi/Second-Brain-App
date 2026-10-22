package com.matan.secondbrain.data

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.matan.secondbrain.ai.ClaudeClient
import com.matan.secondbrain.ai.Prompts
import com.matan.secondbrain.capture.Ingest
import com.matan.secondbrain.importer.ChatImporter
import com.matan.secondbrain.importer.Sensitive
import com.matan.secondbrain.search.SearchEngine
import com.matan.secondbrain.settings.SettingsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipInputStream

class Repository(
    private val context: Context,
    private val db: AppDatabase,
    val settings: SettingsStore,
    private val scope: CoroutineScope,
) {
    private val dao = db.items()
    val engine = SearchEngine()
    val appContext: Context get() = context

    val items: Flow<List<Item>> = dao.observeAll()
    val pendingCount: Flow<Int> = dao.observePendingCount()
    fun observe(id: Long): Flow<Item?> = dao.observe(id)

    private val _indexVersion = MutableStateFlow(0)
    /** Bumps whenever the search index is rebuilt, so screens can re-run searches. */
    val indexVersion: StateFlow<Int> = _indexVersion

    data class EnrichState(val running: Boolean = false, val done: Int = 0, val total: Int = 0, val error: String? = null)
    private val _enrich = MutableStateFlow(EnrichState())
    val enrichState: StateFlow<EnrichState> = _enrich
    private val enrichMutex = Mutex()

    init {
        scope.launch {
            dao.observeAll().collect { list ->
                engine.rebuild(list)
                _indexVersion.value += 1
            }
        }
    }

    private fun client(): ClaudeClient? =
        settings.apiKey.takeIf { it.isNotBlank() }?.let { ClaudeClient(it, settings.model) }

    // ---------- Saving ----------

    suspend fun saveText(text: String, note: String = ""): Long {
        val body = text.trim()
        val type = Ingest.classifyText(body)
        val item = Item(
            type = type,
            text = body,
            url = Ingest.firstUrl(body),
            userNote = note.trim(),
            sensitive = Sensitive.detect(body + "\n" + note).isNotEmpty(),
            tags = if (type == ItemType.PHONE) "אנשים וטלפונים" else "",
        )
        val id = dao.insert(item)
        scope.launch { afterSave(id) }
        return id
    }

    suspend fun saveImage(uri: Uri, note: String = ""): Long? {
        val file = withContext(Dispatchers.IO) { Ingest.copyToStorage(context, uri, "media", "jpg") } ?: return null
        val id = dao.insert(Item(type = ItemType.IMAGE, imagePath = file.absolutePath, userNote = note.trim(), text = ""))
        scope.launch { afterSave(id) }
        return id
    }

    suspend fun saveAudio(uri: Uri, note: String = ""): Long? {
        val file = withContext(Dispatchers.IO) { Ingest.copyToStorage(context, uri, "audio", "m4a") } ?: return null
        val id = dao.insert(Item(type = ItemType.AUDIO, audioPath = file.absolutePath, userNote = note.trim(), title = note.trim().take(80)))
        scope.launch { afterSave(id) }
        return id
    }

    private suspend fun afterSave(id: Long) {
        var item = dao.get(id) ?: return
        if (item.type == ItemType.LINK && item.url != null && item.title.isBlank()) {
            Ingest.fetchLinkMeta(item.url!!)?.let { meta ->
                item = item.copy(title = meta.title, summary = item.summary.ifBlank { meta.description })
                dao.update(item)
            }
        }
        if (item.type == ItemType.IMAGE && item.imagePath != null && item.ocrText.isBlank()) {
            val text = Ingest.ocr(context, File(item.imagePath!!))
            if (text.isNotBlank()) {
                item = item.copy(ocrText = text, sensitive = item.sensitive || Sensitive.detect(text).isNotEmpty())
                dao.update(item)
            }
        }
        if (settings.autoEnrich && !item.sensitive) {
            runCatching { enrichOne(item) }
        }
    }

    suspend fun update(item: Item) = dao.update(item)
    suspend fun delete(item: Item) {
        item.imagePath?.let { runCatching { File(it).delete() } }
        item.audioPath?.let { runCatching { File(it).delete() } }
        dao.delete(item)
    }

    // ---------- Import / export ----------

    data class ImportResult(val added: Int, val duplicates: Int, val vault: Int, val media: Int, val error: String? = null)

    suspend fun importFile(uri: Uri): ImportResult = withContext(Dispatchers.IO) {
        try {
            val name = displayName(uri).lowercase()
            val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                ?: return@withContext ImportResult(0, 0, 0, 0, "לא הצלחתי לקרוא את הקובץ")
            val isZip = name.endsWith(".zip") || (bytes.size > 4 && bytes[0] == 0x50.toByte() && bytes[1] == 0x4B.toByte())
            val text = if (isZip) readTxtFromZip(bytes) else String(bytes, Charsets.UTF_8)
            if (text == null) return@withContext ImportResult(0, 0, 0, 0, "לא נמצא קובץ צ'אט בתוך ה-ZIP")
            val trimmed = text.trimStart('﻿', ' ', '\n', '\r')
            val parsed = if (trimmed.startsWith("{")) ChatImporter.parseImportJson(trimmed) else ChatImporter.parseWhatsAppText(trimmed)
            if (parsed.items.isEmpty()) return@withContext ImportResult(0, 0, 0, parsed.media, "לא נמצאו הודעות בקובץ")
            val ids = dao.insertAll(parsed.items)
            val added = ids.count { it > 0 }
            val vault = parsed.items.zip(ids).count { (item, id) -> id > 0 && item.sensitive }
            if (settings.autoEnrich && client() != null) scope.launch { enrichPending() }
            ImportResult(added, parsed.items.size - added, vault, parsed.media)
        } catch (e: Exception) {
            ImportResult(0, 0, 0, 0, "שגיאה בייבוא: ${e.message}")
        }
    }

    private fun readTxtFromZip(bytes: ByteArray): String? {
        ZipInputStream(bytes.inputStream()).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                val n = entry.name.lowercase()
                if (!entry.isDirectory && (n.endsWith(".txt") || n.endsWith(".json"))) {
                    return String(zip.readBytes(), Charsets.UTF_8)
                }
                entry = zip.nextEntry
            }
        }
        return null
    }

    private fun displayName(uri: Uri): String = runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }
    }.getOrNull() ?: (uri.lastPathSegment ?: "")

    suspend fun exportTo(uri: Uri): Int = withContext(Dispatchers.IO) {
        val all = dao.all()
        val arr = JSONArray()
        for (i in all) {
            arr.put(
                JSONObject()
                    .put("type", i.type).put("text", i.text).put("title", i.title).put("url", i.url ?: JSONObject.NULL)
                    .put("note", i.userNote).put("tags", JSONArray(i.tagList())).put("keywords", JSONArray(i.keywordList()))
                    .put("summary", i.summary).put("ocr_text", i.ocrText).put("created_at", i.createdAt)
                    .put("sensitive", i.sensitive)
            )
        }
        context.contentResolver.openOutputStream(uri)?.use {
            it.write(JSONObject().put("format", "second-brain-pocket/export").put("version", 1).put("items", arr).toString(2).toByteArray())
        }
        all.size
    }

    // ---------- AI enrichment ----------

    private fun itemForPrompt(i: Item): JSONObject = JSONObject()
        .put("id", i.id.toString())
        .put("type", i.type)
        .put("text", i.text.take(1500))
        .apply {
            if (i.userNote.isNotBlank()) put("note", i.userNote.take(300))
            if (i.url != null) put("url", i.url)
            if (i.title.isNotBlank()) put("page_title", i.title)
            if (i.summary.isNotBlank()) put("page_description", i.summary.take(300))
            if (i.ocrText.isNotBlank()) put("ocr_text", i.ocrText.take(1500))
        }

    private fun applyEnrichment(i: Item, o: JSONObject): Item {
        fun list(key: String) = o.optJSONArray(key)?.let { a -> (0 until a.length()).map { a.optString(it) } }.orEmpty()
        val ocr = o.optString("ocr_text").takeIf { it.isNotBlank() }
        return i.copy(
            title = o.optString("title").takeIf { it.isNotBlank() } ?: i.title,
            summary = o.optString("summary").takeIf { it.isNotBlank() } ?: i.summary,
            keywords = Item.joinList(list("keywords")),
            tags = Item.joinList(i.tagList() + list("tags")),
            ocrText = ocr ?: i.ocrText,
            sensitive = i.sensitive || (ocr != null && Sensitive.detect(ocr).isNotEmpty()),
            enrichedAt = System.currentTimeMillis(),
        )
    }

    suspend fun enrichOne(item: Item) {
        val c = client() ?: return
        if (item.sensitive) return
        if (item.type == ItemType.IMAGE && item.imagePath != null) {
            val b64 = Ingest.jpegBase64(File(item.imagePath))
            if (b64 != null) {
                val parts = buildList {
                    add(ClaudeClient.Part.ImageJpeg(b64))
                    add(ClaudeClient.Part.Text(if (item.userNote.isNotBlank()) "הערת המשתמש: ${item.userNote}" else "צילום מסך ששמרתי"))
                }
                val reply = c.complete(Prompts.IMAGE_SYSTEM, parts, 2000)
                dao.update(applyEnrichment(dao.get(item.id) ?: item, ClaudeClient.extractJson(reply)))
                return
            }
        }
        enrichBatch(c, listOf(item))
    }

    private suspend fun enrichBatch(c: ClaudeClient, batch: List<Item>) {
        val arr = JSONArray()
        batch.forEach { arr.put(itemForPrompt(it)) }
        val reply = c.completeText(Prompts.ENRICH_SYSTEM, JSONObject().put("items", arr).toString(), 4000)
        val out = ClaudeClient.extractJson(reply).optJSONArray("items") ?: JSONArray()
        val byId = batch.associateBy { it.id.toString() }
        for (k in 0 until out.length()) {
            val o = out.optJSONObject(k) ?: continue
            val orig = byId[o.optString("id")] ?: continue
            dao.update(applyEnrichment(dao.get(orig.id) ?: orig, o))
        }
    }

    /** Enriches every item that hasn't been processed yet, in batches. */
    suspend fun enrichPending() {
        val c = client() ?: run {
            _enrich.value = EnrichState(error = "צריך מפתח API בהגדרות")
            return
        }
        if (!enrichMutex.tryLock()) return
        try {
            val pending = dao.pendingEnrichment()
            _enrich.value = EnrichState(running = true, done = 0, total = pending.size)
            var done = 0
            val (images, texts) = pending.partition { it.type == ItemType.IMAGE && it.imagePath != null }
            for (chunk in texts.chunked(20)) {
                runCatching { enrichBatch(c, chunk) }.onFailure { e ->
                    _enrich.value = _enrich.value.copy(error = e.message)
                    if (e.message?.contains("מפתח") == true) return
                }
                done += chunk.size
                _enrich.value = _enrich.value.copy(done = done)
            }
            for (img in images) {
                runCatching { enrichOne(img) }.onFailure { e -> _enrich.value = _enrich.value.copy(error = e.message) }
                done += 1
                _enrich.value = _enrich.value.copy(done = done)
            }
        } finally {
            _enrich.value = _enrich.value.copy(running = false)
            enrichMutex.unlock()
        }
    }

    // ---------- Search and answers ----------

    fun search(query: String, extra: List<String> = emptyList()): List<Item> = engine.search(query, extra).map { it.item }

    data class Answer(val text: String?, val sourceIds: List<Long>, val expanded: List<String>, val error: String? = null)

    suspend fun ask(question: String): Answer {
        val c = client() ?: return Answer(null, emptyList(), emptyList())
        return try {
            val expandReply = c.completeText(Prompts.EXPAND_SYSTEM, question, 300)
            val terms = ClaudeClient.extractJson(expandReply).optJSONArray("terms")
                ?.let { a -> (0 until a.length()).map { a.optString(it) } }.orEmpty()
            val candidates = engine.search(question, terms, limit = 40).map { it.item }
            if (candidates.isEmpty()) return Answer("לא מצאתי בזיכרון שלך משהו שקשור לזה.", emptyList(), terms)
            val fmt = SimpleDateFormat("d.M.yyyy", Locale("he"))
            val arr = JSONArray()
            for (i in candidates) {
                arr.put(itemForPrompt(i).put("saved", fmt.format(Date(i.createdAt))).apply {
                    if (i.summary.isNotBlank()) put("summary", i.summary)
                })
            }
            val reply = c.completeText(Prompts.ANSWER_SYSTEM, JSONObject().put("question", question).put("items", arr).toString(), 800)
            val o = ClaudeClient.extractJson(reply)
            val ids = o.optJSONArray("ids")?.let { a -> (0 until a.length()).mapNotNull { a.optString(it).toLongOrNull() } }.orEmpty()
            Answer(o.optString("answer").takeIf { it.isNotBlank() }, ids, terms)
        } catch (e: Exception) {
            Answer(null, emptyList(), emptyList(), e.message ?: "שגיאה")
        }
    }
}

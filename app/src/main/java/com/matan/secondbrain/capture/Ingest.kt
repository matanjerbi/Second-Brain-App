package com.matan.secondbrain.capture

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import android.webkit.MimeTypeMap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.matan.secondbrain.data.ItemType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume

object Ingest {
    val URL_REGEX = Regex("""https?://[^\s<>"]+""", RegexOption.IGNORE_CASE)
    private val PHONE_REGEX = Regex("""^\+?[\d\s\-()‑]{9,18}$""")
    private val CODE_HINTS = Regex("""```|\bdef |\bSELECT\b|\bimport |\bclass |=>|kubectl|redis-cli|psql|\{\s*$""", setOf(RegexOption.IGNORE_CASE, RegexOption.MULTILINE))

    fun classifyText(text: String): String {
        val s = text.trim()
        if (PHONE_REGEX.matches(s) && s.count { it.isDigit() } >= 9) return ItemType.PHONE
        if (URL_REGEX.containsMatchIn(s)) return ItemType.LINK
        if (CODE_HINTS.containsMatchIn(s)) return ItemType.CODE
        return ItemType.NOTE
    }

    fun firstUrl(text: String): String? = URL_REGEX.find(text)?.value?.trimEnd('.', ',', ')', '!', '?')

    private val http by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .followRedirects(true)
            .build()
    }

    data class LinkMeta(val title: String, val description: String)

    /** Reads the page title and description; returns null when the page can't be read. */
    suspend fun fetchLinkMeta(url: String): LinkMeta? = withContext(Dispatchers.IO) {
        runCatching {
            val req = Request.Builder().url(url)
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/124 Mobile Safari/537.36")
                .header("Accept-Language", "he,en;q=0.8")
                .build()
            http.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@use null
                val source = resp.body?.source() ?: return@use null
                source.request(400_000)
                val html = source.buffer.snapshot().utf8()
                fun meta(prop: String): String? {
                    val a = Regex("""<meta[^>]+(?:property|name)=["']$prop["'][^>]*content=["']([^"']*)["']""", RegexOption.IGNORE_CASE).find(html)
                    val b = Regex("""<meta[^>]+content=["']([^"']*)["'][^>]*(?:property|name)=["']$prop["']""", RegexOption.IGNORE_CASE).find(html)
                    return (a ?: b)?.groupValues?.get(1)?.let(::decodeEntities)?.trim()?.takeIf { it.isNotEmpty() }
                }
                val title = meta("og:title")
                    ?: Regex("""<title[^>]*>([^<]*)</title>""", RegexOption.IGNORE_CASE).find(html)?.groupValues?.get(1)?.let(::decodeEntities)?.trim()
                    ?: ""
                val desc = meta("og:description") ?: meta("description") ?: ""
                if (title.isBlank() && desc.isBlank()) null else LinkMeta(title.take(200), desc.take(500))
            }
        }.getOrNull()
    }

    private fun decodeEntities(s: String): String = s
        .replace("&amp;", "&").replace("&quot;", "\"").replace("&#39;", "'").replace("&#x27;", "'")
        .replace("&lt;", "<").replace("&gt;", ">").replace("&nbsp;", " ")

    /** Copies shared content into private storage so it survives after the sharing app revokes access. */
    fun copyToStorage(context: Context, uri: Uri, folder: String, fallbackExt: String): File? = runCatching {
        val mime = context.contentResolver.getType(uri)
        val ext = mime?.let { MimeTypeMap.getSingleton().getExtensionFromMimeType(it) } ?: fallbackExt
        val dir = File(context.filesDir, folder).apply { mkdirs() }
        val out = File(dir, "${UUID.randomUUID()}.$ext")
        context.contentResolver.openInputStream(uri)?.use { input -> out.outputStream().use { input.copyTo(it) } } ?: return null
        out
    }.getOrNull()

    /** On-device OCR (Latin script). Hebrew text is read by the AI pass when a key is set. */
    suspend fun ocr(context: Context, file: File): String = runCatching {
        val image = InputImage.fromFilePath(context, Uri.fromFile(file))
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        suspendCancellableCoroutine<String> { cont ->
            recognizer.process(image)
                .addOnSuccessListener { cont.resume(it.text) }
                .addOnFailureListener { cont.resume("") }
        }
    }.getOrDefault("")

    /** Downscaled JPEG for sending to the vision model. */
    suspend fun jpegBase64(file: File, maxDim: Int = 1568): String? = withContext(Dispatchers.IO) {
        runCatching {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.absolutePath, bounds)
            var sample = 1
            while (bounds.outWidth / sample > maxDim * 2 || bounds.outHeight / sample > maxDim * 2) sample *= 2
            val bmp = BitmapFactory.decodeFile(file.absolutePath, BitmapFactory.Options().apply { inSampleSize = sample })
                ?: return@runCatching null
            val scale = minOf(1f, maxDim.toFloat() / maxOf(bmp.width, bmp.height))
            val scaled = if (scale < 1f) Bitmap.createScaledBitmap(bmp, (bmp.width * scale).toInt(), (bmp.height * scale).toInt(), true) else bmp
            val bos = ByteArrayOutputStream()
            scaled.compress(Bitmap.CompressFormat.JPEG, 85, bos)
            Base64.encodeToString(bos.toByteArray(), Base64.NO_WRAP)
        }.getOrNull()
    }
}

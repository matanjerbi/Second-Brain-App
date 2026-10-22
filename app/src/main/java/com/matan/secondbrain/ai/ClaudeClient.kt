package com.matan.secondbrain.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class AiException(message: String) : Exception(message)

/** Minimal Anthropic Messages API client. */
class ClaudeClient(private val apiKey: String, private val model: String) {

    private val http = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .build()

    sealed class Part {
        data class Text(val text: String) : Part()
        data class ImageJpeg(val base64: String) : Part()
    }

    suspend fun complete(system: String, parts: List<Part>, maxTokens: Int = 1500): String = withContext(Dispatchers.IO) {
        val content = JSONArray()
        for (p in parts) {
            when (p) {
                is Part.Text -> content.put(JSONObject().put("type", "text").put("text", p.text))
                is Part.ImageJpeg -> content.put(
                    JSONObject().put("type", "image").put(
                        "source",
                        JSONObject().put("type", "base64").put("media_type", "image/jpeg").put("data", p.base64),
                    )
                )
            }
        }
        val body = JSONObject()
            .put("model", model)
            .put("max_tokens", maxTokens)
            .put("system", system)
            .put("messages", JSONArray().put(JSONObject().put("role", "user").put("content", content)))

        val req = Request.Builder()
            .url("https://api.anthropic.com/v1/messages")
            .header("x-api-key", apiKey)
            .header("anthropic-version", "2023-06-01")
            .header("content-type", "application/json")
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .build()

        http.newCall(req).execute().use { resp ->
            val raw = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) {
                val msg = runCatching { JSONObject(raw).getJSONObject("error").getString("message") }.getOrNull()
                throw AiException(
                    when (resp.code) {
                        401 -> "מפתח ה-API לא תקין"
                        404 -> "המודל לא נמצא: $model"
                        429 -> "יותר מדי בקשות, נסה שוב עוד רגע"
                        else -> "שגיאה ${resp.code}: ${msg ?: raw.take(200)}"
                    }
                )
            }
            val arr = JSONObject(raw).optJSONArray("content") ?: JSONArray()
            buildString {
                for (i in 0 until arr.length()) {
                    val block = arr.getJSONObject(i)
                    if (block.optString("type") == "text") append(block.optString("text"))
                }
            }
        }
    }

    suspend fun completeText(system: String, user: String, maxTokens: Int = 1500): String =
        complete(system, listOf(Part.Text(user)), maxTokens)

    companion object {
        /** Pulls the first JSON object out of a model reply (tolerates code fences and prose). */
        fun extractJson(text: String): JSONObject {
            val start = text.indexOf('{')
            val end = text.lastIndexOf('}')
            if (start < 0 || end <= start) throw AiException("תשובה לא צפויה מהמודל")
            return JSONObject(text.substring(start, end + 1))
        }
    }
}

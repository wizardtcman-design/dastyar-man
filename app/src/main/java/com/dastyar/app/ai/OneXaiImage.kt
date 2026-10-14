package com.dastyar.app.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.Base64
import java.util.concurrent.TimeUnit

/**
 * 1xAI image generation via its documented, OpenAI-compatible endpoint
 * `POST https://1xai.ir/v1/images/generations` (see https://1xai.ir/llms.txt and
 * https://1xai.ir/kharid/api-tolid-tasvir).
 *
 * Only generation is offered here. 1xAI documents no image-editing endpoint, so
 * the app never claims one; editing keeps using its existing providers.
 *
 * The two models are the ones 1xAI itself lists for this endpoint. `gpt-image-1`
 * answers with base64 (`b64_json`); `dall-e-3` answers with a temporary URL that
 * is downloaded immediately.
 */
object OneXaiImage {

    /** The image models 1xAI documents, with Persian labels for the picker. */
    val models: List<Pair<String, String>> = listOf(
        "gpt-image-1" to "1xAI · gpt-image-1",
        "dall-e-3" to "1xAI · dall-e-3"
    )

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(180, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    /** The last failure, in Persian, for the caller to show. */
    @Volatile
    var lastError: String = ""
        private set

    /** The HTTP status of the last call, for usage logging. */
    @Volatile
    var lastHttp: Int = 0
        private set

    suspend fun generate(
        apiKey: String,
        model: String,
        prompt: String,
        size: String = "1024x1024"
    ): ByteArray? = withContext(Dispatchers.IO) {
        lastError = ""
        lastHttp = 0
        if (apiKey.isBlank()) {
            lastError = "کلید 1xAI تنظیم نشده است."
            return@withContext null
        }
        val body = buildJsonObject {
            put("model", model)
            put("prompt", prompt)
            put("n", 1)
            put("size", size)
        }.toString()
        val req = Request.Builder()
            .url("https://1xai.ir/v1/images/generations")
            .addHeader("Authorization", "Bearer $apiKey")
            .post(body.toRequestBody("application/json".toMediaType()))
            .build()
        try {
            http.newCall(req).execute().use { resp ->
                lastHttp = resp.code
                val text = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    lastError = errorMessage(text).ifBlank { "1xAI: خطای ${resp.code}" }
                    return@use null
                }
                val item = runCatching {
                    json.parseToJsonElement(text).jsonObject["data"]?.jsonArray?.firstOrNull()?.jsonObject
                }.getOrNull()
                val b64 = item?.get("b64_json")?.jsonPrimitive?.contentOrNull
                if (!b64.isNullOrBlank()) {
                    return@use runCatching { Base64.getDecoder().decode(b64) }.getOrNull()
                }
                val url = item?.get("url")?.jsonPrimitive?.contentOrNull
                if (!url.isNullOrBlank()) return@use download(url)
                lastError = "1xAI: پاسخ تصویر خالی بود."
                null
            }
        } catch (e: Exception) {
            lastError = "1xAI: ${e.message ?: "خطای شبکه"}"
            null
        }
    }

    private fun errorMessage(text: String): String =
        runCatching {
            json.parseToJsonElement(text).jsonObject["error"]?.jsonObject
                ?.get("message")?.jsonPrimitive?.contentOrNull
        }.getOrNull().orEmpty()

    private fun download(url: String): ByteArray? = try {
        val req = Request.Builder().url(url).get().build()
        http.newCall(req).execute().use { r -> if (r.isSuccessful) r.body?.bytes() else null }
    } catch (_: Exception) {
        null
    }
}

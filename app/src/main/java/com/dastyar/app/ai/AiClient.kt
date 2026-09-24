package com.dastyar.app.ai

import com.dastyar.app.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
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
import java.util.concurrent.TimeUnit

/**
 * Real AI client.
 *
 * Text / reasoning  -> Atria ASI  (OpenAI-compatible chat completions)
 * Text -> Image     -> Pollinations (public model endpoint, no key)
 * Persian TTS       -> TTS.ai (public endpoint, no key)
 *
 * Nothing here is mocked: every function performs a live network call.
 */
object AiClient {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private const val MODEL = "Atria-Dawn-Preview"

    val chatConfigured: Boolean get() = BuildConfig.ATRIA_API_KEY.isNotBlank()

    // ---------------------------------------------------------------- chat

    suspend fun chat(
        system: String,
        history: List<Pair<String, String>>,
        userMessage: String
    ): Result<String> = withContext(Dispatchers.IO) {
        if (!chatConfigured) return@withContext Result.failure(
            AiException("کلید هوش مصنوعی تنظیم نشده است.")
        )
        try {
            val messages = buildJsonArray {
                add(buildJsonObject {
                    put("role", "system")
                    put("content", system)
                })
                history.forEach { (role, content) ->
                    add(buildJsonObject {
                        put("role", role)
                        put("content", content)
                    })
                }
                add(buildJsonObject {
                    put("role", "user")
                    put("content", userMessage)
                })
            }

            val body = buildJsonObject {
                put("model", MODEL)
                put("messages", messages)
                put("stream", false)
                put("temperature", 0.6)
            }.toString()

            val req = Request.Builder()
                .url("${BuildConfig.ATRIA_BASE_URL}/v1/chat/completions")
                .addHeader("Authorization", "Bearer ${BuildConfig.ATRIA_API_KEY}")
                .addHeader("Content-Type", "application/json")
                .post(body.toRequestBody("application/json".toMediaType()))
                .build()

            http.newCall(req).execute().use { resp ->
                val text = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    return@withContext Result.failure(
                        AiException("خطای API (${resp.code}): ${text.take(200)}")
                    )
                }
                val content = json.parseToJsonElement(text)
                    .jsonObject["choices"]?.jsonArray
                    ?.firstOrNull()?.jsonObject
                    ?.get("message")?.jsonObject
                    ?.get("content")?.jsonPrimitive?.contentOrNull

                if (content.isNullOrBlank()) {
                    Result.failure(AiException("پاسخ خالی از سرور دریافت شد."))
                } else {
                    Result.success(content.trim())
                }
            }
        } catch (e: Exception) {
            Result.failure(AiException("اتصال به هوش مصنوعی ناموفق بود: ${e.message}"))
        }
    }

    // --------------------------------------------------------------- image

    /**
     * Text -> image through Pollinations. The endpoint queues requests, so a
     * 503 "queue full" is retried with backoff instead of being reported as a
     * failure.
     */
    suspend fun generateImage(
        prompt: String,
        width: Int = 768,
        height: Int = 1024,
        seed: Int = (1..999_999).random()
    ): Result<ByteArray> = withContext(Dispatchers.IO) {
        if (prompt.isBlank()) return@withContext Result.failure(AiException("پرامپت خالی است."))
        val encoded = java.net.URLEncoder.encode(prompt, "UTF-8")
        val url = "https://image.pollinations.ai/prompt/$encoded" +
                "?width=$width&height=$height&nologo=true&seed=$seed&safe=false"

        var lastErr: String = ""
        repeat(4) { attempt ->
            try {
                val req = Request.Builder().url(url)
                    .addHeader("User-Agent", "DastyarMan/1.0")
                    .get().build()
                http.newCall(req).execute().use { resp ->
                    val bytes = resp.body?.bytes()
                    if (resp.isSuccessful && bytes != null && bytes.size > 1000) {
                        return@withContext Result.success(bytes)
                    }
                    lastErr = "کد ${resp.code}"
                    if (resp.code !in listOf(500, 502, 503, 504)) {
                        return@withContext Result.failure(
                            AiException("ساخت تصویر ناموفق بود ($lastErr)")
                        )
                    }
                }
            } catch (e: Exception) {
                lastErr = e.message ?: "خطای شبکه"
            }
            withContext(Dispatchers.IO) { Thread.sleep(4000L * (attempt + 1)) }
        }
        Result.failure(AiException("سرور ساخت تصویر شلوغ است ($lastErr). دوباره تلاش کنید."))
    }

    // ----------------------------------------------------------------- tts

    /** Persian text -> speech via TTS.ai. Returns raw audio bytes (WAV). */
    suspend fun textToSpeech(text: String): Result<ByteArray> = withContext(Dispatchers.IO) {
        if (text.isBlank()) return@withContext Result.failure(AiException("متن خالی است."))
        try {
            val body = buildJsonObject {
                put("text", text.take(500))
                put("language", "fa")
                put("format", "mp3")
            }.toString()

            val submit = Request.Builder()
                .url("https://api.tts.ai/v1/tts/")
                .addHeader("Content-Type", "application/json")
                .post(body.toRequestBody("application/json".toMediaType()))
                .build()

            val uuid = http.newCall(submit).execute().use { r ->
                val t = r.body?.string().orEmpty()
                if (!r.isSuccessful) return@withContext Result.failure(
                    AiException("سرویس صدا پاسخ نداد (${r.code})")
                )
                json.parseToJsonElement(t).jsonObject["uuid"]?.jsonPrimitive?.contentOrNull
            } ?: return@withContext Result.failure(AiException("شناسه صدا دریافت نشد."))

            repeat(20) {
                Thread.sleep(1500)
                val poll = Request.Builder()
                    .url("https://api.tts.ai/v1/speech/results/?uuid=$uuid")
                    .get().build()
                val result = http.newCall(poll).execute().use { r -> r.body?.string().orEmpty() }
                val obj = json.parseToJsonElement(result).jsonObject
                when (obj["status"]?.jsonPrimitive?.contentOrNull) {
                    "completed" -> {
                        val url = obj["result_url"]?.jsonPrimitive?.contentOrNull
                            ?: return@withContext Result.failure(AiException("لینک صدا نبود."))
                        val dl = Request.Builder().url(url).get().build()
                        val bytes = http.newCall(dl).execute().use { it.body?.bytes() }
                        return@withContext if (bytes != null && bytes.isNotEmpty())
                            Result.success(bytes)
                        else Result.failure(AiException("فایل صدا خالی بود."))
                    }
                    "failed" -> return@withContext Result.failure(AiException("ساخت صدا ناموفق بود."))
                }
            }
            Result.failure(AiException("زمان ساخت صدا به پایان رسید."))
        } catch (e: Exception) {
            Result.failure(AiException("خطای صدا: ${e.message}"))
        }
    }
}

class AiException(message: String) : Exception(message)

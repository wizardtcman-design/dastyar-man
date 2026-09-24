package com.dastyar.app.ai

import com.dastyar.app.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
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
import java.util.Base64
import java.util.concurrent.TimeUnit

/**
 * Real AI client.
 *
 * Text / chat   -> OpenRouter  (google/gemini-2.5-flash-lite)
 * Text -> image -> OpenRouter  (google/gemini-2.5-flash-image)
 * Image -> text -> OpenRouter  (vision, same lite model)
 * Persian TTS   -> TTS.ai      (public endpoint, no key)
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

    /** Cheap, fast, supports Persian well and also accepts images. */
    private const val TEXT_MODEL = "google/gemini-2.5-flash-lite"

    /** Image generation / editing model. Returns the picture inline (base64). */
    private const val IMAGE_MODEL = "google/gemini-2.5-flash-image"

    val chatConfigured: Boolean get() = BuildConfig.OPENROUTER_API_KEY.isNotBlank()

    private val base get() = BuildConfig.OPENROUTER_BASE_URL.trimEnd('/')

    // ---------------------------------------------------------------- chat

    suspend fun chat(
        system: String,
        history: List<Pair<String, String>>,
        userMessage: String
    ): Result<String> = complete(
        system = system,
        history = history,
        userContent = arrayOf(buildJsonObject { put("type", "text"); put("text", userMessage) }),
        model = TEXT_MODEL
    )

    /** Chat with one image attached (vision). */
    suspend fun chatWithImage(
        system: String,
        userMessage: String,
        imageBase64: String,
        mime: String = "image/jpeg"
    ): Result<String> = complete(
        system = system,
        history = emptyList(),
        userContent = arrayOf(
            buildJsonObject { put("type", "text"); put("text", userMessage) },
            buildJsonObject {
                put("type", "image_url")
                put("image_url", buildJsonObject {
                    put("url", "data:$mime;base64,$imageBase64")
                })
            }
        ),
        model = TEXT_MODEL
    )

    /**
     * Shared chat-completions call. Content is always sent as a typed parts
     * array so text and image messages use the exact same code path.
     */
    private suspend fun complete(
        system: String,
        history: List<Pair<String, String>>,
        userContent: Array<kotlinx.serialization.json.JsonElement>,
        model: String
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
                    put("content", buildJsonArray { userContent.forEach { add(it) } })
                })
            }

            val body = buildJsonObject {
                put("model", model)
                put("messages", messages)
                put("stream", false)
                put("temperature", 0.6)
            }.toString()

            val req = Request.Builder()
                .url("$base/chat/completions")
                .addHeader("Authorization", "Bearer ${BuildConfig.OPENROUTER_API_KEY}")
                .addHeader("Content-Type", "application/json")
                .post(body.toRequestBody("application/json".toMediaType()))
                .build()

            http.newCall(req).execute().use { resp ->
                val text = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    return@withContext Result.failure(
                        AiException(friendlyError(resp.code, text))
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

    /** Turns an API error into a short Persian sentence with a usable hint. */
    private fun friendlyError(code: Int, raw: String): String = when (code) {
        401 -> "کلید هوش مصنوعی معتبر نیست."
        402 -> "اعتبار سرویس هوش مصنوعی کافی نیست."
        429 -> "درخواست‌ها زیاد شده؛ چند لحظه بعد دوباره تلاش کن."
        else -> "خطای API ($code): ${raw.take(180)}"
    }

    // --------------------------------------------------------------- image

    /**
     * Text -> image through OpenRouter's image model. The picture comes back as
     * a base64 data URL, which we decode to raw bytes.
     */
    suspend fun generateImage(
        prompt: String,
        width: Int = 768,
        height: Int = 1024,
        seed: Int = (1..999_999).random()
    ): Result<ByteArray> = withContext(Dispatchers.IO) {
        if (prompt.isBlank()) return@withContext Result.failure(AiException("پرامپت خالی است."))
        if (!chatConfigured) return@withContext Result.failure(
            AiException("کلید هوش مصنوعی تنظیم نشده است.")
        )

        var lastErr = ""
        repeat(3) { attempt ->
            try {
                val body = buildJsonObject {
                    put("model", IMAGE_MODEL)
                    put("messages", buildJsonArray {
                        add(buildJsonObject {
                            put("role", "user")
                            put("content", prompt)
                        })
                    })
                    put("modalities", buildJsonArray {
                        add(kotlinx.serialization.json.JsonPrimitive("image"))
                        add(kotlinx.serialization.json.JsonPrimitive("text"))
                    })
                }.toString()

                val req = Request.Builder()
                    .url("$base/chat/completions")
                    .addHeader("Authorization", "Bearer ${BuildConfig.OPENROUTER_API_KEY}")
                    .addHeader("Content-Type", "application/json")
                    .post(body.toRequestBody("application/json".toMediaType()))
                    .build()

                http.newCall(req).execute().use { resp ->
                    val text = resp.body?.string().orEmpty()
                    if (!resp.isSuccessful) {
                        lastErr = friendlyError(resp.code, text)
                        if (resp.code !in listOf(429, 500, 502, 503, 504)) {
                            return@withContext Result.failure(AiException(lastErr))
                        }
                    } else {
                        val url = json.parseToJsonElement(text)
                            .jsonObject["choices"]?.jsonArray
                            ?.firstOrNull()?.jsonObject
                            ?.get("message")?.jsonObject
                            ?.get("images")?.jsonArray
                            ?.firstOrNull()?.jsonObject
                            ?.get("image_url")?.jsonObject
                            ?.get("url")?.jsonPrimitive?.contentOrNull

                        if (!url.isNullOrBlank()) {
                            val b64 = url.substringAfter("base64,", url)
                            val bytes = Base64.getDecoder().decode(b64)
                            if (bytes.size > 500) return@withContext Result.success(bytes)
                            lastErr = "تصویر خالی برگشت."
                        } else {
                            lastErr = "سرور تصویری برنگرداند."
                        }
                    }
                }
            } catch (e: Exception) {
                lastErr = e.message ?: "خطای شبکه"
            }
            if (attempt < 2) Thread.sleep(3000L * (attempt + 1))
        }
        Result.failure(AiException("ساخت تصویر ناموفق بود: $lastErr"))
    }

    // ----------------------------------------------------------------- tts

    /** Persian text -> speech via TTS.ai. Returns raw audio bytes. */
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

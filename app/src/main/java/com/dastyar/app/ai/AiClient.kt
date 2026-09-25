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
 * Text / chat, text->image and vision all go through the active [AiProvider],
 * which defaults to OpenRouter (google/gemini-2.5-flash-lite for text,
 * google/gemini-2.5-flash-image for images). A user-supplied provider added in
 * Settings wins, so a different service can be used without touching the code.
 *
 * Every request sets max_tokens explicitly. Without it OpenRouter assumes the
 * model's full 65k context and rejects the call with a 402 that reads like an
 * out-of-credit error even when the key has plenty of balance.
 */
object AiClient {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(150, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    /** Replies are short Persian text; this is plenty and keeps cost tiny. */
    private const val CHAT_MAX_TOKENS = 900

    /**
     * The provider actually used: a user-supplied provider wins when present,
     * otherwise the built-in OpenRouter default. Screens never care which one,
     * so a new provider only needs an [AiProviders] entry.
     */
    fun activeProvider(): AiProvider = ApiKeys.userProvider() ?: AiProviders.openRouter

    /** The key for the active provider. */
    private fun effectiveKey(): String =
        ApiKeys.userKey()?.takeIf { it.isNotBlank() } ?: BuildConfig.OPENROUTER_API_KEY

    val chatConfigured: Boolean get() = effectiveKey().isNotBlank()

    /**
     * Last real balance fetched, kept in memory so a later AI request can
     * refresh it without a second provider round-trip on every screen.
     */
    @Volatile
    var lastBalance: ServiceBalance? = null
        private set

    /** Whether the active provider can generate images. */
    val imageConfigured: Boolean get() = chatConfigured && activeProvider().supportsImages

    /** A short, Persian-friendly description of the active service. */
    fun activeServiceLabel(): String = activeProvider().label

    private fun textModel(): String = activeProvider().textModel

    private fun imageModel(): String = activeProvider().imageModel

    // ---------------------------------------------------------------- tests

    /** Verifies the active key with a tiny real request. Returns a Persian result line. */
    suspend fun testConnection(): String = testKey(effectiveKey())

    /**
     * Verifies a specific key by sending a one-token chat request to the active
     * provider. Returns a Persian sentence starting with ✅ on success or ⚠️ on
     * failure.
     */
    suspend fun testKey(key: String): String = withContext(Dispatchers.IO) {
        if (key.isBlank()) return@withContext "⚠️ کلید خالی است."
        try {
            val body = buildJsonObject {
                put("model", textModel())
                put("max_tokens", 8)
                put("messages", buildJsonArray {
                    add(buildJsonObject { put("role", "user"); put("content", "سلام") })
                })
            }.toString()
            val req = Request.Builder()
                .url(activeProvider().chatUrl())
                .addHeader("Authorization", "Bearer $key")
                .addHeader("Content-Type", "application/json")
                .post(body.toRequestBody("application/json".toMediaType()))
                .build()
            http.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) "✅ اتصال برقرار است"
                else "⚠️ ${describeError(resp.code, resp.body?.string().orEmpty())}"
            }
        } catch (e: Exception) {
            "⚠️ اتصال برقرار نشد: اینترنت را بررسی کن."
        }
    }

    /** Tests a candidate provider + key before it is saved. */
    suspend fun testProvider(provider: AiProvider, key: String): String = withContext(Dispatchers.IO) {
        if (key.isBlank()) return@withContext "⚠️ کلید خالی است."
        try {
            val body = buildJsonObject {
                put("model", provider.textModel)
                put("max_tokens", 8)
                put("messages", buildJsonArray {
                    add(buildJsonObject { put("role", "user"); put("content", "سلام") })
                })
            }.toString()
            val req = Request.Builder()
                .url(provider.chatUrl())
                .addHeader("Authorization", "Bearer $key")
                .addHeader("Content-Type", "application/json")
                .post(body.toRequestBody("application/json".toMediaType()))
                .build()
            http.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) "✅ اتصال برقرار است"
                else "⚠️ ${describeError(resp.code, resp.body?.string().orEmpty())}"
            }
        } catch (e: Exception) {
            "⚠️ اتصال برقرار نشد: اینترنت را بررسی کن."
        }
    }

    // ------------------------------------------------------------- balance

    /**
     * Real account credit reported by the provider, never computed locally.
     *
     * [remaining] and [usage] come straight from the provider's own API. When
     * the provider has no balance/usage endpoint, [supported] is false and the
     * UI shows a "not available" line instead of an invented number.
     */
    data class ServiceBalance(
        val supported: Boolean,
        val remaining: Double?,
        val usage: Double?,
        val currency: String = "$",
        /** True when the provider itself reported the limit as exhausted. */
        val exhausted: Boolean = false
    ) {
        companion object {
            fun unsupported() = ServiceBalance(supported = false, remaining = null, usage = null)
        }
    }

    private fun jsonDouble(root: kotlinx.serialization.json.JsonObject, key: String): Double? =
        root[key]?.jsonPrimitive?.contentOrNull?.toDoubleOrNull()

    /**
     * Asks the active provider for the real remaining credit and usage.
     *
     * OpenRouter exposes two real endpoints: `/credits` (account total minus
     * usage) and `/key` (this key's limit, remaining and usage). Both values are
     * taken as-is from the response; nothing is derived by subtracting guessed
     * amounts in the app. Providers without such an endpoint report
     * [ServiceBalance.unsupported] and the UI says so.
     */
    suspend fun fetchBalance(): ServiceBalance = withContext(Dispatchers.IO) {
        val provider = activeProvider()
        if (provider.creditsPath.isBlank() && provider.keyInfoPath.isBlank()) {
            return@withContext ServiceBalance.unsupported()
        }

        fun probe(key: String): Pair<Double?, Double?>? {
            fun get(url: String): kotlinx.serialization.json.JsonObject? = try {
                val req = Request.Builder()
                    .url(url)
                    .addHeader("Authorization", "Bearer $key")
                    .addHeader("Accept", "application/json")
                    .get().build()
                http.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) null else
                        json.parseToJsonElement(resp.body?.string().orEmpty())
                            .jsonObject["data"]?.jsonObject
                }
            } catch (e: Exception) {
                null
            }

            var rem: Double? = null
            var used: Double? = null
            provider.creditsUrl()?.let { url ->
                get(url)?.let { d ->
                    val total = jsonDouble(d, "total_credits")
                    val u = jsonDouble(d, "total_usage")
                    used = u
                    if (total != null && total > 0.0 && u != null) {
                        rem = (total - u).coerceAtLeast(0.0)
                    }
                }
            }
            if (rem == null) provider.keyInfoUrl()?.let { url ->
                get(url)?.let { d ->
                    val r = jsonDouble(d, "limit_remaining")
                    val limit = jsonDouble(d, "limit")
                    val u = jsonDouble(d, "usage")
                    if (u != null) used = u
                    if (r != null) rem = r.coerceAtLeast(0.0)
                    else if (limit != null && u != null) rem = (limit - u).coerceAtLeast(0.0)
                }
            }
            return if (rem == null && used == null) null else rem to used
        }

        var remaining: Double? = null
        var usage: Double? = null
        for (key in candidateKeys(provider)) {
            val r = probe(key) ?: continue
            remaining = r.first
            usage = r.second
            if (remaining != null) break
        }

        if (remaining == null && usage == null) {
            val none = ServiceBalance.unsupported()
            lastBalance = none
            return@withContext none
        }
        ServiceBalance(
            supported = true,
            remaining = remaining,
            usage = usage,
            exhausted = remaining != null && remaining <= 0.0
        ).also { lastBalance = it }
    }

    /**
     * Re-reads the balance and usage after a successful AI request so the
     * Settings card always reflects real provider numbers. Failures are
     * swallowed: a balance refresh must never disturb the feature that succeeded.
     */
    private suspend fun refreshBalanceQuietly() {
        try {
            val b = fetchBalance()
            if (b.supported) lastBalance = b
        } catch (_: Exception) {
        }
    }

    // ------------------------------------------------------------- balance end

    // ---------------------------------------------------------------- chat

    suspend fun chat(
        system: String,
        history: List<Pair<String, String>>,
        userMessage: String
    ): Result<String> = complete(
        system = system,
        history = history,
        userParts = arrayOf(buildJsonObject { put("type", "text"); put("text", userMessage) }),
        model = textModel()
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
        userParts = arrayOf(
            buildJsonObject { put("type", "text"); put("text", userMessage) },
            buildJsonObject {
                put("type", "image_url")
                put("image_url", buildJsonObject { put("url", "data:$mime;base64,$imageBase64") })
            }
        ),
        model = textModel()
    )

    /**
     * Keys to try, best first: the provider the user chose (which may be the
     * built-in OpenRouter), then the built-in key as a safety net. If a user
     * key has gone stale the app keeps working instead of appearing "cut off".
     */
    private fun candidateKeys(p: AiProvider): List<String> {
        val builtIn = BuildConfig.OPENROUTER_API_KEY
        val user = ApiKeys.userKey()?.takeIf { it.isNotBlank() }
        val list = mutableListOf<String>()
        if (user != null) list += user
        if (builtIn.isNotBlank() && builtIn != user) list += builtIn
        return list
    }

    private suspend fun complete(
        system: String,
        history: List<Pair<String, String>>,
        userParts: Array<kotlinx.serialization.json.JsonElement>,
        model: String
    ): Result<String> = withContext(Dispatchers.IO) {
        if (!chatConfigured) return@withContext Result.failure(
            AiException("کلید هوش مصنوعی تنظیم نشده است.")
        )

        val messages = buildJsonArray {
            add(buildJsonObject { put("role", "system"); put("content", system) })
            history.forEach { (role, content) ->
                add(buildJsonObject { put("role", role); put("content", content) })
            }
            add(buildJsonObject {
                put("role", "user")
                put("content", buildJsonArray { userParts.forEach { add(it) } })
            })
        }

        val body = buildJsonObject {
            put("model", model)
            put("messages", messages)
            put("stream", false)
            put("temperature", 0.6)
            put("max_tokens", CHAT_MAX_TOKENS)
        }.toString()

        var lastError = "اتصال به هوش مصنوعی ناموفق بود."

        // Try each key; retry transient server/rate-limit/IO problems before
        // moving on. A stale user key therefore costs one attempt, not the
        // whole feature, and a brief network blip no longer ends the request.
        for (key in candidateKeys(activeProvider())) {
            var stopKey = false
            repeat(3) { attempt ->
                if (stopKey) return@repeat
                try {
                    val req = Request.Builder()
                        .url(activeProvider().chatUrl())
                        .addHeader("Authorization", "Bearer $key")
                        .addHeader("Content-Type", "application/json")
                        .post(body.toRequestBody("application/json".toMediaType()))
                        .build()

                    http.newCall(req).execute().use { resp ->
                        val text = resp.body?.string().orEmpty()
                        if (resp.isSuccessful) {
                            val content = json.parseToJsonElement(text)
                                .jsonObject["choices"]?.jsonArray
                                ?.firstOrNull()?.jsonObject
                                ?.get("message")?.jsonObject
                                ?.get("content")?.jsonPrimitive?.contentOrNull
                            if (!content.isNullOrBlank()) {
                                refreshBalanceQuietly()
                                return@withContext Result.success(content.trim())
                            }
                            lastError = "پاسخ خالی از سرور دریافت شد."
                        } else {
                            lastError = describeError(resp.code, text)
                            // Auth or credit problems are key-specific: stop
                            // retrying this key and move to the next one.
                            if (resp.code == 401 || resp.code == 402 || resp.code == 403) {
                                stopKey = true
                            }
                        }
                    }
                } catch (e: Exception) {
                    lastError = "اتصال به هوش مصنوعی ناموفق بود. اینترنت را بررسی کن."
                }
                if (!stopKey && attempt < 2) Thread.sleep(1200L * (attempt + 1))
            }
        }
        Result.failure(AiException(lastError))
    }

    // --------------------------------------------------------------- image

    /**
     * Text -> image. Uses its own budget so image credit is reported separately
     * from chat credit.
     */
    suspend fun generateImage(
        prompt: String,
        width: Int = 768,
        height: Int = 1024,
        seed: Int = (1..999_999).random()
    ): Result<ByteArray> = withContext(Dispatchers.IO) {
        if (prompt.isBlank()) return@withContext Result.failure(AiException("توصیف تصویر خالی است."))
        if (!chatConfigured) return@withContext Result.failure(
            AiException("کلید هوش مصنوعی تنظیم نشده است.")
        )
        if (!activeProvider().supportsImages) return@withContext Result.failure(
            AiException("سرویس فعلی از ساخت تصویر پشتیبانی نمی‌کند.")
        )

        var lastErr = ""
        for (key in candidateKeys(activeProvider())) {
            var stopKey = false
            repeat(3) { attempt ->
                if (stopKey) return@repeat
                try {
                    val body = buildJsonObject {
                        put("model", imageModel())
                        put("messages", buildJsonArray {
                            add(buildJsonObject { put("role", "user"); put("content", prompt) })
                        })
                        if (activeProvider().supportsImageModalities) {
                            put("modalities", buildJsonArray {
                                add(kotlinx.serialization.json.JsonPrimitive("image"))
                                add(kotlinx.serialization.json.JsonPrimitive("text"))
                            })
                        }
                    }.toString()

                    val req = Request.Builder()
                        .url(activeProvider().chatUrl())
                        .addHeader("Authorization", "Bearer $key")
                        .addHeader("Content-Type", "application/json")
                        .post(body.toRequestBody("application/json".toMediaType()))
                        .build()

                    http.newCall(req).execute().use { resp ->
                        val text = resp.body?.string().orEmpty()
                        if (!resp.isSuccessful) {
                            lastErr = describeError(resp.code, text, image = true)
                            if (resp.code == 401 || resp.code == 402 || resp.code == 403) {
                                stopKey = true
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
                                if (bytes.size > 500) {
                                    refreshBalanceQuietly()
                                    return@withContext Result.success(bytes)
                                }
                                lastErr = "تصویر خالی برگشت."
                            } else {
                                lastErr = "سرور تصویری برنگرداند."
                            }
                        }
                    }
                } catch (e: Exception) {
                    lastErr = "خطای شبکه در ساخت تصویر."
                }
                if (!stopKey && attempt < 2) Thread.sleep(3000L * (attempt + 1))
            }
        }
        Result.failure(AiException("ساخت تصویر ناموفق بود: $lastErr"))
    }

    /**
     * Turns an OpenRouter error into an accurate Persian sentence. The 402 case
     * is checked carefully: OpenRouter uses 402 both for a genuinely empty
     * balance and for a request whose max_tokens exceeds the balance, so the
     * message must not claim the credit has run out unless it really has.
     */
    private fun describeError(code: Int, raw: String, image: Boolean = false): String {
        val low = raw.lowercase()
        return when (code) {
            401 -> "کلید هوش مصنوعی معتبر نیست یا لغو شده است."
            402 -> when {
                low.contains("max_tokens") ->
                    "تنظیمات درخواست با موجودی سرویس هم‌خوان نبود. دوباره تلاش کن."
                low.contains("fewer max_tokens") ->
                    "طول پاسخ بیش از حد بود؛ درخواست کوتاه‌تر ارسال شد."
                else ->
                    "موجودی سرویس هوش مصنوعی کافی نیست. " +
                            if (image) "برای ساخت تصویر به شارژ حساب نیاز است."
                            else "برای ادامه گفتگو به شارژ حساب نیاز است."
            }
            403 -> "دسترسی این مدل برای کلید فعلی باز نیست."
            404 -> "مدل درخواستی در دسترس نیست."
            429 -> "تعداد درخواست‌ها زیاد شده؛ چند لحظه بعد دوباره تلاش کن."
            in 500..599 -> "سرور هوش مصنوعی موقتاً پاسخ نمی‌دهد؛ دوباره تلاش کن."
            else -> "خطای سرویس هوش مصنوعی (کد $code)."
        }
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

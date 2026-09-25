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
     * Image generation reserves a large output budget by default, which a
     * limited-balance key cannot afford and is then rejected as if the account
     * were empty. A modest explicit cap is enough for one image and makes the
     * request pass on the same key that chat already works with.
     */
    private const val IMAGE_MAX_TOKENS = 2000

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
    suspend fun testConnection(): String {
        // Try every candidate key (user key first, then the healthy built-in
        // one) and report the real outcome, including the exact failure reason,
        // so a network block can be told apart from a bad key.
        var last = "⚠️ اتصال برقرار نشد."
        for (key in candidateKeys(activeProvider())) {
            val r = testKey(key)
            if (r.startsWith("✅")) return r
            last = r
        }
        return last
    }

    /**
     * Detailed diagnostic for Settings: reports the real HTTP status or the
     * network exception, so it is clear whether the phone is blocked, offline,
     * or the key is rejected.
     */
    suspend fun diagnose(): String = withContext(Dispatchers.IO) {
        val p = activeProvider()
        val keys = candidateKeys(p)
        if (keys.isEmpty()) return@withContext "⚠️ هیچ کلیدی تنظیم نشده است."
        val report = StringBuilder()
        report.append("سرویس: ${p.label}\n")
        report.append("تعداد کلید قابل‌تلاش: ${keys.size}\n")
        for ((i, key) in keys.withIndex()) {
            val tag = if (i == 0) "کلید اصلی" else "کلید پشتیبان"
            try {
                val body = buildJsonObject {
                    put("model", p.textModel)
                    put("max_tokens", 8)
                    put("messages", buildJsonArray {
                        add(buildJsonObject { put("role", "user"); put("content", "سلام") })
                    })
                }.toString()
                val req = Request.Builder()
                    .url(p.chatUrl())
                    .addHeader("Authorization", "Bearer $key")
                    .addHeader("Content-Type", "application/json")
                    .post(body.toRequestBody("application/json".toMediaType()))
                    .build()
                http.newCall(req).execute().use { resp ->
                    if (resp.isSuccessful) {
                        report.append("✅ $tag: پاسخ ۲۰۰ — سالم\n")
                        return@withContext report.toString().trim()
                    }
                    report.append("❌ $tag: کد HTTP ${resp.code} — ${describeError(resp.code, "")}\n")
                }
            } catch (e: Exception) {
                val reason = when (e) {
                    is java.net.UnknownHostException -> "دامنه پیدا نشد (DNS) — احتمالاً فیلتر است"
                    is java.net.SocketTimeoutException -> "زمان انتظار تمام شد — شبکه کند یا فیلتر"
                    is javax.net.ssl.SSLException -> "خطای امنیتی SSL — احتمالاً فیلتر"
                    is java.net.ConnectException -> "اتصال برقرار نشد — اینترنت قطع یا فیلتر"
                    else -> e.javaClass.simpleName + ": " + (e.message ?: "")
                }
                report.append("⚠️ $tag: $reason\n")
            }
        }
        report.toString().trim()
    }

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
            "⚠️ اتصال برقرار نشد: ${networkReason(e)}"
        }
    }

    /** Turns a network exception into a short Persian explanation. */
    private fun networkReason(e: Exception): String = when (e) {
        is java.net.UnknownHostException -> "دامنه پیدا نشد؛ محتمل است شبکه فیلتر باشد."
        is java.net.SocketTimeoutException -> "زمان انتظار تمام شد؛ شبکه کند یا فیلتر است."
        is javax.net.ssl.SSLException -> "خطای امنیتی اتصال؛ محتمل است شبکه فیلتر باشد."
        is java.net.ConnectException -> "اینترنت قطع است یا سرور مسدود شده."
        else -> "اینترنت را بررسی کن. (${e.javaClass.simpleName})"
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
     * Text -> image.
     *
     * First tries the active provider's image model (Gemini via OpenRouter, or
     * whatever the user configured). Image models need real credit, so when that
     * is unavailable the request falls back to the free, key-less Pollinations
     * endpoint. The user therefore still gets a real image without paying, and
     * automatically gets the provider's quality once credit exists.
     */
    suspend fun generateImage(
        prompt: String,
        width: Int = 768,
        height: Int = 1024,
        seed: Int = (1..999_999).random()
    ): Result<ByteArray> = withContext(Dispatchers.IO) {
        if (prompt.isBlank()) return@withContext Result.failure(AiException("توصیف تصویر خالی است."))

        // 1) Try the provider's paid image model when one is configured.
        if (chatConfigured && activeProvider().supportsImages) {
            paidImage(prompt, seed).getOrNull()?.let { return@withContext Result.success(it) }
        }

        // 2) Free fallback that needs no key and no credit. The free endpoint
        // only understands English prompts, so a Persian prompt is translated
        // first with the chat model (which the same key already powers).
        val englishPrompt = toEnglishPrompt(prompt)
        freeImage(englishPrompt, seed)
    }

    /**
     * Turns a prompt into English for the free image endpoint. Keeps the text
     * as-is when it is already English or when translation is unavailable.
     */
    private suspend fun toEnglishPrompt(prompt: String): String {
        val hasPersian = prompt.any { it in '\u0600'..'\u06FF' }
        if (!hasPersian) return prompt
        return try {
            val res = chat(
                system = "You translate image descriptions to English. " +
                        "Output only the English description, nothing else.",
                history = emptyList(),
                userMessage = prompt
            )
            res.getOrNull()?.trim()?.takeIf { it.isNotBlank() && it.length < 400 } ?: prompt
        } catch (e: Exception) {
            prompt
        }
    }

    /** Provider image model (Gemini image via OpenRouter). Needs real credit. */
    private suspend fun paidImage(prompt: String, seed: Int): Result<ByteArray> = withContext(Dispatchers.IO) {
        var lastErr = ""
        for (key in candidateKeys(activeProvider())) {
            var stopKey = false
            repeat(2) { attempt ->
                if (stopKey) return@repeat
                try {
                    val body = buildJsonObject {
                        put("model", imageModel())
                        // Image models also need an explicit max_tokens. Without
                        // it OpenRouter assumes the full 29k context and rejects
                        // the call with a 402 that reads like an empty balance,
                        // even when the key has plenty of credit.
                        put("max_tokens", IMAGE_MAX_TOKENS)
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
                if (!stopKey && attempt < 1) Thread.sleep(2500L)
            }
        }
        Result.failure(AiException(lastErr))
    }

    /**
     * Free image generation via Pollinations. No API key, no credit; a plain
     * GET that returns real JPEG bytes. Used automatically when the provider's
     * paid image model is not available.
     */
    private suspend fun freeImage(prompt: String, seed: Int): Result<ByteArray> = withContext(Dispatchers.IO) {
        val encoded = java.net.URLEncoder.encode(prompt, "UTF-8")
        val url = "https://image.pollinations.ai/prompt/$encoded" +
                "?width=768&height=1024&nologo=true&seed=$seed"
        var lastErr = "ساخت تصویر ناموفق بود."
        repeat(3) { attempt ->
            try {
                val req = Request.Builder()
                    .url(url)
                    .addHeader("User-Agent", "Dastyar/1.0")
                    .get().build()
                http.newCall(req).execute().use { resp ->
                    if (resp.isSuccessful) {
                        val bytes = resp.body?.bytes()
                        if (bytes != null && bytes.size > 1000) {
                            return@withContext Result.success(bytes)
                        }
                        lastErr = "تصویر خالی برگشت."
                    } else {
                        lastErr = "سرویس تصویر رایگان پاسخ نداد (${resp.code})."
                    }
                }
            } catch (e: Exception) {
                lastErr = "خطای شبکه در ساخت تصویر."
            }
            if (attempt < 2) Thread.sleep(3000L * (attempt + 1))
        }
        Result.failure(AiException(lastErr))
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

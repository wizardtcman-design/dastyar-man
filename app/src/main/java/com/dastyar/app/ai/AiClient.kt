package com.dastyar.app.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.JsonPrimitive
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
    fun activeProvider(): AiProvider = AiProviders.openRouter

    /**
     * The key for OpenRouter. It comes only from the user's own entry, stored in
     * the app's private preferences -- never from a compiled-in constant, the
     * source tree or the APK.
     */
    private fun effectiveKey(): String =
        ServiceKeys.openRouterKey()?.takeIf { it.isNotBlank() }.orEmpty()

    /** True once the user has entered a key, so text AI features are usable. */
    val chatConfigured: Boolean get() = effectiveKey().isNotBlank()

    /** True when OpenRouter is connected and verified. */
    val ready: Boolean get() = ServiceKeys.openRouterReady()

    /** The real reason a provider image attempt failed, for the image engine. */
    @Volatile
    var lastImageError: String = ""
        private set

    /** The text model that last answered, so the UI can show the truth. */
    @Volatile
    var activeTextModel: String = ""
        private set

    /**
     * Last real balance fetched, kept in memory so a later AI request can
     * refresh it without a second provider round-trip on every screen.
     */
    @Volatile
    var lastBalance: ServiceBalance? = null
        private set

    /** Whether OpenRouter itself can generate images (needs paid credit). */
    val imageConfigured: Boolean get() = chatConfigured && ServiceKeys.openRouterSupportsImage()

    /** A short, Persian-friendly description of the active service. */
    fun activeServiceLabel(): String = activeProvider().label

    private fun textModel(): String = ServiceKeys.openRouterTextModel()

    // -------------------------------------------------- model capabilities

    /** What a provider's catalogue says about one model. */
    private data class ModelCaps(
        val id: String,
        val imageOutput: Boolean,
        val imageInput: Boolean
    )

    /**
     * Reads the provider's real `/models` catalogue and returns the capability
     * of every model id it lists. When the catalogue is unavailable the result
     * is empty and the caller keeps the provider's declared model, so a network
     * hiccup never turns into "this model cannot make images".
     */
    private fun fetchCatalogue(provider: AiProvider, key: String): List<ModelCaps> {
        val url = provider.modelsUrl() ?: return emptyList()
        return try {
            val req = Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer $key")
                .addHeader("Accept", "application/json")
                .get().build()
            http.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return emptyList()
                val root = json.parseToJsonElement(resp.body?.string().orEmpty()).jsonObject
                val arr = root["data"]?.jsonArray ?: return emptyList()
                arr.mapNotNull { el ->
                    val obj = el.jsonObject
                    val id = obj["id"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
                    val arch = obj["architecture"]?.jsonObject
                    val out = arch?.get("output_modalities")?.jsonArray
                        ?.mapNotNull { it.jsonPrimitive.contentOrNull } ?: emptyList()
                    val inp = arch?.get("input_modalities")?.jsonArray
                        ?.mapNotNull { it.jsonPrimitive.contentOrNull } ?: emptyList()
                    ModelCaps(
                        id = id,
                        imageOutput = out.contains("image"),
                        imageInput = inp.contains("image")
                    )
                }
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * Picks a real image-output model from the catalogue. The provider's own
     * image model wins when it really outputs images; otherwise the first
     * capable model is used. Returns null when nothing in the catalogue can
     * make images, so the UI can say exactly that.
     */
    private fun resolveImageModel(provider: AiProvider, key: String): ModelCaps? {
        val catalogue = fetchCatalogue(provider, key)
        if (catalogue.isEmpty()) return null
        val capable = catalogue.filter { it.imageOutput }
        if (capable.isEmpty()) return null
        return capable.firstOrNull { it.id == provider.imageModel } ?: capable.first()
    }

    /** Models the catalogue lists as able to make images. */
    suspend fun imageCapableModels(): List<String> = withContext(Dispatchers.IO) {
        val p = activeProvider()
        val key = effectiveKey()
        if (key.isBlank()) return@withContext emptyList()
        fetchCatalogue(p, key).filter { it.imageOutput }.map { it.id }
    }

    // ---------------------------------------------------------------- tests

    /**
     * The outcome of a real connection test. Each kind is kept separate so the
     * UI can show one precise reason and never blur a network problem into an
     * account problem (or the reverse).
     */
    enum class FailKind { NONE, NO_KEY, BAD_KEY, NO_CREDIT, RATE_LIMIT, MODEL, PROVIDER, NETWORK }

    data class ConnectResult(
        val ok: Boolean,
        val kind: FailKind,
        val message: String
    )

    /** Verifies the saved OpenRouter key with a tiny real chat request. */
    suspend fun testConnection(ctx: android.content.Context): ConnectResult =
        testKey(ctx, effectiveKey())

    /**
     * Sends one real chat request with [key], classifies the exact result and
     * records the service state, so the settings screen always reflects reality.
     */
    suspend fun testKey(ctx: android.content.Context, key: String): ConnectResult =
        withContext(Dispatchers.IO) {
            if (key.isBlank()) return@withContext ConnectResult(
                false, FailKind.NO_KEY, "کلید هوش مصنوعی وارد نشده است."
            )
            val p = activeProvider()
            try {
                val body = buildJsonObject {
                    put("model", ServiceKeys.openRouterTextModel())
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
                        // Discover the real image capability from the catalogue.
                        val caps = resolveImageModel(p, key)
                        if (caps != null) {
                            ServiceKeys.updateOpenRouterCaps(
                                ctx, caps.id, supportsImg = true, supportsEdit = caps.imageInput
                            )
                        } else {
                            ServiceKeys.setOpenRouterState(ctx, ServiceKeys.State.CONNECTED)
                        }
                        ConnectResult(true, FailKind.NONE, "✅ اتصال برقرار است")
                    } else {
                        val t = resp.body?.string().orEmpty()
                        ServiceKeys.setOpenRouterState(ctx, orStateFor(resp.code))
                        ConnectResult(false, classify(resp.code, t), describeError(resp.code, t))
                    }
                }
            } catch (e: Exception) {
                ServiceKeys.setOpenRouterState(ctx, ServiceKeys.State.NETWORK)
                ConnectResult(false, FailKind.NETWORK, "اتصال برقرار نشد: ${networkReason(e)}")
            }
        }

    /** Maps an HTTP status to the stored OpenRouter state. */
    private fun orStateFor(code: Int): ServiceKeys.State = when (code) {
        401, 403 -> ServiceKeys.State.BAD_KEY
        402 -> ServiceKeys.State.NO_CREDIT
        429 -> ServiceKeys.State.RATE_LIMIT
        404 -> ServiceKeys.State.MODEL
        in 500..599 -> ServiceKeys.State.PROVIDER
        else -> ServiceKeys.State.PROVIDER
    }

    /**
     * Detailed diagnostic for Settings: reports the real HTTP status or the
     * network exception, so it is clear whether the phone is blocked, offline,
     * or the key is rejected.
     */
    suspend fun diagnose(): String = withContext(Dispatchers.IO) {
        val p = activeProvider()
        val key = effectiveKey()
        if (key.isBlank()) return@withContext "⚠️ هنوز هیچ کلیدی وارد نشده است."

        val report = StringBuilder()
        report.append("سرویس: ${p.label}\n")
        report.append("مدل متن: ${ServiceKeys.openRouterTextModel()}\n\n")
        try {
            val body = buildJsonObject {
                put("model", ServiceKeys.openRouterTextModel())
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
                    report.append("✅ چت: پاسخ ۲۰۰ — سالم\n")
                } else {
                    val t = resp.body?.string().orEmpty()
                    report.append("❌ چت: کد HTTP ${resp.code}\n")
                    report.append("دلیل: ${describeError(resp.code, t)}\n")
                    // Show the provider's own words for the real cause.
                    val detail = providerDetail(t)
                    if (detail.isNotBlank()) report.append("جزئیات سرویس: $detail\n")
                    return@withContext report.toString().trim()
                }
            }
        } catch (e: Exception) {
            report.append("⚠️ شبکه: ${networkReason(e)}\n")
            report.append("این خطا مربوط به کلید یا اعتبار نیست؛ مشکل اتصال اینترنت است.\n")
            return@withContext report.toString().trim()
        }

        // The key works for chat, so also check whether an image model exists.
        val caps = resolveImageModel(p, key)
        report.append(
            if (caps != null) "✅ تصویر: مدل «${caps.id}» در دسترس است\n"
            else "ℹ️ تصویر: هیچ مدل تصویری در فهرست سرویس پیدا نشد\n"
        )
        report.toString().trim()
    }

    /** Extracts the provider's own error message from a JSON error body. */
    private fun providerDetail(raw: String): String = try {
        json.parseToJsonElement(raw).jsonObject["error"]?.jsonObject
            ?.get("message")?.jsonPrimitive?.contentOrNull.orEmpty()
    } catch (e: Exception) {
        ""
    }

    /** Classifies an HTTP status into the exact failure kind. */
    private fun classify(code: Int, raw: String): FailKind {
        val low = raw.lowercase()
        return when {
            code == 401 -> FailKind.BAD_KEY
            code == 403 -> FailKind.BAD_KEY
            code == 402 -> FailKind.NO_CREDIT
            code == 429 -> FailKind.RATE_LIMIT
            code == 404 -> FailKind.MODEL
            code == 400 && (low.contains("model") || low.contains("modalit")) -> FailKind.MODEL
            code in 500..599 -> FailKind.PROVIDER
            else -> FailKind.PROVIDER
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

    /**
     * Tests a candidate OpenRouter key before it is saved, and returns the
     * capability discovered from the live catalogue. No vendor is assumed; the
     * models list decides what can make images.
     */
    suspend fun testProvider(
        provider: AiProvider,
        key: String
    ): Pair<ConnectResult, DiscoveredCaps?> = withContext(Dispatchers.IO) {
        if (key.isBlank()) return@withContext ConnectResult(
            false, FailKind.NO_KEY, "کلید خالی است."
        ) to null
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
                if (resp.isSuccessful) {
                    val caps = resolveImageModel(provider, key)
                    val dc = if (caps != null)
                        DiscoveredCaps(caps.id, supportsImage = true, supportsEdit = caps.imageInput)
                    else DiscoveredCaps("", supportsImage = false, supportsEdit = false)
                    ConnectResult(true, FailKind.NONE, "✅ اتصال برقرار است") to dc
                } else {
                    val t = resp.body?.string().orEmpty()
                    ConnectResult(false, classify(resp.code, t), describeError(resp.code, t)) to null
                }
            }
        } catch (e: Exception) {
            ConnectResult(false, FailKind.NETWORK, "اتصال برقرار نشد: ${networkReason(e)}") to null
        }
    }

    /** Capabilities discovered from a provider's own model catalogue. */
    data class DiscoveredCaps(
        val imageModel: String,
        val supportsImage: Boolean,
        val supportsEdit: Boolean
    )

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
     * Keys to try. There is exactly one: the key the user entered. The app has
     * no compiled-in key, so a fresh install with no key has nothing to leak and
     * nothing to fall back to.
     */
    private fun candidateKeys(p: AiProvider): List<String> =
        ServiceKeys.openRouterKey()?.takeIf { it.isNotBlank() }?.let { listOf(it) } ?: emptyList()

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

        var lastError = "اتصال به هوش مصنوعی ناموفق بود."
        var usedFreeFallback = false

        // Try the configured model first, then genuinely free models. A paid
        // model on a free account answers 402; instead of failing the whole
        // feature, a free model of the same provider is used and its name is
        // returned so nothing is hidden. "Free" is decided by the catalogue's
        // own pricing, never guessed.
        for (candidate in textModelChain(activeProvider())) {
            val body = buildJsonObject {
                put("model", candidate.model)
                put("messages", messages)
                put("stream", false)
                put("temperature", 0.6)
                put("max_tokens", CHAT_MAX_TOKENS)
            }.toString()

            for (key in candidateKeys(activeProvider())) {
                var stopKey = false
                repeat(candidate.attempts) { attempt ->
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
                                val msg = json.parseToJsonElement(text)
                                    .jsonObject["choices"]?.jsonArray
                                    ?.firstOrNull()?.jsonObject
                                    ?.get("message")?.jsonObject
                                val content = msg?.get("content")?.jsonPrimitive?.contentOrNull
                                // A model that returns an image replies with a
                                // null content and the picture under
                                // `message.images`; reading only `content`
                                // would wrongly look empty.
                                val hasImage = !msg?.get("images")?.jsonArray.isNullOrEmpty()
                                if (!content.isNullOrBlank()) {
                                    if (candidate.model != model) usedFreeFallback = true
                                    activeTextModel = candidate.model
                                    refreshBalanceQuietly()
                                    return@withContext Result.success(content.trim())
                                }
                                lastError = if (hasImage)
                                    "پاسخ تصویری از سرویس دریافت شد."
                                else "پاسخ خالی از سرور دریافت شد."
                            } else {
                                lastError = describeError(resp.code, text)
                                // A 402/401/403 is model- or key-specific: stop
                                // this candidate so the next model can be tried.
                                if (resp.code == 401 || resp.code == 402 ||
                                    resp.code == 403 || resp.code == 404
                                ) stopKey = true
                            }
                        }
                    } catch (e: Exception) {
                        lastError = "اتصال به هوش مصنوعی ناموفق بود. اینترنت را بررسی کن."
                    }
                    if (!stopKey && attempt < candidate.attempts - 1) {
                        Thread.sleep(1200L * (attempt + 1))
                    }
                }
            }
            if (lastError.contains("اعتبار") || lastError.contains("نامعتبر")) continue
        }
        Result.failure(AiException(lastError))
    }

    /** One model to try, how many times, and whether it is a free one. */
    private data class ModelAttempt(val model: String, val attempts: Int, val free: Boolean)

    /**
     * The model order for chat: the configured/verified model first, then free
     * models from the provider's catalogue. The free list is only used when the
     * primary cannot answer (paid model on a free account), so quality is kept
     * whenever it is available.
     */
    private fun textModelChain(provider: AiProvider): List<ModelAttempt> {
        val primary = ServiceKeys.openRouterTextModel()
            .ifBlank { provider.textModel }
        val chain = mutableListOf(ModelAttempt(primary, 2, free = false))
        for (m in freeTextModels()) {
            if (m != primary) chain += ModelAttempt(m, 1, free = true)
        }
        return chain
    }

    /**
     * Free text models reported by the provider's own catalogue. Cached after
     * the first lookup so chat does not fetch the list on every message.
     */
    @Volatile
    private var freeModelsCache: List<String>? = null

    private fun freeTextModels(): List<String> {
        freeModelsCache?.let { return it }
        val key = effectiveKey()
        if (key.isBlank()) return emptyList()
        val list = try {
            val url = activeProvider().modelsUrl() ?: return emptyList()
            val req = Request.Builder().url(url)
                .addHeader("Authorization", "Bearer $key")
                .addHeader("Accept", "application/json")
                .get().build()
            http.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return emptyList()
                val root = json.parseToJsonElement(resp.body?.string().orEmpty()).jsonObject
                val arr = root["data"]?.jsonArray ?: return emptyList()
                arr.mapNotNull { el ->
                    val obj = el.jsonObject
                    val id = obj["id"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
                    val p = obj["pricing"]?.jsonObject
                    val pr = p?.get("prompt")?.jsonPrimitive?.contentOrNull?.toDoubleOrNull() ?: 1.0
                    val co = p?.get("completion")?.jsonPrimitive?.contentOrNull?.toDoubleOrNull() ?: 1.0
                    val outMods = obj["architecture"]?.jsonObject
                        ?.get("output_modalities")?.jsonArray
                        ?.mapNotNull { it.jsonPrimitive.contentOrNull } ?: emptyList()
                    // Free, text-capable, and not a preview model that may vanish.
                    if (pr == 0.0 && co == 0.0 && outMods.contains("text")) id else null
                }
            }
        } catch (e: Exception) {
            emptyList()
        }
        freeModelsCache = list
        return list
    }

    // --------------------------------------------------------------- image

    /**
     * Text -> image.
     *
     * 1) Uses the provider's real image model, discovered from the live model
     *    catalogue, through the provider's own image path.
     * 2) If the provider has no image-capable model (or the account cannot pay
     *    for one), it does NOT claim the provider is broken. It tells the user
     *    exactly which model is unavailable and falls back to the free,
     *    key-less endpoint so a picture is still produced.
     */
    /**
     * Generates an image with OpenRouter itself, through its chat endpoint with
     * the `modalities` flag. This is only a tertiary route: OpenRouter's image
     * models need real account credit, so a free account returns 402 and the
     * image engine simply keeps using the other providers. A 402 here is an
     * account state, never reported as "provider disconnected".
     */
    suspend fun providerImage(prompt: String, seed: Int = (1..999_999).random()): ByteArray? =
        withContext(Dispatchers.IO) {
            lastImageError = ""
            val key = effectiveKey()
            if (key.isBlank()) {
                lastImageError = "کلید OpenRouter تنظیم نشده است."
                return@withContext null
            }
            val p = activeProvider()
            val caps = resolveImageModel(p, key)
            if (caps == null) {
                lastImageError = "هیچ مدل تصویری در فهرست OpenRouter پیدا نشد."
                return@withContext null
            }
            discoveredImageModel = caps.id
            discoveredImageInput = caps.imageInput
            val r = callImage(p, key, caps.id, prompt, null, null)
            val out = if (r.ok) r.value else {
                lastImageError = r.message
                null
            }
            refreshBalanceQuietly()
            out
        }

    /**
     * The image model OpenRouter's own catalogue reported, and whether it accepts
     * an input image. Screens with a Context read these after a call and persist
     * them, so capabilities are never guessed from a stale boolean.
     */
    @Volatile
    var discoveredImageModel: String = ""
        private set

    @Volatile
    var discoveredImageInput: Boolean = false
        private set

    /**
     * Real image request to OpenRouter. Returns the bytes and the provider's own
     * reason on failure. [sourceBase64] is set for image-to-image.
     */
    private suspend fun callImage(
        provider: AiProvider,
        key: String,
        model: String,
        prompt: String,
        sourceBase64: String?,
        sourceMime: String?
    ): CfLikeResult = withContext(Dispatchers.IO) {
        var lastErr = "ساخت تصویر با مدل «$model» ناموفق بود."
        var stop = false
        repeat(2) { attempt ->
            if (stop) return@repeat
            try {
                val userContent: kotlinx.serialization.json.JsonElement =
                    if (sourceBase64 != null && sourceMime != null) {
                        buildJsonArray {
                            add(buildJsonObject { put("type", "text"); put("text", prompt) })
                            add(buildJsonObject {
                                put("type", "image_url")
                                put("image_url", buildJsonObject {
                                    put("url", "data:$sourceMime;base64,$sourceBase64")
                                })
                            })
                        }
                    } else JsonPrimitive(prompt)

                val body = buildJsonObject {
                    put("model", model)
                    put("max_tokens", IMAGE_MAX_TOKENS)
                    put("messages", buildJsonArray {
                        add(buildJsonObject { put("role", "user"); put("content", userContent) })
                    })
                    if (provider.supportsImageModalities) {
                        put("modalities", buildJsonArray {
                            add(JsonPrimitive("image"))
                            add(JsonPrimitive("text"))
                        })
                    }
                }.toString()

                val req = Request.Builder()
                    .url(provider.chatUrl())
                    .addHeader("Authorization", "Bearer $key")
                    .addHeader("Content-Type", "application/json")
                    .post(body.toRequestBody("application/json".toMediaType()))
                    .build()

                http.newCall(req).execute().use { resp ->
                    val text = resp.body?.string().orEmpty()
                    if (!resp.isSuccessful) {
                        val detail = providerDetail(text)
                        lastErr = if (detail.isNotBlank()) "$model — $detail"
                        else describeError(resp.code, text, image = true)
                        if (resp.code == 401 || resp.code == 402 || resp.code == 403) stop = true
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
                            val bytes = Base64.getDecoder().decode(url.substringAfter("base64,", url))
                            if (bytes.size > 500) {
                                return@withContext CfLikeResult(true, lastErr, bytes)
                            }
                            lastErr = "تصویر خالی از مدل «$model» برگشت."
                        } else lastErr = "مدل «$model» تصویری برنگرداند."
                    }
                }
            } catch (e: Exception) {
                lastErr = "خطای شبکه در ساخت تصویر: ${networkReason(e)}"
            }
            if (!stop && attempt < 1) Thread.sleep(2500L)
        }
        CfLikeResult(false, lastErr, null)
    }

    /** Small result holder for the OpenRouter image path. */
    data class CfLikeResult(val ok: Boolean, val message: String, val value: ByteArray?)

    /**
     * Turns an OpenRouter error into an accurate Persian sentence. The 402 case
     * is checked carefully: OpenRouter uses 402 both for a genuinely empty
     * balance and for a request whose max_tokens exceeds the balance, so the
     * message must not claim the credit has run out unless it really has.
     */


    /**
     * Turns an OpenRouter error into an accurate Persian sentence. The 402 case
     * is checked carefully: OpenRouter uses 402 both for a genuinely empty
     * balance and for a request whose max_tokens exceeds the balance, so the
     * message must not claim the credit has run out unless it really has.
     */
    private fun describeError(code: Int, raw: String, image: Boolean = false): String {
        val low = raw.lowercase()
        return when (code) {
            401 -> "کلید هوش مصنوعی نامعتبر یا منقضی است. کلید را بررسی یا تعویض کن."
            402 -> when {
                low.contains("max_tokens") ->
                    "تنظیمات درخواست با موجودی سرویس هم‌خوان نبود. دوباره تلاش کن."
                low.contains("fewer max_tokens") ->
                    "طول پاسخ بیش از حد بود؛ درخواست کوتاه‌تر ارسال شد."
                else ->
                    "اعتبار سرویس کافی نیست. " +
                            if (image) "برای ساخت تصویر به شارژ حساب نیاز است."
                            else "برای ادامه گفتگو به شارژ حساب نیاز است."
            }
            403 -> "دسترسی این مدل برای کلید فعلی باز نیست."
            404, 400 -> when {
                low.contains("modalit") || low.contains("image") || low.contains("model") ->
                    "مدل فعلی قابلیت تولید تصویر ندارد؛ یک مدل تصویری فعال انتخاب کنید."
                else -> "مدل درخواستی در دسترس نیست."
            }
            429 -> "تعداد درخواست‌ها زیاد شده؛ چند لحظه بعد دوباره تلاش کن."
            in 500..599 -> "سرویس موقتاً پاسخ نمی‌دهد؛ دوباره تلاش کن."
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

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
 * Cloudflare Workers AI client.
 *
 * Runs a model through the official REST API:
 *   POST https://api.cloudflare.com/client/v4/accounts/{ACCOUNT_ID}/ai/run/{MODEL}
 * with `Authorization: Bearer {API_TOKEN}`.
 *
 * It is fully independent from OpenRouter: each service stores its own
 * credentials and reports its own state, so a fault in one never marks the
 * other as disconnected.
 */
object CloudflareClient {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(180, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    private const val API = "https://api.cloudflare.com/client/v4"

    /**
     * Models confirmed to work on this Workers AI REST path. Text-to-image
     * models return either raw image bytes or JSON with a base64 image; the
     * parser handles both. [imageInput] marks the models that accept a source
     * image, which is what image editing needs.
     */
    data class CfModel(
        val id: String,
        val label: String,
        val imageInput: Boolean = false,
        val returnsJson: Boolean = false
    )

    val MODELS: List<CfModel> = listOf(
        CfModel(
            id = "@cf/black-forest-labs/flux-1-schnell",
            label = "Flux 1 Schnell (سریع)",
            returnsJson = true
        ),
        CfModel(
            id = "@cf/bytedance/stable-diffusion-xl-lightning",
            label = "SDXL Lightning (متعادل)"
        ),
        CfModel(
            id = "@cf/lykon/dreamshaper-8-lcm",
            label = "DreamShaper 8 (هنری)"
        ),
        CfModel(
            id = "@cf/leonardo/phoenix-1.0",
            label = "Leonardo Phoenix (ویرایش تصویر)",
            imageInput = true
        )
    )

    /** The model used for text-to-image when the user has not chosen one. */
    val DEFAULT_MODEL: CfModel = MODELS.first()

    /** A text-to-image model that can also edit when none else is available. */
    fun editModel(): CfModel? = MODELS.firstOrNull { it.imageInput }

    fun modelById(id: String?): CfModel? = MODELS.firstOrNull { it.id == id }

    /** Where an image request goes. */
    private fun runUrl(accountId: String, model: String): String =
        "$API/accounts/$accountId/ai/run/$model"

    private fun searchUrl(accountId: String) =
        "$API/accounts/$accountId/ai/models/search?per_page=200"

    // ------------------------------------------------------------- state

    enum class CfFail { NONE, NO_CREDENTIALS, BAD_TOKEN, FORBIDDEN, RATE_LIMIT, CAPACITY, MODEL, NETWORK, PROVIDER }

    data class CfResult<T>(
        val ok: Boolean,
        val kind: CfFail,
        val message: String,
        val value: T? = null
    )

    /**
     * Tests the credentials with a real model request. A tiny real image is
     * requested from the catalogue so success means the model really ran.
     */
    suspend fun test(accountId: String, token: String, model: CfModel): CfResult<ByteArray> =
        withContext(Dispatchers.IO) {
            if (accountId.isBlank() || token.isBlank()) return@withContext CfResult(
                false, CfFail.NO_CREDENTIALS, "شناسه حساب و توکن لازم است."
            )
            val r = textToImage(accountId, token, model, "a small blue circle on white")
            if (r.ok) CfResult(true, CfFail.NONE, "✅ اتصال برقرار است", r.value)
            else CfResult(false, r.kind, r.message)
        }

    /** Lists the models the account can reach and that output images. */
    suspend fun listImageModels(accountId: String, token: String): List<String> =
        withContext(Dispatchers.IO) {
            if (accountId.isBlank() || token.isBlank()) return@withContext emptyList()
            try {
                val req = Request.Builder().url(searchUrl(accountId))
                    .addHeader("Authorization", "Bearer $token")
                    .addHeader("Accept", "application/json")
                    .get().build()
                http.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) return@withContext emptyList()
                    val root = json.parseToJsonElement(resp.body?.string().orEmpty()).jsonObject
                    val arr = root["result"]?.jsonArray ?: return@withContext emptyList()
                    arr.mapNotNull { el ->
                        val obj = el.jsonObject
                        val task = obj["task"]?.jsonObject?.get("name")?.jsonPrimitive?.contentOrNull
                        if (task != "Text-to-Image") return@mapNotNull null
                        obj["name"]?.jsonPrimitive?.contentOrNull
                    }
                }
            } catch (e: Exception) {
                emptyList()
            }
        }

    // ---------------------------------------------------------- generate

    /**
     * Text -> image. Sends the prompt to the chosen Cloudflare model and
     * returns real image bytes, whether the model answers with raw pixels or
     * with JSON containing a base64 image.
     */
    suspend fun textToImage(
        accountId: String,
        token: String,
        model: CfModel,
        prompt: String
    ): CfResult<ByteArray> = withContext(Dispatchers.IO) {
        call(accountId, token,
            runUrl(accountId, model.id),
            buildJsonObject { put("prompt", prompt) }.toString(),
            model
        )
    }

    /**
     * Image -> image (edit). Only models that accept an input image are used;
     * the caller checks [CfModel.imageInput] first. Returns the edited bytes.
     */
    suspend fun imageToImage(
        accountId: String,
        token: String,
        model: CfModel,
        prompt: String,
        sourceBase64: String
    ): CfResult<ByteArray> = withContext(Dispatchers.IO) {
        if (!model.imageInput) return@withContext CfResult(
            false, CfFail.MODEL, "مدل «${model.label}» قابلیت ویرایش تصویر ندارد."
        )
        call(accountId, token,
            runUrl(accountId, model.id),
            buildJsonObject {
                put("prompt", prompt)
                put("image", sourceBase64)
            }.toString(),
            model
        )
    }

    private fun call(
        accountId: String,
        token: String,
        url: String,
        body: String,
        model: CfModel
    ): CfResult<ByteArray> {
        if (accountId.isBlank() || token.isBlank()) {
            return CfResult(false, CfFail.NO_CREDENTIALS, "شناسه حساب و توکن لازم است.")
        }
        return try {
            val req = Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer $token")
                .addHeader("Content-Type", "application/json")
                .post(body.toRequestBody("application/json".toMediaType()))
                .build()
            http.newCall(req).execute().use { resp ->
                val bytes = resp.body?.bytes() ?: ByteArray(0)
                if (!resp.isSuccessful) {
                    val text = bytes.toString(Charsets.UTF_8)
                    return CfResult(false, cfClassify(resp.code, text), cfDescribe(resp.code, text))
                }
                // Raw pixels come back directly for some models.
                if (bytes.size > 500 && !looksLikeJson(bytes)) {
                    return CfResult(true, CfFail.NONE, "✅ تصویر ساخته شد", bytes)
                }
                // Others answer with JSON containing a base64 image.
                val text = bytes.toString(Charsets.UTF_8)
                val b64 = extractBase64Image(text)
                if (b64 != null) {
                    val raw = try { Base64.getDecoder().decode(b64) } catch (e: Exception) { null }
                    if (raw != null && raw.size > 500) {
                        return CfResult(true, CfFail.NONE, "✅ تصویر ساخته شد", raw)
                    }
                }
                CfResult(false, cfClassify(resp.code, text), cfDescribe(resp.code, text))
            }
        } catch (e: Exception) {
            CfResult(false, cfFailFromException(e), cfNetworkReason(e))
        }
    }

    private fun looksLikeJson(bytes: ByteArray): Boolean =
        bytes.isNotEmpty() && (bytes[0].toInt().toChar() == '{' ||
                bytes[0].toInt().toChar() == '[')

    /** Pulls the base64 image out of a `{ result: { image: "..." } }` reply. */
    private fun extractBase64Image(text: String): String? = try {
        json.parseToJsonElement(text).jsonObject["result"]?.jsonObject
            ?.get("image")?.jsonPrimitive?.contentOrNull
    } catch (e: Exception) {
        null
    }

    // -------------------------------------------------------------- errors

    private fun cfClassify(code: Int, raw: String): CfFail {
        val low = raw.lowercase()
        return when {
            code == 401 -> CfFail.BAD_TOKEN
            code == 403 -> CfFail.FORBIDDEN
            code == 429 -> CfFail.RATE_LIMIT
            // The free daily Neuron allocation being used up is a real quota
            // state, not a provider outage, so it is reported as rate-limited.
            low.contains("daily free allocation") || low.contains("free allocation") ||
                    low.contains("upgrade") || low.contains("neurons") -> CfFail.RATE_LIMIT
            // Cloudflare's own "out of capacity" / temporary capacity codes.
            code == 3040 || low.contains("capacity") || low.contains("out of capacity") ->
                CfFail.CAPACITY
            code == 400 && (low.contains("no route") || low.contains("model")) -> CfFail.MODEL
            code in 500..599 -> CfFail.PROVIDER
            else -> CfFail.PROVIDER
        }
    }

    private fun cfFailFromException(e: Exception): CfFail = CfFail.NETWORK

    /** Turns a Cloudflare error into an accurate Persian sentence. */
    private fun cfDescribe(code: Int, raw: String): String {
        val detail = cfDetail(raw)
        return when (cfClassify(code, raw)) {
            CfFail.BAD_TOKEN -> "شناسه حساب یا توکن Cloudflare نامعتبر است."
            CfFail.FORBIDDEN -> "دسترسی به این مدل با توکن فعلی باز نیست یا به پلن پولی نیاز دارد."
            CfFail.RATE_LIMIT -> {
                val low = raw.lowercase()
                if (low.contains("daily free allocation") || low.contains("neurons"))
                    "سهمیه رایگان روزانه Cloudflare (۱۰٬۰۰۰ Neuron) تمام شده است."
                else "تعداد درخواست‌های Cloudflare زیاد شده؛ کمی بعد دوباره تلاش کن."
            }
            CfFail.CAPACITY -> "ظرفیت مدل Cloudflare موقتاً پر است؛ دوباره تلاش کن."
            CfFail.MODEL -> "این مدل در حساب Cloudflare در دسترس نیست."
            CfFail.NO_CREDENTIALS -> "شناسه حساب و توکن Cloudflare لازم است."
            else -> if (detail.isNotBlank()) "خطای Cloudflare: $detail"
            else "خطای سرویس Cloudflare (کد $code)."
        }
    }

    /** Cloudflare's own error message, when it sends one. */
    private fun cfDetail(raw: String): String = try {
        json.parseToJsonElement(raw).jsonObject["errors"]?.jsonArray
            ?.firstOrNull()?.jsonObject?.get("message")?.jsonPrimitive?.contentOrNull.orEmpty()
    } catch (e: Exception) {
        ""
    }

    private fun cfNetworkReason(e: Exception): String = when (e) {
        is java.net.UnknownHostException -> "دامنه Cloudflare پیدا نشد؛ مشکل اینترنت."
        is java.net.SocketTimeoutException -> "زمان انتظار Cloudflare تمام شد؛ دوباره تلاش کن."
        is javax.net.ssl.SSLException -> "خطای امنیتی اتصال به Cloudflare."
        is java.net.ConnectException -> "اتصال به Cloudflare برقرار نشد."
        else -> "مشکل اتصال به Cloudflare (${e.javaClass.simpleName})."
    }

    /**
     * The real usage endpoint is not exposed for Workers AI REST, so no number
     * is invented. The UI shows the official daily free allowance instead.
     */
    const val FREE_DAILY_NEURONS = 10_000
}

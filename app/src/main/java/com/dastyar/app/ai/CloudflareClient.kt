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
import okhttp3.MultipartBody
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
        val returnsJson: Boolean = false,
        /**
         * True for real inpainting models: they take the source image plus a
         * mask and only regenerate the masked region, so everything the user
         * did not ask to change is preserved. This is the only honest
         * image-to-image path on Cloudflare.
         */
        val inpaint: Boolean = false,
        /**
         * True for the new FLUX.2 klein editing path: the model takes the source
         * photo plus a natural-language instruction as multipart form data
         * (`input_image_0` + `prompt`) and regenerates the whole frame, keeping
         * what the instruction does not ask to change. It has no mask, so the
         * parts to preserve must be named in the prompt.
         */
        val referenceEdit: Boolean = false,
        /**
         * Documented relative consumption. Cloudflare publishes per-model
         * Neuron rates in its pricing docs; when a rate is not published for a
         * model this stays null and the UI shows no number for it. It is never
         * guessed.
         */
        val neuronHint: String? = null
    )

    val MODELS: List<CfModel> = listOf(
        CfModel(
            id = "@cf/black-forest-labs/flux-2-klein-4b",
            label = "ویرایش تصویر (FLUX.2 klein)",
            imageInput = true,
            referenceEdit = true,
            neuronHint = "مدل یکپارچهٔ تولید و ویرایش؛ عکس ورودی را با دستور متنی ویرایش می‌کند"
        ),
        CfModel(
            id = "@cf/black-forest-labs/flux-1-schnell",
            label = "Flux 1 Schnell (سریع)",
            returnsJson = true,
            neuronHint = "مصرف نسبتاً پایین بین مدل‌های تصویری"
        ),
        CfModel(
            id = "@cf/bytedance/stable-diffusion-xl-lightning",
            label = "SDXL Lightning (متعادل)",
            neuronHint = "تک‌مرحله‌ای، مصرف پایین‌تر از SDXL کامل"
        ),
        CfModel(
            id = "@cf/lykon/dreamshaper-8-lcm",
            label = "DreamShaper 8 (هنری)",
            neuronHint = null
        ),
        CfModel(
            id = "@cf/runwayml/stable-diffusion-v1-5-inpainting",
            label = "ویرایش تصویر (Inpainting)",
            imageInput = true,
            inpaint = true,
            neuronHint = "برای ویرایش تصویر؛ فقط ناحیهٔ خواسته‌شده را عوض می‌کند"
        ),
        CfModel(
            id = "@cf/leonardo/phoenix-1.0",
            label = "Leonardo Phoenix (ویرایش تصویر)",
            imageInput = true,
            neuronHint = "برای ویرایش تصویر کاربرد دارد"
        )
    )

    /** The model used for text-to-image when the user has not chosen one. */
    val DEFAULT_MODEL: CfModel =
        MODELS.firstOrNull { it.id == "@cf/black-forest-labs/flux-1-schnell" } ?: MODELS.first()

    /**
     * Resolves the model to use for TEXT-TO-IMAGE from a stored id.
     *
     * The image-model catalog is shared by generation and editing, and the
     * FLUX.2 klein editor only accepts multipart form data (it is an
     * input-image model). If such a model were used for text-to-image, the
     * JSON request [textToImage] sends would be rejected and the picture would
     * not match the prompt. So a generation request is never routed to a
     * reference-edit model: it falls back to the default text-to-image model.
     */
    fun generateModel(id: String?): CfModel {
        val m = modelById(id)
        return if (m != null && !m.referenceEdit) m else DEFAULT_MODEL
    }

    /**
     * The model used for real image editing. Prefers the FLUX.2 klein reference
     * editor, which takes the user's photo plus a natural-language instruction
     * and can change the background, clothing colour or hair in one request.
     * Falls back to the older masked inpainting model only when the reference
     * editor is not listed. It is never a text-to-image model: sending the photo
     * nowhere and rendering a fresh scene is exactly the bug being avoided.
     */
    fun editModel(): CfModel? =
        MODELS.firstOrNull { it.referenceEdit } ?: MODELS.firstOrNull { it.inpaint }

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
        val value: T? = null,
        /** Real HTTP status, for the usage log. 0 when the call never left. */
        val http: Int = 0,
        /** Neurons the provider actually reported, when it reported any. */
        val neurons: Double? = null,
        /** True when the provider itself said the daily allowance is used up. */
        val quotaExhausted: Boolean = false
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
            if (r.ok) CfResult(
                true, CfFail.NONE, "✅ اتصال برقرار است",
                value = r.value, http = r.http, neurons = r.neurons
            )
            else CfResult(
                false, r.kind, r.message,
                http = r.http, neurons = r.neurons, quotaExhausted = r.quotaExhausted
            )
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
     *
     * A reference-edit model (FLUX.2 klein) is refused here: it only accepts
     * multipart form data with an input image, so this JSON text-to-image
     * request would fail. The caller resolves the model through [generateModel],
     * which already avoids that; this is the last-line guard.
     */
    suspend fun textToImage(
        accountId: String,
        token: String,
        model: CfModel,
        prompt: String
    ): CfResult<ByteArray> = withContext(Dispatchers.IO) {
        val safe = if (model.referenceEdit) DEFAULT_MODEL else model
        call(accountId, token,
            runUrl(accountId, safe.id),
            buildJsonObject { put("prompt", prompt) }.toString(),
            safe
        )
    }

    /**
     * Image -> image (edit). Only models that accept an input image are used;
     * the caller checks [CfModel.imageInput] first. Returns the edited bytes.
     *
     * For a true inpainting model the source image and a generated mask are
     * sent, so only the masked region is regenerated and the rest of the
     * picture is preserved.
     */
    suspend fun imageToImage(
        accountId: String,
        token: String,
        model: CfModel,
        prompt: String,
        sourceBase64: String,
        region: EditRegion = EditRegion.WHOLE
    ): CfResult<ByteArray> = withContext(Dispatchers.IO) {
        if (!model.imageInput) return@withContext CfResult(
            false, CfFail.MODEL, "مدل «${model.label}» قابلیت ویرایش تصویر ندارد."
        )
        // FLUX.2 klein reference editor: multipart with the source photo as
        // `input_image_0` (binary) and the instruction as `prompt`. No mask: the
        // parts to preserve are named in the prompt. The source must be a
        // smaller-than-512x512 image, which the caller prepares.
        if (model.referenceEdit) {
            val src = try { Base64.getDecoder().decode(sourceBase64) } catch (_: Exception) { null }
                ?: return@withContext CfResult(false, CfFail.MODEL, "تصویر پایه خوانده نشد.")
            return@withContext referenceEditCall(
                accountId, token, model, prompt, src
            )
        }
        // Real inpainting: decode the source, build a mask for the region the
        // user asked to change, and send both as byte arrays.
        if (model.inpaint) {
            val src = try { Base64.getDecoder().decode(sourceBase64) } catch (_: Exception) { null }
                ?: return@withContext CfResult(false, CfFail.MODEL, "تصویر پایه خوانده نشد.")
            val masked = ImageMasker.build(src, region)
                ?: return@withContext CfResult(false, CfFail.MODEL, "ساخت ماسک ویرایش ناموفق بود.")
            return@withContext call(
                accountId, token, runUrl(accountId, model.id),
                buildJsonObject {
                    put("prompt", prompt)
                    put("image", intArrayOfNode(masked.image))
                    put("mask", intArrayOfNode(masked.mask))
                    // The inpainting model accepts up to 20 steps and a negative
                    // prompt; at the maximum step count the requested change is
                    // actually applied instead of leaving the photo nearly
                    // untouched.
                    put("num_steps", 20)
                    put("negative_prompt", "blurry, low quality, unchanged, identical")
                }.toString(),
                model
            )
        }
        call(accountId, token,
            runUrl(accountId, model.id),
            buildJsonObject {
                put("prompt", prompt)
                put("image", sourceBase64)
            }.toString(),
            model
        )
    }

    /** Which part of the picture the edit is allowed to touch. */
    enum class EditRegion { SKY, BACKGROUND, WHOLE }

    /**
     * Cloudflare's inpainting endpoint takes the image and mask as a JSON array
     * of byte values, not as base64.
     */
    private fun intArrayOfNode(bytes: ByteArray): kotlinx.serialization.json.JsonArray =
        kotlinx.serialization.json.JsonArray(
            bytes.map { kotlinx.serialization.json.JsonPrimitive(it.toInt() and 0xFF) }
        )

    /**
     * FLUX.2 klein reference-edit call. The source photo is scaled below
     * 512x512 (the model's documented limit for input images) and sent as the
     * `input_image_0` multipart field; the instruction goes in `prompt`. The
     * response is JSON with a base64 image in `result.image`.
     */
    private fun referenceEditCall(
        accountId: String,
        token: String,
        model: CfModel,
        prompt: String,
        source: ByteArray
    ): CfResult<ByteArray> {
        if (accountId.isBlank() || token.isBlank()) {
            return CfResult(false, CfFail.NO_CREDENTIALS, "شناسه حساب و توکن لازم است.")
        }
        val prepared = ImageMasker.forReferenceEdit(source)
            ?: return CfResult(false, CfFail.MODEL, "عکس ورودی خوانده نشد.")
        return try {
            val body = MultipartBody.Builder().setType(MultipartBody.FORM)
                .addFormDataPart("prompt", prompt)
                .addFormDataPart(
                    "input_image_0", "source.png",
                    prepared.toRequestBody("image/png".toMediaType())
                )
                .addFormDataPart("width", "512")
                .addFormDataPart("height", "512")
                .build()
            val req = Request.Builder()
                .url(runUrl(accountId, model.id))
                .addHeader("Authorization", "Bearer $token")
                .post(body)
                .build()
            http.newCall(req).execute().use { resp ->
                val bytes = resp.body?.bytes() ?: ByteArray(0)
                val text = bytes.toString(Charsets.UTF_8)
                if (!resp.isSuccessful) {
                    val kind = cfClassify(resp.code, text)
                    return CfResult(
                        false, kind, cfDescribe(resp.code, text),
                        http = resp.code, neurons = extractNeurons(text),
                        quotaExhausted = kind == CfFail.RATE_LIMIT && text.lowercase().contains("neuron")
                    )
                }
                // Raw pixels for some models.
                if (bytes.size > 500 && !looksLikeJson(bytes)) {
                    return CfResult(true, CfFail.NONE, "✅ تصویر ویرایش شد", bytes,
                        http = resp.code, neurons = extractNeurons(text))
                }
                // JSON with a base64 image for this one.
                val b64 = extractBase64Image(text)
                if (b64 != null) {
                    val raw = try { Base64.getDecoder().decode(b64) } catch (e: Exception) { null }
                    if (raw != null && raw.size > 500) {
                        return CfResult(true, CfFail.NONE, "✅ تصویر ویرایش شد", raw,
                            http = resp.code, neurons = extractNeurons(text))
                    }
                }
                CfResult(false, cfClassify(resp.code, text), cfDescribe(resp.code, text),
                    http = resp.code, neurons = extractNeurons(text))
            }
        } catch (e: Exception) {
            CfResult(false, cfFailFromException(e), cfNetworkReason(e))
        }
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
                val text = bytes.toString(Charsets.UTF_8)
                if (!resp.isSuccessful) {
                    val kind = cfClassify(resp.code, text)
                    return CfResult(
                        false, kind, cfDescribe(resp.code, text),
                        http = resp.code, neurons = extractNeurons(text),
                        quotaExhausted = kind == CfFail.RATE_LIMIT && text.lowercase().contains("neuron")
                    )
                }
                // Raw pixels come back directly for some models.
                if (bytes.size > 500 && !looksLikeJson(bytes)) {
                    return CfResult(true, CfFail.NONE, "✅ تصویر ساخته شد", bytes,
                        http = resp.code, neurons = extractNeurons(text))
                }
                // Others answer with JSON containing a base64 image.
                val b64 = extractBase64Image(text)
                if (b64 != null) {
                    val raw = try { Base64.getDecoder().decode(b64) } catch (e: Exception) { null }
                    if (raw != null && raw.size > 500) {
                        return CfResult(true, CfFail.NONE, "✅ تصویر ساخته شد", raw,
                            http = resp.code, neurons = extractNeurons(text))
                    }
                }
                CfResult(false, cfClassify(resp.code, text), cfDescribe(resp.code, text),
                    http = resp.code, neurons = extractNeurons(text))
            }
        } catch (e: Exception) {
            CfResult(false, cfFailFromException(e), cfNetworkReason(e))
        }
    }

    /**
     * Neurons Cloudflare reported in the reply, if it reported any. Cloudflare's
     * image models normally do not return a Neuron figure, so this is usually
     * null and the UI must then say the exact usage is unavailable rather than
     * inventing a number.
     */
    private fun extractNeurons(text: String): Double? = try {
        val root = json.parseToJsonElement(text).jsonObject
        val usage = root["result"]?.jsonObject?.get("usage")?.jsonObject
        val v = usage?.get("neurons")?.jsonPrimitive?.contentOrNull?.toDoubleOrNull()
        v
    } catch (e: Exception) {
        null
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

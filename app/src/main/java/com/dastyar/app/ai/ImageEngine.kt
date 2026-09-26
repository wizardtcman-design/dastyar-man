package com.dastyar.app.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Decides which provider makes an image, in a fixed priority:
 *
 *   1. Cloudflare Workers AI (the app's main image engine)
 *   2. Pollinations        (automatic fallback)
 *   3. OpenRouter image    (only when the user's OpenRouter has real credit)
 *
 * The result always says which provider was used and, when the primary failed,
 * why it fell back -- the user is never told a service is down when it was only
 * out of credit.
 */
object ImageEngine {

    /** Which provider actually produced the bytes. */
    enum class Source { CLOUDFLARE, POLLINATIONS, OPENROUTER }

    data class ImageOutcome(
        val bytes: ByteArray,
        val source: Source,
        /** A short line describing the route, including a fallback reason. */
        val note: String
    )

    data class Failure(val messages: List<String>) {
        fun summary(): String =
            if (messages.isEmpty()) "فعلاً سرویس‌های تولید تصویر در دسترس نیستند."
            else "فعلاً سرویس‌های تولید تصویر در دسترس نیستند.\n" +
                    messages.joinToString("\n") { "• $it" }
    }

    /**
     * Text -> image with the Cloudflare-first priority.
     *
     * Each provider attempt is one real inference: there is no app-level retry
     * around Cloudflare, and once Cloudflare reports its daily quota is used up
     * it is not called again in this request (or for the rest of the day).
     */
    suspend fun generate(
        ctx: android.content.Context?,
        prompt: String,
        width: Int = 768,
        height: Int = 1024,
        seed: Int = (1..999_999).random()
    ): Result<ImageOutcome> = withContext(Dispatchers.IO) {
        if (prompt.isBlank()) return@withContext Result.failure(
            AiException("توصیف تصویر خالی است.")
        )
        val reasons = mutableListOf<String>()

        // 1) Cloudflare, only when connected and not already out of quota today.
        val cfModel = CloudflareClient.modelById(ServiceKeys.cloudflareModel())
            ?: CloudflareClient.DEFAULT_MODEL
        if (ServiceKeys.cloudflareReady() && !QuotaGuard.cloudflareExhaustedToday(ctx)) {
            val t0 = System.currentTimeMillis()
            val r = CloudflareClient.textToImage(
                accountId = ServiceKeys.cloudflareAccount(),
                token = ServiceKeys.cloudflareToken(),
                model = cfModel,
                prompt = prompt
            )
            UsageLog.record(
                ctx, UsageLog.Provider.CLOUDFLARE, cfModel.id, UsageLog.Kind.GENERATE,
                r.ok, r.http, System.currentTimeMillis() - t0, r.neurons, r.message
            )
            if (r.ok && r.value != null) {
                return@withContext Result.success(
                    ImageOutcome(r.value, Source.CLOUDFLARE, "تولید تصویر با Cloudflare AI")
                )
            }
            // A quota error marks Cloudflare used-up for today, so nothing else
            // in the app will spend the (already exhausted) allowance again.
            if (r.quotaExhausted) {
                QuotaGuard.markCloudflareExhausted(ctx)
                reasons += "Cloudflare: سهمیه رایگان امروز تمام شده است"
            } else reasons += "Cloudflare: ${r.message}"
        } else if (QuotaGuard.cloudflareExhaustedToday(ctx)) {
            reasons += "Cloudflare: سهمیه امروز تمام شده است"
        } else {
            reasons += "Cloudflare: متصل نشده است"
        }

        // 2) Pollinations fallback. The endpoint only understands English, so a
        // Persian prompt is translated first with the chat model.
        val english = toEnglishPrompt(prompt)
        val pt0 = System.currentTimeMillis()
        val p = PollinationsClient.image(english, width, height, seed, ServiceKeys.pollinationsKey())
        UsageLog.record(
            ctx, UsageLog.Provider.POLLINATIONS, "flux", UsageLog.Kind.GENERATE,
            p.ok, if (p.ok) 200 else 0, System.currentTimeMillis() - pt0, null, p.message
        )
        if (p.ok && p.value != null) {
            val why = if (QuotaGuard.cloudflareExhaustedToday(ctx))
                "Cloudflare امروز به سقف سهمیه رسیده؛ تصویر با Pollinations تولید شد."
            else "Cloudflare در دسترس نبود — تصویر با Pollinations تولید شد."
            return@withContext Result.success(ImageOutcome(p.value, Source.POLLINATIONS, why))
        }
        reasons += "Pollinations: ${p.message}"

        // 3) OpenRouter image, only when the user's account is known to have a
        // usable image model. A free account that only ever answered 402 for
        // images does not get another wasted inference on every request.
        if (AiClient.chatConfigured && ServiceKeys.openRouterSupportsImage() &&
            ServiceKeys.openRouterState() == ServiceKeys.State.CONNECTED
        ) {
            val o = AiClient.providerImage(prompt, seed)
            if (o != null) {
                UsageLog.record(
                    ctx, UsageLog.Provider.OPENROUTER, AiClient.discoveredImageModel,
                    UsageLog.Kind.GENERATE, true, 200, 0, null, ""
                )
                return@withContext Result.success(
                    ImageOutcome(o, Source.OPENROUTER, "تصویر با OpenRouter تولید شد.")
                )
            }
            reasons += "OpenRouter: ${AiClient.lastImageError.ifBlank { "در دسترس نیست" }}"
        }

        Result.failure(AiException(Failure(reasons).summary()))
    }

    /**
     * Image -> image. Cloudflare edits when the chosen model accepts an input
     * image; otherwise Pollinations is used as the fallback.
     */
    suspend fun edit(
        ctx: android.content.Context?,
        prompt: String,
        sourceBase64: String,
        mime: String = "image/png",
        seed: Int = (1..999_999).random()
    ): Result<ImageOutcome> = withContext(Dispatchers.IO) {
        if (prompt.isBlank()) return@withContext Result.failure(
            AiException("دستور ویرایش خالی است.")
        )
        if (sourceBase64.isBlank()) return@withContext Result.failure(
            AiException("تصویر پایه برای ویرایش پیدا نشد.")
        )
        val reasons = mutableListOf<String>()

        val model = CloudflareClient.modelById(ServiceKeys.cloudflareModel())
        if (ServiceKeys.cloudflareReady() && !QuotaGuard.cloudflareExhaustedToday(ctx)) {
            val editModel = when {
                model != null && model.imageInput -> model
                else -> CloudflareClient.editModel()
            }
            if (editModel != null && editModel.imageInput) {
                val t0 = System.currentTimeMillis()
                val r = CloudflareClient.imageToImage(
                    accountId = ServiceKeys.cloudflareAccount(),
                    token = ServiceKeys.cloudflareToken(),
                    model = editModel,
                    prompt = prompt,
                    sourceBase64 = sourceBase64
                )
                UsageLog.record(
                    ctx, UsageLog.Provider.CLOUDFLARE, editModel.id, UsageLog.Kind.EDIT,
                    r.ok, r.http, System.currentTimeMillis() - t0, r.neurons, r.message
                )
                if (r.ok && r.value != null) {
                    return@withContext Result.success(
                        ImageOutcome(
                            r.value,
                            Source.CLOUDFLARE,
                            "ویرایش تصویر با Cloudflare AI (${editModel.label})"
                        )
                    )
                }
                if (r.quotaExhausted) {
                    QuotaGuard.markCloudflareExhausted(ctx)
                    reasons += "Cloudflare: سهمیه رایگان امروز تمام شده است"
                } else reasons += "Cloudflare: ${r.message}"
            } else {
                reasons += "Cloudflare: مدل مناسبی برای ویرایش تصویر در دسترس نیست"
            }
        } else if (QuotaGuard.cloudflareExhaustedToday(ctx)) {
            reasons += "Cloudflare: سهمیه امروز تمام شده است"
        } else {
            reasons += "Cloudflare: متصل نشده است"
        }

        // Pollinations fallback: it takes a text prompt, so the edit request is
        // folded into a full description and rendered fresh.
        val english = toEnglishPrompt(prompt)
        val pt0 = System.currentTimeMillis()
        val p = PollinationsClient.image(english, 768, 1024, seed, ServiceKeys.pollinationsKey())
        UsageLog.record(
            ctx, UsageLog.Provider.POLLINATIONS, "flux", UsageLog.Kind.EDIT,
            p.ok, if (p.ok) 200 else 0, System.currentTimeMillis() - pt0, null, p.message
        )
        if (p.ok && p.value != null) {
            return@withContext Result.success(
                ImageOutcome(
                    p.value,
                    Source.POLLINATIONS,
                    "Cloudflare ویرایش نکرد — تصویر با Pollinations تولید شد."
                )
            )
        }
        reasons += "Pollinations: ${p.message}"

        Result.failure(AiException(Failure(reasons).summary()))
    }

    /** Turns a Persian prompt into English for the Pollinations endpoint. */
    private suspend fun toEnglishPrompt(prompt: String): String {
        val hasPersian = prompt.any { it in '\u0600'..'\u06FF' }
        if (!hasPersian) return prompt
        return try {
            AiClient.chat(
                system = "You translate image descriptions to English. " +
                        "Output only the English description, nothing else.",
                history = emptyList(),
                userMessage = prompt
            ).getOrNull()?.trim()?.takeIf { it.isNotBlank() && it.length < 400 } ?: prompt
        } catch (e: Exception) {
            prompt
        }
    }
}

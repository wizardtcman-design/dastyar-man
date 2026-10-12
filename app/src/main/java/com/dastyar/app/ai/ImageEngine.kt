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
        seed: Int = (1..999_999).random(),
        /**
         * An already-prepared English prompt. When the user has seen and possibly
         * edited the translated prompt, it is passed back here so the exact same
         * English text is sent -- it is never translated a second time or
         * rewritten behind the user's back. Null means "translate [prompt] now",
         * which is the normal one-tap path.
         */
        englishOverride: String? = null
    ): Result<ImageOutcome> = withContext(Dispatchers.IO) {
        if (prompt.isBlank() && englishOverride.isNullOrBlank()) return@withContext Result.failure(
            AiException("توصیف تصویر خالی است.")
        )
        val reasons = mutableListOf<String>()

        // Both image backends (Cloudflare flux and Pollinations flux) only
        // understand English: sending Persian makes the model invent a random
        // scene, or worse, try to draw the Persian letters. So the prompt is
        // translated ONCE here and the exact same English text is sent to
        // whichever provider runs -- never translated twice and never altered
        // beyond a faithful translation. When the user already reviewed and
        // edited the English prompt, that exact text is used instead.
        //
        // If the description is Persian and the translation did NOT succeed,
        // generation is stopped with a clear error. Sending the Persian text to
        // the image model would produce a picture unrelated to the request while
        // still reporting success -- exactly the bug this guards against.
        val englishPrompt = englishOverride?.takeIf { it.isNotBlank() }
            ?: toEnglishPrompt(prompt)
            ?: return@withContext Result.failure(
                AiException(
                    "ترجمهٔ توضیح فارسی به انگلیسی انجام نشد، پس تصویر درست ساخته نمی‌شود.\n" +
                        "اتصال هوش مصنوعی متن (کلید/سرویس) را بررسی کن و دوباره تلاش کن، " +
                        "یا از دکمهٔ «نمایش پرامپت انگلیسی» برای واردکردن پرامپت انگلیسی استفاده کن."
                )
            )

        // 1) Cloudflare, only when connected and not already out of quota today.
        // The stored model is resolved through generateModel(): if the account
        // has a reference-edit model (which only accepts multipart and needs an
        // input image) selected, generation must NOT use it, or the JSON request
        // would be rejected and the result would not match the prompt.
        val cfModel = CloudflareClient.generateModel(ServiceKeys.cloudflareModel())
        if (ServiceKeys.cloudflareReady() && !QuotaGuard.cloudflareExhaustedToday(ctx)) {
            val t0 = System.currentTimeMillis()
            val r = CloudflareClient.textToImage(
                accountId = ServiceKeys.cloudflareAccount(),
                token = ServiceKeys.cloudflareToken(),
                model = cfModel,
                prompt = englishPrompt
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

        // 2) Pollinations fallback. Reuses the same English prompt computed above
        // (Pollinations' endpoint only understands English too).
        val pt0 = System.currentTimeMillis()
        val p = PollinationsClient.image(englishPrompt, width, height, seed, ServiceKeys.pollinationsKey())
        UsageLog.record(
            ctx, UsageLog.Provider.POLLINATIONS, "flux", UsageLog.Kind.GENERATE,
            p.ok, if (p.ok) 200 else 0, System.currentTimeMillis() - pt0, null, p.message
        )
        if (p.ok && p.value != null) {
            val why = if (QuotaGuard.cloudflareExhaustedToday(ctx))
                "Cloudflare امروز به سقف سهمیه رسیده؛ تصویر با Pollinations تولید شد."
            else "Cloudflare تصویر نساخت (${reasons.firstOrNull()?.removePrefix("Cloudflare: ")}); " +
                    "این تصویر با Pollinations ساخته شده و ممکن است دقیقاً مطابق خواسته نباشد."
            return@withContext Result.success(ImageOutcome(p.value, Source.POLLINATIONS, why))
        }
        reasons += "Pollinations: ${p.message}"

        // 3) OpenRouter image, only when the user's account is known to have a
        // usable image model. A free account that only ever answered 402 for
        // images does not get another wasted inference on every request.
        if (AiClient.chatConfigured && ServiceKeys.openRouterSupportsImage() &&
            ServiceKeys.openRouterState() == ServiceKeys.State.CONNECTED
        ) {
            val o = AiClient.providerImage(englishPrompt, seed)
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

        // Same as generate(): the edit instruction is translated ONCE so the
        // exact same English reaches whichever provider runs. A raw Persian
        // instruction makes the model ignore the edit and render something else.
        // For an edit the translator is told this is an instruction about an
        // EXISTING photo, so it must keep every requested change and never
        // invent a new scene. If the translation is unavailable, the original
        // text is used (the edit path keeps its previous behaviour unchanged).
        val englishPrompt = toEnglishPrompt(prompt, forEdit = true) ?: prompt

        // Which slice of the picture the instruction is about, so the inpainting
        // model changes only that part and leaves the rest untouched.
        val region = editRegion(prompt)

        val model = CloudflareClient.modelById(ServiceKeys.cloudflareModel())
        if (ServiceKeys.cloudflareReady() && !QuotaGuard.cloudflareExhaustedToday(ctx)) {
            // A real edit needs a model that accepts the source image. Prefer
            // the FLUX.2 klein reference editor (one photo + a natural-language
            // instruction, can do several changes at once); fall back to the
            // older masked inpainting model only when it is not listed.
            val editModel = when {
                model != null && (model.referenceEdit || model.inpaint) -> model
                else -> CloudflareClient.editModel()
            }
            if (editModel != null && editModel.imageInput) {
                // The reference editor has no mask, so the parts to keep are
                // named in the prompt. The instruction is joined with a firm
                // "keep everything else" clause so the face and any detail the
                // user did not mention survive as far as the model allows.
                val editPrompt = if (editModel.referenceEdit)
                    "$englishPrompt, keep the person's face, identity and all parts not " +
                        "mentioned above exactly the same, do not change the subject"
                else englishPrompt
                val t0 = System.currentTimeMillis()
                val r = CloudflareClient.imageToImage(
                    accountId = ServiceKeys.cloudflareAccount(),
                    token = ServiceKeys.cloudflareToken(),
                    model = editModel,
                    prompt = editPrompt,
                    sourceBase64 = sourceBase64,
                    region = region
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

        // Second provider: OpenRouter's image model, when it accepts an input
        // image. It receives the SAME translated English instruction and the
        // user's ORIGINAL photo, so the edit is applied to that photo and not a
        // picture generated from scratch.
        if (AiClient.chatConfigured && ServiceKeys.openRouterSupportsImage() &&
            ServiceKeys.openRouterState() == ServiceKeys.State.CONNECTED
        ) {
            val o = AiClient.providerImageEdit(englishPrompt, sourceBase64, mime, seed)
            if (o != null) {
                UsageLog.record(
                    ctx, UsageLog.Provider.OPENROUTER, AiClient.discoveredImageModel,
                    UsageLog.Kind.EDIT, true, 200, 0, null, ""
                )
                return@withContext Result.success(
                    ImageOutcome(o, Source.OPENROUTER, "ویرایش تصویر با OpenRouter انجام شد.")
                )
            }
            reasons += "OpenRouter: ${AiClient.lastImageError.ifBlank { "در دسترس نیست" }}"
        }

        // Pollinations' image endpoint only does text-to-image, so it is not
        // used for edits: it would ignore the user's photo and return a
        // completely different picture, which is exactly the bug being fixed.
        Result.failure(AiException(Failure(reasons).summary()))
    }

    /**
     * Reads the user's instruction to decide which part of the picture the edit
     * is allowed to touch. "Only change the sky" must not repaint the ground;
     * "change the background" must keep the subject. Anything that names no
     * region is treated as a whole-image edit.
     */
    private fun editRegion(prompt: String): CloudflareClient.EditRegion {
        val p = prompt.lowercase()
        val sky = listOf("آسمان", "اسمون", "sky", "cloud", "ابر")
        val bg = listOf("پس‌زمینه", "پس زمینه", "زمینه", "background", "backdrop")
        return when {
            sky.any { p.contains(it) } -> CloudflareClient.EditRegion.SKY
            bg.any { p.contains(it) } -> CloudflareClient.EditRegion.BACKGROUND
            else -> CloudflareClient.EditRegion.WHOLE
        }
    }

    /**
     * Prepares the English prompt for [prompt] without generating an image, so
     * the user can see exactly what will be sent to the image model and edit it
     * if needed. A Persian prompt is translated+structured once; an English one
     * is returned as-is. This never touches the network beyond the translation
     * call and spends no image-generation quota.
     */
    suspend fun prepareEnglishPrompt(prompt: String): String {
        if (prompt.isBlank()) return ""
        return toEnglishPrompt(prompt).orEmpty()
    }

    /**
     * Turns the user's Persian description into a single English prompt that the
     * image models (Flux) can render faithfully.
     *
     * The old version translated the text *literally*, word for word. That is
     * correct for meaning but useless as an image prompt: a short Persian
     * sentence becomes a short English one ("cat"), and Flux then invents a whole
     * scene of its own. This version does two things in one pass:
     *
     *   1. TRANSLATE faithfully -- every subject, count, garment, colour, place,
     *      pose, action and relationship the user wrote is kept exactly.
     *   2. STRUCTURE the result as a prompt -- keep the explicit details and add
     *      only neutral rendering hints (lighting, camera, quality) that the user
     *      did NOT already mention and that do NOT contradict anything they said.
     *
     * The model is explicitly forbidden from inventing new subjects, changing how
     * many people/animals there are, or altering colours/clothing/setting, so a
     * "grey wolf next to a boy in a red jacket in snowy mountains" stays exactly
     * that and never becomes a random wolf.
     *
     * When [forEdit] is true the text is an instruction about an EXISTING photo,
     * not a fresh scene, so a separate system prompt is used: it asks the
     * translator to phrase the change(s) as an edit command and to keep every
     * requested change without turning it into a description of a new image.
     *
     * The same call is used for both Cloudflare and Pollinations, so a prompt is
     * processed exactly once per request and both providers receive the identical
     * English text.
     *
     * Returns the English prompt, or null when the text was Persian and the
     * translation did NOT produce usable English. Null is never swallowed into
     * the Persian original: the caller must stop rather than send Persian to an
     * English-only image model (that is what made pictures unrelated to the
     * request). A non-Persian prompt is returned unchanged.
     */
    private suspend fun toEnglishPrompt(prompt: String, forEdit: Boolean = false): String? {
        val hasPersian = prompt.any { it in '\u0600'..'\u06FF' }
        if (!hasPersian) return prompt
        val cleaned = try {
            AiClient.chat(
                system = if (forEdit) EDIT_PROMPT_ENGINEER_SYSTEM else PROMPT_ENGINEER_SYSTEM,
                history = emptyList(),
                userMessage = prompt
            ).getOrNull()
                ?.trim()
                ?.removeSurrounding("\"")
                // Keep the whole answer, not just the first line: a rich scene
                // is often written across a couple of lines.
                ?.lineSequence()
                ?.map { it.trim() }
                ?.filter { it.isNotBlank() }
                ?.joinToString(" ")
                ?.replace(Regex("\\s+"), " ")
                ?.trim()
                ?.takeIf { it.isNotBlank() && it.length < 700 }
        } catch (e: Exception) {
            null
        }
        // Only accept a result that is genuinely English: if the model echoed
        // Persian back, or answered in another script, sending it would make the
        // image model invent something random. If nothing usable came back, null
        // is returned so the caller reports a real error instead of generating a
        // picture that does not match the Persian request.
        val c = cleaned
        val looksEnglish = c != null && c.none { it in '\u0600'..'\u06FF' } &&
                c.count { it.isLetter() && it.code < 128 } >= 3
        return if (looksEnglish) c else null
    }

    /**
     * The instruction that turns the user's text into a faithful, rendered-ready
     * English prompt. It is deliberately strict about preservation: the model is
     * a translator and prompt-writer, never a storyteller. It may ONLY fill in
     * neutral photographic/style hints, and only when the user did not already
     * give them.
     */
    private const val PROMPT_ENGINEER_SYSTEM =
        "You convert a user's description into ONE English text-to-image prompt for the Flux model. " +
        "Translate the user's text into natural English, then write it as a clear image prompt.\n" +
        "HARD RULES - you must obey all of them:\n" +
        "1. PRESERVE every concrete detail the user gave: all subjects (people and animals), " +
        "their exact count, gender and age if stated, clothing and its colours, objects, " +
        "the setting/place, the pose, the action, the spatial relationships between subjects " +
        "(e.g. 'next to', 'looking at', 'behind'), the time of day, weather and mood. " +
        "Never drop, merge, swap or reorder these in a way that changes the meaning.\n" +
        "2. DO NOT invent new subjects, extra people/animals, or a different scene. " +
        "If the user says one wolf and one boy, there is exactly one wolf and one boy.\n" +
        "3. Keep the user's colours, clothing and environment EXACTLY as written." +
        " If the user says a red jacket, it is red.\n" +
        "4. You MAY add neutral rendering hints ONLY when the user did not specify them and " +
        "they do not contradict the user: e.g. lighting, camera angle, lens, depth of field, " +
        "photo-realistic/cinematic style, high detail. If the user already stated the style, " +
        "lighting or camera, use THEIRS and add nothing that conflicts.\n" +
        "5. Do not answer, explain, ask questions, or add commentary.\n" +
        "Output ONLY the final English prompt as a single line of comma-separated phrases, " +
        "no quotes, no labels, no notes."

    /**
     * The instruction that turns a user's EDIT command (about a photo they
     * already have) into one English editing prompt for the image model.
     *
     * It is a translator, not a scene writer: it must carry EVERY requested
     * change (background, clothing colour, hair, objects, and so on) into the
     * English text and must not drop any, while never describing a brand-new
     * scene. It is told the edit is applied to an existing photo, so parts the
     * user did not mention should stay.
     */
    private const val EDIT_PROMPT_ENGINEER_SYSTEM =
        "You convert a user's Persian instruction into ONE English image-EDITING prompt. " +
        "The prompt will be applied to a photo the user already has, so it must read as an " +
        "edit command, not as a description of a new scene.\n" +
        "HARD RULES - you must obey all of them:\n" +
        "1. CARRY every change the user asked for: change of background/scene, clothing or its " +
        "colour, hair style or colour, added/removed objects, pose or expression. Do not drop, " +
        "weaken or merge any requested change. If the user asked for three changes, all three " +
        "appear in the English prompt.\n" +
        "2. DO NOT invent new changes, new subjects, extra people, or a different scene. Only " +
        "translate and rephrase what the user asked.\n" +
        "3. Keep every specific detail the user gave (colours, items, places) EXACTLY: a red " +
        "jacket stays red, snowy mountains stay snowy mountains.\n" +
        "4. Write it as an instruction to the editor, e.g. 'replace the background with a snowy " +
        "mountain, change the jacket color to red'. It MUST NOT add an instruction to keep or " +
        "preserve the face or other parts; preservation is handled separately, so do not mention it.\n" +
        "5. Do not answer, explain, ask questions, or add commentary.\n" +
        "Output ONLY the final English editing prompt as a single line of comma-separated phrases, " +
        "no quotes, no labels, no notes."
}

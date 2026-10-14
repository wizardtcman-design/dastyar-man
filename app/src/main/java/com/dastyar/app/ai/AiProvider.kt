package com.dastyar.app.ai

/**
 * Which HTTP shape a provider's text endpoint speaks.
 *
 * [CHAT] is the classic OpenAI `/chat/completions` call (a `messages` array),
 * shared by OpenRouter, OpenAI, Groq, CodeCraft, Atria, Aion and most others.
 * [RESPONSES] is the newer OpenAI `/responses` call (an `instructions` string
 * plus an `input` array), used by kie.ai's cheapest text models.
 */
enum class ApiStyle { CHAT, RESPONSES }

/**
 * An AI service the app can talk to. Most providers expose an OpenAI-compatible
 * `/chat/completions` endpoint, which is the common denominator OpenRouter,
 * OpenAI, Groq, Together, DeepSeek and most others share; a few expose the newer
 * `/responses` endpoint instead ([ApiStyle.RESPONSES]).
 *
 * Adding a new provider is meant to be a one-line change to [AiProviders.all]:
 * no screen or feature code has to be touched.
 */
data class AiProvider(
    /** Stable id stored in preferences. */
    val id: String,
    /** Human-readable name shown in Settings. */
    val label: String,
    /** Base URL, without a trailing slash (the path is appended). */
    val baseUrl: String,
    /** Model used for text / chat. */
    val textModel: String,
    /** Model used for text-to-image; empty when the provider cannot make images. */
    val imageModel: String = "",
    /** Whether this provider understands the `modalities: [image, text]` flag. */
    val supportsImageModalities: Boolean = false,
    /** Whether the image model accepts an input image (image-to-image / edit). */
    val supportsImageEdit: Boolean = false,
    /**
     * Path (relative to [baseUrl]) of the provider's account-credit endpoint.
     * Empty when the provider exposes no balance over its API, in which case
     * the app must say so instead of inventing a number.
     */
    val creditsPath: String = "",
    /**
     * Path of a per-key usage/limit endpoint, used as a fallback source of the
     * real remaining balance when [creditsPath] is unavailable or returns zero.
     */
    val keyInfoPath: String = "",
    /**
     * Path of the model catalogue endpoint (`/models`). Used to discover which
     * models really output images and whether they accept image input, instead
     * of trusting a hard-coded boolean.
     */
    val modelsPath: String = "",
    /** Which request/response shape the text endpoint speaks. */
    val apiStyle: ApiStyle = ApiStyle.CHAT
) {
    val supportsImages: Boolean get() = imageModel.isNotBlank()

    fun chatUrl(): String = when (apiStyle) {
        ApiStyle.CHAT -> "${baseUrl.trimEnd('/')}/chat/completions"
        ApiStyle.RESPONSES -> "${baseUrl.trimEnd('/')}/responses"
    }

    fun creditsUrl(): String? =
        creditsPath.takeIf { it.isNotBlank() }?.let { "${baseUrl.trimEnd('/')}/$it" }

    fun keyInfoUrl(): String? =
        keyInfoPath.takeIf { it.isNotBlank() }?.let { "${baseUrl.trimEnd('/')}/$it" }

    fun modelsUrl(): String? =
        modelsPath.takeIf { it.isNotBlank() }?.let { "${baseUrl.trimEnd('/')}/$it" }
}

object AiProviders {

    /**
     * OpenRouter is the app's primary provider. Its image models are reached
     * through the same `/chat/completions` endpoint with `modalities`, which is
     * how OpenRouter actually returns images (its `/images` endpoint requires
     * purchased credit). Capabilities are re-derived from `/models` at runtime.
     */
    val openRouter = AiProvider(
        id = "openrouter",
        label = "OpenRouter",
        baseUrl = "https://openrouter.ai/api/v1",
        textModel = "google/gemini-2.5-flash-lite",
        imageModel = "google/gemini-2.5-flash-image",
        supportsImageModalities = true,
        supportsImageEdit = true,
        creditsPath = "credits",
        keyInfoPath = "key",
        modelsPath = "models"
    )

    /**
     * CodeCraft API is the second text provider the user can pick. It is
     * OpenAI-compatible and exposes a model catalogue, but no image generation:
     * every model it lists outputs text only, so it is offered for chat/text
     * features and never for images.
     */
    val codeCraft = AiProvider(
        id = "codecraft",
        label = "CodeCraft API",
        baseUrl = "https://codecraftapi.com/v1",
        textModel = "gemini-3.6-flash",
        modelsPath = "models"
    )

    /**
     * Atria ASI (Atria Dawn Preview) is the third text provider. It is
     * OpenAI-compatible (`/v1/chat/completions`, `Authorization: Bearer`), so it
     * is served entirely by the app's shared text path. It exposes exactly one
     * model ([textModel]) and is text-only -- it accepts no image input and
     * produces no images, so it is offered for chat/text features only and never
     * for image generation, which stays on Cloudflare / OpenRouter / Pollinations.
     */
    val atria = AiProvider(
        id = "atria",
        label = "Atria (Atria-Dawn-Preview)",
        baseUrl = "https://api.atria-asi.ai/v1",
        textModel = "Atria-Dawn-Preview"
    )

    /**
     * Groq Cloud is the fourth text provider. It is OpenAI-compatible
     * (`/openai/v1/chat/completions`, `Authorization: Bearer`), so it is served
     * entirely by the app's shared text path. It is text-only -- no image
     * models -- so it is offered for chat/text features only.
     *
     * The main model is the larger, higher-quality GPT-OSS 120B; the app also
     * carries the faster GPT-OSS 20B as a same-provider fallback for when the
     * main model is rate-limited (HTTP 429) or busy. Both are listed in
     * [groqModels] so the picker can offer them without any other change.
     */
    val groq = AiProvider(
        id = "groq",
        label = "Groq (GPT-OSS)",
        baseUrl = "https://api.groq.com/openai/v1",
        textModel = "openai/gpt-oss-120b",
        modelsPath = "models"
    )

    /**
     * The models the Groq card offers, main first. The fallback model is a
     * normal, user-selectable choice here; the app additionally uses it
     * automatically when the main model is rate-limited.
     */
    val groqModels = listOf(
        "openai/gpt-oss-120b" to "GPT-OSS 120B (دقیق‌تر)",
        "openai/gpt-oss-20b" to "GPT-OSS 20B (سریع‌تر)"
    )

    /**
     * Aion Labs is the fifth text provider. It is OpenAI-compatible
     * (`/chat/completions`, `Authorization: Bearer`, `/models`) and text-only,
     * so it is served entirely by the shared text path and is offered for
     * chat/text features only.
     *
     * The model is the live catalogue's general-purpose "Aion 2.0" (a DeepSeek
     * V3.2 variant). It was verified with a real Persian chat request: it follows
     * the app's marker formats (recipes, structured lines) and stays within the
     * app's token budget. Its reasoning runs in a hidden field and is not shown.
     */
    val aion = AiProvider(
        id = "aion",
        label = "Aion Labs (Aion 2.0)",
        baseUrl = "https://api.aionlabs.ai/v1",
        textModel = "aion-labs/aion-2.0",
        modelsPath = "models"
    )

    /**
     * kie.ai is the sixth text provider: an aggregator that resells many model
     * families (Gemini, Claude, GPT, Grok, Kimi, DeepSeek) far below list price
     * and is by far the cheapest option for text. Its cheapest text model,
     * GPT-6 Luna, is not on the classic `/chat/completions` route but on the
     * OpenAI **Responses** API under `/openai/v1/responses`, so this provider
     * carries [ApiStyle.RESPONSES] and the app sends `instructions` + `input`
     * instead of `messages`. Verified with a real Persian cooking request: it
     * follows the app's marker format and stays inside the token budget.
     * Text-only -- it is offered for chat/text features and never for images.
     */
    val kie = AiProvider(
        id = "kie",
        label = "kie.ai (GPT-6 Luna)",
        baseUrl = "https://api.kie.ai/openai/v1",
        textModel = "gpt-6-luna",
        apiStyle = ApiStyle.RESPONSES
    )

    /**
     * Cloudflare Workers AI as a text provider, serving Google's Gemma 4
     * (`@cf/google/gemma-4-26b-a4b-it`), the model verified to answer Persian
     * cleanly. It is an independent Cloudflare account: its Account ID + API
     * Token are stored separately from the image account, so text never competes
     * with image generation and the image settings/quota are left untouched.
     *
     * Text is reached through Cloudflare's OpenAI-compatible route
     * (`/accounts/{id}/ai/v1/chat/completions`). The account id is part of the
     * path, so [baseUrl] is only a prefix and the real URL is built at request
     * time from the stored account. Text-only for the app's purposes.
     */
    val cloudflareText = AiProvider(
        id = "cloudflare_text",
        label = "Cloudflare (Gemma 4)",
        baseUrl = "https://api.cloudflare.com/client/v4/accounts",
        textModel = "@cf/google/gemma-4-26b-a4b-it"
    )

    /** Generic OpenAI-compatible endpoint, used for any user-supplied key. */
    val openAiCompatible = AiProvider(
        id = "openai_compatible",
        label = "سرویس سازگار با OpenAI",
        baseUrl = "https://api.openai.com/v1",
        textModel = "gpt-4o-mini"
    )

    /**
     * Providers that can serve chat/text features, in the order shown to the
     * user. Keeping this list short and explicit means the picker never offers a
     * provider that cannot do the job.
     */
    val textProviders: List<AiProvider> =
        listOf(kie, openRouter, cloudflareText, codeCraft, atria, groq, aion)

    val all: List<AiProvider> =
        listOf(kie, openRouter, cloudflareText, codeCraft, atria, groq, aion, openAiCompatible)

    fun byId(id: String?): AiProvider =
        all.firstOrNull { it.id == id } ?: openRouter
}

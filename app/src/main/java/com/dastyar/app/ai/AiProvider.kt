package com.dastyar.app.ai

/**
 * An AI service the app can talk to. Every provider exposes an OpenAI-compatible
 * `/chat/completions` endpoint, which is the common denominator OpenRouter,
 * OpenAI, Groq, Together, DeepSeek and most others share.
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
    val modelsPath: String = ""
) {
    val supportsImages: Boolean get() = imageModel.isNotBlank()

    fun chatUrl(): String = "${baseUrl.trimEnd('/')}/chat/completions"

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
    val textProviders: List<AiProvider> = listOf(openRouter, codeCraft)

    val all: List<AiProvider> = listOf(openRouter, codeCraft, openAiCompatible)

    fun byId(id: String?): AiProvider =
        all.firstOrNull { it.id == id } ?: openRouter
}

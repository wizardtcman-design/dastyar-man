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
    val supportsImageModalities: Boolean = false
) {
    val supportsImages: Boolean get() = imageModel.isNotBlank()

    fun chatUrl(): String = "${baseUrl.trimEnd('/')}/chat/completions"
}

object AiProviders {

    val openRouter = AiProvider(
        id = "openrouter",
        label = "OpenRouter",
        baseUrl = "https://openrouter.ai/api/v1",
        textModel = "google/gemini-2.5-flash-lite",
        imageModel = "google/gemini-2.5-flash-image",
        supportsImageModalities = true
    )

    /** Generic OpenAI-compatible endpoint, used for any user-supplied key. */
    val openAiCompatible = AiProvider(
        id = "openai_compatible",
        label = "سرویس سازگار با OpenAI",
        baseUrl = "https://api.openai.com/v1",
        textModel = "gpt-4o-mini"
    )

    val all: List<AiProvider> = listOf(openRouter, openAiCompatible)

    fun byId(id: String?): AiProvider =
        all.firstOrNull { it.id == id } ?: openRouter
}

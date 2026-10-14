package com.dastyar.app.ai

/**
 * Which HTTP shape a provider's text endpoint speaks.
 *
 * [CHAT] is the classic OpenAI `/chat/completions` call (a `messages` array),
 * used by 1xAI, OpenRouter, OpenAI, Groq and Atria.
 * [RESPONSES] is the newer OpenAI `/responses` call (an `instructions` string
 * plus an `input` array); the machinery is kept for any provider that needs it.
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
    val apiStyle: ApiStyle = ApiStyle.CHAT,
    /**
     * The models the chat picker offers for this provider, as (id, label) pairs
     * with the recommended default first. A short, curated list is used on
     * purpose: it keeps the picker usable on a phone and only lists models that
     * were checked against the provider's own catalogue. Empty means the
     * provider has a single model ([textModel]) and no chooser is shown.
     */
    val modelOptions: List<Pair<String, String>> = emptyList()
) {
    val supportsImages: Boolean get() = imageModel.isNotBlank()

    /**
     * The models offered in the chat model picker: the curated [modelOptions]
     * when present, otherwise just this provider's single [textModel].
     */
    val pickerModels: List<Pair<String, String>>
        get() = if (modelOptions.isNotEmpty()) modelOptions else listOf(textModel to textModel)

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
        modelsPath = "models",
        modelOptions = listOf(
            "google/gemini-2.5-flash-lite" to "Gemini 2.5 Flash Lite (پیش‌فرض، ارزان)",
            "google/gemini-2.5-flash" to "Gemini 2.5 Flash",
            "openai/gpt-4o-mini" to "GPT-4o mini"
        )
    )

    /**
     * 1xAI (1xai.ir) is the app's main OpenAI-compatible text provider: a
     * Toman-billed proxy that reaches OpenAI, Anthropic, Google and DeepSeek
     * models from inside Iran (no VPN, no foreign card), at the provider's USD
     * list price + a flat 20% markup. It is one key and one base URL
     * (`https://1xai.ir/v1`), and it also exposes `/v1/models` and
     * `/v1/usage/summary`.
     *
     * The default is `gemini-2.5-flash-lite`, the cheapest current chat model on
     * 1xAI's own catalogue (≈۳۱٬۰۰۰ تومان ورودی / ۱۲۳٬۰۰۰ تومان خروجی per 1M
     * tokens) with a 1M-token context and solid Persian. Every model in
     * [modelOptions] was read from the official list at https://1xai.ir/models —
     * all are cheap, Persian-capable chat models. Text-only for the app.
     */
    val oneXai = AiProvider(
        id = "1xai",
        label = "1xAI",
        baseUrl = "https://1xai.ir/v1",
        textModel = "gemini-2.5-flash-lite",
        modelsPath = "models",
        modelOptions = listOf(
            "gemini-2.5-flash-lite" to "Gemini 2.5 Flash Lite — پیش‌فرض، ارزان‌ترین مناسب",
            "gemini-3.1-flash-lite" to "Gemini 3.1 Flash Lite — ارزان و نسل جدید",
            "gemini-2.5-flash" to "Gemini 2.5 Flash — سریع و متعادل",
            "gemma-4-26b-a4b-it" to "Gemma 4 26B — ارزان، فارسی روان",
            "gemma-4-31b-it" to "Gemma 4 31B — ارزان، فارسی روان",
            "gpt-4o-mini" to "GPT-4o mini — اقتصادی و خوش‌کیفیت",
            "gpt-5-nano" to "GPT-5 nano — ارزان‌ترین OpenAI",
            "gpt-5.4-mini" to "GPT-5.4 mini — سریع‌تر و باکیفیت",
            "deepseek-chat" to "DeepSeek Chat — ارزان با فارسی خوب",
            "claude-haiku-4-5" to "Claude Haiku 4.5 — سریع و ارزان"
        )
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
        modelsPath = "models",
        modelOptions = groqModels
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
        listOf(oneXai, openRouter, cloudflareText, atria, groq)

    val all: List<AiProvider> =
        listOf(oneXai, openRouter, cloudflareText, atria, groq, openAiCompatible)

    fun byId(id: String?): AiProvider =
        all.firstOrNull { it.id == id } ?: openRouter
}

package com.dastyar.app.ai

import android.content.Context

/**
 * Holds an optional user-supplied key and provider. Everything is only stored
 * on the device (private SharedPreferences); no key is ever written to source
 * or put in a public file. When a user adds a key it wins over the built-in one
 * and the app keeps working without editing any code.
 */
object ApiKeys {
    private const val PREFS = "dastyar_api_keys"
    private const val KEY_ALT = "alt_key"
    private const val KEY_PROVIDER = "alt_provider_id"
    private const val KEY_BASE = "alt_base_url"
    private const val KEY_TEXT_MODEL = "alt_text_model"
    private const val KEY_IMAGE_MODEL = "alt_image_model"

    @Volatile
    private var cachedProvider: AiProvider? = null

    @Volatile
    private var cachedKey: String? = null

    /** The user's chosen provider, or null when the built-in default is used. */
    fun userProvider(): AiProvider? = cachedProvider

    /** The user's stored key, or null when none was entered. */
    fun userKey(): String? = cachedKey

    /**
     * Saves a user-supplied key together with the provider it belongs to. The
     * endpoint/model come from [provider], so a future provider needs no new UI.
     */
    fun saveUserKey(ctx: Context, key: String, provider: AiProvider) {
        val clean = key.trim()
        if (clean.isBlank()) return
        cachedKey = clean
        cachedProvider = provider.copy()
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_ALT, clean)
            .putString(KEY_PROVIDER, provider.id)
            .putString(KEY_BASE, provider.baseUrl)
            .putString(KEY_TEXT_MODEL, provider.textModel)
            .putString(KEY_IMAGE_MODEL, provider.imageModel)
            .apply()
    }

    fun clearUserKey(ctx: Context) {
        cachedKey = null
        cachedProvider = null
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .remove(KEY_ALT).remove(KEY_PROVIDER).remove(KEY_BASE)
            .remove(KEY_TEXT_MODEL).remove(KEY_IMAGE_MODEL).apply()
    }

    /** The stored alternate key (reads preferences directly), or null. */
    fun storedKey(ctx: Context): String? =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_ALT, null)

    fun load(ctx: Context) {
        val p = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val key = p.getString(KEY_ALT, null)?.trim()?.ifBlank { null }
        val base = p.getString(KEY_BASE, null)?.trim()
        val textModel = p.getString(KEY_TEXT_MODEL, null)?.trim()
        cachedKey = key
        cachedProvider = if (key != null && base != null && textModel != null) {
            AiProvider(
                id = p.getString(KEY_PROVIDER, "openai_compatible") ?: "openai_compatible",
                label = AiProviders.byId(p.getString(KEY_PROVIDER, null)).label,
                baseUrl = base,
                textModel = textModel,
                imageModel = p.getString(KEY_IMAGE_MODEL, "").orEmpty()
            )
        } else null
    }
}

package com.dastyar.app.ai

import android.content.Context

/**
 * Holds an optional user-supplied OpenRouter key. It is only stored on the
 * device (private SharedPreferences) and is used instead of the app's built-in
 * key when the user adds one from Settings. No key is ever written to source.
 */
object ApiKeys {
    private const val PREFS = "dastyar_api_keys"
    private const val KEY_ALT = "alt_openrouter_key"

    @Volatile
    private var cached: String? = null

    fun userKey(): String? = cached

    fun saveUserKey(ctx: Context, key: String) {
        cached = key.trim().ifBlank { null }
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY_ALT, cached).apply()
    }

    fun clearUserKey(ctx: Context) {
        cached = null
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().remove(KEY_ALT).apply()
    }

    /** Loads the persisted key into memory. Called once at startup. */
    fun load(ctx: Context) {
        cached = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_ALT, null)?.trim()?.ifBlank { null }
    }
}

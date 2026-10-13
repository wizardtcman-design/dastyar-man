package com.dastyar.app.ai

import android.content.Context

/**
 * Storage for the relay (gateway) settings: whether to use the direct path or a
 * relay, the relay's base URL, and the optional shared secret it expects.
 *
 * Nothing here is secret-by-default except the gateway token, which is kept in
 * the same encrypted store the AI keys use. The provider keys themselves are
 * never moved or duplicated by this class.
 */
object GatewayPrefs {
    private const val PREFS = "dastyar_services"
    private const val KEY_MODE = "gw_mode"
    private const val KEY_ENDPOINT = "gw_endpoint"
    private const val KEY_TOKEN = "gw_token"

    private fun p(ctx: Context) =
        com.dastyar.app.security.SecurePrefs.get(ctx, PREFS)

    /** "DIRECT" or "PROXY". Anything else falls back to DIRECT at the caller. */
    fun mode(ctx: Context): String = p(ctx).getString(KEY_MODE, "DIRECT") ?: "DIRECT"

    fun setMode(ctx: Context, mode: String) {
        p(ctx).edit().putString(KEY_MODE, mode).apply()
    }

    /** The relay base URL, e.g. "https://relay.example.com". Empty = unset. */
    fun endpoint(ctx: Context): String = p(ctx).getString(KEY_ENDPOINT, "") ?: ""

    fun setEndpoint(ctx: Context, url: String) {
        p(ctx).edit().putString(KEY_ENDPOINT, url.trim()).apply()
    }

    /** The shared gateway token, if any. Stored encrypted, never logged. */
    fun token(ctx: Context): String = p(ctx).getString(KEY_TOKEN, "") ?: ""

    fun setToken(ctx: Context, token: String) {
        val t = token.trim()
        if (t.isBlank()) p(ctx).edit().remove(KEY_TOKEN).apply()
        else p(ctx).edit().putString(KEY_TOKEN, t).apply()
    }

    /** Whether the user has configured a relay endpoint at all. */
    fun hasEndpoint(ctx: Context): Boolean = endpoint(ctx).isNotBlank()
}

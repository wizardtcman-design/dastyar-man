package com.dastyar.app.ai

import android.content.Context

/**
 * The single, central place every AI request goes through to reach a provider.
 *
 * The app talks to OpenAI-compatible providers (OpenRouter, Groq, CodeCraft,
 * Atria, and any added later) plus the image providers. On some networks those
 * hosts are blocked, so the app must be able to send the exact same request
 * through a secure relay instead. Rather than scatter that logic across every
 * screen and client, the whole app asks the gateway two questions:
 *
 *   1. [resolveUrl]  -- which absolute URL should this request go to?
 *   2. [applyAuth]   -- which auth headers should it carry?
 *
 * In [Mode.DIRECT] (the default) both answers are the provider's own URL and the
 * user's provider key, so behaviour is byte-for-byte what it was before this
 * file existed. In [Mode.PROXY] the URL points at the user's own relay and the
 * request is authorized with an optional gateway token, so no provider key ever
 * has to be embedded in the app.
 *
 * Adding a new provider needs no networking code: give it a base URL, register
 * it in [AiProviders], and the gateway routes it like every other one.
 */
object ProviderGateway {

    enum class Mode { DIRECT, PROXY }

    /**
     * The Application context, captured once from [DastyarApp]. Only the
     * application context is held (never an Activity), for the lifetime of the
     * process, which is safe and lets the gateway answer URL/auth questions
     * without threading a Context through every existing client method.
     */
    @Volatile
    private var app: Context? = null

    /** Called once from Application.onCreate. */
    fun init(context: Context) {
        app = context.applicationContext
        // Re-apply the saved settings immediately so the first request is right.
        reloadSettings()
    }

    @Volatile private var modeCache: Mode = Mode.DIRECT
    @Volatile private var endpointCache: String = ""
    @Volatile private var tokenCache: String = ""

    /** Re-reads the saved settings from disk into the in-memory cache. */
    fun reloadSettings() {
        val ctx = app ?: return
        modeCache = runCatching { Mode.valueOf(GatewayPrefs.mode(ctx)) }
            .getOrDefault(Mode.DIRECT)
        endpointCache = GatewayPrefs.endpoint(ctx).trimEnd('/')
        tokenCache = GatewayPrefs.token(ctx)
    }

    /** True when a relay is selected and configured well enough to use. */
    fun proxyReady(): Boolean =
        modeCache == Mode.PROXY && endpointCache.startsWith("https://")

    private val ctx: Context? get() = app

    private fun mode(): Mode = modeCache

    /** The user's relay base URL, normalized without a trailing slash. */
    fun endpoint(): String = endpointCache

    /** The optional shared secret the relay expects, if the user set one. */
    fun token(): String = tokenCache

    /** Context-based helpers kept for the settings UI. */
    fun mode(ctx: Context): Mode =
        runCatching { Mode.valueOf(GatewayPrefs.mode(ctx)) }.getOrDefault(Mode.DIRECT)
    fun endpoint(ctx: Context): String = GatewayPrefs.endpoint(ctx).trimEnd('/')
    fun token(ctx: Context): String = GatewayPrefs.token(ctx)
    fun proxyReady(ctx: Context): Boolean =
        mode(ctx) == Mode.PROXY && endpoint(ctx).startsWith("https://")

    /**
     * Only these absolute-path prefixes may be relayed. This keeps the gateway
     * a purpose-built AI relay rather than an open proxy: a request to any other
     * host is never sent through the relay.
     */
    private val RELAYABLE_HOSTS = setOf(
        "openrouter.ai",
        "api.groq.com",
        "codecraftapi.com",
        "api.atria-asi.ai"
    )

    /**
     * The absolute URL a request to [directUrl] should actually go to.
     *
     * In DIRECT mode this is [directUrl] unchanged. In PROXY mode the host is
     * checked against the allow-list and, when allowed, the same path and query
     * are sent to the relay under a "/proxy/{host}{path}" prefix; the relay
     * turns it back into exactly the original request. A non-relayable URL is
     * returned unchanged, so a blocked provider can never be silently
     * redirected somewhere unexpected.
     */
    fun resolveUrl(directUrl: String): String {
        if (!proxyReady()) return directUrl
        val uri = runCatching { java.net.URI(directUrl) }.getOrNull() ?: return directUrl
        val host = uri.host ?: return directUrl
        if (host !in RELAYABLE_HOSTS) return directUrl

        val path = uri.rawPath ?: "/"
        val query = uri.rawQuery?.let { "?$it" } ?: ""
        return endpointCache + "/proxy/" + host + path + query
    }

    /** Context-based variant, for code paths that already hold a Context. */
    fun resolveUrl(c: Context, directUrl: String): String {
        // Prefer the live cache so a just-saved setting is honoured at once.
        if (app != null) return resolveUrl(directUrl)
        val mode = mode(c)
        if (mode != Mode.PROXY) return directUrl
        val base = endpoint(c)
        if (!base.startsWith("https://")) return directUrl
        val uri = runCatching { java.net.URI(directUrl) }.getOrNull() ?: return directUrl
        val host = uri.host ?: return directUrl
        if (host !in RELAYABLE_HOSTS) return directUrl
        val path = uri.rawPath ?: "/"
        val query = uri.rawQuery?.let { "?$it" } ?: ""
        return base + "/proxy/" + host + path + query
    }

    /**
     * Applies authentication to a request builder according to the active mode.
     *
     * DIRECT keeps the provider's own `Authorization: Bearer <providerKey>`.
     * PROXY never sends the provider key; it sends only the optional gateway
     * token, so a provider key can stay entirely on the server side.
     */
    fun applyAuth(builder: okhttp3.Request.Builder, providerKey: String?) {
        if (proxyReady()) {
            // The relay authenticates the app with its own token. The provider
            // key is deliberately NOT attached: the relay holds it in its own
            // secret store.
            if (tokenCache.isNotBlank()) builder.addHeader("X-Gateway-Token", tokenCache)
            builder.addHeader("X-Gateway-Mode", "relay")
        } else if (!providerKey.isNullOrBlank()) {
            builder.addHeader("Authorization", "Bearer $providerKey")
        }
    }
}

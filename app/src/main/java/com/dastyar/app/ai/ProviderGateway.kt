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

    /**
     * Runs a real, read-only probe against the configured relay and returns a
     * short human report (never a secret). It answers the only question the UI
     * cannot: does THIS phone actually reach the relay, and what does the relay
     * say back? It distinguishes "cannot reach the relay at all" (a network
     * block) from a relay auth/allow-list rejection, so a failure is no longer
     * a silent generic message.
     */
    fun testRelay(): String {
        if (modeCache != Mode.PROXY) return "حالت روی «مستقیم» است؛ واسط آزمایش نشد."
        val base = endpointCache
        if (!base.startsWith("https://")) return "آدرس واسط نامعتبر است (باید با https شروع شود)."

        val http = okhttp3.OkHttpClient.Builder()
            .connectTimeout(20, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            .build()
        val out = StringBuilder()
        try {
            // 1) health (no auth): proves the phone can reach the relay at all.
            val hReq = okhttp3.Request.Builder().url("$base/health").get().build()
            http.newCall(hReq).execute().use { r ->
                val body = r.body?.string().orEmpty().take(160)
                out.append("۱) دسترسی به واسط: کد ${r.code}\n")
                if (r.isSuccessful) out.append("   پاسخ: $body\n")
                else out.append("   پاسخ: $body\n")
            }
        } catch (e: Exception) {
            out.append("۱) دسترسی به واسط: ناموفق\n")
            out.append("   خطا: ${e.javaClass.simpleName}\n")
            out.append("   ⇒ یعنی این گوشی حتی به واسط هم نمی‌رسد (احتمالاً workers.dev مسدود است).\n")
            return out.toString().trim()
        }
        try {
            // 2) authenticated models call to the active provider host.
            val host = runCatching { java.net.URI(activeBaseUrl()).host }.getOrNull().orEmpty()
            val probeUrl = "$base/proxy/$host" + modelsPathFor(host)
            val b = okhttp3.Request.Builder().url(probeUrl).get().addHeader("Accept", "application/json")
            if (tokenCache.isNotBlank()) b.addHeader("X-Gateway-Token", tokenCache)
            b.addHeader("X-Gateway-Mode", "relay")
            http.newCall(b.build()).execute().use { r ->
                val body = r.body?.string().orEmpty().take(200)
                out.append("۲) واسط ← $host: کد ${r.code}\n")
                out.append("   پاسخ: $body\n")
                when {
                    r.isSuccessful -> out.append("   ⇒ واسط سالم است و به سرویس می‌رسد.")
                    r.code == 401 -> out.append("   ⇒ توکن واسط اشتباه است.")
                    r.code == 403 -> out.append("   ⇒ میزبان در فهرست مجاز Worker نیست.")
                    r.code == 502 -> out.append("   ⇒ کلید این سرویس روی Worker تنظیم نشده.")
                    else -> out.append("   ⇒ کد غیرمنتظره؛ متن پاسخ بالا را ببین.")
                }
            }
        } catch (e: Exception) {
            out.append("۲) فراخوانی سرویس: خطا (${e.javaClass.simpleName})")
        }
        return out.toString().trim()
    }

    private fun activeBaseUrl(): String =
        com.dastyar.app.ai.ServiceKeys.textProvider().baseUrl

    private fun modelsPathFor(host: String): String = when (host) {
        "api.groq.com" -> "/openai/v1/models"
        else -> "/api/v1/models"
    }
}

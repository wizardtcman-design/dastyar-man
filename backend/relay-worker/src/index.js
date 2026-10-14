/**
 * Dastyar Relay Worker
 * ====================
 *
 * A small, authenticated relay that lets the Dastyar app reach OpenAI-compatible
 * AI providers from networks where those providers are blocked. It is NOT an
 * open proxy: only a fixed allow-list of provider hosts can be reached, every
 * request must carry a shared gateway token, and requests are rate-limited,
 * size-limited and timed out.
 *
 * Design goals
 *  - No provider API key ever lives in the app (APK). Keys are Worker secrets.
 *  - The app authenticates with a single shared token (X-Gateway-Token) which
 *    the user sets in the app's Settings.
 *  - Extensible: adding a new OpenAI-compatible provider is one line in the
 *    PROVIDERS map. The app sends the host it wants in the path, so no route or
 *    provider list has to be duplicated in the client.
 *
 * Request contract (what the app sends):
 *    POST/GET  https://<worker>/proxy/<provider-host><original-path-and-query>
 *    Header    X-Gateway-Token: <shared token>
 *
 * The Worker forwards the request to https://<provider-host><path> and attaches
 * the provider's own secret key from the environment, so the app never has to.
 *
 * Environment / secrets (set via `wrangler secret put`, never in code):
 *    GATEWAY_TOKEN        shared token the app must present
 *    OPENROUTER_API_KEY   key for openrouter.ai
 *    GROQ_API_KEY         key for api.groq.com
 *    CODECRAFT_API_KEY    key for codecraftapi.com (optional)
 *    ATRIA_API_KEY        key for api.atria-asi.ai (optional)
 *    RATE_LIMIT_PER_MIN   optional integer, default 60 requests/minute/client
 */

// ---------------------------------------------------------------------------
// Provider allow-list. Only these host names may be relayed, and each maps to
// the environment secret holding its key. Add a provider by adding a line.
// ---------------------------------------------------------------------------
const PROVIDERS = {
  "openrouter.ai": { secret: "OPENROUTER_API_KEY", auth: "bearer" },
  "api.groq.com": { secret: "GROQ_API_KEY", auth: "bearer" },
  "codecraftapi.com": { secret: "CODECRAFT_API_KEY", auth: "bearer" },
  "api.atria-asi.ai": { secret: "ATRIA_API_KEY", auth: "bearer" },
};

// Hard limits. Deliberately conservative: a chat request is small.
const MAX_BODY_BYTES = 256 * 1024;      // 256 KiB request body
const UPSTREAM_TIMEOUT_MS = 120 * 1000; // 2 minutes
const DEFAULT_RATE_PER_MIN = 60;

// Bumped whenever the routing/allow-list logic changes, so `/health` proves
// which revision is deployed (e.g. whether api.groq.com is really in the map).
const RELAY_VERSION = "1.3.0";

// ---------------------------------------------------------------------------
// Tiny in-memory rate limiter. A Worker isolate may be recycled, so this is a
// best-effort guard, not a billing system. It stops naive abuse and runaway
// loops; for strict quotas put Cloudflare's Rate Limiting rules in front too.
// ---------------------------------------------------------------------------
const hits = new Map(); // clientKey -> { count, windowStart }

function rateLimit(key, limit) {
  const now = Date.now();
  const rec = hits.get(key);
  if (!rec || now - rec.windowStart > 60_000) {
    hits.set(key, { count: 1, windowStart: now });
    return true;
  }
  if (rec.count >= limit) return false;
  rec.count += 1;
  return true;
}

function json(status, obj) {
  return new Response(JSON.stringify(obj), {
    status,
    headers: { "content-type": "application/json; charset=utf-8" },
  });
}

/**
 * Secret-free diagnostic log. It records ONLY non-sensitive routing facts
 * (method, path, host, whether a token was present, its length, upstream
 * status) and never a token or API-key VALUE. This is what makes a real
 * phone-side failure diagnosable from Cloudflare's live logs.
 */
function safeLog(fields) {
  try {
    console.log(JSON.stringify({ diag: true, ...fields }));
  } catch (_) {
    // Logging must never break a request.
  }
}

// Constant-time-ish string compare so a token cannot be guessed byte by byte.
function safeEqual(a, b) {
  if (typeof a !== "string" || typeof b !== "string") return false;
  if (a.length !== b.length) return false;
  let diff = 0;
  for (let i = 0; i < a.length; i++) diff |= a.charCodeAt(i) ^ b.charCodeAt(i);
  return diff === 0;
}

export default {
  async fetch(request, env) {
    const url = new URL(request.url);

    // Health check: no auth, no provider call, no secrets leaked.
    // `version` lets you prove which code revision is actually deployed.
    if (url.pathname === "/health") {
      return json(200, { ok: true, service: "dastyar-relay", version: RELAY_VERSION });
    }

    // Only the /proxy/<host>/<path> shape is served.
    if (!url.pathname.startsWith("/proxy/")) {
      return json(404, { error: "not_found" });
    }

    // ---- authentication (never an open proxy) ----
    const expected = env.GATEWAY_TOKEN;
    if (!expected) {
      // Misconfiguration must fail closed, never open.
      return json(500, { error: "server_not_configured" });
    }
    const provided = request.headers.get("X-Gateway-Token") || "";
    const authOk = safeEqual(provided, expected);
    safeLog({ stage: "auth", ok: authOk, method: request.method, path: url.pathname, hasToken: provided.length > 0, tokenLen: provided.length });
    if (!authOk) {
      return json(401, { error: "unauthorized" });
    }

    // ---- rate limit per client (by token + IP) ----
    const limit = parseInt(env.RATE_LIMIT_PER_MIN || `${DEFAULT_RATE_PER_MIN}`, 10);
    const clientKey =
      (request.headers.get("CF-Connecting-IP") || "unknown") + "|" + provided;
    if (!rateLimit(clientKey, Number.isFinite(limit) ? limit : DEFAULT_RATE_PER_MIN)) {
      return json(429, { error: "rate_limited" });
    }

    // ---- parse the target host + path ----
    const rest = url.pathname.slice("/proxy/".length);
    const slash = rest.indexOf("/");
    if (slash < 0) return json(400, { error: "bad_target" });
    const host = rest.slice(0, slash);
    const path = rest.slice(slash); // keeps the leading "/"

    safeLog({ stage: "routing", ok: true, method: request.method, path: url.pathname, host, pathAfterHost: path });
    const provider = PROVIDERS[host];
    if (!provider) { safeLog({ stage: "host_check", ok: false, host }); return json(403, { error: "host_not_allowed" }); }

    const apiKey = env[provider.secret];
    if (!apiKey) { safeLog({ stage: "secret", ok: false, host, secret: provider.secret }); return json(502, { error: "provider_not_configured", provider: host }); }

    // ---- method + body ----
    const method = request.method.toUpperCase();
    if (method !== "POST" && method !== "GET") {
      return json(405, { error: "method_not_allowed" });
    }

    let bodyBuf = null;
    if (method === "POST") {
      const cl = parseInt(request.headers.get("content-length") || "0", 10);
      if (cl && cl > MAX_BODY_BYTES) return json(413, { error: "body_too_large" });
      bodyBuf = await request.arrayBuffer();
      if (bodyBuf.byteLength > MAX_BODY_BYTES) return json(413, { error: "body_too_large" });
    }

    // ---- build the upstream request ----
    const target = `https://${host}${path}${url.search}`;
    const headers = new Headers();
    // Forward only what a JSON chat call needs; never forward the gateway token.
    const ct = request.headers.get("content-type");
    if (ct) headers.set("content-type", ct);
    const accept = request.headers.get("accept");
    if (accept) headers.set("accept", accept);
    headers.set("authorization", `Bearer ${apiKey}`);
    // A browser-like User-Agent is one of the signals Cloudflare's bot
    // management scores; the default workerd UA scores badly on OpenRouter's
    // zone and can be answered with a 403 "Access denied by security policy".
    headers.set(
      "user-agent",
      "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Mobile Safari/537.36"
    );
    // OpenRouter's documented attribution headers. Sending them is free and
    // keeps requests inside OpenRouter's expected client shape.
    if (host === "openrouter.ai") {
      headers.set("HTTP-Referer", "https://dastyar.app");
      headers.set("X-Title", "Dastyar");
      headers.set("accept-encoding", "identity");
    }

    async function callUpstream() {
      const controller = new AbortController();
      const timer = setTimeout(() => controller.abort(), UPSTREAM_TIMEOUT_MS);
      try {
        return await fetch(target, {
          method,
          headers,
          body: method === "POST" ? bodyBuf : undefined,
          signal: controller.signal,
        });
      } finally {
        clearTimeout(timer);
      }
    }

    let upstream;
    try {
      upstream = await callUpstream();
      // Cloudflare's bot management sometimes answers a first request from a
      // datacenter/workerd fingerprint with this exact 403 even though the same
      // request succeeds moments later. Retry once before giving up, so a
      // transient fingerprint challenge does not surface as a hard failure.
      if (upstream.status === 403) {
        const peek = await upstream.clone().text().catch(() => "");
        if (peek.toLowerCase().includes("access denied by security policy")) {
          safeLog({ stage: "botblock", host, attempt: 1 });
          await new Promise((r) => setTimeout(r, 350));
          upstream = await callUpstream();
        }
      }
    } catch (e) {
      const aborted = e && e.name === "AbortError";
      return json(aborted ? 504 : 502, {
        error: aborted ? "upstream_timeout" : "upstream_unreachable",
      });
    }
    safeLog({ stage: "upstream", ok: upstream.status < 400, host, path, status: upstream.status });

    // Stream the provider's response straight back, preserving status and the
    // content type (so streaming/SSE and JSON both pass through unchanged).
    const respHeaders = new Headers();
    const upCt = upstream.headers.get("content-type");
    if (upCt) respHeaders.set("content-type", upCt);
    // Never cache AI responses.
    respHeaders.set("cache-control", "no-store");

    return new Response(upstream.body, {
      status: upstream.status,
      headers: respHeaders,
    });
  },
};

package com.dastyar.app.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * Pollinations image client, used as the last-resort image fallback.
 *
 * It needs no key for the public endpoint, but an optional key is supported:
 * when the user enters one it is sent as a bearer token so a funded account can
 * be used. A failure here is reported with Pollinations' own reason, never as a
 * problem with the other providers.
 */
object PollinationsClient {

    private val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(180, TimeUnit.SECONDS)
        .build()

    private const val BASE = "https://image.pollinations.ai/prompt"

    enum class PFail { NONE, RATE_LIMIT, BAD_KEY, NETWORK, PROVIDER }

    data class PResult(
        val ok: Boolean,
        val kind: PFail,
        val message: String,
        val value: ByteArray? = null
    )

    private fun url(prompt: String, width: Int, height: Int, seed: Int, key: String?): String {
        val encoded = java.net.URLEncoder.encode(prompt, "UTF-8")
        val k = if (!key.isNullOrBlank()) "&key=$key" else ""
        return "$BASE/$encoded?width=$width&height=$height&nologo=true&seed=$seed&model=flux$k"
    }

    /**
     * Generates an image, translated prompt assumed. Pollinations occasionally
     * answers a transient 5xx, so a failed call is retried a couple of times
     * with a different seed before the caller gives up.
     */
    suspend fun image(
        prompt: String,
        width: Int = 768,
        height: Int = 1024,
        seed: Int = (1..999_999).random(),
        key: String? = null
    ): PResult = withContext(Dispatchers.IO) {
        var last = PResult(false, PFail.PROVIDER, "Pollinations پاسخ نداد.")
        repeat(3) { attempt ->
            val s = seed + attempt * 101
            try {
                val req = Request.Builder()
                    .url(url(prompt, width, height, s, key))
                    .addHeader("User-Agent", "Dastyar/1.0")
                    .get().build()
                http.newCall(req).execute().use { resp ->
                    if (resp.isSuccessful) {
                        val bytes = resp.body?.bytes()
                        if (bytes != null && bytes.size > 1000) {
                            return@withContext PResult(true, PFail.NONE, "✅ تصویر ساخته شد", bytes)
                        }
                        last = PResult(false, PFail.PROVIDER, "Pollinations تصویر خالی برگرداند.")
                    } else {
                        val kind = when (resp.code) {
                            401, 403 -> PFail.BAD_KEY
                            429 -> PFail.RATE_LIMIT
                            else -> PFail.PROVIDER
                        }
                        last = PResult(false, kind, "Pollinations پاسخ نداد (کد ${resp.code}).")
                    }
                }
            } catch (e: Exception) {
                last = PResult(false, PFail.NETWORK, "مشکل اتصال به Pollinations: ${networkReason(e)}")
            }
            if (attempt < 2) Thread.sleep(2500L * (attempt + 1))
        }
        last
    }

    /** A real, cheap request that proves the service answers. */
    suspend fun test(key: String? = null): PResult = image(
        prompt = "a simple blue circle on a white background",
        width = 256,
        height = 256,
        key = key
    )

    private fun networkReason(e: Exception): String = when (e) {
        is java.net.UnknownHostException -> "دامنه پیدا نشد."
        is java.net.SocketTimeoutException -> "زمان انتظار تمام شد."
        is java.net.ConnectException -> "اتصال برقرار نشد."
        else -> e.javaClass.simpleName
    }
}

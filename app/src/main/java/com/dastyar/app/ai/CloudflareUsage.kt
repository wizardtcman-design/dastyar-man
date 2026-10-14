package com.dastyar.app.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit

/**
 * Reads the account's REAL Workers AI consumption from Cloudflare's own GraphQL
 * Analytics API (`aiInferenceAdaptiveGroups`, field `sum { totalNeurons }`).
 *
 * This is the only reliable, Cloudflare-published source of Neuron usage: the
 * Workers AI REST endpoint returns no usage figure, and the dashboard number is
 * not exposed elsewhere. Nothing here is guessed from token or message counts --
 * the value comes straight from Cloudflare.
 *
 * Two honest limits are surfaced rather than hidden:
 *   - the token must carry the "Account Analytics: Read" permission; without it
 *     Cloudflare refuses the query and [Fail.NO_PERMISSION] is reported;
 *   - analytics data can lag a few minutes behind the very latest requests.
 */
object CloudflareUsage {

    private const val GRAPHQL = "https://api.cloudflare.com/client/v4/graphql"

    /** Cloudflare's documented free allocation: 10,000 Neurons per day. */
    const val FREE_DAILY_NEURONS = 10_000L

    /**
     * Documented unit pricing for the Gemma 4 text model, shown as reference
     * only. Consumption is never derived from these; the API value above is used.
     */
    const val GEMMA_PRICING_NOTE =
        "نرخ رسمی مدل: ۰٫۱۰ دلار به‌ازای هر یک میلیون توکن ورودی و ۰٫۳۰ دلار خروجی"

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    enum class Fail { NONE, NO_CREDENTIALS, BAD_TOKEN, NO_PERMISSION, NETWORK, PROVIDER }

    /** One model's real usage for the period, exactly as Cloudflare reported it. */
    data class Row(val model: String, val requests: Int, val neurons: Double)

    data class Usage(
        val ok: Boolean,
        val fail: Fail,
        val message: String,
        val rows: List<Row> = emptyList(),
        /** Real Neurons the account used since 00:00 UTC today. */
        val todayNeurons: Double = 0.0,
        /** Real request count since 00:00 UTC today. */
        val todayRequests: Int = 0,
        /** True when Cloudflare answered, i.e. the account + token are verified. */
        val verified: Boolean = false
    ) {
        val remaining: Double get() = (FREE_DAILY_NEURONS - todayNeurons).coerceAtLeast(0.0)
        val percentUsed: Double
            get() = (todayNeurons / FREE_DAILY_NEURONS.toDouble() * 100.0).coerceIn(0.0, 100.0)
    }

    private fun utcDayStart(): String {
        val fmt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
        fmt.timeZone = TimeZone.getTimeZone("UTC")
        val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return fmt.format(cal.time)
    }

    /** The exact instant the free daily allowance resets: next 00:00 UTC. */
    fun nextResetUtc(): Date {
        val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
        cal.add(Calendar.DAY_OF_YEAR, 1)
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.time
    }

    /** Reads today's real usage for one Cloudflare account. */
    suspend fun fetch(accountId: String, token: String): Usage = withContext(Dispatchers.IO) {
        val acc = accountId.trim()
        val tok = token.trim()
        if (acc.isBlank() || tok.isBlank()) {
            return@withContext Usage(
                false, Fail.NO_CREDENTIALS,
                "برای خواندن مصرف، Account ID و یک توکن با دسترسی «Account Analytics: Read» لازم است."
            )
        }
        val query = """
            query {
              viewer {
                accounts(filter: {accountTag: "$acc"}) {
                  aiInferenceAdaptiveGroups(
                    limit: 1000,
                    filter: {datetimeHour_geq: "${utcDayStart()}"}
                  ) {
                    count
                    sum { totalNeurons }
                    dimensions { modelId }
                  }
                }
              }
            }
        """.trimIndent()

        try {
            val body = "{\"query\": " +
                    kotlinx.serialization.json.JsonPrimitive(query).toString() + "}"
            val req = Request.Builder()
                .url(GRAPHQL)
                .addHeader("Authorization", "Bearer $tok")
                .addHeader("Content-Type", "application/json")
                .post(body.toRequestBody("application/json".toMediaType()))
                .build()
            http.newCall(req).execute().use { resp ->
                val text = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    return@withContext Usage(false, failForHttp(resp.code, text), describe(resp.code, text))
                }
                val root = json.parseToJsonElement(text).jsonObject
                val errors = root["errors"] as? kotlinx.serialization.json.JsonArray
                if (errors != null && errors.isNotEmpty()) {
                    val msg = (errors.firstOrNull() as? kotlinx.serialization.json.JsonObject)
                        ?.get("message")?.jsonPrimitive?.contentOrNull.orEmpty()
                    return@withContext Usage(false, Fail.NO_PERMISSION, permMessage(msg))
                }
                val data = root["data"] as? kotlinx.serialization.json.JsonObject
                    ?: return@withContext Usage(
                        false, Fail.PROVIDER, "پاسخ نامعتبر از تحلیل Cloudflare دریافت شد."
                    )
                val accounts = ((data["viewer"] as? kotlinx.serialization.json.JsonObject)
                    ?.get("accounts")) as? kotlinx.serialization.json.JsonArray
                if (accounts.isNullOrEmpty()) {
                    return@withContext Usage(
                        false, Fail.NO_PERMISSION,
                        "این حساب با توکن فعلی برای خواندن تحلیل در دسترس نیست. " +
                                "دسترسی «Account Analytics: Read» را به توکن بده."
                    )
                }
                val groups = ((accounts.firstOrNull() as? kotlinx.serialization.json.JsonObject)
                    ?.get("aiInferenceAdaptiveGroups")) as? kotlinx.serialization.json.JsonArray
                if (groups == null) {
                    return@withContext Usage(
                        true, Fail.NONE, "امروز مصرفی ثبت نشده است.", emptyList(), 0.0, 0, verified = true
                    )
                }
                var totalNeurons = 0.0
                var totalRequests = 0
                val perModel = LinkedHashMap<String, Row>()
                groups.forEach { el ->
                    val o = el as? kotlinx.serialization.json.JsonObject ?: return@forEach
                    val count = o["count"]?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: 0
                    val neurons = (o["sum"] as? kotlinx.serialization.json.JsonObject)
                        ?.get("totalNeurons")?.jsonPrimitive?.contentOrNull?.toDoubleOrNull() ?: 0.0
                    val model = (o["dimensions"] as? kotlinx.serialization.json.JsonObject)
                        ?.get("modelId")?.jsonPrimitive?.contentOrNull ?: "نامشخص"
                    totalNeurons += neurons
                    totalRequests += count
                    val prev = perModel[model]
                    perModel[model] = Row(
                        model = model,
                        requests = (prev?.requests ?: 0) + count,
                        neurons = (prev?.neurons ?: 0.0) + neurons
                    )
                }
                Usage(
                    ok = true,
                    fail = Fail.NONE,
                    message = "داده از تحلیل رسمی Cloudflare خوانده شد.",
                    rows = perModel.values.sortedByDescending { it.neurons },
                    todayNeurons = totalNeurons,
                    todayRequests = totalRequests,
                    verified = true
                )
            }
        } catch (e: Exception) {
            Usage(false, Fail.NETWORK, networkReason(e))
        }
    }

    private fun failForHttp(code: Int, raw: String): Fail = when {
        code == 401 || code == 403 -> Fail.NO_PERMISSION
        code == 400 && raw.contains("9106") -> Fail.BAD_TOKEN
        code in 500..599 -> Fail.PROVIDER
        else -> Fail.PROVIDER
    }

    private fun describe(code: Int, raw: String): String = when (failForHttp(code, raw)) {
        Fail.NO_PERMISSION ->
            "توکن اجازهٔ خواندن تحلیل Cloudflare را ندارد. " +
                    "دسترسی «Account Analytics: Read» را به توکن اضافه کن."
        Fail.BAD_TOKEN -> "توکن یا شناسهٔ حساب نامعتبر است."
        else -> "خطای سرویس تحلیل Cloudflare (کد $code)."
    }

    private fun permMessage(msg: String): String =
        if (msg.isBlank()) "توکن اجازهٔ خواندن تحلیل Cloudflare را ندارد."
        else "Cloudflare اجازهٔ خواندن تحلیل را نداد: $msg"

    private fun networkReason(e: Exception): String = when (e) {
        is java.net.UnknownHostException -> "دامنهٔ Cloudflare پیدا نشد؛ اینترنت را بررسی کن."
        is java.net.SocketTimeoutException -> "زمان انتظار Cloudflare تمام شد؛ دوباره تلاش کن."
        else -> "مشکل اتصال به Cloudflare (${e.javaClass.simpleName})."
    }
}

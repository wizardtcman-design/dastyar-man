package com.dastyar.app.ai

import android.content.Context
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Records every image request the app itself makes, so the user can see real
 * request counts even when the provider exposes no usage API.
 *
 * Only facts the app actually observed are written: the model, the kind of
 * request, whether it succeeded, the HTTP status, the duration and the timestamp.
 * No Neuron count is stored or shown unless the provider really returned one --
 * this class never estimates or invents a cost.
 */
object UsageLog {

    private const val PREFS = "dastyar_usage"
    private const val KEY_LOG = "image_log"

    /** How many recent entries to keep; a week of use fits easily. */
    private const val MAX_ENTRIES = 500

    enum class Kind { GENERATE, EDIT }
    enum class Provider { CLOUDFLARE, POLLINATIONS, OPENROUTER }

    data class Entry(
        val timestamp: Long,
        val provider: String,
        val model: String,
        val kind: String,
        val ok: Boolean,
        val http: Int,
        /** Real Neurons the provider reported, or null when it reported none. */
        val neurons: Double?,
        val durationMs: Long,
        val note: String = ""
    )

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** Appends one real request record. */
    fun record(
        ctx: Context?,
        provider: Provider,
        model: String,
        kind: Kind,
        ok: Boolean,
        http: Int,
        durationMs: Long,
        neurons: Double? = null,
        note: String = ""
    ) {
        if (ctx == null) return
        val e = Entry(
            timestamp = System.currentTimeMillis(),
            provider = provider.name,
            model = model,
            kind = kind.name,
            ok = ok,
            http = http,
            neurons = neurons,
            durationMs = durationMs,
            note = note
        )
        val arr = readAll(ctx).toMutableList()
        arr += e
        while (arr.size > MAX_ENTRIES) arr.removeAt(0)
        writeAll(ctx, arr)
    }

    private fun writeAll(ctx: Context, list: List<Entry>) {
        val arr = buildJsonArray {
            list.forEach { e ->
                add(buildJsonObject {
                    put("ts", e.timestamp)
                    put("provider", e.provider)
                    put("model", e.model)
                    put("kind", e.kind)
                    put("ok", e.ok)
                    put("http", e.http)
                    e.neurons?.let { put("neurons", it) }
                    put("ms", e.durationMs)
                    put("note", e.note)
                })
            }
        }
        prefs(ctx).edit().putString(KEY_LOG, arr.toString()).apply()
    }

    private fun readAll(ctx: Context): List<Entry> {
        val raw = prefs(ctx).getString(KEY_LOG, null) ?: return emptyList()
        return try {
            json.parseToJsonElement(raw).jsonArray.map { el ->
                val o: JsonObject = el.jsonObject
                Entry(
                    timestamp = o["ts"]?.jsonPrimitive?.content?.toLongOrNull() ?: 0L,
                    provider = o["provider"]?.jsonPrimitive?.content.orEmpty(),
                    model = o["model"]?.jsonPrimitive?.content.orEmpty(),
                    kind = o["kind"]?.jsonPrimitive?.content.orEmpty(),
                    ok = o["ok"]?.jsonPrimitive?.content?.toBoolean() ?: false,
                    http = o["http"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0,
                    neurons = o["neurons"]?.jsonPrimitive?.content?.toDoubleOrNull(),
                    durationMs = o["ms"]?.jsonPrimitive?.content?.toLongOrNull() ?: 0L,
                    note = o["note"]?.jsonPrimitive?.content.orEmpty()
                )
            }
        } catch (ex: Exception) {
            emptyList()
        }
    }

    /** Every recorded entry, oldest first. */
    fun all(ctx: Context): List<Entry> = readAll(ctx)

    /** Entries from today, in the device's own time zone. */
    fun today(ctx: Context): List<Entry> {
        val start = startOfToday()
        return readAll(ctx).filter { it.timestamp >= start }
    }

    /** A real summary computed only from the recorded facts. */
    data class Summary(
        val cloudflareGenerate: Int,
        val cloudflareEdit: Int,
        val cloudflareFailed: Int,
        val fallbackGenerate: Int,
        val models: List<String>,
        val totalMs: Long,
        /** Sum of Neurons only when the provider actually reported them. */
        val neuronsReported: Double?,
        val hasNeuronData: Boolean
    )

    fun todaySummary(ctx: Context): Summary {
        val list = today(ctx)
        val cf = list.filter { it.provider == Provider.CLOUDFLARE.name }
        val reported = cf.mapNotNull { it.neurons }
        return Summary(
            cloudflareGenerate = cf.count { it.kind == Kind.GENERATE.name },
            cloudflareEdit = cf.count { it.kind == Kind.EDIT.name },
            cloudflareFailed = cf.count { !it.ok },
            fallbackGenerate = list.count {
                it.provider == Provider.POLLINATIONS.name && it.kind == Kind.GENERATE.name
            },
            models = cf.map { it.model }.distinct(),
            totalMs = list.sumOf { it.durationMs },
            neuronsReported = if (reported.isEmpty()) null else reported.sum(),
            hasNeuronData = reported.isNotEmpty()
        )
    }

    fun clear(ctx: Context) {
        prefs(ctx).edit().remove(KEY_LOG).apply()
    }

    private fun startOfToday(): Long {
        val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        fmt.timeZone = TimeZone.getDefault()
        val today = fmt.format(Date())
        return fmt.parse(today)?.time ?: 0L
    }
}

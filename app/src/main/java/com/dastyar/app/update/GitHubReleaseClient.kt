package com.dastyar.app.update

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * Reads the newest official release of the app straight from the GitHub
 * Releases API. The APK link is discovered from the release itself -- never
 * hardcoded -- so whichever build CI publishes is the one the app offers.
 *
 * The endpoint for "latest" is used on purpose: it already ignores drafts and
 * pre-releases, so the user is only ever offered a finished, published release.
 */
object GitHubReleaseClient {

    /** Owner/repo of the official source, matches the app's package owner. */
    const val REPO = "wizardtcman-design/dastyar-man"

    private const val LATEST_URL =
        "https://api.github.com/repos/$REPO/releases/latest"

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(40, TimeUnit.SECONDS)
        .build()

    /**
     * Fetches the latest release, or null when there is none, the network is
     * down, or GitHub answers with an unexpected payload. Failures are silent:
     * an update check must never disturb the app.
     */
    suspend fun fetchLatest(): UpdateInfo? = withContext(Dispatchers.IO) {
        try {
            val req = Request.Builder()
                .url(LATEST_URL)
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", "dastyar-man-update")
                .build()
            http.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext null
                val body = resp.body?.string() ?: return@withContext null
                parseRelease(json.parseToJsonElement(body).jsonObject)
            }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Fetches the notes of the release whose tag matches [version] (with or
     * without a leading "v"). Used for the one-time "What's New" dialog after
     * the app has been updated. Returns null when the release is not found or
     * the network is unavailable.
     */
    suspend fun fetchNotesForVersion(version: String): String? = withContext(Dispatchers.IO) {
        val clean = version.trim().removePrefix("v").removePrefix("V")
        if (clean.isBlank()) return@withContext null
        for (tag in listOf("v$clean", clean)) {
            try {
                val req = Request.Builder()
                    .url("https://api.github.com/repos/$REPO/releases/tags/$tag")
                    .header("Accept", "application/vnd.github+json")
                    .header("User-Agent", "dastyar-man-update")
                    .build()
                http.newCall(req).execute().use { resp ->
                    if (resp.isSuccessful) {
                        val body = resp.body?.string() ?: return@withContext null
                        val o = json.parseToJsonElement(body).jsonObject
                        val notes = o["body"]?.jsonPrimitive?.contentOrNull.orEmpty()
                        if (notes.isNotBlank()) return@withContext notes
                    }
                }
            } catch (_: Exception) {
                // try the next tag form
            }
        }
        null
    }

    /** Turns one GitHub release JSON object into [UpdateInfo], or null. */
    private fun parseRelease(o: JsonObject): UpdateInfo? {
        val tag = o["tag_name"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
        if (tag.isBlank()) return null
        val version = tag.removePrefix("v").removePrefix("V")

        val notes = o["body"]?.jsonPrimitive?.contentOrNull.orEmpty()
        val htmlUrl = o["html_url"]?.jsonPrimitive?.contentOrNull.orEmpty()
        val publishedAt = parseIso(o["published_at"]?.jsonPrimitive?.contentOrNull)

        // Find the APK asset. Prefer one whose name ends with .apk; a release
        // may also carry a signature or checksum file, so pick carefully.
        val assets: JsonArray = o["assets"]?.jsonArray ?: JsonArray(emptyList())
        val apk = assets.map { it.jsonObject }.firstOrNull { a ->
            a["name"]?.jsonPrimitive?.contentOrNull
                ?.lowercase()?.endsWith(".apk") == true
        } ?: return null

        val apkUrl = apk["browser_download_url"]?.jsonPrimitive?.contentOrNull.orEmpty()
        if (apkUrl.isBlank()) return null

        return UpdateInfo(
            tagName = tag,
            version = version,
            notes = notes,
            apkUrl = apkUrl,
            apkName = apk["name"]?.jsonPrimitive?.contentOrNull ?: "dastyar-man.apk",
            apkSize = apk["size"]?.jsonPrimitive?.contentOrNull?.toLongOrNull() ?: 0L,
            htmlUrl = htmlUrl,
            publishedAt = publishedAt
        )
    }

    /** Parses an ISO-8601 timestamp to epoch millis; 0 on any problem. */
    private fun parseIso(s: String?): Long {
        if (s.isNullOrBlank()) return 0L
        return try {
            java.time.Instant.parse(s).toEpochMilli()
        } catch (_: Exception) {
            0L
        }
    }
}

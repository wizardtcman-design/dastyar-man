package com.dastyar.app.update

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import kotlin.coroutines.coroutineContext

/**
 * Downloads the release APK to the app's private cache with real progress, a
 * working cancel and a retry, and verifies it before it is ever offered for
 * install.
 *
 * Verification is the important part: an APK is only accepted when
 *   1. the download finished and the file is a valid zip (an APK is a zip), and
 *   2. its signing certificate SHA-256 matches the certificate this running app
 *      was signed with.
 *
 * Step 2 is what truly proves the file is an official update of this same app:
 * Android only allows an in-place upgrade when the new APK is signed with the
 * same key, and this app's CI always signs with the same release keystore. A
 * tampered or unrelated APK is rejected here and can never be installed.
 */
object ApkDownloader {

    /** Progress callback: bytes done, total bytes (-1 unknown), 0..100 percent. */
    fun interface Progress {
        fun onProgress(done: Long, total: Long, percent: Int)
    }

    sealed interface Result {
        data class Success(val file: File) : Result
        data object Cancelled : Result
        data class Failed(val message: String) : Result
    }

    private val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    /** The cache file a given version is downloaded to. */
    fun fileFor(ctx: Context, version: String): File =
        File(ctx.cacheDir, "update/dastyar-${version}.apk")

    /**
     * Downloads [info]'s APK into the app cache, reporting progress and
     * verifying the result. Never leaves a partial file behind: a failed or
     * cancelled attempt deletes what it wrote.
     */
    suspend fun download(
        ctx: Context,
        info: UpdateInfo,
        progress: Progress
    ): Result = withContext(Dispatchers.IO) {
        val out = fileFor(ctx, info.version)
        out.parentFile?.mkdirs()
        val part = File(out.parentFile, out.name + ".part")
        if (part.exists()) part.delete()

        try {
            val req = Request.Builder()
                .url(info.apkUrl)
                .header("User-Agent", "dastyar-man-update")
                .header("Accept", "application/octet-stream")
                .build()
            http.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) {
                    return@withContext Result.Failed("دانلود ناموفق بود (کد ${resp.code}).")
                }
                val body = resp.body ?: return@withContext Result.Failed("پاسخ خالی بود.")
                val total = body.contentLength().let { if (it > 0) it else info.apkSize }

                var written = 0L
                var lastPercent = -1
                body.byteStream().use { input ->
                    part.outputStream().use { fileOut ->
                        val buf = ByteArray(64 * 1024)
                        while (true) {
                            coroutineContext.ensureActive() // cancel support
                            val n = input.read(buf)
                            if (n < 0) break
                            fileOut.write(buf, 0, n)
                            written += n
                            val percent =
                                if (total > 0) ((written * 100) / total).toInt() else -1
                            if (percent != lastPercent) {
                                lastPercent = percent
                                progress.onProgress(written, total, percent)
                            }
                        }
                        fileOut.flush()
                    }
                }
            }

            // Verify before accepting the file.
            val verify = verifyApk(ctx, part)
            if (verify != null) {
                part.delete()
                return@withContext Result.Failed(verify)
            }
            if (out.exists()) out.delete()
            if (!part.renameTo(out)) {
                part.delete()
                return@withContext Result.Failed("ذخیره فایل ممکن نشد.")
            }
            progress.onProgress(out.length(), out.length(), 100)
            Result.Success(out)
        } catch (e: kotlinx.coroutines.CancellationException) {
            part.delete()
            Result.Cancelled
        } catch (e: IOException) {
            part.delete()
            Result.Failed("اتصال قطع شد. دوباره تلاش کن.")
        } catch (e: Exception) {
            part.delete()
            Result.Failed("دانلود با خطا متوقف شد.")
        }
    }

    /**
     * Returns null when [file] is a valid, correctly-signed APK of this app, or
     * a Persian error message when it is not.
     */
    fun verifyApk(ctx: Context, file: File): String? {
        if (!file.exists() || file.length() < 1024) return "فایل دانلود‌شده ناقص است."

        // 1. A real APK is a zip; reject anything that is not one.
        if (!isZip(file)) return "فایل دانلود‌شده یک بسته معتبر نیست."

        // 2. Read the signer advertised inside the downloaded APK.
        val downloaded = downloadedCertSha256(ctx, file)
            ?: return "امضای فایل خوانده نشد؛ نصب امن نیست."

        // 3. Read the signer of the app currently installed.
        val current = installedCertSha256(ctx)
            ?: return "امضای برنامه فعلی خوانده نشد؛ برای امنیت نصب انجام نمی‌شود."

        if (!downloaded.equals(current, ignoreCase = true)) {
            return "فایل با نسخه رسمی دستیار من هم‌امضا نیست؛ نصب لغو شد."
        }
        return null
    }

    private fun isZip(file: File): Boolean = try {
        java.util.zip.ZipFile(file).use { true }
    } catch (_: Exception) {
        false
    }

    /**
     * SHA-256 of the certificate inside the downloaded APK, read through
     * [android.content.pm.PackageManager.getPackageArchiveInfo]. minSdk is 26,
     * so GET_SIGNING_CERTIFICATES is always available.
     */
    private fun downloadedCertSha256(ctx: Context, apk: File): String? {
        return try {
            val flags = android.content.pm.PackageManager.GET_SIGNING_CERTIFICATES
            val info = ctx.packageManager.getPackageArchiveInfo(apk.absolutePath, flags)
                ?: return null
            val signers = info.signingInfo?.apkContentsSigners ?: return null
            val first = signers.firstOrNull() ?: return null
            sha256(first.toByteArray())
        } catch (_: Exception) {
            null
        }
    }

    private fun installedCertSha256(ctx: Context): String? {
        return try {
            val flags = android.content.pm.PackageManager.GET_SIGNING_CERTIFICATES
            val info = ctx.packageManager.getPackageInfo(ctx.packageName, flags)
            val signers = info.signingInfo?.apkContentsSigners ?: return null
            val first = signers.firstOrNull() ?: return null
            sha256(first.toByteArray())
        } catch (_: Exception) {
            null
        }
    }

    private fun sha256(bytes: ByteArray): String {
        val md = MessageDigest.getInstance("SHA-256")
        val d = md.digest(bytes)
        val sb = StringBuilder(d.size * 2)
        for (b in d) sb.append("%02x".format(b))
        return sb.toString()
    }
}

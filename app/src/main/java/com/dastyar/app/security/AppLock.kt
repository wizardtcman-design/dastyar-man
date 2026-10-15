package com.dastyar.app.security

import android.content.Context
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * App-lock settings, stored only in the app's private SharedPreferences.
 *
 * The PIN and the pattern are NEVER stored as plain text: only a salted hash is
 * kept, so reading the preferences file cannot reveal the code. Fingerprint
 * settings store nothing but a boolean.
 *
 * The hash is derived with PBKDF2-HMAC-SHA256 and many rounds. A single round of
 * SHA-256 is fast, so a 4-6 digit PIN could be brute-forced offline in moments
 * if the private preferences file ever leaked; the slow key-derivation function
 * makes that attack costly. Data written by older versions (one round of
 * SHA-256) is still accepted and silently upgraded to PBKDF2 on the first
 * successful unlock, so an existing code is never lost.
 */
object AppLock {

    private const val PREFS = "dastyar_app_lock"

    private const val KEY_ENABLED = "enabled"
    private const val KEY_MODE = "mode"          // "pin" | "pattern"
    private const val KEY_SALT = "salt"
    private const val KEY_HASH = "hash"
    private const val KEY_ITER = "iter"          // PBKDF2 rounds; absent on old data
    private const val KEY_BIOMETRIC = "biometric"

    /** PBKDF2 rounds and derived-key size. */
    private const val ITERATIONS = 120_000
    private const val KEY_BITS = 256

    const val MODE_PIN = "pin"
    const val MODE_PATTERN = "pattern"

    private fun prefs(ctx: Context) =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** The lock is on and a code has been set. */
    fun isEnabled(ctx: Context): Boolean =
        prefs(ctx).getBoolean(KEY_ENABLED, false) && prefs(ctx).getString(KEY_HASH, null) != null

    /** Which code the user chose: [MODE_PIN] or [MODE_PATTERN]. */
    fun mode(ctx: Context): String = prefs(ctx).getString(KEY_MODE, MODE_PIN) ?: MODE_PIN

    /** Whether fingerprint / face unlock is allowed as a shortcut. */
    fun biometricEnabled(ctx: Context): Boolean =
        prefs(ctx).getBoolean(KEY_BIOMETRIC, false)

    fun setBiometricEnabled(ctx: Context, enabled: Boolean) {
        prefs(ctx).edit().putBoolean(KEY_BIOMETRIC, enabled).apply()
    }

    /**
     * Sets (or replaces) the lock code. [code] is the PIN digits or the pattern
     * as a dash-joined sequence of cell indices. A fresh random salt is used
     * every time, so the same code never yields the same stored hash twice.
     */
    fun setCode(ctx: Context, mode: String, code: String) {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val saltHex = salt.joinToString("") { "%02x".format(it) }
        val derived = pbkdf2(salt, code, ITERATIONS)
        val e = prefs(ctx).edit()
            .putString(KEY_MODE, mode)
            .putString(KEY_SALT, saltHex)
            .putBoolean(KEY_ENABLED, true)
        if (derived != null) {
            e.putString(KEY_ITER, ITERATIONS.toString())
            e.putString(KEY_HASH, derived)
        } else {
            // PBKDF2 unavailable (should not happen on API 26+): keep working
            // with the older hash instead of leaving the user without a lock.
            e.remove(KEY_ITER)
            e.putString(KEY_HASH, legacyHash(saltHex, code))
        }
        e.apply()
    }

    /** Turns the lock off and forgets the stored code. */
    fun disable(ctx: Context) {
        prefs(ctx).edit().clear().apply()
    }

    /** True when [code] matches the stored PIN/pattern. */
    fun verify(ctx: Context, code: String): Boolean {
        val p = prefs(ctx)
        val saltHex = p.getString(KEY_SALT, null) ?: return false
        val expected = p.getString(KEY_HASH, null) ?: return false

        val iterStr = p.getString(KEY_ITER, null)
        if (iterStr != null) {
            val iter = iterStr.toIntOrNull() ?: return false
            val salt = hexToBytes(saltHex) ?: return false
            return pbkdf2(salt, code, iter) == expected
        }

        // Data written by an older version used a single round of SHA-256.
        // Verify it that way, then upgrade the stored hash to PBKDF2 so the
        // stronger scheme takes over from this unlock onward, without ever
        // losing the user's code.
        if (legacyHash(saltHex, code) != expected) return false
        hexToBytes(saltHex)?.let { salt ->
            pbkdf2(salt, code, ITERATIONS)?.let { upgraded ->
                p.edit()
                    .putString(KEY_ITER, ITERATIONS.toString())
                    .putString(KEY_HASH, upgraded)
                    .apply()
            }
        }
        return true
    }

    private fun pbkdf2(salt: ByteArray, code: String, iterations: Int): String? = try {
        val spec = PBEKeySpec(code.toCharArray(), salt, iterations, KEY_BITS)
        SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            .generateSecret(spec).encoded.joinToString("") { "%02x".format(it) }
    } catch (_: Exception) {
        null
    }

    /** The one-round salted SHA-256 hash used before PBKDF2 was introduced. */
    private fun legacyHash(saltHex: String, code: String): String {
        val md = MessageDigest.getInstance("SHA-256")
        md.update(saltHex.toByteArray(Charsets.UTF_8))
        md.update(code.toByteArray(Charsets.UTF_8))
        return md.digest().joinToString("") { "%02x".format(it) }
    }

    private fun hexToBytes(hex: String): ByteArray? {
        if (hex.length % 2 != 0) return null
        return try {
            ByteArray(hex.length / 2) { i ->
                ((Character.digit(hex[i * 2], 16) shl 4) or
                        Character.digit(hex[i * 2 + 1], 16)).toByte()
            }
        } catch (_: Exception) {
            null
        }
    }
}

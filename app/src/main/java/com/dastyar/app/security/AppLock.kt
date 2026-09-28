package com.dastyar.app.security

import android.content.Context
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * App-lock settings, stored only in the app's private SharedPreferences.
 *
 * The PIN and the pattern are NEVER stored as plain text: only a salted
 * SHA-256 hash is kept, so reading the preferences file cannot reveal the code.
 * Fingerprint settings store nothing but a boolean.
 */
object AppLock {

    private const val PREFS = "dastyar_app_lock"

    private const val KEY_ENABLED = "enabled"
    private const val KEY_MODE = "mode"          // "pin" | "pattern"
    private const val KEY_SALT = "salt"
    private const val KEY_HASH = "hash"
    private const val KEY_BIOMETRIC = "biometric"

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
        prefs(ctx).edit()
            .putString(KEY_MODE, mode)
            .putString(KEY_SALT, saltHex)
            .putString(KEY_HASH, hash(saltHex, code))
            .putBoolean(KEY_ENABLED, true)
            .apply()
    }

    /** Turns the lock off and forgets the stored code. */
    fun disable(ctx: Context) {
        prefs(ctx).edit().clear().apply()
    }

    /** True when [code] matches the stored PIN/pattern. */
    fun verify(ctx: Context, code: String): Boolean {
        val p = prefs(ctx)
        val salt = p.getString(KEY_SALT, null) ?: return false
        val expected = p.getString(KEY_HASH, null) ?: return false
        return hash(salt, code) == expected
    }

    private fun hash(saltHex: String, code: String): String {
        val md = MessageDigest.getInstance("SHA-256")
        md.update(saltHex.toByteArray(Charsets.UTF_8))
        md.update(code.toByteArray(Charsets.UTF_8))
        return md.digest().joinToString("") { "%02x".format(it) }
    }
}

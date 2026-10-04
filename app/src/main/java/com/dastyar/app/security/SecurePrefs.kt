package com.dastyar.app.security

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Encrypted storage for the credentials the user types into the app (AI service
 * keys). It wraps [EncryptedSharedPreferences], whose contents are encrypted
 * with a key held in the Android Keystore, so reading the raw preferences file
 * on a rooted or backed-up device no longer reveals a working key.
 *
 * Safety first: the app must never fail to start just because encryption is
 * unavailable (a broken keystore, an emulator quirk, an OS upgrade). If the
 * encrypted store cannot be created, [get] falls back to the ordinary private
 * preferences, so the app keeps working and the feature is never lost — it is
 * simply back to the previous, still-private storage.
 *
 * [migrateFromPlain] copies any values that already exist in an older, plaintext
 * preferences file into the encrypted store once, so existing users keep their
 * keys after the upgrade. The old plaintext entry is removed only after it has
 * been written successfully, so no key can be lost in the move.
 */
object SecurePrefs {

    /** Names of the encrypted stores, kept distinct from the old plaintext ones. */
    private const val SUFFIX = "_enc"

    /**
     * Returns the encrypted preferences for [name], or the ordinary private
     * preferences if encryption could not be initialised.
     */
    fun get(ctx: Context, name: String): SharedPreferences {
        return try {
            val masterKey = MasterKey.Builder(ctx)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            EncryptedSharedPreferences.create(
                ctx,
                name + SUFFIX,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (_: Exception) {
            // Keystore unavailable: keep the app running with private prefs.
            ctx.getSharedPreferences(name, Context.MODE_PRIVATE)
        }
    }

    /**
     * Moves any keys listed in [keys] from an old plaintext [name] store into
     * the encrypted one, once, and removes them from the plaintext file after a
     * successful write. Safe to call on every startup: it does nothing when
     * there is nothing left to migrate.
     */
    fun migrateFromPlain(ctx: Context, name: String, keys: List<String>) {
        val plain = ctx.getSharedPreferences(name, Context.MODE_PRIVATE)
        val encrypted = get(ctx, name)
        // Nothing to do when the stores are the same object (fallback mode).
        if (plain === encrypted) return

        val pending = keys.filter { plain.contains(it) }
        if (pending.isEmpty()) return

        val editor = encrypted.edit()
        for (k in pending) {
            // Copy every type a preference can hold. A missing type is skipped.
            plain.getString(k, null)?.let { editor.putString(k, it) }
            if (plain.contains(k)) {
                if (plain.all[k] is Boolean) editor.putBoolean(k, plain.getBoolean(k, false))
                if (plain.all[k] is Int) editor.putInt(k, plain.getInt(k, 0))
                if (plain.all[k] is Long) editor.putLong(k, plain.getLong(k, 0L))
                if (plain.all[k] is Float) editor.putFloat(k, plain.getFloat(k, 0f))
            }
        }
        // commit() (not apply) so the plaintext entries are cleared only after
        // the encrypted copy is definitely on disk.
        if (editor.commit()) {
            val clear = plain.edit()
            for (k in pending) clear.remove(k)
            clear.apply()
        }
    }
}

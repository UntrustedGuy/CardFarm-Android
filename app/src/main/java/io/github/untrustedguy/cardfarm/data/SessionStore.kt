package io.github.untrustedguy.cardfarm.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Persists the Steam session (refresh token, guard data) in encrypted
 * preferences so the app can re-login without the password.
 */
class SessionStore(context: Context) {

    private val prefs: SharedPreferences = run {
        val appContext = context.applicationContext
        try {
            val masterKey = MasterKey.Builder(appContext)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            EncryptedSharedPreferences.create(
                appContext,
                "steam_session",
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
            )
        } catch (e: Exception) {
            // Keystore corruption fallback: start clean rather than crash-loop.
            appContext.deleteSharedPreferences("steam_session")
            appContext.getSharedPreferences("steam_session_plain", Context.MODE_PRIVATE)
        }
    }

    var accountName: String?
        get() = prefs.getString(KEY_ACCOUNT, null)
        set(value) = prefs.edit().putString(KEY_ACCOUNT, value).apply()

    var refreshToken: String?
        get() = prefs.getString(KEY_REFRESH_TOKEN, null)
        set(value) = prefs.edit().putString(KEY_REFRESH_TOKEN, value).apply()

    var guardData: String?
        get() = prefs.getString(KEY_GUARD_DATA, null)
        set(value) = prefs.edit().putString(KEY_GUARD_DATA, value).apply()

    var steamId64: Long
        get() = prefs.getLong(KEY_STEAM_ID, 0L)
        set(value) = prefs.edit().putLong(KEY_STEAM_ID, value).apply()

    /** Whether farming was active when the process last died, for resume. */
    var wasFarming: Boolean
        get() = prefs.getBoolean(KEY_WAS_FARMING, false)
        set(value) = prefs.edit().putBoolean(KEY_WAS_FARMING, value).apply()

    /** Whether CardFarm should make the Steam account appear online to friends. */
    var appearOnline: Boolean
        get() = prefs.getBoolean(KEY_APPEAR_ONLINE, true)
        set(value) = prefs.edit().putBoolean(KEY_APPEAR_ONLINE, value).apply()

    val hasSession: Boolean
        get() = !refreshToken.isNullOrEmpty() && !accountName.isNullOrEmpty()

    fun clearSession() {
        prefs.edit()
            .remove(KEY_REFRESH_TOKEN)
            .remove(KEY_STEAM_ID)
            .remove(KEY_WAS_FARMING)
            .apply()
    }

    private companion object {
        const val KEY_ACCOUNT = "account_name"
        const val KEY_REFRESH_TOKEN = "refresh_token"
        const val KEY_GUARD_DATA = "guard_data"
        const val KEY_STEAM_ID = "steam_id64"
        const val KEY_WAS_FARMING = "was_farming"
        const val KEY_APPEAR_ONLINE = "appear_online"
    }
}

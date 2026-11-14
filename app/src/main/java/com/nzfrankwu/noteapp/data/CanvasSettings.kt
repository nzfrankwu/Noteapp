package com.nzfrankwu.noteapp.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant

private val Context.canvasSettingsStore: DataStore<Preferences> by preferencesDataStore(name = "canvas_settings")

/**
 * Small Canvas settings (in Noteapp's private storage): the school's Canvas address, who is
 * signed in (for "Signed in as …"), and when the last sync finished. No secrets here: the
 * sign-in cookies stay in the in-app browser's store (see WebViewCanvasCookies).
 */
class CanvasSettings(context: Context) {
    private val store = context.applicationContext.canvasSettingsStore

    /** e.g. "https://canvas.example.edu", or null if not set up. */
    val address: Flow<String?> = store.data.map { it[ADDRESS] }

    /** The signed-in user's name, or null if signed out. */
    val signedInAs: Flow<String?> = store.data.map { it[SIGNED_IN_AS] }

    val lastSyncedAt: Flow<Instant?> = store.data.map { prefs -> prefs[LAST_SYNCED]?.let(Instant::ofEpochMilli) }

    /** True after Canvas said the sign-in expired: the app shows "Canvas sign-in expired — sign in again". */
    val signInExpired: Flow<Boolean> = store.data.map { it[SIGN_IN_EXPIRED] ?: false }

    /** Canvas courses you chose "Not now" for, so the "link your courses" prompt stops asking. */
    val hiddenLinkPrompts: Flow<Set<String>> = store.data.map { it[HIDDEN_LINK_PROMPTS].orEmpty() }

    suspend fun hideLinkPrompts(courseIds: Collection<String>) =
        store.edit { it[HIDDEN_LINK_PROMPTS] = it[HIDDEN_LINK_PROMPTS].orEmpty() + courseIds }

    suspend fun setSignInExpired(expired: Boolean) = store.edit { it[SIGN_IN_EXPIRED] = expired }

    suspend fun setAddress(address: String?) = store.edit { if (address == null) it.remove(ADDRESS) else it[ADDRESS] = address }

    suspend fun setSignedInAs(name: String?) = store.edit { if (name == null) it.remove(SIGNED_IN_AS) else it[SIGNED_IN_AS] = name }

    suspend fun setLastSyncedAt(at: Instant) = store.edit { it[LAST_SYNCED] = at.toEpochMilli() }

    /** Back to "never synced" (used by tests). */
    suspend fun clearLastSynced() = store.edit { it.remove(LAST_SYNCED) }

    private companion object {
        val ADDRESS = stringPreferencesKey("address")
        val SIGNED_IN_AS = stringPreferencesKey("signed_in_as")
        val LAST_SYNCED = longPreferencesKey("last_synced_at")
        val SIGN_IN_EXPIRED = booleanPreferencesKey("sign_in_expired")
        val HIDDEN_LINK_PROMPTS = stringSetPreferencesKey("hidden_link_prompts")
    }
}

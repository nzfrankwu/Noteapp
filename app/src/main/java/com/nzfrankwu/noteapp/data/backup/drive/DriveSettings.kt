package com.nzfrankwu.noteapp.data.backup.drive

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.backupSettingsStore: DataStore<Preferences> by preferencesDataStore(name = "backup_settings")

/**
 * Google Drive backup settings and status (PHASE-6.md 6.10), in Noteapp's private storage. Not part
 * of any backup (a restore must not connect another account). No tokens: Play Services keeps those.
 */
class DriveSettings(context: Context) : DriveStatusStore {
    private val store = context.applicationContext.backupSettingsStore

    val status: Flow<DriveStatus> = store.data.map { p ->
        DriveStatus(
            email = p[EMAIL],
            connectedAt = p[CONNECTED_AT],
            wifiOnly = p[WIFI_ONLY] ?: true,
            lastSuccessAt = p[LAST_SUCCESS],
            lastError = p[LAST_ERROR],
            needsSignIn = p[NEEDS_SIGN_IN] ?: false,
            storedBytes = p[STORED_BYTES] ?: 0L,
            pdfIntervalMinutes = PdfInterval.valid(p[PDF_INTERVAL]),
            changedAt = p[CHANGED_AT],
            pdfSyncedAt = p[PDF_SYNCED_AT],
        )
    }

    override suspend fun current(): DriveStatus = status.first()

    suspend fun email(): String? = current().email

    suspend fun connect(email: String, at: Long) = store.edit {
        if (it[EMAIL] != email) {
            it.remove(LAST_SUCCESS)
            it.remove(STORED_BYTES)
        }
        it[EMAIL] = email
        it[CONNECTED_AT] = at
        it.remove(LAST_ERROR)
        it.remove(NEEDS_SIGN_IN)
    }

    suspend fun disconnect() = store.edit {
        it.remove(EMAIL)
        it.remove(CONNECTED_AT)
        it.remove(LAST_ERROR)
        it.remove(NEEDS_SIGN_IN)
        it.remove(PDF_SYNCED_AT)
    }

    suspend fun setWifiOnly(on: Boolean) = store.edit { it[WIFI_ONLY] = on }

    suspend fun setPdfInterval(minutes: Int) = store.edit { it[PDF_INTERVAL] = PdfInterval.valid(minutes) }

    /** Something changed (notebooks, folders, tasks): the PDF copies are out of date. */
    suspend fun markChanged(at: Long) = store.edit { it[CHANGED_AT] = at }

    override suspend fun recordPdfSync(at: Long) {
        store.edit { it[PDF_SYNCED_AT] = at }
    }

    override suspend fun recordSuccess(at: Long, storedBytes: Long) {
        store.edit {
            it[LAST_SUCCESS] = at
            if (storedBytes > 0) it[STORED_BYTES] = storedBytes
            it.remove(LAST_ERROR)
            it.remove(NEEDS_SIGN_IN)
        }
    }

    override suspend fun recordFailure(message: String, needsSignIn: Boolean) {
        store.edit {
            it[LAST_ERROR] = message
            it[NEEDS_SIGN_IN] = needsSignIn
        }
    }

    private companion object {
        val EMAIL = stringPreferencesKey("email")
        val CONNECTED_AT = longPreferencesKey("connected_at")
        val WIFI_ONLY = booleanPreferencesKey("wifi_only")
        val LAST_SUCCESS = longPreferencesKey("last_success_at")
        val LAST_ERROR = stringPreferencesKey("last_error")
        val NEEDS_SIGN_IN = booleanPreferencesKey("needs_sign_in")
        val STORED_BYTES = longPreferencesKey("stored_bytes")
        val PDF_INTERVAL = androidx.datastore.preferences.core.intPreferencesKey("pdf_interval_minutes")
        val CHANGED_AT = longPreferencesKey("changed_at")
        val PDF_SYNCED_AT = longPreferencesKey("pdf_synced_at")
    }
}

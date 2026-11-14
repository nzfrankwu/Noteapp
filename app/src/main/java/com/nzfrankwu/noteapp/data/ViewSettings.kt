package com.nzfrankwu.noteapp.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.nzfrankwu.noteapp.domain.HomeTab
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/** Remembers which home tab was open last. */
interface HomeTabStore {
    val homeTab: Flow<HomeTab>
    suspend fun setHomeTab(tab: HomeTab)
}

/** Keeps the tab in memory only (screen tests). */
class InMemoryHomeTabStore(initial: HomeTab = HomeTab.OVERVIEW) : HomeTabStore {
    private val tab = MutableStateFlow(initial)
    override val homeTab: Flow<HomeTab> = tab
    override suspend fun setHomeTab(tab: HomeTab) { this.tab.value = tab }
}

private val Context.viewSettingsStore: DataStore<Preferences> by preferencesDataStore(name = "view_settings")

/** How you last left the app's views (in Noteapp's private storage). Nothing secret here. */
class ViewSettings(context: Context) : HomeTabStore {
    private val store = context.applicationContext.viewSettingsStore

    override val homeTab: Flow<HomeTab> = store.data.map { HomeTab.fromKey(it[HOME_TAB]) }

    override suspend fun setHomeTab(tab: HomeTab) {
        store.edit { it[HOME_TAB] = tab.key }
    }

    private companion object {
        val HOME_TAB = stringPreferencesKey("home_tab")
    }
}

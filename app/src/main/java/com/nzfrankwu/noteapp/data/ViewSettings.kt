package com.nzfrankwu.noteapp.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.nzfrankwu.noteapp.domain.HomeTab
import com.nzfrankwu.noteapp.domain.RecentNotebooks
import com.nzfrankwu.noteapp.domain.SplitLayout
import com.nzfrankwu.noteapp.domain.SplitMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

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

/**
 * What the notebook editor remembers between visits (PHASE-5.md 5.7, 5.8): the recently opened
 * notebooks (newest first), and how split view was last left.
 */
interface EditorViewStore {
    val recentNotebooks: Flow<List<String>>
    suspend fun notebookOpened(notebookId: String)

    /** Null until a split mode was chosen (the first split follows the window's shape). */
    val splitMode: Flow<SplitMode?>
    suspend fun setSplitMode(mode: SplitMode)

    /** The first pane's share of the space, 0.25–0.75. */
    val splitRatio: Flow<Float>
    suspend fun setSplitRatio(ratio: Float)
}

/** Keeps the editor's choices in memory only (screen tests). */
class InMemoryEditorViewStore : EditorViewStore {
    private val recent = MutableStateFlow(emptyList<String>())
    private val mode = MutableStateFlow<SplitMode?>(null)
    private val ratio = MutableStateFlow(SplitLayout.DEFAULT_RATIO)
    override val recentNotebooks: Flow<List<String>> = recent
    override suspend fun notebookOpened(notebookId: String) = recent.update { RecentNotebooks.opened(it, notebookId) }
    override val splitMode: Flow<SplitMode?> = mode
    override suspend fun setSplitMode(mode: SplitMode) { this.mode.value = mode }
    override val splitRatio: Flow<Float> = ratio
    override suspend fun setSplitRatio(ratio: Float) { this.ratio.value = SplitLayout.saved(ratio) }
}

private val Context.viewSettingsStore: DataStore<Preferences> by preferencesDataStore(name = "view_settings")

/** How you last left the app's views (in Noteapp's private storage). Nothing secret here. */
class ViewSettings(context: Context) : HomeTabStore, EditorViewStore {
    private val store = context.applicationContext.viewSettingsStore

    override val homeTab: Flow<HomeTab> = store.data.map { HomeTab.fromKey(it[HOME_TAB]) }

    override suspend fun setHomeTab(tab: HomeTab) {
        store.edit { it[HOME_TAB] = tab.key }
    }

    override val recentNotebooks: Flow<List<String>> = store.data.map { RecentNotebooks.decode(it[RECENT]) }

    override suspend fun notebookOpened(notebookId: String) {
        store.edit { it[RECENT] = RecentNotebooks.encode(RecentNotebooks.opened(RecentNotebooks.decode(it[RECENT]), notebookId)) }
    }

    override val splitMode: Flow<SplitMode?> = store.data.map { SplitMode.fromKey(it[SPLIT_MODE]) }

    override suspend fun setSplitMode(mode: SplitMode) {
        store.edit { it[SPLIT_MODE] = mode.key }
    }

    override val splitRatio: Flow<Float> = store.data.map { SplitLayout.saved(it[SPLIT_RATIO]) }

    override suspend fun setSplitRatio(ratio: Float) {
        store.edit { it[SPLIT_RATIO] = SplitLayout.saved(ratio) }
    }

    private companion object {
        val HOME_TAB = stringPreferencesKey("home_tab")
        val RECENT = stringPreferencesKey("recent_notebooks")
        val SPLIT_MODE = stringPreferencesKey("split_mode")
        val SPLIT_RATIO = floatPreferencesKey("split_ratio")
    }
}

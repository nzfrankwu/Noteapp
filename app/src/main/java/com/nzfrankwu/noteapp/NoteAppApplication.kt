package com.nzfrankwu.noteapp

import android.app.Application

/** Runs once when the app starts, before any screen. Holds the [AppContainer]. */
class NoteAppApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}

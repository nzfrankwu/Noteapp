package com.nzfrankwu.noteapp.ui

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.time.Clock
import java.time.Instant

/**
 * The current time, now and then every minute, so task lists move tasks into Overdue/Today on
 * their own while a screen stays open.
 */
fun minuteTicker(clock: Clock): Flow<Instant> = flow {
    while (true) {
        emit(clock.instant())
        delay(60_000)
    }
}

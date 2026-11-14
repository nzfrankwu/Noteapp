package com.nzfrankwu.noteapp.data.db

import androidx.room.TypeConverter
import com.nzfrankwu.noteapp.domain.TaskSource
import com.nzfrankwu.noteapp.domain.TaskStatus
import java.time.Instant

/**
 * How values that SQLite can't store directly are saved:
 *  - Times are saved as UTC milliseconds since 1970, and shown in local time by the UI.
 *  - Status and source are saved by their fixed [TaskStatus.key] / [TaskSource.key], never by
 *    the Kotlin name or position, so renaming or reordering them in code can't scramble data.
 */
class Converters {
    @TypeConverter
    fun instantToMillis(instant: Instant?): Long? = instant?.toEpochMilli()

    @TypeConverter
    fun millisToInstant(millis: Long?): Instant? = millis?.let(Instant::ofEpochMilli)

    @TypeConverter
    fun statusToKey(status: TaskStatus): String = status.key

    @TypeConverter
    fun keyToStatus(key: String): TaskStatus = TaskStatus.fromKey(key)

    @TypeConverter
    fun sourceToKey(source: TaskSource): String = source.key

    @TypeConverter
    fun keyToSource(key: String): TaskSource = TaskSource.fromKey(key)
}

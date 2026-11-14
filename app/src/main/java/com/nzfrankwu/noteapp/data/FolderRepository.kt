package com.nzfrankwu.noteapp.data

import androidx.room.withTransaction
import com.nzfrankwu.noteapp.data.db.Course
import com.nzfrankwu.noteapp.data.db.Folder
import com.nzfrankwu.noteapp.data.db.FolderListItem
import com.nzfrankwu.noteapp.data.db.NoteAppDatabase
import com.nzfrankwu.noteapp.domain.CourseCodes
import kotlinx.coroutines.flow.Flow
import java.time.Clock
import java.time.Instant
import java.util.UUID

/** Why a folder change couldn't be made. The screen turns these into messages. */
sealed interface FolderError {
    data object BlankName : FolderError
    data object NotFound : FolderError
    /** The course is already linked to another folder, called [folderName]. */
    data class CourseAlreadyLinked(val folderName: String) : FolderError
}

sealed interface FolderResult {
    data class Success(val folderId: String) : FolderResult
    data class Failure(val error: FolderError) : FolderResult
}

/**
 * Creating, renaming and deleting folders. Screens go through this instead of the database
 * directly, so the rules live in one tested place.
 *
 * [clock] and [newId] can be swapped in tests to get predictable times and IDs.
 */
class FolderRepository(
    private val db: NoteAppDatabase,
    private val clock: Clock = Clock.systemUTC(),
    private val newId: () -> String = { UUID.randomUUID().toString() },
) {
    private val folderDao = db.folderDao()
    private val courseDao = db.courseDao()

    /** Every folder A–Z with its course code, updating live. */
    fun observeFolders(): Flow<List<FolderListItem>> = folderDao.observeAllWithCourse()

    /**
     * Creates a folder. If [courseCode] is given, the folder is linked to that course, which is
     * reused if it already exists (matching ignores spaces and case) or created otherwise.
     */
    suspend fun createFolder(name: String, courseCode: String?): FolderResult = db.withTransaction {
        val cleanName = name.trim()
        if (cleanName.isEmpty()) return@withTransaction FolderResult.Failure(FolderError.BlankName)

        val now = clock.instant()
        val course = CourseCodes.tidy(courseCode)?.let { findOrCreateCourse(it, now) }
        if (course != null) {
            folderDao.getByCourseId(course.id)?.let { linked ->
                return@withTransaction FolderResult.Failure(FolderError.CourseAlreadyLinked(linked.name))
            }
        }

        val folder = Folder(id = newId(), name = cleanName, courseId = course?.id, createdAt = now, updatedAt = now)
        folderDao.insert(folder)
        FolderResult.Success(folder.id)
    }

    suspend fun renameFolder(folderId: String, newName: String): FolderResult = db.withTransaction {
        val cleanName = newName.trim()
        if (cleanName.isEmpty()) return@withTransaction FolderResult.Failure(FolderError.BlankName)
        val folder = folderDao.getById(folderId)
            ?: return@withTransaction FolderResult.Failure(FolderError.NotFound)

        folderDao.update(folder.copy(name = cleanName, updatedAt = clock.instant()))
        FolderResult.Success(folderId)
    }

    /** Deletes only the folder. Its course and that course's tasks are kept. */
    suspend fun deleteFolder(folderId: String) {
        folderDao.getById(folderId)?.let { folderDao.delete(it) }
    }

    private suspend fun findOrCreateCourse(tidyCode: String, now: Instant): Course {
        val key = CourseCodes.matchKey(tidyCode)
        courseDao.getAll().firstOrNull { CourseCodes.matchKey(it.code) == key }?.let { return it }

        val course = Course(id = newId(), code = tidyCode, name = null, canvasCourseId = null, createdAt = now, updatedAt = now)
        courseDao.insert(course)
        return course
    }
}

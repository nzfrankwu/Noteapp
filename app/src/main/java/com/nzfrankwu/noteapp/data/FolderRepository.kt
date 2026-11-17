package com.nzfrankwu.noteapp.data

import androidx.room.withTransaction
import com.nzfrankwu.noteapp.data.db.Course
import com.nzfrankwu.noteapp.data.db.CourseListItem
import com.nzfrankwu.noteapp.data.db.Folder
import com.nzfrankwu.noteapp.data.db.FolderListItem
import com.nzfrankwu.noteapp.data.db.FolderPlace
import com.nzfrankwu.noteapp.data.db.SubfolderListItem
import com.nzfrankwu.noteapp.data.db.NoteAppDatabase
import com.nzfrankwu.noteapp.domain.CourseCodes
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import java.time.Clock
import java.time.Instant
import java.util.UUID

/** Why a folder change couldn't be made. The screen turns these into messages. */
sealed interface FolderError {
    data object BlankName : FolderError
    data object BlankCourseCode : FolderError
    data object NotFound : FolderError
    /** The course (saved as [courseCode]) is already linked to another folder, called [folderName]. */
    data class CourseAlreadyLinked(val courseCode: String, val folderName: String) : FolderError
    /** Subfolders belong to their top folder's course and can't link one of their own. */
    data object SubfolderCantLinkCourse : FolderError
    /** The folder still holds subfolders or notebooks, so it can't be deleted. */
    data class NotEmpty(val subfolders: Int, val notebooks: Int) : FolderError
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
    private val notebookDao = db.notebookDao()

    /** Top-level folders (the home screen's) A–Z with their course codes, updating live. */
    fun observeFolders(): Flow<List<FolderListItem>> = folderDao.observeTopLevelWithCourse()

    /** The folders inside [parentId], A–Z, with how many things each holds. Updates live. */
    fun observeSubfolders(parentId: String): Flow<List<SubfolderListItem>> = folderDao.observeChildren(parentId)

    /** Every folder at every depth, updating live (for "Add to Noteapp"). */
    fun observeAllFolders(): Flow<List<com.nzfrankwu.noteapp.data.db.Folder>> = folderDao.observeAll()

    /**
     * Where a folder sits: its path from the top folder down to it, and the top folder's course
     * (which subfolders belong to). Null once the folder is deleted. Updates live.
     */
    fun observePlace(folderId: String): Flow<FolderPlace?> =
        combine(folderDao.observeAll(), courseDao.observeAll()) { folders, courses ->
            FolderPlace.of(folderId, folders, courses)
        }.distinctUntilChanged()

    /** Creates a folder inside [parentId]. Subfolders have no course of their own. */
    suspend fun createSubfolder(parentId: String, name: String): FolderResult = db.withTransaction {
        val cleanName = name.trim()
        if (cleanName.isEmpty()) return@withTransaction FolderResult.Failure(FolderError.BlankName)
        folderDao.getById(parentId) ?: return@withTransaction FolderResult.Failure(FolderError.NotFound)
        val now = clock.instant()
        val folder = Folder(id = newId(), name = cleanName, courseId = null, createdAt = now, updatedAt = now, parentId = parentId)
        folderDao.insert(folder)
        FolderResult.Success(folder.id)
    }

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
                return@withTransaction FolderResult.Failure(FolderError.CourseAlreadyLinked(course.code, linked.name))
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

    /** One folder with its course code, updating live (null once deleted). For the folder screen. */
    fun observeFolder(folderId: String): Flow<FolderListItem?> = folderDao.observeWithCourse(folderId)

    /**
     * Links an existing folder to a course (reusing the course if it exists, matching ignores
     * spaces and case). Refused if that course already belongs to another folder.
     */
    suspend fun linkCourse(folderId: String, courseCode: String): FolderResult = db.withTransaction {
        val folder = folderDao.getById(folderId) ?: return@withTransaction FolderResult.Failure(FolderError.NotFound)
        if (folder.parentId != null) return@withTransaction FolderResult.Failure(FolderError.SubfolderCantLinkCourse)
        val code = CourseCodes.tidy(courseCode) ?: return@withTransaction FolderResult.Failure(FolderError.BlankCourseCode)
        val now = clock.instant()
        val course = findOrCreateCourse(code, now)
        folderDao.getByCourseId(course.id)?.let { linked ->
            if (linked.id != folderId) {
                return@withTransaction FolderResult.Failure(FolderError.CourseAlreadyLinked(course.code, linked.name))
            }
        }
        if (folder.courseId != course.id) folderDao.update(folder.copy(courseId = course.id, updatedAt = now))
        FolderResult.Success(folderId)
    }

    /** Current Canvas courses with no folder yet (for the "Link your courses" prompt). */
    fun observeCanvasCoursesWithoutFolder(): Flow<List<Course>> = courseDao.observeCanvasCoursesWithoutFolder()

    /** Current courses without a folder, A–Z: the choices in New folder and Link a course. */
    fun observeCoursesWithoutFolder(): Flow<List<Course>> = courseDao.observeCurrentWithoutFolder()

    /** Every course. */
    fun observeCourses(): Flow<List<Course>> = courseDao.observeAll()

    /** Every course with its folder (if any), A–Z, for Settings → Courses. */
    fun observeCoursesWithFolders(): Flow<List<CourseListItem>> = courseDao.observeAllWithFolder()

    /**
     * Archives a course ([archived] = true) or brings it back. Archived courses' tasks are hidden
     * from Home and get no reminders; nothing is deleted. Returns false if the course is gone.
     */
    suspend fun setCourseArchived(courseId: String, archived: Boolean): Boolean = db.withTransaction {
        val course = courseDao.getById(courseId) ?: return@withTransaction false
        if (course.archivedAt != null != archived) {
            val now = clock.instant()
            courseDao.update(course.copy(archivedAt = if (archived) now else null, updatedAt = now))
        }
        true
    }

    /** Makes a new folder for an existing course (e.g. one imported from Canvas). */
    suspend fun createFolderForCourse(name: String, courseId: String): FolderResult = db.withTransaction {
        val cleanName = name.trim()
        if (cleanName.isEmpty()) return@withTransaction FolderResult.Failure(FolderError.BlankName)
        val course = courseDao.getById(courseId) ?: return@withTransaction FolderResult.Failure(FolderError.NotFound)
        folderDao.getByCourseId(course.id)?.let { linked ->
            return@withTransaction FolderResult.Failure(FolderError.CourseAlreadyLinked(course.code, linked.name))
        }
        val now = clock.instant()
        val folder = Folder(id = newId(), name = cleanName, courseId = course.id, createdAt = now, updatedAt = now)
        folderDao.insert(folder)
        FolderResult.Success(folder.id)
    }

    /**
     * Links an existing folder to an existing course by its ID. Refused if another folder has
     * that course. A course the folder had before stays (with its tasks), just without a folder.
     */
    suspend fun linkCourseById(folderId: String, courseId: String): FolderResult = db.withTransaction {
        val folder = folderDao.getById(folderId) ?: return@withTransaction FolderResult.Failure(FolderError.NotFound)
        if (folder.parentId != null) return@withTransaction FolderResult.Failure(FolderError.SubfolderCantLinkCourse)
        val course = courseDao.getById(courseId) ?: return@withTransaction FolderResult.Failure(FolderError.NotFound)
        folderDao.getByCourseId(course.id)?.let { linked ->
            if (linked.id != folderId) {
                return@withTransaction FolderResult.Failure(FolderError.CourseAlreadyLinked(course.code, linked.name))
            }
        }
        if (folder.courseId != course.id) folderDao.update(folder.copy(courseId = course.id, updatedAt = clock.instant()))
        FolderResult.Success(folderId)
    }

    /** Unlinks the folder from its course. The course and its tasks are kept (still in Due soon). */
    suspend fun unlinkCourse(folderId: String): FolderResult = db.withTransaction {
        val folder = folderDao.getById(folderId) ?: return@withTransaction FolderResult.Failure(FolderError.NotFound)
        if (folder.courseId != null) folderDao.update(folder.copy(courseId = null, updatedAt = clock.instant()))
        FolderResult.Success(folderId)
    }

    /**
     * Deletes the folder, but only when it's empty: a folder holding subfolders or notebooks is
     * refused ([FolderError.NotEmpty]), so nothing inside can be lost by accident. Its course and
     * that course's tasks are kept. Notebooks deleted earlier (hidden, kept for "Recently
     * deleted") stay in the database without a folder.
     */
    suspend fun deleteFolder(folderId: String): FolderResult = db.withTransaction {
        val folder = folderDao.getById(folderId) ?: return@withTransaction FolderResult.Success(folderId)
        val subfolders = folderDao.countChildren(folderId)
        val notebooks = notebookDao.countInFolder(folderId)
        if (subfolders + notebooks > 0) {
            return@withTransaction FolderResult.Failure(FolderError.NotEmpty(subfolders, notebooks))
        }
        folderDao.delete(folder)
        FolderResult.Success(folderId)
    }

    /**
     * The course with this code (ignoring spaces and case), or a new one. Typing the code of an
     * archived course brings it back: you're making a folder for it, so it's current again (B.8).
     */
    private suspend fun findOrCreateCourse(tidyCode: String, now: Instant): Course {
        val key = CourseCodes.matchKey(tidyCode)
        courseDao.getAll().firstOrNull { CourseCodes.matchKey(it.code) == key }?.let { found ->
            if (found.archivedAt == null) return found
            val current = found.copy(archivedAt = null, updatedAt = now)
            courseDao.update(current)
            return current
        }

        val course = Course(id = newId(), code = tidyCode, name = null, canvasCourseId = null, createdAt = now, updatedAt = now)
        courseDao.insert(course)
        return course
    }
}

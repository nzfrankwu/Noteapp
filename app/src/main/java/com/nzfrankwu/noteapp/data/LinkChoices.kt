package com.nzfrankwu.noteapp.data

import com.nzfrankwu.noteapp.data.db.Folder
import com.nzfrankwu.noteapp.data.db.Notebook
import com.nzfrankwu.noteapp.data.db.TaskWithCourse
import com.nzfrankwu.noteapp.domain.TaskRules

/** The notebooks offered by "Link notebook": the task's course folder first, then the others. */
data class NotebookChoices(val inCourse: List<NotebookGroup>, val others: List<NotebookGroup>) {
    val isEmpty: Boolean get() = inCourse.isEmpty() && others.isEmpty()
}

/**
 * The tasks offered by "Link to a task": the notebook's course ([courseId]) first, then the
 * others.
 */
data class TaskChoices(val inCourse: List<TaskWithCourse>, val others: List<TaskWithCourse>, val courseId: String? = null) {
    val isEmpty: Boolean get() = inCourse.isEmpty() && others.isEmpty()
    val all: List<TaskWithCourse> get() = inCourse + others
}

/** Which courses' tasks the task pickers show (PHASE-5.md FB3). */
sealed interface CourseFilter {
    /** The notebook's own course (every course when the notebook has none). */
    data object ThisCourse : CourseFilter

    /** Every other course (every course when the notebook has none). */
    data object OtherCourses : CourseFilter

    /** One chosen course. */
    data class One(val courseId: String, val code: String) : CourseFilter
}

/** "Due soon": due date still ahead, or none. "Already due": due date passed. */
enum class DueFilter { SOON, PAST }

/** What the task pickers' search field and filters are set to. */
data class TaskFilter(
    val query: String = "",
    val course: CourseFilter = CourseFilter.ThisCourse,
    val due: DueFilter = DueFilter.SOON,
)

/** Builds the lists the link pickers show (PHASE-4.md 4.2, 4.3). Plain Kotlin, unit-tested. */
object LinkChoices {
    /**
     * The notebooks (not deleted) that could be linked, leaving out [exclude] (those already linked):
     * those in [courseFolderId] and its subfolders first (with depths counted from that folder),
     * then every other folder's, in folder-tree order.
     */
    fun notebooks(courseFolderId: String?, folders: List<Folder>, notebooks: List<Notebook>, exclude: Set<String>): NotebookChoices {
        val tree = ShareTargets.folderTree(folders)
        val start = tree.indexOfFirst { it.folder.id == courseFolderId }
        val courseRows = if (start < 0) {
            emptyList()
        } else {
            val depth = tree[start].depth
            val end = (start + 1 until tree.size).firstOrNull { tree[it].depth <= depth } ?: tree.size
            tree.subList(start, end).map { it.copy(depth = it.depth - depth) }
        }
        val courseIds = courseRows.mapTo(HashSet()) { it.folder.id }
        val candidates = notebooks.filter { it.id !in exclude }
        return NotebookChoices(
            inCourse = ShareTargets.notebooksByFolder(courseRows, candidates),
            others = ShareTargets.notebooksByFolder(tree.filter { it.folder.id !in courseIds }, candidates),
        )
    }

    /**
     * The tasks that could be linked, leaving out [exclude] (those already linked) and tasks of
     * archived courses: those of [courseId] first, then the others. Each list: unfinished tasks
     * first, by date (target date if set, else due date; undated last), then finished ones.
     */
    fun tasks(courseId: String?, tasks: List<TaskWithCourse>, exclude: Set<String>): TaskChoices {
        val order = compareBy<TaskWithCourse> { it.task.status.isFinished }
            .thenBy { TaskRules.effectiveDate(it.task) == null }
            .thenBy { TaskRules.effectiveDate(it.task) }
            .thenBy(String.CASE_INSENSITIVE_ORDER) { it.task.title }
        val candidates = tasks.filter { it.task.id !in exclude && !it.isCourseArchived }.sortedWith(order)
        val (inCourse, others) = candidates.partition { courseId != null && it.task.courseId == courseId }
        return TaskChoices(inCourse, others, courseId)
    }

    /** Whether [task]'s due date has passed (an undated task never has). */
    fun isPastDue(task: TaskWithCourse, now: java.time.Instant): Boolean = task.task.dueAt?.isBefore(now) == true

    /** Whether every word of [query] is in the task's title or course code (any case). */
    fun matches(task: TaskWithCourse, query: String): Boolean {
        val words = query.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (words.isEmpty()) return true
        val text = task.task.title + " " + task.courseCode.orEmpty()
        return words.all { text.contains(it, ignoreCase = true) }
    }

    /**
     * The tasks the pickers list for [filter] (PHASE-5.md FB3), from [choices]. "Due soon" keeps
     * [tasks]'s order (unfinished first, soonest first); "Already due" puts the latest first.
     */
    fun filter(choices: TaskChoices, filter: TaskFilter, now: java.time.Instant): List<TaskWithCourse> {
        val noCourse = choices.courseId == null
        val byCourse = when (val c = filter.course) {
            CourseFilter.ThisCourse -> if (noCourse) choices.all else choices.inCourse
            CourseFilter.OtherCourses -> if (noCourse) choices.all else choices.others
            is CourseFilter.One -> choices.all.filter { it.task.courseId == c.courseId }
        }
        val found = byCourse.filter { matches(it, filter.query) }
        return when (filter.due) {
            DueFilter.SOON -> found.filter { !isPastDue(it, now) }
            DueFilter.PAST -> found.filter { isPastDue(it, now) }.sortedByDescending { it.task.dueAt }
        }
    }

    /** The other courses with tasks to pick, for "one course" (by course code). */
    fun courses(choices: TaskChoices): List<CourseFilter.One> =
        choices.all.mapNotNull { t -> t.task.courseId?.takeIf { it != choices.courseId }?.let { CourseFilter.One(it, t.courseCode ?: "Course") } }
            .distinctBy { it.courseId }
            .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.code })
}

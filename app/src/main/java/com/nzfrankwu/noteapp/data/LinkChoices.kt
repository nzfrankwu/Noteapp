package com.nzfrankwu.noteapp.data

import com.nzfrankwu.noteapp.data.db.Folder
import com.nzfrankwu.noteapp.data.db.Notebook
import com.nzfrankwu.noteapp.data.db.TaskWithCourse
import com.nzfrankwu.noteapp.domain.TaskRules

/** The notebooks offered by "Link notebook": the task's course folder first, then the others. */
data class NotebookChoices(val inCourse: List<NotebookGroup>, val others: List<NotebookGroup>) {
    val isEmpty: Boolean get() = inCourse.isEmpty() && others.isEmpty()
}

/** The tasks offered by "Link to a task": the notebook's course first, then the others. */
data class TaskChoices(val inCourse: List<TaskWithCourse>, val others: List<TaskWithCourse>) {
    val isEmpty: Boolean get() = inCourse.isEmpty() && others.isEmpty()
}

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
        return TaskChoices(inCourse, others)
    }
}

package com.nzfrankwu.noteapp.data.db

/**
 * Where a folder sits among folders: [path] runs from the top-level folder down to the folder
 * itself, and [course] is the top folder's course, which every folder inside it belongs to.
 */
data class FolderPlace(
    val path: List<Folder>,
    val course: Course?,
) {
    /** The folder itself. */
    val folder: Folder get() = path.last()

    /** The top-level folder this one is inside (or the folder itself if it's top-level). */
    val top: Folder get() = path.first()

    val isSubfolder: Boolean get() = path.size > 1

    companion object {
        /**
         * Works out [folderId]'s place from every folder and course, or null if it doesn't exist.
         * A broken chain (a loop, or a missing parent) stops the walk there, so this never hangs.
         */
        fun of(folderId: String, folders: List<Folder>, courses: List<Course>): FolderPlace? {
            val byId = folders.associateBy { it.id }
            val path = ArrayList<Folder>()
            val seen = HashSet<String>()
            var current = byId[folderId] ?: return null
            while (seen.add(current.id)) {
                path.add(current)
                current = byId[current.parentId ?: break] ?: break
            }
            path.reverse()
            val courseId = path.first().courseId
            return FolderPlace(path, courseId?.let { id -> courses.firstOrNull { it.id == id } })
        }
    }
}

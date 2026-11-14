package com.nzfrankwu.noteapp.ui.timeline

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nzfrankwu.noteapp.data.TaskRepository
import com.nzfrankwu.noteapp.data.db.Course
import com.nzfrankwu.noteapp.data.db.TaskWithCourse
import com.nzfrankwu.noteapp.domain.TimelinePlacement
import com.nzfrankwu.noteapp.domain.TimelineRules
import com.nzfrankwu.noteapp.domain.TimelineZoom
import com.nzfrankwu.noteapp.ui.calendar.TaskViewFilters
import com.nzfrankwu.noteapp.ui.home.DueItem
import com.nzfrankwu.noteapp.ui.minuteTicker
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** One task's row: its bar (or off-screen arrow), and where its target date falls. */
data class TimelineRow(
    val task: DueItem,
    val placement: TimelinePlacement,
    /** ◆ "My target date", in days from the first visible day, if it's within the visible days. */
    val target: Double?,
    /** The day to jump to when an off-screen arrow is tapped. */
    val jumpTo: LocalDate,
)

/** What the Timeline tab shows (PHASE-1B.md, B.6). */
data class TimelineState(
    val loaded: Boolean = false,
    val zoom: TimelineZoom = TimelineZoom.TWO_WEEKS,
    val days: List<LocalDate> = emptyList(),
    /** One row per dated task, by due date. */
    val rows: List<TimelineRow> = emptyList(),
    /** Tasks left out because they have no due date (counted with the same filters). */
    val undatedCount: Int = 0,
    val courses: List<Course> = emptyList(),
    val filters: TaskViewFilters = TaskViewFilters(),
    /** "Now", in days from the first visible day (the blue line), or null when off-screen. */
    val nowPosition: Double? = null,
    val now: Instant = Instant.EPOCH,
    val zone: ZoneId = ZoneId.systemDefault(),
) {
    val today: LocalDate get() = now.atZone(zone).toLocalDate()

    companion object {
        fun build(
            tasks: List<TaskWithCourse>,
            courses: List<Course>,
            start: LocalDate,
            zoom: TimelineZoom,
            filters: TaskViewFilters,
            now: Instant,
            zone: ZoneId,
        ): TimelineState {
            val days = TimelineRules.days(start, zoom)
            val shown = tasks
                .filter { filters.courseId == null || it.task.courseId == filters.courseId }
                .filter { filters.showFinished || !it.task.status.isFinished }
            val rows = shown
                .filter { it.task.dueAt != null }
                .sortedWith(compareBy<TaskWithCourse> { it.task.dueAt }.thenBy(String.CASE_INSENSITIVE_ORDER) { it.task.title })
                .map { item ->
                    val task = item.task
                    val due = task.dueAt!!
                    val barStart = TimelineRules.barStart(task.availableAt, task.createdAt, due)
                    val startPos = TimelineRules.position(barStart, start, zone)
                    val placement = TimelineRules.placement(startPos, TimelineRules.position(due, start, zone), zoom.days)
                    val target = task.targetAt?.let { TimelineRules.position(it, start, zone) }?.takeIf { it in 0.0..zoom.days.toDouble() }
                    val jumpTo = when (placement) {
                        TimelinePlacement.After -> barStart.atZone(zone).toLocalDate()
                        else -> due.atZone(zone).toLocalDate()
                    }
                    TimelineRow(DueItem(item), placement, target, jumpTo)
                }
            val nowPos = TimelineRules.position(now, start, zone).takeIf { it in 0.0..zoom.days.toDouble() }
            return TimelineState(
                loaded = true,
                zoom = zoom,
                days = days,
                rows = rows,
                undatedCount = shown.count { it.task.dueAt == null },
                courses = courses,
                filters = filters,
                nowPosition = nowPos,
                now = now,
                zone = zone,
            )
        }
    }
}

/** The Timeline tab: which days are visible, the zoom and the filters. */
class TimelineViewModel(
    private val tasks: TaskRepository,
    private val clock: Clock = Clock.systemDefaultZone(),
    private val zone: ZoneId = ZoneId.systemDefault(),
) : ViewModel() {
    private val zoom = MutableStateFlow(TimelineZoom.TWO_WEEKS)
    private val start = MutableStateFlow(TimelineRules.defaultStart(today(), TimelineZoom.TWO_WEEKS))
    private val filters = MutableStateFlow(TaskViewFilters())

    private fun today(): LocalDate = clock.instant().atZone(zone).toLocalDate()

    val state: StateFlow<TimelineState> = combine(
        tasks.observeAllWithCourse(),
        tasks.observeCourses(),
        minuteTicker(clock),
        combine(start, zoom, ::Pair),
        filters,
    ) { all, courses, now, (start, zoom), filters ->
        TimelineState.build(all, courses, start, zoom, filters, now, zone)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TimelineState())

    fun previous() = start.update { TimelineRules.shift(it, zoom.value, -1) }
    fun next() = start.update { TimelineRules.shift(it, zoom.value, +1) }
    fun showToday() { start.value = TimelineRules.defaultStart(today(), zoom.value) }

    fun setZoom(newZoom: TimelineZoom) {
        if (newZoom == zoom.value) return
        start.value = TimelineRules.startAfterZoom(start.value, zoom.value, newZoom, today())
        zoom.value = newZoom
    }

    /** An off-screen arrow was tapped: move so that day is in view. */
    fun jumpTo(day: LocalDate) { start.value = TimelineRules.startShowing(day, zoom.value) }

    fun setCourse(courseId: String?) = filters.update { it.copy(courseId = courseId) }
    fun toggleShowFinished() = filters.update { it.copy(showFinished = !it.showFinished) }
}

package com.nzfrankwu.noteapp.ui.calendar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nzfrankwu.noteapp.data.TaskRepository
import com.nzfrankwu.noteapp.data.db.Course
import com.nzfrankwu.noteapp.data.db.TaskWithCourse
import com.nzfrankwu.noteapp.domain.CalendarEntry
import com.nzfrankwu.noteapp.domain.CalendarRules
import com.nzfrankwu.noteapp.domain.TaskStatus
import com.nzfrankwu.noteapp.ui.home.DueItem
import com.nzfrankwu.noteapp.ui.minuteTicker
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

/** One day cell of the month grid. */
data class CalendarDay(
    val date: LocalDate,
    /** False for the greyed days from the months before and after. */
    val inMonth: Boolean,
    val isToday: Boolean,
    val entries: List<CalendarEntry<DueItem>>,
)

/** Filters shared by the calendar (and the timeline): one course or all, and Show finished. */
data class TaskViewFilters(
    /** A course's ID, or null for all courses. */
    val courseId: String? = null,
    val showFinished: Boolean = false,
)

/** What the Calendar tab shows (PHASE-1B.md, B.5). */
data class CalendarState(
    val loaded: Boolean = false,
    val month: YearMonth = YearMonth.of(2026, 1),
    val weeks: List<List<CalendarDay>> = emptyList(),
    /** Courses to filter by, A–Z. */
    val courses: List<Course> = emptyList(),
    val filters: TaskViewFilters = TaskViewFilters(),
    val now: Instant = Instant.EPOCH,
    val zone: ZoneId = ZoneId.systemDefault(),
) {
    val today: LocalDate get() = now.atZone(zone).toLocalDate()

    /** The day [date], if it's in the grid. */
    fun day(date: LocalDate): CalendarDay? = weeks.asSequence().flatten().firstOrNull { it.date == date }

    companion object {
        fun build(
            tasks: List<TaskWithCourse>,
            courses: List<Course>,
            month: YearMonth,
            filters: TaskViewFilters,
            now: Instant,
            zone: ZoneId,
        ): CalendarState {
            val weeks = CalendarRules.weeks(month)
            val today = now.atZone(zone).toLocalDate()
            val shown = tasks.filter { filters.courseId == null || it.task.courseId == filters.courseId }.map(::DueItem)
            val byDay = CalendarRules.byDay(shown, zone, weeks.first().first(), weeks.last().last(), filters.showFinished)
            return CalendarState(
                loaded = true,
                month = month,
                weeks = weeks.map { week ->
                    week.map { date ->
                        CalendarDay(date, YearMonth.from(date) == month, date == today, byDay[date].orEmpty())
                    }
                },
                courses = courses,
                filters = filters,
                now = now,
                zone = zone,
            )
        }
    }
}

/** The Calendar tab: which month is shown, the filters, and the day whose list is open. */
class CalendarViewModel(
    private val tasks: TaskRepository,
    private val clock: Clock = Clock.systemDefaultZone(),
    private val zone: ZoneId = ZoneId.systemDefault(),
) : ViewModel() {
    private val month = MutableStateFlow(YearMonth.from(clock.instant().atZone(zone)))
    private val filters = MutableStateFlow(TaskViewFilters())

    private val _openDay = MutableStateFlow<LocalDate?>(null)

    /** The day whose task list is open in the side sheet, or null. */
    val openDay: StateFlow<LocalDate?> = _openDay.asStateFlow()

    val state: StateFlow<CalendarState> = combine(
        tasks.observeAllWithCourse(),
        tasks.observeCourses(),
        minuteTicker(clock),
        month,
        filters,
    ) { all, courses, now, month, filters ->
        CalendarState.build(all, courses, month, filters, now, zone)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CalendarState())

    fun previousMonth() = changeMonth { it.minusMonths(1) }
    fun nextMonth() = changeMonth { it.plusMonths(1) }
    fun showToday() = changeMonth { YearMonth.from(clock.instant().atZone(zone)) }

    private fun changeMonth(change: (YearMonth) -> YearMonth) {
        month.update(change)
        _openDay.value = null
    }

    fun setCourse(courseId: String?) = filters.update { it.copy(courseId = courseId) }
    fun toggleShowFinished() = filters.update { it.copy(showFinished = !it.showFinished) }

    fun openDay(date: LocalDate) { _openDay.value = date }
    fun closeDay() { _openDay.value = null }

    fun setStatus(taskId: String, status: TaskStatus) {
        viewModelScope.launch { tasks.setStatus(taskId, status) }
    }
}

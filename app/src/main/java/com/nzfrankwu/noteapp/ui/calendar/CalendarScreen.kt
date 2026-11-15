package com.nzfrankwu.noteapp.ui.calendar

import android.text.format.DateFormat
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nzfrankwu.noteapp.R
import com.nzfrankwu.noteapp.domain.CalendarEntry
import com.nzfrankwu.noteapp.domain.CalendarRules
import com.nzfrankwu.noteapp.domain.DateText
import com.nzfrankwu.noteapp.domain.TaskRules
import com.nzfrankwu.noteapp.domain.TaskStatus
import com.nzfrankwu.noteapp.ui.components.CourseFilterButton
import com.nzfrankwu.noteapp.ui.components.ShowFinishedCheckbox
import com.nzfrankwu.noteapp.ui.components.TaskCard
import com.nzfrankwu.noteapp.ui.home.DueItem
import com.nzfrankwu.noteapp.ui.theme.Dimens
import com.nzfrankwu.noteapp.ui.theme.LocalCourseColors
import com.nzfrankwu.noteapp.ui.theme.LocalStatusColors
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.floor

/** What the calendar's buttons do. */
class CalendarActions(
    val onPreviousMonth: () -> Unit = {},
    val onNextMonth: () -> Unit = {},
    val onToday: () -> Unit = {},
    val onSelectCourse: (String?) -> Unit = {},
    val onToggleShowFinished: () -> Unit = {},
    val onOpenDay: (LocalDate) -> Unit = {},
    val onCloseDay: () -> Unit = {},
    val onOpenTask: (taskId: String) -> Unit = {},
    val onStatusChange: (taskId: String, TaskStatus) -> Unit = { _, _ -> },
    val onAddTaskOnDay: (LocalDate) -> Unit = {},
)

/** The Calendar tab, connected to its [CalendarViewModel]. */
@Composable
fun CalendarScreen(
    viewModel: CalendarViewModel,
    onOpenTask: (taskId: String) -> Unit,
    onAddTaskOnDay: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val openDay by viewModel.openDay.collectAsStateWithLifecycle()
    CalendarContent(
        state = state,
        openDay = openDay,
        use24Hour = DateFormat.is24HourFormat(LocalContext.current),
        actions = CalendarActions(
            onPreviousMonth = viewModel::previousMonth,
            onNextMonth = viewModel::nextMonth,
            onToday = viewModel::showToday,
            onSelectCourse = viewModel::setCourse,
            onToggleShowFinished = viewModel::toggleShowFinished,
            onOpenDay = viewModel::openDay,
            onCloseDay = viewModel::closeDay,
            onOpenTask = onOpenTask,
            onStatusChange = viewModel::setStatus,
            onAddTaskOnDay = onAddTaskOnDay,
        ),
        modifier = modifier,
    )
}

/**
 * The month calendar (PHASE-1B.md, B.5): a toolbar, then a Monday-to-Sunday month grid with each
 * task on its due date. Tapping a day opens its list in a side sheet.
 */
@Composable
fun CalendarContent(
    state: CalendarState,
    openDay: LocalDate?,
    use24Hour: Boolean,
    actions: CalendarActions,
    modifier: Modifier = Modifier,
) {
    Box(modifier.fillMaxSize().testTag("calendar")) {
        Column(Modifier.fillMaxSize().padding(start = Dimens.ScreenPadding, end = Dimens.ScreenPadding, bottom = Dimens.ScreenPadding)) {
            CalendarToolbar(state, actions)
            if (state.loaded) {
                WeekdayHeader()
                MonthGrid(state, use24Hour, actions, Modifier.weight(1f))
            }
        }
        DaySheet(state, openDay, actions)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CalendarToolbar(state: CalendarState, actions: CalendarActions) {
    FlowRow(
        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
        verticalArrangement = Arrangement.Center,
        itemVerticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = actions.onPreviousMonth, modifier = Modifier.testTag("calendar-previous")) {
                Icon(painterResource(R.drawable.ic_chevron_left), contentDescription = "Previous month")
            }
            Text(
                DateText.month(state.month),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.width(190.dp).testTag("calendar-month"),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
            IconButton(onClick = actions.onNextMonth, modifier = Modifier.testTag("calendar-next")) {
                Icon(painterResource(R.drawable.ic_chevron_right), contentDescription = "Next month")
            }
            OutlinedButton(onClick = actions.onToday, modifier = Modifier.padding(start = 4.dp).testTag("calendar-today")) {
                Text("Today")
            }
        }
        Spacer(Modifier.weight(1f))
        if (!state.filters.courseFixed) CourseFilterButton(state.courses, state.filters.courseId, actions.onSelectCourse)
        ShowFinishedCheckbox(state.filters.showFinished, actions.onToggleShowFinished)
    }
}

@Composable
private fun WeekdayHeader() {
    Row(Modifier.fillMaxWidth().padding(bottom = 4.dp)) {
        DayOfWeek.entries.forEach { day ->
            Text(
                day.getDisplayName(TextStyle.SHORT, Locale.ENGLISH),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f).padding(start = 8.dp),
            )
        }
    }
}

/** The weeks of the month. Swipe left or right to change month. */
@Composable
private fun MonthGrid(state: CalendarState, use24Hour: Boolean, actions: CalendarActions, modifier: Modifier = Modifier) {
    val swipeDistance = with(LocalDensity.current) { 80.dp.toPx() }
    val currentActions by rememberUpdatedState(actions) // the gesture outlives recompositions
    Column(
        modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.small)
            .pointerInput(swipeDistance) {
                var dragged = 0f
                detectHorizontalDragGestures(
                    onDragStart = { dragged = 0f },
                    onDragEnd = {
                        when {
                            dragged <= -swipeDistance -> currentActions.onNextMonth()
                            dragged >= swipeDistance -> currentActions.onPreviousMonth()
                        }
                    },
                ) { _, amount -> dragged += amount }
            }
            .testTag("calendar-grid"),
    ) {
        state.weeks.forEach { week ->
            Row(Modifier.fillMaxWidth().weight(1f)) {
                week.forEach { day ->
                    DayCell(day, state, use24Hour, actions, Modifier.weight(1f).fillMaxHeight())
                }
            }
        }
    }
}

/** Height of one chip, and the gap below it. */
private val ChipHeight = 36.dp
private val CompactChipHeight = 20.dp
private val ChipGap = 2.dp

/** Below this cell width (e.g. portrait), chips show the title only. */
private val CompactCellWidth = 124.dp

@Composable
private fun DayCell(day: CalendarDay, state: CalendarState, use24Hour: Boolean, actions: CalendarActions, modifier: Modifier) {
    val outline = MaterialTheme.colorScheme.outlineVariant
    val label = DateText.date(day.date) + when (val n = day.entries.size) {
        0 -> ", nothing due"
        1 -> ", 1 task"
        else -> ", $n tasks"
    }
    BoxWithConstraints(
        modifier
            .border(0.5.dp, outline)
            .background(if (day.inMonth) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.background)
            .clickable { actions.onOpenDay(day.date) }
            .semantics { contentDescription = label }
            .testTag("calendar-day-${day.date}")
            .padding(4.dp),
    ) {
        val compact = maxWidth < CompactCellWidth
        val chipHeight = if (compact) CompactChipHeight else ChipHeight
        val available = maxHeight - DayNumberSize - 4.dp
        val fit = floor(available / (chipHeight + ChipGap)).toInt().coerceAtLeast(0)
        val shown = CalendarRules.chipsShown(day.entries.size, fit)

        Column(verticalArrangement = Arrangement.spacedBy(ChipGap)) {
            DayNumber(day)
            Spacer(Modifier.height(2.dp))
            day.entries.take(shown).forEach { entry ->
                TaskChip(entry, state, compact, use24Hour, chipHeight) { actions.onOpenTask(entry.task.id) }
            }
            val more = day.entries.size - shown
            if (more > 0) {
                Text(
                    "+$more more",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    modifier = Modifier
                        .clickable { actions.onOpenDay(day.date) }
                        .padding(horizontal = 4.dp, vertical = 1.dp)
                        .testTag("calendar-more-${day.date}"),
                )
            }
        }
    }
}

private val DayNumberSize = 26.dp

@Composable
private fun DayNumber(day: CalendarDay) {
    val colors = MaterialTheme.colorScheme
    Box(
        Modifier
            .size(DayNumberSize)
            .then(if (day.isToday) Modifier.background(colors.primary, CircleShape) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            day.date.dayOfMonth.toString(),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (day.isToday) FontWeight.Bold else FontWeight.Normal,
            color = when {
                day.isToday -> colors.onPrimary
                day.inMonth -> colors.onSurface
                else -> colors.onSurfaceVariant.copy(alpha = 0.6f)
            },
        )
    }
}

/**
 * One task in a day cell: a course-colour bar, the title, and (when there's room) the due time and
 * a status dot. Overdue tasks are red; finished ones are faded.
 */
@Composable
private fun TaskChip(
    entry: CalendarEntry<DueItem>,
    state: CalendarState,
    compact: Boolean,
    use24Hour: Boolean,
    height: Dp,
    onClick: () -> Unit,
) {
    val task = entry.task
    val overdue = TaskRules.isOverdue(task, state.now)
    val courseColor = LocalCourseColors.current.of(task.item.courseCode)
    val titleColor = if (overdue) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
    Row(
        Modifier
            .fillMaxWidth()
            .height(height)
            .alpha(if (task.status.isFinished) 0.5f else 1f)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh, RoundedCornerShape(4.dp))
            .clickable(onClick = onClick)
            .testTag("calendar-chip"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(3.dp).fillMaxHeight().background(courseColor.accent, RoundedCornerShape(topStart = 4.dp, bottomStart = 4.dp)))
        Column(Modifier.padding(horizontal = 4.dp).weight(1f)) {
            Text(
                task.title,
                style = MaterialTheme.typography.labelMedium,
                color = titleColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (!compact) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val time = DateText.time(entry.time, use24Hour)
                    Text(
                        if (entry.isTarget) "Target $time" else time,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (overdue) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    Spacer(Modifier.width(4.dp))
                    StatusDot(task.status)
                }
            }
        }
    }
}

@Composable
private fun StatusDot(status: TaskStatus) {
    Box(
        Modifier
            .size(7.dp)
            .background(LocalStatusColors.current.of(status).content, CircleShape)
            .semantics { contentDescription = status.label },
    )
}

/** The side sheet with one day's tasks and "Add task on this day". */
@Composable
private fun DaySheet(state: CalendarState, openDay: LocalDate?, actions: CalendarActions) {
    BackHandler(enabled = openDay != null) { actions.onCloseDay() }
    AnimatedVisibility(visible = openDay != null, enter = fadeIn(), exit = fadeOut()) {
        Box(
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.32f))
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { actions.onCloseDay() },
        )
    }
    // The last day opened, kept while the sheet slides away (openDay is already null by then).
    var shownDay by remember { mutableStateOf(openDay) }
    if (openDay != null) shownDay = openDay
    BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.CenterEnd) {
        val width = min(maxWidth, 440.dp)
        AnimatedVisibility(
            visible = openDay != null,
            enter = slideInHorizontally { it },
            exit = slideOutHorizontally { it },
        ) {
            val date = shownDay ?: return@AnimatedVisibility
            val entries = state.day(date)?.entries.orEmpty()
            Surface(
                modifier = Modifier.width(width).fillMaxHeight().testTag("day-sheet"),
                shape = RoundedCornerShape(topStart = 16.dp, bottomStart = 16.dp),
                color = MaterialTheme.colorScheme.background,
                tonalElevation = 2.dp,
                shadowElevation = 8.dp,
            ) {
                Column(Modifier.padding(Dimens.PanelPadding)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            DateText.date(date, state.today.year),
                            style = MaterialTheme.typography.titleLarge,
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(onClick = actions.onCloseDay) {
                            Icon(painterResource(R.drawable.ic_close), contentDescription = "Close")
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(Dimens.ItemGap),
                        contentPadding = PaddingValues(bottom = Dimens.ItemGap),
                    ) {
                        if (entries.isEmpty()) {
                            item {
                                Text(
                                    "Nothing due on this day.",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(vertical = 8.dp),
                                )
                            }
                        }
                        items(entries, key = { it.task.id }) { entry ->
                            TaskCard(
                                task = entry.task.item.task,
                                courseCode = entry.task.item.courseCode,
                                now = state.now,
                                zone = state.zone,
                                onClick = { actions.onOpenTask(entry.task.id) },
                                onStatusChange = { actions.onStatusChange(entry.task.id, it) },
                                compact = true,
                            )
                        }
                    }
                    FilledTonalButton(
                        onClick = { actions.onAddTaskOnDay(date) },
                        contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
                        modifier = Modifier.fillMaxWidth().testTag("add-task-on-day"),
                    ) {
                        Icon(painterResource(R.drawable.ic_add), contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                        Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                        Text("Add task on this day")
                    }
                }
            }
        }
    }
}

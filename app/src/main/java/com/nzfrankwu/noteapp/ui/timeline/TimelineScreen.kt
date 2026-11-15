package com.nzfrankwu.noteapp.ui.timeline

import android.text.format.DateFormat
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nzfrankwu.noteapp.R
import com.nzfrankwu.noteapp.domain.DateText
import com.nzfrankwu.noteapp.domain.TaskRules
import com.nzfrankwu.noteapp.domain.TimelinePlacement
import com.nzfrankwu.noteapp.domain.TimelineZoom
import com.nzfrankwu.noteapp.ui.components.CourseFilterButton
import com.nzfrankwu.noteapp.ui.components.CourseTag
import com.nzfrankwu.noteapp.ui.components.EmptyState
import com.nzfrankwu.noteapp.ui.components.SegmentedTabs
import com.nzfrankwu.noteapp.ui.components.ShowFinishedCheckbox
import com.nzfrankwu.noteapp.ui.theme.Dimens
import com.nzfrankwu.noteapp.ui.theme.LocalCourseColors
import com.nzfrankwu.noteapp.ui.theme.LocalStatusColors
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

/** What the timeline's buttons do. */
class TimelineActions(
    val onPrevious: () -> Unit = {},
    val onNext: () -> Unit = {},
    val onToday: () -> Unit = {},
    val onZoom: (TimelineZoom) -> Unit = {},
    val onSelectCourse: (String?) -> Unit = {},
    val onToggleShowFinished: () -> Unit = {},
    val onJumpTo: (LocalDate) -> Unit = {},
    val onOpenTask: (taskId: String) -> Unit = {},
)

/** The Timeline tab, connected to its [TimelineViewModel]. */
@Composable
fun TimelineScreen(viewModel: TimelineViewModel, onOpenTask: (taskId: String) -> Unit, modifier: Modifier = Modifier) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    TimelineContent(
        state = state,
        use24Hour = DateFormat.is24HourFormat(LocalContext.current),
        actions = TimelineActions(
            onPrevious = viewModel::previous,
            onNext = viewModel::next,
            onToday = viewModel::showToday,
            onZoom = viewModel::setZoom,
            onSelectCourse = viewModel::setCourse,
            onToggleShowFinished = viewModel::toggleShowFinished,
            onJumpTo = viewModel::jumpTo,
            onOpenTask = onOpenTask,
        ),
        modifier = modifier,
    )
}

/**
 * The timeline (PHASE-1B.md, B.6): one row per dated task, with a bar from when it became
 * available (or was added) to when it's due. Days run across the top; a blue line marks now.
 */
@Composable
fun TimelineContent(state: TimelineState, use24Hour: Boolean, actions: TimelineActions, modifier: Modifier = Modifier) {
    Column(
        modifier
            .fillMaxSize()
            .padding(start = Dimens.ScreenPadding, end = Dimens.ScreenPadding, bottom = Dimens.ScreenPadding)
            .testTag("timeline"),
    ) {
        TimelineToolbar(state, actions)
        if (!state.loaded) return@Column
        BoxWithConstraints(Modifier.fillMaxWidth().weight(1f)) {
            val dayWidth = maxWidth / state.days.size
            Column(Modifier.fillMaxSize()) {
                DayHeader(state, dayWidth)
                Box(
                    Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.small)
                        .swipeToMove(rememberUpdatedState(actions)),
                ) {
                    DayBackground(state, dayWidth)
                    // Behind the bars, so it never crosses a title; it shows in the gaps between them.
                    NowLine(state, dayWidth)
                    if (state.rows.isEmpty()) {
                        EmptyState(
                            title = "No dated tasks",
                            message = "Tasks with a due date show here as bars.",
                            modifier = Modifier.fillMaxSize(),
                        )
                    } else {
                        TimelineRows(state, dayWidth, use24Hour, actions)
                    }
                }
            }
        }
        if (state.undatedCount > 0) {
            Text(
                if (state.undatedCount == 1) "1 task without a due date" else "${state.undatedCount} tasks without a due date",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp).testTag("timeline-undated"),
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun TimelineToolbar(state: TimelineState, actions: TimelineActions) {
    FlowRow(
        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
        itemVerticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = actions.onPrevious, modifier = Modifier.testTag("timeline-previous")) {
                Icon(painterResource(R.drawable.ic_chevron_left), contentDescription = "Earlier")
            }
            IconButton(onClick = actions.onNext, modifier = Modifier.testTag("timeline-next")) {
                Icon(painterResource(R.drawable.ic_chevron_right), contentDescription = "Later")
            }
            OutlinedButton(onClick = actions.onToday, modifier = Modifier.padding(start = 4.dp, end = 12.dp).testTag("timeline-today")) {
                Text("Today")
            }
            SegmentedTabs(
                options = TimelineZoom.entries,
                selected = state.zoom,
                label = { it.label },
                testTag = { "timeline-zoom-${it.name}" },
                onSelect = actions.onZoom,
            )
        }
        Spacer(Modifier.weight(1f))
        if (!state.filters.courseFixed) CourseFilterButton(state.courses, state.filters.courseId, actions.onSelectCourse)
        ShowFinishedCheckbox(state.filters.showFinished, actions.onToggleShowFinished)
    }
}

/** "M 16" over each day; the 1st of a month also shows the month's name. */
@Composable
private fun DayHeader(state: TimelineState, dayWidth: Dp) {
    Row(Modifier.fillMaxWidth().height(40.dp)) {
        state.days.forEach { day ->
            val isToday = day == state.today
            val weekday = day.dayOfWeek.getDisplayName(TextStyle.NARROW, Locale.ENGLISH)
            val month = day.month.getDisplayName(TextStyle.SHORT, Locale.ENGLISH)
            Column(Modifier.width(dayWidth), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    if (day.dayOfMonth == 1 && dayWidth >= 36.dp) "$weekday $month" else weekday,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
                Text(
                    day.dayOfMonth.toString(),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                    color = if (isToday) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

/** Weekends lightly shaded, and a thin line at the start of each week. */
@Composable
private fun DayBackground(state: TimelineState, dayWidth: Dp) {
    val shade = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.045f)
    val weekLine = MaterialTheme.colorScheme.outlineVariant
    Canvas(Modifier.fillMaxSize()) {
        val w = dayWidth.toPx()
        state.days.forEachIndexed { i, day ->
            if (day.dayOfWeek == DayOfWeek.SATURDAY || day.dayOfWeek == DayOfWeek.SUNDAY) {
                drawRect(shade, topLeft = Offset(i * w, 0f), size = Size(w, size.height))
            }
            if (day.dayOfWeek == DayOfWeek.MONDAY && i > 0) {
                drawLine(weekLine, Offset(i * w, 0f), Offset(i * w, size.height), strokeWidth = 1.dp.toPx())
            }
        }
    }
}

/** The blue line for "now". */
@Composable
private fun NowLine(state: TimelineState, dayWidth: Dp) {
    val position = state.nowPosition ?: return
    val color = MaterialTheme.colorScheme.primary
    Canvas(Modifier.fillMaxSize().testTag("timeline-now")) {
        val x = (position * dayWidth.toPx()).toFloat()
        drawLine(color, Offset(x, 0f), Offset(x, size.height), strokeWidth = 2.dp.toPx())
    }
}

/** Swipe sideways to move in time. [actions] is read when a swipe ends, so it's always the latest. */
private fun Modifier.swipeToMove(actions: State<TimelineActions>): Modifier = this.then(
    Modifier.pointerInput(Unit) {
        val distance = 80.dp.toPx()
        var dragged = 0f
        detectHorizontalDragGestures(
            onDragStart = { dragged = 0f },
            onDragEnd = {
                when {
                    dragged <= -distance -> actions.value.onNext()
                    dragged >= distance -> actions.value.onPrevious()
                }
            },
        ) { _, amount -> dragged += amount }
    },
)

private val RowHeight = 44.dp

/** Narrowest bar drawn, so a very short task can still be seen and tapped. */
private val MinBarWidth = 12.dp
private val BarHeight = 32.dp

/** Bars narrower than this put their label beside the bar instead of on it. */
private val LabelInsideMinWidth = 200.dp
private val OutsideLabelWidth = 200.dp

@Composable
private fun TimelineRows(state: TimelineState, dayWidth: Dp, use24Hour: Boolean, actions: TimelineActions) {
    val listState = rememberLazyListState()
    // When the visible days change, scroll to the first task that's in view (or after it).
    LaunchedEffect(state.days.first()) {
        val first = state.rows.indexOfFirst { it.placement !is TimelinePlacement.Before }
        listState.scrollToItem(if (first >= 0) first else (state.rows.size - 1).coerceAtLeast(0))
    }
    LazyColumn(state = listState, modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(vertical = 4.dp)) {
        items(state.rows, key = { it.task.id }) { row ->
            Box(Modifier.fillMaxWidth().height(RowHeight).alpha(if (row.task.status.isFinished) 0.5f else 1f)) {
                when (val p = row.placement) {
                    TimelinePlacement.Before -> EdgeArrow(row, state, toLeft = true) { actions.onJumpTo(row.jumpTo) }
                    TimelinePlacement.After -> EdgeArrow(row, state, toLeft = false) { actions.onJumpTo(row.jumpTo) }
                    is TimelinePlacement.Visible -> Bar(row, p, state, dayWidth, use24Hour) { actions.onOpenTask(row.task.id) }
                }
            }
        }
    }
}

@Composable
private fun BoxScope.Bar(row: TimelineRow, p: TimelinePlacement.Visible, state: TimelineState, dayWidth: Dp, use24Hour: Boolean, onClick: () -> Unit) {
    val task = row.task
    val overdue = TaskRules.isOverdue(task, state.now)
    val course = LocalCourseColors.current.of(task.item.courseCode)
    val total = dayWidth * state.days.size
    val width = (dayWidth * (p.end - p.start).toFloat()).coerceAtLeast(MinBarWidth)
    // A short bar near the right edge moves left rather than sticking out of the grid.
    val x = (dayWidth * p.start.toFloat()).coerceAtMost(total - width)
    val due = task.dueAt!!.atZone(state.zone)
    val dueText = "${DateText.time(due.toLocalTime(), use24Hour)} ${due.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.ENGLISH)}"
    val description = "${task.title}, ${task.item.courseCode ?: "no course"}, due ${DateText.dateTime(task.dueAt!!, state.zone, state.now)}" +
        (if (overdue) ", overdue" else "")
    val labelInside = width >= LabelInsideMinWidth
    val shape = RoundedCornerShape(6.dp)

    Box(
        Modifier
            .offset(x = x)
            .width(width)
            .height(BarHeight)
            .align(Alignment.CenterStart)
            .background(course.container, shape)
            .then(if (overdue) Modifier.border(1.5.dp, MaterialTheme.colorScheme.error, shape) else Modifier)
            .clickable(onClick = onClick)
            .semantics { contentDescription = description }
            .testTag("timeline-bar"),
    ) {
        if (!p.startsEarlier) {
            Box(Modifier.width(3.dp).fillMaxHeight().background(course.accent, RoundedCornerShape(topStart = 6.dp, bottomStart = 6.dp)))
        }
        if (labelInside) {
            BarLabel(row, Modifier.align(Alignment.CenterStart).padding(start = 8.dp, end = 8.dp), startsEarlier = p.startsEarlier, endsLater = p.endsLater)
        }
    }
    // Target date ◆
    row.target?.let { target ->
        Box(
            Modifier
                .offset(x = dayWidth * target.toFloat() - 5.dp)
                .align(Alignment.CenterStart)
                .size(10.dp)
                .rotate(45f)
                .background(MaterialTheme.colorScheme.onSurface)
                .semantics { contentDescription = "My target date" },
        )
    }
    // Beside the bar: the label (for short bars) and the due time.
    val spaceAfter = total - x - width
    when {
        !labelInside && spaceAfter >= OutsideLabelWidth -> Row(
            // Never past the right edge: the title shortens so the due time still fits.
            Modifier.offset(x = x + width + 6.dp).align(Alignment.CenterStart).widthIn(max = spaceAfter - 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BarLabel(row, Modifier.weight(1f, fill = false), startsEarlier = false, endsLater = false)
            if (!p.endsLater) DueText(dueText, overdue, Modifier.padding(start = 6.dp))
        }
        !labelInside -> BarLabel(
            row,
            Modifier.align(Alignment.CenterEnd).padding(end = total - x + 6.dp).widthIn(max = OutsideLabelWidth),
            startsEarlier = false,
            endsLater = false,
        )
        !p.endsLater && spaceAfter >= 80.dp -> DueText(dueText, overdue, Modifier.offset(x = x + width + 6.dp).align(Alignment.CenterStart))
    }
}

@Composable
private fun DueText(text: String, overdue: Boolean, modifier: Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        color = if (overdue) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        modifier = modifier,
    )
}

/** "◀ ▌ELECTENG 204 ● Lab 2 ▶": course, status dot and title. */
@Composable
private fun BarLabel(row: TimelineRow, modifier: Modifier, startsEarlier: Boolean, endsLater: Boolean) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        if (startsEarlier) Text("◀", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        CourseTag(row.task.item.courseCode, style = MaterialTheme.typography.labelSmall)
        Box(Modifier.size(7.dp).background(LocalStatusColors.current.of(row.task.status).content, CircleShape))
        Text(
            row.task.title,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        if (endsLater) Text("▶", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** A bar outside the visible days: an arrow at that edge; tapping it moves there. */
@Composable
private fun BoxScope.EdgeArrow(row: TimelineRow, state: TimelineState, toLeft: Boolean, onClick: () -> Unit) {
    val whenText = if (toLeft) {
        "due " + DateText.date(row.task.dueAt!!.atZone(state.zone).toLocalDate(), state.today.year)
    } else {
        "from " + DateText.date(row.jumpTo, state.today.year)
    }
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(6.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier
            .align(if (toLeft) Alignment.CenterStart else Alignment.CenterEnd)
            .widthIn(max = 360.dp)
            .testTag(if (toLeft) "timeline-arrow-left" else "timeline-arrow-right"),
    ) {
        Row(Modifier.padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            if (toLeft) Text("◀", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            CourseTag(row.task.item.courseCode, style = MaterialTheme.typography.labelSmall)
            Text(
                row.task.title,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            Text(
                whenText,
                style = MaterialTheme.typography.labelSmall,
                color = if (toLeft && TaskRules.isOverdue(row.task, state.now)) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
            if (!toLeft) Text("▶", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        }
    }
}

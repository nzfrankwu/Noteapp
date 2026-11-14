package com.nzfrankwu.noteapp.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.nzfrankwu.noteapp.R
import com.nzfrankwu.noteapp.domain.DateText
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset

/**
 * A labelled, optional date with an optional time: "[📅 Fri 20 Nov] [🕑 11:59 pm] [✕]",
 * or "[📅 Add date]" when empty. Without a picked time, [defaultTime] is shown (and used).
 * [tag] prefixes the test tags of its buttons (e.g. "due" → "due-date", "due-time", "due-clear").
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DateTimeField(
    label: String,
    hint: String,
    date: LocalDate?,
    time: LocalTime?,
    defaultTime: LocalTime,
    onDateChange: (LocalDate?) -> Unit,
    onTimeChange: (LocalTime?) -> Unit,
    tag: String,
    modifier: Modifier = Modifier,
    today: LocalDate = LocalDate.now(),
) {
    var pickingDate by rememberSaveable { mutableStateOf(false) }
    var pickingTime by rememberSaveable { mutableStateOf(false) }

    Column(modifier.fillMaxWidth()) {
        Text(label, style = MaterialTheme.typography.titleSmall)
        Text(hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            itemVerticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedButton(
                onClick = { pickingDate = true },
                contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
                modifier = Modifier.testTag("$tag-date"),
            ) {
                Icon(painterResource(R.drawable.ic_event), contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                Text(if (date == null) "Add date" else DateText.date(date, today.year))
            }
            if (date != null) {
                OutlinedButton(
                    onClick = { pickingTime = true },
                    contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
                    modifier = Modifier.testTag("$tag-time"),
                ) {
                    Icon(painterResource(R.drawable.ic_schedule), contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                    Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                    Text(DateText.time(time ?: defaultTime))
                }
                IconButton(onClick = { onDateChange(null) }, modifier = Modifier.testTag("$tag-clear")) {
                    Icon(painterResource(R.drawable.ic_close), contentDescription = "Clear $label")
                }
            }
        }
    }

    if (pickingDate) {
        DatePickerDialogFor(
            initial = date ?: today,
            onPicked = { onDateChange(it); pickingDate = false },
            onDismiss = { pickingDate = false },
        )
    }
    if (pickingTime) {
        TimePickerDialogFor(
            initial = time ?: defaultTime,
            onPicked = { onTimeChange(it); pickingTime = false },
            onDismiss = { pickingTime = false },
        )
    }
}

// Material's date picker works in UTC milliseconds at midnight of the chosen day.
private fun LocalDate.toPickerMillis(): Long = atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
private fun Long.toPickerDate(): LocalDate = Instant.ofEpochMilli(this).atZone(ZoneOffset.UTC).toLocalDate()

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DatePickerDialogFor(initial: LocalDate, onPicked: (LocalDate) -> Unit, onDismiss: () -> Unit) {
    val state = rememberDatePickerState(initialSelectedDateMillis = initial.toPickerMillis())
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = { state.selectedDateMillis?.let { onPicked(it.toPickerDate()) } ?: onDismiss() },
                modifier = Modifier.testTag("picker-ok"),
            ) { Text("OK") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    ) {
        DatePicker(state = state)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimePickerDialogFor(initial: LocalTime, onPicked: (LocalTime) -> Unit, onDismiss: () -> Unit) {
    val is24Hour = android.text.format.DateFormat.is24HourFormat(LocalContext.current)
    val state = rememberTimePickerState(initial.hour, initial.minute, is24Hour)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Pick a time") },
        text = { TimePicker(state = state) },
        confirmButton = {
            TextButton(onClick = { onPicked(LocalTime.of(state.hour, state.minute)) }, modifier = Modifier.testTag("picker-ok")) {
                Text("OK")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

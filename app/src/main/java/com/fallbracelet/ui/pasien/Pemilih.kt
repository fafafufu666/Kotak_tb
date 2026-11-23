package com.fallbracelet.ui.pasien

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.fallbracelet.R
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset

/** Date picker dialog; the Material picker works in UTC midnights. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PilihTanggalDialog(awal: LocalDate, onPilih: (LocalDate) -> Unit, onTutup: () -> Unit) {
    val state = rememberDatePickerState(initialSelectedDateMillis = awal.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
    DatePickerDialog(
        onDismissRequest = onTutup,
        confirmButton = {
            TextButton(onClick = {
                state.selectedDateMillis?.let { onPilih(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()) }
                onTutup()
            }) { Text(stringResource(R.string.pilih)) }
        },
        dismissButton = { TextButton(onClick = onTutup) { Text(stringResource(R.string.batal)) } },
    ) {
        DatePicker(state = state)
    }
}

/** 24-hour time picker dialog. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PilihJamDialog(awal: LocalTime, onPilih: (LocalTime) -> Unit, onTutup: () -> Unit) {
    val state = rememberTimePickerState(initialHour = awal.hour, initialMinute = awal.minute, is24Hour = true)
    AlertDialog(
        onDismissRequest = onTutup,
        confirmButton = {
            TextButton(onClick = { onPilih(LocalTime.of(state.hour, state.minute)); onTutup() }) {
                Text(stringResource(R.string.pilih))
            }
        },
        dismissButton = { TextButton(onClick = onTutup) { Text(stringResource(R.string.batal)) } },
        text = { TimePicker(state = state) },
    )
}

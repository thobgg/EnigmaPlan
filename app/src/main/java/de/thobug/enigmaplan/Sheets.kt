package de.thobug.enigmaplan

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.time.LocalTime

private val STATE = mapOf(0 to "Geplant", 1 to "Startet gleich", 2 to "Nimmt gerade auf", 3 to "Erledigt")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Sheet(onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    ModalBottomSheet(onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        // Auf dem Tablet nicht über die ganze Breite ziehen
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
            Column(
                Modifier.widthIn(max = 640.dp).fillMaxWidth().verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp).padding(bottom = 24.dp).navigationBarsPadding(),
                content = content,
            )
        }
    }
}

@Composable
private fun Meta(text: String) =
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)

@Composable
private fun ConfirmDialog(title: String, text: String, confirm: String, onConfirm: () -> Unit, onDismiss: () -> Unit) =
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = {
            TextButton({ onDismiss(); onConfirm() }) { Text(confirm, color = MaterialTheme.colorScheme.error) }
        },
        dismissButton = { TextButton(onDismiss) { Text("Abbrechen") } },
    )

/* ------------------------------------------------------------ Sendung */

@Composable
fun EventSheet(vm: MainVm, e: Event, onDismiss: () -> Unit, onEditTimer: (Timer) -> Unit) {
    val timer = vm.timerFor(e)
    Sheet(onDismiss) {
        ChannelLogo(vm, e.sref, e.sname, 80.dp)
        Text(e.title, Modifier.padding(top = 8.dp), style = MaterialTheme.typography.headlineSmall)
        Meta("${e.sname} · ${dayLabel(e.begin)} · ${hm(e.begin)} – ${hm(e.end)} (${minutes(e.duration)})")
        if (timer != null) Row(Modifier.padding(top = 8.dp)) { Chip("● Timer gesetzt", RecRed, androidx.compose.ui.graphics.Color.White) }
        if (e.short.isNotEmpty() && e.short != e.title)
            Text(e.short, Modifier.padding(top = 12.dp), fontWeight = FontWeight.SemiBold)
        if (e.long.isNotEmpty()) Text(e.long, Modifier.padding(top = 8.dp))
        Column(Modifier.padding(top = 20.dp)) {
            when {
                timer != null -> FilledTonalButton({ onEditTimer(timer) }, Modifier.fillMaxWidth()) { Text("Timer bearbeiten") }
                e.end > vm.now -> Button(
                    { vm.record(e, onDismiss) }, Modifier.fillMaxWidth(), enabled = !vm.loading,
                    colors = ButtonDefaults.buttonColors(containerColor = RecRed),
                ) {
                    Icon(Icons.Default.FiberManualRecord, null, Modifier.padding(end = 8.dp))
                    Text("Aufnehmen")
                }
            }
        }
    }
}

/* ------------------------------------------------------------ Timer */

@Composable
fun TimerSheet(vm: MainVm, t: Timer?, presetRef: String?, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val b0 = t?.begin ?: ((nowSec() / 900 + 1) * 900)
    val e0 = t?.end ?: (b0 + 3600)
    var name by remember { mutableStateOf(t?.name ?: "") }
    var date by remember { mutableStateOf(localDate(b0)) }
    var begin by remember { mutableStateOf(localTime(b0).withSecond(0)) }
    var end by remember { mutableStateOf(localTime(e0).withSecond(0)) }
    var justplay by remember { mutableStateOf(t?.justplay ?: false) }
    var sref by remember { mutableStateOf(t?.sref ?: presetRef ?: vm.services.firstOrNull()?.ref ?: "") }
    var askDelete by remember { mutableStateOf(false) }
    val running = t?.state == 2

    fun pickTime(cur: LocalTime, set: (LocalTime) -> Unit) =
        TimePickerDialog(ctx, { _, h, m -> set(LocalTime.of(h, m)) }, cur.hour, cur.minute, true).show()

    Sheet(onDismiss) {
        Text(if (t == null) "Neuer Timer" else "Timer bearbeiten", style = MaterialTheme.typography.headlineSmall)
        if (t != null) {
            Meta("${t.sname} · ${STATE[t.state] ?: ""}")
            TimerChips(t)
        }

        OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth().padding(top = 12.dp),
            label = { Text("Titel") }, singleLine = true)

        if (t == null) {
            var open by remember { mutableStateOf(false) }
            Box(Modifier.padding(top = 12.dp)) {
                OutlinedButton({ open = true }, Modifier.fillMaxWidth()) {
                    Text(vm.services.firstOrNull { normRef(it.ref) == normRef(sref) }?.name ?: "Sender wählen",
                        Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Icon(Icons.Default.ArrowDropDown, null)
                }
                DropdownMenu(open, { open = false }) {
                    vm.services.forEach { s -> DropdownMenuItem({ Text(s.name) }, onClick = { sref = s.ref; open = false }) }
                }
            }
        }

        Text("Datum", Modifier.padding(top = 12.dp), style = MaterialTheme.typography.labelLarge)
        OutlinedButton(
            { DatePickerDialog(ctx, { _, y, m, d -> date = java.time.LocalDate.of(y, m + 1, d) }, date.year, date.monthValue - 1, date.dayOfMonth).show() },
            Modifier.fillMaxWidth(), enabled = !running,
        ) { Text("${dayLabel(date)}  (${date.dayOfMonth}.${date.monthValue}.${date.year})") }

        Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(Modifier.weight(1f)) {
                Text("Beginn", style = MaterialTheme.typography.labelLarge)
                OutlinedButton({ pickTime(begin) { begin = it } }, Modifier.fillMaxWidth(), enabled = !running) { Text(hm(begin)) }
            }
            Column(Modifier.weight(1f)) {
                Text("Ende", style = MaterialTheme.typography.labelLarge)
                OutlinedButton({ pickTime(end) { end = it } }, Modifier.fillMaxWidth()) { Text(hm(end)) }
            }
        }

        Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(!justplay, { justplay = false }, { Text("Aufnehmen") })
            FilterChip(justplay, { justplay = true }, { Text("Nur umschalten") })
        }

        Button(
            {
                val b = epoch(date, begin)
                var en = epoch(date, end)
                if (en <= b) en += 86400 // über Mitternacht
                val n = name.trim().ifEmpty { "Aufnahme" }
                if (t == null) vm.addTimer(sref, n, b, en, justplay, onDismiss)
                else vm.changeTimer(t, n, b, en, justplay, onDismiss)
            },
            Modifier.fillMaxWidth().padding(top = 20.dp), enabled = !vm.loading && sref.isNotEmpty(),
        ) { Text(if (t == null) "Timer anlegen" else "Speichern") }

        if (t != null) Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton({ vm.toggleTimer(t, onDismiss) }, Modifier.weight(1f), enabled = !vm.loading) {
                Text(if (t.disabled) "Aktivieren" else "Deaktivieren")
            }
            Button(
                { askDelete = true }, Modifier.weight(1f), enabled = !vm.loading,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
            ) { Text("Löschen") }
        }
    }
    if (askDelete && t != null) ConfirmDialog(
        "Timer löschen?",
        if (running) "„${t.name}“ wird gerade aufgenommen. Die Aufnahme wird gestoppt, das bisher Aufgenommene bleibt erhalten."
        else "„${t.name}“ am ${dayLabel(t.begin)} um ${hm(t.begin)} wird gelöscht.",
        "Löschen", { vm.deleteTimer(t, onDismiss) }, { askDelete = false },
    )
}

/* ------------------------------------------------------------ Aufnahme */

@Composable
fun MovieSheet(vm: MainVm, m: Movie, onDismiss: () -> Unit) {
    var askDelete by remember { mutableStateOf(false) }
    Sheet(onDismiss) {
        Text(m.title, style = MaterialTheme.typography.headlineSmall)
        Meta(listOfNotNull(m.sname, if (m.time > 0) "${dayLabel(m.time)}, ${hm(m.time)}" else null, m.size).joinToString(" · "))
        if (m.description.isNotEmpty() && m.description != m.title)
            Text(m.description, Modifier.padding(top = 12.dp), fontWeight = FontWeight.SemiBold)
        if (m.descriptionExt.isNotEmpty()) Text(m.descriptionExt, Modifier.padding(top = 8.dp))
        Button(
            { askDelete = true }, Modifier.fillMaxWidth().padding(top = 20.dp), enabled = !vm.loading,
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
        ) { Text("Aufnahme löschen") }
    }
    if (askDelete) ConfirmDialog(
        "Aufnahme löschen?", "„${m.title}“ (${m.size}) wird endgültig von der Festplatte der Box gelöscht.",
        "Endgültig löschen", { vm.deleteMovie(m, onDismiss) }, { askDelete = false },
    )
}

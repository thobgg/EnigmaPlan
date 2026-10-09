@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package de.thobug.enigmaplan

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

val RecRed = Color(0xFFE53935)

/* ------------------------------------------------------------ Bausteine */

@Composable
fun RecDot() = Box(Modifier.padding(end = 6.dp).size(9.dp).background(RecRed, CircleShape))

@Composable
fun Chip(text: String, color: Color = MaterialTheme.colorScheme.surfaceVariant, textColor: Color = MaterialTheme.colorScheme.onSurfaceVariant) {
    Surface(color = color, shape = RoundedCornerShape(6.dp)) {
        Text(text, Modifier.padding(horizontal = 7.dp, vertical = 1.dp), style = MaterialTheme.typography.labelMedium, color = textColor)
    }
}

@Composable
fun DayHeader(text: String) = Text(
    text.uppercase(), Modifier.padding(start = 16.dp, top = 14.dp, bottom = 4.dp),
    style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary,
)

@Composable
fun Notice(text: String) = Surface(
    color = MaterialTheme.colorScheme.tertiaryContainer, shape = RoundedCornerShape(12.dp),
    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
) { Text(text, Modifier.padding(12.dp), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onTertiaryContainer) }

@Composable
fun Hint(text: String) = Text(text, Modifier.padding(24.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)

@Composable
fun RowCard(onClick: () -> Unit, selected: Boolean = false, content: @Composable () -> Unit) {
    Card(
        onClick = onClick,
        colors = if (selected) CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
        else CardDefaults.cardColors(),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
    ) { Box(Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) { content() } }
}

/** Senderlogo auf dunkler Kachel (Picons sind für dunklen Grund gemacht), sonst Kürzel. */
@Composable
fun ChannelLogo(vm: MainVm, ref: String, name: String, width: Dp = 64.dp) {
    LaunchedEffect(ref) { vm.loadPicon(ref) }
    val img = vm.picons[normRef(ref)]
    Box(
        Modifier.size(width, width * 0.6f).background(Color(0xFF263238), RoundedCornerShape(8.dp)),
        contentAlignment = Alignment.Center,
    ) {
        if (img != null) Image(img, name, Modifier.fillMaxSize().padding(3.dp), contentScale = ContentScale.Fit)
        else Text(name.replace(" HD", "").take(4), color = Color.White, style = MaterialTheme.typography.labelMedium, maxLines = 1)
    }
}

/** Kompakte Zeile für eine Sendung: Uhrzeit, Titel, Aufnahme-Knopf. */
@Composable
fun EventRow(vm: MainVm, e: Event, showChannel: Boolean, onClick: () -> Unit, onRec: (Event) -> Unit) {
    val running = e.begin <= vm.now && e.end > vm.now
    val past = e.end <= vm.now
    val timer = vm.timerFor(e)
    Surface(
        onClick = onClick,
        color = if (running) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 8.dp).alpha(if (past) 0.5f else 1f),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(hm(e.begin), Modifier.width(50.dp).align(Alignment.Top), fontWeight = FontWeight.Bold,
                color = if (running) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
            Column(Modifier.weight(1f)) {
                if (showChannel) Row(Modifier.padding(bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    ChannelLogo(vm, e.sref, e.sname, 40.dp)
                    Text("  " + e.sname, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(e.title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold,
                    maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (e.short.isNotEmpty() && e.short != e.title)
                    Text(e.short, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (running) LinearProgressIndicator(
                    { ((vm.now - e.begin).toFloat() / e.duration.coerceAtLeast(1)).coerceIn(0f, 1f) },
                    Modifier.fillMaxWidth().padding(top = 6.dp), drawStopIndicator = {},
                )
            }
            if (past) Spacer(Modifier.width(48.dp))
            else IconButton({ onRec(e) }, enabled = !vm.loading) {
                if (timer != null) Icon(Icons.Filled.FiberManualRecord, "Timer bearbeiten", tint = RecRed)
                else Icon(Icons.Outlined.Circle, "Aufnehmen", tint = RecRed.copy(alpha = 0.55f))
            }
        }
    }
    HorizontalDivider(Modifier.padding(start = 66.dp), thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
}

/** Sendungen nach Tag gruppiert in ein Grid einfügen. */
fun LazyGridScope.eventsByDay(vm: MainVm, events: List<Event>, showChannel: Boolean, onEvent: (Event) -> Unit, onRec: (Event) -> Unit) {
    events.groupBy { localDate(it.begin) }.forEach { (day, list) ->
        item(span = { GridItemSpan(maxLineSpan) }, key = "d$day") { DayHeader(dayLabel(day)) }
        list.forEach { e -> item(key = "${e.sref}/${e.id}/${e.begin}") { EventRow(vm, e, showChannel, { onEvent(e) }, onRec) } }
    }
}

private val GRID = GridCells.Adaptive(340.dp)
private val BOTTOM = PaddingValues(bottom = 88.dp)

/* ------------------------------------------------------------ Programm */

@Composable
fun ProgramScreen(vm: MainVm, wide: Boolean, onEvent: (Event) -> Unit, onRec: (Event) -> Unit, onManual: (String) -> Unit) {
    if (wide) {
        LaunchedEffect(vm.services) { if (vm.selected == null) vm.services.firstOrNull()?.let(vm::select) }
        Row(Modifier.fillMaxSize()) {
            ChannelList(vm, Modifier.weight(0.4f))
            VerticalDivider()
            Box(Modifier.weight(0.6f)) { ChannelPager(vm, onEvent, onRec, onManual) }
        }
    } else if (vm.selected != null) {
        ChannelPager(vm, onEvent, onRec, onManual)
    } else {
        ChannelList(vm, Modifier.fillMaxSize())
    }
}

@Composable
private fun ChannelList(vm: MainVm, modifier: Modifier) {
    val missing = vm.services.count { s -> vm.nowNext[normRef(s.ref)]?.firstOrNull() == null }
    LazyColumn(modifier, contentPadding = PaddingValues(vertical = 6.dp)) {
        if (vm.services.isNotEmpty() && missing > vm.services.size / 2) item {
            Notice("Für $missing von ${vm.services.size} Sendern hat die Box gerade keine EPG-Daten. " +
                "Sie sammelt das Programm nur von Sendern, die zuletzt eingeschaltet waren.")
        }
        itemsIndexed(vm.services, key = { _, s -> s.ref }) { i, s ->
            val ev = vm.nowNext[normRef(s.ref)].orEmpty()
            val cur = ev.firstOrNull { it.end > vm.now }
            val next = ev.firstOrNull { it.begin > (cur?.begin ?: 0) }
            RowCard({ vm.select(s) }, selected = vm.selected?.ref == s.ref) {
                Row {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    ChannelLogo(vm, s.ref, s.name)
                    Text("${i + 1}", Modifier.padding(top = 4.dp), style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(s.name, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (cur == null) {
                        Text("Keine EPG-Daten", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.outline)
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (vm.timerFor(cur) != null) RecDot()
                            Text(cur.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        Text("${hm(cur.begin)} – ${hm(cur.end)} · noch ${minutes((cur.end - vm.now).coerceAtLeast(0))}",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        LinearProgressIndicator(
                            { ((vm.now - cur.begin).toFloat() / cur.duration.coerceAtLeast(1)).coerceIn(0f, 1f) },
                            Modifier.fillMaxWidth().padding(vertical = 6.dp), drawStopIndicator = {},
                        )
                        if (next != null) Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(hm(next.begin) + "  ", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                            if (vm.timerFor(next) != null) RecDot()
                            Text(next.title, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
                }
            }
        }
    }
}

/** Senderprogramme nebeneinander – Wischen wechselt den Sender. */
@Composable
private fun ChannelPager(vm: MainVm, onEvent: (Event) -> Unit, onRec: (Event) -> Unit, onManual: (String) -> Unit) {
    val services = vm.services
    if (services.isEmpty()) return
    val start = services.indexOfFirst { it.ref == vm.selected?.ref }.coerceAtLeast(0)
    val pager = rememberPagerState(initialPage = start) { services.size }
    // Auswahl in der Senderliste -> Pager
    LaunchedEffect(vm.selected) {
        val i = services.indexOfFirst { it.ref == vm.selected?.ref }
        if (i >= 0 && i != pager.currentPage) pager.scrollToPage(i)
    }
    // Wischen -> Auswahl
    LaunchedEffect(pager, services) {
        snapshotFlow { pager.settledPage }.collect { p ->
            services.getOrNull(p)?.let { if (it.ref != vm.selected?.ref) vm.select(it) }
        }
    }
    HorizontalPager(pager, Modifier.fillMaxSize(), beyondViewportPageCount = 1, key = { services[it].ref }) { page ->
        val s = services[page]
        LaunchedEffect(s.ref) { vm.loadEpg(s.ref) }
        ChannelEpgPage(vm, s, onEvent, onRec, onManual)
    }
}

@Composable
private fun ChannelEpgPage(vm: MainVm, s: Service, onEvent: (Event) -> Unit, onRec: (Event) -> Unit, onManual: (String) -> Unit) {
    val all = vm.epg[s.ref] ?: return Box(Modifier.fillMaxSize()) { CircularProgressIndicator(Modifier.align(Alignment.Center)) }
    val events = remember(all) { all.filter { it.end > nowSec() - 3600 } }
    if (events.isEmpty()) {
        Column {
            Notice("Die Box hat für ${s.name} keine EPG-Daten. Wenn der Sender auf der Box kurz läuft, lädt sie das Programm.")
            OutlinedButton({ onManual(s.ref) }, Modifier.padding(12.dp)) { Text("Timer manuell anlegen") }
        }
        return
    }
    // Liste aus Tagesüberschriften (LocalDate) und Sendungen (Event)
    val rows = remember(events) {
        buildList<Any> {
            var last: LocalDate? = null
            events.forEach { e -> localDate(e.begin).let { d -> if (d != last) { add(d); last = d } }; add(e) }
        }
    }
    val days = remember(rows) { rows.mapIndexedNotNull { i, r -> (r as? LocalDate)?.let { it to i } } }
    val list = rememberLazyListState()
    val scope = rememberCoroutineScope()
    fun nowIndex() = rows.indexOfFirst { it is Event && it.end > nowSec() }.coerceAtLeast(0)
    LaunchedEffect(s.ref) { list.scrollToItem((nowIndex() - 1).coerceAtLeast(0)) }
    val curDay by remember(days) {
        derivedStateOf { days.lastOrNull { it.second <= list.firstVisibleItemIndex + 1 }?.first ?: days.first().first }
    }

    Column(Modifier.fillMaxSize()) {
        DayBar(
            days.map { it.first }, curDay,
            onNow = { scope.launch { list.scrollToItem((nowIndex() - 1).coerceAtLeast(0)) } },
            onPrime = {
                val t = epoch(curDay, LocalTime.of(20, 15))
                val i = rows.indexOfFirst { it is Event && it.end > t + 60 }
                if (i >= 0) scope.launch { list.scrollToItem(i) }
            },
            onDay = { d -> days.firstOrNull { it.first == d }?.let { scope.launch { list.scrollToItem(it.second) } } },
        )
        HorizontalDivider()
        LazyColumn(state = list, contentPadding = BOTTOM, modifier = Modifier.weight(1f)) {
            itemsIndexed(rows, key = { _, r -> if (r is Event) "${r.id}/${r.begin}" else r.toString() }) { _, r ->
                if (r is Event) EventRow(vm, r, false, { onEvent(r) }, onRec)
                else DayHeader(dayLabel(r as LocalDate))
            }
        }
    }
}

/** Schnellsprung: Jetzt, 20:15 und die einzelnen Tage. */
@Composable
private fun DayBar(days: List<LocalDate>, cur: LocalDate, onNow: () -> Unit, onPrime: () -> Unit, onDay: (LocalDate) -> Unit) {
    val state = rememberLazyListState()
    LaunchedEffect(cur) { days.indexOf(cur).takeIf { it >= 0 }?.let { state.animateScrollToItem(it) } }
    LazyRow(
        state = state,
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        item { AssistChip(onNow, { Text("Jetzt") }) }
        item { AssistChip(onPrime, { Text("20:15") }) }
        items(days.size) { i ->
            val d = days[i]
            FilterChip(d == cur, { onDay(d) }, { Text(dayLabel(d)) })
        }
    }
}

/* ------------------------------------------------------------ Suche */

@Composable
fun SearchScreen(vm: MainVm, onEvent: (Event) -> Unit, onRec: (Event) -> Unit) {
    var q by rememberSaveable { mutableStateOf(vm.searchQuery) }
    val focus = LocalFocusManager.current
    Column(Modifier.fillMaxSize()) {
        OutlinedTextField(
            q, { q = it },
            Modifier.fillMaxWidth().padding(12.dp),
            placeholder = { Text("Sendung suchen …") },
            leadingIcon = { Icon(Icons.Default.Search, null) },
            trailingIcon = { if (q.isNotEmpty()) IconButton({ q = "" }) { Icon(Icons.Default.Clear, "Leeren") } },
            singleLine = true,
            shape = RoundedCornerShape(28.dp),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { focus.clearFocus(); vm.search(q) }),
        )
        val res = vm.searchResults
        when {
            vm.searchQuery.isBlank() -> Hint("Titel oder Stichwort eingeben, z. B. „Tatort“. Gesucht wird im EPG der Box.")
            res == null -> {}
            res.isEmpty() -> Hint("Nichts gefunden für „${vm.searchQuery}“.")
            else -> LazyVerticalGrid(GRID, contentPadding = BOTTOM) { eventsByDay(vm, res, true, onEvent, onRec) }
        }
    }
}

/* ------------------------------------------------------------ Timer */

@Composable
fun TimerScreen(vm: MainVm, onTimer: (Timer) -> Unit) {
    if (vm.timers.isEmpty()) {
        Hint("Keine Timer. Tippe im Programm auf eine Sendung und dann auf „Aufnehmen“ – oder lege mit + einen Timer an.")
        return
    }
    val done = vm.timers.count { it.state == 3 }
    LazyVerticalGrid(GRID, contentPadding = BOTTOM) {
        vm.timers.groupBy { localDate(it.begin) }.forEach { (day, list) ->
            item(span = { GridItemSpan(maxLineSpan) }, key = "d$day") { DayHeader(dayLabel(day)) }
            list.forEach { t -> item(key = "${t.sref}/${t.begin}/${t.end}") { TimerCard(vm, t) { onTimer(t) } } }
        }
        if (done > 0) item(span = { GridItemSpan(maxLineSpan) }) {
            OutlinedButton(vm::cleanupTimers, Modifier.padding(12.dp)) { Text("Erledigte Timer entfernen ($done)") }
        }
    }
}

@Composable
private fun TimerCard(vm: MainVm, t: Timer, onClick: () -> Unit) {
    RowCard(onClick) {
        Row(Modifier.alpha(if (t.state == 3) 0.55f else 1f)) {
            ChannelLogo(vm, t.sref, t.sname, 56.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(t.sname, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(t.name, style = MaterialTheme.typography.titleMedium)
                Text("${hm(t.begin)} – ${hm(t.end)} · ${minutes(t.end - t.begin)}",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                TimerChips(t)
            }
        }
    }
}

@Composable
fun TimerChips(t: Timer) {
    val chips = buildList<@Composable () -> Unit> {
        if (t.state == 2) add { Chip("● Nimmt auf", RecRed, Color.White) }
        if (t.state == 3) add { Chip("Erledigt") }
        if (t.disabled) add { Chip("Deaktiviert", MaterialTheme.colorScheme.errorContainer, MaterialTheme.colorScheme.onErrorContainer) }
        if (t.repeated != 0) add { Chip("Serie") }
        if (t.justplay) add { Chip("Nur umschalten") }
    }
    if (chips.isNotEmpty()) FlowRow(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        chips.forEach { it() }
    }
}

/* ------------------------------------------------------------ Aufnahmen */

@Composable
fun RecordingsScreen(vm: MainVm, onMovie: (Movie) -> Unit) {
    val movies = vm.movies ?: return
    LazyVerticalGrid(GRID, contentPadding = BOTTOM) {
        vm.disk?.let { d ->
            item(span = { GridItemSpan(maxLineSpan) }) {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    LinearProgressIndicator({ d.usedFraction }, Modifier.fillMaxWidth().height(6.dp), drawStopIndicator = {})
                    Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Festplatte", style = MaterialTheme.typography.bodySmall)
                        Text("${d.free} frei von ${d.capacity}", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
        if (movies.isEmpty()) item(span = { GridItemSpan(maxLineSpan) }) { Hint("Keine Aufnahmen vorhanden.") }
        movies.forEach { m ->
            item(key = m.ref) {
                RowCard({ onMovie(m) }) {
                    Column {
                        Text(m.sname, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(m.title, style = MaterialTheme.typography.titleMedium)
                        if (m.description.isNotEmpty() && m.description != m.title)
                            Text(m.description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Spacer(Modifier.height(6.dp))
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            if (m.time > 0) Chip("${dayLabel(m.time)}, ${hm(m.time)}")
                            if (m.length.isNotEmpty() && m.length != "?:??") Chip("${m.length} Min.")
                            Chip(m.size)
                        }
                    }
                }
            }
        }
    }
}

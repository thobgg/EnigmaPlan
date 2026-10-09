package de.thobug.enigmaplan

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime

private val DP_PER_MIN = 4.dp
private val ROW_H = 64.dp
private val LOGO_W = 96.dp
private const val MAX_DAYS = 6 // begrenzt die Rasterbreite (Compose-Constraints)

/** TV-Zeitung: Sender untereinander, Zeit nach rechts, Sendungen als Kästchen. */
@Composable
fun GuideScreen(vm: MainVm, onEvent: (Event) -> Unit, onChannel: (Service) -> Unit) {
    LaunchedEffect(vm.guide == null, vm.bref) { if (vm.guide == null) vm.loadGuide() }
    val events = vm.guide ?: return Box(Modifier.fillMaxSize()) {
        Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator()
            Text("Lade das Wochenprogramm aller Sender …", Modifier.padding(top = 12.dp))
        }
    }
    val start = remember(events) { epoch(LocalDate.now(zone), LocalTime.MIDNIGHT) }
    val end = remember(events) { minOf(events.maxOfOrNull { it.end } ?: (start + 86400), start + MAX_DAYS * 86400L) }
    val byRef = remember(events) { events.groupBy { normRef(it.sref) } }
    val days = remember(start, end) { generateSequence(localDate(start)) { it.plusDays(1) }.takeWhile { epoch(it, LocalTime.MIDNIGHT) < end }.toList() }

    val density = LocalDensity.current
    val pxPerSec = with(density) { DP_PER_MIN.toPx() } / 60f
    val totalW = DP_PER_MIN * ((end - start) / 60f)
    fun xPx(ts: Long) = ((ts - start) * pxPerSec).toInt()
    fun xDp(ts: Long) = DP_PER_MIN * ((ts - start) / 60f)

    val scroll = rememberScrollState()
    val scope = rememberCoroutineScope()
    fun jump(ts: Long) = scope.launch { scroll.animateScrollTo(xPx(ts).coerceAtLeast(0)) }
    LaunchedEffect(start) { scroll.scrollTo(xPx(nowSec() - 1800).coerceAtLeast(0)) }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val viewSec = with(density) { (maxWidth - LOGO_W).toPx() } / pxPerSec
        // Nur Sendungen im sichtbaren Fenster (plus Rand) zeichnen
        val visFrom by remember { derivedStateOf { start + (scroll.value / pxPerSec).toLong() - 3 * 3600 } }
        val visTo = visFrom + viewSec.toLong() + 6 * 3600
        val centerDay by remember { derivedStateOf { localDate(start + ((scroll.value / pxPerSec) + viewSec / 2).toLong()) } }

        Column(Modifier.fillMaxSize()) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                item { AssistChip({ jump(nowSec() - 1800) }, { Text("Jetzt") }) }
                item { AssistChip({ jump(epoch(centerDay, LocalTime.of(20, 0))) }, { Text("20:15") }) }
                items(days) { d -> FilterChip(d == centerDay, { jump(epoch(d, LocalTime.of(6, 0))) }, { Text(dayLabel(d)) }) }
            }
            HorizontalDivider()
            // Zeitleiste
            Row(Modifier.height(28.dp)) {
                Box(Modifier.width(LOGO_W))
                Box(Modifier.weight(1f).horizontalScroll(scroll)) {
                    Box(Modifier.width(totalW).fillMaxHeight()) {
                        var t = (visFrom / 1800 + 1) * 1800
                        while (t < visTo && t < end) {
                            val tick = t // eigene Kopie: der offset-Lambda läuft erst beim Layout
                            val midnight = localTime(tick) == LocalTime.MIDNIGHT
                            Text(
                                if (midnight) dayLabel(tick) else hm(tick),
                                Modifier.offset { IntOffset(xPx(tick) + 4, 0) }.align(Alignment.CenterStart),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = if (midnight || localTime(tick).minute == 0) FontWeight.Bold else FontWeight.Normal,
                                color = if (midnight) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            t += 1800
                        }
                        NowLine(xDp(vm.now))
                    }
                }
            }
            HorizontalDivider()
            LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(bottom = 88.dp)) {
                items(vm.services, key = { it.ref }) { s ->
                    Row(Modifier.height(ROW_H)) {
                        Box(
                            Modifier.width(LOGO_W).fillMaxHeight().clickable { onChannel(s) },
                            contentAlignment = Alignment.Center,
                        ) { ChannelLogo(vm, s.ref, s.name, 76.dp) }
                        Box(Modifier.weight(1f).fillMaxHeight().horizontalScroll(scroll)) {
                            Box(Modifier.width(totalW).fillMaxHeight()) {
                                byRef[normRef(s.ref)].orEmpty()
                                    .filter { it.end > visFrom && it.begin < visTo && it.end > start }
                                    .forEach { e -> GuideCell(vm, e, xDp(maxOf(e.begin, start)), DP_PER_MIN * ((e.end - maxOf(e.begin, start)) / 60f)) { onEvent(e) } }
                                NowLine(xDp(vm.now))
                            }
                        }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
        }
    }
}

@Composable
private fun NowLine(x: androidx.compose.ui.unit.Dp) =
    Box(Modifier.offset(x = x).width(2.dp).fillMaxHeight().background(RecRed))

@Composable
private fun GuideCell(vm: MainVm, e: Event, x: androidx.compose.ui.unit.Dp, w: androidx.compose.ui.unit.Dp, onClick: () -> Unit) {
    val running = e.begin <= vm.now && e.end > vm.now
    val past = e.end <= vm.now
    val timer = vm.timerFor(e) != null
    val shape = RoundedCornerShape(6.dp)
    Box(
        Modifier.offset(x = x).width(w).fillMaxHeight().padding(horizontal = 1.dp, vertical = 3.dp)
            .alpha(if (past) 0.45f else 1f)
            .clip(shape)
            .background(if (running) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh)
            .then(if (timer) Modifier.border(2.dp, RecRed, shape) else Modifier)
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 4.dp),
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (timer) RecDot()
                Text(e.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Text("${hm(e.begin)} ${e.short.takeIf { it.isNotEmpty() && it != e.title } ?: ""}",
                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

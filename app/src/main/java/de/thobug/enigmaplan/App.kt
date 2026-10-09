package de.thobug.enigmaplan

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.CalendarViewWeek
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material.icons.outlined.LiveTv
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.foundation.clickable
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.compose.LifecycleResumeEffect

enum class Tab(val label: String, val icon: ImageVector) {
    Programm("Programm", Icons.Outlined.LiveTv),
    Suche("Suche", Icons.Outlined.Search),
    Timer("Timer", Icons.Outlined.Alarm),
    Aufnahmen("Aufnahmen", Icons.Outlined.VideoLibrary),
}

/** Was gerade als Bottom-Sheet offen ist. */
sealed interface SheetState {
    data class OfEvent(val e: Event) : SheetState
    data class EditTimer(val t: Timer) : SheetState
    data class NewTimer(val sref: String?, val event: Event? = null) : SheetState
    data class OfMovie(val m: Movie) : SheetState
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun App(vm: MainVm) {
    var tab by rememberSaveable { mutableStateOf(Tab.Programm) }
    var sheet by remember { mutableStateOf<SheetState?>(null) }
    var settings by remember { mutableStateOf(false) }
    var about by remember { mutableStateOf(false) }
    var guideMode by rememberSaveable { mutableStateOf(true) } // TV-Zeitung statt Liste (nur breit)
    val snack = remember { SnackbarHostState() }
    // Aufnahme-Knopf in der Zeile: einplanen, oder vorhandenen Timer öffnen
    val quickRec: (Event) -> Unit = { e ->
        val t = vm.timerFor(e)
        if (t != null) sheet = SheetState.EditTimer(t) else vm.record(e) {}
    }

    LaunchedEffect(Unit) { for (m in vm.messages) snack.showSnackbar(m) }
    LifecycleResumeEffect(Unit) {
        if (vm.bouquets.isNotEmpty()) vm.refreshAll()
        onPauseOrDispose { }
    }
    LaunchedEffect(tab) {
        when (tab) {
            Tab.Timer -> vm.refreshTimers()
            Tab.Aufnahmen -> vm.refreshMovies()
            else -> {}
        }
    }

    var pulled by remember { mutableStateOf(false) }
    LaunchedEffect(vm.loading) { if (!vm.loading) pulled = false }
    val refresh: () -> Unit = {
        when (tab) {
            Tab.Aufnahmen -> vm.refreshMovies()
            Tab.Suche -> if (vm.searchQuery.isNotBlank()) vm.search(vm.searchQuery) else vm.refreshAll()
            else -> vm.refreshAll()
        }
    }

    androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = maxWidth >= 600.dp
        val phoneChannel = !wide && tab == Tab.Programm && vm.selected != null
        if (phoneChannel) BackHandler { vm.select(null) }
        val activeTimers = vm.timers.count { it.state != 3 && !it.disabled }

        Scaffold(
            contentWindowInsets = WindowInsets(0),
            snackbarHost = { SnackbarHost(snack) },
            topBar = {
                TopAppBar(
                    title = {
                        if (tab == Tab.Programm && wide && guideMode) Text("TV-Zeitung")
                        else if (phoneChannel) Row(verticalAlignment = Alignment.CenterVertically) {
                            ChannelLogo(vm, vm.selected!!.ref, vm.selected!!.name, 52.dp)
                            Spacer(Modifier.width(12.dp))
                            Text(vm.selected!!.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        } else Text(
                            if (phoneChannel) vm.selected!!.name else if (tab == Tab.Programm) "Jetzt im TV" else tab.label,
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                    },
                    navigationIcon = {
                        if (phoneChannel) IconButton({ vm.select(null) }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Zurück")
                        }
                    },
                    actions = {
                        if (tab == Tab.Programm && vm.bouquets.size > 1 && !phoneChannel) BouquetPicker(vm)
                        if (tab == Tab.Programm && wide) IconButton({ guideMode = !guideMode }) {
                            if (guideMode) Icon(Icons.AutoMirrored.Filled.ViewList, "Listenansicht")
                            else Icon(Icons.Default.CalendarViewWeek, "TV-Zeitung")
                        }
                        IconButton(refresh) { Icon(Icons.Default.Refresh, "Aktualisieren") }
                        OverflowMenu(onSettings = { settings = true }, onAbout = { about = true })
                    },
                )
            },
            bottomBar = {
                if (!wide) NavigationBar {
                    Tab.entries.forEach { t ->
                        NavigationBarItem(
                            selected = tab == t, onClick = { tab = t },
                            icon = { TabIcon(t, activeTimers) }, label = { Text(t.label) },
                        )
                    }
                }
            },
            floatingActionButton = {
                if (tab == Tab.Timer) FloatingActionButton({ sheet = SheetState.NewTimer(null) }) {
                    Icon(Icons.Default.Add, "Neuer Timer")
                }
            },
        ) { pad ->
            // Quer liegt die Android-Navigationsleiste seitlich: Inhalt nicht darunter zeichnen
            Row(Modifier.padding(pad).fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing.only(
                // ohne untere Navigationsleiste (breit) auch unten Platz für die Systemleiste lassen
                if (wide) WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom else WindowInsetsSides.Horizontal))) {
                if (wide) {
                    NavigationRail {
                        Tab.entries.forEach { t ->
                            NavigationRailItem(
                                selected = tab == t, onClick = { tab = t },
                                icon = { TabIcon(t, activeTimers) }, label = { Text(t.label) },
                            )
                        }
                    }
                    VerticalDivider()
                }
                Column(Modifier.weight(1f).fillMaxSize()) {
                    if (vm.loading && !pulled) LinearProgressIndicator(Modifier.fillMaxWidth())
                    PullToRefreshBox(
                        isRefreshing = pulled && vm.loading,
                        onRefresh = { pulled = true; refresh() },
                        modifier = Modifier.weight(1f),
                    ) {
                        val off = vm.offline
                        if (off != null && vm.bouquets.isEmpty()) {
                            Offline(off, firstStart = vm.host.isBlank(), onRetry = vm::refreshAll, onSettings = { settings = true })
                        } else when (tab) {
                            Tab.Programm -> if (wide && guideMode) GuideScreen(vm,
                                onEvent = { sheet = SheetState.OfEvent(it) },
                                onChannel = { guideMode = false; vm.select(it) })
                            else ProgramScreen(vm, wide,
                                onEvent = { sheet = SheetState.OfEvent(it) }, onRec = quickRec,
                                onManual = { sheet = SheetState.NewTimer(it) })
                            Tab.Suche -> SearchScreen(vm, { sheet = SheetState.OfEvent(it) }, quickRec)
                            Tab.Timer -> TimerScreen(vm) { sheet = SheetState.EditTimer(it) }
                            Tab.Aufnahmen -> RecordingsScreen(vm) { sheet = SheetState.OfMovie(it) }
                        }
                    }
                }
            }
        }
    }

    val close = { sheet = null }
    when (val s = sheet) {
        is SheetState.OfEvent -> EventSheet(vm, s.e, close,
            onEditTimer = { sheet = SheetState.EditTimer(it) },
            onSeries = { sheet = SheetState.NewTimer(it.sref, it) })
        is SheetState.EditTimer -> TimerSheet(vm, s.t, null, null, close)
        is SheetState.NewTimer -> TimerSheet(vm, null, s.sref, s.event, close)
        is SheetState.OfMovie -> MovieSheet(vm, s.m, close)
        null -> {}
    }
    if (settings) SettingsDialog(vm) { settings = false }
    if (about) AboutDialog(vm) { about = false }
}

@Composable
private fun OverflowMenu(onSettings: () -> Unit, onAbout: () -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton({ open = true }) { Icon(Icons.Default.MoreVert, "Menü") }
        DropdownMenu(open, { open = false }) {
            DropdownMenuItem({ Text("Einstellungen") }, onClick = { open = false; onSettings() },
                leadingIcon = { Icon(Icons.Default.Settings, null) })
            DropdownMenuItem({ Text("Über EnigmaPlan") }, onClick = { open = false; onAbout() },
                leadingIcon = { Icon(Icons.Outlined.Info, null) })
        }
    }
}

@Composable
private fun AboutDialog(vm: MainVm, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val version = remember { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName }
    LaunchedEffect(Unit) { vm.loadBoxInfo() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("EnigmaPlan $version") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text("Schnell mal das Programmheft durchblättern – tap, tap, tap – fertig. " +
                    "Oder in Ruhe alle Timer und Aufnahmen verwalten.", fontWeight = FontWeight.SemiBold)
                Text("Für Enigma2-Receiver mit OpenWebif: Vu+, GigaBlue, Zgemma, Dreambox & Co.",
                    Modifier.padding(top = 8.dp))

                Text("Verbundene Box", Modifier.padding(top = 16.dp), style = MaterialTheme.typography.titleSmall)
                val b = vm.boxInfo
                if (b == null) Text(vm.host, color = MaterialTheme.colorScheme.onSurfaceVariant)
                else Text(
                    listOf(b.model, b.image, "OpenWebif ${b.webif}", "${b.tuners} Tuner", vm.host)
                        .filter { it.isNotBlank() }.joinToString("\n"),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Text("So geht's", Modifier.padding(top = 16.dp), style = MaterialTheme.typography.titleSmall)
                Text("• Sender antippen → Wochenprogramm, wischen = nächster Sender\n" +
                    "• Kreis neben der Sendung = Aufnahme planen, roter Punkt = Timer gesetzt\n" +
                    "• Sendung antippen = Details, Timer bearbeiten oder löschen\n" +
                    "• Leiste oben: Jetzt · 20:15 · Tage", color = MaterialTheme.colorScheme.onSurfaceVariant)

                Text("Hinweis: EPG", Modifier.padding(top = 16.dp), style = MaterialTheme.typography.titleSmall)
                Text("Die App zeigt das Programm, das die Box gespeichert hat. Fehlt es bei manchen Sendern, " +
                    "hilft das Box-Plugin EPGRefresh: Es holt nachts im Standby das Programm aller Favoriten.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant)

                Text("Entwickler", Modifier.padding(top = 16.dp), style = MaterialTheme.typography.titleSmall)
                Text("© 2026 Thomas Bugge")
                val uri = LocalUriHandler.current
                Text("thomas@bgg-mail.de", color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 4.dp).clickable { uri.openUri("mailto:thomas@bgg-mail.de?subject=EnigmaPlan") })
                Text("github.com/thobgg/EnigmaPlan", color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 4.dp).clickable { uri.openUri("https://github.com/thobgg/EnigmaPlan") })
            }
        },
        confirmButton = { TextButton(onDismiss) { Text("Schließen") } },
    )
}

@Composable
private fun TabIcon(t: Tab, activeTimers: Int) {
    if (t == Tab.Timer && activeTimers > 0) {
        BadgedBox(badge = { Badge { Text("$activeTimers") } }) { Icon(t.icon, null) }
    } else Icon(t.icon, null)
}

@Composable
private fun BouquetPicker(vm: MainVm) {
    var open by remember { mutableStateOf(false) }
    Box {
        TextButton({ open = true }) {
            Text(vm.bouquets.firstOrNull { it.ref == vm.bref }?.name ?: "Bouquet", maxLines = 1,
                overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 160.dp))
            Icon(Icons.Default.ArrowDropDown, null)
        }
        DropdownMenu(open, { open = false }) {
            vm.bouquets.forEach { b ->
                DropdownMenuItem({ Text(b.name) }, onClick = { open = false; vm.selectBouquet(b.ref) })
            }
        }
    }
}

@Composable
private fun Offline(msg: String, firstStart: Boolean, onRetry: () -> Unit, onSettings: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
    ) {
        Text(msg, style = MaterialTheme.typography.titleMedium)
        Row(Modifier.padding(top = 16.dp)) {
            if (firstStart) Button(onSettings) { Text("Einstellungen öffnen") }
            else {
                Button(onRetry) { Text("Erneut versuchen") }
                TextButton(onSettings) { Text("Einstellungen") }
            }
        }
    }
}

const val DEFAULT_BEFORE = 3
const val DEFAULT_AFTER = 10

@Composable
private fun SettingsDialog(vm: MainVm, onDismiss: () -> Unit) {
    var host by remember { mutableStateOf(vm.host) }
    var user by remember { mutableStateOf(vm.user) }
    var pass by remember { mutableStateOf(vm.pass) }
    LaunchedEffect(Unit) { vm.loadMargins() }
    var before by remember { mutableStateOf<Int?>(null) }
    var after by remember { mutableStateOf<Int?>(null) }
    LaunchedEffect(vm.margins) { vm.margins?.let { before = it.first; after = it.second } }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Einstellungen") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text("Box-Verbindung", style = MaterialTheme.typography.titleSmall)
                OutlinedTextField(host, { host = it }, label = { Text("IP-Adresse der Box") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri), modifier = Modifier.padding(top = 4.dp))
                OutlinedTextField(user, { user = it }, label = { Text("Benutzer (optional)") }, singleLine = true,
                    modifier = Modifier.padding(top = 8.dp))
                OutlinedTextField(pass, { pass = it }, label = { Text("Passwort (optional)") }, singleLine = true,
                    visualTransformation = PasswordVisualTransformation(), modifier = Modifier.padding(top = 8.dp))

                Text("Aufnahme-Puffer", Modifier.padding(top = 20.dp), style = MaterialTheme.typography.titleSmall)
                Text("Wird in der Box gespeichert und gilt für alle neuen Timer – damit Anfang und Ende nicht fehlen, " +
                    "wenn ein Sender früher anfängt oder überzieht.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                val b = before
                val a = after
                if (b == null || a == null) Text("Lade …", Modifier.padding(top = 8.dp))
                else {
                    Stepper("Vorlauf", b) { before = it }
                    Stepper("Nachlauf", a) { after = it }
                    TextButton({ before = DEFAULT_BEFORE; after = DEFAULT_AFTER }, enabled = b != DEFAULT_BEFORE || a != DEFAULT_AFTER) {
                        Text("Standard ($DEFAULT_BEFORE / $DEFAULT_AFTER Min.)")
                    }
                }
            }
        },
        confirmButton = {
            TextButton({
                val m = vm.margins
                val b = before
                val a = after
                if (host.trim() != vm.host || user.trim() != vm.user || pass != vm.pass) vm.saveSettings(host, user, pass)
                else if (b != null && a != null && m != null && (b to a) != m) vm.saveMargins(b, a)
                onDismiss()
            }) { Text("Speichern") }
        },
        dismissButton = { TextButton(onDismiss) { Text("Abbrechen") } },
    )
}

/** Minuten mit − / + einstellen (0–60). */
@Composable
private fun Stepper(label: String, value: Int, onChange: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f))
        FilledTonalIconButton({ onChange((value - 1).coerceAtLeast(0)) }, enabled = value > 0) { Icon(Icons.Default.Remove, "weniger") }
        Text("$value Min.", Modifier.width(72.dp), textAlign = TextAlign.Center, style = MaterialTheme.typography.titleMedium)
        FilledTonalIconButton({ onChange((value + 1).coerceAtMost(60)) }, enabled = value < 60) { Icon(Icons.Default.Add, "mehr") }
    }
}

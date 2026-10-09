package de.thobug.enigmaplan

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.unit.dp
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
    data class NewTimer(val sref: String?) : SheetState
    data class OfMovie(val m: Movie) : SheetState
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun App(vm: MainVm) {
    var tab by rememberSaveable { mutableStateOf(Tab.Programm) }
    var sheet by remember { mutableStateOf<SheetState?>(null) }
    var settings by remember { mutableStateOf(false) }
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
                        Text(
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
                        IconButton({
                            when (tab) {
                                Tab.Aufnahmen -> vm.refreshMovies()
                                Tab.Suche -> if (vm.searchQuery.isNotBlank()) vm.search(vm.searchQuery) else vm.refreshAll()
                                else -> vm.refreshAll()
                            }
                        }) { Icon(Icons.Default.Refresh, "Aktualisieren") }
                        IconButton({ settings = true }) { Icon(Icons.Default.Settings, "Einstellungen") }
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
            Row(Modifier.padding(pad).fillMaxSize()) {
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
                    if (vm.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
                    Box(Modifier.weight(1f)) {
                        val off = vm.offline
                        if (off != null && vm.bouquets.isEmpty()) {
                            Offline(off, onRetry = vm::refreshAll, onSettings = { settings = true })
                        } else when (tab) {
                            Tab.Programm -> ProgramScreen(vm, wide,
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
        is SheetState.OfEvent -> EventSheet(vm, s.e, close, onEditTimer = { sheet = SheetState.EditTimer(it) })
        is SheetState.EditTimer -> TimerSheet(vm, s.t, null, close)
        is SheetState.NewTimer -> TimerSheet(vm, null, s.sref, close)
        is SheetState.OfMovie -> MovieSheet(vm, s.m, close)
        null -> {}
    }
    if (settings) SettingsDialog(vm) { settings = false }
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
private fun Offline(msg: String, onRetry: () -> Unit, onSettings: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
    ) {
        Text(msg, style = MaterialTheme.typography.titleMedium)
        Row(Modifier.padding(top = 16.dp)) {
            Button(onRetry) { Text("Erneut versuchen") }
            TextButton(onSettings) { Text("Einstellungen") }
        }
    }
}

@Composable
private fun SettingsDialog(vm: MainVm, onDismiss: () -> Unit) {
    var host by remember { mutableStateOf(vm.host) }
    var user by remember { mutableStateOf(vm.user) }
    var pass by remember { mutableStateOf(vm.pass) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Box-Verbindung") },
        text = {
            Column {
                OutlinedTextField(host, { host = it }, label = { Text("IP-Adresse der Box") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri))
                OutlinedTextField(user, { user = it }, label = { Text("Benutzer (optional)") }, singleLine = true,
                    modifier = Modifier.padding(top = 8.dp))
                OutlinedTextField(pass, { pass = it }, label = { Text("Passwort (optional)") }, singleLine = true,
                    visualTransformation = PasswordVisualTransformation(), modifier = Modifier.padding(top = 8.dp))
            }
        },
        confirmButton = { TextButton({ vm.saveSettings(host, user, pass); onDismiss() }) { Text("Speichern") } },
        dismissButton = { TextButton(onDismiss) { Text("Abbrechen") } },
    )
}

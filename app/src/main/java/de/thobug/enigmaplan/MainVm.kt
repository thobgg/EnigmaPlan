package de.thobug.enigmaplan

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.edit
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

fun nowSec() = System.currentTimeMillis() / 1000

class MainVm(app: Application) : AndroidViewModel(app) {
    private val prefs = app.getSharedPreferences("vu", 0)

    var host by mutableStateOf(prefs.getString("host", "192.168.178.94")!!)
        private set
    var user by mutableStateOf(prefs.getString("user", "")!!)
        private set
    var pass by mutableStateOf(prefs.getString("pass", "")!!)
        private set
    private var api = BoxApi(host, user, pass)

    var bouquets by mutableStateOf(listOf<Service>())
    var bref by mutableStateOf<String?>(null)
    var services by mutableStateOf(listOf<Service>())
    var nowNext by mutableStateOf(mapOf<String, List<Event>>())
    var timers by mutableStateOf(listOf<Timer>())
    var movies by mutableStateOf<List<Movie>?>(null)
    var disk by mutableStateOf<Disk?>(null)

    var selected by mutableStateOf<Service?>(null)
        private set
    /** Programm je Sender, Schlüssel = Service-Ref. */
    val epg = mutableStateMapOf<String, List<Event>>()
    var searchQuery by mutableStateOf("")
    var searchResults by mutableStateOf<List<Event>?>(null)

    var loading by mutableStateOf(false)
    var offline by mutableStateOf<String?>(null)
    var now by mutableLongStateOf(nowSec())
    val messages = Channel<String>(Channel.BUFFERED)

    init {
        refreshAll()
        viewModelScope.launch {
            var n = 0
            while (true) {
                delay(30_000)
                now = nowSec()
                if (++n % 2 == 0 && offline == null) runCatching { bref?.let { nowNext = api.nowNext(it) } }
            }
        }
    }

    private fun run(block: suspend () -> Unit) {
        viewModelScope.launch {
            loading = true
            try {
                block()
                offline = null
            } catch (e: Exception) {
                val msg = e.message ?: e.toString()
                if (bouquets.isEmpty()) offline = msg
                messages.send(msg)
            } finally {
                loading = false
                now = nowSec()
            }
        }
    }

    fun refreshAll() = run {
        bouquets = api.bouquets()
        val saved = prefs.getString("bref", null)
        bref = (bouquets.firstOrNull { it.ref == saved } ?: bouquets.firstOrNull())?.ref
        bref?.let {
            services = api.services(it)
            nowNext = api.nowNext(it)
        }
        timers = api.timers()
        val keep = selected?.ref
        epg.keys.retainAll(setOfNotNull(keep))
        keep?.let { epg[it] = api.epgService(it) }
    }

    fun selectBouquet(ref: String) = run {
        bref = ref
        prefs.edit { putString("bref", ref) }
        selected = null
        epg.clear()
        services = api.services(ref)
        nowNext = api.nowNext(ref)
    }

    fun select(s: Service?) {
        selected = s
        s?.let { loadEpg(it.ref) }
    }

    /** Senderprogramm laden, falls noch nicht im Cache. */
    fun loadEpg(ref: String) {
        if (ref in epg) return
        viewModelScope.launch {
            try { epg[ref] = api.epgService(ref) } catch (e: Exception) { messages.send(e.message ?: e.toString()) }
        }
    }

    fun search(q: String) {
        searchQuery = q
        if (q.isBlank()) return
        searchResults = null
        run {
            val t = nowSec()
            searchResults = api.search(q.trim()).filter { it.end > t }.sortedBy { it.begin }
            if (timers.isEmpty()) timers = api.timers()
        }
    }

    fun refreshTimers() = run { timers = api.timers() }

    fun refreshMovies() = run {
        movies = api.movies()
        disk = runCatching { api.disk() }.getOrNull()
    }

    fun timerFor(e: Event): Timer? {
        val r = normRef(e.sref)
        return timers.firstOrNull {
            normRef(it.sref) == r && ((e.id != 0 && it.eit == e.id) || (it.begin <= e.begin + 60 && it.end >= e.end - 60))
        }
    }

    /** Timer-Aktion ausführen, danach Timerliste neu laden. */
    private fun timerAction(msg: String, done: () -> Unit, block: suspend () -> Unit) = run {
        block()
        timers = api.timers()
        done()
        messages.send(msg)
    }

    fun record(e: Event, done: () -> Unit) =
        timerAction("Aufnahme geplant: ${e.title}", done) { api.addByEvent(e) }

    fun addTimer(sref: String, name: String, begin: Long, end: Long, justplay: Boolean, done: () -> Unit) =
        timerAction("Timer angelegt", done) { api.addTimer(sref, name, begin, end, justplay) }

    fun changeTimer(t: Timer, name: String, begin: Long, end: Long, justplay: Boolean, done: () -> Unit) =
        timerAction("Timer gespeichert", done) { api.changeTimer(t, name, begin, end, justplay) }

    fun toggleTimer(t: Timer, done: () -> Unit) =
        timerAction(if (t.disabled) "Timer aktiviert" else "Timer deaktiviert", done) { api.toggleTimer(t) }

    fun deleteTimer(t: Timer, done: () -> Unit) =
        timerAction("Timer gelöscht", done) { api.deleteTimer(t) }

    fun cleanupTimers() = timerAction("Erledigte Timer entfernt", {}) { api.cleanupTimers() }

    fun deleteMovie(m: Movie, done: () -> Unit) = run {
        api.deleteMovie(m)
        done()
        messages.send("Aufnahme gelöscht")
        movies = api.movies()
        disk = runCatching { api.disk() }.getOrNull()
    }

    fun saveSettings(h: String, u: String, p: String) {
        host = h.trim(); user = u.trim(); pass = p
        prefs.edit { putString("host", host); putString("user", user); putString("pass", pass) }
        api = BoxApi(host, user, pass)
        bouquets = emptyList(); services = emptyList(); nowNext = emptyMap(); timers = emptyList()
        movies = null; selected = null; epg.clear()
        refreshAll()
    }
}

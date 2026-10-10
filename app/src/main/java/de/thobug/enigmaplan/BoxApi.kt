package de.thobug.enigmaplan

import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

data class Service(val ref: String, val name: String)

data class Event(
    val id: Int,
    val sref: String,
    val sname: String,
    val title: String,
    val short: String,
    val long: String,
    val begin: Long,
    val duration: Long,
) {
    val end get() = begin + duration
}

data class Timer(
    val sref: String,
    val sname: String,
    val name: String,
    val description: String,
    val begin: Long,
    val end: Long,
    val eit: Int,
    val disabled: Boolean,
    val justplay: Boolean,
    val state: Int,
    val repeated: Int,
    val afterevent: Int,
    val dirname: String,
    val tags: String,
)

data class Movie(
    val ref: String,
    val file: String,
    val title: String,
    val sname: String,
    val description: String,
    val descriptionExt: String,
    val time: Long,
    val size: String,
    val length: String,
)

data class BoxInfo(val model: String, val image: String, val webif: String, val tuners: Int)

data class Disk(val free: String, val capacity: String, val usedFraction: Float)

/** Service-Referenzen vergleichbar machen (Timer haben teils Namen/Pfade angehängt). */
fun normRef(r: String) = r.split(':').take(10).joinToString(":").uppercase()

/** DVB-Steuerzeichen: 0x8A = Zeilenumbruch, übrige C1-Zeichen (z. B. 0x86/0x87 Hervorhebung) entfernen. */
private fun clean(t: String) = t.replace('\u008A', '\n').replace(Regex("[\u0080-\u009F]"), "")
    // OpenWebif liefert manche Zeichen HTML-kodiert
    .replace("&quot;", "\"").replace("&#39;", "'").replace("&apos;", "'")
    .replace("&lt;", "<").replace("&gt;", ">").replace("&amp;", "&")
    .trim()

private fun JSONObject.s(k: String) = if (has(k) && !isNull(k)) clean(optString(k)) else ""
private fun JSONObject.l(k: String) = if (has(k) && !isNull(k)) optLong(k) else 0L
private fun JSONObject.i(k: String) = if (has(k) && !isNull(k)) optInt(k) else 0

/** Zugriff auf die OpenWebif-API der Box. */
class BoxApi(host: String, private val user: String, private val pass: String) {
    private val base = host.trim().trimEnd('/').let { if (it.startsWith("http")) it else "http://$it" }

    /** Direkte Adresse einer Aufnahme (OpenWebif /file, mit Byte-Ranges, also spulbar). */
    fun movieUri(m: Movie): android.net.Uri {
        val b = android.net.Uri.parse(base).buildUpon()
        if (user.isNotEmpty()) b.encodedAuthority(android.net.Uri.encode(user) + ":" + android.net.Uri.encode(pass) + "@" + android.net.Uri.parse(base).encodedAuthority)
        return b.appendPath("file").appendQueryParameter("file", m.file).build()
    }

    private suspend fun get(path: String, params: Map<String, Any?> = emptyMap(), post: Boolean = false): JSONObject =
        withContext(Dispatchers.IO) {
            val q = params.filterValues { it != null }.entries.joinToString("&") {
                it.key + "=" + URLEncoder.encode(it.value.toString(), "UTF-8")
            }
            val url = "$base/api/$path" + if (q.isEmpty() || post) "" else "?$q"
            val conn = URL(url).openConnection() as HttpURLConnection
            conn.connectTimeout = 5000
            conn.readTimeout = 30000
            if (post) {
                conn.requestMethod = "POST"
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
                conn.outputStream.use { it.write(q.toByteArray()) }
            }
            if (user.isNotEmpty()) {
                val auth = Base64.encodeToString("$user:$pass".toByteArray(), Base64.NO_WRAP)
                conn.setRequestProperty("Authorization", "Basic $auth")
            }
            try {
                when (val code = conn.responseCode) {
                    200 -> JSONObject(conn.inputStream.bufferedReader(Charsets.UTF_8).readText())
                    401 -> throw IOException("Box verlangt Benutzername und Passwort (Einstellungen)")
                    else -> throw IOException("Box antwortet mit HTTP $code")
                }
            } catch (e: java.net.ConnectException) {
                throw IOException("Box nicht erreichbar – bist du im Heim-WLAN?")
            } catch (e: java.net.SocketTimeoutException) {
                throw IOException("Box antwortet nicht")
            } finally {
                conn.disconnect()
            }
        }

    /** Wirft eine Exception, wenn die Box result=false meldet. */
    private fun JSONObject.check(fallback: String): JSONObject {
        if (has("result") && !optBoolean("result", true)) throw IOException(s("message").ifEmpty { fallback })
        return this
    }

    private fun services(o: JSONObject): List<Service> {
        val a = o.optJSONArray("services") ?: return emptyList()
        return (0 until a.length()).map { a.getJSONObject(it) }
            .filter { !it.s("servicereference").startsWith("1:64:") } // Trenner/Marker
            .map { Service(it.s("servicereference"), it.s("servicename")) }
    }

    private fun events(o: JSONObject): List<Event> {
        val a = o.optJSONArray("events") ?: return emptyList()
        return (0 until a.length()).mapNotNull { idx ->
            val e = a.getJSONObject(idx)
            val begin = e.l("begin_timestamp")
            val title = e.s("title")
            if (begin == 0L || title.isEmpty()) null
            else Event(e.i("id"), e.s("sref"), e.s("sname"), title, e.s("shortdesc"), e.s("longdesc"), begin, e.l("duration_sec"))
        }
    }

    /** Senderlogo von der Box (OpenWebif /picon), null wenn keins vorhanden. */
    suspend fun picon(ref: String): android.graphics.Bitmap? = withContext(Dispatchers.IO) {
        val name = ref.split(':').take(10).joinToString("_") + ".png"
        val conn = URL("$base/picon/$name").openConnection() as HttpURLConnection
        conn.connectTimeout = 5000
        conn.readTimeout = 10000
        try {
            if (conn.responseCode == 200) conn.inputStream.use { android.graphics.BitmapFactory.decodeStream(it) } else null
        } catch (e: IOException) {
            null
        } finally {
            conn.disconnect()
        }
    }

    suspend fun bouquets() = services(get("getservices"))
    suspend fun services(bref: String) = services(get("getservices", mapOf("sRef" to bref)))

    /** Jetzt/Danach je Sender, Schlüssel = normRef. */
    suspend fun nowNext(bref: String): Map<String, List<Event>> =
        events(get("epgnownext", mapOf("bRef" to bref))).groupBy { normRef(it.sref) }

    /** Programm aller Sender eines Bouquets ab [time] (die Box liefert alles bis zum EPG-Ende). */
    suspend fun epgMulti(bref: String, time: Long) = events(get("epgmulti", mapOf("bRef" to bref, "time" to time)))

    suspend fun epgService(sref: String) = events(get("epgservice", mapOf("sRef" to sref)))
    suspend fun search(q: String) = events(get("epgsearch", mapOf("search" to q)))

    suspend fun timers(): List<Timer> {
        val a = get("timerlist").optJSONArray("timers") ?: return emptyList()
        return (0 until a.length()).map { a.getJSONObject(it) }.map {
            Timer(
                sref = it.s("serviceref"), sname = it.s("servicename"), name = it.s("name"),
                description = it.s("description"), begin = it.l("begin"), end = it.l("end"),
                eit = it.i("eit"), disabled = it.i("disabled") != 0, justplay = it.i("justplay") != 0,
                state = it.i("state"), repeated = it.i("repeated"), afterevent = it.i("afterevent"),
                dirname = it.s("dirname").takeUnless { d -> d == "None" } ?: "", tags = it.s("tags"),
            )
        }.sortedBy { it.begin }
    }

    suspend fun movies(): List<Movie> {
        val a = get("movielist").optJSONArray("movies") ?: return emptyList()
        return (0 until a.length()).map { a.getJSONObject(it) }.map {
            Movie(
                ref = it.s("serviceref"), file = it.s("filename"),
                title = it.s("eventname").ifEmpty { it.s("filename_stripped") },
                sname = it.s("servicename"), description = it.s("description"),
                descriptionExt = it.s("descriptionExtended"), time = it.l("recordingtime"),
                size = it.s("filesize_readable"), length = it.s("length"),
            )
        }.sortedByDescending { it.time }
    }

    suspend fun info(): BoxInfo {
        val i = get("about").optJSONObject("info") ?: JSONObject()
        return BoxInfo(
            model = listOf(i.s("brand"), i.s("model")).filter { it.isNotEmpty() }.joinToString(" "),
            image = listOf(i.s("imagedistro"), i.s("imagever")).filter { it.isNotEmpty() }.joinToString(" "),
            webif = i.s("webifver").removePrefix("OWIF").trim(),
            tuners = i.optJSONArray("tuners")?.length() ?: 0,
        )
    }

    suspend fun disk(): Disk? {
        val hdd = get("about").optJSONObject("info")?.optJSONArray("hdd")?.optJSONObject(0) ?: return null
        fun gb(s: String): Double? {
            val m = Regex("""([\d.]+)\s*(TB|GB|MB)""").find(s) ?: return null
            val f = when (m.groupValues[2]) { "TB" -> 1024.0; "GB" -> 1.0; else -> 1 / 1024.0 }
            return m.groupValues[1].toDoubleOrNull()?.times(f)
        }
        val free = gb(hdd.s("free")) ?: return null
        val cap = gb(hdd.s("capacity"))?.takeIf { it > 0 } ?: return null
        return Disk(hdd.s("free"), hdd.s("capacity"), (1 - free / cap).toFloat().coerceIn(0f, 1f))
    }

    suspend fun addByEvent(e: Event) {
        get("timeraddbyeventid", mapOf("sRef" to e.sref, "eventid" to e.id)).check("Timer konnte nicht angelegt werden")
    }

    /** Aufnahme-Puffer der Box (Minuten) aus den Box-Einstellungen; fehlt ein Wert, gilt der Box-Standard. */
    suspend fun recordingMargins(sample: Event?): Pair<Int, Int> {
        val a = get("settings").optJSONArray("settings")
        val map = (0 until (a?.length() ?: 0)).mapNotNull { a!!.optJSONArray(it) }
            .associate { it.optString(0) to it.optString(1) }
        val before = map["config.recording.margin_before"]?.toIntOrNull()
        val after = map["config.recording.margin_after"]?.toIntOrNull()
        if (before != null && after != null) return before to after
        val fallback = sample?.let { margins(it) } ?: (0 to 0)
        return (before ?: fallback.first) to (after ?: fallback.second)
    }

    suspend fun setRecordingMargins(before: Int, after: Int) {
        get("saveconfig", mapOf("key" to "config.recording.margin_before", "value" to before), post = true).check("Vorlauf nicht gespeichert")
        get("saveconfig", mapOf("key" to "config.recording.margin_after", "value" to after), post = true).check("Nachlauf nicht gespeichert")
    }

    /** Vor-/Nachlauf der Box in Minuten. */
    suspend fun margins(e: Event): Pair<Int, Int> {
        val ev = get("event", mapOf("sref" to e.sref, "idev" to e.id)).optJSONObject("event") ?: return 0 to 0
        return ev.i("recording_margin_before") to ev.i("recording_margin_after")
    }

    suspend fun addTimer(sref: String, name: String, description: String, begin: Long, end: Long, justplay: Boolean, repeated: Int) {
        get(
            "timeradd", mapOf(
                "sRef" to sref, "name" to name, "description" to description.ifEmpty { null },
                "begin" to begin, "end" to end,
                "justplay" to if (justplay) 1 else 0, "afterevent" to 3, "repeated" to repeated,
            )
        ).check("Timer konnte nicht angelegt werden")
    }

    suspend fun changeTimer(t: Timer, name: String, begin: Long, end: Long, justplay: Boolean, repeated: Int) {
        get(
            "timerchange", mapOf(
                "sRef" to t.sref, "channelOld" to t.sref, "beginOld" to t.begin, "endOld" to t.end,
                "name" to name, "begin" to begin, "end" to end, "description" to t.description,
                "justplay" to if (justplay) 1 else 0, "disabled" to if (t.disabled) 1 else 0,
                "afterevent" to t.afterevent, "repeated" to repeated,
                "dirname" to t.dirname.ifEmpty { null }, "tags" to t.tags.ifEmpty { null },
            )
        ).check("Timer konnte nicht gespeichert werden")
    }

    suspend fun deleteTimer(t: Timer) {
        get("timerdelete", mapOf("sRef" to t.sref, "begin" to t.begin, "end" to t.end)).check("Löschen fehlgeschlagen")
    }

    suspend fun toggleTimer(t: Timer) {
        get("timertogglestatus", mapOf("sRef" to t.sref, "begin" to t.begin, "end" to t.end)).check("Fehlgeschlagen")
    }

    suspend fun cleanupTimers() {
        get("timercleanup")
    }

    suspend fun deleteMovie(m: Movie) {
        get("moviedelete", mapOf("sRef" to m.ref)).check("Löschen fehlgeschlagen")
    }
}

package de.thobug.enigmaplan

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

val zone: ZoneId get() = ZoneId.systemDefault()
private val HM = DateTimeFormatter.ofPattern("HH:mm")
private val WD = listOf("Mo", "Di", "Mi", "Do", "Fr", "Sa", "So")

fun localDate(ts: Long): LocalDate = Instant.ofEpochSecond(ts).atZone(zone).toLocalDate()
fun localTime(ts: Long): LocalTime = Instant.ofEpochSecond(ts).atZone(zone).toLocalTime()
fun hm(ts: Long): String = localTime(ts).format(HM)
fun hm(t: LocalTime): String = t.format(HM)
fun epoch(d: LocalDate, t: LocalTime): Long = LocalDateTime.of(d, t).atZone(zone).toEpochSecond()

fun dayLabel(d: LocalDate): String {
    val diff = ChronoUnit.DAYS.between(LocalDate.now(zone), d)
    return when (diff) {
        0L -> "Heute"
        1L -> "Morgen"
        -1L -> "Gestern"
        else -> "${WD[d.dayOfWeek.value - 1]}, ${d.dayOfMonth}.${d.monthValue}."
    }
}

fun dayLabel(ts: Long) = dayLabel(localDate(ts))
fun minutes(sec: Long) = "${(sec + 30) / 60} Min."

/* Serien-Timer: Bitmaske der Wochentage, Bit 0 = Montag (wie Enigma2). */
const val REPEAT_DAILY = 127
const val REPEAT_WORKDAYS = 31
fun weekdayBit(d: LocalDate) = 1 shl (d.dayOfWeek.value - 1)

fun repeatLabel(mask: Int): String = when (mask) {
    0 -> "Einmal"
    REPEAT_DAILY -> "Täglich"
    REPEAT_WORKDAYS -> "Mo–Fr"
    96 -> "Sa + So"
    else -> (0..6).filter { mask and (1 shl it) != 0 }.joinToString(", ") { WD[it] }
}

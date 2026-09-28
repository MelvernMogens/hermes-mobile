package id.melvern.hermesmobile.ui.components

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

/**
 * M8: format waktu UI (English, 24 jam). Pure — `now`/`zone` disuntik untuk test.
 *  - list: "18:54" (hari ini) · "Yesterday" · "Mon" (< 7 hari) · "15 Sep" · "15 Sep 2025"
 *  - bubble: "18:54"
 *  - pemisah hari: "Today" · "Yesterday" · "Monday" · "15 September" · "15 September 2025"
 */
object RelTime {
    private val CLOCK = DateTimeFormatter.ofPattern("HH:mm", Locale.US)
    private val DAY_SHORT = DateTimeFormatter.ofPattern("EEE", Locale.US)
    private val DAY_LONG = DateTimeFormatter.ofPattern("EEEE", Locale.US)
    private val DATE_SHORT = DateTimeFormatter.ofPattern("d MMM", Locale.US)
    private val DATE_SHORT_Y = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.US)
    private val DATE_LONG = DateTimeFormatter.ofPattern("d MMMM", Locale.US)
    private val DATE_LONG_Y = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.US)

    private fun toZoned(epochSec: Double, zone: ZoneId) =
        Instant.ofEpochMilli((epochSec * 1000).toLong()).atZone(zone)

    fun clock(epochSec: Double?, zone: ZoneId = ZoneId.systemDefault()): String =
        if (epochSec == null || epochSec <= 0) "" else toZoned(epochSec, zone).format(CLOCK)

    fun listStamp(epochSec: Double?, nowMs: Long = System.currentTimeMillis(), zone: ZoneId = ZoneId.systemDefault()): String {
        if (epochSec == null || epochSec <= 0) return ""
        val at = toZoned(epochSec, zone)
        val today = Instant.ofEpochMilli(nowMs).atZone(zone).toLocalDate()
        val days = ChronoUnit.DAYS.between(at.toLocalDate(), today)
        return when {
            days <= 0L -> at.format(CLOCK)
            days == 1L -> "Yesterday"
            days < 7L -> at.format(DAY_SHORT)
            at.year == today.year -> at.format(DATE_SHORT)
            else -> at.format(DATE_SHORT_Y)
        }
    }

    fun dayKey(epochSec: Double?, zone: ZoneId = ZoneId.systemDefault()): LocalDate? =
        if (epochSec == null || epochSec <= 0) null else toZoned(epochSec, zone).toLocalDate()

    fun dayLabel(day: LocalDate, nowMs: Long = System.currentTimeMillis(), zone: ZoneId = ZoneId.systemDefault()): String {
        val today = Instant.ofEpochMilli(nowMs).atZone(zone).toLocalDate()
        val days = ChronoUnit.DAYS.between(day, today)
        return when {
            days <= 0L -> "Today"
            days == 1L -> "Yesterday"
            days < 7L -> day.format(DAY_LONG)
            day.year == today.year -> day.format(DATE_LONG)
            else -> day.format(DATE_LONG_Y)
        }
    }
}

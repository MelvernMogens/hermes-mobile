package id.melvern.hermesmobile.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

class RelTimeTest {
    private val zone = ZoneId.of("Asia/Bangkok")
    // "sekarang" = Senin 28 Sep 2026 20:00 WIB
    private val now = ZonedDateTime.of(2026, 9, 28, 20, 0, 0, 0, zone)
    private val nowMs = now.toInstant().toEpochMilli()
    private fun ep(z: ZonedDateTime) = z.toEpochSecond().toDouble()

    @Test fun sameDay_showsClock() =
        assertEquals("18:54", RelTime.listStamp(ep(now.withHour(18).withMinute(54)), nowMs, zone))

    @Test fun yesterday_evenIfLessThan24h() =
        assertEquals("Yesterday", RelTime.listStamp(ep(now.minusDays(1).withHour(23)), nowMs, zone))

    @Test fun withinWeek_showsShortWeekday() =
        assertEquals("Thu", RelTime.listStamp(ep(now.minusDays(4)), nowMs, zone))

    @Test fun older_showsDayMonth() =
        assertEquals("15 Sep", RelTime.listStamp(ep(now.withDayOfMonth(15)), nowMs, zone))

    @Test fun previousYear_includesYear() =
        assertEquals("15 Sep 2025", RelTime.listStamp(ep(now.minusYears(1).withDayOfMonth(15)), nowMs, zone))

    @Test fun missing_isBlank() = assertEquals("", RelTime.listStamp(null, nowMs, zone))

    @Test fun dayLabels() {
        assertEquals("Today", RelTime.dayLabel(LocalDate.of(2026, 9, 28), nowMs, zone))
        assertEquals("Yesterday", RelTime.dayLabel(LocalDate.of(2026, 9, 27), nowMs, zone))
        assertEquals("15 September", RelTime.dayLabel(LocalDate.of(2026, 9, 15), nowMs, zone))
    }
}

package dev.mdwriter.ui.library

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.Locale

/** Locale.US, ZoneOffset.UTC, now = 2026-09-25T15:00:00Z (a Friday) unless noted otherwise (T12 task spec §C). */
class RelativeDateTest {
    private val now = Instant.parse("2026-09-25T15:00:00Z")

    private fun fmt(
        epoch: String?,
        use24h: Boolean = true,
        zone: ZoneId = ZoneOffset.UTC,
        now: Instant = this.now,
    ): String =
        RelativeDate.format(
            epochMillis = epoch?.let { Instant.parse(it).toEpochMilli() },
            now = now,
            zone = zone,
            locale = Locale.US,
            use24h = use24h,
            yesterday = "Yesterday",
        )

    @Test
    fun todayShowsTime() {
        assertThat(fmt("2026-09-25T14:02:00Z", use24h = true)).isEqualTo("14:02")
        assertThat(fmt("2026-09-25T14:02:00Z", use24h = false)).isEqualTo("2:02 PM")
    }

    @Test
    fun yesterday() {
        assertThat(fmt("2026-09-24T23:59:00Z")).isEqualTo("Yesterday")
    }

    @Test
    fun withinLastWeekShowsWeekday() {
        assertThat(fmt("2026-09-21T10:00:00Z")).isEqualTo("Mon")
        assertThat(fmt("2026-09-19T10:00:00Z")).isEqualTo("Sat")
    }

    @Test
    fun sameYearShowsDayMonth() {
        assertThat(fmt("2026-09-18T10:00:00Z")).isEqualTo("18 Sep")
        assertThat(fmt("2026-01-02T10:00:00Z")).isEqualTo("2 Jan")
    }

    @Test
    fun differentYearShowsFullDate() {
        assertThat(fmt("2025-09-21T10:00:00Z")).isEqualTo("21 Sep 2025")
    }

    @Test
    fun futureBeyondTodayShowsDayMonth() {
        assertThat(fmt("2026-09-26T10:00:00Z")).isEqualTo("26 Sep")
    }

    @Test
    fun zoneAffectsYesterdayBoundary() {
        val nowBerlin = Instant.parse("2026-09-24T22:30:00Z") // 2026-09-25T00:30+02:00
        val t = Instant.parse("2026-09-24T21:30:00Z") // 2026-09-24T23:30+02:00
        assertThat(
            RelativeDate.format(
                t.toEpochMilli(),
                nowBerlin,
                ZoneId.of("Europe/Berlin"),
                Locale.US,
                true,
                "Yesterday",
            ),
        ).isEqualTo("Yesterday")
    }

    @Test
    fun nullEpochIsEmpty() {
        assertThat(fmt(null)).isEqualTo("")
    }
}

package dev.mdwriter.ui.library

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

object RelativeDate {
    /**
     * 02 §7: today "14:02" (or "2:02 PM"), yesterday [yesterday], 2–6 days ago "Mon", same year "21 Sep",
     * other years / future beyond today "21 Sep 2025". Pure: zone, locale and 24 h flag are parameters.
     */
    fun format(
        epochMillis: Long?,
        now: Instant,
        zone: ZoneId,
        locale: Locale,
        use24h: Boolean,
        yesterday: String,
    ): String {
        if (epochMillis == null) return ""
        val t = Instant.ofEpochMilli(epochMillis).atZone(zone)
        val today = now.atZone(zone).toLocalDate()
        val days = ChronoUnit.DAYS.between(t.toLocalDate(), today)
        val pattern =
            when {
                days == 0L -> if (use24h) "HH:mm" else "h:mm a"
                days == 1L -> return yesterday
                days in 2..6 -> "EEE"
                t.year == today.year -> "d MMM"
                else -> "d MMM yyyy"
            }
        return DateTimeFormatter.ofPattern(pattern, locale).format(t)
    }
}

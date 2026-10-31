package tw.dietbook

import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

object Days {
    val zone: ZoneId = ZoneId.of("Asia/Taipei")
    private val fmt = DateTimeFormatter.ISO_LOCAL_DATE

    fun today(): String = LocalDate.now(zone).format(fmt)
    fun parse(s: String): LocalDate = LocalDate.parse(s, fmt)
    fun yesterdayOf(day: String): String = parse(day).minusDays(1).format(fmt)
    fun label(day: String): String {
        val d = parse(day)
        val t = LocalDate.now(zone)
        return when (d) {
            t -> "今天 $day"
            t.minusDays(1) -> "昨天 $day"
            else -> day
        }
    }
}

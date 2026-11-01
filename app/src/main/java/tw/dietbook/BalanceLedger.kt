package tw.dietbook

/**
 * Pure 總存款 ledger (no Android).
 *
 * Daily delta = BMR + burn − intake (positive = deficit = good savings).
 * [balance] holds the sum of finalized days from [balanceStartDay] through [lastFinalizedDay].
 * Live total = balance + today's delta (today is never double-counted into [balance]).
 */
data class BalanceLedger(
    var balance: Double = 0.0,
    var lastFinalizedDay: String = "",
    var balanceStartDay: String = "",
) {
    /** Live piggy-bank total including an in-progress day. */
    fun liveTotal(todayDelta: Double): Double = balance + todayDelta

    /**
     * Credit each calendar day after [lastFinalizedDay] up to [yesterday] exactly once.
     * Days with no logs still deposit BMR (burn=0, intake=0 → +BMR).
     * Skips days before [balanceStartDay] when set.
     */
    fun catchUp(
        yesterday: String,
        dayDelta: (day: String) -> Double,
    ): List<String> {
        if (yesterday.isBlank()) return emptyList()
        val end = Days.parse(yesterday)
        val start = when {
            lastFinalizedDay.isNotEmpty() -> Days.parse(lastFinalizedDay).plusDays(1)
            balanceStartDay.isNotEmpty() -> Days.parse(balanceStartDay)
            else -> end // first run with empty markers: only finalize yesterday once
        }
        if (start.isAfter(end)) return emptyList()
        val credited = ArrayList<String>()
        var d = start
        while (!d.isAfter(end)) {
            val day = d.toString()
            if (balanceStartDay.isEmpty() || !d.isBefore(Days.parse(balanceStartDay))) {
                balance += dayDelta(day)
                lastFinalizedDay = day
                if (balanceStartDay.isEmpty()) balanceStartDay = day
                credited.add(day)
            }
            d = d.plusDays(1)
        }
        return credited
    }

    /** Adjust balance when a past finalized day's delta changes (delete / edit). */
    fun adjustFinalizedDelta(oldDelta: Double, newDelta: Double) {
        val diff = newDelta - oldDelta
        if (kotlin.math.abs(diff) > 0.01) balance += diff
    }

    /**
     * Reset savings clock to [today]: balance cleared, history kept elsewhere.
     * [lastFinalizedDay] = yesterday so today is live-only and BMR is not double-finalized.
     */
    fun resetTo(today: String) {
        balance = 0.0
        balanceStartDay = today
        lastFinalizedDay = Days.yesterdayOf(today)
    }
}

object BalanceMath {
    /** Authoritative daily savings delta. Burn is 0 when no manual/HC activity. */
    fun dayDelta(intake: Double, bmr: Double, burn: Double): Double = bmr + burn - intake

    fun burnKcal(manualBurn: Double, hcBurn: Double?, hcIgnored: Boolean): Double {
        val hc = if (hcIgnored) null else hcBurn
        return Calc.effectiveActive(manualBurn, hc) ?: 0.0
    }
}

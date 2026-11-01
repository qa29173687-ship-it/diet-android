package tw.dietbook

import android.content.Context

/** One day's cute wrap-up numbers + advice (not shown on the home screen). */
data class DailySummaryModel(
    val day: String,
    val intakeKcal: Int,
    val proteinG: Int,
    val burnKcal: Int,
    val strengthMin: Int,
    val netKcal: Double,
    val balanceKcal: Double,
    val bmr: Double,
    val advice: AdviceCard,
)

object DailySummary {
    fun build(ctx: Context, day: String, profile: UserProfile? = null, liveBalance: Double? = null): DailySummaryModel {
        val u = profile ?: Store.profile(ctx)
        val snap = Store.snapshot(ctx, day)
        val (intakeRaw, proteinRaw) = if (snap != null) snap.intakeKcal to snap.protein else Store.dayTotals(ctx, day)
        val bmr = snap?.bmr ?: u.bmr
        val tdee = snap?.tdeeFallback ?: Calc.tdee(bmr, u.activity)
        val manual = snap?.manualBurn ?: Store.manualBurn(ctx, day)
        val active = snap?.activeBurn ?: Calc.effectiveActive(manual, null)
        val net = snap?.net ?: Calc.dayNet(intakeRaw, bmr, active, tdee)
        val burnShown = when {
            manual > 0.5 -> manual
            active != null -> active
            snap?.burnSource == "health_connect" -> snap.hcBurn ?: 0.0
            else -> 0.0
        }
        val strengthMin = Store.strengthLogs(ctx, day).sumOf { it.minutes }
        val balance = when {
            liveBalance != null -> liveBalance
            day == Days.today() -> u.balance + net
            else -> u.balance // after finalize catch-up, profile already includes past days
        }
        val advice = DailyAdvice.of(intakeRaw, bmr, balance)
        return DailySummaryModel(
            day = day,
            intakeKcal = Calc.r0(intakeRaw),
            proteinG = Calc.r0(proteinRaw),
            burnKcal = Calc.r0(burnShown),
            strengthMin = strengthMin,
            netKcal = net,
            balanceKcal = balance,
            bmr = bmr,
            advice = advice,
        )
    }

    /** Sample for Robolectric preview — rule 1 (intake below BMR). */
    fun sampleBelowBmr(day: String = Days.yesterdayOf(Days.today())): DailySummaryModel {
        val intake = 980.0
        val bmr = 1320.0
        val balance = -620.0
        return DailySummaryModel(
            day = day,
            intakeKcal = 980,
            proteinG = 42,
            burnKcal = 180,
            strengthMin = 20,
            netKcal = -520.0,
            balanceKcal = balance,
            bmr = bmr,
            advice = DailyAdvice.of(intake, bmr, balance),
        )
    }
}

package tw.dietbook

import android.content.Context
import java.time.LocalDate

/**
 * 累計熱量帳戶：每日淨值持續疊加，不週清。
 * 切到新的一天時，把「昨天」定案並加進 balance。
 */
object Balance {
    suspend fun refresh(ctx: Context, hc: HealthDay? = null): Pair<UserProfile, DaySnapshot> {
        val today = Days.today()
        var u = Store.profile(ctx)
        // Finalize any missing days between lastFinalized and yesterday
        if (u.setupDone) {
            u = finalizeCatchUp(ctx, u, today)
        }
        val health = hc ?: if (u.hcEnabled) Health.readToday(ctx) else
            HealthDay(null, null, null, false, false, "")
        val (intake, protein) = Store.dayTotals(ctx, today)
        val tdee = Calc.tdee(u.bmr, u.activity)
        val active = health.activeKcal
        val net = Calc.dayNet(intake, u.bmr, active, tdee)
        // Optional: update weight from HC into profile (display only unless user saves)
        val snap = DaySnapshot(
            day = today,
            intakeKcal = intake,
            protein = protein,
            bmr = u.bmr,
            activeBurn = active,
            tdeeFallback = tdee,
            net = net,
            steps = health.steps,
            weightKg = health.weightKg ?: u.weightKg,
            finalized = false,
        )
        Store.saveSnapshot(ctx, snap)
        return u to snap
    }

    private fun finalizeCatchUp(ctx: Context, profile: UserProfile, today: String): UserProfile {
        var u = profile
        val last = u.lastFinalizedDay
        val start = if (last.isEmpty()) {
            // first time: don't backfill ancient history; start from yesterday only if logs exist
            Days.parse(today).minusDays(1)
        } else {
            Days.parse(last).plusDays(1)
        }
        val end = Days.parse(today).minusDays(1)
        if (start.isAfter(end)) return u
        var d = start
        var bal = u.balance
        while (!d.isAfter(end)) {
            val day = d.toString()
            val existing = Store.snapshot(ctx, day)
            val snap = if (existing != null && existing.finalized) existing else {
                val (intake, protein) = Store.dayTotals(ctx, day)
                val tdee = Calc.tdee(u.bmr, u.activity)
                // 過去日若無 HC 快照，用 TDEE 回退
                val active = existing?.activeBurn
                val net = Calc.dayNet(intake, u.bmr, active, tdee)
                DaySnapshot(day, intake, protein, u.bmr, active, tdee, net, existing?.steps, existing?.weightKg, true)
            }
            val finalized = snap.copy(finalized = true)
            if (existing?.finalized != true) {
                Store.saveSnapshot(ctx, finalized)
                bal += finalized.net
            }
            u = u.copy(balance = bal, lastFinalizedDay = day)
            Store.saveProfile(ctx, u)
            d = d.plusDays(1)
        }
        return u
    }

    fun adjustBalance(ctx: Context, delta: Double) {
        val u = Store.profile(ctx)
        Store.saveProfile(ctx, u.copy(balance = u.balance + delta))
    }

    fun setBalance(ctx: Context, value: Double) {
        val u = Store.profile(ctx)
        Store.saveProfile(ctx, u.copy(balance = value))
    }
}

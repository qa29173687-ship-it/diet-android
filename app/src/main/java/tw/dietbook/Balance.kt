package tw.dietbook

import android.content.Context

/**
 * 累計熱量帳戶：每日淨值持續疊加，不週清。
 * 活動消耗：有手動運動／鍛鍊時以手動為準，否則用 Health Connect，避免重複計算。
 */
object Balance {
    suspend fun refresh(ctx: Context, hc: HealthDay? = null): Pair<UserProfile, DaySnapshot> {
        val today = Days.today()
        var u = Store.profile(ctx)
        if (u.setupDone) {
            u = finalizeCatchUp(ctx, u, today)
        }
        val health = hc ?: if (u.hcEnabled) Health.readToday(ctx) else
            HealthDay(null, null, null, false, false, "")
        val (intake, protein) = Store.dayTotals(ctx, today)
        val tdee = Calc.tdee(u.bmr, u.activity)
        val manual = Store.manualBurn(ctx, today)
        val hcBurn = health.activeKcal
        val active = Calc.effectiveActive(manual, hcBurn)
        val burnSource = when {
            manual > 0.5 -> "manual"
            active != null -> "health_connect"
            else -> "tdee"
        }
        val net = Calc.dayNet(intake, u.bmr, active, tdee)
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
            manualBurn = manual,
            hcBurn = hcBurn,
            burnSource = burnSource,
        )
        Store.saveSnapshot(ctx, snap)
        return u to snap
    }

    private fun finalizeCatchUp(ctx: Context, profile: UserProfile, today: String): UserProfile {
        var u = profile
        val last = u.lastFinalizedDay
        val start = if (last.isEmpty()) {
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
                val manual = Store.manualBurn(ctx, day)
                val hc = existing?.hcBurn ?: existing?.activeBurn
                val active = Calc.effectiveActive(manual, hc)
                val net = Calc.dayNet(intake, u.bmr, active, tdee)
                DaySnapshot(
                    day, intake, protein, u.bmr, active, tdee, net,
                    existing?.steps, existing?.weightKg, true,
                    manual, hc,
                    when {
                        manual > 0.5 -> "manual"
                        active != null -> "health_connect"
                        else -> "tdee"
                    },
                )
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

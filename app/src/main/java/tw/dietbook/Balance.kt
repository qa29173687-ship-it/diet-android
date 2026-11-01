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
        val hcEffective = if (Store.hcIgnored(ctx, today)) null else hcBurn
        val active = Calc.effectiveActive(manual, hcEffective)
        val burnSource = when {
            Store.hcIgnored(ctx, today) && manual <= 0.5 -> "hc_ignored"
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
                val hcRaw = existing?.hcBurn ?: existing?.activeBurn
                val hc = if (Store.hcIgnored(ctx, day)) null else hcRaw
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


    /**
     * Recalculate a day's snapshot from current logs.
     * Today: live net only (profile.balance unchanged).
     * Past finalized day: adjust profile.balance by (newNet - oldNet).
     */
    fun recomputeDay(ctx: Context, day: String): DaySnapshot {
        val today = Days.today()
        val u = Store.profile(ctx)
        val existing = Store.snapshot(ctx, day)
        val (intake, protein) = Store.dayTotals(ctx, day)
        val tdee = Calc.tdee(u.bmr, u.activity)
        val manual = Store.manualBurn(ctx, day)
        val hcRaw = existing?.hcBurn
        val hc = if (Store.hcIgnored(ctx, day)) null else hcRaw
        val active = Calc.effectiveActive(manual, hc)
        val burnSource = when {
            Store.hcIgnored(ctx, day) && manual <= 0.5 -> "hc_ignored"
            manual > 0.5 -> "manual"
            active != null -> "health_connect"
            else -> "tdee"
        }
        val net = Calc.dayNet(intake, u.bmr, active, tdee)
        val isPast = day != today && !Days.parse(day).isAfter(Days.parse(today))
        val wasFinalized = existing?.finalized == true || (
            isPast && u.lastFinalizedDay.isNotEmpty() &&
                !Days.parse(day).isAfter(Days.parse(u.lastFinalizedDay))
        )
        val snap = DaySnapshot(
            day = day,
            intakeKcal = intake,
            protein = protein,
            bmr = existing?.bmr ?: u.bmr,
            activeBurn = active,
            tdeeFallback = tdee,
            net = net,
            steps = existing?.steps,
            weightKg = existing?.weightKg ?: u.weightKg,
            finalized = wasFinalized && day != today,
            manualBurn = manual,
            hcBurn = hcRaw,
            burnSource = burnSource,
        )
        if (day != today && wasFinalized && existing != null && existing.finalized) {
            val delta = net - existing.net
            if (kotlin.math.abs(delta) > 0.01) {
                adjustBalance(ctx, delta)
            }
        }
        Store.saveSnapshot(ctx, snap)
        return snap
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

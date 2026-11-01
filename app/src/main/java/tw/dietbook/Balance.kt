package tw.dietbook

import android.content.Context

/**
 * 累計熱量存錢筒（v2.4.4）：
 * 每日 delta = BMR + 運動／鍛鍊（或 HC）− 攝取；正＝缺口＝存款。
 * profile.balance = 已定案日（balanceStartDay‥lastFinalizedDay）之和；
 * 今日顯示 = balance + 今日 delta（今日永不寫入 balance，直到跨日 catch-up）。
 */
object Balance {
    private const val MODEL_FLAG = "balanceModelV244"

    suspend fun refresh(ctx: Context, hc: HealthDay? = null): Pair<UserProfile, DaySnapshot> {
        migrateIfNeeded(ctx)
        val today = Days.today()
        var u = Store.profile(ctx)
        if (u.setupDone) {
            u = ensureStartDay(ctx, u, today)
            u = finalizeCatchUp(ctx, u, today)
        }
        val health = hc ?: if (u.hcEnabled) Health.readToday(ctx) else
            HealthDay(null, null, null, false, false, "")
        val snap = buildSnapshot(ctx, u, today, health, finalized = false)
        Store.saveSnapshot(ctx, snap)
        return Store.profile(ctx) to snap
    }

    /** One-time flip: old dayNet was intake−BMR−burn; new is opposite. */
    fun migrateIfNeeded(ctx: Context) {
        val prefs = ctx.getSharedPreferences("dietbook", Context.MODE_PRIVATE)
        if (prefs.getBoolean(MODEL_FLAG, false)) return
        val u = Store.profile(ctx)
        if (u.setupDone) {
            Store.saveProfile(ctx, u.copy(balance = -u.balance))
            // Flip stored snapshot nets so recomputeDay deltas stay consistent
            val start = u.balanceStartDay.ifEmpty {
                if (u.lastFinalizedDay.isNotEmpty()) u.lastFinalizedDay else Days.today()
            }
            var d = Days.parse(start).minusDays(60) // best-effort recent window
            val end = Days.parse(Days.today())
            while (!d.isAfter(end)) {
                val day = d.toString()
                val snap = Store.snapshot(ctx, day)
                if (snap != null) {
                    Store.saveSnapshot(ctx, snap.copy(net = -snap.net))
                }
                d = d.plusDays(1)
            }
        }
        prefs.edit().putBoolean(MODEL_FLAG, true).apply()
    }

    private fun ensureStartDay(ctx: Context, profile: UserProfile, today: String): UserProfile {
        if (profile.balanceStartDay.isNotEmpty()) return profile
        // Upgrade path: do not invent months of missed BMR before lastFinalized.
        val start = when {
            profile.lastFinalizedDay.isNotEmpty() -> profile.lastFinalizedDay
            else -> today
        }
        val fixed = profile.copy(balanceStartDay = start)
        Store.saveProfile(ctx, fixed)
        return fixed
    }

    private fun finalizeCatchUp(ctx: Context, profile: UserProfile, today: String): UserProfile {
        val yesterday = Days.yesterdayOf(today)
        val ledger = BalanceLedger(
            balance = profile.balance,
            lastFinalizedDay = profile.lastFinalizedDay,
            balanceStartDay = profile.balanceStartDay.ifEmpty { profile.lastFinalizedDay },
        )
        val credited = ledger.catchUp(yesterday) { day ->
            val existing = Store.snapshot(ctx, day)
            if (existing != null && existing.finalized) {
                existing.net
            } else {
                val snap = buildSnapshot(ctx, profile, day, health = null, finalized = true)
                Store.saveSnapshot(ctx, snap)
                snap.net
            }
        }
        if (credited.isEmpty() &&
            ledger.balance == profile.balance &&
            ledger.lastFinalizedDay == profile.lastFinalizedDay
        ) {
            return profile
        }
        // Ensure already-finalized snapshots stay marked without re-adding
        credited.forEach { day ->
            val s = Store.snapshot(ctx, day)
            if (s != null && !s.finalized) Store.saveSnapshot(ctx, s.copy(finalized = true))
        }
        val u = profile.copy(
            balance = ledger.balance,
            lastFinalizedDay = ledger.lastFinalizedDay,
            balanceStartDay = ledger.balanceStartDay.ifEmpty { profile.balanceStartDay },
        )
        Store.saveProfile(ctx, u)
        return u
    }

    fun buildSnapshot(
        ctx: Context,
        u: UserProfile,
        day: String,
        health: HealthDay?,
        finalized: Boolean,
    ): DaySnapshot {
        val existing = Store.snapshot(ctx, day)
        val (intake, protein) = Store.dayTotals(ctx, day)
        val tdee = Calc.tdee(u.bmr, u.activity)
        val manual = Store.manualBurn(ctx, day)
        val hcBurn = health?.activeKcal ?: existing?.hcBurn
        val ignored = Store.hcIgnored(ctx, day)
        val burn = BalanceMath.burnKcal(manual, hcBurn, ignored)
        val active = if (burn > 0.5) burn else null
        val burnSource = when {
            ignored && manual <= 0.5 -> "hc_ignored"
            manual > 0.5 -> "manual"
            active != null -> "health_connect"
            else -> "bmr_only"
        }
        val net = BalanceMath.dayDelta(intake, existing?.bmr ?: u.bmr, burn)
        return DaySnapshot(
            day = day,
            intakeKcal = intake,
            protein = protein,
            bmr = existing?.bmr ?: u.bmr,
            activeBurn = active,
            tdeeFallback = tdee,
            net = net,
            steps = health?.steps ?: existing?.steps,
            weightKg = health?.weightKg ?: existing?.weightKg ?: u.weightKg,
            finalized = finalized,
            manualBurn = manual,
            hcBurn = hcBurn,
            burnSource = burnSource,
        )
    }

    /**
     * Recalculate a day's snapshot from current logs.
     * Today: live net only (profile.balance unchanged).
     * Past finalized day: adjust profile.balance by (newNet − oldNet) once.
     */
    fun recomputeDay(ctx: Context, day: String): DaySnapshot {
        migrateIfNeeded(ctx)
        val today = Days.today()
        val u = Store.profile(ctx)
        val existing = Store.snapshot(ctx, day)
        val snap = buildSnapshot(ctx, u, day, health = null, finalized = false)
        val isPast = day != today && !Days.parse(day).isAfter(Days.parse(today))
        val wasFinalized = existing?.finalized == true || (
            isPast && u.lastFinalizedDay.isNotEmpty() &&
                !Days.parse(day).isAfter(Days.parse(u.lastFinalizedDay)) &&
                (u.balanceStartDay.isEmpty() || !Days.parse(day).isBefore(Days.parse(u.balanceStartDay)))
            )
        val out = snap.copy(finalized = wasFinalized && day != today)
        if (day != today && wasFinalized && existing != null) {
            val ledger = BalanceLedger(balance = u.balance)
            ledger.adjustFinalizedDelta(existing.net, out.net)
            if (kotlin.math.abs(ledger.balance - u.balance) > 0.01) {
                Store.saveProfile(ctx, u.copy(balance = ledger.balance))
            }
        }
        Store.saveSnapshot(ctx, out)
        return out
    }

    fun adjustBalance(ctx: Context, delta: Double) {
        val u = Store.profile(ctx)
        Store.saveProfile(ctx, u.copy(balance = u.balance + delta))
    }

    fun setBalance(ctx: Context, value: Double) {
        val u = Store.profile(ctx)
        Store.saveProfile(ctx, u.copy(balance = value))
    }

    /** Settings reset: clear 總存款 window to today; keep food/exercise history. */
    fun resetSavings(ctx: Context) {
        migrateIfNeeded(ctx)
        val today = Days.today()
        val u = Store.profile(ctx)
        val ledger = BalanceLedger(
            balance = u.balance,
            lastFinalizedDay = u.lastFinalizedDay,
            balanceStartDay = u.balanceStartDay,
        )
        ledger.resetTo(today)
        Store.saveProfile(
            ctx,
            u.copy(
                balance = ledger.balance,
                lastFinalizedDay = ledger.lastFinalizedDay,
                balanceStartDay = ledger.balanceStartDay,
            ),
        )
        // Refresh today's live snapshot (BMR credited in live net, not balance)
        val snap = buildSnapshot(ctx, Store.profile(ctx), today, health = null, finalized = false)
        Store.saveSnapshot(ctx, snap)
    }

    fun liveTotal(u: UserProfile, todayNet: Double): Double = u.balance + todayNet
}

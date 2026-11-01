package tw.dietbook

import org.junit.Assert.*
import org.junit.Test
import java.time.ZoneId

class LogicTest {
    @Test fun mifflinMale() {
        assertEquals(1618, Calc.r0(Calc.mifflin("male", 70.0, 170.0, 30)))
    }

    /** BMR 1500 + burn 400 − intake 2000 = +100 savings. */
    @Test fun dayNetBmrPlusBurnMinusIntake() {
        assertEquals(-100.0, Calc.dayNet(2000.0, 1500.0, 400.0, 2300.0), 0.01)
    }

    /** No activity: burn=0 → BMR − intake (TDEE fallback ignored). */
    @Test fun dayNetNoBurnUsesBmrOnly() {
        assertEquals(-500.0, Calc.dayNet(2000.0, 1500.0, null, 2300.0), 0.01)
        assertEquals(1500.0, Calc.dayNet(0.0, 1500.0, null, 2300.0), 0.01)
    }

    @Test fun fatAndPayback() {
        assertEquals(1.0, Calc.fatKg(7700.0), 0.001)
        assertEquals(100.0, Calc.paybackPerDay(700.0, 7), 0.01)
        assertEquals(0.0, Calc.paybackPerDay(-100.0, 7), 0.01)
    }

    @Test fun effectiveActivePrefersManual() {
        assertEquals(300.0, Calc.effectiveActive(300.0, 500.0)!!, 0.01)
        assertEquals(500.0, Calc.effectiveActive(0.0, 500.0)!!, 0.01)
        assertNull(Calc.effectiveActive(0.0, null))
    }

    @Test fun metKcal() {
        assertEquals(122.5, Calc.metKcal(3.5, 70.0, 30), 0.01)
    }

    @Test fun partLevel() {
        assertEquals(0, Calc.partLevel(0))
        assertEquals(1, Calc.partLevel(15))
        assertEquals(2, Calc.partLevel(45))
        assertEquals(3, Calc.partLevel(120))
    }

    @Test fun labelPer100() {
        val d = LabelParser.parse("營養標示\n每100公克\n熱量 250 kcal\n蛋白質 12.5 g\n本包裝含 2 份\n每一份量 50 g")
        assertEquals("per100", d.detectedBasis)
        assertEquals(250.0, d.kcalPer100!!, 0.1)
        assertEquals(12.5, d.proteinPer100!!, 0.1)
        assertEquals(50.0, d.servingG!!, 0.1)
        assertEquals(2.0, d.servingsPerPack!!, 0.1)
    }

    @Test fun labelPerServingNormalize() {
        val (k, p) = LabelParser.normalizeToPer100("perServing", 120.0, 6.0, 40.0)
        assertEquals(300.0, k, 0.1)
        assertEquals(15.0, p, 0.1)
    }

    @Test fun csvParseQuoted() {
        val c = Tfnd.parseCsv("""A01,"a,b",c""")
        assertEquals(listOf("A01", "a,b", "c"), c)
    }

    @Test fun pigStagesNegativeIsFatter() {
        // v2.4.4: negative (surplus) → FAT; positive (deficit) → SLIM
        assertEquals(BodyStage.FAT3, PigSprites.fromBalance(-5000.0))
        assertEquals(BodyStage.FAT2, PigSprites.fromBalance(-1500.0))
        assertEquals(BodyStage.FAT1, PigSprites.fromBalance(-800.0))
        assertEquals(BodyStage.NORMAL, PigSprites.fromBalance(0.0))
        assertEquals(BodyStage.SLIM1, PigSprites.fromBalance(800.0))
        assertEquals(BodyStage.SLIM2, PigSprites.fromBalance(1500.0))
        assertEquals(PigMascotView.Stage.VERY_FAT, PigMascotView.stageFor(PigMascotView.scoreFor(-5000.0)))
        assertEquals(PigMascotView.Stage.NORMAL, PigMascotView.stageFor(PigMascotView.scoreFor(0.0)))
        assertEquals(PigMascotView.Stage.BUFF, PigMascotView.stageFor(PigMascotView.scoreFor(5000.0)))
        assertEquals(PigMascotView.Stage.FIT, PigMascotView.stageFor(PigMascotView.scoreFor(800.0)))
    }

    @Test fun praisePoolsNonEmpty() {
        assertTrue(Praise.cardioLine(200, 1).isNotBlank())
        assertTrue(Praise.strengthLine("手臂", 5).isNotBlank())
        assertTrue(Praise.strengthLine("腹", 0).isNotBlank())
    }

    @Test
    fun outfitsHolidayAndWeather() {
        val cal = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("Asia/Taipei"))
        cal.set(2026, 11, 24) // Dec 24
        assertEquals(OutfitId.SANTA, Outfits.holidayOutfit(cal))
        assertEquals(OutfitId.UMBRELLA, Outfits.weatherOutfit(WeatherSnap(61, 20.0, 1L)))
        assertEquals(OutfitId.ICE_CREAM, Outfits.weatherOutfit(WeatherSnap(0, 33.0, 1L)))
        val kg = kotlin.math.floor(8000.0 / 7700.0).toInt()
        assertEquals(1, kg)
    }

    @Test fun dailyAdviceRules() {
        val a = DailyAdvice.of(intakeKcal = 900.0, bmr = 1320.0, balanceKcal = 500.0)
        assertEquals(AdviceKind.BELOW_BMR, a.kind)
        // intake ≥ BMR but balance negative (進帳) → 注意健康
        val b = DailyAdvice.of(intakeKcal = 1800.0, bmr = 1320.0, balanceKcal = -400.0)
        assertEquals(AdviceKind.SURPLUS_CARE, b.kind)
        // intake ≥ BMR and balance ≥ 0 → praise
        val c = DailyAdvice.of(intakeKcal = 1500.0, bmr = 1320.0, balanceKcal = 300.0)
        assertEquals(AdviceKind.PRAISE, c.kind)
        assertTrue(c.title.contains("很棒"))
    }

    // ---- BalanceLedger / 總存款 model ----

    @Test fun singleDayLiveTotal() {
        val bmr = 1500.0
        val intake = 1800.0
        val burn = 0.0
        val delta = BalanceMath.dayDelta(intake, bmr, burn) // -300
        assertEquals(-300.0, delta, 0.01)
        val ledger = BalanceLedger(balance = 0.0, lastFinalizedDay = "2026-10-31", balanceStartDay = "2026-11-01")
        // today not finalized
        assertEquals(-300.0, ledger.liveTotal(delta), 0.01)
        assertEquals(0.0, ledger.balance, 0.01)
    }

    @Test fun multipleOpensDoNotDoubleCreditBmr() {
        val ledger = BalanceLedger(0.0, lastFinalizedDay = "2026-10-31", balanceStartDay = "2026-10-28")
        val delta = { _: String -> 1500.0 } // pure BMR day
        val first = ledger.catchUp("2026-10-31", delta)
        assertTrue(first.isEmpty()) // already finalized through yesterday
        val balAfter = ledger.balance
        val second = ledger.catchUp("2026-10-31", delta)
        assertTrue(second.isEmpty())
        assertEquals(balAfter, ledger.balance, 0.01)
    }

    @Test fun deleteEntryAdjustsFinalizedDay() {
        val ledger = BalanceLedger(balance = 200.0, lastFinalizedDay = "2026-10-30", balanceStartDay = "2026-10-28")
        // old: BMR1500 +0 −1200 = +300; after delete food 400 → intake 800 → delta +700
        ledger.adjustFinalizedDelta(oldDelta = 300.0, newDelta = 700.0)
        assertEquals(600.0, ledger.balance, 0.01)
    }

    @Test fun exerciseAddsToSavings() {
        val bmr = 1500.0
        val intake = 1500.0
        val noEx = BalanceMath.dayDelta(intake, bmr, 0.0)
        val withEx = BalanceMath.dayDelta(intake, bmr, 400.0)
        assertEquals(0.0, noEx, 0.01)
        assertEquals(400.0, withEx, 0.01)
    }

    @Test fun missedDaysStillGetBmr() {
        val ledger = BalanceLedger(0.0, lastFinalizedDay = "2026-10-28", balanceStartDay = "2026-10-28")
        val credited = ledger.catchUp("2026-10-31") { _ -> 1500.0 }
        assertEquals(listOf("2026-10-29", "2026-10-30", "2026-10-31"), credited)
        assertEquals(4500.0, ledger.balance, 0.01)
        assertEquals("2026-10-31", ledger.lastFinalizedDay)
    }

    @Test fun resetClearsBalanceKeepsClockAtToday() {
        val ledger = BalanceLedger(4500.0, lastFinalizedDay = "2026-10-31", balanceStartDay = "2026-10-01")
        ledger.resetTo("2026-11-01")
        assertEquals(0.0, ledger.balance, 0.01)
        assertEquals("2026-11-01", ledger.balanceStartDay)
        assertEquals("2026-10-31", ledger.lastFinalizedDay)
        // catch-up must not re-credit yesterday after reset
        val again = ledger.catchUp("2026-10-31") { _ -> 1500.0 }
        assertTrue(again.isEmpty())
        // today's live BMR still available
        assertEquals(1500.0, ledger.liveTotal(1500.0), 0.01)
    }

    @Test fun asiaTaipeiZoneAndMidnightBoundary() {
        assertEquals(ZoneId.of("Asia/Taipei"), Days.zone)
        assertEquals("2026-10-31", Days.yesterdayOf("2026-11-01"))
        // Crossing Taipei midnight: lastFinalized=Oct31, "today"=Nov1 → catchUp(Oct31) no-op;
        // next day when today=Nov2, catchUp(Nov1) credits Nov1 once.
        val ledger = BalanceLedger(0.0, "2026-10-31", "2026-10-31")
        assertTrue(ledger.catchUp("2026-10-31") { 1500.0 }.isEmpty())
        val credited = ledger.catchUp("2026-11-01") { day ->
            assertEquals("2026-11-01", day)
            1500.0
        }
        assertEquals(listOf("2026-11-01"), credited)
        assertEquals(1500.0, ledger.balance, 0.01)
        // second open same calendar day: no double
        assertTrue(ledger.catchUp("2026-11-01") { error("should not") }.isEmpty())
        assertEquals(1500.0, ledger.balance, 0.01)
    }

    /** Worked numeric example for release notes. */
    @Test fun workedNumericExample() {
        val bmr = 1500.0
        // Day1: eat 1800, no exercise → delta = 1500-1800 = -300
        val d1 = BalanceMath.dayDelta(1800.0, bmr, 0.0)
        // Day2: eat 1200, burn 300 → 1500+300-1200 = +600
        val d2 = BalanceMath.dayDelta(1200.0, bmr, 300.0)
        // Day3 missed open: 1500+0-0 = +1500
        val d3 = BalanceMath.dayDelta(0.0, bmr, 0.0)
        val ledger = BalanceLedger(0.0, "2026-10-28", "2026-10-29")
        ledger.catchUp("2026-10-31") { day ->
            when (day) {
                "2026-10-29" -> d1
                "2026-10-30" -> d2
                "2026-10-31" -> d3
                else -> error(day)
            }
        }
        // total = -300 + 600 + 1500 = 1800
        assertEquals(1800.0, ledger.balance, 0.01)
        // Today Nov1 live: eat 1600, burn 0 → +live = 1800 + (1500-1600) = 1700
        assertEquals(1700.0, ledger.liveTotal(BalanceMath.dayDelta(1600.0, bmr, 0.0)), 0.01)
    }
}

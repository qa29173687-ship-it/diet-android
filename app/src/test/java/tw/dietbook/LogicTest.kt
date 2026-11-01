package tw.dietbook

import org.junit.Assert.*
import org.junit.Test

class LogicTest {
    @Test fun mifflinMale() {
        assertEquals(1618, Calc.r0(Calc.mifflin("male", 70.0, 170.0, 30)))
    }

    @Test fun dayNetUsesBmrPlusActive() {
        assertEquals(100.0, Calc.dayNet(2000.0, 1500.0, 400.0, 2300.0), 0.01)
    }

    @Test fun dayNetFallbackTdee() {
        assertEquals(-300.0, Calc.dayNet(2000.0, 1500.0, null, 2300.0), 0.01)
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
        // 3.5 MET * 70kg * 0.5h = 122.5
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

    @Test fun pigStages() {
        assertEquals(PigMascotView.Stage.VERY_FAT, PigMascotView.stageFor(PigMascotView.scoreFor(5000.0)))
        assertEquals(PigMascotView.Stage.CHUBBY, PigMascotView.stageFor(PigMascotView.scoreFor(1500.0)))
        assertEquals(PigMascotView.Stage.NORMAL, PigMascotView.stageFor(PigMascotView.scoreFor(0.0)))
        assertEquals(PigMascotView.Stage.FIT, PigMascotView.stageFor(PigMascotView.scoreFor(-1500.0)))
        assertEquals(PigMascotView.Stage.BUFF, PigMascotView.stageFor(PigMascotView.scoreFor(-5000.0)))
    }

    @Test fun praisePoolsNonEmpty() {
        assertTrue(Praise.cardioLine(200, 1).isNotBlank())
        assertTrue(Praise.strengthLine("手臂", 5).isNotBlank())
        assertTrue(Praise.strengthLine("腹", 0).isNotBlank())
    }
}

package tw.dietbook

import org.junit.Assert.*
import org.junit.Test

class LogicTest {
    @Test fun mifflinMale() {
        // 70kg 170cm 30y male ≈ 1648.8
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
}

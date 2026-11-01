package tw.dietbook

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class PortionsTest {
    @Test
    fun loadsAtLeast200AndSweetPotatoPortion() {
        val ctx = RuntimeEnvironment.getApplication()
        val n = Portions.count(ctx)
        assertTrue("expected >=200 common foods, got $n", n >= 200)
        val digua = Portions.search(ctx, "地瓜").first()
        assertEquals("地瓜", digua.name)
        val one = digua.units.first { it.unit == "顆" }
        assertEquals(200.0, one.grams, 0.1)
        val kcal = digua.kcalPer100 * one.grams / 100.0
        assertTrue("地瓜一顆應約 200+ kcal, got $kcal", kcal in 200.0..260.0)
        assertTrue(Portions.search(ctx, "番薯").any { it.name == "地瓜" })
        assertTrue(Portions.search(ctx, "雞蛋").isNotEmpty())
        assertTrue(Portions.search(ctx, "白飯").isNotEmpty())
    }
}

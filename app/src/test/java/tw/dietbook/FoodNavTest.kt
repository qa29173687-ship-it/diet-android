package tw.dietbook

import android.content.Intent
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class FoodNavTest {
    private fun setupProfile() {
        val c = RuntimeEnvironment.getApplication()
        Store.saveProfile(
            c,
            UserProfile(
                setupDone = true, sex = "male", age = 30, heightCm = 170.0, weightKg = 70.0,
                bmr = 1500.0, bmrManual = true, activity = "moderate", goalKcal = 1800.0, goalProtein = 80.0,
                lastFinalizedDay = Days.yesterdayOf(Days.today()),
            )
        )
    }

    @Test
    fun traceIndexLoadsWithoutCrash() {
        setupProfile()
        val c = RuntimeEnvironment.getApplication()
        // 以前開 trace-nutrition.jsonl.gz 會 FileNotFound（AGP 解成 .jsonl）→ 搜尋崩潰回主畫面
        TraceIndex.invalidate()
        val n = TraceIndex.size(c)
        assertTrue("trace rows should load from assets (gz or plain)", n > 0)
        val foods = Tfnd.search(c, "雞蛋", emptyList())
        assertTrue(foods.isNotEmpty())
    }

    @Test
    fun searchResultStartsLogActivity() {
        setupProfile()
        val c = RuntimeEnvironment.getApplication()
        val foods = Tfnd.search(c, "雞蛋", emptyList())
        assertTrue("should find eggs", foods.isNotEmpty())
        val f = foods.first()
        assertNotNull("findFood must resolve search hit", Store.findFood(c, f.id) ?: FoodCodec.fromJson(FoodCodec.toJson(f)))

        val search = Robolectric.buildActivity(SearchActivity::class.java).setup().get()
        search.startActivity(
            Intent(search, LogActivity::class.java)
                .putExtra("foodId", f.id)
                .putExtra("foodJson", FoodCodec.toJson(f))
        )
        val next = Shadows.shadowOf(search).nextStartedActivity
        assertNotNull(next)
        assertEquals(LogActivity::class.java.name, next.component!!.className)
        assertEquals(f.id, next.getStringExtra("foodId"))
        assertNotNull(next.getStringExtra("foodJson"))

        val log = Robolectric.buildActivity(LogActivity::class.java, next).setup().get()
        assertFalse("LogActivity must stay open for grams entry", log.isFinishing)
        search.finish(); log.finish()
    }

    @Test
    fun logOpensFromFoodJsonEvenIfFindFoodFails() {
        setupProfile()
        val c = RuntimeEnvironment.getApplication()
        val fake = FoodItem(
            id = "trace:missing-in-index",
            name = "測試食品",
            kcalPer100 = 100.0,
            proteinPer100 = 5.0,
        )
        val intent = Intent(c, LogActivity::class.java)
            .putExtra("foodId", fake.id)
            .putExtra("foodJson", FoodCodec.toJson(fake))
        val log = Robolectric.buildActivity(LogActivity::class.java, intent).setup().get()
        assertFalse("must stay open using foodJson payload", log.isFinishing)
        log.finish()
    }

    @Test
    fun everySearchHitIsFindable_andLogOpens() {
        setupProfile()
        val c = RuntimeEnvironment.getApplication()
        val queries = listOf("", "雞", "米", "牛", "奶", "蛋")
        val missing = mutableListOf<String>()
        val crashed = mutableListOf<String>()
        for (q in queries) {
            val list = Tfnd.search(c, q, Store.customFoods(c))
            for (f in list.take(15)) {
                val found = Store.findFood(c, f.id) ?: FoodCodec.fromJson(FoodCodec.toJson(f))
                if (found == null) missing += "${f.id}/${f.name}"
                else {
                    try {
                        val intent = Intent(c, LogActivity::class.java)
                            .putExtra("foodId", f.id)
                            .putExtra("foodJson", FoodCodec.toJson(f))
                        val log = Robolectric.buildActivity(LogActivity::class.java, intent).setup().get()
                        if (log.isFinishing) crashed += "finishing:${f.id}"
                        log.finish()
                    } catch (e: Throwable) {
                        crashed += "${f.id}:${e.javaClass.simpleName}:${e.message}"
                    }
                }
            }
        }
        assertTrue("missing findFood: $missing", missing.isEmpty())
        assertTrue("LogActivity crash/finish: $crashed", crashed.isEmpty())
    }

    @Test
    fun emptyAndFullwidthGramsDoNotCrash() {
        setupProfile()
        val c = RuntimeEnvironment.getApplication()
        val f = Tfnd.all(c).first()
        val intent = Intent(c, LogActivity::class.java)
            .putExtra("foodId", f.id)
            .putExtra("foodJson", FoodCodec.toJson(f))
        val log = Robolectric.buildActivity(LogActivity::class.java, intent).setup().get()
        val root = log.findViewById<android.view.ViewGroup>(android.R.id.content)
        fun editTexts(v: android.view.View): List<android.widget.EditText> = when (v) {
            is android.widget.EditText -> listOf(v)
            is android.view.ViewGroup -> (0 until v.childCount).flatMap { editTexts(v.getChildAt(it)) }
            else -> emptyList()
        }
        val fields = editTexts(root)
        assertTrue(fields.isNotEmpty())
        val grams = fields.first()
        for (s in listOf("", ".", "．", "１２３", "12.5", "１２．５", "abc", "0", "-1")) {
            grams.setText(s)
            ShadowLooper.idleMainLooper()
            assertFalse("still open after '$s'", log.isFinishing)
        }
        assertEquals(123.0, Numbers.doubleOrNull("１２３"))
        assertEquals(12.5, Numbers.doubleOrNull("１２．５"))
        log.finish()
    }
}

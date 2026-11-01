package tw.dietbook

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowLooper
import java.io.File

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], qualifiers = "w400dp-h860dp-xhdpi")
class PreviewTest {
    private val out = File(System.getProperty("previewDir") ?: "build/previews").apply { mkdirs() }

    private fun shot(a: Activity, name: String) {
        ShadowLooper.idleMainLooper()
        val v: View = a.window.decorView
        val w = 800; val h = 1720
        v.measure(
            View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY),
        )
        v.layout(0, 0, w, h)
        // advance pig wander a bit
        repeat(8) {
            ShadowLooper.idleMainLooper()
            Thread.sleep(40)
        }
        val b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        v.draw(Canvas(b))
        File(out, "$name.png").outputStream().use { b.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun baseProfile(balance: Double) = UserProfile(
        setupDone = true, sex = "male", age = 32,
        heightCm = 175.0, weightKg = 72.0, bmr = 1680.0, bmrManual = true,
        activity = "moderate", goalKcal = 2200.0, goalProtein = 110.0,
        balance = balance, lastFinalizedDay = Days.yesterdayOf(Days.today()),
        paybackDays = 7, hcEnabled = false, remindPayback = false,
        streakDays = 4, lastActiveDay = Days.yesterdayOf(Days.today()),
        badges = setOf("first_cardio", "streak3"),
    )

    private fun seedLogs() {
        val c = RuntimeEnvironment.getApplication()
        Store.upsertCustom(
            c,
            FoodItem(
                id = "custom:preview", name = "7-11 御飯糰（範例）", category = "自訂",
                kcalPer100 = 180.0, proteinPer100 = 5.5, custom = true,
                servingG = 110.0, servingsPerPack = 1.0, basis = "per100",
                barcode = "4710123456789",
            )
        )
        Store.setDataMeta(
            c, tfndVer = "bundled", traceVer = "bundled-v1",
            lastChecked = System.currentTimeMillis() - 3600_000, lastUpdated = 0L,
        )
        Store.touchFeed(c)
        val day = Days.today()
        Store.saveLogs(
            c, day,
            listOf(
                FoodLog(1, day, "tfnd:demo1", "白飯", 150.0, 174.0, 3.6, System.currentTimeMillis() - 3600_000),
                FoodLog(2, day, "custom:preview", "7-11 御飯糰（範例）", 110.0, 198.0, 6.1, System.currentTimeMillis() - 1800_000),
                FoodLog(3, day, "tfnd:demo2", "雞蛋", 50.0, 72.0, 6.3, System.currentTimeMillis() - 600_000),
            )
        )
        Store.saveCardio(
            c, day,
            listOf(CardioLog(1, day, "walking", "走路", 40, 180.0, 3.5, System.currentTimeMillis() - 900_000))
        )
        Store.saveStrength(
            c, day,
            listOf(StrengthLog(1, day, "手臂", "二頭彎舉", 3, 25, 120.0, System.currentTimeMillis() - 500_000))
        )
        Store.addPartMinutes(c, "手臂", 40)
        Store.addPartMinutes(c, "胸", 20)
        Store.addPartMinutes(c, "腹", 50)
        Store.addPartMinutes(c, "腿", 30)
    }

    private fun homeShot(liveBalance: Double, name: String) {
        val c = RuntimeEnvironment.getApplication()
        val day = Days.today()
        val tdee = Calc.tdee(1680.0, "moderate")
        Store.saveProfile(c, baseProfile(liveBalance))
        Store.setDataMeta(c, tfndVer = "bundled", traceVer = "bundled-v1",
            lastChecked = System.currentTimeMillis() - 3600_000, lastUpdated = 0L)
        Store.touchFeed(c)
        Store.saveLogs(
            c, day,
            listOf(FoodLog(1, day, "tfnd:fill", "維持熱量（預覽）", 100.0, tdee, 20.0, System.currentTimeMillis())),
        )
        Store.saveCardio(c, day, emptyList())
        Store.saveStrength(c, day, emptyList())
        val main = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        ShadowLooper.idleMainLooper()
        Thread.sleep(400)
        ShadowLooper.idleMainLooper()
        shot(main, name)
        main.finish()
        ShadowLooper.idleMainLooper()
    }

    @Test fun render() {
        homeShot(4800.0, "1a-主畫面-胖豬")
        homeShot(0.0, "1b-主畫面-普通豬")
        homeShot(-4800.0, "1c-主畫面-壯豬")
        homeShot(1850.0, "1-主畫面")

        val c = RuntimeEnvironment.getApplication()
        Store.saveProfile(c, baseProfile(1850.0))
        seedLogs()

        val cardio = Robolectric.buildActivity(CardioActivity::class.java).setup().get()
        shot(cardio, "2-運動")
        cardio.finish()

        val strength = Robolectric.buildActivity(StrengthActivity::class.java).setup().get()
        shot(strength, "3-鍛鍊")
        strength.finish()

        val search = Robolectric.buildActivity(SearchActivity::class.java).setup().get()
        shot(search, "4-搜尋吃")
        search.finish()

        val bal = Robolectric.buildActivity(BalanceActivity::class.java).setup().get()
        Thread.sleep(250); ShadowLooper.idleMainLooper()
        shot(bal, "5-小豬存摺")
        bal.finish()

        val settings = Robolectric.buildActivity(SettingsActivity::class.java).setup().get()
        Thread.sleep(100); ShadowLooper.idleMainLooper()
        shot(settings, "6-設定")
        settings.finish()

        val setup = Robolectric.buildActivity(SetupActivity::class.java).setup().get()
        shot(setup, "7-首次設定")
        setup.finish()
    }
}

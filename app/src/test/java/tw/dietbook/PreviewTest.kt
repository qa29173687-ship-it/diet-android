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
            c,
            tfndVer = "bundled",
            traceVer = "bundled-v1",
            lastChecked = System.currentTimeMillis() - 3600_000,
            lastUpdated = 0L,
        )
        val day = Days.today()
        Store.saveLogs(
            c, day,
            listOf(
                FoodLog(1, day, "tfnd:demo1", "白飯", 150.0, 174.0, 3.6, System.currentTimeMillis() - 3600_000),
                FoodLog(2, day, "custom:preview", "7-11 御飯糰（範例）", 110.0, 198.0, 6.1, System.currentTimeMillis() - 1800_000),
                FoodLog(3, day, "tfnd:demo2", "雞蛋", 50.0, 72.0, 6.3, System.currentTimeMillis() - 600_000),
            )
        )
    }

    private fun homeShot(liveBalance: Double, name: String) {
        val c = RuntimeEnvironment.getApplication()
        // Make today's net ≈ 0 so the pig reflects liveBalance cleanly
        val day = Days.today()
        val tdee = Calc.tdee(1680.0, "moderate")
        Store.saveProfile(c, baseProfile(liveBalance))
        Store.setDataMeta(c, tfndVer = "bundled", traceVer = "bundled-v1",
            lastChecked = System.currentTimeMillis() - 3600_000, lastUpdated = 0L)
        Store.saveLogs(
            c, day,
            listOf(
                FoodLog(1, day, "tfnd:fill", "維持熱量（預覽）", 100.0, tdee, 20.0, System.currentTimeMillis()),
            )
        )
        val main = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        ShadowLooper.idleMainLooper()
        Thread.sleep(350)
        ShadowLooper.idleMainLooper()
        shot(main, name)
        main.finish()
        ShadowLooper.idleMainLooper()
    }

    @Test fun render() {
        // Fat / normal / buff home variants (cumulative balance drives pig)
        homeShot(4800.0, "1a-主畫面-胖豬")
        homeShot(0.0, "1b-主畫面-普通豬")
        homeShot(-4800.0, "1c-主畫面-壯豬")
        homeShot(1850.0, "1-主畫面")

        val c = RuntimeEnvironment.getApplication()
        Store.saveProfile(c, baseProfile(1850.0))
        seedLogs()

        val search = Robolectric.buildActivity(SearchActivity::class.java).setup().get()
        shot(search, "2-搜尋")

        val custom = Robolectric.buildActivity(CustomFoodActivity::class.java).setup().get()
        shot(custom, "3-自訂食物")

        val bal = Robolectric.buildActivity(BalanceActivity::class.java).setup().get()
        Thread.sleep(250); ShadowLooper.idleMainLooper()
        shot(bal, "4-小豬存摺")

        val settings = Robolectric.buildActivity(SettingsActivity::class.java).setup().get()
        Thread.sleep(100); ShadowLooper.idleMainLooper()
        shot(settings, "5-設定")

        val setup = Robolectric.buildActivity(SetupActivity::class.java).setup().get()
        shot(setup, "6-首次設定")

        val log = Robolectric.buildActivity(
            LogActivity::class.java,
            Intent(c, LogActivity::class.java).putExtra("foodId", "custom:preview"),
        ).setup().get()
        shot(log, "7-記錄份量")

        val confirm = Robolectric.buildActivity(
            CustomFoodActivity::class.java,
            Intent(c, CustomFoodActivity::class.java)
                .putExtra("confirmBarcode", true)
                .putExtra("barcode", "3017620422003")
                .putExtra("prefillName", "Nutella")
                .putExtra("prefillSource", "Open Food Facts")
                .putExtra("basis", "per100")
                .putExtra("kcalPer100", 539.0)
                .putExtra("proteinPer100", 6.3)
                .putExtra("note", "來源：Open Food Facts"),
        ).setup().get()
        shot(confirm, "8-條碼確認")

        val barcode = Robolectric.buildActivity(BarcodeScanActivity::class.java).setup().get()
        shot(barcode, "9-掃條碼")
    }
}

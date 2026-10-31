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

    private fun seed() {
        val c = RuntimeEnvironment.getApplication()
        Store.saveProfile(
            c,
            UserProfile(
                setupDone = true, sex = "male", age = 32,
                heightCm = 175.0, weightKg = 72.0, bmr = 1680.0, bmrManual = true,
                activity = "moderate", goalKcal = 2200.0, goalProtein = 110.0,
                balance = 1850.0, lastFinalizedDay = Days.yesterdayOf(Days.today()),
                paybackDays = 7, hcEnabled = false, remindPayback = false,
            )
        )
        Store.upsertCustom(
            c,
            FoodItem(
                id = "custom:preview", name = "7-11 御飯糰（範例）", category = "自訂",
                kcalPer100 = 180.0, proteinPer100 = 5.5, custom = true,
                servingG = 110.0, servingsPerPack = 1.0, basis = "per100",
            )
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

    @Test fun render() {
        seed()
        val c = RuntimeEnvironment.getApplication()
        val main = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        ShadowLooper.idleMainLooper()
        // wait for coroutine refresh
        Thread.sleep(200)
        ShadowLooper.idleMainLooper()
        shot(main, "1-主畫面")

        val search = Robolectric.buildActivity(SearchActivity::class.java).setup().get()
        shot(search, "2-搜尋")

        val custom = Robolectric.buildActivity(CustomFoodActivity::class.java).setup().get()
        shot(custom, "3-自訂食物")

        val bal = Robolectric.buildActivity(BalanceActivity::class.java).setup().get()
        Thread.sleep(200); ShadowLooper.idleMainLooper()
        shot(bal, "4-熱量帳戶")

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
    }
}

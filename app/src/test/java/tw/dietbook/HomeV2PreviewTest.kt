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

/**
 * Renders home v2 with rule-1 advice (intake &lt; BMR) at 1080×2340 → demo/home-v2-advice.png
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], qualifiers = "w360dp-h780dp-xxhdpi")
class HomeV2PreviewTest {

    @Test
    fun renderHomeV2AdviceBelowBmr() {
        val c = RuntimeEnvironment.getApplication()
        Store.saveProfile(
            c,
            UserProfile(
                setupDone = true, sex = "female", age = 28,
                heightCm = 162.0, weightKg = 54.0, bmr = 1320.0, bmrManual = true,
                activity = "moderate", goalKcal = 1800.0, goalProtein = 90.0,
                balance = -900.0, lastFinalizedDay = Days.yesterdayOf(Days.today()),
                paybackDays = 7, hcEnabled = false, remindPayback = false,
                streakDays = 5, lastActiveDay = Days.yesterdayOf(Days.today()),
                badges = setOf("first_cardio", "streak3"),
            ),
        )
        Store.touchFeed(c)

        val intent = Intent(c, HomeV2DemoActivity::class.java)
            .putExtra(HomeV2DemoActivity.EXTRA_SAMPLE, true)
            .putExtra(HomeV2DemoActivity.EXTRA_INTAKE, 980)
            .putExtra(HomeV2DemoActivity.EXTRA_PROTEIN, 42)
            .putExtra(HomeV2DemoActivity.EXTRA_BURN, 180)
            .putExtra(HomeV2DemoActivity.EXTRA_STRENGTH_MIN, 20)
            .putExtra(HomeV2DemoActivity.EXTRA_BALANCE, -620.0)
            .putExtra(HomeV2DemoActivity.EXTRA_BMR, 1320.0)
            .putExtra(HomeV2DemoActivity.EXTRA_TIP, "目標：每天溫柔缺口，但別低於 BMR")
        val activity = Robolectric.buildActivity(HomeV2DemoActivity::class.java, intent)
            .setup()
            .get()

        repeat(12) {
            ShadowLooper.idleMainLooper()
            Thread.sleep(50)
        }

        val outDir = File(
            System.getProperty("demoDir")
                ?: "/workspace/diet-android/demo",
        ).apply { mkdirs() }

        shot(activity, File(outDir, "home-v2-advice.png"), 1080, 2340)
        activity.finish()
        ShadowLooper.idleMainLooper()

        require(File(outDir, "home-v2-advice.png").isFile && File(outDir, "home-v2-advice.png").length() > 10_000) {
            "home-v2-advice.png missing or too small in $outDir"
        }
    }

    private fun shot(a: Activity, file: File, w: Int, h: Int) {
        ShadowLooper.idleMainLooper()
        val v: View = a.window.decorView
        v.measure(
            View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY),
        )
        v.layout(0, 0, w, h)
        repeat(6) {
            ShadowLooper.idleMainLooper()
            Thread.sleep(40)
        }
        v.measure(
            View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY),
        )
        v.layout(0, 0, w, h)
        ShadowLooper.idleMainLooper()
        val b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        v.draw(Canvas(b))
        file.parentFile?.mkdirs()
        file.outputStream().use { b.compress(Bitmap.CompressFormat.PNG, 100, it) }
        println("Wrote ${file.absolutePath} (${file.length()} bytes)")
    }
}

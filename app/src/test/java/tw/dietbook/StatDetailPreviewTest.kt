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

/** Renders intake / protein detail panels → demo/detail-*.png */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], qualifiers = "w360dp-h780dp-xxhdpi")
class StatDetailPreviewTest {

    @Test
    fun renderIntakeAndProtein() {
        val c = RuntimeEnvironment.getApplication()
        Store.saveProfile(
            c,
            UserProfile(
                setupDone = true, sex = "female", age = 28,
                heightCm = 162.0, weightKg = 54.0, bmr = 1320.0, bmrManual = true,
                activity = "moderate", goalKcal = 1800.0, goalProtein = 90.0,
                balance = -620.0, lastFinalizedDay = Days.yesterdayOf(Days.today()),
                paybackDays = 7, hcEnabled = false, remindPayback = false,
                streakDays = 5, lastActiveDay = Days.today(),
                badges = setOf("first_cardio", "streak3"),
            ),
        )
        val outDir = File(System.getProperty("demoDir") ?: "/workspace/diet-android/demo").apply { mkdirs() }
        shotKind("intake", File(outDir, "detail-intake.png"))
        shotKind("protein", File(outDir, "detail-protein.png"))
        require(File(outDir, "detail-intake.png").length() > 8_000)
        require(File(outDir, "detail-protein.png").length() > 8_000)
    }

    private fun shotKind(kind: String, file: File) {
        val c = RuntimeEnvironment.getApplication()
        val intent = Intent(c, StatDetailDemoActivity::class.java)
            .putExtra(StatDetailDemoActivity.EXTRA_KIND, kind)
            .putExtra(StatDetailDemoActivity.EXTRA_SAMPLE, true)
        val activity = Robolectric.buildActivity(StatDetailDemoActivity::class.java, intent)
            .setup()
            .get()
        repeat(12) {
            ShadowLooper.idleMainLooper()
            Thread.sleep(40)
        }
        shot(activity, file, 1080, 2340)
        activity.finish()
        ShadowLooper.idleMainLooper()
    }

    private fun shot(a: Activity, file: File, w: Int, h: Int) {
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

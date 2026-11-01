package tw.dietbook

import android.graphics.Bitmap
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * 產出小豬動畫影格，供 ffmpeg 合成 demo/pig-demo.mp4。
 * 執行：./gradlew testDebugUnitTest --tests tw.dietbook.DemoPigTest
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], qualifiers = "w400dp-h860dp-xhdpi")
class DemoPigTest {
    @Test
    fun exportFrames() {
        val c = RuntimeEnvironment.getApplication()
        Store.saveProfile(
            c,
            UserProfile(
                setupDone = true, sex = "male", age = 30, heightCm = 170.0, weightKg = 70.0,
                bmr = 1500.0, bmrManual = true, activity = "moderate",
                goalKcal = 1800.0, goalProtein = 80.0, streakDays = 8,
                lastFinalizedDay = Days.yesterdayOf(Days.today()),
            )
        )
        Store.setUnlockedOutfits(c, setOf(OutfitId.SPORTS, OutfitId.BIB, OutfitId.CHEF))
        Store.setSelectedOutfit(c, OutfitId.SPORTS)

        val out = File(System.getProperty("demoFramesDir") ?: "demo/frames").apply { mkdirs() }
        // clear old
        out.listFiles()?.forEach { it.delete() }

        data class Clip(val scene: CuteScene, val label: String, val frames: Int, val kcal: Double = 420.0, val protein: Double = 90.0)
        val clips = listOf(
            Clip(CuteScene.MORNING, "早安～", 18),
            Clip(CuteScene.BMR_COIN, "基礎代謝入帳", 22, kcal = 1500.0),
            Clip(CuteScene.FOOD_CATCH, "好好吃～ 雞蛋", 28, kcal = 150.0),
            Clip(CuteScene.PROTEIN_POWER, "蛋白質達標！", 22, protein = 95.0),
            Clip(CuteScene.HANDS_POUR, "運動倒幣", 18, kcal = 220.0),
            Clip(CuteScene.LIFT, "重訓舉重", 24, kcal = 180.0),
            Clip(CuteScene.TREADMILL, "有氧跑步機", 24, kcal = 220.0),
            Clip(CuteScene.SURPLUS_BELLY, "有點撐…", 18, kcal = 2400.0),
            Clip(CuteScene.FIREWORKS_KG, "減掉 1 公斤！", 24),
            Clip(CuteScene.POSING, "連續打卡秀", 20),
        )

        var idx = 0
        for (clip in clips) {
            for (i in 0 until clip.frames) {
                val p = if (clip.frames <= 1) 0.5f else i.toFloat() / (clip.frames - 1)
                val bmp = CuteFx.capture(
                    c, clip.scene, p,
                    w = 800, h = 1200,
                    kcal = clip.kcal, protein = clip.protein, label = clip.label,
                )
                val name = "frame_%04d.png".format(idx++)
                File(out, name).outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
                bmp.recycle()
            }
            // hold last frame a beat
            for (h in 0 until 6) {
                val bmp = CuteFx.capture(
                    c, clip.scene, 1f,
                    w = 800, h = 1200,
                    kcal = clip.kcal, protein = clip.protein, label = clip.label,
                )
                File(out, "frame_%04d.png".format(idx++)).outputStream().use {
                    bmp.compress(Bitmap.CompressFormat.PNG, 100, it)
                }
                bmp.recycle()
            }
        }
        File(out, "COUNT.txt").writeText(idx.toString())
    }
}

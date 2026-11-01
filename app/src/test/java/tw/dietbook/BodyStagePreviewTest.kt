package tw.dietbook

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class BodyStagePreviewTest {
    @Test
    fun renderBodyStagesAndActions() {
        val ctx = RuntimeEnvironment.getApplication()
        val out = File(System.getProperty("previewDir") ?: "previews").apply { mkdirs() }
        val stages = BodyStage.values()
        val cellW = 280
        val cellH = 320
        val cols = 3
        val rows = 2
        val sheet = Bitmap.createBitmap(cols * cellW, rows * cellH, Bitmap.Config.ARGB_8888)
        val c = Canvas(sheet)
        c.drawColor(0xFFFFF0F5.toInt())
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFF9A6B7A.toInt()
            textAlign = Paint.Align.CENTER
            textSize = 28f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        stages.forEachIndexed { i, stage ->
            val col = i % cols
            val row = i / cols
            val bm = PigSprites.get(ctx, stage, PigSprites.WALK_1)
            val left = col * cellW
            val top = row * cellH
            val dst = RectF(left + 40f, top + 30f, left + cellW - 40f, top + cellH - 70f)
            c.drawBitmap(bm, null, dst, paint)
            c.drawText(stage.dir, left + cellW / 2f, top + cellH - 28f, text)
            File(out, "stage-${stage.dir}.png").outputStream().use {
                bm.copy(Bitmap.Config.ARGB_8888, false).compress(Bitmap.CompressFormat.PNG, 100, it)
            }
        }
        File(out, "stages-all.png").outputStream().use {
            sheet.compress(Bitmap.CompressFormat.PNG, 100, it)
        }

        // Action strips for normal stage
        fun strip(name: String, frames: Array<String>) {
            val w = 200 * frames.size
            val h = 240
            val b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            val cv = Canvas(b)
            cv.drawColor(Color.WHITE)
            frames.forEachIndexed { i, f ->
                val bm = PigSprites.get(ctx, BodyStage.NORMAL, f)
                cv.drawBitmap(bm, null, RectF(i * 200f + 20f, 20f, i * 200f + 180f, 200f), paint)
                cv.drawText(f, i * 200f + 100f, 230f, text.apply { textSize = 18f })
            }
            File(out, "strip-$name.png").outputStream().use {
                b.compress(Bitmap.CompressFormat.PNG, 100, it)
            }
        }
        strip("walk", PigSprites.WALK)
        strip("picked", PigSprites.PICKED)
        strip("bento", PigSprites.BENTO)
        strip("lift", PigSprites.LIFT)
        strip("run", PigSprites.RUN)

        // CuteFx captures at mid progress for each stage x key scene
        for (stage in stages) {
            for ((scene, label) in listOf(
                CuteScene.FOOD_CATCH to "bento",
                CuteScene.LIFT to "lift",
                CuteScene.TREADMILL to "run",
            )) {
                val bmp = CuteFx.capture(ctx, scene, 0.55f, 640, 960, stage = stage)
                File(out, "anim-${stage.dir}-$label.png").outputStream().use {
                    bmp.compress(Bitmap.CompressFormat.PNG, 100, it)
                }
            }
        }
    }
}

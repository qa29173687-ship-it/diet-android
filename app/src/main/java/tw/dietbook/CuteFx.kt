package tw.dietbook

import android.animation.ValueAnimator
import android.app.Activity
import android.app.Dialog
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

enum class CuteScene {
    BMR_COIN,
    HANDS_POUR,
    FOOD_CATCH,
    PROTEIN_POWER,
    SURPLUS_BELLY,
    FIREWORKS_KG,
    POSING,
    MORNING,
}

data class PendingCute(
    val scene: CuteScene,
    val kcal: Double = 0.0,
    val protein: Double = 0.0,
    val label: String = "",
)

/**
 * Full-screen soft springy overlay for A/B/C + milestones.
 * Drawn with kawaii shapes so we don't need extra sprite sheets.
 */
object CuteFx {
    fun queue(ctx: Context, p: PendingCute) = Store.queueCute(ctx, p)
    fun pop(ctx: Context): PendingCute? = Store.popCute(ctx)

    fun play(activity: Activity, scene: CuteScene, kcal: Double = 0.0, protein: Double = 0.0, label: String = "", onDone: (() -> Unit)? = null) {
        if (activity.isFinishing) {
            onDone?.invoke(); return
        }
        val dlg = Dialog(activity, android.R.style.Theme_Translucent_NoTitleBar)
        dlg.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dlg.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        dlg.setCancelable(true)
        val stage = StageView(activity, scene, kcal, protein, label) {
            try { dlg.dismiss() } catch (_: Exception) {}
            onDone?.invoke()
        }
        dlg.setContentView(FrameLayout(activity).apply {
            setBackgroundColor(0x66000000.toInt())
            addView(stage, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
            setOnClickListener { stage.skip() }
        })
        dlg.setOnDismissListener { onDone?.invoke() }
        try {
            dlg.show()
        } catch (_: Exception) {
            onDone?.invoke()
        }
        when (scene) {
            CuteScene.BMR_COIN, CuteScene.HANDS_POUR, CuteScene.FIREWORKS_KG -> Sfx.coin(activity)
            CuteScene.FOOD_CATCH -> Sfx.chew(activity)
            CuteScene.PROTEIN_POWER -> Sfx.sparkle(activity)
            CuteScene.SURPLUS_BELLY -> Sfx.burp(activity)
            CuteScene.POSING -> Sfx.sparkle(activity)
            CuteScene.MORNING -> Sfx.yawn(activity)
        }
    }

    /** Robolectric / preview helper — draws one frame at progress 0..1. */
    fun capture(ctx: Context, scene: CuteScene, progress: Float, w: Int = 800, h: Int = 1200, kcal: Double = 500.0, protein: Double = 90.0, label: String = ""): android.graphics.Bitmap {
        val view = StageView(ctx, scene, kcal, protein, label) {}
        view.forceProgress(progress)
        view.measure(
            View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY),
        )
        view.layout(0, 0, w, h)
        val b = android.graphics.Bitmap.createBitmap(w, h, android.graphics.Bitmap.Config.ARGB_8888)
        val c = Canvas(b)
        c.drawColor(0xFFFFF0F5.toInt())
        view.draw(c)
        return b
    }

    fun playQueued(activity: Activity, onIdle: (() -> Unit)? = null) {
        val p = pop(activity) ?: run { onIdle?.invoke(); return }
        play(activity, p.scene, p.kcal, p.protein, p.label) {
            // chain next if any
            if (Store.peekCute(activity) != null) playQueued(activity, onIdle)
            else onIdle?.invoke()
        }
    }
}

private class StageView(
    ctx: Context,
    private val scene: CuteScene,
    private val kcal: Double,
    private val protein: Double,
    private val label: String,
    private val onDone: () -> Unit,
) : View(ctx) {
    private var t = 0f
    private var finished = false
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFFF8FB.toInt()
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        setShadowLayer(8f, 0f, 2f, 0x66000000)
    }
    private val sparkles = List(18) {
        floatArrayOf(Random.nextFloat(), Random.nextFloat(), Random.nextFloat() * 360f, 0.4f + Random.nextFloat())
    }
    private val anim = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = when (scene) {
            CuteScene.HANDS_POUR -> 2200L
            CuteScene.FOOD_CATCH -> 2000L
            CuteScene.FIREWORKS_KG -> 2600L
            CuteScene.POSING -> 2800L
            CuteScene.MORNING -> 1800L
            else -> 1600L
        }
        interpolator = OvershootInterpolator(1.15f)
        addUpdateListener {
            t = it.animatedFraction
            invalidate()
            if (t >= 0.999f && !finished) {
                finished = true
                postDelayed({ onDone() }, 120)
            }
        }
    }

    init {
        anim.start()
    }

    fun forceProgress(p: Float) {
        anim.cancel()
        t = p.coerceIn(0f, 1f)
        finished = true
    }

    fun skip() {
        if (finished) return
        finished = true
        anim.cancel()
        onDone()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_DOWN) {
            skip(); return true
        }
        return super.onTouchEvent(event)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        anim.cancel()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat(); val h = height.toFloat()
        if (w <= 0 || h <= 0) return
        val cx = w / 2f; val cy = h * 0.48f
        val s = min(w, h)
        when (scene) {
            CuteScene.BMR_COIN -> drawBmr(canvas, cx, cy, s)
            CuteScene.HANDS_POUR -> drawPour(canvas, cx, cy, s)
            CuteScene.FOOD_CATCH -> drawFood(canvas, cx, cy, s)
            CuteScene.PROTEIN_POWER -> drawProtein(canvas, cx, cy, s)
            CuteScene.SURPLUS_BELLY -> drawSurplus(canvas, cx, cy, s)
            CuteScene.FIREWORKS_KG -> drawFireworks(canvas, cx, cy, s)
            CuteScene.POSING -> drawPosing(canvas, cx, cy, s)
            CuteScene.MORNING -> drawMorning(canvas, cx, cy, s)
        }
        // caption
        text.textSize = 18f * resources.displayMetrics.scaledDensity
        val cap = when (scene) {
            CuteScene.BMR_COIN -> "今日基礎代謝入帳 ✨\n${Calc.r0(kcal)} kcal"
            CuteScene.HANDS_POUR -> "運動存進小豬！\n−${Calc.r0(kcal)} kcal"
            CuteScene.FOOD_CATCH -> if (label.isNotEmpty()) label else "好好吃～"
            CuteScene.PROTEIN_POWER -> "蛋白質達標！💪\n${Calc.r1(protein)} g"
            CuteScene.SURPLUS_BELLY -> "好撐喔～"
            CuteScene.FIREWORKS_KG -> "你減掉 1 公斤了！"
            CuteScene.POSING -> if (label.isNotEmpty()) label else "部位滿等！上台秀一下～"
            CuteScene.MORNING -> "早安～"
        }
        var y = h * 0.78f
        cap.split('\n').forEach { line ->
            canvas.drawText(line, cx, y, text)
            y += text.textSize * 1.25f
        }
    }

    private fun ease(x: Float): Float {
        // soft bounce out
        val c = (x * 1.15f).coerceIn(0f, 1f)
        return (sin((c - 0.5) * PI).toFloat() * 0.5f + 0.5f).coerceIn(0f, 1f)
    }

    private fun drawPigBody(canvas: Canvas, cx: Float, cy: Float, r: Float, belly: Float = 1f) {
        paint.style = Paint.Style.FILL
        paint.color = 0xFFFFC1D4.toInt()
        canvas.drawCircle(cx, cy, r * belly, paint)
        paint.color = 0xFFFF8FAB.toInt()
        canvas.drawCircle(cx - r * 0.35f, cy - r * 0.55f, r * 0.22f, paint)
        canvas.drawCircle(cx + r * 0.35f, cy - r * 0.55f, r * 0.22f, paint)
        paint.color = 0xFFFF9EB5.toInt()
        canvas.drawOval(RectF(cx - r * 0.28f, cy + r * 0.05f, cx + r * 0.28f, cy + r * 0.42f * belly), paint)
        paint.color = 0xFF4A3040.toInt()
        canvas.drawCircle(cx - r * 0.22f, cy - r * 0.12f, r * 0.06f, paint)
        canvas.drawCircle(cx + r * 0.22f, cy - r * 0.12f, r * 0.06f, paint)
    }

    private fun drawCoin(canvas: Canvas, x: Float, y: Float, r: Float) {
        paint.color = 0xFFFFD76A.toInt()
        canvas.drawCircle(x, y, r, paint)
        paint.color = 0xFFFFB830.toInt()
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = r * 0.18f
        canvas.drawCircle(x, y, r * 0.72f, paint)
        paint.style = Paint.Style.FILL
        
    }

    private fun drawBmr(canvas: Canvas, cx: Float, cy: Float, s: Float) {
        val e = ease(t)
        val pigY = cy + s * 0.08f
        drawPigBody(canvas, cx, pigY, s * 0.16f)
        // slot
        paint.color = 0xFF9A6B7A.toInt()
        canvas.drawRoundRect(RectF(cx - s * 0.06f, pigY - s * 0.18f, cx + s * 0.06f, pigY - s * 0.14f), 8f, 8f, paint)
        val coinY = cy - s * 0.42f + (pigY - s * 0.22f - (cy - s * 0.42f)) * e
        val spin = t * 720f
        canvas.save()
        canvas.translate(cx, coinY)
        canvas.rotate(spin)
        drawCoinFixed(canvas, 0f, 0f, s * 0.05f)
        canvas.restore()
        if (t > 0.7f) drawSparkles(canvas, cx, pigY - s * 0.2f, s * 0.2f, (t - 0.7f) / 0.3f)
    }

    private fun drawCoinFixed(canvas: Canvas, x: Float, y: Float, r: Float) {
        paint.style = Paint.Style.FILL
        paint.color = 0xFFFFD76A.toInt()
        canvas.drawCircle(x, y, r, paint)
        paint.color = 0xFFFFB830.toInt()
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = r * 0.16f
        canvas.drawCircle(x, y, r * 0.7f, paint)
        paint.style = Paint.Style.FILL
        text.textSize = r * 1.1f
        text.color = 0xFF8A5A10.toInt()
        canvas.drawText("￥", x, y + r * 0.35f, text)
        text.color = 0xFFFFF8FB.toInt()
    }

    private fun drawPour(canvas: Canvas, cx: Float, cy: Float, s: Float) {
        val e = ease(t.coerceIn(0f, 1f))
        val pigY = cy + s * 0.1f
        // hands hug / shake
        val shake = sin(t * 28f) * s * 0.012f * (if (t in 0.15f..0.75f) 1f else 0f)
        drawPigBody(canvas, cx + shake, pigY, s * 0.15f)
        paint.color = 0xFFFFE0B2.toInt()
        val handY = pigY - s * 0.02f + sin(t * 20f) * s * 0.01f
        canvas.drawCircle(cx - s * 0.22f + shake, handY, s * 0.055f, paint)
        canvas.drawCircle(cx + s * 0.22f + shake, handY, s * 0.055f, paint)
        // coins pour
        val n = 7
        for (i in 0 until n) {
            val ph = ((t * 1.2f) - i * 0.08f).coerceIn(0f, 1f)
            if (ph <= 0f) continue
            val x = cx + (i - n / 2f) * s * 0.04f + sin(i + t * 6) * s * 0.01f
            val y0 = cy - s * 0.35f
            val y1 = pigY - s * 0.05f
            val y = y0 + (y1 - y0) * (ph * ph)
            drawCoinFixed(canvas, x, y, s * 0.028f)
        }
        if (t > 0.55f) drawSparkles(canvas, cx, pigY, s * 0.25f, (t - 0.55f) / 0.45f)
    }

    private fun drawFood(canvas: Canvas, cx: Float, cy: Float, s: Float) {
        val pigY = cy + s * 0.08f
        val chew = 1f + 0.06f * sin(t * 40f) * if (t in 0.35f..0.75f) 1f else 0f
        drawPigBody(canvas, cx, pigY, s * 0.15f * chew)
        // food drop
        val drop = ease((t / 0.35f).coerceIn(0f, 1f))
        val fy = cy - s * 0.4f + (pigY - s * 0.05f - (cy - s * 0.4f)) * drop
        paint.color = 0xFFFFB86C.toInt()
        if (t < 0.4f) {
            canvas.drawRoundRect(RectF(cx - s * 0.04f, fy - s * 0.03f, cx + s * 0.04f, fy + s * 0.03f), 10f, 10f, paint)
        }
        // catch spark
        if (t in 0.35f..0.5f) drawSparkles(canvas, cx, pigY - s * 0.05f, s * 0.12f, (t - 0.35f) / 0.15f)
        // burp cloud late
        if (t > 0.82f) {
            paint.color = 0x66FFFFFF
            canvas.drawCircle(cx + s * 0.18f, pigY - s * 0.12f, s * 0.05f * (t - 0.82f) / 0.18f, paint)
        }
    }

    private fun drawProtein(canvas: Canvas, cx: Float, cy: Float, s: Float) {
        drawPigBody(canvas, cx, cy + s * 0.05f, s * 0.15f)
        // milk carton
        val e = ease(t)
        paint.color = 0xFFFFFFFF.toInt()
        val mx = cx - s * 0.22f
        val my = cy - s * 0.05f + (1f - e) * s * 0.2f
        canvas.drawRoundRect(RectF(mx - s * 0.05f, my - s * 0.08f, mx + s * 0.05f, my + s * 0.08f), 8f, 8f, paint)
        paint.color = 0xFF7EC8A3.toInt()
        canvas.drawRect(mx - s * 0.05f, my - s * 0.02f, mx + s * 0.05f, my + s * 0.08f, paint)
        // flame
        paint.color = 0xFFFF8A50.toInt()
        val flame = Path().apply {
            moveTo(cx + s * 0.18f, cy + s * 0.05f)
            quadTo(cx + s * 0.28f, cy - s * 0.12f * e, cx + s * 0.22f, cy - s * 0.2f * e)
            quadTo(cx + s * 0.14f, cy - s * 0.08f, cx + s * 0.18f, cy + s * 0.05f)
            close()
        }
        canvas.drawPath(flame, paint)
        // fist
        paint.color = 0xFFFFE0B2.toInt()
        canvas.drawCircle(cx + s * 0.02f + s * 0.1f * e, cy + s * 0.02f, s * 0.05f, paint)
        drawSparkles(canvas, cx, cy, s * 0.3f, e)
    }

    private fun drawSurplus(canvas: Canvas, cx: Float, cy: Float, s: Float) {
        val belly = 1f + 0.22f * ease(t)
        drawPigBody(canvas, cx, cy + s * 0.06f, s * 0.15f, belly)
        // doze Zzz
        text.textSize = 22f * resources.displayMetrics.scaledDensity
        text.color = 0xFFFFB6C8.toInt()
        val z = ease((t - 0.4f).coerceAtLeast(0f) / 0.6f)
        canvas.drawText("Z", cx + s * 0.2f, cy - s * 0.18f - z * s * 0.08f, text)
        canvas.drawText("z", cx + s * 0.26f, cy - s * 0.24f - z * s * 0.1f, text)
        text.color = 0xFFFFF8FB.toInt()
    }

    private fun drawFireworks(canvas: Canvas, cx: Float, cy: Float, s: Float) {
        drawPigBody(canvas, cx, cy + s * 0.1f, s * 0.14f)
        // tiny crown
        val e = ease(t)
        paint.color = 0xFFFFD76A.toInt()
        val crownY = cy - s * 0.12f - (1f - e) * s * 0.2f
        val path = Path().apply {
            moveTo(cx - s * 0.08f, crownY)
            lineTo(cx - s * 0.05f, crownY - s * 0.06f)
            lineTo(cx - s * 0.02f, crownY)
            lineTo(cx, crownY - s * 0.08f)
            lineTo(cx + s * 0.02f, crownY)
            lineTo(cx + s * 0.05f, crownY - s * 0.06f)
            lineTo(cx + s * 0.08f, crownY)
            close()
        }
        canvas.drawPath(path, paint)
        // fireworks
        for (i in 0 until 12) {
            val ang = i * 30f + t * 40f
            val rad = s * 0.08f + t * s * 0.28f
            val x = cx + cos(Math.toRadians(ang.toDouble())).toFloat() * rad
            val y = cy - s * 0.05f + sin(Math.toRadians(ang.toDouble())).toFloat() * rad * 0.7f
            paint.color = when (i % 3) {
                0 -> 0xFFFF8FAB.toInt()
                1 -> 0xFFFFD76A.toInt()
                else -> 0xFF7EC8E3.toInt()
            }
            canvas.drawCircle(x, y, s * 0.018f * (1.2f - t), paint)
        }
        drawSparkles(canvas, cx, cy, s * 0.4f, t)
    }

    private fun drawPosing(canvas: Canvas, cx: Float, cy: Float, s: Float) {
        val phase = (t * 3f).toInt()
        val bob = sin(t * 10f) * s * 0.01f
        canvas.save()
        when (phase) {
            0 -> canvas.scale(1.05f, 0.95f, cx, cy)
            1 -> canvas.rotate(-8f + bob, cx, cy)
            else -> canvas.rotate(8f - bob, cx, cy)
        }
        drawPigBody(canvas, cx, cy + bob, s * 0.16f)
        // flex arms
        paint.color = 0xFFFFA8BE.toInt()
        canvas.drawCircle(cx - s * 0.2f, cy, s * 0.06f, paint)
        canvas.drawCircle(cx + s * 0.2f, cy, s * 0.06f, paint)
        canvas.restore()
        // spotlight
        paint.color = 0x33FFE8A0
        canvas.drawCircle(cx, cy + s * 0.18f, s * 0.22f, paint)
        drawSparkles(canvas, cx, cy - s * 0.1f, s * 0.25f, t)
    }

    private fun drawMorning(canvas: Canvas, cx: Float, cy: Float, s: Float) {
        val stretch = 1f + 0.12f * sin(t * PI.toFloat())
        canvas.save()
        canvas.scale(1f, stretch, cx, cy + s * 0.1f)
        drawPigBody(canvas, cx, cy + s * 0.05f, s * 0.15f)
        canvas.restore()
        // sun
        paint.color = 0xFFFFE08A.toInt()
        canvas.drawCircle(cx + s * 0.28f, cy - s * 0.28f, s * 0.07f * ease(t), paint)
    }

    private fun drawSparkles(canvas: Canvas, cx: Float, cy: Float, r: Float, a: Float) {
        paint.style = Paint.Style.FILL
        sparkles.forEachIndexed { i, sp ->
            val ang = sp[2] + a * 90f
            val rad = r * (0.3f + sp[3] * a)
            val x = cx + cos(Math.toRadians(ang.toDouble())).toFloat() * rad * sp[0]
            val y = cy + sin(Math.toRadians(ang.toDouble())).toFloat() * rad * sp[1]
            paint.color = if (i % 2 == 0) 0xFFFFF3A0.toInt() else 0xFFFFC1E3.toInt()
            canvas.drawCircle(x, y, 6f * (1f - a * 0.3f), paint)
        }
    }
}

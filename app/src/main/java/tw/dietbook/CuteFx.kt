package tw.dietbook

import android.animation.ValueAnimator
import android.app.Activity
import android.app.Dialog
import android.content.Context
import android.graphics.Bitmap
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
import android.view.animation.LinearInterpolator
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
    LIFT,
    TREADMILL,
}

data class PendingCute(
    val scene: CuteScene,
    val kcal: Double = 0.0,
    val protein: Double = 0.0,
    val label: String = "",
)

/**
 * 全螢幕可愛動畫。小豬本體一律用目前體型階段的 WebP 貼圖，
 * 金幣／雙手／花火等道具仍以程式繪製。
 */
object CuteFx {
    fun queue(ctx: Context, p: PendingCute) = Store.queueCute(ctx, p)
    fun pop(ctx: Context): PendingCute? = Store.popCute(ctx)

    fun currentStage(ctx: Context): BodyStage {
        val u = Store.profile(ctx)
        val snap = Store.snapshot(ctx, Days.today())
        val live = u.balance + (snap?.net ?: 0.0)
        return PigSprites.fromBalance(live)
    }

    fun play(activity: Activity, scene: CuteScene, kcal: Double = 0.0, protein: Double = 0.0, label: String = "", onDone: (() -> Unit)? = null) {
        if (activity.isFinishing) {
            onDone?.invoke(); return
        }
        val dlg = Dialog(activity, android.R.style.Theme_Translucent_NoTitleBar)
        dlg.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dlg.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        dlg.setCancelable(true)
        var doneOnce = false
        val finish: () -> Unit = {
            if (!doneOnce) {
                doneOnce = true
                try { dlg.dismiss() } catch (_: Exception) {}
                onDone?.invoke()
            }
        }
        val stage = StageView(activity, scene, kcal, protein, label, currentStage(activity), finish)
        dlg.setContentView(FrameLayout(activity).apply {
            setBackgroundColor(0x66000000.toInt())
            addView(stage, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
            setOnClickListener { stage.skip() }
        })
        dlg.setOnDismissListener { finish() }
        try {
            dlg.show()
        } catch (_: Exception) {
            onDone?.invoke()
        }
        when (scene) {
            CuteScene.BMR_COIN, CuteScene.HANDS_POUR, CuteScene.FIREWORKS_KG -> Sfx.coin(activity)
            CuteScene.FOOD_CATCH -> Sfx.chew(activity)
            CuteScene.PROTEIN_POWER, CuteScene.LIFT, CuteScene.POSING -> Sfx.sparkle(activity)
            CuteScene.SURPLUS_BELLY -> Sfx.burp(activity)
            CuteScene.MORNING -> Sfx.yawn(activity)
            CuteScene.TREADMILL -> Sfx.coin(activity)
        }
    }

    fun capture(
        ctx: Context,
        scene: CuteScene,
        progress: Float,
        w: Int = 800,
        h: Int = 1200,
        kcal: Double = 500.0,
        protein: Double = 90.0,
        label: String = "",
        stage: BodyStage = BodyStage.NORMAL,
    ): Bitmap {
        val view = StageView(ctx, scene, kcal, protein, label, stage) {}
        view.forceProgress(progress)
        view.measure(
            View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY),
        )
        view.layout(0, 0, w, h)
        val b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(b)
        c.drawColor(0xFFFFF0F5.toInt())
        view.draw(c)
        return b
    }

    fun playQueued(activity: Activity, onIdle: (() -> Unit)? = null) {
        val p = pop(activity) ?: run { onIdle?.invoke(); return }
        play(activity, p.scene, p.kcal, p.protein, p.label) {
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
    private val bodyStage: BodyStage,
    private val onDone: () -> Unit,
) : View(ctx) {
    private var t = 0f
    private var finished = false
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFFF8FB.toInt()
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        setShadowLayer(8f, 0f, 2f, 0x66000000)
    }
    private val sparkles = List(18) {
        floatArrayOf(Random.nextFloat(), Random.nextFloat(), Random.nextFloat() * 360f, 0.4f + Random.nextFloat())
    }
    private val durationMs = when (scene) {
        CuteScene.HANDS_POUR -> 2200L
        CuteScene.FOOD_CATCH, CuteScene.LIFT, CuteScene.TREADMILL -> 3000L
        CuteScene.FIREWORKS_KG -> 2600L
        CuteScene.POSING -> 2800L
        CuteScene.MORNING -> 1800L
        else -> 1600L
    }
    private val anim = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = durationMs
        interpolator = LinearInterpolator()
        addUpdateListener {
            t = it.animatedFraction
            invalidate()
            if (t >= 0.999f && !finished) {
                finished = true
                postDelayed({ onDone() }, 120)
            }
        }
    }

    init { anim.start() }

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
        if (event.action == MotionEvent.ACTION_DOWN) { skip(); return true }
        return super.onTouchEvent(event)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        anim.cancel()
    }

    private fun drawPigFrame(
        canvas: Canvas,
        cx: Float,
        cy: Float,
        targetW: Float,
        frame: String,
        scaleX: Float = 1f,
        scaleY: Float = 1f,
        rotation: Float = 0f,
    ): FloatArray {
        val bm = PigSprites.get(context, bodyStage, frame)
        val aspect = bm.width.toFloat() / bm.height.toFloat().coerceAtLeast(1f)
        val dw = targetW
        val dh = targetW / aspect
        canvas.save()
        canvas.translate(cx, cy)
        if (rotation != 0f) canvas.rotate(rotation)
        canvas.scale(scaleX, scaleY)
        canvas.drawBitmap(bm, null, RectF(-dw / 2f, -dh / 2f, dw / 2f, dh / 2f), paint)
        canvas.restore()
        return floatArrayOf(dw, dh)
    }

    /** lift/treadmill pattern: 1→2→1→2→3 over ~3s */
    private fun workoutFrame(names: Array<String>): String {
        val slots = floatArrayOf(0f, 0.2f, 0.4f, 0.6f, 0.8f, 1.01f)
        val seq = intArrayOf(0, 1, 0, 1, 2)
        for (i in seq.indices) {
            if (t < slots[i + 1]) return names[seq[i]]
        }
        return names.last()
    }

    private fun bentoFrame(): String = when {
        t < 0.22f -> PigSprites.BENTO_BOX
        t < 0.45f -> PigSprites.BENTO_HOLD
        t < 0.78f -> PigSprites.BENTO_EAT
        else -> PigSprites.BENTO_THUMB
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat(); val h = height.toFloat()
        if (w <= 0 || h <= 0) return
        val cx = w / 2f; val cy = h * 0.48f
        val s = min(w, h)
        val pigW = s * 0.42f
        when (scene) {
            CuteScene.BMR_COIN -> drawBmr(canvas, cx, cy, s, pigW)
            CuteScene.HANDS_POUR -> drawPour(canvas, cx, cy, s, pigW)
            CuteScene.FOOD_CATCH -> drawFood(canvas, cx, cy, s, pigW)
            CuteScene.PROTEIN_POWER -> drawProtein(canvas, cx, cy, s, pigW)
            CuteScene.SURPLUS_BELLY -> drawSurplus(canvas, cx, cy, s, pigW)
            CuteScene.FIREWORKS_KG -> drawFireworks(canvas, cx, cy, s, pigW)
            CuteScene.POSING -> drawPosing(canvas, cx, cy, s, pigW)
            CuteScene.MORNING -> drawMorning(canvas, cx, cy, s, pigW)
            CuteScene.LIFT -> drawLift(canvas, cx, cy, s, pigW)
            CuteScene.TREADMILL -> drawTreadmill(canvas, cx, cy, s, pigW)
        }
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
            CuteScene.LIFT -> "重訓打卡！小豬一起舉～\n−${Calc.r0(kcal)} kcal"
            CuteScene.TREADMILL -> "有氧存進小豬！\n−${Calc.r0(kcal)} kcal"
        }
        var y = h * 0.78f
        cap.split('\n').forEach { line ->
            canvas.drawText(line, cx, y, text)
            y += text.textSize * 1.25f
        }
    }

    private fun ease(x: Float): Float {
        val c = (x * 1.15f).coerceIn(0f, 1f)
        return (sin((c - 0.5) * PI).toFloat() * 0.5f + 0.5f).coerceIn(0f, 1f)
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

    private fun drawBmr(canvas: Canvas, cx: Float, cy: Float, s: Float, pigW: Float) {
        val e = ease(t)
        val pigY = cy + s * 0.06f
        val size = drawPigFrame(canvas, cx, pigY, pigW, PigSprites.WALK_1)
        val dh = size[1]
        val slotY = pigY - dh * 0.42f
        val coinY = cy - s * 0.42f + (slotY - (cy - s * 0.42f)) * e
        canvas.save()
        canvas.translate(cx, coinY)
        canvas.rotate(t * 720f)
        drawCoinFixed(canvas, 0f, 0f, s * 0.05f)
        canvas.restore()
        if (t > 0.7f) drawSparkles(canvas, cx, slotY, s * 0.2f, (t - 0.7f) / 0.3f)
    }

    private fun drawPour(canvas: Canvas, cx: Float, cy: Float, s: Float, pigW: Float) {
        val pigY = cy + s * 0.08f
        val shake = sin(t * 28f) * s * 0.012f * (if (t in 0.15f..0.75f) 1f else 0f)
        val size = drawPigFrame(canvas, cx + shake, pigY, pigW, PigSprites.BOUNCE_HAPPY)
        val dw = size[0]; val dh = size[1]
        paint.style = Paint.Style.FILL
        paint.color = 0xFFFFE0B2.toInt()
        val handY = pigY + dh * 0.05f + sin(t * 20f) * s * 0.01f
        val handR = s * 0.055f
        canvas.drawCircle(cx - dw * 0.52f + shake, handY, handR, paint)
        canvas.drawCircle(cx + dw * 0.52f + shake, handY, handR, paint)
        val n = 7
        for (i in 0 until n) {
            val ph = ((t * 1.2f) - i * 0.08f).coerceIn(0f, 1f)
            if (ph <= 0f) continue
            val x = cx + (i - n / 2f) * s * 0.04f + sin(i + t * 6) * s * 0.01f
            val y0 = cy - s * 0.35f
            val y1 = pigY - dh * 0.35f
            val y = y0 + (y1 - y0) * (ph * ph)
            drawCoinFixed(canvas, x, y, s * 0.028f)
        }
        if (t > 0.55f) drawSparkles(canvas, cx, pigY, s * 0.25f, (t - 0.55f) / 0.45f)
    }

    private fun drawFood(canvas: Canvas, cx: Float, cy: Float, s: Float, pigW: Float) {
        val pigY = cy + s * 0.06f
        val frame = bentoFrame()
        val chew = if (frame == PigSprites.BENTO_EAT) 1f + 0.04f * sin(t * 36f) else 1f
        drawPigFrame(canvas, cx, pigY, pigW, frame, scaleX = chew, scaleY = chew)
        if (t > 0.82f) drawSparkles(canvas, cx, pigY, s * 0.2f, (t - 0.82f) / 0.18f)
    }

    private fun drawLift(canvas: Canvas, cx: Float, cy: Float, s: Float, pigW: Float) {
        val pigY = cy + s * 0.05f
        drawPigFrame(canvas, cx, pigY, pigW, workoutFrame(PigSprites.LIFT))
        if (t > 0.75f) drawSparkles(canvas, cx, pigY, s * 0.22f, (t - 0.75f) / 0.25f)
    }

    private fun drawTreadmill(canvas: Canvas, cx: Float, cy: Float, s: Float, pigW: Float) {
        val pigY = cy + s * 0.05f
        // soft treadmill base
        paint.style = Paint.Style.FILL
        paint.color = 0xFFB0BEC5.toInt()
        canvas.drawRoundRect(
            RectF(cx - s * 0.22f, pigY + s * 0.16f, cx + s * 0.22f, pigY + s * 0.22f),
            10f, 10f, paint,
        )
        paint.color = 0xFF78909C.toInt()
        canvas.drawRoundRect(
            RectF(cx - s * 0.24f, pigY + s * 0.14f, cx - s * 0.18f, pigY + s * 0.24f),
            6f, 6f, paint,
        )
        drawPigFrame(canvas, cx, pigY, pigW, workoutFrame(PigSprites.RUN))
        if (t > 0.75f) drawSparkles(canvas, cx, pigY, s * 0.22f, (t - 0.75f) / 0.25f)
    }

    private fun drawProtein(canvas: Canvas, cx: Float, cy: Float, s: Float, pigW: Float) {
        val pigY = cy + s * 0.05f
        val size = drawPigFrame(canvas, cx, pigY, pigW, PigSprites.LIFT_3)
        val dw = size[0]; val dh = size[1]
        val e = ease(t)
        paint.style = Paint.Style.FILL
        paint.color = 0xFFFFFFFF.toInt()
        val mx = cx - dw * 0.62f
        val my = pigY - dh * 0.05f + (1f - e) * s * 0.2f
        canvas.drawRoundRect(RectF(mx - s * 0.05f, my - s * 0.08f, mx + s * 0.05f, my + s * 0.08f), 8f, 8f, paint)
        paint.color = 0xFF7EC8A3.toInt()
        canvas.drawRect(mx - s * 0.05f, my - s * 0.02f, mx + s * 0.05f, my + s * 0.08f, paint)
        paint.color = 0xFFFF8A50.toInt()
        val flame = Path().apply {
            moveTo(cx + dw * 0.48f, pigY + dh * 0.05f)
            quadTo(cx + dw * 0.7f, pigY - dh * 0.25f * e, cx + dw * 0.55f, pigY - dh * 0.45f * e)
            quadTo(cx + dw * 0.4f, pigY - dh * 0.15f, cx + dw * 0.48f, pigY + dh * 0.05f)
            close()
        }
        canvas.drawPath(flame, paint)
        paint.color = 0xFFFFE0B2.toInt()
        canvas.drawCircle(cx + dw * 0.05f + dw * 0.25f * e, pigY - dh * 0.05f, s * 0.05f, paint)
        drawSparkles(canvas, cx, pigY, s * 0.3f, e)
    }

    private fun drawSurplus(canvas: Canvas, cx: Float, cy: Float, s: Float, pigW: Float) {
        val belly = 1f + 0.12f * ease(t)
        val pigY = cy + s * 0.06f
        val size = drawPigFrame(
            canvas, cx, pigY, pigW, PigSprites.LAND_SQUASH,
            scaleX = belly, scaleY = 1f + 0.06f * ease(t),
        )
        val dh = size[1]
        text.textSize = 22f * resources.displayMetrics.scaledDensity
        text.color = 0xFFFFB6C8.toInt()
        val z = ease((t - 0.4f).coerceAtLeast(0f) / 0.6f)
        canvas.drawText("Z", cx + s * 0.22f, pigY - dh * 0.35f - z * s * 0.08f, text)
        canvas.drawText("z", cx + s * 0.28f, pigY - dh * 0.45f - z * s * 0.1f, text)
        text.color = 0xFFFFF8FB.toInt()
    }

    private fun drawFireworks(canvas: Canvas, cx: Float, cy: Float, s: Float, pigW: Float) {
        val pigY = cy + s * 0.08f
        val size = drawPigFrame(canvas, cx, pigY, pigW, PigSprites.BOUNCE_HAPPY)
        val dw = size[0]; val dh = size[1]
        val e = ease(t)
        paint.style = Paint.Style.FILL
        paint.color = 0xFFFFD76A.toInt()
        val crownY = pigY - dh * 0.48f - (1f - e) * s * 0.2f
        val path = Path().apply {
            moveTo(cx - dw * 0.18f, crownY)
            lineTo(cx - dw * 0.1f, crownY - s * 0.06f)
            lineTo(cx - dw * 0.04f, crownY)
            lineTo(cx, crownY - s * 0.08f)
            lineTo(cx + dw * 0.04f, crownY)
            lineTo(cx + dw * 0.1f, crownY - s * 0.06f)
            lineTo(cx + dw * 0.18f, crownY)
            close()
        }
        canvas.drawPath(path, paint)
        for (i in 0 until 12) {
            val ang = i * 30f + t * 40f
            val rad = s * 0.08f + t * s * 0.28f
            val x = cx + cos(Math.toRadians(ang.toDouble())).toFloat() * rad
            val y = pigY - dh * 0.1f + sin(Math.toRadians(ang.toDouble())).toFloat() * rad * 0.7f
            paint.color = when (i % 3) {
                0 -> 0xFFFF8FAB.toInt()
                1 -> 0xFFFFD76A.toInt()
                else -> 0xFF7EC8E3.toInt()
            }
            canvas.drawCircle(x, y, s * 0.018f * (1.2f - t), paint)
        }
        drawSparkles(canvas, cx, pigY, s * 0.4f, t)
    }

    private fun drawPosing(canvas: Canvas, cx: Float, cy: Float, s: Float, pigW: Float) {
        val phase = (t * 3f).toInt()
        val bob = sin(t * 10f) * s * 0.01f
        val pigY = cy + bob
        val rot = when (phase) {
            0 -> 0f
            1 -> -8f + bob
            else -> 8f - bob
        }
        val sx = if (phase == 0) 1.05f else 1f
        val sy = if (phase == 0) 0.95f else 1f
        val size = drawPigFrame(
            canvas, cx, pigY, pigW, PigSprites.LIFT_3,
            scaleX = sx, scaleY = sy, rotation = rot,
        )
        val dw = size[0]
        paint.style = Paint.Style.FILL
        paint.color = 0x33FFE8A0
        canvas.drawCircle(cx, cy + size[1] * 0.45f, s * 0.22f, paint)
        paint.color = 0x66FFA8BE
        canvas.drawCircle(cx - dw * 0.48f, pigY, s * 0.035f, paint)
        canvas.drawCircle(cx + dw * 0.48f, pigY, s * 0.035f, paint)
        drawSparkles(canvas, cx, pigY - size[1] * 0.2f, s * 0.25f, t)
    }

    private fun drawMorning(canvas: Canvas, cx: Float, cy: Float, s: Float, pigW: Float) {
        val stretch = 1f + 0.12f * sin(t * PI.toFloat())
        val pigY = cy + s * 0.05f
        val frame = if (t < 0.35f) PigSprites.WALK_1 else PigSprites.BOUNCE_HAPPY
        drawPigFrame(canvas, cx, pigY, pigW, frame, scaleX = 1f, scaleY = stretch)
        paint.style = Paint.Style.FILL
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

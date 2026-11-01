package tw.dietbook

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.View
import android.view.animation.OvershootInterpolator
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * 小豬撲滿：依累計熱量餘額在「很胖 → 普通 → 很壯」約五階之間變形。
 * balance > 0（熱量進帳）→ 變胖；balance < 0（缺口／存款）→ 變壯。
 */
class PigMascotView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    enum class Stage(val score: Float, val caption: String) {
        VERY_FAT(2f, "小豬吃太飽了…"),
        CHUBBY(1f, "小豬圓圓的"),
        NORMAL(0f, "小豬狀態剛好"),
        FIT(-1f, "小豬開始練了"),
        BUFF(-2f, "小豬超壯！"),
    }

    private val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 5f
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
    }
    private val blushPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val eyePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val snoutPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val accentPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val sweatPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        color = 0xFF9A6B7A.toInt()
    }
    private val slotPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    /** Continuous morph in [-2, 2]; negative = buff, positive = fat. */
    private var morph = 0f
    private var targetMorph = 0f
    private var bounce = 1f
    private var anim: ValueAnimator? = null
    private var pulse = 0f
    private val pulseAnim = ValueAnimator.ofFloat(0f, (Math.PI * 2).toFloat()).apply {
        duration = 2400
        repeatCount = ValueAnimator.INFINITE
        addUpdateListener {
            pulse = it.animatedValue as Float
            invalidate()
        }
    }

    var stageCaption: String = Stage.NORMAL.caption
        private set

    init {
        // Pastel pink pig palette
        bodyPaint.color = 0xFFFFB6C8.toInt()
        strokePaint.color = 0xFFE07A95.toInt()
        blushPaint.color = 0xFFFF8FAB.toInt()
        blushPaint.alpha = 110
        eyePaint.color = 0xFF3A2A32.toInt()
        snoutPaint.color = 0xFFFF9EB5.toInt()
        accentPaint.color = 0xFF7EC8A3.toInt()
        sweatPaint.color = 0xFF7EC8E3.toInt()
        slotPaint.color = 0xFF6B4A55.toInt()
        if (isInEditMode.not()) pulseAnim.start()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        pulseAnim.cancel()
        anim?.cancel()
    }

    fun setBalance(balanceKcal: Double, animate: Boolean = true) {
        val t = scoreFor(balanceKcal)
        targetMorph = t
        stageCaption = stageFor(t).caption
        if (!animate) {
            morph = t
            bounce = 1f
            invalidate()
            return
        }
        anim?.cancel()
        val start = morph
        anim = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 700
            interpolator = OvershootInterpolator(1.4f)
            addUpdateListener {
                val f = it.animatedFraction
                morph = start + (t - start) * f
                bounce = 1f + 0.08f * sin(f * Math.PI).toFloat()
                invalidate()
            }
            start()
        }
    }

    fun currentStage(): Stage = stageFor(morph)

    companion object {
        fun scoreFor(balance: Double): Float {
            val a = abs(balance)
            val mag = when {
                a >= 4000 -> 2f
                a >= 2200 -> 1.5f
                a >= 1000 -> 1f
                a >= 350 -> 0.5f
                else -> 0f
            }
            return when {
                balance > 50 -> mag
                balance < -50 -> -mag
                else -> 0f
            }
        }

        fun stageFor(score: Float): Stage = when {
            score >= 1.5f -> Stage.VERY_FAT
            score >= 0.5f -> Stage.CHUBBY
            score <= -1.5f -> Stage.BUFF
            score <= -0.5f -> Stage.FIT
            else -> Stage.NORMAL
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        val hWant = (w * 0.92f).toInt()
        val h = resolveSize(hWant, heightMeasureSpec)
        setMeasuredDimension(w, h)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0 || h <= 0) return

        val cx = w / 2f
        val cy = h * 0.48f
        val fat = max(0f, morph)           // 0..2
        val buff = max(0f, -morph)         // 0..2
        val breathe = 1f + 0.015f * sin(pulse.toDouble()).toFloat()
        val scale = bounce * breathe

        canvas.save()
        canvas.translate(cx, cy)
        canvas.scale(scale, scale)

        // Shadow
        val shadow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0x33000000; style = Paint.Style.FILL
        }
        canvas.drawOval(RectF(-90f - fat * 18f, 95f, 90f + fat * 18f, 118f + fat * 6f), shadow)

        // Body size morph
        val bodyW = 100f + fat * 38f - buff * 8f
        val bodyH = 88f + fat * 28f - buff * 4f
        // Buff: taller chest
        val chestBoost = buff * 10f

        // Ears (behind)
        drawEar(canvas, -bodyW * 0.55f, -bodyH * 0.75f, -1)
        drawEar(canvas, bodyW * 0.55f, -bodyH * 0.75f, 1)

        // Arms / muscles when buff
        if (buff > 0.2f) {
            drawArm(canvas, -bodyW * 0.85f, 10f, -1, buff)
            drawArm(canvas, bodyW * 0.85f, 10f, 1, buff)
        }

        // Body
        bodyPaint.color = lerpColor(0xFFFFB6C8.toInt(), 0xFFFF9EB5.toInt(), min(1f, fat / 2f))
        if (buff > 0.5f) {
            // slightly rosier athletic tone
            bodyPaint.color = lerpColor(bodyPaint.color, 0xFFFFA8BE.toInt(), min(1f, (buff - 0.5f) / 1.5f))
        }
        val body = RectF(-bodyW, -bodyH + chestBoost * 0.2f, bodyW, bodyH)
        canvas.drawOval(body, bodyPaint)
        canvas.drawOval(body, strokePaint)

        // Soft belly highlight
        val hi = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0x55FFFFFF
        }
        canvas.drawOval(RectF(-bodyW * 0.45f, bodyH * 0.05f, bodyW * 0.45f, bodyH * 0.75f), hi)

        // Mint spot (brand mark)
        accentPaint.color = 0xFF9ED9C0.toInt()
        canvas.drawOval(RectF(bodyW * 0.25f, bodyH * 0.05f, bodyW * 0.55f, bodyH * 0.38f), accentPaint)

        // Coin slot on head
        val slotY = -bodyH * 0.82f
        canvas.drawRoundRect(RectF(-22f, slotY - 4f, 22f, slotY + 4f), 4f, 4f, slotPaint)

        // Headband when buff
        if (buff > 0.6f) {
            val band = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFF6B8A.toInt() }
            val by = -bodyH * 0.55f
            canvas.drawRoundRect(RectF(-bodyW * 0.55f, by - 10f, bodyW * 0.55f, by + 10f), 8f, 8f, band)
            // knot
            canvas.drawCircle(bodyW * 0.55f + 8f, by, 10f, band)
            canvas.drawCircle(bodyW * 0.55f + 18f, by + 8f, 8f, band)
        }

        // Eyes
        val eyeY = -bodyH * 0.22f
        val eyeSpread = bodyW * 0.32f
        val eyeR = 9f + fat * 1.5f
        // Squint when very fat
        val eyeScaleY = if (fat > 1.2f) 0.65f else 1f
        canvas.save()
        canvas.scale(1f, eyeScaleY, -eyeSpread, eyeY)
        canvas.drawCircle(-eyeSpread, eyeY, eyeR, eyePaint)
        canvas.drawCircle(-eyeSpread - 2.5f, eyeY - 2.5f, 3.2f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE })
        canvas.restore()
        canvas.save()
        canvas.scale(1f, eyeScaleY, eyeSpread, eyeY)
        canvas.drawCircle(eyeSpread, eyeY, eyeR, eyePaint)
        canvas.drawCircle(eyeSpread - 2.5f, eyeY - 2.5f, 3.2f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE })
        canvas.restore()

        // Determined brows when buff
        if (buff > 0.4f) {
            val brow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = 0xFF3A2A32.toInt(); strokeWidth = 5f; style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND
            }
            canvas.drawLine(-eyeSpread - 12f, eyeY - 18f, -eyeSpread + 10f, eyeY - 14f, brow)
            canvas.drawLine(eyeSpread + 12f, eyeY - 18f, eyeSpread - 10f, eyeY - 14f, brow)
        }

        // Blush
        blushPaint.alpha = (90 + fat * 40).toInt().coerceIn(60, 160)
        canvas.drawOval(RectF(-eyeSpread - 28f, eyeY + 8f, -eyeSpread - 4f, eyeY + 26f), blushPaint)
        canvas.drawOval(RectF(eyeSpread + 4f, eyeY + 8f, eyeSpread + 28f, eyeY + 26f), blushPaint)

        // Snout
        val snout = RectF(-28f - fat * 4f, 2f + fat * 4f, 28f + fat * 4f, 42f + fat * 8f)
        snoutPaint.color = 0xFFFF9EB5.toInt()
        canvas.drawOval(snout, snoutPaint)
        canvas.drawOval(snout, strokePaint)
        eyePaint.alpha = 200
        canvas.drawCircle(-10f, 24f + fat * 4f, 4.5f, eyePaint)
        canvas.drawCircle(10f, 24f + fat * 4f, 4.5f, eyePaint)
        eyePaint.alpha = 255

        // Feet
        val footY = bodyH * 0.85f
        bodyPaint.color = 0xFFFFB6C8.toInt()
        canvas.drawOval(RectF(-bodyW * 0.55f, footY, -bodyW * 0.15f, footY + 28f), bodyPaint)
        canvas.drawOval(RectF(bodyW * 0.15f, footY, bodyW * 0.55f, footY + 28f), bodyPaint)
        canvas.drawOval(RectF(-bodyW * 0.55f, footY, -bodyW * 0.15f, footY + 28f), strokePaint)
        canvas.drawOval(RectF(bodyW * 0.15f, footY, bodyW * 0.55f, footY + 28f), strokePaint)

        // Sweat drop when very fat
        if (fat > 1.2f) {
            val sx = bodyW * 0.7f
            val sy = -bodyH * 0.35f
            val drop = Path().apply {
                moveTo(sx, sy)
                quadTo(sx + 14f, sy + 18f, sx, sy + 32f)
                quadTo(sx - 14f, sy + 18f, sx, sy)
                close()
            }
            canvas.drawPath(drop, sweatPaint)
            canvas.drawPath(drop, strokePaint)
        }

        // Flex sparkles when buff
        if (buff > 1.2f) {
            val sp = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFD36B.toInt() }
            drawStar(canvas, -bodyW * 1.15f, -20f, 10f, sp)
            drawStar(canvas, bodyW * 1.15f, -30f, 8f, sp)
        }

        canvas.restore()

        // Caption under pig
        textPaint.textSize = 34f * resources.displayMetrics.scaledDensity / 3f
        canvas.drawText(stageCaption, w / 2f, h * 0.92f, textPaint)
    }

    private fun drawEar(canvas: Canvas, x: Float, y: Float, dir: Int) {
        val ear = Path().apply {
            moveTo(x, y + 18f)
            quadTo(x + dir * 28f, y - 28f, x + dir * 8f, y + 30f)
            close()
        }
        canvas.drawPath(ear, bodyPaint)
        canvas.drawPath(ear, strokePaint)
        // inner
        val inner = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFF9EB5.toInt() }
        val ie = Path().apply {
            moveTo(x + dir * 2f, y + 16f)
            quadTo(x + dir * 16f, y - 8f, x + dir * 6f, y + 24f)
            close()
        }
        canvas.drawPath(ie, inner)
    }

    private fun drawArm(canvas: Canvas, x: Float, y: Float, dir: Int, buff: Float) {
        val muscle = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFB6C8.toInt() }
        val r = 22f + buff * 10f
        // upper arm / bicep
        canvas.drawCircle(x, y, r, muscle)
        canvas.drawCircle(x, y, r, strokePaint)
        // forearm flexed up
        val fx = x + dir * (r * 0.2f)
        val fy = y - r * 1.3f
        canvas.drawCircle(fx, fy, r * 0.75f, muscle)
        canvas.drawCircle(fx, fy, r * 0.75f, strokePaint)
        // fist
        canvas.drawCircle(fx, fy - r * 0.9f, r * 0.45f, muscle)
        canvas.drawCircle(fx, fy - r * 0.9f, r * 0.45f, strokePaint)
        // bicep highlight line
        if (buff > 1f) {
            val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = 0x55E07A95; strokeWidth = 3f; style = Paint.Style.STROKE
            }
            canvas.drawArc(RectF(x - r * 0.6f, y - r * 0.3f, x + r * 0.6f, y + r * 0.7f), 200f, 100f, false, line)
        }
    }

    private fun drawStar(canvas: Canvas, cx: Float, cy: Float, r: Float, p: Paint) {
        val path = Path()
        for (i in 0 until 8) {
            val a = i * Math.PI / 4.0 - Math.PI / 2
            val rr = if (i % 2 == 0) r else r * 0.4f
            val x = (cx + cos(a) * rr).toFloat()
            val y = (cy + sin(a) * rr).toFloat()
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
        canvas.drawPath(path, p)
    }

    private fun lerpColor(a: Int, b: Int, t: Float): Int {
        val tt = t.coerceIn(0f, 1f)
        val ar = Color.red(a); val ag = Color.green(a); val ab = Color.blue(a); val aa = Color.alpha(a)
        val br = Color.red(b); val bg = Color.green(b); val bb = Color.blue(b); val ba = Color.alpha(b)
        return Color.argb(
            (aa + (ba - aa) * tt).toInt(),
            (ar + (br - ar) * tt).toInt(),
            (ag + (bg - ag) * tt).toInt(),
            (ab + (bb - ab) * tt).toInt(),
        )
    }
}

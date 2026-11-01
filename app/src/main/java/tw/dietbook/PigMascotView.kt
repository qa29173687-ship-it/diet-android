package tw.dietbook

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.animation.LinearInterpolator
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/**
 * 電子雞式小豬：貼圖立繪 + 部位變壯疊圖，會在場地內自主遊走／暫停／打滾／坐下睡覺。
 */
class PigMascotView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    enum class Stage(val caption: String) {
        VERY_FAT("小豬吃太飽了…"),
        CHUBBY("小豬圓圓的"),
        NORMAL("小豬狀態剛好"),
        FIT("小豬開始練了"),
        BUFF("小豬超壯！"),
    }

    private enum class Behavior { WALK, PAUSE, ROLL, SIT, SLEEP }

    private val bitmaps = HashMap<Int, Bitmap>()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val overlayPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 4f
        strokeJoin = Paint.Join.ROUND
        color = 0xFFE07A95.toInt()
    }
    private val sweatPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF7EC8E3.toInt() }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        color = 0xFF9A6B7A.toInt()
        textSize = 36f
    }
    private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x33000000 }

    private var balance = 0.0
    private var moodOverride: PigMood? = null
    private var partLevels: Map<String, Int> = emptyMap()
    private var vitals = PigVitals(70, 70, 70)
    var stageCaption: String = Stage.NORMAL.caption
        private set

    // wander state (normalized 0..1 inside usable area)
    private var pigX = 0.5f
    private var pigY = 0.55f
    private var facingRight = true
    private var behavior = Behavior.PAUSE
    private var behaviorUntil = 0L
    private var walkTargetX = 0.5f
    private var walkSpeed = 0.00018f
    private var rollAngle = 0f
    private var rollProgress = 0f
    private var waddle = 0f
    private var bounce = 0f
    private var blink = 0f
    private var celebrateUntil = 0L
    private var tapPulse = 0f

    private val tick = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 1000
        repeatCount = ValueAnimator.INFINITE
        interpolator = LinearInterpolator()
        addUpdateListener {
            stepAi(16)
            bounce = (bounce + 0.09f) % (Math.PI * 2).toFloat()
            blink = (blink + 0.04f) % 40f
            if (tapPulse > 0f) tapPulse = (tapPulse - 0.06f).coerceAtLeast(0f)
            invalidate()
        }
    }

    init {
        isClickable = true
        if (!isInEditMode) tick.start()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        tick.cancel()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (!tick.isStarted) tick.start()
        pickBehavior(force = true)
    }

    fun setBalance(balanceKcal: Double, animate: Boolean = true) {
        balance = balanceKcal
        stageCaption = stageFor(scoreFor(balanceKcal)).caption
        invalidate()
    }

    fun setPartLevels(levels: Map<String, Int>) {
        partLevels = levels
        invalidate()
    }

    fun setVitals(v: PigVitals) {
        vitals = v
        invalidate()
    }

    fun setMoodOverride(mood: PigMood?, ms: Long = 0L) {
        moodOverride = mood
        if (mood == PigMood.CELEBRATE || mood == PigMood.WORKOUT || mood == PigMood.EAT) {
            celebrateUntil = System.currentTimeMillis() + if (ms > 0) ms else 2800L
            behavior = Behavior.PAUSE
            behaviorUntil = celebrateUntil
        }
        invalidate()
    }

    fun currentStage(): Stage = stageFor(scoreFor(balance))

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_DOWN) {
            tapPulse = 1f
            // react: hop + maybe flip
            if (Random.nextBoolean()) facingRight = !facingRight
            behavior = Behavior.PAUSE
            behaviorUntil = System.currentTimeMillis() + 900
            moodOverride = PigMood.NORMAL
            celebrateUntil = System.currentTimeMillis() + 900
            performClick()
            return true
        }
        return super.onTouchEvent(event)
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    private fun bmp(res: Int): Bitmap {
        bitmaps[res]?.let { return it }
        val b = BitmapFactory.decodeResource(resources, res)
        bitmaps[res] = b
        return b
    }

    private fun resolveMood(): PigMood {
        val now = System.currentTimeMillis()
        if (now < celebrateUntil && moodOverride != null) return moodOverride!!
        moodOverride?.let { if (it == PigMood.HUNGRY || it == PigMood.SLEEP) return it }
        if (behavior == Behavior.SLEEP) return PigMood.SLEEP
        if (behavior == Behavior.SIT && vitals.satiety < 35) return PigMood.HUNGRY
        // night
        val hour = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("Asia/Taipei"))
            .get(java.util.Calendar.HOUR_OF_DAY)
        if (hour >= 22 || hour < 6) {
            if (behavior == Behavior.PAUSE || behavior == Behavior.SIT) return PigMood.SLEEP
        }
        if (vitals.satiety < 28) return PigMood.HUNGRY
        val score = scoreFor(balance)
        return when {
            score >= 1.5f -> PigMood.VERY_FAT
            score >= 0.5f -> PigMood.CHUBBY
            score <= -1.5f -> PigMood.BUFF
            score <= -0.5f -> PigMood.BUFF
            else -> PigMood.NORMAL
        }
    }

    private fun spriteRes(mood: PigMood): Int = when (mood) {
        PigMood.EAT -> R.drawable.pig_eat
        PigMood.HUNGRY -> R.drawable.pig_hungry
        PigMood.SLEEP -> R.drawable.pig_sleep
        PigMood.WORKOUT, PigMood.CELEBRATE -> R.drawable.pig_workout
        PigMood.BUFF -> R.drawable.pig_buff
        PigMood.CHUBBY -> R.drawable.pig_chubby
        PigMood.VERY_FAT -> R.drawable.pig_very_fat
        PigMood.NORMAL -> R.drawable.pig_normal
    }

    private fun stepAi(dtMs: Int) {
        val now = System.currentTimeMillis()
        if (now < celebrateUntil) return
        if (now >= behaviorUntil) pickBehavior()
        when (behavior) {
            Behavior.WALK -> {
                val dir = if (walkTargetX >= pigX) 1f else -1f
                facingRight = dir > 0
                pigX += dir * walkSpeed * dtMs
                // slight vertical bob path
                pigY += sin(bounce.toDouble()).toFloat() * 0.0004f * dtMs
                pigY = pigY.coerceIn(0.28f, 0.78f)
                waddle = sin(bounce * 2.2f) * 8f
                if ((dir > 0 && pigX >= walkTargetX) || (dir < 0 && pigX <= walkTargetX)) {
                    pigX = walkTargetX.coerceIn(0.12f, 0.88f)
                    pickBehavior()
                }
                pigX = pigX.coerceIn(0.12f, 0.88f)
            }
            Behavior.ROLL -> {
                rollProgress += dtMs / 900f
                rollAngle = rollProgress * 360f * (if (facingRight) 1f else -1f)
                pigX += (if (facingRight) 1f else -1f) * 0.00055f * dtMs
                pigX = pigX.coerceIn(0.14f, 0.86f)
                if (rollProgress >= 1f) {
                    rollAngle = 0f
                    rollProgress = 0f
                    pickBehavior()
                }
            }
            Behavior.PAUSE, Behavior.SIT, Behavior.SLEEP -> {
                waddle *= 0.9f
                rollAngle *= 0.8f
            }
        }
    }

    private fun pickBehavior(force: Boolean = false) {
        val now = System.currentTimeMillis()
        val hour = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("Asia/Taipei"))
            .get(java.util.Calendar.HOUR_OF_DAY)
        val night = hour >= 22 || hour < 6
        val r = Random.nextFloat()
        behavior = when {
            night && r < 0.55f -> Behavior.SLEEP
            vitals.satiety < 30 && r < 0.4f -> Behavior.SIT
            r < 0.42f -> Behavior.WALK
            r < 0.62f -> Behavior.PAUSE
            r < 0.78f -> Behavior.SIT
            r < 0.90f -> Behavior.ROLL
            else -> Behavior.SLEEP
        }
        if (force && !night) behavior = Behavior.WALK
        behaviorUntil = now + when (behavior) {
            Behavior.WALK -> (1800..4200).random().toLong()
            Behavior.PAUSE -> (900..2600).random().toLong()
            Behavior.SIT -> (1600..4000).random().toLong()
            Behavior.SLEEP -> (2500..6000).random().toLong()
            Behavior.ROLL -> 950L
        }
        if (behavior == Behavior.WALK) {
            walkTargetX = (0.14f + Random.nextFloat() * 0.72f)
            walkSpeed = 0.00012f + Random.nextFloat() * 0.00018f
            facingRight = walkTargetX >= pigX
        }
        if (behavior == Behavior.ROLL) {
            rollProgress = 0f
            if (pigX > 0.7f) facingRight = false
            if (pigX < 0.3f) facingRight = true
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        val h = resolveSize((w * 1.05f).toInt(), heightMeasureSpec)
        setMeasuredDimension(w, max(h, (w * 0.85f).toInt()))
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0 || h <= 0) return

        val mood = resolveMood()
        val src = spriteRes(mood)
        val bm = try { bmp(src) } catch (_: Exception) { return }

        val cx = w * pigX
        val cy = h * pigY
        val baseSize = min(w, h) * 0.42f * (1f + tapPulse * 0.08f)
        val aspect = bm.width.toFloat() / bm.height.toFloat()
        var dw = baseSize
        var dh = baseSize / aspect
        if (dh > h * 0.62f) {
            dh = h * 0.62f
            dw = dh * aspect
        }
        // squash during roll
        if (behavior == Behavior.ROLL) {
            val squash = 1f + 0.18f * sin(rollProgress * Math.PI).toFloat()
            dw *= squash
            dh /= squash
        }
        // sit: lower a bit
        val sitDrop = if (behavior == Behavior.SIT || behavior == Behavior.SLEEP || mood == PigMood.SLEEP || mood == PigMood.HUNGRY) dh * 0.06f else 0f
        val idleBob = if (behavior == Behavior.WALK) sin(bounce.toDouble()).toFloat() * 6f else sin(bounce.toDouble()).toFloat() * 3f

        // shadow
        canvas.drawOval(
            RectF(cx - dw * 0.38f, cy + dh * 0.42f - sitDrop, cx + dw * 0.38f, cy + dh * 0.52f - sitDrop),
            shadowPaint
        )

        canvas.save()
        canvas.translate(cx, cy + idleBob - sitDrop)
        canvas.rotate(rollAngle + waddle * 0.35f)
        if (!facingRight) canvas.scale(-1f, 1f)

        val dst = RectF(-dw / 2f, -dh / 2f, dw / 2f, dh / 2f)
        canvas.drawBitmap(bm, null, dst, paint)

        // muscle overlays (attached, flip with pig)
        drawMuscleOverlays(canvas, dw, dh, mood)

        // sweat for chubby/very fat
        if (mood == PigMood.CHUBBY || mood == PigMood.VERY_FAT) {
            drawSweat(canvas, dw * 0.35f, -dh * 0.15f)
            if (mood == PigMood.VERY_FAT) drawSweat(canvas, -dw * 0.32f, -dh * 0.05f)
        }

        // blink veil occasionally on normal
        if (mood == PigMood.NORMAL && blink < 1.2f) {
            val lid = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x33FFB6C8 }
            canvas.drawOval(RectF(-dw * 0.18f, -dh * 0.18f, dw * 0.18f, -dh * 0.02f), lid)
        }

        canvas.restore()

        // caption
        textPaint.textSize = 13f * resources.displayMetrics.scaledDensity
        val cap = when (behavior) {
            Behavior.ROLL -> "小豬打滾中～"
            Behavior.SLEEP, Behavior.SIT -> if (mood == PigMood.HUNGRY) "肚子餓了…" else if (mood == PigMood.SLEEP) "Zzz…" else stageCaption
            else -> stageCaption
        }
        canvas.drawText(cap, w / 2f, h * 0.96f, textPaint)
    }

    private fun drawMuscleOverlays(canvas: Canvas, dw: Float, dh: Float, mood: PigMood) {
        // Skip heavy overlays when using full buff sprite already
        val arms = partLevels["手臂"] ?: 0
        val chest = partLevels["胸"] ?: 0
        val abs = partLevels["腹"] ?: 0
        val legs = partLevels["腿"] ?: 0
        val shoulders = partLevels["肩"] ?: 0
        val back = partLevels["背"] ?: 0
        val glutes = partLevels["臀"] ?: 0
        if (arms + chest + abs + legs + shoulders + back + glutes <= 0) return
        if (mood == PigMood.BUFF && arms >= 2 && chest >= 2) {
            // buff sprite already shows muscles; still add headband hint via abs/legs only
        }

        fun muscleColor(level: Int): Int = when (level) {
            1 -> 0xFFFFC1D4.toInt()
            2 -> 0xFFFFA8BE.toInt()
            else -> 0xFFFF8FAB.toInt()
        }

        // arms (left/right of torso)
        if (arms > 0 && mood != PigMood.BUFF) {
            val r = dw * (0.10f + arms * 0.025f)
            overlayPaint.color = muscleColor(arms)
            // right arm (visual right = +x when facing)
            canvas.drawCircle(dw * 0.42f, dh * 0.05f, r, overlayPaint)
            canvas.drawCircle(dw * 0.42f, dh * 0.05f, r, strokePaint)
            canvas.drawCircle(-dw * 0.42f, dh * 0.05f, r, overlayPaint)
            canvas.drawCircle(-dw * 0.42f, dh * 0.05f, r, strokePaint)
            if (arms >= 2) {
                canvas.drawCircle(dw * 0.40f, -dh * 0.08f, r * 0.7f, overlayPaint)
                canvas.drawCircle(-dw * 0.40f, -dh * 0.08f, r * 0.7f, overlayPaint)
            }
        }

        // shoulders pads
        if (shoulders > 0) {
            overlayPaint.color = muscleColor(shoulders)
            val r = dw * (0.08f + shoulders * 0.02f)
            canvas.drawCircle(dw * 0.28f, -dh * 0.12f, r, overlayPaint)
            canvas.drawCircle(-dw * 0.28f, -dh * 0.12f, r, overlayPaint)
        }

        // chest
        if (chest > 0) {
            overlayPaint.color = muscleColor(chest)
            val rr = RectF(-dw * (0.16f + chest * 0.02f), -dh * 0.02f, dw * (0.16f + chest * 0.02f), dh * (0.12f + chest * 0.02f))
            canvas.drawOval(rr, overlayPaint)
            strokePaint.alpha = 140
            canvas.drawLine(0f, rr.top + 4f, 0f, rr.bottom - 4f, strokePaint)
            strokePaint.alpha = 255
        }

        // abs
        if (abs > 0) {
            overlayPaint.color = muscleColor(abs)
            val cols = abs.coerceAtMost(3)
            val cellW = dw * 0.07f
            val cellH = dh * 0.05f
            val startY = dh * 0.12f
            for (row in 0 until cols) {
                for (col in 0 until 2) {
                    val x = (col * 2 - 1) * cellW * 1.1f
                    val y = startY + row * cellH * 1.3f
                    canvas.drawRoundRect(RectF(x - cellW, y, x + cellW, y + cellH), 6f, 6f, overlayPaint)
                }
            }
        }

        // legs
        if (legs > 0 || glutes > 0) {
            val lv = max(legs, glutes)
            overlayPaint.color = muscleColor(lv)
            val r = dw * (0.09f + lv * 0.02f)
            canvas.drawOval(RectF(-dw * 0.28f - r, dh * 0.28f, -dw * 0.08f + r * 0.2f, dh * 0.48f), overlayPaint)
            canvas.drawOval(RectF(dw * 0.08f - r * 0.2f, dh * 0.28f, dw * 0.28f + r, dh * 0.48f), overlayPaint)
        }

        // headband if any strength high
        val maxL = listOf(arms, chest, abs, legs, shoulders, back, glutes).maxOrNull() ?: 0
        if (maxL >= 2 && mood != PigMood.BUFF && mood != PigMood.SLEEP) {
            val band = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFF6B8A.toInt() }
            val by = -dh * 0.28f
            canvas.drawRoundRect(RectF(-dw * 0.28f, by - dh * 0.03f, dw * 0.28f, by + dh * 0.03f), 8f, 8f, band)
            canvas.drawCircle(dw * 0.30f, by, dh * 0.035f, band)
        }
    }

    private fun drawSweat(canvas: Canvas, x: Float, y: Float) {
        val drop = Path().apply {
            moveTo(x, y)
            quadTo(x + 10f, y + 14f, x, y + 26f)
            quadTo(x - 10f, y + 14f, x, y)
            close()
        }
        canvas.drawPath(drop, sweatPaint)
        canvas.drawPath(drop, strokePaint)
    }

    companion object {
        fun scoreFor(balance: Double): Float {
            val a = kotlin.math.abs(balance)
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
}

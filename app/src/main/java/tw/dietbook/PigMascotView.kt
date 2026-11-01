package tw.dietbook

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.view.animation.LinearInterpolator
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/**
 * 首頁場地小豬：依累計餘額切換體型貼圖；2D 遊走＋深度縮放；
 * 長按／拖曳抓起、鬆手落地彈跳、甩飛暈眩；點空白處走過去。
 */
class PigMascotView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    /** 相容舊 API：對應新的 BodyStage */
    enum class Stage(val caption: String) {
        VERY_FAT("小豬吃太飽了…"),
        CHUBBY("小豬圓圓的"),
        NORMAL("小豬狀態剛好"),
        FIT("小豬有點瘦"),
        BUFF("小豬太瘦了"),
    }

    private enum class Mode { IDLE, WALK, HELD, FALL, DIZZY, SPECIAL }

    var onBubble: ((String) -> Unit)? = null

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x33000000 }
    private val overlayPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = 4f; color = 0xFFE07A95.toInt()
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        color = 0xFF9A6B7A.toInt()
    }
    private val propPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    private var balance = 0.0
    private var bodyStage = BodyStage.NORMAL
    private var displayStage = BodyStage.NORMAL
    private var stageBlend = 1f // 0=old → 1=new
    private var prevStage = BodyStage.NORMAL
    private var moodOverride: PigMood? = null
    private var partLevels: Map<String, Int> = emptyMap()
    private var vitals = PigVitals(70, 70, 70)
    private var outfit = OutfitId.NONE
    var stageCaption: String = BodyStage.NORMAL.caption
        private set

    // normalized position 0..1 within roam area
    private var pigX = 0.5f
    private var pigY = 0.55f
    private var facingRight = true
    private var mode = Mode.IDLE
    private var modeUntil = 0L
    private var targetX = 0.5f
    private var targetY = 0.55f
    private var walkSpeed = 0.00022f
    private var walkFrame = 0
    private var walkPhase = 0f
    private var bounce = 0f
    private var idleMs = 0L
    private var celebrateUntil = 0L
    private var specialUntil = 0L
    private var morningDoneToday = false

    // grab / fall
    private var holdFrame = 0
    private var holdPhase = 0f
    private var fallT = 0f
    private var fallVx = 0f
    private var fallVy = 0f
    private var fallFromY = 0.4f
    private var landY = 0.55f
    private var squash = 1f
    private var dizzyUntil = 0L
    private var velocityTracker: VelocityTracker? = null
    private var pointerId = -1
    private var grabOffsetX = 0f
    private var grabOffsetY = 0f
    private var longPressArmed = false
    private var downX = 0f
    private var downY = 0f
    private var downAt = 0L
    private val longPressMs = 280L

    private var lastDw = 0f
    private var lastDh = 0f
    private var lastCx = 0f
    private var lastCy = 0f

    private val tick = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 1000
        repeatCount = ValueAnimator.INFINITE
        interpolator = LinearInterpolator()
        addUpdateListener {
            step(16)
            bounce = (bounce + 0.09f) % (Math.PI * 2).toFloat()
            idleMs += 16
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
        velocityTracker?.recycle(); velocityTracker = null
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (!tick.isStarted) tick.start()
        if (mode == Mode.IDLE) pickIdleWalk()
    }

    fun setBalance(balanceKcal: Double, animate: Boolean = true) {
        balance = balanceKcal
        val next = PigSprites.fromBalance(balanceKcal)
        if (next != bodyStage) {
            prevStage = displayStage
            bodyStage = next
            if (animate) {
                stageBlend = 0f
            } else {
                displayStage = next
                stageBlend = 1f
            }
        }
        stageCaption = next.caption
        if (next == BodyStage.SLIM2) {
            onBubble?.invoke(BodyStage.SLIM2.hint)
        }
        invalidate()
    }

    fun setPartLevels(levels: Map<String, Int>) { partLevels = levels; invalidate() }
    fun setVitals(v: PigVitals) { vitals = v; invalidate() }
    fun setOutfit(id: OutfitId) { outfit = id; invalidate() }

    fun setMoodOverride(mood: PigMood?, ms: Long = 0L) {
        moodOverride = mood
        if (mood == PigMood.CELEBRATE || mood == PigMood.WORKOUT || mood == PigMood.EAT) {
            celebrateUntil = System.currentTimeMillis() + if (ms > 0) ms else 2800L
            mode = Mode.SPECIAL
            specialUntil = celebrateUntil
            modeUntil = celebrateUntil
        }
        invalidate()
    }

    fun currentStage(): Stage = when (bodyStage) {
        BodyStage.FAT3 -> Stage.VERY_FAT
        BodyStage.FAT2, BodyStage.FAT1 -> Stage.CHUBBY
        BodyStage.SLIM2 -> Stage.BUFF
        BodyStage.SLIM1 -> Stage.FIT
        BodyStage.NORMAL -> Stage.NORMAL
    }

    fun playWelcome() {
        idleMs = 0
        mode = Mode.SPECIAL
        specialUntil = System.currentTimeMillis() + 1600
        modeUntil = specialUntil
        targetX = 0.5f; targetY = 0.55f
        facingRight = pigX < 0.5f
        onBubble?.invoke("歡迎回來～")
        Sfx.pop(context)
    }

    fun playMorningStretch() {
        if (morningDoneToday) return
        morningDoneToday = true
        mode = Mode.SPECIAL
        specialUntil = System.currentTimeMillis() + 1800
        modeUntil = specialUntil
        onBubble?.invoke("早安")
        Sfx.yawn(context)
    }

    fun puffBelly() {
        setMoodOverride(PigMood.CHUBBY, 2200)
        onBubble?.invoke("好撐喔～")
    }

    fun markInteracted() {
        idleMs = 0
        Store.setLastInteractMs(context, System.currentTimeMillis())
    }

    private fun depthScale(): Float {
        // pigY 0(top)=smaller, 1(bottom)=larger
        return 0.78f + 0.32f * pigY.coerceIn(0f, 1f)
    }

    private fun midSize(w: Float, h: Float): Float = min(w, h) * 0.42f

    private fun step(dt: Int) {
        val now = System.currentTimeMillis()
        if (stageBlend < 1f) {
            stageBlend = (stageBlend + dt / 700f).coerceAtMost(1f)
            if (stageBlend >= 1f) displayStage = bodyStage
        }

        when (mode) {
            Mode.HELD -> {
                holdPhase += dt / 220f
                holdFrame = (holdPhase.toInt() % PigSprites.PICKED.size)
            }
            Mode.FALL -> {
                fallT += dt / 1000f
                pigX += fallVx * dt
                pigY += fallVy * dt
                fallVy += 0.0012f * dt // gravity in normalized space
                pigX = pigX.coerceIn(0.08f, 0.92f)
                // squash while falling near ground
                val remaining = (landY - pigY).coerceAtLeast(0.01f)
                if (pigY >= landY || fallT > 1.6f) {
                    pigY = landY
                    // bounce then settle
                    if (abs(fallVy) > 0.0008f && fallT < 1.2f) {
                        fallVy = -abs(fallVy) * 0.35f
                        squash = 0.72f
                        fallVx *= 0.5f
                    } else {
                        squash = 1f
                        if (now < dizzyUntil) {
                            mode = Mode.DIZZY
                            modeUntil = dizzyUntil
                            onBubble?.invoke("暈暈的…")
                        } else {
                            mode = Mode.IDLE
                            pickIdleWalk()
                        }
                    }
                } else {
                    squash = (0.85f + 0.15f * (1f - (fallVy.coerceIn(0f, 0.003f) / 0.003f))).coerceIn(0.7f, 1.15f)
                }
            }
            Mode.DIZZY -> {
                if (now >= modeUntil) {
                    mode = Mode.IDLE
                    pickIdleWalk()
                }
            }
            Mode.WALK -> {
                val dx = targetX - pigX
                val dy = targetY - pigY
                val dist = hypot(dx.toDouble(), dy.toDouble()).toFloat()
                if (dist < 0.02f) {
                    pigX = targetX; pigY = targetY
                    mode = Mode.IDLE
                    modeUntil = now + (800..2200).random()
                } else {
                    facingRight = dx >= 0
                    val stepLen = walkSpeed * dt
                    pigX += dx / dist * stepLen
                    pigY += dy / dist * stepLen
                    walkPhase += dt / 140f
                    walkFrame = walkPhase.toInt() % 4
                }
            }
            Mode.IDLE -> {
                if (now >= modeUntil && now >= celebrateUntil) pickIdleWalk()
            }
            Mode.SPECIAL -> {
                walkPhase += dt / 180f
                if (now >= specialUntil) {
                    mode = Mode.IDLE
                    pickIdleWalk()
                }
            }
        }
        pigY = pigY.coerceIn(0.12f, 0.88f)
        pigX = pigX.coerceIn(0.08f, 0.92f)
        if (squash < 1f) squash = (squash + dt / 180f).coerceAtMost(1f)
    }

    private fun pickIdleWalk() {
        if (mode == Mode.HELD || mode == Mode.FALL || mode == Mode.DIZZY) return
        val now = System.currentTimeMillis()
        if (Random.nextFloat() < 0.55f) {
            mode = Mode.WALK
            targetX = 0.12f + Random.nextFloat() * 0.76f
            targetY = 0.18f + Random.nextFloat() * 0.64f
            walkSpeed = 0.00016f + Random.nextFloat() * 0.00022f
            facingRight = targetX >= pigX
            modeUntil = now + 8000
        } else {
            mode = Mode.IDLE
            modeUntil = now + (900..2800).random()
        }
    }

    private fun hitPig(x: Float, y: Float): Boolean {
        val hw = lastDw * 0.48f
        val hh = lastDh * 0.48f
        return x in (lastCx - hw)..(lastCx + hw) && y in (lastCy - hh)..(lastCy + hh)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                markInteracted()
                downX = event.x; downY = event.y; downAt = System.currentTimeMillis()
                longPressArmed = hitPig(event.x, event.y)
                pointerId = event.getPointerId(0)
                velocityTracker?.recycle()
                velocityTracker = VelocityTracker.obtain().also { it.addMovement(event) }
                if (longPressArmed) {
                    parent?.requestDisallowInterceptTouchEvent(true)
                }
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                velocityTracker?.addMovement(event)
                val idx = event.findPointerIndex(pointerId).takeIf { it >= 0 } ?: 0
                val x = event.getX(idx); val y = event.getY(idx)
                if (mode != Mode.HELD && longPressArmed) {
                    val heldLong = System.currentTimeMillis() - downAt >= longPressMs
                    val dragged = hypot((x - downX).toDouble(), (y - downY).toDouble()) > 24
                    if (heldLong || dragged) {
                        mode = Mode.HELD
                        holdPhase = 0f; holdFrame = 0
                        grabOffsetX = x - lastCx
                        grabOffsetY = y - lastCy
                        onBubble?.invoke("抓到了！")
                        Sfx.pop(context)
                    }
                }
                if (mode == Mode.HELD) {
                    val w = width.toFloat().coerceAtLeast(1f)
                    val h = height.toFloat().coerceAtLeast(1f)
                    pigX = ((x - grabOffsetX) / w).coerceIn(0.08f, 0.92f)
                    pigY = ((y - grabOffsetY) / h).coerceIn(0.08f, 0.85f)
                    facingRight = (x - downX) >= 0 || facingRight
                }
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                velocityTracker?.addMovement(event)
                val wasHeld = mode == Mode.HELD
                if (wasHeld) {
                    velocityTracker?.computeCurrentVelocity(1000)
                    val vx = (velocityTracker?.xVelocity ?: 0f) / width.coerceAtLeast(1)
                    val vy = (velocityTracker?.yVelocity ?: 0f) / height.coerceAtLeast(1)
                    val speed = hypot(vx.toDouble(), vy.toDouble()).toFloat()
                    fallVx = vx * 0.00035f
                    fallVy = vy * 0.00035f
                    fallFromY = pigY
                    landY = (pigY + 0.12f + Random.nextFloat() * 0.08f).coerceIn(0.35f, 0.88f)
                    fallT = 0f
                    squash = 0.8f
                    mode = Mode.FALL
                    if (speed > 2.8f) {
                        dizzyUntil = System.currentTimeMillis() + 1500
                        onBubble?.invoke("飛出去啦！")
                    } else {
                        dizzyUntil = 0
                        onBubble?.invoke("落地～")
                    }
                    Sfx.pop(context)
                } else if (longPressArmed && event.actionMasked == MotionEvent.ACTION_UP) {
                    // short tap on pig — nuzzle / giggle by zone
                    val now = System.currentTimeMillis()
                    if (now - downAt < longPressMs) {
                        mode = Mode.SPECIAL
                        specialUntil = now + 900
                        modeUntil = specialUntil
                        onBubble?.invoke(if (Random.nextBoolean()) "蹭蹭～" else "嘻嘻！")
                        Sfx.nuzzle(context)
                    }
                } else if (event.actionMasked == MotionEvent.ACTION_UP && !longPressArmed) {
                    // tap empty → walk there
                    val w = width.toFloat().coerceAtLeast(1f)
                    val h = height.toFloat().coerceAtLeast(1f)
                    targetX = (event.x / w).coerceIn(0.1f, 0.9f)
                    targetY = (event.y / h).coerceIn(0.15f, 0.85f)
                    mode = Mode.WALK
                    walkSpeed = 0.00028f
                    facingRight = targetX >= pigX
                    onBubble?.invoke("來了～")
                }
                velocityTracker?.recycle(); velocityTracker = null
                longPressArmed = false
                pointerId = -1
                performClick()
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        val h = resolveSize((w * 1.05f).toInt(), heightMeasureSpec)
        setMeasuredDimension(w, max(h, (w * 0.85f).toInt()))
    }

    private fun currentFrameName(): String = when (mode) {
        Mode.HELD -> PigSprites.PICKED[holdFrame.coerceIn(0, 2)]
        Mode.FALL -> if (squash < 0.92f) PigSprites.LAND_SQUASH else PigSprites.BOUNCE_HAPPY
        Mode.DIZZY -> PigSprites.DIZZY
        Mode.WALK -> PigSprites.WALK[walkFrame.coerceIn(0, 3)]
        Mode.SPECIAL -> when (moodOverride) {
            PigMood.EAT -> PigSprites.BENTO_EAT
            PigMood.WORKOUT -> PigSprites.LIFT_2
            PigMood.HUNGRY -> PigSprites.WALK_1
            PigMood.SLEEP -> PigSprites.WALK_1
            else -> PigSprites.WALK[(walkPhase.toInt() % 4)]
        }
        Mode.IDLE -> PigSprites.WALK_1
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0 || h <= 0) return

        val frame = currentFrameName()
        val stageA = if (stageBlend < 1f) prevStage else bodyStage
        val stageB = bodyStage
        val bmA = PigSprites.get(context, stageA, frame)
        val bmB = if (stageA != stageB) PigSprites.get(context, stageB, frame) else bmA

        val cx = w * pigX
        val cy = h * pigY
        val depth = depthScale()
        var base = midSize(w, h) * depth
        if (mode == Mode.HELD) base *= 1.06f
        val aspect = bmB.width.toFloat() / bmB.height.toFloat().coerceAtLeast(1f)
        var dw = base
        var dh = base / aspect
        // squash / stretch
        dw *= (2f - squash)
        dh *= squash
        if (mode == Mode.DIZZY) {
            dw *= 1f + 0.04f * sin(bounce * 4f)
        }

        lastCx = cx; lastCy = cy; lastDw = dw; lastDh = dh

        // soft shadow (larger when closer / lower)
        val shadowW = dw * (0.34f + 0.08f * pigY)
        val shadowH = dh * 0.10f
        shadowPaint.alpha = (40 + 50 * pigY).toInt().coerceIn(30, 90)
        canvas.drawOval(RectF(cx - shadowW, cy + dh * 0.42f, cx + shadowW, cy + dh * 0.42f + shadowH), shadowPaint)

        canvas.save()
        canvas.translate(cx, cy)
        val sway = if (mode == Mode.HELD) sin(holdPhase * 2.2f) * 12f else 0f
        val dizzySpin = if (mode == Mode.DIZZY) sin(bounce * 3f) * 18f else 0f
        canvas.rotate(sway + dizzySpin)
        if (!facingRight) canvas.scale(-1f, 1f)

        val dst = RectF(-dw / 2f, -dh / 2f, dw / 2f, dh / 2f)
        if (stageBlend < 1f && stageA != stageB) {
            paint.alpha = ((1f - stageBlend) * 255).toInt()
            canvas.drawBitmap(bmA, null, dst, paint)
            paint.alpha = (stageBlend * 255).toInt()
            canvas.drawBitmap(bmB, null, dst, paint)
            paint.alpha = 255
            displayStage = if (stageBlend > 0.5f) stageB else stageA
        } else {
            canvas.drawBitmap(bmB, null, dst, paint)
            displayStage = stageB
        }

        drawMuscleOverlays(canvas, dw, dh)
        drawOutfit(canvas, dw, dh)
        canvas.restore()

        textPaint.textSize = 13f * resources.displayMetrics.scaledDensity
        val cap = when {
            mode == Mode.DIZZY -> "暈眩中…"
            mode == Mode.HELD -> "抓住小豬～"
            bodyStage == BodyStage.SLIM2 -> BodyStage.SLIM2.caption
            else -> stageCaption
        }
        canvas.drawText(cap, w / 2f, h * 0.97f, textPaint)
    }

    private fun drawOutfit(canvas: Canvas, dw: Float, dh: Float) {
        when (outfit) {
            OutfitId.NONE -> {}
            OutfitId.SPORTS -> {
                propPaint.color = 0xFF7EC8E3.toInt()
                canvas.drawRoundRect(RectF(-dw * 0.28f, -dh * 0.02f, dw * 0.28f, dh * 0.28f), 12f, 12f, propPaint)
            }
            OutfitId.BIB -> {
                propPaint.color = 0xFFFFF3A0.toInt()
                val bib = Path().apply {
                    moveTo(-dw * 0.2f, -dh * 0.05f); lineTo(dw * 0.2f, -dh * 0.05f)
                    lineTo(dw * 0.16f, dh * 0.22f); quadTo(0f, dh * 0.28f, -dw * 0.16f, dh * 0.22f); close()
                }
                canvas.drawPath(bib, propPaint)
            }
            OutfitId.CHEF -> {
                propPaint.color = 0xFFFFFFFF.toInt()
                canvas.drawRoundRect(RectF(-dw * 0.18f, -dh * 0.55f, dw * 0.18f, -dh * 0.32f), 10f, 10f, propPaint)
            }
            OutfitId.UMBRELLA -> {
                propPaint.color = 0xFF7EC8E3.toInt()
                canvas.drawArc(RectF(dw * 0.05f, -dh * 0.55f, dw * 0.45f, -dh * 0.15f), 180f, 180f, true, propPaint)
            }
            OutfitId.ICE_CREAM -> {
                propPaint.color = 0xFFFF8FAB.toInt()
                canvas.drawCircle(dw * 0.27f, -dh * 0.1f, dh * 0.05f, propPaint)
            }
            OutfitId.CNY_RED -> {
                propPaint.color = 0xFFE85A5A.toInt()
                canvas.drawRoundRect(RectF(-dw * 0.3f, -dh * 0.05f, dw * 0.3f, dh * 0.3f), 10f, 10f, propPaint)
            }
            OutfitId.SANTA -> {
                propPaint.color = 0xFFE85A5A.toInt()
                canvas.drawCircle(dw * 0.2f, -dh * 0.45f, dh * 0.08f, propPaint)
            }
            OutfitId.MOONCAKE -> {
                propPaint.color = 0xFFE8C07A.toInt()
                canvas.drawRoundRect(RectF(dw * 0.18f, dh * 0.05f, dw * 0.38f, dh * 0.22f), 8f, 8f, propPaint)
            }
        }
    }

    private fun drawMuscleOverlays(canvas: Canvas, dw: Float, dh: Float) {
        val arms = partLevels["手臂"] ?: 0
        val chest = partLevels["胸"] ?: 0
        val abs = partLevels["腹"] ?: 0
        if (arms + chest + abs <= 0) return
        fun col(level: Int) = when (level) { 1 -> 0xFFFFC1D4.toInt(); 2 -> 0xFFFFA8BE.toInt(); else -> 0xFFFF8FAB.toInt() }
        if (arms > 0) {
            overlayPaint.color = col(arms)
            val r = dw * (0.10f + arms * 0.02f)
            canvas.drawCircle(dw * 0.40f, dh * 0.05f, r, overlayPaint)
            canvas.drawCircle(-dw * 0.40f, dh * 0.05f, r, overlayPaint)
        }
        if (chest > 0) {
            overlayPaint.color = col(chest)
            canvas.drawOval(RectF(-dw * 0.18f, -dh * 0.02f, dw * 0.18f, dh * 0.14f), overlayPaint)
        }
        if (abs > 0) {
            overlayPaint.color = col(abs)
            canvas.drawRoundRect(RectF(-dw * 0.1f, dh * 0.12f, dw * 0.1f, dh * 0.28f), 6f, 6f, overlayPaint)
        }
    }

    companion object {
        /** 舊分數 API，供預覽／測試相容；新邏輯請用 PigSprites.fromBalance */
        fun scoreFor(balance: Double): Float {
            val stage = PigSprites.fromBalance(balance)
            return when (stage) {
                BodyStage.FAT3 -> 2f
                BodyStage.FAT2 -> 1.5f
                BodyStage.FAT1 -> 0.5f
                BodyStage.SLIM2 -> -2f
                BodyStage.SLIM1 -> -0.5f
                BodyStage.NORMAL -> 0f
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

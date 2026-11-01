package tw.dietbook

import android.app.Activity
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat

/** Pastel pink 「熱量存錢筒」 palette — soft, cute, light. */
object C {
    const val bg = 0xFFFFF5F8.toInt()
    const val card = 0xFFFFFFFF.toInt()
    const val card2 = 0xFFFFE8EF.toInt()
    const val primary = 0xFFFF8FAB.toInt()
    const val primaryDark = 0xFFE85A7A.toInt()
    const val text = 0xFF4A3040.toInt()
    const val muted = 0xFFB08A98.toInt()
    const val danger = 0xFFFF6B8A.toInt()
    const val warn = 0xFFFFB86C.toInt()
    const val line = 0xFFFFD6E0.toInt()
    const val mint = 0xFF7EC8A3.toInt()
    const val onPrimary = 0xFFFFFFFF.toInt()
}

fun Activity.edge() {
    WindowCompat.setDecorFitsSystemWindows(window, false)
    window.statusBarColor = Color.TRANSPARENT
    window.navigationBarColor = Color.TRANSPARENT
    WindowInsetsControllerCompat(window, window.decorView).apply {
        isAppearanceLightStatusBars = true
        isAppearanceLightNavigationBars = true
    }
}

fun dp(v: View, n: Float): Int =
    TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, n, v.resources.displayMetrics).toInt()

fun Activity.dp(n: Float): Int =
    TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, n, resources.displayMetrics).toInt()

fun roundBg(color: Int, radiusDp: Float, v: View): GradientDrawable =
    GradientDrawable().apply {
        setColor(color)
        cornerRadius = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, radiusDp, v.resources.displayMetrics)
    }

fun col(ctx: Activity, scroll: Boolean = true): Pair<View, LinearLayout> {
    val root = LinearLayout(ctx).apply {
        orientation = LinearLayout.VERTICAL
        setBackgroundColor(C.bg)
    }
    val body = LinearLayout(ctx).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(ctx.dp(16f), ctx.dp(12f), ctx.dp(16f), ctx.dp(16f))
    }
    val content: View = if (scroll) ScrollView(ctx).apply {
        isFillViewport = true
        addView(body, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
    } else body
    root.addView(content, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
    return root to body
}

fun titleBar(ctx: Activity, title: String, subtitle: String? = null, onBack: (() -> Unit)? = null): LinearLayout {
    val bar = LinearLayout(ctx).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(0, 0, 0, ctx.dp(8f))
    }
    if (onBack != null) {
        bar.addView(Button(ctx).apply {
            text = "←"
            setTextColor(C.text)
            background = roundBg(C.card2, 14f, this)
            setOnClickListener { onBack() }
            layoutParams = LinearLayout.LayoutParams(ctx.dp(44f), ctx.dp(44f)).apply { marginEnd = ctx.dp(8f) }
        })
    }
    val texts = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL; layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f) }
    texts.addView(TextView(ctx).apply {
        text = title; setTextColor(C.text); textSize = 22f; typeface = Typeface.DEFAULT_BOLD
    })
    if (subtitle != null) texts.addView(TextView(ctx).apply {
        text = subtitle; setTextColor(C.muted); textSize = 13f
    })
    bar.addView(texts)
    return bar
}

fun card(ctx: Activity): LinearLayout = LinearLayout(ctx).apply {
    orientation = LinearLayout.VERTICAL
    background = roundBg(C.card, 20f, this).also {
        it.setStroke(ctx.dp(1f), C.line)
    }
    setPadding(ctx.dp(16f), ctx.dp(16f), ctx.dp(16f), ctx.dp(16f))
    elevation = 2f
    val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
    lp.bottomMargin = ctx.dp(12f)
    layoutParams = lp
}

fun label(ctx: Activity, s: String, size: Float = 13f, color: Int = C.muted) = TextView(ctx).apply {
    text = s; setTextColor(color); textSize = size
}

fun value(ctx: Activity, s: String, size: Float = 28f, color: Int = C.text) = TextView(ctx).apply {
    text = s; setTextColor(color); textSize = size; typeface = Typeface.DEFAULT_BOLD
}

fun primaryBtn(ctx: Activity, s: String, onClick: () -> Unit) = Button(ctx).apply {
    text = s
    setTextColor(C.onPrimary)
    backgroundTintList = ColorStateList.valueOf(C.primary)
    background = roundBg(C.primary, 16f, this)
    setOnClickListener { onClick() }
    layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ctx.dp(50f)).apply {
        topMargin = ctx.dp(8f); bottomMargin = ctx.dp(4f)
    }
}

fun secondaryBtn(ctx: Activity, s: String, onClick: () -> Unit) = Button(ctx).apply {
    text = s
    setTextColor(C.text)
    background = roundBg(C.card2, 16f, this)
    setOnClickListener { onClick() }
    layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ctx.dp(48f)).apply {
        topMargin = ctx.dp(4f); bottomMargin = ctx.dp(4f)
    }
}

fun field(ctx: Activity, hint: String, value: String = ""): EditText = EditText(ctx).apply {
    setHint(hint); setText(value)
    setTextColor(C.text); setHintTextColor(C.muted)
    background = roundBg(C.card2, 14f, this)
    setPadding(ctx.dp(14f), ctx.dp(14f), ctx.dp(14f), ctx.dp(14f))
    layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
        bottomMargin = ctx.dp(8f)
    }
}

fun progress(ctx: Activity, pct: Int, color: Int = C.primary) = ProgressBar(ctx, null, android.R.attr.progressBarStyleHorizontal).apply {
    max = 100; progress = pct.coerceIn(0, 100)
    progressTintList = ColorStateList.valueOf(color)
    progressBackgroundTintList = ColorStateList.valueOf(C.line)
    layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ctx.dp(10f)).apply {
        topMargin = ctx.dp(6f); bottomMargin = ctx.dp(4f)
    }
}

/** Display helpers for piggy-bank balance language. */
object BankCopy {
    /** v2.4.4: positive = 存款／缺口; negative = 進帳（吃超）. */
    fun headline(balance: Double): Pair<String, String> = when {
        balance > 50 -> "存款（熱量缺口）" to "+${Calc.r0(balance)} kcal"
        balance < -50 -> "熱量進帳" to "−${Calc.r0(-balance)} kcal"
        else -> "存錢筒剛好打平" to "${Calc.r0(balance)} kcal"
    }

    fun color(balance: Double): Int = when {
        balance > 50 -> C.mint
        balance < -50 -> C.danger
        else -> C.text
    }

    fun tip(balance: Double): String {
        val stage = PigSprites.fromBalance(balance)
        return when (stage) {
            BodyStage.FAT3 -> BodyStage.FAT3.hint
            BodyStage.FAT2 -> BodyStage.FAT2.hint
            BodyStage.FAT1 -> BodyStage.FAT1.hint
            BodyStage.SLIM2 -> BodyStage.SLIM2.hint  // 太瘦：溫柔提醒多吃，不鼓勵繼續缺口
            BodyStage.SLIM1 -> BodyStage.SLIM1.hint  // 缺口約 500–1000：不鼓勵再加深
            BodyStage.NORMAL -> BodyStage.NORMAL.hint
        }
    }
}

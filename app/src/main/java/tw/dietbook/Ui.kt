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

object C {
    const val bg = 0xFF0F1412.toInt()
    const val card = 0xFF1A221E.toInt()
    const val card2 = 0xFF222C27.toInt()
    const val primary = 0xFF3DDC97.toInt()
    const val primaryDark = 0xFF1B9E6A.toInt()
    const val text = 0xFFF2F7F4.toInt()
    const val muted = 0xFF9BB0A4.toInt()
    const val danger = 0xFFFF6B6B.toInt()
    const val warn = 0xFFFFC857.toInt()
    const val line = 0xFF2A3831.toInt()
}

fun Activity.edge() {
    WindowCompat.setDecorFitsSystemWindows(window, false)
    window.statusBarColor = Color.TRANSPARENT
    window.navigationBarColor = Color.TRANSPARENT
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
            background = roundBg(C.card2, 10f, this)
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
    background = roundBg(C.card, 16f, this)
    setPadding(ctx.dp(14f), ctx.dp(14f), ctx.dp(14f), ctx.dp(14f))
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
    setTextColor(0xFF0A1A12.toInt())
    backgroundTintList = ColorStateList.valueOf(C.primary)
    background = roundBg(C.primary, 12f, this)
    setOnClickListener { onClick() }
    layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ctx.dp(48f)).apply {
        topMargin = ctx.dp(8f); bottomMargin = ctx.dp(4f)
    }
}

fun secondaryBtn(ctx: Activity, s: String, onClick: () -> Unit) = Button(ctx).apply {
    text = s
    setTextColor(C.text)
    background = roundBg(C.card2, 12f, this)
    setOnClickListener { onClick() }
    layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ctx.dp(48f)).apply {
        topMargin = ctx.dp(4f); bottomMargin = ctx.dp(4f)
    }
}

fun field(ctx: Activity, hint: String, value: String = ""): EditText = EditText(ctx).apply {
    setHint(hint); setText(value)
    setTextColor(C.text); setHintTextColor(C.muted)
    setBackgroundColor(C.card2)
    setPadding(ctx.dp(12f), ctx.dp(12f), ctx.dp(12f), ctx.dp(12f))
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

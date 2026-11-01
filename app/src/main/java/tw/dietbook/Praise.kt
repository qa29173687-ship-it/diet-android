package tw.dietbook

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.app.Activity
import android.app.Dialog
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.Gravity
import android.view.ViewGroup
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import kotlin.random.Random

object Praise {
    private val cardio = listOf(
        "好棒！今天又存了 %d 大卡！",
        "有動就有存款，小豬幫你記著 💪",
        "流汗的樣子超帥的啦～",
        "這次運動超讚，存款又進帳了！",
        "小豬都想跟你一起跑了～",
        "持續運動，小豬心情滿分！",
        "卡路里逃走中～抓得好！",
        "今日有氧成就解鎖！",
        "你的腳步聲，是存錢筒的音樂 🎵",
        "動起來的你，閃閃發亮 ✨",
    )

    private val strength = mapOf(
        "手臂" to listOf("手臂變壯了耶💪", "二頭肌在發光！", "手臂線條越來越明顯～", "再舉一下，小豬也想變壯！"),
        "胸" to listOf("胸肌有在長大喔！", "胸口氣勢滿分 💥", "今天胸部訓練超認真！", "小豬挺胸向你致敬！"),
        "背" to listOf("背肌展開了～好帥", "背部線條慢慢出來了！", "拉得漂亮！小豬鼓掌", "寬肩厚背正在路上～"),
        "肩" to listOf("肩膀圓滾滾變強壯！", "肩線越來越有型", "舉起來～肩部打卡成功", "小豬也想有寬肩 😎"),
        "腹" to listOf("腹肌馬路開工中！", "核心穩定，小豬佩服", "腹部訓練好確實～", "腹肌形狀慢慢浮現 ✨"),
        "腿" to listOf("腿力爆棚！蹲得好", "大腿在變壯喔💪", "腿部日，小豬也抖腳", "下盤穩了，走路都有風"),
        "臀" to listOf("臀部訓練超認真！", "翹臀計畫進度＋1", "臀腿連動好漂亮～", "小豬覺得你超有毅力"),
    )

    private val generalStrength = listOf(
        "鍛鍊完成！小豬為你跳起來了",
        "重訓打卡成功，變壯＋1",
        "汗水不會騙人，你超棒！",
        "今天的努力，小豬都看見了",
        "肌肉記憶＋1，繼續加油！",
        "認真的你最好看 💕",
    )

    private val streakBoost = listOf(
        "連續 %d 天了！小豬好驕傲～",
        "連續 %d 天運動／紀錄，徽章閃亮！",
        "堅持 %d 天，存錢筒為你鼓掌 👏",
    )

    fun cardioLine(kcal: Int, streak: Int): String {
        val base = cardio.random().let { if (it.contains("%d")) it.format(kcal) else it }
        return if (streak >= 3 && Random.nextBoolean()) streakBoost.random().format(streak) + "\n" + base else base
    }

    fun strengthLine(part: String, streak: Int): String {
        val pool = (strength[part] ?: emptyList()) + generalStrength
        val base = pool.random()
        return if (streak >= 3 && Random.nextBoolean()) streakBoost.random().format(streak) + "\n" + base else base
    }

    fun show(activity: Activity, message: String, pigRes: Int = 0, onDone: (() -> Unit)? = null) {
        val dlg = Dialog(activity)
        dlg.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        val root = FrameLayout(activity).apply {
            setPadding(activity.dp(24f), activity.dp(24f), activity.dp(24f), activity.dp(24f))
            setBackgroundColor(0x99000000.toInt())
        }
        val card = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            background = roundBg(C.card, 28f, this).also { it.setStroke(activity.dp(2f), C.line) }
            setPadding(activity.dp(20f), activity.dp(20f), activity.dp(20f), activity.dp(20f))
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { gravity = Gravity.CENTER }
        }
        val stage = CuteFx.currentStage(activity)
        val pig = ImageView(activity).apply {
            if (pigRes != 0) setImageResource(pigRes)
            else setImageBitmap(PigSprites.get(activity, stage, PigSprites.BOUNCE_HAPPY))
            adjustViewBounds = true
            scaleType = ImageView.ScaleType.FIT_CENTER
            layoutParams = LinearLayout.LayoutParams(activity.dp(180f), activity.dp(180f)).apply {
                gravity = Gravity.CENTER_HORIZONTAL
            }
        }
        val hearts = TextView(activity).apply {
            text = "💕 ⭐ 💪 ✨ 💕"
            textSize = 22f
            gravity = Gravity.CENTER
            setTextColor(C.primary)
        }
        val msg = TextView(activity).apply {
            text = message
            textSize = 18f
            gravity = Gravity.CENTER
            setTextColor(C.text)
            setPadding(0, activity.dp(8f), 0, activity.dp(12f))
            paint.isFakeBoldText = true
        }
        val ok = primaryBtn(activity, "耶！謝謝小豬") {
            dlg.dismiss()
            onDone?.invoke()
        }
        card.addView(hearts)
        card.addView(pig)
        card.addView(msg)
        card.addView(ok)
        root.addView(card)
        // confetti dots
        repeat(12) {
            val dot = TextView(activity).apply {
                text = listOf("💕", "⭐", "✨", "💖", "🌟").random()
                textSize = 18f
                layoutParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply {
                    leftMargin = (40..activity.dp(280f)).random()
                    topMargin = (80..activity.dp(420f)).random()
                }
            }
            root.addView(dot)
            ObjectAnimator.ofFloat(dot, "translationY", 0f, 120f + Random.nextInt(80)).apply {
                duration = 900L + Random.nextLong(600)
                start()
            }
            ObjectAnimator.ofFloat(dot, "alpha", 1f, 0f).apply {
                duration = 1200L; start()
            }
        }
        dlg.setContentView(root)
        dlg.setCancelable(true)
        dlg.setOnDismissListener { onDone?.invoke() }
        dlg.show()
        dlg.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        val jump = ObjectAnimator.ofFloat(pig, "translationY", 0f, -36f, 0f, -18f, 0f).apply {
            duration = 900; interpolator = OvershootInterpolator(2f)
        }
        val scaleX = ObjectAnimator.ofFloat(pig, "scaleX", 0.7f, 1.15f, 1f)
        val scaleY = ObjectAnimator.ofFloat(pig, "scaleY", 0.7f, 1.15f, 1f)
        AnimatorSet().apply {
            playTogether(jump, scaleX, scaleY)
            duration = 700
            start()
        }
    }
}

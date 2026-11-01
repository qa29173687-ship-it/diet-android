package tw.dietbook

import android.os.Bundle
import android.text.InputType
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class StrengthActivity : AppCompatActivity() {
    private lateinit var body: LinearLayout
    private var selectedPart = Calc.STRENGTH_PARTS.first()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        edge()
        val (root, b) = col(this)
        body = b
        setContentView(root)
        EdgeInsets.pad(root, ime = true)
        render()
    }

    override fun onResume() {
        super.onResume()
        if (::body.isInitialized) render()
    }

    private fun render() {
        body.removeAllViews()
        body.addView(titleBar(this, "鍛鍊", "重訓部位會讓小豬變壯", onBack = { finish() }))

        val levels = Store.partLevels(this)
        val levelCard = card(this)
        levelCard.addView(label(this, "小豬部位等級（累計分鐘）", 12f))
        val mins = Store.partMinutes(this)
        Calc.STRENGTH_PARTS.chunked(4).forEach { rowParts ->
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            rowParts.forEach { p ->
                val lv = levels[p] ?: 0
                val tv = label(this, "$p\nLv.$lv\n${mins[p] ?: 0}分", 11f, if (lv > 0) C.primaryDark else C.muted).apply {
                    gravity = android.view.Gravity.CENTER
                    background = roundBg(if (p == selectedPart) C.card2 else C.card, 12f, this)
                    setPadding(dp(4f), dp(8f), dp(4f), dp(8f))
                    layoutParams = LinearLayout.LayoutParams(0, dp(64f), 1f).apply {
                        marginStart = dp(2f); marginEnd = dp(2f)
                    }
                    setOnClickListener { selectedPart = p; render() }
                }
                row.addView(tv)
            }
            levelCard.addView(row)
        }
        body.addView(levelCard)

        body.addView(label(this, "選擇部位", 12f))
        val partRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        Calc.STRENGTH_PARTS.forEach { p ->
            val on = p == selectedPart
            partRow.addView(secondaryBtn(this, p) {
                selectedPart = p; render()
            }.apply {
                layoutParams = LinearLayout.LayoutParams(0, dp(42f), 1f).apply {
                    marginStart = dp(1f); marginEnd = dp(1f)
                }
                textSize = 11f
                if (on) {
                    background = roundBg(C.primary, 12f, this)
                    setTextColor(C.onPrimary)
                }
            })
        }
        body.addView(partRow)

        val nameF = field(this, "動作名稱，例如：二頭彎舉、深蹲", "")
        body.addView(label(this, "動作", 12f))
        body.addView(nameF)

        val setsF = field(this, "組數", "3").apply { inputType = InputType.TYPE_CLASS_NUMBER }
        val minF = field(this, "分鐘", "20").apply { inputType = InputType.TYPE_CLASS_NUMBER }
        body.addView(label(this, "組數", 12f)); body.addView(setsF)
        body.addView(label(this, "時長（分鐘）", 12f)); body.addView(minF)

        val u = Store.profile(this)
        val met = Calc.strengthMet(selectedPart)
        val est = Calc.metKcal(met, u.weightKg, 20)
        val kcalF = field(this, "消耗 kcal（可改）", Calc.r0(est).toString()).apply {
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
        }
        body.addView(label(this, "預估消耗（MET $met）", 12f))
        body.addView(kcalF)
        minF.setOnFocusChangeListener { _, _ ->
            val m = Numbers.intOrNull(minF.text.toString()) ?: return@setOnFocusChangeListener
            kcalF.setText(Calc.r0(Calc.metKcal(met, u.weightKg, m)).toString())
        }

        body.addView(primaryBtn(this, "完成鍛鍊！") {
            val sets = Numbers.intOrNull(setsF.text.toString()) ?: 0
            val minutes = Numbers.intOrNull(minF.text.toString()) ?: 0
            val kcal = Numbers.doubleOrNull(kcalF.text.toString()) ?: 0.0
            val name = nameF.text.toString().ifBlank { "${selectedPart}訓練" }
            if (minutes <= 0) {
                Toast.makeText(this, "請填分鐘", Toast.LENGTH_SHORT).show()
                return@primaryBtn
            }
            Store.addStrength(
                this,
                StrengthLog(
                    id = System.currentTimeMillis(),
                    day = Days.today(),
                    part = selectedPart,
                    exercise = name,
                    sets = sets.coerceAtLeast(0),
                    minutes = minutes,
                    kcal = kcal.coerceAtLeast(0.0),
                    at = System.currentTimeMillis(),
                )
            )
            Store.awardBadge(this, "first_strength")
            Store.bumpStreak(this)
            Outfits.maybeUnlockFromStreak(this)
            val lvl = Store.partLevels(this)[selectedPart] ?: 0
            if (lvl >= 2) Store.awardBadge(this, "part_$selectedPart")
            val streak = Store.profile(this).streakDays
            val maxed = lvl >= 3 && selectedPart !in Store.posedParts(this)
            Sfx.pour(this)
            fun afterPour() {
                fun afterPose() {
                    Praise.show(this, Praise.strengthLine(selectedPart, streak), R.drawable.pig_buff) {
                        finish()
                    }
                }
                if (maxed) {
                    Store.markPosedPart(this, selectedPart)
                    CuteFx.play(this, CuteScene.POSING, label = "${selectedPart}滿等！小豬上台秀一下～") { afterPose() }
                } else afterPose()
            }
            CuteFx.play(this, CuteScene.HANDS_POUR, kcal = kcal.coerceAtLeast(0.0)) { afterPour() }
        })

        body.addView(label(this, "今日鍛鍊", 15f, C.text).apply {
            setPadding(0, dp(12f), 0, dp(6f)); paint.isFakeBoldText = true
        })
        val logs = Store.strengthLogs(this, Days.today())
        if (logs.isEmpty()) body.addView(label(this, "還沒有重訓紀錄。", 14f))
        else {
            logs.forEach { log ->
                val row = card(this).apply {
                    setPadding(dp(12f), dp(10f), dp(12f), dp(10f))
                    (layoutParams as LinearLayout.LayoutParams).bottomMargin = dp(8f)
                }
                row.addView(TextView(this).apply {
                    text = "【${log.part}】${log.exercise}"
                    setTextColor(C.text); textSize = 16f; paint.isFakeBoldText = true
                })
                row.addView(label(this, "${log.sets} 組・${log.minutes} 分・${Calc.r0(log.kcal)} kcal"))
                row.setOnLongClickListener {
                    Store.deleteStrength(this, log.day, log.id)
                    Toast.makeText(this, "已刪除", Toast.LENGTH_SHORT).show()
                    render(); true
                }
                body.addView(row)
            }
            body.addView(label(this, "長按可刪除", 12f))
        }
    }
}

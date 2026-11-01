package tw.dietbook

import android.os.Bundle
import android.text.InputType
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class CardioActivity : AppCompatActivity() {
    private lateinit var body: LinearLayout
    private var selected = Calc.CARDIO_TYPES.first()

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
        body.addView(titleBar(this, "運動", "有氧・分鐘換算熱量", onBack = { finish() }))
        body.addView(label(this, "選一種運動，輸入分鐘。熱量＝MET × 體重 × 小時，可再手動改。", 13f))

        val u = Store.profile(this)
        val typeCard = card(this)
        typeCard.addView(label(this, "運動種類", 12f))
        val grid = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        Calc.CARDIO_TYPES.chunked(2).forEach { rowTypes ->
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            rowTypes.forEach { t ->
                val on = t.id == selected.id
                val btn = secondaryBtn(this, "${t.label}\nMET ${t.met}") {
                    selected = t; render()
                }.apply {
                    layoutParams = LinearLayout.LayoutParams(0, dp(56f), 1f).apply {
                        marginStart = dp(2f); marginEnd = dp(2f)
                    }
                    if (on) {
                        background = roundBg(C.primary, 14f, this)
                        setTextColor(C.onPrimary)
                    }
                }
                row.addView(btn)
            }
            if (rowTypes.size == 1) {
                row.addView(LinearLayout(this).apply {
                    layoutParams = LinearLayout.LayoutParams(0, dp(56f), 1f)
                })
            }
            grid.addView(row)
        }
        typeCard.addView(grid)
        body.addView(typeCard)

        val minutesF = field(this, "分鐘", "30").apply {
            inputType = InputType.TYPE_CLASS_NUMBER
        }
        body.addView(label(this, "時長（分鐘）", 12f))
        body.addView(minutesF)

        val est = Calc.metKcal(selected.met, u.weightKg, 30)
        val kcalF = field(this, "消耗 kcal（可改）", Calc.r0(est).toString()).apply {
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
        }
        body.addView(label(this, "預估消耗（依目前體重 ${Calc.r1(u.weightKg)} kg）", 12f))
        body.addView(kcalF)

        minutesF.setOnFocusChangeListener { _, _ ->
            val m = Numbers.intOrNull(minutesF.text.toString()) ?: return@setOnFocusChangeListener
            val k = Calc.metKcal(selected.met, u.weightKg, m)
            kcalF.setText(Calc.r0(k).toString())
        }

        body.addView(label(this, "規則：有手動運動時，今日活動消耗以手動總和為準，不再加 Health Connect，避免算兩次。", 12f, C.muted))

        body.addView(primaryBtn(this, "存下來，讓小豬稱讚我") {
            val minutes = Numbers.intOrNull(minutesF.text.toString()) ?: 0
            val kcal = Numbers.doubleOrNull(kcalF.text.toString()) ?: 0.0
            if (minutes <= 0 || kcal <= 0) {
                Toast.makeText(this, "請填分鐘與熱量", Toast.LENGTH_SHORT).show()
                return@primaryBtn
            }
            val day = Days.today()
            Store.addCardio(
                this,
                CardioLog(
                    id = System.currentTimeMillis(),
                    day = day,
                    type = selected.id,
                    label = selected.label,
                    minutes = minutes,
                    kcal = kcal,
                    met = selected.met,
                    at = System.currentTimeMillis(),
                )
            )
            Store.awardBadge(this, "first_cardio")
            Store.bumpStreak(this)
            Outfits.maybeUnlockFromStreak(this)
            val streak = Store.profile(this).streakDays
            Sfx.pour(this)
            CuteFx.play(this, CuteScene.HANDS_POUR, kcal = kcal) {
                Praise.show(this, Praise.cardioLine(Calc.r0(kcal), streak), R.drawable.pig_workout) {
                    finish()
                }
            }
        })

        body.addView(label(this, "今日運動", 15f, C.text).apply {
            setPadding(0, dp(12f), 0, dp(6f)); paint.isFakeBoldText = true
        })
        val logs = Store.cardioLogs(this, Days.today())
        if (logs.isEmpty()) {
            body.addView(label(this, "還沒有運動紀錄。", 14f))
        } else {
            logs.forEach { log ->
                val row = card(this).apply {
                    setPadding(dp(12f), dp(10f), dp(12f), dp(10f))
                    (layoutParams as LinearLayout.LayoutParams).bottomMargin = dp(8f)
                }
                row.addView(TextView(this).apply {
                    text = "${log.label}　${log.minutes} 分"
                    setTextColor(C.text); textSize = 16f; paint.isFakeBoldText = true
                })
                row.addView(label(this, "消耗 ${Calc.r0(log.kcal)} kcal　MET ${log.met}"))
                row.setOnLongClickListener {
                    Store.deleteCardio(this, log.day, log.id)
                    Toast.makeText(this, "已刪除", Toast.LENGTH_SHORT).show()
                    render(); true
                }
                body.addView(row)
            }
            body.addView(label(this, "長按可刪除　今日有氧合計 ${Calc.r0(Store.cardioKcal(this, Days.today()))} kcal", 12f))
        }
    }
}

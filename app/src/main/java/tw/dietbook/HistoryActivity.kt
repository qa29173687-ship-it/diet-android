package tw.dietbook

import android.os.Bundle
import android.widget.LinearLayout
import android.widget.TextView
import com.google.android.material.snackbar.Snackbar
import androidx.appcompat.app.AppCompatActivity

/** Past / today food·cardio·strength logs with trash + confirm + 復原. */
class HistoryActivity : AppCompatActivity() {
    private lateinit var body: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        edge()
        val (root, b) = col(this)
        body = b
        setContentView(root)
        EdgeInsets.pad(root)
        render()
    }

    override fun onResume() {
        super.onResume()
        if (::body.isInitialized) render()
    }

    private fun render() {
        body.removeAllViews()
        body.addView(titleBar(this, "紀錄明細", "點 🗑 可刪錯帳，支援復原", onBack = { finish() }))
        body.addView(label(this, "含今天與近 14 天。刪過去的日子會同步調整小豬總存款。", 12f, C.muted))

        val today = Days.today()
        var d = Days.parse(today)
        var shown = 0
        for (i in 0 until 14) {
            val day = d.toString()
            val foods = Store.logs(this, day)
            val cardio = Store.cardioLogs(this, day)
            val strength = Store.strengthLogs(this, day)
            if (foods.isEmpty() && cardio.isEmpty() && strength.isEmpty()) {
                d = d.minusDays(1)
                continue
            }
            shown++
            body.addView(label(this, Days.label(day) + if (day == today) "（今天）" else "", 15f, C.text).apply {
                setPadding(0, dp(14f), 0, dp(6f)); paint.isFakeBoldText = true
            })
            foods.forEach { log ->
                addRow(
                    title = log.foodName,
                    sub = "${Calc.r0(log.grams)} g・${Calc.r0(log.kcal)} kcal・蛋白 ${Calc.r1(log.protein)} g",
                    onTrash = {
                        confirm(
                            "刪掉這筆飲食？",
                            "「${log.foodName}」會從小豬帳本拿掉喔～",
                        ) {
                            LogActions.deleteFood(this, log.day, log.id)?.let { DeletedPayload.Food(it) }
                        }
                    },
                )
            }
            cardio.forEach { log ->
                addRow(
                    title = "🏃 ${log.label}　${log.minutes} 分",
                    sub = "消耗 ${Calc.r0(log.kcal)} kcal",
                    onTrash = {
                        confirm(
                            "刪掉這筆運動？",
                            "「${log.label}」會取消當日消耗計算喔～",
                        ) {
                            LogActions.deleteCardio(this, log.day, log.id)?.let { DeletedPayload.Cardio(it) }
                        }
                    },
                )
            }
            strength.forEach { log ->
                addRow(
                    title = "🏋️ 【${log.part}】${log.exercise}",
                    sub = "${log.sets} 組・${log.minutes} 分・${Calc.r0(log.kcal)} kcal",
                    onTrash = {
                        confirm(
                            "刪掉這筆鍛鍊？",
                            "「${log.part}・${log.exercise}」真的要刪嗎？",
                        ) {
                            LogActions.deleteStrength(this, log.day, log.id)?.let { DeletedPayload.Strength(it) }
                        }
                    },
                )
            }
            if (Store.hcIgnored(this, day)) {
                body.addView(label(this, "🙈 本日已忽略 Health Connect", 12f, C.muted))
            }
            d = d.minusDays(1)
        }
        if (shown == 0) {
            body.addView(label(this, "近兩週還沒有可刪的紀錄。", 14f))
        }
    }

    private fun addRow(title: String, sub: String, onTrash: () -> Unit) {
        val row = card(this).apply {
            setPadding(dp(12f), dp(10f), dp(12f), dp(10f))
            (layoutParams as LinearLayout.LayoutParams).bottomMargin = dp(8f)
        }
        row.addView(TextView(this).apply {
            text = title
            setTextColor(C.text); textSize = 15f; paint.isFakeBoldText = true
        })
        row.addView(label(this, sub))
        row.addView(TextView(this).apply {
            text = "🗑 刪除"
            setTextColor(C.danger)
            textSize = 13f
            setPadding(0, dp(6f), 0, 0)
            setOnClickListener { onTrash() }
        })
        body.addView(row)
    }

    private fun confirm(title: String, message: String, action: () -> DeletedPayload?) {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("🐷 $title")
            .setMessage(message)
            .setPositiveButton("刪掉") { _, _ ->
                val payload = action()
                render()
                if (payload != null) {
                    Snackbar.make(findViewById(android.R.id.content), LogActions.label(payload), Snackbar.LENGTH_LONG)
                        .setAction("復原") {
                            LogActions.restore(this, payload)
                            render()
                        }.show()
                }
            }
            .setNegativeButton("先留著", null)
            .show()
    }
}

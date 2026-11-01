package tw.dietbook

import android.os.Bundle
import android.view.Gravity
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

class BalanceActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        edge()
        val (root, body) = col(this)
        setContentView(root)
        EdgeInsets.pad(root, ime = true)
        body.addView(titleBar(this, "小豬存摺", "每日淨值會存進撲滿，不週清", onBack = { finish() }))

        lifecycleScope.launch {
            val (u, snap) = Balance.refresh(this@BalanceActivity)
            render(body, u, snap)
        }
    }

    private fun render(body: android.widget.LinearLayout, u: UserProfile, snap: DaySnapshot) {
        body.removeAllViews()
        body.addView(titleBar(this, "小豬存摺", "每日淨值會存進撲滿，不週清", onBack = { finish() }))

        val pig = PigMascotView(this)
        pig.layoutParams = android.widget.LinearLayout.LayoutParams(dp(220f), dp(200f)).apply {
            gravity = Gravity.CENTER_HORIZONTAL
        }
        pig.setBalance(u.balance, animate = false)
        body.addView(pig)

        val bal = card(this)
        val (headline, amount) = BankCopy.headline(u.balance)
        bal.addView(label(this, headline, 12f))
        bal.addView(value(this, amount, 34f, BankCopy.color(u.balance)))
        bal.addView(label(this, "約 ${"%.2f".format(Calc.fatKg(u.balance))} kg 體脂肪等價（7700 kcal ≈ 1 kg）"))
        bal.addView(label(this, BankCopy.tip(u.balance), 13f, C.text))
        bal.addView(label(this, "說明：進帳＝吃超過消耗（小豬變圓）；存款／缺口＝吃少於消耗（小豬變壯）。粗估而已，個體差很大。", 12f, C.muted))
        body.addView(bal)

        val today = card(this)
        today.addView(label(this, "今日尚未入帳的淨值（跨日才定案）", 12f))
        val todayTxt = when {
            snap.net > 0 -> "進帳 +${Calc.r0(snap.net)} kcal"
            snap.net < 0 -> "存款 +${Calc.r0(-snap.net)} kcal"
            else -> "打平 0 kcal"
        }
        today.addView(value(this, todayTxt, 24f, BankCopy.color(snap.net)))
        val burnTxt = when (snap.burnSource) {
            "manual" -> "手動活動 ${Calc.r0(snap.manualBurn)}"
            "health_connect" -> "HC 活動 ${Calc.r0(snap.activeBurn ?: 0.0)}"
            else -> if (snap.activeBurn != null) "活動 ${Calc.r0(snap.activeBurn)}" else "TDEE ${Calc.r0(snap.tdeeFallback)}"
        }
        today.addView(label(this, "攝取 ${Calc.r0(snap.intakeKcal)}、BMR ${Calc.r0(snap.bmr)}、$burnTxt"))
        body.addView(today)

        if (u.balance > 50) {
            val plan = card(this)
            plan.addView(label(this, "進帳還款計畫", 14f, C.text))
            val daysF = field(this, "天數", u.paybackDays.toString())
            plan.addView(label(this, "想在幾天內把進帳還完？"))
            plan.addView(daysF)
            val out = label(this, "", 15f, C.warn)
            fun upd() {
                val n = daysF.text.toString().toIntOrNull()?.coerceAtLeast(1) ?: 7
                val per = Calc.paybackPerDay(u.balance, n)
                out.text = "每天需比「維持體重」再少約 ${Calc.r0(per)} kcal\n" +
                    "（例如目標吃 ${Calc.r0((snap.activeBurn?.let { u.bmr + it } ?: snap.tdeeFallback) - per)} kcal／日）\n" +
                    "量力而為，小豬不催你。"
                Store.saveProfile(this, Store.profile(this).copy(paybackDays = n))
            }
            daysF.addTextChangedListener(SimpleWatcher { upd() })
            plan.addView(out)
            upd()
            body.addView(plan)
        } else {
            body.addView(card(this).apply {
                addView(label(this@BalanceActivity, "目前沒有熱量進帳，無需還款。持續有缺口＝存款變多，小豬會更壯（請注意營養與健康）。", 13f, C.text))
            })
        }

        body.addView(label(this, "手動調整餘額（校正用）", 13f))
        val adj = field(this, "設為多少 kcal（可負數＝缺口）", Calc.r0(u.balance).toString())
        body.addView(adj)
        body.addView(secondaryBtn(this, "覆寫餘額") {
            val v = adj.text.toString().toDoubleOrNull()
            if (v == null) { Toast.makeText(this, "請輸入數字", Toast.LENGTH_SHORT).show(); return@secondaryBtn }
            Balance.setBalance(this, v)
            Toast.makeText(this, "已更新", Toast.LENGTH_SHORT).show()
            recreate()
        })
        body.addView(label(this, "誠實提醒：體重受水分、醣原、測量誤差影響；熱量帳只是覺察工具，不是醫囑。", 12f, C.muted))
    }
}

package tw.dietbook

import android.os.Bundle
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
        body.addView(titleBar(this, "熱量帳戶", "每日淨值持續累加，不週清", onBack = { finish() }))

        lifecycleScope.launch {
            val (u, snap) = Balance.refresh(this@BalanceActivity)
            render(body, u, snap)
        }
    }

    private fun render(body: android.widget.LinearLayout, u: UserProfile, snap: DaySnapshot) {
        body.removeAllViews()
        body.addView(titleBar(this, "熱量帳戶", "每日淨值持續累加，不週清", onBack = { finish() }))

        val bal = card(this)
        val col = if (u.balance > 0) C.danger else if (u.balance < 0) C.primary else C.text
        bal.addView(label(this, "目前餘額", 12f))
        bal.addView(value(this, "${if (u.balance > 0) "+" else ""}${Calc.r0(u.balance)} kcal", 34f, col))
        bal.addView(label(this, "約 ${"%.2f".format(Calc.fatKg(u.balance))} kg 體脂肪等價（7700 kcal ≈ 1 kg）"))
        bal.addView(label(this, "說明：正數≈熱量盈餘（可能增脂），負數≈赤字（可能減脂）。此為粗估，個體差異大。", 12f, C.warn))
        body.addView(bal)

        val today = card(this)
        today.addView(label(this, "今日尚未入帳的淨值（跨日才定案累加）", 12f))
        today.addView(value(this, "${if (snap.net > 0) "+" else ""}${Calc.r0(snap.net)} kcal", 24f))
        today.addView(label(this, "攝取 ${Calc.r0(snap.intakeKcal)}、BMR ${Calc.r0(snap.bmr)}、" +
            if (snap.activeBurn != null) "活動 ${Calc.r0(snap.activeBurn)}" else "TDEE ${Calc.r0(snap.tdeeFallback)}"))
        body.addView(today)

        if (u.balance > 0) {
            val plan = card(this)
            plan.addView(label(this, "盈餘還款計畫", 14f, C.text))
            val daysF = field(this, "天數", u.paybackDays.toString())
            plan.addView(label(this, "想在幾天內打平到 0？"))
            plan.addView(daysF)
            val out = label(this, "", 15f, C.warn)
            fun upd() {
                val n = daysF.text.toString().toIntOrNull()?.coerceAtLeast(1) ?: 7
                val per = Calc.paybackPerDay(u.balance, n)
                out.text = "每天需比「維持體重」再少約 ${Calc.r0(per)} kcal\n" +
                    "（例如目標吃 ${Calc.r0((snap.activeBurn?.let { u.bmr + it } ?: snap.tdeeFallback) - per)} kcal／日）\n" +
                    "這只是算術估算，請量力而為。"
                Store.saveProfile(this, Store.profile(this).copy(paybackDays = n))
            }
            daysF.addTextChangedListener(SimpleWatcher { upd() })
            plan.addView(out)
            upd()
            body.addView(plan)
        } else {
            body.addView(card(this).apply {
                addView(label(this@BalanceActivity, "目前沒有盈餘，無需還款。持續保持赤字有助減脂（請注意營養與健康）。", 13f, C.text))
            })
        }

        body.addView(label(this, "手動調整餘額（例如校正）", 13f))
        val adj = field(this, "設為多少 kcal（可負數）", Calc.r0(u.balance).toString())
        body.addView(adj)
        body.addView(secondaryBtn(this, "覆寫餘額") {
            val v = adj.text.toString().toDoubleOrNull()
            if (v == null) { Toast.makeText(this, "請輸入數字", Toast.LENGTH_SHORT).show(); return@secondaryBtn }
            Balance.setBalance(this, v)
            Toast.makeText(this, "已更新", Toast.LENGTH_SHORT).show()
            recreate()
        })
        body.addView(label(this, "誠實提醒：體重變化受水分、醣原、測量誤差影響；熱量帳只是幫助覺察的工具，不是醫囑。", 12f, C.muted))
    }
}

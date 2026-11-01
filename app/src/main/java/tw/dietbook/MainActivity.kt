package tw.dietbook

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.health.connect.client.PermissionController
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {
    private lateinit var body: LinearLayout
    private var healthMsg = ""
    private var pig: PigMascotView? = null

    private val hcPerms = registerForActivityResult(
        PermissionController.createRequestPermissionResultContract()
    ) { granted ->
        if (granted.containsAll(Health.perms)) refresh()
        else {
            healthMsg = "未授權 Health Connect，改用活動量估算 TDEE"
            refresh()
        }
    }

    private val notifPerm = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        edge()
        if (!Store.profile(this).setupDone) {
            startActivity(Intent(this, SetupActivity::class.java))
            finish(); return
        }
        val (root, b) = col(this)
        body = b
        setContentView(root)
        EdgeInsets.pad(root, ime = false)
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) notifPerm.launch(Manifest.permission.POST_NOTIFICATIONS)
        maybeAskHealth()
    }

    override fun onResume() {
        super.onResume()
        if (Store.profile(this).setupDone) refresh()
    }

    private fun maybeAskHealth() {
        val u = Store.profile(this)
        if (!u.hcEnabled) return
        if (!Health.available(this)) {
            healthMsg = when (Health.status(this)) {
                androidx.health.connect.client.HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED ->
                    "尚未安裝 Health Connect（可在設定安裝）"
                else -> "此裝置無 Health Connect，改用活動量估算"
            }
            return
        }
        lifecycleScope.launch {
            if (!Health.granted(this@MainActivity)) {
                try { hcPerms.launch(Health.perms) } catch (_: Exception) {
                    healthMsg = "無法開啟 Health Connect 授權"
                }
            }
        }
    }

    fun refresh() {
        lifecycleScope.launch {
            val (u, snap) = try {
                Balance.refresh(this@MainActivity)
            } catch (e: Exception) {
                Toast.makeText(this@MainActivity, "更新失敗：${e.message}", Toast.LENGTH_SHORT).show()
                val p = Store.profile(this@MainActivity)
                val (k, pr) = Store.dayTotals(this@MainActivity, Days.today())
                p to DaySnapshot(Days.today(), k, pr, p.bmr, null, Calc.tdee(p.bmr, p.activity), Calc.dayNet(k, p.bmr, null, Calc.tdee(p.bmr, p.activity)))
            }
            render(u, snap)
        }
    }

    private fun render(u: UserProfile, snap: DaySnapshot) {
        body.removeAllViews()
        body.addView(titleBar(this, "熱量存錢筒", Days.label(snap.day)))

        // —— Hero: pig + big balance ——
        val hero = card(this)
        hero.gravity = Gravity.CENTER_HORIZONTAL
        val pigView = PigMascotView(this).also { pig = it }
        pigView.layoutParams = LinearLayout.LayoutParams(dp(260f), dp(240f)).apply {
            gravity = Gravity.CENTER_HORIZONTAL
        }
        // Include today's unsettled net so pig reacts live
        val liveBalance = u.balance + snap.net
        pigView.setBalance(liveBalance, animate = true)
        hero.addView(pigView)

        val (headline, amount) = BankCopy.headline(liveBalance)
        hero.addView(label(this, headline, 14f, C.muted).apply {
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(0, dp(4f), 0, 0)
        })
        hero.addView(value(this, amount, 36f, BankCopy.color(liveBalance)).apply {
            gravity = Gravity.CENTER_HORIZONTAL
        })
        hero.addView(label(this, BankCopy.tip(liveBalance), 13f, C.text).apply {
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(8f), dp(4f), dp(8f), 0)
        })
        hero.addView(label(this, "含今日尚未入帳淨值；點此看存摺與還款計畫", 11f, C.muted).apply {
            gravity = Gravity.CENTER_HORIZONTAL
        })
        hero.setOnClickListener { startActivity(Intent(this, BalanceActivity::class.java)) }
        body.addView(hero)

        // —— Today intake ——
        val goal = card(this)
        goal.addView(label(this, "今日放進肚子裡的", 12f))
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        row.addView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            addView(value(this@MainActivity, "${Calc.r0(snap.intakeKcal)}", 24f, C.primary))
            addView(label(this@MainActivity, "／ ${Calc.r0(u.goalKcal)} kcal"))
            addView(progress(this@MainActivity, ((snap.intakeKcal / u.goalKcal.coerceAtLeast(1.0)) * 100).toInt()))
        })
        row.addView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            addView(value(this@MainActivity, "${Calc.r1(snap.protein)}", 24f, C.warn))
            addView(label(this@MainActivity, "／ ${Calc.r0(u.goalProtein)} g 蛋白"))
            addView(progress(this@MainActivity, ((snap.protein / u.goalProtein.coerceAtLeast(1.0)) * 100).toInt(), C.warn))
        })
        goal.addView(row)
        body.addView(goal)

        // —— Today net ——
        val netCard = card(this)
        val mode = if (snap.activeBurn != null) "攝取 − BMR − 活動（Health Connect）"
        else "攝取 − TDEE（${Calc.activityLabel(u.activity)}）"
        netCard.addView(label(this, "今日淨值（明天會存進撲滿）", 12f))
        val netColor = if (snap.net > 0) C.danger else if (snap.net < 0) C.mint else C.text
        val netLabel = when {
            snap.net > 0 -> "進帳 +${Calc.r0(snap.net)} kcal"
            snap.net < 0 -> "存款 +${Calc.r0(-snap.net)} kcal"
            else -> "打平 0 kcal"
        }
        netCard.addView(value(this, netLabel, 24f, netColor))
        netCard.addView(label(this, mode))
        val detail = buildString {
            append("攝取 ${Calc.r0(snap.intakeKcal)} − BMR ${Calc.r0(snap.bmr)}")
            if (snap.activeBurn != null) append(" − 活動 ${Calc.r0(snap.activeBurn)}")
            else append("（TDEE ${Calc.r0(snap.tdeeFallback)}）")
            snap.steps?.let { append("　步數 $it") }
        }
        netCard.addView(label(this, detail, 12f, C.muted))
        if (healthMsg.isNotEmpty()) netCard.addView(label(this, healthMsg, 12f, C.warn))
        if (u.balance > 50) {
            val per = Calc.paybackPerDay(u.balance, u.paybackDays.coerceAtLeast(1))
            netCard.addView(label(this, "還款小提醒：${u.paybackDays} 天打平 → 每天約再少 ${Calc.r0(per)} kcal", 12f, C.warn))
        }
        body.addView(netCard)

        body.addView(primaryBtn(this, "＋ 記一筆飲食") {
            startActivity(Intent(this, SearchActivity::class.java))
        })
        val actions = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        fun chip(t: String, act: () -> Unit) = secondaryBtn(this, t, act).also {
            it.layoutParams = LinearLayout.LayoutParams(0, dp(48f), 1f).apply {
                marginStart = dp(2f); marginEnd = dp(2f)
            }
            actions.addView(it)
        }
        chip("條碼") { startActivity(Intent(this, BarcodeScanActivity::class.java)) }
        chip("標示／自訂") { startActivity(Intent(this, CustomFoodActivity::class.java)) }
        chip("存摺") { startActivity(Intent(this, BalanceActivity::class.java)) }
        chip("設定") { startActivity(Intent(this, SettingsActivity::class.java)) }
        body.addView(actions)

        body.addView(label(this, "今日紀錄", 15f, C.text).apply {
            setPadding(0, dp(12f), 0, dp(6f)); paint.isFakeBoldText = true
        })
        val logs = Store.logs(this, snap.day)
        if (logs.isEmpty()) {
            body.addView(label(this, "還沒有紀錄。點上面幫小豬記一筆吧～", 14f))
        } else {
            logs.forEach { log ->
                val rowL = card(this).apply {
                    setPadding(dp(12f), dp(10f), dp(12f), dp(10f))
                    (layoutParams as LinearLayout.LayoutParams).bottomMargin = dp(8f)
                }
                rowL.addView(TextView(this).apply {
                    text = log.foodName; setTextColor(C.text); textSize = 16f; paint.isFakeBoldText = true
                })
                rowL.addView(label(this, "${Calc.r0(log.grams)} g　${Calc.r0(log.kcal)} kcal　蛋白 ${Calc.r1(log.protein)} g"))
                rowL.setOnLongClickListener {
                    Store.deleteLog(this, log.day, log.id)
                    Toast.makeText(this, "已刪除", Toast.LENGTH_SHORT).show()
                    refresh(); true
                }
                body.addView(rowL)
            }
            body.addView(label(this, "長按可刪除單筆", 12f))
        }

        body.addView(label(this, Tfnd.ATTRIBUTION, 11f, C.muted).apply {
            setPadding(0, dp(16f), 0, dp(8f))
        })
    }
}

package tw.dietbook

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.health.connect.client.PermissionController
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

/**
 * 電子雞首頁：場地裡只有小豬遊走，底部三大鍵「吃／運動／鍛鍊」。
 */
class MainActivity : AppCompatActivity() {
    private lateinit var rootPad: LinearLayout
    private lateinit var arena: FrameLayout
    private lateinit var pig: PigMascotView
    private lateinit var balLabel: TextView
    private lateinit var balValue: TextView
    private lateinit var vitalsRow: LinearLayout
    private lateinit var hint: TextView
    private var healthMsg = ""

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

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(C.bg)
        }
        rootPad = root
        setContentView(root)
        EdgeInsets.pad(root, ime = false)

        // top bar: balance + settings
        val top = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16f), dp(8f), dp(12f), dp(4f))
        }
        val balCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        balLabel = label(this, "存錢筒", 12f)
        balValue = value(this, "—", 22f, C.text)
        balCol.addView(balLabel)
        balCol.addView(balValue)
        balCol.setOnClickListener { startActivity(Intent(this, BalanceActivity::class.java)) }
        top.addView(balCol)
        top.addView(secondaryBtn(this, "設定") {
            startActivity(Intent(this, SettingsActivity::class.java))
        }.apply {
            layoutParams = LinearLayout.LayoutParams(dp(88f), dp(40f))
        })
        root.addView(top)

        // vitals
        vitalsRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(16f), 0, dp(16f), dp(4f))
        }
        root.addView(vitalsRow)

        // arena — pig wanders here; weight 1 so buttons stay visible
        arena = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f
            )
            setBackgroundColor(0xFFFFF0F5.toInt())
        }
        pig = PigMascotView(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
            )
        }
        arena.addView(pig)
        hint = label(this, "點小豬會理你｜長按下方按鈕看說明", 11f, C.muted).apply {
            gravity = Gravity.CENTER_HORIZONTAL
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.BOTTOM
                bottomMargin = dp(6f)
            }
        }
        arena.addView(hint)
        root.addView(arena)

        // three big buttons — never covered by pig (outside arena)
        val btns = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(12f), dp(8f), dp(12f), dp(12f))
            setBackgroundColor(C.bg)
        }
        fun big(emoji: String, title: String, sub: String, onClick: () -> Unit): LinearLayout {
            val b = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                background = roundBg(C.card, 22f, this).also { it.setStroke(dp(2f), C.line) }
                setPadding(dp(6f), dp(14f), dp(6f), dp(14f))
                layoutParams = LinearLayout.LayoutParams(0, dp(96f), 1f).apply {
                    marginStart = dp(4f); marginEnd = dp(4f)
                }
                isClickable = true; isFocusable = true
                setOnClickListener { onClick() }
            }
            b.addView(TextView(this).apply {
                text = emoji; textSize = 26f; gravity = Gravity.CENTER
            })
            b.addView(TextView(this).apply {
                text = title; setTextColor(C.text); textSize = 16f
                gravity = Gravity.CENTER; paint.isFakeBoldText = true
            })
            b.addView(TextView(this).apply {
                text = sub; setTextColor(C.muted); textSize = 11f; gravity = Gravity.CENTER
            })
            return b
        }
        btns.addView(big("🍙", "吃", "記飲食") {
            startActivity(Intent(this, SearchActivity::class.java))
        })
        btns.addView(big("🏃", "運動", "有氧") {
            startActivity(Intent(this, CardioActivity::class.java))
        })
        btns.addView(big("🏋️", "鍛鍊", "重訓部位") {
            startActivity(Intent(this, StrengthActivity::class.java))
        })
        root.addView(btns)

        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) notifPerm.launch(Manifest.permission.POST_NOTIFICATIONS)
        maybeAskHealth()
        Notify.scheduleHungryCheck(this)
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
                p to DaySnapshot(
                    Days.today(), k, pr, p.bmr, null, Calc.tdee(p.bmr, p.activity),
                    Calc.dayNet(k, p.bmr, null, Calc.tdee(p.bmr, p.activity))
                )
            }
            render(u, snap)
        }
    }

    private fun render(u: UserProfile, snap: DaySnapshot) {
        val live = u.balance + snap.net
        val (headline, amount) = BankCopy.headline(live)
        balLabel.text = headline
        balValue.text = amount
        balValue.setTextColor(BankCopy.color(live))

        val vitals = PigStats.compute(this, u, snap)
        vitalsRow.removeAllViews()
        fun bar(name: String, v: Int, color: Int) {
            val col = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                    marginEnd = dp(6f)
                }
            }
            col.addView(label(this, "$name $v", 11f))
            col.addView(ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
                max = 100; progress = v.coerceIn(0, 100)
                progressTintList = android.content.res.ColorStateList.valueOf(color)
                progressBackgroundTintList = android.content.res.ColorStateList.valueOf(C.line)
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(8f))
            })
            vitalsRow.addView(col)
        }
        bar("飽足", vitals.satiety, C.primary)
        bar("心情", vitals.mood, C.warn)
        bar("體力", vitals.energy, C.mint)

        pig.setBalance(live, animate = true)
        pig.setPartLevels(Store.partLevels(this))
        pig.setVitals(vitals)
        when {
            vitals.satiety < 28 -> pig.setMoodOverride(PigMood.HUNGRY)
            PigStats.isNight() && vitals.energy < 50 -> pig.setMoodOverride(PigMood.SLEEP)
            else -> pig.setMoodOverride(null)
        }

        val burnNote = when (snap.burnSource) {
            "manual" -> "活動：手動運動 ${Calc.r0(snap.manualBurn)} kcal（優先於健康資料）"
            "health_connect" -> "活動：Health Connect ${Calc.r0(snap.activeBurn ?: 0.0)} kcal"
            else -> "活動：以 TDEE 估算"
        }
        hint.text = buildString {
            append(BankCopy.tip(live))
            append("｜")
            append(burnNote)
            if (u.streakDays > 0) append("｜連續 ${u.streakDays} 天")
            if (healthMsg.isNotEmpty()) append("｜").append(healthMsg)
        }
    }
}

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
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.health.connect.client.PermissionController
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.TimeZone

/**
 * 電子雞首頁：場地裡只有小豬遊走，底部三大鍵「吃／運動／鍛鍊」。
 * v2.2：日常互動、BMR 金幣、運動倒幣佇列播放、里程碑、天氣／節日服裝。
 */
class MainActivity : AppCompatActivity() {
    private lateinit var rootPad: LinearLayout
    private lateinit var arena: FrameLayout
    private lateinit var pig: PigMascotView
    private lateinit var balLabel: TextView
    private lateinit var balValue: TextView
    private lateinit var vitalsRow: LinearLayout
    private lateinit var hint: TextView
    private lateinit var bubble: TextView
    private var healthMsg = ""
    private var bootstrappedDaily = false
    private var playingFx = false

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

    private val locPerm = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        Outfits.refreshWeatherAsync(this) { applyOutfit() }
    }

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
        top.addView(secondaryBtn(this, "衣櫃") {
            showWardrobe()
        }.apply {
            layoutParams = LinearLayout.LayoutParams(dp(72f), dp(40f)).apply { marginEnd = dp(6f) }
        })
        top.addView(secondaryBtn(this, "設定") {
            startActivity(Intent(this, SettingsActivity::class.java))
        }.apply {
            layoutParams = LinearLayout.LayoutParams(dp(72f), dp(40f))
        })
        root.addView(top)

        vitalsRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(16f), 0, dp(16f), dp(4f))
        }
        root.addView(vitalsRow)

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
        pig.onBubble = { msg ->
            bubble.text = msg
            bubble.animate().cancel()
            bubble.alpha = 1f
            bubble.animate().alpha(0f).setStartDelay(1600).setDuration(400).start()
        }
        arena.addView(pig)
        bubble = TextView(this).apply {
            text = ""
            setTextColor(C.text)
            textSize = 14f
            gravity = Gravity.CENTER
            setPadding(dp(12f), dp(6f), dp(12f), dp(6f))
            background = roundBg(0xCCFFFFFF.toInt(), 16f, this)
            alpha = 0f
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
                topMargin = dp(12f)
            }
        }
        arena.addView(bubble)
        hint = label(this, "長按抓住｜拖曳甩飛｜點空白處走過去｜紀錄會播動畫", 11f, C.muted).apply {
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
        maybeAskLocation()
        Notify.scheduleHungryCheck(this)
    }

    override fun onPause() {
        super.onPause()
        Store.setLastAwayMs(this, System.currentTimeMillis())
    }

    override fun onResume() {
        super.onResume()
        if (!Store.profile(this).setupDone) return
        Outfits.maybeUnlockFromStreak(this)
        Outfits.refreshWeatherAsync(this) { applyOutfit() }
        refresh()
    }

    private fun maybeAskLocation() {
        val granted = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
        if (granted) {
            Outfits.refreshWeatherAsync(this) { applyOutfit() }
            return
        }
        // optional — only ask once-ish; user can deny and we fall back to Taipei / none
        if (!Store.profile(this).badges.contains("loc_asked")) {
            Store.awardBadge(this, "loc_asked")
            locPerm.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
        }
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
            maybeDailyMoments(u, snap)
        }
    }

    private fun applyOutfit() {
        if (::pig.isInitialized) pig.setOutfit(Outfits.resolve(this))
    }

    private fun maybeDailyMoments(u: UserProfile, snap: DaySnapshot) {
        if (playingFx) return
        val today = Days.today()
        val away = Store.lastAwayMs(this)
        val now = System.currentTimeMillis()

        // welcome back after ≥ 2h away
        if (away > 0 && now - away >= 2 * 60 * 60_000L) {
            pig.playWelcome()
        }

        // morning stretch first open of day in morning hours
        val hour = Calendar.getInstance(TimeZone.getTimeZone("Asia/Taipei")).get(Calendar.HOUR_OF_DAY)
        if (Store.lastOpenDay(this) != today && hour in 6..10) {
            CuteFx.queue(this, PendingCute(CuteScene.MORNING))
            pig.playMorningStretch()
        }
        Store.setLastOpenDay(this, today)

        // A: first open each day — BMR gold coin
        if (Store.lastBmrCoinDay(this) != today) {
            Store.setLastBmrCoinDay(this, today)
            CuteFx.queue(this, PendingCute(CuteScene.BMR_COIN, kcal = u.bmr))
        }

        // deficit milestones: each −7700 cumulative (negative live balance)
        val live = u.balance + snap.net
        if (live < 0) {
            val kg = kotlin.math.floor((-live) / 7700.0).toInt()
            val celebrated = Store.deficitKgCelebrated(this)
            if (kg > celebrated) {
                for (i in (celebrated + 1)..kg) {
                    CuteFx.queue(this, PendingCute(CuteScene.FIREWORKS_KG, label = "你減掉 1 公斤了！"))
                }
                Store.setDeficitKgCelebrated(this, kg)
                Store.awardBadge(this, "kg_$kg")
            }
        }

        playQueue()
    }

    private fun playQueue() {
        if (playingFx) return
        if (Store.peekCute(this) == null) {
            applyOutfit()
            return
        }
        playingFx = true
        CuteFx.playQueued(this) {
            playingFx = false
            // surplus belly puff on pig if that scene just played — pig state from queue side effects
            refreshQuiet()
            applyOutfit()
        }
    }

    private fun refreshQuiet() {
        lifecycleScope.launch {
            try {
                val (u, snap) = Balance.refresh(this@MainActivity)
                render(u, snap)
            } catch (_: Exception) {
            }
        }
    }

    private fun showWardrobe() {
        Outfits.maybeUnlockFromStreak(this)
        val unlocked = Store.unlockedOutfits(this)
        val options = mutableListOf(OutfitId.NONE to "平常 ${OutfitId.NONE.emoji}")
        Outfits.unlockable.forEach { id ->
            val name = if (id in unlocked) "${id.label} ${id.emoji}" else "${id.label}（連續 7 天解鎖）"
            options += id to name
        }
        // show active weather/holiday as info
        val auto = Outfits.holidayOutfit() ?: Outfits.weatherOutfit(Store.weather(this))
        val info = if (auto != null) "今日自動：${auto.label} ${auto.emoji}（節日／天氣優先）" else "今日無自動服裝"
        AlertDialog.Builder(this)
            .setTitle("小豬衣櫃")
            .setMessage(info + "\n連續 ${Store.profile(this).streakDays} 天")
            .setItems(options.map { it.second }.toTypedArray()) { _, which ->
                val id = options[which].first
                if (id != OutfitId.NONE && id !in unlocked) {
                    Toast.makeText(this, "連續紀錄 7 天就能解鎖喔～", Toast.LENGTH_SHORT).show()
                } else {
                    Store.setSelectedOutfit(this, id)
                    applyOutfit()
                    Toast.makeText(this, "換好了 ${id.emoji}", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("關閉", null)
            .show()
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
        applyOutfit()
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
        val o = Outfits.resolve(this)
        hint.text = buildString {
            append(BankCopy.tip(live))
            append("｜")
            append(burnNote)
            if (u.streakDays > 0) append("｜連續 ${u.streakDays} 天")
            if (o != OutfitId.NONE) append("｜裝：${o.emoji}")
            if (healthMsg.isNotEmpty()) append("｜").append(healthMsg)
        }
    }
}

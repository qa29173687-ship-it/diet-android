package tw.dietbook

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.health.connect.client.PermissionController
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import tw.dietbook.ui.HomeScreenV2
import tw.dietbook.ui.HomeV2UiState
import java.util.Calendar
import java.util.TimeZone

/**
 * 電子雞首頁 v2.4.1：上方今日數值、放大總存款、場地小豬、底部吃／運動／鍛鍊。
 * 建議改在「每日總結」顯示。
 */
class MainActivity : AppCompatActivity() {
    private var pig: PigMascotView? = null
    private var healthMsg = ""
    private var playingFx = false
    private var pendingMomentsAfterSummary = false
    private var uiState by mutableStateOf(
        HomeV2UiState(
            intakeKcal = 0,
            proteinG = 0,
            burnKcal = 0,
            strengthMin = 0,
            balanceKcal = 0.0,
            bmr = 0.0,
        ),
    )
    private var bubbleMsg by mutableStateOf<String?>(null)

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

        setContent {
            HomeScreenV2(
                state = uiState,
                modifier = Modifier.fillMaxSize(),
                onEat = { startActivity(Intent(this, SearchActivity::class.java)) },
                onCardio = { startActivity(Intent(this, CardioActivity::class.java)) },
                onStrength = { startActivity(Intent(this, StrengthActivity::class.java)) },
                onSettings = { startActivity(Intent(this, SettingsActivity::class.java)) },
                onWardrobe = { showWardrobe() },
                onBalance = {
                    // 總存款 → 今日每日總結（內有存摺入口）
                    startActivity(DailySummaryActivity.intent(this, Days.today()))
                },
                onPigReady = { p ->
                    pig = p
                    p.onBubble = { msg -> bubbleMsg = msg }
                    applyOutfit()
                },
                onBubble = { msg -> bubbleMsg = msg },
            )
        }

        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) notifPerm.launch(Manifest.permission.POST_NOTIFICATIONS)
        maybeAskHealth()
        maybeAskLocation()
        Notify.scheduleHungryCheck(this)
        Notify.scheduleDailySummary(this)

        // Deep link from summary notification / other entry
        maybeOpenSummaryFromIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        maybeOpenSummaryFromIntent(intent)
    }

    private fun maybeOpenSummaryFromIntent(intent: Intent?) {
        val day = intent?.getStringExtra(DailySummaryActivity.EXTRA_DAY) ?: return
        startActivity(DailySummaryActivity.intent(this, day))
        intent.removeExtra(DailySummaryActivity.EXTRA_DAY)
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
        pig?.setOutfit(Outfits.resolve(this))
    }

    private fun maybeDailyMoments(u: UserProfile, snap: DaySnapshot) {
        if (playingFx) return
        val today = Days.today()
        val yesterday = Days.yesterdayOf(today)

        // First open of a new day: show yesterday's 每日總結 before BMR coin, if not yet seen.
        if (Store.lastSeenSummaryDay(this) != yesterday && Store.lastOpenDay(this) != today) {
            // Only if there is something to summarize (setup was done yesterday or we have a snap / logs)
            val hasData = Store.snapshot(this, yesterday) != null ||
                Store.dayTotals(this, yesterday).first > 0 ||
                Store.manualBurn(this, yesterday) > 0
            if (hasData || Store.profile(this).lastFinalizedDay == yesterday ||
                Store.profile(this).lastFinalizedDay.isNotEmpty()
            ) {
                pendingMomentsAfterSummary = true
                startActivity(DailySummaryActivity.intent(this, yesterday, fromMorning = true))
                // Mark open day so we don't loop; BMR coin deferred until resume after summary
                Store.setLastOpenDay(this, today)
                return
            }
        }

        if (pendingMomentsAfterSummary) {
            pendingMomentsAfterSummary = false
        }

        playDailyMoments(u, snap)
    }

    private fun playDailyMoments(u: UserProfile, snap: DaySnapshot) {
        if (playingFx) return
        val today = Days.today()
        val away = Store.lastAwayMs(this)
        val now = System.currentTimeMillis()
        val p = pig

        if (p != null && away > 0 && now - away >= 2 * 60 * 60_000L) {
            p.playWelcome()
        }

        val hour = Calendar.getInstance(TimeZone.getTimeZone("Asia/Taipei")).get(Calendar.HOUR_OF_DAY)
        if (Store.lastOpenDay(this) != today && hour in 6..10) {
            CuteFx.queue(this, PendingCute(CuteScene.MORNING))
            p?.playMorningStretch()
        }
        Store.setLastOpenDay(this, today)

        if (Store.lastBmrCoinDay(this) != today) {
            Store.setLastBmrCoinDay(this, today)
            CuteFx.queue(this, PendingCute(CuteScene.BMR_COIN, kcal = u.bmr))
        }

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
        val vitals = PigStats.compute(this, u, snap)
        val burnShown = when (snap.burnSource) {
            "manual" -> snap.manualBurn
            "health_connect" -> snap.activeBurn ?: 0.0
            else -> 0.0
        }
        val strengthMin = Store.strengthLogs(this, Days.today()).sumOf { it.minutes }
        val burnNote = when (snap.burnSource) {
            "manual" -> "活動：手動運動 ${Calc.r0(snap.manualBurn)} kcal"
            "health_connect" -> "活動：Health Connect ${Calc.r0(snap.activeBurn ?: 0.0)} kcal"
            else -> "活動：以 TDEE 估算"
        }
        val o = Outfits.resolve(this)
        val tip = buildString {
            append(BankCopy.tip(live))
            append("｜")
            append(burnNote)
            if (u.streakDays > 0) append("｜連續 ${u.streakDays} 天")
            if (o != OutfitId.NONE) append("｜裝：${o.emoji}")
            if (healthMsg.isNotEmpty()) append("｜").append(healthMsg)
            append("｜點總存款看每日總結")
        }

        uiState = HomeV2UiState(
            intakeKcal = Calc.r0(snap.intakeKcal),
            proteinG = Calc.r0(snap.protein),
            burnKcal = Calc.r0(burnShown),
            strengthMin = strengthMin,
            balanceKcal = live,
            bmr = u.bmr,
            tip = tip,
            vitals = vitals,
        )

        pig?.let { p ->
            p.setBalance(live, animate = true)
            p.setPartLevels(Store.partLevels(this))
            p.setVitals(vitals)
            applyOutfit()
            when {
                vitals.satiety < 28 -> p.setMoodOverride(PigMood.HUNGRY)
                PigStats.isNight() && vitals.energy < 50 -> p.setMoodOverride(PigMood.SLEEP)
                else -> p.setMoodOverride(null)
            }
        }
    }
}

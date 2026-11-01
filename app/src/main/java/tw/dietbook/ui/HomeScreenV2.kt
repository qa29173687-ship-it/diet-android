package tw.dietbook.ui

import android.widget.FrameLayout
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import tw.dietbook.BankCopy
import tw.dietbook.C
import tw.dietbook.PigMascotView
import tw.dietbook.PigMood
import tw.dietbook.PigStats
import tw.dietbook.PigVitals

/** Home v2: top pink daily stats, enlarged center balance, pig + bottom actions. */
data class HomeV2UiState(
    val intakeKcal: Int,
    val proteinG: Int,
    val burnKcal: Int,
    val strengthMin: Int,
    val balanceKcal: Double,
    val bmr: Double = 0.0,
    val tip: String = "",
    val vitals: PigVitals = PigVitals(72, 78, 68),
)

private val PinkBg = Color(C.bg)
private val CardWhite = Color(C.card)
private val CardPink = Color(C.card2)
private val LinePink = Color(C.line)
private val TextMain = Color(C.text)
private val TextMuted = Color(C.muted)
private val PrimaryPink = Color(C.primary)

@Composable
fun HomeScreenV2(
    state: HomeV2UiState,
    modifier: Modifier = Modifier,
    onEat: () -> Unit = {},
    onCardio: () -> Unit = {},
    onStrength: () -> Unit = {},
    onSettings: () -> Unit = {},
    onWardrobe: () -> Unit = {},
    onBalance: () -> Unit = {},
    onPigReady: (PigMascotView) -> Unit = {},
    onBubble: (String) -> Unit = {},
) {
    val (headline, amount) = BankCopy.headline(state.balanceKcal)
    val balColor = Color(BankCopy.color(state.balanceKcal))
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(PinkBg)
            .padding(top = 28.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    "熱量存錢筒",
                    color = TextMain,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text("今天也要好好記帳喔 ♡", color = TextMuted, fontSize = 12.sp)
            }
            Text(
                "👗",
                fontSize = 20.sp,
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(onClick = onWardrobe)
                    .padding(8.dp),
            )
            Text(
                "⚙️",
                fontSize = 22.sp,
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(onClick = onSettings)
                    .padding(8.dp),
            )
        }

        Spacer(Modifier.height(12.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            StatCard("🍙", "今日攝取", "${state.intakeKcal}", "kcal", Modifier.weight(1f))
            StatCard("💪", "蛋白質", "${state.proteinG}", "g", Modifier.weight(1f))
            StatCard("🏃", "運動消耗", "${state.burnKcal}", "kcal", Modifier.weight(1f))
            StatCard("🏋️", "鍛鍊", "${state.strengthMin}", "分", Modifier.weight(1f))
        }

        Spacer(Modifier.height(14.dp))

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .shadow(6.dp, RoundedCornerShape(28.dp))
                .clip(RoundedCornerShape(28.dp))
                .background(CardWhite)
                .border(2.dp, LinePink, RoundedCornerShape(28.dp))
                .clickable(onClick = onBalance)
                .padding(vertical = 18.dp, horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("🐷 總存款", color = TextMuted, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(4.dp))
            Text(
                amount,
                color = balColor,
                fontSize = 42.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            Text(headline, color = TextMain, fontSize = 13.sp)
            if (state.tip.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                Text(
                    state.tip,
                    color = TextMuted,
                    fontSize = 11.sp,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        Spacer(Modifier.height(10.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = 12.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(CardPink)
                .border(2.dp, LinePink, RoundedCornerShape(24.dp)),
        ) {
            PigArena(
                balanceKcal = state.balanceKcal,
                vitals = state.vitals,
                modifier = Modifier.fillMaxSize(),
                onPigReady = onPigReady,
                onBubble = onBubble,
            )
            Text(
                "長按抓住｜拖曳甩飛｜點空白處走過去",
                color = TextMuted,
                fontSize = 11.sp,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 8.dp),
            )
        }

        Spacer(Modifier.height(8.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .padding(bottom = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ActionButton("🍙", "吃", "記飲食", Modifier.weight(1f), onEat)
            ActionButton("🏃", "運動", "有氧", Modifier.weight(1f), onCardio)
            ActionButton("🏋️", "鍛鍊", "重訓部位", Modifier.weight(1f), onStrength)
        }
    }
}

@Composable
private fun StatCard(
    emoji: String,
    label: String,
    value: String,
    unit: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .shadow(3.dp, RoundedCornerShape(18.dp))
            .clip(RoundedCornerShape(18.dp))
            .background(CardWhite)
            .border(1.5.dp, LinePink, RoundedCornerShape(18.dp))
            .padding(vertical = 10.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(emoji, fontSize = 16.sp)
        Text(label, color = TextMuted, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(2.dp))
        Text(value, color = TextMain, fontSize = 18.sp, fontWeight = FontWeight.Bold, maxLines = 1)
        Text(unit, color = PrimaryPink, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun ActionButton(
    emoji: String,
    title: String,
    sub: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Column(
        modifier = modifier
            .height(96.dp)
            .shadow(2.dp, RoundedCornerShape(22.dp))
            .clip(RoundedCornerShape(22.dp))
            .background(CardWhite)
            .border(2.dp, LinePink, RoundedCornerShape(22.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(emoji, fontSize = 26.sp)
        Text(title, color = TextMain, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        Text(sub, color = TextMuted, fontSize = 11.sp)
    }
}

@Composable
private fun PigArena(
    balanceKcal: Double,
    vitals: PigVitals,
    modifier: Modifier = Modifier,
    onPigReady: (PigMascotView) -> Unit = {},
    onBubble: (String) -> Unit = {},
) {
    val context = LocalContext.current
    val pig = remember {
        PigMascotView(context).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
            )
        }
    }
    DisposableEffect(Unit) {
        pig.onBubble = { onBubble(it) }
        onPigReady(pig)
        onDispose { }
    }
    DisposableEffect(balanceKcal, vitals) {
        pig.setBalance(balanceKcal, animate = false)
        pig.setVitals(vitals)
        when {
            vitals.satiety < 28 -> pig.setMoodOverride(PigMood.HUNGRY)
            PigStats.isNight() && vitals.energy < 50 -> pig.setMoodOverride(PigMood.SLEEP)
            else -> pig.setMoodOverride(null)
        }
        onDispose { }
    }
    AndroidView(
        factory = { parent ->
            FrameLayout(parent).apply {
                setBackgroundColor(C.card2)
                if (pig.parent != null) (pig.parent as? android.view.ViewGroup)?.removeView(pig)
                addView(pig)
            }
        },
        modifier = modifier,
        update = {
            pig.setBalance(balanceKcal, animate = false)
            pig.setVitals(vitals)
            pig.onBubble = { onBubble(it) }
        },
    )
}

/** Sample for UI demo / Robolectric. */
object HomeV2Sample {
    val belowBmr = HomeV2UiState(
        intakeKcal = 980,
        proteinG = 42,
        burnKcal = 180,
        strengthMin = 20,
        balanceKcal = -620.0,
        bmr = 1320.0,
        tip = "目標：每天溫柔缺口，但別低於 BMR",
        vitals = PigVitals(satiety = 38, mood = 55, energy = 48),
    )

    val state = belowBmr
}

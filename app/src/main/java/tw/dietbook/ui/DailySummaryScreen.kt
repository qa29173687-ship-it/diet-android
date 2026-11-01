package tw.dietbook.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import tw.dietbook.AdviceCard
import tw.dietbook.AdviceKind
import tw.dietbook.BankCopy
import tw.dietbook.C
import tw.dietbook.Calc
import tw.dietbook.DailySummaryModel
import tw.dietbook.Days

private val PinkBg = Color(C.bg)
private val CardWhite = Color(C.card)
private val LinePink = Color(C.line)
private val TextMain = Color(C.text)
private val TextMuted = Color(C.muted)
private val PrimaryPink = Color(C.primary)
private val WarnSoft = Color(C.warn)
private val MintSoft = Color(C.mint)
private val DangerSoft = Color(C.danger)

@Composable
fun DailySummaryScreen(
    model: DailySummaryModel,
    modifier: Modifier = Modifier,
    onClose: () -> Unit = {},
    onOpenLedger: () -> Unit = {},
) {
    val (headline, amount) = BankCopy.headline(model.balanceKcal)
    val balColor = Color(BankCopy.color(model.balanceKcal))
    val netTxt = when {
        model.netKcal > 0 -> "存款 +${Calc.r0(model.netKcal)} kcal"
        model.netKcal < 0 -> "進帳 −${Calc.r0(-model.netKcal)} kcal"
        else -> "打平 0 kcal"
    }
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(PinkBg)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("🐷 每日總結", color = TextMain, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Text(Days.label(model.day), color = TextMuted, fontSize = 13.sp)
        Spacer(Modifier.height(6.dp))
        Text("小豬幫你把這天的帳本收好囉 ♡", color = TextMuted, fontSize = 12.sp, textAlign = TextAlign.Center)

        Spacer(Modifier.height(16.dp))

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SummaryStat("🍙", "攝取", "${model.intakeKcal}", "kcal", Modifier.weight(1f))
            SummaryStat("💪", "蛋白質", "${model.proteinG}", "g", Modifier.weight(1f))
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SummaryStat("🏃", "運動", "${model.burnKcal}", "kcal", Modifier.weight(1f))
            SummaryStat("🏋️", "鍛鍊", "${model.strengthMin}", "分", Modifier.weight(1f))
        }

        Spacer(Modifier.height(14.dp))

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(4.dp, RoundedCornerShape(24.dp))
                .clip(RoundedCornerShape(24.dp))
                .background(CardWhite)
                .border(2.dp, LinePink, RoundedCornerShape(24.dp))
                .padding(18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("當日淨值", color = TextMuted, fontSize = 12.sp)
            Text(netTxt, color = Color(BankCopy.color(model.netKcal)), fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(10.dp))
            Text("🐷 總存款", color = TextMuted, fontSize = 12.sp)
            Text(amount, color = balColor, fontSize = 36.sp, fontWeight = FontWeight.Bold)
            Text(headline, color = TextMain, fontSize = 13.sp)
        }

        Spacer(Modifier.height(12.dp))

        AdviceBlock(model.advice)

        Spacer(Modifier.height(16.dp))

        CuteBtn("看完了，親親小豬 ♡", PrimaryPink, Color.White, onClose)
        Spacer(Modifier.height(8.dp))
        CuteBtn("打開小豬存摺", Color(C.card2), TextMain, onOpenLedger)
        Spacer(Modifier.height(20.dp))
    }
}

@Composable
private fun SummaryStat(emoji: String, label: String, value: String, unit: String, modifier: Modifier) {
    Column(
        modifier = modifier
            .shadow(2.dp, RoundedCornerShape(18.dp))
            .clip(RoundedCornerShape(18.dp))
            .background(CardWhite)
            .border(1.5.dp, LinePink, RoundedCornerShape(18.dp))
            .padding(vertical = 12.dp, horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(emoji, fontSize = 18.sp)
        Text(label, color = TextMuted, fontSize = 11.sp)
        Text(value, color = TextMain, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Text(unit, color = PrimaryPink, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun AdviceBlock(advice: AdviceCard) {
    val (bg, border, accent) = when (advice.kind) {
        AdviceKind.BELOW_BMR -> Triple(Color(0xFFFFF0F3), Color(0xFFFFC2D0), DangerSoft)
        AdviceKind.SURPLUS_CARE -> Triple(Color(0xFFFFF7EC), Color(0xFFFFD9A8), WarnSoft)
        AdviceKind.PRAISE -> Triple(Color(0xFFF0FFF6), Color(0xFFB8E8C8), MintSoft)
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(2.dp, RoundedCornerShape(20.dp))
            .clip(RoundedCornerShape(20.dp))
            .background(bg)
            .border(1.5.dp, border, RoundedCornerShape(20.dp))
            .padding(14.dp),
    ) {
        Text("小豬的叮嚀", color = TextMuted, fontSize = 11.sp)
        Spacer(Modifier.height(4.dp))
        Text(advice.title, color = accent, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        Text(advice.body, color = TextMain, fontSize = 12.sp, lineHeight = 18.sp)
    }
}

@Composable
private fun CuteBtn(label: String, bg: Color, textColor: Color, onClick: () -> Unit) {
    Text(
        text = label,
        color = textColor,
        fontSize = 15.sp,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth()
            .shadow(2.dp, RoundedCornerShape(18.dp))
            .clip(RoundedCornerShape(18.dp))
            .background(bg)
            .border(1.5.dp, LinePink, RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp),
    )
}

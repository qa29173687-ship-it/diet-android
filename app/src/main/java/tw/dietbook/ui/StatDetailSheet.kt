package tw.dietbook.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
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
import tw.dietbook.BurnDetail
import tw.dietbook.C
import tw.dietbook.Calc
import tw.dietbook.Days
import tw.dietbook.IntakeDetail
import tw.dietbook.ProteinDetail
import tw.dietbook.StatDetailKind
import tw.dietbook.StrengthDetail

private val PinkBg = Color(C.bg)
private val CardWhite = Color(C.card)
private val CardPink = Color(C.card2)
private val LinePink = Color(C.line)
private val TextMain = Color(C.text)
private val TextMuted = Color(C.muted)
private val PrimaryPink = Color(C.primary)
private val MintSoft = Color(C.mint)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatDetailBottomSheet(
    kind: StatDetailKind,
    intake: IntakeDetail?,
    protein: ProteinDetail?,
    burn: BurnDetail?,
    strength: StrengthDetail?,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = PinkBg,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
    ) {
        StatDetailPanel(
            kind = kind,
            intake = intake,
            protein = protein,
            burn = burn,
            strength = strength,
            onClose = onDismiss,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 640.dp)
                .padding(horizontal = 16.dp, vertical = 4.dp),
        )
    }
}

/** Full-panel content (sheet + Robolectric preview host). */
@Composable
fun StatDetailPanel(
    kind: StatDetailKind,
    intake: IntakeDetail?,
    protein: ProteinDetail?,
    burn: BurnDetail?,
    strength: StrengthDetail?,
    onClose: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val (emoji, title) = when (kind) {
        StatDetailKind.INTAKE -> "🍙" to "今日攝取怎麼算"
        StatDetailKind.PROTEIN -> "💪" to "蛋白質怎麼算"
        StatDetailKind.BURN -> "🏃" to "運動消耗怎麼算"
        StatDetailKind.STRENGTH -> "🏋️" to "鍛鍊怎麼算"
    }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("$emoji $title", color = TextMain, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        val day = when (kind) {
            StatDetailKind.INTAKE -> intake?.day
            StatDetailKind.PROTEIN -> protein?.day
            StatDetailKind.BURN -> burn?.day
            StatDetailKind.STRENGTH -> strength?.day
        } ?: Days.today()
        Text(Days.label(day), color = TextMuted, fontSize = 12.sp)
        Spacer(Modifier.height(12.dp))

        when (kind) {
            StatDetailKind.INTAKE -> IntakeBody(intake)
            StatDetailKind.PROTEIN -> ProteinBody(protein)
            StatDetailKind.BURN -> BurnBody(burn)
            StatDetailKind.STRENGTH -> StrengthBody(strength)
        }

        Spacer(Modifier.height(16.dp))
        Text(
            "知道啦 ♡",
            color = Color.White,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .shadow(2.dp, RoundedCornerShape(18.dp))
                .clip(RoundedCornerShape(18.dp))
                .background(PrimaryPink)
                .clickable(onClick = onClose)
                .padding(vertical = 14.dp),
        )
    }
}

@Composable
private fun IntakeBody(detail: IntakeDetail?) {
    if (detail == null || detail.lines.isEmpty()) {
        EmptyCute("今日還沒吃東西喔～", "點下面「吃」記一筆，小豬才吃得飽 ♡")
        return
    }
    FormulaNote("每筆＝食物熱量密度 × 公克 ÷ 100，再全部加總。")
    Spacer(Modifier.height(8.dp))
    detail.lines.forEach { line ->
        DetailCard {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("${line.meal}・${line.time}", color = TextMuted, fontSize = 11.sp)
                    Text(line.name, color = TextMain, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    Text(line.qty, color = TextMuted, fontSize = 12.sp)
                }
                Text("${line.kcal}", color = TextMain, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Text(" kcal", color = PrimaryPink, fontSize = 11.sp)
            }
        }
        Spacer(Modifier.height(8.dp))
    }
    TotalBar("今日攝取合計", "${detail.totalKcal}", "kcal")
}

@Composable
private fun ProteinBody(detail: ProteinDetail?) {
    if (detail == null || detail.meals.isEmpty()) {
        EmptyCute("還沒有蛋白質紀錄～", "蛋白質是小豬的好朋友！吃點雞胸／優格吧 ♡")
        return
    }
    FormulaNote("依進食時間分成早／午／晚／點心；每餐小計後再加總。")
    Spacer(Modifier.height(8.dp))
    detail.meals.forEach { meal ->
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(2.dp, RoundedCornerShape(18.dp))
                .clip(RoundedCornerShape(18.dp))
                .background(CardWhite)
                .border(1.5.dp, LinePink, RoundedCornerShape(18.dp))
                .padding(12.dp),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(mealEmoji(meal.meal) + " " + meal.meal, color = TextMain, fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Text("${fmtG(meal.subtotalG)} g", color = MintSoft, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(6.dp))
            meal.lines.forEach { line ->
                Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                    Text("${line.time}  ${line.name}", color = TextMuted, fontSize = 12.sp, modifier = Modifier.weight(1f))
                    Text("${fmtG(line.proteinG)} g", color = TextMain, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
        Spacer(Modifier.height(8.dp))
    }
    TotalBar("蛋白質合計", fmtG(detail.totalG), "g")
}

@Composable
private fun BurnBody(detail: BurnDetail?) {
    if (detail == null || detail.lines.isEmpty()) {
        EmptyCute("今天還沒運動～", "散步或記一筆有氧，小豬也會跟你一起喘吁吁 ♡")
        FormulaNote(detail?.note ?: "日結時若無活動數據，會用 TDEE 估算。")
        return
    }
    FormulaNote(detail.note)
    Spacer(Modifier.height(8.dp))
    detail.lines.forEach { line ->
        DetailCard {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Text(line.label, color = TextMain, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    Text("${line.source}・${line.duration}", color = TextMuted, fontSize = 11.sp)
                    Spacer(Modifier.height(4.dp))
                    Text(line.formula, color = TextMuted, fontSize = 11.sp)
                }
                Text("${line.kcal}", color = TextMain, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Text(" kcal", color = PrimaryPink, fontSize = 11.sp)
            }
        }
        Spacer(Modifier.height(8.dp))
    }
    TotalBar("運動消耗合計", "${detail.totalKcal}", "kcal")
}

@Composable
private fun StrengthBody(detail: StrengthDetail?) {
    if (detail == null || detail.lines.isEmpty()) {
        EmptyCute("鍛鍊日記還是空白的～", "練一下手臂／腿，小豬會慢慢壯起來喔 ♡")
        return
    }
    FormulaNote("合計以分鐘為主；熱量＝重訓 MET × 體重 × 小時（會併入運動消耗）。")
    Spacer(Modifier.height(8.dp))
    detail.lines.forEach { line ->
        DetailCard {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("${line.part}・${line.exercise}", color = TextMain, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    Text("${line.time}・${line.sets} 組・${line.minutes} 分", color = TextMuted, fontSize = 11.sp)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("${line.minutes} 分", color = TextMain, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    if (line.kcal > 0) Text("${line.kcal} kcal", color = TextMuted, fontSize = 11.sp)
                }
            }
        }
        Spacer(Modifier.height(8.dp))
    }
    TotalBar("鍛鍊合計", "${detail.totalMin}", "分", sub = if (detail.totalKcal > 0) "約 ${detail.totalKcal} kcal" else null)
}

@Composable
private fun FormulaNote(text: String) {
    Text(
        text,
        color = TextMuted,
        fontSize = 12.sp,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(CardPink)
            .border(1.dp, LinePink, RoundedCornerShape(14.dp))
            .padding(10.dp),
    )
}

@Composable
private fun EmptyCute(title: String, body: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(CardWhite)
            .border(1.5.dp, LinePink, RoundedCornerShape(20.dp))
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("🐷💤", fontSize = 36.sp)
        Spacer(Modifier.height(8.dp))
        Text(title, color = TextMain, fontSize = 15.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        Spacer(Modifier.height(4.dp))
        Text(body, color = TextMuted, fontSize = 12.sp, textAlign = TextAlign.Center)
    }
}

@Composable
private fun DetailCard(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(2.dp, RoundedCornerShape(16.dp))
            .clip(RoundedCornerShape(16.dp))
            .background(CardWhite)
            .border(1.5.dp, LinePink, RoundedCornerShape(16.dp))
            .padding(12.dp),
    ) { content() }
}

@Composable
private fun TotalBar(label: String, value: String, unit: String, sub: String? = null) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(PrimaryPink.copy(alpha = 0.15f))
            .border(1.5.dp, PrimaryPink.copy(alpha = 0.45f), RoundedCornerShape(18.dp))
            .padding(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(label, color = TextMuted, fontSize = 12.sp)
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.Center) {
            Text(value, color = TextMain, fontSize = 28.sp, fontWeight = FontWeight.Bold)
            Text(" $unit", color = PrimaryPink, fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 4.dp))
        }
        if (sub != null) Text(sub, color = TextMuted, fontSize = 11.sp)
    }
}

private fun mealEmoji(meal: String): String = when (meal) {
    "早餐" -> "🌅"
    "午餐" -> "☀️"
    "晚餐" -> "🌙"
    "點心" -> "🍪"
    "宵夜" -> "🌃"
    else -> "🍽"
}

private fun fmtG(v: Double): String =
    if (kotlin.math.abs(v - Calc.r0(v).toDouble()) < 0.05) "${Calc.r0(v)}" else "%.1f".format(v)

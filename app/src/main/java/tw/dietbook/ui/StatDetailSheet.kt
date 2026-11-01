package tw.dietbook.ui

import android.content.Context
import androidx.compose.animation.animateColorAsState
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import tw.dietbook.BurnDetail
import tw.dietbook.C
import tw.dietbook.Calc
import tw.dietbook.Days
import tw.dietbook.DeletableKind
import tw.dietbook.DeletedPayload
import tw.dietbook.IntakeDetail
import tw.dietbook.LogActions
import tw.dietbook.ProteinDetail
import tw.dietbook.StatDetail
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
private val DangerSoft = Color(C.danger)

data class PendingDelete(
    val title: String,
    val body: String,
    val action: () -> DeletedPayload?,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatDetailBottomSheet(
    kind: StatDetailKind,
    day: String = Days.today(),
    allowEdit: Boolean = true,
    onDismiss: () -> Unit,
    onChanged: () -> Unit = {},
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var intake by remember { mutableStateOf<IntakeDetail?>(null) }
    var protein by remember { mutableStateOf<ProteinDetail?>(null) }
    var burn by remember { mutableStateOf<BurnDetail?>(null) }
    var strength by remember { mutableStateOf<StrengthDetail?>(null) }
    val snack = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var pending by remember { mutableStateOf<PendingDelete?>(null) }

    fun reload() {
        when (kind) {
            StatDetailKind.INTAKE -> intake = StatDetail.intake(context, day)
            StatDetailKind.PROTEIN -> protein = StatDetail.protein(context, day)
            StatDetailKind.BURN -> burn = StatDetail.burn(context, day)
            StatDetailKind.STRENGTH -> strength = StatDetail.strength(context, day)
        }
    }

    LaunchedEffect(kind, day) { reload() }

    fun offerUndo(payload: DeletedPayload) {
        scope.launch {
            val res = snack.showSnackbar(
                message = LogActions.label(payload),
                actionLabel = "復原",
                duration = SnackbarDuration.Short,
            )
            if (res == SnackbarResult.ActionPerformed) {
                LogActions.restore(context, payload)
                reload()
                onChanged()
            }
        }
    }

    fun requestDelete(title: String, body: String, block: () -> DeletedPayload?) {
        pending = PendingDelete(title, body, block)
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = PinkBg,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
    ) {
        Box {
            StatDetailPanel(
                kind = kind,
                intake = intake,
                protein = protein,
                burn = burn,
                strength = strength,
                allowEdit = allowEdit,
                onClose = onDismiss,
                onRequestDelete = { title, body, block -> requestDelete(title, body, block) },
                onHcToggle = if (allowEdit) {
                    {
                        val prev = LogActions.setHcIgnored(context, day, !StoreHc(context, day))
                        reload()
                        onChanged()
                        offerUndo(DeletedPayload.HcIgnore(day, prev))
                    }
                } else null,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 640.dp)
                    .padding(horizontal = 16.dp, vertical = 4.dp),
            )
            SnackbarHost(
                hostState = snack,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp),
            ) { data ->
                Snackbar(
                    snackbarData = data,
                    containerColor = Color(0xFF4A3040),
                    contentColor = Color.White,
                    actionColor = Color(0xFFFFB4C8),
                )
            }
        }
    }

    pending?.let { p ->
        CuteConfirmDialog(
            title = p.title,
            body = p.body,
            onConfirm = {
                val payload = p.action()
                pending = null
                if (payload != null) {
                    reload()
                    onChanged()
                    offerUndo(payload)
                }
            },
            onDismiss = { pending = null },
        )
    }
}

private fun StoreHc(ctx: Context, day: String) = tw.dietbook.Store.hcIgnored(ctx, day)

@Composable
fun StatDetailPanel(
    kind: StatDetailKind,
    intake: IntakeDetail?,
    protein: ProteinDetail?,
    burn: BurnDetail?,
    strength: StrengthDetail?,
    onClose: () -> Unit = {},
    allowEdit: Boolean = false,
    onRequestDelete: (title: String, body: String, action: () -> DeletedPayload?) -> Unit = { _, _, _ -> },
    onHcToggle: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
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
        if (allowEdit) {
            Text("左滑或點 🗑 可刪錯帳 ♡", color = TextMuted, fontSize = 11.sp)
        }
        Spacer(Modifier.height(12.dp))

        when (kind) {
            StatDetailKind.INTAKE -> IntakeBody(intake, allowEdit) { line ->
                onRequestDelete("刪掉這筆飲食？", "「${line.name}」會從小豬帳本拿掉喔～") {
                    LogActions.deleteFood(context, day, line.id)?.let { DeletedPayload.Food(it) }
                }
            }
            StatDetailKind.PROTEIN -> ProteinBody(protein, allowEdit) { line ->
                onRequestDelete("刪掉這筆蛋白質？", "「${line.name}」會一起從今日攝取移除喔～") {
                    LogActions.deleteFood(context, day, line.id)?.let { DeletedPayload.Food(it) }
                }
            }
            StatDetailKind.BURN -> BurnBody(burn, allowEdit, onHcToggle) { line ->
                when (line.kind) {
                    DeletableKind.CARDIO -> onRequestDelete("刪掉這筆運動？", "「${line.label}」會取消今日消耗計算喔～") {
                        LogActions.deleteCardio(context, day, line.id)?.let { DeletedPayload.Cardio(it) }
                    }
                    DeletableKind.STRENGTH -> onRequestDelete("刪掉這筆鍛鍊？", "「${line.label}」會從小豬肌肉日記拿掉喔～") {
                        LogActions.deleteStrength(context, day, line.id)?.let { DeletedPayload.Strength(it) }
                    }
                    DeletableKind.HC -> onHcToggle?.invoke()
                    DeletableKind.FOOD -> {}
                }
            }
            StatDetailKind.STRENGTH -> StrengthBody(strength, allowEdit) { line ->
                onRequestDelete("刪掉這筆鍛鍊？", "「${line.part}・${line.exercise}」真的要刪嗎？") {
                    LogActions.deleteStrength(context, day, line.id)?.let { DeletedPayload.Strength(it) }
                }
            }
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
fun CuteConfirmDialog(
    title: String,
    body: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = CardWhite,
        title = { Text("🐷 $title", color = TextMain, fontWeight = FontWeight.Bold) },
        text = { Text(body, color = TextMuted, fontSize = 14.sp) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("刪掉", color = DangerSoft, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("先留著", color = PrimaryPink, fontWeight = FontWeight.Bold)
            }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DeletableRow(
    allowEdit: Boolean,
    onDelete: () -> Unit,
    content: @Composable () -> Unit,
) {
    if (!allowEdit) {
        DetailCard { content() }
        return
    }
    val state = rememberSwipeToDismissBoxState(
        confirmValueChange = {
            if (it == SwipeToDismissBoxValue.EndToStart || it == SwipeToDismissBoxValue.StartToEnd) {
                onDelete(); false
            } else false
        },
    )
    SwipeToDismissBox(
        state = state,
        backgroundContent = {
            val color by animateColorAsState(
                if (state.targetValue != SwipeToDismissBoxValue.Settled) DangerSoft.copy(alpha = 0.2f)
                else Color.Transparent,
                label = "delBg",
            )
            Box(
                Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(16.dp))
                    .background(color)
                    .padding(horizontal = 16.dp),
                contentAlignment = Alignment.CenterEnd,
            ) { Text("🗑", fontSize = 22.sp) }
        },
        content = {
            DetailCard {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f)) { content() }
                    Text(
                        "🗑",
                        fontSize = 16.sp,
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .clickable(onClick = onDelete)
                            .padding(8.dp),
                        textAlign = TextAlign.Center,
                    )
                }
            }
        },
    )
}

@Composable
private fun IntakeBody(detail: IntakeDetail?, allowEdit: Boolean, onDelete: (tw.dietbook.IntakeLine) -> Unit) {
    if (detail == null || detail.lines.isEmpty()) {
        EmptyCute("今日還沒吃東西喔～", "點下面「吃」記一筆，小豬才吃得飽 ♡")
        return
    }
    FormulaNote("每筆＝食物熱量密度 × 公克 ÷ 100，再全部加總。")
    Spacer(Modifier.height(8.dp))
    detail.lines.forEach { line ->
        DeletableRow(allowEdit, onDelete = { onDelete(line) }) {
            Column {
                Text("${line.meal}・${line.time}", color = TextMuted, fontSize = 11.sp)
                Text(line.name, color = TextMain, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Row {
                    Text(line.qty, color = TextMuted, fontSize = 12.sp, modifier = Modifier.weight(1f))
                    Text("${line.kcal} kcal", color = PrimaryPink, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
        Spacer(Modifier.height(8.dp))
    }
    TotalBar("今日攝取合計", "${detail.totalKcal}", "kcal")
}

@Composable
private fun ProteinBody(detail: ProteinDetail?, allowEdit: Boolean, onDelete: (tw.dietbook.ProteinLine) -> Unit) {
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
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("${line.time}  ${line.name}", color = TextMuted, fontSize = 12.sp, modifier = Modifier.weight(1f))
                    Text("${fmtG(line.proteinG)} g", color = TextMain, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    if (allowEdit) {
                        Text(
                            "🗑",
                            fontSize = 14.sp,
                            modifier = Modifier
                                .padding(start = 6.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { onDelete(line) }
                                .padding(4.dp),
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
    }
    TotalBar("蛋白質合計", fmtG(detail.totalG), "g")
}

@Composable
private fun BurnBody(
    detail: BurnDetail?,
    allowEdit: Boolean,
    onHcToggle: (() -> Unit)?,
    onDelete: (tw.dietbook.BurnLine) -> Unit,
) {
    if (detail == null || detail.lines.isEmpty()) {
        EmptyCute("今天還沒運動～", "散步或記一筆有氧，小豬也會跟你一起喘吁吁 ♡")
        FormulaNote(detail?.note ?: "日結時若無活動數據，會用 TDEE 估算。")
        return
    }
    FormulaNote(detail.note)
    if (allowEdit && detail.hasHc) {
        Spacer(Modifier.height(8.dp))
        Text(
            if (detail.hcIgnored) "↩ 恢復使用 Health Connect" else "🙈 忽略今日 Health Connect",
            color = PrimaryPink,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(CardPink)
                .border(1.dp, LinePink, RoundedCornerShape(14.dp))
                .clickable { onHcToggle?.invoke() }
                .padding(10.dp),
        )
    }
    Spacer(Modifier.height(8.dp))
    detail.lines.forEach { line ->
        val editable = allowEdit && line.kind != DeletableKind.HC
        DeletableRow(editable, onDelete = { onDelete(line) }) {
            Column {
                Text(line.label, color = TextMain, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Text("${line.source}・${line.duration}", color = TextMuted, fontSize = 11.sp)
                Spacer(Modifier.height(4.dp))
                Text(line.formula, color = TextMuted, fontSize = 11.sp)
                Text("${line.kcal} kcal", color = PrimaryPink, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
        }
        Spacer(Modifier.height(8.dp))
    }
    TotalBar("運動消耗合計", "${detail.totalKcal}", "kcal")
}

@Composable
private fun StrengthBody(detail: StrengthDetail?, allowEdit: Boolean, onDelete: (tw.dietbook.StrengthLine) -> Unit) {
    if (detail == null || detail.lines.isEmpty()) {
        EmptyCute("鍛鍊日記還是空白的～", "練一下手臂／腿，小豬會慢慢壯起來喔 ♡")
        return
    }
    FormulaNote("合計以分鐘為主；熱量＝重訓 MET × 體重 × 小時（會併入運動消耗）。")
    Spacer(Modifier.height(8.dp))
    detail.lines.forEach { line ->
        DeletableRow(allowEdit, onDelete = { onDelete(line) }) {
            Column {
                Text("${line.part}・${line.exercise}", color = TextMain, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Text("${line.time}・${line.sets} 組・${line.minutes} 分", color = TextMuted, fontSize = 11.sp)
                if (line.kcal > 0) Text("${line.kcal} kcal", color = TextMuted, fontSize = 11.sp)
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

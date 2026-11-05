package kr.koasis.travelyunnan.ui.expense

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kr.koasis.travelyunnan.data.AppSettings
import kr.koasis.travelyunnan.data.TripData
import kr.koasis.travelyunnan.data.db.Expense
import kr.koasis.travelyunnan.domain.DayResolver
import kr.koasis.travelyunnan.domain.TripPhase
import kr.koasis.travelyunnan.ui.AppViewModel
import kr.koasis.travelyunnan.ui.common.*
import kr.koasis.travelyunnan.ui.nav.LocalActions
import kr.koasis.travelyunnan.util.External
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

val CATEGORIES = listOf("교통·투어·입장료", "숙박", "식비", "쇼핑·기념품·예비비")
val METHODS = listOf("위챗", "알리페이", "현금", "카드")
val PAYERS = listOf("공동", "아빠", "첫째", "둘째")

/** 예산 구분명(엑셀) → 지출 구분 */
fun budgetKey(category: String) = when {
    category.startsWith("숙박") -> "숙박"
    category.startsWith("식비") -> "식비"
    category.startsWith("쇼핑") -> "쇼핑·기념품·예비비"
    category.startsWith("교통") -> "교통·투어·입장료"
    else -> category
}

fun Expense.krw(rate: Double): Double = if (currency == "CNY") amount * rate else amount

fun expensesCsv(list: List<Expense>, rate: Double): String {
    val f = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
    val head = "일시,일차,금액,통화,원화환산,구분,결제수단,지불자,메모"
    return (listOf(head) + list.sortedBy { it.at }.map { e ->
        val t = LocalDateTime.ofInstant(Instant.ofEpochMilli(e.at), ZoneId.of("Asia/Shanghai")).format(f)
        listOf(t, e.dayId, e.amount, e.currency, e.krw(rate).toLong(), e.category, e.method, e.payer, "\"${e.memo.replace("\"", "'")}\"").joinToString(",")
    }).joinToString("\n")
}

@Composable
fun ExpenseScreen(vm: AppViewModel, data: TripData) {
    val list by vm.expenses.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val now by vm.now.collectAsStateWithLifecycle()
    ExpenseContent(data, list, settings, now, vm::addExpense, vm::deleteExpense, vm::setCnyRate, vm::setCash)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpenseContent(
    data: TripData, list: List<Expense>, settings: AppSettings, now: LocalDateTime,
    onAdd: (Expense) -> Unit, onDelete: (Expense) -> Unit, onRate: (Double?) -> Unit, onCash: (Double) -> Unit,
) {
    val actions = LocalActions.current
    val ctx = LocalContext.current
    val rate = settings.cnyRate ?: data.rates.CNY
    val todayId = (DayResolver.resolve(now, data.days) as? TripPhase.OnTrip)?.day?.id ?: "D1"
    var showAdd by rememberSaveable { mutableStateOf(false) }
    var showSettings by rememberSaveable { mutableStateOf(false) }
    val spentByCat = list.groupBy { it.category }.mapValues { (_, v) -> v.sumOf { it.krw(rate) } }
    val total = list.sumOf { it.krw(rate) }
    val cashSpent = list.filter { it.method == "현금" && it.currency == "CNY" }.sumOf { it.amount }
    val budgetTotal = data.budget.firstOrNull { it.isTotal }?.amountKrw ?: 0.0

    Column(Modifier.fillMaxSize()) {
        AppTopBar("경비", onBack = actions.back) {
            IconButton(onClick = { showSettings = true }) { Icon(Icons.Filled.Tune, "환율·현금") }
            IconButton(onClick = {
                val csv = expensesCsv(list, rate)
                External.copy(ctx, csv, "CSV 를 복사했다")
                runCatching { ctx.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_SUBJECT, "윈난 여행 지출").putExtra(Intent.EXTRA_TEXT, csv), "지출 내보내기")) }
            }) { Icon(Icons.Filled.Share, "CSV 내보내기") }
        }
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(bottom = 96.dp)) {
            item {
                Card(Modifier.padding(16.dp).fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("지출 합계 %,d원".format(total.toLong()), style = MaterialTheme.typography.titleLarge)
                        Text("예산 %,d원 (항공 제외, 3인) · ¥1 = ${rate.toInt()}원".format(budgetTotal.toLong()), style = MaterialTheme.typography.bodyMedium)
                        BudgetBar(total, budgetTotal)
                        if (settings.cashCny > 0) Text("현금 잔액 약 ¥${(settings.cashCny - cashSpent).toLong()} / ¥${settings.cashCny.toLong()}", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            item { SectionTitle("구분별 예산 대비") }
            items(data.budget.filter { !it.isTotal }) { b ->
                val key = budgetKey(b.category)
                val spent = spentByCat[key] ?: 0.0
                Column(Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                    Row { Text(b.category, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f)); Text("%,d / %,d원".format(spent.toLong(), b.amountKrw.toLong()), style = MaterialTheme.typography.bodyMedium) }
                    BudgetBar(spent, b.amountKrw)
                }
            }
            item { SectionTitle("지출 내역 (${list.size}건)") }
            if (list.isEmpty()) item { EmptyState("아직 기록이 없다. 아래 + 로 추가한다") }
            items(list, key = { it.id }) { e -> ExpenseRow(e, rate) { onDelete(e) } }
        }
    }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomEnd) {
        ExtendedFloatingActionButton(onClick = { showAdd = true }, modifier = Modifier.padding(20.dp), icon = { Icon(Icons.Filled.Add, null) }, text = { Text("지출 추가") })
    }
    if (showAdd) AddExpenseSheet(todayId, data.days.map { it.id }, onDismiss = { showAdd = false }) { onAdd(it); showAdd = false }
    if (showSettings) RateSheet(rate, settings.cashCny, onDismiss = { showSettings = false }, onRate = onRate, onCash = onCash)
}

@Composable
private fun BudgetBar(spent: Double, budget: Double) {
    val frac = if (budget > 0) (spent / budget).toFloat().coerceIn(0f, 1f) else 0f
    val over = budget > 0 && spent > budget
    Box(Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(5.dp)).background(MaterialTheme.colorScheme.surfaceVariant)) {
        Box(Modifier.fillMaxWidth(frac).fillMaxHeight().background(if (over) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary))
    }
}

@Composable
private fun ExpenseRow(e: Expense, rate: Double, onDelete: () -> Unit) {
    val t = LocalDateTime.ofInstant(Instant.ofEpochMilli(e.at), ZoneId.of("Asia/Shanghai"))
    ListItem(
        headlineContent = { Text((if (e.currency == "CNY") "¥%,.0f".format(e.amount) else "%,.0f원".format(e.amount)) + "  " + e.memo, fontWeight = FontWeight.SemiBold) },
        supportingContent = { Text("${e.dayId} ${t.format(DateTimeFormatter.ofPattern("M/d HH:mm"))} · ${e.category} · ${e.method} · ${e.payer}" + if (e.currency == "CNY") " · 약 %,d원".format(e.krw(rate).toLong()) else "") },
        trailingContent = { IconButton(onClick = onDelete) { Icon(Icons.Filled.Delete, "삭제", tint = MaterialTheme.colorScheme.outline) } },
    )
}

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun AddExpenseSheet(todayId: String, dayIds: List<String>, onDismiss: () -> Unit, onSave: (Expense) -> Unit) {
    var amount by rememberSaveable { mutableStateOf("") }
    var currency by rememberSaveable { mutableStateOf("CNY") }
    var category by rememberSaveable { mutableStateOf(CATEGORIES[2]) }
    var method by rememberSaveable { mutableStateOf(METHODS[0]) }
    var payer by rememberSaveable { mutableStateOf(PAYERS[0]) }
    var day by rememberSaveable { mutableStateOf(todayId) }
    var memo by rememberSaveable { mutableStateOf("") }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("지출 추가", style = MaterialTheme.typography.titleLarge)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(amount, { amount = it.filter { c -> c.isDigit() || c == '.' } }, label = { Text("금액") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f), singleLine = true)
                listOf("CNY", "KRW").forEach { c -> FilterChip(currency == c, onClick = { currency = c }, label = { Text(if (c == "CNY") "¥ 위안" else "₩ 원") }) }
            }
            OutlinedTextField(memo, { memo = it }, label = { Text("메모 (가게·항목)") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            Text("구분", style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { CATEGORIES.forEach { c -> FilterChip(category == c, onClick = { category = c }, label = { Text(c) }) } }
            Text("결제 · 지불자", style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { METHODS.forEach { c -> FilterChip(method == c, onClick = { method = c }, label = { Text(c) }) } }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { PAYERS.forEach { c -> FilterChip(payer == c, onClick = { payer = c }, label = { Text(c) }) } }
            Text("일차", style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) { dayIds.forEach { d -> FilterChip(day == d, onClick = { day = d }, label = { Text(d) }) } }
            Button(
                onClick = { amount.toDoubleOrNull()?.let { onSave(Expense(dayId = day, amount = it, currency = currency, category = category, memo = memo.trim(), method = method, payer = payer)) } },
                enabled = amount.toDoubleOrNull() != null, modifier = Modifier.fillMaxWidth(),
            ) { Text("저장") }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RateSheet(rate: Double, cash: Double, onDismiss: () -> Unit, onRate: (Double?) -> Unit, onCash: (Double) -> Unit) {
    var r by rememberSaveable { mutableStateOf(rate.toInt().toString()) }
    var c by rememberSaveable { mutableStateOf(if (cash > 0) cash.toLong().toString() else "") }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("환율·현금", style = MaterialTheme.typography.titleLarge)
            OutlinedTextField(r, { r = it.filter { ch -> ch.isDigit() } }, label = { Text("¥1 = 원") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(c, { c = it.filter { ch -> ch.isDigit() } }, label = { Text("가져온 현금 (¥)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true, modifier = Modifier.fillMaxWidth())
            Button(onClick = { onRate(r.toDoubleOrNull()); onCash(c.toDoubleOrNull() ?: 0.0); onDismiss() }, modifier = Modifier.fillMaxWidth()) { Text("저장") }
        }
    }
}

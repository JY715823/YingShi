package com.example.yingshi.feature.ledger

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Flight
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.ImportExport
import androidx.compose.material.icons.filled.LocalHospital
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.RadioButtonChecked
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Redeem
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Timelapse
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material.icons.filled.Wallet
import androidx.compose.material.icons.filled.Work
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.yingshi.feature.ledger.data.LedgerAccount
import com.example.yingshi.feature.ledger.data.LedgerAccountType
import com.example.yingshi.feature.ledger.data.LedgerBook
import com.example.yingshi.feature.ledger.data.ledgerBookTemplateLabel
import java.time.YearMonth

val LedgerHeaderGreen = Color(0xFF47B972)
val LedgerGreen = LedgerHeaderGreen
val LedgerGreenSoft = Color(0xFFEAF8EF)
val LedgerIncomeGreen = Color(0xFF4CAF50)
val LedgerExpenseRed = Color(0xFFF56B82)
val LedgerPageBackground = Color(0xFFF3F4FA)
val LedgerGroupedHeader = Color(0xFFF7F7F8)
val LedgerDivider = Color(0xFFE9E9EE)
val LedgerMuted = Color(0xFF9AA0A8)
val LedgerSubtleText = Color(0xFFB3B6BE)

fun ledgerColor(raw: Long): Color = Color(raw)

fun ledgerIcon(key: String): ImageVector = when (key) {
    "restaurant" -> Icons.Default.Restaurant
    "directions_car" -> Icons.Default.DirectionsCar
    "shopping_bag" -> Icons.Default.ShoppingBag
    "home" -> Icons.Default.Home
    "school" -> Icons.Default.School
    "local_hospital" -> Icons.Default.LocalHospital
    "flight" -> Icons.Default.Flight
    "payments" -> Icons.Default.Payments
    "work" -> Icons.Default.Work
    "redeem" -> Icons.Default.Redeem
    "trending_up" -> Icons.Default.TrendingUp
    "undo" -> Icons.Default.Undo
    "wallet" -> Icons.Default.AccountBalanceWallet
    "wechat" -> Icons.Default.Wallet
    "alipay" -> Icons.Default.Payments
    "budget" -> Icons.Default.Savings
    "stats" -> Icons.Default.BarChart
    "asset" -> Icons.Default.AccountBalance
    "import" -> Icons.Default.ImportExport
    "calendar" -> Icons.Default.CalendarMonth
    "timelapse" -> Icons.Default.Timelapse
    "search" -> Icons.Default.Search
    "category" -> Icons.Default.Category
    "settings" -> Icons.Default.Settings
    "trash" -> Icons.Default.Delete
    "transfer" -> Icons.Default.SwapHoriz
    "more_horiz" -> Icons.Default.MoreHoriz
    else -> Icons.Default.MoreHoriz
}

fun accountIcon(type: LedgerAccountType): ImageVector = when (type) {
    LedgerAccountType.CASH -> Icons.Default.AccountBalanceWallet
    LedgerAccountType.DEBIT_CARD -> Icons.Default.AccountBalance
    LedgerAccountType.CREDIT -> Icons.Default.CreditCard
    LedgerAccountType.ALIPAY -> Icons.Default.Payments
    LedgerAccountType.WECHAT -> Icons.Default.Wallet
    LedgerAccountType.INVESTMENT -> Icons.Default.TrendingUp
    LedgerAccountType.DEBT -> Icons.Default.ArrowDownward
    LedgerAccountType.OTHER -> Icons.Default.MoreHoriz
}

object LedgerActionIcons {
    val Add = Icons.Default.Add
    val Back = Icons.Default.ArrowBack
    val Menu = Icons.Default.Menu
    val Expense = Icons.Default.ArrowDownward
    val Income = Icons.Default.ArrowUpward
    val Transfer = Icons.Default.ArrowForward
    val Edit = Icons.Default.Edit
    val Delete = Icons.Default.Delete
    val Restore = Icons.Default.Restore
}

@Composable
fun LedgerBottomSheetDialog(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.36f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onDismiss,
                    ),
            )
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .then(modifier),
                shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
                color = Color.White,
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    content = content,
                )
            }
        }
    }
}

@Composable
fun LedgerBookPickerSheet(
    books: List<LedgerBook>,
    selectedBookId: String,
    defaultBookId: String? = null,
    onDismiss: () -> Unit,
    onSelectBook: (String) -> Unit,
    onManageBooks: (() -> Unit)? = null,
) {
    LedgerBottomSheetDialog(onDismiss = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = "选择账本",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            books.forEach { book ->
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .clickable { onSelectBook(book.id) },
                    color = if (book.id == selectedBookId) LedgerGreenSoft else Color.White,
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(ledgerColor(book.coverColor).copy(alpha = if (book.id == selectedBookId) 0.18f else 0.12f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(ledgerIcon("wallet"), contentDescription = null, tint = ledgerColor(book.coverColor))
                        }
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Text(
                                    text = book.name,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                if (book.id == defaultBookId) {
                                    Surface(
                                        shape = RoundedCornerShape(999.dp),
                                        color = LedgerGreenSoft,
                                    ) {
                                        Text(
                                            text = "默认",
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                            color = LedgerHeaderGreen,
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = FontWeight.Bold,
                                        )
                                    }
                                }
                            }
                            Text(
                                text = ledgerBookTemplateLabel(book.template),
                                color = LedgerMuted,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        Icon(
                            if (book.id == selectedBookId) Icons.Default.RadioButtonChecked else Icons.Default.RadioButtonUnchecked,
                            contentDescription = null,
                            tint = if (book.id == selectedBookId) LedgerHeaderGreen else LedgerMuted,
                        )
                    }
                }
            }
            onManageBooks?.let { manageBooks ->
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .clickable {
                            onDismiss()
                            manageBooks()
                        },
                    color = Color(0xFFF8F8F9),
                ) {
                    Text(
                        text = "账本管理",
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 14.dp),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = LedgerHeaderGreen,
                    )
                }
            }
        }
    }
}

@Composable
fun LedgerMonthPickerSheet(
    selectedMonth: YearMonth,
    onDismiss: () -> Unit,
    onConfirm: (YearMonth) -> Unit,
) {
    val years = remember { (2020..2035).toList() }
    val months = remember { (1..12).toList() }
    var selectedYear by remember(selectedMonth) { mutableIntStateOf(selectedMonth.year.coerceIn(years.first(), years.last())) }
    var selectedMonthValue by remember(selectedMonth) { mutableIntStateOf(selectedMonth.monthValue.coerceIn(1, 12)) }
    LaunchedEffect(selectedMonth) {
        selectedYear = selectedMonth.year.coerceIn(years.first(), years.last())
        selectedMonthValue = selectedMonth.monthValue.coerceIn(1, 12)
    }
    LedgerBottomSheetDialog(onDismiss = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onDismiss) {
                    Text("取消", color = LedgerMuted, style = MaterialTheme.typography.bodyLarge)
                }
                Text(
                    text = "年月选择",
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                TextButton(onClick = { onConfirm(YearMonth.of(selectedYear, selectedMonthValue)) }) {
                    Text("确定", color = LedgerHeaderGreen, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(320.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                LedgerYearMonthWheelColumn(
                    title = "年",
                    values = years.map { "${it}年" },
                    selectedIndex = selectedYear - years.first(),
                    onSelected = { selectedYear = years[it] },
                    modifier = Modifier.weight(1f),
                )
                LedgerYearMonthWheelColumn(
                    title = "月",
                    values = months.map { "${it}月" },
                    selectedIndex = selectedMonthValue - 1,
                    onSelected = { selectedMonthValue = months[it] },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
fun LedgerDateTimePickerSheet(
    selectedTimeMillis: Long,
    onDismiss: () -> Unit,
    onConfirm: (Long) -> Unit,
) {
    com.example.yingshi.feature.photos.ViewerTimeEditorSheet(
        initialTimeMillis = selectedTimeMillis,
        onDismiss = onDismiss,
        onConfirm = onConfirm,
    )
}

@Composable
fun LedgerAccountPickerSheet(
    accounts: List<LedgerAccount>,
    selectedAccountId: String?,
    title: String = "账户",
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit,
) {
    LedgerBottomSheetDialog(onDismiss = onDismiss) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onDismiss) {
                    Text("取消", color = LedgerMuted, style = MaterialTheme.typography.bodyLarge)
                }
                Text(
                    text = title,
                    modifier = Modifier.weight(1f),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                Box(modifier = Modifier.size(48.dp))
            }
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 18.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                items(accounts, key = { it.id }) { account ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(18.dp))
                            .clickable { onSelect(account.id) }
                            .padding(horizontal = 8.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFF2F2F2)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(accountIcon(account.type), contentDescription = null, tint = ledgerColor(account.color))
                        }
                        Text(
                            text = account.name,
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = formatAmountValue(account.balanceCents),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Icon(
                            if (account.id == selectedAccountId) Icons.Default.RadioButtonChecked else Icons.Default.RadioButtonUnchecked,
                            contentDescription = null,
                            tint = if (account.id == selectedAccountId) Color(0xFF33A8FF) else LedgerMuted,
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun LedgerTransactionsDetailSheet(
    title: String = "账单明细",
    transactions: List<com.example.yingshi.feature.ledger.data.LedgerTransaction>,
    currencySymbol: String,
    onDismiss: () -> Unit,
    onTransactionClick: (com.example.yingshi.feature.ledger.data.LedgerTransaction) -> Unit,
) {
    val grouped = remember(transactions) {
        transactions.groupBy { dayStart(it.occurredAtMillis) }
            .toList()
            .sortedByDescending { it.first }
    }
    LedgerBottomSheetDialog(
        onDismiss = onDismiss,
        modifier = Modifier.fillMaxHeight(0.93f),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onDismiss) {
                    Text("取消", color = LedgerMuted, style = MaterialTheme.typography.bodyMedium)
                }
                Text(
                    text = title,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.width(56.dp))
            }
            if (grouped.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 32.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("没有更多数据", color = LedgerMuted, style = MaterialTheme.typography.bodySmall)
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 20.dp),
                ) {
                    grouped.forEach { (dayStart, dayTransactions) ->
                        val income = dayTransactions.filter { it.type == com.example.yingshi.feature.ledger.data.LedgerTransactionType.INCOME }.sumOf { it.amountCents }
                        val expense = dayTransactions.filter { it.type == com.example.yingshi.feature.ledger.data.LedgerTransactionType.EXPENSE }.sumOf { it.amountCents }
                        item {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(LedgerGroupedHeader)
                                    .padding(horizontal = 16.dp, vertical = 10.dp),
                            ) {
                                Text(
                                    formatLedgerGroupDate(dayStart),
                                    modifier = Modifier.weight(1f),
                                    color = LedgerSubtleText,
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                                Text(
                                    "收 ${formatAmountValue(income)}",
                                    color = LedgerSubtleText,
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                                Spacer(Modifier.width(12.dp))
                                Text(
                                    "支 ${formatAmountValue(expense)}",
                                    color = LedgerSubtleText,
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            }
                        }
                        items(dayTransactions, key = { it.id }) { transaction ->
                            LedgerTransactionListRow(
                                transaction = transaction,
                                currencySymbol = currencySymbol,
                                onClick = { onTransactionClick(transaction) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun LedgerSegmentChip(
    text: String,
    selected: Boolean,
    selectedColor: Color = LedgerHeaderGreen,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(22.dp))
            .clickable(onClick = onClick),
        color = if (selected) selectedColor else Color.White,
        border = BorderStroke(1.dp, if (selected) selectedColor else LedgerDivider),
    ) {
        Box(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = text,
                color = if (selected) Color.White else LedgerMuted,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                softWrap = false,
            )
        }
    }
}

@Composable
private fun LedgerYearMonthWheelColumn(
    title: String,
    values: List<String>,
    selectedIndex: Int,
    onSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    LaunchedEffect(selectedIndex) {
        listState.animateScrollToItem(selectedIndex.coerceIn(values.indices))
    }
    Column(modifier = modifier) {
        Text(
            text = title,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
            color = LedgerMuted,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(8.dp))
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = Color(0xFFF8F8F9),
            shape = RoundedCornerShape(22.dp),
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(292.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 108.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                itemsIndexed(values) { index, value ->
                    val selected = index == selectedIndex
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(if (selected) LedgerGreenSoft else Color.Transparent)
                            .clickable { onSelected(index) }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = value,
                            color = if (selected) LedgerHeaderGreen else MaterialTheme.colorScheme.onSurface,
                            style = if (selected) MaterialTheme.typography.bodyLarge else MaterialTheme.typography.bodyMedium,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                        )
                    }
                }
            }
        }
    }
}

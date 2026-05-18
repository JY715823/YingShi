package com.example.yingshi.feature.ledger

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.statusBarsPadding
import com.example.yingshi.feature.ledger.data.LedgerCategory
import com.example.yingshi.feature.ledger.data.LedgerCategoryType
import com.example.yingshi.feature.ledger.data.LedgerTransaction
import com.example.yingshi.feature.ledger.data.LedgerTransactionType
import java.time.Instant
import java.time.ZoneId

private enum class LedgerAccountPickerTarget {
    PRIMARY,
    TRANSFER_FROM,
    TRANSFER_TO,
}

@Composable
fun LedgerAddTransactionScreen(
    uiState: LedgerUiState,
    initialTransaction: LedgerTransaction? = null,
    onBack: () -> Unit,
    onSelectBook: (String) -> Unit,
    onSaveCategory: (String?, String, String, Long, LedgerCategoryType) -> Unit,
    onToggleCategoryHidden: (String, Boolean) -> Unit,
    onSave: (
        String?,
        LedgerTransactionType,
        Long,
        String?,
        String,
        String?,
        Long,
        String,
        Boolean,
    ) -> Unit,
) {
    var selectedType by rememberSaveable(initialTransaction?.id) { mutableStateOf((initialTransaction?.type ?: LedgerTransactionType.EXPENSE).name) }
    val type = LedgerTransactionType.valueOf(selectedType)
    val categoryType = if (type == LedgerTransactionType.INCOME) LedgerCategoryType.INCOME else LedgerCategoryType.EXPENSE
    val visibleCategories = remember(uiState.categories, categoryType) {
        uiState.categories.filter { it.type == categoryType }
    }
    val categoryOptions = remember(visibleCategories, initialTransaction?.id, categoryType) {
        buildList {
            addAll(visibleCategories)
            initialTransaction?.category
                ?.takeIf { category ->
                    category.type == categoryType && visibleCategories.none { it.id == category.id }
                }
                ?.let(::add)
        }.distinctBy { it.id }
    }
    val visibleAccounts = uiState.accounts
    val accountOptions = remember(visibleAccounts, initialTransaction?.id) {
        buildList {
            addAll(visibleAccounts)
            initialTransaction?.account
                ?.takeIf { account -> visibleAccounts.none { it.id == account.id } }
                ?.let(::add)
            initialTransaction?.toAccount
                ?.takeIf { account -> visibleAccounts.none { it.id == account.id } }
                ?.let(::add)
        }.distinctBy { it.id }
    }
    var selectedCategoryId by rememberSaveable(initialTransaction?.id, categoryOptions.firstOrNull()?.id) {
        mutableStateOf(initialTransaction?.category?.id ?: categoryOptions.firstOrNull()?.id)
    }
    var selectedAccountId by rememberSaveable(initialTransaction?.id, accountOptions.firstOrNull()?.id) {
        mutableStateOf(initialTransaction?.account?.id ?: accountOptions.firstOrNull()?.id.orEmpty())
    }
    var selectedToAccountId by rememberSaveable(initialTransaction?.id, accountOptions.getOrNull(1)?.id) {
        mutableStateOf(initialTransaction?.toAccount?.id ?: accountOptions.getOrNull(1)?.id)
    }
    var accountPickerTarget by rememberSaveable(initialTransaction?.id) {
        mutableStateOf(LedgerAccountPickerTarget.PRIMARY.name)
    }
    var remark by rememberSaveable(initialTransaction?.id) { mutableStateOf(initialTransaction?.remark.orEmpty()) }
    var expression by rememberSaveable(initialTransaction?.id) { mutableStateOf(initialTransaction?.amountCents?.let(::formatAmountValue)?.replace(",", "") ?: "0") }
    var occurredAtMillis by rememberSaveable(initialTransaction?.id) { mutableLongStateOf(initialTransaction?.occurredAtMillis ?: System.currentTimeMillis()) }
    var showBookSheet by rememberSaveable { mutableStateOf(false) }
    var showDateSheet by rememberSaveable { mutableStateOf(false) }
    var showAccountSheet by rememberSaveable { mutableStateOf(false) }
    var showCategoryManager by rememberSaveable { mutableStateOf(false) }
    val evaluatedExpression = LedgerCalculator.evaluate(expression)
    val amountCents = evaluatedExpression?.toCentsOrNull() ?: expression.toCentsOrNull() ?: 0L
    val amountDisplayText = evaluatedExpression?.let { formatAmountValue(it.toCentsOrNull() ?: 0L) } ?: expression

    LaunchedEffect(uiState.currentBookId, type, accountOptions, categoryOptions, initialTransaction?.id) {
        val accountIds = accountOptions.map { it.id }
        val primaryAccountId = accountOptions.firstOrNull()?.id.orEmpty()
        val secondaryAccountId = accountOptions.getOrNull(1)?.id ?: primaryAccountId
        if (selectedAccountId !in accountIds) {
            selectedAccountId = primaryAccountId
        }
        if (type == LedgerTransactionType.TRANSFER) {
            if (selectedToAccountId !in accountIds || selectedToAccountId == selectedAccountId) {
                selectedToAccountId = secondaryAccountId.takeIf { it.isNotBlank() && it != selectedAccountId } ?: primaryAccountId
            }
        } else if (selectedToAccountId !in accountIds) {
            selectedToAccountId = secondaryAccountId
        }
        if (selectedCategoryId !in categoryOptions.map { it.id }) {
            selectedCategoryId = categoryOptions.firstOrNull()?.id
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            LedgerAddTopBar(
                bookName = uiState.bookName,
                onBack = onBack,
                onBookClick = { showBookSheet = true },
                onManageClick = { showCategoryManager = true },
            )
            LedgerAddTypeTabs(
                selected = type,
                onSelected = {
                    selectedType = it.name
                    if (it != LedgerTransactionType.TRANSFER) {
                        val nextType = if (it == LedgerTransactionType.INCOME) LedgerCategoryType.INCOME else LedgerCategoryType.EXPENSE
                        selectedCategoryId = uiState.categories.firstOrNull { category -> category.type == nextType }?.id
                            ?: initialTransaction?.category?.takeIf { category -> category.type == nextType }?.id
                    }
                },
            )
            if (type == LedgerTransactionType.TRANSFER) {
                LedgerTransferSelector(
                    accounts = accountOptions,
                    selectedFromAccountId = selectedAccountId,
                    selectedToAccountId = selectedToAccountId,
                    onFromClick = {
                        accountPickerTarget = LedgerAccountPickerTarget.TRANSFER_FROM.name
                        showAccountSheet = true
                    },
                    onToClick = {
                        accountPickerTarget = LedgerAccountPickerTarget.TRANSFER_TO.name
                        showAccountSheet = true
                    },
                    onSwapClick = {
                        val from = selectedAccountId
                        selectedAccountId = selectedToAccountId ?: selectedAccountId
                        selectedToAccountId = from
                    },
                )
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(5),
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 286.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    items(categoryOptions, key = { it.id }) { category ->
                        LedgerCategoryGridItem(
                            category = category,
                            selected = category.id == selectedCategoryId,
                            onClick = { selectedCategoryId = category.id },
                        )
                    }
                }
            }
        }

            LedgerAmountKeyboardPanel(
                modifier = Modifier.align(Alignment.BottomCenter),
                type = type,
                amountText = amountDisplayText,
                remark = remark,
                dateLabel = formatLedgerPickerDate(occurredAtMillis),
                accountLabel = accountOptions.firstOrNull { it.id == selectedAccountId }?.name ?: "请选择账户",
                onRemarkChange = { remark = it },
                onDateClick = { showDateSheet = true },
            onAccountClick = {
                accountPickerTarget = LedgerAccountPickerTarget.PRIMARY.name
                showAccountSheet = true
            },
            onKeyClick = { key ->
                expression = when (key) {
                    "⌫" -> expression.dropLast(1).ifBlank { "0" }
                    "=" -> LedgerCalculator.evaluate(expression) ?: expression
                    else -> appendKeyboardInput(expression, key)
                }
            },
            onSaveContinue = {
                onSave(
                    initialTransaction?.id,
                    type,
                    amountCents,
                    selectedCategoryId,
                    selectedAccountId,
                    selectedToAccountId,
                    occurredAtMillis,
                    remark,
                    true,
                )
                expression = "0"
                remark = ""
            },
            onDone = {
                onSave(
                    initialTransaction?.id,
                    type,
                    amountCents,
                    selectedCategoryId,
                    selectedAccountId,
                    selectedToAccountId,
                    occurredAtMillis,
                    remark,
                    false,
                )
            },
            saveEnabled = isDraftValid(type, amountCents, selectedCategoryId, selectedAccountId, selectedToAccountId),
        )
    }

    if (showBookSheet) {
        LedgerBookPickerSheet(
            books = uiState.books,
            selectedBookId = uiState.currentBookId,
            onDismiss = { showBookSheet = false },
            onSelectBook = {
                onSelectBook(it)
                showBookSheet = false
            },
        )
    }
    if (showDateSheet) {
        LedgerDateTimePickerSheet(
            selectedTimeMillis = occurredAtMillis,
            onDismiss = { showDateSheet = false },
            onConfirm = {
                occurredAtMillis = it
                showDateSheet = false
            },
        )
    }
    if (showAccountSheet) {
        LedgerAccountPickerSheet(
            accounts = accountOptions,
            selectedAccountId = when (LedgerAccountPickerTarget.valueOf(accountPickerTarget)) {
                LedgerAccountPickerTarget.PRIMARY,
                LedgerAccountPickerTarget.TRANSFER_FROM -> selectedAccountId
                LedgerAccountPickerTarget.TRANSFER_TO -> selectedToAccountId ?: selectedAccountId
            },
            onDismiss = { showAccountSheet = false },
            onSelect = {
                when (LedgerAccountPickerTarget.valueOf(accountPickerTarget)) {
                    LedgerAccountPickerTarget.PRIMARY,
                    LedgerAccountPickerTarget.TRANSFER_FROM -> selectedAccountId = it
                    LedgerAccountPickerTarget.TRANSFER_TO -> selectedToAccountId = it
                }
                showAccountSheet = false
            },
        )
    }
    if (showCategoryManager) {
        LedgerCategoriesScreen(
            uiState = uiState,
            onBack = { showCategoryManager = false },
            onSaveCategory = onSaveCategory,
            onToggleCategoryHidden = onToggleCategoryHidden,
        )
    }
}

@Composable
private fun LedgerAddTopBar(
    bookName: String,
    onBack: () -> Unit,
    onBookClick: () -> Unit,
    onManageClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 6.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack, modifier = Modifier.size(30.dp)) {
            Icon(LedgerActionIcons.Back, contentDescription = "返回", modifier = Modifier.size(16.dp))
        }
        Row(
            modifier = Modifier
                .weight(1f)
                .clickable(onClick = onBookClick),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = bookName,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Icon(Icons.Default.ArrowDropDown, contentDescription = "切换账本", modifier = Modifier.size(13.dp))
        }
        IconButton(onClick = onManageClick, modifier = Modifier.size(30.dp)) {
            Icon(Icons.Default.Add, contentDescription = "分类管理", modifier = Modifier.size(16.dp))
        }
    }
}

@Composable
private fun LedgerAddTypeTabs(
    selected: LedgerTransactionType,
    onSelected: (LedgerTransactionType) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 34.dp, vertical = 0.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        listOf(
            LedgerTransactionType.EXPENSE to "支出",
            LedgerTransactionType.INCOME to "收入",
            LedgerTransactionType.TRANSFER to "转账",
        ).forEach { (type, label) ->
            Column(
                modifier = Modifier.clickable { onSelected(type) },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (selected == type) LedgerHeaderGreen else LedgerMuted,
                    fontWeight = FontWeight.Bold,
                )
                Box(
                    modifier = Modifier
                        .width(40.dp)
                        .height(3.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(if (selected == type) LedgerHeaderGreen else Color.Transparent),
                )
            }
        }
    }
}

@Composable
private fun LedgerCategoryGridItem(
    category: LedgerCategory,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier.clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Box(
            modifier = Modifier
                .size(54.dp)
                .then(if (selected) Modifier.border(2.dp, LedgerHeaderGreen, CircleShape) else Modifier),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(ledgerColor(category.color).copy(alpha = 0.92f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(ledgerIcon(category.iconKey), contentDescription = category.name, tint = Color.White, modifier = Modifier.size(22.dp))
            }
        }
        Text(
            text = category.name,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun LedgerTransferSelector(
    accounts: List<com.example.yingshi.feature.ledger.data.LedgerAccount>,
    selectedFromAccountId: String,
    selectedToAccountId: String?,
    onFromClick: () -> Unit,
    onToClick: () -> Unit,
    onSwapClick: () -> Unit,
) {
    val selectedFrom = accounts.firstOrNull { it.id == selectedFromAccountId }?.name ?: "转出账户"
    val selectedTo = accounts.firstOrNull { it.id == selectedToAccountId }?.name ?: "转入账户"
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        TransferAccountBox(title = "转出账户", value = selectedFrom, onClick = onFromClick)
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center,
        )
        {
            Surface(
                shape = CircleShape,
                color = Color(0xFFF4F5F7),
            ) {
                IconButton(onClick = onSwapClick, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Default.SwapHoriz, contentDescription = "交换账户", tint = LedgerHeaderGreen, modifier = Modifier.size(18.dp))
                }
            }
        }
        TransferAccountBox(title = "转入账户", value = selectedTo, onClick = onToClick)
    }
}

@Composable
private fun TransferAccountBox(
    title: String,
    value: String,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(76.dp)
            .clickable(onClick = onClick),
        color = Color(0xFFF8F8F9),
        shape = RoundedCornerShape(22.dp),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(text = title, color = LedgerMuted, style = MaterialTheme.typography.bodyMedium)
            Text(text = value, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun LedgerAmountKeyboardPanel(
    modifier: Modifier = Modifier,
    type: LedgerTransactionType,
    amountText: String,
    remark: String,
    dateLabel: String,
    accountLabel: String,
    onRemarkChange: (String) -> Unit,
    onDateClick: () -> Unit,
    onAccountClick: () -> Unit,
    onKeyClick: (String) -> Unit,
    onSaveContinue: () -> Unit,
    onDone: () -> Unit,
    saveEnabled: Boolean,
) {
    val amountColor = when (type) {
        LedgerTransactionType.EXPENSE -> LedgerExpenseRed
        LedgerTransactionType.INCOME -> LedgerHeaderGreen
        LedgerTransactionType.TRANSFER -> LedgerHeaderGreen
    }
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = Color.White,
        shadowElevation = 10.dp,
        shape = RoundedCornerShape(topStart = 0.dp, topEnd = 0.dp),
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = if (remark.isBlank()) "添加备注" else remark,
                    modifier = Modifier
                        .weight(1f)
                    .clickable { onRemarkChange("") },
                    color = if (remark.isBlank()) LedgerMuted else Color.Black,
                    style = MaterialTheme.typography.bodyLarge,
                )
                Text(
                    text = amountText.ifBlank { "0.00" },
                    color = amountColor,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .clickable(onClick = onDateClick),
                    verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(ledgerIcon("calendar"), contentDescription = null, tint = LedgerMuted)
                    Text(dateLabel, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                }
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .clickable(onClick = onAccountClick),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.End,
                ) {
                    Icon(accountIcon(com.example.yingshi.feature.ledger.data.LedgerAccountType.WECHAT), contentDescription = null, tint = LedgerHeaderGreen, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        accountLabel,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            val keys = listOf(
                listOf("1", "2", "3", "⌫"),
                listOf("4", "5", "6", "+"),
                listOf("7", "8", "9", "-"),
            )
            keys.forEach { row ->
                Row(modifier = Modifier.fillMaxWidth()) {
                    row.forEach { key ->
                        LedgerKeyboardKey(
                            text = key,
                            modifier = Modifier.weight(1f),
                            onClick = { onKeyClick(key) },
                        )
                    }
                }
            }
            Row(modifier = Modifier.fillMaxWidth()) {
                LedgerKeyboardKey(
                    text = "保存再记",
                    modifier = Modifier.weight(1.25f),
                    textStyle = MaterialTheme.typography.bodyLarge,
                    onClick = { if (saveEnabled) onSaveContinue() },
                )
                LedgerKeyboardKey(text = "0", modifier = Modifier.weight(1f), onClick = { onKeyClick("0") })
                LedgerKeyboardKey(text = ".", modifier = Modifier.weight(1f), onClick = { onKeyClick(".") })
                LedgerKeyboardKey(text = "=", modifier = Modifier.weight(1f), textStyle = MaterialTheme.typography.titleMedium, onClick = { onKeyClick("=") })
                LedgerKeyboardKey(
                    text = "完成",
                    modifier = Modifier.weight(1.15f),
                    filled = true,
                    onClick = { if (saveEnabled) onDone() },
                )
            }
        }
    }
}

@Composable
private fun LedgerKeyboardKey(
    text: String,
    modifier: Modifier = Modifier,
    filled: Boolean = false,
    textStyle: androidx.compose.ui.text.TextStyle = MaterialTheme.typography.titleLarge,
    onClick: () -> Unit,
 ) {
    Box(
        modifier = modifier
            .height(56.dp)
            .background(if (filled) LedgerHeaderGreen else Color.White)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (text == "⌫") {
            Icon(Icons.Default.Close, contentDescription = "删除", tint = Color.Black, modifier = Modifier.size(24.dp))
        } else {
            Text(
                text = text,
                style = textStyle,
                fontWeight = FontWeight.Bold,
                color = if (filled) Color.White else Color.Black,
                textAlign = TextAlign.Center,
            )
        }
    }
}

private fun appendKeyboardInput(expression: String, key: String): String {
    if (key == "." && expression.substringAfterLastAny(listOf("+", "-", "×", "÷")).contains(".")) return expression
    val base = if (expression == "0" && key !in listOf(".", "+", "-", "×", "÷")) "" else expression
    return base + key
}

private fun String.substringAfterLastAny(delimiters: List<String>): String {
    val index = delimiters.maxOf { lastIndexOf(it) }
    return if (index >= 0) substring(index + 1) else this
}

private fun isDraftValid(
    type: LedgerTransactionType,
    amountCents: Long,
    categoryId: String?,
    accountId: String,
    toAccountId: String?,
): Boolean {
    if (amountCents <= 0 || accountId.isBlank()) return false
    if (type != LedgerTransactionType.TRANSFER && categoryId.isNullOrBlank()) return false
    if (type == LedgerTransactionType.TRANSFER && (toAccountId.isNullOrBlank() || accountId == toAccountId)) return false
    return true
}

private fun formatLedgerPickerDate(millis: Long): String {
    val dateTime = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDateTime()
    val today = java.time.LocalDate.now()
    return if (dateTime.toLocalDate() == today) {
        "今天 %02d:%02d".format(dateTime.hour, dateTime.minute)
    } else {
        "%02d/%02d %02d:%02d".format(dateTime.monthValue, dateTime.dayOfMonth, dateTime.hour, dateTime.minute)
    }
}

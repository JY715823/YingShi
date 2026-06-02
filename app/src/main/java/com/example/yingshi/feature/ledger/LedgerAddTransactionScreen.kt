package com.example.yingshi.feature.ledger

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import com.example.yingshi.ui.components.yingShiClickable
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
    initialOccurredAtMillis: Long? = null,
    onBack: () -> Unit,
    onSelectBook: (String) -> Unit,
    onSaveCategory: (String?, String, String, Long, LedgerCategoryType) -> Unit,
    onToggleCategoryHidden: (String, Boolean) -> Unit,
    onReorderCategories: (LedgerCategoryType, List<String>) -> Unit,
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
    onDelete: (String) -> Unit,
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
    val newDraftPrimaryAccountId = remember(uiState.defaultAccountIdForCurrentBook, visibleAccounts) {
        uiState.defaultAccountIdForCurrentBook
            ?.takeIf { defaultId -> visibleAccounts.any { it.id == defaultId } }
            ?: visibleAccounts.firstOrNull()?.id.orEmpty()
    }
    val newDraftTransferInAccountId = remember(newDraftPrimaryAccountId, visibleAccounts) {
        visibleAccounts.firstOrNull { it.id != newDraftPrimaryAccountId }?.id
    }
    var selectedCategoryId by rememberSaveable(initialTransaction?.id, categoryOptions.firstOrNull()?.id) {
        mutableStateOf(initialTransaction?.category?.id ?: categoryOptions.firstOrNull()?.id)
    }
    var selectedAccountId by rememberSaveable(initialTransaction?.id, uiState.currentBookId, newDraftPrimaryAccountId) {
        mutableStateOf(initialTransaction?.account?.id ?: newDraftPrimaryAccountId)
    }
    var selectedToAccountId by rememberSaveable(initialTransaction?.id, uiState.currentBookId, newDraftTransferInAccountId) {
        mutableStateOf(initialTransaction?.toAccount?.id ?: newDraftTransferInAccountId)
    }
    var accountPickerTarget by rememberSaveable(initialTransaction?.id) {
        mutableStateOf(LedgerAccountPickerTarget.PRIMARY.name)
    }
    var remark by rememberSaveable(initialTransaction?.id) { mutableStateOf(initialTransaction?.remark.orEmpty()) }
    var expression by rememberSaveable(initialTransaction?.id) { mutableStateOf(initialTransaction?.amountCents?.let(::formatAmountValue)?.replace(",", "") ?: "0") }
    var occurredAtMillis by rememberSaveable(initialTransaction?.id, initialOccurredAtMillis) {
        mutableLongStateOf(initialTransaction?.occurredAtMillis ?: initialOccurredAtMillis ?: System.currentTimeMillis())
    }
    var showBookSheet by rememberSaveable { mutableStateOf(false) }
    var showDateSheet by rememberSaveable { mutableStateOf(false) }
    var showAccountSheet by rememberSaveable { mutableStateOf(false) }
    var showCategoryManager by rememberSaveable { mutableStateOf(false) }
    var showRemarkDialog by rememberSaveable { mutableStateOf(false) }
    var showDeleteDialog by rememberSaveable { mutableStateOf(false) }
    val evaluatedExpression = LedgerCalculator.evaluate(expression)
    val amountCents = evaluatedExpression?.toCentsOrNull() ?: expression.toCentsOrNull() ?: 0L
    val amountDisplayText = evaluatedExpression?.let { formatAmountValue(it.toCentsOrNull() ?: 0L) } ?: expression
    var lastBookId by rememberSaveable(initialTransaction?.id) { mutableStateOf(uiState.currentBookId) }

    LaunchedEffect(uiState.currentBookId, initialTransaction?.id, newDraftPrimaryAccountId, newDraftTransferInAccountId, categoryOptions, type) {
        if (initialTransaction == null && lastBookId != uiState.currentBookId) {
            selectedAccountId = newDraftPrimaryAccountId
            selectedToAccountId = newDraftTransferInAccountId
            if (type != LedgerTransactionType.TRANSFER) {
                selectedCategoryId = categoryOptions.firstOrNull()?.id
            }
            lastBookId = uiState.currentBookId
        }
    }

    LaunchedEffect(uiState.currentBookId, type, accountOptions, categoryOptions, initialTransaction?.id) {
        val accountIds = accountOptions.map { it.id }
        val primaryAccountId = if (initialTransaction == null) {
            newDraftPrimaryAccountId
        } else {
            accountOptions.firstOrNull()?.id.orEmpty()
        }
        val secondaryAccountId = if (initialTransaction == null) {
            newDraftTransferInAccountId ?: accountOptions.firstOrNull { it.id != primaryAccountId }?.id ?: primaryAccountId
        } else {
            accountOptions.getOrNull(1)?.id ?: primaryAccountId
        }
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
            .background(LedgerPageBackground)
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
                isEditing = initialTransaction != null,
                amountText = amountDisplayText,
                remark = remark,
                dateLabel = formatLedgerPickerDate(occurredAtMillis),
                accountLabel = accountOptions.firstOrNull { it.id == selectedAccountId }?.name ?: "请选择账户",
                onRemarkClick = { showRemarkDialog = true },
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
                onDeleteClick = {
                    showDeleteDialog = true
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
            defaultBookId = uiState.defaultBookId,
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
            onReorderCategories = onReorderCategories,
        )
    }
    if (showRemarkDialog) {
        LedgerRemarkDialog(
            initialRemark = remark,
            onDismiss = { showRemarkDialog = false },
            onConfirm = {
                remark = it
                showRemarkDialog = false
            },
        )
    }
    if (showDeleteDialog && initialTransaction != null) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            containerColor = LedgerRaisedSurface,
            title = {
                Text(
                    text = "删除这笔账单？",
                    color = LedgerHeaderGreen,
                    fontWeight = FontWeight.Bold,
                )
            },
            text = {
                Text(
                    text = "删除后会先进入回收站，可以在回收站恢复。",
                    color = LedgerMuted,
                    style = MaterialTheme.typography.bodyMedium,
                )
            },
            confirmButton = {
                LedgerDialogActionButton(
                    text = "移入回收站",
                    danger = true,
                    onClick = {
                        showDeleteDialog = false
                        onDelete(initialTransaction.id)
                    },
                )
            },
            dismissButton = {
                LedgerDialogActionButton(text = "取消", onClick = { showDeleteDialog = false })
            },
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
            Icon(LedgerActionIcons.Back, contentDescription = "返回", tint = LedgerHeaderGreen, modifier = Modifier.size(16.dp))
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
                color = LedgerHeaderGreen,
            )
            Icon(Icons.Default.ArrowDropDown, contentDescription = "切换账本", tint = LedgerHeaderGreen, modifier = Modifier.size(13.dp))
        }
        IconButton(onClick = onManageClick, modifier = Modifier.size(30.dp)) {
            Icon(Icons.Default.Add, contentDescription = "分类管理", tint = LedgerHeaderGreen, modifier = Modifier.size(16.dp))
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
                        .background(if (selected == type) LedgerPrimaryAction else Color.Transparent),
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
                .then(if (selected) Modifier.border(2.dp, LedgerGlassStroke, CircleShape) else Modifier),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(ledgerColor(category.color).copy(alpha = 0.88f)),
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
                color = LedgerPrimaryAction,
                border = BorderStroke(1.dp, LedgerGlassStroke),
            ) {
                IconButton(onClick = onSwapClick, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Default.SwapHoriz, contentDescription = "交换账户", tint = LedgerOnPrimaryAction, modifier = Modifier.size(18.dp))
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
        color = LedgerRaisedSurface,
        shape = RoundedCornerShape(22.dp),
        border = BorderStroke(1.dp, LedgerDivider.copy(alpha = 0.72f)),
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
    isEditing: Boolean,
    amountText: String,
    remark: String,
    dateLabel: String,
    accountLabel: String,
    onRemarkClick: () -> Unit,
    onDateClick: () -> Unit,
    onAccountClick: () -> Unit,
    onKeyClick: (String) -> Unit,
    onSaveContinue: () -> Unit,
    onDeleteClick: () -> Unit,
    onDone: () -> Unit,
    saveEnabled: Boolean,
) {
    val amountColor = when (type) {
        LedgerTransactionType.EXPENSE -> LedgerExpenseRed
        LedgerTransactionType.INCOME -> LedgerIncomeGreen
        LedgerTransactionType.TRANSFER -> LedgerHeaderGreen
    }
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = LedgerRaisedSurface,
        shadowElevation = 0.dp,
        shape = RoundedCornerShape(topStart = 0.dp, topEnd = 0.dp),
        border = BorderStroke(1.dp, LedgerDivider.copy(alpha = 0.72f)),
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
                        .clickable { onRemarkClick() },
                    color = if (remark.isBlank()) LedgerMuted else LedgerHeaderGreen,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
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
                    text = if (isEditing) "删除" else "保存再记",
                    modifier = Modifier.weight(1.25f),
                    textStyle = MaterialTheme.typography.bodyLarge,
                    danger = isEditing,
                    enabled = isEditing || saveEnabled,
                    onClick = {
                        if (isEditing) {
                            onDeleteClick()
                        } else if (saveEnabled) {
                            onSaveContinue()
                        }
                    },
                )
                LedgerKeyboardKey(text = "0", modifier = Modifier.weight(1f), onClick = { onKeyClick("0") })
                LedgerKeyboardKey(text = ".", modifier = Modifier.weight(1f), onClick = { onKeyClick(".") })
                LedgerKeyboardKey(text = "=", modifier = Modifier.weight(1f), textStyle = MaterialTheme.typography.titleMedium, onClick = { onKeyClick("=") })
                LedgerKeyboardKey(
                    text = "完成",
                    modifier = Modifier.weight(1.15f),
                    filled = true,
                    enabled = saveEnabled,
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
    danger: Boolean = false,
    enabled: Boolean = true,
    textStyle: androidx.compose.ui.text.TextStyle = MaterialTheme.typography.titleLarge,
    onClick: () -> Unit,
) {
    val background = when {
        !enabled -> if (filled) LedgerPrimaryAction.copy(alpha = 0.42f) else LedgerDivider.copy(alpha = 0.28f)
        filled -> LedgerPrimaryAction
        danger -> LedgerMemoryWash
        else -> LedgerRaisedSurface
    }
    val contentColor = when {
        !enabled -> LedgerMuted.copy(alpha = 0.62f)
        filled -> LedgerOnPrimaryAction
        danger -> LedgerExpenseRed
        else -> LedgerHeaderGreen
    }
    val animatedBackground by animateColorAsState(
        targetValue = background,
        animationSpec = tween(durationMillis = 150),
        label = "ledgerKeyboardBackground",
    )
    val animatedContentColor by animateColorAsState(
        targetValue = contentColor,
        animationSpec = tween(durationMillis = 150),
        label = "ledgerKeyboardContent",
    )
    Box(
        modifier = modifier
            .height(56.dp)
            .background(animatedBackground)
            .yingShiClickable(
                enabled = enabled,
                pressedScale = 0.985f,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (text == "⌫") {
            Icon(Icons.Default.Close, contentDescription = "删除", tint = animatedContentColor, modifier = Modifier.size(24.dp))
        } else {
            Text(
                text = text,
                style = textStyle,
                fontWeight = FontWeight.Bold,
                color = animatedContentColor,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun LedgerRemarkDialog(
    initialRemark: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var value by rememberSaveable(initialRemark) { mutableStateOf(initialRemark) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = LedgerRaisedSurface,
        title = {
            Text(
                text = "账单备注",
                color = LedgerHeaderGreen,
                fontWeight = FontWeight.Bold,
            )
        },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { value = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("备注") },
                placeholder = { Text("写下这笔账的来处") },
                singleLine = false,
                minLines = 3,
            )
        },
        confirmButton = {
            LedgerDialogActionButton(
                text = "保存",
                emphasized = true,
                onClick = { onConfirm(value.trim()) },
            )
        },
        dismissButton = {
            LedgerDialogActionButton(text = "取消", onClick = onDismiss)
        },
    )
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

package com.example.yingshi.feature.ledger

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.WindowManager

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
    val view = LocalView.current
    DisposableEffect(view) {
        val window = view.context.findActivity()?.window
        val originalSoftInputMode = window?.attributes?.softInputMode
        window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING)
        onDispose {
            if (window != null && originalSoftInputMode != null) {
                window.setSoftInputMode(originalSoftInputMode)
            }
        }
    }

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
    var isRemarkEditing by rememberSaveable { mutableStateOf(false) }
    var remarkKeyboardObservedVisible by rememberSaveable { mutableStateOf(false) }
    var showDeleteDialog by rememberSaveable { mutableStateOf(false) }
    val normalizedExpression = sanitizeAmountExpression(expression)
    val evaluatedExpression = LedgerCalculator.evaluate(normalizedExpression)
    val amountCents = evaluatedExpression?.toCentsOrNull() ?: normalizedExpression.toCentsOrNull() ?: 0L
    val amountDisplayText = expression.ifBlank { "0" }
    val hasPendingCalculation = expression.hasLedgerOperator()
    var lastBookId by rememberSaveable(initialTransaction?.id) { mutableStateOf(uiState.currentBookId) }
    val density = LocalDensity.current
    val imeBottom = WindowInsets.ime.getBottom(density)
    val currentImeBottom by rememberUpdatedState(imeBottom)
    val imeBottomDp = with(density) { imeBottom.toDp() }

    LaunchedEffect(isRemarkEditing) {
        if (!isRemarkEditing) {
            remarkKeyboardObservedVisible = false
        }
    }
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
    LaunchedEffect(isRemarkEditing, imeBottom) {
        if (!isRemarkEditing) {
            return@LaunchedEffect
        }
        if (imeBottom > 0) {
            remarkKeyboardObservedVisible = true
            return@LaunchedEffect
        }
        if (!remarkKeyboardObservedVisible) {
            return@LaunchedEffect
        }
        kotlinx.coroutines.delay(120)
        if (isRemarkEditing && currentImeBottom == 0) {
            remarkKeyboardObservedVisible = false
            isRemarkEditing = false
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
                creatorUserId = uiState.bookCreatorUserId,
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
                    contentPadding = PaddingValues(
                        start = 16.dp,
                        end = 16.dp,
                        top = 4.dp,
                        bottom = 286.dp,
                    ),
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

            val remarkAmountColor = when (type) {
                LedgerTransactionType.EXPENSE -> LedgerExpenseRed
                LedgerTransactionType.INCOME -> LedgerIncomeGreen
                LedgerTransactionType.TRANSFER -> LedgerHeaderGreen
            }

            LedgerAmountKeyboardPanel(
                modifier = Modifier
                    .align(Alignment.BottomCenter),
                type = type,
                isEditing = initialTransaction != null,
                amountText = amountDisplayText,
                hasPendingCalculation = hasPendingCalculation,
                remark = remark,
                dateLabel = formatLedgerPickerDate(occurredAtMillis),
                accountLabel = accountOptions.firstOrNull { it.id == selectedAccountId }?.name ?: "请选择账户",
                onRemarkClick = { isRemarkEditing = true },
                onDateClick = { showDateSheet = true },
                onAccountClick = {
                    accountPickerTarget = LedgerAccountPickerTarget.PRIMARY.name
                    showAccountSheet = true
                },
                onKeyClick = { key ->
                    if (isRemarkEditing) isRemarkEditing = false
                    expression = when (key) {
                        "⌫" -> expression.dropLast(1).ifBlank { "0" }
                        "=" -> LedgerCalculator.evaluate(sanitizeAmountExpression(expression)) ?: expression
                        else -> appendKeyboardInput(expression, key)
                    }
                },
                onSaveContinue = {
                    if (hasPendingCalculation) {
                        expression = LedgerCalculator.evaluate(sanitizeAmountExpression(expression)) ?: expression
                        isRemarkEditing = false
                        return@LedgerAmountKeyboardPanel
                    }
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
                    isRemarkEditing = false
                },
                onDeleteClick = {
                    showDeleteDialog = true
                },
                onDone = {
                    if (hasPendingCalculation) {
                        expression = LedgerCalculator.evaluate(sanitizeAmountExpression(expression)) ?: expression
                    } else {
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
                    }
                    isRemarkEditing = false
                },
                saveEnabled = isDraftValid(type, amountCents, selectedCategoryId, selectedAccountId, selectedToAccountId),
            )

            if (isRemarkEditing) {
                LedgerFloatingRemarkBar(
                    remark = remark,
                    amountText = amountDisplayText,
                    amountColor = remarkAmountColor,
                    imeBottom = imeBottomDp,
                    onRemarkChange = { remark = it },
                    onDismiss = { isRemarkEditing = false },
                    modifier = Modifier.align(Alignment.BottomCenter),
                )
            }

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
    creatorUserId: String?,
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
            LedgerBookTitleWithCreator(
                title = bookName,
                creatorUserId = creatorUserId,
                textStyle = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                textColor = LedgerHeaderGreen,
                avatarSize = 18.dp,
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
    hasPendingCalculation: Boolean,
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
        modifier = modifier
            .fillMaxWidth(),
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
                Spacer(Modifier.width(10.dp))
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
                    Icon(
                        accountIcon(com.example.yingshi.feature.ledger.data.LedgerAccountType.WECHAT),
                        contentDescription = null,
                        tint = LedgerHeaderGreen,
                        modifier = Modifier.size(18.dp),
                    )
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
                LedgerKeyboardKey(text = "×", modifier = Modifier.weight(1f), onClick = { onKeyClick("×") })
                LedgerKeyboardKey(
                    text = if (hasPendingCalculation) "=" else "完成",
                    modifier = Modifier.weight(1.15f),
                    filled = true,
                    enabled = hasPendingCalculation || saveEnabled,
                    onClick = { if (hasPendingCalculation || saveEnabled) onDone() },
                )
            }
        }
    }
}

@Composable
private fun LedgerFloatingRemarkBar(
    remark: String,
    amountText: String,
    amountColor: Color,
    imeBottom: androidx.compose.ui.unit.Dp,
    onRemarkChange: (String) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    var remarkFieldValue by remember {
        mutableStateOf(TextFieldValue(remark, TextRange(remark.length)))
    }

    LaunchedEffect(remark) {
        if (remarkFieldValue.text != remark) {
            remarkFieldValue = TextFieldValue(remark, TextRange(remark.length))
        }
    }
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(110)
        focusRequester.requestFocus()
        keyboardController?.show()
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp)
            .padding(bottom = imeBottom + 8.dp),
        shape = RoundedCornerShape(20.dp),
        color = LedgerRaisedSurface.copy(alpha = 0.98f),
        border = BorderStroke(1.dp, LedgerGlassStroke.copy(alpha = 0.84f)),
        shadowElevation = 2.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BasicTextField(
                value = remarkFieldValue,
                onValueChange = { nextValue ->
                    remarkFieldValue = nextValue
                    onRemarkChange(nextValue.text)
                },
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = LedgerHeaderGreen),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(
                    onDone = {
                        keyboardController?.hide()
                        onDismiss()
                    },
                ),
                modifier = Modifier
                    .weight(1f)
                    .focusRequester(focusRequester)
                    .onFocusChanged { focusState ->
                        if (focusState.isFocused) {
                            val end = remarkFieldValue.text.length
                            if (remarkFieldValue.selection.start != end || remarkFieldValue.selection.end != end) {
                                remarkFieldValue = remarkFieldValue.copy(selection = TextRange(end))
                            }
                        }
                    },
                decorationBox = { innerTextField ->
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        if (remarkFieldValue.text.isBlank()) {
                            Text(
                                text = "添加备注",
                                style = MaterialTheme.typography.bodyLarge,
                                color = LedgerMuted,
                            )
                        }
                        innerTextField()
                    }
                },
            )
            Spacer(Modifier.width(12.dp))
            Text(
                text = amountText.ifBlank { "0.00" },
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = amountColor,
                maxLines = 1,
            )
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
    val isDeleteKey = text == "⌫"
    val background = when {
        !enabled -> if (filled) LedgerPrimaryAction.copy(alpha = 0.42f) else LedgerDivider.copy(alpha = 0.28f)
        isDeleteKey -> Color(0xFF273036)
        filled -> LedgerPrimaryAction
        danger -> LedgerMemoryWash
        else -> LedgerRaisedSurface
    }
    val contentColor = when {
        !enabled -> LedgerMuted.copy(alpha = 0.62f)
        isDeleteKey -> Color.White
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
        if (isDeleteKey) {
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

private fun appendKeyboardInput(expression: String, key: String): String {
    val operators = listOf("+", "-", "×", "÷")
    val segment = expression.substringAfterLastAny(operators)
    if (key in operators) {
        val base = expression.trimEnd('.')
        if (base.isBlank()) return "0"
        return if (operators.any { base.endsWith(it) }) {
            base.dropLast(1) + key
        } else {
            val resolvedBase = if (base.hasLedgerOperator()) {
                LedgerCalculator.evaluate(sanitizeAmountExpression(base)) ?: base
            } else {
                base
            }
            resolvedBase + key
        }
    }
    if (key == ".") {
        if (segment.contains(".")) return expression
        return if (segment.isBlank()) expression + "0." else expression + "."
    }
    if (key.length == 1 && key.first().isDigit()) {
        val decimals = segment.substringAfter('.', missingDelimiterValue = "")
        if (segment.contains(".") && decimals.length >= 2) return expression
        val base = if (expression == "0") "" else expression
        return base + key
    }
    return expression
}

private fun String.substringAfterLastAny(delimiters: List<String>): String {
    val index = delimiters.maxOf { lastIndexOf(it) }
    return if (index >= 0) substring(index + 1) else this
}

private fun String.hasLedgerOperator(): Boolean {
    return any { it == '+' || it == '-' || it == '×' || it == '÷' }
}

private fun sanitizeAmountExpression(expression: String): String {
    val operators = setOf('+', '-', '×', '÷')
    return expression
        .trim()
        .trimEnd('.')
        .trimEnd { it in operators }
        .ifBlank { "0" }
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

private tailrec fun Context.findActivity(): Activity? {
    return when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }
}

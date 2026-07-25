package com.example.yingshi.feature.ledger

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Timelapse
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.yingshi.ui.components.yingShiClickable
import com.example.yingshi.feature.ledger.data.LedgerAccount
import com.example.yingshi.feature.ledger.data.LedgerCategory
import com.example.yingshi.feature.ledger.data.LedgerCategoryType
import com.example.yingshi.feature.ledger.data.LedgerRecurringFrequency
import com.example.yingshi.feature.ledger.data.LedgerRecurringRule
import com.example.yingshi.feature.ledger.data.LedgerRecurringRuleDraft
import com.example.yingshi.feature.ledger.data.LedgerTransactionType
import com.example.yingshi.feature.ledger.data.recurringFrequencyLabel

private enum class LedgerRecurringEditorEndMode {
    LONG_TERM,
    SPECIFIC,
}

private enum class LedgerRecurringEditorAccountTarget {
    FROM,
    TO,
}

@Composable
fun LedgerRecurringScreen(
    uiState: LedgerUiState,
    onBack: () -> Unit,
    onSelectBook: (String) -> Unit,
    onOpenBooks: () -> Unit,
    onSaveRule: (LedgerRecurringRuleDraft, () -> Unit) -> Unit,
    onToggleRuleEnabled: (String, Boolean) -> Unit,
    onDeleteRule: (String) -> Unit,
    onRefresh: () -> Unit,
) {
    var showBookSheet by rememberSaveable { mutableStateOf(false) }
    var showEditor by rememberSaveable { mutableStateOf(false) }
    var editingRule by remember { mutableStateOf<LedgerRecurringRule?>(null) }
    var actionRule by remember { mutableStateOf<LedgerRecurringRule?>(null) }
    var pendingDeleteRule by remember { mutableStateOf<LedgerRecurringRule?>(null) }

    LaunchedEffect(uiState.currentBookId) {
        onRefresh()
    }

    val activeRules = remember(uiState.recurringRules) {
        uiState.recurringRules.filter { it.enabled }
    }
    val pausedRules = remember(uiState.recurringRules) {
        uiState.recurringRules.filterNot { it.enabled }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(LedgerPageBackground),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack, modifier = Modifier.size(44.dp)) {
                Icon(LedgerActionIcons.Back, contentDescription = "返回", modifier = Modifier.size(20.dp))
            }
            Row(
                modifier = Modifier
                    .weight(1f)
                    .yingShiClickable(
                        pressedScale = 0.96f,
                        shape = RoundedCornerShape(18.dp),
                        onClick = { showBookSheet = true },
                    ),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                LedgerBookTitleWithCreator(
                    title = uiState.bookName,
                    creatorUserId = uiState.bookCreatorUserId,
                    textStyle = MaterialTheme.typography.titleLarge,
                    textColor = Color.Unspecified,
                    fontWeight = FontWeight.Bold,
                    avatarSize = 16.dp,
                )
                Icon(Icons.Default.ArrowDropDown, contentDescription = "切换账本", modifier = Modifier.size(14.dp))
            }
            IconButton(onClick = {
                editingRule = null
                showEditor = true
            }, modifier = Modifier.size(44.dp)) {
                Icon(Icons.Default.Add, contentDescription = "新增规则", modifier = Modifier.size(18.dp))
            }
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .navigationBarsPadding(),
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                LedgerRecurringSectionCard(
                    title = "进行中",
                    count = activeRules.size,
                    emptyText = "暂无进行中的周期规则",
                ) {
                    activeRules.forEach { rule ->
                        LedgerRecurringRuleRow(
                            rule = rule,
                            onClick = { actionRule = rule },
                        )
                    }
                }
            }
            item {
                LedgerRecurringSectionCard(
                    title = "已暂停",
                    count = pausedRules.size,
                    emptyText = "暂无已暂停的周期规则",
                ) {
                    pausedRules.forEach { rule ->
                        LedgerRecurringRuleRow(
                            rule = rule,
                            onClick = { actionRule = rule },
                        )
                    }
                }
            }
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
            onManageBooks = onOpenBooks,
        )
    }
    actionRule?.let { rule ->
        LedgerActionSheet(
            title = recurringRuleTitle(rule),
            onDismiss = { actionRule = null },
            actions = buildList {
                add(
                    LedgerSheetAction("编辑规则") {
                        editingRule = rule
                        showEditor = true
                    },
                )
                add(
                    LedgerSheetAction(if (rule.enabled) "暂停规则" else "恢复规则") {
                        onToggleRuleEnabled(rule.id, !rule.enabled)
                    },
                )
                add(
                    LedgerSheetAction("删除规则", destructive = true) {
                        pendingDeleteRule = rule
                    },
                )
            },
        )
    }
    pendingDeleteRule?.let { rule ->
        AlertDialog(
            onDismissRequest = { pendingDeleteRule = null },
            title = { Text("确认删除") },
            text = { Text("确定要删除周期规则「${recurringRuleTitle(rule)}」吗？删除后无法恢复。") },
            confirmButton = {
                LedgerDialogActionButton(
                    text = "删除",
                    danger = true,
                    onClick = {
                        onDeleteRule(rule.id)
                        pendingDeleteRule = null
                    },
                )
            },
            dismissButton = {
                LedgerDialogActionButton(
                    text = "取消",
                    onClick = { pendingDeleteRule = null },
                )
            },
        )
    }
    if (showEditor) {
        LedgerRecurringRuleEditorSheet(
            uiState = uiState,
            initial = editingRule,
            onDismiss = {
                showEditor = false
                editingRule = null
            },
            onSave = { draft ->
                onSaveRule(draft) {
                    showEditor = false
                    editingRule = null
                }
            },
        )
    }
}

@Composable
private fun LedgerRecurringSectionCard(
    title: String,
    count: Int,
    emptyText: String,
    content: @Composable () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = LedgerRaisedSurface,
        shape = RoundedCornerShape(24.dp),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(8.dp))
                Text("($count)", color = LedgerMuted, style = MaterialTheme.typography.bodySmall)
            }
            if (count == 0) {
                Text(emptyText, color = LedgerMuted, style = MaterialTheme.typography.bodySmall)
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    content()
                }
            }
        }
    }
}

@Composable
private fun LedgerRecurringRuleRow(
    rule: LedgerRecurringRule,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .yingShiClickable(
                pressedScale = 0.96f,
                shape = RoundedCornerShape(18.dp),
                onClick = onClick,
            ),
        color = LedgerGroupedHeader,
        shape = RoundedCornerShape(18.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(ledgerColor(rule.category?.color ?: 0xFF8D99A6)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    ledgerIcon(rule.category?.iconKey ?: "timelapse"),
                    contentDescription = null,
                    tint = LedgerRaisedSurface,
                    modifier = Modifier.size(18.dp),
                )
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = recurringRuleTitle(rule),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = recurringRuleSubtitle(rule),
                    color = LedgerMuted,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = recurringRuleAmount(rule),
                    color = when (rule.type) {
                        LedgerTransactionType.EXPENSE -> LedgerExpenseRed
                        LedgerTransactionType.INCOME -> LedgerHeaderGreen
                        LedgerTransactionType.TRANSFER -> LedgerHeaderGreen
                    },
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = recurringFrequencyLabel(rule.frequency),
                    color = LedgerMuted,
                    style = MaterialTheme.typography.bodySmall,
                )
                Text(
                    text = "下次 ${formatDateTime(rule.nextOccurrenceAtMillis)}",
                    color = LedgerMuted,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun LedgerRecurringRuleEditorSheet(
    uiState: LedgerUiState,
    initial: LedgerRecurringRule? = null,
    onDismiss: () -> Unit,
    onSave: (LedgerRecurringRuleDraft) -> Unit,
) {
    val defaultPrimaryAccountId = uiState.defaultAccountIdForCurrentBook
        ?.takeIf { id -> uiState.accounts.any { it.id == id } }
        ?: uiState.accounts.firstOrNull()?.id
    val defaultSecondaryAccountId = uiState.accounts.firstOrNull { it.id != defaultPrimaryAccountId }?.id

    fun categoryOptions(type: LedgerTransactionType, selectedCategoryId: String?): List<LedgerCategory> {
        val visible = uiState.categories.filter {
            it.type == if (type == LedgerTransactionType.INCOME) LedgerCategoryType.INCOME else LedgerCategoryType.EXPENSE
        }
        val selected = selectedCategoryId?.let { id ->
            uiState.allCategories.firstOrNull { it.id == id }
        }
        return (visible + listOfNotNull(selected)).distinctBy { it.id }
    }

    fun accountOptions(selectedIds: Set<String>): List<LedgerAccount> {
        val selected = selectedIds.mapNotNull { id -> uiState.allAccounts.firstOrNull { it.id == id } }
        return (uiState.accounts + selected).distinctBy { it.id }
    }

    var selectedType by rememberSaveable(initial?.id, uiState.currentBookId) {
        mutableStateOf(initial?.type ?: LedgerTransactionType.EXPENSE)
    }
    var selectedCategoryId by rememberSaveable(initial?.id, uiState.currentBookId) {
        mutableStateOf(
            initial?.category?.id ?: uiState.categories.firstOrNull {
                it.type == if (selectedType == LedgerTransactionType.INCOME) LedgerCategoryType.INCOME else LedgerCategoryType.EXPENSE
            }?.id,
        )
    }
    var selectedAccountId by rememberSaveable(initial?.id, uiState.currentBookId) {
        mutableStateOf(initial?.account?.id ?: defaultPrimaryAccountId)
    }
    var selectedToAccountId by rememberSaveable(initial?.id, uiState.currentBookId) {
        mutableStateOf(initial?.toAccount?.id ?: defaultSecondaryAccountId)
    }
    var amountText by rememberSaveable(initial?.id, uiState.currentBookId) {
        mutableStateOf(initial?.amountCents?.let(::formatAmountValue)?.replace(",", "") ?: "0")
    }
    var remark by rememberSaveable(initial?.id, uiState.currentBookId) {
        mutableStateOf(initial?.remark.orEmpty())
    }
    var selectedFrequency by rememberSaveable(initial?.id, uiState.currentBookId) {
        mutableStateOf(initial?.frequency ?: LedgerRecurringFrequency.MONTHLY)
    }
    var startAtMillis by rememberSaveable(initial?.id, uiState.currentBookId) {
        mutableStateOf(initial?.startAtMillis ?: System.currentTimeMillis())
    }
    var endMode by rememberSaveable(initial?.id, uiState.currentBookId) {
        mutableStateOf(if (initial?.endAtMillis == null) LedgerRecurringEditorEndMode.LONG_TERM else LedgerRecurringEditorEndMode.SPECIFIC)
    }
    var endAtMillis by rememberSaveable(initial?.id, uiState.currentBookId) {
        mutableStateOf(initial?.endAtMillis ?: startAtMillis)
    }
    var showCategorySheet by rememberSaveable { mutableStateOf(false) }
    var showAccountSheet by rememberSaveable { mutableStateOf(false) }
    var accountTarget by rememberSaveable(initial?.id, uiState.currentBookId) {
        mutableStateOf(LedgerRecurringEditorAccountTarget.FROM.name)
    }
    var showStartPicker by rememberSaveable { mutableStateOf(false) }
    var showEndPicker by rememberSaveable { mutableStateOf(false) }

    val currentCategoryOptions = remember(selectedType, selectedCategoryId, uiState.categories, uiState.allCategories) {
        categoryOptions(selectedType, selectedCategoryId)
    }
    val currentAccountOptions = remember(selectedAccountId, selectedToAccountId, uiState.accounts, uiState.allAccounts) {
        accountOptions(setOfNotNull(selectedAccountId, selectedToAccountId))
    }
    val amountCents = amountText.toCentsOrNull() ?: 0L
    val previewDraft = LedgerRecurringRuleDraft(
        id = initial?.id,
        bookId = uiState.currentBookId,
        type = selectedType,
        categoryId = if (selectedType == LedgerTransactionType.TRANSFER) null else selectedCategoryId,
        accountId = selectedAccountId.orEmpty(),
        toAccountId = if (selectedType == LedgerTransactionType.TRANSFER) selectedToAccountId else null,
        amountCents = amountCents,
        remark = remark,
        frequency = selectedFrequency,
        startAtMillis = startAtMillis,
        endAtMillis = if (endMode == LedgerRecurringEditorEndMode.SPECIFIC) endAtMillis else null,
        enabled = initial?.enabled ?: true,
    )
    val canSave = validateRecurringDraftLocal(previewDraft) == null

    LaunchedEffect(selectedType, currentCategoryOptions, currentAccountOptions) {
        if (selectedType == LedgerTransactionType.TRANSFER) {
            selectedCategoryId = null
            val accountIds = currentAccountOptions.map { it.id }
            if (selectedAccountId !in accountIds) {
                selectedAccountId = defaultPrimaryAccountId
            }
            if (selectedToAccountId !in accountIds || selectedToAccountId == selectedAccountId) {
                selectedToAccountId = currentAccountOptions.firstOrNull { it.id != selectedAccountId }?.id
            }
        } else {
            if (selectedCategoryId !in currentCategoryOptions.map { it.id }) {
                selectedCategoryId = currentCategoryOptions.firstOrNull()?.id
            }
            if (selectedAccountId !in currentAccountOptions.map { it.id }) {
                selectedAccountId = defaultPrimaryAccountId
            }
        }
    }

    LedgerBottomSheetDialog(
        onDismiss = onDismiss,
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight(0.94f),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                LedgerDialogActionButton(text = "取消", onClick = onDismiss)
                Text(
                    text = if (initial == null) "新增周期规则" else "编辑周期规则",
                    modifier = Modifier.weight(1f),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                LedgerDialogActionButton(
                    text = "保存",
                    onClick = {
                        onSave(
                            previewDraft,
                        )
                    },
                    enabled = canSave,
                    emphasized = true,
                )
            }
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentPadding = PaddingValues(bottom = 10.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    LedgerRecurringSegmentRow(
                        title = "类型",
                        chips = listOf(
                            LedgerTransactionType.EXPENSE to "支出",
                            LedgerTransactionType.INCOME to "收入",
                            LedgerTransactionType.TRANSFER to "转账",
                        ),
                        selected = selectedType,
                        onSelect = { selectedType = it },
                    )
                }
                if (selectedType != LedgerTransactionType.TRANSFER) {
                    item {
                        LedgerRecurringPickerRow(
                            title = "分类",
                            value = currentCategoryOptions.firstOrNull { it.id == selectedCategoryId }?.name ?: "请选择分类",
                            onClick = { showCategorySheet = true },
                        )
                    }
                    item {
                        LedgerRecurringPickerRow(
                            title = "账户",
                            value = currentAccountOptions.firstOrNull { it.id == selectedAccountId }?.name ?: "请选择账户",
                            onClick = { accountTarget = LedgerRecurringEditorAccountTarget.FROM.name; showAccountSheet = true },
                        )
                    }
                } else {
                    item {
                        LedgerRecurringPickerRow(
                            title = "转出账户",
                            value = currentAccountOptions.firstOrNull { it.id == selectedAccountId }?.name ?: "请选择账户",
                            onClick = { accountTarget = LedgerRecurringEditorAccountTarget.FROM.name; showAccountSheet = true },
                        )
                    }
                    item {
                        LedgerRecurringPickerRow(
                            title = "转入账户",
                            value = currentAccountOptions.firstOrNull { it.id == selectedToAccountId }?.name ?: "请选择账户",
                            onClick = { accountTarget = LedgerRecurringEditorAccountTarget.TO.name; showAccountSheet = true },
                        )
                    }
                }
                item {
                    OutlinedTextField(
                        value = amountText,
                        onValueChange = { amountText = it.filter { char -> char.isDigit() || char == '.' || char == '-' } },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("金额") },
                        singleLine = true,
                    )
                }
                item {
                    OutlinedTextField(
                        value = remark,
                        onValueChange = { remark = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("备注") },
                        maxLines = 2,
                    )
                }
                item {
                    LedgerRecurringPickerRow(
                        title = "首次执行时间",
                        value = formatDateTime(startAtMillis),
                        onClick = { showStartPicker = true },
                    )
                }
                item {
                    LedgerRecurringSegmentRow(
                        title = "周期",
                        chips = listOf(
                            LedgerRecurringFrequency.DAILY to "每天",
                            LedgerRecurringFrequency.WEEKLY to "每周",
                            LedgerRecurringFrequency.MONTHLY to "每月",
                            LedgerRecurringFrequency.YEARLY to "每年",
                        ),
                        selected = selectedFrequency,
                        onSelect = { selectedFrequency = it },
                    )
                }
                item {
                    LedgerRecurringSegmentRow(
                        title = "结束方式",
                        chips = listOf(
                            LedgerRecurringEditorEndMode.LONG_TERM to "长期有效",
                            LedgerRecurringEditorEndMode.SPECIFIC to "指定结束",
                        ),
                        selected = endMode,
                        onSelect = { endMode = it },
                    )
                }
                if (endMode == LedgerRecurringEditorEndMode.SPECIFIC) {
                    item {
                        LedgerRecurringPickerRow(
                            title = "结束时间",
                            value = formatDateTime(endAtMillis),
                            onClick = { showEndPicker = true },
                        )
                    }
                }
            }
        }
    }

    if (showCategorySheet) {
        LedgerCategoryPickerSheet(
            title = if (selectedType == LedgerTransactionType.INCOME) "选择收入分类" else "选择支出分类",
            categories = currentCategoryOptions,
            selectedCategoryId = selectedCategoryId,
            onDismiss = { showCategorySheet = false },
            onSelect = {
                selectedCategoryId = it
                showCategorySheet = false
            },
        )
    }
    if (showAccountSheet) {
        LedgerAccountPickerSheet(
            accounts = currentAccountOptions,
            selectedAccountId = when (LedgerRecurringEditorAccountTarget.valueOf(accountTarget)) {
                LedgerRecurringEditorAccountTarget.FROM -> selectedAccountId
                LedgerRecurringEditorAccountTarget.TO -> selectedToAccountId
            },
            title = if (LedgerRecurringEditorAccountTarget.valueOf(accountTarget) == LedgerRecurringEditorAccountTarget.FROM) "选择账户" else "选择转入账户",
            onDismiss = { showAccountSheet = false },
            onSelect = { accountId ->
                when (LedgerRecurringEditorAccountTarget.valueOf(accountTarget)) {
                    LedgerRecurringEditorAccountTarget.FROM -> selectedAccountId = accountId
                    LedgerRecurringEditorAccountTarget.TO -> selectedToAccountId = accountId
                }
                showAccountSheet = false
            },
        )
    }
    if (showStartPicker) {
        LedgerDateTimePickerSheet(
            selectedTimeMillis = startAtMillis,
            onDismiss = { showStartPicker = false },
            onConfirm = {
                startAtMillis = it
                if (endMode == LedgerRecurringEditorEndMode.SPECIFIC && endAtMillis < it) {
                    endAtMillis = it
                }
                showStartPicker = false
            },
        )
    }
    if (showEndPicker) {
        LedgerDateTimePickerSheet(
            selectedTimeMillis = endAtMillis,
            onDismiss = { showEndPicker = false },
            onConfirm = {
                endAtMillis = it
                showEndPicker = false
            },
        )
    }
}

@Composable
private fun <T> LedgerRecurringSegmentRow(
    title: String,
    chips: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            chips.forEach { (value, text) ->
                LedgerSegmentChip(
                    text = text,
                    selected = value == selected,
                    modifier = Modifier.weight(1f),
                    onClick = { onSelect(value) },
                )
            }
        }
    }
}

@Composable
private fun LedgerRecurringPickerRow(
    title: String,
    value: String,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .yingShiClickable(
                pressedScale = 0.96f,
                shape = RoundedCornerShape(18.dp),
                onClick = onClick,
            ),
        shape = RoundedCornerShape(18.dp),
        color = LedgerGroupedHeader,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(title, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(12.dp))
            Text(
                text = value,
                modifier = Modifier.weight(1f),
                color = LedgerMuted,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = LedgerMuted, modifier = Modifier.size(16.dp))
        }
    }
}

private fun recurringRuleTitle(rule: LedgerRecurringRule): String = when (rule.type) {
    LedgerTransactionType.TRANSFER -> "账户转账"
    else -> rule.category?.name ?: "未分类"
}

private fun recurringRuleSubtitle(rule: LedgerRecurringRule): String = when (rule.type) {
    LedgerTransactionType.TRANSFER -> "${rule.account?.name.orEmpty()} → ${rule.toAccount?.name.orEmpty()}${rule.remark.takeIf { it.isNotBlank() }?.let { " · $it" }.orEmpty()}"
    else -> "${rule.account?.name.orEmpty()}${rule.remark.takeIf { it.isNotBlank() }?.let { " · $it" }.orEmpty()}"
}

private fun recurringRuleAmount(rule: LedgerRecurringRule): String = when (rule.type) {
    LedgerTransactionType.EXPENSE -> "-${formatAmountValue(rule.amountCents)}"
    LedgerTransactionType.INCOME -> formatAmountValue(rule.amountCents)
    LedgerTransactionType.TRANSFER -> formatAmountValue(rule.amountCents)
}

private fun validateRecurringDraftLocal(draft: LedgerRecurringRuleDraft): String? {
    if (draft.amountCents <= 0) return "请输入大于 0 的金额"
    if (draft.accountId.isBlank()) return "请选择账户"
    if (draft.type != LedgerTransactionType.TRANSFER && draft.categoryId.isNullOrBlank()) return "请选择分类"
    if (draft.type == LedgerTransactionType.TRANSFER) {
        if (draft.toAccountId.isNullOrBlank()) return "请选择转入账户"
        if (draft.accountId == draft.toAccountId) return "转出和转入账户不能相同"
    }
    if (draft.endAtMillis != null && draft.endAtMillis < draft.startAtMillis) return "结束时间不能早于首次执行时间"
    return null
}

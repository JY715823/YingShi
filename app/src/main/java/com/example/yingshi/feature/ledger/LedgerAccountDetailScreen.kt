package com.example.yingshi.feature.ledger

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.yingshi.feature.ledger.data.LedgerAccount
import com.example.yingshi.feature.ledger.data.LedgerAccountDraft
import com.example.yingshi.feature.ledger.data.LedgerTransaction
import com.example.yingshi.feature.ledger.data.LedgerTransactionType
import com.example.yingshi.feature.ledger.data.belongsToLedgerAccount
import com.example.yingshi.ui.components.yingShiClickable
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId

@Composable
fun LedgerAccountDetailScreen(
    account: LedgerAccount,
    allTransactions: List<LedgerTransaction>,
    allAccounts: List<LedgerAccount>,
    currencySymbol: String,
    onBack: () -> Unit,
    onEditAccount: () -> Unit,
    onDeleteAccount: () -> Unit,
    onAddTransaction: () -> Unit,
    onTransfer: (fromAccountId: String) -> Unit,
    onSaveAccount: (LedgerAccountDraft) -> Unit,
    onEditTransaction: (LedgerTransaction) -> Unit,
) {
    var showMenuSheet by rememberSaveable { mutableStateOf(false) }
    var editingAccount by remember { mutableStateOf(false) }
    var showDeleteDialog by rememberSaveable { mutableStateOf(false) }
    val currentMonth = remember { YearMonth.now(ZoneId.systemDefault()) }
    // 记录哪些月份是展开的（默认展开当前月）
    var expandedMonths by rememberSaveable { mutableStateOf(setOf(currentMonth.toString())) }

    val accountTransactions = remember(allTransactions, account.id) {
        allTransactions.filter { it.belongsToLedgerAccount(account.id) }
    }

    // 按月份分组
    val monthGroups = remember(accountTransactions) {
        accountTransactions
            .groupBy { txn ->
                val zoned = Instant.ofEpochMilli(txn.occurredAtMillis)
                    .atZone(ZoneId.systemDefault())
                YearMonth.of(zoned.year, zoned.monthValue)
            }
            .toList()
            .sortedByDescending { it.first }
    }

    // 全量统计
    val totalInflow = remember(accountTransactions) {
        accountTransactions
            .filter { it.type == LedgerTransactionType.INCOME }
            .sumOf { it.amountCents }
    }
    val totalOutflow = remember(accountTransactions) {
        accountTransactions
            .filter { it.type == LedgerTransactionType.EXPENSE }
            .sumOf { it.amountCents }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(LedgerPageBackground)
            .statusBarsPadding(),
    ) {
        // Top bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack, modifier = Modifier.size(44.dp)) {
                Icon(
                    LedgerActionIcons.Back,
                    contentDescription = "返回",
                    tint = LedgerHeaderGreen,
                    modifier = Modifier.size(20.dp),
                )
            }
            Text(
                text = account.name,
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = LedgerHeaderGreen,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            IconButton(
                onClick = { showMenuSheet = true },
                modifier = Modifier.size(44.dp),
            ) {
                Icon(
                    Icons.Default.MoreHoriz,
                    contentDescription = "更多",
                    tint = LedgerHeaderGreen,
                    modifier = Modifier.size(20.dp),
                )
            }
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentPadding = PaddingValues(
                horizontal = 16.dp,
                vertical = 12.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Balance card (深色)
            item(key = "balance_card") {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = LedgerHeaderGreen,
                    shape = RoundedCornerShape(24.dp),
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            text = "账户余额(元)",
                            color = LedgerRaisedSurface.copy(alpha = 0.82f),
                            style = MaterialTheme.typography.labelMedium,
                        )
                        Text(
                            text = formatAmountValue(account.balanceCents),
                            color = LedgerRaisedSurface,
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(24.dp),
                        ) {
                            Column {
                                Text(
                                    "流入: ${formatAmountValue(totalInflow)}",
                                    color = LedgerRaisedSurface.copy(alpha = 0.78f),
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                            Column {
                                Text(
                                    "流出: ${formatAmountValue(totalOutflow)}",
                                    color = LedgerRaisedSurface.copy(alpha = 0.78f),
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        }
                    }
                }
            }

            // 按月分组的可折叠区块
            if (monthGroups.isEmpty()) {
                item(key = "empty") {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = LedgerRaisedSurface,
                        shape = RoundedCornerShape(18.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 32.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                "暂无交易记录",
                                color = LedgerMuted,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
            } else {
                monthGroups.forEach { (yearMonth, monthTxns) ->
                    val monthKey = yearMonth.toString()
                    val isExpanded = monthKey in expandedMonths
                    val monthInflow = monthTxns
                        .filter { it.type == LedgerTransactionType.INCOME }
                        .sumOf { it.amountCents }
                    val monthOutflow = monthTxns
                        .filter { it.type == LedgerTransactionType.EXPENSE }
                        .sumOf { it.amountCents }
                    val monthBalance = monthInflow - monthOutflow

                    // 月份摘要行（可点击折叠/展开）
                    item(key = "month_$monthKey") {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .yingShiClickable(
                                    pressedScale = 0.98f,
                                    shape = RoundedCornerShape(18.dp),
                                    onClick = {
                                        expandedMonths = if (isExpanded) {
                                            expandedMonths - monthKey
                                        } else {
                                            expandedMonths + monthKey
                                        }
                                    },
                                ),
                            color = LedgerRaisedSurface,
                            shape = RoundedCornerShape(18.dp),
                        ) {
                            Column(
                                modifier = Modifier.padding(
                                    horizontal = 14.dp,
                                    vertical = 12.dp,
                                ),
                            ) {
                                // 第一行：月份 + 展开指示器
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        "${yearMonth.year}年 ${"%02d".format(yearMonth.monthValue)} 月",
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.Bold,
                                    )
                                    Spacer(Modifier.weight(1f))
                                    Icon(
                                        if (isExpanded) Icons.Default.KeyboardArrowUp
                                        else Icons.Default.ArrowDropDown,
                                        contentDescription = if (isExpanded) "收起" else "展开",
                                        tint = LedgerMuted,
                                        modifier = Modifier.size(20.dp),
                                    )
                                }
                                Spacer(Modifier.height(4.dp))
                                // 第二行：流入 + 流出 + 结余
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                ) {
                                    Text(
                                        "流入 ${formatAmountValue(monthInflow)}",
                                        color = LedgerIncomeGreen,
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                    Text(
                                        "流出 ${formatAmountValue(monthOutflow)}",
                                        color = LedgerExpenseRed,
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                    Spacer(Modifier.weight(1f))
                                    Text(
                                        "结余 ${formatAmountValue(monthBalance)}",
                                        color = LedgerHeaderGreen,
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Bold,
                                    )
                                }
                            }
                        }
                    }

                    // 展开时显示按日分组的交易
                    if (isExpanded) {
                        val dayGroups = monthTxns
                            .groupBy { dayStart(it.occurredAtMillis) }
                            .toList()
                            .sortedByDescending { it.first }

                        dayGroups.forEach { (dayStartMillis, dayTxns) ->
                            val dayIncome = dayTxns
                                .filter { it.type == LedgerTransactionType.INCOME }
                                .sumOf { it.amountCents }
                            val dayExpense = dayTxns
                                .filter { it.type == LedgerTransactionType.EXPENSE }
                                .sumOf { it.amountCents }

                            item(key = "day_${monthKey}_$dayStartMillis") {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(LedgerGroupedHeader)
                                        .padding(horizontal = 14.dp, vertical = 10.dp),
                                ) {
                                    Text(
                                        formatLedgerGroupDate(dayStartMillis),
                                        modifier = Modifier.weight(1f),
                                        color = LedgerSubtleText,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                    Text(
                                        "收 ${formatAmountValue(dayIncome)}",
                                        color = LedgerIncomeGreen.copy(alpha = 0.8f),
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                    Spacer(Modifier.width(12.dp))
                                    Text(
                                        "支 ${formatAmountValue(dayExpense)}",
                                        color = LedgerExpenseRed.copy(alpha = 0.8f),
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                }
                            }
                            items(
                                dayTxns,
                                key = { "txn_${it.id}" },
                            ) { transaction ->
                                Surface(
                                    modifier = Modifier.fillMaxWidth(),
                                    color = LedgerRaisedSurface,
                                    shape = RoundedCornerShape(18.dp),
                                ) {
                                    LedgerTransactionListRow(
                                        transaction = transaction,
                                        currencySymbol = currencySymbol,
                                        onClick = { onEditTransaction(transaction) },
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Bottom spacer
            item(key = "bottom_spacer") {
                Spacer(Modifier.height(76.dp))
            }
        }

        // Bottom action bar: 记一笔 + 转账
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding(),
            color = LedgerRaisedSurface,
            shadowElevation = 4.dp,
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .yingShiClickable(
                            pressedScale = 0.97f,
                            shape = RoundedCornerShape(24.dp),
                            onClick = onAddTransaction,
                        ),
                    shape = RoundedCornerShape(24.dp),
                    color = LedgerHeaderGreen,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = "记一笔",
                            color = LedgerRaisedSurface,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .yingShiClickable(
                            pressedScale = 0.97f,
                            shape = RoundedCornerShape(24.dp),
                            onClick = { onTransfer(account.id) },
                        ),
                    shape = RoundedCornerShape(24.dp),
                    color = LedgerPrimaryAction,
                    border = BorderStroke(
                        1.dp,
                        LedgerGlassStroke.copy(alpha = 0.88f),
                    ),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        Icon(
                            Icons.Default.SwapHoriz,
                            contentDescription = null,
                            tint = LedgerHeaderGreen,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = "转账",
                            color = LedgerHeaderGreen,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
        }
    }

    // 3-dot menu bottom sheet
    if (showMenuSheet) {
        LedgerActionSheet(
            title = account.name,
            onDismiss = { showMenuSheet = false },
            actions = listOf(
                LedgerSheetAction("编辑账户") {
                    editingAccount = true
                },
                LedgerSheetAction(
                    label = "删除账户",
                    destructive = true,
                ) {
                    showDeleteDialog = true
                },
            ),
        )
    }

    // Edit account sheet
    if (editingAccount) {
        LedgerAccountEditorSheet(
            initial = account,
            onDismiss = { editingAccount = false },
            onSave = { draft ->
                onSaveAccount(draft.copy(id = account.id))
                editingAccount = false
            },
        )
    }

    // Delete confirmation dialog
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            containerColor = LedgerRaisedSurface,
            title = {
                Text(
                    text = "删除账户？",
                    color = LedgerHeaderGreen,
                    fontWeight = FontWeight.Bold,
                )
            },
            text = {
                Text(
                    text = "删除后该账户关联的交易记录不会被删除，但账户本身无法恢复。",
                    color = LedgerMuted,
                    style = MaterialTheme.typography.bodyMedium,
                )
            },
            confirmButton = {
                LedgerDialogActionButton(
                    text = "确认删除",
                    danger = true,
                    onClick = {
                        showDeleteDialog = false
                        onDeleteAccount()
                    },
                )
            },
            dismissButton = {
                LedgerDialogActionButton(
                    text = "取消",
                    onClick = { showDeleteDialog = false },
                )
            },
        )
    }
}

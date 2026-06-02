package com.example.yingshi.feature.ledger

import android.app.Application
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Timelapse
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.yingshi.feature.ledger.data.LedgerTransaction
import com.example.yingshi.feature.ledger.data.LedgerTransactionType
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

enum class LedgerRoute {
    HOME,
    ADD,
    BOOKS,
    ASSETS,
    STATS,
    BUDGET,
    RECURRING,
    IMPORT,
    CALENDAR,
    SEARCH,
    CATEGORIES,
    SETTINGS,
    TRASH,
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LedgerScreen(
    modifier: Modifier = Modifier,
    openHomeNonce: Int = 0,
    openAddNonce: Int = 0,
    onCloseLedger: () -> Unit = {},
    viewModel: LedgerViewModel = viewModel(
        factory = LedgerViewModel.factory(LocalContext.current.applicationContext as Application),
    ),
) {
    val uiState by viewModel.uiState.collectAsState()
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var route by rememberSaveable { mutableStateOf(LedgerRoute.HOME.name) }
    var editingTransactionId by rememberSaveable { mutableStateOf<String?>(null) }
    var lastOpenHomeNonce by rememberSaveable { mutableStateOf(0) }
    var lastOpenAddNonce by rememberSaveable { mutableStateOf(0) }

    LaunchedEffect(Unit) {
        viewModel.handleLedgerEntry()
    }
    LaunchedEffect(openHomeNonce) {
        if (openHomeNonce > 0 && openHomeNonce != lastOpenHomeNonce) {
            lastOpenHomeNonce = openHomeNonce
            editingTransactionId = null
            route = LedgerRoute.HOME.name
        }
    }
    LaunchedEffect(openAddNonce) {
        if (shouldOpenLedgerAdd(openAddNonce, lastOpenAddNonce)) {
            lastOpenAddNonce = openAddNonce
            editingTransactionId = null
            route = LedgerRoute.ADD.name
        }
    }
    LaunchedEffect(uiState.message) {
        uiState.message?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            viewModel.consumeMessage()
        }
    }
    BackHandler(enabled = drawerState.isOpen) {
        scope.launch { drawerState.close() }
    }
    BackHandler(enabled = route != LedgerRoute.HOME.name) {
        route = LedgerRoute.HOME.name
    }
    BackHandler(enabled = route == LedgerRoute.HOME.name && !drawerState.isOpen) {
        onCloseLedger()
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = route == LedgerRoute.HOME.name,
        drawerContent = {
            LedgerDrawer(
                uiState = uiState,
                onNavigate = {
                    route = it.name
                    scope.launch { drawerState.close() }
                },
            )
        },
        modifier = modifier,
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(LedgerPageBackground),
        ) {
            when (LedgerRoute.valueOf(route)) {
                LedgerRoute.HOME -> LedgerHomeScreen(
                    uiState = uiState,
                    onOpenDrawer = { scope.launch { drawerState.open() } },
                    onSelectBook = viewModel::selectBook,
                    onSelectMonth = viewModel::selectMonth,
                    onNavigate = { route = it.name },
                    onCloseLedger = onCloseLedger,
                    onAdd = {
                        editingTransactionId = null
                        route = LedgerRoute.ADD.name
                    },
                    onEditTransaction = {
                        editingTransactionId = it.id
                        route = LedgerRoute.ADD.name
                    },
                )

                LedgerRoute.BOOKS -> LedgerBooksScreen(
                    uiState = uiState,
                    onBack = { route = LedgerRoute.HOME.name },
                    onSaveBook = viewModel::saveBook,
                    onSetDefaultBook = viewModel::setDefaultBook,
                    onArchiveBook = { viewModel.setBookArchived(it, true) },
                    onRestoreBook = { viewModel.setBookArchived(it, false) },
                )

                LedgerRoute.ADD -> LedgerAddTransactionScreen(
                    uiState = uiState,
                    initialTransaction = (uiState.allTransactions + uiState.transactions + uiState.stats.transactions)
                        .distinctBy { it.id }
                        .firstOrNull { it.id == editingTransactionId },
                    onBack = { route = LedgerRoute.HOME.name },
                    onSelectBook = viewModel::selectBook,
                    onSaveCategory = viewModel::saveCategory,
                    onToggleCategoryHidden = viewModel::setCategoryHidden,
                    onReorderCategories = viewModel::reorderCategories,
                    onSave = { transactionId, type, amount, categoryId, accountId, toAccountId, occurredAt, remark, keepOpen ->
                        viewModel.saveTransaction(
                            transactionId = transactionId,
                            type = type,
                            amountCents = amount,
                            categoryId = categoryId,
                            accountId = accountId,
                            toAccountId = toAccountId,
                            occurredAtMillis = occurredAt,
                            remark = remark,
                            keepOpen = keepOpen,
                            onSaved = {
                                if (!keepOpen) route = LedgerRoute.HOME.name
                                editingTransactionId = null
                            },
                        )
                    },
                )

                LedgerRoute.ASSETS -> LedgerAssetsScreen(
                    uiState = uiState,
                    onBack = { route = LedgerRoute.HOME.name },
                    onEditTransaction = {
                        editingTransactionId = it.id
                        route = LedgerRoute.ADD.name
                    },
                    onSaveAccount = viewModel::saveAccount,
                    onToggleAccountHidden = viewModel::setAccountHidden,
                    onReorderAccounts = viewModel::reorderAccounts,
                )
                LedgerRoute.STATS -> LedgerStatsScreen(
                    uiState = uiState,
                    onBack = { route = LedgerRoute.HOME.name },
                    onSelectBook = viewModel::selectBook,
                    onSelectMode = viewModel::selectStatsMode,
                    onShiftPeriod = viewModel::shiftStatsPeriod,
                    onSelectCustomRange = viewModel::selectCustomStatsRange,
                    onEditTransaction = {
                        editingTransactionId = it.id
                        route = LedgerRoute.ADD.name
                    },
                )

                LedgerRoute.BUDGET -> LedgerBudgetScreen(
                    uiState = uiState,
                    onBack = { route = LedgerRoute.HOME.name },
                    onSelectBook = viewModel::selectBook,
                    onSelectPeriod = viewModel::selectBudgetPeriod,
                    onSetBudget = viewModel::setBudget,
                    onSetCategoryBudget = viewModel::setCategoryBudget,
                    onClearBudget = viewModel::clearBudget,
                    onClearCategoryBudget = viewModel::clearCategoryBudget,
                )

                LedgerRoute.RECURRING -> LedgerRecurringScreen(
                    uiState = uiState,
                    onBack = { route = LedgerRoute.HOME.name },
                    onSelectBook = viewModel::selectBook,
                    onOpenBooks = { route = LedgerRoute.BOOKS.name },
                    onSaveRule = viewModel::saveRecurringRule,
                    onToggleRuleEnabled = viewModel::setRecurringRuleEnabled,
                    onDeleteRule = viewModel::deleteRecurringRule,
                    onRefresh = viewModel::refreshRecurringRules,
                )

                LedgerRoute.IMPORT -> LedgerStaticScreen(
                    title = "记账导入",
                    summary = "导入入口保留位置，本轮先不接真实导入流程。",
                    onBack = { route = LedgerRoute.HOME.name },
                )

                LedgerRoute.CALENDAR -> LedgerCalendarScreen(
                    uiState = uiState,
                    onBack = { route = LedgerRoute.HOME.name },
                    onSelectDate = viewModel::selectDate,
                    onAdd = { route = LedgerRoute.ADD.name },
                )

                LedgerRoute.SEARCH -> LedgerSearchScreen(
                    uiState = uiState,
                    onBack = { route = LedgerRoute.HOME.name },
                    onEditTransaction = {
                        editingTransactionId = it.id
                        route = LedgerRoute.ADD.name
                    },
                    onUpdateKeyword = viewModel::updateSearchKeyword,
                    onUpdateType = viewModel::updateSearchType,
                    onUpdateCategory = viewModel::updateSearchCategory,
                    onUpdateAccount = viewModel::updateSearchAccount,
                    onUpdateDateRange = viewModel::updateSearchDateRange,
                    onUpdateAmountRange = viewModel::updateSearchAmountRange,
                    onClearFilters = viewModel::clearSearchFilters,
                    onToggleSelectionMode = viewModel::toggleSearchSelectionMode,
                    onToggleTransactionSelection = viewModel::toggleSearchTransactionSelection,
                    onClearSelection = viewModel::clearSearchSelection,
                    onDeleteSelected = viewModel::deleteSelectedTransactions,
                    onBatchUpdateCategory = viewModel::updateSelectedTransactionsCategory,
                    onBatchUpdateAccount = viewModel::updateSelectedTransactionsAccount,
                    onBatchUpdateTransferAccount = viewModel::updateSelectedTransferTransactionsAccount,
                )

                LedgerRoute.CATEGORIES -> LedgerCategoriesScreen(
                    uiState = uiState,
                    onBack = { route = LedgerRoute.HOME.name },
                    onSaveCategory = viewModel::saveCategory,
                    onToggleCategoryHidden = viewModel::setCategoryHidden,
                    onReorderCategories = viewModel::reorderCategories,
                )

                LedgerRoute.SETTINGS -> LedgerSettingsScreen(
                    uiState = uiState,
                    onBack = { route = LedgerRoute.HOME.name },
                    onOpenBooks = { route = LedgerRoute.BOOKS.name },
                    onOpenRecurring = { route = LedgerRoute.RECURRING.name },
                    onSetDefaultBook = viewModel::setDefaultBook,
                    onSetDefaultAccountForBook = viewModel::setDefaultAccountForBook,
                )

                LedgerRoute.TRASH -> LedgerTrashScreen(
                    uiState = uiState,
                    onBack = { route = LedgerRoute.HOME.name },
                    onRestore = viewModel::restoreTransaction,
                    onPermanentDelete = viewModel::permanentlyDeleteTransaction,
                )
            }
        }
    }
}

internal fun shouldOpenLedgerAdd(openAddNonce: Int, lastOpenAddNonce: Int): Boolean {
    return openAddNonce > 0 && openAddNonce != lastOpenAddNonce
}

@Composable
private fun LedgerHomeScreen(
    uiState: LedgerUiState,
    onOpenDrawer: () -> Unit,
    onSelectBook: (String) -> Unit,
    onSelectMonth: (YearMonth) -> Unit,
    onNavigate: (LedgerRoute) -> Unit,
    onCloseLedger: () -> Unit,
    onAdd: () -> Unit,
    onEditTransaction: (LedgerTransaction) -> Unit,
) {
    var showBookSheet by rememberSaveable { mutableStateOf(false) }
    var showMonthSheet by rememberSaveable { mutableStateOf(false) }
    val grouped = remember(uiState.transactions) {
        uiState.transactions.groupBy { dayStart(it.occurredAtMillis) }
            .toList()
            .sortedByDescending { it.first }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            LedgerHomeHeader(
                uiState = uiState,
                onOpenDrawer = onOpenDrawer,
                onBookClick = { showBookSheet = true },
                onMonthClick = { showMonthSheet = true },
                onCloseLedger = onCloseLedger,
            )
            LedgerQuickActionsRow(
                uiState = uiState,
                onNavigate = onNavigate,
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .offset(y = (-14).dp),
            )
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 2.dp, bottom = 104.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (grouped.isEmpty()) {
                    item {
                        LedgerEmptyState(
                            title = "当月暂无账单",
                            summary = "点右下角记一笔，账单会按日期自动分组。",
                        )
                    }
                } else {
                    items(grouped, key = { it.first }) { (dayStart, dayTransactions) ->
                        LedgerDayGroupCard(
                            dayStartMillis = dayStart,
                            transactions = dayTransactions,
                            currencySymbol = uiState.currencySymbol,
                            onTransactionClick = onEditTransaction,
                        )
                    }
                }
            }
        }

        FloatingActionButton(
            onClick = onAdd,
            containerColor = LedgerHeaderGreen,
            contentColor = Color.White,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .navigationBarsPadding()
                .padding(end = 18.dp, bottom = 16.dp),
        ) {
            Text("+", style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
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
            onManageBooks = { onNavigate(LedgerRoute.BOOKS) },
        )
    }
    if (showMonthSheet) {
        LedgerMonthPickerSheet(
            selectedMonth = uiState.selectedMonth,
            onDismiss = { showMonthSheet = false },
            onConfirm = {
                onSelectMonth(it)
                showMonthSheet = false
            },
        )
    }
}

@Composable
private fun LedgerHomeHeader(
    uiState: LedgerUiState,
    onOpenDrawer: () -> Unit,
    onBookClick: () -> Unit,
    onMonthClick: () -> Unit,
    onCloseLedger: () -> Unit,
) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(LedgerHeaderGreen)
                .statusBarsPadding()
                .padding(horizontal = 14.dp)
                .padding(top = 6.dp, bottom = 14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onOpenDrawer, modifier = Modifier.size(34.dp)) {
                Icon(LedgerActionIcons.Menu, contentDescription = "菜单", tint = Color.White, modifier = Modifier.size(24.dp))
            }
            Row(
                modifier = Modifier
                    .weight(1f)
                    .clickable(onClick = onBookClick),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = uiState.bookName,
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Icon(Icons.Default.ArrowDropDown, contentDescription = "切换账本", tint = Color.White, modifier = Modifier.size(16.dp))
            }
            Surface(
                shape = RoundedCornerShape(22.dp),
                color = Color.White.copy(alpha = 0.12f),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.18f)),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    IconButton(onClick = {}, modifier = Modifier.size(26.dp)) {
                        Icon(Icons.Default.MoreHoriz, contentDescription = "更多", tint = Color.White, modifier = Modifier.size(18.dp))
                    }
                    Box(
                        modifier = Modifier
                            .height(18.dp)
                            .width(1.dp)
                            .background(Color.White.copy(alpha = 0.22f)),
                    )
                    IconButton(onClick = onCloseLedger, modifier = Modifier.size(26.dp)) { LedgerCloseCircleIcon() }
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Bottom,
        ) {
            Column(
                modifier = Modifier
                .weight(1.15f)
                .clickable(onClick = onMonthClick),
            ) {
                Text(
                    text = formatYearLabel(uiState.selectedMonth) + "年",
                    color = Color.White.copy(alpha = 0.75f),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = formatMonthLabel(uiState.selectedMonth),
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "月",
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Icon(Icons.Default.ArrowDropDown, contentDescription = "选择月份", tint = Color.White, modifier = Modifier.size(15.dp))
                }
            }
            Box(
                modifier = Modifier
                    .padding(bottom = 4.dp)
                    .width(1.dp)
                    .height(44.dp)
                    .background(Color.White.copy(alpha = 0.55f)),
            )
            LedgerHeaderMetric(
                title = "支出",
                value = formatAmountValue(uiState.stats.expenseCents),
                modifier = Modifier.weight(1f),
            )
            LedgerHeaderMetric(
                title = "收入",
                value = formatAmountValue(uiState.stats.incomeCents),
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun LedgerHeaderMetric(
    title: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(start = 14.dp, bottom = 4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = title,
            color = Color.White.copy(alpha = 0.75f),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = value,
            color = Color.White,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun LedgerQuickActionsRow(
    uiState: LedgerUiState,
    onNavigate: (LedgerRoute) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        color = Color.White,
        shape = RoundedCornerShape(24.dp),
        shadowElevation = 1.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            listOf(
                Triple("资产", "asset", LedgerRoute.ASSETS),
                Triple("统计", "stats", LedgerRoute.STATS),
                Triple("预算", "budget", LedgerRoute.BUDGET),
                Triple("导入", "import", LedgerRoute.IMPORT),
            ).forEach { (title, iconKey, route) ->
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onNavigate(route) },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(LedgerGreenSoft),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            ledgerIcon(iconKey),
                            contentDescription = title,
                            tint = LedgerHeaderGreen,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                    Text(
                        text = title,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
}

@Composable
private fun LedgerDayGroupCard(
    dayStartMillis: Long,
    transactions: List<LedgerTransaction>,
    currencySymbol: String,
    onTransactionClick: (LedgerTransaction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val income = transactions.filter { it.type == LedgerTransactionType.INCOME }.sumOf { it.amountCents }
    val expense = transactions.filter { it.type == LedgerTransactionType.EXPENSE }.sumOf { it.amountCents }
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = Color.White,
        shape = RoundedCornerShape(26.dp),
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(LedgerGroupedHeader)
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = formatLedgerGroupDate(dayStartMillis),
                    modifier = Modifier.weight(1f),
                    color = LedgerSubtleText,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "收 ${formatAmountValue(income)}",
                    color = LedgerSubtleText,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.width(20.dp))
                Text(
                    text = "支 ${formatAmountValue(expense)}",
                    color = LedgerSubtleText,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            transactions.forEach { transaction ->
                LedgerTransactionListRow(
                    transaction = transaction,
                    currencySymbol = currencySymbol,
                    onClick = { onTransactionClick(transaction) },
                )
            }
        }
    }
}

@Composable
fun LedgerTransactionListRow(
    transaction: LedgerTransaction,
    currencySymbol: String,
    onClick: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = onClick != null) { onClick?.invoke() }
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(transaction.category?.color?.let(::ledgerColor) ?: LedgerHeaderGreen),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = if (transaction.type == LedgerTransactionType.TRANSFER) {
                    ledgerIcon("transfer")
                } else {
                    ledgerIcon(transaction.category?.iconKey ?: "more_horiz")
                },
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(20.dp),
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = when (transaction.type) {
                    LedgerTransactionType.TRANSFER -> "转账"
                    else -> transaction.category?.name ?: "未分类"
                },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = when (transaction.type) {
                    LedgerTransactionType.TRANSFER -> "${transaction.account?.name.orEmpty()} -> ${transaction.toAccount?.name.orEmpty()}"
                    else -> transaction.remark.ifBlank { transaction.account?.name.orEmpty() }
                },
                color = LedgerSubtleText,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = when (transaction.type) {
                    LedgerTransactionType.EXPENSE -> "-${formatAmountValue(transaction.amountCents)}"
                    LedgerTransactionType.INCOME -> formatAmountValue(transaction.amountCents)
                    LedgerTransactionType.TRANSFER -> formatAmountValue(transaction.amountCents)
                },
                color = when (transaction.type) {
                    LedgerTransactionType.EXPENSE -> LedgerExpenseRed
                    LedgerTransactionType.INCOME -> LedgerHeaderGreen
                    LedgerTransactionType.TRANSFER -> MaterialTheme.colorScheme.onSurface
                },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = transaction.account?.name.orEmpty(),
                color = LedgerSubtleText,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun LedgerCloseCircleIcon() {
    Box(contentAlignment = Alignment.Center) {
        Icon(Icons.Default.RadioButtonUnchecked, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
        Icon(Icons.Default.Close, contentDescription = null, tint = Color.White, modifier = Modifier.size(10.dp))
    }
}

@Composable
private fun LedgerDrawer(
    uiState: LedgerUiState,
    onNavigate: (LedgerRoute) -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth(0.78f)
            .fillMaxSize(),
        color = Color.White,
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(horizontal = 14.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 14.dp)) {
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .clip(CircleShape)
                            .background(LedgerHeaderGreen),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(ledgerIcon("wallet"), contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
                    }
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(uiState.bookName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text("净资产 ${formatAmountValue(uiState.netAssetCents)}", style = MaterialTheme.typography.bodySmall, color = LedgerSubtleText)
                    }
                }
            }
            drawerSection("功能")
            drawerItem("日历", "calendar", LedgerRoute.CALENDAR, onNavigate)
            drawerItem("搜索", "search", LedgerRoute.SEARCH, onNavigate)
            drawerItem("统计", "stats", LedgerRoute.STATS, onNavigate)
            drawerSection("管理")
            drawerItem("账本管理", "wallet", LedgerRoute.BOOKS, onNavigate)
            drawerItem("资产管理", "asset", LedgerRoute.ASSETS, onNavigate)
            drawerItem("分类管理", "category", LedgerRoute.CATEGORIES, onNavigate)
            drawerItem("预算管理", "budget", LedgerRoute.BUDGET, onNavigate)
            drawerItem("周期记账", "timelapse", LedgerRoute.RECURRING, onNavigate)
            drawerItem("记账导入", "import", LedgerRoute.IMPORT, onNavigate)
            drawerItem("记账设置", "settings", LedgerRoute.SETTINGS, onNavigate)
            drawerItem("回收站", "trash", LedgerRoute.TRASH, onNavigate)
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.drawerSection(title: String) {
    item {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium,
            color = LedgerSubtleText,
            modifier = Modifier.padding(top = 10.dp, bottom = 4.dp),
        )
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.drawerItem(
    title: String,
    iconKey: String,
    route: LedgerRoute,
    onNavigate: (LedgerRoute) -> Unit,
) {
    item {
        NavigationDrawerItem(
            label = { Text(title, style = MaterialTheme.typography.bodyMedium) },
            selected = false,
            icon = { Icon(ledgerIcon(iconKey), contentDescription = null, modifier = Modifier.size(18.dp)) },
            onClick = { onNavigate(route) },
            colors = NavigationDrawerItemDefaults.colors(
                unselectedContainerColor = Color.Transparent,
                unselectedIconColor = LedgerHeaderGreen,
            ),
        )
    }
}

@Composable
fun LedgerPageScaffold(
    title: String,
    onBack: () -> Unit,
    action: @Composable (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(LedgerPageBackground)
            .statusBarsPadding(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 6.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack, modifier = Modifier.size(32.dp)) {
                Icon(LedgerActionIcons.Back, contentDescription = "返回", modifier = Modifier.size(18.dp))
            }
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center,
            )
            Box(modifier = Modifier.width(32.dp), contentAlignment = Alignment.CenterEnd) {
                action?.invoke()
            }
        }
        content()
    }
}

@Composable
fun LedgerStaticScreen(title: String, summary: String, onBack: () -> Unit) {
    LedgerPageScaffold(title = title, onBack = onBack) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            LedgerEmptyState(title = title, summary = summary)
        }
    }
}

@Composable
private fun LedgerEmptyState(title: String, summary: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            modifier = Modifier
                .size(68.dp)
                .clip(CircleShape)
                .background(LedgerGreenSoft),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Default.Timelapse, contentDescription = null, tint = LedgerHeaderGreen, modifier = Modifier.size(28.dp))
        }
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(summary, style = MaterialTheme.typography.bodySmall, color = LedgerSubtleText, textAlign = TextAlign.Center)
    }
}

internal fun formatLedgerGroupDate(dayStartMillis: Long): String {
    val date = Instant.ofEpochMilli(dayStartMillis).atZone(ZoneId.systemDefault()).toLocalDate()
    val week = date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.CHINA)
    return "%02d/%02d %s".format(date.monthValue, date.dayOfMonth, week)
}

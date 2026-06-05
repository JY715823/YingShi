package com.example.yingshi.feature.ledger

import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
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
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.IosShare
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.RadioButtonChecked
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.yingshi.feature.ledger.data.LedgerAccount
import com.example.yingshi.feature.ledger.data.LedgerAccountType
import com.example.yingshi.feature.ledger.data.LedgerBudgetPeriod
import com.example.yingshi.feature.ledger.data.LedgerCategory
import com.example.yingshi.feature.ledger.data.LedgerCategoryStat
import com.example.yingshi.feature.ledger.data.LedgerCategoryType
import com.example.yingshi.feature.ledger.data.LedgerDailyStat
import com.example.yingshi.feature.ledger.data.LedgerSearchTransactionType
import com.example.yingshi.feature.ledger.data.LedgerTransferAccountSide
import com.example.yingshi.feature.ledger.data.LedgerTransaction
import com.example.yingshi.feature.ledger.data.LedgerTransactionType
import com.example.yingshi.feature.ledger.data.belongsToLedgerAccount
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import androidx.compose.foundation.gestures.detectDragGestures
import kotlinx.coroutines.launch
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

private enum class LedgerAssetScope(val label: String) {
    ALL("全部"),
    MINE("我的"),
    PARTNER("女朋友的"),
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LedgerAssetsScreen(
    uiState: LedgerUiState,
    onBack: () -> Unit,
    onEditTransaction: (LedgerTransaction) -> Unit,
    onSaveAccount: (
        String?,
        String,
        LedgerAccountType,
        Long,
        Boolean,
        String,
    ) -> Unit,
    onToggleAccountHidden: (String, Boolean) -> Unit,
    onReorderAccounts: (List<String>) -> Unit,
) {
    var showAccountDetail by remember { mutableStateOf<LedgerAccount?>(null) }
    var editingAccount by remember { mutableStateOf<LedgerAccount?>(null) }
    var actionAccount by remember { mutableStateOf<LedgerAccount?>(null) }
    var showCreateAccountSheet by rememberSaveable { mutableStateOf(false) }
    var selectedScopeName by rememberSaveable { mutableStateOf(LedgerAssetScope.ALL.name) }
    val assetScopes = LedgerAssetScope.entries
    val selectedScope = LedgerAssetScope.valueOf(selectedScopeName)
    val pagerState = rememberPagerState(
        initialPage = assetScopes.indexOf(selectedScope).coerceAtLeast(0),
        pageCount = { assetScopes.size },
    )
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(selectedScopeName) {
        val targetPage = assetScopes.indexOf(LedgerAssetScope.valueOf(selectedScopeName))
        if (targetPage >= 0 && targetPage != pagerState.currentPage) {
            pagerState.animateScrollToPage(targetPage)
        }
    }
    LaunchedEffect(pagerState.currentPage) {
        val pageScope = assetScopes.getOrNull(pagerState.currentPage) ?: return@LaunchedEffect
        if (pageScope.name != selectedScopeName) {
            selectedScopeName = pageScope.name
        }
    }

    LedgerPageScaffold(
        title = "资产管理",
        onBack = onBack,
        action = {
            Surface(
                modifier = Modifier
                    .size(44.dp)
                    .clickable { showCreateAccountSheet = true },
                shape = CircleShape,
                color = LedgerPrimaryAction,
                border = BorderStroke(1.dp, LedgerGlassStroke.copy(alpha = 0.88f)),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = "新增账户",
                        tint = LedgerHeaderGreen,
                        modifier = Modifier.size(25.dp),
                    )
                }
            }
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .navigationBarsPadding()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                assetScopes.forEach { scope ->
                    LedgerSegmentChip(
                        text = scope.label,
                        selected = selectedScope == scope,
                        modifier = Modifier.weight(1f),
                        horizontalPadding = 14.dp,
                        verticalPadding = 8.dp,
                        largeText = true,
                        onClick = {
                            selectedScopeName = scope.name
                            coroutineScope.launch {
                                pagerState.animateScrollToPage(assetScopes.indexOf(scope))
                            }
                        },
                    )
                }
            }

            HorizontalPager(
                state = pagerState,
                modifier = Modifier.weight(1f),
                key = { page -> assetScopes[page].name },
            ) { page ->
                val pageScope = assetScopes[page]
                val visibleAccounts = uiState.allAccounts.filter { account -> account.matchesAssetScope(pageScope) }
                LedgerAssetsScopePage(
                    allAccounts = uiState.allAccounts,
                    visibleAccounts = visibleAccounts,
                    onReorderAccounts = onReorderAccounts,
                    onOpenAccount = { showAccountDetail = it },
                    onMoreAccount = { actionAccount = it },
                )
            }
        }
    }

    showAccountDetail?.let { account ->
        LedgerTransactionsDetailSheet(
            title = "${account.name}账单",
            transactions = uiState.allTransactions
                .filter { transaction -> transaction.belongsToLedgerAccount(account.id) }
                .sortedByDescending { it.occurredAtMillis },
            currencySymbol = uiState.currencySymbol,
            onDismiss = { showAccountDetail = null },
            onTransactionClick = {
                showAccountDetail = null
                onEditTransaction(it)
            },
        )
    }
    actionAccount?.let { account ->
        LedgerActionSheet(
            title = account.name,
            onDismiss = { actionAccount = null },
            actions = listOf(
                LedgerSheetAction("编辑账户") {
                    editingAccount = account
                },
                LedgerSheetAction(if (account.hidden) "显示账户" else "隐藏账户") {
                    onToggleAccountHidden(account.id, !account.hidden)
                },
            ),
        )
    }
    if (showCreateAccountSheet) {
        LedgerAccountEditorSheet(
            onDismiss = { showCreateAccountSheet = false },
            onSave = { name, type, initialBalanceCents, includeInTotal, note ->
                onSaveAccount(null, name, type, initialBalanceCents, includeInTotal, note)
                showCreateAccountSheet = false
            },
        )
    }
    editingAccount?.let { account ->
        LedgerAccountEditorSheet(
            initial = account,
            onDismiss = { editingAccount = null },
            onSave = { name, type, initialBalanceCents, includeInTotal, note ->
                onSaveAccount(account.id, name, type, initialBalanceCents, includeInTotal, note)
                editingAccount = null
            },
        )
    }
}

@Composable
private fun LedgerAssetsScopePage(
    allAccounts: List<LedgerAccount>,
    visibleAccounts: List<LedgerAccount>,
    onReorderAccounts: (List<String>) -> Unit,
    onOpenAccount: (LedgerAccount) -> Unit,
    onMoreAccount: (LedgerAccount) -> Unit,
) {
    val scopedNetAssetCents = remember(visibleAccounts) {
        visibleAccounts.filter { it.includeInTotal && !it.hidden }.sumOf { it.balanceCents }
    }

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Surface(
            color = LedgerHeaderGreen,
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("净资产", color = Color.White.copy(alpha = 0.82f), style = MaterialTheme.typography.bodyMedium)
                Text(
                    formatAmountValue(scopedNetAssetCents),
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
        LedgerLongPressReorderList(
            items = visibleAccounts,
            keyOf = { it.id },
            modifier = Modifier.weight(1f),
            onOrderCommitted = { orderedVisibleIds ->
                onReorderAccounts(
                    mergeScopedAccountOrder(
                        allAccounts = allAccounts,
                        scopedAccountIds = visibleAccounts.map { it.id }.toSet(),
                        orderedScopedIds = orderedVisibleIds,
                    ),
                )
            },
            itemHeight = 72.dp,
        ) { account, _ ->
            AccountRow(
                account = account,
                onClick = { onOpenAccount(account) },
                onMoreClick = { onMoreAccount(account) },
            )
        }
    }
}

@Composable
private fun AccountRow(

    account: LedgerAccount,
    onClick: () -> Unit,
    onMoreClick: () -> Unit,
) {
    Surface(
        color = Color.White,
        shape = RoundedCornerShape(22.dp),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(ledgerColor(account.color)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(accountIcon(account.type), contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(account.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                    if (account.hidden) {
                        LedgerHiddenBadge()
                    }
                }
                Text(
                    account.note.ifBlank { "余额随账单自动更新" },
                    style = MaterialTheme.typography.bodySmall,
                    color = LedgerSubtleText,
                )
            }
            Text(formatAmountValue(account.balanceCents), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
            Icon(
                Icons.Default.MoreHoriz,
                contentDescription = "更多",
                modifier = Modifier
                    .size(18.dp)
                    .clickable(onClick = onMoreClick),
            )
        }
    }
}

private fun LedgerAccount.matchesAssetScope(scope: LedgerAssetScope): Boolean {
    val isPartner = listOf(name, note).any { text ->
        text.contains("女朋友") ||
            text.contains("女友") ||
            text.contains("对象") ||
            text.contains("另一半") ||
            text.contains("她的") ||
            text.contains("partner", ignoreCase = true)
    }
    return when (scope) {
        LedgerAssetScope.ALL -> true
        LedgerAssetScope.MINE -> !isPartner
        LedgerAssetScope.PARTNER -> isPartner
    }
}

private fun mergeScopedAccountOrder(
    allAccounts: List<LedgerAccount>,
    scopedAccountIds: Set<String>,
    orderedScopedIds: List<String>,
): List<String> {
    if (scopedAccountIds.size == allAccounts.size) return orderedScopedIds
    val scopedIterator = orderedScopedIds.iterator()
    return allAccounts.map { account ->
        if (account.id in scopedAccountIds && scopedIterator.hasNext()) {
            scopedIterator.next()
        } else {
            account.id
        }
    }
}

private fun pointerAngleDegrees(position: Offset, center: Offset): Float {
    return Math.toDegrees(
        atan2(
            (position.y - center.y).toDouble(),
            (position.x - center.x).toDouble(),
        ),
    ).toFloat()
}

private fun shortestAngleDelta(current: Float, previous: Float): Float {
    var delta = current - previous
    while (delta > 180f) delta -= 360f
    while (delta < -180f) delta += 360f
    return delta
}

@Composable
fun LedgerStatsScreen(
    uiState: LedgerUiState,
    onBack: () -> Unit,
    onSelectBook: (String) -> Unit,
    onSelectMode: (LedgerStatsMode) -> Unit,
    onShiftPeriod: (Int) -> Unit,
    onSelectCustomRange: (LocalDate, LocalDate) -> Unit,
    onEditTransaction: (LedgerTransaction) -> Unit,
) {
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current
    var selectedLineKeys by rememberSaveable { mutableStateOf(setOf("expense")) }
    var selectedCategoryType by rememberSaveable { mutableStateOf(LedgerCategoryType.EXPENSE.name) }
    var showBookSheet by rememberSaveable { mutableStateOf(false) }
    var showCategoryDetail by rememberSaveable { mutableStateOf<LedgerCategoryStat?>(null) }
    var showCustomRangeDialog by rememberSaveable { mutableStateOf(false) }
    val categoryType = LedgerCategoryType.valueOf(selectedCategoryType)
    val categoryStats = remember(uiState.stats.transactions, categoryType) {
        val targetType = if (categoryType == LedgerCategoryType.EXPENSE) {
            LedgerTransactionType.EXPENSE
        } else {
            LedgerTransactionType.INCOME
        }
        val typedTransactions = uiState.stats.transactions.filter { it.type == targetType }
        val total = typedTransactions.sumOf { it.amountCents }.coerceAtLeast(1L)
        typedTransactions
            .groupBy { it.category?.id ?: "uncategorized" }
            .map { (_, items) ->
                val amount = items.sumOf { it.amountCents }
                LedgerCategoryStat(
                    category = items.firstOrNull()?.category,
                    amountCents = amount,
                    count = items.size,
                    percent = amount.toFloat() / total.toFloat(),
                )
            }
            .sortedByDescending { it.amountCents }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(LedgerPageBackground)
            .statusBarsPadding(),
    ) {
        LedgerStatsTopBar(
            onBack = onBack,
            onShare = {
                clipboardManager.setText(AnnotatedString(ledgerStatsReportText(uiState)))
                Toast.makeText(context, "统计报告已复制", Toast.LENGTH_SHORT).show()
            },
        )
        LedgerStatsModeTabs(
            selected = uiState.selectedStatsMode,
            onSelected = {
                if (it == LedgerStatsMode.CUSTOM) {
                    showCustomRangeDialog = true
                }
                onSelectMode(it)
            },
        )
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .navigationBarsPadding(),
            contentPadding = PaddingValues(horizontal = 18.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                StatsSummaryCard(
                    uiState = uiState,
                    onShiftPeriod = onShiftPeriod,
                    onBookClick = { showBookSheet = true },
                )
            }
            item {
                StatsTrendCard(
                    uiState = uiState,
                    selectedLineKeys = selectedLineKeys,
                    onToggleKey = { key ->
                        selectedLineKeys = if (selectedLineKeys.contains(key)) {
                            selectedLineKeys - key
                        } else {
                            selectedLineKeys + key
                        }.ifEmpty { setOf("expense") }
                    },
                )
            }
            item {
                StatsCategoryCard(
                    uiState = uiState,
                    categoryType = categoryType,
                    categoryStats = categoryStats,
                    onCategoryTypeChange = { selectedCategoryType = it.name },
                    onCategoryClick = { showCategoryDetail = it },
                )
            }
            item {
                StatsCompareCard(uiState = uiState)
            }
            item {
                StatsReportCard(uiState = uiState)
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
        )
    }
    showCategoryDetail?.let { stat ->
        LedgerCategoryDetailSheet(
            uiState = uiState,
            stat = stat,
            onDismiss = { showCategoryDetail = null },
            onTransactionClick = {
                showCategoryDetail = null
                onEditTransaction(it)
            },
        )
    }
    if (showCustomRangeDialog) {
        CustomRangeDialog(
            startDate = uiState.customStatsStartDate,
            endDate = uiState.customStatsEndDate,
            onDismiss = { showCustomRangeDialog = false },
            onConfirm = { start, end ->
                onSelectCustomRange(start, end)
                showCustomRangeDialog = false
            },
        )
    }
}

@Composable
private fun LedgerStatsTopBar(
    onBack: () -> Unit,
    onShare: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 6.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack, modifier = Modifier.size(32.dp)) {
            Icon(Icons.Default.ArrowBack, contentDescription = "返回", modifier = Modifier.size(18.dp))
        }
        Text(
            text = "统计",
            modifier = Modifier.weight(1f),
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
        IconButton(onClick = onShare, modifier = Modifier.size(32.dp)) {
            Icon(Icons.Default.IosShare, contentDescription = "分享", modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun LedgerStatsModeTabs(
    selected: LedgerStatsMode,
    onSelected: (LedgerStatsMode) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        listOf(
            LedgerStatsMode.WEEK to "周",
            LedgerStatsMode.MONTH to "月",
            LedgerStatsMode.YEAR to "年",
            LedgerStatsMode.TOTAL to "总",
            LedgerStatsMode.CUSTOM to "自定义",
        ).forEach { (mode, title) ->
            LedgerSegmentChip(
                text = title,
                selected = mode == selected,
                onClick = { onSelected(mode) },
            )
        }
    }
}

@Composable
private fun StatsSummaryCard(
    uiState: LedgerUiState,
    onShiftPeriod: (Int) -> Unit,
    onBookClick: () -> Unit,
) {
    Surface(color = Color.White, shape = RoundedCornerShape(26.dp), modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (uiState.selectedStatsMode == LedgerStatsMode.WEEK || uiState.selectedStatsMode == LedgerStatsMode.MONTH || uiState.selectedStatsMode == LedgerStatsMode.YEAR) {
                    Icon(Icons.Default.ChevronLeft, contentDescription = "上一周期", modifier = Modifier.size(18.dp).clickable { onShiftPeriod(-1) })
                    Spacer(Modifier.width(6.dp))
                }
                Text(
                    text = ledgerStatsCurrentLabel(uiState),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                if (uiState.selectedStatsMode == LedgerStatsMode.WEEK || uiState.selectedStatsMode == LedgerStatsMode.MONTH || uiState.selectedStatsMode == LedgerStatsMode.YEAR) {
                    Spacer(Modifier.width(6.dp))
                    Icon(Icons.Default.ChevronRight, contentDescription = "下一周期", modifier = Modifier.size(18.dp).clickable { onShiftPeriod(1) })
                }
                Spacer(Modifier.weight(1f))
                Row(
                    modifier = Modifier.clickable(onClick = onBookClick),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    LedgerBookTitleWithCreator(
                        title = uiState.bookName,
                        creatorUserId = uiState.bookCreatorUserId,
                        textStyle = MaterialTheme.typography.bodyLarge,
                        textColor = Color.Unspecified,
                        fontWeight = FontWeight.Bold,
                        avatarSize = 16.dp,
                    )
                    Icon(Icons.Default.ArrowDropDown, contentDescription = "切换账本", modifier = Modifier.size(16.dp))
                }
            }
            Row(modifier = Modifier.fillMaxWidth()) {
                StatsSummaryMetric("月支出", formatAmountValue(uiState.stats.expenseCents), Modifier.weight(1f))
                StatsSummaryMetric("月收入", formatAmountValue(uiState.stats.incomeCents), Modifier.weight(1f))
                StatsSummaryMetric("月结余", formatAmountValue(uiState.stats.balanceCents), Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun StatsSummaryMetric(
    title: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, color = LedgerSubtleText, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun StatsTrendCard(
    uiState: LedgerUiState,
    selectedLineKeys: Set<String>,
    onToggleKey: (String) -> Unit,
) {
    Surface(color = Color.White, shape = RoundedCornerShape(26.dp), modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("收支统计", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                StatsToggleChip("支出", "expense", selectedLineKeys.contains("expense"), LedgerExpenseRed, onToggleKey)
                Spacer(Modifier.width(6.dp))
                StatsToggleChip("收入", "income", selectedLineKeys.contains("income"), LedgerHeaderGreen, onToggleKey)
                Spacer(Modifier.width(6.dp))
                StatsToggleChip("结余", "balance", selectedLineKeys.contains("balance"), Color(0xFF7E8E88), onToggleKey)
            }
            LedgerTrendChart(
                dailyStats = uiState.stats.dailyStats,
                selectedLineKeys = selectedLineKeys,
            )
        }
    }
}

@Composable
private fun StatsToggleChip(
    title: String,
    key: String,
    selected: Boolean,
    color: Color,
    onToggleKey: (String) -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = if (selected) color.copy(alpha = 0.14f) else Color(0xFFF5F5F7),
        modifier = Modifier.clickable { onToggleKey(key) },
    ) {
        Box(modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)) {
            Text(
                title,
                color = if (selected) color else LedgerMuted,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun LedgerTrendChart(
    dailyStats: List<LedgerDailyStat>,
    selectedLineKeys: Set<String>,
) {
    val displayStats = dailyStats.ifEmpty { listOf(LedgerDailyStat(0L, 0L, 0L)) }
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(210.dp),
    ) {
        val maxValue = displayStats.maxOf {
            maxOf(it.expenseCents, it.incomeCents, kotlin.math.abs(it.incomeCents - it.expenseCents), 1L)
        }.toFloat()
        val graphLeft = 54.dp.toPx()
        val graphRight = size.width - 12.dp.toPx()
        val graphTop = 16.dp.toPx()
        val graphBottom = size.height - 28.dp.toPx()
        val graphHeight = graphBottom - graphTop
        val zeroY = graphTop + graphHeight / 2f
        val graphWidth = graphRight - graphLeft
        repeat(5) { index ->
            val y = graphTop + graphHeight / 4f * index
            drawLine(
                color = Color(0xFFF1F1F4),
                start = Offset(graphLeft, y),
                end = Offset(graphRight, y),
                strokeWidth = if (kotlin.math.abs(y - zeroY) < 1f) 2.4f else 1.4f,
            )
        }
        drawLine(
            color = Color(0xFFD9DCE3),
            start = Offset(graphLeft, graphTop),
            end = Offset(graphLeft, graphBottom),
            strokeWidth = 2.4f,
        )
        drawLine(
            color = Color(0xFFD9DCE3),
            start = Offset(graphLeft, zeroY),
            end = Offset(graphRight, zeroY),
            strokeWidth = 2.4f,
        )
        fun xFor(index: Int): Float {
            if (displayStats.size == 1) return graphLeft + graphWidth / 2f
            return graphLeft + graphWidth / (displayStats.size - 1) * index
        }
        fun yFor(value: Long): Float = zeroY - (value / maxValue) * (graphHeight / 2f)
        fun drawSeries(values: List<Long>, color: Color) {
            if (values.size < 2) return
            val path = Path()
            values.forEachIndexed { index, value ->
                val x = xFor(index)
                val y = yFor(value)
                if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            drawPath(path = path, color = color, style = Stroke(width = 5f, cap = StrokeCap.Round))
        }
        if (selectedLineKeys.contains("expense")) drawSeries(displayStats.map { it.expenseCents }, LedgerExpenseRed)
        if (selectedLineKeys.contains("income")) drawSeries(displayStats.map { it.incomeCents }, LedgerHeaderGreen)
        if (selectedLineKeys.contains("balance")) drawSeries(displayStats.map { it.incomeCents - it.expenseCents }, Color(0xFF7E8E88))

        val textPaint = android.graphics.Paint().apply {
            isAntiAlias = true
            textAlign = android.graphics.Paint.Align.CENTER
            textSize = 11.sp.toPx()
            color = android.graphics.Color.parseColor("#B3B6BE")
        }
        drawContext.canvas.nativeCanvas.apply {
            drawText("0", graphLeft - 10.dp.toPx(), zeroY + textPaint.textSize / 3f, textPaint)
            drawText(formatAmountValue(maxValue.toLong()), graphLeft - 2.dp.toPx(), graphTop + 10.dp.toPx(), textPaint)
            drawText("-${formatAmountValue(maxValue.toLong())}", graphLeft - 2.dp.toPx(), graphBottom - 4.dp.toPx(), textPaint)
        }
        val labelStep = kotlin.math.max(1, displayStats.size / 6)
        displayStats.forEachIndexed { index, stat ->
            if (index % labelStep != 0 && index != displayStats.lastIndex) return@forEachIndexed
            val x = xFor(index)
            val date = Instant.ofEpochMilli(stat.dayStartMillis).atZone(ZoneId.systemDefault()).toLocalDate()
            val label = "%02d".format(date.dayOfMonth)
            drawContext.canvas.nativeCanvas.drawText(label, x, graphBottom + 18.dp.toPx(), textPaint)
        }
    }
}

@Composable
private fun StatsCategoryCard(
    uiState: LedgerUiState,
    categoryType: LedgerCategoryType,
    categoryStats: List<LedgerCategoryStat>,
    onCategoryTypeChange: (LedgerCategoryType) -> Unit,
    onCategoryClick: (LedgerCategoryStat) -> Unit,
) {
    Surface(color = Color.White, shape = RoundedCornerShape(26.dp), modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("分类统计", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                LedgerSegmentChip(
                    text = "支出",
                    selected = categoryType == LedgerCategoryType.EXPENSE,
                    onClick = { onCategoryTypeChange(LedgerCategoryType.EXPENSE) },
                )
                Spacer(Modifier.width(6.dp))
                LedgerSegmentChip(
                    text = "收入",
                    selected = categoryType == LedgerCategoryType.INCOME,
                    onClick = { onCategoryTypeChange(LedgerCategoryType.INCOME) },
                )
            }
            if (categoryStats.isEmpty()) {
                LedgerEmptyStateCompact("暂无分类数据")
            } else {
                LedgerDonutChart(
                    categoryStats = categoryStats,
                    uiState = uiState,
                    categoryType = categoryType,
                    onCategoryTypeChange = onCategoryTypeChange,
                )
                categoryStats.forEach { stat ->
                    LedgerCategoryStatRow(stat = stat, onClick = { onCategoryClick(stat) })
                }
            }
        }
    }
}

@Composable
private fun LedgerDonutChart(
    categoryStats: List<LedgerCategoryStat>,
    uiState: LedgerUiState,
    categoryType: LedgerCategoryType,
    onCategoryTypeChange: (LedgerCategoryType) -> Unit,
) {
    var rotation by rememberSaveable { mutableStateOf(-110f) }
    var dragAngle by remember { mutableStateOf<Float?>(null) }
    val total = categoryStats.sumOf { it.amountCents }.coerceAtLeast(1L).toFloat()
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(306.dp),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(
            modifier = Modifier
                .size(300.dp)
                .pointerInput(categoryStats) {
                    val center = Offset(150.dp.toPx(), 150.dp.toPx())
                    detectDragGestures(
                        onDragStart = { offset ->
                            dragAngle = pointerAngleDegrees(offset, center)
                        },
                        onDragCancel = { dragAngle = null },
                        onDragEnd = { dragAngle = null },
                    ) { change, _ ->
                        val currentAngle = pointerAngleDegrees(change.position, center)
                        dragAngle?.let { previousAngle ->
                            rotation += shortestAngleDelta(currentAngle, previousAngle)
                        }
                        dragAngle = currentAngle
                        change.consume()
                    }
                },
        ) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val arcDiameter = 178.dp.toPx()
            val stroke = 62.dp.toPx()
            val arcTopLeft = Offset(center.x - arcDiameter / 2f, center.y - arcDiameter / 2f)
            val outerRadius = arcDiameter / 2f + stroke / 2f
            val labelLineLength = 16.dp.toPx()
            val labelOffset = 18.dp.toPx()
            val textPaint = android.graphics.Paint().apply {
                isAntiAlias = true
                textSize = 10.sp.toPx()
                color = android.graphics.Color.parseColor("#426E70")
                typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
            }
            var startAngle = rotation
            categoryStats.forEach { stat ->
                val sweep = stat.amountCents / total * 360f
                val color = ledgerColor(stat.category?.color ?: 0xFF8D99A6)
                drawArc(
                    color = color,
                    startAngle = startAngle,
                    sweepAngle = sweep,
                    useCenter = false,
                    topLeft = arcTopLeft,
                    size = Size(arcDiameter, arcDiameter),
                    style = Stroke(width = stroke, cap = StrokeCap.Butt),
                )
                val midAngle = Math.toRadians((startAngle + sweep / 2f).toDouble())
                val directionX = cos(midAngle).toFloat()
                val directionY = sin(midAngle).toFloat()
                val lineStart = Offset(
                    x = center.x + directionX * (outerRadius - 3.dp.toPx()),
                    y = center.y + directionY * (outerRadius - 3.dp.toPx()),
                )
                val lineBend = Offset(
                    x = center.x + directionX * (outerRadius + labelLineLength),
                    y = center.y + directionY * (outerRadius + labelLineLength),
                )
                val labelSide = if (directionX >= 0f) 1f else -1f
                val labelX = (lineBend.x + labelSide * labelOffset)
                    .coerceIn(18.dp.toPx(), size.width - 18.dp.toPx())
                val labelEnd = Offset(labelX - labelSide * 4.dp.toPx(), lineBend.y)
                drawLine(
                    color = color.copy(alpha = 0.72f),
                    start = lineStart,
                    end = lineBend,
                    strokeWidth = 1.5.dp.toPx(),
                    cap = StrokeCap.Round,
                )
                drawLine(
                    color = color.copy(alpha = 0.72f),
                    start = lineBend,
                    end = labelEnd,
                    strokeWidth = 1.5.dp.toPx(),
                    cap = StrokeCap.Round,
                )
                drawContext.canvas.nativeCanvas.apply {
                    textPaint.textAlign = if (labelSide > 0f) {
                        android.graphics.Paint.Align.LEFT
                    } else {
                        android.graphics.Paint.Align.RIGHT
                    }
                    drawText(
                        "${String.format("%.1f", stat.percent * 100)}%",
                        labelX,
                        lineBend.y + 4.dp.toPx(),
                        textPaint,
                    )
                }
                startAngle += sweep
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                if (categoryType == LedgerCategoryType.EXPENSE) "总支出" else "总收入",
                color = LedgerHeaderGreen,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Bold,
            )
            Text(
                if (categoryType == LedgerCategoryType.EXPENSE) formatAmountValue(uiState.stats.expenseCents) else formatAmountValue(uiState.stats.incomeCents),
                color = LedgerHeaderGreen,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
            )
            Surface(
                shape = CircleShape,
                color = if (categoryType == LedgerCategoryType.EXPENSE) LedgerHeaderGreen else LedgerExpenseRed,
                modifier = Modifier.clickable {
                    onCategoryTypeChange(
                        if (categoryType == LedgerCategoryType.EXPENSE) LedgerCategoryType.INCOME else LedgerCategoryType.EXPENSE,
                    )
                },
            ) {
                Box(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Default.SwapHoriz,
                        contentDescription = "切换统计类型",
                        tint = Color.White,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun LedgerCategoryStatRow(
    stat: LedgerCategoryStat,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        color = Color.White,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(ledgerColor(stat.category?.color ?: 0xFF8D99A6)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(ledgerIcon(stat.category?.iconKey ?: "more_horiz"), contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                }
                Text(stat.category?.name ?: "未分类", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                Text("${stat.count}笔", color = LedgerSubtleText, style = MaterialTheme.typography.bodySmall)
                Text("${String.format("%.2f", stat.percent * 100)}%", color = LedgerSubtleText, style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.weight(1f))
                Text("-${formatAmountValue(stat.amountCents)}", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
            }
            LinearProgressIndicator(
                progress = { stat.percent.coerceIn(0f, 1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp),
                color = ledgerColor(stat.category?.color ?: 0xFF8D99A6),
                trackColor = Color(0xFFF6F6F8),
            )
        }
    }
}

@Composable
private fun StatsCompareCard(uiState: LedgerUiState) {
    val thisMonth = uiState.stats.expenseCents
    val lastMonth = ledgerComparablePreviousExpenseCents(uiState)
    val currentLabel = ledgerStatsCurrentShortLabel(uiState)
    val previousLabel = ledgerStatsPreviousShortLabel(uiState)
    val delta = lastMonth?.let { thisMonth - it }
    Surface(color = Color.White, shape = RoundedCornerShape(26.dp), modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                if (delta != null && previousLabel != null) {
                    "$currentLabel 较$previousLabel 支出${if (delta <= 0) "减少" else "增加"}${formatAmountValue(kotlin.math.abs(delta))}"
                } else {
                    "全部时间暂无可比周期"
                },
                color = LedgerMuted,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold,
            )
            Text(formatAmountValue(thisMonth), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            LedgerTag(currentLabel, LedgerHeaderGreen)
            Text(lastMonth?.let(::formatAmountValue) ?: "--", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            LedgerTag(previousLabel ?: "无可比", Color(0xFFF3F4FA))
        }
    }
}

@Composable
private fun LedgerTag(text: String, backgroundColor: Color) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = backgroundColor,
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            color = if (backgroundColor == LedgerHeaderGreen) Color.White else Color.Black,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun StatsReportCard(uiState: LedgerUiState) {
    Surface(color = Color.White, shape = RoundedCornerShape(26.dp), modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("报表统计", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Row(modifier = Modifier.fillMaxWidth()) {
                ReportHeader("时间", Modifier.weight(1f))
                ReportHeader("收入", Modifier.weight(1f))
                ReportHeader("支出", Modifier.weight(1f))
                ReportHeader("结余", Modifier.weight(1f))
            }
            ReportRow("总计", uiState.stats.incomeCents, uiState.stats.expenseCents, uiState.stats.balanceCents)
            uiState.stats.dailyStats.sortedByDescending { it.dayStartMillis }.forEach { stat ->
                ReportRow(
                    label = formatReportDate(stat.dayStartMillis),
                    income = stat.incomeCents,
                    expense = stat.expenseCents,
                    balance = stat.incomeCents - stat.expenseCents,
                )
            }
        }
    }
}

@Composable
private fun ReportHeader(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        modifier = modifier,
        color = LedgerSubtleText,
        style = MaterialTheme.typography.bodySmall,
        fontWeight = FontWeight.SemiBold,
    )
}

@Composable
private fun ReportRow(label: String, income: Long, expense: Long, balance: Long) {
    Row(modifier = Modifier.fillMaxWidth()) {
        ReportCell(label, Modifier.weight(1f), bold = true)
        ReportCell(formatAmountValue(income), Modifier.weight(1f), bold = true)
        ReportCell(formatAmountValue(expense), Modifier.weight(1f), bold = true)
        ReportCell(if (balance < 0) "-${formatAmountValue(-balance)}" else formatAmountValue(balance), Modifier.weight(1f), bold = true)
    }
}

@Composable
private fun ReportCell(text: String, modifier: Modifier = Modifier, bold: Boolean = false) {
    Text(
        text = text,
        modifier = modifier.padding(vertical = 6.dp),
        style = MaterialTheme.typography.bodySmall,
        fontWeight = if (bold) FontWeight.Bold else FontWeight.SemiBold,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LedgerCategoryDetailSheet(
    uiState: LedgerUiState,
    stat: LedgerCategoryStat,
    onDismiss: () -> Unit,
    onTransactionClick: (LedgerTransaction) -> Unit,
) {
    LedgerTransactionsDetailSheet(
        title = "账单明细",
        transactions = uiState.stats.transactions.filter { it.category?.id == stat.category?.id },
        currencySymbol = uiState.currencySymbol,
        onDismiss = onDismiss,
        onTransactionClick = onTransactionClick,
    )
}

@Composable
fun LedgerBudgetScreen(
    uiState: LedgerUiState,
    onBack: () -> Unit,
    onSelectBook: (String) -> Unit,
    onSelectPeriod: (LedgerBudgetPeriod) -> Unit,
    onSetBudget: (Long) -> Unit,
    onSetCategoryBudget: (String, Long) -> Unit,
    onClearBudget: () -> Unit,
    onClearCategoryBudget: (String) -> Unit,
) {
    var showBookSheet by rememberSaveable { mutableStateOf(false) }
    var showBudgetDialog by remember { mutableStateOf(false) }
    var categoryBudgetTarget by remember { mutableStateOf<String?>(null) }
    val budgetCategories = remember(uiState.categories, uiState.categoryBudgets) {
        val visible = uiState.categories.filter { it.type == LedgerCategoryType.EXPENSE }
        val hiddenBudgetCategories = uiState.categoryBudgets
            .mapNotNull { it.category }
            .filter { category -> visible.none { it.id == category.id } }
        (visible + hiddenBudgetCategories).distinctBy { it.id }
    }

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
                Icon(Icons.Default.ArrowBack, contentDescription = "返回", modifier = Modifier.size(18.dp))
            }
            Row(
                modifier = Modifier
                    .weight(1f)
                    .clickable { showBookSheet = true },
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                LedgerBookTitleWithCreator(
                    title = uiState.bookName,
                    creatorUserId = uiState.bookCreatorUserId,
                    textStyle = MaterialTheme.typography.titleMedium,
                    textColor = Color.Unspecified,
                    fontWeight = FontWeight.Bold,
                    avatarSize = 16.dp,
                )
                Icon(Icons.Default.ArrowDropDown, contentDescription = "切换账本", modifier = Modifier.size(14.dp))
            }
            Box(modifier = Modifier.width(32.dp))
        }
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .navigationBarsPadding(),
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    LedgerBudgetPeriod.entries.forEach { period ->
                        val label = when (period) {
                            LedgerBudgetPeriod.WEEK -> "周"
                            LedgerBudgetPeriod.MONTH -> "月"
                            LedgerBudgetPeriod.QUARTER -> "季"
                            LedgerBudgetPeriod.YEAR -> "年"
                        }
                        LedgerSegmentChip(
                            text = label,
                            selected = uiState.selectedBudgetPeriod == period,
                            modifier = Modifier.weight(1f),
                            onClick = { onSelectPeriod(period) },
                        )
                    }
                }
            }
            item {
                Surface(color = Color.White, shape = RoundedCornerShape(26.dp), modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("总预算", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                Text(
                                    uiState.budget?.totalAmountCents?.let(::formatAmountValue) ?: "点击设置预算金额",
                                    color = LedgerMuted,
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                            LedgerDialogActionButton(
                                text = "设置",
                                onClick = { showBudgetDialog = true },
                                emphasized = true,
                            )
                            if (uiState.budget != null) {
                                LedgerDialogActionButton(text = "清空", onClick = onClearBudget, danger = true)
                            }
                        }
                        val progress = if ((uiState.budget?.totalAmountCents ?: 0) > 0) {
                            uiState.totalBudgetUsedCents.toFloat() / uiState.budget!!.totalAmountCents
                        } else 0f
                        LinearProgressIndicator(
                            progress = { progress.coerceIn(0f, 1f) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(5.dp),
                            color = if (uiState.totalBudgetRemainingCents < 0) LedgerExpenseRed else LedgerHeaderGreen,
                            trackColor = Color(0xFFF4F5F7),
                        )
                        Text(
                            "已用 ${formatAmountValue(uiState.totalBudgetUsedCents)} · 剩余 ${formatAmountValue(uiState.totalBudgetRemainingCents)}",
                            color = if (uiState.totalBudgetRemainingCents < 0) LedgerExpenseRed else LedgerMuted,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
            items(budgetCategories, key = { it.id }) { category ->
                val categoryBudget = uiState.categoryBudgets.firstOrNull { it.category?.id == category.id }
                Surface(
                    color = Color.White,
                    shape = RoundedCornerShape(22.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { categoryBudgetTarget = category.id },
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Row(
                                modifier = Modifier.weight(1f),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Text(category.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                                if (category.hidden) {
                                    LedgerHiddenBadge()
                                }
                            }
                            Text(categoryBudget?.amountCents?.let(::formatAmountValue) ?: "未设置", color = LedgerMuted, style = MaterialTheme.typography.bodySmall)
                            if (categoryBudget != null) {
                                LedgerDialogActionButton(
                                    text = "清空",
                                    onClick = { onClearCategoryBudget(category.id) },
                                    danger = true,
                                )
                            }
                        }
                        val progress = if ((categoryBudget?.amountCents ?: 0L) > 0L) {
                            (categoryBudget?.usedCents ?: 0L).toFloat() / categoryBudget!!.amountCents
                        } else 0f
                        LinearProgressIndicator(
                            progress = { progress.coerceIn(0f, 1f) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(3.dp),
                            color = if (progress > 1f) LedgerExpenseRed else LedgerHeaderGreen,
                            trackColor = Color(0xFFF4F5F7),
                        )
                        Text(
                            "已用 ${formatAmountValue(categoryBudget?.usedCents ?: 0L)} · ${categoryBudget?.transactionCount ?: 0}笔",
                            color = LedgerMuted,
                            style = MaterialTheme.typography.bodySmall,
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
        )
    }
    if (showBudgetDialog) {
        AmountDialog(
            title = "设置总预算",
            onDismiss = { showBudgetDialog = false },
            onConfirm = {
                onSetBudget(it)
                showBudgetDialog = false
            },
        )
    }
    categoryBudgetTarget?.let { categoryId ->
        AmountDialog(
            title = "设置分类预算",
            onDismiss = { categoryBudgetTarget = null },
            onConfirm = {
                onSetCategoryBudget(categoryId, it)
                categoryBudgetTarget = null
            },
        )
    }
}

@Composable
private fun AmountDialog(title: String, onDismiss: () -> Unit, onConfirm: (Long) -> Unit) {
    var value by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { value = it.filter { char -> char.isDigit() || char == '.' } },
                label = { Text("金额") },
                singleLine = true,
            )
        },
        confirmButton = {
            LedgerDialogActionButton(
                text = "确定",
                onClick = { value.toCentsOrNull()?.let(onConfirm) },
                emphasized = true,
            )
        },
        dismissButton = {
            LedgerDialogActionButton(text = "取消", onClick = onDismiss)
        },
    )
}

@Composable
fun LedgerCalendarScreen(
    uiState: LedgerUiState,
    onBack: () -> Unit,
    onSelectDate: (LocalDate) -> Unit,
    onSelectMonth: (YearMonth) -> Unit,
    onAdd: () -> Unit,
    onEditTransaction: (LedgerTransaction) -> Unit,
) {
    val calendarCells = remember(uiState.selectedMonth) {
        ledgerCalendarCells(uiState.selectedMonth)
    }
    val selectedDayStart = remember(uiState.selectedDate) {
        uiState.selectedDate.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
    }
    val selectedTransactions = remember(uiState.transactions, selectedDayStart) {
        uiState.transactions.filter { dayStart(it.occurredAtMillis) == selectedDayStart }
    }
    LedgerPageScaffold(title = uiState.bookName, creatorUserId = uiState.bookCreatorUserId, onBack = onBack, action = {
        IconButton(onClick = onAdd, modifier = Modifier.size(32.dp)) {
            Icon(LedgerActionIcons.Add, contentDescription = "补记一笔", tint = LedgerHeaderGreen, modifier = Modifier.size(18.dp))
        }
    }) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .navigationBarsPadding(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                ) {
                    IconButton(onClick = { onSelectMonth(uiState.selectedMonth.minusMonths(1)) }, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Default.ChevronLeft, contentDescription = "上个月", tint = LedgerHeaderGreen)
                    }
                    Text(
                        text = formatYearMonth(uiState.selectedMonth),
                        modifier = Modifier.padding(horizontal = 18.dp),
                        color = LedgerHeaderGreen,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    IconButton(onClick = { onSelectMonth(uiState.selectedMonth.plusMonths(1)) }, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Default.ChevronRight, contentDescription = "下个月", tint = LedgerHeaderGreen)
                    }
                }
            }

            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = LedgerRaisedSurface,
                    shape = RoundedCornerShape(18.dp),
                    border = BorderStroke(1.dp, LedgerDivider.copy(alpha = 0.72f)),
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Row(modifier = Modifier.fillMaxWidth()) {
                            listOf("一", "二", "三", "四", "五", "六", "日").forEach { weekLabel ->
                                Text(
                                    text = weekLabel,
                                    modifier = Modifier.weight(1f),
                                    textAlign = TextAlign.Center,
                                    color = LedgerSubtleText,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                        }
                        calendarCells.chunked(7).forEach { week ->
                            Row(modifier = Modifier.fillMaxWidth()) {
                                week.forEach { cell ->
                                    val dayTransactions = uiState.transactions.filter { dayStart(it.occurredAtMillis) == cell.dayStartMillis }
                                    LedgerCalendarDayCell(
                                        cell = cell,
                                        selected = cell.date == uiState.selectedDate,
                                        incomeCents = dayTransactions.filter { it.type == LedgerTransactionType.INCOME }.sumOf { it.amountCents },
                                        expenseCents = dayTransactions.filter { it.type == LedgerTransactionType.EXPENSE }.sumOf { it.amountCents },
                                        onClick = {
                                            onSelectDate(cell.date)
                                            if (!cell.inCurrentMonth) {
                                                onSelectMonth(YearMonth.from(cell.date))
                                            }
                                        },
                                        modifier = Modifier.weight(1f),
                                    )
                                }
                            }
                        }
                    }
                }
            }

            item {
                LedgerCalendarSelectedDayCard(
                    date = uiState.selectedDate,
                    transactions = selectedTransactions,
                    currencySymbol = uiState.currencySymbol,
                    onAdd = onAdd,
                    onEditTransaction = onEditTransaction,
                )
            }

            item {
                LedgerSegmentChip(
                    text = "为选中日期补记一笔",
                    selected = true,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = onAdd,
                )
            }
        }
    }
}

@Composable
private fun LedgerCalendarDayCell(
    cell: LedgerCalendarCell,
    selected: Boolean,
    incomeCents: Long,
    expenseCents: Long,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val hasIncome = incomeCents > 0
    val hasExpense = expenseCents > 0
    val contentAlpha = if (cell.inCurrentMonth) 1f else 0.34f
    Surface(
        modifier = modifier
            .height(76.dp)
            .padding(2.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
        color = if (selected) LedgerGlowWash else Color.Transparent,
        shape = RoundedCornerShape(12.dp),
        border = if (selected) BorderStroke(1.dp, LedgerHeaderGreen.copy(alpha = 0.72f)) else null,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .alpha(contentAlpha)
                .padding(horizontal = 2.dp, vertical = 7.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Text(
                text = if (cell.isToday) "今" else cell.date.dayOfMonth.toString(),
                color = if (selected) LedgerHeaderGreen else MaterialTheme.colorScheme.onBackground,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
            )
            if (hasExpense) {
                Text(
                    text = "-${formatAmountValue(expenseCents)}",
                    color = LedgerExpenseRed,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    softWrap = false,
                )
            }
            if (hasIncome) {
                Text(
                    text = "+${formatAmountValue(incomeCents)}",
                    color = LedgerIncomeGreen,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    softWrap = false,
                )
            }
        }
    }
}

@Composable
private fun LedgerCalendarSelectedDayCard(
    date: LocalDate,
    transactions: List<LedgerTransaction>,
    currencySymbol: String,
    onAdd: () -> Unit,
    onEditTransaction: (LedgerTransaction) -> Unit,
) {
    val income = transactions.filter { it.type == LedgerTransactionType.INCOME }.sumOf { it.amountCents }
    val expense = transactions.filter { it.type == LedgerTransactionType.EXPENSE }.sumOf { it.amountCents }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = LedgerRaisedSurface,
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, LedgerDivider.copy(alpha = 0.72f)),
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "%02d/%02d".format(date.monthValue, date.dayOfMonth),
                        color = LedgerHeaderGreen,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = "收 ${formatAmountValue(income)} · 支 ${formatAmountValue(expense)}",
                        color = LedgerSubtleText,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Surface(
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .clickable(onClick = onAdd),
                    color = LedgerPrimaryAction,
                    border = BorderStroke(1.dp, LedgerGlassStroke.copy(alpha = 0.82f)),
                ) {
                    Text(
                        text = "补记",
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                        color = LedgerOnPrimaryAction,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
            if (transactions.isEmpty()) {
                Text(
                    text = "暂无内容，赶紧记一笔吧~",
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 10.dp),
                    color = LedgerSubtleText,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                )
            } else {
                transactions.take(4).forEach { transaction ->
                    LedgerTransactionListRow(
                        transaction = transaction,
                        currencySymbol = currencySymbol,
                        onClick = { onEditTransaction(transaction) },
                    )
                }
                if (transactions.size > 4) {
                    Text(
                        text = "还有 ${transactions.size - 4} 笔",
                        color = LedgerSubtleText,
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

private data class LedgerCalendarCell(
    val date: LocalDate,
    val inCurrentMonth: Boolean,
    val isToday: Boolean,
    val dayStartMillis: Long,
)

private fun ledgerCalendarCells(month: YearMonth): List<LedgerCalendarCell> {
    val firstDay = month.atDay(1)
    val start = firstDay.minusDays((firstDay.dayOfWeek.value - 1).toLong())
    val today = LocalDate.now()
    return (0 until 42).map { offset ->
        val date = start.plusDays(offset.toLong())
        LedgerCalendarCell(
            date = date,
            inCurrentMonth = YearMonth.from(date) == month,
            isToday = date == today,
            dayStartMillis = date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli(),
        )
    }
}

@Composable
fun LedgerSearchScreen(
    uiState: LedgerUiState,
    onBack: () -> Unit,
    onEditTransaction: (LedgerTransaction) -> Unit,
    onUpdateKeyword: (String) -> Unit,
    onUpdateType: (LedgerSearchTransactionType) -> Unit,
    onUpdateCategory: (String?) -> Unit,
    onUpdateAccount: (String?) -> Unit,
    onUpdateDateRange: (LocalDate?, LocalDate?) -> Unit,
    onUpdateAmountRange: (Long?, Long?) -> Unit,
    onClearFilters: () -> Unit,
    onToggleSelectionMode: () -> Unit,
    onToggleTransactionSelection: (String) -> Unit,
    onClearSelection: () -> Unit,
    onDeleteSelected: () -> Unit,
    onBatchUpdateCategory: (String) -> Unit,
    onBatchUpdateAccount: (String) -> Unit,
    onBatchUpdateTransferAccount: (LedgerTransferAccountSide, String) -> Unit,
) {
    var showTypeSheet by rememberSaveable { mutableStateOf(false) }
    var showCategorySheet by rememberSaveable { mutableStateOf(false) }
    var showAccountSheet by rememberSaveable { mutableStateOf(false) }
    var showDateRangeDialog by rememberSaveable { mutableStateOf(false) }
    var showAmountRangeDialog by rememberSaveable { mutableStateOf(false) }
    var showBatchCategorySheet by rememberSaveable { mutableStateOf(false) }
    var showBatchAccountSheet by rememberSaveable { mutableStateOf(false) }
    var showBatchTransferSideSheet by rememberSaveable { mutableStateOf(false) }
    var batchTransferSide by rememberSaveable { mutableStateOf<LedgerTransferAccountSide?>(null) }

    val selectedTransactions = remember(uiState.searchResults, uiState.selectedSearchTransactionIds) {
        uiState.searchResults.filter { it.id in uiState.selectedSearchTransactionIds }
    }
    val selectedTypes = remember(selectedTransactions) { selectedTransactions.map { it.type }.distinct() }
    val canBatchChangeCategory = selectedTransactions.isNotEmpty() && selectedTypes.size == 1 && selectedTypes.first() != LedgerTransactionType.TRANSFER
    val canBatchChangeAccount = selectedTransactions.isNotEmpty() && (
        selectedTransactions.none { it.type == LedgerTransactionType.TRANSFER } ||
            selectedTransactions.all { it.type == LedgerTransactionType.TRANSFER }
        )
    val isTransferOnly = selectedTransactions.isNotEmpty() && selectedTypes.singleOrNull() == LedgerTransactionType.TRANSFER
    val batchCategoryType = when (selectedTypes.singleOrNull()) {
        LedgerTransactionType.EXPENSE -> LedgerCategoryType.EXPENSE
        LedgerTransactionType.INCOME -> LedgerCategoryType.INCOME
        else -> null
    }
    val categoryLabel = uiState.allCategories.firstOrNull { it.id == uiState.searchFilter.categoryId }?.name ?: "全部分类"
    val accountLabel = uiState.allAccounts.firstOrNull { it.id == uiState.searchFilter.accountId }?.name ?: "全部账户"
    val dateLabel = if (uiState.searchFilter.startDate == null && uiState.searchFilter.endDate == null) {
        "日期"
    } else {
        "${uiState.searchFilter.startDate ?: "开始"} ~ ${uiState.searchFilter.endDate ?: "结束"}"
    }
    val amountLabel = if (uiState.searchFilter.minAmountCents == null && uiState.searchFilter.maxAmountCents == null) {
        "金额"
    } else {
        "${uiState.searchFilter.minAmountCents?.let(::formatAmountValue) ?: "不限"} ~ ${uiState.searchFilter.maxAmountCents?.let(::formatAmountValue) ?: "不限"}"
    }

    LedgerPageScaffold(title = "搜索账单", onBack = onBack) {
        Box(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .navigationBarsPadding(),
                contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 10.dp, bottom = if (uiState.isSearchSelectionMode && selectedTransactions.isNotEmpty()) 96.dp else 14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item {
                    OutlinedTextField(
                        value = uiState.searchFilter.keyword,
                        onValueChange = onUpdateKeyword,
                        label = { Text("关键词") },
                        placeholder = { Text("金额、备注、分类、账户") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                    )
                }
                item {
                    Surface(color = Color.White, shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                SearchFilterChip("类型:${searchTypeLabel(uiState.searchFilter.type)}", uiState.searchFilter.type != LedgerSearchTransactionType.ALL) { showTypeSheet = true }
                                SearchFilterChip("分类:$categoryLabel", uiState.searchFilter.categoryId != null) { showCategorySheet = true }
                                SearchFilterChip("账户:$accountLabel", uiState.searchFilter.accountId != null) { showAccountSheet = true }
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                SearchFilterChip(dateLabel, uiState.searchFilter.startDate != null || uiState.searchFilter.endDate != null) { showDateRangeDialog = true }
                                SearchFilterChip(amountLabel, uiState.searchFilter.minAmountCents != null || uiState.searchFilter.maxAmountCents != null) { showAmountRangeDialog = true }
                                SearchFilterChip("清空", false, onClick = onClearFilters)
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                SearchFilterChip(if (uiState.isSearchSelectionMode) "退出多选" else "多选", uiState.isSearchSelectionMode, onClick = onToggleSelectionMode)
                                Spacer(Modifier.weight(1f))
                                Text("共 ${uiState.searchResults.size} 条", color = LedgerMuted, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
                if (uiState.isSearchSelectionMode) {
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("已选 ${selectedTransactions.size} 笔", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.weight(1f))
                            LedgerDialogActionButton(text = "清空选择", onClick = onClearSelection, danger = true)
                        }
                    }
                }
                if (uiState.searchResults.isEmpty()) {
                    item { LedgerEmptyStateCompact("没有匹配的账单") }
                } else {
                    items(uiState.searchResults, key = { it.id }) { transaction ->
                        SearchTransactionRow(
                            transaction = transaction,
                            currencySymbol = uiState.currencySymbol,
                            selectionMode = uiState.isSearchSelectionMode,
                            selected = transaction.id in uiState.selectedSearchTransactionIds,
                            onClick = {
                                if (uiState.isSearchSelectionMode) {
                                    onToggleTransactionSelection(transaction.id)
                                } else {
                                    onEditTransaction(transaction)
                                }
                            },
                        )
                    }
                }
            }

            if (uiState.isSearchSelectionMode && selectedTransactions.isNotEmpty()) {
                Surface(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth(),
                    color = LedgerRaisedSurface,
                    border = BorderStroke(1.dp, LedgerDivider.copy(alpha = 0.64f)),
                    shadowElevation = 10.dp,
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text("${selectedTransactions.size}项", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                        Spacer(Modifier.weight(1f))
                        LedgerDialogActionButton(text = "删除", onClick = onDeleteSelected, danger = true)
                        LedgerDialogActionButton(
                            text = "改分类",
                            onClick = { showBatchCategorySheet = true },
                            enabled = canBatchChangeCategory,
                        )
                        LedgerDialogActionButton(
                            text = "改账户",
                            onClick = {
                                if (isTransferOnly) {
                                    batchTransferSide = null
                                    showBatchAccountSheet = false
                                    showBatchTransferSideSheet = true
                                } else {
                                    showBatchAccountSheet = true
                                }
                            },
                            enabled = canBatchChangeAccount,
                        )
                    }
                }
            }
        }
    }

    if (showTypeSheet) {
        LedgerSearchTypeSheet(
            selected = uiState.searchFilter.type,
            onDismiss = { showTypeSheet = false },
            onSelect = {
                onUpdateType(it)
                showTypeSheet = false
            },
        )
    }
    if (showCategorySheet) {
        LedgerCategoryPickerSheet(
            title = "筛选分类",
            categories = uiState.allCategories,
            selectedCategoryId = uiState.searchFilter.categoryId,
            includeAllLabel = "全部分类",
            onDismiss = { showCategorySheet = false },
            onSelect = {
                onUpdateCategory(it)
                showCategorySheet = false
            },
        )
    }
    if (showAccountSheet) {
        LedgerAccountChoiceSheet(
            title = "筛选账户",
            accounts = uiState.allAccounts,
            selectedAccountId = uiState.searchFilter.accountId,
            includeAllLabel = "全部账户",
            onDismiss = { showAccountSheet = false },
            onSelect = {
                onUpdateAccount(it)
                showAccountSheet = false
            },
        )
    }
    if (showDateRangeDialog) {
        LedgerDateRangeDialog(
            startDate = uiState.searchFilter.startDate,
            endDate = uiState.searchFilter.endDate,
            onDismiss = { showDateRangeDialog = false },
            onConfirm = { start, end ->
                onUpdateDateRange(start, end)
                showDateRangeDialog = false
            },
        )
    }
    if (showAmountRangeDialog) {
        LedgerAmountRangeDialog(
            minAmountCents = uiState.searchFilter.minAmountCents,
            maxAmountCents = uiState.searchFilter.maxAmountCents,
            onDismiss = { showAmountRangeDialog = false },
            onConfirm = { min, max ->
                onUpdateAmountRange(min, max)
                showAmountRangeDialog = false
            },
        )
    }
    if (showBatchCategorySheet && batchCategoryType != null) {
        LedgerCategoryPickerSheet(
            title = "批量改分类",
            categories = uiState.categories.filter { it.type == batchCategoryType },
            onDismiss = { showBatchCategorySheet = false },
            onSelect = {
                if (it != null) onBatchUpdateCategory(it)
                showBatchCategorySheet = false
            },
        )
    }
    if (showBatchTransferSideSheet) {
        LedgerActionSheet(
            title = "改转账账户",
            onDismiss = { showBatchTransferSideSheet = false },
            actions = listOf(
                LedgerSheetAction("改转出账户") {
                    batchTransferSide = LedgerTransferAccountSide.FROM
                    showBatchTransferSideSheet = false
                    showBatchAccountSheet = true
                },
                LedgerSheetAction("改转入账户") {
                    batchTransferSide = LedgerTransferAccountSide.TO
                    showBatchTransferSideSheet = false
                    showBatchAccountSheet = true
                },
            ),
        )
    }
    if (showBatchAccountSheet) {
        LedgerAccountChoiceSheet(
            title = when (batchTransferSide) {
                LedgerTransferAccountSide.FROM -> "批量改转出账户"
                LedgerTransferAccountSide.TO -> "批量改转入账户"
                null -> "批量改账户"
            },
            accounts = uiState.accounts,
            onDismiss = {
                showBatchAccountSheet = false
                batchTransferSide = null
            },
            onSelect = {
                if (it != null) {
                    when (batchTransferSide) {
                        null -> onBatchUpdateAccount(it)
                        else -> onBatchUpdateTransferAccount(batchTransferSide!!, it)
                    }
                }
                showBatchAccountSheet = false
                batchTransferSide = null
            },
        )
    }
}

@Composable
private fun SearchFilterChip(
    text: String,
    active: Boolean,
    onClick: () -> Unit,
) {
    LedgerSegmentChip(
        text = text,
        selected = active,
        onClick = onClick,
    )
}

@Composable
private fun SearchTransactionRow(
    transaction: LedgerTransaction,
    currencySymbol: String,
    selectionMode: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Surface(color = Color.White, shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (selectionMode) {
                Icon(
                    if (selected) Icons.Default.RadioButtonChecked else Icons.Default.RadioButtonUnchecked,
                    contentDescription = null,
                    tint = if (selected) LedgerHeaderGreen else LedgerMuted,
                    modifier = Modifier
                        .padding(start = 12.dp)
                        .clickable(onClick = onClick),
                )
            }
            Box(modifier = Modifier.weight(1f)) {
                LedgerTransactionListRow(
                    transaction = transaction,
                    currencySymbol = currencySymbol,
                    onClick = onClick,
                )
            }
        }
    }
}

@Composable
fun LedgerCategoriesScreen(
    uiState: LedgerUiState,
    onBack: () -> Unit,
    onSaveCategory: (String?, String, String, Long, LedgerCategoryType) -> Unit,
    onToggleCategoryHidden: (String, Boolean) -> Unit,
    onReorderCategories: (LedgerCategoryType, List<String>) -> Unit,
) {
    var selectedType by rememberSaveable { mutableStateOf(LedgerCategoryType.EXPENSE.name) }
    var editingCategory by remember { mutableStateOf<LedgerCategory?>(null) }
    var actionCategory by remember { mutableStateOf<LedgerCategory?>(null) }
    var showCreateCategorySheet by rememberSaveable { mutableStateOf(false) }
    val type = LedgerCategoryType.valueOf(selectedType)
    val categories = remember(uiState.allCategories, type) {
        uiState.allCategories.filter { it.type == type }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
            .statusBarsPadding(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 6.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.ArrowBack, contentDescription = "返回", modifier = Modifier.size(18.dp))
            }
            Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("分类管理", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text("长按可拖动排序", style = MaterialTheme.typography.bodySmall, color = LedgerMuted)
            }
            Box(modifier = Modifier.width(32.dp))
        }
        Row(modifier = Modifier.fillMaxWidth()) {
            CategoryManageTab("支出", selected = type == LedgerCategoryType.EXPENSE, modifier = Modifier.weight(1f)) {
                selectedType = LedgerCategoryType.EXPENSE.name
            }
            CategoryManageTab("收入", selected = type == LedgerCategoryType.INCOME, modifier = Modifier.weight(1f)) {
                selectedType = LedgerCategoryType.INCOME.name
            }
        }
        LedgerLongPressReorderList(
            items = categories,
            keyOf = { it.id },
            modifier = Modifier.weight(1f).padding(horizontal = 16.dp, vertical = 8.dp),
            onOrderCommitted = { onReorderCategories(type, it) },
            itemHeight = 66.dp,
        ) { category, isDragging ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .alpha(if (category.hidden) 0.62f else if (isDragging) 0.92f else 1f)
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(ledgerColor(category.color)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(ledgerIcon(category.iconKey), contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                }
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(category.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                    if (category.hidden) {
                        LedgerHiddenBadge()
                    }
                }
                Icon(
                    Icons.Default.MoreHoriz,
                    contentDescription = "更多",
                    modifier = Modifier
                        .size(18.dp)
                        .clickable { actionCategory = category },
                )
            }
        }
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = Color.White,
            shadowElevation = 6.dp,
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showCreateCategorySheet = true }
                    .padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("+", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(10.dp))
                Text("添加", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
            }
        }
    }

    actionCategory?.let { category ->
        LedgerActionSheet(
            title = category.name,
            onDismiss = { actionCategory = null },
            actions = listOf(
                LedgerSheetAction("编辑分类") {
                    editingCategory = category
                },
                LedgerSheetAction(if (category.hidden) "显示分类" else "隐藏分类") {
                    onToggleCategoryHidden(category.id, !category.hidden)
                },
            ),
        )
    }
    if (showCreateCategorySheet) {
        LedgerCategoryEditorSheet(
            type = type,
            onDismiss = { showCreateCategorySheet = false },
            onSave = { name, iconKey, color ->
                onSaveCategory(null, name, iconKey, color, type)
                showCreateCategorySheet = false
            },
        )
    }
    editingCategory?.let { category ->
        LedgerCategoryEditorSheet(
            type = category.type,
            initial = category,
            onDismiss = { editingCategory = null },
            onSave = { name, iconKey, color ->
                onSaveCategory(category.id, name, iconKey, color, category.type)
                editingCategory = null
            },
        )
    }
}

@Composable
private fun CategoryManageTab(
    text: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Column(
        modifier = modifier
            .clickable(onClick = onClick)
            .padding(top = 6.dp, bottom = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = text,
            color = if (selected) LedgerHeaderGreen else Color.Black,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
        )
        Box(
            modifier = Modifier
                .width(82.dp)
                .height(3.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(if (selected) LedgerHeaderGreen else Color.Transparent),
        )
    }
}

@Composable
fun LedgerTrashScreen(
    uiState: LedgerUiState,
    onBack: () -> Unit,
    onRestore: (String) -> Unit,
    onPermanentDelete: (String) -> Unit,
) {
    LedgerPageScaffold(title = "回收站", onBack = onBack) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .navigationBarsPadding()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (uiState.deletedItems.isEmpty()) {
                item { LedgerEmptyStateCompact("回收站为空") }
            } else {
                items(uiState.deletedItems, key = { it.id }) { item ->
                    Surface(color = Color.White, shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(item.title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyLarge)
                                Text("删除于 ${formatDateTime(item.deletedAtMillis)}", color = LedgerMuted, style = MaterialTheme.typography.bodySmall)
                            }
                            LedgerDialogActionButton(text = "恢复", onClick = { onRestore(item.itemId) }, emphasized = true)
                            LedgerDialogActionButton(text = "删除", onClick = { onPermanentDelete(item.itemId) }, danger = true)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LedgerEmptyStateCompact(text: String) {
    Surface(color = Color.White, shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
        Box(modifier = Modifier.padding(24.dp), contentAlignment = Alignment.Center) {
            Text(text, color = LedgerMuted, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun CustomRangeDialog(
    startDate: LocalDate,
    endDate: LocalDate,
    onDismiss: () -> Unit,
    onConfirm: (LocalDate, LocalDate) -> Unit,
) {
    var startText by rememberSaveable { mutableStateOf(startDate.toString()) }
    var endText by rememberSaveable { mutableStateOf(endDate.toString()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("自定义时间区间") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(value = startText, onValueChange = { startText = it }, label = { Text("开始日期 YYYY-MM-DD") })
                OutlinedTextField(value = endText, onValueChange = { endText = it }, label = { Text("结束日期 YYYY-MM-DD") })
            }
        },
        confirmButton = {
            LedgerDialogActionButton(
                text = "确定",
                onClick = confirm@{
                    val start = runCatching { LocalDate.parse(startText) }.getOrNull() ?: return@confirm
                    val end = runCatching { LocalDate.parse(endText) }.getOrNull() ?: return@confirm
                    onConfirm(start, end)
                },
                emphasized = true,
            )
        },
        dismissButton = {
            LedgerDialogActionButton(text = "取消", onClick = onDismiss)
        },
    )
}

private fun formatReportDate(dayStartMillis: Long): String {
    val date = Instant.ofEpochMilli(dayStartMillis).atZone(ZoneId.systemDefault()).toLocalDate()
    return "%02d/%02d".format(date.monthValue, date.dayOfMonth)
}

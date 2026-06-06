package com.example.yingshi.feature.ledger

import android.app.Application
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
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
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.yingshi.data.repository.RepositoryMode
import com.example.yingshi.data.repository.RepositoryProvider
import com.example.yingshi.feature.photos.rememberCollaboratorDirectorySnapshot
import com.example.yingshi.ui.components.YingShiNotice
import com.example.yingshi.ui.components.YingShiNoticeHost
import com.example.yingshi.ui.components.YingShiNoticeTone
import com.example.yingshi.ui.components.yingShiClickable
import com.example.yingshi.feature.ledger.data.LedgerTransaction
import com.example.yingshi.feature.ledger.data.LedgerTransactionType
import com.example.yingshi.feature.ledger.data.LedgerCategoryType
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.abs

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
    val collaboratorDirectory = rememberCollaboratorDirectorySnapshot(
        fallbackToFakeProfile = RepositoryProvider.currentMode != RepositoryMode.REAL,
    )
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    var route by rememberSaveable { mutableStateOf(LedgerRoute.HOME.name) }
    var editingTransactionId by rememberSaveable { mutableStateOf<String?>(null) }
    var draftOccurredAtMillis by rememberSaveable { mutableStateOf<Long?>(null) }
    var lastOpenHomeNonce by rememberSaveable { mutableStateOf(0) }
    var lastOpenAddNonce by rememberSaveable { mutableStateOf(0) }
    var notice by remember { mutableStateOf<YingShiNotice?>(null) }
    var noticeNonce by remember { mutableStateOf(0) }

    fun showNotice(
        message: String,
        tone: YingShiNoticeTone = YingShiNoticeTone.INFO,
    ) {
        noticeNonce += 1
        notice = YingShiNotice(message = message, tone = tone, nonce = noticeNonce)
    }

    LaunchedEffect(Unit) {
        viewModel.handleLedgerEntry()
    }
    LaunchedEffect(collaboratorDirectory.currentUser?.userId) {
        viewModel.refreshBookCreatorsFromCollaborators()
    }
    LaunchedEffect(openHomeNonce) {
        if (openHomeNonce > 0 && openHomeNonce != lastOpenHomeNonce) {
            lastOpenHomeNonce = openHomeNonce
            editingTransactionId = null
            draftOccurredAtMillis = null
            route = LedgerRoute.HOME.name
        }
    }
    LaunchedEffect(openAddNonce) {
        if (shouldOpenLedgerAdd(openAddNonce, lastOpenAddNonce)) {
            lastOpenAddNonce = openAddNonce
            editingTransactionId = null
            draftOccurredAtMillis = null
            route = LedgerRoute.ADD.name
        }
    }
    LaunchedEffect(uiState.message) {
        uiState.message?.let {
            showNotice(it)
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
            AnimatedContent(
                targetState = LedgerRoute.valueOf(route),
                transitionSpec = {
                    fadeIn(animationSpec = tween(160)) togetherWith
                        fadeOut(animationSpec = tween(110))
                },
                label = "ledgerRouteTransition",
            ) { currentRoute ->
                when (currentRoute) {
                    LedgerRoute.HOME -> LedgerHomeScreen(
                        uiState = uiState,
                        onOpenDrawer = { scope.launch { drawerState.open() } },
                        onSelectBook = viewModel::selectBook,
                        onSelectMonth = viewModel::selectMonth,
                        onNavigate = { route = it.name },
                        onCloseLedger = onCloseLedger,
                        onAdd = {
                            editingTransactionId = null
                            draftOccurredAtMillis = null
                            route = LedgerRoute.ADD.name
                        },
                        onEditTransaction = {
                            editingTransactionId = it.id
                            draftOccurredAtMillis = null
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
                        initialOccurredAtMillis = draftOccurredAtMillis,
                        onBack = { route = LedgerRoute.HOME.name },
                        onSelectBook = viewModel::selectBook,
                        onSaveCategory = viewModel::saveCategory,
                        onToggleCategoryHidden = viewModel::setCategoryHidden,
                        onReorderCategories = viewModel::reorderCategories,
                        onSave = { transactionId, type, amount, categoryId, accountId, toAccountId, occurredAt, remark, keepOpen ->
                            if (!keepOpen) {
                                route = LedgerRoute.HOME.name
                                editingTransactionId = null
                                draftOccurredAtMillis = null
                            }
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
                                    if (!keepOpen) draftOccurredAtMillis = null
                                },
                            )
                        },
                        onDelete = { transactionId ->
                            viewModel.deleteTransaction(transactionId)
                            editingTransactionId = null
                            draftOccurredAtMillis = null
                            route = LedgerRoute.HOME.name
                        },
                    )

                    LedgerRoute.ASSETS -> LedgerAssetsScreen(
                        uiState = uiState,
                        onBack = { route = LedgerRoute.HOME.name },
                        onEditTransaction = {
                            editingTransactionId = it.id
                            draftOccurredAtMillis = null
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
                            draftOccurredAtMillis = null
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

                    LedgerRoute.IMPORT -> LedgerImportScreen(
                        uiState = uiState,
                        onBack = { route = LedgerRoute.HOME.name },
                        onImport = viewModel::importTransactions,
                        exportTextProvider = viewModel::exportCurrentBookTransactionsText,
                        onExportCopied = {
                            showNotice("已复制当前账本数据", YingShiNoticeTone.SUCCESS)
                        },
                    )

                    LedgerRoute.CALENDAR -> LedgerCalendarScreen(
                        uiState = uiState,
                        onBack = { route = LedgerRoute.HOME.name },
                        onSelectDate = viewModel::selectDate,
                        onSelectMonth = viewModel::selectMonth,
                        onAdd = {
                            editingTransactionId = null
                            draftOccurredAtMillis = selectedDateAtCurrentTime(uiState)
                            route = LedgerRoute.ADD.name
                        },
                        onEditTransaction = {
                            editingTransactionId = it.id
                            draftOccurredAtMillis = null
                            route = LedgerRoute.ADD.name
                        },
                    )

                    LedgerRoute.SEARCH -> LedgerSearchScreen(
                        uiState = uiState,
                        onBack = { route = LedgerRoute.HOME.name },
                        onEditTransaction = {
                            editingTransactionId = it.id
                            draftOccurredAtMillis = null
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

            YingShiNoticeHost(
                notice = notice,
                modifier = Modifier.align(Alignment.TopCenter),
                onExpired = {
                    if (notice?.nonce == it) {
                        notice = null
                    }
                },
            )
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
    var showMoreSheet by rememberSaveable { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val density = LocalDensity.current
    val viewConfiguration = LocalViewConfiguration.current
    var monthPullDistancePx by remember { mutableFloatStateOf(0f) }
    val monthPullThresholdPx = with(density) { 204.dp.toPx() }
    val monthPullMaxPx = monthPullThresholdPx * 1.28f
    val monthPullDamping = 0.42f
    val monthPullTouchSlop = viewConfiguration.touchSlop
    val topPullSpace = with(density) {
        monthPullDistancePx.coerceAtLeast(0f).coerceAtMost(monthPullMaxPx).toDp()
    }
    val bottomPullSpace = with(density) {
        (-monthPullDistancePx).coerceAtLeast(0f).coerceAtMost(monthPullMaxPx).toDp()
    }
    val grouped = remember(uiState.transactions) {
        uiState.transactions.groupBy { dayStart(it.occurredAtMillis) }
            .toList()
            .sortedByDescending { it.first }
    }
    fun isAtListTop(): Boolean {
        return !listState.canScrollBackward || (
            listState.firstVisibleItemIndex == 0 &&
                listState.firstVisibleItemScrollOffset <= 2
            )
    }
    fun isAtListBottom(): Boolean {
        if (!listState.canScrollForward) return true
        val layoutInfo = listState.layoutInfo
        if (layoutInfo.totalItemsCount == 0) return true
        val lastVisible = layoutInfo.visibleItemsInfo.lastOrNull() ?: return false
        return lastVisible.index >= layoutInfo.totalItemsCount - 1 &&
            lastVisible.offset + lastVisible.size <= layoutInfo.viewportEndOffset + 2
    }
    fun commitMonthPull() {
        val pull = monthPullDistancePx
        monthPullDistancePx = 0f
        when {
            pull >= monthPullThresholdPx -> onSelectMonth(uiState.selectedMonth.plusMonths(1))
            pull <= -monthPullThresholdPx -> onSelectMonth(uiState.selectedMonth.minusMonths(1))
        }
    }
    LaunchedEffect(uiState.selectedMonth) {
        monthPullDistancePx = 0f
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            LedgerHomeHeader(
                uiState = uiState,
                onOpenDrawer = onOpenDrawer,
                onBookClick = { showBookSheet = true },
                onMonthClick = { showMonthSheet = true },
                onMoreClick = { showMoreSheet = true },
                onCloseLedger = onCloseLedger,
            )
            LedgerQuickActionsRow(
                uiState = uiState,
                onNavigate = onNavigate,
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .offset(y = (-14).dp),
            )
            Box(
                modifier = Modifier
                    .weight(1f)
                    .pointerInput(
                        uiState.selectedMonth,
                        monthPullThresholdPx,
                        monthPullMaxPx,
                        monthPullTouchSlop,
                    ) {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            val pointerId = down.id
                            val startPosition = down.position
                            var activeDirection = 0
                            var accumulatedDy = 0f
                            var lockedToEdge = false

                            while (true) {
                                val event = awaitPointerEvent(PointerEventPass.Initial)
                                val change = event.changes.firstOrNull { it.id == pointerId }
                                    ?: event.changes.firstOrNull()
                                    ?: continue

                                if (!change.pressed) {
                                    if (monthPullDistancePx != 0f) {
                                        commitMonthPull()
                                    }
                                    break
                                }

                                val deltaY = change.positionChange().y
                                if (deltaY == 0f && activeDirection == 0) continue

                                if (activeDirection == 0) {
                                    accumulatedDy += deltaY
                                    val totalDx = change.position.x - startPosition.x
                                    val absDy = abs(accumulatedDy)
                                    val absDx = abs(totalDx)
                                    if (absDy < monthPullTouchSlop || absDy <= absDx * 1.05f) {
                                        continue
                                    }

                                    activeDirection = when {
                                        accumulatedDy > 0f && isAtListTop() -> 1
                                        accumulatedDy < 0f && isAtListBottom() -> -1
                                        else -> 0
                                    }
                                    lockedToEdge = activeDirection != 0

                                    if (activeDirection == 0) {
                                        if (absDx > absDy * 1.1f) break
                                        continue
                                    }
                                }

                                if (!lockedToEdge) continue

                                val previous = monthPullDistancePx
                                val next = when (activeDirection) {
                                    1 -> (previous + deltaY * if (deltaY > 0f) monthPullDamping else 1f)
                                        .coerceIn(0f, monthPullMaxPx)

                                    -1 -> (previous + deltaY * if (deltaY < 0f) monthPullDamping else 1f)
                                        .coerceIn(-monthPullMaxPx, 0f)

                                    else -> previous
                                }

                                if (next != previous) {
                                    monthPullDistancePx = next
                                    event.changes.forEach { it.consume() }
                                }

                                if (next == 0f) {
                                    val inwardRelease = when (activeDirection) {
                                        1 -> deltaY < 0f
                                        -1 -> deltaY > 0f
                                        else -> false
                                    }
                                    if (inwardRelease) {
                                        activeDirection = 0
                                        lockedToEdge = false
                                        accumulatedDy = 0f
                                    }
                                }
                            }
                        }
                    }
            ) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = 16.dp,
                        end = 16.dp,
                        top = 2.dp + topPullSpace,
                        bottom = 104.dp + bottomPullSpace,
                    ),
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
                if (monthPullDistancePx > 0f) {
                    LedgerMonthPullIndicator(
                        pullDistancePx = monthPullDistancePx,
                        thresholdPx = monthPullThresholdPx,
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.TopCenter),
                    )
                }
                if (monthPullDistancePx < 0f) {
                    LedgerMonthPullIndicator(
                        pullDistancePx = monthPullDistancePx,
                        thresholdPx = monthPullThresholdPx,
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 92.dp),
                    )
                }
            }
        }

        Surface(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .navigationBarsPadding()
                .padding(end = 18.dp, bottom = 16.dp)
                .size(58.dp)
                .yingShiClickable(
                    shape = CircleShape,
                    pressedScale = 0.94f,
                    onClick = onAdd,
                ),
            shape = CircleShape,
            color = LedgerPrimaryAction,
            border = BorderStroke(1.dp, LedgerGlassStroke.copy(alpha = 0.82f)),
            shadowElevation = 2.dp,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = LedgerActionIcons.Add,
                    contentDescription = "记一笔",
                    tint = LedgerOnPrimaryAction,
                    modifier = Modifier.size(24.dp),
                )
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
    if (showMoreSheet) {
        LedgerHomeMoreSheet(
            onDismiss = { showMoreSheet = false },
            onNavigate = {
                showMoreSheet = false
                onNavigate(it)
            },
        )
    }
}

@Composable
private fun LedgerMonthPullIndicator(
    pullDistancePx: Float,
    thresholdPx: Float,
    modifier: Modifier = Modifier,
) {
    if (pullDistancePx == 0f) return
    val progress = (abs(pullDistancePx) / thresholdPx).coerceIn(0f, 1f)
    val indicatorHeight = with(LocalDensity.current) {
        (18.dp.toPx() + abs(pullDistancePx).coerceAtMost(thresholdPx * 1.12f) * 0.58f).toDp()
    }
    Box(
        modifier = modifier.height(indicatorHeight.coerceAtMost(78.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            modifier = Modifier.size((26 + 12 * progress).dp),
            shape = CircleShape,
            color = LedgerRaisedSurface.copy(alpha = 0.94f),
            border = BorderStroke(1.dp, LedgerGlassStroke.copy(alpha = 0.88f)),
            shadowElevation = if (progress >= 1f) 2.dp else 0.dp,
        ) {
            Box(contentAlignment = Alignment.Center) {
                CircularProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.size(20.dp),
                    color = if (progress >= 1f) LedgerHeaderGreen else LedgerSubtleText,
                    strokeWidth = 2.4.dp,
                    trackColor = LedgerGroupedHeader,
                )
            }
        }
    }
}

@Composable
private fun LedgerHomeHeader(
    uiState: LedgerUiState,
    onOpenDrawer: () -> Unit,
    onBookClick: () -> Unit,
    onMonthClick: () -> Unit,
    onMoreClick: () -> Unit,
    onCloseLedger: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(LedgerGroupedHeader)
            .statusBarsPadding()
            .padding(horizontal = 14.dp)
            .padding(top = 6.dp, bottom = 14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier.width(112.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                IconButton(onClick = onOpenDrawer, modifier = Modifier.size(44.dp)) {
                    Icon(LedgerActionIcons.Menu, contentDescription = "菜单", tint = LedgerHeaderGreen, modifier = Modifier.size(22.dp))
                }
            }
            Row(
                modifier = Modifier
                    .weight(1f)
                    .clickable(onClick = onBookClick),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                LedgerBookTitleWithCreator(
                    title = uiState.bookName,
                    creatorUserId = uiState.bookCreatorUserId,
                    textStyle = MaterialTheme.typography.titleMedium,
                    textColor = LedgerHeaderGreen,
                    fontWeight = FontWeight.Bold,
                    avatarSize = 18.dp,
                )
                Icon(Icons.Default.ArrowDropDown, contentDescription = "切换账本", tint = LedgerHeaderGreen, modifier = Modifier.size(16.dp))
            }
            Box(
                modifier = Modifier.width(112.dp),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Surface(
                    shape = RoundedCornerShape(22.dp),
                    color = LedgerRaisedSurface.copy(alpha = 0.88f),
                    border = BorderStroke(1.dp, LedgerGlassStroke),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        IconButton(onClick = onMoreClick, modifier = Modifier.size(44.dp)) {
                            Icon(Icons.Default.MoreHoriz, contentDescription = "更多", tint = LedgerHeaderGreen, modifier = Modifier.size(20.dp))
                        }
                        Box(
                            modifier = Modifier
                                .height(22.dp)
                                .width(1.dp)
                                .background(LedgerDivider),
                        )
                        IconButton(onClick = onCloseLedger, modifier = Modifier.size(44.dp)) { LedgerCloseCircleIcon() }
                    }
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
                    color = LedgerMuted,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = formatMonthLabel(uiState.selectedMonth),
                        color = LedgerHeaderGreen,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "月",
                        color = LedgerHeaderGreen,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Icon(Icons.Default.ArrowDropDown, contentDescription = "选择月份", tint = LedgerHeaderGreen, modifier = Modifier.size(15.dp))
                }
            }
            Box(
                modifier = Modifier
                    .padding(bottom = 4.dp)
                    .width(1.dp)
                    .height(44.dp)
                    .background(LedgerDivider),
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
            color = LedgerMuted,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = value,
            color = LedgerHeaderGreen,
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
        color = LedgerRaisedSurface,
        shape = RoundedCornerShape(24.dp),
        border = BorderStroke(1.dp, LedgerDivider.copy(alpha = 0.72f)),
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
                            .background(LedgerPrimaryAction),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            ledgerIcon(iconKey),
                            contentDescription = title,
                            tint = LedgerOnPrimaryAction,
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
        color = LedgerRaisedSurface,
        shape = RoundedCornerShape(26.dp),
        border = BorderStroke(1.dp, LedgerDivider.copy(alpha = 0.72f)),
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
                .background((transaction.category?.color?.let(::ledgerColor) ?: LedgerHeaderGreen).copy(alpha = 0.92f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = if (transaction.type == LedgerTransactionType.TRANSFER) {
                    ledgerIcon("transfer")
                } else {
                    ledgerIcon(transaction.category?.iconKey ?: "more_horiz")
                },
                contentDescription = null,
                tint = LedgerRaisedSurface,
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
            val detailText = when (transaction.type) {
                LedgerTransactionType.TRANSFER -> "${transaction.account?.name.orEmpty()} -> ${transaction.toAccount?.name.orEmpty()}"
                else -> transaction.remark
            }
            if (detailText.isNotBlank()) {
                Text(
                    text = detailText,
                    color = LedgerSubtleText,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
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
                    LedgerTransactionType.INCOME -> LedgerIncomeGreen
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
        Icon(Icons.Default.RadioButtonUnchecked, contentDescription = null, tint = LedgerHeaderGreen, modifier = Modifier.size(18.dp))
        Icon(Icons.Default.Close, contentDescription = null, tint = LedgerHeaderGreen, modifier = Modifier.size(10.dp))
    }
}

@Composable
private fun LedgerHomeMoreSheet(
    onDismiss: () -> Unit,
    onNavigate: (LedgerRoute) -> Unit,
) {
    LedgerBottomSheetDialog(onDismiss = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "更多操作",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = LedgerHeaderGreen,
            )
            LedgerMoreSheetAction("搜索账单", "search", LedgerRoute.SEARCH, onNavigate)
            LedgerMoreSheetAction("日历视图", "calendar", LedgerRoute.CALENDAR, onNavigate)
            LedgerMoreSheetAction("周期记账", "timelapse", LedgerRoute.RECURRING, onNavigate)
            LedgerMoreSheetAction("分类管理", "category", LedgerRoute.CATEGORIES, onNavigate)
            LedgerMoreSheetAction("回收站", "trash", LedgerRoute.TRASH, onNavigate, danger = true)
        }
    }
}

@Composable
private fun LedgerMoreSheetAction(
    title: String,
    iconKey: String,
    route: LedgerRoute,
    onNavigate: (LedgerRoute) -> Unit,
    danger: Boolean = false,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .clickable { onNavigate(route) },
        color = if (danger) LedgerMemoryWash else LedgerRaisedSurface,
        border = BorderStroke(1.dp, if (danger) LedgerMemoryContainer else LedgerDivider.copy(alpha = 0.72f)),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                ledgerIcon(iconKey),
                contentDescription = null,
                tint = if (danger) LedgerExpenseRed else LedgerHeaderGreen,
                modifier = Modifier.size(20.dp),
            )
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = if (danger) LedgerExpenseRed else LedgerHeaderGreen,
            )
        }
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
        color = LedgerRaisedSurface,
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
                        Icon(ledgerIcon("wallet"), contentDescription = null, tint = LedgerRaisedSurface, modifier = Modifier.size(22.dp))
                    }
                    Spacer(Modifier.width(12.dp))
                    Column {
                        LedgerBookTitleWithCreator(
                            title = uiState.bookName,
                            creatorUserId = uiState.bookCreatorUserId,
                            textStyle = MaterialTheme.typography.titleMedium,
                            textColor = Color.Unspecified,
                            fontWeight = FontWeight.Bold,
                            avatarSize = 16.dp,
                        )
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
    creatorUserId: String? = null,
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
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack, modifier = Modifier.size(44.dp)) {
                Icon(LedgerActionIcons.Back, contentDescription = "返回", modifier = Modifier.size(24.dp))
            }
            Box(
                modifier = Modifier.weight(1f),
                contentAlignment = Alignment.Center,
            ) {
                LedgerBookTitleWithCreator(
                    title = title,
                    creatorUserId = creatorUserId,
                    textStyle = MaterialTheme.typography.titleMedium,
                    textColor = Color.Unspecified,
                    fontWeight = FontWeight.Bold,
                    avatarSize = 16.dp,
                )
            }
            Box(modifier = Modifier.width(44.dp), contentAlignment = Alignment.CenterEnd) {
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
private fun LedgerImportScreen(
    uiState: LedgerUiState,
    onBack: () -> Unit,
    onImport: (LedgerImportPreview, () -> Unit) -> Unit,
    exportTextProvider: () -> String,
    onExportCopied: () -> Unit,
) {
    val clipboardManager = LocalClipboardManager.current
    var importText by rememberSaveable { mutableStateOf("") }
    val sampleText = remember(uiState.categories, uiState.accounts) {
        val expenseCategory = uiState.categories.firstOrNull { it.type == LedgerCategoryType.EXPENSE }?.name ?: "餐饮"
        val incomeCategory = uiState.categories.firstOrNull { it.type == LedgerCategoryType.INCOME }?.name ?: "工资"
        val account = uiState.accounts.firstOrNull()?.name ?: "微信"
        "$LedgerImportExportHeader\n2026-06-02,支出,18.50,$expenseCategory,$account,,午餐\n2026-06-03,收入,5200,$incomeCategory,$account,,工资"
    }
    val preview = remember(importText, uiState.currentBookId, uiState.allCategories, uiState.allAccounts) {
        buildLedgerImportPreview(
            text = importText,
            bookId = uiState.currentBookId,
            categories = uiState.allCategories,
            accounts = uiState.allAccounts,
        )
    }

    LedgerPageScaffold(
        title = "记账导入",
        onBack = onBack,
        action = {
            IconButton(
                onClick = {
                    clipboardManager.setText(AnnotatedString(exportTextProvider()))
                    onExportCopied()
                },
                modifier = Modifier.size(44.dp),
            ) {
                Icon(ledgerIcon("import"), contentDescription = "复制导出", tint = LedgerHeaderGreen, modifier = Modifier.size(18.dp))
            }
        },
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .navigationBarsPadding(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = LedgerRaisedSurface,
                    shape = RoundedCornerShape(24.dp),
                    border = BorderStroke(1.dp, LedgerDivider.copy(alpha = 0.72f)),
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(LedgerPrimaryAction),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    ledgerIcon("import"),
                                    contentDescription = null,
                                    tint = LedgerOnPrimaryAction,
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                            Spacer(Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "粘贴 CSV 或表格文本",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = LedgerHeaderGreen,
                                )
                                Text(
                                    text = "日期、类型、金额、分类、账户、转入账户、备注",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = LedgerSubtleText,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                        OutlinedTextField(
                            value = importText,
                            onValueChange = { importText = it },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(172.dp),
                            placeholder = { Text(sampleText, color = LedgerSubtleText) },
                            textStyle = MaterialTheme.typography.bodyMedium,
                            minLines = 6,
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            LedgerSegmentChip(
                                text = "粘贴示例",
                                selected = false,
                                modifier = Modifier.weight(1f),
                                onClick = { importText = sampleText },
                            )
                            LedgerSegmentChip(
                                text = "清空",
                                selected = false,
                                modifier = Modifier.weight(1f),
                                onClick = { importText = "" },
                            )
                        }
                    }
                }
            }

            item {
                LedgerImportSummaryCard(
                    preview = preview,
                    hasInput = importText.isNotBlank(),
                    onImport = {
                        onImport(preview) {
                            importText = ""
                        }
                    },
                )
            }

            if (preview.rows.isNotEmpty()) {
                item {
                    Text(
                        text = "预览",
                        style = MaterialTheme.typography.bodyMedium,
                        color = LedgerSubtleText,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(start = 2.dp, top = 4.dp),
                    )
                }
                items(preview.rows.take(24), key = { "${it.lineNumber}-${it.rawText}" }) { row ->
                    LedgerImportPreviewRowCard(row)
                }
                if (preview.rows.size > 24) {
                    item {
                        Text(
                            text = "还有 ${preview.rows.size - 24} 行会一并处理",
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.bodySmall,
                            color = LedgerSubtleText,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LedgerImportSummaryCard(
    preview: LedgerImportPreview,
    hasInput: Boolean,
    onImport: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = if (preview.invalidCount > 0) LedgerMemoryWash else LedgerGlowWash,
        shape = RoundedCornerShape(22.dp),
        border = BorderStroke(
            1.dp,
            if (preview.invalidCount > 0) LedgerMemoryContainer else LedgerGlassStroke.copy(alpha = 0.72f),
        ),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = if (hasInput) "可导入 ${preview.validCount} 笔" else "等待粘贴账单",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = LedgerHeaderGreen,
                )
                Text(
                    text = if (hasInput) {
                        if (preview.invalidCount > 0) "有 ${preview.invalidCount} 行需要修正，导入时会跳过" else "格式检查通过"
                    } else {
                        "支持英文逗号 CSV，也支持从表格复制出的制表符文本"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = LedgerSubtleText,
                )
            }
            Surface(
                modifier = Modifier
                    .clip(RoundedCornerShape(18.dp))
                    .clickable(enabled = preview.validCount > 0, onClick = onImport),
                color = if (preview.validCount > 0) LedgerPrimaryAction else LedgerDivider.copy(alpha = 0.42f),
                border = BorderStroke(1.dp, LedgerGlassStroke.copy(alpha = if (preview.validCount > 0) 0.9f else 0.3f)),
            ) {
                Text(
                    text = "导入",
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 9.dp),
                    color = if (preview.validCount > 0) LedgerOnPrimaryAction else LedgerSubtleText,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

@Composable
private fun LedgerImportPreviewRowCard(row: LedgerImportPreviewRow) {
    val valid = row.draft != null
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = if (valid) LedgerRaisedSurface else LedgerMemoryWash,
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, if (valid) LedgerDivider.copy(alpha = 0.72f) else LedgerMemoryContainer),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 13.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(if (valid) LedgerGreenSoft else LedgerMemoryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = row.lineNumber.toString(),
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold,
                    color = if (valid) LedgerHeaderGreen else LedgerExpenseRed,
                )
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    text = row.error ?: "${row.type?.toImportPreviewLabel().orEmpty()} ${row.amountCents?.let(::formatAmountValue).orEmpty()}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (valid) LedgerHeaderGreen else LedgerExpenseRed,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = listOf(row.categoryName, row.accountName, row.toAccountName, row.remark)
                        .filter { it.isNotBlank() }
                        .joinToString(" · ")
                        .ifBlank { row.rawText },
                    style = MaterialTheme.typography.bodySmall,
                    color = LedgerSubtleText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
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

private fun LedgerTransactionType.toImportPreviewLabel(): String = when (this) {
    LedgerTransactionType.EXPENSE -> "支出"
    LedgerTransactionType.INCOME -> "收入"
    LedgerTransactionType.TRANSFER -> "转账"
}

private fun selectedDateAtCurrentTime(uiState: LedgerUiState): Long {
    val now = LocalTime.now()
    return uiState.selectedDate
        .atTime(now.hour, now.minute)
        .atZone(ZoneId.systemDefault())
        .toInstant()
        .toEpochMilli()
}

internal fun formatLedgerGroupDate(dayStartMillis: Long): String {
    val date = Instant.ofEpochMilli(dayStartMillis).atZone(ZoneId.systemDefault()).toLocalDate()
    val week = date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.CHINA)
    return "%02d/%02d %s".format(date.monthValue, date.dayOfMonth, week)
}

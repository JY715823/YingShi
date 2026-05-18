package com.example.yingshi.feature.ledger

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.yingshi.feature.ledger.data.LedgerAccount
import com.example.yingshi.feature.ledger.data.LedgerAccountDraft
import com.example.yingshi.feature.ledger.data.LedgerBudget
import com.example.yingshi.feature.ledger.data.LedgerBudgetPeriod
import com.example.yingshi.feature.ledger.data.LedgerBook
import com.example.yingshi.feature.ledger.data.LedgerCategory
import com.example.yingshi.feature.ledger.data.LedgerCategoryBudget
import com.example.yingshi.feature.ledger.data.LedgerCategoryDraft
import com.example.yingshi.feature.ledger.data.LedgerCategoryType
import com.example.yingshi.feature.ledger.data.LedgerDatabase
import com.example.yingshi.feature.ledger.data.LedgerDateUtils
import com.example.yingshi.feature.ledger.data.LedgerDeletedItem
import com.example.yingshi.feature.ledger.data.LedgerPeriodStats
import com.example.yingshi.feature.ledger.data.LedgerRepository
import com.example.yingshi.feature.ledger.data.LedgerSearchFilter
import com.example.yingshi.feature.ledger.data.LedgerSearchTransactionType
import com.example.yingshi.feature.ledger.data.LedgerSeedData
import com.example.yingshi.feature.ledger.data.LedgerTransaction
import com.example.yingshi.feature.ledger.data.LedgerTransactionDraft
import com.example.yingshi.feature.ledger.data.LedgerTransactionType
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.TemporalAdjusters

enum class LedgerStatsMode {
    WEEK,
    MONTH,
    YEAR,
    TOTAL,
    CUSTOM,
}

data class LedgerUiState(
    val isLoading: Boolean = true,
    val books: List<LedgerBook> = emptyList(),
    val currentBookId: String = LedgerSeedData.DefaultBookId,
    val bookName: String = "日常账本",
    val currencySymbol: String = "¥",
    val selectedMonth: YearMonth = YearMonth.now(),
    val selectedDate: LocalDate = LocalDate.now(),
    val selectedBudgetPeriod: LedgerBudgetPeriod = LedgerBudgetPeriod.MONTH,
    val selectedStatsMode: LedgerStatsMode = LedgerStatsMode.MONTH,
    val customStatsStartDate: LocalDate = LocalDate.now().minusMonths(1),
    val customStatsEndDate: LocalDate = LocalDate.now(),
    val categories: List<LedgerCategory> = emptyList(),
    val allCategories: List<LedgerCategory> = emptyList(),
    val accounts: List<LedgerAccount> = emptyList(),
    val allAccounts: List<LedgerAccount> = emptyList(),
    val allTransactions: List<LedgerTransaction> = emptyList(),
    val transactions: List<LedgerTransaction> = emptyList(),
    val stats: LedgerPeriodStats = LedgerPeriodStats(0, 0, 0, emptyList(), emptyList(), emptyList()),
    val budget: LedgerBudget? = null,
    val categoryBudgets: List<LedgerCategoryBudget> = emptyList(),
    val deletedItems: List<LedgerDeletedItem> = emptyList(),
    val searchFilter: LedgerSearchFilter = LedgerSearchFilter(),
    val searchResults: List<LedgerTransaction> = emptyList(),
    val selectedSearchTransactionIds: Set<String> = emptySet(),
    val isSearchSelectionMode: Boolean = false,
    val message: String? = null,
) {
    val netAssetCents: Long
        get() = allAccounts.filter { it.includeInTotal && !it.hidden }.sumOf { it.balanceCents }

    val totalBudgetUsedCents: Long
        get() = stats.expenseCents

    val totalBudgetRemainingCents: Long
        get() = (budget?.totalAmountCents ?: 0L) - totalBudgetUsedCents
}

class LedgerViewModel(
    application: Application,
    private val repository: LedgerRepository = LedgerRepository(
        LedgerDatabase.getInstance(application).ledgerDao(),
    ),
) : AndroidViewModel(application) {
    private val _uiState = MutableStateFlow(LedgerUiState())
    val uiState: StateFlow<LedgerUiState> = _uiState.asStateFlow()

    private var observeJob: Job? = null
    private var searchJob: Job? = null

    init {
        viewModelScope.launch {
            repository.ensureSeedData()
            repository.ensureDemoData()
            repository.observeBooks().collectLatest { books ->
                val fallbackId = books.firstOrNull()?.id ?: LedgerSeedData.DefaultBookId
                _uiState.update { state ->
                    val selectedBookId = if (books.any { it.id == state.currentBookId }) {
                        state.currentBookId
                    } else {
                        fallbackId
                    }
                    state.copy(
                        books = books,
                        currentBookId = selectedBookId,
                    )
                }
                observe()
            }
        }
    }

    fun previousMonth() {
        _uiState.update { it.copy(selectedMonth = it.selectedMonth.minusMonths(1)) }
        observe()
    }

    fun nextMonth() {
        _uiState.update { it.copy(selectedMonth = it.selectedMonth.plusMonths(1)) }
        observe()
    }

    fun selectDate(date: LocalDate) {
        _uiState.update {
            it.copy(
                selectedDate = date,
                selectedMonth = YearMonth.from(date),
            )
        }
        observe()
    }

    fun selectBudgetPeriod(period: LedgerBudgetPeriod) {
        _uiState.update { it.copy(selectedBudgetPeriod = period) }
        observe()
    }

    fun selectBook(bookId: String) {
        if (bookId == _uiState.value.currentBookId) return
        _uiState.update {
            it.copy(
                currentBookId = bookId,
                searchFilter = it.searchFilter.copy(categoryId = null, accountId = null),
                selectedSearchTransactionIds = emptySet(),
                isSearchSelectionMode = false,
            )
        }
        observe()
    }

    fun selectMonth(month: YearMonth) {
        _uiState.update {
            it.copy(
                selectedMonth = month,
                selectedDate = month.atDay(it.selectedDate.dayOfMonth.coerceAtMost(month.lengthOfMonth())),
            )
        }
        observe()
    }

    fun selectStatsMode(mode: LedgerStatsMode) {
        _uiState.update { it.copy(selectedStatsMode = mode) }
        observe()
    }

    fun selectCustomStatsRange(startDate: LocalDate, endDate: LocalDate) {
        _uiState.update {
            it.copy(
                customStatsStartDate = startDate,
                customStatsEndDate = endDate,
            )
        }
        observe()
    }

    fun shiftStatsPeriod(step: Int) {
        _uiState.update { state ->
            when (state.selectedStatsMode) {
                LedgerStatsMode.WEEK -> state.copy(selectedDate = state.selectedDate.plusWeeks(step.toLong()))
                LedgerStatsMode.MONTH -> state.copy(
                    selectedMonth = state.selectedMonth.plusMonths(step.toLong()),
                    selectedDate = state.selectedDate.plusMonths(step.toLong()),
                )
                LedgerStatsMode.YEAR -> state.copy(selectedDate = state.selectedDate.plusYears(step.toLong()))
                LedgerStatsMode.TOTAL, LedgerStatsMode.CUSTOM -> state
            }
        }
        observe()
    }

    fun updateSearchKeyword(keyword: String) {
        updateSearchFilter { it.copy(keyword = keyword) }
    }

    fun updateSearchType(type: LedgerSearchTransactionType) {
        updateSearchFilter { it.copy(type = type) }
    }

    fun updateSearchCategory(categoryId: String?) {
        updateSearchFilter { it.copy(categoryId = categoryId) }
    }

    fun updateSearchAccount(accountId: String?) {
        updateSearchFilter { it.copy(accountId = accountId) }
    }

    fun updateSearchDateRange(startDate: LocalDate?, endDate: LocalDate?) {
        val normalized = if (startDate != null && endDate != null && startDate.isAfter(endDate)) {
            endDate to startDate
        } else {
            startDate to endDate
        }
        updateSearchFilter {
            it.copy(
                startDate = normalized.first,
                endDate = normalized.second,
            )
        }
    }

    fun updateSearchAmountRange(minAmountCents: Long?, maxAmountCents: Long?) {
        val normalized = if (minAmountCents != null && maxAmountCents != null && minAmountCents > maxAmountCents) {
            maxAmountCents to minAmountCents
        } else {
            minAmountCents to maxAmountCents
        }
        updateSearchFilter {
            it.copy(
                minAmountCents = normalized.first,
                maxAmountCents = normalized.second,
            )
        }
    }

    fun clearSearchFilters() {
        _uiState.update {
            it.copy(
                searchFilter = LedgerSearchFilter(),
                selectedSearchTransactionIds = emptySet(),
                isSearchSelectionMode = false,
            )
        }
        observeSearch(_uiState.value.searchFilter)
    }

    fun toggleSearchSelectionMode() {
        _uiState.update {
            val enabled = !it.isSearchSelectionMode
            it.copy(
                isSearchSelectionMode = enabled,
                selectedSearchTransactionIds = if (enabled) it.selectedSearchTransactionIds else emptySet(),
            )
        }
    }

    fun toggleSearchTransactionSelection(transactionId: String) {
        _uiState.update { state ->
            val selected = if (state.selectedSearchTransactionIds.contains(transactionId)) {
                state.selectedSearchTransactionIds - transactionId
            } else {
                state.selectedSearchTransactionIds + transactionId
            }
            state.copy(
                selectedSearchTransactionIds = selected,
                isSearchSelectionMode = true,
            )
        }
    }

    fun clearSearchSelection() {
        _uiState.update {
            it.copy(
                selectedSearchTransactionIds = emptySet(),
                isSearchSelectionMode = false,
            )
        }
    }

    fun saveTransaction(
        transactionId: String? = null,
        type: LedgerTransactionType,
        amountCents: Long,
        categoryId: String?,
        accountId: String,
        toAccountId: String?,
        occurredAtMillis: Long,
        remark: String,
        keepOpen: Boolean = false,
        onSaved: () -> Unit = {},
    ) {
        viewModelScope.launch {
            val validationMessage = validateDraft(type, amountCents, categoryId, accountId, toAccountId)
            if (validationMessage != null) {
                _uiState.update { it.copy(message = validationMessage) }
                return@launch
            }
            repository.saveTransaction(
                LedgerTransactionDraft(
                    id = transactionId,
                    bookId = _uiState.value.currentBookId,
                    categoryId = categoryId,
                    accountId = accountId,
                    toAccountId = toAccountId,
                    amountCents = amountCents,
                    type = type,
                    occurredAtMillis = occurredAtMillis,
                    remark = remark,
                ),
            )
            _uiState.update { it.copy(message = if (keepOpen) "已保存，可继续记一笔" else "账单已保存") }
            onSaved()
        }
    }

    fun saveCategory(
        categoryId: String? = null,
        name: String,
        iconKey: String,
        color: Long,
        type: LedgerCategoryType,
        onSaved: () -> Unit = {},
    ) {
        val normalizedName = name.trim()
        if (normalizedName.isBlank()) {
            _uiState.update { it.copy(message = "请输入分类名称") }
            return
        }
        val duplicate = _uiState.value.allCategories.any {
            it.type == type &&
                it.id != categoryId &&
                it.name.equals(normalizedName, ignoreCase = true)
        }
        if (duplicate) {
            _uiState.update { it.copy(message = "同账本下该分类名称已存在") }
            return
        }
        viewModelScope.launch {
            repository.saveCategory(
                LedgerCategoryDraft(
                    id = categoryId,
                    bookId = _uiState.value.currentBookId,
                    name = normalizedName,
                    iconKey = iconKey,
                    color = color,
                    type = type,
                ),
            )
            _uiState.update { it.copy(message = if (categoryId == null) "分类已新增" else "分类已更新") }
            onSaved()
        }
    }

    fun setCategoryHidden(categoryId: String, hidden: Boolean) {
        viewModelScope.launch {
            repository.setCategoryHidden(categoryId, hidden)
            _uiState.update { it.copy(message = if (hidden) "分类已隐藏" else "分类已显示") }
        }
    }

    fun saveAccount(
        accountId: String? = null,
        name: String,
        type: com.example.yingshi.feature.ledger.data.LedgerAccountType,
        initialBalanceCents: Long,
        includeInTotal: Boolean,
        note: String,
        onSaved: () -> Unit = {},
    ) {
        val normalizedName = name.trim()
        if (normalizedName.isBlank()) {
            _uiState.update { it.copy(message = "请输入账户名称") }
            return
        }
        val duplicate = _uiState.value.allAccounts.any {
            it.id != accountId &&
                it.name.equals(normalizedName, ignoreCase = true)
        }
        if (duplicate) {
            _uiState.update { it.copy(message = "同账本下该账户名称已存在") }
            return
        }
        viewModelScope.launch {
            repository.saveAccount(
                LedgerAccountDraft(
                    id = accountId,
                    bookId = _uiState.value.currentBookId,
                    name = normalizedName,
                    type = type,
                    initialBalanceCents = initialBalanceCents,
                    includeInTotal = includeInTotal,
                    note = note,
                ),
            )
            _uiState.update { it.copy(message = if (accountId == null) "账户已新增" else "账户已更新") }
            onSaved()
        }
    }

    fun setAccountHidden(accountId: String, hidden: Boolean) {
        viewModelScope.launch {
            repository.setAccountHidden(accountId, hidden)
            _uiState.update { it.copy(message = if (hidden) "账户已隐藏" else "账户已显示") }
        }
    }

    fun deleteTransaction(transactionId: String) {
        viewModelScope.launch {
            repository.deleteTransaction(transactionId)
            _uiState.update { it.copy(message = "已移入回收站") }
        }
    }

    fun deleteSelectedTransactions() {
        val ids = _uiState.value.selectedSearchTransactionIds.toList()
        if (ids.isEmpty()) return
        viewModelScope.launch {
            repository.deleteTransactions(ids)
            _uiState.update {
                it.copy(
                    selectedSearchTransactionIds = emptySet(),
                    isSearchSelectionMode = false,
                    message = "已移入回收站",
                )
            }
        }
    }

    fun updateSelectedTransactionsCategory(categoryId: String) {
        val selectedTransactions = _uiState.value.searchResults.filter { it.id in _uiState.value.selectedSearchTransactionIds }
        if (selectedTransactions.isEmpty()) return
        val types = selectedTransactions.map { it.type }.distinct()
        if (types.size != 1 || types.first() == LedgerTransactionType.TRANSFER) {
            _uiState.update { it.copy(message = "仅相同类型的支出/收入账单可批量改分类") }
            return
        }
        viewModelScope.launch {
            repository.batchUpdateCategory(selectedTransactions.map { it.id }, categoryId)
            _uiState.update {
                it.copy(
                    selectedSearchTransactionIds = emptySet(),
                    isSearchSelectionMode = false,
                    message = "批量改分类已完成",
                )
            }
        }
    }

    fun updateSelectedTransactionsAccount(accountId: String) {
        val selectedTransactions = _uiState.value.searchResults.filter { it.id in _uiState.value.selectedSearchTransactionIds }
        if (selectedTransactions.isEmpty()) return
        if (selectedTransactions.any { it.type == LedgerTransactionType.TRANSFER }) {
            _uiState.update { it.copy(message = "转账账单暂不支持批量改账户") }
            return
        }
        viewModelScope.launch {
            repository.batchUpdateAccount(selectedTransactions.map { it.id }, accountId)
            _uiState.update {
                it.copy(
                    selectedSearchTransactionIds = emptySet(),
                    isSearchSelectionMode = false,
                    message = "批量改账户已完成",
                )
            }
        }
    }

    fun restoreTransaction(transactionId: String) {
        viewModelScope.launch {
            repository.restoreTransaction(transactionId)
            _uiState.update { it.copy(message = "账单已恢复") }
        }
    }

    fun permanentlyDeleteTransaction(transactionId: String) {
        viewModelScope.launch {
            repository.permanentlyDeleteTransaction(transactionId)
            _uiState.update { it.copy(message = "已永久删除") }
        }
    }

    fun setBudget(amountCents: Long) {
        viewModelScope.launch {
            repository.upsertBudget(
                bookId = _uiState.value.currentBookId,
                period = _uiState.value.selectedBudgetPeriod,
                date = _uiState.value.selectedDate,
                amountCents = amountCents,
            )
            _uiState.update { it.copy(message = "预算已更新") }
            observe()
        }
    }

    fun clearBudget() {
        val budget = _uiState.value.budget ?: run {
            _uiState.update { it.copy(message = "当前没有可清空的预算") }
            return
        }
        viewModelScope.launch {
            repository.clearBudget(budget.id)
            _uiState.update { it.copy(message = "总预算已清空") }
            observe()
        }
    }

    fun setCategoryBudget(categoryId: String, amountCents: Long) {
        viewModelScope.launch {
            val budget = _uiState.value.budget
            if (budget == null) {
                repository.upsertBudget(
                    bookId = _uiState.value.currentBookId,
                    period = _uiState.value.selectedBudgetPeriod,
                    date = _uiState.value.selectedDate,
                    amountCents = amountCents,
                )
                observe()
                _uiState.update { it.copy(message = "请再次选择分类预算") }
                return@launch
            }
            repository.upsertCategoryBudget(
                budget = budget,
                categoryId = categoryId,
                amountCents = amountCents,
            )
            _uiState.update { it.copy(message = "分类预算已更新") }
            observe()
        }
    }

    fun clearCategoryBudget(categoryId: String) {
        val budget = _uiState.value.budget ?: run {
            _uiState.update { it.copy(message = "当前没有可清空的预算") }
            return
        }
        viewModelScope.launch {
            repository.clearCategoryBudget(budget.id, categoryId)
            _uiState.update { it.copy(message = "分类预算已清空") }
            observe()
        }
    }

    fun consumeMessage() {
        _uiState.update { it.copy(message = null) }
    }

    private fun observe() {
        observeJob?.cancel()
        val bookId = _uiState.value.currentBookId
        val statsRange = when (_uiState.value.selectedStatsMode) {
            LedgerStatsMode.WEEK -> {
                val start = _uiState.value.selectedDate.with(java.time.DayOfWeek.MONDAY)
                val end = start.plusDays(6)
                LedgerDateUtils.dateRange(start, end)
            }
            LedgerStatsMode.MONTH -> LedgerDateUtils.monthRange(_uiState.value.selectedMonth)
            LedgerStatsMode.YEAR -> {
                val start = _uiState.value.selectedDate.with(TemporalAdjusters.firstDayOfYear())
                val end = _uiState.value.selectedDate.with(TemporalAdjusters.lastDayOfYear())
                LedgerDateUtils.dateRange(start, end)
            }
            LedgerStatsMode.TOTAL -> LedgerDateUtils.dateRange(LocalDate.of(2020, 1, 1), LocalDate.of(2035, 12, 31))
            LedgerStatsMode.CUSTOM -> LedgerDateUtils.dateRange(
                _uiState.value.customStatsStartDate,
                _uiState.value.customStatsEndDate,
            )
        }
        val monthRange = LedgerDateUtils.monthRange(_uiState.value.selectedMonth)
        val budgetDate = _uiState.value.selectedDate
        val budgetPeriod = _uiState.value.selectedBudgetPeriod
        observeJob = viewModelScope.launch {
            combine(
                repository.observeBooks(),
                repository.observeBook(bookId),
                repository.observeVisibleCategories(bookId),
                repository.observeAllCategories(bookId),
                repository.observeVisibleAccounts(bookId),
                repository.observeAllAccounts(bookId),
                repository.observeTransactions(bookId),
                repository.observeTransactionsInRange(bookId, monthRange.startMillis, monthRange.endMillis),
                repository.observePeriodStats(bookId, statsRange.startMillis, statsRange.endMillis),
                repository.observeBudgetBundle(bookId, budgetPeriod, budgetDate),
                repository.observeDeletedItems(bookId),
            ) { values ->
                @Suppress("UNCHECKED_CAST")
                val books = values[0] as List<LedgerBook>
                @Suppress("UNCHECKED_CAST")
                val book = values[1] as LedgerBook?
                @Suppress("UNCHECKED_CAST")
                val categories = values[2] as List<LedgerCategory>
                @Suppress("UNCHECKED_CAST")
                val allCategories = values[3] as List<LedgerCategory>
                @Suppress("UNCHECKED_CAST")
                val accounts = values[4] as List<LedgerAccount>
                @Suppress("UNCHECKED_CAST")
                val allAccounts = values[5] as List<LedgerAccount>
                @Suppress("UNCHECKED_CAST")
                val allTransactions = values[6] as List<LedgerTransaction>
                @Suppress("UNCHECKED_CAST")
                val transactions = values[7] as List<LedgerTransaction>
                val stats = values[8] as LedgerPeriodStats
                @Suppress("UNCHECKED_CAST")
                val budgetBundle = values[9] as Pair<LedgerBudget?, List<LedgerCategoryBudget>>
                @Suppress("UNCHECKED_CAST")
                val deletedItems = values[10] as List<LedgerDeletedItem>
                val previous = _uiState.value
                LedgerUiState(
                    isLoading = false,
                    books = books,
                    currentBookId = book?.id ?: bookId,
                    bookName = book?.name ?: "日常账本",
                    currencySymbol = book?.currencySymbol ?: "¥",
                    selectedMonth = previous.selectedMonth,
                    selectedDate = previous.selectedDate,
                    selectedBudgetPeriod = previous.selectedBudgetPeriod,
                    selectedStatsMode = previous.selectedStatsMode,
                    customStatsStartDate = previous.customStatsStartDate,
                    customStatsEndDate = previous.customStatsEndDate,
                    categories = categories,
                    allCategories = allCategories,
                    accounts = accounts,
                    allAccounts = allAccounts,
                    allTransactions = allTransactions,
                    transactions = transactions,
                    stats = stats,
                    budget = budgetBundle.first,
                    categoryBudgets = budgetBundle.second,
                    deletedItems = deletedItems,
                    searchFilter = previous.searchFilter,
                    searchResults = previous.searchResults,
                    selectedSearchTransactionIds = previous.selectedSearchTransactionIds,
                    isSearchSelectionMode = previous.isSearchSelectionMode,
                    message = previous.message,
                )
            }.collect { state ->
                _uiState.value = state
            }
        }
        observeSearch(_uiState.value.searchFilter)
    }

    private fun observeSearch(filter: LedgerSearchFilter) {
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            repository.observeSearch(_uiState.value.currentBookId, filter).collect { results ->
                _uiState.update { state ->
                    val resultIds = results.map { it.id }.toSet()
                    state.copy(
                        searchResults = results,
                        selectedSearchTransactionIds = state.selectedSearchTransactionIds.intersect(resultIds),
                    )
                }
            }
        }
    }

    private fun updateSearchFilter(transform: (LedgerSearchFilter) -> LedgerSearchFilter) {
        val nextFilter = transform(_uiState.value.searchFilter)
        _uiState.update {
            it.copy(
                searchFilter = nextFilter,
                selectedSearchTransactionIds = emptySet(),
                isSearchSelectionMode = false,
            )
        }
        observeSearch(nextFilter)
    }

    private fun validateDraft(
        type: LedgerTransactionType,
        amountCents: Long,
        categoryId: String?,
        accountId: String,
        toAccountId: String?,
    ): String? {
        if (amountCents <= 0) return "请输入大于 0 的金额"
        if (accountId.isBlank()) return "请选择账户"
        if (type != LedgerTransactionType.TRANSFER && categoryId.isNullOrBlank()) return "请选择分类"
        if (type == LedgerTransactionType.TRANSFER) {
            if (toAccountId.isNullOrBlank()) return "请选择转入账户"
            if (accountId == toAccountId) return "转出和转入账户不能相同"
        }
        return null
    }

    companion object {
        fun factory(application: Application): ViewModelProvider.Factory {
            return object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return LedgerViewModel(application) as T
                }
            }
        }
    }
}

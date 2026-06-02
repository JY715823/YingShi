package com.example.yingshi.feature.ledger.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.util.UUID

class LedgerRepository(
    private val dao: LedgerDao,
    private val syncBridge: LedgerSyncBridge = NoOpLedgerSyncBridge,
) {
    val shouldSeedDemoData: Boolean
        get() = syncBridge.shouldSeedDemoData

    suspend fun ensureSeedData(nowMillis: Long = System.currentTimeMillis()) {
        LedgerSeedData.defaultBooks(nowMillis).forEach { book ->
            if (dao.getBook(book.id) == null) {
                dao.insertBook(book)
            }
        }
        LedgerSeedData.defaultAccounts(nowMillis).forEach { account ->
            if (dao.getAccount(account.id) == null) {
                dao.insertAccount(account)
            }
        }
        LedgerSeedData.defaultCategories(nowMillis).forEach { category ->
            if (dao.getCategory(category.id) == null) {
                dao.insertCategory(category)
            }
        }
    }

    suspend fun ensureDemoData(nowMillis: Long = System.currentTimeMillis()) {
        if (dao.countTransactionsByBook(LedgerSeedData.DefaultBookId) == 0) {
            seedDemoTransactions(nowMillis)
        }
    }

    suspend fun hydrateFromBackendIfNeeded() {
        syncBridge.hydrate(this)
    }

    suspend fun exportLocalSnapshot(): LedgerLocalSnapshot {
        return LedgerLocalSnapshot(
            books = dao.getAllBooks(),
            categories = dao.getAllCategories(),
            accounts = dao.getAllAccounts(),
            transactions = dao.getAllTransactions(),
            budgets = dao.getAllBudgets(),
            categoryBudgets = dao.getAllCategoryBudgets(),
            deletedItems = dao.getAllDeletedItems(),
            recurringRules = dao.getAllRecurringRules(),
            recurringOccurrences = dao.getAllRecurringOccurrences(),
        )
    }

    suspend fun replaceLocalSnapshot(snapshot: LedgerLocalSnapshot) {
        dao.replaceAllData(snapshot)
    }

    fun observeBooks(): Flow<List<LedgerBook>> = dao.observeBooks().map { rows ->
        rows.map { it.toDomain() }
    }

    fun observeArchivedBooks(): Flow<List<LedgerBook>> = dao.observeArchivedBooks().map { rows ->
        rows.map { it.toDomain() }
    }

    fun observeAllBooks(): Flow<List<LedgerBook>> = dao.observeAllBooks().map { rows ->
        rows.map { it.toDomain() }
    }

    fun observePrimaryBook(): Flow<LedgerBook?> = dao.observePrimaryBook().map { it?.toDomain() }

    fun observeBook(bookId: String): Flow<LedgerBook?> = dao.observeBook(bookId).map { it?.toDomain() }

    fun observeVisibleCategories(bookId: String): Flow<List<LedgerCategory>> =
        dao.observeVisibleCategories(bookId).map { rows -> rows.map { it.toDomain() } }

    fun observeAllCategories(bookId: String): Flow<List<LedgerCategory>> =
        dao.observeAllCategories(bookId).map { rows -> rows.map { it.toDomain() } }

    fun observeCategoriesByType(bookId: String, type: LedgerCategoryType): Flow<List<LedgerCategory>> =
        dao.observeCategoriesByType(bookId, type).map { rows -> rows.map { it.toDomain() } }

    fun observeVisibleAccounts(bookId: String): Flow<List<LedgerAccount>> =
        dao.observeVisibleAccounts(bookId).map { rows -> rows.map { it.toDomain() } }

    fun observeAllVisibleAccounts(): Flow<List<LedgerAccount>> =
        dao.observeVisibleAccountsAcrossBooks().map { rows -> rows.map { it.toDomain() } }

    fun observeAllAccounts(bookId: String): Flow<List<LedgerAccount>> =
        dao.observeAllAccounts(bookId).map { rows -> rows.map { it.toDomain() } }

    fun observeAccounts(bookId: String): Flow<List<LedgerAccount>> = observeVisibleAccounts(bookId)

    fun observeTransactions(bookId: String): Flow<List<LedgerTransaction>> = combine(
        dao.observeTransactions(bookId),
        dao.observeAllCategories(bookId),
        dao.observeAllAccounts(bookId),
    ) { transactions, categories, accounts ->
        mapTransactions(
            transactions = transactions,
            categories = categories,
            accounts = accounts,
        )
    }

    fun observeTransactionsInRange(
        bookId: String,
        startMillis: Long,
        endMillis: Long,
    ): Flow<List<LedgerTransaction>> = combine(
        dao.observeTransactionsInRange(bookId, startMillis, endMillis),
        dao.observeAllCategories(bookId),
        dao.observeAllAccounts(bookId),
    ) { transactions, categories, accounts ->
        mapTransactions(
            transactions = transactions,
            categories = categories,
            accounts = accounts,
        )
    }

    fun observePeriodStats(
        bookId: String,
        startMillis: Long,
        endMillis: Long,
    ): Flow<LedgerPeriodStats> = observeTransactionsInRange(bookId, startMillis, endMillis)
        .map(::calculateStats)

    fun observeSearch(bookId: String, filter: LedgerSearchFilter): Flow<List<LedgerTransaction>> =
        observeTransactions(bookId).map { transactions ->
            transactions.filter(filter::matches)
        }

    fun observeBudgetBundle(
        bookId: String,
        period: LedgerBudgetPeriod,
        date: LocalDate,
    ): Flow<Pair<LedgerBudget?, List<LedgerCategoryBudget>>> {
        val range = LedgerDateUtils.periodRange(date, period)
        return dao.observeBudget(bookId, period, range.startMillis, range.endMillis).map { budgetEntity ->
            val budget = budgetEntity?.let {
                LedgerBudget(
                    id = it.id,
                    period = it.period,
                    startMillis = it.startMillis,
                    endMillis = it.endMillis,
                    totalAmountCents = it.totalAmountCents,
                )
            }
            val categoryBudgets = budgetEntity?.let { entity ->
                val rows = dao.getCategoryBudgets(entity.id)
                val transactions = dao.getTransactionsInRange(bookId, entity.startMillis, entity.endMillis)
                val categories = dao.observeAllCategories(bookId).first()
                rows.map { row ->
                    val category = categories.firstOrNull { it.id == row.categoryId }?.toDomain()
                    val categoryTransactions = transactions.filter {
                        it.categoryId == row.categoryId &&
                            it.type == LedgerTransactionType.EXPENSE &&
                            it.deletedAtMillis == null
                    }
                    LedgerCategoryBudget(
                        id = row.id,
                        budgetId = row.budgetId,
                        category = category,
                        amountCents = row.amountCents,
                        usedCents = categoryTransactions.sumOf { it.amountCents },
                        transactionCount = categoryTransactions.size,
                    )
                }
            } ?: emptyList()
            budget to categoryBudgets
        }
    }

    fun observeDeletedItems(bookId: String): Flow<List<LedgerDeletedItem>> =
        dao.observeDeletedItems(bookId).map { rows ->
            rows.map {
                LedgerDeletedItem(
                    id = it.id,
                    itemId = it.itemId,
                    title = it.title,
                    amountCents = it.amountCents,
                    deletedAtMillis = it.deletedAtMillis,
                    expiresAtMillis = it.expiresAtMillis,
                )
            }
        }

    fun observeRecurringRules(bookId: String): Flow<List<LedgerRecurringRule>> = combine(
        dao.observeRecurringRules(bookId),
        dao.observeAllCategories(bookId),
        dao.observeAllAccounts(bookId),
    ) { rules, categories, accounts ->
        val categoriesById = categories.associateBy({ it.id }, { it.toDomain() })
        val accountsById = accounts.associateBy({ it.id }, { it.toDomain() })
        rules.map { it.toDomain(categoriesById, accountsById) }
    }

    suspend fun findBookByName(name: String): LedgerBook? = dao.findBookByName(name)?.toDomain()

    suspend fun saveBook(draft: LedgerBookDraft): String {
        val now = System.currentTimeMillis()
        val existing = draft.id?.let { dao.getBook(it) }
        val normalizedName = draft.name.trim()
        return if (existing == null) {
            val bookId = UUID.randomUUID().toString()
            val nextSortOrder = dao.observeAllBooks().first().maxOfOrNull { it.sortOrder }?.plus(1) ?: 0
            val template = draft.template.ifBlank { LedgerBookTemplateDaily }
            dao.insertBook(
                LedgerBookEntity(
                    id = bookId,
                    name = normalizedName,
                    template = template,
                    currencyCode = "CNY",
                    currencySymbol = "¥",
                    coverColor = draft.coverColor,
                    sortOrder = nextSortOrder,
                    createdAtMillis = now,
                    updatedAtMillis = now,
                ),
            )
            dao.insertCategories(LedgerSeedData.seedCategoriesForTemplate(bookId, template, now))
            dao.insertAccounts(LedgerSeedData.seedAccountsForTemplate(bookId, template, now))
            syncBridge.afterMutation(this)
            bookId
        } else {
            dao.updateBook(
                existing.copy(
                    name = normalizedName,
                    coverColor = draft.coverColor,
                    updatedAtMillis = now,
                ),
            )
            syncBridge.afterMutation(this)
            existing.id
        }
    }

    suspend fun setBookArchived(bookId: String, archived: Boolean) {
        dao.setBookArchived(bookId, archived, System.currentTimeMillis())
        syncBridge.afterMutation(this)
    }

    suspend fun reorderCategories(bookId: String, type: LedgerCategoryType, orderedIds: List<String>) {
        val now = System.currentTimeMillis()
        val categories = dao.observeAllCategories(bookId).first()
            .filter { it.type == type }
            .associateBy { it.id }
        orderedIds.distinct().forEachIndexed { index, categoryId ->
            val category = categories[categoryId] ?: return@forEachIndexed
            if (category.sortOrder != index) {
                dao.updateCategory(
                    category.copy(
                        sortOrder = index,
                        updatedAtMillis = now,
                    ),
                )
            }
        }
        syncBridge.afterMutation(this)
    }

    suspend fun reorderAccounts(bookId: String, orderedIds: List<String>) {
        val now = System.currentTimeMillis()
        val accounts = dao.observeAllAccounts(bookId).first()
            .associateBy { it.id }
        orderedIds.distinct().forEachIndexed { index, accountId ->
            val account = accounts[accountId] ?: return@forEachIndexed
            if (account.sortOrder != index) {
                dao.updateAccount(
                    account.copy(
                        sortOrder = index,
                        updatedAtMillis = now,
                    ),
                )
            }
        }
    }

    suspend fun saveRecurringRule(draft: LedgerRecurringRuleDraft): String {
        val now = System.currentTimeMillis()
        val existing = draft.id?.let { dao.getRecurringRule(it) }
        val nextOccurrenceAtMillis = if (existing == null) {
            LedgerRecurringScheduler.firstOccurrenceAtOrAfter(
                referenceMillis = draft.startAtMillis,
                startAtMillis = draft.startAtMillis,
                frequency = draft.frequency,
            )
        } else {
            LedgerRecurringScheduler.firstOccurrenceAtOrAfter(
                referenceMillis = maxOf(existing.nextOccurrenceAtMillis, draft.startAtMillis),
                startAtMillis = draft.startAtMillis,
                frequency = draft.frequency,
            )
        }
        val entity = LedgerRecurringRuleEntity(
            id = existing?.id ?: UUID.randomUUID().toString(),
            bookId = draft.bookId,
            type = draft.type,
            categoryId = draft.categoryId,
            accountId = draft.accountId,
            toAccountId = draft.toAccountId,
            amountCents = draft.amountCents,
            remark = draft.remark.trim(),
            frequency = draft.frequency,
            startAtMillis = draft.startAtMillis,
            endAtMillis = draft.endAtMillis,
            nextOccurrenceAtMillis = nextOccurrenceAtMillis,
            enabled = draft.enabled,
            createdAtMillis = existing?.createdAtMillis ?: now,
            updatedAtMillis = now,
        )
        if (existing == null) {
            dao.insertRecurringRule(entity)
        } else {
            dao.updateRecurringRule(entity)
        }
        syncBridge.afterMutation(this)
        return entity.id
    }

    suspend fun setRecurringRuleEnabled(ruleId: String, enabled: Boolean) {
        val rule = dao.getRecurringRule(ruleId) ?: return
        dao.updateRecurringRule(
            rule.copy(
                enabled = enabled,
                updatedAtMillis = System.currentTimeMillis(),
            ),
        )
        syncBridge.afterMutation(this)
    }

    suspend fun deleteRecurringRule(ruleId: String) {
        dao.deleteRecurringOccurrencesByRuleId(ruleId)
        dao.deleteRecurringRule(ruleId)
        syncBridge.afterMutation(this)
    }

    suspend fun materializeDueRecurringTransactions(nowMillis: Long = System.currentTimeMillis()) {
        val dueRules = dao.getDueRecurringRules(nowMillis)
        dueRules.forEach { rule ->
            var cursor = rule
            var changed = false
            while (cursor.enabled &&
                cursor.nextOccurrenceAtMillis <= nowMillis &&
                (cursor.endAtMillis == null || cursor.nextOccurrenceAtMillis <= cursor.endAtMillis)
            ) {
                val occurrenceAtMillis = cursor.nextOccurrenceAtMillis
                val occurrenceId = recurringOccurrenceId(cursor.id, occurrenceAtMillis)
                val transactionId = recurringTransactionId(cursor.id, occurrenceAtMillis)
                val occurrence = LedgerRecurringOccurrenceEntity(
                    id = occurrenceId,
                    ruleId = cursor.id,
                    transactionId = transactionId,
                    occurrenceAtMillis = occurrenceAtMillis,
                    createdAtMillis = nowMillis,
                )
                val occurrenceExists = dao.getRecurringOccurrence(cursor.id, occurrenceAtMillis) != null
                if (!occurrenceExists) {
                    dao.insertRecurringOccurrence(occurrence)
                    changed = true
                }
                val transactionExists = dao.getTransaction(transactionId) != null
                if (!transactionExists) {
                    dao.insertTransaction(
                        LedgerTransactionEntity(
                            id = transactionId,
                            bookId = cursor.bookId,
                            categoryId = cursor.categoryId,
                            accountId = cursor.accountId,
                            toAccountId = cursor.toAccountId,
                            amountCents = cursor.amountCents,
                            type = cursor.type,
                            occurredAtMillis = occurrenceAtMillis,
                            remark = cursor.remark,
                            method = "recurring",
                            createdAtMillis = nowMillis,
                            updatedAtMillis = nowMillis,
                        ),
                    )
                    changed = true
                }
                val next = LedgerRecurringScheduler.nextOccurrenceAfter(
                    occurrenceMillis = occurrenceAtMillis,
                    startAtMillis = cursor.startAtMillis,
                    frequency = cursor.frequency,
                )
                cursor = cursor.copy(
                    nextOccurrenceAtMillis = next,
                    updatedAtMillis = nowMillis,
                )
            }
            if (cursor.nextOccurrenceAtMillis != rule.nextOccurrenceAtMillis) {
                dao.updateRecurringRule(cursor)
                changed = true
            }
            if (changed) {
                syncBridge.afterMutation(this)
            }
        }
    }

    suspend fun saveTransaction(draft: LedgerTransactionDraft) {
        val now = System.currentTimeMillis()
        val entity = LedgerTransactionEntity(
            id = draft.id ?: UUID.randomUUID().toString(),
            bookId = draft.bookId,
            categoryId = draft.categoryId,
            accountId = draft.accountId,
            toAccountId = draft.toAccountId,
            amountCents = draft.amountCents,
            type = draft.type,
            occurredAtMillis = draft.occurredAtMillis,
            remark = draft.remark.trim(),
            method = "manual",
            createdAtMillis = draft.id?.let { dao.getTransaction(it)?.createdAtMillis } ?: now,
            updatedAtMillis = now,
        )
        if (draft.id == null) {
            dao.insertTransaction(entity)
        } else {
            dao.updateTransaction(entity)
        }
        syncBridge.afterMutation(this)
    }

    suspend fun saveCategory(draft: LedgerCategoryDraft) {
        val now = System.currentTimeMillis()
        val existing = draft.id?.let { dao.getCategory(it) }
        val sortOrder = existing?.sortOrder ?: (
            dao.observeAllCategories(draft.bookId).first()
                .filter { it.type == draft.type }
                .maxOfOrNull { it.sortOrder }?.plus(1) ?: 0
            )
        val entity = LedgerCategoryEntity(
            id = existing?.id ?: UUID.randomUUID().toString(),
            bookId = draft.bookId,
            name = draft.name.trim(),
            iconKey = draft.iconKey,
            color = draft.color,
            type = draft.type,
            sortOrder = sortOrder,
            hidden = existing?.hidden ?: false,
            createdAtMillis = existing?.createdAtMillis ?: now,
            updatedAtMillis = now,
        )
        if (existing == null) {
            dao.insertCategory(entity)
        } else {
            dao.updateCategory(entity)
        }
        syncBridge.afterMutation(this)
    }

    suspend fun setCategoryHidden(categoryId: String, hidden: Boolean) {
        dao.setCategoryHidden(categoryId, hidden, System.currentTimeMillis())
        syncBridge.afterMutation(this)
    }

    suspend fun saveAccount(draft: LedgerAccountDraft) {
        val now = System.currentTimeMillis()
        val existing = draft.id?.let { dao.getAccount(it) }
        val sortOrder = existing?.sortOrder ?: (
            dao.observeAllAccounts(draft.bookId).first()
                .maxOfOrNull { it.sortOrder }?.plus(1) ?: 0
            )
        val iconKey = defaultAccountIconKey(draft.type)
        val color = defaultAccountColor(draft.type)
        val balanceCents = if (existing == null) {
            draft.initialBalanceCents
        } else {
            existing.balanceCents + (draft.initialBalanceCents - existing.initialBalanceCents)
        }
        val entity = LedgerAccountEntity(
            id = existing?.id ?: UUID.randomUUID().toString(),
            bookId = draft.bookId,
            name = draft.name.trim(),
            type = draft.type,
            iconKey = iconKey,
            color = color,
            initialBalanceCents = draft.initialBalanceCents,
            balanceCents = balanceCents,
            creditLimitCents = existing?.creditLimitCents,
            includeInTotal = draft.includeInTotal,
            hidden = existing?.hidden ?: false,
            note = draft.note.trim(),
            sortOrder = sortOrder,
            createdAtMillis = existing?.createdAtMillis ?: now,
            updatedAtMillis = now,
        )
        if (existing == null) {
            dao.insertAccount(entity)
        } else {
            dao.updateAccount(entity)
        }
        syncBridge.afterMutation(this)
    }

    suspend fun setAccountHidden(accountId: String, hidden: Boolean) {
        dao.setAccountHidden(accountId, hidden, System.currentTimeMillis())
        syncBridge.afterMutation(this)
    }

    suspend fun defaultBookId(): String =
        dao.observePrimaryBook().first()?.id ?: LedgerSeedData.DefaultBookId

    suspend fun hasBookTransactions(bookId: String): Boolean = dao.countTransactionsByBook(bookId) > 0

    private suspend fun seedDemoTransactions(nowMillis: Long) {
        val zone = ZoneId.systemDefault()
        val month = YearMonth.now(zone)
        val entries = listOf(
            demoDraft(month, 5, 10, 30, "cat-salary", "account-icbc-5941", 250_600, LedgerTransactionType.INCOME, "工资"),
            demoDraft(month, 5, 21, 10, "cat-food", "account-wechat", 1_600, LedgerTransactionType.EXPENSE, "吃肠粉"),
            demoDraft(month, 6, 9, 40, "cat-entertainment", "account-wechat", 4_638, LedgerTransactionType.EXPENSE, "买牛奶"),
            demoDraft(month, 7, 13, 50, "cat-food", "account-wechat", 970, LedgerTransactionType.EXPENSE, "买泡面"),
            demoDraft(month, 8, 10, 0, "cat-study", "account-wechat", 4_092, LedgerTransactionType.EXPENSE, "订阅资料"),
            demoDraft(month, 9, 12, 30, "cat-food", "account-wechat", 10_000, LedgerTransactionType.EXPENSE, "充饭卡"),
            demoDraft(month, 9, 12, 40, "cat-food", "account-wechat", 10_000, LedgerTransactionType.EXPENSE, "充饭卡"),
            demoDraft(month, 9, 19, 10, "cat-food", "account-icbc-5941", 1_020, LedgerTransactionType.EXPENSE, "吃饭"),
            demoDraft(month, 11, 18, 20, "cat-gift-expense", "account-wechat", 3_466, LedgerTransactionType.EXPENSE, "给冰冰买酸奶"),
            demoDraft(month, 12, 20, 50, "cat-entertainment", "account-wechat", 1_990, LedgerTransactionType.EXPENSE, "买清洁湿巾"),
            demoDraft(month, 12, 21, 15, "cat-study", "account-wechat", 1_800, LedgerTransactionType.EXPENSE, "百度网盘会员"),
            demoDraft(month, 13, 8, 5, "cat-food", "account-wechat", 10_310, LedgerTransactionType.EXPENSE, "点外卖"),
            demoDraft(month, 13, 19, 15, "cat-food", "account-wechat", 3_588, LedgerTransactionType.EXPENSE, "吃面"),
            demoDraft(month, 14, 11, 40, "cat-food", "account-wechat", 13_380, LedgerTransactionType.EXPENSE, "吃烤肉"),
            demoDraft(month, 14, 18, 10, "cat-daily", "account-icbc-5941", 190, LedgerTransactionType.EXPENSE, "洗衣服"),
            demoDraft(month, 14, 18, 16, "cat-traffic", "account-icbc-5941", 570, LedgerTransactionType.EXPENSE, "骑车"),
            demoDraft(month, 15, 9, 20, "cat-entertainment", "account-wechat", 2_580, LedgerTransactionType.EXPENSE, "买摄像头探测器"),
            demoDraft(month, 15, 10, 5, "cat-entertainment", "account-wechat", 1_990, LedgerTransactionType.EXPENSE, "买清洁湿巾"),
            demoDraft(month, 15, 12, 0, "cat-gift-expense", "account-wechat", 1_966, LedgerTransactionType.EXPENSE, "给冰冰买干噎酸奶"),
            demoDraft(month, 15, 17, 45, "cat-study", "account-wechat", 3_000, LedgerTransactionType.EXPENSE, "充 ai"),
            demoDraft(month, 15, 20, 5, "cat-study", "account-wechat", 1_800, LedgerTransactionType.EXPENSE, "百度网盘会员"),
            demoDraft(month, 16, 9, 30, "cat-fitness", "account-wechat", 9_200, LedgerTransactionType.EXPENSE, "买鱼油"),
        )
        entries.forEach { saveTransaction(it) }
        upsertBudget(
            bookId = LedgerSeedData.DefaultBookId,
            period = LedgerBudgetPeriod.MONTH,
            date = month.atDay(1),
            amountCents = 350_000,
        )
    }

    private fun demoDraft(
        month: YearMonth,
        day: Int,
        hour: Int,
        minute: Int,
        categoryId: String,
        accountId: String,
        amountCents: Long,
        type: LedgerTransactionType,
        remark: String,
    ): LedgerTransactionDraft {
        val occurredAtMillis = month.atDay(day)
            .atTime(hour, minute)
            .atZone(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()
        return LedgerTransactionDraft(
            bookId = LedgerSeedData.DefaultBookId,
            categoryId = categoryId,
            accountId = accountId,
            amountCents = amountCents,
            type = type,
            occurredAtMillis = occurredAtMillis,
            remark = remark,
        )
    }

    suspend fun deleteTransaction(transactionId: String) {
        val now = System.currentTimeMillis()
        val transaction = dao.getTransaction(transactionId) ?: return
        val category = transaction.categoryId?.let { dao.getCategory(it) }
        val title = category?.name ?: if (transaction.type == LedgerTransactionType.TRANSFER) "账户转账" else "账单"
        dao.softDeleteTransaction(
            transactionId = transactionId,
            deletedAtMillis = now,
            expiresAtMillis = now + 30L * 24L * 60L * 60L * 1000L,
            deletedItemId = UUID.randomUUID().toString(),
            title = title,
        )
        syncBridge.afterMutation(this)
    }

    suspend fun restoreTransaction(transactionId: String) {
        dao.restoreTransaction(transactionId, System.currentTimeMillis())
        syncBridge.afterMutation(this)
    }

    suspend fun permanentlyDeleteTransaction(transactionId: String) {
        dao.permanentlyDeleteDeletedItem(transactionId)
        syncBridge.afterMutation(this)
    }

    suspend fun deleteTransactions(transactionIds: List<String>) {
        transactionIds.distinct().forEach { deleteTransaction(it) }
    }

    suspend fun batchUpdateCategory(transactionIds: List<String>, categoryId: String) {
        val now = System.currentTimeMillis()
        dao.getTransactions(transactionIds.distinct()).forEach { transaction ->
            dao.updateTransaction(
                transaction.copy(
                    categoryId = categoryId,
                    updatedAtMillis = now,
                ),
            )
        }
        syncBridge.afterMutation(this)
    }

    suspend fun batchUpdateAccount(transactionIds: List<String>, accountId: String) {
        val now = System.currentTimeMillis()
        dao.getTransactions(transactionIds.distinct()).forEach { transaction ->
            if (transaction.type == LedgerTransactionType.TRANSFER) return@forEach
            dao.updateTransaction(
                transaction.copy(
                    accountId = accountId,
                    updatedAtMillis = now,
                ),
            )
        }
        syncBridge.afterMutation(this)
    }

    suspend fun batchUpdateTransferAccount(
        transactionIds: List<String>,
        side: LedgerTransferAccountSide,
        accountId: String,
    ) {
        val now = System.currentTimeMillis()
        dao.getTransactions(transactionIds.distinct()).forEach { transaction ->
            if (transaction.type != LedgerTransactionType.TRANSFER) return@forEach
            dao.updateTransaction(
                transaction.copy(
                    accountId = if (side == LedgerTransferAccountSide.FROM) accountId else transaction.accountId,
                    toAccountId = if (side == LedgerTransferAccountSide.TO) accountId else transaction.toAccountId,
                    updatedAtMillis = now,
                ),
            )
        }
        syncBridge.afterMutation(this)
    }

    suspend fun upsertBudget(
        bookId: String,
        period: LedgerBudgetPeriod,
        date: LocalDate,
        amountCents: Long,
    ) {
        val now = System.currentTimeMillis()
        val range = LedgerDateUtils.periodRange(date, period)
        val existing = dao.observeBudget(bookId, period, range.startMillis, range.endMillis).first()
        if (existing == null) {
            dao.insertBudget(
                LedgerBudgetEntity(
                    id = UUID.randomUUID().toString(),
                    bookId = bookId,
                    period = period,
                    startMillis = range.startMillis,
                    endMillis = range.endMillis,
                    totalAmountCents = amountCents,
                    createdAtMillis = now,
                    updatedAtMillis = now,
                ),
            )
        } else {
            dao.updateBudget(
                existing.copy(
                    totalAmountCents = amountCents,
                    updatedAtMillis = now,
                ),
            )
        }
        syncBridge.afterMutation(this)
    }

    suspend fun upsertCategoryBudget(
        budget: LedgerBudget,
        categoryId: String,
        amountCents: Long,
    ) {
        val now = System.currentTimeMillis()
        val rows = dao.getCategoryBudgets(budget.id)
        val existing = rows.firstOrNull { it.categoryId == categoryId }
        if (existing == null) {
            dao.insertCategoryBudget(
                LedgerCategoryBudgetEntity(
                    id = UUID.randomUUID().toString(),
                    budgetId = budget.id,
                    categoryId = categoryId,
                    amountCents = amountCents,
                    createdAtMillis = now,
                    updatedAtMillis = now,
                ),
            )
        } else {
            dao.updateCategoryBudget(
                existing.copy(
                    amountCents = amountCents,
                    updatedAtMillis = now,
                ),
            )
        }
        val categoryTotal = dao.categoryBudgetTotal(budget.id)
        val currentBudget = dao.getBudget(budget.id) ?: return
        if (categoryTotal > currentBudget.totalAmountCents) {
            dao.updateBudget(
                currentBudget.copy(
                    totalAmountCents = categoryTotal,
                    updatedAtMillis = now,
                ),
            )
        }
        syncBridge.afterMutation(this)
    }

    suspend fun clearBudget(budgetId: String) {
        dao.deleteCategoryBudgetsByBudgetId(budgetId)
        dao.deleteBudget(budgetId)
        syncBridge.afterMutation(this)
    }

    suspend fun clearCategoryBudget(budgetId: String, categoryId: String) {
        dao.deleteCategoryBudgetByCategory(budgetId, categoryId)
        syncBridge.afterMutation(this)
    }

    private fun recurringOccurrenceId(ruleId: String, occurrenceAtMillis: Long): String =
        "recurring-occurrence-$ruleId-$occurrenceAtMillis"

    private fun recurringTransactionId(ruleId: String, occurrenceAtMillis: Long): String =
        "recurring-transaction-$ruleId-$occurrenceAtMillis"

    private fun mapTransactions(
        transactions: List<LedgerTransactionEntity>,
        categories: List<LedgerCategoryEntity>,
        accounts: List<LedgerAccountEntity>,
    ): List<LedgerTransaction> {
        val categoriesById = categories.associateBy { it.id }
        val accountsById = accounts.associateBy { it.id }
        return transactions.map { transaction ->
            LedgerTransaction(
                id = transaction.id,
                bookId = transaction.bookId,
                category = transaction.categoryId?.let { categoriesById[it]?.toDomain() },
                account = accountsById[transaction.accountId]?.toDomain(),
                toAccount = transaction.toAccountId?.let { accountsById[it]?.toDomain() },
                amountCents = transaction.amountCents,
                type = transaction.type,
                occurredAtMillis = transaction.occurredAtMillis,
                remark = transaction.remark,
                method = transaction.method,
            )
        }
    }

    private fun calculateStats(transactions: List<LedgerTransaction>): LedgerPeriodStats {
        val income = transactions
            .filter { it.type == LedgerTransactionType.INCOME }
            .sumOf { it.amountCents }
        val expense = transactions
            .filter { it.type == LedgerTransactionType.EXPENSE }
            .sumOf { it.amountCents }
        val expenseTransactions = transactions.filter { it.type == LedgerTransactionType.EXPENSE }
        val categoryStats = expenseTransactions
            .groupBy { it.category?.id ?: "uncategorized" }
            .map { (_, items) ->
                val amount = items.sumOf { it.amountCents }
                LedgerCategoryStat(
                    category = items.firstOrNull()?.category,
                    amountCents = amount,
                    count = items.size,
                    percent = if (expense > 0) amount.toFloat() / expense else 0f,
                )
            }
            .sortedByDescending { it.amountCents }
        val dailyStats = transactions
            .groupBy { LedgerDateUtils.dayStartMillis(it.occurredAtMillis) }
            .map { (dayStart, items) ->
                LedgerDailyStat(
                    dayStartMillis = dayStart,
                    expenseCents = items.filter { it.type == LedgerTransactionType.EXPENSE }.sumOf { it.amountCents },
                    incomeCents = items.filter { it.type == LedgerTransactionType.INCOME }.sumOf { it.amountCents },
                )
            }
            .sortedBy { it.dayStartMillis }
        return LedgerPeriodStats(
            expenseCents = expense,
            incomeCents = income,
            balanceCents = income - expense,
            transactions = transactions,
            categoryStats = categoryStats,
            dailyStats = dailyStats,
        )
    }
}

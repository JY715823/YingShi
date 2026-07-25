package com.example.yingshi.feature.ledger.data

import com.example.yingshi.data.remote.dto.DeletedRowRefDto
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.util.UUID

class LedgerRepository(
    internal val dao: LedgerDao,
    private val syncBridge: LedgerSyncBridge = NoOpLedgerSyncBridge,
    private val currentUserIdProvider: () -> String? = { null },
) {
    companion object {
        private const val CHANGELOG_MAX_ENTRIES = 1000
        private const val CHANGELOG_TRIM_MARGIN = 100
        private const val CHANGELOG_HARD_CAP = 2000
    }
    val shouldSeedDemoData: Boolean
        get() = syncBridge.shouldSeedDemoData

    val syncErrors: Flow<String>
        get() = syncBridge.syncErrors

    suspend fun ensureSeedData(nowMillis: Long = System.currentTimeMillis()) {
        val currentUserId = currentUserIdProvider()?.takeIf { it.isNotBlank() }
        LedgerSeedData.defaultBooks(
            nowMillis = nowMillis,
            creatorUserId = currentUserId,
        ).forEach { book ->
            if (dao.getBook(book.id) == null) {
                dao.insertBook(book)
                // 记录 changelog，确保种子账本同步到服务端，避免后续交易因 bookId 不存在而 500
                recordChangelog(LedgerSyncChangelogEntity(tableName = "books", rowId = book.id))
            }
        }
        if (currentUserId != null) {
            dao.getAllBooks().forEach { book ->
                if (book.creatorUserId.isNullOrBlank()) {
                    dao.updateBook(book.copy(creatorUserId = currentUserId, updatedAtMillis = nowMillis))
                    recordChangelog(LedgerSyncChangelogEntity(tableName = "books", rowId = book.id))
                }
            }
        }
        LedgerSeedData.defaultAccounts(nowMillis).forEach { account ->
            if (dao.getAccount(account.id) == null) {
                dao.insertAccount(account)
                recordChangelog(LedgerSyncChangelogEntity(tableName = "accounts", rowId = account.id))
            }
        }
        LedgerSeedData.defaultCategories(nowMillis).forEach { category ->
            if (dao.getCategory(category.id) == null) {
                dao.insertCategory(category)
                recordChangelog(LedgerSyncChangelogEntity(tableName = "categories", rowId = category.id))
            }
        }
        backfillMissingBookCreatorUserIds(nowMillis)
    }

    suspend fun ensureDemoData(nowMillis: Long = System.currentTimeMillis()) {
        if (dao.countTransactionsByBook(LedgerSeedData.DefaultBookId) == 0) {
            seedDemoTransactions(nowMillis)
        }
    }

    suspend fun hydrateFromBackendIfNeeded() {
        syncBridge.hydrate(this)
        backfillMissingBookCreatorUserIds()
    }

    suspend fun backfillSeedChangelogIfNeeded() {
        syncBridge.backfillSeedChangelogIfNeeded(this)
    }

    suspend fun upsertSyncResponse(snapshot: LedgerLocalSnapshot) {
        snapshot.books.forEach { dao.upsertBook(it) }
        snapshot.categories.forEach { dao.upsertCategory(it) }
        snapshot.accounts.forEach { dao.upsertAccount(it) }
        snapshot.transactions.forEach { dao.upsertTransaction(it) }
        snapshot.budgets.forEach { dao.upsertBudget(it) }
        snapshot.categoryBudgets.forEach { dao.upsertCategoryBudget(it) }
        snapshot.deletedItems.forEach { dao.upsertDeletedItem(it) }
        snapshot.recurringRules.forEach { dao.upsertRecurringRule(it) }
        snapshot.recurringOccurrences.forEach { dao.upsertRecurringOccurrence(it) }
    }

    suspend fun deleteSyncRows(deletedRows: List<DeletedRowRefDto>) {
        // FR-3: 7 tables use soft delete (set deletedAtMillis + updatedAtMillis) so that
        // business queries filter them out AND the next outbound sync propagates the
        // tombstone. books keeps hard delete (uses isDeleted boolean archive).
        // deleted_items keeps hard delete (deletedAtMillis is a business NOT NULL field).
        val now = System.currentTimeMillis()
        deletedRows.forEach { ref ->
            when (ref.table) {
                "books" -> dao.hardDeleteBook(ref.id)
                "categories" -> dao.softDeleteCategory(ref.id, now, now)
                "accounts" -> dao.softDeleteAccount(ref.id, now, now)
                "transactions" -> dao.softDeleteTransactionForSync(ref.id, now, now)
                "budgets" -> dao.softDeleteBudget(ref.id, now, now)
                "category_budgets" -> dao.softDeleteCategoryBudget(ref.id, now, now)
                "deleted_items" -> dao.hardDeleteDeletedItem(ref.id)
                "recurring_rules" -> dao.softDeleteRecurringRule(ref.id, now, now)
                "recurring_occurrences" -> dao.softDeleteRecurringOccurrence(ref.id, now, now)
            }
        }
    }

    /**
     * FR-22: 写入 changelog 并执行上限保护。
     * - 超 MAX_ENTRIES(1000) 时删除最旧的 TRIM_MARGIN(100) 条非删除条目（保留 tombstone）
     * - 全 isDelete=true 且仍超 HARD_CAP(2000) 时兜底删除最旧条目
     */
    private suspend fun recordChangelog(entry: LedgerSyncChangelogEntity) {
        dao.insertChangelogEntry(entry)
        val count = dao.countChangelogEntries()
        if (count > CHANGELOG_MAX_ENTRIES) {
            val overflow = count - CHANGELOG_MAX_ENTRIES + CHANGELOG_TRIM_MARGIN
            dao.deleteOldestNonDeleteChangelog(overflow)
            // 兜底：全 isDelete=true 时仍超限
            val remaining = dao.countChangelogEntries()
            if (remaining > CHANGELOG_HARD_CAP) {
                dao.deleteOldestChangelog(remaining - CHANGELOG_MAX_ENTRIES)
            }
        }
    }

    suspend fun backfillMissingBookCreatorUserIds(
        nowMillis: Long = System.currentTimeMillis(),
    ) {
        val currentUserId = currentUserIdProvider()?.takeIf { it.isNotBlank() } ?: return
        dao.getAllBooks().forEach { book ->
            if (book.creatorUserId.isNullOrBlank()) {
                dao.updateBook(book.copy(creatorUserId = currentUserId, updatedAtMillis = nowMillis))
            }
        }
    }

    fun observeBooks(): Flow<List<LedgerBook>> = dao.observeBooks().map { rows ->
        rows.map { it.toDomain() }
    }

    fun observeArchivedBooks(): Flow<List<LedgerBook>> = dao.observeArchivedBooks().map { rows ->
        rows.map { it.toDomain() }
    }

    fun observeBook(bookId: String): Flow<LedgerBook?> = dao.observeBook(bookId).map { it?.toDomain() }

    fun observeVisibleCategories(bookId: String): Flow<List<LedgerCategory>> =
        dao.observeVisibleCategories(bookId).map { rows -> rows.map { it.toDomain() } }

    fun observeAllCategories(bookId: String): Flow<List<LedgerCategory>> =
        dao.observeAllCategories(bookId).map { rows -> rows.map { it.toDomain() } }

    fun observeAllVisibleAccounts(): Flow<List<LedgerAccount>> =
        dao.observeVisibleAccountsAcrossBooks().map { rows -> rows.map { it.toDomain() } }

    fun observeAllAccountsGlobally(): Flow<List<LedgerAccount>> =
        dao.observeAllAccountsGlobally().map { rows -> rows.map { it.toDomain() } }

    fun observeTransactions(bookId: String): Flow<List<LedgerTransaction>> = combine(
        dao.observeTransactions(bookId),
        dao.observeAllCategories(bookId),
        dao.observeAllAccountsGlobally(),
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
        dao.observeAllAccountsGlobally(),
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

    fun observeSearch(bookId: String, filter: LedgerSearchFilter): Flow<List<LedgerTransaction>> {
        val keyword = filter.keyword.trim()
        val typeFilter = if (filter.type == LedgerSearchTransactionType.ALL) "" else filter.type.name
        val categoryId = filter.categoryId.orEmpty()
        val accountId = filter.accountId.orEmpty()
        val startDateMillis = filter.startDate?.atStartOfDay(ZoneId.systemDefault())?.toInstant()?.toEpochMilli() ?: -1L
        val endDateMillis = filter.endDate?.plusDays(1)?.atStartOfDay(ZoneId.systemDefault())?.toInstant()?.toEpochMilli()?.minus(1) ?: -1L
        val minAmountCents = filter.minAmountCents ?: -1L
        val maxAmountCents = filter.maxAmountCents ?: -1L
        return combine(
            dao.searchTransactionsFiltered(
                bookId = bookId,
                keyword = keyword,
                typeFilter = typeFilter,
                categoryId = categoryId,
                accountId = accountId,
                startDateMillis = startDateMillis,
                endDateMillis = endDateMillis,
                minAmountCents = minAmountCents,
                maxAmountCents = maxAmountCents,
            ),
            dao.observeAllCategories(bookId),
            dao.observeAllAccountsGlobally(),
        ) { transactions, categories, accounts ->
            mapTransactions(
                transactions = transactions,
                categories = categories,
                accounts = accounts,
            )
        }
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
        dao.observeAllAccountsGlobally(),
    ) { rules, categories, accounts ->
        val categoriesById = categories.associateBy({ it.id }, { it.toDomain() })
        val accountsById = accounts.associateBy({ it.id }, { it.toDomain() })
        rules.map { it.toDomain(categoriesById, accountsById) }
    }

    suspend fun saveBook(draft: LedgerBookDraft): String {
        val now = System.currentTimeMillis()
        val existing = draft.id?.let { dao.getBook(it) }
        val normalizedName = draft.name.trim()
        return if (existing == null) {
            val bookId = UUID.randomUUID().toString()
            val nextSortOrder = dao.observeAllBooks().first().maxOfOrNull { it.sortOrder }?.plus(1) ?: 0
            val template = draft.template.ifBlank { LedgerBookTemplateDaily }
            val creatorUserId = draft.creatorUserId ?: currentUserIdProvider()
            dao.insertBook(
                LedgerBookEntity(
                    id = bookId,
                    name = normalizedName,
                    creatorUserId = creatorUserId,
                    template = template,
                    currencyCode = "CNY",
                    currencySymbol = "¥",
                    coverColor = draft.coverColor,
                    sortOrder = nextSortOrder,
                    createdAtMillis = now,
                    updatedAtMillis = now,
                ),
            )
            val seedCategories = LedgerSeedData.seedCategoriesForTemplate(bookId, template, now)
            dao.insertCategories(seedCategories)
            recordChangelog(LedgerSyncChangelogEntity(tableName = "books", rowId = bookId))
            seedCategories.forEach { recordChangelog(LedgerSyncChangelogEntity(tableName = "categories", rowId = it.id)) }
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
            recordChangelog(LedgerSyncChangelogEntity(tableName = "books", rowId = existing.id))
            syncBridge.afterMutation(this)
            existing.id
        }
    }

    suspend fun setBookArchived(bookId: String, archived: Boolean) {
        dao.setBookArchived(bookId, archived, System.currentTimeMillis())
        recordChangelog(LedgerSyncChangelogEntity(tableName = "books", rowId = bookId))
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
                recordChangelog(LedgerSyncChangelogEntity(tableName = "categories", rowId = categoryId))
            }
        }
        syncBridge.afterMutation(this)
    }

    suspend fun reorderAccounts(orderedIds: List<String>) {
        val now = System.currentTimeMillis()
        val accounts = dao.getAllAccountsGlobally()
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
                recordChangelog(LedgerSyncChangelogEntity(tableName = "accounts", rowId = accountId))
            }
        }
        syncBridge.afterMutation(this)
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
        // 防御：bookId / accountId 为 NOT NULL，空值兜底到默认账本
        val resolvedBookId = draft.bookId.takeIf { it.isNotBlank() } ?: defaultBookId()
        val entity = LedgerRecurringRuleEntity(
            id = existing?.id ?: UUID.randomUUID().toString(),
            bookId = resolvedBookId,
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
        recordChangelog(LedgerSyncChangelogEntity(tableName = "recurring_rules", rowId = entity.id))
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
        recordChangelog(LedgerSyncChangelogEntity(tableName = "recurring_rules", rowId = ruleId))
        syncBridge.afterMutation(this)
    }

    suspend fun deleteRecurringRule(ruleId: String) {
        dao.deleteRecurringOccurrencesByRuleId(ruleId)
        dao.deleteRecurringRule(ruleId)
        recordChangelog(LedgerSyncChangelogEntity(tableName = "recurring_rules", rowId = ruleId, isDelete = true))
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
                    updatedAtMillis = nowMillis,
                )
                val occurrenceExists = dao.getRecurringOccurrence(cursor.id, occurrenceAtMillis) != null
                if (!occurrenceExists) {
                    dao.insertRecurringOccurrence(occurrence)
                    recordChangelog(LedgerSyncChangelogEntity(tableName = "recurring_occurrences", rowId = occurrenceId))
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
                    recordChangelog(LedgerSyncChangelogEntity(tableName = "transactions", rowId = transactionId))
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
                recordChangelog(LedgerSyncChangelogEntity(tableName = "recurring_rules", rowId = cursor.id))
                changed = true
            }
            if (changed) {
                syncBridge.afterMutation(this)
            }
        }
    }

    suspend fun saveTransaction(draft: LedgerTransactionDraft) {
        val now = System.currentTimeMillis()
        // 防御：若 UI 传入空 bookId（如刚归档最后一个账本后 currentBookId=""），
        // 回退到默认账本，避免服务端 FK 约束违反触发 500 死循环。
        val resolvedBookId = draft.bookId.takeIf { it.isNotBlank() } ?: defaultBookId()
        val entity = LedgerTransactionEntity(
            id = draft.id ?: UUID.randomUUID().toString(),
            bookId = resolvedBookId,
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
        recordChangelog(LedgerSyncChangelogEntity(tableName = "transactions", rowId = entity.id))
        syncBridge.afterMutation(this)
    }

    suspend fun saveTransactions(drafts: List<LedgerTransactionDraft>) {
        if (drafts.isEmpty()) return
        val now = System.currentTimeMillis()
        // 批量导入同样需要 bookId 兜底，避免个别 draft 携带空 bookId
        val fallbackBookId = defaultBookId()
        val entities = drafts.map { draft ->
            LedgerTransactionEntity(
                id = draft.id ?: UUID.randomUUID().toString(),
                bookId = draft.bookId.takeIf { it.isNotBlank() } ?: fallbackBookId,
                categoryId = draft.categoryId,
                accountId = draft.accountId,
                toAccountId = draft.toAccountId,
                amountCents = draft.amountCents,
                type = draft.type,
                occurredAtMillis = draft.occurredAtMillis,
                remark = draft.remark.trim(),
                method = "import",
                createdAtMillis = now,
                updatedAtMillis = now,
            )
        }
        dao.insertTransactions(entities)
        entities.forEach { entity ->
            recordChangelog(LedgerSyncChangelogEntity(tableName = "transactions", rowId = entity.id))
        }
        syncBridge.afterMutation(this)
    }

    suspend fun saveCategory(draft: LedgerCategoryDraft) {
        val now = System.currentTimeMillis()
        val existing = draft.id?.let { dao.getCategory(it) }
        // 防御：bookId NOT NULL + FK，空值兜底到默认账本
        val resolvedBookId = draft.bookId.takeIf { it.isNotBlank() } ?: defaultBookId()
        val sameTypeCategories = dao.observeAllCategories(resolvedBookId).first()
            .filter { it.type == draft.type }

        // "其他" 分类始终置底：新增分类时插入到 "其他" 前面（同 book + 同 type）
        // 识别规则：名称为 "其他"（不区分大小写）或 id 以 "cat-other-" 开头
        val otherCategory = sameTypeCategories.firstOrNull { existingCat ->
            existingCat.name.trim().equals("其他", ignoreCase = true) ||
                existingCat.id.startsWith("cat-other-")
        }

        val sortOrder = if (existing != null) {
            existing.sortOrder
        } else when {
            // 找到 "其他"：插入到它前面，把它和后面所有项后移 +1
            otherCategory != null -> {
                val insertAt = otherCategory.sortOrder
                sameTypeCategories
                    .filter { it.sortOrder >= insertAt && it.id != otherCategory.id }
                    .forEach { cat ->
                        dao.updateCategory(
                            cat.copy(
                                sortOrder = cat.sortOrder + 1,
                                updatedAtMillis = now,
                            ),
                        )
                        recordChangelog(LedgerSyncChangelogEntity(tableName = "categories", rowId = cat.id))
                    }
                // "其他" 自己也后移
                dao.updateCategory(
                    otherCategory.copy(
                        sortOrder = otherCategory.sortOrder + 1,
                        updatedAtMillis = now,
                    ),
                )
                recordChangelog(LedgerSyncChangelogEntity(tableName = "categories", rowId = otherCategory.id))
                insertAt
            }
            else -> sameTypeCategories.maxOfOrNull { it.sortOrder }?.plus(1) ?: 0
        }
        val entity = LedgerCategoryEntity(
            id = existing?.id ?: UUID.randomUUID().toString(),
            bookId = resolvedBookId,
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
        recordChangelog(LedgerSyncChangelogEntity(tableName = "categories", rowId = entity.id))
        syncBridge.afterMutation(this)
    }

    suspend fun setCategoryHidden(categoryId: String, hidden: Boolean) {
        dao.setCategoryHidden(categoryId, hidden, System.currentTimeMillis())
        recordChangelog(LedgerSyncChangelogEntity(tableName = "categories", rowId = categoryId))
        syncBridge.afterMutation(this)
    }

    suspend fun saveAccount(draft: LedgerAccountDraft) {
        val now = System.currentTimeMillis()
        val existing = draft.id?.let { dao.getAccount(it) }
        val sortOrder = existing?.sortOrder ?: (
            dao.getAllAccountsGlobally()
                .maxOfOrNull { it.sortOrder }?.plus(1) ?: 0
            )
        val iconKey = defaultAccountIconKey(draft.type)
        val color = defaultAccountColor(draft.type)
        val balanceCents = if (existing == null) {
            draft.initialBalanceCents
        } else {
            existing.balanceCents + (draft.initialBalanceCents - existing.initialBalanceCents)
        }
        // 修复：bookId 不能为空字符串，否则服务端 ledger_accounts.book_id 的
        // NOT NULL + FK 约束会在事务提交阶段抛 ConstraintViolationException，
        // 被包装为 TransactionSystemException 后被 generic handler 捕获返回 500，
        // 表现为"同步失败（服务器错误）"。
        // V5_6 客户端将账号与账本解耦（UI 不再绑定），但服务端 schema 仍保留 FK，
        // 因此客户端需写入一个有效的 bookId（默认账本）以满足约束。
        val resolvedBookId = existing?.bookId?.takeIf { it.isNotBlank() }
            ?: defaultBookId()
        val entity = LedgerAccountEntity(
            id = existing?.id ?: UUID.randomUUID().toString(),
            bookId = resolvedBookId,
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
            ownerUserId = draft.ownerUserId,
            bankKey = draft.bankKey,
            bankName = draft.bankName,
            cardNumberTail = draft.cardNumberTail,
            sortOrder = sortOrder,
            createdAtMillis = existing?.createdAtMillis ?: now,
            updatedAtMillis = now,
        )
        if (existing == null) {
            dao.insertAccount(entity)
        } else {
            dao.updateAccount(entity)
        }
        recordChangelog(LedgerSyncChangelogEntity(tableName = "accounts", rowId = entity.id))
        syncBridge.afterMutation(this)
    }

    suspend fun setAccountHidden(accountId: String, hidden: Boolean) {
        dao.setAccountHidden(accountId, hidden, System.currentTimeMillis())
        recordChangelog(LedgerSyncChangelogEntity(tableName = "accounts", rowId = accountId))
        syncBridge.afterMutation(this)
    }

    suspend fun deleteAccount(accountId: String) {
        val now = System.currentTimeMillis()
        dao.softDeleteAccount(accountId, now, now)
        recordChangelog(LedgerSyncChangelogEntity(tableName = "accounts", rowId = accountId))
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
        recordChangelog(LedgerSyncChangelogEntity(tableName = "transactions", rowId = transactionId))
        syncBridge.afterMutation(this)
    }

    suspend fun restoreTransaction(transactionId: String) {
        dao.restoreTransaction(transactionId, System.currentTimeMillis())
        recordChangelog(LedgerSyncChangelogEntity(tableName = "transactions", rowId = transactionId))
        syncBridge.afterMutation(this)
    }

    suspend fun permanentlyDeleteTransaction(transactionId: String) {
        dao.hardDeleteTransaction(transactionId)
        dao.deleteDeletedItemByItemId(transactionId)
        recordChangelog(LedgerSyncChangelogEntity(tableName = "transactions", rowId = transactionId, isDelete = true))
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
            recordChangelog(LedgerSyncChangelogEntity(tableName = "transactions", rowId = transaction.id))
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
            recordChangelog(LedgerSyncChangelogEntity(tableName = "transactions", rowId = transaction.id))
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
            recordChangelog(LedgerSyncChangelogEntity(tableName = "transactions", rowId = transaction.id))
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
            val budgetId = UUID.randomUUID().toString()
            dao.insertBudget(
                LedgerBudgetEntity(
                    id = budgetId,
                    bookId = bookId,
                    period = period,
                    startMillis = range.startMillis,
                    endMillis = range.endMillis,
                    totalAmountCents = amountCents,
                    createdAtMillis = now,
                    updatedAtMillis = now,
                ),
            )
            recordChangelog(LedgerSyncChangelogEntity(tableName = "budgets", rowId = budgetId))
        } else {
            dao.updateBudget(
                existing.copy(
                    totalAmountCents = amountCents,
                    updatedAtMillis = now,
                ),
            )
            recordChangelog(LedgerSyncChangelogEntity(tableName = "budgets", rowId = existing.id))
        }
        syncBridge.afterMutation(this)
    }

    suspend fun upsertCategoryBudget(
        budget: LedgerBudget,
        categoryId: String,
        amountCents: Long,
    ): Boolean {
        val now = System.currentTimeMillis()
        val currentBudget = dao.getBudget(budget.id) ?: return false
        val rows = dao.getCategoryBudgets(budget.id)
        val existing = rows.firstOrNull { it.categoryId == categoryId }

        // FR-18: 预计算分类预算总额，超额则拒绝写入（不再自动调高总预算）
        val previousAmount = existing?.amountCents ?: 0L
        val projectedTotal = dao.categoryBudgetTotal(budget.id) - previousAmount + amountCents
        if (projectedTotal > currentBudget.totalAmountCents) {
            return false
        }

        if (existing == null) {
            val categoryBudgetId = UUID.randomUUID().toString()
            dao.insertCategoryBudget(
                LedgerCategoryBudgetEntity(
                    id = categoryBudgetId,
                    budgetId = budget.id,
                    categoryId = categoryId,
                    amountCents = amountCents,
                    createdAtMillis = now,
                    updatedAtMillis = now,
                ),
            )
            recordChangelog(LedgerSyncChangelogEntity(tableName = "category_budgets", rowId = categoryBudgetId))
        } else {
            dao.updateCategoryBudget(
                existing.copy(
                    amountCents = amountCents,
                    updatedAtMillis = now,
                ),
            )
            recordChangelog(LedgerSyncChangelogEntity(tableName = "category_budgets", rowId = existing.id))
        }
        syncBridge.afterMutation(this)
        return true
    }

    suspend fun clearBudget(budgetId: String) {
        dao.deleteCategoryBudgetsByBudgetId(budgetId)
        dao.deleteBudget(budgetId)
        recordChangelog(LedgerSyncChangelogEntity(tableName = "budgets", rowId = budgetId, isDelete = true))
        syncBridge.afterMutation(this)
    }

    suspend fun clearCategoryBudget(budgetId: String, categoryId: String) {
        val existing = dao.getCategoryBudgets(budgetId).firstOrNull { it.categoryId == categoryId }
        dao.deleteCategoryBudgetByCategory(budgetId, categoryId)
        if (existing != null) {
            recordChangelog(LedgerSyncChangelogEntity(tableName = "category_budgets", rowId = existing.id, isDelete = true))
        }
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
                accountId = transaction.accountId,
                toAccountId = transaction.toAccountId,
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

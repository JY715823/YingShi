package com.example.yingshi.feature.ledger.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        LedgerBookEntity::class,
        LedgerCategoryEntity::class,
        LedgerAccountEntity::class,
        LedgerTransactionEntity::class,
        LedgerBudgetEntity::class,
        LedgerCategoryBudgetEntity::class,
        LedgerDeletedItemEntity::class,
        LedgerRecurringRuleEntity::class,
        LedgerRecurringOccurrenceEntity::class,
        LedgerSyncChangelogEntity::class,
    ],
    version = 4,
    exportSchema = false,
)
@TypeConverters(LedgerTypeConverters::class)
abstract class LedgerDatabase : RoomDatabase() {
    abstract fun ledgerDao(): LedgerDao

    companion object {
        @Volatile
        private var instance: LedgerDatabase? = null

        fun getInstance(context: Context): LedgerDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    LedgerDatabase::class.java,
                    "yingshi-ledger.db",
                ).addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4).build().also { instance = it }
            }
        }
    }
}

val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS ledger_recurring_rules (
                id TEXT NOT NULL PRIMARY KEY,
                bookId TEXT NOT NULL,
                type TEXT NOT NULL,
                categoryId TEXT,
                accountId TEXT NOT NULL,
                toAccountId TEXT,
                amountCents INTEGER NOT NULL,
                remark TEXT NOT NULL,
                frequency TEXT NOT NULL,
                startAtMillis INTEGER NOT NULL,
                endAtMillis INTEGER,
                nextOccurrenceAtMillis INTEGER NOT NULL,
                enabled INTEGER NOT NULL,
                createdAtMillis INTEGER NOT NULL,
                updatedAtMillis INTEGER NOT NULL
            )
            """.trimIndent(),
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS index_ledger_recurring_rules_bookId_enabled_nextOccurrenceAtMillis ON ledger_recurring_rules(bookId, enabled, nextOccurrenceAtMillis)",
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS index_ledger_recurring_rules_bookId_type ON ledger_recurring_rules(bookId, type)")
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS ledger_recurring_occurrences (
                id TEXT NOT NULL PRIMARY KEY,
                ruleId TEXT NOT NULL,
                transactionId TEXT NOT NULL,
                occurrenceAtMillis INTEGER NOT NULL,
                createdAtMillis INTEGER NOT NULL
            )
            """.trimIndent(),
        )
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_ledger_recurring_occurrences_ruleId_occurrenceAtMillis ON ledger_recurring_occurrences(ruleId, occurrenceAtMillis)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_ledger_recurring_occurrences_transactionId ON ledger_recurring_occurrences(transactionId)")
    }
}

val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE ledger_books ADD COLUMN creatorUserId TEXT")
    }
}

val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS ledger_sync_changelog (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                tableName TEXT NOT NULL,
                rowId TEXT NOT NULL,
                isDelete INTEGER NOT NULL DEFAULT 0,
                changedAtMillis INTEGER NOT NULL
            )
            """.trimIndent(),
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS index_ledger_sync_changelog_tableName ON ledger_sync_changelog(tableName)")
    }
}

class LedgerTypeConverters {
    @TypeConverter
    fun transactionTypeToString(value: LedgerTransactionType): String = value.name

    @TypeConverter
    fun stringToTransactionType(value: String): LedgerTransactionType =
        LedgerTransactionType.entries.firstOrNull { it.name == value } ?: LedgerTransactionType.EXPENSE

    @TypeConverter
    fun categoryTypeToString(value: LedgerCategoryType): String = value.name

    @TypeConverter
    fun stringToCategoryType(value: String): LedgerCategoryType =
        LedgerCategoryType.entries.firstOrNull { it.name == value } ?: LedgerCategoryType.EXPENSE

    @TypeConverter
    fun accountTypeToString(value: LedgerAccountType): String = value.name

    @TypeConverter
    fun stringToAccountType(value: String): LedgerAccountType =
        LedgerAccountType.entries.firstOrNull { it.name == value } ?: LedgerAccountType.OTHER

    @TypeConverter
    fun budgetPeriodToString(value: LedgerBudgetPeriod): String = value.name

    @TypeConverter
    fun stringToBudgetPeriod(value: String): LedgerBudgetPeriod =
        LedgerBudgetPeriod.entries.firstOrNull { it.name == value } ?: LedgerBudgetPeriod.MONTH

    @TypeConverter
    fun deletedItemTypeToString(value: LedgerDeletedItemType): String = value.name

    @TypeConverter
    fun stringToDeletedItemType(value: String): LedgerDeletedItemType =
        LedgerDeletedItemType.entries.firstOrNull { it.name == value } ?: LedgerDeletedItemType.TRANSACTION

    @TypeConverter
    fun recurringFrequencyToString(value: LedgerRecurringFrequency): String = value.name

    @TypeConverter
    fun stringToRecurringFrequency(value: String): LedgerRecurringFrequency =
        LedgerRecurringFrequency.entries.firstOrNull { it.name == value } ?: LedgerRecurringFrequency.DAILY
}

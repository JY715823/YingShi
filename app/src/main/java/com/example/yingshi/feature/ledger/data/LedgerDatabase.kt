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
    ],
    version = 2,
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
                ).addMigrations(MIGRATION_1_2).build().also { instance = it }
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

class LedgerTypeConverters {
    @TypeConverter
    fun transactionTypeToString(value: LedgerTransactionType): String = value.name

    @TypeConverter
    fun stringToTransactionType(value: String): LedgerTransactionType =
        LedgerTransactionType.valueOf(value)

    @TypeConverter
    fun categoryTypeToString(value: LedgerCategoryType): String = value.name

    @TypeConverter
    fun stringToCategoryType(value: String): LedgerCategoryType =
        LedgerCategoryType.valueOf(value)

    @TypeConverter
    fun accountTypeToString(value: LedgerAccountType): String = value.name

    @TypeConverter
    fun stringToAccountType(value: String): LedgerAccountType =
        LedgerAccountType.valueOf(value)

    @TypeConverter
    fun budgetPeriodToString(value: LedgerBudgetPeriod): String = value.name

    @TypeConverter
    fun stringToBudgetPeriod(value: String): LedgerBudgetPeriod =
        LedgerBudgetPeriod.valueOf(value)

    @TypeConverter
    fun deletedItemTypeToString(value: LedgerDeletedItemType): String = value.name

    @TypeConverter
    fun stringToDeletedItemType(value: String): LedgerDeletedItemType =
        LedgerDeletedItemType.valueOf(value)

    @TypeConverter
    fun recurringFrequencyToString(value: LedgerRecurringFrequency): String = value.name

    @TypeConverter
    fun stringToRecurringFrequency(value: String): LedgerRecurringFrequency =
        LedgerRecurringFrequency.valueOf(value)
}

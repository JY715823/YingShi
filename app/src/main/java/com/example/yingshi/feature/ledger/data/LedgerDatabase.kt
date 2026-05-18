package com.example.yingshi.feature.ledger.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters

@Database(
    entities = [
        LedgerBookEntity::class,
        LedgerCategoryEntity::class,
        LedgerAccountEntity::class,
        LedgerTransactionEntity::class,
        LedgerBudgetEntity::class,
        LedgerCategoryBudgetEntity::class,
        LedgerDeletedItemEntity::class,
    ],
    version = 1,
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
                ).build().also { instance = it }
            }
        }
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
}

package com.example.yingshi.feature.ledger.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "ledger_sync_changelog",
    indices = [Index(value = ["tableName"])],
)
data class LedgerSyncChangelogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val tableName: String,
    val rowId: String,
    val isDelete: Boolean = false,
    val changedAtMillis: Long = System.currentTimeMillis(),
)

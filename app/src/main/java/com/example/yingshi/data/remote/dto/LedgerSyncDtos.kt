package com.example.yingshi.data.remote.dto

import com.google.gson.JsonElement

data class LedgerSyncRequestDto(
    val lastSyncVersionMillis: Long,
    val changes: LedgerChangesDto,
)

data class LedgerSyncResponseDto(
    val versionMillis: Long,
    val changes: LedgerChangesDto,
    val rejectedRowIds: List<RejectedRowRefDto>? = null,
)

data class RejectedRowRefDto(
    val table: String,
    val id: String?,
    val reason: String,
)

data class LedgerChangesDto(
    val books: List<JsonElement> = emptyList(),
    val categories: List<JsonElement> = emptyList(),
    val accounts: List<JsonElement> = emptyList(),
    val transactions: List<JsonElement> = emptyList(),
    val budgets: List<JsonElement> = emptyList(),
    val categoryBudgets: List<JsonElement> = emptyList(),
    val deletedItems: List<JsonElement> = emptyList(),
    val recurringRules: List<JsonElement> = emptyList(),
    val recurringOccurrences: List<JsonElement> = emptyList(),
    val deletedRowIds: List<DeletedRowRefDto> = emptyList(),
)

data class DeletedRowRefDto(
    val table: String,
    val id: String,
)

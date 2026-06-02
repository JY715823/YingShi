package com.example.yingshi.data.remote.dto

import com.google.gson.JsonElement

data class LedgerSnapshotDto(
    val versionMillis: Long = 0L,
    val payload: JsonElement? = null,
)

data class UpsertLedgerSnapshotRequestDto(
    val payload: JsonElement,
)

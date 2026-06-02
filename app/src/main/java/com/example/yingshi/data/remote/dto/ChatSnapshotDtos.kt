package com.example.yingshi.data.remote.dto

import com.google.gson.JsonElement

data class ChatSnapshotDto(
    val versionMillis: Long = 0L,
    val payload: JsonElement? = null,
)

data class UpsertChatSnapshotRequestDto(
    val payload: JsonElement,
)

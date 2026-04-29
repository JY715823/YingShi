package com.example.yingshi.data.remote.dto

data class TrashItemDto(
    val trashItemId: String,
    val itemType: String,
    val sourcePostId: String? = null,
    val sourceMediaId: String? = null,
    val title: String,
    val previewInfo: String,
    val deletedAtMillis: Long,
    val relatedPostIds: List<String> = emptyList(),
    val relatedMediaIds: List<String> = emptyList(),
)

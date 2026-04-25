package com.example.yingshi.feature.photos

import androidx.compose.runtime.Immutable

enum class TrashEntryType(
    val label: String,
    val summary: String,
) {
    POST_DELETED(
        label = "帖子删除",
        summary = "仅删除帖子本体，后续恢复与详情查看放到 Stage 7.2。",
    ),
    MEDIA_REMOVED(
        label = "媒体移除",
        summary = "只移除帖子与媒体的目录关系，媒体本体和媒体评论仍保留。",
    ),
    MEDIA_SYSTEM_DELETED(
        label = "媒体系统删",
        summary = "从全局媒体流和所有帖子中本地隐藏，正式回收站语义后续继续补齐。",
    ),
}

@Immutable
data class TrashEntryUiModel(
    val id: String,
    val type: TrashEntryType,
    val deletedAtMillis: Long,
    val title: String,
    val previewInfo: String,
    val sourcePostId: String? = null,
    val sourceMediaId: String? = null,
    val relatedPostIds: List<String> = emptyList(),
    val relatedMediaIds: List<String> = emptyList(),
    val palette: PhotoThumbnailPalette,
)

@Immutable
data class TrashMediaSnapshot(
    val mediaId: String,
    val displayTimeMillis: Long,
    val palette: PhotoThumbnailPalette,
    val sourcePostId: String? = null,
    val sourcePostTitle: String? = null,
)

package com.example.yingshi.feature.photos

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.graphics.Color

object FakeAlbumRepository {
    enum class MediaDeleteSemantic {
        DIRECTORY_ONLY,
        SYSTEM_WIDE,
    }

    data class MediaDeleteOutcome(
        val deletedPostIds: Set<String> = emptySet(),
        val affectedPostIds: Set<String> = emptySet(),
    )

    data class PostSystemDeleteImpact(
        val mediaCount: Int,
        val sharedMediaCount: Int,
        val affectedOtherPostCount: Int,
    )

    private data class ManagedPostMediaState(
        val id: String,
        val displayTimeMillis: Long,
        val commentCount: Int,
        val palette: PhotoThumbnailPalette,
        val aspectRatio: Float,
        val width: Int? = null,
        val height: Int? = null,
        val isCover: Boolean,
    )

    private val albums = listOf(
        AlbumSummaryUiModel(
            id = "album-spring-window",
            title = "春天窗边",
            subtitle = "光线、桌面和慢一点的日常",
            accent = PhotoThumbnailPalette(
                start = Color(0xFFF3E8DA),
                end = Color(0xFFD8E3D3),
                accent = Color(0xFFB78C61),
            ),
        ),
        AlbumSummaryUiModel(
            id = "album-city-night",
            title = "夜晚散步",
            subtitle = "晚归路上收集到的片段",
            accent = PhotoThumbnailPalette(
                start = Color(0xFF24364C),
                end = Color(0xFF465C7A),
                accent = Color(0xFFF2C98B),
            ),
        ),
        AlbumSummaryUiModel(
            id = "album-weekend-table",
            title = "周末餐桌",
            subtitle = "一起吃饭时留下的小记录",
            accent = PhotoThumbnailPalette(
                start = Color(0xFFEAD8C7),
                end = Color(0xFFC9D6C4),
                accent = Color(0xFF8A6A4C),
            ),
        ),
        AlbumSummaryUiModel(
            id = "album-small-trip",
            title = "短途小游",
            subtitle = "走出去之后才会想记住的几帧",
            accent = PhotoThumbnailPalette(
                start = Color(0xFFD9E6EA),
                end = Color(0xFFBCCED8),
                accent = Color(0xFF577C8C),
            ),
        ),
        AlbumSummaryUiModel(
            id = "album-new-year",
            title = "新年碎片",
            subtitle = "节日气氛和一点点仪式感",
            accent = PhotoThumbnailPalette(
                start = Color(0xFFF1DFCF),
                end = Color(0xFFE2C9BD),
                accent = Color(0xFFAD6F5B),
            ),
        ),
    )

    private val posts = mutableStateListOf<AlbumPostCardUiModel>().apply {
        addAll(seedPosts().sortedByDescending { it.postDisplayTimeMillis })
    }
    private val postMediaByPostId = mutableStateMapOf<String, androidx.compose.runtime.snapshots.SnapshotStateList<ManagedPostMediaState>>()

    fun getAlbums(): List<AlbumSummaryUiModel> = albums

    fun getPosts(): List<AlbumPostCardUiModel> = posts

    fun getPost(postId: String): AlbumPostCardUiModel? {
        return posts.firstOrNull { it.id == postId }
    }

    fun createLocalPostFromSystemMedia(
        mediaItems: List<SystemMediaItem>,
    ): AlbumPostCardUiModel? {
        val normalizedMedia = mediaItems
            .distinctBy { it.id }
            .sortedByDescending { it.displayTimeMillis }
            .mapIndexed { index, item ->
                item.toManagedState(isCover = index == 0)
            }
        if (normalizedMedia.isEmpty()) return null

        val primaryAlbum = albums.first()
        val coverMedia = normalizedMedia.first()
        val postTime = normalizedMedia.maxOf { it.displayTimeMillis }
        val postId = "post-system-import-$postTime-${posts.size + 1}"
        val post = AlbumPostCardUiModel(
            id = postId,
            albumId = primaryAlbum.id,
            albumIds = listOf(primaryAlbum.id),
            title = buildSystemImportTitle(
                mediaCount = normalizedMedia.size,
                displayTimeMillis = postTime,
            ),
            summary = "从系统媒体工具区加入的本地占位帖子",
            postDisplayTimeMillis = postTime,
            mediaCount = normalizedMedia.size,
            coverPalette = coverMedia.palette,
            coverAspectRatio = coverMedia.aspectRatio,
        )
        postMediaByPostId[postId] = mutableStateListOf<ManagedPostMediaState>().apply {
            addAll(normalizedMedia)
        }
        posts.add(post)
        posts.sortByDescending { it.postDisplayTimeMillis }
        FakePhotoFeedRepository.unhidePostsLocally(listOf(postId))
        return post
    }

    fun appendSystemMediaToPost(
        postId: String,
        mediaItems: List<SystemMediaItem>,
    ): Int {
        val post = getPost(postId) ?: return 0
        val mediaState = ensurePostMedia(postId = postId, fallbackPost = post)
        val existingMediaIds = mediaState.mapTo(mutableSetOf()) { it.id }
        val appendedMedia = mediaItems
            .distinctBy { it.id }
            .filterNot { existingMediaIds.contains(it.id) }
            .sortedByDescending { it.displayTimeMillis }
            .map { item ->
                item.toManagedState(isCover = false)
            }
        if (appendedMedia.isEmpty()) return 0

        val nextItems = normalizeCover(mediaState.toList() + appendedMedia)
        replacePostMedia(postId = postId, newItems = nextItems)
        val coverMedia = nextItems.firstOrNull { it.isCover } ?: nextItems.first()
        syncPostAfterMediaMutation(
            postId = postId,
            mediaCount = nextItems.size,
            coverPalette = coverMedia.palette,
            coverAspectRatio = coverMedia.aspectRatio,
        )
        return appendedMedia.size
    }

    fun getEditablePostDraft(postId: String): EditablePostDraft? {
        val post = getPost(postId) ?: return null
        return EditablePostDraft(
            postId = post.id,
            title = post.title,
            summary = post.summary,
            postDisplayTimeMillis = post.postDisplayTimeMillis,
            albumIds = post.albumIds,
        )
    }

    fun getManagedPostMedia(postId: String): List<ManagedPostMediaUiModel>? {
        val post = getPost(postId) ?: return null
        return ensurePostMedia(postId = postId, fallbackPost = post).map { media ->
            media.toUiModel()
        }.filterNot { media ->
            FakePhotoFeedRepository.isMediaHidden(media.id)
        }
    }

    fun setPostCover(postId: String, mediaId: String): Boolean {
        val post = getPost(postId) ?: return false
        val mediaState = ensurePostMedia(postId = postId, fallbackPost = post)
        val targetIndex = mediaState.indexOfFirst { it.id == mediaId }
        if (targetIndex < 0) return false

        val target = mediaState[targetIndex].copy(isCover = true)
        val reordered = buildList {
            add(target)
            mediaState.forEachIndexed { index, media ->
                if (index != targetIndex) {
                    add(media.copy(isCover = false))
                }
            }
        }
        replacePostMedia(postId = postId, newItems = reordered)
        syncPostCardCover(
            postId = postId,
            coverPalette = target.palette,
            coverAspectRatio = target.aspectRatio,
        )
        return true
    }

    fun getPostDetail(route: PostDetailPlaceholderRoute): PostDetailUiModel {
        val post = getPost(route.postId)
        val title = post?.title ?: route.title
        val summary = post?.summary ?: route.summary
        val postDisplayTimeMillis = post?.postDisplayTimeMillis ?: route.postDisplayTimeMillis
        val albumIds = post?.albumIds ?: route.albumIds.ifEmpty { listOf(route.albumId) }
        val mediaItems = ensurePostMedia(
            postId = route.postId,
            fallbackPost = post,
            fallbackRoute = route,
        ).map { media ->
            PostDetailMediaUiModel(
                id = media.id,
                displayTimeMillis = media.displayTimeMillis,
                commentCount = FakeCommentRepository.mediaCommentCount(media.id),
                palette = media.palette,
                aspectRatio = media.aspectRatio,
                width = media.width,
                height = media.height,
            )
        }.filterNot { media ->
            FakePhotoFeedRepository.isMediaHidden(media.id)
        }

        return PostDetailUiModel(
            postId = route.postId,
            title = title,
            summary = summary,
            contributorLabel = if (route.postId.length % 2 == 0) "我整理" else "你补充",
            postDisplayTimeMillis = postDisplayTimeMillis,
            albumIds = albumIds,
            albumChips = buildAlbumChips(albumIds),
            mediaItems = mediaItems,
            comments = FakeCommentRepository.getPostComments(route.postId),
        )
    }

    fun toPostDetailRoute(post: AlbumPostCardUiModel): PostDetailPlaceholderRoute {
        return PostDetailPlaceholderRoute(
            postId = post.id,
            albumId = post.albumId,
            albumIds = post.albumIds,
            title = post.title,
            summary = post.summary,
            postDisplayTimeMillis = post.postDisplayTimeMillis,
            mediaCount = post.mediaCount,
            coverPalette = post.coverPalette,
            coverAspectRatio = post.coverAspectRatio,
        )
    }

    fun updatePostBasicInfo(
        postId: String,
        title: String,
        summary: String,
        postDisplayTimeMillis: Long,
        albumIds: List<String>,
    ) {
        val index = posts.indexOfFirst { it.id == postId }
        if (index < 0) return

        val current = posts[index]
        val nextAlbumIds = albumIds.distinct()
        val nextPrimaryAlbumId = nextAlbumIds.firstOrNull() ?: current.albumId
        posts[index] = current.copy(
            albumId = nextPrimaryAlbumId,
            albumIds = nextAlbumIds,
            title = title,
            summary = summary,
            postDisplayTimeMillis = postDisplayTimeMillis,
        )
        posts.sortByDescending { it.postDisplayTimeMillis }
    }

    fun getManagedMediaOrder(postId: String): List<String> {
        val post = getPost(postId) ?: return emptyList()
        return ensurePostMedia(postId = postId, fallbackPost = post).map { it.id }
    }

    fun updatePostMediaOrder(
        postId: String,
        orderedIds: List<String>,
    ): Boolean {
        val post = getPost(postId) ?: return false
        val mediaState = ensurePostMedia(postId = postId, fallbackPost = post)
        if (orderedIds.isEmpty() || orderedIds.size != mediaState.size) return false

        val orderedItems = orderedIds.mapNotNull { mediaId ->
            mediaState.firstOrNull { it.id == mediaId }
        }
        if (orderedItems.size != mediaState.size) return false

        val coverId = mediaState.firstOrNull { it.isCover }?.id ?: orderedItems.first().id
        val reordered = orderedItems.map { media ->
            media.copy(isCover = media.id == coverId)
        }
        replacePostMedia(postId = postId, newItems = reordered)
        reordered.firstOrNull { it.isCover }?.let { cover ->
            syncPostCardCover(
                postId = postId,
                coverPalette = cover.palette,
                coverAspectRatio = cover.aspectRatio,
            )
        }
        return true
    }

    fun previewDeleteOutcome(
        postId: String,
        mediaIds: Set<String>,
        semantic: MediaDeleteSemantic,
    ): MediaDeleteOutcome {
        if (mediaIds.isEmpty()) return MediaDeleteOutcome()
        when (semantic) {
            MediaDeleteSemantic.DIRECTORY_ONLY -> {
                val affectedPostIds = mutableSetOf<String>()
                val deletedPostIds = mutableSetOf<String>()
                val remaining = remainingMediaCountAfterDelete(postId, mediaIds)
                if (remaining <= 0) {
                    deletedPostIds += postId
                } else {
                    affectedPostIds += postId
                }
                return MediaDeleteOutcome(
                    deletedPostIds = deletedPostIds,
                    affectedPostIds = affectedPostIds,
                )
            }
            MediaDeleteSemantic.SYSTEM_WIDE -> return previewGlobalMediaDelete(mediaIds)
        }
    }

    fun applyMediaDelete(
        postId: String,
        mediaIds: Set<String>,
        semantic: MediaDeleteSemantic,
    ): MediaDeleteOutcome {
        if (mediaIds.isEmpty()) return MediaDeleteOutcome()
        val preview = previewDeleteOutcome(postId = postId, mediaIds = mediaIds, semantic = semantic)
        when (semantic) {
            MediaDeleteSemantic.DIRECTORY_ONLY -> {
                if (!preview.deletedPostIds.contains(postId)) {
                    removeMediaFromPost(postId = postId, mediaIds = mediaIds)
                }
            }
            MediaDeleteSemantic.SYSTEM_WIDE -> return applyGlobalMediaDelete(mediaIds)
        }
        return preview
    }

    fun previewGlobalMediaDelete(mediaIds: Set<String>): MediaDeleteOutcome {
        if (mediaIds.isEmpty()) return MediaDeleteOutcome()
        val affectedPostIds = mutableSetOf<String>()
        val deletedPostIds = mutableSetOf<String>()

        posts.toList().forEach { post ->
            val state = ensurePostMedia(postId = post.id, fallbackPost = post)
            if (state.any { mediaIds.contains(it.id) }) {
                val remaining = state.count { !mediaIds.contains(it.id) }
                if (remaining <= 0) {
                    deletedPostIds += post.id
                } else {
                    affectedPostIds += post.id
                }
            }
        }
        return MediaDeleteOutcome(
            deletedPostIds = deletedPostIds,
            affectedPostIds = affectedPostIds,
        )
    }

    fun applyGlobalMediaDelete(mediaIds: Set<String>): MediaDeleteOutcome {
        if (mediaIds.isEmpty()) return MediaDeleteOutcome()
        val preview = previewGlobalMediaDelete(mediaIds)
        FakePhotoFeedRepository.hideMediaGlobally(mediaIds)
        return preview
    }

    fun deletePostsLocally(postIds: Collection<String>): Set<String> {
        val removedIds = mutableSetOf<String>()
        postIds.distinct().forEach { postId ->
            val index = posts.indexOfFirst { it.id == postId }
            if (index >= 0) {
                posts.removeAt(index)
                postMediaByPostId.remove(postId)
                removedIds += postId
            }
        }
        if (removedIds.isNotEmpty()) {
            FakePhotoFeedRepository.hidePostsLocally(removedIds)
        }
        return removedIds
    }

    fun snapshotPost(postId: String): TrashPostSnapshot? {
        val post = getPost(postId) ?: return null
        val mediaSnapshots = ensurePostMedia(postId = postId, fallbackPost = post).map { media ->
            media.toTrashSnapshot(
                sourcePostId = post.id,
                sourcePostTitle = post.title,
            )
        }
        return TrashPostSnapshot(
            post = post,
            mediaSnapshots = mediaSnapshots,
        )
    }

    fun snapshotPostMedia(
        postId: String,
        mediaIds: Set<String>,
    ): List<TrashMediaSnapshot> {
        if (mediaIds.isEmpty()) return emptyList()
        val post = getPost(postId) ?: return emptyList()
        return ensurePostMedia(postId = postId, fallbackPost = post)
            .filter { mediaIds.contains(it.id) }
            .map { media ->
                media.toTrashSnapshot(
                    sourcePostId = post.id,
                    sourcePostTitle = post.title,
                )
            }
    }

    fun snapshotMediaRelations(
        mediaIds: Set<String>,
    ): Map<String, List<TrashPostRelationSnapshot>> {
        if (mediaIds.isEmpty()) return emptyMap()
        val relationsByMediaId = linkedMapOf<String, MutableList<TrashPostRelationSnapshot>>()
        posts.forEach { post ->
            ensurePostMedia(postId = post.id, fallbackPost = post)
                .filter { mediaIds.contains(it.id) }
                .forEach { media ->
                    val snapshot = media.toTrashSnapshot(
                        sourcePostId = post.id,
                        sourcePostTitle = post.title,
                    )
                    relationsByMediaId.getOrPut(media.id) { mutableListOf() }
                        .add(
                            TrashPostRelationSnapshot(
                                postId = post.id,
                                postTitle = post.title.ifBlank { "当前帖子" },
                                mediaSnapshot = snapshot,
                            ),
                        )
                }
        }
        return relationsByMediaId
    }

    fun restorePost(snapshot: TrashPostSnapshot): Boolean {
        val restoredMedia = snapshot.mediaSnapshots
            .ifEmpty { return false }
            .map { it.toManagedState() }
        val visibleMedia = restoredMedia.filterNot { media ->
            FakePhotoFeedRepository.isMediaHidden(media.id)
        }
        val coverMedia = visibleMedia.firstOrNull { it.isCover }
            ?: visibleMedia.firstOrNull()
            ?: restoredMedia.firstOrNull { it.isCover }
            ?: restoredMedia.first()

        postMediaByPostId[snapshot.post.id] = mutableStateListOf<ManagedPostMediaState>().apply {
            addAll(restoredMedia)
        }

        val normalizedPost = snapshot.post.copy(
            mediaCount = visibleMedia.size.takeIf { it > 0 } ?: restoredMedia.size,
            coverPalette = coverMedia.palette,
            coverAspectRatio = coverMedia.aspectRatio,
        )
        val existingIndex = posts.indexOfFirst { it.id == normalizedPost.id }
        if (existingIndex >= 0) {
            posts[existingIndex] = normalizedPost
        } else {
            posts.add(normalizedPost)
        }
        posts.sortByDescending { it.postDisplayTimeMillis }
        FakePhotoFeedRepository.unhidePostsLocally(listOf(normalizedPost.id))
        return true
    }

    fun restoreMediaToPost(
        postId: String,
        mediaSnapshot: TrashMediaSnapshot,
    ): Boolean {
        val post = getPost(postId) ?: return false
        val mediaState = ensurePostMedia(postId = postId, fallbackPost = post)
        if (mediaState.any { it.id == mediaSnapshot.mediaId }) return true

        mediaState.add(mediaSnapshot.toManagedState())
        val normalized = normalizeCover(mediaState.toList())
        replacePostMedia(postId = postId, newItems = normalized)
        syncPostAfterMediaMutation(
            postId = postId,
            mediaCount = normalized.size,
            coverPalette = normalized.first().palette,
            coverAspectRatio = normalized.first().aspectRatio,
        )
        return true
    }

    fun restoreMediaRelations(relationSnapshots: List<TrashPostRelationSnapshot>): Int {
        if (relationSnapshots.isEmpty()) return 0
        return relationSnapshots.count { relation ->
            restoreMediaToPost(
                postId = relation.postId,
                mediaSnapshot = relation.mediaSnapshot,
            )
        }
    }

    fun getPostSystemDeleteImpact(postId: String): PostSystemDeleteImpact {
        val post = getPost(postId) ?: return PostSystemDeleteImpact(
            mediaCount = 0,
            sharedMediaCount = 0,
            affectedOtherPostCount = 0,
        )
        val mediaItems = ensurePostMedia(postId = postId, fallbackPost = post)
        val sharedMediaIds = mediaItems.map { it.id }.filter { mediaId ->
            posts.any { otherPost ->
                otherPost.id != postId &&
                    ensurePostMedia(postId = otherPost.id, fallbackPost = otherPost).any { media -> media.id == mediaId }
            }
        }
        val affectedOtherPostIds = posts.mapNotNull { otherPost ->
            if (otherPost.id == postId) return@mapNotNull null
            val sharesAnyMedia = ensurePostMedia(postId = otherPost.id, fallbackPost = otherPost).any { media ->
                sharedMediaIds.contains(media.id)
            }
            otherPost.id.takeIf { sharesAnyMedia }
        }
        return PostSystemDeleteImpact(
            mediaCount = mediaItems.size,
            sharedMediaCount = sharedMediaIds.distinct().size,
            affectedOtherPostCount = affectedOtherPostIds.distinct().size,
        )
    }

    private fun ensurePostMedia(
        postId: String,
        fallbackPost: AlbumPostCardUiModel? = getPost(postId),
        fallbackRoute: PostDetailPlaceholderRoute? = null,
    ): androidx.compose.runtime.snapshots.SnapshotStateList<ManagedPostMediaState> {
        postMediaByPostId[postId]?.let { return it }

        val post = fallbackPost ?: getPost(postId)
        val mediaCount = (post?.mediaCount ?: fallbackRoute?.mediaCount ?: 0).coerceAtLeast(1).coerceAtMost(8)
        val basePalette = post?.coverPalette ?: fallbackRoute?.coverPalette ?: albums.first().accent
        val baseTime = post?.postDisplayTimeMillis ?: fallbackRoute?.postDisplayTimeMillis ?: System.currentTimeMillis()
        val mediaState = mutableStateListOf<ManagedPostMediaState>().apply {
            repeat(mediaCount) { index ->
                val mediaId = fakeMediaIdForPost(postId, index)
                val aspectRatio = listOf(0.92f, 1.0f, 1.08f, 0.96f)[index % 4]
                val dimensions = fakeManagedMediaDimensions(
                    postId = postId,
                    index = index,
                    aspectRatio = aspectRatio,
                )
                add(
                    ManagedPostMediaState(
                        id = mediaId,
                        displayTimeMillis = baseTime + (index * 9 * 60 * 1000L),
                        commentCount = FakeCommentRepository.mediaCommentCount(mediaId),
                        palette = if (index == 0) {
                            basePalette
                        } else {
                            shiftedPalette(basePalette = basePalette, index = index)
                        },
                        aspectRatio = aspectRatio,
                        width = dimensions.first,
                        height = dimensions.second,
                        isCover = index == 0,
                    ),
                )
            }
        }
        postMediaByPostId[postId] = mediaState
        return mediaState
    }

    private fun replacePostMedia(
        postId: String,
        newItems: List<ManagedPostMediaState>,
    ) {
        val mediaState = ensurePostMedia(postId)
        mediaState.clear()
        mediaState.addAll(newItems)
    }

    private fun remainingMediaCountAfterDelete(
        postId: String,
        mediaIds: Set<String>,
    ): Int {
        val post = getPost(postId) ?: return 0
        val state = ensurePostMedia(postId = postId, fallbackPost = post)
        return state.count { !mediaIds.contains(it.id) }
    }

    private fun removeMediaFromPost(
        postId: String,
        mediaIds: Set<String>,
    ) {
        val post = getPost(postId) ?: return
        val state = ensurePostMedia(postId = postId, fallbackPost = post)
        val remaining = state.filterNot { mediaIds.contains(it.id) }
        if (remaining.isEmpty()) return

        val normalized = normalizeCover(remaining)
        replacePostMedia(postId = postId, newItems = normalized)
        syncPostAfterMediaMutation(
            postId = postId,
            mediaCount = normalized.size,
            coverPalette = normalized.first().palette,
            coverAspectRatio = normalized.first().aspectRatio,
        )
    }

    private fun normalizeCover(
        items: List<ManagedPostMediaState>,
    ): List<ManagedPostMediaState> {
        if (items.isEmpty()) return items
        val coverId = items.firstOrNull { it.isCover }?.id ?: items.first().id
        return items.map { media ->
            media.copy(isCover = media.id == coverId)
        }
    }

    private fun syncPostCardCover(
        postId: String,
        coverPalette: PhotoThumbnailPalette,
        coverAspectRatio: Float,
    ) {
        val index = posts.indexOfFirst { it.id == postId }
        if (index < 0) return
        posts[index] = posts[index].copy(
            coverPalette = coverPalette,
            coverAspectRatio = coverAspectRatio,
        )
    }

    private fun syncPostAfterMediaMutation(
        postId: String,
        mediaCount: Int,
        coverPalette: PhotoThumbnailPalette,
        coverAspectRatio: Float,
    ) {
        val index = posts.indexOfFirst { it.id == postId }
        if (index < 0) return
        posts[index] = posts[index].copy(
            mediaCount = mediaCount,
            coverPalette = coverPalette,
            coverAspectRatio = coverAspectRatio,
        )
    }

    private fun ManagedPostMediaState.toUiModel(): ManagedPostMediaUiModel {
        return ManagedPostMediaUiModel(
            id = id,
            displayTimeMillis = displayTimeMillis,
            commentCount = FakeCommentRepository.mediaCommentCount(id),
            palette = palette,
            aspectRatio = aspectRatio,
            isCover = isCover,
        )
    }

    private fun ManagedPostMediaState.toTrashSnapshot(
        sourcePostId: String,
        sourcePostTitle: String,
    ): TrashMediaSnapshot {
        return TrashMediaSnapshot(
            mediaId = id,
            displayTimeMillis = displayTimeMillis,
            palette = palette,
            aspectRatio = aspectRatio,
            width = width,
            height = height,
            isCover = isCover,
            sourcePostId = sourcePostId,
            sourcePostTitle = sourcePostTitle.ifBlank { "当前帖子" },
        )
    }

    private fun TrashMediaSnapshot.toManagedState(): ManagedPostMediaState {
        return ManagedPostMediaState(
            id = mediaId,
            displayTimeMillis = displayTimeMillis,
            commentCount = FakeCommentRepository.mediaCommentCount(mediaId),
            palette = palette,
            aspectRatio = aspectRatio,
            width = width,
            height = height,
            isCover = isCover,
        )
    }

    private fun buildAlbumChips(albumIds: List<String>): List<String> {
        val albumTitles = albumIds.mapNotNull { albumId ->
            albums.firstOrNull { it.id == albumId }?.title
        }
        return if (albumTitles.isEmpty()) {
            listOf("未归入相册")
        } else {
            albumTitles
        }
    }

    private fun SystemMediaItem.toManagedState(isCover: Boolean): ManagedPostMediaState {
        return ManagedPostMediaState(
            id = id,
            displayTimeMillis = displayTimeMillis,
            commentCount = FakeCommentRepository.mediaCommentCount(id),
            palette = palette,
            aspectRatio = aspectRatio,
            width = width,
            height = height,
            isCover = isCover,
        )
    }

    private fun buildSystemImportTitle(
        mediaCount: Int,
        displayTimeMillis: Long,
    ): String {
        val dateLabel = java.text.SimpleDateFormat("M月d日", java.util.Locale.CHINA)
            .format(java.util.Date(displayTimeMillis))
        return "系统导入 $dateLabel · $mediaCount 项"
    }

    private fun fakeManagedMediaDimensions(
        postId: String,
        index: Int,
        aspectRatio: Float,
    ): Pair<Int?, Int?> {
        val longImagePosts = setOf(
            "post-night-walk",
            "post-sunday-brunch",
            "post-new-year",
            "post-rooftop-night",
        )
        if (index == 1 && postId in longImagePosts) {
            return 1080 to 3240
        }

        val width = 1200
        val height = (width / aspectRatio.coerceAtLeast(0.56f)).toInt()
        return width to height
    }

    private fun shiftedPalette(
        basePalette: PhotoThumbnailPalette,
        index: Int,
    ): PhotoThumbnailPalette {
        val alpha = 0.78f + ((index % 3) * 0.06f)
        return PhotoThumbnailPalette(
            start = basePalette.start.copy(alpha = alpha.coerceAtMost(1f)),
            end = basePalette.end.copy(alpha = (alpha + 0.08f).coerceAtMost(1f)),
            accent = basePalette.accent,
        )
    }

    private fun fakeMediaIdForPost(postId: String, index: Int): String {
        val sharedIds = sharedMediaIdsByPost[postId]
        return sharedIds?.getOrNull(index) ?: "$postId-media-$index"
    }

    private fun seedPosts(): List<AlbumPostCardUiModel> {
        return listOf(
            AlbumPostCardUiModel(
                id = "post-window-light",
                albumId = "album-spring-window",
                title = "四月窗边",
                summary = "午后光线慢慢落在桌面上，像把一天也放轻了一点。",
                postDisplayTimeMillis = 1713505800000,
                mediaCount = 6,
                coverPalette = albums[0].accent,
                coverAspectRatio = 0.96f,
            ),
            AlbumPostCardUiModel(
                id = "post-flower-table",
                albumId = "album-spring-window",
                title = "花和杯子",
                summary = "临时摆在一起的几样东西，最后反而最像纪念照。",
                postDisplayTimeMillis = 1713162600000,
                mediaCount = 4,
                coverPalette = PhotoThumbnailPalette(
                    start = Color(0xFFF1E2D7),
                    end = Color(0xFFE8CFCB),
                    accent = Color(0xFFBA8E7B),
                ),
                coverAspectRatio = 1.02f,
            ),
            AlbumPostCardUiModel(
                id = "post-curtain-air",
                albumId = "album-spring-window",
                title = "风吹过纱帘",
                summary = "几张很轻的图，像在记那天房间里的空气。",
                postDisplayTimeMillis = 1712817000000,
                mediaCount = 3,
                coverPalette = PhotoThumbnailPalette(
                    start = Color(0xFFE6E7DD),
                    end = Color(0xFFD3DED8),
                    accent = Color(0xFF8D9B8E),
                ),
                coverAspectRatio = 0.92f,
            ),
            AlbumPostCardUiModel(
                id = "post-river-night",
                albumId = "album-city-night",
                title = "河边夜色",
                summary = "走到桥边时风特别大，照片里却只剩很安静的水面。",
                postDisplayTimeMillis = 1714200600000,
                mediaCount = 8,
                coverPalette = albums[1].accent,
                coverAspectRatio = 1.05f,
            ),
            AlbumPostCardUiModel(
                id = "post-late-return",
                albumId = "album-city-night",
                title = "晚归路上",
                summary = "路灯、倒影和几句没说完的话，刚好拼成一帖。",
                postDisplayTimeMillis = 1713855000000,
                mediaCount = 5,
                coverPalette = PhotoThumbnailPalette(
                    start = Color(0xFF182435),
                    end = Color(0xFF304962),
                    accent = Color(0xFFD8B17D),
                ),
                coverAspectRatio = 0.94f,
            ),
            AlbumPostCardUiModel(
                id = "post-subway-blue",
                albumId = "album-city-night",
                title = "地铁快到站",
                summary = "蓝色车窗里都是匆忙的人，但这一组想留下的是慢下来的感觉。",
                postDisplayTimeMillis = 1713336600000,
                mediaCount = 7,
                coverPalette = PhotoThumbnailPalette(
                    start = Color(0xFF213043),
                    end = Color(0xFF5D7390),
                    accent = Color(0xFF9DB8D7),
                ),
                coverAspectRatio = 1.08f,
            ),
            AlbumPostCardUiModel(
                id = "post-sunday-brunch",
                albumId = "album-weekend-table",
                title = "周日早午餐",
                summary = "桌子并不丰盛，但这一餐因为聊天被记得很久。",
                postDisplayTimeMillis = 1713685800000,
                mediaCount = 9,
                coverPalette = albums[2].accent,
                coverAspectRatio = 0.98f,
            ),
            AlbumPostCardUiModel(
                id = "post-orange-soup",
                albumId = "album-weekend-table",
                title = "橙色汤碗",
                summary = "那天的暖色都落在汤碗和木桌上，看起来很像秋天。",
                postDisplayTimeMillis = 1713072600000,
                mediaCount = 4,
                coverPalette = PhotoThumbnailPalette(
                    start = Color(0xFFF0D5BC),
                    end = Color(0xFFD8C7B7),
                    accent = Color(0xFFBC7C42),
                ),
                coverAspectRatio = 1.03f,
            ),
            AlbumPostCardUiModel(
                id = "post-night-snack",
                albumId = "album-weekend-table",
                title = "半夜加餐",
                summary = "只是几张很日常的图，但翻回来时会先想起那天的气氛。",
                postDisplayTimeMillis = 1712467800000,
                mediaCount = 5,
                coverPalette = PhotoThumbnailPalette(
                    start = Color(0xFFDDD5C8),
                    end = Color(0xFFC8D1C1),
                    accent = Color(0xFF7E7B63),
                ),
                coverAspectRatio = 0.95f,
            ),
            AlbumPostCardUiModel(
                id = "post-cloudy-station",
                albumId = "album-small-trip",
                title = "阴天车站",
                summary = "出发前的几分钟没有特别的事，但很适合留作开头。",
                postDisplayTimeMillis = 1712284200000,
                mediaCount = 6,
                coverPalette = albums[3].accent,
                coverAspectRatio = 0.93f,
            ),
            AlbumPostCardUiModel(
                id = "post-hill-road",
                albumId = "album-small-trip",
                title = "上坡那段路",
                summary = "真正记住的不是目的地，是边走边聊的那一小段。",
                postDisplayTimeMillis = 1712111400000,
                mediaCount = 5,
                coverPalette = PhotoThumbnailPalette(
                    start = Color(0xFFD7E6E2),
                    end = Color(0xFFB6D0CB),
                    accent = Color(0xFF5A8A82),
                ),
                coverAspectRatio = 1.04f,
            ),
            AlbumPostCardUiModel(
                id = "post-new-year-window",
                albumId = "album-new-year",
                title = "新年窗台",
                summary = "节日装饰、晚一点的灯和安静待着的时刻。",
                postDisplayTimeMillis = 1704209400000,
                mediaCount = 7,
                coverPalette = albums[4].accent,
                coverAspectRatio = 1.01f,
            ),
            AlbumPostCardUiModel(
                id = "post-firework-reflection",
                albumId = "album-new-year",
                title = "烟火倒影",
                summary = "真正被记住的其实不是烟火本身，是一起看的那一瞬。",
                postDisplayTimeMillis = 1704065400000,
                mediaCount = 4,
                coverPalette = PhotoThumbnailPalette(
                    start = Color(0xFF3A2947),
                    end = Color(0xFF6D4966),
                    accent = Color(0xFFF0B38E),
                ),
                coverAspectRatio = 1.06f,
            ),
        )
    }

    private val sharedMediaIdsByPost = mapOf(
        "post-sunday-brunch" to listOf(
            "media-2026-04-12-a",
            "media-2026-04-12-b",
        ),
        "post-flower-table" to listOf(
            "media-2026-04-12-b",
            "post-flower-table-media-1",
        ),
        "post-late-return" to listOf(
            "media-2026-03-30-a",
            "media-2026-03-30-b",
        ),
        "post-river-night" to listOf(
            "media-2026-03-30-b",
            "post-river-night-media-1",
        ),
        "post-firework-reflection" to listOf(
            "media-2026-01-01-b",
            "post-firework-reflection-media-1",
        ),
    )
}

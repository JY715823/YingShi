package com.example.yingshi.feature.photos

import kotlin.math.absoluteValue

object FakeCommentRepository {
    private val postCommentBodies = listOf(
        "这一组放在一起看，比单张照片更像那天的完整记忆。",
        "标题先这样占位，后面接真实评论系统时再替换。",
        "我喜欢这里保留一点上下文，不急着进入全屏查看。",
        "这条先当作帖子评论，只讨论整组内容。",
        "以后这里可以承接更长一点的记录，但现在先保持轻。",
        "这组里最有感觉的是开头那几张，节奏很好。",
        "如果后面要导出成纪念册，这一帖应该保留下来。",
        "这里的时间顺序很清楚，看起来像一小段日记。",
        "先记一笔，后面真实输入器接入后再完善。",
        "评论区保持这样干净就很好，不要太社区化。",
        "超过十条时先给展开入口，后面再接分页。",
        "这是一条用于验证展开更多占位的帖子评论。",
    )

    private val mediaCommentBodies = listOf(
        "这张的光很温柔，像那天刚好慢下来了一点。",
        "我记得这里，当时风特别轻。",
        "这个角度好像比现场更安静。",
        "这张适合单独收藏，颜色很稳。",
        "只看这一张也能想起当时的空气。",
        "这个瞬间比整组标题还直接。",
        "这条是媒体评论，只属于当前这张图。",
        "以后真实评论接入时，这里应该跟着 mediaId 走。",
        "预览层能看到这条，说明滚动列表已经成立。",
        "同一个媒体出现在不同帖子里，也应该共享这些评论。",
        "这条用于验证预览最多十条的上限。",
    )

    fun getPostComments(postId: String): List<CommentUiModel> {
        val count = 4 + (postId.hashCode().absoluteValue % 9)
        return List(count) { index ->
            CommentUiModel(
                id = "$postId-post-comment-$index",
                targetType = CommentTargetType.Post,
                targetId = postId,
                author = if (index % 2 == 0) "我" else "你",
                content = postCommentBodies[index % postCommentBodies.size],
                createdAtMillis = 1714300000000L - (index * 17 * 60 * 1000L),
                isMine = index % 2 == 0,
            )
        }.sortedByDescending { it.createdAtMillis }
    }

    fun getMediaComments(mediaId: String): List<CommentUiModel> {
        val count = mediaCommentCount(mediaId)
        return List(count) { index ->
            CommentUiModel(
                id = "$mediaId-media-comment-$index",
                targetType = CommentTargetType.Media,
                targetId = mediaId,
                author = if (index % 2 == 0) "我" else "你",
                content = mediaCommentBodies[index % mediaCommentBodies.size],
                createdAtMillis = 1714400000000L - (index * 11 * 60 * 1000L),
                isMine = index % 2 == 0,
            )
        }.sortedByDescending { it.createdAtMillis }
    }

    fun mediaCommentCount(mediaId: String): Int {
        val rawValue = mediaId.hashCode().absoluteValue % 12
        return if (rawValue <= 1) 0 else rawValue
    }
}


package com.example.yingshi.feature.photos

import androidx.compose.ui.graphics.Color

object FakeAlbumRepository {
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
            subtitle = "走出去之后才会想记住的几幕",
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

    private val posts = listOf(
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
            summary = "真正被记住的其实不是烟火本身，是一起看的那一秒。",
            postDisplayTimeMillis = 1704065400000,
            mediaCount = 4,
            coverPalette = PhotoThumbnailPalette(
                start = Color(0xFF3A2947),
                end = Color(0xFF6D4966),
                accent = Color(0xFFF0B38E),
            ),
            coverAspectRatio = 1.06f,
        ),
    ).sortedByDescending { it.postDisplayTimeMillis }

    fun getAlbums(): List<AlbumSummaryUiModel> = albums

    fun getPosts(): List<AlbumPostCardUiModel> = posts

    fun toPostDetailRoute(post: AlbumPostCardUiModel): PostDetailPlaceholderRoute {
        return PostDetailPlaceholderRoute(
            postId = post.id,
            albumId = post.albumId,
            title = post.title,
            summary = post.summary,
            postDisplayTimeMillis = post.postDisplayTimeMillis,
            mediaCount = post.mediaCount,
            coverPalette = post.coverPalette,
            coverAspectRatio = post.coverAspectRatio,
        )
    }
}

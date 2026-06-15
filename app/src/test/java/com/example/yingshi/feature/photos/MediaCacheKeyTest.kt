package com.example.yingshi.feature.photos

import org.junit.Assert.assertEquals
import org.junit.Test

class MediaCacheKeyTest {

    @Test
    fun backendMediaFileUrlUsesMediaIdVariantAsStableKey() {
        val first = stableMediaCacheUrlKey(
            "https://api.example.com/api/media/files/media_001?variant=preview&X-Amz-Signature=aaa",
        )
        val second = stableMediaCacheUrlKey(
            "https://api.example.com/api/media/files/media_001?variant=preview&X-Amz-Signature=bbb",
        )

        assertEquals("media:media_001:preview", first)
        assertEquals(first, second)
    }

    @Test
    fun signedCdnUrlIgnoresTencentAuthQuery() {
        val first = stableMediaCacheUrlKey(
            "https://cdn.example.com/originals/2026/06/media_001.jpg?sign=aaa&t=1780000000",
        )
        val second = stableMediaCacheUrlKey(
            "https://cdn.example.com/originals/2026/06/media_001.jpg?sign=bbb&t=1780000900",
        )

        assertEquals("https://cdn.example.com/originals/2026/06/media_001.jpg", first)
        assertEquals(first, second)
    }

    @Test
    fun signedCosUrlIgnoresProviderSignatureQuery() {
        val first = stableMediaCacheUrlKey(
            "https://bucket.cos.ap-guangzhou.myqcloud.com/originals/2026/06/media_001.jpg?q-signature=aaa&q-key-time=1;2",
        )
        val second = stableMediaCacheUrlKey(
            "https://bucket.cos.ap-guangzhou.myqcloud.com/originals/2026/06/media_001.jpg?q-signature=bbb&q-key-time=3;4",
        )

        assertEquals("https://bucket.cos.ap-guangzhou.myqcloud.com/originals/2026/06/media_001.jpg", first)
        assertEquals(first, second)
    }

    @Test
    fun thumbnailCacheKeyIncludesRefreshKeyForWarmAlbumCoverRefresh() {
        val mediaSource = AppContentMediaSource(
            thumbnailUrl = "https://api.example.com/api/media/files/media_001?variant=preview",
            thumbnailCacheKey = "media:media_001:preview",
            refreshKey = "album-preview:post_001:0|nonce:3",
        )

        assertEquals(
            "media:media_001:preview|refresh:album-preview:post_001:0|nonce:3",
            mediaSource.thumbnailModelCacheKey(AppMediaType.IMAGE),
        )
    }
}

package com.example.yingshi.data.remote.mapper

import com.example.yingshi.data.remote.dto.MediaDto
import com.example.yingshi.data.remote.dto.PostDetailDto
import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RemoteMappersCompatibilityTest {

    private val gson = Gson()

    @Test
    fun mediaDtoWithoutAccessStillMapsForPhotoFeed() {
        val dto = gson.fromJson(
            """
            {
              "mediaId": "media_001",
              "mediaType": "image",
              "url": "/api/media/files/media_001",
              "previewUrl": "/api/media/files/media_001?variant=preview",
              "originalUrl": "/api/media/files/media_001",
              "mimeType": "image/jpeg",
              "width": 1080,
              "height": 1920,
              "aspectRatio": 0.5625,
              "displayTimeMillis": 1780243200000,
              "smallAlbumIds": ["small_album_001"]
            }
            """.trimIndent(),
            MediaDto::class.java,
        )

        val model = dto.toRemoteModel()

        assertEquals("media_001", model.mediaId)
        assertEquals("/api/media/files/media_001?variant=preview", model.previewUrl)
        assertTrue(model.access.isEmpty())
        assertEquals(listOf("small_album_001"), model.smallAlbumIds)
    }

    @Test
    fun mediaDtoWithoutOptionalListsStillMapsSafely() {
        val dto = gson.fromJson(
            """
            {
              "mediaId": "media_002",
              "mediaType": "image",
              "url": "/api/media/files/media_002",
              "previewUrl": "/api/media/files/media_002?variant=preview",
              "originalUrl": "/api/media/files/media_002",
              "mimeType": "image/jpeg",
              "width": 1200,
              "height": 1600,
              "aspectRatio": 0.75,
              "displayTimeMillis": 1780243201000
            }
            """.trimIndent(),
            MediaDto::class.java,
        )

        val model = dto.toRemoteModel()

        assertTrue(model.smallAlbumIds.isEmpty())
        assertTrue(model.access.isEmpty())
    }

    @Test
    fun mediaAccessPrefersBackendFallbackUrlOverSignedUrl() {
        val dto = gson.fromJson(
            """
            {
              "mediaId": "media_004",
              "mediaType": "image",
              "url": "/api/media/files/media_004",
              "previewUrl": "/api/media/files/media_004?variant=preview",
              "originalUrl": "/api/media/files/media_004",
              "mimeType": "image/jpeg",
              "displayTimeMillis": 1780243203000,
              "access": [
                {
                  "variant": "preview",
                  "url": "/api/media/files/media_004?variant=preview",
                  "signedUrl": "https://cdn.example.com/media_004-preview.jpg?sign=abc"
                },
                {
                  "variant": "original",
                  "url": "/api/media/files/media_004",
                  "signedUrl": "https://cdn.example.com/media_004.jpg?sign=abc"
                }
              ]
            }
            """.trimIndent(),
            MediaDto::class.java,
        )

        val model = dto.toRemoteModel()

        assertEquals("/api/media/files/media_004?variant=preview", model.previewUrl)
        assertEquals("/api/media/files/media_004", model.originalUrl)
        assertEquals("/api/media/files/media_004", model.mediaUrl)
    }

    @Test
    fun postDetailWithoutMediaAccessStillMapsSafely() {
        val dto = gson.fromJson(
            """
            {
              "smallAlbumId": "small_album_001",
              "title": "六月",
              "summary": "测试",
              "displayTimeMillis": 1780243200000,
              "albumId": "album_001",
              "mediaCount": 1,
              "mediaItems": [
                {
                  "sortOrder": 1,
                  "isCover": true,
                  "media": {
                    "mediaId": "media_003",
                    "mediaType": "image",
                    "url": "/api/media/files/media_003",
                    "previewUrl": "/api/media/files/media_003?variant=preview",
                    "originalUrl": "/api/media/files/media_003",
                    "mimeType": "image/jpeg",
                    "width": 1080,
                    "height": 1440,
                    "aspectRatio": 0.75,
                    "displayTimeMillis": 1780243202000
                  }
                }
              ]
            }
            """.trimIndent(),
            PostDetailDto::class.java,
        )

        val model = dto.toRemoteDetail()

        assertEquals("small_album_001", model.postId)
        assertEquals(1, model.mediaItems.size)
        assertTrue(model.mediaItems.first().access.isEmpty())
        assertEquals("/api/media/files/media_003?variant=preview", model.mediaItems.first().previewUrl)
    }
}

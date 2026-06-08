package com.example.yingshi.data.remote.api

import org.junit.Assert.assertEquals
import org.junit.Test
import retrofit2.http.DELETE

class SmallAlbumApiContractTest {

    @Test
    fun deleteSmallAlbumPathMatchesBackendContract() {
        val annotation = SmallAlbumApi::class.java
            .declaredMethods
            .first { it.name == "deleteSmallAlbum" }
            .getAnnotation(DELETE::class.java)

        assertEquals("api/small-albums/{smallAlbumId}", requireNotNull(annotation).value)
    }
}

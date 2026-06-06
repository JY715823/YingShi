package com.example.yingshi.data.remote.api

import org.junit.Assert.assertEquals
import org.junit.Test
import retrofit2.http.GET
import retrofit2.http.PUT

class ChatApiContractTest {

    @Test
    fun chatSnapshotGetPathMatchesBackendContract() {
        val annotation = ChatApi::class.java
            .declaredMethods
            .first { it.name == "getSnapshot" }
            .getAnnotation(GET::class.java)

        assertEquals("api/chat/imported/snapshot", requireNotNull(annotation).value)
    }

    @Test
    fun chatSnapshotPutPathMatchesBackendContract() {
        val annotation = ChatApi::class.java
            .declaredMethods
            .first { it.name == "putSnapshot" }
            .getAnnotation(PUT::class.java)

        assertEquals("api/chat/imported/snapshot", requireNotNull(annotation).value)
    }
}

package com.example.yingshi.feature.chat.data

import android.content.Context
import com.example.yingshi.data.remote.config.RemoteServiceFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.io.FileOutputStream

class ChatMediaSyncBridge(
    private val context: Context,
    private val api: ChatMediaApi = RemoteServiceFactory.chatMediaApi,
) {
    /**
     * Check if a resource exists on the server (HEAD request).
     */
    suspend fun isResourceStored(objectKey: String): Boolean = withContext(Dispatchers.IO) {
        runCatching { api.checkMediaExists(objectKey).isSuccessful }
            .getOrElse { false }
    }

    /**
     * Upload a local resource file to the server.
     * Returns the stored object key, or null if upload failed.
     */
    suspend fun uploadResource(
        libraryId: String,
        chatStableKey: String,
        localFilePath: String,
        storedFileName: String,
        md5: String?,
        mimeType: String?,
    ): String? = withContext(Dispatchers.IO) {
        runCatching {
            val file = File(localFilePath)
            if (!file.exists()) return@runCatching null

            val mediaType = mimeType?.toMediaTypeOrNull()
            val filePart = MultipartBody.Part.createFormData(
                "file",
                storedFileName,
                file.asRequestBody(mediaType),
            )
            val chatStableKeyBody = chatStableKey.toRequestBody("text/plain".toMediaTypeOrNull())
            val md5Body = md5?.toRequestBody("text/plain".toMediaTypeOrNull())

            val response = api.uploadMedia(
                file = filePart,
                chatStableKey = chatStableKeyBody,
                md5 = md5Body,
            )
            response["objectKey"]
        }.getOrElse { null }
    }

    /**
     * Download a resource from the server to local storage.
     * Returns the local file path, or null if download failed.
     */
    suspend fun downloadResource(
        objectKey: String,
        targetLocalPath: String,
    ): String? = withContext(Dispatchers.IO) {
        runCatching {
            val body = api.downloadMedia(objectKey)
            val targetFile = File(targetLocalPath)
            targetFile.parentFile?.mkdirs()
            body.byteStream().use { input ->
                FileOutputStream(targetFile).use { output ->
                    input.copyTo(output)
                }
            }
            targetLocalPath
        }.getOrElse { null }
    }
}

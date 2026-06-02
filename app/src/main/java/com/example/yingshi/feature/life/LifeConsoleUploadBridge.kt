package com.example.yingshi.feature.life

import android.content.Context
import android.graphics.BitmapFactory
import android.media.ExifInterface
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.MediaStore
import android.provider.OpenableColumns
import com.example.yingshi.data.model.CreateUploadTokenPayload
import com.example.yingshi.data.model.RemoteLifeConsoleToday
import com.example.yingshi.data.remote.result.ApiResult
import com.example.yingshi.data.repository.RepositoryProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.math.roundToInt

object LifeConsoleUploadBridge {
    suspend fun uploadMedia(
        context: Context,
        category: String,
        uris: List<Uri>,
    ): ApiResult<RemoteLifeConsoleToday> {
        if (uris.isEmpty()) {
            return ApiResult.Error(
                code = "NO_MEDIA_SELECTED",
                message = "没有选择媒体。",
            )
        }

        val uploadedMediaIds = mutableListOf<String>()
        for (uri in uris) {
            val metadata = withContext(Dispatchers.IO) {
                resolveUploadMetadata(context, uri)
            }
            val tokenResult = RepositoryProvider.uploadRepository.createUploadToken(metadata.toTokenPayload())
            val uploadId = when (tokenResult) {
                is ApiResult.Success -> tokenResult.data.uploadId
                is ApiResult.Error -> return tokenResult
                ApiResult.Loading -> return ApiResult.Loading
            }

            val uploadResult = RepositoryProvider.uploadRepository.uploadLocalStream(
                uploadId = uploadId,
                fileName = metadata.fileName,
                mimeType = metadata.mimeType,
                fileSizeBytes = metadata.fileSizeBytes,
                openInputStream = {
                    context.contentResolver.openInputStream(uri)
                        ?: error("无法读取已选择的媒体。")
                },
            )
            when (uploadResult) {
                is ApiResult.Success -> uploadedMediaIds += uploadResult.data.mediaId
                is ApiResult.Error -> return uploadResult
                ApiResult.Loading -> return ApiResult.Loading
            }
        }

        return RepositoryProvider.lifeConsoleRepository.addMedia(
            category = category,
            mediaIds = uploadedMediaIds,
        )
    }

    private fun resolveUploadMetadata(context: Context, uri: Uri): UploadMetadata {
        val mimeType = context.contentResolver.getType(uri)?.lowercase(Locale.ROOT)
            ?: "image/jpeg"
        val isVideo = mimeType.startsWith("video/")
        val fileName = queryDisplayName(context, uri)
            ?: "life-${System.currentTimeMillis()}.${if (isVideo) "mp4" else "jpg"}"
        val fileSizeBytes = queryFileSize(context, uri).takeIf { it > 0L } ?: 1L
        val (width, height, durationMillis) = if (isVideo) {
            resolveVideoMetadata(context, uri)
        } else {
            resolveImageMetadata(context, uri)
        }
        val nowMillis = System.currentTimeMillis()
        val capturedAtMillis = queryMediaTime(context, uri)
        val displayTimeMillis = capturedAtMillis ?: nowMillis
        return UploadMetadata(
            fileName = fileName,
            mimeType = mimeType,
            fileSizeBytes = fileSizeBytes,
            mediaType = if (isVideo) "video" else "image",
            width = width.coerceAtLeast(1),
            height = height.coerceAtLeast(1),
            durationMillis = durationMillis,
            displayTimeMillis = displayTimeMillis,
            capturedAtMillis = capturedAtMillis,
            importedAtMillis = nowMillis,
        )
    }

    private fun queryDisplayName(context: Context, uri: Uri): String? {
        return context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                if (cursor.moveToFirst()) {
                    cursor.getStringOrNull(cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME))
                } else {
                    null
                }
            }?.trim()?.takeIf { it.isNotBlank() }
    }

    private fun queryFileSize(context: Context, uri: Uri): Long {
        return context.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)
            ?.use { cursor ->
                if (cursor.moveToFirst()) {
                    cursor.getLongOrNull(cursor.getColumnIndex(OpenableColumns.SIZE)) ?: 0L
                } else {
                    0L
                }
            } ?: 0L
    }

    private fun queryMediaTime(context: Context, uri: Uri): Long? {
        return runCatching {
            context.contentResolver.query(
                uri,
                arrayOf("datetaken", MediaStore.MediaColumns.DATE_MODIFIED),
                null,
                null,
                null,
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val dateTakenMillis = cursor.getLongOrNull(cursor.getColumnIndex("datetaken"))
                    val dateModifiedSeconds = cursor.getLongOrNull(
                        cursor.getColumnIndex(MediaStore.MediaColumns.DATE_MODIFIED),
                    )
                    dateTakenMillis ?: dateModifiedSeconds?.times(1000L)
                } else {
                    null
                }
            }
        }.getOrNull()
    }

    private fun resolveImageMetadata(context: Context, uri: Uri): Triple<Int, Int, Long?> {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        runCatching {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                BitmapFactory.decodeStream(inputStream, null, bounds)
            }
        }
        val orientationDegrees = resolveImageOrientationDegrees(context, uri)
        val rawWidth = bounds.outWidth.takeIf { it > 0 } ?: 1
        val rawHeight = bounds.outHeight.takeIf { it > 0 } ?: 1
        val width = if (orientationDegrees == 90 || orientationDegrees == 270) rawHeight else rawWidth
        val height = if (orientationDegrees == 90 || orientationDegrees == 270) rawWidth else rawHeight
        return Triple(width, height, null)
    }

    private fun resolveVideoMetadata(context: Context, uri: Uri): Triple<Int, Int, Long?> {
        val retriever = MediaMetadataRetriever()
        return runCatching {
            retriever.setDataSource(context, uri)
            val rawWidth = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
                ?.toIntOrNull()
            val rawHeight = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
                ?.toIntOrNull()
            val rotation = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)
                ?.toIntOrNull()
                ?: 0
            val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull()
            val width = rawWidth ?: 1920
            val height = rawHeight ?: ((width / (16f / 9f)).roundToInt().coerceAtLeast(1))
            if (rotation == 90 || rotation == 270) {
                Triple(height, width, duration)
            } else {
                Triple(width, height, duration)
            }
        }.getOrElse {
            Triple(1920, 1080, null)
        }.also {
            runCatching { retriever.release() }
        }
    }

    private fun resolveImageOrientationDegrees(context: Context, uri: Uri): Int {
        return runCatching {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                when (ExifInterface(inputStream).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL,
                )) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> 90
                    ExifInterface.ORIENTATION_ROTATE_180 -> 180
                    ExifInterface.ORIENTATION_ROTATE_270 -> 270
                    else -> 0
                }
            } ?: 0
        }.getOrDefault(0)
    }

    private fun android.database.Cursor.getStringOrNull(columnIndex: Int): String? {
        if (columnIndex < 0 || isNull(columnIndex)) return null
        return getString(columnIndex)
    }

    private fun android.database.Cursor.getLongOrNull(columnIndex: Int): Long? {
        if (columnIndex < 0 || isNull(columnIndex)) return null
        return getLong(columnIndex)
    }

    private data class UploadMetadata(
        val fileName: String,
        val mimeType: String,
        val fileSizeBytes: Long,
        val mediaType: String,
        val width: Int,
        val height: Int,
        val durationMillis: Long?,
        val displayTimeMillis: Long,
        val capturedAtMillis: Long?,
        val importedAtMillis: Long,
    ) {
        fun toTokenPayload(): CreateUploadTokenPayload {
            return CreateUploadTokenPayload(
                fileName = fileName,
                mimeType = mimeType,
                fileSizeBytes = fileSizeBytes,
                mediaType = mediaType,
                width = width,
                height = height,
                durationMillis = durationMillis,
                displayTimeMillis = displayTimeMillis,
                capturedAtMillis = capturedAtMillis,
                importedAtMillis = importedAtMillis,
                displayTimeSource = if (capturedAtMillis == null) "IMPORTED" else "ORIGINAL",
            )
        }
    }
}

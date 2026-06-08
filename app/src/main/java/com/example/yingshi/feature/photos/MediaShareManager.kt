package com.example.yingshi.feature.photos

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import com.example.yingshi.data.remote.auth.AuthSessionManager
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class ShareableMediaItem(
    val mediaId: String,
    val mediaType: AppMediaType,
    val mimeType: String?,
    val displayName: String,
    val previewUrl: String?,
    val originalUrl: String?,
    val videoUrl: String?,
)

sealed class MediaShareLaunchResult {
    data class Success(
        val mediaCount: Int,
        val skippedCount: Int,
        val zipped: Boolean,
    ) : MediaShareLaunchResult()

    data class Error(
        val message: String,
    ) : MediaShareLaunchResult()
}

object MediaShareManager {
    suspend fun shareMedia(
        context: Context,
        items: List<ShareableMediaItem>,
        packageBaseName: String = "映世分享",
        sharePreferences: SharePreferenceState = SettingsRepository.getSettingsState().sharePreferences,
    ): MediaShareLaunchResult = withContext(Dispatchers.IO) {
        val distinctItems = items.distinctBy { it.mediaId }
            .filter { sharePreferences.includeVideos || it.mediaType != AppMediaType.VIDEO }
        if (distinctItems.isEmpty()) {
            return@withContext MediaShareLaunchResult.Error("当前没有可分享的真实媒体。")
        }

        val sessionDir = prepareSessionDirectory(context)
        val preparedFiles = linkedMapOf<String, File>()
        val skippedItems = mutableListOf<String>()

        distinctItems.forEachIndexed { index, item ->
            val preparedFile = runCatching {
                prepareFileForShare(
                    context = context,
                    item = item,
                    index = index,
                    sessionDir = sessionDir,
                    imageQualityPreference = sharePreferences.imageQualityPreference,
                )
            }.getOrNull()
            if (preparedFile == null) {
                skippedItems += item.mediaId
            } else {
                preparedFiles[item.mediaId] = preparedFile
            }
        }

        if (preparedFiles.isEmpty()) {
            return@withContext MediaShareLaunchResult.Error("当前媒体还没有可分享的真实文件。")
        }

        val shouldZip = sharePreferences.shouldZip(preparedFiles.size)
        return@withContext if (shouldZip) {
            val zipFile = createZipFile(
                packageBaseName = packageBaseName,
                preparedFiles = preparedFiles.values.toList(),
                sessionDir = sessionDir,
            )
            launchShareIntent(
                context = context,
                files = listOf(zipFile),
                chooserTitle = "分享压缩包",
                mediaCount = preparedFiles.size,
                zipped = true,
                skippedCount = skippedItems.size,
            )
        } else {
            launchShareIntent(
                context = context,
                files = preparedFiles.values.toList(),
                chooserTitle = if (preparedFiles.size > 1) "分享媒体" else "分享图片",
                mediaCount = preparedFiles.size,
                zipped = false,
                skippedCount = skippedItems.size,
            )
        }
    }

    private suspend fun launchShareIntent(
        context: Context,
        files: List<File>,
        chooserTitle: String,
        mediaCount: Int,
        zipped: Boolean,
        skippedCount: Int,
    ): MediaShareLaunchResult {
        val uris = files.map { file ->
            FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file,
            )
        }
        return withContext(Dispatchers.Main) {
            val mimeType = shareIntentMimeType(files)
            val intent = if (uris.size == 1) {
                Intent(Intent.ACTION_SEND).apply {
                    type = mimeType
                    putExtra(Intent.EXTRA_STREAM, uris.first())
                    clipData = ClipData.newUri(context.contentResolver, files.first().name, uris.first())
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
            } else {
                val clipData = ClipData.newUri(context.contentResolver, files.first().name, uris.first())
                uris.drop(1).forEach { uri ->
                    clipData.addItem(ClipData.Item(uri))
                }
                Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                    type = mimeType
                    putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
                    this.clipData = clipData
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
            }
            try {
                context.startActivity(
                    Intent.createChooser(intent, chooserTitle).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
                MediaShareLaunchResult.Success(
                    mediaCount = mediaCount,
                    skippedCount = skippedCount,
                    zipped = zipped,
                )
            } catch (_: ActivityNotFoundException) {
                MediaShareLaunchResult.Error("当前设备未提供可用分享入口。")
            } catch (_: Throwable) {
                MediaShareLaunchResult.Error("分享失败，请稍后重试。")
            }
        }
    }

    private fun shareIntentMimeType(files: List<File>): String {
        if (files.size == 1 && files.first().extension.equals("zip", ignoreCase = true)) {
            return "application/zip"
        }
        val extensions = files.map { it.extension.lowercase(Locale.ROOT) }.toSet()
        return when {
            extensions.all { it in ImageExtensions } -> "image/*"
            extensions.all { it in VideoExtensions } -> "video/*"
            else -> "*/*"
        }
    }

    private fun prepareSessionDirectory(context: Context): File {
        val rootDir = context.cacheDir.resolve("share-media").apply { mkdirs() }
        rootDir.listFiles().orEmpty().forEach { child ->
            if (System.currentTimeMillis() - child.lastModified() > 24 * 60 * 60 * 1000L) {
                child.deleteRecursively()
            }
        }
        return rootDir.resolve("session-${System.currentTimeMillis()}").apply { mkdirs() }
    }

    private fun createZipFile(
        packageBaseName: String,
        preparedFiles: List<File>,
        sessionDir: File,
    ): File {
        val zipFile = sessionDir.resolve("${sanitizeFileName(packageBaseName)}.zip")
        ZipOutputStream(FileOutputStream(zipFile)).use { output ->
            preparedFiles.forEach { file ->
                file.inputStream().use { input ->
                    output.putNextEntry(ZipEntry(file.name))
                    input.copyTo(output)
                    output.closeEntry()
                }
            }
        }
        return zipFile
    }

    private fun prepareFileForShare(
        context: Context,
        item: ShareableMediaItem,
        index: Int,
        sessionDir: File,
        imageQualityPreference: ShareImageQualityPreference,
    ): File? {
        val resolvedSource = resolveShareSource(item, imageQualityPreference) ?: return null
        val extension = resolveShareExtension(
            displayName = item.displayName,
            source = resolvedSource,
            mimeType = item.mimeType,
            mediaType = item.mediaType,
        )
        val fileName = buildShareFileName(
            displayName = item.displayName,
            index = index,
            extension = extension,
        )
        val targetFile = sessionDir.resolve(fileName)
        copySourceToFile(
            context = context,
            source = resolvedSource,
            targetFile = targetFile,
        )
        return targetFile
    }

    private fun resolveShareSource(
        item: ShareableMediaItem,
        imageQualityPreference: ShareImageQualityPreference,
    ): String? {
        return when (item.mediaType) {
            AppMediaType.VIDEO -> item.videoUrl ?: item.originalUrl ?: item.previewUrl
            AppMediaType.IMAGE -> when (imageQualityPreference) {
                ShareImageQualityPreference.ORIGINAL -> {
                    item.originalUrl ?: item.previewUrl
                }

                ShareImageQualityPreference.PREVIEW -> {
                    item.previewUrl ?: item.originalUrl
                }
            }
        }?.trim()?.takeIf { it.isNotBlank() }
    }

    private fun copySourceToFile(
        context: Context,
        source: String,
        targetFile: File,
    ) {
        if (source.startsWith("http", ignoreCase = true)) {
            downloadRemoteSourceToFile(source = source, targetFile = targetFile)
            return
        }

        val parsedUri = Uri.parse(source)
        if (parsedUri.scheme.equals("content", ignoreCase = true) ||
            parsedUri.scheme.equals("android.resource", ignoreCase = true) ||
            parsedUri.scheme.equals("file", ignoreCase = true)
        ) {
            context.contentResolver.openInputStream(parsedUri)?.use { input ->
                targetFile.outputStream().use { output -> input.copyTo(output) }
            } ?: error("无法读取待分享媒体。")
            return
        }

        val directFile = File(source)
        if (directFile.exists()) {
            directFile.inputStream().use { input ->
                targetFile.outputStream().use { output -> input.copyTo(output) }
            }
            return
        }

        error("当前媒体还没有可分享的真实文件。")
    }

    private fun downloadRemoteSourceToFile(
        source: String,
        targetFile: File,
    ) {
        val connection = (URL(source).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            doInput = true
            backendMediaRequestHeaders(source, AuthSessionManager.getAccessToken())
                .forEach { (key, value) -> setRequestProperty(key, value) }
            connectTimeout = 15_000
            readTimeout = 60_000
        }
        connection.connect()
        val statusCode = connection.responseCode
        if (statusCode !in 200..299) {
            connection.disconnect()
            error("下载待分享媒体失败。")
        }
        connection.inputStream.use { input ->
            targetFile.outputStream().use { output -> input.copyTo(output) }
        }
        connection.disconnect()
    }

    private fun resolveShareExtension(
        displayName: String,
        source: String,
        mimeType: String?,
        mediaType: AppMediaType,
    ): String {
        val displayNameExtension = displayName.substringAfterLast('.', "").trim()
        if (displayNameExtension.isNotBlank()) {
            return displayNameExtension.lowercase(Locale.ROOT)
        }
        val pathExtension = MimeTypeMap.getFileExtensionFromUrl(source).orEmpty().trim()
        if (pathExtension.isNotBlank()) {
            return pathExtension.lowercase(Locale.ROOT)
        }
        val mimeExtension = mimeType
            ?.substringAfter('/', "")
            ?.substringBefore(';')
            ?.trim()
            ?.takeIf { it.isNotBlank() }
        if (mimeExtension != null) {
            return mimeExtension.lowercase(Locale.ROOT)
        }
        return if (mediaType == AppMediaType.VIDEO) "mp4" else "jpg"
    }

    private fun buildShareFileName(
        displayName: String,
        index: Int,
        extension: String,
    ): String {
        val baseName = displayName.substringBeforeLast('.').trim().ifBlank {
            "media-${index + 1}"
        }
        return "${sanitizeFileName(baseName)}.$extension"
    }

    private fun sanitizeFileName(rawValue: String): String {
        return rawValue
            .trim()
            .replace(InvalidFileNameRegex, "_")
            .trim('_')
            .ifBlank { "media" }
    }

    private val InvalidFileNameRegex = Regex("[\\\\/:*?\"<>|\\s]+")
    private val ImageExtensions = setOf("jpg", "jpeg", "png", "webp", "gif", "heic", "heif")
    private val VideoExtensions = setOf("mp4", "mov", "m4v", "3gp", "webm", "mkv")
}

fun MediaShareLaunchResult.Success.toNoticeMessage(): String {
    val baseMessage = if (zipped) {
        "已准备 ${mediaCount} 项媒体的压缩包，系统分享已打开"
    } else {
        "已准备 ${mediaCount} 项媒体，系统分享已打开"
    }
    return if (skippedCount > 0) {
        "$baseMessage，另跳过 $skippedCount 项暂不可分享的媒体"
    } else {
        baseMessage
    }
}

fun PhotoFeedItem.toShareableMediaItem(): ShareableMediaItem {
    val source = mediaSource
    return ShareableMediaItem(
        mediaId = mediaId,
        mediaType = mediaType,
        mimeType = source?.mimeType,
        displayName = mediaId,
        previewUrl = source.viewerPreviewImageUrl(mediaType) ?: source?.thumbnailUrl ?: source?.mediaUrl,
        originalUrl = source.viewerOriginalImageUrl(mediaType) ?: source?.mediaUrl ?: source?.originalUrl,
        videoUrl = source.viewerVideoUrl(mediaType) ?: source?.mediaUrl,
    )
}

fun PostDetailMediaUiModel.toShareableMediaItem(): ShareableMediaItem {
    val source = mediaSource
    return ShareableMediaItem(
        mediaId = id,
        mediaType = mediaType,
        mimeType = source?.mimeType,
        displayName = id,
        previewUrl = source.viewerPreviewImageUrl(mediaType) ?: source?.thumbnailUrl ?: source?.mediaUrl,
        originalUrl = source.viewerOriginalImageUrl(mediaType) ?: source?.mediaUrl ?: source?.originalUrl,
        videoUrl = source.viewerVideoUrl(mediaType) ?: source?.mediaUrl,
    )
}

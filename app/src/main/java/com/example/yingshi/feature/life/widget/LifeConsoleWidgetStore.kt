package com.example.yingshi.feature.life.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.media.ExifInterface
import com.example.yingshi.data.model.RemoteLifeConsoleMediaSlot
import com.example.yingshi.data.model.RemoteLifeConsoleBowelUserSummary
import com.example.yingshi.data.model.RemoteLifeConsoleToday
import com.example.yingshi.data.model.RemoteMedia
import com.example.yingshi.data.remote.auth.AuthSessionManager
import com.example.yingshi.feature.life.firstUsableImageUrl
import com.example.yingshi.feature.life.isVideo
import com.example.yingshi.data.remote.config.RemoteServiceFactory
import com.google.gson.Gson
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.max

internal object LifeConsoleWidgetStore {
    private const val PREFS_NAME = "life_console_widget"
    private const val KEY_SNAPSHOT_JSON = "snapshot_json"
    private const val KEY_STATUS = "status"
    private const val KEY_INDEX_PREFIX = "index_"
    private const val KEY_COUNT_PREFIX = "count_"
    private const val KEY_FRONT_SLOT_CONSOLE = "front_slot_console"
    private const val KEY_FRONT_SLOT_PEOPLE = "front_slot_people"
    private const val THUMB_TARGET_PX = 720

    private val gson = Gson()

    fun loadSnapshot(context: Context): RemoteLifeConsoleToday? {
        val json = prefs(context).getString(KEY_SNAPSHOT_JSON, null)?.takeIf { it.isNotBlank() }
            ?: return null
        return runCatching {
            gson.fromJson(json, RemoteLifeConsoleToday::class.java)
        }.getOrNull()
    }

    fun saveSnapshot(context: Context, snapshot: RemoteLifeConsoleToday) {
        prefs(context).edit()
            .putString(KEY_SNAPSHOT_JSON, gson.toJson(snapshot))
            .apply()
        clampIndexes(context, snapshot)
        // NFR-3: 顺带清理过期缓存（7 天前的缩略图），跳过当前快照仍引用的文件避免误删
        val protectedMediaIds = listOf(
            snapshot.personSelf,
            snapshot.personPartner,
            snapshot.mealSelf,
            snapshot.mealPartner,
        ).flatMap { it.mediaItems }.map { it.mediaId }.toSet()
        cleanExpiredCache(context, protectedMediaIds)
    }

    fun status(context: Context): String {
        return prefs(context).getString(KEY_STATUS, "待刷新").orEmpty().ifBlank { "待刷新" }
    }

    fun saveStatus(context: Context, status: String) {
        prefs(context).edit().putString(KEY_STATUS, status).apply()
    }

    fun currentIndex(context: Context, slotKey: LifeConsoleWidgetSlotKey, itemCount: Int): Int {
        if (itemCount <= 0) return 0
        return prefs(context)
            .getInt(KEY_INDEX_PREFIX + slotKey.storageKey, itemCount - 1)
            .coerceIn(0, itemCount - 1)
    }

    fun moveIndex(context: Context, slotKey: LifeConsoleWidgetSlotKey, delta: Int) {
        val snapshot = loadSnapshot(context) ?: return
        val itemCount = snapshot.slot(slotKey).mediaItems.size
        if (itemCount <= 0) return
        val current = currentIndex(context, slotKey, itemCount)
        val next = (current + delta).floorMod(itemCount)
        prefs(context).edit()
            .putInt(KEY_INDEX_PREFIX + slotKey.storageKey, next)
            .apply()
    }

    /**
     * FR-5: 持久化 front_slot（当前置顶的相框）。
     * Console 和 People 共用同一 SP 文件，按 slotKey.category 区分两个键：
     * - PERSON → front_slot_people（值 person_self/person_partner）
     * - MEAL → front_slot_console（值 meal_self/meal_partner）
     */
    fun saveFrontSlot(context: Context, slotKey: LifeConsoleWidgetSlotKey) {
        val key = frontSlotKeyFor(slotKey)
        prefs(context).edit().putString(key, slotKey.storageKey).apply()
    }

    /**
     * FR-5: 读取当前置顶的 slotKey storageKey。
     * 默认值 = self（自己框在前，符合 FR-2 AC-3 下框默认在前层）。
     */
    fun currentFrontSlot(context: Context, slotKey: LifeConsoleWidgetSlotKey): String {
        val key = frontSlotKeyFor(slotKey)
        val default = if (slotKey.category == LifeConsoleWidgetProvider.CATEGORY_PERSON) {
            LifeConsoleWidgetSlotKey.PERSON_SELF.storageKey
        } else {
            LifeConsoleWidgetSlotKey.MEAL_SELF.storageKey
        }
        return prefs(context).getString(key, default) ?: default
    }

    private fun frontSlotKeyFor(slotKey: LifeConsoleWidgetSlotKey): String {
        return if (slotKey.category == LifeConsoleWidgetProvider.CATEGORY_PERSON) {
            KEY_FRONT_SLOT_PEOPLE
        } else {
            KEY_FRONT_SLOT_CONSOLE
        }
    }

    fun removeMediaOptimistically(
        context: Context,
        slotKey: LifeConsoleWidgetSlotKey,
        mediaId: String,
    ): RemoteLifeConsoleToday? {
        val snapshot = loadSnapshot(context) ?: return null
        saveSnapshot(context, snapshot.withSlot(slotKey) { slot ->
            slot.copy(mediaItems = slot.mediaItems.filterNot { it.mediaId == mediaId })
        })
        return snapshot
    }

    fun restoreSnapshot(context: Context, snapshot: RemoteLifeConsoleToday) {
        saveSnapshot(context, snapshot)
    }

    fun updateBowelOptimistically(context: Context, delta: Int): RemoteLifeConsoleToday? {
        val snapshot = loadSnapshot(context) ?: return null
        val userId = snapshot.currentUser.userId
        val nowMillis = System.currentTimeMillis()
        val users = snapshot.bowel.users.toMutableList()
        val userIndex = users.indexOfFirst { it.userId == userId }
        val current = users.getOrNull(userIndex) ?: RemoteLifeConsoleBowelUserSummary(
            userId = userId,
            count = 0,
            latestOccurredAtMillis = null,
            eventTimesMillis = emptyList(),
        )
        if (delta < 0 && current.count <= 0) return null
        val nextCount = (current.count + delta).coerceAtLeast(0)
        val nextTimes = if (delta > 0) {
            current.eventTimesMillis + nowMillis
        } else {
            current.eventTimesMillis.dropLast(1)
        }
        val nextUser = current.copy(
            count = nextCount,
            latestOccurredAtMillis = nextTimes.lastOrNull(),
            eventTimesMillis = nextTimes,
        )
        if (userIndex >= 0) {
            users[userIndex] = nextUser
        } else {
            users += nextUser
        }
        saveSnapshot(context, snapshot.copy(bowel = snapshot.bowel.copy(users = users)))
        return snapshot
    }

    fun cachedBitmapFor(context: Context, media: RemoteMedia): Bitmap? {
        val file = thumbnailFile(context, media)
        if (!file.exists() || file.length() <= 0L) return null
        // FR-9: 圆角裁切半径 = 5dp（与相纸白底圆角一致），按 density 换算为 px
        val radiusPx = 5f * context.resources.displayMetrics.density
        return decodeSampledBitmap(file, radiusPx)
    }

    fun warmThumbnailCache(context: Context, snapshot: RemoteLifeConsoleToday) {
        listOf(
            snapshot.personSelf,
            snapshot.personPartner,
            snapshot.mealSelf,
            snapshot.mealPartner,
        ).flatMap { it.mediaItems }
            .distinctBy { it.mediaId }
            .forEach { media ->
                runCatching { ensureThumbnailCached(context, media) }
            }
    }

    private fun ensureThumbnailCached(context: Context, media: RemoteMedia): File? {
        val file = thumbnailFile(context, media)
        if (file.exists() && file.length() > 0L) return file
        val rawUrl = media.widgetCacheUrl()
            ?: return null
        val url = resolvedUrl(rawUrl)
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 8_000
            readTimeout = 15_000
            requestMethod = "GET"
            AuthSessionManager.getAccessToken()?.let { token ->
                setRequestProperty("Authorization", "Bearer $token")
            }
        }
        try {
            if (connection.responseCode !in 200..299) {
                return null
            }
            file.parentFile?.mkdirs()
            connection.inputStream.use { input ->
                file.outputStream().use { output -> input.copyTo(output) }
            }
        } finally {
            connection.disconnect()
        }
        return file.takeIf { it.exists() && it.length() > 0L }
    }

    private fun resolvedUrl(rawUrl: String): String {
        if (rawUrl.startsWith("http://") || rawUrl.startsWith("https://")) {
            return rawUrl
        }
        val baseUrl = RemoteServiceFactory.currentBaseUrl().trimEnd('/')
        return baseUrl + "/" + rawUrl.trimStart('/')
    }

    private fun thumbnailFile(context: Context, media: RemoteMedia): File {
        val safeId = media.mediaId.replace(Regex("[^A-Za-z0-9._-]"), "_")
        return File(context.cacheDir, "life-console-widget/$safeId.original-thumb")
    }

    /**
     * FR-9: 位图解码 + 圆角裁切。
     * 解码使用 RGB_565 降采样，圆角裁切输出 ARGB_8888（支持 alpha 透明圆角区）。
     * 裁切在解码缓存阶段一次性完成，不增加每次渲染开销（AC-2）。
     *
     * @param file 缓存文件
     * @param radiusPx 圆角半径 px，<=0 时不裁切
     */
    private fun decodeSampledBitmap(file: File, radiusPx: Float = 0f): Bitmap? {
        val bounds = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        val sampleSize = calculateSampleSize(bounds.outWidth, bounds.outHeight)
        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = Bitmap.Config.RGB_565
        }
        val decoded = BitmapFactory.decodeFile(file.absolutePath, options) ?: return null
        val oriented = applyExifOrientation(file, decoded)
        return if (radiusPx > 0f) applyRoundCorners(oriented, radiusPx) else oriented
    }

    /**
     * 修复照片方向：读取 EXIF orientation 标签并旋转/翻转 bitmap，
     * 使小组件显示方向与查看器（Coil 自动应用 EXIF）保持一致。
     * 无方向信息或已是正常方向时原样返回。
     */
    private fun applyExifOrientation(file: File, bitmap: Bitmap): Bitmap {
        val exif = try {
            ExifInterface(file.absolutePath)
        } catch (e: Exception) {
            return bitmap
        }
        val orientation = exif.getAttributeInt(
            ExifInterface.TAG_ORIENTATION,
            ExifInterface.ORIENTATION_NORMAL,
        )
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> {
                matrix.postRotate(90f)
                matrix.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_TRANSVERSE -> {
                matrix.postRotate(270f)
                matrix.postScale(-1f, 1f)
            }
            else -> return bitmap
        }
        val transformed = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        if (transformed != bitmap) bitmap.recycle()
        return transformed
    }

    /**
     * FR-9: 用 Canvas + Path 预裁切圆角。
     * 创建 ARGB_8888 目标 Bitmap（RGB_565 不支持 alpha），通过 SRC_IN xfermode 仅保留圆角内像素。
     * 回收源 RGB_565 Bitmap 释放内存。
     */
    private fun applyRoundCorners(bitmap: Bitmap, radiusPx: Float): Bitmap {
        if (radiusPx <= 0f) return bitmap
        val output = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val rect = RectF(0f, 0f, bitmap.width.toFloat(), bitmap.height.toFloat())
        val path = Path().apply { addRoundRect(rect, radiusPx, radiusPx, Path.Direction.CW) }
        canvas.drawPath(path, paint)
        paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
        canvas.drawBitmap(bitmap, 0f, 0f, paint)
        bitmap.recycle()
        return output
    }

    /**
     * NFR-3: 缩略图缓存清理，修复只增不删问题。
     * 扫描缓存目录，删除最后修改时间超过 maxAgeMillis 的文件（默认 7 天）。
     * 在 saveSnapshot 后调用，跳过 [protectedMediaIds] 中仍被当前快照引用的文件，避免误删有效缓存。
     */
    fun cleanExpiredCache(
        context: Context,
        protectedMediaIds: Set<String> = emptySet(),
        maxAgeMillis: Long = 7L * 24 * 3600 * 1000L,
    ) {
        runCatching {
            val cacheDir = File(context.cacheDir, "life-console-widget")
            if (!cacheDir.exists()) return@runCatching
            val cutoff = System.currentTimeMillis() - maxAgeMillis
            cacheDir.listFiles()?.forEach { file ->
                if (!file.isFile || file.lastModified() >= cutoff) return@forEach
                // 跳过当前快照仍引用的缩略图（文件名格式：{safeId}.original-thumb）
                val safeId = file.nameWithoutExtension
                if (protectedMediaIds.any { it.replace(Regex("[^A-Za-z0-9._-]"), "_") == safeId }) {
                    return@forEach
                }
                file.delete()
            }
        }
    }

    private fun calculateSampleSize(width: Int, height: Int): Int {
        if (width <= 0 || height <= 0) return 1
        var sample = 1
        var halfWidth = width / 2
        var halfHeight = height / 2
        while (halfWidth / sample >= THUMB_TARGET_PX || halfHeight / sample >= THUMB_TARGET_PX) {
            sample *= 2
        }
        return max(1, sample)
    }

    private fun clampIndexes(context: Context, snapshot: RemoteLifeConsoleToday) {
        val editor = prefs(context).edit()
        LifeConsoleWidgetSlotKey.entries.forEach { slotKey ->
            val count = snapshot.slot(slotKey).mediaItems.size
            val key = KEY_INDEX_PREFIX + slotKey.storageKey
            val countKey = KEY_COUNT_PREFIX + slotKey.storageKey
            val prevCount = prefs(context).getInt(countKey, 0)
            if (count != prevCount && count > 0) {
                // Media list changed (sync): jump to the latest item
                editor.putInt(key, count - 1)
            } else if (count <= 0) {
                editor.remove(key)
            }
            // else: count unchanged — keep user's current position
            editor.putInt(countKey, count)
        }
        editor.apply()
    }

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private fun Int.floorMod(modulus: Int): Int {
        return ((this % modulus) + modulus) % modulus
    }

    private fun RemoteMedia.widgetCacheUrl(): String? {
        return if (isVideo()) {
            firstUsableImageUrl(coverUrl, thumbnailUrl, previewUrl, mediaUrl, originalUrl)
        } else {
            firstUsableImageUrl(originalUrl, mediaUrl, previewUrl, thumbnailUrl, coverUrl)
        }
    }
}

internal enum class LifeConsoleWidgetSlotKey(
    val storageKey: String,
    val category: String,
    val editable: Boolean,
) {
    PERSON_SELF("person_self", LifeConsoleWidgetProvider.CATEGORY_PERSON, true),
    PERSON_PARTNER("person_partner", LifeConsoleWidgetProvider.CATEGORY_PERSON, false),
    MEAL_SELF("meal_self", LifeConsoleWidgetProvider.CATEGORY_MEAL, true),
    MEAL_PARTNER("meal_partner", LifeConsoleWidgetProvider.CATEGORY_MEAL, false),
}

internal fun RemoteLifeConsoleToday.slot(slotKey: LifeConsoleWidgetSlotKey): RemoteLifeConsoleMediaSlot {
    return when (slotKey) {
        LifeConsoleWidgetSlotKey.PERSON_SELF -> personSelf
        LifeConsoleWidgetSlotKey.PERSON_PARTNER -> personPartner
        LifeConsoleWidgetSlotKey.MEAL_SELF -> mealSelf
        LifeConsoleWidgetSlotKey.MEAL_PARTNER -> mealPartner
    }
}

private fun RemoteLifeConsoleToday.withSlot(
    slotKey: LifeConsoleWidgetSlotKey,
    transform: (RemoteLifeConsoleMediaSlot) -> RemoteLifeConsoleMediaSlot,
): RemoteLifeConsoleToday {
    return when (slotKey) {
        LifeConsoleWidgetSlotKey.PERSON_SELF -> copy(personSelf = transform(personSelf))
        LifeConsoleWidgetSlotKey.PERSON_PARTNER -> copy(personPartner = transform(personPartner))
        LifeConsoleWidgetSlotKey.MEAL_SELF -> copy(mealSelf = transform(mealSelf))
        LifeConsoleWidgetSlotKey.MEAL_PARTNER -> copy(mealPartner = transform(mealPartner))
    }
}

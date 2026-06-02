package com.example.yingshi.feature.life.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.example.yingshi.data.model.RemoteLifeConsoleMediaSlot
import com.example.yingshi.data.model.RemoteLifeConsoleToday
import com.example.yingshi.data.model.RemoteMedia
import com.example.yingshi.data.remote.auth.AuthSessionManager
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
    private const val THUMB_TARGET_PX = 360

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
            .getInt(KEY_INDEX_PREFIX + slotKey.storageKey, 0)
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

    fun cachedBitmapFor(context: Context, media: RemoteMedia): Bitmap? {
        val file = thumbnailFile(context, media)
        if (!file.exists() || file.length() <= 0L) return null
        return decodeSampledBitmap(file)
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
        val rawUrl = media.thumbnailUrl
            ?: media.previewUrl
            ?: media.coverUrl
            ?: media.mediaUrl
            ?: media.originalUrl
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
        return File(context.cacheDir, "life-console-widget/$safeId.thumb")
    }

    private fun decodeSampledBitmap(file: File): Bitmap? {
        val bounds = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        val sampleSize = calculateSampleSize(bounds.outWidth, bounds.outHeight)
        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = Bitmap.Config.RGB_565
        }
        return BitmapFactory.decodeFile(file.absolutePath, options)
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
            val index = currentIndex(context, slotKey, count)
            editor.putInt(KEY_INDEX_PREFIX + slotKey.storageKey, index)
        }
        editor.apply()
    }

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private fun Int.floorMod(modulus: Int): Int {
        return ((this % modulus) + modulus) % modulus
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

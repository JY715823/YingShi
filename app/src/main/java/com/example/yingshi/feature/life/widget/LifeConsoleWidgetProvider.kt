package com.example.yingshi.feature.life.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.View
import android.widget.RemoteViews
import com.example.yingshi.MainActivity
import com.example.yingshi.R
import com.example.yingshi.app.AppNavigationRequests
import com.example.yingshi.data.model.RemoteLifeConsoleMediaSlot
import com.example.yingshi.data.model.RemoteLifeConsoleToday
import com.example.yingshi.data.model.RemoteMedia
import com.example.yingshi.data.remote.auth.AuthSessionManager
import com.example.yingshi.data.remote.config.BackendDebugConfig
import com.example.yingshi.data.remote.result.ApiResult
import com.example.yingshi.data.repository.RepositoryProvider
import com.example.yingshi.feature.life.WidgetMediaEntryActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class LifeConsoleWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        LifeConsoleWidgetController.renderAllFromStore(context)
        LifeConsoleWidgetController.fetchAndUpdateAll(context)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        LifeConsoleWidgetController.handleReceive(context, intent)
    }

    companion object {
        const val EXTRA_CATEGORY = "life_console_category"
        const val CATEGORY_PERSON = "PERSON"
        const val CATEGORY_MEAL = "MEAL"

        fun refreshAll(context: Context) {
            LifeConsoleWidgetController.refreshAll(context)
        }

        fun applySnapshot(context: Context, snapshot: RemoteLifeConsoleToday) {
            LifeConsoleWidgetController.applySnapshot(context, snapshot)
        }
    }
}

internal object LifeConsoleWidgetController {
    private const val EXTRA_SLOT_KEY = "life_console_slot_key"
    private const val ACTION_REFRESH = "com.example.yingshi.widget.REFRESH_LIFE_CONSOLE"
    private const val ACTION_BOWEL_ADD = "com.example.yingshi.widget.BOWEL_ADD"
    private const val ACTION_BOWEL_REMOVE = "com.example.yingshi.widget.BOWEL_REMOVE"
    private const val ACTION_SLOT_PREV = "com.example.yingshi.widget.SLOT_PREV"
    private const val ACTION_SLOT_NEXT = "com.example.yingshi.widget.SLOT_NEXT"
    private const val ACTION_SLOT_DELETE = "com.example.yingshi.widget.SLOT_DELETE"
    private const val LANE_CONSOLE = "console"
    private const val LANE_PEOPLE = "people"

    private val widgetActions = setOf(
        ACTION_REFRESH,
        ACTION_BOWEL_ADD,
        ACTION_BOWEL_REMOVE,
        ACTION_SLOT_PREV,
        ACTION_SLOT_NEXT,
        ACTION_SLOT_DELETE,
    )
    private val widgetScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun refreshAll(context: Context) {
        renderAllFromStore(context)
        fetchAndUpdateAll(context)
    }

    fun applySnapshot(context: Context, snapshot: RemoteLifeConsoleToday) {
        initRuntime(context)
        LifeConsoleWidgetStore.saveSnapshot(context, snapshot)
        LifeConsoleWidgetStore.saveStatus(context, updateStatus("已更新"))
        renderAllFromStore(context)
        widgetScope.launch {
            LifeConsoleWidgetStore.warmThumbnailCache(context, snapshot)
            renderAllFromStore(context)
        }
    }

    fun handleReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action !in widgetActions) return

        initRuntime(context)
        val slotKey = intent.getStringExtra(EXTRA_SLOT_KEY)?.let(::slotKeyOrNull)
        when (action) {
            ACTION_REFRESH -> fetchAndUpdateAll(context)
            ACTION_BOWEL_ADD -> widgetScope.launch {
                LifeConsoleWidgetStore.saveStatus(context, "记录中")
                renderAllFromStore(context)
                RepositoryProvider.lifeConsoleRepository.addBowelEvent()
                fetchAndUpdateAll(context)
            }
            ACTION_BOWEL_REMOVE -> widgetScope.launch {
                LifeConsoleWidgetStore.saveStatus(context, "删除中")
                renderAllFromStore(context)
                RepositoryProvider.lifeConsoleRepository.deleteLatestBowelEvent()
                fetchAndUpdateAll(context)
            }
            ACTION_SLOT_PREV -> {
                if (slotKey != null) {
                    LifeConsoleWidgetStore.moveIndex(context, slotKey, -1)
                    renderAllFromStore(context)
                }
            }
            ACTION_SLOT_NEXT -> {
                if (slotKey != null) {
                    LifeConsoleWidgetStore.moveIndex(context, slotKey, 1)
                    renderAllFromStore(context)
                }
            }
            ACTION_SLOT_DELETE -> {
                if (slotKey != null) {
                    deleteCurrentMedia(context, slotKey)
                }
            }
        }
    }

    fun renderAllFromStore(context: Context) {
        val manager = AppWidgetManager.getInstance(context)
        val snapshot = LifeConsoleWidgetStore.loadSnapshot(context)
        val status = LifeConsoleWidgetStore.status(context)
        updateConsoleWidgets(context, manager, status, snapshot)
        updatePeopleWidgets(context, manager, status, snapshot)
    }

    fun fetchAndUpdateAll(context: Context) {
        initRuntime(context)
        LifeConsoleWidgetStore.saveStatus(context, "同步中")
        renderAllFromStore(context)
        widgetScope.launch {
            when (val result = RepositoryProvider.lifeConsoleRepository.getToday()) {
                is ApiResult.Success -> {
                    LifeConsoleWidgetStore.saveSnapshot(context, result.data)
                    LifeConsoleWidgetStore.saveStatus(context, updateStatus("更新"))
                    renderAllFromStore(context)
                    LifeConsoleWidgetStore.warmThumbnailCache(context, result.data)
                    renderAllFromStore(context)
                }
                is ApiResult.Error -> {
                    LifeConsoleWidgetStore.saveStatus(context, "同步失败")
                    renderAllFromStore(context)
                }
                ApiResult.Loading -> Unit
            }
        }
    }

    private fun deleteCurrentMedia(context: Context, slotKey: LifeConsoleWidgetSlotKey) {
        if (!slotKey.editable) return
        val snapshot = LifeConsoleWidgetStore.loadSnapshot(context) ?: return
        val slot = snapshot.slot(slotKey)
        val currentMedia = currentMedia(context, slotKey, slot) ?: return
        val restoredSnapshot = LifeConsoleWidgetStore.removeMediaOptimistically(context, slotKey, currentMedia.mediaId)
            ?: return
        LifeConsoleWidgetStore.saveStatus(context, "删除中")
        renderAllFromStore(context)

        widgetScope.launch {
            when (RepositoryProvider.lifeConsoleRepository.deleteMedia(slotKey.category, currentMedia.mediaId)) {
                is ApiResult.Success -> {
                    LifeConsoleWidgetStore.saveStatus(context, updateStatus("已删除"))
                    fetchAndUpdateAll(context)
                }
                is ApiResult.Error -> {
                    LifeConsoleWidgetStore.restoreSnapshot(context, restoredSnapshot)
                    LifeConsoleWidgetStore.saveStatus(context, "删除失败")
                    renderAllFromStore(context)
                }
                ApiResult.Loading -> Unit
            }
        }
    }

    private fun updateConsoleWidgets(
        context: Context,
        appWidgetManager: AppWidgetManager,
        status: String,
        snapshot: RemoteLifeConsoleToday?,
    ) {
        val ids = appWidgetManager.getAppWidgetIds(ComponentName(context, LifeConsoleWidgetProvider::class.java))
        ids.forEach { appWidgetId ->
            appWidgetManager.updateAppWidget(appWidgetId, buildConsoleViews(context, status, snapshot))
        }
    }

    private fun updatePeopleWidgets(
        context: Context,
        appWidgetManager: AppWidgetManager,
        status: String,
        snapshot: RemoteLifeConsoleToday?,
    ) {
        val ids = appWidgetManager.getAppWidgetIds(ComponentName(context, LifePeopleWidgetProvider::class.java))
        ids.forEach { appWidgetId ->
            appWidgetManager.updateAppWidget(appWidgetId, buildPeopleViews(context, status, snapshot))
        }
    }

    private fun buildConsoleViews(
        context: Context,
        status: String,
        snapshot: RemoteLifeConsoleToday?,
    ): RemoteViews {
        return RemoteViews(context.packageName, R.layout.life_console_widget).apply {
            setTextViewText(R.id.widget_status, status)
            bindConsoleActions(context)
            bindSlot(context, snapshot, LifeConsoleWidgetSlotKey.MEAL_SELF, MealSelfViews)
            bindSlot(context, snapshot, LifeConsoleWidgetSlotKey.MEAL_PARTNER, MealPartnerViews)
            bindBowel(snapshot)
        }
    }

    private fun buildPeopleViews(
        context: Context,
        status: String,
        snapshot: RemoteLifeConsoleToday?,
    ): RemoteViews {
        return RemoteViews(context.packageName, R.layout.life_people_widget).apply {
            setTextViewText(R.id.widget_status, status)
            bindPeopleActions(context)
            bindSlot(context, snapshot, LifeConsoleWidgetSlotKey.PERSON_SELF, PersonSelfViews)
            bindSlot(context, snapshot, LifeConsoleWidgetSlotKey.PERSON_PARTNER, PersonPartnerViews)
        }
    }

    private fun RemoteViews.bindConsoleActions(context: Context) {
        setOnClickPendingIntent(
            R.id.widget_open_console,
            openMainIntent(context, AppNavigationRequests.ACTION_OPEN_LIFE_CONSOLE, LANE_CONSOLE, 10),
        )
        setOnClickPendingIntent(
            R.id.widget_ledger,
            openMainIntent(context, AppNavigationRequests.ACTION_OPEN_LEDGER, LANE_CONSOLE, 11),
        )
        setOnClickPendingIntent(R.id.widget_refresh, widgetBroadcast(context, LifeConsoleWidgetProvider::class.java, LANE_CONSOLE, ACTION_REFRESH, 12))
        setOnClickPendingIntent(R.id.widget_bowel_add, widgetBroadcast(context, LifeConsoleWidgetProvider::class.java, LANE_CONSOLE, ACTION_BOWEL_ADD, 13))
        setOnClickPendingIntent(R.id.widget_bowel_remove, widgetBroadcast(context, LifeConsoleWidgetProvider::class.java, LANE_CONSOLE, ACTION_BOWEL_REMOVE, 14))
        setOnClickPendingIntent(
            R.id.widget_meal_self_upload,
            openMediaEntry(context, LifeConsoleWidgetProvider.CATEGORY_MEAL, LANE_CONSOLE, 16),
        )
    }

    private fun RemoteViews.bindPeopleActions(context: Context) {
        setOnClickPendingIntent(
            R.id.widget_open_console,
            openMainIntent(context, AppNavigationRequests.ACTION_OPEN_LIFE_CONSOLE, LANE_PEOPLE, 20),
        )
        setOnClickPendingIntent(R.id.widget_refresh, widgetBroadcast(context, LifePeopleWidgetProvider::class.java, LANE_PEOPLE, ACTION_REFRESH, 22))
        setOnClickPendingIntent(
            R.id.widget_person_self_upload,
            openMediaEntry(context, LifeConsoleWidgetProvider.CATEGORY_PERSON, LANE_PEOPLE, 25),
        )
    }

    private fun RemoteViews.bindSlot(
        context: Context,
        snapshot: RemoteLifeConsoleToday?,
        slotKey: LifeConsoleWidgetSlotKey,
        views: SlotViews,
    ) {
        val slot = snapshot?.slot(slotKey)
        val mediaItems = slot?.mediaItems.orEmpty()
        val currentIndex = LifeConsoleWidgetStore.currentIndex(context, slotKey, mediaItems.size)
        val media = mediaItems.getOrNull(currentIndex)
        val countText = if (mediaItems.isEmpty()) "0" else "${currentIndex + 1}/${mediaItems.size}"
        val typePrefix = if (media?.isVideo() == true) "▶ " else ""

        setTextViewText(views.metaId, typePrefix + countText)
        setOnClickPendingIntent(views.prevId, slotIntent(context, ACTION_SLOT_PREV, slotKey, views.prevId))
        setOnClickPendingIntent(views.nextId, slotIntent(context, ACTION_SLOT_NEXT, slotKey, views.nextId))

        if (views.openId != null) {
            setOnClickPendingIntent(
                views.openId,
                openMainIntent(
                    context = context,
                    action = AppNavigationRequests.ACTION_OPEN_LIFE_CONSOLE,
                    lane = laneFor(slotKey),
                    requestCode = requestCodeFor(slotKey, views.openId),
                    slotKey = slotKey,
                    mediaId = media?.mediaId,
                ),
            )
        }
        if (views.uploadId != null) {
            setOnClickPendingIntent(
                views.uploadId,
                openMediaEntry(context, slotKey.category, laneFor(slotKey), requestCodeFor(slotKey, views.uploadId)),
            )
            setViewVisibility(views.uploadId, if (slotKey.editable) View.VISIBLE else View.GONE)
        }
        if (views.deleteId != null) {
            setOnClickPendingIntent(views.deleteId, slotIntent(context, ACTION_SLOT_DELETE, slotKey, views.deleteId))
            setViewVisibility(views.deleteId, if (media != null && slotKey.editable) View.VISIBLE else View.GONE)
        }

        if (media == null) {
            setViewVisibility(views.imageId, View.GONE)
            setViewVisibility(views.emptyId, View.VISIBLE)
            setTextViewText(views.emptyId, "今天还没有")
            return
        }

        val bitmap = LifeConsoleWidgetStore.cachedBitmapFor(context, media)
        if (bitmap != null) {
            setImageViewBitmap(views.imageId, bitmap)
            setViewVisibility(views.imageId, View.VISIBLE)
            setViewVisibility(views.emptyId, View.GONE)
        } else {
            setViewVisibility(views.imageId, View.GONE)
            setViewVisibility(views.emptyId, View.VISIBLE)
            setTextViewText(views.emptyId, if (media.isVideo()) "视频封面同步中" else "图片同步中")
        }
    }

    private fun RemoteViews.bindBowel(snapshot: RemoteLifeConsoleToday?) {
        val currentUserId = snapshot?.currentUser?.userId
        val partnerUserId = snapshot?.partner?.userId
        val bowelSelf = snapshot?.bowel?.users?.firstOrNull { it.userId == currentUserId }
        val bowelPartner = snapshot?.bowel?.users?.firstOrNull { it.userId == partnerUserId }
        val selfCount = bowelSelf?.count ?: 0
        val partnerCount = bowelPartner?.count ?: 0
        setTextViewText(R.id.widget_bowel_self_count, selfCount.toString())
        setTextViewText(R.id.widget_bowel_partner_count, partnerCount.toString())
        setTextViewText(R.id.widget_bowel_self_latest, latestText(bowelSelf?.latestOccurredAtMillis))
        setTextViewText(R.id.widget_bowel_partner_latest, latestText(bowelPartner?.latestOccurredAtMillis))
    }

    private fun currentMedia(
        context: Context,
        slotKey: LifeConsoleWidgetSlotKey,
        slot: RemoteLifeConsoleMediaSlot,
    ): RemoteMedia? {
        val index = LifeConsoleWidgetStore.currentIndex(context, slotKey, slot.mediaItems.size)
        return slot.mediaItems.getOrNull(index)
    }

    private fun RemoteMedia.isVideo(): Boolean {
        return mediaType.equals("video", ignoreCase = true) ||
            mimeType?.startsWith("video/", ignoreCase = true) == true ||
            videoUrl != null
    }

    private fun latestText(latestTimeMillis: Long?): String {
        return latestTimeMillis?.let { "最近 ${formatTime(it)}" } ?: "今天还没有"
    }

    private fun formatTime(timeMillis: Long): String {
        return SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(timeMillis))
    }

    private fun updateStatus(prefix: String): String {
        return "$prefix ${formatTime(System.currentTimeMillis())}"
    }

    private fun slotKeyOrNull(value: String): LifeConsoleWidgetSlotKey? {
        return LifeConsoleWidgetSlotKey.entries.firstOrNull { it.storageKey == value }
    }

    private fun openMainIntent(
        context: Context,
        action: String,
        lane: String,
        requestCode: Int,
        slotKey: LifeConsoleWidgetSlotKey? = null,
        mediaId: String? = null,
    ): PendingIntent {
        val safeSlotKey = slotKey?.storageKey.orEmpty()
        val safeMediaId = mediaId.orEmpty()
        val resolvedRequestCode = widgetRequestCode(lane, requestCode)
        val intent = Intent(context, MainActivity::class.java).apply {
            this.action = action
            data = Uri.parse(
                "yingshi://widget/$lane/open/$action/$requestCode/$safeSlotKey/$safeMediaId",
            )
            putExtra(AppNavigationRequests.EXTRA_LIFE_CONSOLE_SLOT_KEY, slotKey?.storageKey)
            putExtra(AppNavigationRequests.EXTRA_LIFE_CONSOLE_MEDIA_ID, mediaId)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            context,
            resolvedRequestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun openMediaEntry(context: Context, category: String, lane: String, requestCode: Int): PendingIntent {
        val resolvedRequestCode = widgetRequestCode(lane, requestCode)
        val intent = Intent(context, WidgetMediaEntryActivity::class.java).apply {
            data = Uri.parse("yingshi://widget/$lane/upload/$requestCode/$category")
            putExtra(LifeConsoleWidgetProvider.EXTRA_CATEGORY, category)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            context,
            resolvedRequestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun slotIntent(
        context: Context,
        action: String,
        slotKey: LifeConsoleWidgetSlotKey,
        requestCode: Int,
    ): PendingIntent {
        val lane = laneFor(slotKey)
        val providerClass = providerClassFor(slotKey)
        val intent = Intent(context, providerClass).apply {
            this.action = action
            data = Uri.parse("yingshi://widget/$lane/slot/$action/${slotKey.storageKey}/$requestCode")
            putExtra(EXTRA_SLOT_KEY, slotKey.storageKey)
        }
        return PendingIntent.getBroadcast(
            context,
            widgetRequestCode(lane, requestCodeFor(slotKey, requestCode)),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun widgetBroadcast(
        context: Context,
        providerClass: Class<out AppWidgetProvider>,
        lane: String,
        action: String,
        requestCode: Int,
    ): PendingIntent {
        val intent = Intent(context, providerClass).apply {
            this.action = action
            data = Uri.parse("yingshi://widget/$lane/action/$action/$requestCode")
        }
        return PendingIntent.getBroadcast(
            context,
            widgetRequestCode(lane, requestCode),
            intent,
            PendingIntent.FLAG_CANCEL_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun requestCodeFor(slotKey: LifeConsoleWidgetSlotKey, base: Int): Int {
        return (slotKey.ordinal + 1) * 10_000 + (base and 0x0FFF)
    }

    private fun laneFor(slotKey: LifeConsoleWidgetSlotKey): String {
        return if (slotKey.category == LifeConsoleWidgetProvider.CATEGORY_PERSON) {
            LANE_PEOPLE
        } else {
            LANE_CONSOLE
        }
    }

    private fun providerClassFor(slotKey: LifeConsoleWidgetSlotKey): Class<out AppWidgetProvider> {
        return if (laneFor(slotKey) == LANE_PEOPLE) {
            LifePeopleWidgetProvider::class.java
        } else {
            LifeConsoleWidgetProvider::class.java
        }
    }

    private fun widgetRequestCode(lane: String, requestCode: Int): Int {
        val laneOffset = if (lane == LANE_PEOPLE) 200_000 else 100_000
        return laneOffset + (requestCode and 0x0FFFFF)
    }

    private fun initRuntime(context: Context) {
        AuthSessionManager.init(context.applicationContext)
        BackendDebugConfig.init(context.applicationContext)
    }

    private data class SlotViews(
        val imageId: Int,
        val emptyId: Int,
        val prevId: Int,
        val nextId: Int,
        val metaId: Int,
        val openId: Int? = null,
        val uploadId: Int? = null,
        val deleteId: Int? = null,
    )

    private val PersonSelfViews = SlotViews(
        imageId = R.id.widget_person_self_image,
        emptyId = R.id.widget_person_self_empty,
        prevId = R.id.widget_person_self_prev,
        nextId = R.id.widget_person_self_next,
        metaId = R.id.widget_person_self_meta,
        openId = R.id.widget_person_self_open,
        uploadId = R.id.widget_person_self_upload,
        deleteId = R.id.widget_person_self_delete,
    )

    private val PersonPartnerViews = SlotViews(
        imageId = R.id.widget_person_partner_image,
        emptyId = R.id.widget_person_partner_empty,
        prevId = R.id.widget_person_partner_prev,
        nextId = R.id.widget_person_partner_next,
        metaId = R.id.widget_person_partner_meta,
        openId = R.id.widget_person_partner_open,
    )

    private val MealSelfViews = SlotViews(
        imageId = R.id.widget_meal_self_image,
        emptyId = R.id.widget_meal_self_empty,
        prevId = R.id.widget_meal_self_prev,
        nextId = R.id.widget_meal_self_next,
        metaId = R.id.widget_meal_self_meta,
        openId = R.id.widget_meal_self_open,
        uploadId = R.id.widget_meal_self_upload,
        deleteId = R.id.widget_meal_self_delete,
    )

    private val MealPartnerViews = SlotViews(
        imageId = R.id.widget_meal_partner_image,
        emptyId = R.id.widget_meal_partner_empty,
        prevId = R.id.widget_meal_partner_prev,
        nextId = R.id.widget_meal_partner_next,
        metaId = R.id.widget_meal_partner_meta,
        openId = R.id.widget_meal_partner_open,
    )
}

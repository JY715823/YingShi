package com.example.yingshi.feature.life.widget

import android.content.Context
import android.view.View
import android.widget.RemoteViews
import com.example.yingshi.R
import com.example.yingshi.data.model.RemoteLifeConsoleMediaSlot
import com.example.yingshi.data.model.RemoteLifeConsoleToday
import com.example.yingshi.data.model.RemoteMedia
import com.example.yingshi.data.remote.auth.AuthSessionManager
import com.example.yingshi.data.remote.config.BackendDebugConfig
import com.example.yingshi.feature.life.isVideo
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Date
import java.util.Locale
import com.example.yingshi.feature.life.LIFE_CONSOLE_ZONE_ID

/**
 * Console Widget 视图构建器。
 * 从 LifeConsoleWidgetController 中提取 Console Widget 的 RemoteViews 构建逻辑。
 * bindSlot 为 Console 和 People Widget 共享。
 */
internal object ConsoleWidgetBuilder {

    private const val ACTION_REFRESH = "com.example.yingshi.widget.REFRESH_LIFE_CONSOLE"
    private const val ACTION_BOWEL_ADD = "com.example.yingshi.widget.BOWEL_ADD"
    private const val ACTION_BOWEL_REMOVE = "com.example.yingshi.widget.BOWEL_REMOVE"
    private const val ACTION_SLOT_PREV = "com.example.yingshi.widget.SLOT_PREV"
    private const val ACTION_SLOT_NEXT = "com.example.yingshi.widget.SLOT_NEXT"
    private const val ACTION_SLOT_DELETE = "com.example.yingshi.widget.SLOT_DELETE"
    private const val LANE_CONSOLE = "console"

    data class SlotViews(
        val imageId: Int,
        val emptyId: Int,
        val prevId: Int,
        val nextId: Int,
        val metaId: Int,
        val openId: Int? = null,
        val uploadId: Int? = null,
        val deleteId: Int? = null,
    )

    val PersonSelfViews = SlotViews(
        imageId = R.id.widget_person_self_image,
        emptyId = R.id.widget_person_self_empty,
        prevId = R.id.widget_person_self_prev,
        nextId = R.id.widget_person_self_next,
        metaId = R.id.widget_person_self_meta,
        openId = R.id.widget_person_self_open,
        uploadId = R.id.widget_person_self_upload,
        deleteId = R.id.widget_person_self_delete,
    )

    val PersonPartnerViews = SlotViews(
        imageId = R.id.widget_person_partner_image,
        emptyId = R.id.widget_person_partner_empty,
        prevId = R.id.widget_person_partner_prev,
        nextId = R.id.widget_person_partner_next,
        metaId = R.id.widget_person_partner_meta,
        openId = R.id.widget_person_partner_open,
    )

    val MealSelfViews = SlotViews(
        imageId = R.id.widget_meal_self_image,
        emptyId = R.id.widget_meal_self_empty,
        prevId = R.id.widget_meal_self_prev,
        nextId = R.id.widget_meal_self_next,
        metaId = R.id.widget_meal_self_meta,
        openId = R.id.widget_meal_self_open,
        uploadId = R.id.widget_meal_self_upload,
        deleteId = R.id.widget_meal_self_delete,
    )

    val MealPartnerViews = SlotViews(
        imageId = R.id.widget_meal_partner_image,
        emptyId = R.id.widget_meal_partner_empty,
        prevId = R.id.widget_meal_partner_prev,
        nextId = R.id.widget_meal_partner_next,
        metaId = R.id.widget_meal_partner_meta,
        openId = R.id.widget_meal_partner_open,
    )

    fun build(
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

    private fun RemoteViews.bindConsoleActions(context: Context) {
        setOnClickPendingIntent(
            R.id.widget_ledger,
            WidgetPendingIntentFactory.openMainIntent(
                context,
                com.example.yingshi.app.AppNavigationRequests.ACTION_OPEN_LEDGER,
                LANE_CONSOLE,
                11,
            ),
        )
        setOnClickPendingIntent(
            R.id.widget_refresh,
            WidgetPendingIntentFactory.widgetBroadcast(
                context,
                LifeConsoleWidgetProvider::class.java,
                LANE_CONSOLE,
                ACTION_REFRESH,
                12,
            ),
        )
        setOnClickPendingIntent(
            R.id.widget_bowel_add,
            WidgetPendingIntentFactory.widgetBroadcast(
                context,
                LifeConsoleWidgetProvider::class.java,
                LANE_CONSOLE,
                ACTION_BOWEL_ADD,
                13,
            ),
        )
        setOnClickPendingIntent(
            R.id.widget_bowel_remove,
            WidgetPendingIntentFactory.widgetBroadcast(
                context,
                LifeConsoleWidgetProvider::class.java,
                LANE_CONSOLE,
                ACTION_BOWEL_REMOVE,
                14,
            ),
        )
        setOnClickPendingIntent(
            R.id.widget_meal_self_upload,
            WidgetPendingIntentFactory.openMediaEntry(
                context,
                LifeConsoleWidgetProvider.CATEGORY_MEAL,
                LANE_CONSOLE,
                16,
            ),
        )
    }

    fun RemoteViews.bindSlot(
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
        setOnClickPendingIntent(
            views.prevId,
            WidgetPendingIntentFactory.slotIntent(context, ACTION_SLOT_PREV, slotKey, views.prevId),
        )
        setOnClickPendingIntent(
            views.nextId,
            WidgetPendingIntentFactory.slotIntent(context, ACTION_SLOT_NEXT, slotKey, views.nextId),
        )

        if (views.openId != null) {
            if (media != null) {
                setOnClickPendingIntent(
                    views.openId,
                    WidgetPendingIntentFactory.openMediaViewer(
                        context = context,
                        media = media,
                        lane = WidgetPendingIntentFactory.laneFor(slotKey),
                        requestCode = WidgetPendingIntentFactory.requestCodeFor(slotKey, views.openId),
                    ),
                )
            } else {
                setOnClickPendingIntent(
                    views.openId,
                    WidgetPendingIntentFactory.widgetBroadcast(
                        context = context,
                        providerClass = WidgetPendingIntentFactory.providerClassFor(slotKey),
                        lane = WidgetPendingIntentFactory.laneFor(slotKey),
                        action = ACTION_REFRESH,
                        requestCode = WidgetPendingIntentFactory.requestCodeFor(slotKey, views.openId),
                    ),
                )
            }
        }
        if (views.uploadId != null) {
            setOnClickPendingIntent(
                views.uploadId,
                WidgetPendingIntentFactory.openMediaEntry(
                    context,
                    slotKey.category,
                    WidgetPendingIntentFactory.laneFor(slotKey),
                    WidgetPendingIntentFactory.requestCodeFor(slotKey, views.uploadId),
                ),
            )
            setViewVisibility(views.uploadId, if (slotKey.editable) View.VISIBLE else View.GONE)
        }
        if (views.deleteId != null) {
            setOnClickPendingIntent(
                views.deleteId,
                WidgetPendingIntentFactory.slotIntent(context, ACTION_SLOT_DELETE, slotKey, views.deleteId),
            )
            setViewVisibility(views.deleteId, if (media != null && slotKey.editable) View.VISIBLE else View.GONE)
        }

        if (media == null) {
            setViewVisibility(views.imageId, View.GONE)
            setViewVisibility(views.emptyId, View.VISIBLE)
            setTextViewText(views.emptyId, emptyHintFor(slotKey))
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

    fun RemoteViews.bindBowel(snapshot: RemoteLifeConsoleToday?) {
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

    fun currentMedia(
        context: Context,
        slotKey: LifeConsoleWidgetSlotKey,
        slot: RemoteLifeConsoleMediaSlot,
    ): RemoteMedia? {
        val index = LifeConsoleWidgetStore.currentIndex(context, slotKey, slot.mediaItems.size)
        return slot.mediaItems.getOrNull(index)
    }

    private fun latestText(latestTimeMillis: Long?): String {
        return latestTimeMillis?.let { "最近 ${formatTime(it)}" } ?: "今天还没有"
    }

    private fun emptyHintFor(slotKey: LifeConsoleWidgetSlotKey): String {
        return if (slotKey.editable) "点击记录" else "对方还没记录"
    }

    private fun formatTime(timeMillis: Long): String {
        val now = System.currentTimeMillis()
        val diffMs = now - timeMillis
        val diffMin = diffMs / 60_000
        if (diffMin < 1) return "刚刚"
        if (diffMin < 60) return "${diffMin}分钟前"
        val zoneId = ZoneId.of(LIFE_CONSOLE_ZONE_ID)
        val today = LocalDate.now(zoneId)
        val target = Instant.ofEpochMilli(timeMillis).atZone(zoneId)
        val targetDate = target.toLocalDate()
        return if (targetDate == today) {
            String.format("%02d:%02d", target.hour, target.minute)
        } else {
            String.format("%02d/%02d", target.monthValue, target.dayOfMonth)
        }
    }

    fun formatAbsoluteTime(timeMillis: Long): String {
        return SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(timeMillis))
    }

    fun updateStatus(prefix: String): String {
        return "$prefix ${formatAbsoluteTime(System.currentTimeMillis())}"
    }

    fun widgetTodayDate(zoneId: String = LIFE_CONSOLE_ZONE_ID): String {
        return LocalDate.now(ZoneId.of(zoneId)).toString()
    }

    fun initRuntime(context: Context) {
        AuthSessionManager.init(context.applicationContext)
        BackendDebugConfig.init(context.applicationContext)
    }
}
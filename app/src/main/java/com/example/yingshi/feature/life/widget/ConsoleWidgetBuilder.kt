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
import java.time.LocalDate
import java.time.ZoneId
import java.util.Date
import java.util.Locale
import com.example.yingshi.feature.life.LIFE_CONSOLE_ZONE_ID
import com.example.yingshi.app.AppNavigationRequests

/**
 * Console Widget 视图构建器。
 * 从 LifeConsoleWidgetController 中提取 Console Widget 的 RemoteViews 构建逻辑。
 * bindSlot 为 Console 和 People Widget 共享。
 */
internal object ConsoleWidgetBuilder {

    data class SlotViews(
        val imageFlipperId: Int,
        val imageIdA: Int,
        val imageIdB: Int,
        val emptyId: Int,
        val prevId: Int,
        val nextId: Int,
        val metaId: Int,
        val frameId: Int,
        val openId: Int? = null,
        val uploadId: Int? = null,
        val deleteId: Int? = null,
    )

    val PersonSelfViews = SlotViews(
        imageFlipperId = R.id.widget_person_self_flipper,
        imageIdA = R.id.widget_person_self_image_a,
        imageIdB = R.id.widget_person_self_image_b,
        emptyId = R.id.widget_person_self_empty,
        prevId = R.id.widget_person_self_prev,
        nextId = R.id.widget_person_self_next,
        metaId = R.id.widget_person_self_meta,
        frameId = R.id.widget_person_self_frame,
        openId = R.id.widget_person_self_open,
        uploadId = R.id.widget_person_self_upload,
        deleteId = R.id.widget_person_self_delete,
    )

    val PersonPartnerViews = SlotViews(
        imageFlipperId = R.id.widget_person_partner_flipper,
        imageIdA = R.id.widget_person_partner_image_a,
        imageIdB = R.id.widget_person_partner_image_b,
        emptyId = R.id.widget_person_partner_empty,
        prevId = R.id.widget_person_partner_prev,
        nextId = R.id.widget_person_partner_next,
        metaId = R.id.widget_person_partner_meta,
        frameId = R.id.widget_person_partner_frame,
        openId = R.id.widget_person_partner_open,
    )

    val MealSelfViews = SlotViews(
        imageFlipperId = R.id.widget_meal_self_flipper,
        imageIdA = R.id.widget_meal_self_image_a,
        imageIdB = R.id.widget_meal_self_image_b,
        emptyId = R.id.widget_meal_self_empty,
        prevId = R.id.widget_meal_self_prev,
        nextId = R.id.widget_meal_self_next,
        metaId = R.id.widget_meal_self_meta,
        frameId = R.id.widget_meal_self_frame,
        openId = R.id.widget_meal_self_open,
        uploadId = R.id.widget_meal_self_upload,
        deleteId = R.id.widget_meal_self_delete,
    )

    val MealPartnerViews = SlotViews(
        imageFlipperId = R.id.widget_meal_partner_flipper,
        imageIdA = R.id.widget_meal_partner_image_a,
        imageIdB = R.id.widget_meal_partner_image_b,
        emptyId = R.id.widget_meal_partner_empty,
        prevId = R.id.widget_meal_partner_prev,
        nextId = R.id.widget_meal_partner_next,
        metaId = R.id.widget_meal_partner_meta,
        frameId = R.id.widget_meal_partner_frame,
        openId = R.id.widget_meal_partner_open,
    )

    fun build(
        context: Context,
        status: String,
        snapshot: RemoteLifeConsoleToday?,
    ): RemoteViews {
        return RemoteViews(context.packageName, R.layout.life_console_widget).apply {
            setTextViewText(R.id.widget_status, status)
            setTextViewText(R.id.widget_date, widgetTodayDate())
            bindConsoleActions(context)
            // FR-5: 根据 front_slot 设置相框层级（setElevation），自己框默认在前
            applyFrontSlotElevation(context, LifeConsoleWidgetSlotKey.MEAL_SELF)
            bindSlot(context, snapshot, LifeConsoleWidgetSlotKey.MEAL_SELF, MealSelfViews)
            bindSlot(context, snapshot, LifeConsoleWidgetSlotKey.MEAL_PARTNER, MealPartnerViews)
            bindBowel(snapshot)
        }
    }

    /**
     * FR-5: 根据 front_slot 持久化值设置相框层级（setElevation）。
     * - 自己框在前 → self elevation=8dp, partner elevation=0dp
     * - 对方框在前 → self elevation=0dp, partner elevation=8dp
     *
     * 实现: 通过 setFloat(viewId, "setElevation", dp) 反射调用 View.setElevation(float)。
     * 该方法是 @RemotableViewMethod（API 21+），可跨进程在 Launcher 中生效。
     *
     * 注意: 原方案使用 setZ 但 View.setZ(float) 不在 RemoteViews 白名单中（无 @RemotableViewMethod 注解），
     * 调用会被 Launcher 静默丢弃，导致 FR-5 相框层级切换完全失效。复检发现后改用 setElevation。
     *
     * 单位换算: setElevation 接收 px，1dp ≈ density * 1px。Widget 在 Launcher 中渲染，
     * 通过 resources.displayMetrics.density 获取密度后换算为 dp 对应的 px 值。
     */
    internal fun RemoteViews.applyFrontSlotElevation(
        context: Context,
        selfSlotKey: LifeConsoleWidgetSlotKey,
    ) {
        val frontSlot = LifeConsoleWidgetStore.currentFrontSlot(context, selfSlotKey)
        val (selfFrameId, partnerFrameId) = if (selfSlotKey == LifeConsoleWidgetSlotKey.MEAL_SELF) {
            R.id.widget_meal_self_frame to R.id.widget_meal_partner_frame
        } else {
            R.id.widget_person_self_frame to R.id.widget_person_partner_frame
        }
        val selfIsFront = frontSlot == selfSlotKey.storageKey
        // 8dp 阴影高度足以产生明显视觉层级，避免使用过小值在 MIUI 上看不出效果
        val density = context.resources.displayMetrics.density
        val frontElevationPx = 8f * density
        val backElevationPx = 0f
        setFloat(selfFrameId, "setElevation", if (selfIsFront) frontElevationPx else backElevationPx)
        setFloat(partnerFrameId, "setElevation", if (selfIsFront) backElevationPx else frontElevationPx)
    }

    private fun RemoteViews.bindConsoleActions(context: Context) {
        // FR-2 AC-5: 修复死视图，标题点击打开今日足迹页
        setOnClickPendingIntent(
            R.id.widget_open_console,
            WidgetPendingIntentFactory.openMainIntent(
                context,
                AppNavigationRequests.ACTION_OPEN_LIFE_CONSOLE,
                WidgetActions.LANE_CONSOLE,
                WidgetActions.RC_CONSOLE_TITLE,
            ),
        )
        setOnClickPendingIntent(
            R.id.widget_ledger,
            WidgetPendingIntentFactory.openMainIntent(
                context,
                com.example.yingshi.app.AppNavigationRequests.ACTION_OPEN_LEDGER_ADD,
                WidgetActions.LANE_CONSOLE,
                11,
            ),
        )
        setOnClickPendingIntent(
            R.id.widget_refresh,
            WidgetPendingIntentFactory.widgetBroadcast(
                context,
                LifeConsoleWidgetProvider::class.java,
                WidgetActions.LANE_CONSOLE,
                WidgetActions.ACTION_REFRESH,
                12,
            ),
        )
        setOnClickPendingIntent(
            R.id.widget_bowel_add,
            WidgetPendingIntentFactory.widgetBroadcast(
                context,
                LifeConsoleWidgetProvider::class.java,
                WidgetActions.LANE_CONSOLE,
                WidgetActions.ACTION_BOWEL_ADD,
                13,
            ),
        )
        setOnClickPendingIntent(
            R.id.widget_bowel_remove,
            WidgetPendingIntentFactory.widgetBroadcast(
                context,
                LifeConsoleWidgetProvider::class.java,
                WidgetActions.LANE_CONSOLE,
                WidgetActions.ACTION_BOWEL_REMOVE,
                14,
            ),
        )
        setOnClickPendingIntent(
            R.id.widget_meal_self_upload,
            WidgetPendingIntentFactory.openMediaEntry(
                context,
                LifeConsoleWidgetProvider.CATEGORY_MEAL,
                WidgetActions.LANE_CONSOLE,
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
            WidgetPendingIntentFactory.slotIntent(context, WidgetActions.ACTION_SLOT_PREV, slotKey, views.prevId),
        )
        setOnClickPendingIntent(
            views.nextId,
            WidgetPendingIntentFactory.slotIntent(context, WidgetActions.ACTION_SLOT_NEXT, slotKey, views.nextId),
        )
        // FR-5 AC-1: 相框根 FrameLayout 点击置顶（覆盖白区/胶带/间隙，子视图 PendingIntent 优先）
        setOnClickPendingIntent(
            views.frameId,
            WidgetPendingIntentFactory.toFrontIntent(context, slotKey, views.frameId),
        )

        if (views.openId != null) {
            if (media != null) {
                setOnClickPendingIntent(
                    views.openId,
                    WidgetPendingIntentFactory.openMediaViewer(
                        context = context,
                        media = media,
                        slotKey = slotKey,
                        lane = WidgetPendingIntentFactory.laneFor(slotKey),
                        requestCode = WidgetPendingIntentFactory.requestCodeFor(slotKey, views.openId),
                    ),
                )
            } else if (slotKey.editable) {
                // FR-8 AC-1: 空状态点击 + 图标打开上传入口（自己框）
                setOnClickPendingIntent(
                    views.openId,
                    WidgetPendingIntentFactory.openMediaEntry(
                        context = context,
                        category = slotKey.category,
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
                        action = WidgetActions.ACTION_REFRESH,
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
                WidgetPendingIntentFactory.slotIntent(context, WidgetActions.ACTION_SLOT_DELETE, slotKey, views.deleteId),
            )
            setViewVisibility(views.deleteId, if (media != null && slotKey.editable) View.VISIBLE else View.GONE)
        }

        if (media == null) {
            // FR-8: 空状态时隐藏 ‹ › meta 翻页控件，仅保留虚线框+图标+文案
            setViewVisibility(views.imageFlipperId, View.GONE)
            setViewVisibility(views.emptyId, View.VISIBLE)
            setTextViewText(views.emptyId, emptyHintFor(slotKey))
            setViewVisibility(views.prevId, View.GONE)
            setViewVisibility(views.nextId, View.GONE)
            setViewVisibility(views.metaId, View.GONE)
            return
        }

        // FR-4 AC-2: 单照片时隐藏 ‹ › 翻页按钮，meta 保留（显示 1/1）
        if (mediaItems.size <= 1) {
            setViewVisibility(views.prevId, View.GONE)
            setViewVisibility(views.nextId, View.GONE)
        } else {
            setViewVisibility(views.prevId, View.VISIBLE)
            setViewVisibility(views.nextId, View.VISIBLE)
        }

        val bitmap = LifeConsoleWidgetStore.cachedBitmapFor(context, media)
        if (bitmap != null) {
            // FR-12: ViewFlipper 双缓冲淡入淡出过渡。
            // 将 bitmap 同时加载到两个子 ImageView，确保设备重启/Launcher 重启后
            // ViewFlipper 重置到 child 0 时仍能正确显示（showNext 是相对动作，
            // 无法绝对定位到指定 child，故双加载保证任意 child 显示时均有 bitmap）。
            // 代价：翻页时旧 bitmap 被立即覆盖，交叉淡入淡出退化为同图淡入淡出，
            // 但 200ms 动画仍提供平滑过渡感，且彻底避免 re-inflate 后空白/旧图 bug。
            setImageViewBitmap(views.imageIdA, bitmap)
            setImageViewBitmap(views.imageIdB, bitmap)
            setViewVisibility(views.imageFlipperId, View.VISIBLE)
            setViewVisibility(views.emptyId, View.GONE)
            // showNext 是 RemoteViews 类自身方法（非反射），跨进程安全，触发 in/out 动画
            showNext(views.imageFlipperId)
        } else {
            setViewVisibility(views.imageFlipperId, View.GONE)
            setViewVisibility(views.emptyId, View.VISIBLE)
            setTextViewText(views.emptyId, if (media.isVideo()) "视频封面同步中" else "图片同步中")
        }
    }

    fun RemoteViews.bindBowel(snapshot: RemoteLifeConsoleToday?) {
        // FR-7: 大便徽标降级为胶囊，仅显示双方计数（latest 文本已移除）
        val currentUserId = snapshot?.currentUser?.userId
        val partnerUserId = snapshot?.partner?.userId
        val bowelSelf = snapshot?.bowel?.users?.firstOrNull { it.userId == currentUserId }
        val bowelPartner = snapshot?.bowel?.users?.firstOrNull { it.userId == partnerUserId }
        val selfCount = bowelSelf?.count ?: 0
        val partnerCount = bowelPartner?.count ?: 0
        setTextViewText(R.id.widget_bowel_self_count, selfCount.toString())
        setTextViewText(R.id.widget_bowel_partner_count, partnerCount.toString())
    }

    fun currentMedia(
        context: Context,
        slotKey: LifeConsoleWidgetSlotKey,
        slot: RemoteLifeConsoleMediaSlot,
    ): RemoteMedia? {
        val index = LifeConsoleWidgetStore.currentIndex(context, slotKey, slot.mediaItems.size)
        return slot.mediaItems.getOrNull(index)
    }

    private fun emptyHintFor(slotKey: LifeConsoleWidgetSlotKey): String {
        return if (slotKey.editable) "点击记录" else "还没记录"
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
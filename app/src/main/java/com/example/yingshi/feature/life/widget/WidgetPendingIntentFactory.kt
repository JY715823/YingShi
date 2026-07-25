package com.example.yingshi.feature.life.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.example.yingshi.MainActivity
import com.example.yingshi.app.AppNavigationRequests
import com.example.yingshi.data.model.RemoteMedia
import com.example.yingshi.feature.life.WidgetMediaEntryActivity

/**
 * Widget PendingIntent 工厂。
 * 从 LifeConsoleWidgetController 中提取所有 PendingIntent 构建逻辑，
 * 解决 Controller 中 Intent 构建与视图构建耦合的问题。
 */
internal object WidgetPendingIntentFactory {

    fun openMainIntent(
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

    fun openMediaEntry(
        context: Context,
        category: String,
        lane: String,
        requestCode: Int,
    ): PendingIntent {
        val resolvedRequestCode = widgetRequestCode(lane, requestCode)
        val intent = Intent(context, WidgetMediaEntryActivity::class.java).apply {
            data = Uri.parse("yingshi://widget/$lane/upload/$requestCode/$category")
            putExtra(LifeConsoleWidgetProvider.EXTRA_CATEGORY, category)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_MULTIPLE_TASK
        }
        return PendingIntent.getActivity(
            context,
            resolvedRequestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    /**
     * FR-6 Round 2: 点击照片打开查看态。
     * 目标改为 LifeWidgetOpenDispatchActivity（透明分发），由其完成：
     * 1. FR-5: saveFrontSlot + refreshWidget（置顶该框）
     * 2. FR-6: 启动 QuickViewer（携带 slotKey）
     */
    fun openMediaViewer(
        context: Context,
        media: RemoteMedia,
        slotKey: LifeConsoleWidgetSlotKey,
        lane: String,
        requestCode: Int,
    ): PendingIntent {
        val resolvedRequestCode = widgetRequestCode(lane, requestCode)
        val intent = LifeWidgetOpenDispatchActivity.intent(context, slotKey, media).apply {
            data = Uri.parse("yingshi://widget/$lane/view/$requestCode/${media.mediaId}")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_MULTIPLE_TASK
        }
        return PendingIntent.getActivity(
            context,
            resolvedRequestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    fun slotIntent(
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
            putExtra("life_console_slot_key", slotKey.storageKey)
        }
        return PendingIntent.getBroadcast(
            context,
            widgetRequestCode(lane, requestCodeFor(slotKey, requestCode)),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    /**
     * FR-5: 相框点击置顶 Intent（广播，触发 Controller 的 SLOT_TO_FRONT 分支）。
     * 用于相框白区/照片区背景点击，将该框切换到前层。
     */
    fun toFrontIntent(
        context: Context,
        slotKey: LifeConsoleWidgetSlotKey,
        requestCode: Int,
    ): PendingIntent {
        return slotIntent(context, WidgetActions.ACTION_SLOT_TO_FRONT, slotKey, requestCode)
    }

    fun widgetBroadcast(
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

    fun requestCodeFor(slotKey: LifeConsoleWidgetSlotKey, base: Int): Int {
        return (slotKey.ordinal + 1) * 10_000 + (base and 0x0FFF)
    }

    fun laneFor(slotKey: LifeConsoleWidgetSlotKey): String {
        return if (slotKey.category == LifeConsoleWidgetProvider.CATEGORY_PERSON) {
            WidgetActions.LANE_PEOPLE
        } else {
            WidgetActions.LANE_CONSOLE
        }
    }

    fun providerClassFor(slotKey: LifeConsoleWidgetSlotKey): Class<out AppWidgetProvider> {
        return if (laneFor(slotKey) == WidgetActions.LANE_PEOPLE) {
            LifePeopleWidgetProvider::class.java
        } else {
            LifeConsoleWidgetProvider::class.java
        }
    }

    fun widgetRequestCode(lane: String, requestCode: Int): Int {
        val laneOffset = if (lane == WidgetActions.LANE_PEOPLE) 200_000 else 100_000
        return laneOffset + (requestCode and 0x0FFFFF)
    }
}
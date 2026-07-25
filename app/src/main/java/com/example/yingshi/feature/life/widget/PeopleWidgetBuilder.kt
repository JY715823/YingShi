package com.example.yingshi.feature.life.widget

import android.content.Context
import android.widget.RemoteViews
import com.example.yingshi.R
import com.example.yingshi.app.AppNavigationRequests
import com.example.yingshi.data.model.RemoteLifeConsoleToday

/**
 * People Widget 视图构建器。
 * 从 LifeConsoleWidgetController 中提取 People Widget 的 RemoteViews 构建逻辑。
 * 复用 ConsoleWidgetBuilder.bindSlot 共享的 slot 绑定逻辑。
 */
internal object PeopleWidgetBuilder {

    fun build(
        context: Context,
        status: String,
        snapshot: RemoteLifeConsoleToday?,
    ): RemoteViews {
        return RemoteViews(context.packageName, R.layout.life_people_widget).apply {
            setTextViewText(R.id.widget_status, status)
            setTextViewText(R.id.widget_date, ConsoleWidgetBuilder.widgetTodayDate())
            bindPeopleActions(context)
            with(ConsoleWidgetBuilder) {
                // FR-5: 根据 front_slot 设置相框层级（setElevation），自己框默认在前
                applyFrontSlotElevation(context, LifeConsoleWidgetSlotKey.PERSON_SELF)
                bindSlot(context, snapshot, LifeConsoleWidgetSlotKey.PERSON_SELF, ConsoleWidgetBuilder.PersonSelfViews)
            }
            with(ConsoleWidgetBuilder) {
                bindSlot(context, snapshot, LifeConsoleWidgetSlotKey.PERSON_PARTNER, ConsoleWidgetBuilder.PersonPartnerViews)
            }
        }
    }

    private fun RemoteViews.bindPeopleActions(context: Context) {
        // FR-2 AC-5: 修复死视图，标题点击打开人物痕迹页
        setOnClickPendingIntent(
            R.id.widget_open_console,
            WidgetPendingIntentFactory.openMainIntent(
                context,
                AppNavigationRequests.ACTION_OPEN_LIFE_CONSOLE,
                WidgetActions.LANE_PEOPLE,
                WidgetActions.RC_PEOPLE_TITLE,
                slotKey = LifeConsoleWidgetSlotKey.PERSON_SELF,
            ),
        )
        setOnClickPendingIntent(
            R.id.widget_refresh,
            WidgetPendingIntentFactory.widgetBroadcast(
                context,
                LifePeopleWidgetProvider::class.java,
                WidgetActions.LANE_PEOPLE,
                WidgetActions.ACTION_REFRESH,
                22,
            ),
        )
        // 侧边置顶热区：点右上空白让对方框置顶（解决下框挡住上框白区控件的问题）
        setOnClickPendingIntent(
            R.id.widget_person_partner_bring_front,
            WidgetPendingIntentFactory.toFrontIntent(
                context,
                LifeConsoleWidgetSlotKey.PERSON_PARTNER,
                R.id.widget_person_partner_bring_front,
            ),
        )
        // 点左下空白让自己框置顶
        setOnClickPendingIntent(
            R.id.widget_person_self_bring_front,
            WidgetPendingIntentFactory.toFrontIntent(
                context,
                LifeConsoleWidgetSlotKey.PERSON_SELF,
                R.id.widget_person_self_bring_front,
            ),
        )
    }
}
package com.example.yingshi.feature.life.widget

import android.content.Context
import android.widget.RemoteViews
import com.example.yingshi.R
import com.example.yingshi.data.model.RemoteLifeConsoleToday

/**
 * People Widget 视图构建器。
 * 从 LifeConsoleWidgetController 中提取 People Widget 的 RemoteViews 构建逻辑。
 * 复用 ConsoleWidgetBuilder.bindSlot 共享的 slot 绑定逻辑。
 */
internal object PeopleWidgetBuilder {

    private const val ACTION_REFRESH = "com.example.yingshi.widget.REFRESH_LIFE_CONSOLE"
    private const val LANE_PEOPLE = "people"

    fun build(
        context: Context,
        status: String,
        snapshot: RemoteLifeConsoleToday?,
    ): RemoteViews {
        return RemoteViews(context.packageName, R.layout.life_people_widget).apply {
            setTextViewText(R.id.widget_status, status)
            bindPeopleActions(context)
            with(ConsoleWidgetBuilder) {
                bindSlot(context, snapshot, LifeConsoleWidgetSlotKey.PERSON_SELF, ConsoleWidgetBuilder.PersonSelfViews)
            }
            with(ConsoleWidgetBuilder) {
                bindSlot(context, snapshot, LifeConsoleWidgetSlotKey.PERSON_PARTNER, ConsoleWidgetBuilder.PersonPartnerViews)
            }
        }
    }

    private fun RemoteViews.bindPeopleActions(context: Context) {
        setOnClickPendingIntent(
            R.id.widget_refresh,
            WidgetPendingIntentFactory.widgetBroadcast(
                context,
                LifePeopleWidgetProvider::class.java,
                LANE_PEOPLE,
                ACTION_REFRESH,
                22,
            ),
        )
        setOnClickPendingIntent(
            R.id.widget_person_self_upload,
            WidgetPendingIntentFactory.openMediaEntry(
                context,
                LifeConsoleWidgetProvider.CATEGORY_PERSON,
                LANE_PEOPLE,
                25,
            ),
        )
    }
}
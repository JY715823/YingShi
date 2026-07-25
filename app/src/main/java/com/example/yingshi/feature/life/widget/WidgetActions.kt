package com.example.yingshi.feature.life.widget

/**
 * Widget ACTION 与 LANE 常量单一来源（Round 1 FR-2 重构）。
 *
 * 消除以下文件中的常量重复：
 * - LifeConsoleWidgetProvider.kt（Controller 内部）
 * - ConsoleWidgetBuilder.kt
 * - PeopleWidgetBuilder.kt
 * - WidgetPendingIntentFactory.kt
 *
 * 所有 widget 广播 action 和 lane 标识符必须在此声明，禁止在其他文件重复定义。
 */
internal object WidgetActions {

    // ============ 广播 Action（Widget Provider 接收） ============

    /** 刷新 widget 数据（Console 和 People 共用） */
    const val ACTION_REFRESH = "com.example.yingshi.widget.REFRESH_LIFE_CONSOLE"

    /** 大便计数 +1 */
    const val ACTION_BOWEL_ADD = "com.example.yingshi.widget.BOWEL_ADD"

    /** 大便计数 -1（删除最新一条） */
    const val ACTION_BOWEL_REMOVE = "com.example.yingshi.widget.BOWEL_REMOVE"

    /** Slot 上一张 */
    const val ACTION_SLOT_PREV = "com.example.yingshi.widget.SLOT_PREV"

    /** Slot 下一张 */
    const val ACTION_SLOT_NEXT = "com.example.yingshi.widget.SLOT_NEXT"

    /** Slot 删除当前媒体 */
    const val ACTION_SLOT_DELETE = "com.example.yingshi.widget.SLOT_DELETE"

    /** Slot 置顶（将该框切换到前层） */
    const val ACTION_SLOT_TO_FRONT = "com.example.yingshi.widget.SLOT_TO_FRONT"

    // ============ Lane 标识符（PendingIntent requestCode 隔离） ============

    /** 今日痕迹 Widget lane（requestCode 100000+） */
    const val LANE_CONSOLE = "console"

    /** 人物痕迹 Widget lane（requestCode 200000+） */
    const val LANE_PEOPLE = "people"

    // ============ 标题点击 requestCode ============

    /** Console 标题点击打开今日足迹页的 requestCode */
    const val RC_CONSOLE_TITLE = 10

    /** People 标题点击打开人物痕迹页的 requestCode */
    const val RC_PEOPLE_TITLE = 21

    /** widget widgetActions 全集（Controller.handleReceive 过滤用） */
    val ALL: Set<String> = setOf(
        ACTION_REFRESH,
        ACTION_BOWEL_ADD,
        ACTION_BOWEL_REMOVE,
        ACTION_SLOT_PREV,
        ACTION_SLOT_NEXT,
        ACTION_SLOT_DELETE,
        ACTION_SLOT_TO_FRONT,
    )
}

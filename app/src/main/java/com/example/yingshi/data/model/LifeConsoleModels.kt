package com.example.yingshi.data.model

data class RemoteLifeConsoleUser(
    val userId: String,
    val account: String,
    val displayName: String,
    val avatarUrl: String? = null,
)

data class RemoteLifeConsoleMediaSlot(
    val category: String,
    val ownerUserId: String?,
    val editable: Boolean,
    val mediaItems: List<RemoteMedia>,
)

// Round 7: 单条大便事件 (今日页大便页每条单独展示)
data class RemoteLifeConsoleBowelEvent(
    val bowelEventId: String,
    val userId: String,
    val occurredAtMillis: Long,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val locationLabel: String? = null,
)

data class RemoteLifeConsoleBowelUserSummary(
    val userId: String,
    val count: Int,
    val latestOccurredAtMillis: Long?,
    val eventTimesMillis: List<Long>,
    val latestLocationLabel: String? = null,
    // Round 7: 当日所有大便事件列表 (含完整位置信息), 用于今日页大便页每条单独展示
    val events: List<RemoteLifeConsoleBowelEvent>? = null,
)

data class RemoteLifeConsoleBowelSummary(
    val users: List<RemoteLifeConsoleBowelUserSummary>,
)

data class RemoteLifeConsoleToday(
    val date: String,
    val zoneId: String,
    val currentUser: RemoteLifeConsoleUser,
    val partner: RemoteLifeConsoleUser?,
    val personSelf: RemoteLifeConsoleMediaSlot,
    val personPartner: RemoteLifeConsoleMediaSlot,
    val mealSelf: RemoteLifeConsoleMediaSlot,
    val mealPartner: RemoteLifeConsoleMediaSlot,
    val bowel: RemoteLifeConsoleBowelSummary,
)

data class RemoteLifeConsoleHistoryDay(
    val date: String,
    val displayLabel: String,
    val selfMedia: List<RemoteMedia>,
    val partnerMedia: List<RemoteMedia>,
    val locationLabel: String? = null,
)

data class RemoteLifeConsoleBowelHistoryDay(
    val date: String,
    val displayLabel: String,
    val users: List<RemoteLifeConsoleBowelUserSummary>,
    val locationLabel: String? = null,
)

data class RemoteLifeConsoleHistory(
    val zoneId: String,
    val currentUser: RemoteLifeConsoleUser,
    val partner: RemoteLifeConsoleUser?,
    val personDays: List<RemoteLifeConsoleHistoryDay>,
    val mealDays: List<RemoteLifeConsoleHistoryDay>,
    val bowelDays: List<RemoteLifeConsoleBowelHistoryDay>,
)

data class RemoteLifeConsoleBowelMutation(
    val eventId: String?,
    val bowel: RemoteLifeConsoleBowelSummary,
)

data class RemotePushPreference(
    val module: String,
    val category: String,
    val enabled: Boolean,
)

data class RemotePushDeliveryAudit(
    val id: String,
    val module: String,
    val category: String,
    val eventType: String,
    val status: String,
    val reason: String,
    val targetRoute: String,
    val actorUserId: String,
    val enabledDeviceCount: Int,
    val partnerDeviceCount: Int,
    val targetDeviceCount: Int,
    val attemptedCount: Int,
    val successfulCount: Int,
    val invalidTokenCount: Int,
    val usedSelfFallback: Boolean,
    val createdAtMillis: Long,
)

data class RemotePushDiagnostics(
    val selfFallbackEnabled: Boolean,
    val currentUserEnabledDeviceCount: Int,
    val libraryEnabledDeviceCount: Int,
    val recentDeliveries: List<RemotePushDeliveryAudit>,
)

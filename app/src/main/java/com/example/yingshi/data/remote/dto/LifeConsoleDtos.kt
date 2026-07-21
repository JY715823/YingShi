package com.example.yingshi.data.remote.dto

data class LifeConsoleUserDto(
    val userId: String,
    val account: String,
    val displayName: String,
    val avatarUrl: String? = null,
)

data class LifeConsoleMediaSlotDto(
    val category: String,
    val ownerUserId: String? = null,
    val editable: Boolean = false,
    val mediaItems: List<MediaDto> = emptyList(),
)

data class LifeConsoleBowelUserSummaryDto(
    val userId: String,
    val count: Int = 0,
    val latestOccurredAtMillis: Long? = null,
    val eventTimesMillis: List<Long> = emptyList(),
    val latestLocationLabel: String? = null,
    // Round 8: 当日所有大便事件列表 (含完整位置信息), 用于今日页/历史页大便每条单独展示
    val events: List<LifeConsoleBowelEventDto>? = null,
)

data class LifeConsoleBowelSummaryDto(
    val users: List<LifeConsoleBowelUserSummaryDto> = emptyList(),
)

data class LifeConsoleTodayDto(
    val date: String,
    val zoneId: String,
    val currentUser: LifeConsoleUserDto,
    val partner: LifeConsoleUserDto? = null,
    val personSelf: LifeConsoleMediaSlotDto,
    val personPartner: LifeConsoleMediaSlotDto,
    val mealSelf: LifeConsoleMediaSlotDto,
    val mealPartner: LifeConsoleMediaSlotDto,
    val bowel: LifeConsoleBowelSummaryDto,
)

data class LifeConsoleHistoryDayDto(
    val date: String,
    val displayLabel: String,
    val selfMedia: List<MediaDto> = emptyList(),
    val partnerMedia: List<MediaDto> = emptyList(),
    val locationLabel: String? = null,
)

data class LifeConsoleBowelHistoryDayDto(
    val date: String,
    val displayLabel: String,
    val users: List<LifeConsoleBowelUserSummaryDto> = emptyList(),
    val locationLabel: String? = null,
)

data class LifeConsoleHistoryDto(
    val zoneId: String,
    val currentUser: LifeConsoleUserDto,
    val partner: LifeConsoleUserDto? = null,
    val personDays: List<LifeConsoleHistoryDayDto> = emptyList(),
    val mealDays: List<LifeConsoleHistoryDayDto> = emptyList(),
    val bowelDays: List<LifeConsoleBowelHistoryDayDto> = emptyList(),
)

data class LifeConsoleMediaRequestDto(
    val category: String,
    val mediaIds: List<String>,
)

/**
 * FR-18/FR-19: Optional request body for POST /api/life-console/bowel-events.
 * All fields nullable so callers can omit the body entirely (legacy clients still work).
 */
data class LifeConsoleBowelEventRequestDto(
    val latitude: Double? = null,
    val longitude: Double? = null,
    val locationLabel: String? = null,
)

data class LifeConsoleBowelEventDto(
    val bowelEventId: String,
    val userId: String,
    val occurredAtMillis: Long,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val locationLabel: String? = null,
)

data class LifeConsoleBowelMutationResponseDto(
    val event: LifeConsoleBowelEventDto? = null,
    val bowel: LifeConsoleBowelSummaryDto,
)

/**
 * Round 7 阶段 7: PATCH /api/life-console/media/{mediaId}/location
 * 和 PATCH /api/life-console/bowel-events/{eventId}/location 的请求体。
 * 与服务端 UpdateLocationRequest record 对齐。
 */
data class UpdateLocationRequestDto(
    val latitude: Double? = null,
    val longitude: Double? = null,
    val locationLabel: String? = null,
)

data class RegisterPushTokenRequestDto(
    val platform: String,
    val token: String,
)

data class RegisterPushTokenResponseDto(
    val tokenId: String,
    val platform: String,
    val lastSeenAtMillis: Long,
    val enabled: Boolean,
)

data class PushPreferenceDto(
    val module: String,
    val category: String,
    val enabled: Boolean,
)

data class PushPreferencesResponseDto(
    val preferences: List<PushPreferenceDto> = emptyList(),
)

data class PushDeliveryAuditDto(
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

data class PushDiagnosticsResponseDto(
    val selfFallbackEnabled: Boolean,
    val currentUserEnabledDeviceCount: Int,
    val libraryEnabledDeviceCount: Int,
    val recentDeliveries: List<PushDeliveryAuditDto> = emptyList(),
)

data class UpdatePushPreferenceRequestDto(
    val module: String,
    val category: String,
    val enabled: Boolean,
)

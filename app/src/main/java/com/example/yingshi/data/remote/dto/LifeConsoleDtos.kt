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
)

data class LifeConsoleBowelHistoryDayDto(
    val date: String,
    val displayLabel: String,
    val users: List<LifeConsoleBowelUserSummaryDto> = emptyList(),
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

data class LifeConsoleBowelEventDto(
    val bowelEventId: String,
    val userId: String,
    val occurredAtMillis: Long,
)

data class LifeConsoleBowelMutationResponseDto(
    val event: LifeConsoleBowelEventDto? = null,
    val bowel: LifeConsoleBowelSummaryDto,
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

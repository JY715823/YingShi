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

data class RemoteLifeConsoleBowelUserSummary(
    val userId: String,
    val count: Int,
    val latestOccurredAtMillis: Long?,
    val eventTimesMillis: List<Long>,
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

data class RemoteLifeConsoleBowelMutation(
    val eventId: String?,
    val bowel: RemoteLifeConsoleBowelSummary,
)

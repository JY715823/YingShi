package com.example.yingshi.feature.home

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.example.yingshi.data.cache.AppReadCacheStore
import com.example.yingshi.data.cache.OfflineAccessManager
import com.example.yingshi.data.model.RemoteCurrentUser
import com.example.yingshi.data.model.RemoteMedia
import com.example.yingshi.data.remote.auth.AuthSessionManager
import com.example.yingshi.feature.ledger.data.LedgerBook
import com.example.yingshi.feature.ledger.data.LedgerDatabase
import com.example.yingshi.feature.ledger.data.LedgerDateUtils
import com.example.yingshi.feature.ledger.data.LedgerPreferencesStore
import com.example.yingshi.feature.ledger.data.LedgerRepository
import com.example.yingshi.feature.ledger.data.LedgerTransaction
import com.example.yingshi.feature.ledger.data.LedgerTransactionType
import com.example.yingshi.feature.ledger.data.NoOpLedgerSyncBridge
import com.example.yingshi.feature.ledger.formatMoney
import com.example.yingshi.feature.photos.AppContentMediaSource
import com.example.yingshi.feature.photos.AppMediaType
import com.example.yingshi.feature.photos.PhotoThumbnailPalette
import com.example.yingshi.feature.photos.resolveAppMediaType
import com.example.yingshi.feature.photos.toAppContentMediaSource
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId
import kotlinx.coroutines.flow.flowOf

@Immutable
data class HomeUiState(
    val currentUser: RemoteCurrentUser? = null,
    val spaceLabel: String = "共同空间",
    val unreadNotificationCount: Int = 0,
    val recentPhotos: HomeRecentPhotosSummary = HomeRecentPhotosSummary(),
    val ledger: HomeLedgerSummary = HomeLedgerSummary(),
    val isReadOnly: Boolean = false,
    val readOnlyMessage: String? = null,
)

@Immutable
data class HomeRecentPhotosSummary(
    val totalCount: Int = 0,
    val latestPhotoAtMillis: Long? = null,
    val cachedAtMillis: Long? = null,
    val tiles: List<HomePhotoTile> = emptyList(),
) {
    val hasPhotos: Boolean
        get() = totalCount > 0
}

@Immutable
data class HomePhotoTile(
    val mediaId: String,
    val mediaSource: AppContentMediaSource?,
    val mediaType: AppMediaType,
    val palette: PhotoThumbnailPalette,
)

@Immutable
data class HomeLedgerSummary(
    val bookName: String? = null,
    val latestTransactionLabel: String? = null,
    val latestTransactionAmountText: String? = null,
    val latestTransactionAtMillis: Long? = null,
    val monthExpenseText: String? = null,
    val hasBook: Boolean = false,
    val hasTransaction: Boolean = false,
)

@Composable
fun rememberHomeUiState(): HomeUiState {
    val appContext = LocalContext.current.applicationContext
    val sessionVersion = AuthSessionManager.sessionVersion
    val cacheVersion = AppReadCacheStore.changeVersion
    val offlineState = OfflineAccessManager.state
    val currentUser = remember(sessionVersion, cacheVersion) {
        AuthSessionManager.getCurrentUserSnapshot() ?: AppReadCacheStore.readCurrentUser()?.payload
    }
    val userId = currentUser?.userId
    val readCacheSummary = remember(userId, cacheVersion, sessionVersion) {
        buildHomeReadCacheSummary(currentUser)
    }
    val ledgerRepository = remember(appContext) { createHomeLedgerRepository(appContext) }
    val ledgerPreferencesStore = remember(appContext) { LedgerPreferencesStore(appContext) }

    LaunchedEffect(ledgerRepository, sessionVersion) {
        ledgerRepository.ensureSeedData()
        ledgerRepository.backfillMissingBookCreatorUserIds()
    }

    val books by ledgerRepository.observeBooks().collectAsState(initial = emptyList())
    val defaultBookId = remember(books) {
        LedgerPreferencesStore.resolveDefaultBookId(
            storedBookId = ledgerPreferencesStore.getDefaultBookId(),
            visibleBookIds = books.map(LedgerBook::id),
        )
    }
    val transactionsFlow = remember(defaultBookId, ledgerRepository) {
        if (defaultBookId.isNullOrBlank()) {
            flowOf(emptyList())
        } else {
            ledgerRepository.observeTransactions(defaultBookId)
        }
    }
    val transactions by transactionsFlow.collectAsState(initial = emptyList())
    val activeBook = remember(books, defaultBookId) {
        books.firstOrNull { it.id == defaultBookId }
    }
    val ledgerSummary = remember(activeBook, transactions) {
        buildHomeLedgerSummary(
            book = activeBook,
            transactions = transactions,
        )
    }

    return remember(
        currentUser,
        readCacheSummary,
        ledgerSummary,
        offlineState,
    ) {
        HomeUiState(
            currentUser = currentUser,
            spaceLabel = resolveHomeSpaceLabel(currentUser),
            unreadNotificationCount = readCacheSummary.unreadNotificationCount,
            recentPhotos = readCacheSummary.recentPhotos,
            ledger = ledgerSummary,
            isReadOnly = offlineState.isReadOnly,
            readOnlyMessage = offlineState.message,
        )
    }
}

private data class HomeReadCacheSummary(
    val recentPhotos: HomeRecentPhotosSummary = HomeRecentPhotosSummary(),
    val unreadNotificationCount: Int = 0,
)

private fun buildHomeReadCacheSummary(
    currentUser: RemoteCurrentUser?,
): HomeReadCacheSummary {
    val userId = currentUser?.userId ?: return HomeReadCacheSummary()
    val photoFeed = AppReadCacheStore.readPhotoFeed(userId)?.payload?.items.orEmpty()
        .sortedByDescending { it.displayTimeMillis }
    val notifications = AppReadCacheStore.readNotifications(userId)?.payload?.items.orEmpty()
    return HomeReadCacheSummary(
        recentPhotos = buildHomeRecentPhotosSummary(photoFeed),
        unreadNotificationCount = notifications.count { !it.isRead },
    )
}

private fun buildHomeRecentPhotosSummary(
    mediaItems: List<RemoteMedia>,
): HomeRecentPhotosSummary {
    val latestPhoto = mediaItems.firstOrNull()
    return HomeRecentPhotosSummary(
        totalCount = mediaItems.size,
        latestPhotoAtMillis = latestPhoto?.displayTimeMillis,
        cachedAtMillis = mediaItems.maxOfOrNull { it.importedAtMillis ?: it.createdAtMillis ?: it.displayTimeMillis },
        tiles = mediaItems
            .take(5)
            .map { media ->
                HomePhotoTile(
                    mediaId = media.mediaId,
                    mediaSource = media.toAppContentMediaSource(),
                    mediaType = media.toHomeMediaType(),
                    palette = homePaletteFor(media.mediaId),
                )
            },
    )
}

private fun buildHomeLedgerSummary(
    book: LedgerBook?,
    transactions: List<LedgerTransaction>,
): HomeLedgerSummary {
    val currencySymbol = book?.currencySymbol?.takeIf { it.isNotBlank() } ?: "¥"
    val latestTransaction = transactions.firstOrNull()
    val monthRange = LedgerDateUtils.monthRange(YearMonth.now())
    val monthExpenseCents = transactions.asSequence()
        .filter { it.type == LedgerTransactionType.EXPENSE }
        .filter { it.occurredAtMillis >= monthRange.startMillis && it.occurredAtMillis < monthRange.endMillis }
        .sumOf { it.amountCents }
    return HomeLedgerSummary(
        bookName = book?.name,
        latestTransactionLabel = latestTransaction?.toHomeLedgerLabel(),
        latestTransactionAmountText = latestTransaction?.toHomeLedgerAmountText(currencySymbol),
        latestTransactionAtMillis = latestTransaction?.occurredAtMillis,
        monthExpenseText = monthExpenseCents.formatMoney(symbol = currencySymbol),
        hasBook = book != null,
        hasTransaction = latestTransaction != null,
    )
}

private fun LedgerTransaction.toHomeLedgerLabel(): String {
    return category?.name?.takeIf { it.isNotBlank() }
        ?: remark.trim().takeIf { it.isNotBlank() }
        ?: when (type) {
            LedgerTransactionType.EXPENSE -> "支出"
            LedgerTransactionType.INCOME -> "收入"
            LedgerTransactionType.TRANSFER -> "转账"
        }
}

private fun LedgerTransaction.toHomeLedgerAmountText(
    currencySymbol: String,
): String {
    val amount = amountCents.formatMoney(symbol = currencySymbol)
    return when (type) {
        LedgerTransactionType.EXPENSE -> "-$amount"
        LedgerTransactionType.INCOME -> "+$amount"
        LedgerTransactionType.TRANSFER -> amount
    }
}

private fun RemoteMedia.toHomeMediaType(): AppMediaType {
    return resolveAppMediaType(
        rawType = mediaType,
        mimeType = mimeType,
        thumbnailUrl = thumbnailUrl ?: previewUrl,
        mediaUrl = mediaUrl,
        videoUrl = videoUrl,
        coverUrl = coverUrl,
        originalUrl = originalUrl,
    )
}

private fun resolveHomeSpaceLabel(
    currentUser: RemoteCurrentUser?,
): String {
    if (currentUser == null) {
        return "共同空间"
    }
    currentUser.libraryDisplayName?.trim()?.takeIf { it.isNotBlank() }?.let { return it }
    val currentName = currentUser.displayName.trim().ifBlank { "你" }
    val partnerName = currentUser.partner?.displayName?.trim()?.takeIf { it.isNotBlank() }
    return if (partnerName != null) {
        "$currentName 和 $partnerName"
    } else {
        currentName
    }
}

private fun homePaletteFor(key: String): PhotoThumbnailPalette {
    val palettes = listOf(
        PhotoThumbnailPalette(
            start = androidx.compose.ui.graphics.Color(0xFFB8D8F8),
            end = androidx.compose.ui.graphics.Color(0xFF7EA6DF),
            accent = androidx.compose.ui.graphics.Color(0xFFEAF5FF),
        ),
        PhotoThumbnailPalette(
            start = androidx.compose.ui.graphics.Color(0xFFF5D5C3),
            end = androidx.compose.ui.graphics.Color(0xFFE5A58F),
            accent = androidx.compose.ui.graphics.Color(0xFFFFF2EA),
        ),
        PhotoThumbnailPalette(
            start = androidx.compose.ui.graphics.Color(0xFFD4E7C0),
            end = androidx.compose.ui.graphics.Color(0xFF87B593),
            accent = androidx.compose.ui.graphics.Color(0xFFF1F8E7),
        ),
        PhotoThumbnailPalette(
            start = androidx.compose.ui.graphics.Color(0xFFD9D4F5),
            end = androidx.compose.ui.graphics.Color(0xFF8BA0DB),
            accent = androidx.compose.ui.graphics.Color(0xFFF3F1FF),
        ),
        PhotoThumbnailPalette(
            start = androidx.compose.ui.graphics.Color(0xFFE8D3BF),
            end = androidx.compose.ui.graphics.Color(0xFFC28F66),
            accent = androidx.compose.ui.graphics.Color(0xFFFAECDD),
        ),
    )
    return palettes[key.hashCode().let { if (it == Int.MIN_VALUE) 0 else kotlin.math.abs(it) } % palettes.size]
}

private fun createHomeLedgerRepository(
    context: Context,
): LedgerRepository {
    return LedgerRepository(
        dao = LedgerDatabase.getInstance(context).ledgerDao(),
        syncBridge = NoOpLedgerSyncBridge,
        currentUserIdProvider = { AuthSessionManager.getCurrentUserSnapshot()?.userId },
    )
}

fun formatHomeRelativeTime(
    millis: Long?,
    zoneId: ZoneId = ZoneId.systemDefault(),
): String {
    millis ?: return "最近回忆"
    val target = Instant.ofEpochMilli(millis).atZone(zoneId).toLocalDate()
    val today = Instant.ofEpochMilli(System.currentTimeMillis()).atZone(zoneId).toLocalDate()
    return when (java.time.temporal.ChronoUnit.DAYS.between(target, today)) {
        0L -> "今天"
        1L -> "昨天"
        else -> if (target.year == today.year) {
            "${target.monthValue}月${target.dayOfMonth}日"
        } else {
            "${target.year}/${target.monthValue}/${target.dayOfMonth}"
        }
    }
}

fun formatHomeTimeStamp(
    millis: Long?,
    zoneId: ZoneId = ZoneId.systemDefault(),
): String {
    millis ?: return "--"
    val value = Instant.ofEpochMilli(millis).atZone(zoneId).toLocalDateTime()
    val today = Instant.ofEpochMilli(System.currentTimeMillis()).atZone(zoneId).toLocalDate()
    return if (value.toLocalDate() == today) {
        "${value.hour.toString().padStart(2, '0')}:${value.minute.toString().padStart(2, '0')}"
    } else if (value.year == today.year) {
        "${value.monthValue}/${value.dayOfMonth} ${value.hour.toString().padStart(2, '0')}:${value.minute.toString().padStart(2, '0')}"
    } else {
        "${value.year}/${value.monthValue}/${value.dayOfMonth}"
    }
}

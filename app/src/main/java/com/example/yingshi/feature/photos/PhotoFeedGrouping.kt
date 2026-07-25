package com.example.yingshi.feature.photos

import java.util.Calendar
import java.util.Locale

internal fun buildPhotoFeedBlocks(
    items: List<PhotoFeedItem>,
    density: PhotoFeedDensity,
): List<PhotoFeedBlock> {
    if (items.isEmpty()) return emptyList()

    return when (density) {
        PhotoFeedDensity.COMFORT_2,
        PhotoFeedDensity.COMFORT_3,
        PhotoFeedDensity.DENSE_4,
        -> buildMonthAndDayBlocks(items = items, columns = density.columns)

        PhotoFeedDensity.OVERVIEW_8 ->
            buildMonthBlocks(items = items, columns = density.columns)

        PhotoFeedDensity.OVERVIEW_16 ->
            buildYearBlocks(items = items, columns = density.columns)
    }
}

internal fun buildCollaborativePhotoFeedBlocks(
    items: List<PhotoFeedItem>,
    density: PhotoFeedDensity,
    directory: CollaboratorDirectorySnapshot,
    selectedUserIds: Set<String>,
    timeBucketHours: Int,
): List<PhotoFeedBlock> {
    if (items.isEmpty()) return emptyList()
    if (directory.all.isEmpty()) return buildPhotoFeedBlocks(items = items, density = density)

    val currentIdentity = directory.currentUser
    val partnerIdentity = directory.partner
    val normalizedBucketHours = CollaborativeBucketHoursOptions
        .firstOrNull { it == timeBucketHours }
        ?: DefaultCollaborativeBucketHours
    val allUserIds = directory.all.mapTo(linkedSetOf()) { it.userId }
    val selectedIds = normalizeCollaboratorSelectionKeepingEmpty(
        selectedUserIds = selectedUserIds,
        allUserIds = allUserIds,
    )
    if (selectedIds.isEmpty()) return emptyList()

    val visibleItems = items.filter { item ->
        resolveCollaborativeOwnerUserId(
            item = item,
            currentUserId = currentIdentity?.userId,
            partnerUserId = partnerIdentity?.userId,
        ) in selectedIds
    }
    if (visibleItems.isEmpty()) return emptyList()

    val currentUserId = currentIdentity?.userId
    val partnerUserId = partnerIdentity?.userId
    val showsBoth = !currentUserId.isNullOrBlank() &&
        !partnerUserId.isNullOrBlank() &&
        currentUserId in selectedIds &&
        partnerUserId in selectedIds

    return when (density) {
        PhotoFeedDensity.OVERVIEW_16 -> buildCollaborativeYearBlocks(
            items = visibleItems,
            density = density,
            currentIdentity = currentIdentity,
            partnerIdentity = partnerIdentity,
            currentUserId = currentUserId,
            partnerUserId = partnerUserId,
            showsBoth = showsBoth,
        )

        PhotoFeedDensity.OVERVIEW_8 -> buildCollaborativeMonthBlocks(
            items = visibleItems,
            density = density,
            currentIdentity = currentIdentity,
            partnerIdentity = partnerIdentity,
            currentUserId = currentUserId,
            partnerUserId = partnerUserId,
            showsBoth = showsBoth,
        )

        else -> if (normalizedBucketHours >= DefaultCollaborativeBucketHours) {
            buildCollaborativeDayBlocks(
                items = visibleItems,
                density = density,
                currentIdentity = currentIdentity,
                partnerIdentity = partnerIdentity,
                currentUserId = currentUserId,
                partnerUserId = partnerUserId,
                showsBoth = showsBoth,
            )
        } else {
            buildCollaborativeHourBlocks(
                items = visibleItems,
                density = density,
                currentIdentity = currentIdentity,
                partnerIdentity = partnerIdentity,
                currentUserId = currentUserId,
                partnerUserId = partnerUserId,
                showsBoth = showsBoth,
                bucketHours = normalizedBucketHours,
            )
        }
    }
}

internal fun buildPhotoFeedScrubberAnchors(
    blocks: List<PhotoFeedBlock>,
    density: PhotoFeedDensity,
    leadingItemCount: Int,
): List<PhotoFeedScrubberAnchor> {
    val hasFineGrainedAnchors = blocks.any { block ->
        block is PhotoFeedDayHeader || block is PhotoFeedTimeBucketHeader
    }
    return blocks.mapIndexedNotNull { index, block ->
        when {
            block is PhotoFeedTimeBucketHeader -> {
                PhotoFeedScrubberAnchor(
                    blockKey = block.key,
                    itemIndex = leadingItemCount + index,
                    label = block.scrubberLabel,
                    timeMillis = block.anchorTimeMillis,
                )
            }

            block is PhotoFeedDayHeader && block.anchorTimeMillis != null -> {
                PhotoFeedScrubberAnchor(
                    blockKey = block.key,
                    itemIndex = leadingItemCount + index,
                    label = block.scrubberLabel,
                    timeMillis = block.anchorTimeMillis,
                )
            }

            density.columns >= 8 &&
                !hasFineGrainedAnchors &&
                block is PhotoFeedSectionHeader &&
                block.anchorTimeMillis != null -> {
                PhotoFeedScrubberAnchor(
                    blockKey = block.key,
                    itemIndex = leadingItemCount + index,
                    label = block.scrubberFallbackLabel(),
                    timeMillis = block.anchorTimeMillis,
                )
            }

            else -> null
        }
    }
}

internal fun buildPhotoFeedScrubberYearMarkers(
    anchors: List<PhotoFeedScrubberAnchor>,
): List<PhotoFeedScrubberYearMarker> {
    if (anchors.isEmpty()) return emptyList()
    val lastIndex = anchors.lastIndex
    // 年份标记的 progress 改为基于 anchor index, 与滑条 thumb 的 progress 坐标系一致.
    // 此前用时间坐标系 (newestTime - markerTime) / range, 当媒体在时间上分布不均时,
    // 滑条位置和年份标记位置会对不上 (如滑到 2026.5 但标记显示在 2024-2025 之间).
    // anchors 按时间降序 (最新在前), 第一个遇到的某年份 anchor 就是该年份最新的, 映射到 progress 0~1.
    val firstAnchorIndexByYear = linkedMapOf<Int, Int>()
    anchors.forEachIndexed { index, anchor ->
        val year = calendarFor(anchor.timeMillis).get(Calendar.YEAR)
        if (year !in firstAnchorIndexByYear) {
            firstAnchorIndexByYear[year] = index
        }
    }
    return firstAnchorIndexByYear.entries
        .sortedByDescending { it.key }
        .map { (year, firstAnchorIndex) ->
            val progress = if (lastIndex <= 0) 0f else (firstAnchorIndex.toFloat() / lastIndex.toFloat())
            PhotoFeedScrubberYearMarker(
                year = year,
                progress = progress.coerceIn(0f, 1f),
            )
        }
}

internal fun resolveCurrentScrubberAnchorIndex(
    itemIndex: Int,
    anchors: List<PhotoFeedScrubberAnchor>,
): Int {
    if (anchors.isEmpty()) return 0

    val matchedIndex = anchors.indexOfLast { anchor ->
        anchor.itemIndex <= itemIndex
    }

    return if (matchedIndex >= 0) matchedIndex else 0
}

private fun buildMonthAndDayBlocks(
    items: List<PhotoFeedItem>,
    columns: Int,
): List<PhotoFeedBlock> {
    val blocks = mutableListOf<PhotoFeedBlock>()

    monthGroups(items).forEach { monthGroup ->
        blocks += PhotoFeedSectionHeader(
            key = "month-${monthGroup.year}-${monthGroup.month}",
            title = formatMonthTitle(monthGroup.year, monthGroup.month),
            granularity = PhotoFeedTimeGranularity.MONTH,
            year = monthGroup.year,
            month = monthGroup.month,
            anchorTimeMillis = monthGroup.anchorTimeMillis,
        )

        dayGroups(monthGroup.items).forEach { dayGroup ->
            blocks += PhotoFeedDayHeader(
                key = "day-${dayGroup.year}-${dayGroup.month}-${dayGroup.day}",
                title = formatDayTitle(dayGroup.month, dayGroup.day),
                year = dayGroup.year,
                month = dayGroup.month,
                day = dayGroup.day,
                scrubberLabel = formatDayScrubberLabel(dayGroup.year, dayGroup.month, dayGroup.day),
                anchorTimeMillis = dayGroup.anchorTimeMillis,
            )
            addGridRows(
                target = blocks,
                prefix = "day-${dayGroup.year}-${dayGroup.month}-${dayGroup.day}",
                items = dayGroup.items,
                columns = columns,
            )
        }
    }

    return blocks
}

private fun buildMonthBlocks(
    items: List<PhotoFeedItem>,
    columns: Int,
): List<PhotoFeedBlock> {
    val blocks = mutableListOf<PhotoFeedBlock>()

    monthGroups(items).forEach { monthGroup ->
        blocks += PhotoFeedSectionHeader(
            key = "month-${monthGroup.year}-${monthGroup.month}",
            title = formatMonthTitle(monthGroup.year, monthGroup.month),
            granularity = PhotoFeedTimeGranularity.MONTH,
            year = monthGroup.year,
            month = monthGroup.month,
            anchorTimeMillis = monthGroup.anchorTimeMillis,
        )
        addGridRows(
            target = blocks,
            prefix = "month-${monthGroup.year}-${monthGroup.month}",
            items = monthGroup.items,
            columns = columns,
        )
    }

    return blocks
}

private fun buildYearBlocks(
    items: List<PhotoFeedItem>,
    columns: Int,
): List<PhotoFeedBlock> {
    val blocks = mutableListOf<PhotoFeedBlock>()

    yearGroups(items).forEach { yearGroup ->
        blocks += PhotoFeedSectionHeader(
            key = "year-${yearGroup.year}",
            title = formatYearTitle(yearGroup.year),
            granularity = PhotoFeedTimeGranularity.YEAR,
            year = yearGroup.year,
            anchorTimeMillis = yearGroup.anchorTimeMillis,
        )
        addGridRows(
            target = blocks,
            prefix = "year-${yearGroup.year}",
            items = yearGroup.items,
            columns = columns,
        )
    }

    return blocks
}

private fun buildCollaborativeMonthBlocks(
    items: List<PhotoFeedItem>,
    density: PhotoFeedDensity,
    currentIdentity: CollaboratorIdentityUiModel?,
    partnerIdentity: CollaboratorIdentityUiModel?,
    currentUserId: String?,
    partnerUserId: String?,
    showsBoth: Boolean,
): List<PhotoFeedBlock> {
    val blocks = mutableListOf<PhotoFeedBlock>()
    monthGroups(items).forEach { monthGroup ->
        blocks += PhotoFeedSectionHeader(
            key = "month-${monthGroup.year}-${monthGroup.month}",
            title = formatMonthTitle(monthGroup.year, monthGroup.month),
            granularity = PhotoFeedTimeGranularity.MONTH,
            year = monthGroup.year,
            month = monthGroup.month,
            anchorTimeMillis = monthGroup.anchorTimeMillis,
        )
        addCollaborativeGridRows(
            target = blocks,
            prefix = "month-${monthGroup.year}-${monthGroup.month}",
            items = monthGroup.items,
            columns = density.columns,
            currentIdentity = currentIdentity,
            partnerIdentity = partnerIdentity,
            currentUserId = currentUserId,
            partnerUserId = partnerUserId,
            showsBoth = showsBoth,
        )
    }
    return blocks
}

private fun buildCollaborativeYearBlocks(
    items: List<PhotoFeedItem>,
    density: PhotoFeedDensity,
    currentIdentity: CollaboratorIdentityUiModel?,
    partnerIdentity: CollaboratorIdentityUiModel?,
    currentUserId: String?,
    partnerUserId: String?,
    showsBoth: Boolean,
): List<PhotoFeedBlock> {
    val blocks = mutableListOf<PhotoFeedBlock>()
    yearGroups(items).forEach { yearGroup ->
        blocks += PhotoFeedSectionHeader(
            key = "year-${yearGroup.year}",
            title = formatYearTitle(yearGroup.year),
            granularity = PhotoFeedTimeGranularity.YEAR,
            year = yearGroup.year,
            anchorTimeMillis = yearGroup.anchorTimeMillis,
        )
        addCollaborativeGridRows(
            target = blocks,
            prefix = "year-${yearGroup.year}",
            items = yearGroup.items,
            columns = density.columns,
            currentIdentity = currentIdentity,
            partnerIdentity = partnerIdentity,
            currentUserId = currentUserId,
            partnerUserId = partnerUserId,
            showsBoth = showsBoth,
        )
    }
    return blocks
}

private fun buildCollaborativeDayBlocks(
    items: List<PhotoFeedItem>,
    density: PhotoFeedDensity,
    currentIdentity: CollaboratorIdentityUiModel?,
    partnerIdentity: CollaboratorIdentityUiModel?,
    currentUserId: String?,
    partnerUserId: String?,
    showsBoth: Boolean,
): List<PhotoFeedBlock> {
    val blocks = mutableListOf<PhotoFeedBlock>()
    monthGroups(items).forEach { monthGroup ->
        blocks += PhotoFeedSectionHeader(
            key = "month-${monthGroup.year}-${monthGroup.month}",
            title = formatMonthTitle(monthGroup.year, monthGroup.month),
            granularity = PhotoFeedTimeGranularity.MONTH,
            year = monthGroup.year,
            month = monthGroup.month,
            anchorTimeMillis = monthGroup.anchorTimeMillis,
        )
        dayGroups(monthGroup.items).forEach { dayGroup ->
            blocks += PhotoFeedDayHeader(
                key = "day-${dayGroup.year}-${dayGroup.month}-${dayGroup.day}",
                title = formatDayTitle(dayGroup.month, dayGroup.day),
                year = dayGroup.year,
                month = dayGroup.month,
                day = dayGroup.day,
                scrubberLabel = formatDayScrubberLabel(dayGroup.year, dayGroup.month, dayGroup.day),
                anchorTimeMillis = dayGroup.anchorTimeMillis,
            )
            addCollaborativeGridRows(
                target = blocks,
                prefix = "day-${dayGroup.year}-${dayGroup.month}-${dayGroup.day}",
                items = dayGroup.items,
                columns = density.columns,
                currentIdentity = currentIdentity,
                partnerIdentity = partnerIdentity,
                currentUserId = currentUserId,
                partnerUserId = partnerUserId,
                showsBoth = showsBoth,
            )
        }
    }
    return blocks
}

private fun buildCollaborativeHourBlocks(
    items: List<PhotoFeedItem>,
    density: PhotoFeedDensity,
    currentIdentity: CollaboratorIdentityUiModel?,
    partnerIdentity: CollaboratorIdentityUiModel?,
    currentUserId: String?,
    partnerUserId: String?,
    showsBoth: Boolean,
    bucketHours: Int,
): List<PhotoFeedBlock> {
    val blocks = mutableListOf<PhotoFeedBlock>()
    monthGroups(items).forEach { monthGroup ->
        blocks += PhotoFeedSectionHeader(
            key = "month-${monthGroup.year}-${monthGroup.month}",
            title = formatMonthTitle(monthGroup.year, monthGroup.month),
            granularity = PhotoFeedTimeGranularity.MONTH,
            year = monthGroup.year,
            month = monthGroup.month,
            anchorTimeMillis = monthGroup.anchorTimeMillis,
        )
        dayGroups(monthGroup.items).forEach { dayGroup ->
            blocks += PhotoFeedDayHeader(
                key = "day-${dayGroup.year}-${dayGroup.month}-${dayGroup.day}",
                title = formatDayTitle(dayGroup.month, dayGroup.day),
                year = dayGroup.year,
                month = dayGroup.month,
                day = dayGroup.day,
                scrubberLabel = formatDayScrubberLabel(dayGroup.year, dayGroup.month, dayGroup.day),
                anchorTimeMillis = dayGroup.anchorTimeMillis,
            )
            collaborativeBuckets(
                items = dayGroup.items,
                timeBucketHours = bucketHours,
            ).forEach { bucket ->
                val currentCount = bucket.items.count { item ->
                    collaborativeSlotForItem(
                        item = item,
                        currentUserId = currentUserId,
                        partnerUserId = partnerUserId,
                    ) == CollaborativeSlot.CURRENT
                }
                val partnerCount = bucket.items.count { item ->
                    collaborativeSlotForItem(
                        item = item,
                        currentUserId = currentUserId,
                        partnerUserId = partnerUserId,
                    ) == CollaborativeSlot.PARTNER
                }
                blocks += PhotoFeedTimeBucketHeader(
                    key = "bucket-${bucket.startMillis}",
                    title = formatHourRangeTitle(bucket.startMillis, bucketHours),
                    scrubberLabel = formatDayScrubberLabel(
                        year = dayGroup.year,
                        month = dayGroup.month,
                        day = dayGroup.day,
                    ),
                    bucketHours = bucketHours,
                    anchorTimeMillis = bucket.startMillis,
                    currentCount = currentCount,
                    partnerCount = partnerCount,
                )
                addCollaborativeGridRows(
                    target = blocks,
                    prefix = "bucket-${bucket.startMillis}",
                    items = bucket.items,
                    columns = density.columns,
                    currentIdentity = currentIdentity,
                    partnerIdentity = partnerIdentity,
                    currentUserId = currentUserId,
                    partnerUserId = partnerUserId,
                    showsBoth = showsBoth,
                )
            }
        }
    }
    return blocks
}

private fun addCollaborativeGridRows(
    target: MutableList<PhotoFeedBlock>,
    prefix: String,
    items: List<PhotoFeedItem>,
    columns: Int,
    currentIdentity: CollaboratorIdentityUiModel?,
    partnerIdentity: CollaboratorIdentityUiModel?,
    currentUserId: String?,
    partnerUserId: String?,
    showsBoth: Boolean,
) {
    if (!showsBoth) {
        addGridRows(
            target = target,
            prefix = prefix,
            items = items,
            columns = columns,
        )
        return
    }

    val currentItems = items.filter { item ->
        collaborativeSlotForItem(
            item = item,
            currentUserId = currentUserId,
            partnerUserId = partnerUserId,
        ) == CollaborativeSlot.CURRENT
    }
    val partnerItems = items.filter { item ->
        collaborativeSlotForItem(
            item = item,
            currentUserId = currentUserId,
            partnerUserId = partnerUserId,
        ) == CollaborativeSlot.PARTNER
    }

    if (currentItems.isNotEmpty()) {
        currentIdentity?.let { identity ->
            if (columns <= 4) {
                target += PhotoFeedCollaboratorHeader(
                    key = "$prefix-current-label",
                    identity = identity,
                )
            }
        }
        addGridRows(
            target = target,
            prefix = "$prefix-current",
            items = currentItems,
            columns = columns,
        )
    }
    if (currentItems.isNotEmpty() && partnerItems.isNotEmpty()) {
        target += PhotoFeedCollaboratorDivider(
            key = "$prefix-divider",
        )
    }
    if (partnerItems.isNotEmpty()) {
        partnerIdentity?.let { identity ->
            if (columns <= 4) {
                target += PhotoFeedCollaboratorHeader(
                    key = "$prefix-partner-label",
                    identity = identity,
                )
            }
        }
        addGridRows(
            target = target,
            prefix = "$prefix-partner",
            items = partnerItems,
            columns = columns,
        )
    }
}

private fun addGridRows(
    target: MutableList<PhotoFeedBlock>,
    prefix: String,
    items: List<PhotoFeedItem>,
    columns: Int,
) {
    items.chunked(columns).forEachIndexed { index, rowItems ->
        target += PhotoFeedGridRow(
            key = "$prefix-row-$index",
            items = rowItems,
        )
    }
}

private fun monthGroups(items: List<PhotoFeedItem>): List<MonthGroup> {
    val groups = linkedMapOf<Pair<Int, Int>, MutableList<PhotoFeedItem>>()

    items.forEach { item ->
        val key = item.displayYear to item.displayMonth
        groups.getOrPut(key) { mutableListOf() } += item
    }

    return groups.map { (key, groupItems) ->
        MonthGroup(
            year = key.first,
            month = key.second,
            items = groupItems,
            anchorTimeMillis = monthStartMillis(key.first, key.second),
        )
    }
}

private fun dayGroups(items: List<PhotoFeedItem>): List<DayGroup> {
    val groups = linkedMapOf<Triple<Int, Int, Int>, MutableList<PhotoFeedItem>>()

    items.forEach { item ->
        val key = Triple(item.displayYear, item.displayMonth, item.displayDay)
        groups.getOrPut(key) { mutableListOf() } += item
    }

    return groups.map { (key, groupItems) ->
        DayGroup(
            year = key.first,
            month = key.second,
            day = key.third,
            items = groupItems,
            anchorTimeMillis = dayStartMillis(key.first, key.second, key.third),
        )
    }
}

private fun yearGroups(items: List<PhotoFeedItem>): List<YearGroup> {
    val groups = linkedMapOf<Int, MutableList<PhotoFeedItem>>()

    items.forEach { item ->
        groups.getOrPut(item.displayYear) { mutableListOf() } += item
    }

    return groups.map { (year, groupItems) ->
        YearGroup(
            year = year,
            items = groupItems,
            anchorTimeMillis = yearStartMillis(year),
        )
    }
}

private data class MonthGroup(
    val year: Int,
    val month: Int,
    val items: List<PhotoFeedItem>,
    val anchorTimeMillis: Long,
)

private data class DayGroup(
    val year: Int,
    val month: Int,
    val day: Int,
    val items: List<PhotoFeedItem>,
    val anchorTimeMillis: Long,
)

private data class YearGroup(
    val year: Int,
    val items: List<PhotoFeedItem>,
    val anchorTimeMillis: Long,
)

internal fun findBlockIndexForMedia(
    blocks: List<PhotoFeedBlock>,
    mediaId: String,
): Int {
    return blocks.indexOfFirst { block ->
        block is PhotoFeedGridRow && block.items.any { it.mediaId == mediaId }
    }
}

internal fun headerIndexForMedia(
    blocks: List<PhotoFeedBlock>,
    mediaBlockIndex: Int,
    density: PhotoFeedDensity,
): Int {
    if (mediaBlockIndex <= 0) return mediaBlockIndex.coerceAtLeast(0)
    val headerIndex = blocks
        .take(mediaBlockIndex + 1)
        .indexOfLast { block ->
            when {
                block is PhotoFeedTimeBucketHeader -> true
                density.columns <= 4 -> block is PhotoFeedDayHeader
                else -> block is PhotoFeedSectionHeader
            }
        }
    return if (headerIndex >= 0) headerIndex else mediaBlockIndex
}

internal val CollaborativeBucketHoursOptions: List<Int> = listOf(1, 2, 4, 6, 8, 12, 24)
internal const val DefaultCollaborativeBucketHours: Int = 24

private enum class CollaborativeSlot {
    CURRENT,
    PARTNER,
}

private data class CollaborativeBucket(
    val startMillis: Long,
    val items: List<PhotoFeedItem>,
)

private fun collaborativeSlotForItem(
    item: PhotoFeedItem,
    currentUserId: String?,
    partnerUserId: String?,
): CollaborativeSlot {
    return when {
        !partnerUserId.isNullOrBlank() && item.uploadedByUserId == partnerUserId -> CollaborativeSlot.PARTNER
        else -> CollaborativeSlot.CURRENT
    }
}

private fun collaborativeOwnerUserId(
    item: PhotoFeedItem,
    currentUserId: String?,
    partnerUserId: String?,
): String? {
    return when (collaborativeSlotForItem(item, currentUserId, partnerUserId)) {
        CollaborativeSlot.CURRENT -> currentUserId ?: item.uploadedByUserId ?: partnerUserId
        CollaborativeSlot.PARTNER -> partnerUserId ?: item.uploadedByUserId ?: currentUserId
    }
}

internal fun resolveCollaborativeOwnerUserId(
    item: PhotoFeedItem,
    currentUserId: String?,
    partnerUserId: String?,
): String? = collaborativeOwnerUserId(item, currentUserId, partnerUserId)

private fun collaborativeBuckets(
    items: List<PhotoFeedItem>,
    timeBucketHours: Int,
): List<CollaborativeBucket> {
    val grouped = linkedMapOf<Long, MutableList<PhotoFeedItem>>()
    items.forEach { item ->
        val bucketStartMillis = collaborativeBucketStartMillis(
            displayTimeMillis = item.mediaDisplayTimeMillis,
            bucketHours = timeBucketHours,
        )
        grouped.getOrPut(bucketStartMillis) { mutableListOf() } += item
    }
    return grouped.map { (startMillis, bucketItems) ->
        CollaborativeBucket(
            startMillis = startMillis,
            items = bucketItems,
        )
    }
}

private fun collaborativeBucketStartMillis(
    displayTimeMillis: Long,
    bucketHours: Int,
): Long {
    val calendar = calendarFor(displayTimeMillis).apply {
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }
    if (bucketHours >= DefaultCollaborativeBucketHours) {
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        return calendar.timeInMillis
    }
    val bucketStartHour = (calendar.get(Calendar.HOUR_OF_DAY) / bucketHours) * bucketHours
    calendar.set(Calendar.HOUR_OF_DAY, bucketStartHour)
    return calendar.timeInMillis
}

internal fun scrubberGranularityForDensity(density: PhotoFeedDensity): PhotoFeedTimeGranularity {
    return when (density) {
        PhotoFeedDensity.COMFORT_2,
        PhotoFeedDensity.COMFORT_3,
        PhotoFeedDensity.DENSE_4,
        -> PhotoFeedTimeGranularity.DAY

        PhotoFeedDensity.OVERVIEW_8 -> PhotoFeedTimeGranularity.MONTH
        PhotoFeedDensity.OVERVIEW_16 -> PhotoFeedTimeGranularity.YEAR
    }
}

internal fun formatScrubberLabel(
    year: Int,
    month: Int,
    day: Int,
    density: PhotoFeedDensity,
): String {
    return when (scrubberGranularityForDensity(density)) {
        PhotoFeedTimeGranularity.YEAR -> formatYearTitle(year)
        PhotoFeedTimeGranularity.MONTH -> formatMonthScrubberLabel(year, month)
        PhotoFeedTimeGranularity.DAY -> formatDayScrubberLabel(year, month, day)
    }
}

internal fun formatScrubberDateLabel(
    timeMillis: Long,
    density: PhotoFeedDensity,
): String {
    val calendar = calendarFor(timeMillis)
    val year = calendar.get(Calendar.YEAR)
    val month = calendar.get(Calendar.MONTH) + 1
    val day = calendar.get(Calendar.DAY_OF_MONTH)
    return formatScrubberLabel(year = year, month = month, day = day, density = density)
}

private fun PhotoFeedSectionHeader.scrubberFallbackLabel(): String {
    val resolvedYear = year ?: anchorTimeMillis?.let { calendarFor(it).get(Calendar.YEAR) } ?: return title
    return when (granularity) {
        PhotoFeedTimeGranularity.YEAR -> formatYearTitle(resolvedYear)
        PhotoFeedTimeGranularity.MONTH -> {
            val resolvedMonth = month
                ?: anchorTimeMillis?.let { calendarFor(it).get(Calendar.MONTH) + 1 }
                ?: return title
            formatMonthScrubberLabel(resolvedYear, resolvedMonth)
        }
        PhotoFeedTimeGranularity.DAY -> title
    }
}

private fun formatYearTitle(year: Int): String = "${year}年"

private fun formatMonthTitle(year: Int, month: Int): String = "${year}年${month}月"

private fun formatDayTitle(month: Int, day: Int): String = "${month}月${day}日"

private fun formatMonthScrubberLabel(year: Int, month: Int): String = "${year}年${month}月"

private fun formatDayScrubberLabel(year: Int, month: Int, day: Int): String {
    return "${year}年${month}月${day}日"
}

private fun formatHourRangeTitle(
    startMillis: Long,
    bucketHours: Int,
): String {
    val calendar = calendarFor(startMillis)
    val startHour = calendar.get(Calendar.HOUR_OF_DAY)
    val endHour = (startHour + bucketHours).coerceAtMost(24)
    return "${startHour}:00-${endHour}:00"
}

private fun yearStartMillis(year: Int): Long {
    return calendarFor(0L).apply {
        set(Calendar.YEAR, year)
        set(Calendar.MONTH, Calendar.JANUARY)
        set(Calendar.DAY_OF_MONTH, 1)
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis
}

private fun monthStartMillis(year: Int, month: Int): Long {
    return calendarFor(0L).apply {
        set(Calendar.YEAR, year)
        set(Calendar.MONTH, month - 1)
        set(Calendar.DAY_OF_MONTH, 1)
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis
}

private fun dayStartMillis(year: Int, month: Int, day: Int): Long {
    return calendarFor(0L).apply {
        set(Calendar.YEAR, year)
        set(Calendar.MONTH, month - 1)
        set(Calendar.DAY_OF_MONTH, day)
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis
}

private fun calendarFor(timeMillis: Long): Calendar {
    return Calendar.getInstance(Locale.CHINA).apply {
        timeInMillis = timeMillis
    }
}

package com.example.yingshi.feature.photos

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

private fun buildMonthAndDayBlocks(
    items: List<PhotoFeedItem>,
    columns: Int,
): List<PhotoFeedBlock> {
    val blocks = mutableListOf<PhotoFeedBlock>()

    monthGroups(items).forEach { monthGroup ->
        blocks += PhotoFeedSectionHeader(
            key = "month-${monthGroup.year}-${monthGroup.month}",
            title = "${monthGroup.year}年${monthGroup.month}月",
        )

        dayGroups(monthGroup.items).forEach { dayGroup ->
            blocks += PhotoFeedDayHeader(
                key = "day-${dayGroup.year}-${dayGroup.month}-${dayGroup.day}",
                title = "${dayGroup.month}月${dayGroup.day}日",
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
            title = "${monthGroup.year}年${monthGroup.month}月",
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
            title = "${yearGroup.year}年",
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
        )
    }
}

private data class MonthGroup(
    val year: Int,
    val month: Int,
    val items: List<PhotoFeedItem>,
)

private data class DayGroup(
    val year: Int,
    val month: Int,
    val day: Int,
    val items: List<PhotoFeedItem>,
)

private data class YearGroup(
    val year: Int,
    val items: List<PhotoFeedItem>,
)

package com.example.yingshi.feature.photos

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.yingshi.ui.theme.YingShiThemeTokens
import java.util.Calendar
import java.util.Locale
import kotlinx.coroutines.launch

// ── Lunar data ───────────────────────────────────────────────────────

private val lunarDayNames = listOf(
    "初一", "初二", "初三", "初四", "初五", "初六", "初七", "初八", "初九", "初十",
    "十一", "十二", "十三", "十四", "十五", "十六", "十七", "十八", "十九", "二十",
    "廿一", "廿二", "廿三", "廿四", "廿五", "廿六", "廿七", "廿八", "廿九", "三十",
)

private val cnyDates = listOf(
    2020 to (1 to 25), 2021 to (2 to 12), 2022 to (2 to 1),
    2023 to (1 to 22), 2024 to (2 to 10), 2025 to (1 to 29),
    2026 to (2 to 17), 2027 to (2 to 6),  2028 to (1 to 26),
    2029 to (2 to 13), 2030 to (2 to 3),  2031 to (1 to 23),
)

private val lunarMonthLengths = intArrayOf(30, 29, 30, 29, 30, 30, 29, 30, 29, 30, 29, 30)

private val specialDays: Map<Int, String> = mapOf(
    101 to "元旦", 120 to "大寒", 204 to "立春", 214 to "情人节", 217 to "春节",
    305 to "惊蛰", 321 to "春分", 405 to "清明", 420 to "谷雨",
    501 to "劳动节", 505 to "立夏", 510 to "母亲节", 521 to "小满",
    605 to "芒种", 619 to "端午", 621 to "夏至",
    707 to "小暑", 723 to "大暑", 807 to "立秋", 823 to "处暑",
    907 to "白露", 923 to "秋分",
    1001 to "国庆节", 1008 to "寒露", 1023 to "霜降",
    1107 to "立冬", 1122 to "小雪", 1207 to "大雪", 1222 to "冬至",
)

private val daysBeforeMonth = intArrayOf(0, 31, 59, 90, 120, 151, 181, 212, 243, 273, 304, 334)
private fun isLeap(y: Int) = (y % 4 == 0 && y % 100 != 0) || (y % 400 == 0)

private fun daysSinceEpoch(y: Int, m: Int, d: Int): Int {
    val year = y - 1
    return year * 365 + year / 4 - year / 100 + year / 400 +
        daysBeforeMonth[m - 1] + (if (m > 2 && isLeap(y)) 1 else 0) + d
}

private fun monthLength(year: Int, month: Int): Int = when (month) {
    1, 3, 5, 7, 8, 10, 12 -> 31; 4, 6, 9, 11 -> 30
    2 -> if (isLeap(year)) 29 else 28; else -> 30
}

private fun weekday(y: Int, m: Int, d: Int): Int {
    val t = intArrayOf(0, 3, 2, 5, 0, 3, 5, 1, 4, 6, 2, 4)
    val year = if (m < 3) y - 1 else y
    return (year + year / 4 - year / 100 + year / 400 + t[m - 1] + d) % 7
}

private val allLunarStarts: List<Pair<Int, Int>> = run {
    val list = mutableListOf<Pair<Int, Int>>()
    for ((year, md) in cnyDates) {
        val (m, d) = md; var e = daysSinceEpoch(year, m, d)
        var back = e; for (i in 11 downTo 0) { back -= lunarMonthLengths[i]; list += back to lunarMonthLengths[i] }
        for (len in lunarMonthLengths) { list += e to len; e += len }
    }
    list.distinct().sortedBy { it.first }
}

private val yearLunarCache: Map<Int, Array<Array<String?>>> = run {
    val c = mutableMapOf<Int, Array<Array<String?>>>()
    for (year in 2021..2030) {
        c[year] = Array(13) { mo -> Array(32) { dy ->
            if (mo == 0 || dy == 0) null
            else specialDays[mo * 100 + dy] ?: run {
                val t = daysSinceEpoch(year, mo, dy)
                var r: String? = null
                for ((s, l) in allLunarStarts) { val o = t - s; if (o in 0..<l) { r = lunarDayNames.getOrNull(o); break } }
                r
            }
        } }
    }
    c
}

private fun lunarCached(year: Int, month: Int, day: Int): String? =
    yearLunarCache[year]?.getOrNull(month)?.getOrNull(day)

// ── Cell data ────────────────────────────────────────────────────────

@Immutable
private data class CellTexts(
    val day: Int, val isToday: Boolean, val isSelected: Boolean,
    val top: String?, val topGreen: Boolean, val topRed: Boolean, val bot: String?,
)

// ── Pager constants ──────────────────────────────────────────────────

private const val PagerStartYear = 2020
private const val PagerEndYear = 2030
private const val PagerMonthCount = (PagerEndYear - PagerStartYear + 1) * 12

private fun pagerToYM(index: Int) = PagerStartYear + index / 12 to (index % 12)
private fun ymToPager(y: Int, m: Int) = (y - PagerStartYear) * 12 + m

// ── Public entry ─────────────────────────────────────────────────────

@Composable
fun ViewerTimeEditorSheet(
    initialTimeMillis: Long,
    onDismiss: () -> Unit,
    onConfirm: (Long) -> Unit,
) {
    val ic = remember(initialTimeMillis) {
        Calendar.getInstance(Locale.CHINA).apply { timeInMillis = initialTimeMillis }
    }
    var selY by remember { mutableIntStateOf(ic.get(Calendar.YEAR)) }
    var selM by remember { mutableIntStateOf(ic.get(Calendar.MONTH)) }
    var selD by remember { mutableIntStateOf(ic.get(Calendar.DAY_OF_MONTH)) }
    val ih = ic.get(Calendar.HOUR_OF_DAY)
    val im = (ic.get(Calendar.MINUTE) / 5) * 5
    var selH by remember { mutableIntStateOf(ih) }
    var selMin by remember { mutableIntStateOf(im) }
    var showTimeWheel by remember { mutableStateOf(false) }

    ViewerMainSheet(selY, selM, selD, selH, selMin,
        onYm = { y, m -> selY = y; selM = m },
        onDay = { selD = it },
        onDismiss = onDismiss,
        onDone = {
            onConfirm(Calendar.getInstance(Locale.CHINA).apply {
                set(selY, selM, selD, selH, selMin, 0); set(Calendar.MILLISECOND, 0)
            }.timeInMillis)
        },
        onTime = { showTimeWheel = true },
    )
    if (showTimeWheel) ViewerTimeWheelSheet(selH, selMin,
        onDismiss = { showTimeWheel = false },
        onConfirm = { h, m -> selH = h; selMin = m; showTimeWheel = false },
    )
}

// ── Main sheet ───────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ViewerMainSheet(
    selY: Int, selM: Int, selD: Int, selH: Int, selMin: Int,
    onYm: (Int, Int) -> Unit, onDay: (Int) -> Unit,
    onDismiss: () -> Unit, onDone: () -> Unit, onTime: () -> Unit,
) {
    val sp = YingShiThemeTokens.spacing; val rd = YingShiThemeTokens.radius
    val colors = YingShiThemeTokens.colors
    val accent = colors.viewerAccent
    val textColor = colors.viewerText
    val secondaryText = colors.viewerTextSecondary
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val initPage = ymToPager(selY, selM)
    val pagerState = rememberPagerState(initPage, pageCount = { PagerMonthCount })
    val (py, pm) = pagerToYM(pagerState.currentPage)
    val initPageKey = remember { initPage }
    LaunchedEffect(pagerState.currentPage) {
        if (pagerState.currentPage == initPageKey) return@LaunchedEffect
        val (y, m) = pagerToYM(pagerState.currentPage); onYm(y, m)
    }
    val today = remember {
        val c = Calendar.getInstance(Locale.CHINA)
        Triple(c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH))
    }
    val wk = listOf("一", "二", "三", "四", "五", "六", "日")
    val stableOnDay by rememberUpdatedState(onDay)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = colors.viewerSurface,
        dragHandle = {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Spacer(Modifier.height(sp.sm))
                Surface(
                    Modifier.size(36.dp, 4.dp),
                    RoundedCornerShape(rd.capsule),
                    color = secondaryText.copy(alpha = 0.34f),
                ) {}
                Spacer(Modifier.height(sp.xs))
            }
        },
    ) {
        Column(Modifier.fillMaxWidth().fillMaxHeight(0.54f).padding(horizontal = sp.lg)) {
            Row(Modifier.fillMaxWidth().padding(top = sp.sm), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                TextButton(onClick = onDismiss) { Text("取消", color = secondaryText) }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val p = pagerState.currentPage > 0
                    val n = pagerState.currentPage < PagerMonthCount - 1
                    TextButton(onClick = { if (p) scope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) } }, enabled = p) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                            contentDescription = "上一个月",
                            tint = if (p) textColor else secondaryText.copy(alpha = 0.56f),
                        )
                    }
                    Text(
                        "${py}/%02d".format(pm + 1),
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = textColor,
                    )
                    TextButton(onClick = { if (n) scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) } }, enabled = n) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = "下一个月",
                            tint = if (n) textColor else secondaryText.copy(alpha = 0.56f),
                        )
                    }
                }
                TextButton(onClick = onDone) { Text("确定", color = accent, fontWeight = FontWeight.Bold) }
            }

            Spacer(Modifier.height(8.dp))

            Row(Modifier.fillMaxWidth()) {
                wk.forEach { d ->
                    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        Text(d, fontSize = 11.sp, color = secondaryText)
                    }
                }
            }

            Spacer(Modifier.height(2.dp))

            HorizontalPager(
                state = pagerState, modifier = Modifier.fillMaxWidth().weight(1f),
                beyondViewportPageCount = 1, key = { it },
            ) { page ->
                val (y, m) = pagerToYM(page)
                CalendarMonth(y, m, if (y == selY && m == selM) selD else 0, today, stableOnDay)
            }

            Spacer(Modifier.height(6.dp))

            Surface(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(rd.lg)).clickable(onClick = onTime),
                RoundedCornerShape(rd.lg),
                color = colors.viewerBackground.copy(alpha = 0.82f),
            ) {
                Row(Modifier.fillMaxWidth().padding(horizontal = sp.md, vertical = sp.sm), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                    Text("时间", style = MaterialTheme.typography.bodyMedium, color = secondaryText)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "%02d:%02d".format(selH, selMin),
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = textColor,
                        )
                        Spacer(Modifier.width(4.dp))
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = "编辑时间",
                            tint = secondaryText,
                        )
                    }
                }
            }
        }
    }
}

// ── Calendar month ───────────────────────────────────────────────────

@Composable
private fun CalendarMonth(
    year: Int, month: Int, selectedDay: Int,
    today: Triple<Int, Int, Int>, onDaySelected: (Int) -> Unit,
) {
    val colors = YingShiThemeTokens.colors
    val cells = remember(year, month, selectedDay) {
        val dim = monthLength(year, month + 1)
        val fdow = (weekday(year, month + 1, 1) + 6) % 7
        val rows = ((fdow + dim + 6) / 7)
        (0 until rows * 7).map { idx ->
            val day = idx - fdow + 1
            if (day !in 1..dim) null
            else {
                val lt = lunarCached(year, month + 1, day)
                val isT = year == today.first && month == today.second && day == today.third
                val showTop = isT || lt in setOf("元旦", "春节", "清明", "劳动节", "国庆节")
                CellTexts(day, isT, day == selectedDay,
                    top = if (showTop) lt else null, topGreen = isT,
                    topRed = lt in setOf("元旦", "春节", "清明", "劳动节", "国庆节"), bot = lt)
            }
        }
    }

    val cellShape = remember { RoundedCornerShape(6.dp) }
    val selectedBg = remember(colors.viewerAccent) { colors.viewerAccent.copy(alpha = 0.16f) }

    Column(Modifier.fillMaxSize()) {
        val rows = cells.size / 7
        for (r in 0 until rows) {
            Row(Modifier.fillMaxWidth().weight(1f)) {
                for (c in 0 until 7) {
                    val cell = cells[r * 7 + c]
                    val bg = if (cell != null && cell.isSelected) selectedBg else Color.Transparent
                    Box(Modifier.weight(1f).fillMaxHeight().background(bg, cellShape)
                        .clickable(enabled = cell != null) { cell?.let { onDaySelected(it.day) } },
                        contentAlignment = Alignment.Center,
                    ) {
                        if (cell != null) {
                            Column(Modifier.fillMaxSize(), Arrangement.Center, Alignment.CenterHorizontally) {
                                if (cell.top != null) {
                                    Text(if (cell.topGreen) "今" else (cell.top ?: ""), fontSize = 10.sp,
                                        fontWeight = if (cell.topGreen) FontWeight.Bold else FontWeight.Normal,
                                        color = when {
                                            cell.topGreen -> colors.viewerAccent
                                            cell.topRed -> MaterialTheme.colorScheme.error
                                            else -> colors.viewerTextSecondary
                                        },
                                        maxLines = 1)
                                    Spacer(Modifier.height(1.dp))
                                }
                                Text("${cell.day}", fontSize = 15.sp,
                                    fontWeight = if (cell.isSelected || cell.isToday) FontWeight.Bold else FontWeight.Normal,
                                    color = if (cell.isSelected) colors.viewerAccent else colors.viewerText,
                                    maxLines = 1)
                                if (cell.bot != null && !cell.topRed && !cell.topGreen) {
                                    Spacer(Modifier.height(1.dp))
                                    Text(
                                        cell.bot ?: "",
                                        fontSize = 10.sp,
                                        color = colors.viewerTextSecondary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ── Time wheel sheet ─────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ViewerTimeWheelSheet(
    initialHour: Int, initialMinute: Int,
    onDismiss: () -> Unit, onConfirm: (Int, Int) -> Unit,
) {
    val sp = YingShiThemeTokens.spacing; val rd = YingShiThemeTokens.radius; val den = LocalDensity.current
    val colors = YingShiThemeTokens.colors
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val sm = (initialMinute / 5) * 5
    var selH by remember { mutableIntStateOf(initialHour) }
    var selM by remember { mutableIntStateOf(sm) }
    val hours = (0..23).map { "%02d".format(it) }; val minutes = (0..11).map { "%02d".format(it * 5) }
    val itemH = 48.dp; val vc = 5
    val vpDp = with(den) { ((itemH.roundToPx()) * vc).toDp() }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = colors.viewerSurface,
        dragHandle = {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Spacer(Modifier.height(sp.sm))
                Surface(
                    Modifier.size(36.dp, 4.dp),
                    RoundedCornerShape(rd.capsule),
                    color = colors.viewerTextSecondary.copy(alpha = 0.34f),
                ) {}
                Spacer(Modifier.height(sp.xs))
            }
        },
    ) {
        Column(Modifier.fillMaxWidth().fillMaxHeight(0.40f).padding(horizontal = sp.xl)) {
            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                TextButton(onClick = onDismiss) { Text("取消", color = colors.viewerTextSecondary) }
                Text(
                    "选择时间",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = colors.viewerText,
                )
                TextButton(onClick = { onConfirm(selH, selM) }) { Text("确定", color = colors.viewerAccent, fontWeight = FontWeight.Bold) }
            }
            Spacer(Modifier.height(sp.sm))
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text(
                    "%02d:%02d".format(selH, selM),
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                    color = colors.viewerText,
                )
            }
            Spacer(Modifier.height(sp.xs))
            Row(Modifier.weight(1f).height(vpDp), Arrangement.Center) {
                WheelColumn(hours, initialHour, selH, itemH, vc, { selH = it }, Modifier.weight(1f))
                Spacer(Modifier.width(8.dp))
                WheelColumn(minutes, sm / 5, selM / 5, itemH, vc, { selM = it * 5 }, Modifier.weight(1f))
            }
        }
    }
}

// ── Wheel column ─────────────────────────────────────────────────────

@Composable
private fun WheelColumn(
    items: List<String>, initIdx: Int, selIdx: Int,
    itemHDp: Dp, vc: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier,
) {
    val colors = YingShiThemeTokens.colors
    val d = LocalDensity.current; val ih = with(d) { itemHDp.roundToPx() }; val vp = ih * vc
    val pad = vp / 2 - ih / 2; val vpDp = with(d) { vp.toDp() }; val pdDp = with(d) { pad.toDp() }
    val listState = rememberLazyListState(); val scope = rememberCoroutineScope()
    var userScrolled by remember { mutableStateOf(false) }; var snapping by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { listState.scrollToItem(initIdx, 0) }
    LaunchedEffect(listState) {
        snapshotFlow { listState.isScrollInProgress }.collect { sc ->
            if (sc) userScrolled = true
            else if (userScrolled && !snapping) {
                snapping = true
                val lo = listState.layoutInfo; val vpC = lo.viewportStartOffset + vp / 2
                var best = 0; var bestD = Int.MAX_VALUE
                for (info in lo.visibleItemsInfo) {
                    val dist = kotlin.math.abs(info.offset + ih / 2 - vpC)
                    if (dist < bestD) { bestD = dist; best = info.index }
                }
                val snap = best.coerceIn(0, items.lastIndex)
                if (snap != selIdx) onSelect(snap)
                scope.launch { listState.animateScrollToItem(snap, 0) }
                userScrolled = false; snapping = false
            }
        }
    }

    Box(modifier = modifier.height(vpDp)) {
        LazyColumn(state = listState, modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(vertical = pdDp)) {
            items(count = items.size, key = { "${items[it]}_$it" }) { idx ->
                val ctr = idx == selIdx
                Box(Modifier.fillMaxWidth().height(itemHDp), contentAlignment = Alignment.Center) {
                    Text(items[idx],
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = if (ctr) FontWeight.Bold else FontWeight.Normal),
                        color = if (ctr) colors.viewerText else colors.viewerTextSecondary.copy(alpha = 0.72f),
                        textAlign = TextAlign.Center)
                }
            }
        }
        val lc = colors.viewerTextSecondary.copy(alpha = 0.18f)
        Box(Modifier.fillMaxWidth().height(1.dp).align(Alignment.TopCenter).offset(y = with(d) { (vp / 2 - ih / 2).toDp() }).background(lc))
        Box(Modifier.fillMaxWidth().height(1.dp).align(Alignment.TopCenter).offset(y = with(d) { (vp / 2 + ih / 2).toDp() }).background(lc))
        val mh = with(d) { (vp / 2 - ih / 2).toDp() }
        Box(
            Modifier.fillMaxWidth()
                .height(mh)
                .align(Alignment.TopCenter)
                .background(
                    Brush.verticalGradient(
                        listOf(
                            colors.viewerSurface.copy(alpha = 0.96f),
                            colors.viewerSurface.copy(alpha = 0.66f),
                            Color.Transparent,
                        ),
                    ),
                ),
        )
        Box(
            Modifier.fillMaxWidth()
                .height(mh)
                .align(Alignment.BottomCenter)
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color.Transparent,
                            colors.viewerSurface.copy(alpha = 0.66f),
                            colors.viewerSurface.copy(alpha = 0.96f),
                        ),
                    ),
                ),
        )
    }
}

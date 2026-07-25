package com.example.yingshi.feature.ledger

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.yingshi.ui.components.yingShiClickable
import com.example.yingshi.feature.ledger.data.LedgerBook
import com.example.yingshi.feature.ledger.data.LedgerBookTemplateDaily
import com.example.yingshi.feature.ledger.data.LedgerSeedData
import com.example.yingshi.feature.photos.rememberCollaboratorDirectorySnapshot
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

private enum class LedgerBookScope {
    MINE,
    PARTNER,
    OURS,
}

private fun LedgerBook.matchesBookScope(
    scope: LedgerBookScope,
    currentUserId: String?,
    partnerUserId: String?,
): Boolean = when (scope) {
    LedgerBookScope.MINE -> creatorUserId == null || creatorUserId == currentUserId
    LedgerBookScope.PARTNER -> partnerUserId != null && creatorUserId == partnerUserId
    LedgerBookScope.OURS -> creatorUserId == SHARED_OWNER_FLAG
}

private val LedgerBookColorOptions = listOf(
    0xFF47B972, 0xFF3CB4A5, 0xFF58B16B, 0xFF4B82F5,
    0xFFF59E4A, 0xFF9C6ADE, 0xFFE85D75, 0xFF6C7CE0,
    0xFF2BB6CF, 0xFFB8C24D, 0xFFE0A23C, 0xFF5E7C8B,
    0xFFD94560, 0xFF3FA7D6, 0xFF8BC34A, 0xFFF06292,
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LedgerBooksScreen(
    uiState: LedgerUiState,
    onBack: () -> Unit,
    onSaveBook: (String?, String, Long, String?, () -> Unit) -> Unit,
    onSetDefaultBook: (String) -> Unit,
    onArchiveBook: (String) -> Unit,
    onRestoreBook: (String) -> Unit,
) {
    var showCreateSheet by rememberSaveable { mutableStateOf(false) }
    var editingBook by remember { mutableStateOf<LedgerBook?>(null) }
    var actionBook by remember { mutableStateOf<LedgerBook?>(null) }
    val directory = rememberCollaboratorDirectorySnapshot(fallbackToFakeProfile = false)
    val currentUserId = directory.currentUser?.userId
    val partnerUserId = directory.partner?.userId
    val mineLabel = directory.currentUser?.displayName ?: "我的"
    val partnerLabel = directory.partner?.displayName ?: "对方的"
    val scopes = LedgerBookScope.entries
    var selectedScopeName by rememberSaveable { mutableStateOf(LedgerBookScope.MINE.name) }
    val selectedScope = LedgerBookScope.valueOf(selectedScopeName)
    val pagerState = rememberPagerState(
        initialPage = scopes.indexOf(selectedScope).coerceAtLeast(0),
        pageCount = { scopes.size },
    )
    val coroutineScope = rememberCoroutineScope()

    fun scopeLabel(scope: LedgerBookScope): String = when (scope) {
        LedgerBookScope.MINE -> mineLabel
        LedgerBookScope.PARTNER -> partnerLabel
        LedgerBookScope.OURS -> "我们"
    }

    LaunchedEffect(selectedScopeName) {
        val targetPage = scopes.indexOf(LedgerBookScope.valueOf(selectedScopeName))
        if (targetPage >= 0 && targetPage != pagerState.currentPage) {
            pagerState.animateScrollToPage(targetPage)
        }
    }
    // 立即同步 currentPage → selectedScopeName，避免滑动后 tab 选中延迟
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage }
            .distinctUntilChanged()
            .collect { currentPage ->
                val pageScope = scopes.getOrNull(currentPage) ?: return@collect
                if (pageScope.name != selectedScopeName) {
                    selectedScopeName = pageScope.name
                }
            }
    }

    LedgerPageScaffold(
        title = "账本管理",
        onBack = onBack,
        action = {
            // 对方账本页下不显示新增按钮
            if (selectedScope != LedgerBookScope.PARTNER) {
                Surface(
                    modifier = Modifier
                        .size(44.dp)
                        .yingShiClickable(
                            pressedScale = 0.94f,
                            shape = CircleShape,
                        ) { showCreateSheet = true },
                    shape = CircleShape,
                    color = LedgerPrimaryAction,
                    border = BorderStroke(1.dp, LedgerGlassStroke.copy(alpha = 0.88f)),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Default.Add,
                            contentDescription = "新增账本",
                            tint = LedgerHeaderGreen,
                            modifier = Modifier.size(25.dp),
                        )
                    }
                }
            }
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .navigationBarsPadding(),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                scopes.forEach { scope ->
                    LedgerSegmentChip(
                        text = scopeLabel(scope),
                        selected = selectedScope == scope,
                        modifier = Modifier.weight(1f),
                        horizontalPadding = 14.dp,
                        verticalPadding = 8.dp,
                        largeText = true,
                        onClick = {
                            selectedScopeName = scope.name
                            coroutineScope.launch {
                                pagerState.scrollToPage(scopes.indexOf(scope))
                            }
                        },
                    )
                }
            }
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.weight(1f),
                key = { page -> scopes[page].name },
            ) { page ->
                val pageScope = scopes[page]
                val visibleBooks = uiState.books.filter { it.matchesBookScope(pageScope, currentUserId, partnerUserId) }
                val visibleArchivedBooks = uiState.archivedBooks.filter { it.matchesBookScope(pageScope, currentUserId, partnerUserId) }
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 14.dp),
                    contentPadding = PaddingValues(bottom = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    item {
                        Text("可用账本", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = LedgerMuted)
                    }
                    if (visibleBooks.isEmpty()) {
                        item {
                            Surface(
                                color = LedgerRaisedSurface,
                                shape = RoundedCornerShape(20.dp),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(
                                    text = "暂无账本",
                                    modifier = Modifier.padding(vertical = 32.dp, horizontal = 16.dp),
                                    color = LedgerMuted,
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            }
                        }
                    } else {
                        items(visibleBooks, key = { it.id }) { book ->
                            LedgerBookRow(
                                book = book,
                                isDefault = book.id == uiState.defaultBookId,
                                archived = false,
                                onClick = { actionBook = book },
                            )
                        }
                    }
                    if (visibleArchivedBooks.isNotEmpty()) {
                        item {
                            Text("已归档", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = LedgerMuted)
                        }
                        items(visibleArchivedBooks, key = { it.id }) { book ->
                            LedgerBookRow(
                                book = book,
                                isDefault = false,
                                archived = true,
                                onClick = { actionBook = book },
                            )
                        }
                    }
                }
            }
        }
    }

    if (showCreateSheet) {
        LedgerBookEditorSheet(
            defaultOwnerUserId = currentUserId,
            partnerUserId = partnerUserId,
            mineLabel = mineLabel,
            partnerLabel = partnerLabel,
            onDismiss = { showCreateSheet = false },
            onSave = { name, coverColor, ownerUserId ->
                onSaveBook(null, name, coverColor, ownerUserId) {
                    showCreateSheet = false
                }
            },
        )
    }
    editingBook?.let { book ->
        LedgerBookEditorSheet(
            initial = book,
            defaultOwnerUserId = currentUserId,
            partnerUserId = partnerUserId,
            mineLabel = mineLabel,
            partnerLabel = partnerLabel,
            onDismiss = { editingBook = null },
            onSave = { name, coverColor, _ ->
                onSaveBook(book.id, name, coverColor, book.creatorUserId) {
                    editingBook = null
                }
            },
        )
    }
    actionBook?.let { book ->
        val actions = buildList {
            if (book.isDeleted) {
                add(
                    LedgerSheetAction("恢复账本") {
                        onRestoreBook(book.id)
                    },
                )
            } else {
                add(
                    LedgerSheetAction("编辑账本") {
                        editingBook = book
                    },
                )
                if (book.id != uiState.defaultBookId) {
                    add(
                        LedgerSheetAction("设为默认账本") {
                            onSetDefaultBook(book.id)
                        },
                    )
                }
                // 默认账本也可归档, 归档后自动将下一个设为默认
                add(
                    LedgerSheetAction("归档账本", destructive = true) {
                        onArchiveBook(book.id)
                    },
                )
            }
        }
        LedgerActionSheet(
            title = book.name,
            onDismiss = { actionBook = null },
            actions = actions,
        )
    }
}

@Composable
fun LedgerSettingsScreen(
    uiState: LedgerUiState,
    onBack: () -> Unit,
    onOpenBooks: () -> Unit,
    onOpenRecurring: () -> Unit,
    onSetDefaultBook: (String) -> Unit,
    onSetDefaultAccountForBook: (String, String?) -> Unit,
) {
    var showBookPicker by rememberSaveable { mutableStateOf(false) }
    var accountPickerBookId by rememberSaveable { mutableStateOf<String?>(null) }
    val context = LocalContext.current

    LedgerPageScaffold(title = "记账设置", onBack = onBack) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .navigationBarsPadding()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            contentPadding = PaddingValues(bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                LedgerSettingsSection(title = "默认账本") {
                    LedgerSettingsRow(
                        title = "默认账本",
                        subtitle = "重新进入记账时，优先打开这个账本。",
                        value = uiState.books.firstOrNull { it.id == uiState.defaultBookId }?.name ?: "请选择",
                        onClick = { showBookPicker = true },
                    )
                    LedgerSettingsRow(
                        title = "账本管理",
                        subtitle = "新增账本、编辑账本、切换默认账本。",
                        value = "进入",
                        onClick = onOpenBooks,
                    )
                }
            }
            item {
                LedgerSettingsSection(title = "默认账户") {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        uiState.books.forEach { book ->
                            val visibleAccounts = uiState.visibleAccountsByBook[book.id].orEmpty()
                            val selectedAccountName = visibleAccounts
                                .firstOrNull { it.id == uiState.defaultAccountIdsByBook[book.id] }
                                ?.name
                                ?: if (visibleAccounts.isEmpty()) "暂无可见账户" else "请选择"
                            LedgerSettingsRow(
                                title = book.name,
                                subtitle = "新建支出/收入默认使用；新建转账默认作为转出账户。",
                                value = selectedAccountName,
                                enabled = visibleAccounts.isNotEmpty(),
                                onClick = { accountPickerBookId = book.id },
                            )
                        }
                    }
                }
            }
            item {
                LedgerSettingsSection(title = "保留项") {
                    LedgerSettingsRow(
                        title = "周期记账",
                        subtitle = "新增、编辑周期规则，进入后自动补齐到期账单。",
                        value = "进入",
                        onClick = onOpenRecurring,
                    )
                    LedgerSettingsRow(
                        title = "自定义背景",
                        subtitle = "选择一张喜欢的图，作为账本背景。",
                        onClick = {
                            android.widget.Toast.makeText(context, "即将上线", android.widget.Toast.LENGTH_SHORT).show()
                        },
                    )
                    LedgerSettingsRow(
                        title = "小组件",
                        subtitle = "在桌面快速查看这个月的小账。",
                        onClick = {
                            android.widget.Toast.makeText(context, "即将上线", android.widget.Toast.LENGTH_SHORT).show()
                        },
                    )
                }
            }
        }
    }

    if (showBookPicker) {
        LedgerBookPickerSheet(
            books = uiState.books,
            selectedBookId = uiState.defaultBookId ?: uiState.currentBookId,
            defaultBookId = uiState.defaultBookId,
            onDismiss = { showBookPicker = false },
            onSelectBook = {
                onSetDefaultBook(it)
                showBookPicker = false
            },
            onManageBooks = onOpenBooks,
        )
    }
    accountPickerBookId?.let { bookId ->
        val visibleAccounts = uiState.visibleAccountsByBook[bookId].orEmpty()
        LedgerAccountPickerSheet(
            accounts = visibleAccounts,
            selectedAccountId = uiState.defaultAccountIdsByBook[bookId],
            title = "默认账户",
            onDismiss = { accountPickerBookId = null },
            onSelect = {
                onSetDefaultAccountForBook(bookId, it)
                accountPickerBookId = null
            },
        )
    }
}

@Composable
private fun LedgerBookRow(
    book: LedgerBook,
    isDefault: Boolean,
    archived: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        color = if (archived) LedgerGroupedHeader else LedgerRaisedSurface,
        shape = RoundedCornerShape(24.dp),
        modifier = Modifier
            .fillMaxWidth()
            .yingShiClickable(
                pressedScale = 0.96f,
                shape = RoundedCornerShape(18.dp),
                onClick = onClick,
            ),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(ledgerColor(book.coverColor).copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .size(18.dp)
                        .clip(CircleShape)
                        .background(ledgerColor(book.coverColor)),
                )
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = book.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    LedgerBookCreatorBadge(
                        creatorUserId = book.creatorUserId,
                        avatarSize = 18.dp,
                    )
                    if (isDefault) {
                        LedgerLabelBadge(text = "默认", color = LedgerHeaderGreen, background = LedgerGreenSoft)
                    }
                    if (archived) {
                        LedgerLabelBadge(text = "已归档", color = LedgerMuted, background = LedgerGroupedHeader)
                    }
                }
            }
            Icon(
                Icons.Default.MoreHoriz,
                contentDescription = "更多",
                tint = LedgerMuted,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
private fun LedgerBookEditorSheet(
    initial: LedgerBook? = null,
    defaultOwnerUserId: String? = null,
    partnerUserId: String? = null,
    mineLabel: String = "我的",
    partnerLabel: String = "对方的",
    onDismiss: () -> Unit,
    onSave: (name: String, coverColor: Long, ownerUserId: String?) -> Unit,
) {
    var name by rememberSaveable(initial?.id) { mutableStateOf(initial?.name.orEmpty()) }
    var coverColor by rememberSaveable(initial?.id) {
        mutableStateOf(initial?.coverColor ?: LedgerSeedData.defaultCoverColor(LedgerBookTemplateDaily))
    }
    // 归属选择：0=我的, 1=对方, 2=我们
    var ownerScope by rememberSaveable(initial?.id) {
        mutableIntStateOf(
            when {
                initial?.creatorUserId == null -> 0
                initial.creatorUserId == partnerUserId -> 1
                initial.creatorUserId == SHARED_OWNER_FLAG -> 2
                else -> 0
            },
        )
    }

    LedgerBottomSheetDialog(onDismiss = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            LedgerSheetHeader(
                title = if (initial == null) "新增账本" else "编辑账本",
                onDismiss = onDismiss,
                onConfirm = {
                    val resolvedOwner = when (ownerScope) {
                        1 -> partnerUserId
                        2 -> SHARED_OWNER_FLAG
                        else -> defaultOwnerUserId
                    }
                    onSave(name.trim(), coverColor, resolvedOwner)
                },
            )
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("账本名称") },
                singleLine = true,
            )
            // 归属选择（仅新增时可选，编辑时锁定）
            if (initial == null) {
                Text("归属", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    LedgerSegmentChip(
                        text = mineLabel,
                        selected = ownerScope == 0,
                        modifier = Modifier.weight(1f),
                        onClick = { ownerScope = 0 },
                    )
                    LedgerSegmentChip(
                        text = partnerLabel,
                        selected = ownerScope == 1,
                        modifier = Modifier.weight(1f),
                        onClick = { ownerScope = 1 },
                    )
                    LedgerSegmentChip(
                        text = "我们",
                        selected = ownerScope == 2,
                        modifier = Modifier.weight(1f),
                        onClick = { ownerScope = 2 },
                    )
                }
            }
            Text("主题色", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
            // 16 色分两行展示，每行 8 个，用 SpaceEvenly 占满整行
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                LedgerBookColorOptions.chunked(8).forEach { rowOptions ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                    ) {
                        rowOptions.forEach { option ->
                            Surface(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .yingShiClickable(
                                        pressedScale = 0.94f,
                                        shape = CircleShape,
                                        onClick = { coverColor = option },
                                    ),
                                shape = CircleShape,
                                color = ledgerColor(option),
                                border = BorderStroke(
                                    width = if (coverColor == option) 2.5.dp else 0.dp,
                                    color = if (coverColor == option) LedgerHeaderGreen else Color.Transparent,
                                ),
                            ) {
                                Box(modifier = Modifier.fillMaxSize())
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LedgerSettingsSection(
    title: String,
    content: @Composable () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = LedgerRaisedSurface,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            content()
        }
    }
}

@Composable
private fun LedgerSettingsRow(
    title: String,
    subtitle: String,
    value: String? = null,
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .yingShiClickable(
                enabled = enabled && onClick != null,
                pressedScale = 0.96f,
                shape = RoundedCornerShape(18.dp),
                onClick = { onClick?.invoke() },
            )
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = LedgerMuted,
            )
        }
        value?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodyMedium,
                color = if (enabled) LedgerHeaderGreen else LedgerMuted,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Icon(
            Icons.Default.ChevronRight,
            contentDescription = null,
            tint = if (enabled) LedgerMuted else LedgerDivider,
            modifier = Modifier.size(18.dp),
        )
    }
}

@Composable
internal fun LedgerLabelBadge(
    text: String,
    color: Color,
    background: Color,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(999.dp),
        color = background,
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            color = color,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun LedgerSheetHeader(
    title: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "取消",
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .yingShiClickable(
                    pressedScale = 0.94f,
                    shape = RoundedCornerShape(12.dp),
                    onClick = onDismiss,
                )
                .padding(horizontal = 6.dp, vertical = 6.dp),
            color = LedgerMuted,
            style = MaterialTheme.typography.bodyLarge,
        )
        Text(
            text = title,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = "保存",
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .yingShiClickable(
                    pressedScale = 0.94f,
                    shape = RoundedCornerShape(12.dp),
                    onClick = onConfirm,
                )
                .padding(horizontal = 6.dp, vertical = 6.dp),
            color = LedgerHeaderGreen,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Bold,
        )
    }
}

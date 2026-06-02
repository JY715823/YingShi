package com.example.yingshi.feature.ledger

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.yingshi.feature.ledger.data.LedgerBook
import com.example.yingshi.feature.ledger.data.LedgerBookTemplateDaily
import com.example.yingshi.feature.ledger.data.LedgerBookTemplates
import com.example.yingshi.feature.ledger.data.LedgerSeedData
import com.example.yingshi.feature.ledger.data.ledgerBookTemplateLabel

private val LedgerBookColorOptions = listOf(
    0xFF47B972,
    0xFF3CB4A5,
    0xFF58B16B,
    0xFF4B82F5,
    0xFFF59E4A,
    0xFF9C6ADE,
)

@Composable
fun LedgerBooksScreen(
    uiState: LedgerUiState,
    onBack: () -> Unit,
    onSaveBook: (String?, String, String, Long, () -> Unit) -> Unit,
    onSetDefaultBook: (String) -> Unit,
    onArchiveBook: (String) -> Unit,
    onRestoreBook: (String) -> Unit,
) {
    var showCreateSheet by rememberSaveable { mutableStateOf(false) }
    var editingBook by remember { mutableStateOf<LedgerBook?>(null) }
    var actionBook by remember { mutableStateOf<LedgerBook?>(null) }

    LedgerPageScaffold(
        title = "账本管理",
        onBack = onBack,
        action = {
            Text(
                text = "+",
                modifier = Modifier.clickable { showCreateSheet = true },
                color = LedgerHeaderGreen,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
        },
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .navigationBarsPadding()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            contentPadding = PaddingValues(bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Surface(
                    color = Color.White,
                    shape = RoundedCornerShape(22.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            text = "重新进入记账时，会优先打开默认账本。",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = "账本可归档后恢复，历史数据会保留。",
                            style = MaterialTheme.typography.bodySmall,
                            color = LedgerMuted,
                        )
                    }
                }
            }
            item {
                Text("可用账本", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = LedgerMuted)
            }
            items(uiState.books, key = { it.id }) { book ->
                LedgerBookRow(
                    book = book,
                    isDefault = book.id == uiState.defaultBookId,
                    archived = false,
                    onClick = { actionBook = book },
                )
            }
            if (uiState.archivedBooks.isNotEmpty()) {
                item {
                    Text("已归档", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = LedgerMuted)
                }
                items(uiState.archivedBooks, key = { it.id }) { book ->
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

    if (showCreateSheet) {
        LedgerBookEditorSheet(
            onDismiss = { showCreateSheet = false },
            onSave = { name, template, coverColor ->
                onSaveBook(null, name, template, coverColor) {
                    showCreateSheet = false
                }
            },
        )
    }
    editingBook?.let { book ->
        LedgerBookEditorSheet(
            initial = book,
            onDismiss = { editingBook = null },
            onSave = { name, _, coverColor ->
                onSaveBook(book.id, name, book.template, coverColor) {
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
                    add(
                        LedgerSheetAction("归档账本", destructive = true) {
                            onArchiveBook(book.id)
                        },
                    )
                }
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
                    LedgerStaticSettingRow(
                        title = "周期记账",
                        subtitle = "新增、编辑周期规则，进入后自动补齐到期账单。",
                        value = "进入",
                        onClick = onOpenRecurring,
                    )
                    LedgerStaticSettingRow(
                        title = "自定义背景",
                        subtitle = "选择一张喜欢的图，作为账本背景。",
                    )
                    LedgerStaticSettingRow(
                        title = "小组件",
                        subtitle = "在桌面快速查看这个月的小账。",
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
        color = if (archived) Color(0xFFF7F7F8) else Color.White,
        shape = RoundedCornerShape(22.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
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
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (isDefault) {
                        LedgerLabelBadge(text = "默认", color = LedgerHeaderGreen, background = LedgerGreenSoft)
                    }
                    if (archived) {
                        LedgerLabelBadge(text = "已归档", color = LedgerMuted, background = LedgerGroupedHeader)
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    LedgerLabelBadge(
                        text = ledgerBookTemplateLabel(book.template),
                        color = LedgerMuted,
                        background = LedgerGroupedHeader,
                    )
                    Text(
                        text = book.currencySymbol,
                        style = MaterialTheme.typography.bodySmall,
                        color = LedgerMuted,
                    )
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
    onDismiss: () -> Unit,
    onSave: (name: String, template: String, coverColor: Long) -> Unit,
) {
    var name by rememberSaveable(initial?.id) { mutableStateOf(initial?.name.orEmpty()) }
    var template by rememberSaveable(initial?.id) {
        mutableStateOf(initial?.template ?: LedgerBookTemplateDaily)
    }
    var coverColor by rememberSaveable(initial?.id) {
        mutableStateOf(initial?.coverColor ?: LedgerSeedData.defaultCoverColor(template))
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
                onConfirm = { onSave(name.trim(), template, coverColor) },
            )
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("账本名称") },
                singleLine = true,
            )
            Text("模板", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
            if (initial == null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    LedgerBookTemplates.forEach { option ->
                        LedgerSegmentChip(
                            text = ledgerBookTemplateLabel(option),
                            selected = option == template,
                            modifier = Modifier.weight(1f),
                            onClick = {
                                template = option
                                coverColor = LedgerSeedData.defaultCoverColor(option)
                            },
                        )
                    }
                }
            } else {
                Surface(
                    color = Color(0xFFF8F8F9),
                    shape = RoundedCornerShape(18.dp),
                ) {
                    Text(
                        text = ledgerBookTemplateLabel(template),
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
            Text("主题色", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                LedgerBookColorOptions.forEach { option ->
                    Surface(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .clickable { coverColor = option },
                        shape = CircleShape,
                        color = ledgerColor(option),
                        border = BorderStroke(
                            width = if (coverColor == option) 2.dp else 0.dp,
                            color = if (coverColor == option) Color.Black.copy(alpha = 0.14f) else Color.Transparent,
                        ),
                    ) {
                        Box(modifier = Modifier.fillMaxSize())
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
        shape = RoundedCornerShape(22.dp),
        color = Color.White,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
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
    value: String,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .clickable(enabled = enabled, onClick = onClick)
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
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = if (enabled) Color.Black else LedgerMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Icon(
            Icons.Default.ChevronRight,
            contentDescription = null,
            tint = if (enabled) LedgerMuted else LedgerDivider,
            modifier = Modifier.size(18.dp),
        )
    }
}

@Composable
private fun LedgerStaticSettingRow(
    title: String,
    subtitle: String,
    value: String? = null,
    onClick: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .clickable(enabled = onClick != null) { onClick?.invoke() }
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
                color = LedgerHeaderGreen,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun LedgerLabelBadge(
    text: String,
    color: Color,
    background: Color,
) {
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = background,
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            color = color,
            style = MaterialTheme.typography.bodySmall,
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
                .clip(RoundedCornerShape(10.dp))
                .clickable(onClick = onDismiss)
                .padding(horizontal = 6.dp, vertical = 6.dp),
            color = LedgerMuted,
            style = MaterialTheme.typography.bodyLarge,
        )
        Text(
            text = title,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = "保存",
            modifier = Modifier
                .clip(RoundedCornerShape(10.dp))
                .clickable(onClick = onConfirm)
                .padding(horizontal = 6.dp, vertical = 6.dp),
            color = LedgerHeaderGreen,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Bold,
        )
    }
}

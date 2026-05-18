package com.example.yingshi.feature.ledger

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.RadioButtonChecked
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.example.yingshi.feature.ledger.data.LedgerAccount
import com.example.yingshi.feature.ledger.data.LedgerAccountType
import com.example.yingshi.feature.ledger.data.LedgerCategory
import com.example.yingshi.feature.ledger.data.LedgerCategoryType
import com.example.yingshi.feature.ledger.data.LedgerSearchTransactionType
import com.example.yingshi.feature.ledger.data.defaultAccountColor
import java.time.LocalDate

data class LedgerSheetAction(
    val label: String,
    val destructive: Boolean = false,
    val onClick: () -> Unit,
)

private val CategoryIconOptions = listOf(
    "restaurant",
    "shopping_bag",
    "home",
    "directions_car",
    "flight",
    "school",
    "payments",
    "wallet",
    "trending_up",
    "redeem",
    "local_hospital",
    "calendar",
    "category",
    "import",
    "more_horiz",
)

private val CategoryColorOptions = listOf(
    0xFFFF8A3D,
    0xFFF8BE2C,
    0xFF74C97D,
    0xFF24B8E8,
    0xFF52C8B7,
    0xFF7B72E9,
    0xFF2F98F2,
    0xFFFF5B3F,
    0xFFFF4D79,
    0xFF1FA15B,
    0xFFE85A5A,
    0xFF8D99A6,
)

@Composable
fun LedgerActionSheet(
    title: String,
    onDismiss: () -> Unit,
    actions: List<LedgerSheetAction>,
) {
    LedgerBottomSheetDialog(onDismiss = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            actions.forEach { action ->
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .clickable {
                            onDismiss()
                            action.onClick()
                        },
                    color = Color(0xFFF8F8F9),
                ) {
                    Text(
                        text = action.label,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 14.dp),
                        color = if (action.destructive) LedgerExpenseRed else Color.Black,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }
}

@Composable
fun LedgerCategoryEditorSheet(
    type: LedgerCategoryType,
    initial: LedgerCategory? = null,
    onDismiss: () -> Unit,
    onSave: (name: String, iconKey: String, color: Long) -> Unit,
) {
    var name by rememberSaveable(initial?.id) { mutableStateOf(initial?.name.orEmpty()) }
    var iconKey by rememberSaveable(initial?.id) { mutableStateOf(initial?.iconKey ?: CategoryIconOptions.first()) }
    var color by rememberSaveable(initial?.id) { mutableStateOf(initial?.color ?: CategoryColorOptions.first()) }
    LedgerBottomSheetDialog(onDismiss = onDismiss, modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            SheetHeader(
                title = if (initial == null) {
                    if (type == LedgerCategoryType.EXPENSE) "新增支出分类" else "新增收入分类"
                } else {
                    "编辑分类"
                },
                onDismiss = onDismiss,
                onConfirm = { onSave(name.trim(), iconKey, color) },
            )
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("分类名称") },
                singleLine = true,
            )
            Text("选择图标", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
            LazyVerticalGrid(
                columns = GridCells.Fixed(5),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(164.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(CategoryIconOptions) { option ->
                    IconChoiceChip(
                        iconKey = option,
                        selected = option == iconKey,
                        onClick = { iconKey = option },
                    )
                }
            }
            Text("选择颜色", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
            LazyVerticalGrid(
                columns = GridCells.Fixed(6),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(96.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(CategoryColorOptions) { option ->
                    ColorChoiceChip(
                        color = option,
                        selected = option == color,
                        onClick = { color = option },
                    )
                }
            }
        }
    }
}

@Composable
fun LedgerAccountEditorSheet(
    initial: LedgerAccount? = null,
    onDismiss: () -> Unit,
    onSave: (
        name: String,
        type: LedgerAccountType,
        initialBalanceCents: Long,
        includeInTotal: Boolean,
        note: String,
    ) -> Unit,
) {
    var name by rememberSaveable(initial?.id) { mutableStateOf(initial?.name.orEmpty()) }
    var type by rememberSaveable(initial?.id) { mutableStateOf(initial?.type?.name ?: LedgerAccountType.CASH.name) }
    var initialBalanceText by rememberSaveable(initial?.id) {
        mutableStateOf(initial?.initialBalanceCents?.let(::formatAmountValue)?.replace(",", "") ?: "0")
    }
    var includeInTotal by rememberSaveable(initial?.id) { mutableStateOf(initial?.includeInTotal ?: true) }
    var note by rememberSaveable(initial?.id) { mutableStateOf(initial?.note.orEmpty()) }
    LedgerBottomSheetDialog(onDismiss = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            SheetHeader(
                title = if (initial == null) "新增账户" else "编辑账户",
                onDismiss = onDismiss,
                onConfirm = {
                    onSave(
                        name.trim(),
                        LedgerAccountType.valueOf(type),
                        initialBalanceText.toCentsOrNull() ?: 0L,
                        includeInTotal,
                        note,
                    )
                },
            )
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("账户名称") },
                singleLine = true,
            )
            Text("账户类型", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
            LazyVerticalGrid(
                columns = GridCells.Fixed(4),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(144.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(LedgerAccountType.entries.toList()) { accountType ->
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = if (accountType.name == type) LedgerGreenSoft else Color(0xFFF8F8F9),
                        border = BorderStroke(1.dp, if (accountType.name == type) LedgerHeaderGreen else LedgerDivider),
                        modifier = Modifier.clickable { type = accountType.name },
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 10.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(Color.White),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    accountIcon(accountType),
                                    contentDescription = null,
                                    tint = ledgerColor(defaultAccountColor(accountType)),
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                            Text(
                                text = accountTypeLabel(accountType),
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }
            }
            OutlinedTextField(
                value = initialBalanceText,
                onValueChange = { initialBalanceText = it.filter { char -> char.isDigit() || char == '.' || char == '-' } },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("期初余额") },
                singleLine = true,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LedgerSegmentChip(
                    text = "计入总资产",
                    selected = includeInTotal,
                    modifier = Modifier.weight(1f),
                    onClick = { includeInTotal = true },
                )
                LedgerSegmentChip(
                    text = "不计入",
                    selected = !includeInTotal,
                    selectedColor = LedgerMuted,
                    modifier = Modifier.weight(1f),
                    onClick = { includeInTotal = false },
                )
            }
            OutlinedTextField(
                value = note,
                onValueChange = { note = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("备注") },
                maxLines = 3,
            )
        }
    }
}

@Composable
fun LedgerCategoryPickerSheet(
    title: String,
    categories: List<LedgerCategory>,
    selectedCategoryId: String? = null,
    includeAllLabel: String? = null,
    onDismiss: () -> Unit,
    onSelect: (String?) -> Unit,
) {
    LedgerBottomSheetDialog(onDismiss = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SheetTitleOnly(title = title, onDismiss = onDismiss)
            includeAllLabel?.let { label ->
                PickerRow(
                    label = label,
                    selected = selectedCategoryId == null,
                    onClick = { onSelect(null) },
                )
            }
            categories.forEach { category ->
                PickerRow(
                    label = category.name,
                    selected = category.id == selectedCategoryId,
                    leading = {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(ledgerColor(category.color)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(ledgerIcon(category.iconKey), contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                        }
                    },
                    trailingText = if (category.hidden) "已隐藏" else null,
                    onClick = { onSelect(category.id) },
                )
            }
        }
    }
}

@Composable
fun LedgerAccountChoiceSheet(
    title: String,
    accounts: List<LedgerAccount>,
    selectedAccountId: String? = null,
    includeAllLabel: String? = null,
    onDismiss: () -> Unit,
    onSelect: (String?) -> Unit,
) {
    LedgerBottomSheetDialog(onDismiss = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SheetTitleOnly(title = title, onDismiss = onDismiss)
            includeAllLabel?.let { label ->
                PickerRow(
                    label = label,
                    selected = selectedAccountId == null,
                    onClick = { onSelect(null) },
                )
            }
            accounts.forEach { account ->
                PickerRow(
                    label = account.name,
                    selected = account.id == selectedAccountId,
                    leading = {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFF2F2F2)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(accountIcon(account.type), contentDescription = null, tint = ledgerColor(account.color), modifier = Modifier.size(18.dp))
                        }
                    },
                    trailingText = if (account.hidden) "已隐藏" else formatAmountValue(account.balanceCents),
                    onClick = { onSelect(account.id) },
                )
            }
        }
    }
}

@Composable
fun LedgerSearchTypeSheet(
    selected: LedgerSearchTransactionType,
    onDismiss: () -> Unit,
    onSelect: (LedgerSearchTransactionType) -> Unit,
) {
    LedgerBottomSheetDialog(onDismiss = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SheetTitleOnly(title = "筛选类型", onDismiss = onDismiss)
            LedgerSearchTransactionType.entries.forEach { type ->
                PickerRow(
                    label = searchTypeLabel(type),
                    selected = type == selected,
                    onClick = { onSelect(type) },
                )
            }
        }
    }
}

@Composable
fun LedgerDateRangeDialog(
    startDate: LocalDate?,
    endDate: LocalDate?,
    onDismiss: () -> Unit,
    onConfirm: (LocalDate?, LocalDate?) -> Unit,
) {
    var startText by rememberSaveable { mutableStateOf(startDate?.toString().orEmpty()) }
    var endText by rememberSaveable { mutableStateOf(endDate?.toString().orEmpty()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("日期范围") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = startText,
                    onValueChange = { startText = it },
                    label = { Text("开始日期 YYYY-MM-DD") },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = endText,
                    onValueChange = { endText = it },
                    label = { Text("结束日期 YYYY-MM-DD") },
                    singleLine = true,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val start = startText.trim().takeIf { it.isNotBlank() }?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: startText.trim().takeIf { it.isBlank() }?.let { null }
                    val end = endText.trim().takeIf { it.isNotBlank() }?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: endText.trim().takeIf { it.isBlank() }?.let { null }
                    if ((startText.isBlank() || start != null) && (endText.isBlank() || end != null)) {
                        onConfirm(start, end)
                    }
                },
            ) { Text("确定") }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                TextButton(onClick = { onConfirm(null, null) }) { Text("清空") }
                TextButton(onClick = onDismiss) { Text("取消") }
            }
        },
    )
}

@Composable
fun LedgerAmountRangeDialog(
    minAmountCents: Long?,
    maxAmountCents: Long?,
    onDismiss: () -> Unit,
    onConfirm: (Long?, Long?) -> Unit,
) {
    var minText by rememberSaveable { mutableStateOf(minAmountCents?.let(::formatAmountValue)?.replace(",", "").orEmpty()) }
    var maxText by rememberSaveable { mutableStateOf(maxAmountCents?.let(::formatAmountValue)?.replace(",", "").orEmpty()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("金额范围") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = minText,
                    onValueChange = { minText = it.filter { char -> char.isDigit() || char == '.' || char == '-' } },
                    label = { Text("最小金额") },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = maxText,
                    onValueChange = { maxText = it.filter { char -> char.isDigit() || char == '.' || char == '-' } },
                    label = { Text("最大金额") },
                    singleLine = true,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val min = minText.trim().takeIf { it.isNotBlank() }?.toCentsOrNull()
                    val max = maxText.trim().takeIf { it.isNotBlank() }?.toCentsOrNull()
                    if ((minText.isBlank() || min != null) && (maxText.isBlank() || max != null)) {
                        onConfirm(min, max)
                    }
                },
            ) { Text("确定") }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                TextButton(onClick = { onConfirm(null, null) }) { Text("清空") }
                TextButton(onClick = onDismiss) { Text("取消") }
            }
        },
    )
}

@Composable
fun LedgerHiddenBadge() {
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = LedgerGroupedHeader,
    ) {
        Text(
            text = "已隐藏",
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            color = LedgerMuted,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

fun accountTypeLabel(type: LedgerAccountType): String = when (type) {
    LedgerAccountType.CASH -> "现金"
    LedgerAccountType.DEBIT_CARD -> "储蓄卡"
    LedgerAccountType.CREDIT -> "信用卡"
    LedgerAccountType.ALIPAY -> "支付宝"
    LedgerAccountType.WECHAT -> "微信"
    LedgerAccountType.INVESTMENT -> "理财"
    LedgerAccountType.DEBT -> "负债"
    LedgerAccountType.OTHER -> "其他"
}

fun searchTypeLabel(type: LedgerSearchTransactionType): String = when (type) {
    LedgerSearchTransactionType.ALL -> "全部"
    LedgerSearchTransactionType.EXPENSE -> "支出"
    LedgerSearchTransactionType.INCOME -> "收入"
    LedgerSearchTransactionType.TRANSFER -> "转账"
}

@Composable
private fun SheetHeader(
    title: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextButton(onClick = onDismiss) { Text("取消", color = LedgerMuted) }
        Text(
            text = title,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
        TextButton(onClick = onConfirm) { Text("保存", color = LedgerHeaderGreen, fontWeight = FontWeight.Bold) }
    }
}

@Composable
private fun SheetTitleOnly(title: String, onDismiss: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextButton(onClick = onDismiss) { Text("取消", color = LedgerMuted) }
        Text(
            text = title,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.width(48.dp))
    }
}

@Composable
private fun PickerRow(
    label: String,
    selected: Boolean,
    leading: (@Composable (() -> Unit))? = null,
    trailingText: String? = null,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        leading?.invoke()
        Text(
            text = label,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        trailingText?.let {
            Text(it, color = LedgerMuted, style = MaterialTheme.typography.bodySmall)
        }
        Icon(
            if (selected) Icons.Default.RadioButtonChecked else Icons.Default.RadioButtonUnchecked,
            contentDescription = null,
            tint = if (selected) LedgerHeaderGreen else LedgerMuted,
        )
    }
}

@Composable
private fun IconChoiceChip(
    iconKey: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = if (selected) LedgerGreenSoft else Color(0xFFF8F8F9),
        border = BorderStroke(1.dp, if (selected) LedgerHeaderGreen else LedgerDivider),
        modifier = Modifier.clickable(onClick = onClick),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                ledgerIcon(iconKey),
                contentDescription = null,
                tint = if (selected) LedgerHeaderGreen else LedgerMuted,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
private fun ColorChoiceChip(
    color: Long,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(34.dp)
            .clip(CircleShape)
            .background(ledgerColor(color))
            .then(if (selected) Modifier.border(2.dp, LedgerHeaderGreen, CircleShape) else Modifier)
            .clickable(onClick = onClick),
    )
}

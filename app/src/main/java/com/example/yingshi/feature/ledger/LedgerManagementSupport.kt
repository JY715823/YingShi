package com.example.yingshi.feature.ledger

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.ui.zIndex
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items as lazyColumnItems
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.RadioButtonChecked
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.mutableFloatStateOf
import com.example.yingshi.feature.ledger.data.LedgerAccount
import com.example.yingshi.feature.ledger.data.LedgerAccountDraft
import com.example.yingshi.feature.ledger.data.LedgerAccountType
import com.example.yingshi.feature.ledger.data.LedgerCategory
import com.example.yingshi.feature.ledger.data.LedgerCategoryType
import com.example.yingshi.feature.ledger.data.LedgerSearchTransactionType
import com.example.yingshi.feature.ledger.data.LedgerBank
import com.example.yingshi.feature.ledger.data.LedgerBankCatalog
import com.example.yingshi.feature.ledger.data.LedgerBankCategory
import com.example.yingshi.feature.ledger.data.LedgerBankIcon
import com.example.yingshi.feature.ledger.data.defaultAccountColor
import com.example.yingshi.feature.photos.rememberCollaboratorDirectorySnapshot
import com.example.yingshi.ui.components.yingShiClickable
import java.time.LocalDate
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

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
                        .yingShiClickable(pressedScale = 0.97f) {
                            onDismiss()
                            action.onClick()
                        },
                    color = if (action.destructive) {
                        MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.82f)
                    } else {
                        LedgerGroupedHeader
                    },
                ) {
                    Text(
                        text = action.label,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 14.dp),
                        color = if (action.destructive) MaterialTheme.colorScheme.onErrorContainer else LedgerHeaderGreen,
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
    defaultOwnerUserId: String? = null,
    onDismiss: () -> Unit,
    onSave: (LedgerAccountDraft) -> Unit,
) {
    val directory = rememberCollaboratorDirectorySnapshot(fallbackToFakeProfile = false)
    val currentUserId = directory.currentUser?.userId
    val partnerUserId = directory.partner?.userId
    val mineLabel = directory.currentUser?.displayName ?: "我的"
    val partnerLabel = directory.partner?.displayName ?: "对方的"

    var name by rememberSaveable(initial?.id) { mutableStateOf(initial?.name.orEmpty()) }
    var type by rememberSaveable(initial?.id) { mutableStateOf(initial?.type?.name ?: LedgerAccountType.CASH.name) }
    var initialBalanceText by rememberSaveable(initial?.id) {
        mutableStateOf(initial?.initialBalanceCents?.let(::formatAmountValue)?.replace(",", "") ?: "0")
    }
    var includeInTotal by rememberSaveable(initial?.id) { mutableStateOf(initial?.includeInTotal ?: true) }
    var note by rememberSaveable(initial?.id) { mutableStateOf(initial?.note.orEmpty()) }
    var ownerScope by rememberSaveable(initial?.id) {
        mutableIntStateOf(
            when {
                initial?.ownerUserId == null -> 0
                initial.ownerUserId == partnerUserId -> 1
                initial.ownerUserId == "shared" -> 2
                else -> 0
            },
        )
    }
    var bankKey by rememberSaveable(initial?.id) { mutableStateOf(initial?.bankKey ?: "") }
    var bankName by rememberSaveable(initial?.id) { mutableStateOf(initial?.bankName ?: "") }
    var cardNumber by rememberSaveable(initial?.id) { mutableStateOf(initial?.cardNumberTail ?: "") }
    var showBankPicker by rememberSaveable { mutableStateOf(false) }

    val selectedType = LedgerAccountType.valueOf(type)
    val isDebitCard = selectedType == LedgerAccountType.DEBIT_CARD
    val selectedBank = remember(bankKey) { LedgerBankCatalog.findByKey(bankKey) }
    val binMatch = remember(cardNumber) {
        if (cardNumber.length >= 4) LedgerBankCatalog.matchByBin(cardNumber) else null
    }

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
                    val tail = cardNumber.filter { it.isDigit() }.takeLast(4)
                    onSave(
                        LedgerAccountDraft(
                            id = initial?.id,
                            name = name.trim(),
                            type = selectedType,
                            initialBalanceCents = initialBalanceText.toCentsOrNull() ?: 0L,
                            includeInTotal = includeInTotal,
                            note = note,
                            ownerUserId = when (ownerScope) {
                                1 -> partnerUserId
                                2 -> SHARED_OWNER_FLAG
                                else -> currentUserId ?: defaultOwnerUserId
                            },
                            bankKey = if (isDebitCard && bankKey.isNotBlank()) bankKey else null,
                            bankName = if (isDebitCard && bankName.isNotBlank()) bankName else null,
                            cardNumberTail = if (isDebitCard && tail.length == 4) tail else null,
                        ),
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
            val hiddenTypes = setOf(
                LedgerAccountType.CREDIT,
                LedgerAccountType.INVESTMENT,
                LedgerAccountType.DEBT,
            )
            val typeOptions = remember(initial?.type) {
                if (initial?.type != null && initial.type in hiddenTypes) {
                    LedgerAccountType.entries.toList()
                } else {
                    LedgerAccountType.entries.filterNot { it in hiddenTypes }
                }
            }
            LazyVerticalGrid(
                columns = GridCells.Fixed(4),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(144.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(typeOptions) { accountType ->
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = if (accountType.name == type) LedgerGreenSoft else LedgerGroupedHeader,
                        border = BorderStroke(1.dp, if (accountType.name == type) LedgerHeaderGreen else LedgerDivider),
                        modifier = Modifier.yingShiClickable(pressedScale = 0.97f) { type = accountType.name },
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
                                    .background(LedgerRaisedSurface),
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
            if (isDebitCard) {
                Text("发卡银行", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = LedgerRaisedSurface,
                    border = BorderStroke(1.dp, LedgerDivider),
                    modifier = Modifier
                        .fillMaxWidth()
                        .yingShiClickable(pressedScale = 0.98f) { showBankPicker = true },
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        if (selectedBank != null) {
                            LedgerBankIcon(bank = selectedBank, size = 32.dp)
                            Text(
                                text = selectedBank.fullName,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(LedgerGroupedHeader),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    accountIcon(LedgerAccountType.DEBIT_CARD),
                                    contentDescription = null,
                                    tint = LedgerMuted,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                            Text(
                                text = "请选择发卡银行",
                                style = MaterialTheme.typography.bodyMedium,
                                color = LedgerMuted,
                            )
                        }
                        Spacer(modifier = Modifier.weight(1f))
                        Icon(
                            Icons.Filled.ArrowDropDown,
                            contentDescription = null,
                            tint = LedgerMuted,
                        )
                    }
                }
                OutlinedTextField(
                    value = cardNumber,
                    onValueChange = { input ->
                        val digits = input.filter { it.isDigit() }
                        if (digits.length <= 19) cardNumber = digits
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("储蓄卡卡号") },
                    singleLine = true,
                    supportingText = {
                        if (binMatch != null && selectedBank == null) {
                            Text(
                                text = "识别到：${binMatch.fullName}",
                                color = LedgerHeaderGreen,
                            )
                        } else if (cardNumber.length >= 4) {
                            Text(text = "仅保存尾号后四位，不存储完整卡号", color = LedgerMuted)
                        }
                    },
                )
            }
            OutlinedTextField(
                value = initialBalanceText,
                onValueChange = { initialBalanceText = it.filter { char -> char.isDigit() || char == '.' || char == '-' } },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("期初余额") },
                singleLine = true,
            )
            Text("归属", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LedgerSegmentChip(
                    text = mineLabel,
                    selected = ownerScope == 0,
                    modifier = Modifier.weight(1f),
                    onClick = { ownerScope = 0 },
                )
                LedgerSegmentChip(
                    text = partnerLabel,
                    selected = ownerScope == 1,
                    selectedColor = LedgerMuted,
                    modifier = Modifier.weight(1f),
                    onClick = { ownerScope = 1 },
                )
                LedgerSegmentChip(
                    text = "我们",
                    selected = ownerScope == 2,
                    selectedColor = LedgerHeaderGreen,
                    modifier = Modifier.weight(1f),
                    onClick = { ownerScope = 2 },
                )
            }
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
    if (showBankPicker) {
        LedgerBankPickerSheet(
            selectedBankKey = bankKey,
            onDismiss = { showBankPicker = false },
            onSelect = { bank ->
                bankKey = bank.key
                bankName = bank.fullName
                showBankPicker = false
            },
        )
    }
}

@Composable
fun LedgerBankPickerSheet(
    selectedBankKey: String?,
    onDismiss: () -> Unit,
    onSelect: (LedgerBank) -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    val grouped = remember(query) {
        val filtered = if (query.isBlank()) {
            LedgerBankCatalog.banks
        } else {
            LedgerBankCatalog.banks.filter { bank ->
                bank.shortName.contains(query, ignoreCase = true) ||
                    bank.fullName.contains(query, ignoreCase = true) ||
                    bank.alias.contains(query, ignoreCase = true)
            }
        }
        filtered.groupBy { it.category }
    }
    LedgerBottomSheetDialog(onDismiss = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            SheetTitleOnly(title = "选择发卡银行", onDismiss = onDismiss)
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("搜索银行名称") },
                singleLine = true,
            )
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(420.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                grouped.forEach { (category, banks) ->
                    item {
                        Text(
                            text = category.label,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = LedgerMuted,
                        )
                    }
                    lazyColumnItems(banks) { bank ->
                        val selected = bank.key == selectedBankKey
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = if (selected) LedgerGreenSoft else LedgerRaisedSurface,
                            border = BorderStroke(1.dp, if (selected) LedgerHeaderGreen else LedgerDivider),
                            modifier = Modifier
                                .fillMaxWidth()
                                .yingShiClickable(pressedScale = 0.98f) { onSelect(bank) },
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                LedgerBankIcon(bank = bank, size = 36.dp)
                                Text(
                                    text = bank.fullName,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                )
                                Spacer(modifier = Modifier.weight(1f))
                                if (selected) {
                                    Icon(
                                        Icons.Filled.RadioButtonChecked,
                                        contentDescription = null,
                                        tint = LedgerHeaderGreen,
                                    )
                                } else {
                                    Icon(
                                        Icons.Filled.RadioButtonUnchecked,
                                        contentDescription = null,
                                        tint = LedgerMuted,
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
                            Icon(ledgerIcon(category.iconKey), contentDescription = null, tint = LedgerRaisedSurface, modifier = Modifier.size(18.dp))
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
                                .background(LedgerGroupedHeader),
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
            LedgerDialogActionButton(
                text = "确定",
                onClick = {
                    val start = startText.trim().takeIf { it.isNotBlank() }?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: startText.trim().takeIf { it.isBlank() }?.let { null }
                    val end = endText.trim().takeIf { it.isNotBlank() }?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: endText.trim().takeIf { it.isBlank() }?.let { null }
                    if ((startText.isBlank() || start != null) && (endText.isBlank() || end != null)) {
                        onConfirm(start, end)
                    }
                },
                emphasized = true,
            )
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                LedgerDialogActionButton(text = "清空", onClick = { onConfirm(null, null) }, danger = true)
                LedgerDialogActionButton(text = "取消", onClick = onDismiss)
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
            LedgerDialogActionButton(
                text = "确定",
                onClick = {
                    val min = minText.trim().takeIf { it.isNotBlank() }?.toCentsOrNull()
                    val max = maxText.trim().takeIf { it.isNotBlank() }?.toCentsOrNull()
                    if ((minText.isBlank() || min != null) && (maxText.isBlank() || max != null)) {
                        onConfirm(min, max)
                    }
                },
                emphasized = true,
            )
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                LedgerDialogActionButton(text = "清空", onClick = { onConfirm(null, null) }, danger = true)
                LedgerDialogActionButton(text = "取消", onClick = onDismiss)
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
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        LedgerDialogActionButton(text = "取消", onClick = onDismiss)
        Text(
            text = title,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
        LedgerDialogActionButton(text = "保存", onClick = onConfirm, emphasized = true)
    }
}

@Composable
private fun SheetTitleOnly(title: String, onDismiss: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        LedgerDialogActionButton(text = "取消", onClick = onDismiss)
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
            .yingShiClickable(pressedScale = 0.97f, onClick = onClick)
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
        color = if (selected) LedgerGreenSoft else LedgerGroupedHeader,
        border = BorderStroke(1.dp, if (selected) LedgerHeaderGreen else LedgerDivider),
        modifier = Modifier.yingShiClickable(pressedScale = 0.97f, onClick = onClick),
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
            .yingShiClickable(pressedScale = 0.97f, onClick = onClick),
    )
}

@Composable
fun <T> LedgerLongPressReorderList(
    items: List<T>,
    keyOf: (T) -> String,
    modifier: Modifier = Modifier,
    itemSpacing: androidx.compose.ui.unit.Dp = 8.dp,
    itemHeight: androidx.compose.ui.unit.Dp = 72.dp,
    onOrderCommitted: (List<String>) -> Unit,
    itemContent: @Composable (item: T, isDragging: Boolean) -> Unit,
) {
    if (items.isEmpty()) return
    val density = LocalDensity.current
    val itemDistancePxFallback = with(density) { (itemHeight + itemSpacing).toPx() }
    // FR-8: remember key 用 IDs 列表而非 items 引用，防止同步推送导致状态丢失
    val itemIds = remember(items) { items.map(keyOf) }
    val originalIds = itemIds
    var orderedIds by remember(itemIds) { mutableStateOf(itemIds) }
    val itemsById = remember(items) { items.associateBy(keyOf) }
    var draggedId by remember { mutableStateOf<String?>(null) }
    // FR-2: Animatable 替代 mutableFloatStateOf，拖动结束时平滑归位
    val dragOffsetAnimatable = remember { androidx.compose.animation.core.Animatable(0f) }
    var draggedStartAbsoluteY by remember { mutableFloatStateOf(0f) }
    // FR-3: 实测 item 距离
    var actualItemDistancePx by remember { mutableFloatStateOf(itemDistancePxFallback) }
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    val coroutineScope = androidx.compose.runtime.rememberCoroutineScope()
    val edgeScrollThresholdPx = with(density) { 96.dp.toPx() }
    // FR-4: 距离加速参数
    val minAutoScrollSpeedPx = with(density) { 400.dp.toPx() }
    val maxAutoScrollSpeedPx = with(density) { 1800.dp.toPx() }
    var autoScrollJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    var currentAutoScrollDir by remember { mutableIntStateOf(0) }
    // FR-1: 触觉反馈
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current

    fun swap(fromIndex: Int, toIndex: Int) {
        val mutable = orderedIds.toMutableList()
        val temp = mutable[fromIndex]
        mutable[fromIndex] = mutable[toIndex]
        mutable[toIndex] = temp
        orderedIds = mutable
    }

    fun updateAutoScroll(fingerAbsoluteY: Float) {
        val layoutInfo = listState.layoutInfo
        val viewportStart = layoutInfo.viewportStartOffset.toFloat()
        val viewportEnd = layoutInfo.viewportEndOffset.toFloat()
        val activeId = draggedId
        if (activeId == null) {
            if (currentAutoScrollDir != 0) {
                autoScrollJob?.cancel()
                autoScrollJob = null
                currentAutoScrollDir = 0
            }
            return
        }
        val activeIndex = orderedIds.indexOf(activeId)
        val direction = when {
            fingerAbsoluteY > viewportEnd - edgeScrollThresholdPx && activeIndex < orderedIds.lastIndex -> 1
            fingerAbsoluteY < viewportStart + edgeScrollThresholdPx && activeIndex > 0 -> -1
            else -> 0
        }
        if (direction == currentAutoScrollDir && autoScrollJob?.isActive == true) return
        currentAutoScrollDir = direction
        autoScrollJob?.cancel()
        if (direction == 0) {
            autoScrollJob = null
            return
        }
        autoScrollJob = coroutineScope.launch {
            val dir = direction
            var lastFrameNanos = 0L
            while (draggedId != null && currentAutoScrollDir == dir) {
                androidx.compose.runtime.withFrameNanos { frameTimeNanos ->
                    if (lastFrameNanos == 0L) {
                        lastFrameNanos = frameTimeNanos
                        return@withFrameNanos
                    }
                    // FR-4: 基于实际 deltaTime 计算滚动量（帧率无关）
                    val deltaTime = (frameTimeNanos - lastFrameNanos) / 1_000_000_000f
                    lastFrameNanos = frameTimeNanos
                    // FR-4: 距离加速 — 手指越靠近边缘滚动越快
                    val currentFingerY = draggedStartAbsoluteY + dragOffsetAnimatable.value
                    val distanceToEdge = if (dir > 0) viewportEnd - currentFingerY else currentFingerY - viewportStart
                    val intensity = (1f - (distanceToEdge / edgeScrollThresholdPx)).coerceIn(0f, 1f)
                    val speedPxPerSec = minAutoScrollSpeedPx + (maxAutoScrollSpeedPx - minAutoScrollSpeedPx) * intensity
                    val delta = dir * speedPxPerSec * deltaTime
                    val consumed = listState.dispatchRawDelta(delta)
                    // 关键补偿：列表滚动后被拖项布局位置移动了 consumed，加回 dragOffset
                    coroutineScope.launch {
                        dragOffsetAnimatable.snapTo(dragOffsetAnimatable.value + consumed)
                    }
                    // 滚动后立即检查 swap
                    val aid = draggedId ?: return@withFrameNanos
                    var idx = orderedIds.indexOf(aid)
                    if (idx < 0) return@withFrameNanos
                    val dist = actualItemDistancePx
                    val currentOffset = dragOffsetAnimatable.value
                    var adjustedOffset = currentOffset
                    while (adjustedOffset > dist && idx < orderedIds.lastIndex) {
                        swap(idx, idx + 1)
                        adjustedOffset -= dist
                        idx++
                        // FR-1: swap 触觉反馈
                        haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                    }
                    while (adjustedOffset < -dist && idx > 0) {
                        swap(idx, idx - 1)
                        adjustedOffset += dist
                        idx--
                        haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                    }
                    if (adjustedOffset != currentOffset) {
                        coroutineScope.launch {
                            dragOffsetAnimatable.snapTo(adjustedOffset)
                        }
                    }
                }
            }
        }
    }

    LazyColumn(
        state = listState,
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(itemSpacing),
    ) {
        lazyColumnItems(orderedIds, key = { itemId -> itemId }) { id ->
            val item = itemsById[id]
            if (item != null) {
                val isDragging = draggedId == id
                val inDrag = draggedId != null
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        // FR-6: 拖动项 zIndex 提升至 10f
                        .zIndex(if (isDragging) 10f else 0f)
                        .animateItem(
                            fadeInSpec = null,
                            fadeOutSpec = null,
                            // FR-5: inDrag 期间 placementSpec 200ms + FastOutSlowInEasing
                            placementSpec = when {
                                isDragging -> null
                                inDrag -> androidx.compose.animation.core.tween(
                                    durationMillis = 200,
                                    easing = androidx.compose.animation.core.FastOutSlowInEasing,
                                )
                                else -> androidx.compose.animation.core.tween(durationMillis = 200)
                            },
                        )
                        .graphicsLayer {
                            translationY = if (isDragging) dragOffsetAnimatable.value else 0f
                            // FR-7: alpha 统一由外层管理
                            alpha = if (inDrag && !isDragging) 0.86f else 1f
                            // FR-6: 拖动项阴影和缩放增强
                            shadowElevation = if (isDragging) 24f else 0f
                            val scale = if (isDragging) 1.06f else 1f
                            scaleX = scale
                            scaleY = scale
                        }
                        .pointerInput(id) {
                            detectDragGesturesAfterLongPress(
                                onDragStart = { offset ->
                                    draggedId = id
                                    coroutineScope.launch { dragOffsetAnimatable.snapTo(0f) }
                                    // FR-3: 从 layoutInfo 实测 item 距离
                                    val layoutInfo = listState.layoutInfo
                                    val visibleItem = layoutInfo.visibleItemsInfo.firstOrNull { it.key == id }
                                    draggedStartAbsoluteY = visibleItem?.offset?.toFloat() ?: offset.y
                                    actualItemDistancePx = visibleItem?.let { current ->
                                        val next = layoutInfo.visibleItemsInfo.firstOrNull {
                                            it.index == current.index + 1
                                        }
                                        if (next != null) {
                                            (next.offset - current.offset).toFloat()
                                        } else {
                                            val spacingPx = with(density) { itemSpacing.toPx() }
                                            (current.size + spacingPx).toFloat()
                                        }
                                    } ?: itemDistancePxFallback
                                    // FR-1: 拖动开始触觉反馈
                                    haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                                },
                                onDragEnd = {
                                    val reordered = orderedIds != originalIds
                                    currentAutoScrollDir = 0
                                    autoScrollJob?.cancel()
                                    autoScrollJob = null
                                    // FR-2: spring 动画平滑归位
                                    coroutineScope.launch {
                                        dragOffsetAnimatable.animateTo(
                                            targetValue = 0f,
                                            animationSpec = androidx.compose.animation.core.spring(
                                                dampingRatio = 0.7f,
                                                stiffness = 300f,
                                            ),
                                        )
                                        draggedId = null
                                        // FR-1: 拖动结束触觉反馈
                                        haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.ContextClick)
                                        if (reordered) onOrderCommitted(orderedIds)
                                    }
                                },
                                onDragCancel = {
                                    currentAutoScrollDir = 0
                                    autoScrollJob?.cancel()
                                    autoScrollJob = null
                                    // FR-2: 取消时 spring 动画回滚到原位
                                    coroutineScope.launch {
                                        dragOffsetAnimatable.animateTo(
                                            targetValue = 0f,
                                            animationSpec = androidx.compose.animation.core.spring(
                                                dampingRatio = 0.7f,
                                                stiffness = 300f,
                                            ),
                                        )
                                        draggedId = null
                                        orderedIds = originalIds
                                        haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.ContextClick)
                                    }
                                },
                                onDrag = { change, dragAmount ->
                                    change.consume()
                                    val activeId = draggedId ?: return@detectDragGesturesAfterLongPress
                                    val newOffset = dragOffsetAnimatable.value + dragAmount.y
                                    coroutineScope.launch { dragOffsetAnimatable.snapTo(newOffset) }
                                    var index = orderedIds.indexOf(activeId)
                                    val dist = actualItemDistancePx
                                    var adjustedOffset = newOffset
                                    // 累计超过一个 item 距离就 swap
                                    while (adjustedOffset > dist && index < orderedIds.lastIndex) {
                                        swap(index, index + 1)
                                        adjustedOffset -= dist
                                        index++
                                        // FR-1: swap 触觉反馈
                                        haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                                    }
                                    while (adjustedOffset < -dist && index > 0) {
                                        swap(index, index - 1)
                                        adjustedOffset += dist
                                        index--
                                        haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                                    }
                                    if (adjustedOffset != newOffset) {
                                        coroutineScope.launch { dragOffsetAnimatable.snapTo(adjustedOffset) }
                                    }
                                    val fingerAbsoluteY = draggedStartAbsoluteY + adjustedOffset
                                    updateAutoScroll(fingerAbsoluteY)
                                },
                            )
                        },
                ) {
                    itemContent(item, isDragging)
                }
            }
        }
    }
}

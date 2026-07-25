package com.example.yingshi.feature.ledger.data

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

enum class LedgerBankCategory(val label: String) {
    STATE_OWNED("国有大行"),
    JOINT_STOCK("股份制"),
    CITY("城商行"),
    RURAL("农商农信"),
}

data class LedgerBank(
    val key: String,
    val shortName: String,
    val alias: String,
    val fullName: String,
    val brandColor: Long,
    val binPrefixes: List<String>,
    val category: LedgerBankCategory,
)

object LedgerBankCatalog {
    val banks: List<LedgerBank> = listOf(
        // 国有大行
        LedgerBank("icbc", "工行", "工行", "中国工商银行", 0xFFC7000B, listOf("6222", "9558"), LedgerBankCategory.STATE_OWNED),
        LedgerBank("abc", "农行", "农行", "中国农业银行", 0xFF1B9E93, listOf("6228", "9559"), LedgerBankCategory.STATE_OWNED),
        LedgerBank("boc", "中行", "中行", "中国银行", 0xFFA4192D, listOf("6216", "4563", "6013"), LedgerBankCategory.STATE_OWNED),
        LedgerBank("ccb", "建行", "建行", "中国建设银行", 0xFF0066B3, listOf("4367", "6227"), LedgerBankCategory.STATE_OWNED),
        LedgerBank("bocom", "交行", "交行", "交通银行", 0xFF0E2C76, listOf("6222", "6014", "5218"), LedgerBankCategory.STATE_OWNED),
        LedgerBank("psbc", "邮储", "邮储", "中国邮政储蓄银行", 0xFF007043, listOf("6221", "6210"), LedgerBankCategory.STATE_OWNED),
        // 股份制
        LedgerBank("cmb", "招商", "招商", "招商银行", 0xFFC81D27, listOf("6225", "6226"), LedgerBankCategory.JOINT_STOCK),
        LedgerBank("spdb", "浦发", "浦发", "上海浦东发展银行", 0xFF8B0010, listOf("6225", "9843"), LedgerBankCategory.JOINT_STOCK),
        LedgerBank("citic", "中信", "中信", "中信银行", 0xFF003C8F, listOf("6226", "4336"), LedgerBankCategory.JOINT_STOCK),
        LedgerBank("ceb", "光大", "光大", "中国光大银行", 0xFF6F008F, listOf("6226", "6282"), LedgerBankCategory.JOINT_STOCK),
        LedgerBank("hxb", "华夏", "华夏", "华夏银行", 0xFFB7121B, listOf("6226"), LedgerBankCategory.JOINT_STOCK),
        LedgerBank("cmbc", "民生", "民生", "中国民生银行", 0xFF00895C, listOf("6226", "4218"), LedgerBankCategory.JOINT_STOCK),
        LedgerBank("cgb", "广发", "广发", "广发银行", 0xFFC8102E, listOf("6225", "6227"), LedgerBankCategory.JOINT_STOCK),
        LedgerBank("cib", "兴业", "兴业", "兴业银行", 0xFF003D7A, listOf("6229", "4864"), LedgerBankCategory.JOINT_STOCK),
        LedgerBank("pab", "平安", "平安", "平安银行", 0xFFFB7F1D, listOf("6221", "6029", "9988"), LedgerBankCategory.JOINT_STOCK),
        LedgerBank("czb", "浙商", "浙商", "浙商银行", 0xFF0F3B7A, listOf("6223", "6210"), LedgerBankCategory.JOINT_STOCK),
        LedgerBank("cbhb", "渤海", "渤海", "渤海银行", 0xFF003B7A, listOf("6228"), LedgerBankCategory.JOINT_STOCK),
        LedgerBank("hfb", "恒丰", "恒丰", "恒丰银行", 0xFFED7102, listOf("6228", "6230"), LedgerBankCategory.JOINT_STOCK),
        // 城商行
        LedgerBank("bob", "北京", "北京", "北京银行", 0xFFC7000B, listOf("6214", "6221"), LedgerBankCategory.CITY),
        LedgerBank("bos", "上海", "上海", "上海银行", 0xFF005BAC, listOf("6218", "6221"), LedgerBankCategory.CITY),
        LedgerBank("njcb", "南京", "南京", "南京银行", 0xFFB31E8C, listOf("6217", "6223"), LedgerBankCategory.CITY),
        LedgerBank("nbcb", "宁波", "宁波", "宁波银行", 0xFF0061B1, listOf("6212", "6223"), LedgerBankCategory.CITY),
        LedgerBank("jsb", "江苏", "江苏", "江苏银行", 0xFFC7000B, listOf("6223", "6215"), LedgerBankCategory.CITY),
        LedgerBank("hzb", "杭州", "杭州", "杭州银行", 0xFF0066B3, listOf("6217", "6221"), LedgerBankCategory.CITY),
        LedgerBank("cdcb", "成都", "成都", "成都银行", 0xFFC8102E, listOf("6221", "6210"), LedgerBankCategory.CITY),
        LedgerBank("cscb", "长沙", "长沙", "长沙银行", 0xFFE60012, listOf("6223", "9400"), LedgerBankCategory.CITY),
        LedgerBank("gzb", "广州", "广州", "广州银行", 0xFFC7000B, listOf("6224", "9400"), LedgerBankCategory.CITY),
        // 农商农信
        LedgerBank("rcc", "农信", "农信", "农村信用社", 0xFF1B9E93, listOf("6210", "6221"), LedgerBankCategory.RURAL),
        LedgerBank("crcb", "重庆农商", "重庆农商", "重庆农村商业银行", 0xFFC7000B, listOf("6212", "6221"), LedgerBankCategory.RURAL),
        LedgerBank("srcb", "上海农商", "上海农商", "上海农村商业银行", 0xFF005BAC, listOf("6224", "6228"), LedgerBankCategory.RURAL),
        LedgerBank("brcb", "北京农商", "北京农商", "北京农村商业银行", 0xFF006341, listOf("6208", "6210"), LedgerBankCategory.RURAL),
        LedgerBank("grcb", "广州农商", "广州农商", "广州农村商业银行", 0xFFC8102E, listOf("6224", "9400"), LedgerBankCategory.RURAL),
    )

    private val bankByKey = banks.associateBy { it.key }

    fun findByKey(key: String?): LedgerBank? = key?.let { bankByKey[it] }

    fun matchByBin(cardNumber: String): LedgerBank? {
        if (cardNumber.length < 4) return null
        val prefix4 = cardNumber.take(4)
        val prefix6 = cardNumber.take(6)
        return banks.firstOrNull { bank ->
            bank.binPrefixes.any { prefix ->
                prefix6.startsWith(prefix) || prefix4.startsWith(prefix)
            }
        }
    }

    fun groupedByCategory(): Map<LedgerBankCategory, List<LedgerBank>> = banks.groupBy { it.category }
}

@Composable
fun LedgerBankIcon(
    bank: LedgerBank,
    modifier: Modifier = Modifier,
    size: androidx.compose.ui.unit.Dp = 32.dp,
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(10.dp))
            .background(Color(bank.brandColor)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = bank.alias.take(2),
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = if (size >= 28.dp) 12.sp else 10.sp,
        )
    }
}

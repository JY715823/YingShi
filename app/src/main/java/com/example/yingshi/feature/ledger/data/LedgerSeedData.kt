package com.example.yingshi.feature.ledger.data

object LedgerSeedData {
    const val DefaultBookId = "book-daily"
    const val TravelBookId = "book-travel"
    const val SharedBookId = "book-shared"
    const val DefaultCashAccountId = "account-cash"

    fun defaultBooks(nowMillis: Long) = listOf(
        LedgerBookEntity(
            id = DefaultBookId,
            name = "日常账本",
            template = "daily",
            currencyCode = "CNY",
            currencySymbol = "¥",
            coverColor = 0xFF47B972,
            sortOrder = 0,
            createdAtMillis = nowMillis,
            updatedAtMillis = nowMillis,
        ),
        LedgerBookEntity(
            id = TravelBookId,
            name = "旅行账本",
            template = "travel",
            currencyCode = "CNY",
            currencySymbol = "¥",
            coverColor = 0xFF3CB4A5,
            sortOrder = 1,
            createdAtMillis = nowMillis,
            updatedAtMillis = nowMillis,
        ),
        LedgerBookEntity(
            id = SharedBookId,
            name = "家庭账本",
            template = "shared",
            currencyCode = "CNY",
            currencySymbol = "¥",
            coverColor = 0xFF58B16B,
            sortOrder = 2,
            createdAtMillis = nowMillis,
            updatedAtMillis = nowMillis,
        ),
    )

    fun defaultAccounts(nowMillis: Long) = listOf(
        LedgerAccountEntity(
            id = DefaultCashAccountId,
            bookId = DefaultBookId,
            name = "现金",
            type = LedgerAccountType.CASH,
            iconKey = "wallet",
            color = 0xFF4CAF50,
            initialBalanceCents = 0,
            balanceCents = 0,
            includeInTotal = true,
            sortOrder = 0,
            createdAtMillis = nowMillis,
            updatedAtMillis = nowMillis,
        ),
        LedgerAccountEntity(
            id = "account-wechat",
            bookId = DefaultBookId,
            name = "微信余额",
            type = LedgerAccountType.WECHAT,
            iconKey = "wechat",
            color = 0xFF20B15A,
            initialBalanceCents = 0,
            balanceCents = 0,
            includeInTotal = true,
            sortOrder = 1,
            createdAtMillis = nowMillis,
            updatedAtMillis = nowMillis,
        ),
        LedgerAccountEntity(
            id = "account-alipay",
            bookId = DefaultBookId,
            name = "支付宝",
            type = LedgerAccountType.ALIPAY,
            iconKey = "alipay",
            color = 0xFF248BFF,
            initialBalanceCents = 0,
            balanceCents = 0,
            includeInTotal = true,
            sortOrder = 2,
            createdAtMillis = nowMillis,
            updatedAtMillis = nowMillis,
        ),
        LedgerAccountEntity(
            id = "account-icbc-5941",
            bookId = DefaultBookId,
            name = "工商银行储蓄卡(5941)",
            type = LedgerAccountType.DEBIT_CARD,
            iconKey = "wallet",
            color = 0xFFE54D4D,
            initialBalanceCents = 0,
            balanceCents = 0,
            includeInTotal = true,
            sortOrder = 3,
            createdAtMillis = nowMillis,
            updatedAtMillis = nowMillis,
        ),
        LedgerAccountEntity(
            id = "account-travel-cash",
            bookId = TravelBookId,
            name = "旅行现金",
            type = LedgerAccountType.CASH,
            iconKey = "wallet",
            color = 0xFF3CB4A5,
            initialBalanceCents = 120_000,
            balanceCents = 120_000,
            includeInTotal = true,
            sortOrder = 0,
            createdAtMillis = nowMillis,
            updatedAtMillis = nowMillis,
        ),
        LedgerAccountEntity(
            id = "account-travel-card",
            bookId = TravelBookId,
            name = "旅行银行卡",
            type = LedgerAccountType.DEBIT_CARD,
            iconKey = "wallet",
            color = 0xFF2B87D1,
            initialBalanceCents = 50_000,
            balanceCents = 50_000,
            includeInTotal = true,
            sortOrder = 1,
            createdAtMillis = nowMillis,
            updatedAtMillis = nowMillis,
        ),
        LedgerAccountEntity(
            id = "account-home-wechat",
            bookId = SharedBookId,
            name = "家庭微信",
            type = LedgerAccountType.WECHAT,
            iconKey = "wechat",
            color = 0xFF4CAF50,
            initialBalanceCents = 88_800,
            balanceCents = 88_800,
            includeInTotal = true,
            sortOrder = 0,
            createdAtMillis = nowMillis,
            updatedAtMillis = nowMillis,
        ),
        LedgerAccountEntity(
            id = "account-home-cash",
            bookId = SharedBookId,
            name = "家用现金",
            type = LedgerAccountType.CASH,
            iconKey = "wallet",
            color = 0xFFFFB74D,
            initialBalanceCents = 12_800,
            balanceCents = 12_800,
            includeInTotal = true,
            sortOrder = 1,
            createdAtMillis = nowMillis,
            updatedAtMillis = nowMillis,
        ),
    )

    fun defaultCategories(nowMillis: Long): List<LedgerCategoryEntity> {
        return buildCategoriesForBook(
            bookId = DefaultBookId,
            nowMillis = nowMillis,
            expenseSeeds = listOf(
                CategorySeed("cat-general", "一般", "redeem", 0xFFFF8A3D),
                CategorySeed("cat-shopping", "购物", "shopping_bag", 0xFFF8BE2C),
                CategorySeed("cat-food", "餐饮", "restaurant", 0xFF74C97D),
                CategorySeed("cat-daily", "日用", "home", 0xFF24B8E8),
                CategorySeed("cat-traffic", "交通", "directions_car", 0xFF52C8B7),
                CategorySeed("cat-entertainment", "娱乐", "more_horiz", 0xFF7B72E9),
                CategorySeed("cat-travel", "旅行", "flight", 0xFF2F98F2),
                CategorySeed("cat-fitness", "健身", "category", 0xFF27BDE8),
                CategorySeed("cat-beauty", "美容", "redeem", 0xFFB07AF5),
                CategorySeed("cat-study", "学习", "school", 0xFFFF5B3F),
                CategorySeed("cat-gift-expense", "礼物", "shopping_bag", 0xFFFF4D79),
                CategorySeed("cat-gaming", "打牌", "work", 0xFF11B4CA),
                CategorySeed("cat-photo", "摄影", "category", 0xFF19B0C9),
                CategorySeed("cat-office", "办公", "import", 0xFFFFA645),
                CategorySeed("cat-pet", "宠物", "home", 0xFF92B66B),
                CategorySeed("cat-communication", "通讯", "calendar", 0xFF3B78E7),
                CategorySeed("cat-housing", "住房", "asset", 0xFF1FA15B),
                CategorySeed("cat-medical", "医疗", "local_hospital", 0xFFE85A5A),
                CategorySeed("cat-other-expense", "其他", "more_horiz", 0xFF8D99A6),
            ),
            incomeSeeds = listOf(
                CategorySeed("cat-salary", "工资", "payments", 0xFFFF9A3D),
                CategorySeed("cat-part-time", "兼职", "work", 0xFF7FCD72),
                CategorySeed("cat-cards", "打牌", "work", 0xFF3CCD72),
                CategorySeed("cat-finance", "理财", "trending_up", 0xFF25BDE8),
                CategorySeed("cat-gift", "礼金", "redeem", 0xFF57D6C8),
                CategorySeed("cat-other-income", "其他", "more_horiz", 0xFF7B72E9),
                CategorySeed("cat-forex", "外汇", "payments", 0xFF3398EE),
                CategorySeed("cat-stock", "股市", "asset", 0xFFF6BE31),
                CategorySeed("cat-precious", "贵金属", "redeem", 0xFFAA74F3),
                CategorySeed("cat-property", "房产", "home", 0xFFFF643E),
                CategorySeed("cat-saving", "存款", "wallet", 0xFFFFA645),
                CategorySeed("cat-transfer", "转账", "transfer", 0xFFA9D17A),
                CategorySeed("cat-cash-income", "现金", "wallet", 0xFF4B82F5),
                CategorySeed("cat-trade", "交易", "transfer", 0xFF21B96A),
                CategorySeed("cat-collection", "收银", "asset", 0xFF7D94C8),
                CategorySeed("cat-red-packet", "红包", "wallet", 0xFFF0C132),
                CategorySeed("cat-reimburse", "报销", "category", 0xFF28BEDA),
                CategorySeed("cat-borrow-in", "借入", "payments", 0xFFFB991A),
                CategorySeed("cat-tip", "打赏", "redeem", 0xFF8BBC62),
                CategorySeed("cat-refund", "退款", "undo", 0xFF1AA8C5),
            ),
        ) + buildCategoriesForBook(
            bookId = TravelBookId,
            nowMillis = nowMillis,
            expenseSeeds = listOf(
                CategorySeed("travel-ticket", "车票", "flight", 0xFF2F98F2),
                CategorySeed("travel-stay", "住宿", "home", 0xFF24B8E8),
                CategorySeed("travel-food", "餐饮", "restaurant", 0xFF74C97D),
                CategorySeed("travel-play", "游玩", "redeem", 0xFF7B72E9),
                CategorySeed("travel-shopping", "购物", "shopping_bag", 0xFFFF6C8E),
                CategorySeed("travel-other", "其他", "more_horiz", 0xFF8D99A6),
            ),
            incomeSeeds = listOf(
                CategorySeed("travel-refund", "退款", "undo", 0xFF25BDE8),
                CategorySeed("travel-subsidy", "补贴", "payments", 0xFF21B96A),
                CategorySeed("travel-other-income", "其他", "more_horiz", 0xFF8D99A6),
            ),
        ) + buildCategoriesForBook(
            bookId = SharedBookId,
            nowMillis = nowMillis,
            expenseSeeds = listOf(
                CategorySeed("shared-food", "买菜", "restaurant", 0xFF74C97D),
                CategorySeed("shared-home", "家居", "home", 0xFF24B8E8),
                CategorySeed("shared-child", "孩子", "school", 0xFFFF5B3F),
                CategorySeed("shared-traffic", "出行", "directions_car", 0xFF52C8B7),
                CategorySeed("shared-other", "其他", "more_horiz", 0xFF8D99A6),
            ),
            incomeSeeds = listOf(
                CategorySeed("shared-income", "家用", "payments", 0xFF21B96A),
                CategorySeed("shared-refund", "退款", "undo", 0xFF25BDE8),
            ),
        )
    }
}

private data class CategorySeed(
    val id: String,
    val name: String,
    val iconKey: String,
    val color: Long,
) {
    fun toEntity(
        bookId: String,
        type: LedgerCategoryType,
        sortOrder: Int,
        nowMillis: Long,
    ) = LedgerCategoryEntity(
        id = id,
        bookId = bookId,
        name = name,
        iconKey = iconKey,
        color = color,
        type = type,
        sortOrder = sortOrder,
        createdAtMillis = nowMillis,
        updatedAtMillis = nowMillis,
    )
}

private fun buildCategoriesForBook(
    bookId: String,
    nowMillis: Long,
    expenseSeeds: List<CategorySeed>,
    incomeSeeds: List<CategorySeed>,
): List<LedgerCategoryEntity> {
    val expense = expenseSeeds.mapIndexed { index, seed ->
        seed.toEntity(
            bookId = bookId,
            type = LedgerCategoryType.EXPENSE,
            sortOrder = index,
            nowMillis = nowMillis,
        )
    }
    val income = incomeSeeds.mapIndexed { index, seed ->
        seed.toEntity(
            bookId = bookId,
            type = LedgerCategoryType.INCOME,
            sortOrder = index,
            nowMillis = nowMillis,
        )
    }
    return expense + income
}

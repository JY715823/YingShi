package com.example.yingshi.feature.life

/**
 * 生活首页子路由 sealed class。
 * 替代 YingShiApp 中 4 个互斥 boolean 变量，用 when 表达式替代 if-else 链。
 */
sealed class LifeSubRoute {
    object Life : LifeSubRoute()
    object Console : LifeSubRoute()
    object Ledger : LifeSubRoute()
    object LedgerAdd : LifeSubRoute()
    object Chat : LifeSubRoute()
}
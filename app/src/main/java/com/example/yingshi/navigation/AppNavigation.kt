package com.example.yingshi.navigation

enum class RootDestination(
    val label: String,
    val glyph: String,
) {
    HOME(label = "主页", glyph = "主"),
    PHOTOS(label = "照片", glyph = "照"),
    LIFE(label = "生活", glyph = "生"),
}

enum class PhotosTopDestination(
    val label: String,
    val headline: String,
    val supporting: String,
) {
    PHOTOS(
        label = "照片",
        headline = "全局媒体流占位",
        supporting = "照片页只保留全局媒体流的壳层，不接真实媒体、不接真实 Viewer。",
    ),
    ALBUMS(
        label = "相册",
        headline = "相册与帖子目录占位",
        supporting = "后续再接帖子卡片、相册分组和帖子详情。",
    ),
    TRASH(
        label = "回收站",
        headline = "删除与恢复入口占位",
        supporting = "当前只保留信息架构和页面位置，不实现真实回收逻辑。",
    ),
}

package com.example.yingshi.navigation

enum class RootDestination(
    val label: String,
    val glyph: String,
) {
    HOME(label = "首页", glyph = "主"),
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
        headline = "全局媒体流",
        supporting = "按媒体时间浏览 app 内容体系中的去重媒体，不显示帖子卡语义。",
    ),
    ALBUMS(
        label = "相册",
        headline = "相册与帖子目录",
        supporting = "按相册浏览帖子，先建立切换、网格和帖子详情占位入口。",
    ),
    TRASH(
        label = "回收站",
        headline = "删除与恢复入口占位",
        supporting = "当前只保留信息架构和页面位置，不实现真实回收逻辑。",
    ),
}

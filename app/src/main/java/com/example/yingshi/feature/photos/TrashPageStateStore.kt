package com.example.yingshi.feature.photos

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue

/**
 * 回收站列表页全局滚动状态保存。
 *
 * 修复：回收站查看态（TrashDetailScreen）通过顶层 when 分支替换了 PhotosRootScreen，
 * 导致回收站列表页整个 composable 离开组合树，rememberLazyGridState() 持有的滚动位置被丢弃。
 * 使用全局 state store 保存滚动位置，返回时恢复。
 */
class TrashPageStateStore {
    var savedFirstVisibleItemIndex by mutableIntStateOf(0)
    var savedFirstVisibleItemScrollOffset by mutableIntStateOf(0)
}

val GlobalTrashPageStateStore = TrashPageStateStore()

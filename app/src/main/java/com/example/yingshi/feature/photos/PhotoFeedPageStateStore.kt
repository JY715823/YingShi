package com.example.yingshi.feature.photos

object PhotoFeedPageStateStore {
    var pendingScrollTargetMediaId: String? = null
    var pendingScrollAnchorOriginalIndex: Int = -1
    var savedFirstVisibleItemIndex: Int = 0
    var savedFirstVisibleItemScrollOffset: Int = 0
}

package com.example.yingshi.feature.photos

/**
 * 合并本地草稿媒体与远端新增媒体的共享逻辑。
 *
 * 算法：
 * 1. 用 latest 覆盖 current 中同 id 的项（通过 [transformLatest] 转换类型）
 * 2. 新增 latest 中不在 currentIds 且不在 [previousBaselineIds] 的项（防止重新加入已删除的基线项）
 *
 * @param currentItems 当前草稿中的媒体列表（类型 T）
 * @param latestItems 远端最新的媒体列表（类型 U）
 * @param previousBaselineIds 上一轮基线 id 集合，用于过滤已删除的项
 * @param currentIdSelector 从 T 提取 id
 * @param latestIdSelector 从 U 提取 id
 * @param transformLatest 将 U 转换为 T 的函数
 * @return 合并后的 List<T>
 */
internal fun <T, U> mergeMediaDraftWithRemoteAdditions(
    currentItems: List<T>,
    latestItems: List<U>,
    previousBaselineIds: Collection<String>,
    currentIdSelector: (T) -> String,
    latestIdSelector: (U) -> String,
    transformLatest: (U) -> T,
): List<T> {
    val currentIds = currentItems.mapTo(linkedSetOf()) { currentIdSelector(it) }
    val latestById = latestItems.associateBy { latestIdSelector(it) }
    return buildList {
        currentItems.forEach { item ->
            val latest = latestById[currentIdSelector(item)]
            if (latest != null) add(transformLatest(latest)) else add(item)
        }
        latestById.forEach { (id, media) ->
            if (id !in currentIds && id !in previousBaselineIds) {
                add(transformLatest(media))
            }
        }
    }
}

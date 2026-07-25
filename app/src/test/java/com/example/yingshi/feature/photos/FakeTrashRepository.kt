package com.example.yingshi.feature.photos

import com.example.yingshi.data.model.RemotePendingCleanup
import com.example.yingshi.data.model.RemoteTrashDetail
import com.example.yingshi.data.model.RemoteTrashItem
import com.example.yingshi.data.remote.result.ApiResult
import com.example.yingshi.data.repository.TrashRepository

/**
 * Round 4 测试专用 TrashRepository fake 实现。
 *
 * 特性：
 * - 每个方法的返回值可配置（默认 Success 空列表）
 * - 记录每次调用的入参，供测试 verify
 * - 支持按 id 返回不同结果（用于部分失败场景）
 */
internal class FakeTrashRepository : TrashRepository {

    // 默认返回值
    var trashItemsResult: ApiResult<List<RemoteTrashItem>> = ApiResult.Success(emptyList())
    var trashDetailResult: ApiResult<RemoteTrashDetail> = ApiResult.Success(sampleRemoteTrashDetail())
    var restoreResult: ApiResult<RemoteTrashItem> = ApiResult.Success(sampleRemoteTrashItem())
    var moveOutResult: ApiResult<RemotePendingCleanup> = ApiResult.Success(sampleRemotePendingCleanup())
    var purgeResult: ApiResult<RemoteTrashItem> = ApiResult.Success(sampleRemoteTrashItem())
    var undoResult: ApiResult<RemoteTrashItem> = ApiResult.Success(sampleRemoteTrashItem())
    var pendingItemsResult: ApiResult<List<RemotePendingCleanup>> = ApiResult.Success(emptyList())
    var lifeTrashItemsResult: ApiResult<List<RemoteTrashItem>> = ApiResult.Success(emptyList())
    var lifePendingCleanupResult: ApiResult<List<RemotePendingCleanup>> = ApiResult.Success(emptyList())

    // 按 id 返回不同结果（可选，用于部分失败场景）
    var restoreResultsById: Map<String, ApiResult<RemoteTrashItem>> = emptyMap()
    var moveOutResultsById: Map<String, ApiResult<RemotePendingCleanup>> = emptyMap()
    var purgeResultsById: Map<String, ApiResult<RemoteTrashItem>> = emptyMap()

    // 调用记录
    val getTrashItemsCalls = mutableListOf<String?>()
    val getTrashDetailCalls = mutableListOf<String>()
    val restoreCalls = mutableListOf<String>()
    val moveOutCalls = mutableListOf<String>()
    val purgeCalls = mutableListOf<String>()
    val undoCalls = mutableListOf<String>()
    var getPendingCleanupCalls = 0
    val getLifeTrashItemsCalls = mutableListOf<String?>()
    val getLifePendingCleanupCalls = mutableListOf<String?>()

    override suspend fun getTrashItems(type: String?): ApiResult<List<RemoteTrashItem>> {
        getTrashItemsCalls.add(type)
        return trashItemsResult
    }

    override suspend fun getTrashDetail(trashItemId: String): ApiResult<RemoteTrashDetail> {
        getTrashDetailCalls.add(trashItemId)
        return trashDetailResult
    }

    override suspend fun restoreTrashItem(trashItemId: String): ApiResult<RemoteTrashItem> {
        restoreCalls.add(trashItemId)
        return restoreResultsById[trashItemId] ?: restoreResult
    }

    override suspend fun moveTrashItemOut(trashItemId: String): ApiResult<RemotePendingCleanup> {
        moveOutCalls.add(trashItemId)
        return moveOutResultsById[trashItemId] ?: moveOutResult
    }

    override suspend fun purgeTrashItem(trashItemId: String): ApiResult<RemoteTrashItem> {
        purgeCalls.add(trashItemId)
        return purgeResultsById[trashItemId] ?: purgeResult
    }

    override suspend fun undoMoveTrashItemOut(trashItemId: String): ApiResult<RemoteTrashItem> {
        undoCalls.add(trashItemId)
        return undoResult
    }

    override suspend fun getPendingCleanupItems(): ApiResult<List<RemotePendingCleanup>> {
        getPendingCleanupCalls += 1
        return pendingItemsResult
    }

    override suspend fun getLifeTrashItems(category: String?): ApiResult<List<RemoteTrashItem>> {
        getLifeTrashItemsCalls.add(category)
        return lifeTrashItemsResult
    }

    override suspend fun getLifePendingCleanupItems(category: String?): ApiResult<List<RemotePendingCleanup>> {
        getLifePendingCleanupCalls.add(category)
        return lifePendingCleanupResult
    }

    fun reset() {
        getTrashItemsCalls.clear()
        getTrashDetailCalls.clear()
        restoreCalls.clear()
        moveOutCalls.clear()
        purgeCalls.clear()
        undoCalls.clear()
        getPendingCleanupCalls = 0
        getLifeTrashItemsCalls.clear()
        getLifePendingCleanupCalls.clear()
    }
}

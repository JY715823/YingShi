package com.example.yingshi.feature.photos

import com.example.yingshi.data.model.AuthTokens
import com.example.yingshi.data.remote.auth.AuthSessionManager
import com.example.yingshi.data.remote.auth.BackendAutoLoginManager
import com.example.yingshi.data.remote.result.ApiResult
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * Round 4: RealTrashListViewModel 状态流转测试。
 *
 * 覆盖 AC-1 (分类切换/恢复/移出/purge) 的 ViewModel 行为验证。
 *
 * 测试策略：
 * - 使用 FakeTrashRepository 控制 repository 返回值
 * - 使用 AuthSessionManager.saveTokens 模拟已登录状态（绕过 BackendAutoLoginManager 路径）
 * - 不设置 currentUserSnapshot，使 readCachedList/persistTrashList 返回 null（绕过 AppReadCacheStore）
 * - 使用 UnconfinedTestDispatcher 让 viewModelScope 协程立即执行
 */
class RealTrashListViewModelTest {

    @Rule
    @JvmField
    val mainDispatcherRule = MainDispatcherRule(UnconfinedTestDispatcher())

    private lateinit var fakeRepo: FakeTrashRepository

    private fun validTokens() = AuthTokens(
        accessToken = "test-access",
        refreshToken = "test-refresh",
        accessTokenExpireAtMillis = System.currentTimeMillis() + 60_000L,
        refreshTokenExpireAtMillis = System.currentTimeMillis() + 10 * 60_000L,
    )

    @Before
    fun setUp() {
        fakeRepo = FakeTrashRepository()
        AuthSessionManager.clearTokensPreservingReadCache()
        AuthSessionManager.saveTokens(validTokens())
        BackendAutoLoginManager.markLoggedOut("test-setup")
    }

    @After
    fun tearDown() {
        AuthSessionManager.clearTokensPreservingReadCache()
    }

    // ==================== showSelectionMessage ====================

    @Test
    fun showSelectionMessage_updatesStatusAndClearsError() = runTest {
        val viewModel = RealTrashListViewModel(trashRepository = fakeRepo)

        viewModel.showSelectionMessage("自定义选择消息")

        assertEquals("自定义选择消息", viewModel.uiState.value.statusMessage)
        assertNull(viewModel.uiState.value.errorMessage)
    }

    @Test
    fun showSelectionMessage_overwritesPreviousMessage() = runTest {
        val viewModel = RealTrashListViewModel(trashRepository = fakeRepo)

        viewModel.showSelectionMessage("第一条")
        viewModel.showSelectionMessage("第二条")

        assertEquals("第二条", viewModel.uiState.value.statusMessage)
    }

    // ==================== restoreEntries 空列表守卫 ====================

    @Test
    fun restoreEntries_emptyListDoesNothing() = runTest {
        val viewModel = RealTrashListViewModel(trashRepository = fakeRepo)

        viewModel.restoreEntries(emptyList(), null) { }

        assertEquals(false, viewModel.uiState.value.isMutating)
        assertTrue(fakeRepo.restoreCalls.isEmpty())
    }

    // ==================== moveEntriesToPendingCleanup 空列表守卫 ====================

    @Test
    fun moveEntriesToPendingCleanup_emptyListDoesNothing() = runTest {
        val viewModel = RealTrashListViewModel(trashRepository = fakeRepo)

        viewModel.moveEntriesToPendingCleanup(emptyList(), null)

        assertEquals(false, viewModel.uiState.value.isMutating)
        assertTrue(fakeRepo.moveOutCalls.isEmpty())
    }

    // ==================== purgePendingCleanupEntries 空列表守卫 ====================

    @Test
    fun purgePendingCleanupEntries_emptyListDoesNothing() = runTest {
        val viewModel = RealTrashListViewModel(trashRepository = fakeRepo)

        viewModel.purgePendingCleanupEntries(emptyList(), null)

        assertEquals(false, viewModel.uiState.value.isMutating)
        assertTrue(fakeRepo.purgeCalls.isEmpty())
    }

    // ==================== restoreEntries 成功路径 ====================

    @Test
    fun restoreEntries_success_setsStatusMessageAndCallsRepository() = runTest {
        fakeRepo.restoreResult = ApiResult.Success(sampleRemoteTrashItem(trashItemId = "entry-1"))
        fakeRepo.trashItemsResult = ApiResult.Success(emptyList())
        fakeRepo.pendingItemsResult = ApiResult.Success(emptyList())

        val viewModel = RealTrashListViewModel(trashRepository = fakeRepo)

        val entry = sampleTrashEntryUiModel(id = "entry-1")
        var callbackMediaIds: List<String>? = null

        viewModel.restoreEntries(listOf(entry), null) { mediaIds ->
            callbackMediaIds = mediaIds
        }

        // 等待 IO 操作完成
        Thread.sleep(200)

        // 验证 repository 被调用
        assertEquals(listOf("entry-1"), fakeRepo.restoreCalls)
        // 验证状态
        assertEquals(false, viewModel.uiState.value.isMutating)
        assertTrue(viewModel.uiState.value.statusMessage?.contains("已恢复") == true)
        // 验证回调
        assertNotNull(callbackMediaIds)
    }

    // ==================== restoreEntries 失败路径 ====================

    @Test
    fun restoreEntries_error_setsErrorMessage() = runTest {
        fakeRepo.restoreResult = ApiResult.Error(message = "恢复失败")
        fakeRepo.trashItemsResult = ApiResult.Error(message = "读取回收站列表失败。")
        fakeRepo.pendingItemsResult = ApiResult.Success(emptyList())

        val viewModel = RealTrashListViewModel(trashRepository = fakeRepo)

        val entry = sampleTrashEntryUiModel(id = "entry-1")
        viewModel.restoreEntries(listOf(entry), null) { }

        Thread.sleep(200)

        assertEquals(false, viewModel.uiState.value.isMutating)
        assertTrue(viewModel.uiState.value.errorMessage?.isNotBlank() == true)
    }

    // ==================== moveEntriesToPendingCleanup 成功路径 ====================

    @Test
    fun moveEntriesToPendingCleanup_success_setsStatusMessage() = runTest {
        fakeRepo.moveOutResult = ApiResult.Success(sampleRemotePendingCleanup(trashItemId = "entry-1"))
        fakeRepo.trashItemsResult = ApiResult.Success(emptyList())
        fakeRepo.pendingItemsResult = ApiResult.Success(emptyList())

        val viewModel = RealTrashListViewModel(trashRepository = fakeRepo)

        val entry = sampleTrashEntryUiModel(id = "entry-1")
        viewModel.moveEntriesToPendingCleanup(listOf(entry), null)

        Thread.sleep(200)

        assertEquals(listOf("entry-1"), fakeRepo.moveOutCalls)
        assertEquals(false, viewModel.uiState.value.isMutating)
        assertTrue(viewModel.uiState.value.statusMessage?.contains("已移出") == true)
    }

    // ==================== purgePendingCleanupEntries 成功路径 ====================

    @Test
    fun purgePendingCleanupEntries_success_setsStatusMessage() = runTest {
        fakeRepo.purgeResult = ApiResult.Success(sampleRemoteTrashItem(trashItemId = "entry-1"))
        fakeRepo.trashItemsResult = ApiResult.Success(emptyList())
        fakeRepo.pendingItemsResult = ApiResult.Success(emptyList())

        val viewModel = RealTrashListViewModel(trashRepository = fakeRepo)

        val pendingEntry = sampleTrashPendingCleanupUiModel(
            entry = sampleTrashEntryUiModel(id = "entry-1"),
        )
        viewModel.purgePendingCleanupEntries(listOf(pendingEntry), null)

        Thread.sleep(200)

        assertEquals(listOf("entry-1"), fakeRepo.purgeCalls)
        assertEquals(false, viewModel.uiState.value.isMutating)
        assertTrue(viewModel.uiState.value.statusMessage?.contains("已永久删除") == true)
    }

    // ==================== undoPendingCleanup 成功路径 ====================

    @Test
    fun undoPendingCleanup_success_setsStatusMessage() = runTest {
        fakeRepo.undoResult = ApiResult.Success(sampleRemoteTrashItem(trashItemId = "entry-1"))
        fakeRepo.trashItemsResult = ApiResult.Success(emptyList())
        fakeRepo.pendingItemsResult = ApiResult.Success(emptyList())

        val viewModel = RealTrashListViewModel(trashRepository = fakeRepo)

        viewModel.undoPendingCleanup("entry-1", null)

        Thread.sleep(200)

        assertEquals(listOf("entry-1"), fakeRepo.undoCalls)
        assertTrue(viewModel.uiState.value.statusMessage?.contains("已撤销") == true)
    }

    // ==================== refresh 加载列表 ====================

    @Test
    fun refresh_loadsEntriesFromRepository() = runTest {
        val remoteItem1 = sampleRemoteTrashItem(trashItemId = "t1", title = "条目1")
        val remoteItem2 = sampleRemoteTrashItem(trashItemId = "t2", title = "条目2")
        fakeRepo.trashItemsResult = ApiResult.Success(listOf(remoteItem1, remoteItem2))
        fakeRepo.pendingItemsResult = ApiResult.Success(emptyList())

        val viewModel = RealTrashListViewModel(trashRepository = fakeRepo)

        viewModel.refresh(null)

        Thread.sleep(200)

        assertEquals(false, viewModel.uiState.value.isLoading)
        assertEquals(2, viewModel.uiState.value.entries.size)
        assertEquals("t1", viewModel.uiState.value.entries[0].id)
        assertEquals("t2", viewModel.uiState.value.entries[1].id)
    }

    @Test
    fun refresh_deduplicatesByBusinessIdentityKey() = runTest {
        val remoteItem1 = sampleRemoteTrashItem(trashItemId = "t1", title = "相同标题", previewInfo = "相同预览")
        val remoteItem2 = sampleRemoteTrashItem(trashItemId = "t2", title = "相同标题", previewInfo = "相同预览")
        fakeRepo.trashItemsResult = ApiResult.Success(listOf(remoteItem1, remoteItem2))
        fakeRepo.pendingItemsResult = ApiResult.Success(emptyList())

        val viewModel = RealTrashListViewModel(trashRepository = fakeRepo)

        viewModel.refresh(null)

        Thread.sleep(200)

        // 两条 entry 的 businessIdentityKey 相同（同 type/sourcePostId/sourceMediaId/title/previewInfo）
        assertEquals(1, viewModel.uiState.value.entries.size)
    }

    @Test
    fun refresh_error_setsErrorMessage() = runTest {
        fakeRepo.trashItemsResult = ApiResult.Error(message = "网络错误")
        fakeRepo.pendingItemsResult = ApiResult.Success(emptyList())

        val viewModel = RealTrashListViewModel(trashRepository = fakeRepo)

        viewModel.refresh(null)

        Thread.sleep(200)

        assertEquals(false, viewModel.uiState.value.isLoading)
        assertTrue(viewModel.uiState.value.errorMessage?.isNotBlank() == true)
    }
}

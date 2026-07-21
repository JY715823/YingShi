package com.example.yingshi.feature.photos

import com.example.yingshi.data.model.AuthTokens
import com.example.yingshi.data.model.RemoteTrashItem
import com.example.yingshi.data.remote.auth.AuthSessionManager
import com.example.yingshi.data.remote.auth.BackendAutoLoginManager
import com.example.yingshi.data.remote.result.ApiResult
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * Round 4: RealTrashDetailViewModel 状态流转测试。
 *
 * 覆盖 AC-1 的详情页 ViewModel 行为验证（恢复/移出/purge）。
 *
 * 测试策略同 RealTrashListViewModelTest：
 * - FakeTrashRepository 控制返回值
 * - AuthSessionManager.saveTokens 模拟已登录
 * - UnconfinedTestDispatcher 让协程立即执行
 */
class RealTrashDetailViewModelTest {

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

    private fun sampleRoute(entryId: String = "detail-1") = TrashDetailRoute(
        entryId = entryId,
        entryType = TrashEntryType.MEDIA_SYSTEM_DELETED,
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

    // ==================== init 触发 refresh ====================

    @Test
    fun init_triggersRefreshAndLoadsDetail() = runTest {
        fakeRepo.trashDetailResult = ApiResult.Success(
            sampleRemoteTrashDetail(item = sampleRemoteTrashItem(trashItemId = "detail-1")),
        )

        val viewModel = RealTrashDetailViewModel(
            route = sampleRoute("detail-1"),
            trashRepository = fakeRepo,
        )

        Thread.sleep(200)

        assertEquals(false, viewModel.uiState.value.isLoading)
        assertEquals("detail-1", fakeRepo.getTrashDetailCalls.first())
        assertNotNull(viewModel.uiState.value.detail)
    }

    // ==================== restore 成功路径 ====================

    @Test
    fun restore_success_invokesCallbackAndSetsStatusMessage() = runTest {
        fakeRepo.trashDetailResult = ApiResult.Success(sampleRemoteTrashDetail())
        val restoredItem = sampleRemoteTrashItem(trashItemId = "detail-1", state = "restored")
        fakeRepo.restoreResult = ApiResult.Success(restoredItem)

        val viewModel = RealTrashDetailViewModel(
            route = sampleRoute("detail-1"),
            trashRepository = fakeRepo,
        )
        Thread.sleep(200)

        var callbackItem: RemoteTrashItem? = null
        viewModel.restore { item -> callbackItem = item }

        Thread.sleep(200)

        assertEquals(listOf("detail-1"), fakeRepo.restoreCalls)
        assertEquals(restoredItem, callbackItem)
        assertEquals(false, viewModel.uiState.value.isMutating)
        assertTrue(viewModel.uiState.value.statusMessage?.isNotBlank() == true)
    }

    // ==================== remove 成功路径 ====================

    @Test
    fun remove_success_invokesCallbackAndSetsStatusMessage() = runTest {
        fakeRepo.trashDetailResult = ApiResult.Success(sampleRemoteTrashDetail())
        fakeRepo.moveOutResult = ApiResult.Success(sampleRemotePendingCleanup(trashItemId = "detail-1"))

        val viewModel = RealTrashDetailViewModel(
            route = sampleRoute("detail-1"),
            trashRepository = fakeRepo,
        )
        Thread.sleep(200)

        var callbackInvoked = false
        viewModel.remove { callbackInvoked = true }

        Thread.sleep(200)

        assertEquals(listOf("detail-1"), fakeRepo.moveOutCalls)
        assertTrue(callbackInvoked)
        assertEquals(false, viewModel.uiState.value.isMutating)
        assertTrue(viewModel.uiState.value.statusMessage?.contains("移出") == true)
    }

    // ==================== purgePendingCleanup 成功路径 ====================

    @Test
    fun purgePendingCleanup_success_invokesCallbackAndSetsStatusMessage() = runTest {
        fakeRepo.trashDetailResult = ApiResult.Success(sampleRemoteTrashDetail())
        fakeRepo.purgeResult = ApiResult.Success(sampleRemoteTrashItem(trashItemId = "detail-1"))

        val viewModel = RealTrashDetailViewModel(
            route = sampleRoute("detail-1"),
            trashRepository = fakeRepo,
        )
        Thread.sleep(200)

        var callbackInvoked = false
        viewModel.purgePendingCleanup { callbackInvoked = true }

        Thread.sleep(200)

        assertEquals(listOf("detail-1"), fakeRepo.purgeCalls)
        assertTrue(callbackInvoked)
        assertEquals(false, viewModel.uiState.value.isMutating)
        assertTrue(viewModel.uiState.value.statusMessage?.contains("永久删除") == true)
    }

    // ==================== restore error 路径 ====================

    @Test
    fun restore_error_setsErrorMessage() = runTest {
        fakeRepo.trashDetailResult = ApiResult.Success(sampleRemoteTrashDetail())
        fakeRepo.restoreResult = ApiResult.Error(message = "恢复失败")

        val viewModel = RealTrashDetailViewModel(
            route = sampleRoute("detail-1"),
            trashRepository = fakeRepo,
        )
        Thread.sleep(200)

        viewModel.restore { }

        Thread.sleep(200)

        assertEquals(false, viewModel.uiState.value.isMutating)
        assertTrue(viewModel.uiState.value.errorMessage?.isNotBlank() == true)
    }

    // ==================== undoRemove 成功路径 ====================

    @Test
    fun undoRemove_success_setsStatusMessage() = runTest {
        fakeRepo.trashDetailResult = ApiResult.Success(sampleRemoteTrashDetail())
        fakeRepo.undoResult = ApiResult.Success(sampleRemoteTrashItem(trashItemId = "detail-1"))

        val viewModel = RealTrashDetailViewModel(
            route = sampleRoute("detail-1"),
            trashRepository = fakeRepo,
        )
        Thread.sleep(200)

        viewModel.undoRemove()

        Thread.sleep(200)

        assertEquals(listOf("detail-1"), fakeRepo.undoCalls)
        assertTrue(viewModel.uiState.value.statusMessage?.contains("撤销") == true)
    }
}

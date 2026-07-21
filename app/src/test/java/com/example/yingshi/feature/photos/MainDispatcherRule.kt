package com.example.yingshi.feature.photos

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.rules.TestWatcher
import org.junit.runner.Description

/**
 * Round 4 协程测试规则：替换 Dispatchers.Main 为 TestDispatcher。
 *
 * 用法：
 * ```
 * class MyViewModelTest {
 *     @get:Rule val mainDispatcherRule = MainDispatcherRule()
 *
 *     @Test fun `test`() = runTest {
 *         // viewModel 中的 viewModelScope 会使用 TestDispatcher
 *         advanceUntilIdle()
 *     }
 * }
 * ```
 */
class MainDispatcherRule(
    val dispatcher: TestDispatcher = StandardTestDispatcher(),
) : TestWatcher() {
    override fun starting(description: Description) {
        Dispatchers.setMain(dispatcher)
    }

    override fun finished(description: Description) {
        Dispatchers.resetMain()
    }
}

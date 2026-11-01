package com.oft3r.vscode

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LauncherViewModelTest {

    @Test
    fun `when server is immediately reachable transitions to READY without waking VM`() = runTest {
        var vmStarted = false
        val probe = object : ServerProbe {
            override suspend fun isServerReachable(url: String): Boolean = true
        }
        val starter = object : VmStarter {
            override fun startTerminalVm(): Boolean {
                vmStarted = true
                return true
            }
        }
        val dispatcher = StandardTestDispatcher(testScheduler)
        val viewModel = LauncherViewModel(
            probe = probe,
            vmStarter = starter,
            ioDispatcher = dispatcher,
            pollDelayMs = 100L,
            maxAttempts = 5
        )

        viewModel.connectAndLaunch()
        runCurrent()

        assertEquals(ConnectionState.READY, viewModel.uiState.value.connectionState)
        assertEquals(false, vmStarted)
    }

    @Test
    fun `when server is initially unreachable wakes VM and transitions to READY once reachable`() = runTest {
        var callCount = 0
        var vmStarted = false
        val probe = object : ServerProbe {
            override suspend fun isServerReachable(url: String): Boolean {
                callCount++
                return callCount >= 3
            }
        }
        val starter = object : VmStarter {
            override fun startTerminalVm(): Boolean {
                vmStarted = true
                return true
            }
        }
        val dispatcher = StandardTestDispatcher(testScheduler)
        val viewModel = LauncherViewModel(
            probe = probe,
            vmStarter = starter,
            ioDispatcher = dispatcher,
            pollDelayMs = 100L,
            maxAttempts = 10
        )

        viewModel.connectAndLaunch()
        runCurrent()

        assertTrue(vmStarted)
        assertEquals(ConnectionState.WAKING_VM, viewModel.uiState.value.connectionState)

        advanceTimeBy(300L)
        runCurrent()

        assertEquals(ConnectionState.READY, viewModel.uiState.value.connectionState)
    }

    @Test
    fun `when server remains unreachable after max attempts transitions to TIMEOUT`() = runTest {
        val probe = object : ServerProbe {
            override suspend fun isServerReachable(url: String): Boolean = false
        }
        val starter = object : VmStarter {
            override fun startTerminalVm(): Boolean = true
        }
        val dispatcher = StandardTestDispatcher(testScheduler)
        val viewModel = LauncherViewModel(
            probe = probe,
            vmStarter = starter,
            ioDispatcher = dispatcher,
            pollDelayMs = 100L,
            maxAttempts = 3
        )

        viewModel.connectAndLaunch()
        advanceTimeBy(500L)
        runCurrent()

        assertEquals(ConnectionState.TIMEOUT, viewModel.uiState.value.connectionState)
    }
}

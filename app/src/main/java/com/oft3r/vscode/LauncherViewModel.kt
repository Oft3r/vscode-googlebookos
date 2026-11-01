package com.oft3r.vscode

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class LauncherViewModel(
    private val probe: ServerProbe,
    private val vmStarter: VmStarter,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val pollDelayMs: Long = 1200L,
    private val maxAttempts: Int = 35
) : ViewModel() {

    private val _uiState = MutableStateFlow(LauncherUiState())
    val uiState: StateFlow<LauncherUiState> = _uiState.asStateFlow()

    private var connectionJob: Job? = null

    fun connectAndLaunch() {
        connectionJob?.cancel()
        connectionJob = viewModelScope.launch(ioDispatcher) {
            _uiState.update {
                it.copy(
                    connectionState = ConnectionState.CHECKING,
                    attemptCount = 1,
                    statusMessage = "Conectando con el servidor local de VS Code..."
                )
            }

            val url = _uiState.value.serverUrl
            if (probe.isServerReachable(url)) {
                _uiState.update {
                    it.copy(
                        connectionState = ConnectionState.READY,
                        statusMessage = "Servidor de VS Code listo"
                    )
                }
                return@launch
            }

            // Server is not reachable yet -> wake up Terminal VM
            vmStarter.startTerminalVm()
            _uiState.update {
                it.copy(
                    connectionState = ConnectionState.WAKING_VM,
                    statusMessage = "Iniciando el entorno Linux y el servidor de VS Code..."
                )
            }

            for (attempt in 2..maxAttempts) {
                delay(pollDelayMs)
                _uiState.update {
                    it.copy(
                        attemptCount = attempt,
                        statusMessage = "Esperando a que VS Code esté listo (intento $attempt/$maxAttempts)..."
                    )
                }
                if (probe.isServerReachable(url)) {
                    _uiState.update {
                        it.copy(
                            connectionState = ConnectionState.READY,
                            statusMessage = "Servidor de VS Code listo"
                        )
                    }
                    return@launch
                }
            }

            _uiState.update {
                it.copy(
                    connectionState = ConnectionState.TIMEOUT,
                    statusMessage = "El servidor de VS Code tardó en responder. Verifica que la Terminal esté activa y toca Reintentar."
                )
            }
        }
    }

    fun openTerminalManually() {
        vmStarter.startTerminalVm()
    }
}

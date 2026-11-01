package com.oft3r.vscode

import java.net.HttpURLConnection
import java.net.URL

enum class ConnectionState {
    CHECKING,
    WAKING_VM,
    READY,
    TIMEOUT
}

data class LauncherUiState(
    val connectionState: ConnectionState = ConnectionState.CHECKING,
    val serverUrl: String = "http://localhost:18080",
    val attemptCount: Int = 0,
    val statusMessage: String = "Conectando con el servidor local de VS Code..."
)

interface ServerProbe {
    suspend fun isServerReachable(url: String): Boolean
}

interface VmStarter {
    fun startTerminalVm(): Boolean
}

class HttpServerProbe(
    private val timeoutMs: Int = 1500
) : ServerProbe {
    override suspend fun isServerReachable(url: String): Boolean {
        return try {
            val connection = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = timeoutMs
                readTimeout = timeoutMs
                requestMethod = "GET"
                instanceFollowRedirects = false
            }
            val code = connection.responseCode
            connection.disconnect()
            code in 200..499
        } catch (e: Exception) {
            false
        }
    }
}

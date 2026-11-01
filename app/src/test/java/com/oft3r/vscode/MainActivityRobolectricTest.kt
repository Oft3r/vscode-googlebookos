package com.oft3r.vscode

import java.net.ServerSocket
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MainActivityRobolectricTest {

    @Test
    fun `HttpServerProbe detects live local server and closed port accurately`() = runBlocking {
        val probe = HttpServerProbe(timeoutMs = 500)
        // Closed ephemeral port should return false
        assertFalse(probe.isServerReachable("http://127.0.0.1:59999"))

        // Start a minimal HTTP server on an ephemeral port
        ServerSocket(0).use { server ->
            val port = server.localPort
            val serverThread = Thread {
                try {
                    server.accept().use { client ->
                        client.getOutputStream().write(
                            "HTTP/1.1 200 OK\r\nContent-Length: 2\r\n\r\nOK".toByteArray()
                        )
                        client.getOutputStream().flush()
                    }
                } catch (_: Exception) {
                }
            }
            serverThread.start()

            assertTrue(probe.isServerReachable("http://127.0.0.1:$port"))
            serverThread.join(1000)
        }
    }
}

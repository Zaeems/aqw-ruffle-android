package rs.ruffle

import java.net.ServerSocket
import java.net.Socket
import kotlin.concurrent.thread
import android.util.Log

class AqwBridge(val localPort: Int, val targetHost: String, val targetPort: Int) {
    private var serverSocket: ServerSocket? = null
    private var running = false

    fun start() {
        running = true
        thread {
            try {
                serverSocket = ServerSocket(localPort)
                while (running) {
                    val client = serverSocket?.accept()
                    if (client != null) {
                        handleConnection(client)
                    }
                }
            } catch (e: Exception) {
                Log.e("AQW", "Server Error: ${e.message}")
            }
        }
    }

    private fun handleConnection(client: Socket) {
        thread {
            try {
                val inputStream = client.getInputStream()
                val outputStream = client.getOutputStream()
                val buffer = ByteArray(1024)
                val read = inputStream.read(buffer)
                if (read == -1) return@thread
                val request = String(buffer, 0, read)

                if (request.contains("<policy-file-request/>")) {
                    val policy = "<?xml version=\"1.0\"?><cross-domain-policy><allow-access-from domain=\"*\" to-ports=\"*\" /></cross-domain-policy>\u0000"
                    outputStream.write(policy.toByteArray())
                    outputStream.flush()
                    client.close()
                } else {
                    val target = Socket(targetHost, targetPort)
                    target.getOutputStream().write(buffer, 0, read) // Forward first packet
                    thread { try { inputStream.copyTo(target.getOutputStream()) } catch(e: Exception){} }
                    thread { try { target.getInputStream().copyTo(outputStream) } catch(e: Exception){} }
                }
            } catch (e: Exception) { client.close() }
        }
    }

    fun stop() {
        running = false
        serverSocket?.close()
    }
}
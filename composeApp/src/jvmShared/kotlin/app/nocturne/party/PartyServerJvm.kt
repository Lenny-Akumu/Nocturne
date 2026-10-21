package app.nocturne.party

import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.ServerSocket
import kotlin.concurrent.thread

actual fun startPartyServer(state: () -> String): PartyServer {
    val socket = ServerSocket(0)
    thread(isDaemon = true, name = "party-server") {
        while (!socket.isClosed) {
            val client = runCatching { socket.accept() }.getOrNull() ?: break
            thread(isDaemon = true) {
                client.use { c ->
                    runCatching {
                        c.soTimeout = 4000
                        c.getInputStream().bufferedReader().readLine()
                        val body = state().toByteArray()
                        c.getOutputStream().apply {
                            write("HTTP/1.1 200 OK\r\nContent-Type: application/json; charset=utf-8\r\nAccess-Control-Allow-Origin: *\r\nContent-Length: ${body.size}\r\nConnection: close\r\n\r\n".toByteArray())
                            write(body)
                            flush()
                        }
                    }
                }
            }
        }
    }
    return object : PartyServer {
        override val port = socket.localPort
        override fun stop() = runCatching { socket.close() }.let { }
    }
}

actual fun lanAddresses(): List<String> =
    NetworkInterface.getNetworkInterfaces().toList()
        .filter { runCatching { it.isUp && !it.isLoopback }.getOrDefault(false) }
        .sortedBy { n -> val d = n.displayName.lowercase(); if (listOf("virtual", "vethernet", "vmware", "hyper-v", "wsl", "vpn", "tap", "rmnet", "dummy").any { it in d }) 1 else 0 }
        .flatMap { it.inetAddresses.toList() }
        .filterIsInstance<Inet4Address>()
        .filter { it.isSiteLocalAddress }
        .map { it.hostAddress }
        .distinct()
        .take(4)

package io.github.amitsidhpura.claudebrains.bridge

import java.net.InetAddress
import java.net.ServerSocket

/** Mirrors the extension: random port in 10000-65535, probe up to 50 times. */
object PortFinder {
    private const val MIN = 10_000
    private const val RANGE = 55_536 // 65535 - 10000 + 1

    fun findFree(): Int {
        val loopback = InetAddress.getByName("127.0.0.1")
        repeat(50) {
            val port = MIN + (Math.random() * RANGE).toInt()
            runCatching {
                ServerSocket(port, 1, loopback).use { return port }
            }
        }
        error("Failed to find an available port after multiple attempts")
    }
}

package com.syncroze.claudecode.bridge

import com.intellij.openapi.diagnostic.Logger
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.java_websocket.WebSocket
import org.java_websocket.handshake.ClientHandshake
import org.java_websocket.server.WebSocketServer
import java.net.InetSocketAddress
import java.util.concurrent.Executors

/**
 * IDE side of the Claude Code bridge: an MCP server over WebSocket, loopback only.
 *
 * The CLI connects and must present header `x-claude-code-ide-authorization: <authToken>`.
 * We answer standard MCP JSON-RPC 2.0: initialize / tools/list / tools/call, delegating
 * tool calls to [IdeTools]. Single client, like the reference host.
 */
class IdeMcpServer(
    port: Int,
    private val authToken: String,
    private val ideName: String,
    private val tools: IdeTools,
) : WebSocketServer(InetSocketAddress("127.0.0.1", port)) {

    private val log = Logger.getInstance(IdeMcpServer::class.java)
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val toolExecutor = Executors.newCachedThreadPool { r ->
        Thread(r, "claude-ide-tool").apply { isDaemon = true }
    }

    @Volatile
    private var active: WebSocket? = null

    override fun onStart() {
        connectionLostTimeout = 0
        log.info("IDE-MCP server listening on 127.0.0.1 (localhost only)")
    }

    /** Stop the server and the tool executor. */
    fun shutdown() {
        runCatching { toolExecutor.shutdownNow() }
        runCatching { stop() }
    }

    override fun onOpen(conn: WebSocket, handshake: ClientHandshake) {
        val presented = handshake.getFieldValue("x-claude-code-ide-authorization")
        if (presented != authToken) {
            log.warn("Unauthorized WebSocket connection attempt")
            conn.close(1008, "Unauthorized")
            return
        }
        active?.let { runCatching { it.close() } } // keep a single client
        active = conn
        log.info("Claude CLI connected")
    }

    override fun onClose(conn: WebSocket, code: Int, reason: String?, remote: Boolean) {
        if (conn == active) active = null
    }

    override fun onError(conn: WebSocket?, ex: Exception) {
        log.warn("IDE-MCP socket error", ex)
    }

    override fun onMessage(conn: WebSocket, message: String) {
        val req = runCatching { json.parseToJsonElement(message).jsonObject }.getOrNull() ?: return
        val id: JsonElement = req["id"] ?: JsonNull
        val method = req["method"]?.jsonPrimitive?.content ?: return

        val response: JsonObject? = when (method) {
            "initialize" -> result(id, buildJsonObject {
                put("protocolVersion", "2024-11-05")
                put("serverInfo", buildJsonObject {
                    put("name", "Claude Code $ideName MCP")
                    put("version", "0.1.0")
                })
                put("capabilities", buildJsonObject { put("tools", buildJsonObject {}) })
            })

            "tools/list" -> result(id, buildJsonObject {
                put("tools", buildJsonArray { IdeTools.SPECS.forEach { add(it) } })
            })

            "tools/call" -> {
                val params = req["params"]?.jsonObject
                val name = params?.get("name")?.jsonPrimitive?.content
                val args = params?.get("arguments")?.jsonObject ?: JsonObject(emptyMap())
                // Dispatch async: a tool (e.g. openDiff) may block on user input; must not stall the socket.
                toolExecutor.submit {
                    val resp = runCatching { tools.call(name.orEmpty(), args) }.fold(
                        onSuccess = { r ->
                            result(id, buildJsonObject {
                                put("content", buildJsonArray {
                                    r.texts.forEach { t ->
                                        add(buildJsonObject { put("type", "text"); put("text", t) })
                                    }
                                })
                            })
                        },
                        onFailure = { e -> error(id, -32603, e.message ?: "tool error") },
                    )
                    runCatching { conn.send(json.encodeToString(JsonObject.serializer(), resp)) }
                }
                null // response is sent from the executor
            }

            // notifications (no id) — nothing to return
            else -> if (id is JsonNull) null else error(id, -32601, "Method not found: $method")
        }

        response?.let { conn.send(json.encodeToString(JsonObject.serializer(), it)) }
    }

    private fun result(id: JsonElement, res: JsonObject) = buildJsonObject {
        put("jsonrpc", "2.0"); put("id", id); put("result", res)
    }

    private fun error(id: JsonElement, code: Int, msg: String) = buildJsonObject {
        put("jsonrpc", "2.0"); put("id", id)
        put("error", buildJsonObject { put("code", code); put("message", msg) })
    }
}

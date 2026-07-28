package com.syncroze.claudecode.cli

import com.intellij.openapi.diagnostic.Logger
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.io.File
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets
import kotlin.concurrent.thread

/**
 * Spawns and drives the official `claude` CLI in streaming mode.
 *
 * Two channels over stream-json:
 *  - conversation: `assistant`/`stream_event`/`result` lines -> [onEvent]; user turns on stdin.
 *  - control protocol: the CLI asks permission via `control_request{subtype:can_use_tool}`;
 *    we surface it through [onPermission] and answer with `control_response` on stdin.
 *
 * Permission routing is enabled by `--permission-prompt-tool stdio` (the SDK sentinel that sends
 * permission requests over stdio) plus `--permission-mode`.
 */
class ClaudeCli(
    private val workingDir: File,
    private val ssePort: Int,
    private val executable: String,
    private val permissionMode: String,
    private val resumeSessionId: String? = null,
    private val onEvent: (String) -> Unit,
    private val onPermission: (requestId: String, toolName: String, input: JsonObject) -> Unit,
    private val onInit: (commandsJson: String) -> Unit,
    private val onExit: (Int) -> Unit,
) {
    private val log = Logger.getInstance(ClaudeCli::class.java)

    /**
     * Transcript the CLI is currently writing (`~/.claude/projects/<enc-cwd>/<id>.jsonl`), so the UI
     * can refuse to delete it out from under a live session. Known immediately when resuming; for a
     * fresh session the CLI assigns the id, so it stays null until an event carries it.
     */
    @Volatile
    var sessionId: String? = resumeSessionId
        private set

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private var process: Process? = null
    private var stdin: OutputStreamWriter? = null

    @Volatile
    private var stopped = false

    fun start() {
        val cmd = mutableListOf(
            executable,
            "--input-format", "stream-json",
            "--output-format", "stream-json",
            "--include-partial-messages",
            "--verbose",
            "--permission-prompt-tool", "stdio",
            "--permission-mode", permissionMode,
        )
        if (resumeSessionId != null) {
            cmd += listOf("--resume", resumeSessionId)
        }
        val pb = ProcessBuilder(cmd).directory(workingDir).redirectErrorStream(false)
        pb.environment().apply {
            put("CLAUDE_CODE_SSE_PORT", ssePort.toString())
            put("CLAUDE_CODE_ENTRYPOINT", "phpstorm-syncroze")
            put("CLAUDE_CODE_ENABLE_SDK_FILE_CHECKPOINTING", "1") // enables rewind_files
            gitBashPath()?.let { put("CLAUDE_CODE_GIT_BASH_PATH", it) }
        }

        val p = pb.start().also { process = it }
        stdin = OutputStreamWriter(p.outputStream, StandardCharsets.UTF_8)

        thread(name = "claude-stdout", isDaemon = true) {
            p.inputStream.bufferedReader(StandardCharsets.UTF_8).useLines { lines ->
                lines.forEach { line -> if (line.isNotBlank()) runCatching { route(line) } }
            }
        }
        thread(name = "claude-stderr", isDaemon = true) {
            p.errorStream.bufferedReader(StandardCharsets.UTF_8).forEachLine { log.info("[claude] $it") }
        }
        thread(name = "claude-wait", isDaemon = true) {
            val code = p.waitFor()
            log.info("claude exited: $code")
            if (!stopped) onExit(code) // suppress when we intentionally restarted/stopped
        }

        // Ask for metadata (slash commands, models, account) up front — the CLI doesn't emit
        // its `init` until the first user turn, but the initialize control request returns it now.
        sendInitialize()
    }

    private fun sendInitialize() {
        val line = json.encodeToString(JsonObject.serializer(), buildJsonObject {
            put("type", "control_request")
            put("request_id", INIT_REQ_ID)
            put("request", buildJsonObject { put("subtype", "initialize") })
        })
        writeLine(line)
    }

    /** Split control-protocol frames from conversation events. */
    private fun route(line: String) {
        val obj = runCatching { json.parseToJsonElement(line).jsonObject }.getOrNull()
        when (obj?.get("type")?.jsonPrimitive?.content) {
            "control_request" -> handleControlRequest(obj!!)
            "control_response" -> handleControlResponse(obj!!)
            else -> {
                // conversation events carry the id of the transcript being written; accept either
                // spelling since the stream and the on-disk records disagree (session_id/sessionId)
                (obj?.get("session_id") ?: obj?.get("sessionId"))?.jsonPrimitive?.content
                    ?.takeIf { it.isNotBlank() }
                    ?.let { if (it != sessionId) { sessionId = it; log.info("live session $it") } }
                onEvent(line)
            }
        }
    }

    private fun handleControlResponse(obj: JsonObject) {
        val resp = obj["response"]?.jsonObject ?: return
        val reqId = resp["request_id"]?.jsonPrimitive?.content
        val result = resp["response"]
        when {
            reqId == INIT_REQ_ID ->
                resp["response"]?.jsonObject?.let { onInit(it.toString()) } // { commands, models, account, ... }
            reqId?.startsWith("rewinddry-") == true && result != null ->
                onEvent(json.encodeToString(JsonObject.serializer(), buildJsonObject {
                    put("type", "__rewind_check"); put("id", reqId.removePrefix("rewinddry-")); put("result", result)
                }))
            reqId?.startsWith("rewind-") == true && result != null ->
                onEvent(json.encodeToString(JsonObject.serializer(), buildJsonObject {
                    put("type", "__rewind"); put("result", result)
                }))
        }
    }

    private fun handleControlRequest(obj: JsonObject) {
        val reqId = obj["request_id"]?.jsonPrimitive?.content ?: return
        val request = obj["request"]?.jsonObject
        when (request?.get("subtype")?.jsonPrimitive?.content) {
            "can_use_tool" -> {
                val tool = request["tool_name"]?.jsonPrimitive?.content ?: "tool"
                val input = request["input"]?.jsonObject ?: JsonObject(emptyMap())
                onPermission(reqId, tool, input)
            }
            // Anything else we don't implement (hooks, sdk mcp): acknowledge so the CLI won't hang.
            else -> writeControlResponse(reqId, buildJsonObject {})
        }
    }

    /** Answer a can_use_tool request. allow=true applies the tool; false rejects it. */
    fun respondPermission(requestId: String, allow: Boolean, input: JsonObject) {
        val response = if (allow) {
            buildJsonObject {
                put("behavior", "allow")
                put("updatedInput", input)
            }
        } else {
            buildJsonObject {
                put("behavior", "deny")
                put("message", "User rejected the change in the IDE")
            }
        }
        writeControlResponse(requestId, response)
    }

    private fun writeControlResponse(requestId: String, response: JsonObject) {
        val line = json.encodeToString(JsonObject.serializer(), buildJsonObject {
            put("type", "control_response")
            put("response", buildJsonObject {
                put("subtype", "success")
                put("request_id", requestId)
                put("response", response)
            })
        })
        writeLine(line)
    }

    /** Host-initiated: change the permission mode (default | acceptEdits | plan | bypassPermissions). */
    fun setPermissionMode(mode: String) = sendControlRequest(buildJsonObject {
        put("subtype", "set_permission_mode")
        put("mode", mode)
    })

    /** Host-initiated: interrupt the in-flight response. */
    fun interrupt() = sendControlRequest(buildJsonObject { put("subtype", "interrupt") })

    /** Host-initiated: switch the model for this session (e.g. "sonnet", "opus[1m]", "default"). */
    fun setModel(model: String) = sendControlRequest(buildJsonObject {
        put("subtype", "set_model")
        put("model", model)
    })

    /**
     * Check (dryRun=true) or perform (dryRun=false) reverting file edits since [userMessageId].
     * Dry-run result arrives as `__rewind_check`, actual as `__rewind`.
     */
    fun rewindFiles(userMessageId: String, dryRun: Boolean) {
        val prefix = if (dryRun) "rewinddry-" else "rewind-"
        val line = json.encodeToString(JsonObject.serializer(), buildJsonObject {
            put("type", "control_request")
            put("request_id", "$prefix$userMessageId")
            put("request", buildJsonObject {
                put("subtype", "rewind_files")
                put("user_message_id", userMessageId)
                put("dry_run", dryRun)
            })
        })
        writeLine(line)
    }

    private fun sendControlRequest(request: JsonObject) {
        val line = json.encodeToString(JsonObject.serializer(), buildJsonObject {
            put("type", "control_request")
            put("request_id", java.util.UUID.randomUUID().toString())
            put("request", request)
        })
        writeLine(line)
    }

    /** Send a text-only user turn. */
    fun sendUserText(text: String) = sendUserMessage(text, emptyList(), null)

    /** Send a user turn with optional images and an optional message id (for rewind). */
    fun sendUserMessage(text: String, images: List<Pair<String, String>>, messageId: String? = null) {
        val line = json.encodeToString(JsonObject.serializer(), buildJsonObject {
            put("type", "user")
            messageId?.let { put("uuid", it) }
            put("message", buildJsonObject {
                put("role", "user")
                put("content", buildJsonArray {
                    if (text.isNotBlank()) {
                        add(buildJsonObject { put("type", "text"); put("text", text) })
                    }
                    images.forEach { (mediaType, data) ->
                        add(buildJsonObject {
                            put("type", "image")
                            put("source", buildJsonObject {
                                put("type", "base64")
                                put("media_type", mediaType)
                                put("data", data)
                            })
                        })
                    }
                })
            })
        })
        writeLine(line)
    }

    private fun writeLine(line: String) {
        val w = stdin ?: return
        synchronized(w) { w.write(line); w.write("\n"); w.flush() }
    }

    fun stop() {
        stopped = true
        runCatching { stdin?.close() }
        process?.destroy()
    }

    private companion object {
        const val INIT_REQ_ID = "sdk-init"
    }

    private fun gitBashPath(): String? {
        if (!System.getProperty("os.name").startsWith("Windows")) return null
        return listOf(
            "C:/Program Files/Git/bin/bash.exe",
            "C:/Program Files (x86)/Git/bin/bash.exe",
        ).firstOrNull { File(it).exists() }
    }
}

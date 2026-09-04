package io.github.amitsidhpura.claudebrains.cli

import com.intellij.openapi.diagnostic.Logger
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.io.File
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets
import java.util.Base64
import kotlin.concurrent.thread

/**
 * A user-message attachment. [kind] is "image" | "pdf" | "text"; [data] is base64 (images/pdf) or,
 * for text, still base64 as received from the webview — decoded to raw text when the block is built.
 */
data class Attachment(val kind: String, val mediaType: String, val data: String, val name: String)

/**
 * Spawns and drives the official `claude` CLI in streaming mode.
 *
 * Two channels over stream-json:
 *  - conversation: `assistant`/`stream_event`/`result` lines -> [onEvent]; user turns on stdin.
 *  - control protocol: the CLI asks permission via `control_request{subtype:can_use_tool}`;
 *    we surface it through [onPermission] and answer with `control_response` on stdin.
 *
 * Permission routing is enabled by `--permission-prompt-tool stdio` (the SDK sentinel that sends
 * permission requests over stdio) plus `--permission-mode` — only when the user has picked a mode;
 * without the flag the CLI applies its own precedence (`permissions.defaultMode` from the settings
 * files, else its default), and the flag beats the file (measured 2026-09-04, checklist 4.7).
 *
 * A third lane rides the same control channel: host-registered HOOKS. The `initialize` request
 * declares them (`hooks: {PreToolUse: [{matcher, hookCallbackIds}]}` — the SDK's own shape, read
 * from the 2.1.233 binary), and the CLI then blocks each matching tool call on a
 * `control_request{subtype:hook_callback, callback_id, input, tool_use_id}` until we answer.
 * [onHook] receives them; the answer is a hook JSON output (`{continue:true}` for "carry on").
 */
class ClaudeCli(
    private val workingDir: File,
    private val ssePort: Int,
    private val authToken: String,
    private val executable: String,
    private val permissionMode: String?,
    private val resumeSessionId: String? = null,
    private val onEvent: (String) -> Unit,
    private val onPermission: (requestId: String, toolName: String, input: JsonObject, suggestions: JsonArray?, reason: String?) -> Unit,
    private val onInit: (commandsJson: String) -> Unit,
    private val onExit: (Int) -> Unit,
    /** A declared hook fired: [respond] MUST be called (once) or the CLI stalls until its timeout. */
    private val onHook: (callbackId: String, input: JsonObject, respond: (JsonObject) -> Unit) -> Unit =
        { _, _, respond -> respond(buildJsonObject { put("continue", true) }) },
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

    /** Temp file backing `--mcp-config`; deleted when the process is stopped. */
    private var mcpConfigFile: File? = null

    @Volatile
    private var stopped = false

    /**
     * Last few stderr lines, for reporting WHY the CLI died. Bounded because a crashing process can
     * print without limit, and only the tail explains the exit anyway.
     */
    private val stderrTail = ArrayDeque<String>()
    /** Recent stderr, newest last — empty when the process said nothing. */
    fun stderrTail(): List<String> = synchronized(stderrTail) { stderrTail.toList() }

    @Volatile
    private var sawFrame = false
    /**
     * Whether this process ever produced a parseable stream-json frame. False on a non-zero exit
     * means it died at startup — argument parsing, in practice an old CLI rejecting a newer flag
     * vocabulary — which is when the panel may fairly suggest updating.
     */
    fun sawFrame(): Boolean = sawFrame

    /** `--mcp-config` JSON pointing the CLI at the plugin's MCP-over-WebSocket bridge. */
    private fun ideMcpConfig(): String {
        val cfg = buildJsonObject {
            put("mcpServers", buildJsonObject {
                put("ide", buildJsonObject {
                    put("type", "ws")
                    put("url", "ws://127.0.0.1:$ssePort")
                    put("headers", buildJsonObject {
                        put("x-claude-code-ide-authorization", authToken)
                    })
                })
            })
        }
        return json.encodeToString(JsonObject.serializer(), cfg)
    }

    /**
     * Spill the MCP config to a temp file and hand the CLI its path.
     *
     * `--mcp-config` also accepts the JSON inline, but not on Windows: ProcessBuilder joins argv
     * into one command line and only quotes arguments containing whitespace. This JSON has none,
     * so it goes through raw and the CLI's C-runtime argument parser eats every `"` — the CLI then
     * sees `{mcpServers:{ide:...}}`, decides it must be a filename, and exits 1 with
     * "MCP config file not found". A path has no quotes to lose, and works identically on POSIX.
     */
    private fun writeMcpConfig(): File =
        File.createTempFile("claude-brains-mcp", ".json").apply {
            deleteOnExit() // backstop if stop() never runs (IDE killed)
            writeText(ideMcpConfig(), StandardCharsets.UTF_8)
            mcpConfigFile = this
        }

    fun start() {
        val cmd = mutableListOf(
            executable,
            "--input-format", "stream-json",
            "--output-format", "stream-json",
            "--include-partial-messages",
            "--verbose",
            "--permission-prompt-tool", "stdio",
            // Expose the IDE bridge to the model. In stream-json mode the CLI never auto-connects
            // from CLAUDE_CODE_SSE_PORT (that discovery only runs in the interactive TUI), so the
            // server must be passed explicitly. Plain "ws" transport (the "ws-ide" type is filtered
            // as internal-only from user config); the CLI requests WebSocket subprotocol "mcp",
            // which IdeMcpServer advertises. Tools surface to the model as mcp__ide__<name>.
            "--mcp-config", writeMcpConfig().absolutePath,
        )
        if (resumeSessionId != null) {
            cmd += listOf("--resume", resumeSessionId)
        }
        // Omitted when nothing is persisted (see the class doc): the CLI then honours the user's
        // own `permissions.defaultMode`, which the flag would otherwise silently beat.
        if (permissionMode != null) {
            cmd += listOf("--permission-mode", permissionMode)
        }
        val pb = ProcessBuilder(cmd).directory(workingDir).redirectErrorStream(false)
        pb.environment().apply {
            // A desktop-launched IDE carries the bare GUI-session environment — no nvm/pyenv/…
            // PATH additions from the user's shell init — so anything the CLI spawns through
            // them dies with ENOENT while the identical config works in a terminal (measured
            // 2026-08-09: `npx @playwright/mcp` failed under the IDE, fine in zsh; npx lived
            // only in nvm's shell-added dir). Two overlays, shell values winning, our keys
            // last: EnvironmentUtil is the platform's shell snapshot but loads it ONLY on
            // macOS (shouldLoadShellEnv() bytecode: `if (!isMac) return false` — verified on
            // 242 AND 262, which is why the first fix attempt was a no-op on Linux); ShellEnv
            // is our own capture covering Linux (empty on Windows — no shell PATH layer there).
            putAll(com.intellij.util.EnvironmentUtil.getEnvironmentMap())
            putAll(io.github.amitsidhpura.claudebrains.ShellEnv.get())
            put("CLAUDE_CODE_SSE_PORT", ssePort.toString())
            put("CLAUDE_CODE_ENTRYPOINT", "phpstorm-claude-brains")
            gitBashPath()?.let { put("CLAUDE_CODE_GIT_BASH_PATH", it) }
        }

        val p = pb.start().also { process = it }
        stdin = OutputStreamWriter(p.outputStream, StandardCharsets.UTF_8)

        val outThread = thread(name = "claude-stdout", isDaemon = true) {
            p.inputStream.bufferedReader(StandardCharsets.UTF_8).useLines { lines ->
                lines.forEach { line ->
                    if (line.isNotBlank()) runCatching { route(line) }
                        .onFailure { log.warn("dropped stream line: ${line.take(200)}", it) }
                }
            }
        }
        val errThread = thread(name = "claude-stderr", isDaemon = true) {
            p.errorStream.bufferedReader(StandardCharsets.UTF_8).forEachLine {
                log.info("[claude] $it")
                // Keep the tail so a non-zero exit can SAY why. The panel used to show
                // "claude process exited (1)" with the reason only in idea.log, which a normal user
                // never opens — and unlike everything else, there is no terminal to check, because
                // this session's CLI is the one that just died.
                synchronized(stderrTail) {
                    stderrTail.addLast(it)
                    while (stderrTail.size > STDERR_TAIL) stderrTail.removeFirst()
                }
            }
        }
        thread(name = "claude-wait", isDaemon = true) {
            val code = p.waitFor()
            // The pipes can still hold the death note: a process failing at argument parsing
            // writes stderr and exits faster than the reader threads run (measured 2026-09-05
            // with a stub CLI — the exit reported with stderrTail still empty, so the ERR box
            // never rendered). Draining first also settles sawFrame before the exit frame reads
            // it. Bounded, so a wedged pipe cannot hang the exit report.
            runCatching { outThread.join(1000); errThread.join(1000) }
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
            put("request", buildJsonObject {
                put("subtype", "initialize")
                // The autosave hook, registered exactly as the VS Code host registers its own
                // (`PreToolUse Edit|Write|Read → saveFileIfNeeded`, extension.js 2.1.233; we add
                // MultiEdit, which takes the same file_path). The CLI validates this shape strictly:
                // an object of hook events → arrays of {matcher?, hookCallbackIds[], timeout?}.
                put("hooks", buildJsonObject {
                    put("PreToolUse", buildJsonArray {
                        add(buildJsonObject {
                            put("matcher", "Edit|Write|MultiEdit|Read")
                            put("hookCallbackIds", buildJsonArray { add(JsonPrimitive(HOOK_AUTOSAVE)) })
                        })
                    })
                })
            })
        })
        writeLine(line)
    }

    /** Split control-protocol frames from conversation events. */
    private fun route(line: String) {
        val obj = runCatching { json.parseToJsonElement(line).jsonObject }.getOrNull()
        if (obj != null) sawFrame = true
        when (obj?.get("type")?.jsonPrimitive?.content) {
            "control_request" -> handleControlRequest(obj!!)
            "control_response" -> handleControlResponse(obj!!)
            else -> {
                // conversation events carry the id of the transcript being written; accept either
                // spelling since the stream and the on-disk records disagree (session_id/sessionId)
                (obj?.get("session_id") ?: obj?.get("sessionId"))?.jsonPrimitive?.content
                    ?.takeIf { it.isNotBlank() }
                    ?.let { if (it != sessionId) { sessionId = it; log.info("live session $it") } }
                // Same idiom as `if (!stopped) onExit(code)`: a restart pushes __clear before the
                // old process dies, so a frame still buffered in this reader could land in the NEW
                // conversation's log. Worse than a stray block: a stale message_start would set the
                // webview busy with no result ever coming to clear it.
                if (!stopped) onEvent(line)
            }
        }
    }

    /**
     * Host requests that want their answer back, keyed by request_id. Everything else the host
     * sends is fire-and-forget (set_model, interrupt, …) and only its error is surfaced. A
     * side question is the first request whose SUCCESS carries the payload the user is waiting
     * for, so its callback is registered before the line is written and consumed exactly once.
     */
    private val pending = java.util.concurrent.ConcurrentHashMap<String, (response: JsonObject?, error: String?) -> Unit>()

    private fun handleControlResponse(obj: JsonObject) {
        val resp = obj["response"]?.jsonObject ?: return
        val reqId = resp["request_id"]?.jsonPrimitive?.content
        reqId?.let { pending.remove(it) }?.let { cb ->
            if (resp["subtype"]?.jsonPrimitive?.content == "error")
                cb(null, resp["error"]?.jsonPrimitive?.content ?: "control request failed")
            else cb(resp["response"]?.jsonObject ?: JsonObject(emptyMap()), null)
            return
        }
        // A refused host request (e.g. set_permission_mode the CLI won't honour) must reach the
        // user — swallowing it once left the mode chip lying about Auto mode.
        if (resp["subtype"]?.jsonPrimitive?.content == "error") {
            val msg = resp["error"]?.jsonPrimitive?.content ?: "control request failed"
            log.warn("control error ($reqId): $msg")
            onEvent(json.encodeToString(JsonObject.serializer(), buildJsonObject {
                put("type", "__ctl_error"); put("error", msg)
            }))
            return
        }
        when {
            reqId == INIT_REQ_ID ->
                resp["response"]?.jsonObject?.let { onInit(it.toString()) } // { commands, models, account, ... }
        }
    }

    private fun handleControlRequest(obj: JsonObject) {
        val reqId = obj["request_id"]?.jsonPrimitive?.content ?: return
        val request = obj["request"]?.jsonObject
        when (request?.get("subtype")?.jsonPrimitive?.content) {
            "can_use_tool" -> {
                val tool = request["tool_name"]?.jsonPrimitive?.content ?: "tool"
                val input = request["input"]?.jsonObject ?: JsonObject(emptyMap())
                // Ready-made "don't ask again" options the CLI computed for this exact prompt
                // (setMode / addRules / addDirectories) — echoed back as `updatedPermissions`.
                // EXCEPT sandbox escalations (`blocked_path`): those re-ask on every command no
                // matter what is granted — allow rules, additionalDirectories and acceptEdits were
                // all probed (2.1.220) and none suppresses the next prompt (only a bare "Bash"
                // rule or bypassPermissions does). Offering "don't ask again" there would be a
                // lie, so those cards fall back to plain Accept / Reject.
                val suggestions =
                    if (request["blocked_path"] == null) request["permission_suggestions"] as? JsonArray
                    else null
                // Why the ask escalated, when the CLI says (schema: "human-readable reason … for the
                // consent line of the host's dialog"; checklist 1.23). Absent on every ask measured
                // so far — a plain mode-requires-it prompt carries no reason.
                val reason = request["decision_reason"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }
                onPermission(reqId, tool, input, suggestions, reason)
            }
            "hook_callback" -> {
                val id = request["callback_id"]?.jsonPrimitive?.content ?: ""
                val input = request["input"]?.jsonObject ?: JsonObject(emptyMap())
                onHook(id, input) { out -> writeControlResponse(reqId, out) }
            }
            // MCP elicitation (a server asking the user a question). The panel has no form for
            // it, so answer with the schema's honest no-answer — the response enum is
            // `action: accept|decline|cancel` and a bare `{}` is invalid (checklist 11.5, 2026-08-29).
            "elicitation" -> writeControlResponse(reqId, buildJsonObject { put("action", "decline") })
            // Anything else we don't implement (sdk mcp): acknowledge so the CLI won't hang.
            else -> writeControlResponse(reqId, buildJsonObject {})
        }
    }

    /**
     * Answer a can_use_tool request. allow=true applies the tool; false rejects it.
     * [updatedPermissions] optionally carries the accepted permission suggestion(s), which the CLI
     * persists (session or settings file, per each entry's `destination`) so it stops asking.
     *
     * [feedback] is the user's typed reason (the plan card's input). On deny it IS the message —
     * probed 2.1.233: the deny message is delivered to the model verbatim as the tool_result, so
     * the CLI's own "the user said" branch fires and it revises instead of asking. On allow it is
     * APPENDED TO THE APPROVED PLAN via `updatedInput` (the terminal's ctrl+g edit path): the
     * ExitPlanMode tool_result echoes the approved plan, so the model reads the note in the same
     * message as the approval, before its first implementation call — the terminal's own
     * shift+tab does the equivalent, pushing acceptFeedback as an extra text block on that
     * tool_result. Two dead ends, both probed on 2.1.233: a `feedback` field on the allow
     * response is silently dropped, and a user message steered in via stdin is only read at the
     * NEXT model call — when the model starts implementing immediately, the first tool call wins
     * and the note arrives after it (observed live 2026-08-16).
     */
    fun respondPermission(
        requestId: String, allow: Boolean, input: JsonObject,
        updatedPermissions: JsonArray? = null, feedback: String? = null,
    ) {
        val response = if (allow) {
            // Approve-with-notes: the note becomes part of the approved plan (and of the plan
            // file the CLI writes), under the shared marker heading SessionStore parses back out.
            val finalInput = if (!feedback.isNullOrBlank() && input["plan"] != null) {
                JsonObject(input.toMutableMap().apply {
                    put("plan", kotlinx.serialization.json.JsonPrimitive(
                        (input["plan"]?.jsonPrimitive?.content ?: "") +
                            io.github.amitsidhpura.claudebrains.RenderLimits.PLAN_NOTES_MARKER + feedback))
                })
            } else input
            buildJsonObject {
                put("behavior", "allow")
                put("updatedInput", finalInput)
                if (updatedPermissions != null) put("updatedPermissions", updatedPermissions)
            }
        } else {
            buildJsonObject {
                put("behavior", "deny")
                put("message", feedback?.takeIf { it.isNotBlank() }
                    ?: io.github.amitsidhpura.claudebrains.RenderLimits.REJECT_MESSAGE)
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

    /**
     * Host-initiated: change the permission mode — manual | acceptEdits | plan | auto, all of
     * which the CLI accepts at runtime (probed 2.1.220). `bypassPermissions` is the exception it
     * refuses to be raised to without `--dangerously-skip-permissions`; nothing sends it.
     */
    fun setPermissionMode(mode: String) = sendControlRequest(buildJsonObject {
        put("subtype", "set_permission_mode")
        put("mode", mode)
    })

    /** Host-initiated: interrupt the in-flight response. */
    fun interrupt() = sendControlRequest(buildJsonObject { put("subtype", "interrupt") })

    /** Host-initiated: kill ONE background task (a sub-agent or a backgrounded shell) by the
     * `task_id` the `background_tasks_changed` roster carries. Schema `stop_task{task_id}`; in
     * the stdio host since 2.1.238 (probed 2026-08-23). The CLI answers success for unknown ids
     * too, so the only confirmation is the next roster frame no longer listing the task. */
    fun stopTask(taskId: String) = sendControlRequest(buildJsonObject {
        put("subtype", "stop_task")
        put("task_id", taskId)
    })

    /**
     * Host-initiated: switch the model for this session (e.g. "sonnet", "opus[1m]", "default").
     * Answered — not fire-and-forget since 2.1.251: a user's `PreModelSwitch` hook runs for this
     * request (`source:"sdk"`) and a deny comes back as `{subtype:"error", error:"Model switch
     * blocked by a PreModelSwitch hook: …"}` (measured 2026-08-30). [onResponse] gets that error
     * so the caller can revert what it changed optimistically (checklist 9.11).
     */
    fun setModel(model: String, onResponse: ((JsonObject?, String?) -> Unit)? = null) =
        sendControlRequest(buildJsonObject {
            put("subtype", "set_model")
            put("model", model)
        }, onResponse)

    /** Host-initiated: merge settings into the CLI's per-process flag layer (probed 2026-08-24:
     * {"fastMode": true} clears the SDK fast-mode gate, false reverts — get_settings.effective
     * reflects both directions). */
    fun applyFlagSettings(settings: JsonObject) = sendControlRequest(buildJsonObject {
        put("subtype", "apply_flag_settings")
        put("settings", settings)
    })

    /** Host-initiated: cap extended thinking. null = reset to the session default; 0 = off.
     * (The CLI schema: max_thinking_tokens int|null, "omitted or null … resets".) */
    fun setMaxThinkingTokens(maxTokens: Int?) = sendControlRequest(buildJsonObject {
        put("subtype", "set_max_thinking_tokens")
        put("max_thinking_tokens", maxTokens?.let { JsonPrimitive(it) } ?: JsonNull)
    })


    /**
     * Host-initiated: a side question (checklist 8.11; the TUI's `/btw`). The CLI answers it on a
     * one-turn fork of the conversation — tools denied, transcript write skipped — so nothing
     * lands in the thread (measured 2026-08-29: `system/control_request_progress{started}`, then
     * `control_response{response:{response, synthetic}}`; no record on disk, no turn on the wire).
     * [history] is the panel's earlier `{question, response}` pairs so a follow-up reads as one;
     * the CLI keeps none itself. The answer, or the CLI's error text, comes back on [onAnswer].
     */
    fun askSideQuestion(question: String, history: JsonArray, onAnswer: (response: JsonObject?, error: String?) -> Unit) =
        sendControlRequest(buildJsonObject {
            put("subtype", "side_question")
            put("question", question)
            if (history.isNotEmpty()) put("history", history)
        }, onAnswer)

    private fun sendControlRequest(request: JsonObject, onResponse: ((JsonObject?, String?) -> Unit)? = null) {
        val id = java.util.UUID.randomUUID().toString()
        onResponse?.let { pending[id] = it }
        val line = json.encodeToString(JsonObject.serializer(), buildJsonObject {
            put("type", "control_request")
            put("request_id", id)
            put("request", request)
        })
        writeLine(line)
    }


    /** Send a user turn with optional attachments. */
    fun sendUserMessage(text: String, attachments: List<Attachment>) {
        val line = json.encodeToString(JsonObject.serializer(), buildJsonObject {
            put("type", "user")
            put("message", buildJsonObject {
                put("role", "user")
                put("content", buildJsonArray {
                    if (text.isNotBlank()) {
                        add(buildJsonObject { put("type", "text"); put("text", text) })
                    }
                    // images -> image blocks; pdf/text -> document blocks (matching the VS Code extension)
                    attachments.forEach { att ->
                        when (att.kind) {
                            "pdf" -> add(buildJsonObject {
                                put("type", "document")
                                put("source", buildJsonObject {
                                    put("type", "base64"); put("media_type", "application/pdf"); put("data", att.data)
                                })
                                if (att.name.isNotBlank()) put("title", att.name)
                            })
                            "text" -> add(buildJsonObject {
                                put("type", "document")
                                put("source", buildJsonObject {
                                    put("type", "text"); put("media_type", "text/plain"); put("data", decodeBase64Text(att.data))
                                })
                                if (att.name.isNotBlank()) put("title", att.name)
                            })
                            else -> add(buildJsonObject {   // "image"
                                put("type", "image")
                                put("source", buildJsonObject {
                                    put("type", "base64"); put("media_type", att.mediaType); put("data", att.data)
                                })
                            })
                        }
                    }
                })
            })
        })
        writeLine(line)
    }

    private fun decodeBase64Text(b64: String): String =
        runCatching { String(Base64.getDecoder().decode(b64), Charsets.UTF_8) }.getOrDefault("")

    private fun writeLine(line: String) {
        val w = stdin ?: return
        // A dead pipe is an expected state, not a caller error: a CLI that fails at argument
        // parsing is gone before the very first sendInitialize(), and the throw used to unwind
        // out of start() — leaving ClaudeSessionService.cli unassigned, so the exit frame read
        // stderrTail/sawFrame off a null instance (measured 2026-09-05, stub CLI). The exit
        // path, not the writer, reports the death.
        runCatching { synchronized(w) { w.write(line); w.write("\n"); w.flush() } }
            .onFailure { log.warn("write to claude failed (process dead?): ${it.message}") }
    }

    fun stop() {
        stopped = true
        // A question still in flight will never be answered by this process; say so rather than
        // leave the panel's row pending forever.
        pending.keys.toList().forEach { id -> pending.remove(id)?.invoke(null, "claude stopped before answering") }
        runCatching { stdin?.close() }
        process?.destroy()
        // Carries the bridge auth token; don't leave it in the temp dir.
        runCatching { mcpConfigFile?.delete() }
        mcpConfigFile = null
    }

    /**
     * Bounded wait for the process to actually die after [stop] (which only SENDS the signal).
     * For callers that must outlive-proof a file operation — a dying CLI can still flush a
     * write, resurrecting a transcript deleted too early. Call OFF the EDT.
     */
    fun awaitExit(timeoutMs: Long) {
        runCatching { process?.waitFor(timeoutMs, java.util.concurrent.TimeUnit.MILLISECONDS) }
    }

    companion object {
        const val INIT_REQ_ID = "sdk-init"
        /** callback_id of the PreToolUse autosave hook declared in [sendInitialize]. */
        const val HOOK_AUTOSAVE = "autosave"
        /** stderr lines kept for the exit message — the tail is what explains a crash. */
        const val STDERR_TAIL = 8
    }

    private fun gitBashPath(): String? {
        if (!System.getProperty("os.name").startsWith("Windows")) return null
        return listOf(
            "C:/Program Files/Git/bin/bash.exe",
            "C:/Program Files (x86)/Git/bin/bash.exe",
        ).firstOrNull { File(it).exists() }
    }
}

package io.github.amitsidhpura.claudebrains.ui

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.fileChooser.FileChooserFactory
import com.intellij.openapi.fileChooser.FileSaverDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.ui.jcef.JBCefBrowser
import com.intellij.ui.jcef.JBCefBrowserBase
import com.intellij.ui.jcef.JBCefJSQuery
import io.github.amitsidhpura.claudebrains.ClaudeSessionService
import io.github.amitsidhpura.claudebrains.RenderLimits
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.cef.browser.CefBrowser
import org.cef.browser.CefFrame
import org.cef.handler.CefLoadHandlerAdapter
import java.awt.datatransfer.DataFlavor
import java.awt.dnd.DnDConstants
import java.awt.dnd.DropTarget
import java.awt.dnd.DropTargetAdapter
import java.awt.dnd.DropTargetDropEvent
import java.io.File
import java.nio.charset.StandardCharsets
import java.util.Base64
import javax.swing.JComponent

/**
 * JCEF host for the chat UI. Bridges the web UI to the session service.
 *
 * JS -> Kotlin: a single `window.__bridge(json)` channel carrying `{kind:"user"|"perm", ...}`.
 * Kotlin -> JS: `window.onClaudeEvent(rawStreamJsonLine)` — including synthetic
 * `{type:"permission_request"}` frames for tool approvals.
 */
class ChatPanel(private val project: Project, parent: Disposable) {

    private val browser = JBCefBrowser()
    private val jsToKotlin = JBCefJSQuery.create(browser as JBCefBrowserBase)
    private val session = project.getService(ClaudeSessionService::class.java)
    private val json = Json { ignoreUnknownKeys = true }

    val component: JComponent get() = browser.component

    init {
        Disposer.register(parent, browser)

        jsToKotlin.addHandler { raw ->
            handleFromWeb(raw)
            null
        }

        browser.jbCefClient.addLoadHandler(object : CefLoadHandlerAdapter() {
            override fun onLoadEnd(b: CefBrowser?, frame: CefFrame?, httpStatusCode: Int) {
                browser.cefBrowser.executeJavaScript(
                    "window.__bridge = function(p){ ${jsToKotlin.inject("p")} };",
                    browser.cefBrowser.url, 0,
                )
                startSession()
            }
        }, browser.cefBrowser)

        loadUi()
        installFileDrop()
    }

    /**
     * OS file drags never reach the DOM: JCEF's AWT→CEF drag bridge is not wired for OSR on
     * Linux, so the page's own `drop` handler (which works — proven with a synthetic
     * DataTransfer drop) simply never fires for external files (manual-test 2.9). This AWT
     * DropTarget is the delivery layer instead: read the files off the EDT, base64 them, and
     * hand them to the page's `__dropFiles`, which routes into the same pending/attachment
     * pipeline as paste and the picker (fileKind still decides what is supported, so
     * unsupported binaries are skipped identically). If a future JBR ever wires native DnD,
     * a drop could arrive on both paths and duplicate chips — take the DOM handler out then.
     */
    private fun installFileDrop() {
        DropTarget(browser.component, object : DropTargetAdapter() {
            override fun drop(e: DropTargetDropEvent) {
                e.acceptDrop(DnDConstants.ACTION_COPY)
                val files = runCatching {
                    @Suppress("UNCHECKED_CAST")
                    (e.transferable.getTransferData(DataFlavor.javaFileListFlavor) as List<File>)
                }.getOrNull().orEmpty()
                e.dropComplete(files.isNotEmpty())
                if (files.isEmpty()) return
                ApplicationManager.getApplication().executeOnPooledThread {
                    val payload = buildJsonArray {
                        files.forEach { f ->
                            if (!f.isFile) return@forEach
                            if (f.length() > MAX_DROP_BYTES) {
                                log.warn("drop: ${f.name} skipped (${f.length()} bytes > $MAX_DROP_BYTES)")
                                return@forEach
                            }
                            add(buildJsonObject {
                                put("name", f.name)
                                // extension-based; a code file often probes null, and the page's
                                // isTextFile falls back to the extension list — same as paste
                                put("mime", runCatching { java.nio.file.Files.probeContentType(f.toPath()) }
                                    .getOrNull() ?: "")
                                put("data", Base64.getEncoder().encodeToString(f.readBytes()))
                            })
                        }
                    }
                    if (payload.isEmpty()) return@executeOnPooledThread
                    val jsArg = JsonPrimitive(payload.toString()).toString()
                    ApplicationManager.getApplication().invokeLater {
                        browser.cefBrowser.executeJavaScript(
                            "window.__dropFiles && window.__dropFiles(JSON.parse($jsArg));",
                            browser.cefBrowser.url, 0,
                        )
                    }
                }
            }
        })
    }

    private fun handleFromWeb(raw: String) {
        val msg = runCatching { json.parseToJsonElement(raw) as JsonObject }.getOrNull() ?: return
        when (msg["kind"]?.jsonPrimitive?.content) {
            "user" -> {
                val text = msg["text"]?.jsonPrimitive?.content ?: ""
                val attachments = (msg["images"] as? JsonArray)?.mapNotNull { e ->
                    val o = e.jsonObject
                    val data = o["data"]?.jsonPrimitive?.content ?: return@mapNotNull null
                    val mt = o["media_type"]?.jsonPrimitive?.content ?: "application/octet-stream"
                    val kind = o["kind"]?.jsonPrimitive?.content ?: "image"
                    val name = o["name"]?.jsonPrimitive?.content ?: "file"
                    io.github.amitsidhpura.claudebrains.cli.Attachment(kind, mt, data, name)
                } ?: emptyList()
                session.sendUser(text, attachments)
            }
            "download" -> {
                val data = msg["data"]?.jsonPrimitive?.content ?: return
                val name = msg["name"]?.jsonPrimitive?.content ?: "file"
                saveAttachment(name, data)
            }
            "perm" -> {
                val id = msg["id"]?.jsonPrimitive?.content ?: return
                val allow = msg["allow"]?.jsonPrimitive?.content == "true"
                // "sugg" is a comma-separated list of grant tokens: "N" accepts suggestion N
                // whole (the merged "Always allow" button sends every suggestion's index), and
                // "N.R" accepts only rule R of suggestion N — a split-menu partial grant of a
                // compound command, whose rules arrive as ONE addRules suggestion (measured
                // 2026-08-09, CLI 2.1.226). "-1" is the no-suggestion sentinel.
                val suggs = (msg["sugg"]?.jsonPrimitive?.content ?: "")
                    .split(',').map { it.trim() }.filter { it.isNotEmpty() && it != "-1" }
                session.respondPermission(id, allow, suggs)
            }
            "answer" -> {
                val id = msg["id"]?.jsonPrimitive?.content ?: return
                session.answerQuestion(id, msg["answers"]?.jsonPrimitive?.content ?: "{}")
            }
            "mode" -> msg["mode"]?.jsonPrimitive?.content?.let { session.setPermissionMode(it) }
            "model" -> msg["model"]?.jsonPrimitive?.content?.let { session.setModel(it) }
            "customModels" -> msg["json"]?.jsonPrimitive?.content?.let { session.setCustomModels(it) }
            "stop" -> session.interrupt()
            // Pre-apply gutter lookup for an auto-approved edit's diff (4.4). The page asks at
            // content_block_stop — before the CLI runs the tool — which is the only moment
            // old_string is still findable. File IO goes to a pooled thread; the answer rides
            // a __lineStart frame and may legitimately arrive after tool_result (late = the
            // diff simply renders without a gutter, same as a null answer).
            "lineStart" -> {
                val id = msg["id"]?.jsonPrimitive?.content ?: return
                val tool = msg["tool"]?.jsonPrimitive?.content ?: return
                val input = msg["input"]?.jsonPrimitive?.content ?: return
                ApplicationManager.getApplication().executeOnPooledThread {
                    val line = editLineStart(tool, input)
                    pushFrame(buildJsonObject {
                        put("type", "__lineStart")
                        put("id", id)
                        line?.let { put("line", it) }
                    })
                }
            }
            "new" -> {
                transcriptItems = emptyList(); transcriptFrom = 0  // "more" must not serve the old session
                clearLog(); session.newConversation(); pushTitle(null)
            }
            "resume" -> msg["id"]?.jsonPrimitive?.content?.let { id ->
                clearLog(); pushTranscript(id); session.resumeSession(id); pushTitle(id)
            }
            // Reload the current conversation from disk — same path as picking it in history, so
            // out-of-band edits (and a CLI that died) come back as a clean replay. An id with no
            // file yet is an untouched session: `--resume` would exit 1, so restart it clean.
            "refresh" -> {
                val id = session.currentSessionId()?.takeIf { session.sessionExists(it) }
                transcriptItems = emptyList(); transcriptFrom = 0
                clearLog()
                if (id != null) { pushTranscript(id); session.resumeSession(id) }
                else session.newConversation()
                pushTitle(id)
            }
            // openFile returns false when the path no longer resolves, and a click that does
            // NOTHING reads as a broken panel. Truncation markers make this reachable — a session
            // outlives the CLI's tool-results dir — but it covers every file reference in the log.
            // line/endLine are optional and 1-based: a Read that named a range selects it, anything
            // else opens at the top exactly as before.
            "open" -> msg["path"]?.jsonPrimitive?.content?.let {
                val line = msg["line"]?.jsonPrimitive?.contentOrNull?.toIntOrNull()
                val endLine = msg["endLine"]?.jsonPrimitive?.contentOrNull?.toIntOrNull()
                if (!session.openFile(it, line, endLine)) notifyMissing(it)
            }
            // The CLI keeps the task list on disk, keyed by session id; the webview asks after
            // any Task* call rather than us parsing the stream for them.
            "tasks" -> pushTasks(msg["id"]?.jsonPrimitive?.content)
            "more" -> pushEarlier()
            "history" -> pushSessions()
            "delete" -> msg["id"]?.jsonPrimitive?.content?.let {
                session.deleteSession(it); pushSessions()
            }
        }
    }

    /**
     * Current task list for the live session, mapped into the shape the checklist renderer takes
     * (`content`/`status`/`activeForm`) so live and replay feed the SAME builder.
     */
    private fun pushTasks(toolUseId: String? = null) {
        val id = session.currentSessionId() ?: return
        val items = session.tasks(id).map { t ->
            val o = t.jsonObject
            buildJsonObject {
                put("content", o["subject"]?.jsonPrimitive?.contentOrNull ?: "")
                put("status", o["status"]?.jsonPrimitive?.contentOrNull ?: "pending")
                o["activeForm"]?.jsonPrimitive?.contentOrNull?.let { put("activeForm", it) }
            }
        }
        if (items.isEmpty()) return
        pushFrame(buildJsonObject {
            put("type", "__tasks")
            // Echoed so the page can attach the checklist to the Task* tool line that asked —
            // without it every snapshot appended at the turn's end, and a parallel-call turn
            // stacked detached duplicate lists there (the live-vs-replay gap the user showed).
            toolUseId?.let { put("id", it) }
            put("items", JsonArray(items))
        })
    }

    /** Same balloon group DiffReview uses, so a failed open is reported the one way the plugin reports. */
    private fun notifyMissing(path: String) {
        com.intellij.notification.NotificationGroupManager.getInstance()
            .getNotificationGroup("Claude Brains")
            .createNotification(
                "File not found",
                path,
                com.intellij.notification.NotificationType.WARNING,
            )
            .notify(project)
    }

    private fun frameOf(type: String, items: JsonElement): String =
        json.encodeToString(JsonObject.serializer(), buildJsonObject { put("type", type); put("items", items) })

    private fun pushFrame(frame: JsonObject) =
        pushEvent(json.encodeToString(JsonObject.serializer(), frame))

    private fun clearLog() = pushEvent("""{"type":"__clear"}""")

    /** Last title shipped, so the per-turn re-check only pushes when the CLI renames the thread. */
    private var lastTitle: String? = null

    /**
     * Header title. [id] null = fresh conversation; the page shows its own "New conversation"
     * placeholder when the text is empty, so an unnamed live session doesn't flash a stale name.
     */
    private fun pushTitle(id: String?) {
        val title = id?.let { session.sessionTitle(it) } ?: ""
        if (title == lastTitle) return
        lastTitle = title
        pushFrame(buildJsonObject { put("type", "__title"); put("text", title) })
    }

    /** Full parsed transcript of the resumed session; only the tail is shipped up front. */
    private var transcriptItems: List<JsonObject> = emptyList()
    private var transcriptFrom = 0   // items before this index have not been sent yet

    /** Blocks [from, to) of the parsed transcript, plus the aligned cut both chunk kinds need. */
    private fun chunkItems(from: Int, to: Int) =
        buildJsonArray { transcriptItems.subList(from, to).forEach { add(it) } }

    private fun alignedCut(endExclusive: Int, maxBlocks: Int) =
        io.github.amitsidhpura.claudebrains.session.SessionStore.alignedStart(transcriptItems, endExclusive, maxBlocks)

    private fun pushTranscript(id: String) {
        // The parse is cheap (~0.1s even for a 177 MB file) and unavoidable — tool results patch
        // earlier blocks, so the tail can't be read in isolation. What was slow is shipping and
        // rendering all of it at once, so only the newest turns go now; the rest wait for "more".
        transcriptItems = session.readTranscript(id)
        transcriptFrom = alignedCut(transcriptItems.size, INITIAL_BLOCKS)
        pushFrame(buildJsonObject {
            put("type", "__transcript")
            put("context", session.contextTokens(id))   // seeds the context gauge
            put("more", transcriptFrom)                 // blocks still available above
            put("items", chunkItems(transcriptFrom, transcriptItems.size))
        })
    }

    private fun pushEarlier() {
        val to = transcriptFrom
        if (to <= 0) return
        transcriptFrom = alignedCut(to, MORE_BLOCKS)
        pushFrame(buildJsonObject {
            put("type", "__transcript_more")
            put("more", transcriptFrom)
            put("items", chunkItems(transcriptFrom, to))
        })
    }

    private fun pushSessions() {
        val frame = buildJsonObject {
            put("type", "sessions")
            session.currentSessionId()?.let { put("current", it) } // row is marked, delete hidden
            put("items", buildJsonArray {
                session.listSessions().forEach { s ->
                    add(buildJsonObject {
                        put("id", s.id)
                        put("title", s.title)
                        put("time", s.lastActivity)
                        put("size", s.sizeBytes)
                        put("tokens", s.tokens)
                    })
                }
            })
        }
        pushFrame(frame)
    }

    /**
     * 1-based line where an Edit/Write/MultiEdit lands in the file, so the diff can show a
     * line-number gutter. Edit: locate `old_string` in the current file text. MultiEdit: locate
     * the FIRST edit's old_string (the page numbers only the first section). Write: line 1.
     * null when it can't be found. Must run PRE-apply — once the CLI has written the file,
     * old_string is gone and this correctly answers null (the diff renders without a gutter).
     */
    private fun editLineStart(tool: String, inputJson: String): Int? {
        val obj = runCatching { json.parseToJsonElement(inputJson).jsonObject }.getOrNull() ?: return null
        if (tool == "Write") return 1
        val old = when (tool) {
            "Edit" -> obj["old_string"]?.jsonPrimitive?.content
            "MultiEdit" -> (obj["edits"] as? JsonArray)?.firstOrNull()
                ?.let { it as? JsonObject }?.get("old_string")?.jsonPrimitive?.content
            else -> null
        } ?: return null
        val path = (obj["file_path"] ?: obj["path"])?.jsonPrimitive?.content ?: return null
        val text = readFileText(path) ?: return null
        val idx = text.indexOf(old)
        if (idx < 0) return null
        var line = 1
        for (i in 0 until idx) if (text[i] == '\n') line++
        return line
    }

    /**
     * Current text of a file. Reads fresh from disk (the CLI applies edits to disk out-of-band, so
     * the IDE's VFS/document cache is stale right after an edit) — but honors the in-editor buffer
     * when it has genuine unsaved changes, since that's what Claude matched `old_string` against.
     */
    private fun readFileText(path: String): String? = runCatching {
        val vf = io.github.amitsidhpura.claudebrains.findVFile(path)
        val unsaved: String? = if (vf != null) {
            io.github.amitsidhpura.claudebrains.readLocked {
                val fdm = com.intellij.openapi.fileEditor.FileDocumentManager.getInstance()
                val doc = fdm.getDocument(vf)
                if (doc != null && fdm.isDocumentUnsaved(doc)) doc.text else null
            }
        } else null
        unsaved ?: java.io.File(path).readText()
    }.getOrNull()

    /**
     * Save a downloaded attachment to disk. JCEF registers no CefDownloadHandler, so a JS
     * `<a download>` no-ops — instead the page routes the bytes here and we show the IDE's native
     * save dialog. Runs on the EDT; base64 is the standard data-URL alphabet.
     */
    private fun saveAttachment(name: String, base64: String) {
        val bytes = runCatching { java.util.Base64.getDecoder().decode(base64) }.getOrNull() ?: return
        ApplicationManager.getApplication().invokeLater {
            val descriptor = saveDescriptor("Save Attachment", "Save the attached file to disk")
            val dialog = FileChooserFactory.getInstance().createSaveFileDialog(descriptor, project)
            val wrapper = dialog.save(null as com.intellij.openapi.vfs.VirtualFile?, name)
            if (wrapper != null) runCatching { wrapper.file.writeBytes(bytes) }
                .onFailure {
                    com.intellij.openapi.diagnostic.Logger.getInstance(ChatPanel::class.java)
                        .warn("saving attachment $name failed", it)
                }
        }
    }

    /**
     * 2025.1+ deprecates the (title, description, vararg extensions) ctor in favor of a two-arg
     * overload that doesn't exist on our 242 baseline. Reflection lets one binary use whichever
     * the running IDE has, without a direct bytecode ref the Plugin Verifier would flag.
     */
    private fun saveDescriptor(title: String, desc: String): FileSaverDescriptor =
        runCatching {
            FileSaverDescriptor::class.java.getConstructor(String::class.java, String::class.java)
                .newInstance(title, desc)
        }.getOrElse {
            FileSaverDescriptor::class.java
                .getConstructor(String::class.java, String::class.java, Array<String>::class.java)
                .newInstance(title, desc, emptyArray<String>())
        }

    private fun loadUi() {
        // loadHTML has no base URL, so a <link> can't resolve — splice the shared
        // stylesheet (webview/chat.css, also linked by design/mockup.html) at the marker.
        val html = javaClass.getResourceAsStream("/webview/chat.html")!!
            .use { it.readBytes() }.toString(StandardCharsets.UTF_8)
        val css = javaClass.getResourceAsStream("/webview/chat.css")!!
            .use { it.readBytes() }.toString(StandardCharsets.UTF_8)
        browser.loadHTML(
            html.replace("<!--CSS-->", "<style>\n$css\n</style>")
                // Welcome-screen version, spliced (not pushed as an event) so it is present on
                // first paint. Read from our own descriptor — never hardcode, it would drift
                // from build.gradle.kts. getPluginByClass avoids repeating the plugin id and is
                // non-deprecated on 242 → 262 (checked in platform bytecode).
                .replace("<!--VERSION-->", pluginVersion()?.let { "v$it" } ?: "")
                // Caps/key-order shared with the replay parser. Spliced for the same reason as the
                // version: one source (RenderLimits.kt), so live and replay cannot drift apart.
                .replace("<!--LIMITS-->", "<script>window.LIMITS=${RenderLimits.asJs()}</script>")
        )
    }

    /**
     * Our own version, read from the `plugin.xml` that Gradle's `patchPluginXml` writes into our
     * jar — still sourced from `build.gradle.kts`, never hardcoded, so it cannot drift.
     *
     * Deliberately NOT `PluginManager.getPluginByClass`: that is annotated `@ApiStatus.Internal`,
     * which the Plugin Verifier reports on 2026.2 (it is not deprecated, so a deprecation check
     * misses it — check for BOTH). Every public alternative varies across the 242 → 262 range,
     * whereas reading our own resource depends on no platform API at all and cannot rot.
     */
    private fun pluginVersion(): String? = runCatching {
        javaClass.getResourceAsStream("/META-INF/plugin.xml")!!
            .use { it.readBytes() }.toString(StandardCharsets.UTF_8)
            .let { Regex("<version>([^<]+)</version>").find(it)?.groupValues?.get(1)?.trim() }
            ?.takeIf { it.isNotEmpty() }
    }.getOrNull()

    private var started = false
    private fun startSession() {
        if (started) return
        started = true
        session.start(
            onEvent = { line ->
                pushEvent(line)
                // The CLI names a thread with an `ai-title` record written around the end of a
                // turn, so re-read at every result; pushTitle no-ops unless the name changed.
                if (line.contains("\"type\":\"result\"")) pushTitle(session.currentSessionId())
            },
            onPermission = { requestId, toolName, inputJson, suggestionsJson ->
                val frame = buildJsonObject {
                    put("type", "permission_request")
                    put("id", requestId)
                    put("tool", toolName)
                    put("input", inputJson) // raw JSON string; the page parses/pretty-prints
                    suggestionsJson?.let { put("suggestions", it) } // CLI's don't-ask-again options
                    editLineStart(toolName, inputJson)?.let { put("lineStart", it) } // for the diff gutter
                }
                pushFrame(frame)
            },
            onInit = { metaJson ->
                val meta = runCatching { json.parseToJsonElement(metaJson).jsonObject }.getOrNull()
                if (meta != null) {
                    meta["commands"]?.let { pushEvent(frameOf("__commands", it)) }
                    // custom models first, so a restored custom selection can resolve its display name
                    runCatching { json.parseToJsonElement(session.customModels()) }.getOrNull()
                        ?.let { pushFrame(buildJsonObject { put("type", "__customModels"); put("items", it) }) }
                    meta["models"]?.let { models ->
                        val frame = buildJsonObject {
                            put("type", "__models"); put("items", models)
                            session.selectedModel()?.let { put("selected", it) }
                        }
                        pushFrame(frame)
                    }
                }
            },
            // Carry the stderr tail on a NON-ZERO exit: "claude process exited (1)" with the reason
            // buried in idea.log is a dead end, and there is no terminal to check — the CLI that
            // just died is this session's.
            onExit = { code ->
                pushFrame(buildJsonObject {
                    put("type", "__exit")
                    put("code", code)
                    if (code != 0) session.stderrTail().takeIf { it.isNotEmpty() }
                        ?.let { put("stderr", it.joinToString("\n")) }
                })
            },
        )

        // Seed the mode chip with the persisted mode the CLI was just launched with —
        // it isn't in the initialize payload, and the chip's built-in default is "default".
        pushFrame(buildJsonObject { put("type", "__mode"); put("mode", session.selectedMode()) })

        // Feed the file list for @-mention autocomplete.
        runCatching {
            val files = session.listProjectFiles()
            val frame = buildJsonObject {
                put("type", "files")
                put("items", buildJsonArray { files.forEach { add(JsonPrimitive(it)) } })
            }
            pushFrame(frame)
        }
    }

    /** Deliver a raw stream-json line to the page's window.onClaudeEvent(json). */
    private fun pushEvent(jsonLine: String) {
        // Encode as a fully-escaped JS string literal (robust for large/complex payloads like init).
        val jsArg = JsonPrimitive(jsonLine).toString()
        ApplicationManager.getApplication().invokeLater {
            browser.cefBrowser.executeJavaScript(
                "window.onClaudeEvent && window.onClaudeEvent($jsArg);",
                browser.cefBrowser.url, 0,
            )
        }
    }

    /** Open Chrome DevTools for the webview. Reached only through the "Claude Brains:
     *  Open DevTools" action (DevToolsAction) — logged because every keyboard route into
     *  JCEF has failed silently at least once (WM grabs, OSR), which is why there is none. */
    fun openDevtools() {
        log.info("openDevtools requested, url=${browser.cefBrowser.url}")
        runCatching { browser.openDevtools() }
            .onFailure { log.warn("openDevtools failed", it) }
    }

    companion object {
        private const val INITIAL_BLOCKS = 250
        /** Per-file cap for drag-dropped attachments: protects the JVM + JS bridge from an
         *  accidental ISO drop; generous vs the API's own image limits, same spirit as the
         *  paste path (which the browser caps naturally at clipboard size). */
        private const val MAX_DROP_BYTES = 25L * 1024 * 1024
        private const val MORE_BLOCKS = 500
        private val log = Logger.getInstance(ChatPanel::class.java)

        /** The project's live panel, stashed by ClaudeToolWindowFactory so actions can reach it. */
        val PANEL_KEY: com.intellij.openapi.util.Key<ChatPanel> =
            com.intellij.openapi.util.Key.create("claude-brains.chatPanel")
    }
}

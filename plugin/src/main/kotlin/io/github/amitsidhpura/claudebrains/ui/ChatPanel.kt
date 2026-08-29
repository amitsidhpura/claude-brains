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
import io.github.amitsidhpura.claudebrains.EditProposals
import io.github.amitsidhpura.claudebrains.RenderLimits
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
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

    // The editor half of an edit permission (dual-surface, first answer wins — 2026-08-09):
    // when the CLI asks can_use_tool for an Edit/Write/MultiEdit, the panel card AND a real
    // editor diff both open; ClaudeSessionService.respondPermission arbitrates. Keyed by the
    // permission request_id so whichever surface answers can retire the other.
    private val permDiffReview = io.github.amitsidhpura.claudebrains.bridge.DiffReview(project)
    private val permDiffs =
        java.util.concurrent.ConcurrentHashMap<String, java.util.concurrent.CompletableFuture<List<String>>>()

    val component: JComponent get() = browser.component

    init {
        Disposer.register(parent, browser)

        jsToKotlin.addHandler { raw ->
            handleFromWeb(raw)
            null
        }

        browser.jbCefClient.addLoadHandler(object : CefLoadHandlerAdapter() {
            // Main frame only: this fires per frame, and seedUi() must not run several times for
            // one load. Nothing in chat.html is framed today, so `started` had been hiding that.
            override fun onLoadEnd(b: CefBrowser?, frame: CefFrame?, httpStatusCode: Int) {
                if (frame?.isMain == false) return
                browser.cefBrowser.executeJavaScript(
                    "window.__bridge = function(p){ ${jsToKotlin.inject("p")} };",
                    browser.cefBrowser.url, 0,
                )
                // The session starts once; the page is seeded on every load, since a reload leaves
                // the DOM back at its defaults with a live CLI still attached. See seedUi().
                startSession()
                seedUi()
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
                // "text" is the plan card's typed feedback; empty means none was given
                val feedback = msg["text"]?.jsonPrimitive?.content?.trim()?.takeIf { it.isNotEmpty() }
                session.respondPermission(id, allow, suggs, feedback)
                // The card answered (or lost the race — either way the question is settled):
                // the editor half must not keep advertising a live decision.
                permDiffs.remove(id)?.let { permDiffReview.dismiss(it) }
            }
            "answer" -> {
                val id = msg["id"]?.jsonPrimitive?.content ?: return
                session.answerQuestion(id, msg["answers"]?.jsonPrimitive?.content ?: "{}")
            }
            // "Files changed · Review" (3.6): open a diff chain of the turn's (baseline, now) pairs.
            "review" -> msg["turn"]?.jsonPrimitive?.content?.toIntOrNull()?.let { turn ->
                val pairs = session.reviewTurn(turn)
                if (pairs.isNotEmpty()) permDiffReview.openChain("Claude: changes (turn $turn)", pairs)
            }
            "mode" -> msg["mode"]?.jsonPrimitive?.content?.let { session.setPermissionMode(it) }
            "model" -> msg["model"]?.jsonPrimitive?.content?.let { session.setModel(it) }
            "fastMode" -> session.setFastMode(msg["on"]?.jsonPrimitive?.content == "true")
            "thinking" -> session.setThinking(msg["on"]?.jsonPrimitive?.content != "false")
            "customModels" -> msg["json"]?.jsonPrimitive?.content?.let { session.setCustomModels(it) }
            "stop" -> session.interrupt()
            // ✕ on a background-task roster row (11.3): kill that one task, not the turn.
            "stopTask" -> msg["id"]?.jsonPrimitive?.content?.let { session.stopTask(it) }
            // Side question (8.11): the page's own row id rides the request and comes back on the
            // `__side` frame, so the answer lands on the row that asked — order is not assumed.
            "side" -> {
                val id = msg["id"]?.jsonPrimitive?.content ?: return
                val question = msg["question"]?.jsonPrimitive?.content ?: return
                val history = msg["history"] as? JsonArray ?: JsonArray(emptyList())
                session.askSideQuestion(question, history) { response, error ->
                    pushFrame(buildJsonObject {
                        put("type", "__side")
                        put("id", id)
                        when {
                            error != null -> put("error", error)
                            else -> {
                                // `response` is null when the model returned nothing (the CLI's own
                                // contract); the page shows its no-answer line for that.
                                put("response", response?.get("response") ?: JsonNull)
                                put("synthetic", response?.get("synthetic") ?: JsonPrimitive(false))
                            }
                        }
                    })
                }
            }
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
            // stopForReplay BEFORE pushTranscript, here and in "refresh": a dying CLI flushes a
            // pending permission's auto-deny to the transcript within ms, so reading first showed
            // that record one reload LATE (a pending plan card replayed "Approved", then flipped
            // to the recorded denial on the next reload — user report 2026-08-23).
            "resume" -> msg["id"]?.jsonPrimitive?.content?.let { id ->
                clearLog(); session.stopForReplay(); pushTranscript(id); session.resumeSession(id); pushTitle(id)
            }
            // Reload the current conversation from disk — same path as picking it in history, so
            // out-of-band edits (and a CLI that died) come back as a clean replay. An id with no
            // file yet is an untouched session: `--resume` would exit 1, so restart it clean.
            "refresh" -> {
                val id = session.currentSessionId()?.takeIf { session.sessionExists(it) }
                transcriptItems = emptyList(); transcriptFrom = 0
                clearLog()
                if (id != null) { session.stopForReplay(); pushTranscript(id); session.resumeSession(id) }
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
            // No id: the header renames the thread you are IN, and Kotlin owns which that is.
            // pushTitle skips an unchanged name, so re-pushing after a no-op rename costs nothing.
            "rename" -> msg["title"]?.jsonPrimitive?.contentOrNull?.let { title ->
                if (session.renameSession(title)) { pushTitle(session.currentSessionId()); pushSessions() }
            }
            "delete" -> msg["id"]?.jsonPrimitive?.content?.let { id ->
                if (id == session.currentSessionId()) {
                    // Deleting the live thread leaves it first — same reset the "new" action
                    // does — and the service removes the file once the old CLI is gone (a dying
                    // process can still flush a write). The list re-pushes when it's done.
                    transcriptItems = emptyList(); transcriptFrom = 0
                    clearLog(); pushTitle(null)
                    session.deleteCurrentSession { pushSessions() }
                } else {
                    session.deleteSession(id); pushSessions()
                }
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

    /** Whether this turn already looked for a name, so the scan below runs at most once per turn. */
    private var titleProbed = false

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
     * The editor surface of an edit permission: a real diff (Accept/Reject buttons on the
     * in-editor banner, right pane editable) opened alongside the panel card, either of which
     * answers the can_use_tool. An edited pane rides back as `updatedInput` (tweak-travel, 3.5 —
     * EditProposals.tweakedInput; whole-file shape as VS Code sends it) and the card is redrawn
     * with the edit that actually ran plus a note. Verdict mapping
     * differs from the bridge flow on ONE point — TAB_CLOSED here means "the editor bows out",
     * not accept-as-proposed: closing the diff without deciding leaves the card as the sole
     * surface, because a permission must never be granted by a window being tidied away.
     */
    private fun openEditorPermissionDiff(requestId: String, tool: String, inputJson: String, suggestionsJson: String?) {
        if (tool != "Edit" && tool != "Write" && tool != "MultiEdit") return
        ApplicationManager.getApplication().executeOnPooledThread {
            val obj = runCatching { json.parseToJsonElement(inputJson).jsonObject }.getOrNull()
                ?: return@executeOnPooledThread
            val path = (obj["file_path"] ?: obj["path"])?.jsonPrimitive?.content
                ?: return@executeOnPooledThread
            // Degrade to card-only when the proposal can't be reconstructed (old_string not
            // found — e.g. the file changed underneath): a wrong diff is worse than none.
            // ONE read feeds the proposal, the left pane and the tweak's whole-file old_string:
            // a second read could differ (unsaved buffer vs disk, VFS lag) and the CLI would then
            // fail to find the old_string it is handed.
            val current = readFileText(path)
            val proposed = EditProposals.proposedContent(tool, obj, current)
                ?: return@executeOnPooledThread
            // The bar's COMBINED suggestion button (user's spec 2026-08-09): one button that
            // grants every allow-suggestion whole — deliberately no split/partial-grant
            // dropdown here; the panel card keeps that.
            val combined = combinedSuggestion(suggestionsJson)
            val future = permDiffReview.open(
                path, proposed, File(path).name,
                acceptAllLabel = combined?.first, current = current,
            )
            permDiffs[requestId] = future
            future.whenComplete { verdict, _ ->
                permDiffs.remove(requestId)
                val tweak = verdict?.getOrNull(1)?.let { EditProposals.tweakedInput(tool, obj, current, it) }
                val tweakDiff = tweak?.let { Pair(current ?: "", verdict[1]) }
                when (verdict?.firstOrNull()) {
                    "FILE_SAVED" -> respondFromEditor(requestId, allow = true, updatedInput = tweak, tweakDiff = tweakDiff)
                    "FILE_SAVED_ALL" -> respondFromEditor(requestId, allow = true,
                        suggestionTokens = combined?.second.orEmpty(), updatedInput = tweak, tweakDiff = tweakDiff)
                    "DIFF_REJECTED" -> respondFromEditor(requestId, allow = false)
                    // TAB_CLOSED / cancellation: no answer from the editor; the card stands.
                }
            }
        }
    }

    /**
     * The editor bar's combined suggestion button: label + grant tokens, or null when the CLI
     * offered nothing usable. Recognizes the same entries the page's permSuggestions does
     * (allow setMode/acceptEdits, allow addRules with rules) and grants them ALL whole — each
     * token is a suggestion's ORIGINAL index, because respondPermission echoes by position.
     * Label follows the card's vocabulary: rules present -> "Always allow" (the wider grant
     * names the button), mode-only -> "Accept all edits".
     */
    private fun combinedSuggestion(suggestionsJson: String?): Pair<String, List<String>>? {
        val arr = suggestionsJson
            ?.let { runCatching { json.parseToJsonElement(it) as? JsonArray }.getOrNull() }
            ?: return null
        var hasRules = false
        val tokens = ArrayList<String>()
        arr.forEachIndexed { idx, s ->
            val o = s as? JsonObject ?: return@forEachIndexed
            val behavior = o["behavior"]?.jsonPrimitive?.contentOrNull
            if (behavior != null && behavior != "allow") return@forEachIndexed
            val type = o["type"]?.jsonPrimitive?.contentOrNull
            val isMode = type == "setMode" && o["mode"]?.jsonPrimitive?.contentOrNull == "acceptEdits"
            val isRules = type == "addRules" && ((o["rules"] as? JsonArray)?.size ?: 0) > 0
            if (!isMode && !isRules) return@forEachIndexed
            if (isRules) hasRules = true
            tokens.add(idx.toString())
        }
        if (tokens.isEmpty()) return null
        return (if (hasRules) "Always allow" else "Accept all edits") to tokens
    }

    /**
     * Editor verdict → control response, and (when it won the race) retire the panel card.
     * [tweakDiff] (current → final pane text) rides the frame so the card can redraw its diff as
     * the edit that actually ran — live's counterpart of replay's `structuredPatch`.
     */
    private fun respondFromEditor(
        requestId: String, allow: Boolean, suggestionTokens: List<String> = emptyList(),
        updatedInput: JsonObject? = null, tweakDiff: Pair<String, String>? = null,
    ) {
        if (session.respondPermission(requestId, allow, suggestionTokens, updatedInput = updatedInput)) {
            pushFrame(buildJsonObject {
                put("type", "__perm_answered")
                put("id", requestId)
                put("allow", allow)
                if (tweakDiff != null) {
                    put("tweaked", true)
                    put("oldStr", tweakDiff.first)
                    put("newStr", tweakDiff.second)
                }
            })
        }
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
        // loadHTML has no base URL, so neither a <link> nor a <script src> can resolve — the
        // page's JS (webview/js/*.js) is spliced by WebviewAssets.page(), and the shared
        // stylesheet (webview/chat.css, also linked by design/mockup.html) at the marker below.
        val html = WebviewAssets.page()
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
                // The roster after a `commands_changed` push is state the page cannot recover on
                // its own: a reload used to replay only the INITIALIZE-time roster, so a skill
                // discovered mid-session vanished from the / menu (checklist 7.10). Keep the raw
                // frame and replay it after the init seed — same handler, same REPLACE semantics.
                if (line.contains("\"commands_changed\"")) lastCommandsChanged = line
                // The CLI names a thread with an `ai-title` record written around the end of a
                // turn, so re-read at every result; pushTitle no-ops unless the name changed.
                if (line.contains("\"type\":\"result\"")) {
                    titleProbed = false
                    pushTitle(session.currentSessionId())
                } else if (lastTitle.isNullOrEmpty() && !titleProbed && line.contains("\"message_start\"")) {
                    // Waiting for `result` leaves an UNNAMED header for the length of a whole turn,
                    // while the history list — which reads the same titleOf() straight off disk on
                    // every open — already shows the name. Measured on a real session: first prompt
                    // 10:09, next 11:10, so the header read "New conversation" beside a titled
                    // "current" row for an hour (the CLI wrote no ai-title for it at all, so the
                    // first-user-message fallback was the final answer the whole time).
                    // message_start is the first point where the record that fallback derives from
                    // is certainly on disk. It fires once per assistant message, and titleOf scans
                    // the whole file with a (mtime,size) cache that a growing transcript misses
                    // every time — hence once per turn, and only while there is no name to lose.
                    titleProbed = true
                    pushTitle(session.currentSessionId())
                }
            },
            onPermission = { requestId, toolName, inputJson, suggestionsJson, reason ->
                val frame = buildJsonObject {
                    put("type", "permission_request")
                    put("id", requestId)
                    put("tool", toolName)
                    put("input", inputJson) // raw JSON string; the page parses/pretty-prints
                    suggestionsJson?.let { put("suggestions", it) } // CLI's don't-ask-again options
                    reason?.let { put("reason", it) }               // CLI's decision_reason (1.23)
                    editLineStart(toolName, inputJson)?.let { put("lineStart", it) } // for the diff gutter
                }
                pushFrame(frame)
                openEditorPermissionDiff(requestId, toolName, inputJson, suggestionsJson)
            },
            onInit = { metaJson ->
                lastInitMeta = metaJson   // kept so a reloaded page can be seeded without a CLI restart
                lastCommandsChanged = null // a fresh initialize is the newer roster; a push from the old CLI is stale
                pushInitMeta(metaJson)
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
    }

    /** The CLI's `initialize` payload, replayed into a reloaded page — it arrives only at CLI start. */
    private var lastInitMeta: String? = null

    /** The newest `system/commands_changed` frame from THIS CLI process, replayed after [lastInitMeta]. */
    private var lastCommandsChanged: String? = null

    /** Slash commands + the model roster, from the CLI's initialize payload. */
    private fun pushInitMeta(metaJson: String) {
        val meta = runCatching { json.parseToJsonElement(metaJson).jsonObject }.getOrNull() ?: return
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
        // Fast-mode truth rides the same payload (fast_mode_state / fast_mode_disabled_reason);
        // forward it with the persisted preference so the footer switch reflects the CLI, and the
        // pref covers the gap when the state is absent. Replayed on every load via seedUi.
        pushFrame(buildJsonObject {
            put("type", "__fastMode")
            meta["fast_mode_state"]?.let { put("state", it) }
            meta["fast_mode_disabled_reason"]?.let { put("reason", it) }
            put("pref", session.fastMode())
        })
    }

    /**
     * Push everything the page cannot know on its own. Runs on EVERY load, not just the first.
     *
     * The webview is a view; Kotlin owns this state. A reload — a renderer restart, a reload from
     * DevTools — puts the DOM back to its markup defaults, and every one of these was previously
     * sent once inside [startSession] behind the `started` guard: the header fell back to "New
     * conversation", the mode chip to "default", tool lines to absolute paths, and the model and
     * slash-command menus to empty, with nothing left to correct any of it. The title is the one
     * that could never heal on its own, because [pushTitle] only pushes on a CHANGE and the name
     * had not changed — hence clearing [lastTitle] first, so the page is told what it is showing
     * rather than what it was last sent.
     */
    private fun seedUi() {
        // Seed the mode chip with the persisted mode the CLI was just launched with —
        // it isn't in the initialize payload, and the chip's built-in default is "default".
        pushFrame(buildJsonObject { put("type", "__mode"); put("mode", session.selectedMode()) })

        // The project root, so a tool line can show `plugin/src/…` instead of repeating the whole
        // prefix on every row. From the IDE rather than the CLI's `system/init` cwd, which carries
        // the same value but only arrives at the FIRST TURN — a resumed transcript renders before
        // that, and would show absolute paths until the user spoke. The webview refreshes from
        // init's cwd anyway, since that is what the model's paths are actually relative to.
        project.basePath?.let { pushFrame(buildJsonObject { put("type", "__project"); put("root", it) }) }

        lastInitMeta?.let { pushInitMeta(it) }
            // before the CLI's first initialize there is still a preference to show on the switch
            ?: pushFrame(buildJsonObject { put("type", "__fastMode"); put("pref", session.fastMode()) })
        // Thinking is Kotlin-owned preference with no CLI echo — seed it on every load.
        pushFrame(buildJsonObject { put("type", "__thinking"); put("on", !session.thinkingOff()) })
        lastCommandsChanged?.let { pushEvent(it) }   // after the seed: it REPLACES the init roster

        lastTitle = null; titleProbed = false
        pushTitle(session.currentSessionId())

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

package io.github.amitsidhpura.claudebrains

import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectFileIndex
import io.github.amitsidhpura.claudebrains.bridge.IdeLockFile
import io.github.amitsidhpura.claudebrains.bridge.IdeMcpServer
import io.github.amitsidhpura.claudebrains.bridge.IdeTools
import io.github.amitsidhpura.claudebrains.bridge.PortFinder
import io.github.amitsidhpura.claudebrains.cli.ClaudeCli
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import java.io.File
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * One live Claude Code session per project: owns the IDE-MCP bridge (server + lockfile)
 * and the CLI process. The UI ([io.github.amitsidhpura.claudebrains.ui.ChatPanel]) attaches via [connectUi].
 */
@Service(Service.Level.PROJECT)
class ClaudeSessionService(private val project: Project) : Disposable {

    private val log = Logger.getInstance(ClaudeSessionService::class.java)

    init {
        // Warm the shell-env capture (ShellEnv KDoc has the whole story) as early as the
        // service exists, so it's ready before the first CLI start instead of costing that
        // start its bounded wait.
        ShellEnv.warm()
    }

    private var server: IdeMcpServer? = null
    private var lock: IdeLockFile? = null
    private var cli: ClaudeCli? = null
    private var port: Int = -1
    private var authToken: String = ""

    /** UI callbacks, kept so we can restart the CLI (new/resume) without re-registering. */
    private var onEventCb: ((String) -> Unit)? = null
    private var onPermissionCb: ((String, String, String, String?) -> Unit)? = null
    private var onInitCb: ((String) -> Unit)? = null
    private var onExitCb: ((Int) -> Unit)? = null

    /** requestId -> tool input, so the UI can answer allow/deny without echoing the payload. */
    private val pendingPermissions = ConcurrentHashMap<String, JsonObject>()

    /** requestId -> the CLI's permission_suggestions, so an accepted one can be echoed back. */
    private val pendingSuggestions = ConcurrentHashMap<String, kotlinx.serialization.json.JsonArray>()

    private val cwd: File get() = File(project.basePath ?: System.getProperty("user.home"))

    private val props get() = PropertiesComponent.getInstance()
    /** Persisted model choice (null/"default" = CLI default). */
    fun selectedModel(): String? = props.getValue(MODEL_KEY)

    /** Persisted permission mode; every (re)start launches the CLI with it. */
    /**
     * Persisted permission mode, migrated to the CLI's current vocabulary (2.1.220): `default` is
     * no longer advertised (it still parses, but `manual` replaced it), and what this plugin
     * labelled "Auto mode" was `bypassPermissions` — approve everything — while the CLI's own Auto
     * is `auto`, which safety-checks each action and pauses on anything risky. A stored bypass is
     * therefore migrated DOWN to `auto`: the label the user chose now means the safer thing.
     */
    fun selectedMode(): String = when (val m = props.getValue(MODE_KEY)) {
        null, "", "default" -> "manual"
        "bypassPermissions" -> "auto"
        else -> m
    }

    /** User-defined models (JSON array of {value, displayName, description}) — persisted across runs. */
    fun customModels(): String = props.getValue(CUSTOM_MODELS_KEY) ?: "[]"
    fun setCustomModels(jsonArray: String) = props.setValue(CUSTOM_MODELS_KEY, jsonArray)

    /**
     * @param onEvent sink for raw stream-json conversation lines
     * @param onPermission (requestId, toolName, inputJson, suggestionsJson?) when the CLI asks
     *   to run a tool; suggestionsJson is its permission_suggestions array, when present
     */
    fun start(
        onEvent: (String) -> Unit,
        onPermission: (requestId: String, toolName: String, inputJson: String, suggestionsJson: String?) -> Unit,
        onInit: (commandsJson: String) -> Unit,
        onExit: (Int) -> Unit,
    ) {
        // Rebind BEFORE the guard: a ChatPanel recreated against a still-running service (the tool
        // window's content is rebuilt) would otherwise return here without ever being wired to the
        // CLI, leaving a panel that renders nothing and can never recover — while the composer and
        // the history list, which call the service directly, keep working and hide it.
        onEventCb = onEvent; onPermissionCb = onPermission; onInitCb = onInit; onExitCb = onExit
        if (server != null) return // already running

        port = PortFinder.findFree()
        val token = UUID.randomUUID().toString()
        authToken = token
        val ideName = ApplicationInfo.getInstance().versionName // e.g. "PhpStorm"

        val tools = IdeTools(project)
        server = IdeMcpServer(port, token, ideName, tools).apply { start() }
        lock = IdeLockFile(port, token).apply { write(workspaceFolders(), ideName) }

        startCli(resumeSessionId = null)
        log.info("Claude session started on port $port")
    }

    private fun startCli(resumeSessionId: String?) {
        runCatching { cli?.stop() }
        pendingPermissions.clear()
        pendingSuggestions.clear()
        cli = ClaudeCli(
            workingDir = cwd,
            ssePort = port,
            authToken = authToken,
            executable = resolveExecutable(),
            permissionMode = selectedMode(),
            resumeSessionId = resumeSessionId,
            onEvent = { line -> onEventCb?.invoke(line) },
            onPermission = { requestId, toolName, input, suggestions ->
                pendingPermissions[requestId] = input
                suggestions?.let { pendingSuggestions[requestId] = it }
                onPermissionCb?.invoke(requestId, toolName, input.toString(), suggestions?.toString())
            },
            onInit = { commandsJson -> onInitCb?.invoke(commandsJson) },
            onExit = { code -> onExitCb?.invoke(code) },
        ).apply { start() }

        // Re-apply the persisted model on every (re)start.
        selectedModel()?.takeIf { it.isNotBlank() && it != "default" }?.let { cli?.setModel(it) }
    }

    /** Restart with a fresh conversation. */
    fun newConversation() = startCli(resumeSessionId = null)

    /** Restart, resuming a past conversation by id. */
    fun resumeSession(id: String) = startCli(resumeSessionId = id)

    /** Past conversations for this project, newest first. */
    fun listSessions(): List<io.github.amitsidhpura.claudebrains.session.SessionStore.SessionInfo> =
        io.github.amitsidhpura.claudebrains.session.SessionStore.list(cwd.path)

    /**
     * Open a file referenced in the chat (tool line path, card header) in the editor.
     * Mirrors [io.github.amitsidhpura.claudebrains.bridge.IdeTools]'s `openFile`, which is private and
     * MCP-facing; paths from the transcript are usually absolute, but a relative one is resolved
     * against the project root so @-mention style references work too.
     */
    /**
     * Open a file reference from the panel. [line]/[endLine] are 1-BASED line numbers (the shape
     * `Read`'s `offset`/`limit` use); when present the editor lands on that line and SELECTS the
     * range, so clicking "chat.css (lines 40-119)" shows exactly the slice Claude read rather than
     * the top of a 683-line file.
     */
    fun openFile(rawPath: String, line: Int? = null, endLine: Int? = null): Boolean {
        val p = rawPath.trim().replace('\\', '/')
        if (p.isEmpty()) return false
        val vf = findVFile(p)
            ?: project.basePath?.let { findVFile("$it/${p.trimStart('/')}") }
            ?: return false
        com.intellij.openapi.application.ApplicationManager.getApplication().invokeLater {
            // OpenFileDescriptor takes a 0-BASED line; the wire carries 1-based, so convert once here
            val zero = line?.let { maxOf(0, it - 1) } ?: 0
            com.intellij.openapi.fileEditor.OpenFileDescriptor(project, vf, zero, 0).navigate(true)
            if (line == null) return@invokeLater
            val editor = com.intellij.openapi.fileEditor.FileEditorManager
                .getInstance(project).selectedTextEditor ?: return@invokeLater
            val doc = editor.document
            // Clamp both ends: a transcript outlives the file it names, so a range recorded against
            // an older, longer version must not throw — it just selects what is still there.
            val startLine = zero.coerceIn(0, maxOf(0, doc.lineCount - 1))
            val lastLine = (endLine?.let { maxOf(0, it - 1) } ?: startLine)
                .coerceIn(startLine, maxOf(0, doc.lineCount - 1))
            editor.selectionModel.setSelection(
                doc.getLineStartOffset(startLine),
                doc.getLineEndOffset(lastLine),
            )
        }
        return true
    }

    /** Transcript the live CLI is writing, once known (null on a fresh session until it reports). */
    fun currentSessionId(): String? = cli?.sessionId

    /** Title of the live conversation, or null before it has a transcript worth naming. */
    fun currentTitle(): String? = currentSessionId()?.let { sessionTitle(it) }

    /** Whether a session id has records on disk — i.e. whether `--resume` on it can work. */
    fun sessionExists(id: String): Boolean =
        io.github.amitsidhpura.claudebrains.session.SessionStore.exists(cwd.path, id)

    /** Title of any conversation on disk. */
    fun sessionTitle(id: String): String? =
        io.github.amitsidhpura.claudebrains.session.SessionStore.titleOf(cwd.path, id)

    /**
     * Delete a past conversation from disk. Irreversible; the UI confirms first.
     * Refuses the live transcript: the CLI reopens the file per write, so deleting it mid-session
     * just recreates it with the remaining records — a silently truncated history rather than a
     * removal. This is the backstop; the live thread deletes via [deleteCurrentSession] instead.
     */
    fun deleteSession(id: String): Boolean {
        if (id == cli?.sessionId) return false
        return io.github.amitsidhpura.claudebrains.session.SessionStore.delete(cwd.path, id)
    }

    /**
     * Name the LIVE conversation, by appending the same `custom-title` record the terminal's
     * `/rename` writes. Unlike [deleteSession] this does NOT refuse the live thread — appending is
     * the safe direction (the CLI opens the transcript O_APPEND per write, so it cannot overwrite
     * the line), and the conversation you are in is the one you want to name.
     *
     * Returns false when there is no session on disk yet: a thread that has not spoken has no
     * transcript, and there is nothing to append to.
     */
    fun renameSession(title: String): Boolean {
        val id = cli?.sessionId ?: return false
        return io.github.amitsidhpura.claudebrains.session.SessionStore.rename(cwd.path, id, title)
    }

    /**
     * Delete the LIVE conversation — the one [deleteSession] refuses. Leave it first: restart
     * on a fresh conversation, wait (bounded, off the EDT) for the OLD process to actually die,
     * THEN delete the file it can no longer resurrect. [onDone] fires from that pooled thread
     * once the file is gone (or the wait/delete failed — the sessions list re-push shows the
     * truth either way).
     */
    fun deleteCurrentSession(onDone: () -> Unit) {
        val old = cli
        val oldId = old?.sessionId
        newConversation()
        com.intellij.openapi.application.ApplicationManager.getApplication().executeOnPooledThread {
            old?.awaitExit(5_000)
            oldId?.let { io.github.amitsidhpura.claudebrains.session.SessionStore.delete(cwd.path, it) }
            onDone()
        }
    }

    /** Context in use at the end of a past conversation, so a resumed thread shows its gauge. */
    fun contextTokens(id: String): Long =
        io.github.amitsidhpura.claudebrains.session.SessionStore.contextTokens(cwd.path, id)

    /** Renderable blocks of a past conversation, for replay into the UI. */
    fun readTranscript(id: String): List<kotlinx.serialization.json.JsonObject> =
        io.github.amitsidhpura.claudebrains.session.SessionStore.readTranscript(cwd.path, id)


    /** Recent CLI stderr, so a non-zero exit can report why instead of only a code. */
    fun stderrTail(): List<String> = cli?.stderrTail().orEmpty()

    /** The session's current task list, from the store the CLI keeps at ~/.claude/tasks/<id>/. */
    fun tasks(id: String): kotlinx.serialization.json.JsonArray =
        io.github.amitsidhpura.claudebrains.session.SessionStore.tasks(id)

    /** Send a user turn with attachments (images / pdf / text). */
    fun sendUser(text: String, attachments: List<io.github.amitsidhpura.claudebrains.cli.Attachment>) =
        cli?.sendUserMessage(text, attachments)

    /**
     * Answer a permission card. Non-empty [suggestionTokens] means the user took one of the CLI's
     * permission_suggestions ("accept all edits" / "always allow" / "allow directory") — those
     * entries ride the allow response as `updatedPermissions` and the CLI stops asking. Tokens:
     * "N" grants suggestion N whole; "N.R" grants only rule R of suggestion N — a compound
     * command's rules arrive as ONE addRules suggestion with rules[] (measured 2026-08-09,
     * CLI 2.1.226), so a split-menu partial grant must echo the suggestion NARROWED to the
     * picked rule(s). Probed: the CLI accepts the subset and persists exactly those rules.
     *
     * FIRST ANSWER WINS: an edit permission now has two surfaces (the panel card and the editor
     * diff), and the pending map is the arbiter — whichever route removes the entry sends the
     * control_response; the loser gets `false` and must not answer again. This also stops a
     * response for an id the CLI never asked about (or already got) from reaching stdin.
     */
    fun respondPermission(requestId: String, allow: Boolean, suggestionTokens: List<String> = emptyList()): Boolean {
        val input = pendingPermissions.remove(requestId) ?: return false
        val suggestions = pendingSuggestions.remove(requestId)
        val picked = mutableListOf<JsonElement>()
        if (allow && suggestions != null) {
            val whole = linkedSetOf<Int>()
            val partial = LinkedHashMap<Int, MutableList<Int>>()   // suggestion idx -> rule idxs
            suggestionTokens.forEach { tok ->
                val dot = tok.indexOf('.')
                if (dot < 0) tok.toIntOrNull()?.let { whole.add(it) }
                else {
                    val si = tok.take(dot).toIntOrNull()
                    val ri = tok.substring(dot + 1).toIntOrNull()
                    if (si != null && ri != null) partial.getOrPut(si) { mutableListOf() }.add(ri)
                }
            }
            whole.forEach { i -> suggestions.getOrNull(i)?.let { picked.add(it) } }
            partial.forEach { (si, ruleIdxs) ->
                if (si in whole) return@forEach                    // whole grant already covers it
                val s = suggestions.getOrNull(si) as? JsonObject ?: return@forEach
                val rules = s["rules"] as? JsonArray ?: return@forEach
                val subset = ruleIdxs.mapNotNull { rules.getOrNull(it) }
                if (subset.isEmpty()) return@forEach
                picked.add(JsonObject(s.toMutableMap().apply { put("rules", JsonArray(subset)) }))
            }
        }
        val chosen = if (picked.isEmpty()) null
        else kotlinx.serialization.json.buildJsonArray { picked.forEach { add(it) } }
        cli?.respondPermission(requestId, allow, input, chosen)
        return true
    }

    /** Answer an AskUserQuestion permission: allow + {questions, answers} as updatedInput. */
    fun answerQuestion(requestId: String, answersJson: String) {
        val original = pendingPermissions.remove(requestId) ?: JsonObject(emptyMap())
        pendingSuggestions.remove(requestId)
        val answers = runCatching { Json.parseToJsonElement(answersJson).jsonObject }.getOrNull()
            ?: JsonObject(emptyMap())
        val updated = buildJsonObject {
            original["questions"]?.let { put("questions", it) }
            put("answers", answers)
        }
        cli?.respondPermission(requestId, true, updated)
    }

    /**
     * Persist + apply the permission mode. All four switch LIVE — probed against 2.1.220:
     * `set_permission_mode` succeeds for manual/acceptEdits/plan/auto.
     *
     * This used to special-case Auto: it meant `bypassPermissions`, which the CLI refuses to be
     * *raised to* at runtime (only via `--dangerously-skip-permissions`, a launch flag that guts
     * every other mode), so entering it relaunched the CLI with `--resume` and told the webview
     * via `__modeRestarted`. Auto now means the CLI's own `auto`, which switches like any other
     * mode — so the relaunch, the resume and that event are all gone. Recover from git history if
     * a bypass option is ever wanted back.
     */
    fun setPermissionMode(mode: String) {
        props.setValue(MODE_KEY, mode)
        cli?.setPermissionMode(mode)
    }

    fun interrupt() = cli?.interrupt()

    fun setModel(model: String) {
        props.setValue(MODEL_KEY, model)
        cli?.setModel(model)
    }

    /** Relative paths of project content files, for @-mention autocomplete (capped). */
    fun listProjectFiles(limit: Int = 3000): List<String> =
        readLocked {
            val base = project.basePath?.replace('\\', '/')
            val out = ArrayList<String>(limit)
            ProjectFileIndex.getInstance(project).iterateContent { vf ->
                if (!vf.isDirectory) {
                    val p = vf.path
                    out.add(if (base != null && p.startsWith(base)) p.substring(base.length).trimStart('/') else p)
                }
                out.size < limit
            }
            out
        }

    private fun workspaceFolders(): List<String> =
        listOfNotNull(project.basePath)

    /**
     * Resolve the `claude` binary: `-Dclaude.executable` override -> PATH ->
     * the binary bundled with the installed VS Code extension (personal fallback).
     */
    private fun resolveExecutable(): String {
        System.getProperty("claude.executable")?.let { if (File(it).exists()) return it }

        val windows = System.getProperty("os.name").startsWith("Windows")
        val exe = if (windows) "claude.exe" else "claude"

        // Search PATH.
        val sep = File.pathSeparator
        System.getenv("PATH")?.split(sep)?.forEach { dir ->
            val f = File(dir, exe)
            if (f.canExecute()) return f.absolutePath
        }

        // Fallback: the native binary shipped with the installed VS Code extension.
        findVsCodeExtensionBinary(exe)?.let { return it }

        return exe // last resort; ProcessBuilder will error if truly absent
    }

    private fun findVsCodeExtensionBinary(exe: String): String? {
        val extRoot = File(System.getProperty("user.home"), ".vscode/extensions")
        if (!extRoot.isDirectory) return null
        return extRoot.listFiles { f -> f.isDirectory && f.name.startsWith("anthropic.claude-code-") }
            ?.sortedByDescending { it.name }
            ?.map { File(it, "resources/native-binary/$exe") }
            ?.firstOrNull { it.canExecute() }
            ?.absolutePath
    }

    override fun dispose() {
        runCatching { cli?.stop() }
        runCatching { server?.shutdown() }
        runCatching { lock?.delete() }
    }

    private companion object {
        const val MODEL_KEY = "claudeCode.selectedModel"
        const val MODE_KEY = "claudeCode.permissionMode"
        const val CUSTOM_MODELS_KEY = "claudeCode.customModels"
    }
}

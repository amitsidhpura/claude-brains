package io.github.amitsidhpura.claudebrains

import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.application.ReadAction
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
    private var server: IdeMcpServer? = null
    private var lock: IdeLockFile? = null
    private var cli: ClaudeCli? = null
    private var port: Int = -1

    /** UI callbacks, kept so we can restart the CLI (new/resume) without re-registering. */
    private var onEventCb: ((String) -> Unit)? = null
    private var onPermissionCb: ((String, String, String) -> Unit)? = null
    private var onInitCb: ((String) -> Unit)? = null
    private var onExitCb: ((Int) -> Unit)? = null

    /** requestId -> tool input, so the UI can answer allow/deny without echoing the payload. */
    private val pendingPermissions = ConcurrentHashMap<String, JsonObject>()

    private val cwd: File get() = File(project.basePath ?: System.getProperty("user.home"))

    private val props get() = PropertiesComponent.getInstance()
    /** Persisted model choice (null/"default" = CLI default). */
    fun selectedModel(): String? = props.getValue(MODEL_KEY)

    /** User-defined models (JSON array of {value, displayName, description}) — persisted across runs. */
    fun customModels(): String = props.getValue(CUSTOM_MODELS_KEY) ?: "[]"
    fun setCustomModels(jsonArray: String) = props.setValue(CUSTOM_MODELS_KEY, jsonArray)

    /**
     * @param onEvent sink for raw stream-json conversation lines
     * @param onPermission (requestId, toolName, inputJson) when the CLI asks to run a tool
     */
    fun start(
        onEvent: (String) -> Unit,
        onPermission: (requestId: String, toolName: String, inputJson: String) -> Unit,
        onInit: (commandsJson: String) -> Unit,
        onExit: (Int) -> Unit,
    ) {
        if (server != null) return // already running
        onEventCb = onEvent; onPermissionCb = onPermission; onInitCb = onInit; onExitCb = onExit

        port = PortFinder.findFree()
        val token = UUID.randomUUID().toString()
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
        cli = ClaudeCli(
            workingDir = cwd,
            ssePort = port,
            executable = resolveExecutable(),
            permissionMode = "default",
            resumeSessionId = resumeSessionId,
            onEvent = { line -> onEventCb?.invoke(line) },
            onPermission = { requestId, toolName, input ->
                pendingPermissions[requestId] = input
                onPermissionCb?.invoke(requestId, toolName, input.toString())
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
    fun openFile(rawPath: String): Boolean {
        val p = rawPath.trim().replace('\\', '/')
        if (p.isEmpty()) return false
        val vf = findVFile(p)
            ?: project.basePath?.let { findVFile("$it/${p.trimStart('/')}") }
            ?: return false
        com.intellij.openapi.application.ApplicationManager.getApplication().invokeLater {
            com.intellij.openapi.fileEditor.OpenFileDescriptor(project, vf).navigate(true)
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
     * removal. This is the backstop; the UI also hides delete on the current row.
     */
    fun deleteSession(id: String): Boolean {
        if (id == cli?.sessionId) return false
        return io.github.amitsidhpura.claudebrains.session.SessionStore.delete(cwd.path, id)
    }

    /** Context in use at the end of a past conversation, so a resumed thread shows its gauge. */
    fun contextTokens(id: String): Long =
        io.github.amitsidhpura.claudebrains.session.SessionStore.contextTokens(cwd.path, id)

    /** Renderable blocks of a past conversation, for replay into the UI. */
    fun readTranscript(id: String): List<kotlinx.serialization.json.JsonObject> =
        io.github.amitsidhpura.claudebrains.session.SessionStore.readTranscript(cwd.path, id)


    /** Send a user turn with attachments (images / pdf / text). */
    fun sendUser(text: String, attachments: List<io.github.amitsidhpura.claudebrains.cli.Attachment>) =
        cli?.sendUserMessage(text, attachments)

    fun respondPermission(requestId: String, allow: Boolean) {
        val input = pendingPermissions.remove(requestId) ?: JsonObject(emptyMap())
        cli?.respondPermission(requestId, allow, input)
    }

    /** Answer an AskUserQuestion permission: allow + {questions, answers} as updatedInput. */
    fun answerQuestion(requestId: String, answersJson: String) {
        val original = pendingPermissions.remove(requestId) ?: JsonObject(emptyMap())
        val answers = runCatching { Json.parseToJsonElement(answersJson).jsonObject }.getOrNull()
            ?: JsonObject(emptyMap())
        val updated = buildJsonObject {
            original["questions"]?.let { put("questions", it) }
            put("answers", answers)
        }
        cli?.respondPermission(requestId, true, updated)
    }

    fun setPermissionMode(mode: String) = cli?.setPermissionMode(mode)

    fun interrupt() = cli?.interrupt()

    fun setModel(model: String) {
        props.setValue(MODEL_KEY, model)
        cli?.setModel(model)
    }

    /** Relative paths of project content files, for @-mention autocomplete (capped). */
    fun listProjectFiles(limit: Int = 3000): List<String> =
        ReadAction.compute<List<String>, RuntimeException> {
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
        const val CUSTOM_MODELS_KEY = "claudeCode.customModels"
    }
}

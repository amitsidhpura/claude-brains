package com.syncroze.claudecode

import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectFileIndex
import com.syncroze.claudecode.bridge.IdeLockFile
import com.syncroze.claudecode.bridge.IdeMcpServer
import com.syncroze.claudecode.bridge.IdeTools
import com.syncroze.claudecode.bridge.PortFinder
import com.syncroze.claudecode.cli.ClaudeCli
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
 * and the CLI process. The UI ([com.syncroze.claudecode.ui.ChatPanel]) attaches via [connectUi].
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
    fun listSessions(): List<com.syncroze.claudecode.session.SessionStore.SessionInfo> =
        com.syncroze.claudecode.session.SessionStore.list(cwd.path)

    /** Rendered turns of a past conversation, for replay into the UI. */
    fun readTranscript(id: String): List<com.syncroze.claudecode.session.SessionStore.TranscriptItem> =
        com.syncroze.claudecode.session.SessionStore.readTranscript(cwd.path, id)

    fun sendUserText(text: String) = cli?.sendUserText(text)

    /** Send a user turn with images (media_type to base64 data) and an id (for rewind). */
    fun sendUser(text: String, images: List<Pair<String, String>>, id: String?) =
        cli?.sendUserMessage(text, images, id)

    /** Check (dryRun) or revert file edits from the turn identified by [id]. */
    fun rewind(id: String, dryRun: Boolean) = cli?.rewindFiles(id, dryRun)

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
    }
}

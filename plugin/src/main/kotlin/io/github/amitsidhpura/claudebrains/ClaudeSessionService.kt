package io.github.amitsidhpura.claudebrains

import io.github.amitsidhpura.claudebrains.session.SavedCommands
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
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
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

    /**
     * Keeps the IDE's view of the working tree in step with the CLI's writes. Lives on the SESSION
     * rather than the panel: the CLI keeps writing whether or not the tool window is open, and a
     * stale editor is not a rendering concern.
     */
    /** Per-turn "files changed" (3.6): baselines from the autosave hook, settled at `result`. */
    private val turnChanges = TurnChanges()
    private val fileSync = CliFileSync(onTurnEnd = {
        refreshFromDisk(cwd.path)
        // Read AFTER the refresh, off the EDT (plain disk reads: the CLI wrote to disk and the
        // VFS may lag; disk is what the CLI's next Read would see).
        com.intellij.openapi.application.ApplicationManager.getApplication().executeOnPooledThread {
            val ended = turnChanges.endTurn { p -> runCatching { java.io.File(p).takeIf { it.isFile }?.readText() }.getOrNull() }
            if (ended != null) onEventCb?.invoke(buildJsonObject {
                put("type", "__files_changed")
                put("turn", ended.first)
                put("files", kotlinx.serialization.json.buildJsonArray {
                    ended.second.forEach { c -> add(buildJsonObject {
                        put("path", c.path); put("added", c.added); put("removed", c.removed); put("isNew", c.isNew)
                    }) }
                })
            }.toString())
        }
    })

    private var server: IdeMcpServer? = null
    private var lock: IdeLockFile? = null
    private var cli: ClaudeCli? = null
    private var port: Int = -1
    private var authToken: String = ""

    /** UI callbacks, kept so we can restart the CLI (new/resume) without re-registering. */
    private var onEventCb: ((String) -> Unit)? = null
    private var onPermissionCb: ((String, String, String, String?, String?) -> Unit)? = null
    private var onInitCb: ((String) -> Unit)? = null
    private var onExitCb: ((Int) -> Unit)? = null
    private var onCancelCb: ((String) -> Unit)? = null   // a withdrawn ask (1.28), after its pending entry is gone
    private var onDialogCb: ((String, String, String) -> Unit)? = null   // a host dialog (4.10): requestId, kind, payloadJson

    /** Host dialogs awaiting an answer (4.10) — the arbiter, as [pendingPermissions] is for asks. */
    private val pendingDialogs: MutableSet<String> = ConcurrentHashMap.newKeySet()

    /** requestId -> tool input, so the UI can answer allow/deny without echoing the payload. */
    private val pendingPermissions = ConcurrentHashMap<String, JsonObject>()

    /** requestId -> the CLI's permission_suggestions, so an accepted one can be echoed back. */
    private val pendingSuggestions = ConcurrentHashMap<String, kotlinx.serialization.json.JsonArray>()

    private val cwd: File get() = File(project.basePath ?: System.getProperty("user.home"))

    /** Global settings, shared by every project (custom models, the two include switches; the
     *  save-as-command scope lives in SaveCommandDialog). */
    private val props get() = PropertiesComponent.getInstance()
    /** Per-project settings (2026-11-26): model, effort, fast mode, thinking, permission mode and
     *  the two slash-command lists, in the project's `.idea/workspace.xml` (git-ignored by the IDE),
     *  so a pick in one window never carries into another project. A project with nothing saved
     *  starts on Default, `medium`, fast off, thinking on, Manual, and empty command lists. */
    private val projectProps get() = PropertiesComponent.getInstance(project)
    /** Persisted model choice (null/"default" = Default, sent as `set_model default`). */
    fun selectedModel(): String? = projectProps.getValue(MODEL_KEY)

    /**
     * The project's permission mode, `manual` when it has never picked one (2026-11-26, the user's
     * choice): the CLI is always launched WITH `--permission-mode`, which beats the user's
     * `permissions.defaultMode` — so a new project never inherits Auto or another project's pick.
     * Until 2026-11-26 a never-picked mode launched without the flag, as VS Code does (4.7).
     * The migration of stored values is [PermissionModes.resolveStored].
     */
    fun selectedMode(): String = PermissionModes.resolveStored(projectProps.getValue(MODE_KEY))

    /** User-defined models (JSON array of {value, displayName, description}) — persisted across runs. */
    fun customModels(): String = props.getValue(CUSTOM_MODELS_KEY) ?: "[]"
    fun setCustomModels(jsonArray: String) = props.setValue(CUSTOM_MODELS_KEY, jsonArray)

    /** Persisted fast-mode preference (9.4). The CLI's flag layer is per-process, so every
     * (re)start re-applies it; actual state is reconciled from the CLI's own frames. */
    fun fastMode(): Boolean = projectProps.getBoolean(FAST_MODE_KEY, false)
    fun setFastMode(on: Boolean) {
        projectProps.setValue(FAST_MODE_KEY, on, false)
        cli?.applyFlagSettings(kotlinx.serialization.json.buildJsonObject { put("fastMode", on) })
    }

    /** The project's effort level (9.2), saved from any `/effort <level>` message ([EffortPick]);
     * `medium` when never picked ([startupEffort]). Per-process in the CLI since 2.1.331, so every
     * (re)start re-applies it. */
    fun effortLevel(): String = startupEffort(projectProps.getValue(EFFORT_KEY))

    /** The level the slider should show: the CLI's `applied.effort` read after the start-up
     * re-apply, or a newer pick, else the saved one. Seeded into a reloaded page. */
    fun shownEffort(): String = shownEffort ?: effortLevel()
    @Volatile private var shownEffort: String? = null

    /** Side question (8.11): answered off-thread by the CLI; see [ClaudeCli.askSideQuestion]. With
     * no CLI running there is nobody to ask, and the panel needs to hear that too. */
    fun askSideQuestion(question: String, history: kotlinx.serialization.json.JsonArray,
                        onAnswer: (response: kotlinx.serialization.json.JsonObject?, error: String?) -> Unit) {
        val c = cli ?: return onAnswer(null, "claude is not running")
        c.askSideQuestion(question, history, onAnswer)
    }

    /** 6.6: whether the editor selection rides every prompt — the paperclip menu's "Include editor
     *  selection" entry. Application-level (global across projects, unlike the model pick): survives
     *  restarts, seeded to the page on every load (ChatPanel.seedUi). Default ON. */
    fun includeSelection(): Boolean = props.getBoolean(SELECTION_KEY, true)
    fun setIncludeSelection(on: Boolean) = props.setValue(SELECTION_KEY, on, true)

    /** 7.12: when each slash command was last sent ({name: epochMs}, JSON). Per project, like the
     *  model pick: a project's own `.claude/commands/` differ, so another project's order is noise. */
    fun commandRecents(): String = SavedCommands.encodeRecent(SavedCommands.parseRecent(projectProps.getValue(RECENT_CMDS_KEY)))
    fun noteCommandUsed(name: String) =
        projectProps.setValue(RECENT_CMDS_KEY, SavedCommands.bumpRecent(projectProps.getValue(RECENT_CMDS_KEY), name, System.currentTimeMillis()))

    /** 7.13: the commands pinned above the composer (JSON array of names, in pin order).
     *  Per project, like the model pick and the recents, so a pin of a project's own command is
     *  never a stale pin elsewhere; a command the project lacks is simply not drawn. Sanitised on the way
     *  in AND out, so the page never sees more than a list of plausible names. */
    fun commandPins(): String = SavedCommands.encodePins(SavedCommands.parsePins(projectProps.getValue(PINNED_CMDS_KEY)))
    fun setCommandPins(jsonArray: String) =
        projectProps.setValue(PINNED_CMDS_KEY, SavedCommands.encodePins(SavedCommands.parsePins(jsonArray)))

    /** 6.6: "Include open file" — the active file's content rides the prompt when nothing is
     *  highlighted (the highlight always wins). Same persistence as the selection switch. Default OFF. */
    fun includeOpenFile(): Boolean = props.getBoolean(OPEN_FILE_KEY, false)
    fun setIncludeOpenFile(on: Boolean) = props.setValue(OPEN_FILE_KEY, on, false)

    /** Persisted thinking preference (9.5), stored as the exception (off). ON resets the CLI to
     * its session default (max_thinking_tokens null); OFF caps it at 0. */
    fun thinkingOff(): Boolean = projectProps.getBoolean(THINKING_OFF_KEY, false)
    fun setThinking(on: Boolean) {
        projectProps.setValue(THINKING_OFF_KEY, !on, false)
        cli?.setMaxThinkingTokens(if (on) null else 0)
    }

    /**
     * @param onEvent sink for raw stream-json conversation lines
     * @param onPermission (requestId, toolName, inputJson, suggestionsJson?, reason?) when the CLI
     *   asks to run a tool; suggestionsJson is its permission_suggestions array, when present;
     *   reason is its `decision_reason`, when present (1.23)
     */
    fun start(
        onEvent: (String) -> Unit,
        onPermission: (requestId: String, toolName: String, inputJson: String, suggestionsJson: String?, reason: String?) -> Unit,
        onInit: (commandsJson: String) -> Unit,
        onExit: (Int) -> Unit,
        onCancel: (requestId: String) -> Unit = {},
        onDialog: (requestId: String, kind: String, payloadJson: String) -> Unit = { _, _, _ -> },
    ) {
        // Rebind BEFORE the guard: a ChatPanel recreated against a still-running service (the tool
        // window's content is rebuilt) would otherwise return here without ever being wired to the
        // CLI, leaving a panel that renders nothing and can never recover — while the composer and
        // the history list, which call the service directly, keep working and hide it.
        onEventCb = onEvent; onPermissionCb = onPermission; onInitCb = onInit; onExitCb = onExit
        onCancelCb = onCancel; onDialogCb = onDialog
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

    /**
     * 15.7 — what Claude Code's own record says about this project's folder: trusted, not, or
     * null when the record cannot be read; with the directory an attestation would name. The
     * home folder is never reported untrusted: the CLI does not keep an answer for it, so a line
     * there could not be answered for good. Reads a file — call off the EDT.
     */
    fun trustPosture(): Pair<Boolean?, File> {
        val dir = WorkspaceTrust.attestDir(cwd)
        if (project.basePath == null || dir == File(System.getProperty("user.home")).absoluteFile) return null to dir
        val text = runCatching { WorkspaceTrust.configFile().readText() }.getOrNull()
        return WorkspaceTrust.isTrusted(text, dir.absolutePath) to dir
    }

    /** One-shot: the next [startCli] attests trust for this directory, then forgets it. */
    private var attestTrustDir: String? = null

    /**
     * 15.7 — the user said Trust on the panel's line. The answer can only ride an `initialize`,
     * so the CLI restarts, on the same conversation when there is one. The CLI writes its own
     * record; the panel only learns the outcome (`workspace_trust_recorded` on the init payload).
     */
    fun trustFolder() {
        attestTrustDir = trustPosture().second.absolutePath
        startCli(resumeSessionId = cli?.sessionId)
    }

    private fun startCli(resumeSessionId: String?) {
        runCatching { cli?.stop() }
        // a new process hears the whole note again; fast mode starts as the saved preference
        // (re-applied at every start) until the CLI's own fast_mode_state says otherwise
        lastStateKey = null; lastSettings = null; lastUsage = null; fastState = if (fastMode()) "on" else "off"
        val trustDir = attestTrustDir.also { attestTrustDir = null }
        pendingPermissions.clear()
        pendingSuggestions.clear()
        pendingDialogs.clear()
        turnChanges.reset()
        // Assigned BEFORE start(): a CLI that dies at argument parsing fires onExit within
        // milliseconds, and the exit frame reads stderrTail()/sawFrame() through this field —
        // assigning after start() left that read on the previous (null) instance (2026-09-05).
        val fresh = ClaudeCli(
            workingDir = cwd,
            ssePort = port,
            authToken = authToken,
            executable = resolveExecutable(),
            permissionMode = selectedMode(),
            resumeSessionId = resumeSessionId,
            addDirs = extraRoots(),
            trustDirectory = trustDir,
            onEvent = { line ->
                // BEFORE the UI callback: the panel's own rendering must not be able to delay, or
                // fail, the editor catching up with what the CLI just wrote to disk.
                fileSync.onLine(line)
                if (line.contains("\"fast_mode_state\"")) noteFastState(line)
                onEventCb?.invoke(line)
            },
            onPermission = { requestId, toolName, input, suggestions, reason ->
                pendingPermissions[requestId] = input
                suggestions?.let { pendingSuggestions[requestId] = it }
                onPermissionCb?.invoke(requestId, toolName, input.toString(), suggestions?.toString(), reason)
            },
            onInit = { commandsJson -> onInitCb?.invoke(commandsJson) },
            onExit = { code -> onExitCb?.invoke(code) },
            // The CLI no longer waits on this ask (1.28): forget it here FIRST, so a click that
            // races the frame finds nothing pending (respondPermission returns false, sends nothing),
            // then let the panel retire the card.
            onCancel = { requestId ->
                pendingPermissions.remove(requestId)
                pendingSuggestions.remove(requestId)
                pendingDialogs.remove(requestId)
                onCancelCb?.invoke(requestId)
            },
            // A host dialog (4.10): remembered here so a click that lost to a withdrawal sends
            // nothing (respondDialog returns false), the same arbiter the asks have.
            onDialog = { requestId, kind, payload ->
                pendingDialogs.add(requestId)
                onDialogCb?.invoke(requestId, kind, payload.toString())
            },
            onHook = { id, input, respond ->
                if (id == ClaudeCli.HOOK_AUTOSAVE) Autosave.handle(input, respond, turnChanges::snapshot)
                else respond(kotlinx.serialization.json.buildJsonObject { put("continue", true) })
            },
        )
        cli = fresh
        fresh.start()

        // Re-apply the persisted model on every (re)start — `default` too, and when nothing is saved
        // (#65 doubt 19, 2026-11-26): a CLI sent no model ran Sonnet 5.5 while its own Default row
        // named Opus 5.5 (2.1.332: applied.model, system/init and the reply all Sonnet 5.5); after
        // `set_model default` the turn was served by Opus 5.5. A refusal here (9.11) has no previous
        // value to fall back to: the persisted choice is dropped and the chip falls back to the
        // roster head (`previous` absent).
        startupModel(selectedModel()).let { m ->
            cli?.setModel(m) { _, err -> if (err != null) revertModel(m, null, err) }
        }
        // …and the footer-switch preferences, which live in per-process CLI state (flag layer /
        // thinking cap). Only the non-default states are worth a request.
        if (fastMode()) cli?.applyFlagSettings(kotlinx.serialization.json.buildJsonObject { put("fastMode", true) })
        if (thinkingOff()) cli?.setMaxThinkingTokens(0)
        // 9.2: the saved effort level, silently — no turn, no line, no transcript (`/effort` lasts
        // one process since 2.1.331). A later slider move still wins: the `/effort` turn moves
        // `applied` past this flag layer (measured on 2.1.332, `_local/probe-effort-persist/`).
        // Then the slider is set from what the CLI actually runs on, picked or not — it used to
        // open on a fixed High while a never-picked CLI ran on `medium`.
        cli?.applyFlagSettings(buildJsonObject { put("effortLevel", effortLevel()) })
        shownEffort = null
        cli?.getSettings { r, _ ->
            val applied = ((r?.get("applied") as? JsonObject)?.get("effort") as? JsonPrimitive)
                ?.contentOrNull?.takeIf { it in EffortPick.LEVELS } ?: return@getSettings
            if (shownEffort == null) shownEffort = applied   // a pick sent meanwhile is newer
            onEventCb?.invoke(buildJsonObject { put("type", "__effort"); put("level", shownEffort) }.toString())
        }
    }

    /**
     * Stop the CLI so the caller can re-read the transcript, waiting (bounded) for it to actually
     * die ONLY when a permission is still pending: a CLI killed mid-permission appends its
     * auto-deny tool_result within ms (measured 2026-08-23), and a replay read before that write
     * showed a pending plan as decided one reload later than it happened. With nothing pending
     * there is no such record to wait for, and an unconditional wait made every ordinary reload
     * visibly slower (user, 2026-08-23). Idempotent with the stop() inside startCli.
     */
    fun stopForReplay() {
        cli?.let {
            it.stop()
            if (pendingPermissions.isNotEmpty()) it.awaitExit(1_500)
        }
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
     * range, so clicking "chat.html (lines 40-119)" shows exactly the slice Claude read rather than
     * the top of the file. [select] false lands the caret on [line] WITHOUT selecting anything —
     * the `· file` pill's click (6.6): the whole file rode, so a highlighted line would misstate
     * what was sent (user, 2026-10-10).
     */
    fun openFile(rawPath: String, line: Int? = null, endLine: Int? = null, select: Boolean = true): Boolean {
        val p = rawPath.trim().replace('\\', '/')
        if (p.isEmpty()) return false
        // findVFileOnDisk, not findVFile: the click is a user action on a path the transcript
        // just showed, and the VFS snapshot lags files written behind the IDE's back — a
        // screenshot dropped into an unindexed scratch folder reported "File not found" while
        // sitting on disk (2026-08-21). See Vfs.kt for the measurement and the threading rule.
        val vf = findVFileOnDisk(p)
            ?: project.basePath?.let { findVFileOnDisk("$it/${p.trimStart('/')}") }
            ?: return false
        com.intellij.openapi.application.ApplicationManager.getApplication().invokeLater {
            // OpenFileDescriptor takes a 0-BASED line; the wire carries 1-based, so convert once here
            val zero = line?.let { maxOf(0, it - 1) } ?: 0
            com.intellij.openapi.fileEditor.OpenFileDescriptor(project, vf, zero, 0).navigate(true)
            if (line == null || !select) return@invokeLater
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

    /** The uncut text behind a replayed IN/OUT box (1.27), read from the live session's transcript. */
    fun toolText(toolId: String, which: String): String? = currentSessionId()?.let {
        io.github.amitsidhpura.claudebrains.session.SessionStore.toolText(cwd.path, it, toolId, which)
    }

    /**
     * Open [text] read-only in an editor tab named [title] (1.27) — VS Code's `open_content` with
     * `editable:false`. A LightVirtualFile: nothing touches disk, the tab closes like any other.
     */
    fun openText(title: String, text: String) {
        com.intellij.openapi.application.ApplicationManager.getApplication().invokeLater {
            val vf = com.intellij.testFramework.LightVirtualFile(
                title, com.intellij.openapi.fileTypes.PlainTextFileType.INSTANCE, text)
            vf.isWritable = false
            io.github.amitsidhpura.claudebrains.bridge.openInEditor(project, vf)
        }
    }

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

    /** 8.15: the text (and record time) behind bookmarked replies, read back from the transcript. */
    fun bookmarkTexts(id: String, uuids: Set<String>): Map<String, Pair<String, Long?>> =
        io.github.amitsidhpura.claudebrains.session.SessionStore.textsByUuid(cwd.path, id, uuids)


    /** Recent CLI stderr, so a non-zero exit can report why instead of only a code. */
    fun stderrTail(): List<String> = cli?.stderrTail().orEmpty()

    fun sawFrame(): Boolean = cli?.sawFrame() ?: false

    /** The session's current task list, from the store the CLI keeps at ~/.claude/tasks/<id>/. */
    fun tasks(id: String): kotlinx.serialization.json.JsonArray =
        io.github.amitsidhpura.claudebrains.session.SessionStore.tasks(id)

    /**
     * Send a user turn with attachments (images / pdf / text). Model-switch items 3 and 4: the
     * applied model / effort and the plan usage are read fresh first (two control requests, no
     * turn), and a `<session_state>` note rides after the text when it says something new
     * ([SessionStateNote]). The reads answer in well under a second; past [STATE_WAIT_MS] the
     * message goes without a note rather than wait. Sends stay in order: each waits for the one
     * before it ([sendChain]).
     */
    fun sendUser(text: String, attachments: List<io.github.amitsidhpura.claudebrains.cli.Attachment>) {
        val c = cli ?: return
        val done = java.util.concurrent.CompletableFuture<Unit>()
        val prev = sendChain.getAndSet(done)
        // A slash command goes as typed: text after its name would become its arguments
        // (`/effort high` from the slider, `/compact` from the gauge).
        if (text.trimStart().startsWith("/")) {
            // 9.2: an effort pick is saved here, whoever sent it (slider, switch marker, typed).
            EffortPick.parse(text)?.let { lvl -> projectProps.setValue(EFFORT_KEY, lvl); shownEffort = lvl }
            prev.whenComplete { _, _ -> try { c.sendUserMessage(text, attachments) } finally { done.complete(Unit) } }
            return
        }
        prev.whenComplete { _, _ ->
            var settings: JsonObject? = null; var usage: JsonObject? = null
            val latch = java.util.concurrent.CountDownLatch(2)
            c.getSettings { r, _ -> settings = r; latch.countDown() }
            c.getUsage { r, _ -> usage = r; latch.countDown() }
            // A read that fails or comes back late keeps the last good answer: seen 2026-11-25, one
            // get_usage in a live run gave nothing and the note dropped its usage line, then
            // restated it on the next message.
            com.intellij.openapi.application.ApplicationManager.getApplication().executeOnPooledThread {
                try {
                    latch.await(STATE_WAIT_MS, java.util.concurrent.TimeUnit.MILLISECONDS)
                    settings = (settings?.takeIf { it["applied"] != null } ?: lastSettings)?.also { lastSettings = it }
                    usage = (usage?.takeIf { it["rate_limits"] != null } ?: lastUsage)?.also { lastUsage = it }
                    val snap = SessionStateNote.snapshot(settings, usage, fastState)
                    val tag = if (snap.key() != lastStateKey) SessionStateNote.tag(snap) else null
                    if (tag != null) lastStateKey = snap.key()
                    c.sendUserMessage(if (tag != null) SessionStateNote.append(text, tag) else text, attachments)
                } finally { done.complete(Unit) }
            }
        }
    }

    private val sendChain = java.util.concurrent.atomic.AtomicReference(
        java.util.concurrent.CompletableFuture.completedFuture(Unit))
    @Volatile private var lastStateKey: List<Any?>? = null
    @Volatile private var fastState: String? = null
    @Volatile private var lastSettings: JsonObject? = null
    @Volatile private var lastUsage: JsonObject? = null
    private fun noteFastState(line: String) {
        runCatching {
            val o = Json.parseToJsonElement(line) as? JsonObject ?: return
            val v = (o["fast_mode_state"] ?: (o["response"] as? JsonObject)?.let { r ->
                r["fast_mode_state"] ?: (r["response"] as? JsonObject)?.get("fast_mode_state") })
            (v as? kotlinx.serialization.json.JsonPrimitive)?.takeIf { it.isString }?.let { fastState = it.content }
        }
    }

    /**
     * Answer a permission card. Non-empty [suggestionTokens] means the user took one of the CLI's
     * permission_suggestions ("accept all edits" / "always allow" / "allow directory") — those
     * entries ride the allow response as `updatedPermissions` and the CLI stops asking. Tokens:
     * "N" grants suggestion N whole; "N.R" grants only rule R of suggestion N — a compound
     * command's rules arrive as ONE addRules suggestion with rules[] (measured 2026-08-09,
     * CLI 2.1.226), so a split-menu partial grant must echo the suggestion NARROWED to the
     * picked rule(s). Probed: the CLI accepts the subset and persists exactly those rules.
     * [destination] (4.8) is where the grant is kept when a destination row was picked; null keeps
     * each suggestion's own — see [PermissionDestinations] for what the CLI does with each value.
     *
     * FIRST ANSWER WINS: an edit permission now has two surfaces (the panel card and the editor
     * diff), and the pending map is the arbiter — whichever route removes the entry sends the
     * control_response; the loser gets `false` and must not answer again. This also stops a
     * response for an id the CLI never asked about (or already got) from reaching stdin.
     */
    fun respondPermission(
        requestId: String, allow: Boolean, suggestionTokens: List<String> = emptyList(),
        feedback: String? = null, updatedInput: JsonObject? = null, destination: String? = null,
        command: String? = null,
    ): Boolean {
        // [updatedInput] replaces the pending input on allow — the editor diff's tweaked edit
        // (3.5, EditProposals.tweakedInput); null answers with the input the CLI asked about.
        // [command] (3.8) is the card's edited Bash command: it replaces `command` in the input the
        // CLI asked about (EditProposals.withCommand); the editor diff's [updatedInput] and this
        // never both apply — a Bash card has no editor surface.
        val input = (pendingPermissions.remove(requestId) ?: return false)
            .let { if (allow && updatedInput != null) updatedInput else it }
            .let { if (allow) EditProposals.withCommand(it, command) else it }
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
        // An edited command's grant names the EDITED text (3.8), then the picked destination (4.8).
        val stamped = PermissionDestinations.stamp(
            EditProposals.withRulesFor(picked.map { it.jsonObject }, command.takeIf { allow }), destination)
        val chosen = if (stamped.isEmpty()) null
        else kotlinx.serialization.json.buildJsonArray { stamped.forEach { add(it) } }
        cli?.respondPermission(requestId, allow, input, chosen, feedback)
        return true
    }

    /** The review pairs of a finished turn (3.6); empty when unknown or evicted. */
    fun reviewTurn(turn: Int): List<TurnChanges.Pair> = turnChanges.review(turn)

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
        projectProps.setValue(MODE_KEY, mode)
        cli?.setPermissionMode(mode)
    }

    fun interrupt() = cli?.interrupt()

    fun stopTask(taskId: String) = cli?.stopTask(taskId)

    /** Output of a shell/Monitor task (11.7); see [ClaudeCli.getTaskOutput]. No CLI → the panel
     * hears an error and stops polling. */
    fun getTaskOutput(taskId: String, onAnswer: (response: kotlinx.serialization.json.JsonObject?, error: String?) -> Unit) {
        val c = cli ?: return onAnswer(null, "claude is not running")
        c.getTaskOutput(taskId, onAnswer)
    }

    /** Move a foreground tool call to the background (11.7); see [ClaudeCli.backgroundTask]. */
    fun backgroundTask(toolUseId: String, onAnswer: (response: kotlinx.serialization.json.JsonObject?, error: String?) -> Unit) {
        val c = cli ?: return onAnswer(null, "claude is not running")
        c.backgroundTask(toolUseId, onAnswer)
    }

    /** The conversation as the terminal's `/export` plain text (15.4); see [ClaudeCli.exportConversation]. */
    fun exportConversation(onAnswer: (response: kotlinx.serialization.json.JsonObject?, error: String?) -> Unit) {
        val c = cli ?: return onAnswer(null, "claude is not running")
        c.exportConversation(onAnswer)
    }

    /**
     * Answer a host dialog (4.10) with the kind's own result token (`continue` / `interrupt` for
     * the auto-mode server-fallback notice). FIRST ANSWER WINS, and a dialog the CLI already
     * withdrew is no longer pending — either way `false` means nothing was sent.
     */
    fun respondDialog(requestId: String, result: String): Boolean {
        if (!pendingDialogs.remove(requestId)) return false
        cli?.respondDialog(requestId, result)
        return true
    }

    /**
     * Optimistic: the chip has already flipped and the choice is persisted before the CLI answers,
     * because through 2.1.250 `set_model` never refused. Since 2.1.251 a `PreModelSwitch` hook can
     * (checklist 9.11), so an error answer puts the persisted value back and sends the webview a
     * `__model_rejected` frame that reverts the chip WITHOUT bridging a model message back.
     */
    fun setModel(model: String, ack: Boolean = false) {
        val previous = selectedModel()
        projectProps.setValue(MODEL_KEY, model)
        cli?.setModel(model) { _, err ->
            if (err != null) revertModel(model, previous, err)
            // Model-switch handoff: the page sends its continue message only once the CLI has
            // answered the switch, so a refused one (a PreModelSwitch hook) never runs the next
            // step on the wrong model.
            else if (ack) onEventCb?.invoke(buildJsonObject {
                put("type", "__model_set")
                put("model", model)
            }.toString())
        }
    }

    private fun revertModel(model: String, previous: String?, error: String) {
        if (selectedModel() == model) projectProps.setValue(MODEL_KEY, previous)
        onEventCb?.invoke(buildJsonObject {
            put("type", "__model_rejected")
            put("model", model)
            previous?.let { put("previous", it) }
            put("error", error)
        }.toString())
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

    /** The base directory plus every content root outside it — the lock file's `workspaceFolders`. */
    private fun workspaceFolders(): List<String> =
        listOfNotNull(project.basePath) + extraRoots()

    /**
     * Content roots the CLI cannot reach from its cwd (attached modules, a monorepo parent), read
     * at each launch — a root attached mid-session is picked up by the next New/resume, not live.
     */
    private fun extraRoots(): List<String> = readLocked {
        WorkspaceRoots.extraDirs(project.basePath,
            com.intellij.openapi.roots.ProjectRootManager.getInstance(project).contentRoots.map { it.path })
    }

    /**
     * Resolve the `claude` binary: `-Dclaude.executable` override -> the PATH the CLI is
     * spawned under (the user's SHELL PATH, `ShellEnv.path()`, not the IDE's bare one — a
     * desktop-launched IDE misses `~/.local/bin`, where the standalone installer puts it) ->
     * the binary bundled with the installed VS Code extension (personal fallback, and a trap:
     * it pins the panel to whatever version that extension last shipped).
     */
    private fun resolveExecutable(): String {
        System.getProperty("claude.executable")?.let { if (File(it).exists()) return it }

        val windows = System.getProperty("os.name").startsWith("Windows")
        val exe = if (windows) "claude.exe" else "claude"

        ShellEnv.which(exe, ShellEnv.path())?.let { return it }

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
        /** How long a send waits for the session-state reads before it goes without a note. */
        const val STATE_WAIT_MS = 2000L
        const val MODEL_KEY = "claudeCode.selectedModel"
        const val MODE_KEY = "claudeCode.permissionMode"
        const val CUSTOM_MODELS_KEY = "claudeCode.customModels"
        const val FAST_MODE_KEY = "claudeCode.fastMode"
        const val THINKING_OFF_KEY = "claudeCode.thinkingOff"
        const val EFFORT_KEY = "claudeCode.effortLevel"
        const val SELECTION_KEY = "claudeCode.includeSelection"
        const val OPEN_FILE_KEY = "claudeCode.includeOpenFile"
        const val RECENT_CMDS_KEY = "claudeCode.commandRecents"
        const val PINNED_CMDS_KEY = "claudeCode.commandPins"
    }
}

/** The model sent to the CLI at every start: the saved pick, or `default` when none is saved (#65 doubt 19). */
internal fun startupModel(saved: String?): String = saved?.takeIf { it.isNotBlank() } ?: "default"

/** The effort level sent to the CLI at every start: the project's saved pick, or `medium` when none is saved. */
internal fun startupEffort(saved: String?): String = saved?.takeIf { it in EffortPick.LEVELS } ?: "medium"

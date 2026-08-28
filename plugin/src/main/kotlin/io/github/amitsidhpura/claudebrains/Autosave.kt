package io.github.amitsidhpura.claudebrains

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.fileEditor.FileDocumentManager
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/**
 * Save a dirty editor before Claude reads or writes its file — the VS Code host's
 * `claudeCode.autosave` (default on), which it implements as an SDK `PreToolUse` hook on
 * `Edit|Write|Read` that saves the document if `isDirty` (extension.js 2.1.233, `saveFileIfNeeded`).
 *
 * WHY A HOOK AND NOT THE PERMISSION CARD: under acceptEdits/auto or a saved rule no `can_use_tool`
 * arrives, and Read never asks — the hook is the only pre-tool moment that fires for every call.
 * WHY IT MATTERS: without it Claude reads the on-disk text while the user's unsaved typing sits in
 * the editor, edits against a stale baseline, and the IDE later reports the write as an external
 * change conflicting with the buffer. Saving first makes disk and editor agree at the instant the
 * tool runs; CliFileSync then handles the other direction (the CLI's write → the editor).
 *
 * The reply is always "carry on" (`{continue:true}`, the reference's exact output). Every failure
 * path — no path, unknown file, no document, save exception — still replies, because an unanswered
 * hook_callback stalls the CLI until its timeout. Always on: the plugin has no settings page by
 * design, and the IDE's own "save on frame deactivation" makes an explicit save the expected norm.
 */
object Autosave {
    private val log = Logger.getInstance(Autosave::class.java)
    private val CONTINUE: JsonObject = buildJsonObject { put("continue", true) }

    /** Hook input is the CLI's standard shape: `{hook_event_name, tool_name, tool_input:{file_path…}}`. */
    /** Edit tools whose PreToolUse is the last moment the file is still un-edited (3.6). */
    private val EDIT_TOOLS = setOf("Edit", "Write", "MultiEdit")

    /**
     * [snapshot] receives (path, text-before-the-edit) for Edit/Write/MultiEdit — the baseline
     * TurnChanges keeps per turn; null text = the file does not exist yet (a Write creating it).
     * Taken AFTER the save, so the baseline is exactly what the CLI is about to read.
     */
    fun handle(input: JsonObject, respond: (JsonObject) -> Unit, snapshot: ((String, String?) -> Unit)? = null) {
        val toolName = input["tool_name"]?.jsonPrimitive?.contentOrNull
        val rawPath = input["tool_input"]?.jsonObject?.get("file_path")?.jsonPrimitive?.contentOrNull
        val wantSnap = snapshot != null && toolName in EDIT_TOOLS && !rawPath.isNullOrBlank()
        val toolInput = input["tool_input"]?.jsonObject
        val path = toolInput?.get("file_path")?.jsonPrimitive?.contentOrNull
            ?: toolInput?.get("notebook_path")?.jsonPrimitive?.contentOrNull
        val vf = path?.takeIf { it.isNotBlank() && !it.startsWith("file:", ignoreCase = true) }?.let(::findVFile)
        if (vf == null) {
            // No VFS file: nothing to save, and the baseline is "absent" (or unreadable — treat
            // the same; a disk read here would be the only unlocked read in the flow).
            if (wantSnap) snapshot!!(rawPath!!, readOnDisk(rawPath))
            respond(CONTINUE); return
        }
        // Reply from the EDT AFTER the save, so the tool cannot outrun the disk write.
        ApplicationManager.getApplication().invokeLater {
            try {
                val fdm = FileDocumentManager.getInstance()
                val doc = fdm.getDocument(vf)
                if (doc != null && fdm.isDocumentUnsaved(doc)) {
                    fdm.saveDocument(doc)
                    log.info("autosaved before ${input["tool_name"]?.jsonPrimitive?.contentOrNull}: ${vf.path}")
                }
                if (wantSnap) snapshot!!(rawPath!!, doc?.text ?: String(vf.contentsToByteArray(), vf.charset))
            } catch (t: Throwable) {
                log.warn("autosave failed for ${vf.path}", t)
            } finally {
                respond(CONTINUE)
            }
        }
    }

    private fun readOnDisk(path: String): String? =
        runCatching { java.io.File(path).takeIf { it.isFile }?.readText() }.getOrNull()
}

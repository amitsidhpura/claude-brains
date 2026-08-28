package io.github.amitsidhpura.claudebrains

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 * Reconstructs the full post-edit file content a can_use_tool input describes, for the editor
 * surface of an edit permission (ChatPanel.openEditorPermissionDiff). Pure logic, kept out of
 * ChatPanel so plain JUnit can pin it.
 */
object EditProposals {

    /**
     * The proposed file content, or null when it can't be derived (unknown tool, missing file,
     * old_string not found — e.g. the file changed underneath; a wrong diff is worse than none).
     * Mirrors the CLI's own apply rules: Edit replaces the FIRST occurrence (all when
     * replace_all), MultiEdit applies its edits[] IN ORDER each against the result of the
     * previous, Write replaces wholesale.
     */
    fun proposedContent(tool: String, obj: JsonObject, current: String?): String? {
        return when (tool) {
            "Write" -> obj.s("content")
            "Edit" -> current?.let { apply(it, obj) }
            "MultiEdit" -> {
                var text = current ?: return null
                val edits = (obj["edits"] as? JsonArray) ?: return null
                for (e in edits) text = apply(text, e as? JsonObject ?: return null) ?: return null
                text
            }
            else -> null
        }
    }

    /**
     * Tweak-travel (checklist 3.5): the can_use_tool input to send back as `updatedInput` when the
     * user edited the right pane of the editor diff before accepting, or null when the pane still
     * holds exactly the proposal (or the proposal cannot be derived) — null means "answer with the
     * ORIGINAL input", so an untouched pane is byte-for-byte the pre-3.5 behaviour.
     *
     * The shape copies what the VS Code extension sends (2.1.250, `rf(..., "single")`: one hunk
     * with 100 000 lines of context, i.e. the whole file): Edit → `old_string` = the whole current
     * file, `new_string` = the whole pane; Write → `content` = the pane; MultiEdit → one such edit
     * in `edits[]` (VS Code hands MultiEdit to the card instead; the schema allows it). Probed
     * 2026-08-28 (CLI 2.1.250, `--permission-prompt-tool stdio`): the CLI applies the tweaked
     * input, writes the file, and the tool_result is the usual one-liner.
     *
     * A whole-file `old_string` is only valid when [current] is the SAME text the diff's left pane
     * showed — one read must feed both, or the CLI's own uniqueness/exists check fails the edit.
     */
    fun tweakedInput(tool: String, input: JsonObject, current: String?, finalText: String): JsonObject? {
        val proposed = proposedContent(tool, input, current) ?: return null
        if (finalText == proposed) return null
        val path = input["file_path"] ?: input["path"] ?: return null
        val wholeFile = JsonObject(mapOf(
            "old_string" to JsonPrimitive(current ?: ""),
            "new_string" to JsonPrimitive(finalText),
            "replace_all" to JsonPrimitive(false),
        ))
        return when (tool) {
            "Write" -> JsonObject(input.toMutableMap().apply { put("content", JsonPrimitive(finalText)) })
            "Edit" -> JsonObject(input.toMutableMap().apply { putAll(wholeFile) })
            "MultiEdit" -> JsonObject(mapOf("file_path" to path, "edits" to JsonArray(listOf(wholeFile))))
            else -> null
        }
    }

    /**
     * Replay's half of 3.5: did the edit the CLI APPLIED differ from the one the model PROPOSED?
     * The transcript keeps the model's original `tool_use` input while `toolUseResult` records
     * what ran (`oldString`/`newString`/`originalFile` for Edit, `content` for Write — measured
     * 2026-08-28; its `userModified` flag means "file changed on disk", not this). Both are
     * replayed onto the same original text and compared; anything underivable is "not tweaked",
     * the direction that never invents a claim about the user.
     */
    fun tweaked(tool: String, input: JsonObject, result: JsonObject): Boolean {
        val original = result.s("originalFile") ?: ""
        val applied = when (tool) {
            "Write" -> result.s("content")
            "Edit" -> {
                val old = result.s("oldString") ?: return false
                val new = result.s("newString") ?: return false
                apply(original, JsonObject(mapOf(
                    "old_string" to JsonPrimitive(old), "new_string" to JsonPrimitive(new),
                    "replace_all" to JsonPrimitive((result["replaceAll"] as? JsonPrimitive)?.contentOrNull == "true"),
                )))
            }
            else -> null
        } ?: return false
        val proposed = proposedContent(tool, input, original) ?: return false
        return applied != proposed
    }

    private fun JsonObject.s(k: String) = (this[k] as? JsonPrimitive)?.contentOrNull

    private fun apply(text: String, e: JsonObject): String? {
        val old = e.s("old_string") ?: return null
        val new = e.s("new_string") ?: return null
        val all = (e["replace_all"] as? JsonPrimitive)?.contentOrNull == "true"
        return when {
            old.isEmpty() -> if (text.isEmpty()) new else null  // empty-old is create-only
            !text.contains(old) -> null
            all -> text.replace(old, new)
            else -> text.replaceFirst(old, new)
        }
    }
}

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

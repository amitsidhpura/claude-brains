package io.github.amitsidhpura.claudebrains

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Watches the CLI's event stream for writes to disk and refreshes the IDE's view of them, so an
 * edit appears in an open editor and a new file appears in the project tree without the user
 * reaching for "Reload from disk".
 *
 * WHY IT SITS ON THE STREAM AND NOT ON THE PERMISSION CARD: a pre-approved tool — acceptEdits/auto
 * mode, a saved rule, a pre-authorized path — produces NO `can_use_tool` at all (gotchas), so the
 * card is not a reliable sighting of a write. `tool_use` / `tool_result` always arrive.
 *
 * WHY IT PAIRS TWO FRAMES: the path is in the `tool_use` input, but the file is not written until
 * the tool has run, so refreshing there would read the file as it was. The `tool_result` says it
 * finished but carries only `tool_use_id` — hence the small id -> path map between them.
 *
 * Shape verified against a real captured stream (`_local/wire.jsonl`): the wire carries a WHOLE
 * `assistant` record whose content holds `tool_use` with the complete input, and a `user` record
 * whose content holds `tool_result` with `tool_use_id`. The partial `stream_event`/`input_json_delta`
 * frames are a rendering concern and are ignored here — nothing needs re-assembling.
 *
 * BASH IS THE HOLE, AND IT IS NOT HYPOTHETICAL. Measured 2026-08-13: asked to create one file and
 * overwrite another, the CLI used a single `Bash` call for both — no Write, no Edit — and nothing
 * here fired. Its input names no file (`git checkout`, `npm install`, a heredoc), so there is no path
 * to refresh from. That is what [onTurnEnd] is for: one sweep of the project when the turn finishes,
 * which costs about what IntelliJ already does whenever the window regains focus, and catches
 * everything the per-file path cannot see.
 *
 * Both callbacks are injected so the parsing can be tested without an IDE.
 */
class CliFileSync(
    private val refresh: (String) -> Unit = ::refreshFromDisk,
    private val onTurnEnd: () -> Unit = {},
) {

    /**
     * Tools that write a file the IDE may be showing. Deliberately NOT [RenderLimits.RESULT_SKIP]:
     * that is a RENDERING list and includes ExitPlanMode/AskUserQuestion/TodoWrite, which touch no
     * file at all. A tool named here without a recognisable path key is simply skipped.
     *
     * Bash is absent on purpose. It can write anything (`git checkout`, `npm install`, a heredoc),
     * but its input names no file, so there is nothing to refresh from — catching those would mean
     * refreshing the whole project on every shell command. Left as a known gap rather than a guess.
     */
    private val writeTools = setOf("Write", "Edit", "MultiEdit", "NotebookEdit")

    /** `notebook_path` rides alongside [RenderLimits.PATH_KEYS] for NotebookEdit. */
    private val pathKeys = RenderLimits.PATH_KEYS + "notebook_path"

    /**
     * tool_use_id -> path, awaiting its result. Bounded: a turn that is interrupted between the
     * tool_use and its result leaves an entry behind, and the panel can run for days.
     */
    private val pending = LinkedHashMap<String, String>()

    private val json = Json { ignoreUnknownKeys = true }

    fun onLine(line: String) {
        // A turn just ended. Sweep, for the writes no tool_use could name — see the Bash note above.
        // Matched as a raw substring like ChatPanel's own result probe: the CLI emits compact JSON.
        // Fires on an intermediate `result` too (a suspending background task), which is harmless —
        // it is still a moment when work has just finished touching the tree.
        if (line.contains("\"type\":\"result\"")) { onTurnEnd(); return }
        // Cheap gate before parsing — the overwhelming majority of frames are streaming deltas.
        if (!line.contains("tool_use") && !line.contains("tool_result")) return
        val rec = runCatching { json.parseToJsonElement(line).jsonObject }.getOrNull() ?: return
        val content = rec["message"]?.jsonObject?.get("content") as? JsonArray ?: return
        for (block in content) {
            val b = block as? JsonObject ?: continue
            when (b["type"]?.jsonPrimitive?.contentOrNull) {
                "tool_use" -> remember(b)
                "tool_result" -> settle(b)
            }
        }
    }

    private fun remember(b: JsonObject) {
        val name = b["name"]?.jsonPrimitive?.contentOrNull ?: return
        if (name !in writeTools) return
        val id = b["id"]?.jsonPrimitive?.contentOrNull ?: return
        val input = b["input"] as? JsonObject ?: return
        val path = pathKeys.firstNotNullOfOrNull { input[it]?.jsonPrimitive?.contentOrNull }
            ?.takeIf { it.isNotBlank() } ?: return
        pending[id] = path
        while (pending.size > MAX_PENDING) pending.remove(pending.keys.first())
    }

    private fun settle(b: JsonObject) {
        val id = b["tool_use_id"]?.jsonPrimitive?.contentOrNull ?: return
        val path = pending.remove(id) ?: return
        // Refreshed even when the result is an error: a failed edit can still have touched the file
        // (a partial write, a created-then-failed file), and a refresh that finds nothing changed
        // costs nothing. Guessing the other way would leave the editor stale.
        refresh(path)
    }

    private companion object {
        /** Far above any real turn's parallel writes; only a backstop against an unbounded map. */
        const val MAX_PENDING = 256
    }
}

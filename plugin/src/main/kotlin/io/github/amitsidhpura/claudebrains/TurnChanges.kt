package io.github.amitsidhpura.claudebrains

/**
 * What Claude changed, per turn (checklist 3.6 — "Files changed (n) · Review").
 *
 * Baselines come from the PreToolUse hook the CLI already blocks on before every Edit / Write /
 * MultiEdit (`Autosave.handle` → [snapshot]): the first time a path is touched in a turn, its
 * text at that moment is kept. At the turn's `result` ([endTurn]) every touched path is re-read
 * and the ones whose text differs are reported with line counts; the pair (baseline, final) is
 * kept per turn so a later "Review" can open real IDE diffs of exactly that turn's work.
 *
 * Why not the CLI: `get_workspace_diff` (probed 2026-08-28, 2.1.250) is git HEAD vs working
 * tree — it lists the user's own edits and staged changes, and needs git. VS Code gets its map
 * from a `file_updated` MCP notification to its in-process sdkMcpServer plus a checkpoint store;
 * we run an external ws server the CLI never notifies. The hook is the one place BOTH clients
 * already have where the file is still un-edited. Limits, stated: only the three edit tools
 * (a Bash `sed` is invisible — same as VS Code's checkpointing), live sessions only (a resumed
 * turn has no baseline; replay shows the count from the transcript, without Review).
 *
 * Pure logic — the IDE reads are injected — so JUnit pins it.
 */
class TurnChanges {

    data class Change(val path: String, val added: Int, val removed: Int, val isNew: Boolean)
    /** A reviewable pair: [before] is null for a file the turn created. */
    data class Pair(val path: String, val before: String?, val after: String)

    private val current = LinkedHashMap<String, String?>()          // path -> baseline (null = absent)
    private val turns = LinkedHashMap<Int, List<Pair>>()
    private var turnIndex = 0

    /** First touch of [path] in this turn keeps [text] (null when the file does not exist yet). */
    @Synchronized
    fun snapshot(path: String, text: String?) {
        if (!current.containsKey(path)) current[path] = text
    }

    @Synchronized fun touched(): Set<String> = current.keys.toSet()

    /**
     * Close the turn: [readNow] gives each touched path's current text (null = gone). Returns the
     * turn's index and the files that actually changed, in touch order; an unchanged touch (an
     * Edit that failed, a Write of identical content) is dropped. Nothing touched → null.
     */
    @Synchronized
    fun endTurn(readNow: (String) -> String?): kotlin.Pair<Int, List<Change>>? {
        if (current.isEmpty()) return null
        val idx = ++turnIndex
        val pairs = ArrayList<Pair>()
        val changes = ArrayList<Change>()
        for ((path, before) in current) {
            val after = readNow(path)
            if (after == null || after == before) continue
            val (add, rem) = lineDelta(before ?: "", after)
            pairs.add(Pair(path, before, after))
            changes.add(Change(path, add, rem, before == null))
        }
        current.clear()
        if (pairs.isEmpty()) return null
        turns[idx] = pairs
        while (turns.size > MAX_TURNS) turns.remove(turns.keys.first())
        return idx to changes
    }

    @Synchronized fun review(turn: Int): List<Pair> = turns[turn].orEmpty()

    @Synchronized fun reset() { current.clear(); turns.clear(); turnIndex = 0 }

    companion object {
        const val MAX_TURNS = 50

        /** Added / removed line counts by common prefix + suffix — the same trim the webview's
         *  wholeFileHunk applies, so the line says what the review diff will show. */
        fun lineDelta(before: String, after: String): kotlin.Pair<Int, Int> {
            val o = before.split('\n'); val n = after.split('\n')
            var p = 0
            while (p < o.size && p < n.size && o[p] == n[p]) p++
            var s = 0
            while (s < o.size - p && s < n.size - p && o[o.size - 1 - s] == n[n.size - 1 - s]) s++
            return (n.size - s - p) to (o.size - s - p)
        }
    }
}

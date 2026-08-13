package io.github.amitsidhpura.claudebrains

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * [CliFileSync] — turning the CLI's write frames into "refresh this path".
 *
 * PROVENANCE: the frame shapes are from a real captured stream (`_local/wire.jsonl`, not in git):
 * a WHOLE `assistant` record carrying `tool_use` with the complete input, and a `user` record
 * carrying `tool_result` with `tool_use_id`. Only the tool names and paths are substituted.
 */
class CliFileSyncTest {

    private val refreshed = mutableListOf<String>()
    private var sweeps = 0
    private val sync = CliFileSync({ refreshed += it }, { sweeps++ })

    private fun toolUse(id: String, name: String, input: String) = sync.onLine(
        """{"type":"assistant","message":{"role":"assistant","content":""" +
            """[{"type":"tool_use","id":"$id","name":"$name","input":$input}]}}""",
    )

    private fun toolResult(id: String, isError: Boolean = false) = sync.onLine(
        """{"type":"user","message":{"role":"user","content":""" +
            """[{"type":"tool_result","tool_use_id":"$id","is_error":$isError,"content":"ok"}]}}""",
    )

    @Test
    fun `an edit refreshes its file, but only once the result says the write finished`() {
        toolUse("t1", "Edit", """{"file_path":"/p/src/App.kt","old_string":"a","new_string":"b"}""")
        assertEquals(emptyList<String>(), refreshed, "the file is not written yet at tool_use")
        toolResult("t1")
        assertEquals(listOf("/p/src/App.kt"), refreshed)
    }

    @Test
    fun `a Write of a NEW file refreshes it too - the add case`() {
        toolUse("t2", "Write", """{"file_path":"/p/src/New.kt","content":"hello"}""")
        toolResult("t2")
        assertEquals(listOf("/p/src/New.kt"), refreshed)
    }

    @Test
    fun `MultiEdit and NotebookEdit are covered, the latter through notebook_path`() {
        toolUse("t3", "MultiEdit", """{"file_path":"/p/a.kt","edits":[]}""")
        toolResult("t3")
        toolUse("t4", "NotebookEdit", """{"notebook_path":"/p/nb.ipynb","new_source":"x"}""")
        toolResult("t4")
        assertEquals(listOf("/p/a.kt", "/p/nb.ipynb"), refreshed)
    }

    /** A failed edit can still have touched the file; a refresh that finds nothing costs nothing. */
    @Test
    fun `an errored result still refreshes`() {
        toolUse("t5", "Write", """{"file_path":"/p/x.kt","content":"y"}""")
        toolResult("t5", isError = true)
        assertEquals(listOf("/p/x.kt"), refreshed)
    }

    @Test
    fun `a parallel fan-out refreshes each file against its own result`() {
        toolUse("a", "Edit", """{"file_path":"/p/one.kt"}""")
        toolUse("b", "Edit", """{"file_path":"/p/two.kt"}""")
        toolResult("b")
        assertEquals(listOf("/p/two.kt"), refreshed, "the id decides which file, not the order")
        toolResult("a")
        assertEquals(listOf("/p/two.kt", "/p/one.kt"), refreshed)
    }

    /** NEGATIVE CONTROLS — the frames that must NOT cause a refresh. */
    @Test
    fun `tools that write nothing are ignored`() {
        toolUse("r1", "Read", """{"file_path":"/p/App.kt"}""")
        toolUse("r2", "Grep", """{"pattern":"x","path":"/p"}""")
        toolUse("r3", "TodoWrite", """{"todos":[]}""")
        toolUse("r4", "ExitPlanMode", """{"plan":"do it"}""")
        listOf("r1", "r2", "r3", "r4").forEach { toolResult(it) }
        assertEquals(emptyList<String>(), refreshed, "Read names a file_path but never writes it")
    }

    @Test
    fun `a result with no matching tool_use is ignored`() {
        toolResult("never-seen")
        assertEquals(emptyList<String>(), refreshed)
    }

    @Test
    fun `a write tool with no recognisable path key is skipped, not guessed at`() {
        toolUse("t6", "Write", """{"content":"orphan"}""")
        toolResult("t6")
        assertEquals(emptyList<String>(), refreshed)
    }

    @Test
    fun `each result fires once - a repeated result cannot refresh twice`() {
        toolUse("t7", "Edit", """{"file_path":"/p/once.kt"}""")
        toolResult("t7")
        toolResult("t7")
        assertEquals(listOf("/p/once.kt"), refreshed)
    }

    @Test
    fun `malformed and unrelated lines are survived`() {
        sync.onLine("""not json at all {"tool_use"""")
        sync.onLine("""{"type":"stream_event","event":{"type":"content_block_delta"}}""")
        sync.onLine("""{"type":"result","subtype":"success"}""")
        assertEquals(emptyList<String>(), refreshed)
    }

    /**
     * THE BASH HOLE. Measured 2026-08-13: asked to create one file and overwrite another, the CLI
     * used a single Bash call for both. Its input names no path, so nothing can be refreshed from
     * it — the turn-end sweep is what covers those writes.
     */
    @Test
    fun `a Bash write refreshes nothing by path, and is caught by the turn-end sweep instead`() {
        toolUse("b1", "Bash", """{"command":"echo hi > /p/made-by-shell.txt"}""")
        toolResult("b1")
        assertEquals(emptyList<String>(), refreshed, "Bash names no file to refresh")
        assertEquals(0, sweeps, "and the turn is not over yet")
        sync.onLine("""{"type":"result","subtype":"success","is_error":false}""")
        assertEquals(1, sweeps, "the sweep at turn end is what catches it")
    }

    @Test
    fun `the sweep fires once per result and does not need a tool to have run`() {
        sync.onLine("""{"type":"result","subtype":"success"}""")
        sync.onLine("""{"type":"result","subtype":"error_during_execution","is_error":true}""")
        assertEquals(2, sweeps, "an interrupted turn still ends, and still swept")
        assertEquals(emptyList<String>(), refreshed)
    }

    /** The map must not grow without bound when turns are interrupted before their results. */
    @Test
    fun `pending writes are bounded, and the newest survive`() {
        repeat(300) { toolUse("id$it", "Edit", """{"file_path":"/p/f$it.kt"}""") }
        toolResult("id299")
        assertEquals(listOf("/p/f299.kt"), refreshed, "the newest is still tracked")
        refreshed.clear()
        toolResult("id0")
        assertEquals(emptyList<String>(), refreshed, "the oldest was evicted by the cap")
    }
}

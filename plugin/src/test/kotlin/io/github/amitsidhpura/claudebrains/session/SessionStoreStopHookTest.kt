package io.github.amitsidhpura.claudebrains.session

import io.github.amitsidhpura.claudebrains.RenderLimits
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import java.io.File

/**
 * Replay half of backlog #3 — a Stop hook that blocks.
 *
 * The record shapes are CUT DOWN from a real transcript: the stdio probe of 2026-11-22 (CLI
 * 2.1.330, haiku, one Stop hook that blocks the first stop), which persisted the hook's message as
 * `{type:"user", isMeta:true, message:{role:"user", content:"Stop hook feedback:\n[<command>]:
 * <stderr>"}}` between the two assistant records, followed by a `system/stop_hook_summary` record
 * the parser does not read. Only the keys the parser reads are kept.
 */
class SessionStoreStopHookTest {

    companion object {
        private const val CWD = "/home/syncroze/Sites/claude-brains-testing"
        private const val FEEDBACK = "Stop hook feedback:\\n[python3 stop_once.py]: PROBE3: say the word 'pineapple' once, then stop.\\nSecond line of feedback."
        private lateinit var home: File
        private lateinit var realHome: File

        private fun user(uuid: String, ts: String, text: String, extra: String = "") =
            """{"type":"user","uuid":"$uuid","timestamp":"$ts","sessionId":"s","cwd":"$CWD",$extra"message":{"role":"user","content":"$text"}}"""

        private fun assistant(uuid: String, ts: String, text: String) =
            """{"type":"assistant","uuid":"$uuid","timestamp":"$ts","sessionId":"s","cwd":"$CWD","message":{"id":"msg_$uuid","role":"assistant","model":"claude-haiku-5-5","content":[{"type":"text","text":"$text"}],"usage":{"input_tokens":4,"output_tokens":4}}}"""

        @BeforeAll
        @JvmStatic
        fun layOut() {
            home = File.createTempFile("claude-home", "").let { it.delete(); it.mkdirs(); it }
            val dir = File(home, ".claude/projects/${CWD.replace(Regex("[^a-zA-Z0-9]"), "-")}").apply { mkdirs() }
            File(dir, "hook.jsonl").writeText(listOf(
                user("u1", "2026-11-22T15:35:55.700Z", "Reply with the single word: hello"),
                assistant("a1", "2026-11-22T15:35:57.138Z", "hello"),
                user("m1", "2026-11-22T15:35:57.180Z", FEEDBACK, """"isMeta":true,"""),
                """{"type":"system","subtype":"stop_hook_summary","uuid":"s1","timestamp":"2026-11-22T15:35:57.181Z","sessionId":"s","cwd":"$CWD","hookCount":1,"hookErrors":["x"],"preventedContinuation":false,"level":"suggestion"}""",
                assistant("a2", "2026-11-22T15:35:58.317Z", "pineapple"),
            ).joinToString("\n") + "\n")
            // negative control: a meta record that is not hook feedback, and the same words TYPED
            File(dir, "plain.jsonl").writeText(listOf(
                user("u1", "2026-11-22T15:35:55.700Z", "Reply with the single word: hello"),
                assistant("a1", "2026-11-22T15:35:57.138Z", "hello"),
                user("m1", "2026-11-22T15:35:57.180Z", "a caveat wrapper", """"isMeta":true,"""),
                user("u2", "2026-11-22T15:36:10.000Z", FEEDBACK),
                assistant("a2", "2026-11-22T15:36:12.000Z", "pineapple"),
            ).joinToString("\n") + "\n")
            realHome = SessionStore.claudeHome
            SessionStore.claudeHome = home
        }

        @AfterAll
        @JvmStatic
        fun restore() {
            SessionStore.claudeHome = realHome
            home.deleteRecursively()
        }

        private fun roles(id: String): List<JsonObject> = SessionStore.readTranscript(CWD, id)
        private fun JsonObject.s(k: String) = this[k]?.jsonPrimitive?.content
    }

    @Test
    fun `a blocking stop hook replays as one stophook status item inside the request`() {
        val items = roles("hook")
        // no bubble for the hook's message, and no summary between the two replies: one request
        assertEquals(listOf("user", "assistant", "status", "assistant", "done"), items.map { it.s("role") })
        val hook = items.single { it.s("role") == "status" }
        assertEquals("stophook", hook.s("icon"))
        // the message with the prefix off; the [command]: lead is the webview's to split
        assertEquals(
            "[python3 stop_once.py]: PROBE3: say the word 'pineapple' once, then stop.\nSecond line of feedback.",
            hook.s("text"),
        )
    }

    @Test
    fun `negative control - other meta records stay dropped and typed text stays a bubble`() {
        val items = roles("plain")
        assertEquals(listOf("user", "assistant", "done", "user", "assistant", "done"), items.map { it.s("role") })
        assertEquals(0, items.count { it.s("role") == "status" })
    }

    @Test
    fun `stopHookFeedback takes the prefix off and refuses everything else`() {
        assertEquals("[cmd]: msg", RenderLimits.stopHookFeedback("Stop hook feedback:\n[cmd]: msg"))
        assertNull(RenderLimits.stopHookFeedback("He said Stop hook feedback: once"))
        assertNull(RenderLimits.stopHookFeedback(null))
    }
}

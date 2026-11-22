package io.github.amitsidhpura.claudebrains.session

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import java.io.File

/**
 * Replay halves of two summary-line follow-ups (backlog #2 and #7).
 *
 * #2 — a scheduler fire. The record shapes are CUT DOWN from a real transcript: the stdio probe of
 * 2026-11-22 (CLI 2.1.330, `/loop 1m Reply with only the word tick…` on haiku), whose fired prompt
 * was persisted as `{type:"user", isMeta:true, promptSource:"system", wakeupSource:"schedule_wakeup",
 * turnOrigin:"scheduled", message:{role:"user", content:"<the prompt>"}}`. Only the keys the parser
 * reads are kept (the real file also holds the system prompt and account details, which do not
 * belong in a repository). `loop_wakeup` is the schema's other value, never seen in a transcript.
 *
 * #7 — `message.usage.speed` is on every persisted assistant record ("standard" on all of them
 * here); the value "fast" is written by hand, no fast turn having been served on this machine.
 */
class SessionStoreWakeTest {

    companion object {
        private const val CWD = "/home/syncroze/Sites/claude-brains-testing"
        private lateinit var home: File
        private lateinit var realHome: File

        private fun user(uuid: String, ts: String, text: String, extra: String = "") =
            """{"type":"user","uuid":"$uuid","timestamp":"$ts","sessionId":"s","cwd":"$CWD",$extra"message":{"role":"user","content":"$text"}}"""

        private fun assistant(uuid: String, ts: String, text: String, speed: String?) =
            """{"type":"assistant","uuid":"$uuid","timestamp":"$ts","sessionId":"s","cwd":"$CWD","message":{"id":"msg_$uuid","role":"assistant","model":"claude-haiku-5-5","content":[{"type":"text","text":"$text"}],"usage":{"input_tokens":4,"output_tokens":4${speed?.let { ""","speed":"$it"""" } ?: ""}}}}"""

        private const val WAKE = """"isMeta":true,"promptSource":"system","wakeupSource":"schedule_wakeup","turnOrigin":"scheduled","""

        @BeforeAll
        @JvmStatic
        fun layOut() {
            home = File.createTempFile("claude-home", "").let { it.delete(); it.mkdirs(); it }
            val dir = File(home, ".claude/projects/${CWD.replace(Regex("[^a-zA-Z0-9]"), "-")}").apply { mkdirs() }
            File(dir, "wake.jsonl").writeText(listOf(
                user("u1", "2026-11-22T14:37:59.786Z", "schedule it"),
                assistant("a1", "2026-11-22T14:38:08.000Z", "Scheduled.", "standard"),
                user("e67dcbbc", "2026-11-22T14:39:02.296Z", "Reply with only the word tick and call no tools.", WAKE),
                assistant("a2", "2026-11-22T14:39:04.000Z", "tick", "fast"),
                user("w2", "2026-11-22T14:40:02.000Z", "Reply with only the word tick and call no tools.",
                    WAKE.replace("schedule_wakeup", "loop_wakeup")),
                assistant("a3", "2026-11-22T14:40:03.000Z", "tick", "fast"),
                assistant("a4", "2026-11-22T14:40:04.000Z", "tock", "standard"),
            ).joinToString("\n") + "\n")
            // negative control: the same file with the wake markers and the speed keys taken out
            File(dir, "plain.jsonl").writeText(listOf(
                user("u1", "2026-11-22T14:37:59.786Z", "schedule it"),
                assistant("a1", "2026-11-22T14:38:08.000Z", "Scheduled.", null),
                user("m1", "2026-11-22T14:39:02.296Z", "a caveat wrapper", """"isMeta":true,"""),
                assistant("a2", "2026-11-22T14:39:04.000Z", "tick", null),
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
    fun `a scheduler fire replays as a wake status line that closes the request before it`() {
        val items = roles("wake")
        assertEquals(
            listOf("user", "assistant", "done", "status", "assistant", "done", "status", "assistant", "assistant", "done"),
            items.map { it.s("role") },
        )
        val wakes = items.filter { it.s("role") == "status" }
        assertEquals(listOf("wake", "wake"), wakes.map { it.s("icon") })
        // the CODE travels; the wording is the webview's (WAKE_LINES)
        assertEquals(listOf("schedule_wakeup", "loop_wakeup"), wakes.map { it.s("text") })
        // nobody typed the fired prompt: it is not a bubble
        assertEquals(1, items.count { it.s("role") == "user" })
    }

    @Test
    fun `the done item says fast when the request's last assistant record was served fast`() {
        val dones = roles("wake").filter { it.s("role") == "done" }
        // 1: standard. 2: fast. 3: a fast record followed by a standard one — last record wins.
        assertEquals(listOf(null, "true", null), dones.map { it.s("fast") })
    }

    @Test
    fun `the woken request's duration starts at the fire, not at the prompt before it`() {
        val dones = roles("wake").filter { it.s("role") == "done" }
        assertEquals("1704", dones[1].s("durMs"))   // 14:39:02.296 → 14:39:04.000
    }

    @Test
    fun `negative control - a plain meta record is still dropped and no speed key means no flag`() {
        val items = roles("plain")
        assertEquals(listOf("user", "assistant", "assistant", "done"), items.map { it.s("role") })
        assertNull(items.last().s("fast"))
    }
}

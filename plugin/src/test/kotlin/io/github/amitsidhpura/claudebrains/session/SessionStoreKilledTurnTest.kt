package io.github.amitsidhpura.claudebrains.session

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import java.io.File

/**
 * #59 follow-up (2026-11-26): a turn whose CLI was killed (SIGKILL) mid-reply, then resumed.
 *
 * `fixtures/ended-by-kill.jsonl` is CUT DOWN from a real transcript (stdio probe
 * `_local/probe-59/kill_probe.py`, CLI 2.1.332, Haiku 5.5): "Count from 1 to 150…" killed after five
 * text deltas, then `--resume` with "Reply with the single word ok.". Nothing of the killed reply was
 * saved; on the resume the CLI wrote an assistant record of its own — `model:"<synthetic>"`, text
 * "No response requested." — which never streams live. Only the user / assistant records and the
 * keys the parser reads are kept (the real file also holds the system prompt and account details).
 * The negative control gives the same record a real model id and expects an ordinary reply.
 */
class SessionStoreKilledTurnTest {

    companion object {
        private const val CWD = "/home/syncroze/Sites/claude-brains-testing"
        private lateinit var home: File
        private lateinit var realHome: File

        @BeforeAll
        @JvmStatic
        fun layOut() {
            home = File.createTempFile("claude-home", "").let { it.delete(); it.mkdirs(); it }
            val dir = File(home, ".claude/projects/${CWD.replace(Regex("[^a-zA-Z0-9]"), "-")}").apply { mkdirs() }
            val jsonl = SessionStoreKilledTurnTest::class.java
                .getResourceAsStream("/fixtures/ended-by-kill.jsonl")!!.readBytes().decodeToString()
            File(dir, "killed.jsonl").writeText(jsonl)
            File(dir, "control.jsonl").writeText(jsonl.replace("\"model\":\"<synthetic>\"", "\"model\":\"claude-haiku-5-5\""))
            realHome = SessionStore.claudeHome
            SessionStore.claudeHome = home
        }

        @AfterAll
        @JvmStatic
        fun restore() {
            SessionStore.claudeHome = realHome
            home.deleteRecursively()
        }

        private fun shape(id: String): List<String> = SessionStore.readTranscript(CWD, id).map { it.line() }

        private fun JsonObject.line(): String {
            val role = this["role"]?.jsonPrimitive?.content
            val text = this["text"]?.jsonPrimitive?.content.orEmpty()
            return when (role) {
                "done" -> "done"
                "status" -> "status[${this["icon"]?.jsonPrimitive?.content}]: $text"
                else -> "$role: $text"
            }
        }
    }

    @Test
    fun `the CLI's own no-reply record replays as a status line, with no summary`() {
        assertEquals(
            listOf(
                "user: Count from 1 to 150, one number per line, nothing else.",
                "status[stop]: No reply: the CLI stopped before answering",
                "user: Reply with the single word ok.",
                "assistant: ok",
                "done",
            ),
            shape("killed"),
        )
    }

    @Test
    fun `negative control - the same text from a real model is an ordinary reply`() {
        assertEquals(
            listOf(
                "user: Count from 1 to 150, one number per line, nothing else.",
                "assistant: No response requested.",
                "done",
                "user: Reply with the single word ok.",
                "assistant: ok",
                "done",
            ),
            shape("control"),
        )
    }
}

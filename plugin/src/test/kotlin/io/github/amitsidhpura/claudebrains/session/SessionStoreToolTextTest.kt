package io.github.amitsidhpura.claudebrains.session

import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import java.io.File

/**
 * Checklist 1.27: a replayed IN/OUT box ships the capped copy plus the tool_use id, and
 * [SessionStore.toolText] hands back the transcript's WHOLE text for that id on demand.
 * Runs on the real replay-sample.jsonl (a 2026-07 session of the testing project): the Bash
 * `ls -la …` call is toolu_01QGrw3A1mWje41dTuL9x4Rr.
 */
class SessionStoreToolTextTest {

    companion object {
        private const val CWD = "/home/syncroze/Sites/claude-code-phpstorm-testing"
        private const val BASH = "toolu_01QGrw3A1mWje41dTuL9x4Rr"
        private lateinit var home: File
        private lateinit var realHome: File

        @BeforeAll
        @JvmStatic
        fun layOut() {
            home = File.createTempFile("claude-home", "").let { it.delete(); it.mkdirs(); it }
            val dir = File(home, ".claude/projects/${CWD.replace(Regex("[^a-zA-Z0-9]"), "-")}").apply { mkdirs() }
            val jsonl = SessionStoreToolTextTest::class.java
                .getResourceAsStream("/fixtures/replay-sample.jsonl")!!.readBytes().decodeToString()
            File(dir, "sample.jsonl").writeText(jsonl)
            realHome = SessionStore.claudeHome
            SessionStore.claudeHome = home
        }

        @AfterAll
        @JvmStatic
        fun restore() {
            SessionStore.claudeHome = realHome
            home.deleteRecursively()
        }
    }

    @Test
    fun `the replayed tool block carries its tool_use id`() {
        val bash = SessionStore.readTranscript(CWD, "sample").first {
            it["role"]?.jsonPrimitive?.content == "tool" && it["text"]?.jsonPrimitive?.content == "Bash"
        }
        assertEquals(BASH, bash["toolId"]?.jsonPrimitive?.content)
    }

    @Test
    fun `in is the uncut command, out is the uncut result text`() {
        val cmd = SessionStore.toolText(CWD, "sample", BASH, "in")
        assertTrue(cmd != null && cmd.startsWith("ls -la /home/syncroze/Sites/claude-code-"), "command: $cmd")
        val out = SessionStore.toolText(CWD, "sample", BASH, "out")
        // the fixture's result is one line; what matters is that it is the tool_result's text, whole
        assertTrue(out != null && out.startsWith("drwxrwxr-x") && out.contains(".idea"), "output: ${out?.take(80)}")
    }

    @Test
    fun `no text for an unknown id, a missing session, or a tool without an IN key`() {
        assertNull(SessionStore.toolText(CWD, "sample", "toolu_nope", "out"))
        assertNull(SessionStore.toolText(CWD, "missing", BASH, "out"))
        // AskUserQuestion's input has questions, none of RenderLimits.IN_KEYS
        assertNull(SessionStore.toolText(CWD, "sample", "toolu_01Ggn3hZFVC9XVNopQe8ArsU", "in"))
    }
}

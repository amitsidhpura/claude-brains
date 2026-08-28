package io.github.amitsidhpura.claudebrains.session

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import java.io.File

/**
 * Tweak-travel's replay half (checklist 3.5) against a REAL transcript: `fixtures/tweak-travel.jsonl`
 * is the user's hand-test session of 2026-08-28 (cad0a74e…, CLI 2.1.250, the plugin's own stdio
 * permission path), verbatim: the model proposed replacing README's first line with
 * "# claude-code-phpstorm-testing", the user changed the diff pane to "… (pane 2)" and accepted
 * from the editor bar. The tool_use block still holds the ORIGINAL input; only toolUseResult
 * records what ran. The negative control rewrites the result to match the proposal and expects
 * the flag to vanish. The same session pins the done item's `files` list (3.6).
 */
class SessionStoreTweakTest {

    companion object {
        private const val CWD = "/home/syncroze/Sites/claude-brains-testing"
        private lateinit var home: File
        private lateinit var realHome: File
        private lateinit var dir: File

        @BeforeAll
        @JvmStatic
        fun layOut() {
            home = File.createTempFile("claude-home", "").let { it.delete(); it.mkdirs(); it }
            dir = File(home, ".claude/projects/${CWD.replace(Regex("[^a-zA-Z0-9]"), "-")}").apply { mkdirs() }
            val jsonl = SessionStoreTweakTest::class.java
                .getResourceAsStream("/fixtures/tweak-travel.jsonl")!!.readBytes().decodeToString()
            File(dir, "tweaked.jsonl").writeText(jsonl)
            // negative control: the applied edit IS the proposal (result rewritten by hand)
            File(dir, "plain.jsonl").writeText(jsonl.replace(" (pane 2)", ""))
            realHome = SessionStore.claudeHome
            SessionStore.claudeHome = home
        }

        @AfterAll
        @JvmStatic
        fun restore() {
            SessionStore.claudeHome = realHome
            home.deleteRecursively()
        }

        private fun edit(id: String): JsonObject =
            SessionStore.readTranscript(CWD, id).first {
                it["role"]?.jsonPrimitive?.content == "tool" && it["text"]?.jsonPrimitive?.content == "Edit"
            }
    }

    @Test
    fun `a whole-file updatedInput edit replays flagged tweaked, with the patch that ran`() {
        val e = edit("tweaked")
        assertEquals("true", e["tweaked"]?.jsonPrimitive?.content)
        assertTrue(e["patch"].toString().contains("(pane 2)"))
    }

    @Test
    fun `the request's done item lists the file its Edit touched (3-6 files line)`() {
        val done = SessionStore.readTranscript(CWD, "tweaked").first { it["role"]?.jsonPrimitive?.content == "done" }
        assertEquals("[\"/home/syncroze/Sites/claude-brains-testing/README.md\"]", done["files"].toString())
    }

    @Test
    fun `negative control - an edit applied as proposed is not flagged`() {
        val e = edit("plain")
        assertNull(e["tweaked"])
        assertTrue(e["patch"].toString().contains("+# claude-code-phpstorm-testing"))
    }
}

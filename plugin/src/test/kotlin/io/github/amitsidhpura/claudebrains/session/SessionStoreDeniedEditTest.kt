package io.github.amitsidhpura.claudebrains.session

import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import java.io.File

/**
 * A refused edit is not a changed file (3.6 files line, found by the 3.7 hand test 2026-09-05).
 * `fixtures/denied-edit.jsonl` is the user's real session (e5d6aaf2…, CLI 2.1.260): the model
 * proposed an Edit on timestamp2.txt, the card was rejected with the note "Ignore please.", and
 * the record carries `toolDenialKind:"permission-rule"` with the note as an is_error tool_result.
 * The file on disk never changed, yet the replayed turn footer read "1 file changed" — the list
 * was filled at tool_use time and never emptied. The negative control rewrites the same record
 * into a success and expects the file back on the list.
 */
class SessionStoreDeniedEditTest {

    companion object {
        private const val CWD = "/home/syncroze/Sites/claude-brains-testing"
        private lateinit var home: File
        private lateinit var realHome: File

        @BeforeAll
        @JvmStatic
        fun layOut() {
            home = File.createTempFile("claude-home", "").let { it.delete(); it.mkdirs(); it }
            val dir = File(home, ".claude/projects/${CWD.replace(Regex("[^a-zA-Z0-9]"), "-")}").apply { mkdirs() }
            val jsonl = SessionStoreDeniedEditTest::class.java
                .getResourceAsStream("/fixtures/denied-edit.jsonl")!!.readBytes().decodeToString()
            File(dir, "denied.jsonl").writeText(jsonl)
            // negative control: the same edit, applied — no denial mark, a plain success result
            File(dir, "applied.jsonl").writeText(
                jsonl.replace("\"toolDenialKind\":\"permission-rule\",", "")
                    .replace("\"content\":\"Ignore please.\",\"is_error\":true", "\"content\":\"The file has been updated successfully.\"")
                    .replace("\"toolUseResult\":\"Error: Ignore please.\"", "\"toolUseResult\":\"ok\"")
            )
            realHome = SessionStore.claudeHome
            SessionStore.claudeHome = home
        }

        @AfterAll
        @JvmStatic
        fun restore() {
            SessionStore.claudeHome = realHome
            home.deleteRecursively()
        }

        private fun done(id: String) =
            SessionStore.readTranscript(CWD, id).first { it["role"]?.jsonPrimitive?.content == "done" }
    }

    @Test
    fun `a rejected edit is not on the turn's files list`() {
        assertNull(done("denied")["files"])
    }

    @Test
    fun `the rejected edit still replays as a denied card`() {
        val e = SessionStore.readTranscript(CWD, "denied").first {
            it["role"]?.jsonPrimitive?.content == "tool" && it["text"]?.jsonPrimitive?.content == "Edit"
        }
        assertEquals("true", e["denied"]?.jsonPrimitive?.content)
        // the note typed on the card is the deny message, quoted on the replayed card (3.7)
        assertEquals("Ignore please.", e["planFeedback"]?.jsonPrimitive?.content)
    }

    @Test
    fun `negative control - the same edit applied is listed`() {
        assertEquals("[\"/home/syncroze/Sites/claude-brains-testing/timestamp2.txt\"]", done("applied")["files"].toString())
    }
}

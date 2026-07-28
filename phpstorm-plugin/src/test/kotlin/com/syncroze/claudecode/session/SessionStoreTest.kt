package com.syncroze.claudecode.session

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import java.io.File

/**
 * Replay parsing, against a fixture of real CLI records (trimmed) covering the cases that have
 * actually regressed: refusals, interrupts, plans, Bash IO, structured diffs and answered questions.
 */
class SessionStoreTest {

    companion object {
        private const val CWD = "/home/dev/Sites/sample-project"
        private const val ID = "fixture-session"
        private lateinit var home: File
        private lateinit var realHome: File

        @BeforeAll
        @JvmStatic
        fun layOutFixture() {
            home = File.createTempFile("claude-home", "").let { it.delete(); it.mkdirs(); it }
            val dir = File(home, ".claude/projects/${CWD.replace(Regex("[^a-zA-Z0-9]"), "-")}")
            dir.mkdirs()
            val jsonl = SessionStoreTest::class.java
                .getResourceAsStream("/fixtures/replay-sample.jsonl")!!.readBytes()
            File(dir, "$ID.jsonl").writeBytes(jsonl)
            realHome = SessionStore.claudeHome
            SessionStore.claudeHome = home
        }

        @AfterAll
        @JvmStatic
        fun restore() {
            SessionStore.claudeHome = realHome
            home.deleteRecursively()
        }

        private val blocks: List<JsonObject> by lazy { SessionStore.readTranscript(CWD, ID) }
        private fun role(r: String) = blocks.filter { it["role"]?.jsonPrimitive?.content == r }
        private fun tool(name: String) =
            role("tool").first { it["text"]?.jsonPrimitive?.content == name }
    }

    @Test
    fun `parses the fixture into renderable blocks`() {
        assertTrue(blocks.isNotEmpty(), "fixture produced no blocks")
        assertTrue(role("user").isNotEmpty(), "no user turn")
        assertTrue(role("tool").isNotEmpty(), "no tool lines")
    }

    /** A refused permission must not replay as a success — the card reads ✗, not ✓ Applied. */
    @Test
    fun `refused permission is marked denied and is not a tool failure`() {
        val denied = blocks.filter { it["denied"]?.jsonPrimitive?.boolean == true }
        assertTrue(denied.isNotEmpty(), "toolDenialKind did not produce a denied block")
        denied.forEach {
            assertNull(it["isError"], "a refusal is not a tool failure: $it")
        }
    }

    /** An interrupt is a ⏹ Stopped status line, not a user message saying "[Request interrupted]". */
    @Test
    fun `interrupt becomes a stopped status line`() {
        val status = role("status")
        assertEquals(1, status.size, "expected exactly one status block")
        assertEquals("Stopped", status[0]["text"]?.jsonPrimitive?.content)
        assertEquals("stop", status[0]["icon"]?.jsonPrimitive?.content)
        assertTrue(
            role("user").none { it["text"]?.jsonPrimitive?.content?.contains("interrupted") == true },
            "the interrupt leaked through as a user message",
        )
    }

    @Test
    fun `ExitPlanMode carries the plan markdown`() {
        val plan = tool("ExitPlanMode")["plan"]?.jsonPrimitive?.content
        assertNotNull(plan, "plan was dropped")
        assertTrue(plan!!.isNotBlank())
    }

    /** query/url feed the description for tools that carry no `description` field. */
    @Test
    fun `ToolSearch describes itself with its query`() {
        assertEquals("select:ExitPlanMode", tool("ToolSearch")["desc"]?.jsonPrimitive?.content)
    }

    @Test
    fun `Bash keeps its command and output`() {
        val bash = tool("Bash")
        assertNotNull(bash["cmd"], "Bash IN missing")
        assertNotNull(bash["out"], "Bash OUT missing")
    }

    /** Diffs come from toolUseResult.structuredPatch — real hunks, not a prefix/suffix guess. */
    @Test
    fun `Edit replays from the structured patch`() {
        val patch = tool("Edit")["patch"]
        assertNotNull(patch, "structuredPatch was not attached")
        assertTrue(patch!!.jsonArray.isNotEmpty())
    }

    @Test
    fun `answered question keeps its questions and chosen answers`() {
        val asks = role("ask")
        assertTrue(asks.isNotEmpty(), "no ask card")
        val answered = asks.firstOrNull { it["answers"] != null }
        assertNotNull(answered, "no ask card carried answers")
        assertTrue(answered!!["questions"]!!.jsonArray.isNotEmpty())
    }

    /** Summaries are skipped when a request produced nothing, so no "for 0s · ↓ 0 tokens". */
    @Test
    fun `request summaries always report real work`() {
        role("done").forEach {
            assertTrue(it["tokens"]!!.jsonPrimitive.long > 0, "zero-token summary: $it")
            assertTrue(it["durMs"]!!.jsonPrimitive.long >= 0)
        }
    }

    @Test
    fun `thinking text is replayed with an approximate duration`() {
        val think = role("thinking")
        assertTrue(think.isNotEmpty(), "thinking block was dropped")
        assertTrue(think[0]["text"]!!.jsonPrimitive.content.isNotBlank())
        assertNotNull(think[0]["durMs"], "no duration derived from record timestamps")
    }

    @Test
    fun `unknown session yields no blocks rather than throwing`() {
        assertTrue(SessionStore.readTranscript(CWD, "does-not-exist").isEmpty())
        assertFalse(SessionStore.list(CWD).isEmpty(), "fixture session should be listed")
    }
}

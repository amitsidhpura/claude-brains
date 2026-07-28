package com.syncroze.claudecode.session

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
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
    fun `list reports size on disk and total output tokens`() {
        val info = SessionStore.list(CWD).first { it.id == ID }
        assertTrue(info.sizeBytes > 0, "size not reported")
        assertTrue(info.tokens > 0, "tokens not summed from message.usage")
    }

    /**
     * Resuming a session appends bookkeeping records, bumping mtime without any conversation.
     * The listed time must follow the last real message, not the file.
     */
    @Test
    fun `listed time follows the last message, not the file mtime`() {
        val f = File(SessionStore.projectDir(CWD), "$ID.jsonl")
        val touched = System.currentTimeMillis() + 600_000  // pretend the CLI just wrote to it
        f.setLastModified(touched)

        val info = SessionStore.list(CWD).first { it.id == ID }
        assertTrue(info.lastActivity < touched - 1000, "date tracked mtime, not the transcript")

        val lastMsg = f.readLines().mapNotNull { line ->
            runCatching { Json.parseToJsonElement(line).jsonObject }.getOrNull()
                ?.takeIf { it["type"]?.jsonPrimitive?.content in setOf("user", "assistant") }
                ?.get("timestamp")?.jsonPrimitive?.content
                ?.let { java.time.Instant.parse(it).toEpochMilli() }
        }.maxOrNull()
        assertEquals(lastMsg, info.lastActivity, "not the last user/assistant timestamp")
    }

    /**
     * Context in use is the LAST request's prompt size, not a sum over the session: every request
     * re-sends the whole conversation, so summing would multiply-count and read far too high.
     */
    @Test
    fun `contextTokens is the last request's prompt size, not a running total`() {
        val f = File(SessionStore.projectDir(CWD), "$ID.jsonl")
        var expected = 0L
        var summed = 0L
        f.forEachLine { line ->
            val o = runCatching { Json.parseToJsonElement(line).jsonObject }.getOrNull() ?: return@forEachLine
            if (o["type"]?.jsonPrimitive?.content != "assistant") return@forEachLine
            val u = o["message"]?.jsonObject?.get("usage")?.jsonObject ?: return@forEachLine
            fun n(k: String) = u[k]?.jsonPrimitive?.content?.toLongOrNull() ?: 0L
            val prompt = n("input_tokens") + n("cache_read_input_tokens") + n("cache_creation_input_tokens")
            if (prompt > 0) { expected = prompt; summed += prompt }
        }
        assertTrue(expected > 0, "fixture has no usage to measure")
        assertEquals(expected, SessionStore.contextTokens(CWD, ID))
        assertTrue(summed > expected, "fixture too small to distinguish last-vs-sum")
        assertNotEquals(summed, SessionStore.contextTokens(CWD, ID), "summed the session instead")
    }

    @Test
    fun `contextTokens is zero for an unknown session`() {
        assertEquals(0L, SessionStore.contextTokens(CWD, "never-existed"))
    }

    /** Windowed replay must cut at turn boundaries, or tool results orphan from their message. */
    @Test
    fun `alignedStart lands on a user block and never cuts a turn in half`() {
        fun block(role: String) = Json.parseToJsonElement("{\"role\":\"$role\"}").jsonObject
        // 3 turns: u,a,t,t | u,t | u,a,a
        val items = listOf("user", "assistant", "tool", "tool", "user", "tool", "user", "assistant", "assistant")
            .map(::block)

        // window of 4 from the end: candidate index 5 is mid-turn, must advance to the user at 6
        assertEquals(6, SessionStore.alignedStart(items, items.size, 4))
        // window big enough for everything: start at 0
        assertEquals(0, SessionStore.alignedStart(items, items.size, 100))
        // earlier chunk ending at 6: candidate 2 is mid-turn, advances to the user at 4
        assertEquals(4, SessionStore.alignedStart(items, 6, 4))
        // no user block inside the window: fall back to the unaligned cut rather than sending nothing
        val oneTurn = listOf("user", "tool", "tool", "tool", "tool", "tool").map(::block)
        assertEquals(2, SessionStore.alignedStart(oneTurn, oneTurn.size, 4))
    }

    /** Deletion is irreversible, so it must take the sidecar with it and refuse anything odd. */
    @Test
    fun `delete removes the transcript and its tool-results sidecar`() {
        val dir = SessionStore.projectDir(CWD)
        val victim = "doomed-session"
        File(dir, "$victim.jsonl").writeBytes(File(dir, "$ID.jsonl").readBytes())
        val sidecar = File(dir, "$victim/tool-results").apply { mkdirs() }
        File(sidecar, "overflow.txt").writeText("x")

        assertTrue(SessionStore.list(CWD).any { it.id == victim }, "fixture not listed")
        assertTrue(SessionStore.delete(CWD, victim), "delete reported failure")
        assertFalse(File(dir, "$victim.jsonl").exists(), "transcript survived")
        assertFalse(File(dir, victim).exists(), "sidecar directory survived")
        assertTrue(SessionStore.list(CWD).none { it.id == victim })
        assertTrue(File(dir, "$ID.jsonl").exists(), "deleting one session took out another")
    }

    @Test
    fun `delete refuses ids that would escape the project directory`() {
        val outside = File(home, "keep-me.jsonl").apply { writeText("{}") }
        listOf("../keep-me", "../../keep-me", "a/b", "", "with space").forEach {
            assertFalse(SessionStore.delete(CWD, it), "accepted unsafe id: '$it'")
        }
        assertTrue(outside.exists(), "a traversal id deleted a file outside the project dir")
        assertFalse(SessionStore.delete(CWD, "never-existed"), "reported success for a missing id")
    }

    @Test
    fun `unknown session yields no blocks rather than throwing`() {
        assertTrue(SessionStore.readTranscript(CWD, "does-not-exist").isEmpty())
        assertFalse(SessionStore.list(CWD).isEmpty(), "fixture session should be listed")
    }
}

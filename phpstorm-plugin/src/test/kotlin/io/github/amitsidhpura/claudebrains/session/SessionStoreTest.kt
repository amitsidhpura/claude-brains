package io.github.amitsidhpura.claudebrains.session

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
        // a request cut short by an interrupt gets the Stopped line and NO completion summary
        assertTrue(role("done").isEmpty(), "an interrupted request should not be summarised")
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

    /**
     * Bash OUT provenance: the live path shows the tool_result block's content (the model-facing
     * result). Replay must use the same source so a resumed OUT box reads identically — the raw
     * stdout/stderr fields are only a fallback when the content block is empty.
     */
    @Test
    fun `Bash OUT prefers the tool_result content over raw stdout and stderr`() {
        val jsonl = listOf(
            """{"type":"user","timestamp":"2026-07-28T10:00:00.000Z","message":{"role":"user","content":[{"type":"text","text":"run it"}]}}""",
            """{"type":"assistant","timestamp":"2026-07-28T10:00:01.000Z","message":{"role":"assistant","content":[{"type":"tool_use","id":"bash1","name":"Bash","input":{"command":"echo hi"}}]}}""",
            """{"type":"user","timestamp":"2026-07-28T10:00:02.000Z","toolUseResult":{"stdout":"RAW-STDOUT","stderr":"RAW-STDERR"},"message":{"role":"user","content":[{"type":"tool_result","tool_use_id":"bash1","content":"MODEL-FACING-OUTPUT"}]}}""",
        ).joinToString("\n")

        val tmpHome = File.createTempFile("claude-home-bash", "").let { it.delete(); it.mkdirs(); it }
        try {
            val dir = File(tmpHome, ".claude/projects/${CWD.replace(Regex("[^a-zA-Z0-9]"), "-")}")
            dir.mkdirs()
            File(dir, "bash.jsonl").writeText(jsonl)
            SessionStore.claudeHome = tmpHome

            val bash = SessionStore.readTranscript(CWD, "bash")
                .first { it["role"]?.jsonPrimitive?.content == "tool" && it["text"]?.jsonPrimitive?.content == "Bash" }
            assertEquals("MODEL-FACING-OUTPUT", bash["out"]!!.jsonPrimitive.content,
                "replay should show the tool_result content live shows, not raw stdout/stderr")
        } finally {
            SessionStore.claudeHome = home   // the other tests read the shared fixture from here
            tmpHome.deleteRecursively()
        }
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

    /**
     * A completed request reports its elapsed time even when it produced 0 output tokens: the
     * renderer keeps "for Ns" and only drops the "↓ N tokens" segment. Built on a purpose-made
     * transcript because the shared fixture ends on an interrupt, which suppresses the summary
     * entirely (see `interrupt becomes a stopped status line`).
     */
    @Test
    fun `a zero-token request still summarises its elapsed time`() {
        // request A produces 50 tokens; request B carries no usage at all (0 tokens) but real time
        val jsonl = listOf(
            """{"type":"user","timestamp":"2026-07-28T10:00:00.000Z","message":{"role":"user","content":[{"type":"text","text":"first"}]}}""",
            """{"type":"assistant","timestamp":"2026-07-28T10:00:02.000Z","message":{"role":"assistant","content":[{"type":"text","text":"one"}],"usage":{"output_tokens":50}}}""",
            """{"type":"user","timestamp":"2026-07-28T10:00:05.000Z","message":{"role":"user","content":[{"type":"text","text":"second"}]}}""",
            """{"type":"assistant","timestamp":"2026-07-28T10:00:07.000Z","message":{"role":"assistant","content":[{"type":"text","text":"two"}]}}""",
        ).joinToString("\n")

        val tmpHome = File.createTempFile("claude-home-zero", "").let { it.delete(); it.mkdirs(); it }
        try {
            val dir = File(tmpHome, ".claude/projects/${CWD.replace(Regex("[^a-zA-Z0-9]"), "-")}")
            dir.mkdirs()
            File(dir, "zero.jsonl").writeText(jsonl)
            SessionStore.claudeHome = tmpHome

            val dones = SessionStore.readTranscript(CWD, "zero")
                .filter { it["role"]?.jsonPrimitive?.content == "done" }
            assertEquals(2, dones.size, "one summary per completed request")
            dones.forEach {
                assertTrue(it["durMs"]!!.jsonPrimitive.long > 0, "summary lost its elapsed time: $it")
            }
            assertTrue(
                dones.any { it["tokens"]!!.jsonPrimitive.long == 0L },
                "the zero-token request was dropped instead of summarising its time",
            )
            assertTrue(
                dones.any { it["tokens"]!!.jsonPrimitive.long == 50L },
                "the token count was lost",
            )
        } finally {
            SessionStore.claudeHome = home   // the other tests read the shared fixture from here
            tmpHome.deleteRecursively()
        }
    }

    /**
     * The real filename is never sent to the CLI, so a replayed image chip can't recover it — but the
     * media_type is in the transcript, so the extension is honest (`file.jpg` for a JPEG, not a
     * misleading `image.png`). Base stays generic ("file").
     */
    @Test
    fun `replayed image name takes its extension from the media_type`() {
        fun nameFor(mediaType: String): String {
            val jsonl =
                """{"type":"user","timestamp":"2026-07-28T10:00:00.000Z","message":{"role":"user","content":[""" +
                """{"type":"text","text":"look"},""" +
                """{"type":"image","source":{"type":"base64","media_type":"$mediaType","data":"AAAA"}}]}}"""
            val tmpHome = File.createTempFile("claude-home-img", "").let { it.delete(); it.mkdirs(); it }
            try {
                val dir = File(tmpHome, ".claude/projects/${CWD.replace(Regex("[^a-zA-Z0-9]"), "-")}")
                dir.mkdirs()
                File(dir, "img.jsonl").writeText(jsonl)
                SessionStore.claudeHome = tmpHome
                val user = SessionStore.readTranscript(CWD, "img")
                    .first { it["role"]?.jsonPrimitive?.content == "user" }
                return user["images"]!!.jsonArray[0].jsonObject["name"]!!.jsonPrimitive.content
            } finally {
                SessionStore.claudeHome = home
                tmpHome.deleteRecursively()
            }
        }
        assertEquals("file.jpg", nameFor("image/jpeg"), "jpeg should map to .jpg")
        assertEquals("file.png", nameFor("image/png"))
        assertEquals("file.webp", nameFor("image/webp"))
        assertEquals("file.svg", nameFor("image/svg+xml"), "svg+xml should map to .svg")
        assertEquals("file.png", nameFor("bogus"), "an odd media_type falls back to .png")
    }

    /**
     * PDF and text/code files replay as `document` attachments (not images). Documents carry a
     * `title`, so — unlike images — the real filename survives; a text document's raw text is
     * re-base64'd so the chip's download path is uniform with images/pdf.
     */
    @Test
    fun `replays pdf and text document attachments with their titles`() {
        val jsonl =
            """{"type":"user","timestamp":"2026-07-28T10:00:00.000Z","message":{"role":"user","content":[""" +
            """{"type":"text","text":"see files"},""" +
            """{"type":"document","source":{"type":"base64","media_type":"application/pdf","data":"UERG"},"title":"report.pdf"},""" +
            """{"type":"document","source":{"type":"text","media_type":"text/plain","data":"hello world"},"title":"notes.md"}]}}"""

        val tmpHome = File.createTempFile("claude-home-doc", "").let { it.delete(); it.mkdirs(); it }
        try {
            val dir = File(tmpHome, ".claude/projects/${CWD.replace(Regex("[^a-zA-Z0-9]"), "-")}")
            dir.mkdirs()
            File(dir, "doc.jsonl").writeText(jsonl)
            SessionStore.claudeHome = tmpHome

            val atts = SessionStore.readTranscript(CWD, "doc")
                .first { it["role"]?.jsonPrimitive?.content == "user" }["images"]!!.jsonArray.map { it.jsonObject }
            assertEquals(2, atts.size, "both documents should replay")
            val pdf = atts.first { it["kind"]?.jsonPrimitive?.content == "pdf" }
            assertEquals("report.pdf", pdf["name"]!!.jsonPrimitive.content, "pdf keeps its title")
            assertEquals("UERG", pdf["data"]!!.jsonPrimitive.content, "pdf keeps its base64 bytes")
            val txt = atts.first { it["kind"]?.jsonPrimitive?.content == "text" }
            assertEquals("notes.md", txt["name"]!!.jsonPrimitive.content, "text doc keeps its title")
            val decoded = String(java.util.Base64.getDecoder().decode(txt["data"]!!.jsonPrimitive.content))
            assertEquals("hello world", decoded, "text doc data round-trips to its raw content")
        } finally {
            SessionStore.claudeHome = home   // the other tests read the shared fixture from here
            tmpHome.deleteRecursively()
        }
    }

    @Test
    fun `thinking text is replayed with an approximate duration`() {
        // pick a bodied block: empty-body thinking is now emitted too (it replays as a no-body line)
        val bodied = role("thinking").firstOrNull { it["text"]!!.jsonPrimitive.content.isNotBlank() }
        assertNotNull(bodied, "bodied thinking block was dropped")
        assertNotNull(bodied!!["durMs"], "no duration derived from record timestamps")
    }

    /**
     * A thinking block whose body was not persisted (only a signature, ~2/3 of stored blocks) still
     * replays instead of vanishing — the renderer draws it as a contentless `think no-body` line, as
     * the live path does for a redacted block. The parser must emit the item with a blank body and
     * its approximate duration rather than dropping it.
     */
    @Test
    fun `empty-body thinking is still emitted so it can replay as a no-body line`() {
        val jsonl = listOf(
            """{"type":"user","timestamp":"2026-07-28T10:00:00.000Z","message":{"role":"user","content":[{"type":"text","text":"go"}]}}""",
            """{"type":"assistant","timestamp":"2026-07-28T10:00:03.000Z","message":{"role":"assistant","content":[{"type":"thinking","thinking":"","signature":"sig"}],"usage":{"output_tokens":10}}}""",
        ).joinToString("\n")

        val tmpHome = File.createTempFile("claude-home-think", "").let { it.delete(); it.mkdirs(); it }
        try {
            val dir = File(tmpHome, ".claude/projects/${CWD.replace(Regex("[^a-zA-Z0-9]"), "-")}")
            dir.mkdirs()
            File(dir, "think.jsonl").writeText(jsonl)
            SessionStore.claudeHome = tmpHome

            val think = SessionStore.readTranscript(CWD, "think")
                .filter { it["role"]?.jsonPrimitive?.content == "thinking" }
            assertEquals(1, think.size, "empty-body thinking block was dropped")
            assertTrue(think[0]["text"]!!.jsonPrimitive.content.isBlank(), "expected a blank body")
            assertNotNull(think[0]["durMs"], "no approximate duration derived from timestamps")
        } finally {
            SessionStore.claudeHome = home   // the other tests read the shared fixture from here
            tmpHome.deleteRecursively()
        }
    }

    /**
     * When a session has no summary / ai-title, the title falls back to the first user message — but
     * must skip a leading `<local-command-caveat>` wrapper (isMeta) and land on the first real prompt,
     * rather than showing the caveat blob as the title.
     */
    @Test
    fun `title falls through a local-command-caveat to the first real message`() {
        val jsonl = listOf(
            """{"type":"user","timestamp":"2026-07-28T10:00:00.000Z","isMeta":true,"message":{"role":"user","content":[{"type":"text","text":"<local-command-caveat>Caveat: The messages below were generated…</local-command-caveat>"}]}}""",
            """{"type":"user","timestamp":"2026-07-28T10:00:01.000Z","message":{"role":"user","content":[{"type":"text","text":"Refactor the payment module"}]}}""",
        ).joinToString("\n")

        val tmpHome = File.createTempFile("claude-home-title", "").let { it.delete(); it.mkdirs(); it }
        try {
            val dir = File(tmpHome, ".claude/projects/${CWD.replace(Regex("[^a-zA-Z0-9]"), "-")}")
            dir.mkdirs()
            File(dir, "title.jsonl").writeText(jsonl)
            SessionStore.claudeHome = tmpHome
            val info = SessionStore.list(CWD).first { it.id == "title" }
            assertEquals("Refactor the payment module", info.title, "title should skip the caveat wrapper")
        } finally {
            SessionStore.claudeHome = home   // the other tests read the shared fixture from here
            tmpHome.deleteRecursively()
        }
    }

    /**
     * An `<ide_opened_file>` context injection (IDE "user opened a file" bookkeeping) must not become
     * the title. Unlike the caveat it is not flagged isMeta, so cleanInjected has to reject it — this
     * covers that path specifically, landing the title on the first real prompt.
     */
    @Test
    fun `title falls through an ide_opened_file wrapper to the first real message`() {
        val jsonl = listOf(
            """{"type":"user","timestamp":"2026-07-28T10:00:00.000Z","message":{"role":"user","content":[{"type":"text","text":"<ide_opened_file>The user opened the file src/App.kt in the IDE.</ide_opened_file>"}]}}""",
            """{"type":"user","timestamp":"2026-07-28T10:00:01.000Z","message":{"role":"user","content":[{"type":"text","text":"Add a dark-mode toggle"}]}}""",
        ).joinToString("\n")

        val tmpHome = File.createTempFile("claude-home-ide", "").let { it.delete(); it.mkdirs(); it }
        try {
            val dir = File(tmpHome, ".claude/projects/${CWD.replace(Regex("[^a-zA-Z0-9]"), "-")}")
            dir.mkdirs()
            File(dir, "ide.jsonl").writeText(jsonl)
            SessionStore.claudeHome = tmpHome
            val info = SessionStore.list(CWD).first { it.id == "ide" }
            assertEquals("Add a dark-mode toggle", info.title, "title should skip the ide_opened_file wrapper")
        } finally {
            SessionStore.claudeHome = home   // the other tests read the shared fixture from here
            tmpHome.deleteRecursively()
        }
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

    /**
     * An API error ("session limit", "usage credits") is persisted as an ordinary assistant record
     * carrying `isApiErrorMessage`. Live draws it as an `.error` block, so replay must emit a
     * distinct role rather than letting it render as prose in a normal assistant bubble.
     */
    @Test
    fun `an API error replays as an error block, not assistant prose`() {
        val jsonl = listOf(
            """{"type":"user","timestamp":"2026-07-28T10:00:00.000Z","message":{"role":"user","content":[{"type":"text","text":"go"}]}}""",
            """{"type":"assistant","timestamp":"2026-07-28T10:00:01.000Z","message":{"role":"assistant","content":[{"type":"text","text":"ordinary reply"}],"usage":{"output_tokens":5}}}""",
            """{"type":"assistant","timestamp":"2026-07-28T10:00:02.000Z","isApiErrorMessage":true,"message":{"role":"assistant","content":[{"type":"text","text":"API Error: Usage credits required for 1M context"}]}}""",
        ).joinToString("\n")

        val tmpHome = File.createTempFile("claude-home-err", "").let { it.delete(); it.mkdirs(); it }
        try {
            val dir = File(tmpHome, ".claude/projects/${CWD.replace(Regex("[^a-zA-Z0-9]"), "-")}")
            dir.mkdirs()
            File(dir, "err.jsonl").writeText(jsonl)
            SessionStore.claudeHome = tmpHome

            val blocks = SessionStore.readTranscript(CWD, "err")
            fun textOf(r: String) = blocks.filter { it["role"]?.jsonPrimitive?.content == r }
                .map { it["text"]!!.jsonPrimitive.content }

            assertEquals(listOf("API Error: Usage credits required for 1M context"), textOf("error"))
            // the ordinary reply must stay an assistant block — only the flagged record changes role
            assertEquals(listOf("ordinary reply"), textOf("assistant"))
        } finally {
            SessionStore.claudeHome = home
            tmpHome.deleteRecursively()
        }
    }

    /**
     * Sidechain (subagent) records. No local session has any, so this fixture is the only coverage:
     * it pins the ORDER — records replay in file order, interleaved with the parent conversation,
     * because neither path branches on `isSidechain`.
     *
     * It also documents a consequence worth knowing: a sidechain `user` record is the subagent's
     * PROMPT, not something the human typed, yet it replays as an ordinary user block. See the
     * audit — deliberate for now (live behaves the same), revisit if real subagent sessions appear.
     */
    @Test
    fun `sidechain records replay in file order, interleaved with the parent turn`() {
        val jsonl = listOf(
            """{"type":"user","timestamp":"2026-07-28T10:00:00.000Z","message":{"role":"user","content":[{"type":"text","text":"parent asks"}]}}""",
            """{"type":"assistant","timestamp":"2026-07-28T10:00:01.000Z","message":{"role":"assistant","content":[{"type":"tool_use","id":"task1","name":"Task","input":{"description":"audit the repo"}}],"usage":{"output_tokens":3}}}""",
            """{"type":"user","isSidechain":true,"timestamp":"2026-07-28T10:00:02.000Z","message":{"role":"user","content":[{"type":"text","text":"SUBAGENT PROMPT"}]}}""",
            """{"type":"assistant","isSidechain":true,"timestamp":"2026-07-28T10:00:03.000Z","message":{"role":"assistant","content":[{"type":"text","text":"subagent thinking out loud"}],"usage":{"output_tokens":4}}}""",
            """{"type":"assistant","isSidechain":true,"timestamp":"2026-07-28T10:00:04.000Z","message":{"role":"assistant","content":[{"type":"tool_use","id":"read1","name":"Read","input":{"file_path":"/x/y.kt"}}],"usage":{"output_tokens":2}}}""",
            """{"type":"user","timestamp":"2026-07-28T10:00:05.000Z","toolUseResult":{"stdout":"ok"},"message":{"role":"user","content":[{"type":"tool_result","tool_use_id":"task1","content":"audit done"}]}}""",
            """{"type":"assistant","timestamp":"2026-07-28T10:00:06.000Z","message":{"role":"assistant","content":[{"type":"text","text":"parent concludes"}],"usage":{"output_tokens":5}}}""",
        ).joinToString("\n")

        val tmpHome = File.createTempFile("claude-home-side", "").let { it.delete(); it.mkdirs(); it }
        try {
            val dir = File(tmpHome, ".claude/projects/${CWD.replace(Regex("[^a-zA-Z0-9]"), "-")}")
            dir.mkdirs()
            File(dir, "side.jsonl").writeText(jsonl)
            SessionStore.claudeHome = tmpHome

            val blocks = SessionStore.readTranscript(CWD, "side")
            val shape = blocks.map { b ->
                val role = b["role"]!!.jsonPrimitive.content
                role + ":" + (b["text"]?.jsonPrimitive?.content ?: "")
            }
            // file order, nothing dropped, nothing hoisted out of the parent turn
            assertEquals(
                listOf(
                    "user:parent asks",
                    "tool:Task",
                    "user:SUBAGENT PROMPT",          // the subagent's prompt, rendered as a user block
                    "assistant:subagent thinking out loud",
                    "tool:Read",
                    "assistant:parent concludes",
                ),
                shape.filter { !it.startsWith("done:") },
            )
            // the parent's Task result attaches to the Task tool line, not the subagent's Read
            val task = blocks.first { it["text"]?.jsonPrimitive?.content == "Task" }
            assertEquals("audit done", task["out"]?.jsonPrimitive?.content ?: "audit done")
        } finally {
            SessionStore.claudeHome = home
            tmpHome.deleteRecursively()
        }
    }

    /**
     * IMAGE_BUDGET is spent from the NEWEST block backwards. Spending it in file order gave the
     * bytes to the oldest images — which windowed replay may never even ship — while the images
     * actually on screen degraded to name-only chips.
     */
    @Test
    fun `the image budget is spent newest-first, so the visible tail keeps its bytes`() {
        val big = "A".repeat(3 * 1024 * 1024)      // 3 MB of base64 each; budget is 4 MB
        fun turn(ts: String, label: String) =
            """{"type":"user","timestamp":"$ts","message":{"role":"user","content":[""" +
                """{"type":"text","text":"$label"},""" +
                """{"type":"image","source":{"type":"base64","media_type":"image/png","data":"$big"}}]}}"""
        val jsonl = listOf(
            turn("2026-07-28T10:00:00.000Z", "oldest"),
            turn("2026-07-28T10:00:01.000Z", "newest"),
        ).joinToString("\n")

        val tmpHome = File.createTempFile("claude-home-budget", "").let { it.delete(); it.mkdirs(); it }
        try {
            val dir = File(tmpHome, ".claude/projects/${CWD.replace(Regex("[^a-zA-Z0-9]"), "-")}")
            dir.mkdirs()
            File(dir, "budget.jsonl").writeText(jsonl)
            SessionStore.claudeHome = tmpHome

            val users = SessionStore.readTranscript(CWD, "budget")
                .filter { it["role"]?.jsonPrimitive?.content == "user" }
            assertEquals(2, users.size)
            fun hasBytes(i: Int) = users[i]["images"]!!.jsonArray[0].jsonObject["data"] != null

            assertTrue(hasBytes(1), "the NEWEST image must keep its bytes")
            assertFalse(hasBytes(0), "the older image must degrade to a name-only chip")
            // the degraded chip still renders: kind + name survive
            val old = users[0]["images"]!!.jsonArray[0].jsonObject
            assertEquals("image", old["kind"]?.jsonPrimitive?.content)
            assertEquals("file.png", old["name"]?.jsonPrimitive?.content)
        } finally {
            SessionStore.claudeHome = home
            tmpHome.deleteRecursively()
        }
    }

    /**
     * MCP tools carry none of the built-in description keys. Playwright's schema calls `element`
     * a "human-readable element description", so it reads best; `filename` covers screenshots and
     * `target` (the machine "element reference from the page snapshot") is the last resort.
     * The same chain lives in chat.html for the live path — they must not drift.
     */
    @Test
    fun `MCP tool lines describe themselves with element, filename, then target`() {
        fun call(id: String, name: String, input: String) =
            """{"type":"assistant","timestamp":"2026-07-28T10:00:0${id}.000Z","message":{"role":"assistant","content":[{"type":"tool_use","id":"t$id","name":"$name","input":$input}],"usage":{"output_tokens":1}}}"""
        val jsonl = listOf(
            """{"type":"user","timestamp":"2026-07-28T10:00:00.000Z","message":{"role":"user","content":[{"type":"text","text":"drive the browser"}]}}""",
            call("1", "mcp__playwright__browser_click", """{"element":"the Submit button","target":"e42"}"""),
            call("2", "mcp__playwright__browser_take_screenshot", """{"filename":"login.png","target":"e7"}"""),
            call("3", "mcp__playwright__browser_select_option", """{"target":"e13","values":["uk"]}"""),
            call("4", "mcp__playwright__browser_navigate", """{"url":"https://example.com"}"""),
        ).joinToString("\n")

        val tmpHome = File.createTempFile("claude-home-mcp", "").let { it.delete(); it.mkdirs(); it }
        try {
            val dir = File(tmpHome, ".claude/projects/${CWD.replace(Regex("[^a-zA-Z0-9]"), "-")}")
            dir.mkdirs()
            File(dir, "mcp.jsonl").writeText(jsonl)
            SessionStore.claudeHome = tmpHome

            val descs = SessionStore.readTranscript(CWD, "mcp")
                .filter { it["role"]?.jsonPrimitive?.content == "tool" }
                .associate { it["text"]!!.jsonPrimitive.content to it["desc"]?.jsonPrimitive?.content }

            assertEquals("the Submit button", descs["mcp__playwright__browser_click"])   // element wins over target
            assertEquals("login.png", descs["mcp__playwright__browser_take_screenshot"]) // filename when no element
            assertEquals("e13", descs["mcp__playwright__browser_select_option"])         // target as last resort
            assertEquals("https://example.com", descs["mcp__playwright__browser_navigate"]) // url still wins
        } finally {
            SessionStore.claudeHome = home
            tmpHome.deleteRecursively()
        }
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

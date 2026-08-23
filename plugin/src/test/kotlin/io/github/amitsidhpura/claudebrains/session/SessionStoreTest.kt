package io.github.amitsidhpura.claudebrains.session

import io.github.amitsidhpura.claudebrains.RenderLimits
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.contentOrNull
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
        // a request cut short by an interrupt gets the Stopped line and NO completion summary.
        // Scoped to the interrupted request (nothing between the Stopped line and the next turn
        // may be a summary) — the fixture later grew legitimately completed requests, which DO
        // summarise, so the old fixture-global "no done blocks at all" over-asserted.
        val stopIdx = blocks.indexOf(status[0])
        val after = blocks.drop(stopIdx + 1)
        val nextTurn = after.indexOfFirst { it["role"]?.jsonPrimitive?.content == "user" }
        val window = if (nextTurn >= 0) after.take(nextTurn) else after
        assertTrue(window.none { it["role"]?.jsonPrimitive?.content == "done" },
            "an interrupted request should not be summarised")
    }

    @Test
    fun `denied plan captures the typed reason, the stock message stays off the card`() {
        // Fixture records mirror the live wire (probed 2.1.233): a denied ExitPlanMode's
        // tool_result content IS the deny message, verbatim. A typed reason must reach replay as
        // planFeedback; the stock no-reason constant must NOT be quoted as if the user wrote it.
        val deniedPlans = role("tool").filter {
            it["plan"] != null && it["denied"]?.jsonPrimitive?.boolean == true
        }
        assertEquals(3, deniedPlans.size,
            "fixture carries a typed-reason, a stock, and a commented denial")
        val typed = deniedPlans.first { it["planFeedback"] != null }
        assertEquals("Use fillPath on the card too", typed["planFeedback"]?.jsonPrimitive?.content)
        assertTrue(deniedPlans.any { it["planFeedback"] == null },
            "the stock '${RenderLimits.REJECT_MESSAGE}' denial must stay unquoted")
    }

    @Test
    fun `plan comments parse out of the deny message, machinery stays off the card`() {
        // The deny message is the VS Code client's own, copied byte-for-byte from its transcript
        // (2026-08-23): prefix line + "Comments on the plan:" + one [Re: "…"] line. The comments
        // must become structured rows and NEITHER machinery line may be quoted as free text.
        val card = role("tool").first { it["planComments"] != null && it["denied"] != null }
        val cs = card["planComments"]!!.jsonArray
        assertEquals(1, cs.size)
        assertEquals("one", cs[0].jsonObject["a"]?.jsonPrimitive?.content)
        assertEquals("Not one but two", cs[0].jsonObject["t"]?.jsonPrimitive?.content)
        assertNull(card["planFeedback"], "the deny prefix/header are machinery, not user text")
    }

    @Test
    fun `approve-with-comments splits free text from the anchored lines under the marker`() {
        val card = role("tool").first { it["planComments"] != null && it["denied"] == null }
        val cs = card["planComments"]!!.jsonArray
        assertEquals(1, cs.size)
        assertEquals("Retry helper", cs[0].jsonObject["a"]?.jsonPrimitive?.content)
        assertEquals("Reuse the existing HttpRetry util", cs[0].jsonObject["t"]?.jsonPrimitive?.content)
        assertEquals("ship it this week", card["planFeedback"]?.jsonPrimitive?.content,
            "free text before the comments block still reaches the quoted footer")
    }

    @Test
    fun `mid-turn steered message replays as the user bubble it was, without doubling queued ones`() {
        // Record shapes copied from a real 2.1.233 transcript: a message consumed mid-turn
        // persists ONLY as an attachment (queued_command/prompt); one queued to the next turn
        // persists as attachment AND user record. Replay must show the first and not double the
        // second; task-notification attachments are machinery and never render.
        val userTexts = role("user").map { it["text"]?.jsonPrimitive?.content }
        assertTrue(userTexts.contains("steered-note: also add logging"),
            "an attachment-only steered message must become a user bubble")
        assertEquals(1, userTexts.count { it == "queued-question: what next?" },
            "attachment + delivered user record must render exactly once")
        assertFalse(userTexts.any { it?.contains("machinery, not a person") == true },
            "task-notification attachments never become bubbles")
    }

    @Test
    fun `approved plan notes are parsed out of the approved plan, body stays the original`() {
        // Approve-with-notes appends the note to updatedInput.plan (ClaudeCli); the transcript's
        // tool_use input keeps the ORIGINAL plan while toolUseResult.plan carries the marker
        // section. The footer quotes the note; the card body must not grow the appended section.
        val ok = role("tool").first {
            it["plan"]?.jsonPrimitive?.contentOrNull == "# Plan v4\n1. Ship it." &&
                it["denied"] == null
        }
        assertEquals("also add logging", ok["planFeedback"]?.jsonPrimitive?.content)
        assertFalse(ok["plan"]!!.jsonPrimitive.content.contains("User notes on approval"),
            "the body renders the original plan — the note lives in the footer quote")
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

    /**
     * A LOCAL built-in's output (`/context`, `/recap`, …) persists as `system/local_command` with
     * the text inside `<local-command-stdout>…</local-command-stdout>` — while the live wire spells
     * the SAME content as a bare whole-message assistant frame (measured 2026-08-15, CLI 2.1.233,
     * session 41a89e81…; neither path shares a spelling with the other). Replay must render it as
     * the assistant block live draws, and a `<local-command-stderr>` body as an error block.
     */
    @Test
    fun `a local_command record replays as an assistant block, stderr as an error`() {
        val jsonl = listOf(
            """{"type":"user","timestamp":"2026-08-15T10:00:00.000Z","message":{"role":"user","content":[{"type":"text","text":"<command-name>/context</command-name>\n<command-message>context</command-message>\n<command-args></command-args>"}]}}""",
            """{"type":"system","subtype":"local_command","content":"<local-command-stdout>## Context Usage\n\n**Tokens:** 24.3k</local-command-stdout>","level":"info","timestamp":"2026-08-15T10:00:01.000Z","uuid":"lc-1"}""",
            """{"type":"system","subtype":"local_command","content":"<local-command-stderr>command failed</local-command-stderr>","level":"info","timestamp":"2026-08-15T10:00:02.000Z","uuid":"lc-2"}""",
        ).joinToString("\n")

        val tmpHome = File.createTempFile("claude-home-lc", "").let { it.delete(); it.mkdirs(); it }
        try {
            val dir = File(tmpHome, ".claude/projects/${CWD.replace(Regex("[^a-zA-Z0-9]"), "-")}")
            dir.mkdirs()
            File(dir, "lc.jsonl").writeText(jsonl)
            SessionStore.claudeHome = tmpHome

            val out = SessionStore.readTranscript(CWD, "lc")
            val blk = out.first { it["role"]?.jsonPrimitive?.content == "assistant" }
            assertTrue(blk["text"]!!.jsonPrimitive.content.startsWith("## Context Usage"),
                "stdout body should replay as the assistant block live draws, wrapper stripped")
            val err = out.first { it["role"]?.jsonPrimitive?.content == "error" }
            assertEquals("command failed", err["text"]!!.jsonPrimitive.content,
                "stderr body should replay as an error block, wrapper stripped")
        } finally {
            SessionStore.claudeHome = home   // the other tests read the shared fixture from here
            tmpHome.deleteRecursively()
        }
    }

    /**
     * The same local-command output also reaches disk as a plain `user` record whose content is a
     * STRING (measured 2026-08-15: `/security-review` failing on a repo with no `origin/HEAD` wrote
     * exactly one such record). `cleanInjected`'s drop list handled that family wrongly in BOTH
     * directions — `<local-command-stdout>` was silently swallowed, and `<local-command-stderr>`
     * was not listed at all, so it replayed as raw XML inside a blue user box. Both must route
     * through the same mapping the `system/local_command` branch uses: stdout -> assistant block,
     * stderr -> error block. A caveat wrapper still drops.
     */
    @Test
    fun `local-command wrappers on a user record replay as assistant and error, not raw XML`() {
        val jsonl = listOf(
            """{"type":"user","timestamp":"2026-08-15T10:00:00.000Z","message":{"role":"user","content":"<local-command-stdout>## Context Usage\n\n**Tokens:** 24.3k</local-command-stdout>"}}""",
            """{"type":"user","timestamp":"2026-08-15T10:00:01.000Z","message":{"role":"user","content":"<local-command-stderr>fatal: ambiguous argument 'origin/HEAD...'</local-command-stderr>"}}""",
            """{"type":"user","timestamp":"2026-08-15T10:00:02.000Z","message":{"role":"user","content":"<local-command-caveat>Caveat: generated while running local commands.</local-command-caveat>"}}""",
        ).joinToString("\n")

        val tmpHome = File.createTempFile("claude-home-lcu", "").let { it.delete(); it.mkdirs(); it }
        try {
            val dir = File(tmpHome, ".claude/projects/${CWD.replace(Regex("[^a-zA-Z0-9]"), "-")}")
            dir.mkdirs()
            File(dir, "lcu.jsonl").writeText(jsonl)
            SessionStore.claudeHome = tmpHome

            val out = SessionStore.readTranscript(CWD, "lcu")
            val blk = out.first { it["role"]?.jsonPrimitive?.content == "assistant" }
            assertTrue(blk["text"]!!.jsonPrimitive.content.startsWith("## Context Usage"),
                "stdout on a user record should replay as an assistant block, not be swallowed")
            val err = out.first { it["role"]?.jsonPrimitive?.content == "error" }
            assertEquals("fatal: ambiguous argument 'origin/HEAD...'", err["text"]!!.jsonPrimitive.content,
                "stderr should replay as an error block with the wrapper stripped")
            assertTrue(out.none { it["role"]?.jsonPrimitive?.content == "user" },
                "no raw-XML user box: every wrapper here is plumbing, not something the human typed")
        } finally {
            SessionStore.claudeHome = home   // the other tests read the shared fixture from here
            tmpHome.deleteRecursively()
        }
    }

    /**
     * The CLI writes its post-compaction summary as a `user` record carrying `isCompactSummary`
     * rather than `isMeta`, so the meta filter missed it and a resumed session showed a 25k-41k
     * character blue message box the human never typed (measured in two real local sessions). It
     * belongs to the boundary before it — linked by `parentUuid`, not by adjacency — folded under
     * the marker. Both orphan directions are covered because either can fall at a file boundary.
     */
    @Test
    fun `a compaction replays as one marker with its summary, never as a user message`() {
        val jsonl = listOf(
            """{"type":"user","timestamp":"2026-08-05T10:00:00.000Z","message":{"role":"user","content":[{"type":"text","text":"real question"}]}}""",
            // boundary + its summary: must collapse into ONE compact block
            """{"type":"system","subtype":"compact_boundary","uuid":"b1","timestamp":"2026-08-05T10:01:00.000Z","content":"Conversation compacted","compactMetadata":{"trigger":"manual","preTokens":375909,"durationMs":178900}}""",
            """{"type":"user","uuid":"s1","parentUuid":"b1","isCompactSummary":true,"isVisibleInTranscriptOnly":true,"timestamp":"2026-08-05T10:01:01.000Z","message":{"role":"user","content":[{"type":"text","text":"SUMMARY-BODY"}]}}""",
            // a boundary whose summary never arrived — the marker still stands
            """{"type":"system","subtype":"compact_boundary","uuid":"b2","timestamp":"2026-08-05T10:02:00.000Z","content":"Conversation compacted","compactMetadata":{"trigger":"auto","preTokens":167204}}""",
            """{"type":"user","timestamp":"2026-08-05T10:03:00.000Z","message":{"role":"user","content":[{"type":"text","text":"another real question"}]}}""",
            // a summary whose boundary is missing — still must NOT become a blue user box
            """{"type":"user","uuid":"s3","parentUuid":"gone","isCompactSummary":true,"timestamp":"2026-08-05T10:04:00.000Z","message":{"role":"user","content":[{"type":"text","text":"ORPHAN-SUMMARY"}]}}""",
        ).joinToString("\n")

        val tmpHome = File.createTempFile("claude-home-compact", "").let { it.delete(); it.mkdirs(); it }
        try {
            val dir = File(tmpHome, ".claude/projects/${CWD.replace(Regex("[^a-zA-Z0-9]"), "-")}")
            dir.mkdirs()
            File(dir, "compact.jsonl").writeText(jsonl)
            SessionStore.claudeHome = tmpHome

            val out = SessionStore.readTranscript(CWD, "compact")
            val users = out.filter { it["role"]?.jsonPrimitive?.content == "user" }
            val compacts = out.filter { it["role"]?.jsonPrimitive?.content == "compact" }

            assertEquals(
                listOf("real question", "another real question"),
                users.map { it["text"]!!.jsonPrimitive.content },
                "only messages the user actually typed may replay as user blocks",
            )
            users.forEach {
                assertFalse(
                    it["text"]!!.jsonPrimitive.content.contains("SUMMARY"),
                    "a compaction summary must never be attributed to the user",
                )
            }

            assertEquals(3, compacts.size, "two boundaries and one orphaned summary")
            assertEquals("manual", compacts[0]["trigger"]?.jsonPrimitive?.content)
            assertEquals(375909L, compacts[0]["tokens"]?.jsonPrimitive?.long)
            assertEquals("SUMMARY-BODY", compacts[0]["text"]?.jsonPrimitive?.content,
                "the summary folds into the boundary that claims it")

            assertEquals("auto", compacts[1]["trigger"]?.jsonPrimitive?.content)
            assertEquals("", compacts[1]["text"]?.jsonPrimitive?.content,
                "a boundary with no summary still renders its marker")

            assertNull(compacts[2]["trigger"], "an orphaned summary has no metadata to show")
            assertEquals("ORPHAN-SUMMARY", compacts[2]["text"]?.jsonPrimitive?.content)
        } finally {
            SessionStore.claudeHome = home   // the other tests read the shared fixture from here
            tmpHome.deleteRecursively()
        }
    }

    /**
     * The CLI writes the boundary + summary when compaction FINISHES, physically BEFORE the
     * /compact command records that triggered it — which keep their typed-at timestamps (real
     * shopify session, 2026-08-21: boundary 13:20:19 at file position 295, its command 13:18:11
     * at 298). Replaying file order drew "conversation compacted" above the /compact that asked
     * for it; the bubble must be inserted back in front of the marker, matching live.
     */
    @Test
    fun `a manual compact's command bubble replays before the marker, as live drew it`() {
        val jsonl = listOf(
            """{"type":"user","timestamp":"2026-08-21T13:17:00.000Z","message":{"role":"user","content":[{"type":"text","text":"earlier question"}]}}""",
            // compaction end: boundary + summary land first, the command records after —
            // with timestamps from when they were typed, two minutes earlier
            """{"type":"system","subtype":"compact_boundary","uuid":"b1","timestamp":"2026-08-21T13:20:19.000Z","content":"Conversation compacted","compactMetadata":{"trigger":"manual","preTokens":80952,"durationMs":128004}}""",
            """{"type":"user","uuid":"s1","parentUuid":"b1","isCompactSummary":true,"timestamp":"2026-08-21T13:20:19.500Z","message":{"role":"user","content":[{"type":"text","text":"SUMMARY-BODY"}]}}""",
            """{"type":"user","isMeta":true,"timestamp":"2026-08-21T13:18:11.000Z","message":{"role":"user","content":[{"type":"text","text":"<local-command-caveat>Caveat: local commands.</local-command-caveat>"}]}}""",
            """{"type":"user","timestamp":"2026-08-21T13:18:11.000Z","message":{"role":"user","content":[{"type":"text","text":"<command-name>/compact</command-name>\n<command-message>compact</command-message>"}]}}""",
            """{"type":"user","timestamp":"2026-08-21T13:20:20.000Z","message":{"role":"user","content":[{"type":"text","text":"<local-command-stdout>Compacted</local-command-stdout>"}]}}""",
        ).joinToString("\n")

        val tmpHome = File.createTempFile("claude-home-compact-order", "").let { it.delete(); it.mkdirs(); it }
        try {
            val dir = File(tmpHome, ".claude/projects/${CWD.replace(Regex("[^a-zA-Z0-9]"), "-")}")
            dir.mkdirs()
            File(dir, "compact-order.jsonl").writeText(jsonl)
            SessionStore.claudeHome = tmpHome

            val out = SessionStore.readTranscript(CWD, "compact-order")
            fun at(role: String, text: String) = out.indexOfFirst {
                it["role"]?.jsonPrimitive?.content == role &&
                    it["text"]?.jsonPrimitive?.content == text
            }.also { assertTrue(it >= 0, "no $role block with text \"$text\"") }
            val bubble = at("user", "/compact")
            val marker = out.indexOfFirst { it["role"]?.jsonPrimitive?.content == "compact" }
            val stdout = at("assistant", "Compacted")

            assertTrue(bubble < marker,
                "the /compact bubble must replay BEFORE the marker it caused (live's order)")
            assertTrue(marker < stdout, "the command's stdout still follows the marker")
            assertEquals("SUMMARY-BODY", out[marker]["text"]?.jsonPrimitive?.content,
                "the summary still folds into the displaced marker by parentUuid")
            assertEquals("manual", out[marker]["trigger"]?.jsonPrimitive?.content)
        } finally {
            SessionStore.claudeHome = home
            tmpHome.deleteRecursively()
        }
    }

    /**
     * A resumed session must not re-seed the context gauge with the size the compaction just
     * removed. Scanning the tail backwards, the boundary is met BEFORE the request that preceded
     * it — and that request's usage is the pre-compact figure. Observed in `runIde`: compacting at
     * 345k cleared the chip live, then a resume put 34% (345k of 1M) straight back.
     */
    @Test
    fun `a compaction resets the resumed context gauge`() {
        fun assistant(t: String, used: Long) =
            """{"type":"assistant","timestamp":"$t","message":{"id":"m$used","role":"assistant",""" +
                """"content":[{"type":"text","text":"hi"}],"usage":{"input_tokens":$used,""" +
                """"cache_read_input_tokens":0,"cache_creation_input_tokens":0}}}"""
        val boundary =
            """{"type":"system","subtype":"compact_boundary","uuid":"b1","timestamp":"2026-08-05T10:02:00.000Z","compactMetadata":{"trigger":"manual","preTokens":345000}}"""

        val tmpHome = File.createTempFile("claude-home-ctx", "").let { it.delete(); it.mkdirs(); it }
        try {
            val dir = File(tmpHome, ".claude/projects/${CWD.replace(Regex("[^a-zA-Z0-9]"), "-")}")
            dir.mkdirs()
            SessionStore.claudeHome = tmpHome

            // compaction is the newest thing in the file: the gauge must read "unknown", not 345000
            File(dir, "ctx-a.jsonl").writeText(
                listOf(assistant("2026-08-05T10:01:00.000Z", 345000), boundary).joinToString("\n"))
            assertEquals(0L, SessionStore.contextTokens(CWD, "ctx-a"),
                "the pre-compact request's usage must not survive the compaction")

            // once a real turn follows the boundary, ITS prompt is the context again
            File(dir, "ctx-b.jsonl").writeText(
                listOf(assistant("2026-08-05T10:01:00.000Z", 345000), boundary,
                       assistant("2026-08-05T10:03:00.000Z", 12000)).joinToString("\n"))
            assertEquals(12000L, SessionStore.contextTokens(CWD, "ctx-b"),
                "the newest post-compact request is the current context")
        } finally {
            SessionStore.claudeHome = home   // the other tests read the shared fixture from here
            tmpHome.deleteRecursively()
        }
    }

    /**
     * A safety classifier can make the CLI retry on another model and WITHDRAW what it already sent.
     * Leaving retracted text on screen would show content the model explicitly took back.
     *
     * Not one local transcript contains `model_refusal_fallback` or an assistant `supersedes` field,
     * so this fixture is the only thing it has ever run against — but the CONTRACT is no longer
     * guessed. Verified 2026-08-05 against the 2.1.222 binary's own wire schema, which also settled
     * two things this test did not originally cover:
     *  - `scope` decides whether the SESSION model changed. "local" means a subagent / `/btw` /
     *    background fork fell back and the session model is untouched, so a notice claiming a switch
     *    would be a falsehood. Absent means "session" — the schema says so explicitly.
     *  - `model_refusal_no_fallback` is a separate subtype for the refusal that was NOT retried.
     *    It carries no `fallback_model` and no retractions; the record exists only to say the turn
     *    ended and why, and we used to drop it on the floor.
     *
     * Both retraction lanes are covered, and so is the guard that matters most — a `user` block is
     * never withdrawn, because the model cannot take back what the human typed.
     */
    @Test
    fun `retracted assistant messages are withdrawn, but never the user's own`() {
        fun assistant(uuid: String, text: String, supersedes: String? = null) =
            """{"type":"assistant","uuid":"$uuid","timestamp":"2026-08-05T10:0${uuid.last()}:00.000Z"""" +
                (supersedes?.let { ""","supersedes":["$it"]""" } ?: "") +
                ""","message":{"id":"m$uuid","role":"assistant","content":[{"type":"text","text":"$text"}]}}"""

        val jsonl = listOf(
            """{"type":"user","uuid":"u1","timestamp":"2026-08-05T10:00:00.000Z","message":{"role":"user","content":[{"type":"text","text":"MY-QUESTION"}]}}""",
            assistant("a1", "FLAGGED-ANSWER"),
            assistant("a2", "KEPT-ANSWER"),
            // the refusal retracts a1 AND tries to retract the user's own message
            """{"type":"system","subtype":"model_refusal_fallback","uuid":"r1","direction":"retry",""" +
                """"original_model":"claude-opus-5","fallback_model":"claude-sonnet-5",""" +
                """"content":"Safeguards flagged this message.","session_id":"s",""" +
                """"retracted_message_uuids":["a1","u1"],"timestamp":"2026-08-05T10:03:00.000Z"}""",
            // the second lane: an assistant record superseding an earlier one directly
            assistant("a3", "SUPERSEDING", supersedes = "a2"),
            assistant("a4", "CAMEL-FLAGGED"),
            // camelCase spelling: the CLI's emission site writes originalModel/fallbackModel, the
            // VS Code validator reads snake_case — a transcript may carry either, so both must work
            """{"type":"system","subtype":"model_refusal_fallback","uuid":"r2","direction":"retry",""" +
                """"originalModel":"claude-opus-5","fallbackModel":"claude-haiku-4-5",""" +
                """"content":"Safeguards flagged this message.","session_id":"s",""" +
                """"retractedMessageUuids":["a4"],"timestamp":"2026-08-05T10:05:00.000Z"}""",
            // scope:"local" — a subagent fell back. Nothing is retracted and the session model did
            // NOT change, so the notice must not claim a switch.
            assistant("a5", "LOCAL-KEPT"),
            """{"type":"system","subtype":"model_refusal_fallback","uuid":"r3","direction":"retry",""" +
                """"scope":"local","original_model":"claude-opus-5","fallback_model":"claude-sonnet-5",""" +
                """"content":"Safeguards flagged a side question.","session_id":"s",""" +
                """"timestamp":"2026-08-05T10:06:00.000Z"}""",
            // the refusal with no retry at all; `content` is empty, which one emission site really
            // does send, so the parser has to supply the sentence itself
            """{"type":"system","subtype":"model_refusal_no_fallback","uuid":"r4",""" +
                """"original_model":"claude-opus-5","request_id":null,"content":"","session_id":"s",""" +
                """"timestamp":"2026-08-05T10:07:00.000Z"}""",
        ).joinToString("\n")

        val tmpHome = File.createTempFile("claude-home-refusal", "").let { it.delete(); it.mkdirs(); it }
        try {
            val dir = File(tmpHome, ".claude/projects/${CWD.replace(Regex("[^a-zA-Z0-9]"), "-")}")
            dir.mkdirs()
            File(dir, "refusal.jsonl").writeText(jsonl)
            SessionStore.claudeHome = tmpHome

            val out = SessionStore.readTranscript(CWD, "refusal")
            val texts = out.mapNotNull { it["text"]?.jsonPrimitive?.contentOrNull }

            assertTrue(texts.any { it == "MY-QUESTION" },
                "the model cannot withdraw a message the user typed, even when it names its uuid")
            assertFalse(texts.any { it == "FLAGGED-ANSWER" },
                "a retracted assistant message must not survive the replay")
            assertFalse(texts.any { it == "SUPERSEDING" && texts.contains("KEPT-ANSWER") },
                "the supersedes lane must remove what it replaces")
            assertFalse(texts.any { it == "KEPT-ANSWER" }, "a2 was superseded by a3")
            assertTrue(texts.any { it == "SUPERSEDING" }, "the superseding message itself stays")
            assertTrue(texts.any { it.startsWith("Safeguards flagged") && it.contains("claude-sonnet-5") },
                "the notice must say what happened and which model took over")
            assertFalse(texts.any { it == "CAMEL-FLAGGED" },
                "the camelCase spelling the CLI's emission site writes must retract too")
            assertTrue(texts.any { it.contains("claude-haiku-4-5") },
                "the camelCase model fields must reach the notice")

            val localNotice = texts.single { it.startsWith("Safeguards flagged a side question.") }
            assertFalse(localNotice.contains("Switched"),
                "a local-scope fallback did not swap the session model, so the notice must not say it did")
            assertTrue(localNotice.contains("session model is unchanged"),
                "a local-scope notice has to state what did NOT change, or it reads as a session switch")
            assertTrue(texts.any { it == "LOCAL-KEPT" },
                "a local-scope fallback retracts nothing — that message must survive")

            assertTrue(texts.any { it.contains("declined to answer") && it.contains("claude-opus-5") },
                "model_refusal_no_fallback must still produce a notice when its content is empty")
        } finally {
            SessionStore.claudeHome = home   // the other tests read the shared fixture from here
            tmpHome.deleteRecursively()
        }
    }

    /**
     * An image a TOOL returned (client-parity item 8) — a Playwright screenshot, or `Read` on a PNG.
     *
     * The shape below is REAL, from the transcript of a live run that took a screenshot and read it
     * back. It corrects the item, which named `toolUseResult.isImage`: that field exists but belongs
     * to BASH results, where it is always `false` — which is why measuring it found zero and the gap
     * looked unreachable. The discriminator is `toolUseResult.type == "image"`.
     *
     * The bytes go on `images`, the same list user attachments use, so `trimAttachments` applies the
     * 4 MB replay budget to tool images too and a screenshot cannot starve the visible tail.
     */
    @Test
    fun `an image a tool returned survives replay`() {
        val b64 = "iVBORw0KGgoAAAANSUhEUg" + "A".repeat(200)
        val jsonl = listOf(
            """{"type":"user","uuid":"u1","timestamp":"2026-08-06T01:30:00.000Z","message":{"role":"user","content":[{"type":"text","text":"screenshot it"}]}}""",
            """{"type":"assistant","uuid":"a1","timestamp":"2026-08-06T01:31:00.000Z","message":{"id":"m1","role":"assistant","content":[""" +
                """{"type":"tool_use","id":"r1","name":"Read","input":{"file_path":"/home/x/google-home.png"}}]}}""",
            // the result: an image block, no text at all — which is how it used to vanish
            """{"type":"user","uuid":"u2","timestamp":"2026-08-06T01:31:02.000Z",""" +
                """"toolUseResult":{"type":"image","file":{"base64":"$b64","type":"image/png",""" +
                """"dimensions":{"originalWidth":921,"originalHeight":892,"displayWidth":921,"displayHeight":892},""" +
                """"originalSize":40960}},""" +
                """"message":{"role":"user","content":[{"type":"tool_result","tool_use_id":"r1",""" +
                """"content":[{"type":"image","source":{"type":"base64","media_type":"image/png","data":"$b64"}}]}]}}""",
        ).joinToString("\n")

        val tmpHome = File.createTempFile("claude-home-img", "").let { it.delete(); it.mkdirs(); it }
        try {
            val dir = File(tmpHome, ".claude/projects/${CWD.replace(Regex("[^a-zA-Z0-9]"), "-")}")
            dir.mkdirs()
            File(dir, "img.jsonl").writeText(jsonl)
            SessionStore.claudeHome = tmpHome

            val tool = SessionStore.readTranscript(CWD, "img")
                .single { it["role"]?.jsonPrimitive?.contentOrNull == "tool" }
            val imgs = tool["images"]?.jsonArray
            assertEquals(1, imgs?.size, "the image must reach replay, not be dropped for having no text")

            val im = imgs!![0].jsonObject
            assertEquals("image/png", im["media_type"]?.jsonPrimitive?.contentOrNull)
            assertEquals(b64, im["data"]?.jsonPrimitive?.contentOrNull)
            assertEquals("google-home.png", im["name"]?.jsonPrimitive?.contentOrNull,
                "named from the path so the lightbox caption and alt text mean something")
            // carried through verbatim: the renderer reserves the box from displayWidth/Height
            assertEquals(921, im["dimensions"]?.jsonObject?.get("displayWidth")?.jsonPrimitive?.content?.toInt())
        } finally {
            SessionStore.claudeHome = home
            tmpHome.deleteRecursively()
        }
    }

    /**
     * `system/informational` — the CLI's own notices (client-parity item 22).
     *
     * The first record below is REAL, copied verbatim from a transcript produced by driving 2.1.222
     * with a `UserPromptSubmit` hook that exits 2. That turn emits this record **and nothing else**:
     * no assistant message, no text. Unhandled, a resumed session showed the prompt vanishing into
     * silence, exactly as the live panel did.
     *
     * Note the spelling split the wire/transcript pair carries yet again — the stream says
     * `prevent_continuation`, the transcript `preventContinuation`.
     */
    @Test
    fun `the CLI's own notices replay instead of vanishing`() {
        val jsonl = listOf(
            // verbatim from a real run (uuid/session trimmed, nothing else changed)
            """{"parentUuid":null,"isSidechain":false,"type":"system","subtype":"informational",""" +
                """"content":"UserPromptSubmit operation blocked by hook:\n[echo UPS-BLOCK-REASON >&2; exit 2]: UPS-BLOCK-REASON\n\n\nOriginal prompt: say ok",""" +
                """"isMeta":false,"timestamp":"2026-08-05T19:20:48.101Z","uuid":"i1","level":"warning",""" +
                """"preventContinuation":true,"sessionId":"s","version":"2.1.222"}""",
            """{"type":"system","subtype":"informational","uuid":"i2","level":"notice",""" +
                """"content":"a quieter notice","timestamp":"2026-08-05T19:20:49.000Z","sessionId":"s"}""",
            // blank content is not a notice, and must not leave an empty line behind
            """{"type":"system","subtype":"informational","uuid":"i3","level":"warning",""" +
                """"content":"   ","timestamp":"2026-08-05T19:20:50.000Z","sessionId":"s"}""",
            // an unrelated system subtype must still be ignored, as before
            """{"type":"system","subtype":"some_future_thing","uuid":"i4",""" +
                """"content":"ignore me","timestamp":"2026-08-05T19:20:51.000Z","sessionId":"s"}""",
        ).joinToString("\n")

        val tmpHome = File.createTempFile("claude-home-info", "").let { it.delete(); it.mkdirs(); it }
        try {
            val dir = File(tmpHome, ".claude/projects/${CWD.replace(Regex("[^a-zA-Z0-9]"), "-")}")
            dir.mkdirs()
            File(dir, "info.jsonl").writeText(jsonl)
            SessionStore.claudeHome = tmpHome

            val infos = SessionStore.readTranscript(CWD, "info")
                .filter { it["role"]?.jsonPrimitive?.contentOrNull == "info" }
            assertEquals(2, infos.size, "blank content and unknown subtypes must not become blocks")

            assertTrue(infos[0]["text"]?.jsonPrimitive?.contentOrNull
                ?.startsWith("UserPromptSubmit operation blocked by hook:") == true)
            assertEquals("warning", infos[0]["level"]?.jsonPrimitive?.contentOrNull,
                "level rides along so replay maps prominence through the same table as live")
            assertEquals("notice", infos[1]["level"]?.jsonPrimitive?.contentOrNull)
            assertNull(infos[0]["uuid"], "nothing retracts a notice, so it advertises no uuid")
        } finally {
            SessionStore.claudeHome = home
            tmpHome.deleteRecursively()
        }
    }

    /**
     * Client-parity item 33: a mid-turn Stop is persisted as a user record whose whole text is a
     * literal marker. Measured 2026-08-06: 28 such records locally, only 5 carrying
     * `interruptedByShutdown` — so the flag alone (pinned by `interrupt becomes a stopped status
     * line` against the fixture) left 23 replaying as blue user boxes holding text the human never
     * typed. Both marker variants become the same "⏹ Stopped" the live path draws; a message that
     * merely QUOTES the marker (3 exist locally — this repo's own audit sessions) must stay a user
     * box, which is why the match is whole-block equality and not contains.
     */
    @Test
    fun `interrupt markers become stopped lines, quoting them stays a user message`() {
        val jsonl = listOf(
            """{"type":"user","uuid":"m1","timestamp":"2026-08-05T10:00:00.000Z","sessionId":"s",""" +
                """"message":{"role":"user","content":[{"type":"text","text":"[Request interrupted by user]"}]}}""",
            """{"type":"user","uuid":"m2","timestamp":"2026-08-05T10:00:01.000Z","sessionId":"s",""" +
                """"message":{"role":"user","content":[{"type":"text","text":"[Request interrupted by user for tool use]"}]}}""",
            """{"type":"user","uuid":"m3","timestamp":"2026-08-05T10:00:02.000Z","sessionId":"s",""" +
                """"message":{"role":"user","content":[{"type":"text",""" +
                """"text":"the doc quotes [Request interrupted by user] as the marker string"}]}}""",
        ).joinToString("\n")

        val tmpHome = File.createTempFile("claude-home-stop", "").let { it.delete(); it.mkdirs(); it }
        try {
            val dir = File(tmpHome, ".claude/projects/${CWD.replace(Regex("[^a-zA-Z0-9]"), "-")}")
            dir.mkdirs()
            File(dir, "stops.jsonl").writeText(jsonl)
            SessionStore.claudeHome = tmpHome

            val blocks = SessionStore.readTranscript(CWD, "stops")
            val status = blocks.filter { it["role"]?.jsonPrimitive?.contentOrNull == "status" }
            val users = blocks.filter { it["role"]?.jsonPrimitive?.contentOrNull == "user" }
            assertEquals(2, status.size, "both marker variants must become status lines")
            assertTrue(status.all { it["text"]?.jsonPrimitive?.contentOrNull == "Stopped" })
            assertEquals(1, users.size, "quoting the marker mid-text must stay a user message")
            assertTrue(users[0]["text"]?.jsonPrimitive?.contentOrNull
                ?.startsWith("the doc quotes") == true)
        } finally {
            SessionStore.claudeHome = home
            tmpHome.deleteRecursively()
        }
    }

    /**
     * Client-parity item 32: the retry banner must accept BOTH spellings of the retry event.
     * Transcripts persist the internal twin `api_error` ({error:{message,formatted}, retryAttempt,
     * maxRetries}); the live wire sends `api_retry` ({attempt, max_retries, error_status,
     * error:"<code string>"}) — the shape below is byte-for-byte from a real 401 storm probed
     * 2026-08-06. The first cut of item 31 read only the transcript shape, so the live banner
     * never fired; this pins both, on the replay side where both can be exercised.
     */
    @Test
    fun `a retry storm replays in either spelling`() {
        val jsonl = listOf(
            """{"type":"system","subtype":"api_error","uuid":"r1","sessionId":"s",""" +
                """"timestamp":"2026-08-05T10:00:00.000Z","retryAttempt":3,"maxRetries":10,""" +
                """"error":{"message":"overloaded","formatted":"529 Overloaded"}}""",
            // verbatim wire frame from the real storm (uuid/session trimmed)
            """{"type":"system","subtype":"api_retry","uuid":"r2","session_id":"s",""" +
                """"timestamp":"2026-08-05T10:00:01.000Z","attempt":1,"max_retries":10,""" +
                """"retry_delay_ms":602,"error_status":401,"error":"authentication_failed"}""",
        ).joinToString("\n")

        val tmpHome = File.createTempFile("claude-home-retry", "").let { it.delete(); it.mkdirs(); it }
        try {
            val dir = File(tmpHome, ".claude/projects/${CWD.replace(Regex("[^a-zA-Z0-9]"), "-")}")
            dir.mkdirs()
            File(dir, "retry.jsonl").writeText(jsonl)
            SessionStore.claudeHome = tmpHome

            val status = SessionStore.readTranscript(CWD, "retry")
                .filter { it["role"]?.jsonPrimitive?.contentOrNull == "status" }
            assertEquals(2, status.size, "both spellings must produce a status line")
            assertEquals("529 Overloaded — retrying (3/10)",
                status[0]["text"]?.jsonPrimitive?.contentOrNull)
            assertEquals("401 authentication_failed — retrying (1/10)",
                status[1]["text"]?.jsonPrimitive?.contentOrNull,
                "the wire spelling used to render NOTHING — a string error and snake_case counters")
        } finally {
            SessionStore.claudeHome = home
            tmpHome.deleteRecursively()
        }
    }

    /**
     * The file order of a retry storm lies (measured 2026-08-09 on a real network-off session):
     * the CLI buffers `api_error` records and flushes them AFTER the error record that concluded
     * the storm, with EARLIER timestamps. Replay must insert them back ahead of the error —
     * retries, then the failure that ended them, live's order. Pins the apiErrAnchor mechanics
     * (see DisplacedAnchor), including that a YOUNGER record still appends after the error.
     */
    @Test
    fun `late-flushed retries replay before the error that ended their storm`() {
        val jsonl = listOf(
            // the error record that concluded the storm — file position FIRST, timestamp LAST
            """{"type":"assistant","uuid":"e1","isApiErrorMessage":true,"timestamp":"2026-08-09T09:47:24.000Z","message":{"role":"assistant","content":[{"type":"text","text":"API Error: connection refused"}]}}""",
            // the buffered retries, flushed after it with earlier timestamps
            """{"type":"system","subtype":"api_error","uuid":"r1","timestamp":"2026-08-09T09:44:20.000Z","retryAttempt":1,"maxRetries":10,"error":{"message":"refused","formatted":"ECONNREFUSED"}}""",
            """{"type":"system","subtype":"api_error","uuid":"r2","timestamp":"2026-08-09T09:45:30.000Z","retryAttempt":2,"maxRetries":10,"error":{"message":"refused","formatted":"ECONNREFUSED"}}""",
            // a LATER request's retry — younger than the error, must NOT enter the old turn
            """{"type":"system","subtype":"api_error","uuid":"r9","timestamp":"2026-08-09T09:50:00.000Z","retryAttempt":1,"maxRetries":10,"error":{"message":"late","formatted":"LATER-STORM"}}""",
        ).joinToString("\n")

        val tmpHome = File.createTempFile("claude-home-retry-order", "").let { it.delete(); it.mkdirs(); it }
        try {
            val dir = File(tmpHome, ".claude/projects/${CWD.replace(Regex("[^a-zA-Z0-9]"), "-")}")
            dir.mkdirs()
            File(dir, "retry-order.jsonl").writeText(jsonl)
            SessionStore.claudeHome = tmpHome

            val out = SessionStore.readTranscript(CWD, "retry-order")
            val roles = out.map { it["role"]?.jsonPrimitive?.contentOrNull }
            val texts = out.map { it["text"]?.jsonPrimitive?.contentOrNull }
            val err = roles.indexOf("error").also { assertTrue(it >= 0, "no error block") }
            val r1 = texts.indexOf("ECONNREFUSED — retrying (1/10)")
            val r2 = texts.indexOf("ECONNREFUSED — retrying (2/10)")
            val r9 = texts.indexOf("LATER-STORM — retrying (1/10)")

            assertTrue(r1 in 0..<r2 && r2 < err,
                "late-flushed retries must insert back ahead of the error, in their own order " +
                    "(got retries at $r1,$r2 vs error at $err)")
            assertTrue(r9 > err, "a younger retry belongs after the old storm's error, not inside it")
        } finally {
            SessionStore.claudeHome = home
            tmpHome.deleteRecursively()
        }
    }

    /**
     * What a sub-agent was ASKED (client-parity item 3). The tool line already carried `description`
     * — a handful of words — while the `prompt` it actually ran on was dropped, so "what did it just
     * go off and do?" was unanswerable from the panel. Measured 2026-08-06: local `Agent` prompts
     * reach 2121 characters against descriptions of four or five words.
     *
     * The IN box is keyed off `RenderLimits.IN_KEYS`, not off the tool name, so this also covers
     * `WebFetch`'s extraction instruction and any future tool that takes a prompt. Verified against
     * the three real `Agent` records in a local transcript before being pinned here.
     */
    @Test
    fun `a sub-agent's prompt fills the IN box, not just its description`() {
        fun toolUse(id: String, name: String, input: String) =
            """{"type":"assistant","uuid":"$id","timestamp":"2026-08-06T10:00:00.000Z","message":""" +
                """{"id":"m$id","role":"assistant","content":[{"type":"tool_use","id":"$id",""" +
                """"name":"$name","input":$input}]}}"""

        val jsonl = listOf(
            toolUse("t1", "Agent", """{"description":"Find flagged API usages","prompt":"Search the repo for deprecated calls and report each with a file:line.","subagent_type":"Explore"}"""),
            toolUse("t2", "WebFetch", """{"url":"https://example.com/doc","prompt":"Extract the rate limit table."}"""),
            // neither key: must stay boxless rather than growing an empty IN
            toolUse("t3", "Read", """{"file_path":"/tmp/x.kt"}"""),
            // a blank prompt is not an instruction — same "first NON-BLANK wins" rule as DESC_KEYS
            toolUse("t4", "Agent", """{"description":"Empty brief","prompt":"   "}"""),
        ).joinToString("\n")

        val tmpHome = File.createTempFile("claude-home-prompt", "").let { it.delete(); it.mkdirs(); it }
        try {
            val dir = File(tmpHome, ".claude/projects/${CWD.replace(Regex("[^a-zA-Z0-9]"), "-")}")
            dir.mkdirs()
            File(dir, "prompt.jsonl").writeText(jsonl)
            SessionStore.claudeHome = tmpHome

            val tools = SessionStore.readTranscript(CWD, "prompt")
                .filter { it["role"]?.jsonPrimitive?.contentOrNull == "tool" }
            assertEquals(4, tools.size)

            assertEquals("Find flagged API usages", tools[0]["desc"]?.jsonPrimitive?.contentOrNull,
                "the description still describes the line")
            assertEquals("Search the repo for deprecated calls and report each with a file:line.",
                tools[0]["cmd"]?.jsonPrimitive?.contentOrNull,
                "and the prompt is what it was actually asked")

            assertEquals("https://example.com/doc", tools[1]["desc"]?.jsonPrimitive?.contentOrNull)
            assertEquals("Extract the rate limit table.", tools[1]["cmd"]?.jsonPrimitive?.contentOrNull,
                "the rule is keyed on the input, not on the tool being Agent")

            assertNull(tools[2]["cmd"], "a tool with no command and no prompt gets no IN box")
            assertNull(tools[3]["cmd"], "a whitespace-only prompt is not an instruction")
        } finally {
            SessionStore.claudeHome = home
            tmpHome.deleteRecursively()
        }
    }

    /**
     * `mcp__playwright__browser_evaluate`'s JS body belongs in the IN box, not on the tool line.
     *
     * `function` sat in [RenderLimits.DESC_KEYS] from 2026-08-05, added to close the blank-tool-line
     * gap on the assumption its value reads like prose. It does not: measured across the nine real
     * `browser_evaluate` records in local transcripts it runs 230-2965 characters of multi-line JS,
     * so at [RenderLimits.DESC_MAX] the panel showed a mid-token slice of the first line, wrapped
     * across the tool line. It moved to [RenderLimits.IN_KEYS] on 2026-08-12 — the same shape as
     * Bash, whose line is blank because the command is in the box.
     *
     * THE FIXTURE IS THE POINT. The case that let this through used `() => document.title` — 21
     * characters on one line — which cannot express the failure at all: it fits under every cap and
     * has nothing to wrap. This one is multi-line and kilobyte-scale, like the real records.
     */
    @Test
    fun `a browser_evaluate's JS body fills the IN box, and its tool line stays blank`() {
        // Shaped like the real thing — an arrow function whose body is a run of DOM assertions —
        // and sized into the middle of the measured 230-2965 band rather than to a round number.
        val fn = buildString {
            append("() => {\n")
            append("  const head = document.getElementById('head');\n")
            append("  const editing = () => head.classList.contains('editing');\n")
            append("  const fire = el => el.dispatchEvent(new MouseEvent('click', {bubbles: true}));\n")
            append("  const r = [];\n")
            repeat(16) { i ->
                append("  startRename(); fire(document.getElementById('target-$i'));\n")
                append("  r.push(['case $i closes the editor', editing() === false]);\n")
            }
            append("  return r.map(x => (x[1] ? 'PASS  ' : 'FAIL  ') + x[0]);\n")
            append("}")
        }
        val huge = fn + "\n" + "// pad\n".repeat(RenderLimits.CMD_MAX)   // deliberately past the cap

        fun toolUse(id: String, name: String, input: String) =
            """{"type":"assistant","uuid":"$id","timestamp":"2026-08-12T10:00:00.000Z","message":""" +
                """{"id":"m$id","role":"assistant","content":[{"type":"tool_use","id":"$id",""" +
                """"name":"$name","input":$input}]}}"""
        fun jsonStr(s: String) = Json.encodeToString(String.serializer(), s)

        val jsonl = listOf(
            toolUse("t1", "mcp__playwright__browser_evaluate", """{"function":${jsonStr(fn)}}"""),
            // over CMD_MAX: the box must say what it dropped, which a description never could
            toolUse("t2", "mcp__playwright__browser_evaluate", """{"function":${jsonStr(huge)}}"""),
            // `element` still describes the line for the Playwright tools that carry one — moving
            // `function` must not disturb the rest of the chain
            toolUse("t3", "mcp__playwright__browser_click", """{"element":"the Submit button","target":"e42"}"""),
        ).joinToString("\n")

        val tmpHome = File.createTempFile("claude-home-evaluate", "").let { it.delete(); it.mkdirs(); it }
        try {
            val dir = File(tmpHome, ".claude/projects/${CWD.replace(Regex("[^a-zA-Z0-9]"), "-")}")
            dir.mkdirs()
            File(dir, "evaluate.jsonl").writeText(jsonl)
            SessionStore.claudeHome = tmpHome

            val tools = SessionStore.readTranscript(CWD, "evaluate")
                .filter { it["role"]?.jsonPrimitive?.contentOrNull == "tool" }
            assertEquals(3, tools.size)

            assertTrue(
                fn.length > RenderLimits.DESC_MAX * 10 && fn.contains('\n'),
                "the fixture must be able to express the bug: multi-line and far past DESC_MAX, " +
                    "not the 21-char one-liner that shipped with it (was ${fn.length} chars)",
            )
            assertNull(tools[0]["desc"], "the tool line is blank by design — the body is in the box")
            assertEquals(fn, tools[0]["cmd"]?.jsonPrimitive?.contentOrNull,
                "and the box holds the function whole, newlines and all")
            assertNull(tools[0]["cmdCut"], "a real-world function is under CMD_MAX, so nothing is cut")

            assertNull(tools[1]["desc"])
            assertEquals(RenderLimits.CMD_MAX, tools[1]["cmd"]!!.jsonPrimitive.content.length,
                "past the cap it is cut to CMD_MAX, the same rule as a Bash command")
            assertNotNull(tools[1]["cmdCut"], "and the cut is announced, unlike the silent DESC_MAX")

            assertEquals("the Submit button", tools[2]["desc"]?.jsonPrimitive?.contentOrNull,
                "the rest of DESC_KEYS is untouched")
            assertNull(tools[2]["cmd"], "and a tool with no IN key still gets no box")
        } finally {
            SessionStore.claudeHome = home
            tmpHome.deleteRecursively()
        }
    }

    /**
     * A web search the API ran server-side (client-parity item 12). It arrives as a `server_tool_use`
     * block plus a `web_search_tool_result` block on the SAME assistant message — there is no
     * `tool_result` user event, because no client-side tool ever ran. Replay dropped both, exactly
     * as live did.
     *
     * Zero local records, and deliberately noted as such: `server_tool_use` never appears in any
     * transcript here, but neither does `WebSearch` or `WebFetch` in ANY form, so the absence says
     * the feature was never exercised — not that the CLI does not emit it. The shape is the CLI
     * binary's own reader (2.1.222); `content` is an array on success and an object on failure,
     * which is the discriminator.
     */
    @Test
    fun `a server-side search replays as a tool line with its results`() {
        val jsonl = listOf(
            """{"type":"user","uuid":"u1","timestamp":"2026-08-06T10:00:00.000Z","message":{"role":"user","content":[{"type":"text","text":"look it up"}]}}""",
            """{"type":"assistant","uuid":"a1","timestamp":"2026-08-06T10:00:01.000Z","message":{"id":"m1","role":"assistant","content":[""" +
                """{"type":"server_tool_use","id":"srv1","name":"web_search","input":{"query":"kotlin coroutines"}},""" +
                """{"type":"web_search_tool_result","tool_use_id":"srv1","content":[""" +
                """{"type":"web_search_result","title":"Coroutines basics","url":"https://kotlinlang.org/docs/x.html"},""" +
                """{"type":"web_search_result","title":"Flow","url":"https://kotlinlang.org/docs/flow.html"}]}]}}""",
            """{"type":"assistant","uuid":"a2","timestamp":"2026-08-06T10:00:05.000Z","message":{"id":"m2","role":"assistant","content":[""" +
                """{"type":"server_tool_use","id":"srv2","name":"web_search","input":{"query":"unsearchable"}},""" +
                """{"type":"web_search_tool_result","tool_use_id":"srv2","content":{"error_code":"max_uses_exceeded"}}]}}""",
        ).joinToString("\n")

        val tmpHome = File.createTempFile("claude-home-search", "").let { it.delete(); it.mkdirs(); it }
        try {
            val dir = File(tmpHome, ".claude/projects/${CWD.replace(Regex("[^a-zA-Z0-9]"), "-")}")
            dir.mkdirs()
            File(dir, "search.jsonl").writeText(jsonl)
            SessionStore.claudeHome = tmpHome

            val out = SessionStore.readTranscript(CWD, "search")
            val tools = out.filter { it["role"]?.jsonPrimitive?.contentOrNull == "tool" }
            assertEquals(2, tools.size, "each server_tool_use is one tool line")

            val ok = tools[0]
            assertEquals("web_search", ok["text"]?.jsonPrimitive?.contentOrNull)
            assertEquals("kotlin coroutines", ok["desc"]?.jsonPrimitive?.contentOrNull,
                "the query describes the line — `query` is already in DESC_KEYS")
            assertEquals("2 results\n\nCoroutines basics\nhttps://kotlinlang.org/docs/x.html\n\n" +
                "Flow\nhttps://kotlinlang.org/docs/flow.html",
                ok["out"]?.jsonPrimitive?.contentOrNull,
                "the OUT box must match RenderLimits.searchResults byte for byte")
            assertNull(ok["isError"], "a successful search is not a failed tool")

            val bad = tools[1]
            assertEquals("Web search error: max_uses_exceeded", bad["out"]?.jsonPrimitive?.contentOrNull,
                "an object `content` is the ERROR branch, not a result set")
            assertEquals(true, bad["isError"]?.jsonPrimitive?.contentOrNull?.toBoolean(),
                "a failed search must colour its dot red, like any other failed tool")
        } finally {
            SessionStore.claudeHome = home
            tmpHome.deleteRecursively()
        }
    }

    /**
     * Every tool's result gets an OUT box now, not just Bash's — except the ones whose outcome the
     * panel already shows. Measured before building: rendering all of them would have added ~2100
     * boxes, 2033 of which said "The file … has been updated successfully" directly under the diff
     * that had just shown the change. The exception to the exception is an ERROR, which shows
     * whatever the tool: a failure is not a restatement of a success.
     */
    @Test
    fun `non-Bash results render, unless the panel already shows the outcome`() {
        fun call(i: Int, id: String, name: String, input: String) =
            """{"type":"assistant","uuid":"u$i","timestamp":"2026-08-05T10:0$i:00.000Z",""" +
                """"message":{"id":"m$i","role":"assistant","content":[{"type":"tool_use",""" +
                """"id":"$id","name":"$name","input":$input}]}}"""
        fun result(i: Int, id: String, text: String, isError: Boolean = false) =
            """{"type":"user","uuid":"r$i","timestamp":"2026-08-05T10:0$i:30.000Z",""" +
                """"message":{"role":"user","content":[{"type":"tool_result","tool_use_id":"$id",""" +
                (if (isError) """"is_error":true,""" else "") +
                """"content":${q(text)}}]}}"""

        val jsonl = listOf(
            call(1, "t1", "Read", """{"file_path":"/tmp/a.txt"}"""),
            result(1, "t1", "line one\nline two"),
            call(2, "t2", "Edit", """{"file_path":"/tmp/a.txt"}"""),
            result(2, "t2", "The file /tmp/a.txt has been updated successfully."),
            call(3, "t3", "Agent", """{"description":"audit the docs"}"""),
            result(3, "t3", "AGENT-REPORT: four findings."),
            // an Edit that FAILED still speaks — skipping it would hide the reason
            call(4, "t4", "Edit", """{"file_path":"/tmp/b.txt"}"""),
            result(4, "t4", "String to replace not found in file.", isError = true),
        ).joinToString("\n")

        val tmpHome = File.createTempFile("claude-home-results", "").let { it.delete(); it.mkdirs(); it }
        try {
            val dir = File(tmpHome, ".claude/projects/${CWD.replace(Regex("[^a-zA-Z0-9]"), "-")}")
            dir.mkdirs()
            File(dir, "results.jsonl").writeText(jsonl)
            SessionStore.claudeHome = tmpHome

            val tools = SessionStore.readTranscript(CWD, "results")
                .filter { it["role"]?.jsonPrimitive?.content == "tool" }
            fun outOf(name: String, idx: Int = 0) =
                tools.filter { it["text"]?.jsonPrimitive?.content == name }[idx]["out"]
                    ?.jsonPrimitive?.contentOrNull

            assertEquals("line one\nline two", outOf("Read"),
                "a Read result is real content and was being dropped entirely")
            assertEquals("AGENT-REPORT: four findings.", outOf("Agent"),
                "the sub-agent's report (item 2) falls out of the same guard")
            assertNull(outOf("Edit", 0),
                "an Edit's success text restates the diff already on screen")
            assertEquals("String to replace not found in file.", outOf("Edit", 1),
                "but a FAILED Edit must still say why — an error is never a restatement")
        } finally {
            SessionStore.claudeHome = home   // the other tests read the shared fixture from here
            tmpHome.deleteRecursively()
        }
    }

    /**
     * The checklist is how a long turn is followed without reading every tool line. TodoWrite sends
     * its WHOLE list on every call, so the newest call is the state and no aggregation is needed —
     * unlike TaskCreate/TaskUpdate, which are increments. Its RESULT is boilerplate addressed to the
     * model ("Ensure that you continue to use the todo list"), which is why the tool is in
     * `RESULT_SKIP`: the list lives in the INPUT.
     */
    @Test
    fun `TodoWrite replays as a checklist, not as its boilerplate result`() {
        // ONE line: JSONL is one record per line, and a pretty-printed literal here silently
        // splits the record so the parser skips it and the assertions fail for the wrong reason.
        val todos = """[{"content":"Parse the transcript","status":"completed","activeForm":"Parsing the transcript"},""" +
            """{"content":"Render the checklist","status":"in_progress","activeForm":"Rendering the checklist"},""" +
            """{"content":"Write the tests","status":"pending","activeForm":"Writing the tests"}]"""
        val jsonl = listOf(
            """{"type":"assistant","uuid":"u1","timestamp":"2026-08-05T10:00:00.000Z","message":{"id":"m1",""" +
                """"role":"assistant","content":[{"type":"tool_use","id":"t1","name":"TodoWrite",""" +
                """"input":{"todos":$todos}}]}}""",
            """{"type":"user","uuid":"r1","timestamp":"2026-08-05T10:00:01.000Z","message":{"role":"user",""" +
                """"content":[{"type":"tool_result","tool_use_id":"t1","content":""" +
                """"Todos have been modified successfully. Ensure that you continue to use the todo list"}]}}""",
        ).joinToString("\n")

        val tmpHome = File.createTempFile("claude-home-todo", "").let { it.delete(); it.mkdirs(); it }
        try {
            val dir = File(tmpHome, ".claude/projects/${CWD.replace(Regex("[^a-zA-Z0-9]"), "-")}")
            dir.mkdirs()
            File(dir, "todo.jsonl").writeText(jsonl)
            SessionStore.claudeHome = tmpHome

            val tw = SessionStore.readTranscript(CWD, "todo")
                .first { it["text"]?.jsonPrimitive?.contentOrNull == "TodoWrite" }
            val list = tw["todos"]?.jsonArray
            assertNotNull(list, "the checklist never reached the wire")
            assertEquals(3, list!!.size)
            assertEquals(
                listOf("completed", "in_progress", "pending"),
                list.map { it.jsonObject["status"]!!.jsonPrimitive.content },
                "every status must survive — the renderer draws a different glyph for each",
            )
            assertEquals(
                "Rendering the checklist",
                list[1].jsonObject["activeForm"]?.jsonPrimitive?.content,
                "activeForm is what the in-flight row shows, so it has to travel with the item",
            )
            assertNull(
                tw["out"],
                "TodoWrite is in RESULT_SKIP: its result is boilerplate aimed at the model, and " +
                    "printing it under the checklist would restate nothing useful",
            )
        } finally {
            SessionStore.claudeHome = home   // the other tests read the shared fixture from here
            tmpHome.deleteRecursively()
        }
    }

    /** JSON-quote a string for the hand-built fixture lines above. */
    private fun q(s: String) = Json.encodeToString(String.serializer(), s)

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
    fun `a rename outranks every derived title, and the newest rename wins`() {
        // Client-parity item 34: `custom-title` is what /rename (and a fork's "(fork)" stamp)
        // writes, and it never reached titleOf — a renamed session kept showing its derived name.
        // Precedence with an explicit name present: customTitle > summary > ai-title > first user.
        val jsonl = listOf(
            """{"type":"user","timestamp":"2026-07-28T10:00:00.000Z","message":{"role":"user","content":[{"type":"text","text":"first message"}]}}""",
            """{"type":"summary","summary":"A derived summary","leafUuid":"x"}""",
            """{"type":"ai-title","aiTitle":"An AI title","sessionId":"rename"}""",
            """{"type":"custom-title","customTitle":"First rename","sessionId":"rename"}""",
            """{"type":"custom-title","customTitle":"Final rename","sessionId":"rename"}""",
        ).joinToString("\n")

        val tmpHome = File.createTempFile("claude-home-rename", "").let { it.delete(); it.mkdirs(); it }
        try {
            val dir = File(tmpHome, ".claude/projects/${CWD.replace(Regex("[^a-zA-Z0-9]"), "-")}")
            dir.mkdirs()
            File(dir, "rename.jsonl").writeText(jsonl)
            SessionStore.claudeHome = tmpHome
            val info = SessionStore.list(CWD).first { it.id == "rename" }
            assertEquals("Final rename", info.title,
                "the user's own name must beat summary and ai-title, and the LAST rename wins")
        } finally {
            SessionStore.claudeHome = home
            tmpHome.deleteRecursively()
        }
    }

    @Test
    fun `an injected ide_selection is stripped, not shown as raw xml`() {
        // Client-parity item 37: the one injected tag the census found unstripped — a TUI-attached
        // IDE's selection notification, not isMeta, replaying as raw XML in a blue user box.
        val jsonl = listOf(
            """{"type":"user","timestamp":"2026-07-28T10:00:00.000Z","message":{"role":"user","content":[""" +
                """{"type":"text","text":"<ide_selection>The user selected the lines 6 to 11 from env.prod:\nSECRET=x</ide_selection>"}]}}""",
            """{"type":"user","timestamp":"2026-07-28T10:00:01.000Z","message":{"role":"user","content":[{"type":"text","text":"a real message"}]}}""",
        ).joinToString("\n")

        val tmpHome = File.createTempFile("claude-home-idesel", "").let { it.delete(); it.mkdirs(); it }
        try {
            val dir = File(tmpHome, ".claude/projects/${CWD.replace(Regex("[^a-zA-Z0-9]"), "-")}")
            dir.mkdirs()
            File(dir, "idesel.jsonl").writeText(jsonl)
            SessionStore.claudeHome = tmpHome
            val users = SessionStore.readTranscript(CWD, "idesel")
                .filter { it["role"]?.jsonPrimitive?.contentOrNull == "user" }
            assertEquals(1, users.size, "the injected selection must not become a user box")
            assertEquals("a real message", users[0]["text"]?.jsonPrimitive?.contentOrNull)
        } finally {
            SessionStore.claudeHome = home
            tmpHome.deleteRecursively()
        }
    }

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

    /**
     * A CUSTOM slash command sent with no arguments writes a DIFFERENT wrapper shape than a
     * built-in (measured 2026-08-15, CLI 2.1.228): `<command-message>x</command-message>\n
     * <command-name>/x</command-name>` — message BEFORE name, and NO `<command-args>` tag at all.
     * The one-pattern regex that required name-then-args silently missed it and the session title
     * became raw XML (seen live the day custom commands were enabled in the menu, 3.1).
     */
    @Test
    fun `title collapses the arg-less custom-command wrapper shape`() {
        val jsonl = listOf(
            """{"type":"user","timestamp":"2026-08-15T10:00:00.000Z","message":{"role":"user","content":[{"type":"text","text":"<command-message>dummy-cmd</command-message>\n<command-name>/dummy-cmd</command-name>"}]}}""",
        ).joinToString("\n")

        val tmpHome = File.createTempFile("claude-home-cmd", "").let { it.delete(); it.mkdirs(); it }
        try {
            val dir = File(tmpHome, ".claude/projects/${CWD.replace(Regex("[^a-zA-Z0-9]"), "-")}")
            dir.mkdirs()
            File(dir, "cmd.jsonl").writeText(jsonl)
            SessionStore.claudeHome = tmpHome
            val info = SessionStore.list(CWD).first { it.id == "cmd" }
            assertEquals("/dummy-cmd", info.title, "arg-less custom wrapper should collapse to the command name")
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
     * The block cap keeps the NEWEST turns. It used to be applied by stopping the read, which kept
     * the OLDEST: a real 38 MB session (11,638 records, ~6,000 blocks) resumed on 2026-08-12 and
     * replayed as though it had ended on 2026-08-06 — six days of work missing, "Resumed" drawn
     * under the stale tail, and nothing on screen to say anything had been dropped.
     *
     * Negative control RUN against the pre-fix parser (`git checkout HEAD --` of SessionStore.kt,
     * 2026-08-12): failed exactly where it should — `expected "ask 40" to survive the window`.
     *
     * TURNS ARE LONG HERE ON PURPOSE. The first version of this fixture used one user + one
     * assistant per turn, and passed against an eviction that scanned only a chunk ahead for the
     * turn boundary — with a boundary every 3 blocks it could not miss. Real transcripts are
     * nothing like that: the retained window of `metrobuildsuppliers` held 9 user messages in 253
     * blocks, so the bounded scan gave up and the panel opened mid-turn, on an assistant reply with
     * no question above it (user screenshot, 2026-08-12). 14 blocks a turn reproduces that shape.
     */
    @Test
    fun `the block window keeps the newest turns, not the oldest`() {
        val turns = 40
        val perTurn = 12   // assistant blocks per turn; with the user block and the summary -> 14
        val jsonl = (1..turns).flatMap { n ->
            val t = "2026-07-28T10:%02d:00.000Z".format(n)
            listOf("""{"type":"user","timestamp":"$t","message":{"role":"user","content":[{"type":"text","text":"ask $n"}]}}""") +
                (1..perTurn).map { i ->
                    """{"type":"assistant","timestamp":"$t","message":{"role":"assistant","content":[{"type":"text","text":"reply $n.$i"}],"usage":{"output_tokens":1}}}"""
                }
        }.joinToString("\n")
        val total = turns * (perTurn + 2)   // user + perTurn assistant + the per-request summary

        val tmpHome = File.createTempFile("claude-home-window", "").let { it.delete(); it.mkdirs(); it }
        try {
            val dir = File(tmpHome, ".claude/projects/${CWD.replace(Regex("[^a-zA-Z0-9]"), "-")}")
            dir.mkdirs()
            File(dir, "window.jsonl").writeText(jsonl)
            SessionStore.claudeHome = tmpHome

            // 40 turns of 14 blocks = 560, so a cap of 40 is comfortably exceeded and the direction
            // of the drop is unambiguous. 40 also holds under three turns, so the boundary the
            // eviction has to find is never the one it started from.
            val max = 40
            val blocks = SessionStore.readTranscript(CWD, "window", max = max)
            val texts = blocks.mapNotNull { it["text"]?.jsonPrimitive?.contentOrNull }

            // Direction first: it is the assertion that separates this window from the old one,
            // and a size check firing ahead of it would mask which end actually survived.
            assertTrue(texts.contains("ask $turns"), "expected \"ask $turns\" to survive the window")
            assertTrue(texts.contains("reply $turns.$perTurn"),
                "expected the newest turn's last block to survive the window")
            assertFalse(texts.contains("ask 1"), "the OLDEST turn is the one the window must drop")
            assertEquals("truncated", blocks.first()["role"]?.jsonPrimitive?.content,
                "a windowed transcript must say so at its top edge")
            // The regression the long turns exist for: a window that opens on an assistant reply
            // with no question above it reads as a broken conversation, not a windowed one.
            assertEquals("user", blocks[1]["role"]?.jsonPrimitive?.content,
                "past the marker, the retained window must start at a turn boundary, not mid-turn")
            // Eviction fires at max + chunk and cuts at a turn boundary, so the retained count
            // lands NEAR the cap from either side — the ceiling is the slack, not `max` itself.
            assertTrue(blocks.size <= max + max / 8 + 1,
                "window held ${blocks.size} blocks, past the cap of $max plus its chunk of slack")

            // The count must be the blocks actually dropped: whatever survived, marker excluded.
            // A hardcoded or stale number here would read as authoritative.
            val kept = blocks.size - 1
            assertEquals((total - kept).toLong(), blocks.first()["dropped"]?.jsonPrimitive?.long,
                "the marker must count what was dropped, not what was kept")
            // NEGATIVE CONTROL for the marker: the same transcript under a cap it cannot reach must
            // carry no marker at all. A marker on every conversation would be worse than none —
            // it would claim history is missing from sessions that are complete.
            val whole = SessionStore.readTranscript(CWD, "window", max = 4000)
            assertEquals(total, whole.size, "fixture should parse to $total blocks with no window in play")
            assertTrue(whole.none { it["role"]?.jsonPrimitive?.content == "truncated" },
                "nothing was dropped, so nothing may claim it was")
        } finally {
            SessionStore.claudeHome = home   // the other tests read the shared fixture from here
            tmpHome.deleteRecursively()
        }
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

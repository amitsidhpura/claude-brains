package io.github.amitsidhpura.claudebrains.session

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

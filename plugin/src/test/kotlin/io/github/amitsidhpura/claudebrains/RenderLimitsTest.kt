package io.github.amitsidhpura.claudebrains

import io.github.amitsidhpura.claudebrains.session.SessionStore
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

/**
 * The live renderer (chat.html) and the replay parser (SessionStore) must agree on the tool-line
 * caps and the description key order, or the same tool call renders differently while streaming
 * than it does after a resume. They agree by construction now — RenderLimits is spliced into the
 * webview at `<!--LIMITS-->` — and these tests exist to keep it that way: they fail the build if
 * the splice point disappears or if a literal is hardcoded back into the JS.
 */
class RenderLimitsTest {

    private val html: String =
        javaClass.getResourceAsStream("/webview/chat.html")!!.use { it.readBytes() }.decodeToString()

    @Test
    fun `chat html carries the splice point exactly once`() {
        val marker = "<!" + "--LIMITS-->"   // split so this test file is not itself a second copy
        assertEquals(
            1, html.split(marker).size - 1,
            "the marker must appear EXACTLY once: ChatPanel replaces every occurrence, and the " +
                "replacement carries a </script>, so a second one (even inside a JS comment) ends " +
                "the script block early and silently kills the whole webview",
        )
        assertTrue(html.contains("window.LIMITS"), "chat.html no longer reads the spliced limits")
    }

    /**
     * A closing script tag written anywhere inside a script block — a string, even a `//` comment —
     * ends the block as far as the HTML parser is concerned, silently truncating everything after
     * it. Costs a blank tool window that looks like a JCEF failure rather than a typo.
     */
    @Test
    fun `script tags are balanced`() {
        val close = "</" + "script>"
        assertEquals(
            html.split("<script").size - 1, html.split(close).size - 1,
            "unbalanced script tags in chat.html — a stray `$close` (even inside a comment or a " +
                "string) truncates the block and kills the webview",
        )
    }

    @Test
    fun `caps are not hardcoded in the webview`() {
        // The exact literals that used to live in both languages. Anything matching means a value
        // was written into the JS again instead of being read from LIM.
        listOf(
            "slice(0, ${RenderLimits.DESC_MAX})",
            "slice(0, ${RenderLimits.CMD_MAX})",
            "slice(0, ${RenderLimits.OUT_MAX})",
        ).forEach {
            assertTrue(!html.contains(it), "chat.html hardcodes `$it` — read it from LIM instead")
        }
        listOf("LIM.descMax", "LIM.cmdMax", "LIM.outMax", "LIM.descKeys", "LIM.pathKeys",
            "LIM.resultSkip").forEach {
            assertTrue(html.contains(it), "chat.html no longer uses $it")
        }
    }

    @Test
    fun `description keys are not hardcoded as a chain in the webview`() {
        assertTrue(
            !html.contains("inp.description || inp.file_path"),
            "chat.html rebuilt the description chain by hand — walk LIM.descKeys instead",
        )
    }

    @Test
    fun `js literal round-trips the values`() {
        assertEquals(
            "{descMax:140,cmdMax:4000,outMax:2000," +
                "descKeys:[\"description\",\"file_path\",\"path\",\"pattern\",\"query\",\"url\"," +
                "\"element\",\"filename\",\"target\",\"skill\",\"status\",\"taskId\",\"task_id\"," +
                "\"function\",\"uri\"]," +
                "pathKeys:[\"file_path\",\"path\"]," +
                "resultSkip:[\"Edit\",\"Write\",\"MultiEdit\",\"NotebookEdit\",\"ExitPlanMode\"," +
                "\"AskUserQuestion\",\"TaskCreate\",\"TaskUpdate\",\"TodoWrite\",\"TaskList\"]}",
            RenderLimits.asJs(),
        )
    }

    /**
     * A Read of lines 40-80 used to render exactly like a Read of the whole file. Mirrored by
     * `descSuffix` in chat.html, so the arithmetic has to match: `limit` COUNTS lines, which is why
     * the end is offset+limit-1 and not offset+limit — the off-by-one that would misreport every
     * range by a line.
     */
    @Test
    fun `a Read's line range reaches the tool line`() {
        assertEquals(" (lines 40-119)", RenderLimits.descSuffix("Read", 40, 80))
        assertEquals(" (from line 40)", RenderLimits.descSuffix("Read", 40, null))
        assertEquals(" (first 80 lines)", RenderLimits.descSuffix("Read", null, 80))
        assertEquals("", RenderLimits.descSuffix("Read", null, null), "a whole-file Read says nothing")
        // no other tool has a range worth appending — Grep/Glob never occur locally, and Bash's
        // command and Edit's strings are already on screen in the IN box and the diff
        assertEquals("", RenderLimits.descSuffix("Bash", 40, 80))
        assertEquals("", RenderLimits.descSuffix("Edit", 40, 80))
    }

    @Test
    fun `path keys are part of the description order`() {
        assertTrue(RenderLimits.DESC_KEYS.containsAll(RenderLimits.PATH_KEYS))
    }

    /**
     * Server-side search results (item 12), mirrored by `searchResultText` in chat.html. The case
     * that matters most is the ERROR one: the wire discriminator is whether `content` is an ARRAY,
     * not a `type` field, so reading it the wrong way round prints an object into the transcript.
     * A `null` list here is that error branch.
     */
    @Test
    fun `search results format identically on both paths`() {
        val two = RenderLimits.searchResults(
            listOf("Kotlin docs" to "https://kotlinlang.org", "Gradle" to "https://gradle.org"), null)
        assertFalse(two.isError)
        assertEquals("2 results\n\nKotlin docs\nhttps://kotlinlang.org\n\nGradle\nhttps://gradle.org", two.text)

        // singular, because "1 results" is the kind of detail that makes a panel look unfinished
        assertEquals("1 result\n\nOnly\nhttps://x", RenderLimits.searchResults(listOf("Only" to "https://x"), null).text)
        assertEquals("No results.", RenderLimits.searchResults(emptyList(), null).text)

        val err = RenderLimits.searchResults(null, "max_uses_exceeded")
        assertTrue(err.isError, "an error must mark the tool line failed, not read as a result set")
        assertEquals("Web search error: max_uses_exceeded", err.text)
        assertEquals("Web search error: unknown", RenderLimits.searchResults(null, null).text)

        // degenerate rows: a missing title falls back to the url, and a row with neither still
        // occupies a line rather than collapsing the list silently
        assertEquals("1 result\n\nhttps://only-url", RenderLimits.searchResults(listOf("" to "https://only-url"), null).text)
        assertEquals("1 result\n\nuntitled", RenderLimits.searchResults(listOf("" to ""), null).text)
        // title == url must not print the same string twice
        assertEquals("1 result\n\nhttps://same", RenderLimits.searchResults(listOf("https://same" to "https://same"), null).text)
    }

    /**
     * The cut rule is implemented TWICE — here and as `cutInfo` in chat.html — because live
     * truncates in JS and replay in Kotlin. These cases are the contract between the two: the same
     * output must report the same amount dropped whether it is streaming or replayed. The
     * mid-line and trailing-newline rows are the ones that disagree if either side counts naively.
     */
    @Test
    fun `cut counts whole dropped lines only`() {
        // text                                cap  expect lines, bytes
        val cases = listOf(
            // cut lands MID-line: "cde" is the tail of a line still on screen, not a dropped line
            Triple("ab\ncde", 3, 0 to 3),
            // cut lands exactly ON a newline, one whole line follows
            Triple("abc\ndef", 3, 1 to 4),
            // trailing newline ends the dropped line, it does not begin another
            Triple("abc\ndef\n", 3, 1 to 5),
            // nothing but a newline dropped — closes the shown line, no line lost
            Triple("abc\n", 3, 0 to 1),
            // no newlines anywhere: the renderer shows size alone rather than "+0 lines"
            Triple("abcdefghij", 4, 0 to 6),
            // blank lines count
            Triple("a\n\n\nb", 1, 3 to 4),
        )
        cases.forEach { (text, cap, want) ->
            val c = RenderLimits.cut(text, cap)!!
            assertEquals(want.first, c.lines, "lines for ${text.replace("\n", "\\n")} @ $cap")
            assertEquals(want.second, c.bytes, "bytes for ${text.replace("\n", "\\n")} @ $cap")
            assertEquals(cap, c.shown.length, "shown length for ${text.replace("\n", "\\n")} @ $cap")
            assertEquals(text, c.shown + text.substring(cap), "shown must be a prefix of the input")
        }
    }

    @Test
    fun `cut returns null when nothing is dropped`() {
        assertEquals(null, RenderLimits.cut("abc", 3), "a text exactly at the cap is not truncated")
        assertEquals(null, RenderLimits.cut("ab", 3))
        assertEquals(null, RenderLimits.cut("", 3))
    }

    /**
     * The `<persisted-output>` wrapper, verbatim from a real transcript record (402.7KB / 412387
     * bytes). Live has no `toolUseResult`, so parsing this is how the live path learns the true
     * size and the spill path at all.
     */
    private val spillText = """
        <persisted-output>
        Output too large (402.7KB). Full output saved to: /home/dev/.claude/projects/p/s/tool-results/byb19giot.txt

        Preview (first 2KB):
        227215:updatedPermissions
        227216:updatedPermissionsDropped
        ...
        </persisted-output>
    """.trimIndent()

    @Test
    fun `persisted-output wrapper yields size, path and the preview alone`() {
        val s = RenderLimits.persistedOutput(spillText)!!
        assertEquals(
            "/home/dev/.claude/projects/p/s/tool-results/byb19giot.txt", s.path,
            "the spill path is what makes the marker clickable",
        )
        // 402.7 KB in BINARY units — the CLI divides by 1024, so this must land within a rounding
        // step of the 412387 the structured field reports for the same record. The exact value is
        // pinned because chat.html's `persistedOutput` must produce the SAME number: 402.7 * 1024
        // = 412364.8, so truncating there and rounding here would silently disagree by a byte
        // (measured against the real record in headless Chrome, 2026-08-05).
        assertEquals(412365L, s.bytes)
        assertTrue(
            kotlin.math.abs(s.bytes!! - 412387L) < 1024,
            "parsed size must agree with the structured persistedOutputSize to within its rounding",
        )
        assertEquals(
            "227215:updatedPermissions\n227216:updatedPermissionsDropped", s.preview,
            "the box shows the preview only — no wrapper tags, no header, no trailing ellipsis",
        )
    }

    /**
     * The guard that matters: one local record is a Bash result that GREPS for the tag, so its
     * output merely contains the string. Eating its body would delete real output and invent a
     * spill link. Both tags must bound the whole text.
     */
    @Test
    fun `text that merely mentions the wrapper is not a spill`() {
        listOf(
            "grep -n persisted-output found:\n<persisted-output>\nnot really\n",
            "<persisted-output>\nno closing tag, so not the real thing",
            "prefix <persisted-output>\nOutput too large (1KB). Full output saved to: /x\n</persisted-output>",
            "ordinary output\nwith no tags at all",
            "",
        ).forEach {
            assertEquals(null, RenderLimits.persistedOutput(it), "must not treat as a spill: ${it.take(40)}")
        }
    }

    @Test
    fun `spill size parses the unit, or survives its absence`() {
        fun bytes(s: String) = RenderLimits.persistedOutput(
            "<persisted-output>\nOutput too large ($s). Full output saved to: /x/y.txt\n\nPreview (first 2KB):\nbody\n</persisted-output>"
        )!!.bytes
        assertEquals(900L, bytes("900B"))
        assertEquals(1024L, bytes("1KB"))
        assertEquals(1258291L, bytes("1.2MB"))
        assertEquals(1610612736L, bytes("1.5GB"))
        // a wrapper with no parsable size still yields its path — the link is the useful half
        val noSize = RenderLimits.persistedOutput(
            "<persisted-output>\nFull output saved to: /x/y.txt\n\nPreview (first 2KB):\nbody\n</persisted-output>"
        )!!
        assertEquals(null, noSize.bytes)
        assertEquals("/x/y.txt", noSize.path)
    }

    /**
     * Replay has to put the cut on the wire, or a resumed session shows the slice with no marker
     * while the live render showed one. Also pins the spill fields: `outFile` is emitted ONLY when
     * the path still resolves, because the marker is clickable and a dead click reads as a bug —
     * of four such records in local transcripts, three point at files that no longer exist.
     */
    @Test
    fun `replay carries what the caps dropped, and only a spill file that exists`() {
        val home = File.createTempFile("claude-home", "").let { it.delete(); it.mkdirs(); it }
        val cwd = "/home/dev/Sites/cut-fixture"
        val dir = File(home, ".claude/projects/${cwd.replace(Regex("[^a-zA-Z0-9]"), "-")}")
        dir.mkdirs()
        val spill = File(home, "spilled.txt").apply { writeText("the whole output") }
        val gone = File(home, "vanished.txt").absolutePath

        val cmd = "echo start\n" + "run --step\n".repeat(RenderLimits.CMD_MAX)   // well over the cap
        val out = "line 0\n" + "line n\n".repeat(RenderLimits.OUT_MAX)
        fun call(i: Int, id: String) =
            """{"type":"assistant","uuid":"u$i","timestamp":"2026-08-05T10:00:0$i.000Z",""" +
                """"message":{"id":"m$i","role":"assistant","content":[{"type":"tool_use",""" +
                """"id":"$id","name":"Bash","input":{"command":${q(cmd)}}}]}}"""
        fun result(i: Int, id: String, path: String?) =
            """{"type":"user","uuid":"r$i","timestamp":"2026-08-05T10:01:0$i.000Z",""" +
                """"message":{"role":"user","content":[{"type":"tool_result","tool_use_id":"$id",""" +
                """"content":${q(out)}}]},"toolUseResult":{"stdout":${q(out)}""" +
                (path?.let { ""","persistedOutputPath":${q(it)},"persistedOutputSize":772297""" } ?: "") + "}}"

        File(dir, "cut.jsonl").writeText(
            listOf(
                call(0, "t0"), result(0, "t0", spill.absolutePath),   // spill file EXISTS
                call(1, "t1"), result(1, "t1", gone),                 // spill file is GONE
                call(2, "t2"), result(2, "t2", null),                 // no spill at all
            ).joinToString("\n")
        )

        val real = SessionStore.claudeHome
        try {
            SessionStore.claudeHome = home
            val tools = SessionStore.readTranscript(cwd, "cut")
                .filter { it["role"]?.jsonPrimitive?.content == "tool" }
            assertEquals(3, tools.size)

            // both caps report what they dropped, on every record
            tools.forEach { t ->
                assertEquals(
                    RenderLimits.CMD_MAX, t["cmd"]?.jsonPrimitive?.contentOrNull?.length,
                    "cmd was not capped at CMD_MAX",
                )
                assertEquals(
                    RenderLimits.cut(cmd, RenderLimits.CMD_MAX)!!.lines,
                    t["cmdCut"]?.jsonObject?.get("lines")?.jsonPrimitive?.int,
                    "cmdCut must match the rule the live path runs",
                )
                assertTrue(
                    (t["outCut"]?.jsonObject?.get("bytes")?.jsonPrimitive?.int ?: 0) > 0,
                    "an over-cap result must say how much it dropped",
                )
                assertEquals(772297L.takeIf { t !== tools[2] }, t["outTotal"]?.jsonPrimitive?.longOrNull)
            }

            assertEquals(
                spill.absolutePath, tools[0]["outFile"]?.jsonPrimitive?.contentOrNull,
                "an existing spill file must be offered",
            )
            assertEquals(
                null, tools[1]["outFile"],
                "a spill path that no longer resolves must NOT become a clickable dead link",
            )
            assertEquals(null, tools[2]["outFile"])
            assertEquals(
                null, tools[2]["outTotal"],
                "no spill metadata means no size to report",
            )
        } finally {
            SessionStore.claudeHome = real
            home.deleteRecursively()
        }
    }

    /**
     * `returnCodeInterpretation` is the CLI explaining a non-obvious exit code. Measured across
     * local transcripts: 35 records carry one, and 33 say something the result text does not — the
     * important shape being a grep whose whole output is "(Bash completed with no output)" and
     * whose only explanation is this field. The other 2 repeat what the content already says, so
     * they must NOT be echoed underneath it.
     *
     * `interrupted` is true in ZERO of 5673 local records, so the flag is carried but has never
     * been exercised against real data — asserted here on a synthetic record only.
     */
    @Test
    fun `replay carries the exit-code explanation, unless the output already says it`() {
        val home = File.createTempFile("claude-home", "").let { it.delete(); it.mkdirs(); it }
        val cwd = "/home/dev/Sites/note-fixture"
        val dir = File(home, ".claude/projects/${cwd.replace(Regex("[^a-zA-Z0-9]"), "-")}")
        dir.mkdirs()

        fun call(i: Int, id: String) =
            """{"type":"assistant","uuid":"u$i","timestamp":"2026-08-05T10:00:0$i.000Z",""" +
                """"message":{"id":"m$i","role":"assistant","content":[{"type":"tool_use",""" +
                """"id":"$id","name":"Bash","input":{"command":"grep -rn x ."}}]}}"""
        fun result(i: Int, id: String, content: String, extra: String) =
            """{"type":"user","uuid":"r$i","timestamp":"2026-08-05T10:01:0$i.000Z",""" +
                """"message":{"role":"user","content":[{"type":"tool_result","tool_use_id":"$id",""" +
                """"content":${q(content)}}]},"toolUseResult":{"stdout":${q(content)}$extra}}"""

        File(dir, "note.jsonl").writeText(
            listOf(
                // the case that matters: the interpretation is the ONLY explanation
                call(0, "t0"), result(0, "t0", "(Bash completed with no output)",
                    ""","returnCodeInterpretation":"No matches found""""),
                // already in the content — echoing it underneath would just be noise
                call(1, "t1"), result(1, "t1", "No matches found in 412 files",
                    ""","returnCodeInterpretation":"No matches found""""),
                // no interpretation at all
                call(2, "t2"), result(2, "t2", "ok", ""),
                // killed rather than finished — synthetic, no real record has ever had this
                call(3, "t3"), result(3, "t3", "waiting…", ""","interrupted":true"""),
            ).joinToString("\n")
        )

        val real = SessionStore.claudeHome
        try {
            SessionStore.claudeHome = home
            val tools = SessionStore.readTranscript(cwd, "note")
                .filter { it["role"]?.jsonPrimitive?.content == "tool" }
            assertEquals(4, tools.size)
            assertEquals(
                "No matches found", tools[0]["note"]?.jsonPrimitive?.contentOrNull,
                "an explanation the output does not give must reach the user",
            )
            assertEquals(
                null, tools[1]["note"],
                "an explanation the output ALREADY states must not be repeated under it",
            )
            assertEquals(null, tools[2]["note"])
            assertEquals(
                true, tools[3]["interrupted"]?.jsonPrimitive?.contentOrNull?.toBoolean(),
                "a killed command must be distinguishable from one that finished",
            )
            listOf(0, 1, 2).forEach {
                assertEquals(null, tools[it]["interrupted"], "only a killed command carries the flag")
            }
        } finally {
            SessionStore.claudeHome = real
            home.deleteRecursively()
        }
    }

    /** JSON-quote a string for the hand-built fixture lines above. */
    private fun q(s: String) = Json.encodeToString(String.serializer(), s)

    /**
     * Replay must resolve a tool description exactly as the live renderer does. These are the same
     * inputs driven through chat.html's `content_block_stop` handler in a browser, with the same
     * expected results — including the case that used to disagree, where a BLANK description was
     * kept by Kotlin's `?:` but skipped by JS's `||`, so live showed the path and replay a blank.
     */
    @Test
    fun `replay resolves descriptions the same way the live renderer does`() {
        val home = File.createTempFile("claude-home", "").let { it.delete(); it.mkdirs(); it }
        val cwd = "/home/dev/Sites/limits-fixture"
        val dir = File(home, ".claude/projects/${cwd.replace(Regex("[^a-zA-Z0-9]"), "-")}")
        dir.mkdirs()
        val cases = listOf(
            // name          input                                                    expected desc, isPath
            Triple("Read", """{"description":"a description","file_path":"/tmp/x.txt"}""", "a description" to false),
            Triple("Read", """{"file_path":"/tmp/x.txt"}""", "/tmp/x.txt" to true),
            Triple("Glob", """{"path":"/tmp/dir"}""", "/tmp/dir" to true),
            Triple("Read", """{"description":"   ","file_path":"/tmp/y.txt"}""", "/tmp/y.txt" to true),
            Triple("mcp__x", """{"element":"the Submit button","target":"ref=42"}""", "the Submit button" to false),
            Triple("mcp__x", """{"target":"ref=42"}""", "ref=42" to false),
            // the blank-tool-line tail: each key is owned by exactly one tool, and `status` wins
            // over `taskId` on purpose — "in_progress" says what happened, an id does not
            Triple("TaskUpdate", """{"taskId":"t-42","status":"in_progress"}""", "in_progress" to false),
            Triple("TaskStop", """{"task_id":"t-42"}""", "t-42" to false),
            Triple("Skill", """{"skill":"pdf","args":"x"}""", "pdf" to false),
            Triple("mcp__playwright__browser_evaluate", """{"function":"() => document.title"}""",
                "() => document.title" to false),
        )
        val lines = cases.mapIndexed { i, (name, input, _) ->
            """{"type":"assistant","uuid":"u$i","timestamp":"2026-08-02T10:00:0$i.000Z",""" +
                """"message":{"id":"m$i","role":"assistant","content":""" +
                """[{"type":"tool_use","id":"t$i","name":"$name","input":$input}]}}"""
        } + listOf(   // parenthesised: `list + a + b` would append each FRAGMENT as its own line
            """{"type":"assistant","uuid":"ucap","timestamp":"2026-08-02T10:01:00.000Z",""" +
                """"message":{"id":"mcap","role":"assistant","content":[{"type":"tool_use",""" +
                """"id":"tcap","name":"Read","input":{"description":"${"z".repeat(500)}"}}]}}"""
        )
        File(dir, "limits.jsonl").writeText(lines.joinToString("\n"))

        val real = SessionStore.claudeHome
        try {
            SessionStore.claudeHome = home
            val tools = SessionStore.readTranscript(cwd, "limits")
                .filter { it["role"]?.jsonPrimitive?.content == "tool" }
            assertEquals(cases.size + 1, tools.size, "expected one tool block per record")
            cases.forEachIndexed { i, (_, input, want) ->
                val (desc, isPath) = want
                assertEquals(desc, tools[i]["desc"]?.jsonPrimitive?.contentOrNull, "desc for $input")
                assertEquals(
                    isPath, tools[i]["isPath"]?.jsonPrimitive?.contentOrNull?.toBoolean() ?: false,
                    "isPath for $input",
                )
            }
            assertEquals(
                RenderLimits.DESC_MAX, tools.last()["desc"]?.jsonPrimitive?.contentOrNull?.length,
                "description was not capped at DESC_MAX",
            )
        } finally {
            SessionStore.claudeHome = real
            home.deleteRecursively()
        }
    }
}

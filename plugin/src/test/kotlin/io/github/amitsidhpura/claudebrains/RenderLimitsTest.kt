package io.github.amitsidhpura.claudebrains

import io.github.amitsidhpura.claudebrains.session.SessionStore
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
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
        listOf("LIM.descMax", "LIM.cmdMax", "LIM.outMax", "LIM.descKeys", "LIM.pathKeys").forEach {
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
                "\"element\",\"filename\",\"target\"]," +
                "pathKeys:[\"file_path\",\"path\"]}",
            RenderLimits.asJs(),
        )
    }

    @Test
    fun `path keys are part of the description order`() {
        assertTrue(RenderLimits.DESC_KEYS.containsAll(RenderLimits.PATH_KEYS))
    }

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

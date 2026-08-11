package io.github.amitsidhpura.claudebrains.session

import io.github.amitsidhpura.claudebrains.RenderLimits
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
 * The path a tool line SHOWS and the path it OPENS are two different strings, and only one of them
 * may be truncated.
 *
 * `desc` is capped at DESC_MAX for display; the webview shortens it further, to project-relative,
 * and clamps it to one line. `fullPath` is the raw path, and it is what the click handler sends to
 * the editor — so a path longer than the cap used to be *clicked* in its truncated form.
 *
 * Its own fixture rather than a record appended to `replay-sample.jsonl`: that file is a chain of
 * real records with a uuid/parentUuid spine, and splicing into it to get one long path would risk
 * the retraction and compaction logic that reads exactly that spine.
 */
class SessionStorePathTest {

    companion object {
        private const val CWD = "/home/dev/Sites/path-project"
        private const val ID = "path-fixture"
        // 190 chars — comfortably past DESC_MAX (140), and the tail is the part that matters.
        private val LONG_PATH = "$CWD/" + (1..12).joinToString("/") { "directory-level-$it" } + "/TheFileName.kt"
        private const val SHORT_PATH = "$CWD/src/App.kt"
        private lateinit var home: File
        private lateinit var realHome: File

        @BeforeAll
        @JvmStatic
        fun layOutFixture() {
            check(LONG_PATH.length > RenderLimits.DESC_MAX) { "fixture path is not longer than the cap" }
            home = File.createTempFile("claude-home-path", "").let { it.delete(); it.mkdirs(); it }
            val dir = File(home, ".claude/projects/${CWD.replace(Regex("[^a-zA-Z0-9]"), "-")}")
            dir.mkdirs()
            fun rec(uuid: String, parent: String?, toolId: String, name: String, input: String) =
                """{"parentUuid":${parent?.let { "\"$it\"" } ?: "null"},"isSidechain":false,"uuid":"$uuid",""" +
                    """"type":"assistant","cwd":"$CWD","message":{"model":"claude-opus-5","type":"message",""" +
                    """"role":"assistant","content":[{"type":"tool_use","id":"$toolId","name":"$name",""" +
                    """"input":$input}],"stop_reason":"tool_use","usage":{}}}"""
            File(dir, "$ID.jsonl").writeText(
                listOf(
                    """{"parentUuid":null,"isSidechain":false,"uuid":"u0","type":"user","cwd":"$CWD",""" +
                        """"message":{"role":"user","content":"edit some files"}}""",
                    rec("u1", "u0", "toolu_long", "Edit", """{"file_path":"$LONG_PATH"}"""),
                    rec("u2", "u1", "toolu_short", "Read", """{"file_path":"$SHORT_PATH"}"""),
                    rec("u3", "u2", "toolu_bash", "Bash", """{"command":"ls","description":"List the files"}"""),
                ).joinToString("\n") + "\n",
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

        private val blocks: List<JsonObject> by lazy { SessionStore.readTranscript(CWD, ID) }
        private fun tool(name: String) =
            blocks.first { it["role"]?.jsonPrimitive?.content == "tool" && it["text"]?.jsonPrimitive?.content == name }
        private fun str(o: JsonObject, k: String) = o[k]?.jsonPrimitive?.content
    }

    @Test
    fun `a path past the cap is truncated for display but sent whole to the editor`() {
        val edit = tool("Edit")
        assertEquals(RenderLimits.DESC_MAX, str(edit, "desc")!!.length, "desc is the DISPLAY string, so it is capped")
        assertEquals(LONG_PATH, str(edit, "fullPath"), "fullPath is the CLICK TARGET, so it is whole")
        assertTrue(
            str(edit, "fullPath")!!.endsWith("TheFileName.kt"),
            "the filename is exactly what the cap used to eat",
        )
    }

    @Test
    fun `a short path carries the same pair, so the webview never has to guess which to use`() {
        val read = tool("Read")
        assertEquals(SHORT_PATH, str(read, "desc"))
        assertEquals(SHORT_PATH, str(read, "fullPath"))
    }

    /** A description is not a path: nothing to open, so nothing to carry. */
    @Test
    fun `a free-text description gets no fullPath`() {
        val bash = tool("Bash")
        assertEquals("List the files", str(bash, "desc"))
        assertNull(bash["fullPath"], "a Bash description is not clickable")
        assertNull(bash["isPath"], "and is not marked as a path")
    }
}

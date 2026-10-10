package io.github.amitsidhpura.claudebrains.session

import io.github.amitsidhpura.claudebrains.RenderLimits
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import java.io.File

/**
 * 6.6 on the replay path: a prompt that carried the editor selection comes back as the prompt's
 * text plus `selection {path,start,end}` (the bubble's pill), a selection-only prompt keeps its
 * record (pill, no text), and the TUI's own `<ide_selection>` record is still dropped. Its own
 * fixture rather than a record spliced into `replay-sample.jsonl` (that file is a uuid chain the
 * retraction logic walks).
 */
class SessionStoreSelectionTest {

    companion object {
        private const val CWD = "/home/dev/Sites/selection-project"
        private const val ID = "selection-fixture"
        private const val PATH = "$CWD/src/components/App.kt"
        private lateinit var home: File
        private lateinit var realHome: File

        @BeforeAll
        @JvmStatic
        fun layOutFixture() {
            home = File.createTempFile("claude-home-sel", "").let { it.delete(); it.mkdirs(); it }
            val dir = File(home, ".claude/projects/${CWD.replace(Regex("[^a-zA-Z0-9]"), "-")}")
            dir.mkdirs()
            fun esc(s: String) = s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n")
            fun user(uuid: String, parent: String?, text: String) =
                """{"parentUuid":${parent?.let { "\"$it\"" } ?: "null"},"isSidechain":false,"uuid":"$uuid","type":"user","cwd":"$CWD",""" +
                    """"timestamp":"2026-10-10T10:0${uuid.last()}:00.000Z","message":{"role":"user","content":[{"type":"text","text":"${esc(text)}"}]}}"""
            fun assistant(uuid: String, parent: String, text: String) =
                """{"parentUuid":"$parent","isSidechain":false,"uuid":"$uuid","type":"assistant","cwd":"$CWD","message":{"model":"claude-opus-5","type":"message",""" +
                    """"role":"assistant","content":[{"type":"text","text":"${esc(text)}"}],"stop_reason":"end_turn","usage":{}}}"""
            val withSel = RenderLimits.withIdeSelection("Explain this", RenderLimits.ideSelectionTag(PATH, 12, 18, "fun render() {\n  return x\n}"))
            val cursorOnly = RenderLimits.withIdeSelection("Where am I?", RenderLimits.ideSelectionTag(PATH, 7, 7, ""))
            val tagOnly = RenderLimits.ideSelectionTag(PATH, 5, 6, "x")   // a tag with no prompt: the TUI's own record shape
            val withFile = RenderLimits.withIdeSelection("What's in here?", RenderLimits.ideSelectionTag(PATH, 3, 3, "", "fun main() {\n  if (a < b) {}\n}\n"))
            val tui = "<ide_selection>The user selected the lines 1 to 2 from $PATH:\nfoo</ide_selection>\n\nsome other wording"
            File(dir, "$ID.jsonl").writeText(
                listOf(
                    user("u1", null, withSel),
                    assistant("a1", "u1", "It renders twice because…"),
                    user("u2", "a1", cursorOnly),
                    assistant("a2", "u2", "That line opens the component."),
                    user("u3", "a2", tui),
                    assistant("a3", "u3", "ok"),
                    user("u4", "a3", "plain prompt"),
                    assistant("a4", "u4", "ok"),
                    user("u5", "a4", tagOnly),
                    assistant("a5", "u5", "ok"),
                    user("u6", "a5", withFile),
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
        private fun users() = blocks.filter { it["role"]?.jsonPrimitive?.content == "user" }
    }

    @Test
    fun `prompt plus selection replays as the prompt with a selection object`() {
        val u = users().first { it["text"]?.jsonPrimitive?.content == "Explain this" }
        val sel = u["selection"]!!.jsonObject
        assertEquals(PATH, sel["path"]!!.jsonPrimitive.content)
        assertEquals(12, sel["start"]!!.jsonPrimitive.int)
        assertEquals(18, sel["end"]!!.jsonPrimitive.int)
    }

    @Test
    fun `cursor only (opened-file tag) replays as a one-line selection`() {
        val u = users().first { it["text"]?.jsonPrimitive?.content == "Where am I?" }
        assertEquals(7, u["selection"]!!.jsonObject["start"]!!.jsonPrimitive.int)
        assertEquals(7, u["selection"]!!.jsonObject["end"]!!.jsonPrimitive.int)
    }

    @Test
    fun `tag-only records (the TUI's injected shape, foreign or ours) are still dropped, and a plain prompt has no selection key`() {
        assertEquals(4, users().size, "u3 / u5 (tag-only) must not become bubbles: ${users()}")
        val plain = users().first { it["text"]?.jsonPrimitive?.content == "plain prompt" }
        assertNull(plain["selection"])
    }

    @Test
    fun `open-file content replays as a one-line selection flagged file, the cursor-only record is not flagged`() {
        val u = users().first { it["text"]?.jsonPrimitive?.content == "What's in here?" }
        val sel = u["selection"]!!.jsonObject
        assertEquals(3, sel["start"]!!.jsonPrimitive.int)
        assertEquals(true, sel["file"]!!.jsonPrimitive.content.toBoolean())
        val cursor = users().first { it["text"]?.jsonPrimitive?.content == "Where am I?" }
        assertNull(cursor["selection"]!!.jsonObject["file"])
    }

    @Test
    fun `the thread title reads the prompt, never the tag`() {
        val title = SessionStore.list(CWD).first { it.id == ID }.title
        assertEquals("Explain this", title)
    }
}

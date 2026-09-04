package io.github.amitsidhpura.claudebrains

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.assertFalse
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.jsonArray
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test

/**
 * Pins the proposal reconstruction the editor permission diff is built from
 * (ChatPanel.openEditorPermissionDiff → EditProposals). The null cases matter as much as the
 * happy paths: null means "no editor diff, card-only" — the degrade direction when a wrong
 * diff would be worse than none.
 */
class EditProposalsTest {

    private fun obj(s: String) = Json.parseToJsonElement(s).jsonObject

    @Test
    fun `edit replaces the first occurrence only`() {
        assertEquals(
            "x b x a",
            EditProposals.proposedContent("Edit",
                obj("""{"old_string":"a","new_string":"b"}"""), "x a x a"),
        )
    }

    @Test
    fun `replace_all replaces every occurrence`() {
        assertEquals(
            "x b x b",
            EditProposals.proposedContent("Edit",
                obj("""{"old_string":"a","new_string":"b","replace_all":true}"""), "x a x a"),
        )
    }

    @Test
    fun `old_string not found degrades to null, never to a wrong diff`() {
        assertNull(EditProposals.proposedContent("Edit",
            obj("""{"old_string":"missing","new_string":"b"}"""), "content"))
    }

    @Test
    fun `write proposes its content wholesale, current file irrelevant`() {
        assertEquals("new body",
            EditProposals.proposedContent("Write", obj("""{"content":"new body"}"""), null))
    }

    @Test
    fun `multiedit applies edits in order, each against the previous result`() {
        // The second edit's old_string only EXISTS after the first applies — order is load-bearing.
        assertEquals(
            "left baz right",
            EditProposals.proposedContent("MultiEdit",
                obj("""{"edits":[{"old_string":"foo","new_string":"bar"},{"old_string":"bar","new_string":"baz"}]}"""),
                "left foo right"),
        )
    }

    @Test
    fun `one failing edit in a multiedit voids the whole proposal`() {
        assertNull(EditProposals.proposedContent("MultiEdit",
            obj("""{"edits":[{"old_string":"foo","new_string":"bar"},{"old_string":"zzz","new_string":"baz"}]}"""),
            "left foo right"))
    }

    @Test
    fun `empty old_string is create-only`() {
        assertEquals("seed",
            EditProposals.proposedContent("Edit",
                obj("""{"old_string":"","new_string":"seed"}"""), ""))
        assertNull(EditProposals.proposedContent("Edit",
            obj("""{"old_string":"","new_string":"seed"}"""), "not empty"))
    }

    @Test
    fun `negative control - unknown tool and missing file yield null`() {
        assertNull(EditProposals.proposedContent("Bash", obj("""{"command":"ls"}"""), "x"))
        assertNull(EditProposals.proposedContent("Edit",
            obj("""{"old_string":"a","new_string":"b"}"""), null))
    }

    // ---- tweak-travel (3.5) ---------------------------------------------------------------

    @Test
    fun `untouched pane answers with the original input (null)`() {
        assertNull(EditProposals.tweakedInput("Edit",
            obj("""{"file_path":"/f","old_string":"beta","new_string":"BETA"}"""),
            "alpha\nbeta\n", "alpha\nBETA\n"))
        assertNull(EditProposals.tweakedInput("Write",
            obj("""{"file_path":"/f","content":"x"}"""), null, "x"))
    }

    @Test
    fun `edited pane rides back as a whole-file edit, the shape VS Code sends`() {
        val t = EditProposals.tweakedInput("Edit",
            obj("""{"file_path":"/f","old_string":"beta","new_string":"BETA"}"""),
            "alpha\nbeta\n", "alpha\nBETA (tweaked)\n")!!
        assertEquals("/f", t["file_path"]!!.jsonPrimitive.content)
        assertEquals("alpha\nbeta\n", t["old_string"]!!.jsonPrimitive.content)
        assertEquals("alpha\nBETA (tweaked)\n", t["new_string"]!!.jsonPrimitive.content)
        assertEquals("false", t["replace_all"]!!.jsonPrimitive.content)
    }

    @Test
    fun `write and multiedit keep their own schemas`() {
        val w = EditProposals.tweakedInput("Write",
            obj("""{"file_path":"/f","content":"x"}"""), null, "y")!!
        assertEquals("y", w["content"]!!.jsonPrimitive.content)
        assertNull(w["old_string"])
        val m = EditProposals.tweakedInput("MultiEdit",
            obj("""{"file_path":"/f","edits":[{"old_string":"a","new_string":"b"}]}"""),
            "a", "c")!!
        val e = m["edits"]!!.jsonArray.single().jsonObject
        assertEquals("a", e["old_string"]!!.jsonPrimitive.content)
        assertEquals("c", e["new_string"]!!.jsonPrimitive.content)
    }

    @Test
    fun `underivable proposal never claims a tweak`() {
        assertNull(EditProposals.tweakedInput("Edit",
            obj("""{"file_path":"/f","old_string":"missing","new_string":"b"}"""), "content", "anything"))
        assertNull(EditProposals.tweakedInput("Bash", obj("""{"command":"ls"}"""), "x", "y"))
    }

    // Replay half. The Edit record is the 2026-08-28 probe, verbatim: the model proposed
    // beta→BETA, the user accepted "BETA (tweaked by user)" from the editor pane.
    private val probeInput = obj("""{"replace_all":false,"file_path":"/p/probe35.txt","old_string":"beta","new_string":"BETA"}""")
    private val probeResult = obj("""{"filePath":"/p/probe35.txt","oldString":"alpha\nbeta\ngamma\ndelta\n","newString":"alpha\nBETA (tweaked by user)\ngamma\ndelta\n","originalFile":"alpha\nbeta\ngamma\ndelta\n","userModified":false,"replaceAll":false}""")

    @Test
    fun `replay detects a tweaked edit from toolUseResult`() {
        assertTrue(EditProposals.tweaked("Edit", probeInput, probeResult))
    }

    @Test
    fun `negative control - the same edit applied as proposed is not a tweak`() {
        val plain = obj("""{"filePath":"/p/probe35.txt","oldString":"beta","newString":"BETA","originalFile":"alpha\nbeta\ngamma\ndelta\n","userModified":false,"replaceAll":false}""")
        assertFalse(EditProposals.tweaked("Edit", probeInput, plain))
        // Write create: originalFile null, content as proposed
        assertFalse(EditProposals.tweaked("Write",
            obj("""{"file_path":"/f","content":"x"}"""), obj("""{"type":"create","filePath":"/f","content":"x","originalFile":null}""")))
        assertTrue(EditProposals.tweaked("Write",
            obj("""{"file_path":"/f","content":"x"}"""), obj("""{"type":"create","filePath":"/f","content":"x-edited","originalFile":null}""")))
        // a result with no comparable fields is silence, not a claim
        assertFalse(EditProposals.tweaked("Edit", probeInput, obj("""{"filePath":"/p"}""")))
    }

    @Test fun `an edited command replaces only the command field (3-8)`() {
        val input = kotlinx.serialization.json.Json.parseToJsonElement(
            """{"command":"factor 97","description":"Factor the number 97","timeout":5000}""").jsonObject
        val out = EditProposals.withCommand(input, "factor 91")
        assertEquals("factor 91", out["command"]?.jsonPrimitive?.content)
        assertEquals(input["description"], out["description"])
        assertEquals(input["timeout"], out["timeout"])
    }

    @Test fun `no edit, a blank edit, or the same text leaves the input untouched`() {
        val input = kotlinx.serialization.json.Json.parseToJsonElement("""{"command":"factor 97"}""").jsonObject
        assertSame(input, EditProposals.withCommand(input, null))
        assertSame(input, EditProposals.withCommand(input, "  "))
        assertSame(input, EditProposals.withCommand(input, "factor 97"))
    }

    @Test fun `an edited command's grant is rewritten to one exact rule for the edited text (3-8)`() {
        val sugg = kotlinx.serialization.json.Json.parseToJsonElement(
            """[{"type":"addRules","rules":[{"toolName":"Bash","ruleContent":"factor 97"}],"behavior":"allow","destination":"localSettings"},
                {"type":"setMode","mode":"acceptEdits","destination":"session"}]""").jsonArray.map { it.jsonObject }
        val out = EditProposals.withRulesFor(sugg, "factor 91")
        assertEquals("""[{"toolName":"Bash","ruleContent":"factor 91"}]""", out[0]["rules"].toString())
        // a compound edit → one exact rule per part, as the CLI's own suggestions are shaped
        val parts = EditProposals.withRulesFor(sugg, "factor 91 && factor 95")
        assertEquals("""[{"toolName":"Bash","ruleContent":"factor 91"},{"toolName":"Bash","ruleContent":"factor 95"}]""", parts[0]["rules"].toString())
        assertEquals("localSettings", out[0]["destination"]?.jsonPrimitive?.content)   // the rest rides untouched
        assertSame(sugg[1], out[1])                                                    // setMode is not a rule
        assertSame(sugg, EditProposals.withRulesFor(sugg, null))
    }

    @Test fun `splitCommand follows the CLI's per-part shape and leaves quotes and subshells whole`() {
        assertEquals(listOf("factor 97", "mcookie", "openssl rand -hex 4", "base32 <<< hello"),
            EditProposals.splitCommand("factor 97 && mcookie ; openssl rand -hex 4 && base32 <<< hello"))
        assertEquals(listOf("factor 91", "factor 95"), EditProposals.splitCommand("factor 91 & factor 95"))
        assertEquals(listOf("ls", "grep x", "true"), EditProposals.splitCommand("ls | grep x || true | grep x"))  // deduped
        assertEquals(listOf("echo 'a && b'", "(cd x && make)"), EditProposals.splitCommand("echo 'a && b'; (cd x && make)"))
        assertEquals(listOf("factor 97"), EditProposals.splitCommand("  factor 97  "))
    }
}

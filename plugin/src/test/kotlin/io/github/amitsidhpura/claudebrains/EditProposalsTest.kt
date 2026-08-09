package io.github.amitsidhpura.claudebrains

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
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
}

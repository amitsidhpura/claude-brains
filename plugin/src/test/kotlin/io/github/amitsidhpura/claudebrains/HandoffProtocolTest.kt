package io.github.amitsidhpura.claudebrains

import io.github.amitsidhpura.claudebrains.cli.ClaudeCli
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Model-switch handoff: the protocol text the plugin appends to the system prompt and the marker
 * the webview parses are two halves of one format. A change to either must change both.
 */
class HandoffProtocolTest {
    private val js = javaClass.getResourceAsStream("/webview/js/20-markdown.js")!!
        .use { it.readBytes() }.toString(Charsets.UTF_8)

    /** The page's own pattern, read out of the source so the test cannot drift from it. */
    private val markerRe: Regex = Regex("""const HANDOFF_RE = /(.+)/;""").find(js)!!.groupValues[1].toRegex()

    @Test fun `the protocol ships and teaches the marker the page parses`() {
        val p = ClaudeCli.HANDOFF_PROTOCOL
        assertTrue(p.startsWith("# Model-switch protocol"), "not the protocol text?")
        val template = """<<MODEL_SWITCH v="1" model="<alias>" next="<step-id>">>"""
        assertTrue(p.contains(template), "the protocol's marker template changed")
        val filled = template.replace("<alias>", "opus").replace("<step-id>", "step-2")
        val m = markerRe.find(filled)
        assertEquals(listOf("opus", "step-2"), m?.groupValues?.drop(1),
            "the marker the protocol teaches must be the one the page reads")
        assertTrue(p.contains("continue next=<step-id>"), "the continue message the page sends is named in the protocol")
    }

    @Test fun `the page offers exactly the models the protocol names`() {
        val models = Regex("""const HANDOFF_MODELS = \[([^\]]+)]""").find(js)!!.groupValues[1]
            .split(',').map { it.trim().trim('\'') }
        assertEquals(listOf("fable", "opus", "sonnet", "haiku"), models)
        assertTrue(ClaudeCli.HANDOFF_PROTOCOL.contains("one of `fable`, `opus`, `sonnet`, `haiku`"))
    }

    @Test fun `only a whole last line is a marker`() {
        assertTrue(markerRe.find("""see <<MODEL_SWITCH v="1" model="opus" next="s">>""") == null)
        assertTrue(markerRe.find("""<<MODEL_SWITCH v="2" model="opus" next="s">>""") == null, "an unknown version is not acted on")
        assertTrue(markerRe.find("""<<MODEL_SWITCH v="1" model="opus" next="s 2">>""") == null)
        assertTrue(markerRe.find("""<<MODEL_SWITCH v="1" model="opus" next="s" state="./.handoff/state.md">>""") == null,
            "the first build's marker with a state file is not this protocol's")
        assertTrue(ClaudeCli.HANDOFF_PROTOCOL.contains("Write no handoff or state files"))
    }
}

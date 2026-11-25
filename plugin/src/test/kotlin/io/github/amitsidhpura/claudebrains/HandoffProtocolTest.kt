package io.github.amitsidhpura.claudebrains

import io.github.amitsidhpura.claudebrains.cli.ClaudeCli
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Model-switch protocol (register 9.12): the text the plugin appends to the system prompt and the
 * marker the webview parses are two halves of one format. A change to either must change both.
 */
class HandoffProtocolTest {
    private val js = javaClass.getResourceAsStream("/webview/js/20-markdown.js")!!
        .use { it.readBytes() }.toString(Charsets.UTF_8)
    private val p = ClaudeCli.HANDOFF_PROTOCOL

    /** The page's own pattern and lists, read out of the source so the test cannot drift from it. */
    private val markerRe: Regex = Regex("""const HANDOFF_RE = /(.+)/;""").find(js)!!.groupValues[1].toRegex()
    private fun list(name: String) = Regex("""const $name = \[([^\]]+)]""").find(js)!!.groupValues[1]
        .split(',').map { it.trim().trim('\'') }

    private val template = """<<MODEL_SWITCH v="1" model="<alias>" effort="<level>" speed="<speed>" next="<step-id>" reason="<why>">>"""

    @Test fun `the protocol ships and teaches the marker the page parses`() {
        assertTrue(p.startsWith("# Model-switch protocol"), "not the protocol text?")
        assertTrue(p.contains(template), "the protocol's marker template changed")
        val filled = template.replace("<alias>", "opus").replace("<level>", "high").replace("<speed>", "fast")
            .replace("<step-id>", "step-2").replace("<why>", "a long refactor")
        assertNotNull(markerRe.find(filled), "the marker the protocol teaches must be one the page reads")
        assertNotNull(markerRe.find("""<<MODEL_SWITCH v="1" effort="low" next="step-3">>"""), "an effort-only change")
        assertTrue(p.contains("continue next=<step-id>"), "the continue message the page sends is named in the protocol")
    }

    @Test fun `the page's attribute names, models, levels and speeds are the protocol's`() {
        assertEquals(listOf("model", "effort", "speed", "next", "reason"), list("HANDOFF_KEYS"))
        assertEquals(list("HANDOFF_KEYS"), Regex(""" ([a-z]+)="""").findAll(template).map { it.groupValues[1] }.drop(1).toList(),
            "the template names the page's attributes, in the page's order")
        assertEquals(listOf("fable", "opus", "sonnet", "haiku"), list("HANDOFF_MODELS"))
        assertTrue(p.contains("one of `fable`, `opus`, `sonnet`, `haiku`"))
        assertEquals(listOf("low", "medium", "high", "xhigh", "max"), list("HANDOFF_EFFORTS"))
        assertTrue(p.contains("one of `low`, `medium`, `high`, `xhigh`, `max`"))
        assertEquals(listOf("fast", "standard"), list("HANDOFF_SPEEDS"))
        assertTrue(p.contains("`fast` or `standard`"))
    }

    @Test fun `only a whole last line is a marker`() {
        assertNull(markerRe.find("""see <<MODEL_SWITCH v="1" model="opus" next="s">>"""))
        assertNull(markerRe.find("""<<MODEL_SWITCH v="2" model="opus" next="s">>"""), "an unknown version is not acted on")
    }

    @Test fun `the protocol explains the session-state note the plugin sends`() {
        assertTrue(p.contains("`<session_state>`"))
        assertTrue(p.contains("50, 75 or 90 %"), "the thresholds named in the protocol are SessionStateNote's")
        assertEquals(listOf(50, 75, 90), SessionStateNote.THRESHOLDS)
        assertTrue(p.contains("Write no handoff or state files"))
    }

    @Test fun `a loaded model-selection guide outranks the built-in rule`() {
        val i = p.indexOf("## Deciding, before every step")
        assertTrue(i >= 0)
        val next = p.indexOf("\n## ", i + 1)
        val deciding = p.substring(i, if (next < 0) p.length else next)
        assertTrue(deciding.replace("\n", " ").contains("If a model-selection guide is in your context (for example the cxtify topic model-picker), decide by it; the rule below is only the fallback."),
            "the precedence line opens § Deciding")
    }
}

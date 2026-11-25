package io.github.amitsidhpura.claudebrains

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Model-switch items 3 and 4: the `<session_state>` note, from the CLI's own answers. */
class SessionStateNoteTest {
    private fun j(s: String): JsonObject = Json.parseToJsonElement(s).jsonObject

    // Shapes cut down from the 2026-11-25 get_settings / get_usage answers (2.1.331, stdio).
    private val settings = j("""{"effective":{},"applied":{"model":"claude-opus-5-5","effort":"medium","advisor":null}}""")
    private fun usage(five: Int, week: Int, fable: Int, credits: Boolean) = j("""{"subscription_type":"max","rate_limits":{
        "five_hour":{"utilization":$five,"resets_at":"2026-11-25T19:19:59.584534+00:00"},
        "seven_day":{"utilization":$week,"resets_at":"2026-11-27T22:59:59.584560+00:00"},
        "seven_day_opus":null,
        "extra_usage":{"is_enabled":$credits},
        "model_scoped":[{"display_name":"Fable","utilization":$fable,"resets_at":"2026-11-27T22:59:59.584781+00:00"}]}}""")

    @Test fun `the note names the model, effort, fast mode and every limit`() {
        val tag = SessionStateNote.tag(SessionStateNote.snapshot(settings, usage(1, 59, 100, false), "off"))!!
        assertEquals("<session_state>Now on: model claude-opus-5-5, effort medium, fast mode off.\n" +
            "Plan usage: 5-hour limit 1% used, resets 2026-11-25 19:19 UTC; weekly limit 59% used, resets 2026-11-27 22:59 UTC; " +
            "Fable weekly limit 100% used, resets 2026-11-27 22:59 UTC — used up; usage credits are off.\n" +
            "Added by the chat client, not typed by the user.</session_state>", tag)
        assertTrue(SessionStateNote.tag(SessionStateNote.snapshot(settings, usage(1, 59, 100, true), "on"))!!
            .contains("used up; further use bills usage credits"))
    }

    @Test fun `a new note is due only on a change or a crossed threshold`() {
        fun key(five: Int, effort: String = "medium") = SessionStateNote.snapshot(
            j("""{"applied":{"model":"claude-opus-5-5","effort":"$effort"}}"""), usage(five, 59, 40, false), "off").key()
        assertEquals(key(10), key(49), "usage moving inside a band says nothing new")
        assertNotEquals(key(49), key(50), "crossing 50 % does")
        assertNotEquals(key(74), key(75)); assertNotEquals(key(89), key(90))
        assertEquals(key(91), key(99))
        assertNotEquals(key(10), key(10, "high"), "an effort change does")
    }

    @Test fun `nothing known gives no note`() {
        assertEquals(null, SessionStateNote.tag(SessionStateNote.snapshot(null, null, null)))
    }

    @Test fun `replay and titles lose the note, the selection tag survives under it`() {
        val tag = SessionStateNote.tag(SessionStateNote.snapshot(settings, null, null))!!
        val withSel = RenderLimits.withIdeSelection("fix it", RenderLimits.ideSelectionTag("a.kt", 3, 4, "x"))
        val sent = SessionStateNote.append(withSel, tag)
        val (body, had) = SessionStateNote.split(sent)
        assertTrue(had); assertEquals(withSel, body)
        assertEquals("a.kt", RenderLimits.splitIdeSelection(body).second?.path)
        val (plain, none) = SessionStateNote.split("just text")
        assertFalse(none); assertEquals("just text", plain)
        assertEquals("", SessionStateNote.split(SessionStateNote.append("", tag)).first, "a tag-only message (images alone) is empty text")
    }
}

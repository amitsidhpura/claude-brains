package io.github.amitsidhpura.claudebrains

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test

/** Pins the destination-stamping rule behind the Always-allow caret (checklist 4.8). */
class PermissionDestinationsTest {
    private val rule = Json.parseToJsonElement(
        """{"type":"addRules","rules":[{"toolName":"Bash","ruleContent":"factor 97"}],"behavior":"allow","destination":"localSettings"}"""
    ).jsonObject
    private val mode = Json.parseToJsonElement(
        """{"type":"setMode","mode":"acceptEdits","destination":"session"}"""
    ).jsonObject

    @Test fun `a picked destination replaces the suggestion's own on rule grants`() {
        val out = PermissionDestinations.stamp(listOf(rule), "projectSettings")
        assertEquals("projectSettings", out[0]["destination"]?.jsonPrimitive?.content)
        assertEquals(rule["rules"], out[0]["rules"])          // the rest of the entry rides untouched
    }

    @Test fun `setMode entries keep their own scope`() {
        val out = PermissionDestinations.stamp(listOf(mode, rule), "userSettings")
        assertSame(mode, out[0])
        assertEquals("userSettings", out[1]["destination"]?.jsonPrimitive?.content)
    }

    @Test fun `no pick or an unknown value leaves the entries exactly as the CLI suggested`() {
        // An unknown destination makes the CLI drop the grant silently (measured 2.1.260), so it is
        // never forwarded — the suggestion's own destination is the safe answer.
        val entries = listOf(rule)
        assertSame(entries, PermissionDestinations.stamp(entries, null))
        assertSame(entries, PermissionDestinations.stamp(entries, "cliArg"))
        assertSame(entries, PermissionDestinations.stamp(entries, "bogus"))
        assertNull(PermissionDestinations.stamp(emptyList(), "session").firstOrNull())
    }

    @Test fun `exactly the four the picker offers are forwarded`() {
        assertEquals(setOf("session", "localSettings", "projectSettings", "userSettings"), PermissionDestinations.OFFERED)
    }
}

package io.github.amitsidhpura.claudebrains

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/** 9.2: which messages count as an effort pick the plugin saves and re-applies at start. */
class EffortPickTest {
    @Test fun `the five slider levels are picks`() {
        for (l in listOf("low", "medium", "high", "xhigh", "max")) assertEquals(l, EffortPick.parse("/effort $l"))
    }

    @Test fun `surrounding whitespace is fine`() {
        assertEquals("low", EffortPick.parse("  /effort   low \n"))
    }

    @Test fun `anything else is left to the CLI and not saved`() {
        assertNull(EffortPick.parse("/effort"))
        assertNull(EffortPick.parse("/effort "))
        assertNull(EffortPick.parse("/effort bogus"))
        assertNull(EffortPick.parse("/effort High"))      // the CLI's levels are lower case
        assertNull(EffortPick.parse("/effort low please"))
        assertNull(EffortPick.parse("/efforts low"))
        assertNull(EffortPick.parse("set /effort low"))
        assertNull(EffortPick.parse("/model opus"))
    }
}

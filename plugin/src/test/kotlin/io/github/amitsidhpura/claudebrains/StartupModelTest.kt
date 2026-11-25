package io.github.amitsidhpura.claudebrains

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * #65 doubt 19 (2026-11-26): the model sent to the CLI at every start. A CLI sent none ran Sonnet 5.5
 * while its Default row named Opus 5.5 (2.1.332), so "nothing saved" and "Default saved" both send
 * `default` now; any other pick is sent as saved.
 */
class StartupModelTest {
    @Test fun `nothing saved sends default`() {
        assertEquals("default", startupModel(null))
        assertEquals("default", startupModel(""))
        assertEquals("default", startupModel("  "))
    }

    @Test fun `default saved sends default`() {
        assertEquals("default", startupModel("default"))
    }

    @Test fun `any other pick is sent as saved`() {
        assertEquals("opus", startupModel("opus"))
        assertEquals("claude-sonnet-5", startupModel("claude-sonnet-5"))
    }
}

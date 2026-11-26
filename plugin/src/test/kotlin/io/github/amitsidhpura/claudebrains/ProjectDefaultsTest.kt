package io.github.amitsidhpura.claudebrains

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * The per-project settings (2026-11-26): a project with nothing saved starts on Manual and on
 * `medium` effort, whatever another project or the user's `permissions.defaultMode` says.
 */
class ProjectDefaultsTest {
    @Test fun `nothing saved starts on Manual`() {
        assertEquals("manual", PermissionModes.resolveStored(null))
        assertEquals("manual", PermissionModes.resolveStored(""))
    }

    @Test fun `nothing saved starts on medium effort`() {
        assertEquals("medium", startupEffort(null))
        assertEquals("medium", startupEffort(""))
    }

    @Test fun `a value that is not a level starts on medium effort`() {
        assertEquals("medium", startupEffort("turbo"))
    }

    @Test fun `a saved level is kept`() {
        for (lvl in EffortPick.LEVELS) assertEquals(lvl, startupEffort(lvl))
    }
}

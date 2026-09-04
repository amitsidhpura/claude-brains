package io.github.amitsidhpura.claudebrains

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/** Pins the persisted-mode rule behind the spawn flag (checklist 4.5 / 4.7). */
class PermissionModesTest {
    @Test fun `nothing persisted means no flag, so the CLI applies the user's own defaultMode`() {
        assertNull(PermissionModes.resolveStored(null))
        assertNull(PermissionModes.resolveStored(""))
    }

    @Test fun `stored values are migrated to the CLI's current vocabulary`() {
        assertEquals("manual", PermissionModes.resolveStored("default"))
        assertEquals("auto", PermissionModes.resolveStored("bypassPermissions"))
        assertEquals("plan", PermissionModes.resolveStored("plan"))
        assertEquals("manual", PermissionModes.resolveStored("manual"))
    }
}

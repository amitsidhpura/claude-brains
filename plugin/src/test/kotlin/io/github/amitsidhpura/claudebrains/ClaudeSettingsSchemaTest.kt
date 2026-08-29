package io.github.amitsidhpura.claudebrains

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** The matcher behind the schema provider (13.2): both settings files, only inside `.claude`. */
class ClaudeSettingsSchemaTest {
    private fun m(name: String, parent: String?) = ClaudeSettingsSchemaProviderFactory.matches(name, parent)

    @Test
    fun `both settings files inside a claude directory match`() {
        assertTrue(m("settings.json", ".claude"), "project / user settings")
        assertTrue(m("settings.local.json", ".claude"), "the file the CLI writes rules into — the SchemaStore catalog misses it")
    }

    @Test
    fun `anything else does not`() {
        assertFalse(m("settings.json", "config"), "a settings.json elsewhere is somebody else's")
        assertFalse(m("settings.json", null), "no parent")
        assertFalse(m("mcp.json", ".claude"), "other files under .claude have other shapes")
        assertFalse(m("settings.json.bak", ".claude"), "exact names only")
    }

    @Test
    fun `the schema is the published SchemaStore one, never a bundled copy`() {
        assertTrue(ClaudeSettingsSchemaProviderFactory.URL.startsWith("https://json.schemastore.org/"))
    }
}

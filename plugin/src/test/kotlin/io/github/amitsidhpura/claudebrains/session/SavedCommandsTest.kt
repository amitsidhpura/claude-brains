package io.github.amitsidhpura.claudebrains.session

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** 7.11 / 7.12: the command file a saved message becomes, the suggested name, and the recency map. */
class SavedCommandsTest {
    @Test
    fun `a plain prompt is the whole file - no frontmatter`() {
        assertEquals("Run the tests\n", SavedCommands.fileBody("", "Run the tests\n\n"))
    }

    @Test
    fun `description is one quoted line, and placeholders get a hint`() {
        val f = SavedCommands.fileBody("  Tests: \"all\"\nthen fix  ", "Fix \$ARGUMENTS please")
        assertEquals("---\ndescription: \"Tests: \\\"all\\\" then fix\"\nargument-hint: \"[text]\"\n---\nFix \$ARGUMENTS please\n", f)
        assertEquals("[text]", SavedCommands.argumentHint("use \$1"))
        // the CLI substitutes \$1..\$9 too, so a literal "\$5" is a placeholder either way
        assertEquals("[text]", SavedCommands.argumentHint("costs \$5"))
        assertNull(SavedCommands.argumentHint("no placeholders here"))
    }

    @Test
    fun `suggested names are valid command names`() {
        assertEquals("run-the-tests", SavedCommands.suggestName("Run the tests and fix what fails"))
        assertEquals("fix-login", SavedCommands.suggestName("  fix: login!"))
        assertEquals("v2-release", SavedCommands.suggestName("2.0 → v2 release"))
        assertEquals("saved-message", SavedCommands.suggestName("??? 123"))
        listOf("Run the tests", "  fix: login!", "2.0 → v2 release", "??? 123").forEach {
            assertTrue(SavedCommands.NAME.matches(SavedCommands.suggestName(it)), it)
        }
        assertFalse(SavedCommands.NAME.matches("Upper"))
        assertFalse(SavedCommands.NAME.matches("sub:name"))
        assertFalse(SavedCommands.NAME.matches("1st"))
    }

    @Test
    fun `recency keeps the newest and survives garbage`() {
        var s: String? = "not json"
        assertEquals(emptyMap<String, Long>(), SavedCommands.parseRecent(s))
        s = SavedCommands.bumpRecent(s, "a", 10)
        s = SavedCommands.bumpRecent(s, "b", 20)
        s = SavedCommands.bumpRecent(s, "a", 30)
        assertEquals(mapOf("a" to 30L, "b" to 20L), SavedCommands.parseRecent(s))
        repeat(SavedCommands.MAX_RECENT + 5) { s = SavedCommands.bumpRecent(s, "c$it", 100L + it) }
        val m = SavedCommands.parseRecent(s)
        assertEquals(SavedCommands.MAX_RECENT, m.size)
        assertFalse("a" in m, "the oldest fall off")
        assertTrue("c${SavedCommands.MAX_RECENT + 4}" in m)
    }
}

package io.github.amitsidhpura.claudebrains

import io.github.amitsidhpura.claudebrains.ui.MentionPaths
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/** Pins the selection → `@mention` token rule behind MentionAction (checklist 6.5). */
class MentionPathsTest {
    private val base = "/home/u/proj"

    @Test fun `files under the project base become project-relative, folders get a trailing slash`() {
        val out = MentionPaths.tokens(base, listOf(
            "/home/u/proj/src/App.tsx" to false,
            "/home/u/proj/docs" to true,
        ))
        assertEquals(listOf("src/App.tsx", "docs/"), out)
    }

    @Test fun `a path outside the project stays absolute, and the base itself is the dot folder`() {
        val out = MentionPaths.tokens(base, listOf(
            "/etc/hosts" to false,
            "/home/u/proj" to true,
            "/home/u/projects/other.txt" to false,   // shares the prefix text, is NOT under the base
        ))
        assertEquals(listOf("/etc/hosts", "./", "/home/u/projects/other.txt"), out)
    }

    @Test fun `windows separators normalise and duplicates collapse in selection order`() {
        val out = MentionPaths.tokens("C:\\work\\proj\\", listOf(
            "C:\\work\\proj\\b.kt" to false,
            "C:/work/proj/a.kt" to false,
            "C:\\work\\proj\\b.kt" to false,
        ))
        assertEquals(listOf("b.kt", "a.kt"), out)
    }

    @Test fun `no base path means every token is absolute`() {
        assertEquals(listOf("/x/y.txt"), MentionPaths.tokens(null, listOf("/x/y.txt" to false)))
    }
}

package io.github.amitsidhpura.claudebrains

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/** Pins which content roots become `--add-dir` arguments (checklist 2.12). */
class WorkspaceRootsTest {
    private val base = "/home/dev/Sites/app"

    @Test fun `roots inside the base directory are covered by the cwd and dropped`() {
        assertEquals(emptyList<String>(),
            WorkspaceRoots.extraDirs(base, listOf(base, "$base/src", "$base/tests/")))
    }

    @Test fun `an attached sibling is extra`() {
        assertEquals(listOf("/home/dev/Sites/computer"),
            WorkspaceRoots.extraDirs(base, listOf(base, "/home/dev/Sites/computer")))
    }

    @Test fun `a monorepo parent covers a sibling attached beside it`() {
        assertEquals(listOf("/home/dev/Sites"),
            WorkspaceRoots.extraDirs(base, listOf(base, "/home/dev/Sites/computer", "/home/dev/Sites")))
    }

    @Test fun `a prefix that is not a path boundary is not nested`() {
        // "/home/dev/Sites/app2" starts with the base's text but is a different directory
        assertEquals(listOf("/home/dev/Sites/app2"), WorkspaceRoots.extraDirs(base, listOf("/home/dev/Sites/app2")))
    }

    @Test fun `a root under another extra root is dropped, duplicates and separators normalised`() {
        assertEquals(listOf("D:/sites/lib"),
            WorkspaceRoots.extraDirs("D:/sites/app", listOf("D:\\sites\\lib\\", "D:/sites/lib", "D:/sites/lib/core")))
    }

    @Test fun `no base path means every root is extra`() {
        assertEquals(listOf("/a", "/b"), WorkspaceRoots.extraDirs(null, listOf("/a", "/b")))
    }
}

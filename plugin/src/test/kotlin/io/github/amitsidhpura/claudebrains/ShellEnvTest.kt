package io.github.amitsidhpura.claudebrains

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

/**
 * Pins the PATH lookup the CLI executable goes through. The lookup must walk the SAME PATH the
 * CLI is spawned under: on 2026-09-27 it walked the IDE's bare PATH instead, missed the
 * standalone 2.1.283 in `~/.local/bin` and launched the VS Code extension's 2.1.270 — the panel
 * then lacked every model that CLI predates (Opus 5.5) while the terminal had them.
 */
class ShellEnvTest {
    private val sep = File.pathSeparator

    private fun exe(dir: File, name: String = "claude"): File =
        File(dir, name).apply { writeText("#!/bin/sh\n"); setExecutable(true) }

    @Test fun `the first PATH entry holding an executable wins`(@TempDir a: File, @TempDir b: File) {
        exe(a); exe(b)
        assertEquals(File(a, "claude").absolutePath, ShellEnv.which("claude", "$a$sep$b"))
    }

    @Test fun `entries without the executable are skipped, empty entries too`(@TempDir a: File, @TempDir b: File) {
        exe(b)
        assertEquals(File(b, "claude").absolutePath, ShellEnv.which("claude", "$a$sep${sep}$b"))
    }

    @Test fun `a directory named like the executable is not a match`(@TempDir a: File) {
        File(a, "claude").mkdir()
        assertNull(ShellEnv.which("claude", a.path))
    }

    @Test fun `no PATH resolves nothing`(@TempDir a: File) {
        exe(a)
        assertNull(ShellEnv.which("claude", null))
        assertNull(ShellEnv.which("claude", ""))
    }
}

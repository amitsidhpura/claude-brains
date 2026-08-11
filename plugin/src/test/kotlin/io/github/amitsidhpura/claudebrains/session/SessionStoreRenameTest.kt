package io.github.amitsidhpura.claudebrains.session

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.File

/**
 * Renaming a conversation writes the CLI's own `custom-title` record — the write half of what
 * [SessionStore.computeTitle] already reads (client-parity item 34).
 *
 * The shape asserted here is not invented: it was read out of the 2.1.226 binary, where the CLI
 * builds `{type:"custom-title", customTitle: title.trim(), sessionId}` and appends it with a
 * trailing newline. If that ever drifts, this test is the thing that should fail — a rename the
 * terminal cannot read back is worse than no rename at all.
 *
 * Its own fixture rather than a record spliced into `replay-sample.jsonl`: that file is a chain of
 * real records with a uuid/parentUuid spine, and this test needs to append to a file and read the
 * bytes back.
 */
class SessionStoreRenameTest {

    private val cwd = "/home/dev/Sites/rename-project"
    private val id = "rename-fixture"
    private lateinit var home: File
    private lateinit var realHome: File
    private lateinit var transcript: File

    private val original = listOf(
        """{"parentUuid":null,"uuid":"u0","type":"user","cwd":"$cwd","message":{"role":"user","content":"do a thing"}}""",
        """{"parentUuid":"u0","uuid":"u1","type":"ai-title","aiTitle":"Doing the thing"}""",
    )

    @BeforeEach
    fun layOutFixture() {
        home = File.createTempFile("claude-home-rename", "").let { it.delete(); it.mkdirs(); it }
        val dir = File(home, ".claude/projects/${cwd.replace(Regex("[^a-zA-Z0-9]"), "-")}")
        dir.mkdirs()
        transcript = File(dir, "$id.jsonl")
        transcript.writeText(original.joinToString("\n") + "\n")
        realHome = SessionStore.claudeHome
        SessionStore.claudeHome = home
    }

    @AfterEach
    fun restore() {
        SessionStore.claudeHome = realHome
        home.deleteRecursively()
    }

    @Test
    fun `a rename appends exactly the record the CLI writes, and nothing else`() {
        assertTrue(SessionStore.rename(cwd, id, "Queue spacing fix"))
        val lines = transcript.readLines()
        assertEquals(original + listOf("""{"type":"custom-title","customTitle":"Queue spacing fix","sessionId":"$id"}"""), lines,
            "the appended line must match the CLI's record byte for byte, and the file must be otherwise untouched")
        assertTrue(transcript.readText().endsWith("\n"), "the CLI terminates the record with a newline")
    }

    @Test
    fun `the new name is what the conversation is then called`() {
        assertEquals("Doing the thing", SessionStore.titleOf(cwd, id), "derived title before the rename")
        SessionStore.rename(cwd, id, "Queue spacing fix")
        assertEquals("Queue spacing fix", SessionStore.titleOf(cwd, id), "custom title outranks the ai-title")
    }

    /** Renaming twice is normal; the last one is the name. Nothing rewrites, so both records stay. */
    @Test
    fun `a later rename supersedes an earlier one`() {
        SessionStore.rename(cwd, id, "First name")
        SessionStore.rename(cwd, id, "Second name")
        assertEquals("Second name", SessionStore.titleOf(cwd, id))
        assertEquals(2, transcript.readLines().count { it.contains("custom-title") }, "append-only: both records survive")
    }

    @Test
    fun `the title is trimmed, exactly as the CLI trims it`() {
        SessionStore.rename(cwd, id, "   Padded name  ")
        assertTrue(transcript.readText().contains(""""customTitle":"Padded name""""), "leading/trailing space is not part of the name")
    }

    /** A name cannot be erased — the CLI throws on an empty title, so refusing is the parity move. */
    @Test
    fun `a blank title is refused and writes nothing`() {
        val before = transcript.readText()
        assertFalse(SessionStore.rename(cwd, id, "   "))
        assertEquals(before, transcript.readText(), "a refused rename must not touch the file")
    }

    /** The id comes from the webview, so it gets the same guard [SessionStore.delete] uses. */
    @Test
    fun `an id that could walk out of the project directory is refused`() {
        assertFalse(SessionStore.rename(cwd, "../escape", "Anything"))
        assertFalse(SessionStore.rename(cwd, "has/slash", "Anything"))
        assertFalse(SessionStore.rename(cwd, "", "Anything"))
    }

    @Test
    fun `renaming a conversation with no transcript on disk is refused, not created`() {
        val dir = transcript.parentFile
        assertFalse(SessionStore.rename(cwd, "never-spoke", "Anything"))
        assertFalse(File(dir, "never-spoke.jsonl").exists(), "a rename must never conjure a transcript")
    }
}

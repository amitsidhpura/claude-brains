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

    /**
     * A rename is appended WHERE IT HAPPENS, so on a long-lived thread it lands thousands of records
     * past the head window [SessionStore.computeTitle] parses in full. This is the defect 0.5.0
     * shipped with: on a real 10,458-line transcript the renames sat on lines 10455-10458, were
     * written perfectly, and were never read back — so the header kept showing the derived
     * first-message title and the rename looked like it had done nothing at all.
     */
    @Test
    fun `a rename far past the head window is still the name`() {
        val filler = (0 until 900).joinToString("\n") { i ->
            """{"parentUuid":"u1","uuid":"f$i","type":"assistant","message":{"role":"assistant","content":"turn $i"}}"""
        }
        transcript.appendText(filler + "\n")
        assertEquals("Doing the thing", SessionStore.titleOf(cwd, id), "derived title before the rename")
        assertTrue(SessionStore.rename(cwd, id, "metrobuildsuppliers main development"))
        assertTrue(transcript.readLines().size > 400, "the fixture must be longer than the head window to test anything")
        assertEquals("metrobuildsuppliers main development", SessionStore.titleOf(cwd, id),
            "a rename must be permanent, however long the conversation it names")
    }

    /**
     * Past the head window lines are rejected on a substring before being parsed, so a message that
     * merely QUOTES a title record's key gets parsed after all — and must not be mistaken for one.
     */
    @Test
    fun `a late message quoting a title key does not become the title`() {
        transcript.appendText((0 until 500).joinToString("\n") { i ->
            """{"parentUuid":"u1","uuid":"q$i","type":"user","message":{"role":"user","content":"what writes the \"ai-title\" record?"}}"""
        } + "\n")
        assertEquals("Doing the thing", SessionStore.titleOf(cwd, id), "the ai-title at the head is still the name")
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

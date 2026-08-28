package io.github.amitsidhpura.claudebrains

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Pins the per-turn change tracker behind "Files changed · Review" (checklist 3.6). */
class TurnChangesTest {

    @Test
    fun `first touch keeps the baseline, later touches in the same turn do not overwrite it`() {
        val t = TurnChanges()
        t.snapshot("/f", "v0")
        t.snapshot("/f", "v1")      // second Edit in the turn: the CLI already changed the file
        val (idx, changes) = t.endTurn { "v2" }!!
        assertEquals(1, idx)
        assertEquals(listOf(TurnChanges.Change("/f", 1, 1, false)), changes)
        assertEquals("v0", t.review(1).single().before)
        assertEquals("v2", t.review(1).single().after)
    }

    @Test
    fun `unchanged and vanished files are dropped, a created file is new`() {
        val t = TurnChanges()
        t.snapshot("/same", "x")
        t.snapshot("/gone", "y")
        t.snapshot("/new", null)
        val (_, changes) = t.endTurn { p -> when (p) { "/same" -> "x"; "/new" -> "a\nb"; else -> null } }!!
        assertEquals(listOf(TurnChanges.Change("/new", 2, 1, true)), changes)   // "" is one empty line
    }

    @Test
    fun `negative control - a turn with no edit tools reports nothing and keeps no pairs`() {
        val t = TurnChanges()
        assertNull(t.endTurn { "anything" })
        assertTrue(t.review(1).isEmpty())
        t.snapshot("/f", "x")
        assertNull(t.endTurn { "x" })          // touched but identical → nothing to review
        assertTrue(t.review(1).isEmpty())
    }

    @Test
    fun `turns are independent and the index only advances when something was touched`() {
        val t = TurnChanges()
        t.snapshot("/a", "1"); t.endTurn { "2" }
        assertNull(t.endTurn { "zzz" })         // nothing touched: no index spent
        t.snapshot("/b", "1"); val (idx, _) = t.endTurn { "3" }!!
        assertEquals(2, idx)
        assertEquals("/a", t.review(1).single().path)
        assertEquals("/b", t.review(2).single().path)
    }

    @Test
    fun `line delta trims the common prefix and suffix`() {
        assertEquals(1 to 1, TurnChanges.lineDelta("a\nb\nc", "a\nB\nc"))
        assertEquals(2 to 0, TurnChanges.lineDelta("a\nc", "a\nx\ny\nc"))
        assertEquals(0 to 0, TurnChanges.lineDelta("same", "same"))
    }
}

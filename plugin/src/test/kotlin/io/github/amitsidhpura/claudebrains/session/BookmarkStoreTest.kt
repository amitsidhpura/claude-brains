package io.github.amitsidhpura.claudebrains.session

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

/** 8.15: the per-conversation bookmark file — add, remove, the file's shape, and its going away. */
class BookmarkStoreTest {
    @TempDir lateinit var dir: File

    @Test
    fun `add then remove, the file goes with the last bookmark`() {
        val store = BookmarkStore(dir)
        val sid = "e5bacd49-84d9-44cf-ac11-b1c890d137df"
        assertEquals(emptyList<BookmarkStore.Bookmark>(), store.list(sid))
        val one = store.update(sid, "u1", on = true, writtenAt = 1793038614810)
        assertEquals(listOf("u1"), one.map { it.uuid })
        assertEquals(1793038614810, one[0].writtenAt)
        val f = File(dir, "$sid.json")
        assertTrue(f.isFile)
        val text = f.readText()
        assertTrue(text.contains("\"bookmarks\"") && text.contains("\"updatedAt\"") && text.contains("\"writtenAt\":1793038614810"), text)
        assertFalse(text.contains("text"), "the file never holds the reply's text")

        val two = store.update(sid, "u2", on = true, writtenAt = null)
        assertEquals(listOf("u1", "u2"), two.map { it.uuid })
        assertNull(two[1].writtenAt)
        assertEquals(listOf("u1", "u2"), store.list(sid).map { it.uuid })   // re-read from disk

        assertEquals(listOf("u2"), store.update(sid, "u1", on = false, writtenAt = null).map { it.uuid })
        assertEquals(emptyList<BookmarkStore.Bookmark>(), store.update(sid, "u2", on = false, writtenAt = null))
        assertFalse(f.exists(), "an empty list deletes the file")
        assertFalse(File(dir, "$sid.json.tmp").exists())
    }

    @Test
    fun `adding twice keeps one entry, a bad id touches nothing`() {
        val store = BookmarkStore(dir)
        store.update("s1", "u1", on = true, writtenAt = null)
        assertEquals(listOf("u1"), store.update("s1", "u1", on = true, writtenAt = null).map { it.uuid })
        assertEquals(emptyList<BookmarkStore.Bookmark>(), store.update("../etc", "u1", on = true, writtenAt = null))
        assertEquals(listOf("u1"), store.update("s1", "no spaces here", on = true, writtenAt = null).map { it.uuid })
        assertEquals(listOf("s1.json"), dir.list()!!.toList())
    }

    @Test
    fun `an unreadable file reads as no bookmarks`() {
        File(dir, "s2.json").writeText("{not json")
        assertEquals(emptyList<BookmarkStore.Bookmark>(), BookmarkStore(dir).list("s2"))
    }
}

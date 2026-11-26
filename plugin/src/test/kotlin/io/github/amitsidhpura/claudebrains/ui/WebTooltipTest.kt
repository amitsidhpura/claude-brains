package io.github.amitsidhpura.claudebrains.ui

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.awt.event.InputEvent
import java.awt.event.MouseEvent
import javax.swing.JComponent
import javax.swing.JPanel

/** Backlog #4 — the webview's `title` text as a Swing tooltip. */
class WebTooltipTest {

    @Test
    fun `short text is escaped and left to size itself`() {
        assertEquals("<html>Export conversation</html>", WebTooltip.html("Export conversation"))
    }

    @Test
    fun `page text can never be tooltip markup`() {
        // a tool line's title mirrors what the model wrote
        assertEquals(
            "<html>&lt;html&gt;&lt;b&gt;x&lt;/b&gt; &amp; y</html>",
            WebTooltip.html("<html><b>x</b> & y"),
        )
    }

    @Test
    fun `line breaks are kept and a long line gets a width so it wraps`() {
        assertEquals("<html>one<br>two</html>", WebTooltip.html("one\r\ntwo"))
        val long = "x".repeat(WebTooltip.WRAP_AT + 1)
        assertEquals("<html><div style=\"width:320px\">$long</div></html>", WebTooltip.html(long))
        // exactly at the limit: not long
        assertEquals("<html>${"x".repeat(WebTooltip.WRAP_AT)}</html>", WebTooltip.html("x".repeat(WebTooltip.WRAP_AT)))
    }

    @Test
    fun `the reported text becomes the component's tooltip and an empty report clears it`() {
        val c = JPanel()
        val tip = WebTooltip(c)
        tip.set("Rename conversation")
        assertEquals("<html>Rename conversation</html>", c.getClientProperty(JComponent.TOOL_TIP_TEXT_KEY))
        assertEquals("<html>Rename conversation</html>", c.toolTipText)
        tip.set("")
        assertNull(c.toolTipText)
        tip.set("  ")
        assertNull(c.toolTipText)
        tip.set(null)
        assertNull(c.toolTipText)
    }

    private fun mouse(c: JPanel, id: Int, x: Int, mods: Int = 0) =
        c.dispatchEvent(MouseEvent(c, id, 0L, mods, x, 5, 1, false, MouseEvent.BUTTON1))

    @Test
    fun `a resting pointer is replayed so a late tooltip still shows`() {
        val c = JPanel()
        val tip = WebTooltip(c)
        mouse(c, MouseEvent.MOUSE_MOVED, 10)
        assertEquals(10, tip.replayable()?.x)
    }

    @Test
    fun `nothing is replayed while a button is held or after a press`() {
        // A replay is a move with NO button at the last plain-move spot. Dispatched during a drag it
        // told the page the button was up at the drag's start, ending a pinned-command drag (7.13) on
        // its first tooltip change — and the replay itself changed the tooltip again, 30 times a second.
        val c = JPanel()
        val tip = WebTooltip(c)
        mouse(c, MouseEvent.MOUSE_MOVED, 10)
        mouse(c, MouseEvent.MOUSE_PRESSED, 10, InputEvent.BUTTON1_DOWN_MASK)
        assertNull(tip.replayable(), "held after press")
        mouse(c, MouseEvent.MOUSE_DRAGGED, 200, InputEvent.BUTTON1_DOWN_MASK)
        assertNull(tip.replayable(), "held while dragging")
        mouse(c, MouseEvent.MOUSE_RELEASED, 200)
        assertNull(tip.replayable(), "the pre-press spot is stale after the release")
        mouse(c, MouseEvent.MOUSE_MOVED, 210)
        assertEquals(210, tip.replayable()?.x, "the next plain move is replayable again")
    }

    @Test
    fun `no component is a no-op`() {
        WebTooltip(null).set("anything")
    }
}

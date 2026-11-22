package io.github.amitsidhpura.claudebrains.ui

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
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

    @Test
    fun `no component is a no-op`() {
        WebTooltip(null).set("anything")
    }
}

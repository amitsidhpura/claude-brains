package io.github.amitsidhpura.claudebrains.ui

import com.intellij.openapi.util.text.StringUtil
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.JComponent

/**
 * Shows the webview's HTML `title` tooltips as ordinary IDE tooltips (backlog #4).
 *
 * CEF reports the text under the pointer through `CefDisplayHandler.onTooltip`; this keeps it as
 * the browser component's Swing tooltip text, which is all the IDE's tooltip manager needs: it
 * watches every mouse move, asks the component under the pointer for its tooltip, and does the
 * delay, the placement, the look and the hiding itself — the same tooltip every other control in
 * the IDE has.
 *
 * Two details:
 *  - The text arrives AFTER the mouse move that caused it (CEF answers asynchronously), so a
 *    pointer that stops dead on a control would get no tooltip until it moved again. When the
 *    text changes, the last real mouse move is dispatched once more so the manager looks again.
 *    The browser sees that repeat too and reports the same text, which changes nothing here.
 *  - The text is page content (a tool line's `title` mirrors what the model wrote), and a Swing
 *    tooltip renders "<html>…" as markup: it is always escaped. Long text is given a width so it
 *    wraps instead of running off the screen.
 *
 * All calls on the EDT. A null component (not a Swing component) makes this a no-op.
 */
internal class WebTooltip(private val comp: JComponent?) {
    private var text: String? = null
    private var last: MouseEvent? = null     // the last REAL mouse move over the browser
    private var nudge: MouseEvent? = null    // the repeat being dispatched right now, if any

    init {
        comp?.addMouseMotionListener(object : MouseAdapter() {
            override fun mouseMoved(e: MouseEvent) { if (e !== nudge) last = e }
        })
        comp?.addMouseListener(object : MouseAdapter() {
            override fun mouseExited(e: MouseEvent) { last = null }
        })
    }

    fun set(raw: String?) {
        val c = comp ?: return
        val t = raw?.takeIf { it.isNotBlank() }
        if (t == text) return
        text = t
        c.putClientProperty(JComponent.TOOL_TIP_TEXT_KEY, t?.let(::html))
        val e = last ?: return
        if (!c.isShowing) return
        val again = MouseEvent(c, MouseEvent.MOUSE_MOVED, System.currentTimeMillis(), 0, e.x, e.y, 0, false)
        nudge = again
        try { c.dispatchEvent(again) } finally { nudge = null }
    }

    companion object {
        /** Past this many characters a tooltip gets a fixed width and wraps. */
        const val WRAP_AT = 60
        const val WRAP_WIDTH_PX = 320

        /** Page text as tooltip markup: escaped, line breaks kept, long text wrapped. */
        fun html(text: String): String {
            val body = StringUtil.escapeXmlEntities(text).replace("\r\n", "\n").replace("\n", "<br>")
            val long = text.lineSequence().any { it.length > WRAP_AT }
            return if (long) "<html><div style=\"width:${WRAP_WIDTH_PX}px\">$body</div></html>" else "<html>$body</html>"
        }
    }
}

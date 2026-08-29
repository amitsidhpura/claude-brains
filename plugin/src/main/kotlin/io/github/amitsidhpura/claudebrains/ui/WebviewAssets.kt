package io.github.amitsidhpura.claudebrains.ui

import java.nio.charset.StandardCharsets

/**
 * The webview page, assembled from its split sources.
 *
 * `webview/chat.html` is markup only; the panel's JS lives under `webview/js/` and is spliced
 * back into the page's single `<script>` block at the `JS` marker — the same trick `loadUi` uses
 * for `chat.css` and `window.LIMITS`, and for the same reason: `loadHTML` has no base URL, so a
 * `<script src>` could never resolve. The splice is a pure concatenation in [JS_FILES] order into
 * ONE shared script scope — no modules, no per-file scope, load semantics identical to the years
 * the code lived inline (the 2026-08-19 split was verified byte-identical modulo banners).
 *
 * [JS_FILES] is the ONLY copy of the file order: `ChatPanel.loadUi` renders through [page], and
 * `RenderLimitsTest` asserts over [page] plus that the manifest matches the directory exactly —
 * a file missing from this list would otherwise ship dark and silently fall out of every test.
 *
 * The banner line before each file is what maps a DevTools/stack-trace line number in the loaded
 * page back to its source file — the page is one script, so trace lines are page lines.
 */
object WebviewAssets {

    /** Ordered manifest of the script sources. Order is load order — append with care. */
    val JS_FILES = listOf(
        "00-core.js",          // DOM handles, the LIMITS guard, shared inline-SVG icons
        "10-turn-working.js",  // scroll/pin, turn structure, working line
        "20-markdown.js",      // offline highlighter + safe markdown renderer
        "30-menus.js",         // JS->Kotlin bridge, dropdown system, mode/effort/model pickers
        "40-sessions.js",      // new / history / resume, rename
        "50-blocks.js",        // shared block builders (live + replay draw through these)
        "55-replay.js",        // transcript replay: replay* builders, renderTranscript, earlier-chunks
        "60-composer.js",      // attachments, queue, send/stop/retry, auto-grow
        "65-slash.js",         // slash menu + @-mentions, JCEF Delete-key workaround
        "67-side.js",          // side question panel (/btw): off-thread answers, never in the log
        "70-events.js",        // Kotlin -> page: the onClaudeEvent switchboard
        "75-retraction.js",    // uuid stamping + eviction, status/result lines
        "80-gauge.js",         // context gauge
        "85-cards.js",         // permission / plan cards, AskUserQuestion tabbed card
        "90-gallery.js",       // dev gallery (window.__gallery)
    )

    private fun resource(path: String): String =
        javaClass.getResourceAsStream(path)!!.use { it.readBytes() }.toString(StandardCharsets.UTF_8)

    /**
     * All script sources concatenated in manifest order, each behind a `file:` banner line.
     * The result must never contain a closing script tag — see RenderLimitsTest's balance test.
     */
    fun js(): String = JS_FILES.joinToString("") { name ->
        "/* ===== file: $name ===== */\n" + resource("/webview/js/$name")
    }

    /**
     * `chat.html` with the JS spliced in — the whole page as it existed before the split, banners
     * aside. The CSS / VERSION / LIMITS markers are left for `loadUi` (and are asserted on by
     * tests, which is why this stops here). Only the marker TEXT is replaced — its own line
     * ending stays put, so a CRLF checkout (the Windows box) still splices; `js()` supplies no
     * trailing newline for the same reason, keeping the page byte-exact on LF checkouts.
     */
    fun page(): String = resource("/webview/chat.html").replace("<!--JS-->", js().removeSuffix("\n"))
}

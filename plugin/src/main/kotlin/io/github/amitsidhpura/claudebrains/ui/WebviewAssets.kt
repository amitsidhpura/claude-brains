package io.github.amitsidhpura.claudebrains.ui

import java.nio.charset.StandardCharsets

/**
 * The webview page, assembled from its split sources.
 *
 * `webview/chat.html` is markup only; the panel's JS lives under `webview/js/` and is spliced
 * back into the page's single `<script>` block at the `JS` marker — the same trick `loadUi` uses
 * for the CSS (`webview/css/`, spliced by [css] at the `CSS` marker) and `window.LIMITS`, and for
 * the same reason: `loadHTML` has no base URL, so a `<script src>` or `<link>` could never
 * resolve. Both splices are pure concatenations in manifest order — the JS into ONE shared script
 * scope (no modules, no per-file scope; the 2026-08-19 split was verified byte-identical modulo
 * banners), the CSS into ONE `<style>` block where concatenation order IS cascade order (the
 * 2026-09-04 split was verified the same way — never reorder [CSS_FILES]).
 *
 * [JS_FILES] and [CSS_FILES] are the ONLY copies of the file order: `ChatPanel.loadUi` renders
 * through [page] and [css], and `RenderLimitsTest` asserts over [page] plus that each manifest
 * matches its directory exactly — a file missing from these lists would otherwise ship dark and
 * silently fall out of every test. `design/mockup.html` mirrors [CSS_FILES] as `<link>` tags
 * (it is a plain file in a browser, so links DO resolve there); the same test pins that copy.
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

    /** Ordered manifest of the style sources. Order is CASCADE order — never reorder. */
    val CSS_FILES = listOf(
        "00-tokens.css",    // file banner, :root tokens, base/reset, scrollbars, #app mode accents
        "10-header.css",    // header, rename editor
        "20-log.css",       // #log chrome, fades, scroll button, welcome screen
        "25-turns.css",     // turn structure, .msg-user, .fold, .tbl
        "30-blocks.css",    // gutter dots, .blk/.think/.generating/.done/.files/.tool-line, paths
        "35-attached.css",  // .io box, .t-prog/.t-note, .tool-imgs, cut markers, .codeblock
        "40-cards.css",     // permission/plan cards, plan comments, .diff, AskUserQuestion, .error, history panel
        "50-side.css",      // side question panel; attachment chips, lightbox, retry, todos, status, compaction
        "60-composer.css",  // composer, queued messages, bg-tasks chip, context gauge
        "70-popups.css",    // popups, slash/mention menus, model/effort pickers
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
     * All style sources concatenated in manifest order, each behind a `file:` banner line.
     * The result must never contain a closing style tag — same failure mode as the script
     * balance: a stray `</style>` truncates the spliced block and kills the whole page.
     */
    fun css(): String = CSS_FILES.joinToString("") { name ->
        "/* ===== file: $name ===== */\n" + resource("/webview/css/$name")
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

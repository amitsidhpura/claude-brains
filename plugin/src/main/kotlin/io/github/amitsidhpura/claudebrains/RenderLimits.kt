package io.github.amitsidhpura.claudebrains

/**
 * Caps and key orders that the LIVE renderer (chat.html) and the REPLAY parser (SessionStore) MUST
 * agree on: the same tool call has to render identically whether it is streaming now or replayed
 * from the transcript later. These used to be the same numbers written twice in two languages with
 * nothing tying them together, so editing one side silently changed only half the app.
 *
 * Kotlin owns them and ChatPanel splices them into the webview at `<!--LIMITS-->` (as `window.LIMITS`),
 * which is the same reasoning as the spliced plugin version: one copy, no drift possible.
 * Changing a value here changes both paths at once — that is the point.
 */
object RenderLimits {

    /** Tool description shown on a tool line, in characters. */
    const val DESC_MAX = 140

    /**
     * Bash command, in characters — the SAME cap wherever a command is shown or kept: the
     * permission card's preview, the IN box that records what ran, and replay. The card used to
     * preview 4000 while only 2000 was stored, so the record of what ran was shorter than what was
     * approved; whatever you were shown is now what gets kept.
     */
    const val CMD_MAX = 4000

    /** Bash output (OUT), in characters. Output is the bulky half, so it stays tighter. */
    const val OUT_MAX = 2000

    /**
     * Keys tried IN ORDER for a tool line's description; the first non-blank one wins.
     * `query`/`url` cover ToolSearch, WebSearch and WebFetch, which carry no description.
     * `element`/`filename`/`target` are MCP (Playwright): `element` is the schema's own
     * human-readable element description so it reads best, while `target` is the machine ref
     * ("exact target element reference from the page snapshot") and is the last resort.
     *
     * Tools whose input has none of these keys render a blank tool line — see the known gaps in
     * CLAUDE.md (TaskUpdate, Skill, TaskOutput/TaskStop each need a bespoke key).
     */
    val DESC_KEYS = listOf(
        "description", "file_path", "path", "pattern", "query", "url", "element", "filename", "target",
    )

    /** Of [DESC_KEYS], the ones whose value is a file path — rendered clickable (`.t-desc.path`). */
    val PATH_KEYS = setOf("file_path", "path")

    /**
     * What a cap threw away, so the renderer can say so instead of presenting a slice as the whole
     * thing. `null` from [cut] means nothing was dropped and no marker is drawn.
     *
     * [lines] counts WHOLE dropped lines only. The first fragment after the cut is the tail of the
     * last line still on screen, not a line of its own, so it is deliberately not counted — a cut
     * landing mid-line would otherwise always over-report by one. It is 0 for output with no
     * newlines at all (minified JSON, a single long line), which is why the renderer falls back to
     * size alone rather than printing "+0 lines".
     */
    data class Cut(val shown: String, val bytes: Int, val lines: Int)

    /**
     * THE cut rule. The live renderer runs its own copy in JS (`cutInfo` in chat.html) because the
     * two paths truncate in different languages, so the algorithm — not just the caps — has to be
     * stated once and mirrored exactly, or the same output reports a different amount dropped
     * while streaming than it does after a resume. `RenderLimitsTest` pins the cases.
     *
     * [bytes] is a count of UTF-16 code units, which is what `String.length` means in Kotlin AND in
     * JS — the two agree by construction, including on emoji and other astral characters.
     */
    fun cut(text: String, max: Int): Cut? {
        if (text.length <= max) return null
        val rest = text.substring(max)
        // A single trailing newline ends the last dropped line; it does not begin another one.
        val body = rest.removeSuffix("\n")
        return Cut(text.substring(0, max), rest.length, body.count { it == '\n' })
    }

    /** The same values as a JS object literal, for the webview splice. */
    fun asJs(): String {
        fun arr(v: Collection<String>) = v.joinToString(",", "[", "]") { "\"$it\"" }
        return "{descMax:$DESC_MAX,cmdMax:$CMD_MAX,outMax:$OUT_MAX," +
            "descKeys:${arr(DESC_KEYS)},pathKeys:${arr(PATH_KEYS)}}"
    }
}

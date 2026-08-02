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

    /** Bash command (IN) and Bash output (OUT), in characters. */
    const val BASH_MAX = 2000

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

    /** The same values as a JS object literal, for the webview splice. */
    fun asJs(): String {
        fun arr(v: Collection<String>) = v.joinToString(",", "[", "]") { "\"$it\"" }
        return "{descMax:$DESC_MAX,bashMax:$BASH_MAX," +
            "descKeys:${arr(DESC_KEYS)},pathKeys:${arr(PATH_KEYS)}}"
    }
}

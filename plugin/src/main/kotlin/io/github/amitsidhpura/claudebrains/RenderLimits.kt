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
     * The tail of the chain closes the blank-tool-line gap, measured across local transcripts
     * 2026-08-05. Every one of these keys is owned by exactly ONE tool — checked for collisions
     * before adding, because the chain is global and a generic key would hijack other tools' lines:
     *   `skill` Skill · `status`+`taskId` TaskUpdate · `task_id` TaskStop ·
     *   `function` mcp__playwright__browser_evaluate · `uri` mcp__ide__getDiagnostics
     * `status` precedes `taskId` deliberately: "in_progress" says what happened, an opaque id does
     * not. Together they account for the 74 tool lines that rendered blank and shouldn't have.
     *
     * Tools that are STILL blank are blank BY DESIGN, not by omission: Bash (458 — the command is
     * in the IN box), AskUserQuestion (49 — the card is the content), ExitPlanMode (11 — the plan
     * card), TodoWrite (11 — needs the real checklist renderer, client-parity item 14). `todos` and
     * `plan` are deliberately NOT in the chain: both are structures, and stringifying one into a
     * 140-char description would be worse than the blank it replaces.
     */
    val DESC_KEYS = listOf(
        "description", "file_path", "path", "pattern", "query", "url", "element", "filename", "target",
        "skill", "status", "taskId", "task_id", "function", "uri",
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

    /**
     * The CLI's own truncation, which happens BEFORE ours: an oversized Bash result is replaced
     * wholesale by a wrapper naming the file it was spilled to.
     *
     *     <persisted-output>
     *     Output too large (402.7KB). Full output saved to: /…/tool-results/byb19giot.txt
     *
     *     Preview (first 2KB):
     *     …preview…
     *     ...
     *     </persisted-output>
     *
     * Replay gets the same facts as real fields (`toolUseResult.persistedOutputSize` / `Path`), but
     * the LIVE stream event carries no `toolUseResult` at all (probed in runIde 2026-08-05), so
     * parsing this wrapper is the only way live can match replay. It also stops the raw tag being
     * rendered to the user — it is an injected wrapper of the family `cleanInjected()` strips.
     */
    data class Spill(val preview: String, val bytes: Long?, val path: String?)

    private val SPILL_PATH = Regex("""saved to:[ \t]*(\S.*?)[ \t]*(?:\r?\n|$)""")
    private val SPILL_SIZE = Regex("""\(([\d.]+)\s*([KMGT]?)B\)""", RegexOption.IGNORE_CASE)
    private val SPILL_PREVIEW_HEAD = Regex("""(?m)^Preview\b[^\n]*:[ \t]*\r?\n""")

    /**
     * Parse the wrapper above, or `null` when [text] is ordinary output.
     *
     * Both tags are REQUIRED, at the very start and very end. A Bash result that merely mentions
     * `<persisted-output>` — grepping for it, or dumping a transcript, which is exactly what one
     * local record does — must not be mistaken for the real thing and have its body eaten.
     */
    fun persistedOutput(text: String): Spill? {
        val t = text.trim()
        if (!t.startsWith("<persisted-output>") || !t.endsWith("</persisted-output>")) return null
        val body = t.removePrefix("<persisted-output>").removeSuffix("</persisted-output>")
        val path = SPILL_PATH.find(body)?.groupValues?.get(1)?.takeIf { it.isNotBlank() }
        val size = SPILL_SIZE.find(body)?.let { m ->
            val n = m.groupValues[1].toDoubleOrNull() ?: return@let null
            // Binary units: 412387 bytes is reported as "402.7KB" (412387 / 1024), not 412.4KB.
            val mult = when (m.groupValues[2].uppercase()) {
                "K" -> 1L shl 10; "M" -> 1L shl 20; "G" -> 1L shl 30; "T" -> 1L shl 40
                else -> 1L
            }
            // ROUND, not truncate: JS's Math.round is the mirror, and 402.7 * 1024 = 412364.8 lands
            // either side of the boundary depending on which you pick. Same rendered size either
            // way, but the two languages must produce the same NUMBER or this drifts by definition.
            Math.round(n * mult)
        }
        // Everything after the "Preview (first 2KB):" line is real output; the CLI's own trailing
        // "..." elision goes, because our marker is the canonical statement of what is missing.
        val head = SPILL_PREVIEW_HEAD.find(body)
        val preview = (if (head != null) body.substring(head.range.last + 1) else body)
            .trim().removeSuffix("...").trimEnd()
        return Spill(preview, size, path)
    }

    /** The same values as a JS object literal, for the webview splice. */
    fun asJs(): String {
        fun arr(v: Collection<String>) = v.joinToString(",", "[", "]") { "\"$it\"" }
        return "{descMax:$DESC_MAX,cmdMax:$CMD_MAX,outMax:$OUT_MAX," +
            "descKeys:${arr(DESC_KEYS)},pathKeys:${arr(PATH_KEYS)}}"
    }
}

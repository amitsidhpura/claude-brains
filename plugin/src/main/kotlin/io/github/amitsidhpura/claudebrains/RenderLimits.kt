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
     * card), TodoWrite (11 — the checklist below it IS the content, item 14). `todos` and `plan` are
     * deliberately NOT in the chain: both are structures, and stringifying one into a 140-char
     * description would be worse than the blank it replaces.
     */
    val DESC_KEYS = listOf(
        "description", "file_path", "path", "pattern", "query", "url", "element", "filename", "target",
        "skill", "status", "taskId", "task_id", "function", "uri",
    )

    /** Of [DESC_KEYS], the ones whose value is a file path — rendered clickable (`.t-desc.path`). */
    val PATH_KEYS = setOf("file_path", "path")

    /**
     * Keys tried IN ORDER for the IN box: what the tool was ASKED, as distinct from the one-line
     * description above it. First non-blank wins, exactly like [DESC_KEYS].
     *
     * `command` is Bash's, and was the only one for a long time. `prompt` joins it for client-parity
     * item 3 — a sub-agent's brief (`Agent`) and `WebFetch`'s extraction instruction. The
     * description only *summarises* those: measured across local transcripts 2026-08-06, an `Agent`
     * description is a handful of words while its prompt runs to 2121 characters, and none of that
     * reached the screen. "What did it just go off and do?" was unanswerable from the panel.
     *
     * Deliberately a KEY list and not a tool list, for the same reason [RESULT_SKIP] is a structural
     * rule: a future tool that takes a prompt gets an IN box without anyone remembering to add it.
     * Every key here must be one whose value is the instruction ITSELF — not a reference to it, and
     * not a structure. Capped by [CMD_MAX], the same box and the same cut marker as the command.
     */
    val IN_KEYS = listOf("command", "prompt")

    /**
     * Tools whose RESULT text is not worth an OUT box, because the panel already shows the outcome
     * another way. Measured across local transcripts 2026-08-05: rendering every non-Bash result
     * would have added ~2100 boxes, and 2033 of those said things like "The file … has been updated
     * successfully" directly beneath the diff that had just shown the change.
     *
     * The rule is structural, not a taste list: **if a tool renders its own card, diff or
     * description, its result text is a restatement.**
     *   Edit/Write/NotebookEdit → the diff card · ExitPlanMode → the plan card ·
     *   AskUserQuestion → the answered card · TaskCreate/TaskUpdate → the description
     *   ("in_progress" already says it) · TodoWrite → boilerplate telling the MODEL to keep using
     *   the list; its real fix is the checklist renderer, client-parity item 14.
     *
     * An ERROR always shows, whatever the tool: a failure is never a restatement of success.
     */
    val RESULT_SKIP = setOf(
        "Edit", "Write", "MultiEdit", "NotebookEdit",
        "ExitPlanMode", "AskUserQuestion", "TaskCreate", "TaskUpdate", "TodoWrite",
        // TaskList earned its place the moment the checklist shipped (item 14b): its result is the
        // same list as plain text, folded to three of five lines, directly above the checklist that
        // renders all of it with glyphs and strikethrough. Same rule as the rest — the panel shows
        // the outcome another way, so the raw text is a restatement.
        "TaskList",
    )

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

    /**
     * What a tool line's description leaves out, when leaving it out changes the meaning. Today
     * that is exactly one case: a `Read` of lines 40-80 rendered identically to a Read of the whole
     * file, which is the difference between "Claude read this" and "Claude read a slice of this".
     * 347 of 719 local Reads carry a range.
     *
     * Deliberately NOT a per-tool matrix, which is what the audit assumed. Measured 2026-08-05:
     * `Grep`/`Glob` — the item's headline examples — do not occur in local transcripts at all;
     * Bash's `command` and Edit's `old_string`/`new_string` are already shown in the IN box and the
     * diff; and `prompt` on Agent/WebFetch is client-parity item 3, scored on its own.
     *
     * Mirrored by `descSuffix` in chat.html — same two-language contract as [cut], pinned by tests.
     */
    fun descSuffix(tool: String, offset: Long?, limit: Long?): String {
        if (tool != "Read") return ""
        return when {
            // both: an exact window. -1 because `limit` counts lines, so 40+80 ends at 119, not 120.
            offset != null && limit != null -> " (lines $offset-${offset + limit - 1})"
            offset != null -> " (from line $offset)"
            limit != null -> " (first $limit lines)"
            else -> ""
        }
    }

    /**
     * A server-side web search's results, formatted for the OUT box (client-parity item 12).
     *
     * The wire shape is stated in only one place — the CLI's own reader — and it is unusual enough
     * to be worth quoting, because the discriminator is a TYPE CHECK and not a field:
     *
     *     if (a.type === "web_search_tool_result") {
     *       if (!Array.isArray(a.content)) { `Web search error: ${a.content.error_code}` ; continue }
     *       let l = a.content.map(c => ({title: c.title, url: c.url}))
     *     }
     *
     * So `content` is EITHER an array of results OR an error object. Reading it the other way round
     * prints `[object Object]` into the transcript, which is why both paths go through here.
     *
     * Mirrored by `searchResultText` in chat.html — the same two-language contract [cut] and
     * [descSuffix] carry, and pinned by `RenderLimitsTest` for the same reason: the caps being equal
     * does not help if the two sides format the body differently, because then one OUT box says
     * something the other does not and a resume silently rewrites history.
     *
     * @param results title-to-url pairs, or `null` when the search itself failed.
     */
    data class SearchOut(val text: String, val isError: Boolean)

    fun searchResults(results: List<Pair<String, String>>?, errorCode: String?): SearchOut {
        if (results == null) return SearchOut("Web search error: ${errorCode ?: "unknown"}", true)
        if (results.isEmpty()) return SearchOut("No results.", false)
        // Title over url, blank line between, so a long list stays scannable inside the fold.
        val body = results.joinToString("\n\n") { (title, url) ->
            val t = title.ifBlank { url.ifBlank { "untitled" } }
            if (url.isNotBlank() && url != t) "$t\n$url" else t
        }
        val head = if (results.size == 1) "1 result" else "${results.size} results"
        return SearchOut("$head\n\n$body", false)
    }

    /**
     * A caveat the CLI appends to an otherwise-successful tool result (client-parity item 11).
     * Real example, from a live Edit whose file changed on disk between the read and the write:
     *
     *     The file /…/timestamp.txt has been updated successfully. (note: the file had been
     *     modified on disk since you last read it — the edit applied cleanly, but the file contains
     *     other changes not in your context. Read it before edits that depend on surrounding
     *     content.)
     *
     * This matters because [RESULT_SKIP] drops Edit/Write result text wholesale — correctly, since
     * "has been updated successfully" restates the diff above it. But the parenthetical is NOT a
     * restatement: nothing else on screen says the file moved under us, and the card still reads
     * `✓ Applied`. So the skip rule gains a companion, in the same structural spirit as "an error is
     * never skipped": **a caveat is never skipped either.**
     *
     * Extracted from the TEXT rather than from `toolUseResult.staleRecovered`, deliberately: the
     * flag exists only on the replayed record (the live stream carries no `toolUseResult` at all,
     * probed 2026-08-05), whereas this sentence is in the result content on BOTH paths. One rule,
     * both renderers, no divergence.
     *
     * Anchored to the END so a `(note: …)` occurring inside ordinary output — a compiler message, a
     * quoted log line — cannot be mistaken for the CLI's own caveat.
     */
    private val RESULT_NOTE = Regex("""\(note:\s*(.+?)\)\s*$""", RegexOption.DOT_MATCHES_ALL)

    fun resultNote(text: String): String? =
        RESULT_NOTE.find(text.trim())?.groupValues?.get(1)
            ?.replace(Regex("""\s+"""), " ")?.trim()?.takeIf { it.isNotEmpty() }

    /** The same values as a JS object literal, for the webview splice. */
    fun asJs(): String {
        fun arr(v: Collection<String>) = v.joinToString(",", "[", "]") { "\"$it\"" }
        return "{descMax:$DESC_MAX,cmdMax:$CMD_MAX,outMax:$OUT_MAX," +
            "descKeys:${arr(DESC_KEYS)},pathKeys:${arr(PATH_KEYS)},resultSkip:${arr(RESULT_SKIP)}," +
            "inKeys:${arr(IN_KEYS)}}"
    }
}

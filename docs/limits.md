# Size limits and caps

Every place the UI or the parser bounds something, and by how much. Written 2026-07-29.

Three different kinds of bound, which matter differently:

- **Folded** — collapsed to 2 lines + a fading 3rd; the whole block is the click-toggle. Nothing is lost.
- **Scrolled** — height is capped, content is intact and reachable.
- **Truncated** — data is actually dropped and cannot be recovered without reopening the source.

---

## Folded (2 lines + fade, whole block toggles — 2026-07-31, replaced clamp-tab + IN/OUT/diff scroll)

Long blocks fold rather than getting their own vertical scrollbar — a nested scroll area inside
`#log` traps the mouse wheel. Collapsed shows 2 full lines with the 3rd fading out; content of
≤3 lines gets no fold and no affordance. Clicking anywhere toggles both ways; a click that ends a
text selection never toggles, and clicks on links/chips pass through (`foldBlock`, chat.html).

| block | fade colour | `--fold-bpad` | foldBlock called from |
|---|---|---|---|
| user message `.msg-user` | `--user-bg` | 9px (8 pad + 1 border) | `addUserMessage` |
| permission card command `.card .cmd` | `--code-bg` | 9px | `renderPermission` |
| diffs `.diff` (live + replay cards) | `--code-bg` | 9px | `renderPermission` / `replayCard` |
| Bash IN/OUT `.io-v` | `--code-bg` | 0 (padding lives on `.io-row`) | `ioRow` (covers live IN, patched OUT, replay) |
| code blocks `.codeblock pre` | `--code-bg` | 8px (border lives on `.codeblock`) | `foldCode` |

The 3-line cap is exact per block (`3lh` + the block's own padding/border via `--fold-pad`), so it
holds across differing fonts and line-heights. An attachment-chip row inside a user message is
overhead, not content — `foldBlock` measures it into `--fold-extra` so the cap still yields 3
TEXT lines. Wide content (`.card .cmd`, `.diff`, `.codeblock pre`, `.io-v` — all
`white-space: pre`) scrolls horizontally when expanded; while collapsed it just crops.

That crop-while-collapsed rule was silently FALSE for `.card .cmd` until 2026-08-02. Its own
`overflow-x: auto` ties the collapsed rule's `overflow: hidden` on specificity (both 0,2,0) and is
declared later, so it won — and a horizontal scrollbar inside a `max-height`-capped box steals its
height from the CONTENT, clipping the 3rd line to about a third of itself (measured: `clientHeight`
48px against the 58px the other surfaces get, a 10px scrollbar headless / ~15px in the IDE). The
fade then rides over a stub of a line, which reads as a much harsher cut than the same command in
a Bash IN box. Fixed with `.card .cmd.fold:not(.open) { overflow: hidden }` (0,3,0). Every other
folded surface is a weaker selector (`.diff` / `.io-v` 0,1,0 · `.codeblock pre` 0,1,1) and was
never affected. Any NEW folded surface declared with two-or-more classes and its own `overflow-x`
will hit exactly this — `#wide` in `design/fold-fade-probe.html` flags it in one run.

**Fade geometry (inconsistency spotted 2026-08-01, FIXED 2026-08-02).** The fade is anchored to the
TEXT, not to the border box: `height: calc(1lh + var(--fold-bpad, 0px))` with the colour stop at
`calc(100% - var(--fold-bpad, 0px))`, where `--fold-bpad` is that surface's own bottom
padding+border (column above). The ramp therefore spans exactly the 3rd text line everywhere and
whatever padding sits below it stays flat.

The old formula anchored to the box (`height: 1lh + --fold-pad/2`, opaque at 75%), which only
worked for surfaces that pad themselves. `.io-v` pads via its parent `.io-row`, so the same
formula compressed into exactly `1lh` and hit full opacity MID-LINE — the same command faded
gently in a card `.cmd` and got cut off in a Bash IN box. Measured span of the
transparent→opaque ramp, in text lines (`design/fold-fade-probe.html`, five surfaces, identical
4-line text; `#old` re-applies the old rule, `#open` renders unfolded as the alpha reference):

| surface | old | new |
|---|---|---|
| `.msg-user` | 1.12 | 1.00 |
| `.card .cmd` | 1.24 | 1.00 |
| `.diff` | 1.24 | 1.00 |
| `.codeblock pre` | 1.18 | 1.00 |
| `.io-v` | **0.75** | 1.00 |

Fade colour stays per-surface — a user message must blend to its own bubble, not `--code-bg`.
`--fold-pad` is untouched; it feeds the 3-line `max-height` cap, which was always correct.
`calc()` px-from-end stops and `lh` units are fine on JCEF's Chromium 122.

## Scrolled (height capped, content intact)

| block | limit |
|---|---|
| history list `.hist-list` | 5 rows, MEASURED at render (`capToRows`); 271px CSS fallback |
| slash-command menu `#slashList` | 5 rows, MEASURED at render (`capToRows`); 274px CSS fallback |
| composer textarea `#input` | 200px (~10 lines) |

Both lists cap at 5 rows through one helper, `capToRows(list, n, sel)` in chat.html. A row is NOT a
whole number of pixels (54.19px for history) and its height follows the IDE's font, so any
hardcoded "N × row" cap eventually lands a fraction under the real height and shows a scrollbar
for a list that visibly fits — that is what happened to the history list at exactly 5 items (270px
cap vs 270.9px of rows). The CSS values survive only as the pre-measure fallback.

The helper measures the BOTTOM OF ROW N, not one row's height × n: `#slashList` rows differ (a
command with no description is shorter), so multiplying is wrong there. Four rules, each of which
cost a debugging round:

- Measure only while the list is VISIBLE. A hidden panel measures 0 and pins `max-height: 0px`,
  which silently empties the list. `renderSlash` is called BEFORE `.show` on the typing path, so
  the cap is applied after `.show` at both call sites rather than inside `renderSlash`.
- Reset with `max-height: none`, not `''`. Clearing the inline style leaves the CSS fallback in
  force, so the measurement comes back already clamped and the cap re-applies the stale number.
- n rows or fewer get NO row cap — falling back to the CSS value reintroduces the original bug the
  moment the IDE font makes rows taller (verified: at a larger font 5 slash rows are 321px against
  the 274px fallback, 5 history rows 372px against 271px).
- Clamp to the webview either way. The history panel grows DOWN and the composer popups grow UP, so
  the guard trims by `max(0, -top, bottom - innerHeight)`; in a short tool window the list scrolls
  instead of putting rows off-screen where nothing can reach them.

Verified headless against the real chat.html (CSS spliced in as Kotlin does it) at 1–7 rows, both
open paths, normal and enlarged fonts, and a 380px-tall window. `CMD_ALLOWED` is a `const Set` but
Sets mutate, so the probe widens the allowlist without editing the code under test — today only
`/compact` and `/clear` pass `cmdKind`, so the slash menu never reaches 5 rows in production and
this cap is future-proofing rather than a live fix.

## Truncated (data is dropped)

| what | limit | where | says so? |
|---|---|---|---|
| diff rows | 400 | `MAX_DIFF_ROWS`, live + replay | ✅ "… diff truncated" |
| Bash command | 4000 chars | `RenderLimits.CMD_MAX` — card preview, IN box and replay, one number | ✅ `.io-cut` |
| Bash output | 2000 chars | `RenderLimits.OUT_MAX` (live + replay) | ✅ `.io-cut` |
| card command preview | 4000 chars | `RenderLimits.CMD_MAX` — same cap as the IN box, by construction | ✅ `.cmd-cut` |
| tool description | 140 chars | `RenderLimits.DESC_MAX` (live + replay) | ❌ one line, accepted |
| session title fallback | 80 chars | `SessionStore.titleOf` | ❌ a title, not content |

### Every cap that drops CONTENT announces it (2026-08-05)

Closed `docs/client-parity.md` item 10. A cut used to be indistinguishable from a short result: the
panel showed a slice and nothing said so, which made the fold actively misleading — expanding a
truncated OUT box yields the 2000-char slice, not the whole output, so the fold read as "you have
now seen everything". Measured scale before the fix: **79 blocks in a single local session** were
being cut with no indication.

- **The rule lives once**, in `RenderLimits.cut` — the doc comment there owns it, and `cutInfo` in
  chat.html is the mirror. Live truncates in JS and replay in Kotlin, so the ALGORITHM has to match,
  not just the cap. `RenderLimitsTest.cut counts whole dropped lines only` pins the cases; the
  mid-line and trailing-newline ones are what a naive count gets wrong.
- **Lines counted are WHOLE dropped lines.** The first fragment after the cut is the tail of a line
  still on screen, so it is not counted — otherwise a mid-line cut always over-reports by one.
  `lines == 0` (minified JSON, one long line) falls back to size alone rather than "+0 lines".
- **The marker is a SIBLING of `.io-v`, never a child.** `foldBlock` collapses `.io-v` to three
  lines, so a marker inside it would itself be folded away. Same reason `.cmd-cut` sits outside
  `<pre class="cmd">`. Verified headless: it has non-zero height while `.io-v` still carries `.fold`.
- **Fold ≠ marker.** The fold hides content it still holds ("click to expand"); the marker reports
  content that is gone ("+312 lines · 12.4 KB not shown"). It wears the UI font rather than the
  box's monospace so it does not read as one more line of output.
- **The CLI truncates before we do.** It caps `stdout` around 30 000 chars and spills the whole
  result to `toolUseResult.persistedOutputPath`, with the real size in `persistedOutputSize`. When
  present the marker reports that true total and offers to open the file. `outFile` is emitted
  **only when the path still resolves** — sessions outlive the tool-results dir, and a project
  rename orphans every path (of 4 such records locally, only 1 file survives). A click that opens
  nothing raises a balloon instead of doing nothing at all.

## Volume (how much is loaded at all)

| what | limit | note |
|---|---|---|
| replay blocks per session | 4000 | `readTranscript(max)`. A 5.7 MB session yields ~876, so rarely reached. |
| transcript images | 4 MB of base64 | `IMAGE_BUDGET`, spent newest-block-first so the visible tail keeps its bytes; past it, chips render name-only. Budgeted over the whole PARSE, not per frame — a frame carries only its own window's images (0.02 MB at 250 blocks, measured 2026-08-03). |
| history list | 40 sessions | `SessionStore.list(limit)` |
| title scan | first 400 lines | `titleOf` |
| @-mention file index | 3000 files | `ClaudeSessionService.listProjectFiles` |
| @-mention suggestions shown | 20 | |
| slash-command suggestions shown | 40 | |

---

## Known rough edges

**Duplicated constants — FIXED 2026-08-02.** The 2000-char Bash caps, the 140-char description cap
and the description key order used to exist twice: once in `chat.html` for the live path, once in
`SessionStore.kt` for replay, the same values in two languages with nothing tying them together.
They now have one home, `RenderLimits.kt`, which `ChatPanel.loadUi` splices into the webview as
`window.LIMITS` at the `LIMITS` marker — the same trick as the spliced plugin version, and for the
same reason. The webview reads `LIM.descMax` / `LIM.bashMax` / `LIM.descKeys` / `LIM.pathKeys` and
throws on load if the splice is missing, rather than falling back to defaults: a default WOULD BE
the second copy, and every use of these sits inside a `try/catch` that would swallow the mistake.

`RenderLimitsTest` keeps it that way — it fails the build if a literal is hardcoded back into the
JS, if the chain is rebuilt by hand, or if the marker disappears, and it replays the same six
description cases that were driven through the live handler in a browser so both paths are pinned
to identical results.

That parity check earned its keep immediately: Kotlin's `?:` KEPT a blank description while JS's
`||` skipped it, so a tool call carrying `description: "   "` plus a `file_path` showed the path
live and a blank line on resume. Both now skip blanks.

Two traps found while wiring the splice, both of which silently truncate the whole webview and
present as a blank tool window rather than an error:

- `String.replace` swaps EVERY occurrence, so the marker's literal text must appear exactly once —
  a second mention inside a JS comment gets a real `<script>` element spliced into it.
- A closing script tag anywhere inside the script block ends it as far as the HTML parser is
  concerned, *including inside a `//` comment*. Describing this hazard in a comment is enough to
  cause it. `RenderLimitsTest` asserts both invariants (marker appears once, script tags balance).

**Preview shows more than the transcript keeps — FIXED 2026-08-03.** The permission card previewed
4000 chars of a command while only 2000 were stored, so the record of what ran was shorter than
what was approved. Both now use `RenderLimits.CMD_MAX` (4000): whatever you were shown is what gets
kept, live and on replay. Bash OUTPUT keeps its own tighter cap (`OUT_MAX`, 2000) — it is the bulky
half and is not something you approved.

Fixed alongside it: the preview sliced the ESCAPED string, so a cut could land inside an entity
(`&amp;`) and the effective limit shrank with every special character in the command. It slices
first, then escapes.

**Token/size scans are cached, not free.** `SessionStore.tokensOf` scans the whole transcript to
sum `message.usage.output_tokens`, rejecting lines on a substring before parsing. Cached by
`(mtime, size)`, so a 177 MB session is scanned once (~0.5s) rather than on every panel open.
`lastActivityOf` reads only the trailing 256 KB.

---

# Big-session performance

Measured 2026-07-29 on this machine, headless Chrome (no compositor) + the real `SessionStore`.
Numbers to be re-taken in JCEF before trusting their magnitude; the *shape* should hold.

## Where the time actually goes

Parsing is not the bottleneck:

| session | file | blocks | `readTranscript` | frame shipped to the webview |
|---|---|---|---|---|
| claude-brains | 10 MB | 1,848 | 175 ms | 4.18 MB |
| syncroze-core | 177 MB | 4,000 (capped) | 91 ms | 4.29 MB |

What the frame is made of differs sharply by session, so there is no single fix:

- **brains**: 57% of the 4.18 MB is base64 image payload — 2.38 MB from just 46 blocks.
- **core**: 9% images; the bulk is tool blocks (2.26 MB) and thinking (0.69 MB).

DOM construction is trivial; **layout is the cost**, and it scales with the whole document
rather than the visible part:

| turns | build | layout + scroll |
|---|---|---|
| 400 | 2 ms | 49 ms |
| 2000 | 8 ms | 132 ms |
| 2000 + `content-visibility: auto` | 9 ms | 4 ms |

## content-visibility: the flex trap

`content-visibility: auto` skips layout/paint for off-screen subtrees. Off-screen elements then
have no measured height, so `contain-intrinsic-size` supplies a placeholder — and the scrollbar is
only as honest as that estimate.

**`contain-intrinsic-size` is ignored while the element is a flex item.** `#log` is
`display: flex; flex-direction: column; gap: 18px`, so skipped `.turn`s collapse to zero and only
the 18px gaps survive:

| container | intrinsic size | document height | error |
|---|---|---|---|
| flex (current) | — | 213,100 | ground truth |
| flex | `0 250px` | 18,116 | **−91%, estimate ignored** |
| block | none | 18,134 | −91% |
| block | `0 213px` (true average) | 231,134 | +8% |
| block | `0 250px` | 268,134 | +26% |
| block | `0 400px` | 418,134 | +96% |

REFINEMENT (2026-07-29, later): the restriction applies to the element that IS the flex item.
Once containment moved to `.turn-body` (a block inside `.turn`, done for the stacking-context fix),
`#log` could return to `display: flex; gap: 18px` with estimates fully respected — measured
identical to block in height honesty and layout time. Flex `gap` is also strictly better here than
the interim `#log > *` margin rules: a `display:none` #welcome generates no flex item (so no
phantom first-turn gap), and there are no high-specificity `#log > *` rules to defeat #welcome's
own margins — both of which were real regressions during the block detour.

Turn heights here vary from a one-line "ok" to a 400-row diff, so no single average is right in
both directions — an argument for `contain-intrinsic-size: auto <fallback>`, which lets the browser
remember real sizes once a turn has been rendered. `auto` measured identically to a fixed value in
the benchmark, as expected: a one-shot synthetic run never re-visits a turn.

## Ranked options

1. **DONE 2026-07-29** — `content-visibility: auto` on `.turn-body`. `#log` became `display: block`
   with `#log > * + * { margin-top: 18px }` replacing `gap`, `#welcome` centres itself
   (`min-height: 100%` + `justify-content: center`) rather than depending on a flex parent. Verified after the change: turn spacing still 18px, welcome
   still centred, `.msg-user` still pins (held at the container top with its turn scrolled 40px
   past), layout ~0ms for 1000 turns.
   Two regressions came out of the first attempt (content-visibility on `.turn` itself) and shaped
   the final shape: (a) containment makes the element a STACKING CONTEXT, so `.msg-user`'s `z-index: 6`
   was scoped inside the turn and `#fade-top` (z 5) painted over the pinned message — hence
   `.turn-body`, with the user message left outside it; (b) `#log > * + *` gave the FIRST turn a top
   margin, because a `display:none` `#welcome` still counts as a sibling for `+` — now
   `margin-bottom` with a `:last-child` reset, since a display:none element generates no box;
   (c) the welcome screen stopped centring — `#log > *:last-child { margin-bottom: 0 }` is (1,0,1)
   and beat `#welcome { margin: auto }` at (1,0,0), zeroing the auto bottom margin so only
   `margin-top: auto` survived and pinned it to the BOTTOM, while on first load the class that
   restored flex was never in the markup at all, leaving it at the TOP. Fixed by making `#welcome`
   centre itself, which removed the class plumbing from chat.html and the mockup entirely.
   The placeholder was `250px` (from SYNTHETIC turns, ~193px) until it was MEASURED on 2026-08-03
   and raised to `1000px`. Real turns are several times taller: 298 turns from two sessions,
   rendered with containment disabled so heights are honest, at three panel widths —

   | session | turns | median | mean | p75 | p90 | max |
   |---|---|---|---|---|---|---|
   | 42d09b97 (15 MB) @560 | 285 | 1006 | 1457 | 1709 | 2882 | 13949 |
   | 42d09b97 @760 | 285 | 873 | 1324 | 1584 | 2762 | 13718 |
   | 7d30ad11 (1.2 MB) @560 | 13 | 1208 | 2320 | 3972 | 3989 | 9717 |

   so `250px` under-reported the scrollbar by roughly 4x. The median is the right statistic here
   rather than the mean, which a single 14000px turn drags upward. Reproduce with
   `./gradlew probe --args="<project> <session> --json"` piped into a headless render of chat.html
   (see the note on embedding a transcript in a `<script>` tag, below). The decay half — `auto`
   remembering a turn's real size once rendered — cannot be measured headless, which never marks a
   turn rendered, so it was checked in the sandbox instead (2026-08-03, `runIde`, the 15 MB session,
   scrolled bottom→top→bottom): thumb stable rather than resizing pass to pass, no stutter as turns
   came into view, earlier chunks streaming in without a jump, and the return pass steadier than the
   first — which is decay working. Nothing about the estimate is open now.
   FOLLOW-UP 2026-07-29: windowed loading (below) shipped — initial frame is the newest ~250
   blocks cut at a turn boundary (`SessionStore.alignedStart`, tested), 89–95% smaller on real
   sessions (4.35→0.46 MB, 4.09→0.21 MB). Earlier chunks (~500 blocks) load automatically and
   silently as the user scrolls within 600px of the top — no visible affordance; chunks are
   near-instant since Kotlin holds the parsed list. Prepends are viewport-anchored (scrollTop shifted by exactly how far the previously
   topmost element moved). Known trade-off: DOM search only sees loaded blocks.
   (The IMAGE_BUDGET-in-file-order trade-off noted here originally is GONE — `SessionStore` now
   walks the budget newest-block-first, so the visible tail keeps its bytes.)
2. ~~Stop inlining base64 images in the transcript frame; load on lightbox open.~~
   **MOOT 2026-08-03 — measured.** Windowing removed the premise. At the shipped 250-block window
   the whole frame is 0.22 MB and base64 is 0.02 MB of it (8.9%); it is only at whole-session size
   that images dominate (2.77 MB of 5.48 MB, 50.6%), and that frame is never shipped.
3. ~~Chunk the transcript push.~~ **MOOT 2026-08-03 — measured.** The "~4 MB string in one hit"
   this was written against is now 0.22 MB (250 blocks) / 0.67 MB (750). Windowing solved it.
4. ~~Defer per-block markdown/highlight until visible.~~ **NOT WORTH IT 2026-08-03 — measured.**
   `renderTranscript` over the real 250-block window costs 40 ms of build and ~0 ms of layout,
   once, at open (750 blocks: 34 ms; the entire 3334-block session, which is never shipped:
   184 ms). The original suspect `clampBlock` no longer exists either — folding replaced clamping
   on 2026-07-31. Revisit only if the window size grows a lot.
5. **DONE 2026-07-29** — windowed rendering, described in the FOLLOW-UP under 1.

All three of the remaining options were closed by the same thing: windowed loading changed what
gets shipped, so optimisations sized against a 4 MB frame no longer have anything to save. Measure
before implementing any of them again — the numbers above are the baseline to re-take.

**Rendering a real transcript headless** (how the numbers above were taken): dump blocks with
`./gradlew probe --args="<project> <session> --json"`, embed them in a
`<script type="application/json">` block, and call `renderTranscript(items, 0)`. Two traps: the CSS
and the LIMITS marker must both be spliced exactly as `ChatPanel.loadUi` does it, or the page is
unstyled or throws; and transcripts CONTAIN literal `</script>` text, which ends the JSON block
early and silently renders garbage — escape `</` as `<\/`, which is a valid JSON escape. To measure
true turn heights, also disable containment (`.turn-body { content-visibility: visible }`),
otherwise off-screen turns report the placeholder instead of their real size.

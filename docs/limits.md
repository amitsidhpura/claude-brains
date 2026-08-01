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

| block | fade colour | foldBlock called from |
|---|---|---|
| user message `.msg-user` | `--user-bg` | `addUserMessage` |
| permission card command `.card .cmd` | `--code-bg` | `renderPermission` |
| diffs `.diff` (live + replay cards) | `--code-bg` | `renderPermission` / `replayCard` |
| Bash IN/OUT `.io-v` | `--code-bg` | `ioRow` (covers live IN, patched OUT, replay) |
| code blocks `.codeblock pre` | `--code-bg` | `foldCode` |

The 3-line cap is exact per block (`3lh` + the block's own padding/border via `--fold-pad`), so it
holds across differing fonts and line-heights. An attachment-chip row inside a user message is
overhead, not content — `foldBlock` measures it into `--fold-extra` so the cap still yields 3
TEXT lines. Wide content (`.diff`, `.codeblock pre`, `.io-v` —
all `white-space: pre`) scrolls horizontally when expanded; while collapsed it just crops.

**Known inconsistency (spotted 2026-08-01, deliberately deferred):** the fade GEOMETRY differs per
surface even where the colour matches. The `::after` gradient is anchored to the element's bottom
(`height: 1lh + --fold-pad/2`, opaque at 75%), but surfaces disagree about where their bottom
padding lives: `.cmd`/`.diff`/`.msg-user`/`pre` carry padding INSIDE the folded element, so the
gradient's opaque end lands in the padding and the 3rd line fades gently; `.io-v`'s padding lives
on the parent `.io-row`, so the same formula compresses into exactly `1lh` and reaches full
opacity mid-line — a visibly harsher cut (same command in a card `.cmd` vs a Bash IN box fades
differently). Fix, when picked up: give each surface a `--fold-bpad` (real bottom padding+border:
msg-user 9px / cmd+diff 9px / pre 8px / io-v 0) and anchor the gradient to the TEXT —
`height: calc(1lh + var(--fold-bpad, 0px))` with the colour stop at
`calc(100% - var(--fold-bpad, 0px))` — so the transition spans exactly the last text line on every
surface. Fade colour stays per-surface (a user message must blend to its own bubble, not
`--code-bg`). `calc()` px-from-end stops and `lh` units are fine on JCEF's Chromium 122. Verify by
rendering all five surfaces with the same 4-line text and pixel-sampling the fade span per line.

## Scrolled (height capped, content intact)

| block | limit |
|---|---|
| history list `.hist-list` | 270px (5 × 54px rows) |
| slash-command menu `#slashList` | 274px (5 × 54px rows + 4px pad) |
| composer textarea `#input` | 200px (~10 lines) |

## Truncated (data is dropped)

| what | limit | where |
|---|---|---|
| diff rows | 400 | `MAX_DIFF_ROWS`, live + replay; appends "… diff truncated" |
| Bash command | 2000 chars | `chat.html` + `SessionStore.toolItem` |
| Bash output | 2000 chars | `chat.html` + `SessionStore.applyToolResult` |
| card command preview | 4000 chars | `previewHtml` |
| tool description | 140 chars | `chat.html` + `SessionStore.toolItem` |
| session title fallback | 80 chars | `SessionStore.titleOf` |

## Volume (how much is loaded at all)

| what | limit | note |
|---|---|---|
| replay blocks per session | 4000 | `readTranscript(max)`. A 5.7 MB session yields ~876, so rarely reached. |
| transcript images | 4 MB of base64 | `IMAGE_BUDGET`; past it, chips render name-only. The whole transcript ships as one `executeJavaScript` string. |
| history list | 40 sessions | `SessionStore.list(limit)` |
| title scan | first 400 lines | `titleOf` |
| @-mention file index | 3000 files | `ClaudeSessionService.listProjectFiles` |
| @-mention suggestions shown | 20 | |
| slash-command suggestions shown | 40 | |

---

## Known rough edges

**Duplicated constants.** The 2000-char Bash caps and the 140-char description cap exist twice —
once in `chat.html` for the live path and once in `SessionStore.kt` for replay. They are the same
number in two languages with nothing tying them together, so live and replayed renderings of the
same tool call can silently diverge if only one side is changed. The `description → file_path →
path → pattern → query → url` chain has the same problem and carries a comment on both sides.

**Preview shows more than the transcript keeps.** The permission card previews 4000 chars of a
command, but only 2000 are stored for the IN box — so after approving, the record of what ran is
shorter than what was approved.

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
| claude-code-phpstorm | 10 MB | 1,848 | 175 ms | 4.18 MB |
| syncroze-core | 177 MB | 4,000 (capped) | 91 ms | 4.29 MB |

What the frame is made of differs sharply by session, so there is no single fix:

- **phpstorm**: 57% of the 4.18 MB is base64 image payload — 2.38 MB from just 46 blocks.
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
   OPEN: the `250px` fallback in `contain-intrinsic-size: auto 300px` is a guess. Synthetic turns
   measured ~193px, so it still overestimates; real turns with cards/diffs run much taller.
   The `auto` keyword should decay that error as turns are visited and remembered, but headless
   never marks a turn rendered, so that half is UNVERIFIED — check the scrollbar in a sandbox and
   tune the fallback there.
   FOLLOW-UP 2026-07-29: windowed loading (below) shipped — initial frame is the newest ~250
   blocks cut at a turn boundary (`SessionStore.alignedStart`, tested), 89–95% smaller on real
   sessions (4.35→0.46 MB, 4.09→0.21 MB). Earlier chunks (~500 blocks) load automatically and
   silently as the user scrolls within 600px of the top — no visible affordance; chunks are
   near-instant since Kotlin holds the parsed list. Prepends are viewport-anchored (scrollTop shifted by exactly how far the previously
   topmost element moved). Known trade-offs: DOM search only sees loaded blocks, and IMAGE_BUDGET
   is still consumed in file order, so unshipped early blocks can starve visible tail images — flip
   the budget walk when it bites.
2. Stop inlining base64 images in the transcript frame; load on lightbox open. Halves image-heavy frames.
3. Chunk the transcript push — currently one ~4 MB string escaped into a JS literal, shipped through
   `executeJavaScript` and `JSON.parse`d in one hit.
4. Defer per-block markdown/highlight until visible. `clampBlock` is the suspect: one rAF per block,
   each reading `scrollHeight` after a DOM mutation — classic layout thrash at ~1,850 blocks. UNMEASURED.
5. Windowed rendering (newest N turns + "load earlier"). Probably unnecessary if 1–3 land.

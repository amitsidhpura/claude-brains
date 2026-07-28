# Size limits and caps

Every place the UI or the parser bounds something, and by how much. Written 2026-07-29.

Three different kinds of bound, which matter differently:

- **Clamped** — collapsed behind a "show more" tab; nothing is lost.
- **Scrolled** — height is capped, content is intact and reachable.
- **Truncated** — data is actually dropped and cannot be recovered without reopening the source.

---

## Clamped (collapse + tab, fully recoverable)

Long blocks collapse to a fade with a tab on the block's bottom border rather than getting their
own scrollbar — a nested scroll area inside `#log` traps the mouse wheel.

| block | limit | fade colour | set in |
|---|---|---|---|
| user message `.msg-user` | 12 lines | `--user-bg` | `chat.html` `addUserMessage` |
| permission card command `.card .cmd` | 14 lines | `--code-bg` | `chat.html` `renderPermission` |
| code blocks `.codeblock pre` | 16 lines | `#202226` | `chat.html` `clampCode` |

Line height is `1.5em`, so the pixel height is `lines × 1.5em`. `clampBlock` measures after a frame
and removes the affordance entirely when the content fits, so short blocks get no tab and no fade.

## Scrolled (height capped, content intact)

| block | limit |
|---|---|
| Bash IN/OUT `.io-v` | 200px |
| diffs `.diff` | 240px |
| history list `.hist-list` | 340px |
| slash-command menu `#slashList` | 320px |
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

So adopting it is not additive: `#log` must become `display: block` with margins on `.turn`
replacing `gap`. Layout drops 41 ms → 2 ms either way; the estimate only governs scrollbar honesty.

Turn heights here vary from a one-line "ok" to a 400-row diff, so no single average is right in
both directions — an argument for `contain-intrinsic-size: auto <fallback>`, which lets the browser
remember real sizes once a turn has been rendered. `auto` measured identically to a fixed value in
the benchmark, as expected: a one-shot synthetic run never re-visits a turn.

## Ranked options

1. **DONE 2026-07-29** — `content-visibility: auto` on `.turn-body`. `#log` became `display: block`
   with `#log > * + * { margin-top: 18px }` replacing `gap`, and flexes again only via `#log.empty`
   while the welcome screen is up (that is what `margin: auto` needs to centre it) — chat.html and
   the mockup both toggle that class. Verified after the change: turn spacing still 18px, welcome
   still centred, `.msg-user` still pins (held at the container top with its turn scrolled 40px
   past), layout ~0ms for 1000 turns.
   Two regressions came out of the first attempt (content-visibility on `.turn` itself) and shaped
   the final shape: (a) containment makes the element a STACKING CONTEXT, so `.msg-user`'s `z-index: 6`
   was scoped inside the turn and `#fade-top` (z 5) painted over the pinned message — hence
   `.turn-body`, with the user message left outside it; (b) `#log > * + *` gave the FIRST turn a top
   margin, because a `display:none` `#welcome` still counts as a sibling for `+` — now
   `margin-bottom` with a `:last-child` reset, since a display:none element generates no box.
   OPEN: the `250px` fallback in `contain-intrinsic-size: auto 300px` is a guess. Synthetic turns
   measured ~193px, so it still overestimates; real turns with cards/diffs run much taller.
   The `auto` keyword should decay that error as turns are visited and remembered, but headless
   never marks a turn rendered, so that half is UNVERIFIED — check the scrollbar in a sandbox and
   tune the fallback there.
2. Stop inlining base64 images in the transcript frame; load on lightbox open. Halves image-heavy frames.
3. Chunk the transcript push — currently one ~4 MB string escaped into a JS literal, shipped through
   `executeJavaScript` and `JSON.parse`d in one hit.
4. Defer per-block markdown/highlight until visible. `clampBlock` is the suspect: one rAF per block,
   each reading `scrollHeight` after a DOM mutation — classic layout thrash at ~1,850 blocks. UNMEASURED.
5. Windowed rendering (newest N turns + "load earlier"). Probably unnecessary if 1–3 land.

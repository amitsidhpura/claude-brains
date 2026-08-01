# Port plan: design/mockup.html → chat.html

The mockup is **finalized** (commits `1a12a3c`, `b3067d0`) and is the source of truth for
all styles and markup. Open it in a browser to see every component live; the devbar in the
top-right toggles menus/stop-state and sets preview widths.

**Does NOT ship:** the dev scaffolding — `#devbar`, `#frame`/`#grip` resizable preview,
localStorage width persistence, the demo log content, and the demo `setInterval`s driving
the spinner/verbs/token counters (the renderer drives those from real stream events).

## Phases

### Phase 1 — CSS + static chrome — **DONE 2026-07-28** (pending sandbox check)
Notes from the implementation:
- `webview/chat.css` created (canon from mockup + a marked LEGACY section for classes the
  current renderer still emits — delete it in Phase 2). Mockup `<link>`s the same file;
  `ChatPanel.loadUi()` splices it at `<!--CSS-->`.
- Effort: the CLI supports `/effort <level>` at runtime (verified: "Set effort level to high");
  levels low/medium/high/xhigh/max. The slider ships, wired to send `/effort` — the CLI's
  confirmation appears as a normal assistant reply.
- Header retry button replaced by the mockup's timeline `.retry` line (shown after errors).
- attachMenu ships with Upload-from-computer + Add-context (inserts `@`); "Browse the web"
  omitted until there's a backend for it.
- **Split the CSS into `webview/chat.css` (single source of truth).** `ChatPanel.loadUi()`
  reads chat.html as a string and calls `loadHTML()` — there is no base URL, so a plain
  `<link href="chat.css">` will NOT resolve inside JCEF. Instead splice at load time: read
  both resources and replace a `<!--CSS-->` marker in the HTML with `<style>…</style>`
  (3-line change in `loadUi()`). `design/mockup.html` then `<link>`s the same file relatively
  (`../plugin/src/main/resources/webview/chat.css`), which browsers resolve over
  `file://`. Do this as PART of Phase 1 — retrofitting later is much more work.
  After this, styling-only iterations = edit one file, no porting step.
- Copy the token block (`:root`) and all component CSS; drop the frame/devbar/scrollbar-preview sections.
- Composer: chips row (`#chips`), auto-grow textarea (`rows=1`, `LINE=20`, `MAXLINES=10`,
  empty state pinned to one line), button bar with divider, paperclip/slash icon buttons,
  model + mode chips in `.menu-anchor` wrappers, send button with `.ic-send`/`.ic-stop` and `.stop` state.
- Header: rotate-ccw-clock (history), message-circle-x (new conversation), title, bottom border.
- **During this phase:** check `docs/ide-mcp-protocol.md` / control protocol for a
  reasoning-effort setting. If none exists, the effort slider in the mode menu is cosmetic — drop it.

### Phase 2 — renderer changes — **DONE 2026-07-28** (pending sandbox verify)
Full rewrite of chat.html body+script to the canon markup; legacy CSS section deleted.
Implemented: `.turn` wrapping (curTurn), sticky `.msg-user` with in-message `.undo` (shown from
dry-run rewind, keyed by turnId→userEl), in-message attachment chips → `#lightbox`; canon cards
(Edit old→new diff, Write additions, Bash `.cmd`, plan `.card` with blk + step-forward button);
tabbed AskUserQuestion (`renderAsk`: header tabs, icon indicators, Other→text input,
auto-advance, submit-enable, Cancel/Esc); slash popup + legacy @-mention popup; live thinking
(`.think-live` shimmer + ~char/4 token estimate → `<details>` "Thought for Ns"); working line
(flower `FRAMES`, `VERBS`, elapsed + `message_delta.usage.output_tokens` meta); red fail dots
(`tool_result.is_error`); `.error` block + `.retry` line; effort slider → `/effort`.
Remaining polish → Phase 3.

Phase 2 leftovers / to verify in sandbox:
- Token meta relies on `message_delta.usage.output_tokens` — confirm the CLI emits it midstream.
- Undo button is keyed to the turn's user box; confirm placement + removal after revert.
- Ask "Other" free-text path + multiSelect submission shape.

### Phase 2 (original notes)
- Wrap each turn in `.turn`; `.msg-user` is sticky; undo button lives **inside** the message
  (`.has-undo` + `.undo`, shown only when dry-run rewind says so).
- Permission cards → `.card` / `.card.warn`: Edit (old→new diff), Write (additions), Bash
  (`.cmd` pre block), generic. Buttons use shared `.ok` / `.no` classes with tick / circle-x icons.
- Plan card → `.card` with `.card-b` (`.ok` = Approve & implement with tick, `.no` = Keep planning
  with step-forward icon).
- **AskUserQuestion — full rewrite** (biggest piece): tabbed card (`.ask`, `.ask-tab.active`,
  `.ask-panel`), icon indicators replacing native inputs (see `IND` svgs in mockup JS),
  "Other" option revealing a text input, auto-advance to next tab on radio answer,
  submit-enable when all questions answered, Submit (`.ask-go ok`) + Cancel (`.no`) buttons.
- Slash menu → `.popup`/`.popup-item` style with live filtering; typing after opening via the
  button auto-prepends `/`. Model + mode menus anchored to their chips (`right: 0`), chip label
  syncs on pick (populate from `initialize.models`; switch via `set_model` / `set_permission_mode`).
- Keyboard nav for all menus: `MENUS` array, `activeMenu()`, ↑/↓ moves `.sel`, Enter activates,
  single Escape handler (priority: lightbox → open menu → history panel), hover moves `.sel`
  (one highlight, never two).
- Live states: `.think-live` shimmer + token count while thinking → collapsed
  `<details class="think">` "Thought for Ns" when done; flower spinner (`FRAMES`, 175 ms) +
  whimsical `VERBS` (1.8 s cycle) + meta `(6s · ↓ 88 tokens · thought for 4s)` while working;
  remove the working line when the first assistant text streams in.
- Dots: **green** = tool success, **red** = tool failure (`.tool-line.fail`, from `tool_result`
  `is_error`), **gray** (`--dot`) = thinking, **white** (`--fg`) = assistant text blocks.
- Error block (`.error`) + retry line (`.retry`, timeline-style with rotate-ccw icon).
- Attachment chips (`.att`): thumbnail/file icon + name + dimensions, hover-overlay ✕
  (absolute, chip-colored bg + left shadow fade — never shifts layout), `rmAtt()` hides the
  whole `#chips` row when the last chip is removed. In-message chips (`.msg-atts`, `.att.click`)
  open the `#lightbox` preview (real `<img>` replaces the `.ph-img` placeholder; caption = filename).
- History panel → `#histPanel` / `.hist-h` / `.hist-item .t/.d`; `top` computed from the
  header's `offsetHeight` on open; closes on outside click and Escape.

### Phase 3 — verify in sandbox
`runIde`, exercise real CLI streaming: partial deltas into the new markup, permission flows,
ask card answers (`updatedInput`), rewind/undo, session resume replay, stop/interrupt.
Expect a handful of streaming edge-case fixes.

## Class mapping (old chat.html → new)
| Old | New |
|---|---|
| `.perm` / `.perm-h` / `.perm-b` | `.card`(.warn) / `.card-h` / `.card-b` |
| `.add-l` | `.add` (diff lines are `display: block` now) |
| `.q` / `.q-opt` (flat question list) | `.ask` tabbed card (see Phase 2) |
| `.code` / `.code-h` / `.copy` | `.codeblock` / `.cb-h` / icon copy button |
| `.mi` (menu items) | `.popup` / `.popup-item` / `.pi-*` |
| `.think-body` | `.think .body` (+ new `.think-live` streaming state) |
| `.hist-panel` | `#histPanel` / `.hist-h` / `.hist-item` |

## Layout behaviors to port (JS in mockup, bottom `<script>`)
- `syncGutter()` — composer `paddingRight = 14px + log scrollbar width` so right edges align.
- ResizeObserver on composer: log `paddingBottom` / fade height track composer height;
  stay pinned to bottom while typing.
- `autoGrow()` — including the empty-state one-line pin (placeholder metrics lie).
- Composer is `max-width: 720px` centered — decide whether to keep the cap in the IDE panel.
- `.sel` is **only** the popup keyboard cursor; tabs use `.active`, chosen options `.checked`,
  effort dot `.cur`.

## Small conventions
- File paths get `.path` (monospace); `card-h code` is plain monospace, no background.
- Icon sizes: 12 (micro) / 14 (small) / 15 (inside buttons) / 16 (indicators) / 18 (composer bar) / 19 (header).
- All colors go through the `:root` tokens — no new hardcoded hexes.

## After the port — how design iteration works
- **Styling changes** (colors, spacing, radii, hovers, tokens): edit `webview/chat.css` only.
  The mockup links the same file, so browser preview and plugin stay in sync automatically.
- **Structural changes** (a block gains an element): two places — the mockup fixture AND the
  renderer's template string in chat.html. Treat the mockup markup as canonical and copy it
  verbatim into the renderer.
- **Gallery mode (worth adding):** a dev flag in the real chat.html that injects the mockup's
  fixture blocks into the live webview, so every state (error, ask card, spinner, fail dot)
  can be verified in JCEF without driving the CLI into producing one.
- The mockup keeps only: demo markup fixtures, devbar/frame scaffolding, demo JS timers.

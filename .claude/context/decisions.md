# Decisions

Format: `## YYYY-MM-DD — <decision>`, newest first, with *why* and *alternatives rejected*.
Never delete entries; mark superseded ones.

## 2026-08-12 — The Marketplace upload is automated, but only the UPLOAD
`.github/workflows/marketplace-upload.yml` (the repo's first workflow) fires on `release: published`,
downloads the zip `gh release create` just attached, and POSTs it to
`https://plugins.jetbrains.com/api/updates/upload` with `xmlId` + `file` and a bearer token from the
`JETBRAINS_MARKETPLACE_TOKEN` repo secret. Steps 1-9 of `docs/release.md` stay manual and local.
**Why:** the web-form upload was the one release step with nothing enforcing it, and the step that
decides whether Marketplace users see the release at all. Re-posting the PUBLISHED asset rather than
building in CI keeps "the zip users get is the zip that was smoke-tested on this machine" — the
guarantee step 9's `cmp` check already establishes. It also needs no Gradle and no 1.5 GB IDE
download in CI, so the run is seconds. Signing is unaffected: the zip goes up unsigned exactly as the
web form sent it (`signPlugin` is `onlyIf`-guarded on a key this project doesn't configure, and five
releases were accepted this way).
**Rejected:** `./gradlew publishPlugin` — it works (IPGP 2.1.0 falls back to `buildPlugin`'s archive
when signing is unconfigured), but it is still a command to remember, which IS the problem. Also
rejected: building the zip in CI on a tag push (publishes an artifact nobody ran, and moves the
release-notes approval gate somewhere worse); and a `channel` field (Stable is what the web form did).
**Consequence, handled:** stale `changeNotes` used to be caught by the human doing the upload —
0.4.0 and 0.5.0 both shipped 0.3.3's notes. It is now step 1b of the checklist.

## 2026-08-12 — A conversation's title is derived from the WHOLE transcript, not a window
`computeTitle` reads every line: the first `TITLE_HEAD_LINES` (400) are parsed in full for the
derived title (first user message, legacy `summary`), and past that a line is rejected on a substring
unless it holds `custom-title` or `ai-title`.
**Why:** those two records are appended WHERE THEY HAPPEN, so on a long thread they are thousands of
records in — a rename on a real 10,458-line transcript sat on lines 10455-10458 and the 400-line head
scan never saw it, so the panel showed the derived title forever and the rename looked broken. The
cost is the scan `tokensOf` already pays for every session in `list()` (measured on the same 35 MB
file: 521 ms cold, 0 ms cached), so this adds no new class of work.
**Rejected:** a tail window (`tailLines`, as `lastActivityOf` uses) — 256 KB is ~75 records on a
transcript with large tool results, so the name would evaporate after ~10 more turns, which is worse
than a consistent bug; and caching the name in our own state — a second source of truth is exactly
what the byte-identical `custom-title` record exists to avoid.

## 2026-08-12 — Rename writes the CLI's own `custom-title` record, from the header title
`SessionStore.rename` appends `{type:"custom-title", customTitle: title.trim(), sessionId}` + "\n"
with O_APPEND — the CLI's writer, read verbatim from the 2.1.226 binary, three fields, no uuid or
timestamp. UI is the header title: hover pencil, click the title OR the pencil, header becomes the
editor, Enter saves / Esc cancels. Safe on the LIVE session, unlike delete.
**Why:** byte-identical writes mean a rename here and a `/rename` in the terminal are one act, so
neither client sees a name the other cannot explain. The header (not the history row) was the
user's explicit choice. Appending is safe live because the CLI opens O_APPEND per write.
**Rejected:** the `rename_session` control subtype — it answers "onRenameSession callback not
registered", dispatching to an SDK embedder's callback, unreachable over stream-json. Also
rejected: a `/rename` slash command, and rename in the conversations list (user picked the header).

## 2026-08-12 — Tool-line paths: project-relative, one line, and a CHARACTER budget for the tail
Display drops the project root (segment-boundary match only) and splits into a shrinking `.p-head`
and a never-shrinking `.p-tail`. The tail carries the filename plus its parent when the two fit
`RenderLimits.PATH_TAIL_MAX` (40 chars), else the filename alone. The absolute path rides
`dataset.path`; the click handler prefers it over `textContent`.
**Why:** a character budget needs no layout measurement — the alternative forces a synchronous
layout per tool line during a replay that renders hundreds. CSS alone cannot express "shrink the
parent only once the prefix is gone": flex distributes shrink proportionally, so any factor big
enough to save the filename also nibbles the parent on a path that had room (both regimes measured
on real JCEF). Storing the absolute separately also fixed a path past DESC_MAX being *clicked*
truncated.
**Rejected:** filename-only tail (loses the disambiguating folder); parent always in the tail
(a narrow panel then clips the FILENAME); three spans with shrink ordering (proportional shrink
cost the parent a character it didn't need to lose); `direction: rtl` for a left-side ellipsis
(`unicode-bidi: plaintext` cancels it, and without it the leading `/` reorders).

## 2026-08-12 — The context gauge is an SVG arc, drawn with the composer's Lucide geometry
A ring in front of the percentage: `pathLength="100"` so `stroke-dasharray` IS the percentage,
both strokes `currentColor`, `--ctx-pct` set beside the digits in `renderContext`.
**Why:** `currentColor` makes the ring cross the 50% warn threshold with the number for free, with
no second rule to keep in step. `pathLength` removes the 2*pi*r constant. Lucide geometry (24
viewBox, r=9, stroke 2, 19px box, 3-unit inset) makes it measure identically to the square-slash
beside it — 13.5x13.5, confirmed in DevTools.
**Rejected:** a conic-gradient pie behind a radial mask — a third of the code and no markup, but
Chromium anti-aliases neither the sweep edge nor the mask, so it rendered visibly stepped.

## 2026-08-09 — Deleting the LIVE conversation = leave it first, then delete
Every history row now offers delete, the current one included. The live path routes through
`ClaudeSessionService.deleteCurrentSession`: restart on a fresh conversation, `awaitExit(5s)`
on the OLD process off-EDT, then delete the transcript, then re-push the sessions list.
**Why:** the CLI reopens the transcript per write, so a live delete truncates rather than
removes (the original refusal reason) — and `stop()` only sends the signal, so a dying CLI
can still flush one resurrecting write; the bounded wait closes that window. Deleting the
thread you're looking at was a standing user annoyance; "make it not exist" implies leaving
it. Mid-turn delete stops the turn — identical to mid-turn "New conversation", deliberately.
**Rejected:** delete-in-place (truncates, the refusal stays as backstop); hiding delete on the
current row (the previous design — solved the corruption, not the user's need); unbounded wait
(a hung CLI would leak the pooled thread and the file).

## 2026-08-09 — Editor verdict UI is a bar UNDER the diff, card-identical, combined grant only
Accept / Accept-all / Reject live on a `FileEditorManager.addBottomComponent` bar beneath the
diff editor: centered, no prose, no tint, the panel card's exact colours (chat.css .ok/.no,
mirrored constants) and exact Lucide glyphs (bundled /icons/accept.svg, accept-all.svg,
reject.svg). The suggestion button is COMBINED: one click grants every allow-suggestion whole
("Always allow" when rules are among them, else "Accept all edits"), via the new
FILE_SAVED_ALL verdict — a permission-flow-only extension; bridge verdicts stay the reference
three. No split/partial-grant dropdown in the editor — the panel card keeps that.
**Why:** the two surfaces answer the SAME question, so they must read as one control; the
user reviewed each iteration in runIde and specified bottom placement, no text, no tint, and
"combined, not the dropdown". Partial grants are a deliberate act that belongs on the card.
**Rejected:** toolbar CONTEXT_ACTIONS icons (invisible among diff icons); toolbar text
buttons (no warning-free API across 242→262); NOTIFICATION_PROVIDERS banner (top-only, loud);
platform icons (don't match the card's glyphs); split dropdown in the bar (user's explicit
spec). Mechanics and traps in gotchas.md.

## 2026-08-09 — Edit permissions are dual-surface, first answer wins; editor close is NOT a grant
A can_use_tool for Edit/Write/MultiEdit opens the panel card AND a real editor diff (read-only,
balloon Accept/Reject); `ClaudeSessionService.respondPermission`'s pending map is the arbiter —
first remove() sends, the loser no-ops. Closing the diff without deciding leaves the card as the
sole surface.
**Why:** keyboard users keep the card (and the always-allow rules, which stay card-only); the
editor gets the reference-style surface the roadmap wanted. TAB_CLOSED deliberately DIVERGES
from the bridge flow's accept-as-proposed here: a permission must never be granted by a window
being tidied away. Read-only panes because accept answers with the ORIGINAL input — an editable
pane would silently discard typing (tweak-travel is a possible v2).
**Rejected:** editor-only surface (breaks panel-only workflows); editor-title buttons for v1
(balloon ships now, buttons can follow); TAB_CLOSED→allow parity (unsafe for permissions).

## 2026-08-09 — openDiff never writes; every review's diff tab closes by a HELD handle
DiffReview implements the reference verdict contract (FILE_SAVED + final pane text /
DIFF_REJECTED / TAB_CLOSED — the CALLER does the disk write) and opens its diff as its own
`ChainDiffVirtualFile`, holding the file reference from creation; resolution closes exactly
that tab.
**Why:** measured against both halves of the reference — the VS Code extension builds both
panes as temp documents and never touches the real file; the CLI maps the verdict to
{oldContent, newContent} and writes itself. And `FileEditorManager.openFiles` does NOT report
diff editors (measured live: visible diff tab, empty openFiles), so any find-then-close closes
nothing — the old closeAllDiffTabs filter had never worked.
**Rejected:** writing the file on accept (double-write that fights the CLI — 10.5's original
premise, corrected); find-by-name/class tab closing (provably a no-op).

## 2026-08-09 — 10.1/10.3 re-scoped: the model-facing IDE-tool allowlist is upstream policy
The CLI drops every `mcp__ide__*` tool from the model's roster except getDiagnostics +
executeCode — a filter byte-identical across 2.1.222/223/226. Items now mean their bridge
halves (verified over MCP-over-WS).
**Why:** three identical versions = deliberate policy, not a regression to wait out; VS Code
models get the same two tools, so parity is preserved by accepting it.
**Rejected:** registering the bridge under a non-"ide" server name to dodge the prefix filter —
the CLI finds its IDE client BY the literal name `"ide"` for its own IDE features (TUI
diff-in-IDE among them); the dodge breaks more than it restores.

## 2026-08-09 — Retry-line wording is a chat.html table; replay reorders late-flushed retries (9.1)
The wire's api_retry `error` is a five-code enum (network failures = literal "unknown") —
`RETRY_REASONS` translates it, unfamiliar codes degrade to the raw code, twins dedupe by
consecutive attempt/max key. Replay-side, SessionStore inserts an api_error record whose
timestamp precedes the last emitted error item BEFORE that error.
**Why:** the rich reason never rides the stream (TUI-in-process only), so translation is the
ceiling live; and the CLI flushes buffered api_error records AFTER the concluding error record —
file order lies, timestamps and the parent chain don't.
**Rejected:** surfacing "the" error reason live (doesn't exist on the wire); a Set for dedupe
(would swallow a second same-turn storm's restart at 1/10); general timestamp re-sort of replay
(risky for every other record family — the insertion is scoped to this measured pattern).

## 2026-08-09 — Model-facing tool results are suppressed by CONTENT, not tool name (7.4)
`RenderLimits.isInternalResult()` drops the OUT box for a result whose FIRST line closes with
the CLI's own "(This tool result is internal metadata …)" declaration; `stripPlumbing()` also
strips a position-0 `[harness: …]` envelope.
**Why:** the async sub-agent launch is model-facing bookkeeping end to end, but the SAME tool's
completed result is the sub-agent's report — the one thing on that line worth reading — so
RESULT_SKIP's name-keying cannot express the split; only the text can. Position-0-only for the
envelope is structural safety: the CLI escapes line-initial forgeries to `[\harness:` before
prepending its own, so an unescaped one in first position can only be real.
**Rejected:** adding Task/Agent to RESULT_SKIP (kills the completed report); stripping
`[harness:` anywhere in the text (a sub-agent quoting the marker — this project's own notes
do — would lose it).

## 2026-08-09 — Replay error parity: the CODE travels, the WORDING lives once in chat.html (8.2)
SessionStore emits `status` items carrying the raw auth error code (`icon:"auth"`); replay
resolves it through the same AUTH_BLOCKED map the live path reads. `AUTH_BLOCKED_CODES` (the
membership set) lives in RenderLimits but is deliberately NOT spliced into LIMITS.
**Why:** the per-code text is UI wording only chat.html needs; Kotlin needs only membership.
A spliced set the JS never reads is dead weight — the alignment is pinned by a test instead
(every code must appear in chat.html), and an unknown code degrades to showing the raw code
rather than nothing. The phantom-summary suppression (`reqError`) is last-record-wins, not
sticky, so a request that recovers after an error record keeps its summary.
**Rejected:** duplicating the wording into Kotlin (drift); keying suppression on the presence
of any error record (a recovered request would lose its summary).

## 2026-08-09 — Live edit diffs render OPTIMISTICALLY from tool input, superseded by the card
Edit/Write/MultiEdit now draw replay's "Applied" card live, built from the tool_use INPUT at
`tool_result` time, and a `permission_request` for the same edit marks the pending record
superseded so manual mode never double-renders.
**Why:** the live wire carries no diff data at all (no `toolUseResult`/`structuredPatch` — probed
2026-08-05), and in acceptEdits / under a saved rule / in a pre-authorized path the CLI never
sends `can_use_tool`, so the permission card — the only live diff producer — never fires. Keying
off `currentMode` instead would have missed rule-grant and scratchpad approvals; keying off the
card's absence is not knowable at `content_block_stop` (the card, if any, arrives later). Hence
optimistic-plus-supersede rather than wait-and-see.
**Rejected:** asking the CLI for the patch (nothing to ask); a bespoke live diff widget (replay's
card already exists, is mode-agnostic by design, and sidesteps the bare-`.diff` float problem).
Cost of the choice: the gutter line must be fetched PRE-apply (see gotchas) and degrades to no
line numbers when it can't be found.

## 2026-08-09 — All keyboard chords removed; the plugin binds NO shortcuts
The three webview JS keydown chords are deleted, not re-homed as IDE actions: Ctrl/Cmd+N (new
conversation), Ctrl+Alt+G (gallery), F12 (DevTools). The `"devtools"` bridge branch in
`ChatPanel.kt` went with them — nothing sends it now. Every capability keeps a route: the New
button and `/clear` both send `kind:'new'`, the gallery stays exposed as `window.__gallery()`,
and DevTools keeps the shortcut-less `ClaudeBrains.OpenDevTools` action (Find Action).
**Why:** all three were dead on this setup — Ctrl+N was swallowed by the IDE's "Go to Class",
and the Ctrl+Alt+G / F12 handlers never fired at all inside JCEF. A shortcut that silently does
nothing is worse than none.
**Rejected:** re-registering them as IDE-level actions with `<keyboard-shortcut>` (the fix
previously queued in state.md) — that trades a dead chord for a keymap collision to hunt on
every IDE and OS, for a panel whose buttons are already one click away. Closes manual-test
1.5 / 11.1 / 11.2, dropping the open-issue count from 19 to 17.

## 2026-08-08 — Manual-test pass conventions; two behaviours accepted, not fixed
The 92-item pass closed with a "tick + inline ISSUE note" convention: a box is ticked when the
behaviour was OBSERVED, with defects recorded under it rather than leaving boxes open. Two
verification methods were admitted for unforceable events, each with provenance stated in the
tick: CDP fixture injection through `onClaudeEvent` (rate-limit: recorded fixture; refusal
fallback: the VS-Code-bundle shape the handler was built from), and direct MCP-over-WS calls to
the bridge (openFile / selection / openDiff) when CLI 2.1.226 stopped exposing those tools to
the model. **Accepted, not bugs:** untitled sessions deriving "/model <x>" as their title (CLI
audit record, same family as /effort turns); replayed image chips as "file.jpg <smaller>" (the
transcript persists the bare API image block — no filename exists to show).
**Rejected:** styling the issue notes as GitHub alerts (tried, reverted — plain text wanted);
logging the name-loss as a defect (retracted after measuring the records).

## 2026-08-07 — Light theme declined; colour groundwork removed
`docs/colors.md`, `design/colors.html`, and `design/light.css` (the theming groundwork —
light.css was the draft palette, unwired from mockup.html's `<link>` + devbar theme toggle)
deleted, along with the completed `docs/port-plan.md` and the one-off comparison pages
`design/btn-variants.html` / `design/model-icon-variants.html`. The
colours-through-`:root`-tokens rule they carried moved to conventions.md; the token inventory
itself had already drifted (28 documented vs 35 in chat.css).
**Why:** the user doesn't want a light theme, and the doc duplicated what `chat.css` answers.
**Rejected:** keeping the doc trimmed to theming notes — groundwork for work that won't happen
is reload-budget spent on nothing; git history keeps it recoverable.

## 2026-08-07 — Project context lives in `.claude/context/`; CLAUDE.md abolished
The `/context` skill's file set replaces the 427-line root `CLAUDE.md` (last committed copy at
`ee7e9fc`) and the global auto-memory directory for this project. `.gitignore` un-ignores
`.claude/context/` so the memory travels with the repo.
**Why:** one portable, structured location instead of a monolithic file that competes for the
whole reload budget every session. **Rejected:** keeping CLAUDE.md alongside (two sources that
drift — the same reason the plugin doesn't rebuild terminal config). `runbook.md` deliberately
not created: release steps already live in `docs/release.md`.

## 2026-08-06 — Declined (not deferred): cost display, token/usage panel, per-record metadata
Client-parity items 17, 18, 26 declined outright, joining the already-deliberate non-features:
hidden bookkeeping records (25), stripped IDE context (27), off-window history as a consequence
of windowed replay (28), silent `/effort` turns replaying as an honest audit trail (30).
**Why:** decision, not queue position — usage display moved OUT of deferred. Keeps the panel
focused on the many-times-an-hour loop.

## 2026-08-03 — Mode switcher sends the CLI's own four modes; bypass machinery removed
`manual` / `acceptEdits` / `plan` / `auto`, all switching live via one control request.
**Why:** `auto` is a safety-checked mode; the old plugin sent `bypassPermissions` under the
label "Auto" and had to relaunch the CLI to enter it (only bypass is refused at runtime).
**Rejected:** keeping bypass + relaunch-with-resume (`__modeRestarted` is gone). A stored
`default`/`bypassPermissions` migrates in `selectedMode()`.

## 2026-07-31 — Renamed to "Claude Brains"; distribution is Path B
Plugin id `io.github.amitsidhpura.claude-brains`, packages `io.github.amitsidhpura.claudebrains.*`,
no syncroze references. Custom plugin repo on `github.com/amitsidhpura/claude-brains`, NOT the
JetBrains Marketplace (process in docs/release.md).
*Partially superseded same day:* the plugin WAS also submitted to the Marketplace on 2026-07-31
(vendor `amitsidhpura`) — both channels ship the same id; IDEs take the higher version
(docs/release.md § Marketplace listing).

## 2026-07-30 — Slash menu is an allowlist; /model and /effort deliberately hidden
Over `--input-format stream-json` there's no interactive terminal, so only turn-producing
commands work; unconfirmed ones are hidden and refused. Enabled: `/compact` (→ CLI), `/clear`
(native, = New button). `/model`/`/effort` hidden because the composer has the model chip and
effort slider. Effort rides a muted `/effort` turn (no control request exists); it reappearing
on resume is ACCEPTED as an honest audit trail — no filter planned (closed renderer-parity's
last open item).

## 2026-07-30 — Per-turn file rewind removed
Not worth the cost: needs `CLAUDE_CODE_ENABLE_SDK_FILE_CHECKPOINTING=1` (which also bloats
every transcript with file-history snapshots), a git repo, and client-supplied uuids.
Revival notes in gotchas.md.

## Founding — "Develop in the IDE. Configure in the Terminal."
The scope rule. Settings page and non-terminal login are the terminal's half and will NOT be
built — they are "By design" absences, never gaps or backlog. Rationale: rebuilding `~/.claude`
config in the IDE is a second implementation that can only drift.

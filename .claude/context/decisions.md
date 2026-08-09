# Decisions

Format: `## YYYY-MM-DD — <decision>`, newest first, with *why* and *alternatives rejected*.
Never delete entries; mark superseded ones.

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

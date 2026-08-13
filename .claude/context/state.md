# State

## Current focus
**Nothing in flight.** Two things shipped and were committed but are NOT yet released — both ride the
next version bump: the in-flight gutter dot (2026-08-13) and the VFS refresh after CLI writes
(2026-08-14). Next work is whatever is picked from the list below or backlog.md.

What landed: the timeline gutter dot now reports state — **white and pulsing while in flight, green
on success, red on failure** (`@keyframes bg-pulse` on `.tool-line.run::before` / `.think-live::before`,
opacity only). A sub-agent settles when its TASK ends rather than when it was launched, and goes red
only on a `failed`/`killed` task status. Reasoning in decisions.md, traps in gotchas.md, the compared
motions in `design/dot-pulse-probe.html`.

## Three contracts to know before touching `webview/chat.css`
- **Spacing:** `--block-gap` (18px, independent blocks) and `--attach-gap` (8px, a line and what
  hangs off it). A new block under a line takes the attach gap — glossary.md for the vocabulary,
  gotchas.md for which FORM (a flex parent needs `calc(attach - block)`, a block parent takes the
  value directly). `design/tool-gap-probe.html` holds the compared candidates.
- **The gutter dot:** ONE geometry rule serves `.blk`/`.think`/`.think-live`/`.tool-line`; what
  differs is `--dot-c` on each element. Add a dot state by setting `--dot-c`, never by copying the
  geometry. `--pulse-period` (1.6s) is the panel's shared live heartbeat, `--pulse-name` the
  in-flight motion, and `design/dot-pulse-probe.html` drives that token.
- **Nothing may grow OUTSIDE the dot's 10px box.** `.turn-body` carries `content-visibility: auto`,
  whose paint containment clips outward drawing into a "cut half-rectangle" — this has bitten twice
  (the card menu, then an outward halo that had to be withdrawn). See gotchas before adding any
  effect with geometry.

## Testing the PLUGIN — two probes worth knowing
**The plugin's own MCP bridge answers questions about the IDE.** Speak to it the way the CLI does
(lockfile `authToken` in header `x-claude-code-ide-authorization`, subprotocol `mcp`, `tools/call`)
and `openFile` becomes a free "does the VFS know this file" probe — it calls `findVFile` and
refreshes nothing. `getDiagnostics` reads the loaded Document, so it answers "did an open editor
reload". Filter the lockfile by `ideName`: several IDEs may hold the same workspace open. Both
caveats are in gotchas.

## Testing the webview — read this before writing a fixture
`tools/live_harness.py` replays wire frames into the LIVE panel over CDP. **Nothing on the wire
creates a `.turn-body`** — the panel makes one in `addUserMessage` → `newTurn()` when the user sends
— so a fixture that only feeds frames builds its blocks bare in `#log`, outside the containment and
stacking context real blocks live in. That blind spot hid a real clipping bug behind 31 green
assertions. Use the per-step `"setup"` hook to build the turn first. `tools/fixtures/45-tool-dot-in-flight.json`
(31 steps / 63 assertions) is the worked example, including the turn-body cases.

Standing verification for panel work: live harness **217/217** in real JCEF (`./gradlew runIde` up,
tool window open), `./gradlew test` **87 green**.

## Next steps
- [ ] **3.1 custom commands + 9.10 together** (user's explicit pairing). `cmdKind` in chat.html has
      no custom-command detection, so everything outside {clear, compact} greys as 'tui'.
      Constraint: `system/init`'s `commands` payload has NO type field, so the slash allowlist is
      the only lever. The `dummy-cmd.md` fixture EXISTS on the Linux box
      (`~/Sites/claude-brains-testing/.claude/commands/`); recreate it on Windows before starting.
- [x] ~~VFS refresh after CLI writes~~ — DONE and committed 2026-08-14 (`CliFileSync` +
      `Vfs.refreshFromDisk`), verified end to end through the plugin's own MCP bridge and by the
      user in their own IDE.
- [ ] Release the in-flight dot in the next version bump (`docs/release.md`; `changeNotesHtml` in
      `plugin/build.gradle.kts` must be updated or `buildPlugin` refuses).

## Defect register (docs/manual-test.md)
**2 open ISSUE / 23 RESOLVED, zero unticked checklist items.** The 2 open are 3.1 + 9.10, which the
user wants worked TOGETHER.

## Which machine — check this FIRST, both are real
The 2026-08-13 sessions ran on **Linux** (`/home/syncroze/Sites/claude-brains`). Full paths for both
boxes are in overview.md § External references. What actually differs: the sibling test repo is
`~/Sites/claude-brains-testing` on Linux vs `D:\sites\claude-brains-test` on Windows — different
name, neither in git — and the 3.1/9.10 `dummy-cmd.md` fixture exists only on Linux.

## Known gaps (deliberately left)
- A sub-agent's WORK outcome is not surfaced, and cannot reliably be (gotchas). Its own text says so.
- A reloaded webview recovers its chrome but NOT its log (backlog).
- **No way to kill a background process from the panel** — `interrupt()` only stops the in-flight
  response, and the roster rows are display-only. In backlog.
- `prefers-reduced-motion` is honoured in CSS but unverified against the real OS setting on
  offscreen-rendered JCEF (backlog).
- Sidechain/subagent replay ordering untested — still no `isSidechain` records in a PARENT transcript.
- Windowed replay: DOM search only sees loaded blocks.
- Editor permission diff: tweak-travel is the remaining v2 half (backlog).
- A filename longer than the whole tool line is hard-clipped with no ellipsis (offered, declined).

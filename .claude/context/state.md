# State

## Current focus
**Unreleased work in the tree, all verified.** Since 0.6.0 (2026-08-14) three tranches landed on
2026-08-15: custom commands in the `/` menu (3.1 + 9.10), a 16-command IDE-development enabled set,
and the rendering fixes those exposed — then three UI fixes from user reports (background-roster
lifecycle, stuck popup highlight, one path renderer everywhere). Register: **0 open**.
Next real step is a version bump.

## The slash-command surface
- **Custom commands auto-enable from a wire marker, not a disk scan.** The CLI suffixes every custom
  entry's description with " (project)"/" (user)"; `markCustom()` (chat.html) strips it into `c.src`
  + the `customCmds` map, `cmdKind` returns 'text' for map hits and `mcp__` names, `.pi-src` renders
  the badge. Native/allowlist checked FIRST so a custom `clear.md` cannot shadow `/clear`.
- **16 built-ins enabled** (`CMD_ALLOWED`), grouped in docs/slash-commands.md by IDE-development
  relevance. `/usage` stays Hidden — the 2026-08-06 *declined* decision, not an omission.
- The CLI watches the PROJECT commands dir (drop ≈2.5s, delete ≈1s → `commands_changed`, no turn);
  `~/.claude/commands` is NOT watched → `/reload-skills` is the manual lever.

## Rendering rules learned the hard way (all fixture-pinned; see gotchas for the wire facts)
1. A local built-in's answer is a bare whole-message `assistant` frame with ZERO stream events.
2. A local built-in that FAILS reports through a `user` frame whose `content` is a STRING
   (`<local-command-stderr>`), dropped by an `!Array.isArray` guard before any rendering.
3. The CLI emits an `assistant` frame PER CONTENT BLOCK, so `msgStreamed` is a TURN-level fact —
   never cleared mid-turn (a `message_stop` reset double-renders the other way).
4. `background_tasks_changed` is a PER-PROCESS level signal: reset the roster on CLI restart
   (`clearLogUI`) and NEVER per turn — a background shell outlives its turn by design.
5. `sel` is a keyboard cursor with no exit counterpart; a non-actionable popup must opt out
   (`nosel`), and a CSS opt-out covering only `:hover` loses the tie to `.sel`.
6. **One path renderer for every surface:** `fillPath()` + the shared `pathParts()` cut. Relative,
   middle-ellipsised, ABSOLUTE path on `dataset.path` + `title` (the click handler reads
   `dataset.path` first — that is what allows the text to be shortened at all).

## Testing — the standing setup
- Live harness: `python tools/live_harness.py` needs `./gradlew runIde` up + tool window open.
  Baseline **273**; `./gradlew test` **103**. Wire fixtures need a `.turn-body` via the per-step
  `setup` hook. **Harness runs leave state in the live panel** — reload the webview before
  eyeballing it.
- **The wire tape is the tool for "why did nothing render":** monkey-patch `onClaudeEvent` and wrap
  the original in try/catch — ChatPanel calls it inside `executeJavaScript`, so a throw is swallowed
  by JCEF and the frame vanishes silently. DOM evidence alone cannot separate "the CLI sent nothing"
  from "the panel dropped it". Scratchpad `sweep.py`/`runner.py`/`matrix.py` (not committed).
- **`Input.dispatchMouseEvent` over CDP** drives real CEF input — stronger than synthetic DOM events
  for hover/click questions (used for the popup-highlight fix).
- All chat.html globals are readable from CDP (`busy`, `bgTasks`, `slashCommands`, `submit`, …).
- Manual re-test fixtures: `~/Sites/claude-brains-testing/.claude/commands/dummy-cmd.md` +
  `sub/nested-cmd.md`. **That repo is disposable** (user, 2026-08-15) — reset with
  `git reset --hard 64fa85a && git clean -fdxq -e vendor -e .idea`, then recreate those two files.

## Next steps
- [ ] **Release the next version** (`docs/release.md`). New since 0.6.0: custom-command menu, the
      16-command set, the three rendering fixes, and the three UI fixes above. `changeNotesHtml` in
      `plugin/build.gradle.kts` must be updated or `buildPlugin` refuses.
- [ ] Then backlog order: reloaded-webview log replay, kill-background-process from the panel,
      editor accept/reject v2 tweak-travel.

## Known gaps (deliberately left)
- **Paths inside free prose are NOT shortened** — todos, task/progress lines, info/error lines,
  compaction summaries, IN/OUT boxes, `.cmd` blocks. Rewriting paths inside sentences is a riskier
  job than the structured cases; awaiting a decision. Also flagged: `getDiagnostics`' `uri` is a
  `file://` value routed as free text, and the "File not found" balloon prints a bare absolute path.
- `/batch`'s fan-out verified at **N=2**, not 5–30; the whole command sweep ran against ONE sandbox
  project (a PHP CLI repo), so **`/run` never had to start a server**.
- The queue path (`.q-row`) sweep test was inconclusive — the first turn finished too fast.
- A webview reload replays the INITIALIZE-time roster, so mid-session commands vanish until the next
  `commands_changed` (backlog).
- The **mcp** source badge is fixture-pinned only; `aliases` is display-only (menu doesn't match it).
- Sub-agent WORK outcome not surfaced and cannot reliably be; reloaded webview loses its log; no way
  to kill a background process from the panel; `prefers-reduced-motion` unverified against the real
  OS setting; sidechain replay ordering untested; editor tweak-travel is the remaining v2 half.

## Which machine — check FIRST, both are real
2026-08-15 ran on **Linux** (`/home/syncroze/Sites/claude-brains`). Paths for both boxes in
overview.md § External references. The slash-menu fixtures exist on Linux only.

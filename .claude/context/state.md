# State

## Current focus
**2026-09-01 (seventh session): the tool-result `(note: …)` caveat hardening is committed on
`main` — UNRELEASED (0.12.3 is the shipped version and lacks it).** The user's 2026-08-30 "giant
yellow text" sighting was `resultNote` misreading large output as a caveat: the end-anchored regex
(`RenderLimits.kt` + its `50-blocks.js` mirror) captured everything from a literal `(note:` in the
output to a final `)`. Two guards added, each landed test-first with its negative control watched
failing: `NOTE_MAX = 400` (a longer collapsed capture is DROPPED, not truncated) and a position-0
guard (a match that starts the trimmed result is output — every real CLI note template is APPENDED
after other text, measured in the 2.1.252 binary). Full conditions list in `docs/limits.md` row
"tool-result `(note:)` caveat"; evidence trail in `tools/fixtures/69-result-note-cap.json`
provenance (two control runs + real-wire reproduction on CLI 2.1.252).
- Accepted residual: a sub-400 parenthetical appended mid-result and ending it is byte-identical
  to a real caveat and still renders — irreducible without structural marking from the CLI.
- The CLI is now **2.1.252** locally (2.1.251 audit still stands; 2.1.252 changelog is bugfix-only
  incl. "background task notifications with very large failure output" — unrelated to our misfire,
  that one is CLI-side API-payload, the panel never renders task-notification content).
- Checklist rules still in force: `**id** mark [effort] **Name** — gist; facts`; **At a glance**
  hand-maintained (recount with `awk` on change). Checklist: 83 ✅ · 46 ➖ (129 rows).
- Do not re-propose: effort chip suffix (2026-08-26); non-red destructive hovers (2026-08-29);
  Claude-side rewind/checkpoints or host git actions (2026-08-29); "Effort (High)" label / blue
  slider track (2026-08-29); Copilot-derived features other than terminal-output context
  (2026-08-29, decisions.md).

## Released — 0.12.3 (2026-08-30)
**0.12.3 is the shipped version** (tag `v0.12.3`): first-paint flash fixed for real
(`ui/ChatPanel.kt`), Marketplace-approved same day. The note-caveat hardening above is on `main`
only — releasing it is a separate, user-initiated call. Change notes carry the LAST THREE
versions + the GitHub releases link.

## Open work — ids verified against `docs/feature-checklist.md`
- No open checklist rows. Wants: backlog § Next up (Worktrees bundle 14.1+14.3, 15.5 debugger
  tools) and § Deferred (conversation tabs + 8.8/8.10).

## Testing — the standing setup
- `python3 tools/live_harness.py` baseline **592** (fixtures to **69**); `./gradlew test` **134**.
- Sandbox **PhpStorm 2024.2.6**; start (from `plugin/`; background tasks start in the REPO ROOT):
  `cd plugin && ./gradlew runIde -PskipVerifierIdes -PjcefDebugPort=9222
  --args="$HOME/Sites/claude-brains-testing"`. **`runIde` DETACHES** — gradle's exit code says
  nothing; `pgrep -f 'idea.system.pat[h]'`; kill by pid, wait for CDP to vanish. Claude may start
  and kill the sandbox on its own (user, 2026-08-29). Never run the harness and a CDP injection
  concurrently — fixtures `__clear` the log and the injection vanishes.
- **ALWAYS verify the running build BY CONTENT over CDP before trusting a fixture run** (e.g. grep
  the page HTML for the exact new code string). Control builds restore the WHOLE
  `plugin/src/main/resources/webview/` directory.
- Real end-to-end turns can be driven over CDP: `sendTurn('<prompt>', [])` in the panel scope
  spawns the real CLI; approve a permission card via `.card-b button.ok`. Used 2026-09-01 for the
  note-misfire before/after (scripts were scratchpad-only, gone with the session; trivial to redo).
- Fixture ids from page-lifetime counters (`sq1…`) must be read from the bridge tape, never
  written as literals (gotchas § Testing). A panel-supplied slash entry (`CMD_LOCAL`) changes
  every fixture that COUNTS rendered slash rows — recount when adding one.
- Sandbox-panel CLI sessions DO persist under
  `~/.claude/projects/-home-syncroze-Sites-claude-brains-testing/` (measured 2026-09-01) — grep
  there for real wire shapes; the 2026-08-28 "not findable" cases stay unexplained (gotchas).

## Next steps
- [ ] Release the note-caveat hardening in the next version bump (bundle with whatever comes
      next; nothing urgent — the bug needs output containing `(note:` AND ending with `)`).
- [ ] SchemaStore watch (no action until it syncs past 2.1.251):
      `github.com/SchemaStore/schemastore/commits/master/src/schemas/json/claude-code-settings.json`.
- [ ] **User errands**: Windows `./gradlew test` + VFS click check; upload Marketplace screenshots
      04+05 (regenerated 2026-08-30) to `plugins.jetbrains.com/plugin/33274`.

## Known gaps (deliberately left)
- **The Thinking switch is INERT on Fable** — measured 2026-08-26, "document only" by decision.
- **Do not gate UI on roster capability flags** (9.10 ➖); only `supportsFastMode` is gated.
- Effort/model conversation MARKERS dropped 2026-08-24; confirm-card path wrapping declined.
- Typing `/model` or `/clear` in the composer is refused (`cmdKind` → 'tui'); the chip / the header
  New button are the only surfaces. No keyboard-only new conversation.
- 1M switch carries NO client-side validity logic. Fast-mode "· fast" marker parked.
- 5.6 keyboard-only comment pill; plan-card keyboard shortcuts deferred 2026-08-16;
  `DiffReview.open` snapshot-only lookup parked; `/batch` verified at N=2 only.
- Note-caveat residual: sub-400 appended parenthetical at result end still renders as a note
  (accepted 2026-09-01, decisions.md).

## Which machine — check FIRST, both are real
2026-08-26 → 2026-09-01 sessions ran on **Linux** (`/home/syncroze/Sites/claude-brains`).
Paths for both boxes in overview.md § External references. Windows still owes the CRLF splice check.

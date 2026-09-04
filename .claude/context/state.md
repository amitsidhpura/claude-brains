# State

## Current focus
**2026-09-04: nothing in flight — 0.12.5 released and Marketplace-Approved.** `main` is clean
with zero unreleased work. Next candidates when the user picks one: backlog § Next up
(Update-Claude button, Worktrees bundle 14.1+14.3, 15.5 debugger tools).

## Open investigations
- **Fold-verdict report NOT reproduced** (user's Windows screenshot 2026-09-04: a Read OUT box
  expanded, not collapsible). Every venue folds correctly here. WAITING on the user running the
  DevTools diagnostic snippet on the Windows box + Help→About. Do not guess-fix (conventions).

## Released — 0.12.5 (2026-09-04), Marketplace-Approved
Tag `v0.12.5`, commit `a77a565` — the four first-impression fixes + the CSS split:
- `/context` / local built-ins render as prose, not a red error block (fixture 70)
- Read range suffix never wraps (fixture 71); MCP notice per fault severity (fixture 72)
- Early CLI death (pre-stream-json, `early:true`) shows the muted "may be out of date —
  `claude update`" hint + two instant-death race fixes (fixture 73)
- **CLI floor now stated everywhere**: "2.1.200+" in README, plugin.xml description (feeds the
  Marketplace listing) and the updatePlugins.xml feed. Cutoff MEASURED: 2.1.200 (2026-07-03)
  introduced `--permission-mode manual` and fully works; 2.1.199 rejects → hint. Policy stays
  no-floor/no-shims, hint only (decisions 2026-09-04).
- verifyPlugin ladder is now **8 IDEs** (PS-242 → PS-263), all Compatible. Marketplace verifier
  Success incl. a clean 2026.3 EAP IDE run (user's screenshot).

## Released — 0.12.4 (2026-09-01), Marketplace-approved
Tag `v0.12.4`, commit `e644dce`: files-changed rows + Review-only click target (fixture 60),
one-line bg-task titles, `(note:)` caveat cap. Change notes carry the last three versions.
- Accepted residual: a sub-400 parenthetical appended mid-result and ending it still renders as
  a note — irreducible without structural marking from the CLI.
- Checklist rules in force: `**id** mark [effort] **Name** — gist; facts`; **At a glance**
  hand-maintained (recount with `awk`). Checklist: 83 ✅ · 46 ➖ (129 rows).
- Do not re-propose: effort chip suffix (2026-08-26); non-red destructive hovers (2026-08-29);
  Claude-side rewind/checkpoints or host git actions (2026-08-29); "Effort (High)" label / blue
  slider track (2026-08-29); Copilot-derived features other than terminal-output context
  (2026-08-29); old-CLI vocab translation/retry (2026-09-05, superseded note 2026-09-04).

## Open work — ids verified against `docs/feature-checklist.md`
- No open checklist rows. Wants: backlog § Next up (Update-Claude button, Worktrees bundle
  14.1+14.3, 15.5 debugger tools) and § Deferred (conversation tabs + 8.8/8.10).

## Testing — the standing setup
- `python3 tools/live_harness.py` baseline **630** (fixtures to **73**); `./gradlew test` **137**.
- Sandbox **PhpStorm 2024.2.6**; start (from `plugin/`; background tasks start in the REPO ROOT):
  `cd plugin && ./gradlew runIde -PskipVerifierIdes -PjcefDebugPort=9222
  --args="$HOME/Sites/claude-brains-testing"`. **`runIde` DETACHES** — gradle's exit code says
  nothing; `pgrep -f 'idea.system.pat[h]'`; kill by pid, wait for CDP to vanish. Claude may start
  and kill the sandbox on its own (user, 2026-08-29). Never run the harness and a CDP injection
  concurrently — fixtures `__clear` the log and the injection vanishes.
- **ALWAYS verify the running build BY CONTENT over CDP before trusting a fixture run** — grep
  `<script>`/`<style>` textContent. Control builds restore the WHOLE
  `plugin/src/main/resources/webview/` directory.
- **PATH-dependent sandbox launches need `./gradlew --stop` first** (daemon caches env), and the
  **full harness baseline only counts against a real-CLI sandbox** (stub sandbox: 627/3) —
  gotchas § Testing. Old-CLI stub recipe: `_local/stubbin/claude` (5-line exit-1 script) + PATH
  prefix on runIde; instant-death paths NEED an instant stub (found two latent races).
- Real end-to-end turns can be driven over CDP: `sendTurn('<prompt>', [])` in the panel scope;
  approve a permission card via `.card-b button.ok`.
- Fixture ids from page-lifetime counters (`sq1…`) must be read from the bridge tape, never
  written as literals (gotchas § Testing). A panel-supplied slash entry (`CMD_LOCAL`) changes
  every fixture that COUNTS rendered slash rows — recount when adding one.
- Sandbox-panel CLI sessions persist under
  `~/.claude/projects/-home-syncroze-Sites-claude-brains-testing/` (measured 2026-09-01).

## Next steps
- [x] Release 0.12.5 — done 2026-09-04, Approved (§ Released above).
- [ ] **Waiting on the user**: Windows DevTools fold diagnostic + Help→About (§ Open
      investigations).
- [ ] SchemaStore watch (no action until it syncs past 2.1.251):
      `github.com/SchemaStore/schemastore/commits/master/src/schemas/json/claude-code-settings.json`.
- [ ] **User errands**: Windows `./gradlew test` + VFS click check; upload Marketplace screenshots
      01+03 and 04+05 to `plugins.jetbrains.com/plugin/33274`; check the listing page shows the
      new plugin.xml description ("2.1.200+") now that the 0.12.5 upload refreshed it.

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
2026-08-26 → 2026-09-04 sessions ran on **Linux** (`/home/syncroze/Sites/claude-brains`).
Paths for both boxes in overview.md § External references. Windows still owes the CRLF splice
check. (Journal note: the prior save's "2026-09-05" headings were written a day ahead — those
sessions ran 2026-09-04, per commit timestamps.)

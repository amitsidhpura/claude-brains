# State

## Current focus
**2026-09-05 (tenth session, Linux): mockup parity pass — `design/mockup.html` now carries a static
example of EVERY renderer state (0 of 214 CSS classes unexercised, was 13); a model-chip/menu
mismatch was investigated, NOT reproduced, and parked (backlog § Next up). The 2026-09-04
full-surface audit is complete (93 ✅ · 0 ⬜ · 47 ➖, 140 rows). Everything since 0.12.5 is
UNRELEASED on `main` (committed and pushed with this save).** Release only when asked. CLI on this
box is **2.1.261** (the checklist header still says 2.1.260 — a re-audit is the natural next ask).

### Shipped since 0.12.5 — the release-notes list, one line each (details: checklist rows)
- **Mockup parity** (2026-09-05, tenth): static examples for 1.26 banners (all six glyph kinds),
  1.28 withdrawn card (+ the auto-deny OUT box above it), 1.27 "open in editor" marker, 2.12
  directory grant, 3.8 `cmd-compound`/`cmd-edited` (typing into the command hides per-rule rows),
  card-menu `.up` flip, slash alias/source badges, fast-mode cooldown (devbar "fast"), stopping
  bg-task row, side-panel Clear → empty placeholder, replay `t-sfx`/`ti-missing`/`und-t`, `hl-n`,
  `ta-c`. Verified in a real browser over `http.server 8731` (gotchas § Webview).
- **Checklist folds** (docs task, 2026-09-05): every long row is a 1–2 line gist + a collapsed
  "Read more…" fold; 68 folds; nothing dropped. Shape rules in conventions § Docs.
- **1.26** banner-class `system` frames draw as muted status lines with a per-kind gutter glyph
  (branch / lucide link-2 / bookmark / open dot; warnings keep the alert). Twelve subtypes handled;
  only `vcs_state_changed` and `notification` are real on this wire (measured), six are REPL-only.
- **1.28** a withdrawn ask (`control_cancel_request`, measured: an interrupt over a parked card)
  settles the card as "✗ Withdrawn — Claude stopped waiting", drops the pending entry first, closes
  the editor diff tab. The CLI's own auto-deny OUT box above it stays (user's call).
- **1.27** the cut marker under a truncated IN/OUT box reads "open in editor" and opens the whole
  text read-only (`LightVirtualFile`); live rows from the page's copy, replayed rows via
  `SessionStore.toolText` by tool id; spills keep opening the CLI's file.
- Earlier in the same unreleased span: **4.7, 4.8, 2.12, 3.7 (+3 fixes), 3.8, 6.9, 6.5**; **4.9**
  deferred (no keyboard answers on cards).

## Open investigations
- **Model chip says "Fable (1M)" while the menu checks NO row** (user screenshot 2026-09-05, "mid
  session I think"; user: leave for future). NOT reproduced. Proven: that label comes only from
  `prettyModel` (no roster row matched the selected id, even tag-stripped), which is also why no ✓
  and the cursor sits on Default; the roster's Fable value is `fable[1m]` on 2.1.236 AND 2.1.261
  (measured, bare `initialize`); PhpStorm persists exactly `fable[1m]`; the menu code is identical
  in 0.12.5 and main. Inferred, unreproduced: 1M switch OFF on Default pins `claude-opus-5` (no
  row matches by value → no ✓, chip "Opus 5"). Diagnostic next time: DevTools
  `document.getElementById('modelChip').title` = the exact id. Details: backlog § Next up.
- **Fold-verdict report NOT reproduced** (user's Windows screenshot 2026-09-04: a Read OUT box
  expanded, not collapsible). Every venue folds correctly here. WAITING on the user running the
  DevTools diagnostic snippet on the Windows box + Help→About. Do not guess-fix (conventions).
- **Title tooltips never show in the panel on Linux JCEF** (user, 2026-09-05) — backlog § Next up:
  handle `CefDisplayHandler.onTooltip`. Never make a `title` the only carrier of a fact.
- Two observations parked in backlog (not defects of any row): a `/loop` tick reaches the panel as
  a fresh init + reply with NO user frame; a Stop hook's feedback user frame is not drawn.

## Released — 0.12.5 (2026-09-04, `a77a565`, tag `v0.12.5`), Marketplace-Approved
Four first-impression fixes + the CSS split; CLI floor "2.1.200+" stated in README, plugin.xml and
the feed (measured cutoff; policy no-floor/no-shims, hint only). verifyPlugin ladder 8 IDEs.
- Checklist rules in force: `**id** mark [effort] **Name** [tags] — gist` on the first line, facts
  under a `<!-- --><details><summary>Read more…</summary>` fold with NO blank lines and NO nested
  lists (conventions § Docs); **At a glance** hand-maintained (recount with a regex over the first
  line only: `^- \*\*\d+\.\d+\*\* mark`).
- Do not re-propose: effort chip suffix (2026-08-26); non-red destructive hovers (2026-08-29);
  Claude-side rewind/checkpoints or host git actions (2026-08-29); "Effort (High)" label / blue
  slider track (2026-08-29); Copilot-derived features other than terminal-output context
  (2026-08-29); old-CLI vocab translation/retry (2026-09-04); keyboard answers on cards (2026-09-05);
  suppressing the auto-deny OUT box above a withdrawn card (2026-09-05).

## Testing — the standing setup
- `python3 tools/live_harness.py` baseline **776** (fixtures to **84**); `./gradlew test` **163**.
- Sandbox **PhpStorm 2024.2.6**; start (from `plugin/`; background tasks start in the REPO ROOT):
  `cd plugin && ./gradlew runIde -PskipVerifierIdes -PjcefDebugPort=9222
  --args="$HOME/Sites/claude-brains-testing"`. **`runIde` DETACHES** — gradle's exit code says
  nothing; `pgrep -f 'idea.system.pat[h]'`; kill by pid, wait for CDP to vanish (`until ! ss -ltn |
  grep -q ':9222'`). Claude may start and kill the sandbox on its own (user, 2026-08-29). Never run
  the harness and a CDP injection concurrently. **`pkill -f`/`pgrep -f` with a pattern that also
  matches your own shell kills the shell (exit 144) — bracket one char: `'http.serve[r] 8731'`.**
- **Free negative control**: write the fixture while the sandbox still runs the PRE-change build,
  run it, see the discriminating asserts fail, THEN edit sources and restart. Every fixture assert
  must be null-safe (`(sel||{}).textContent`, a guarded `.click()`): a throw ABORTS the harness and
  the control reads as nothing (gotchas § Testing). Control builds restore the WHOLE
  `plugin/src/main/resources/webview/` directory.
- **The harness has no mid-frame JS hook**: a click between frames goes in the NEXT step's `setup`.
  A Bash tool line always has an IN row — count OUT rows by their `.io-k` text.
- **Stdio probes**: `tools/probe_stdio.py` (panel flags; `--on-ask park` + `--interrupt-after` is
  the 1.28 recipe; `--cfg` for settings-dependent runs — gotchas § Testing). A plain run writes a
  real transcript under `~/.claude/projects/-tmp-…/` — delete those afterwards (14 leftovers from
  older sessions still sit there, untouched on purpose).
- **PATH-dependent sandbox launches need `./gradlew --stop` first**, and the **full harness baseline
  only counts against a real-CLI sandbox** (gotchas § Testing).
- Real end-to-end turns over CDP: `sendTurn('<prompt>', [])`; approve a card via `.card-b button.ok`;
  `tools/cdp.py -f probe.js` for multi-line JS; `--screenshot`. Visual candidates are rendered in
  the REAL panel this way (three glyph options, 2026-09-05).
- Fixture ids from page-lifetime counters (`sq1…`) must be read from the bridge tape, never written
  as literals; a panel-supplied slash entry (`CMD_LOCAL`) changes every fixture that COUNTS slash rows.
- Sandbox-panel CLI sessions persist under `~/.claude/projects/-home-syncroze-Sites-claude-brains-testing/`;
  a real transcript makes the best Kotlin test resource (`src/test/resources/fixtures/*.jsonl`).

## Next steps
- [x] Mockup parity pass 2026-09-05 (tenth); committed and pushed with this save.
- [ ] **Model chip / menu mismatch** — parked; when it recurs, capture the chip title id + box + CLI
      version + what preceded it (1M flip, fallback line, CLI restart). § Open investigations.
- [ ] **Waiting on the user**: Windows DevTools fold diagnostic + Help→About (§ Open investigations).
- [ ] Testing repo carries the 2026-09-05 hand-test commit `bf46eb2 banner test` (+ `banner-test.txt`,
      and it swept in the staged deletion of `dummy-permission-test.txt`) — `git reset --soft HEAD~1`
      there if unwanted; the user's decision.
- [ ] Next natural asks: a **release** (notes list above; conventions § release gate: verifyPlugin,
      verdict files), or a **re-audit at 2.1.261** (runbook step 8: header References/date, §2's
      "unchanged in <ver>", the [NEW] legend).
- [ ] SchemaStore watch (no action until it syncs past 2.1.251):
      `github.com/SchemaStore/schemastore/commits/master/src/schemas/json/claude-code-settings.json`.
- [ ] `update_settings` allowlist watch — during each CLI re-audit, not before (backlog § Next up).
- [ ] **User errands**: Windows `./gradlew test` + VFS click check; upload Marketplace screenshots
      01+03 and 04+05 to `plugins.jetbrains.com/plugin/33274`; check the listing page shows the
      "2.1.200+" description.

## Known gaps (deliberately left)
- **The Thinking switch is INERT on Fable** — measured 2026-08-26, "document only" by decision.
- **Do not gate UI on roster capability flags** (9.10 ➖); only `supportsFastMode` is gated.
- Effort/model conversation MARKERS dropped 2026-08-24; confirm-card path wrapping declined.
- Typing `/model` or `/clear` in the composer is refused (`cmdKind` → 'tui'); the chip / the header
  New button are the only surfaces. No keyboard-only new conversation; no shortcuts on any card.
- 1M switch carries NO client-side validity logic. Fast-mode "· fast" marker parked.
- Banner frames, task frames and withdrawn asks are live-only (never persisted); replay draws the
  CLI's own auto-deny result instead. Decided Bash cards vanish on replay; a Bash card's reject note
  is live-only. A note typed before Accept on an ordinary card is dropped.
- VS Code's "Answering your earlier questions" fold-in of late answers is not replicated (1.28).

## Which machine — check FIRST, both are real
2026-08-26 → 2026-09-05 sessions ran on **Linux** (`/home/syncroze/Sites/claude-brains`).
Paths for both boxes in overview.md § External references. Windows still owes the CRLF splice
check and the fold diagnostic.

# State

## Current focus
**2026-09-05 (eighth session, Linux): working through the 2026-09-04 full-surface audit's rows one
at a time — seven shipped (6.9, 6.5, 4.7, 4.8, 2.12, 3.7, 3.8), 4.9 deferred, three open (all §1).
All of it since 0.12.5 is UNRELEASED on `main`; 3.8 is uncommitted (committed through `d1974c4`).** The user's standing call: "I am planning to finish all even if
small." Each row goes: explain it in plain words → the user decides → probe/measure → build with
the free negative control → the user hand-tests in the sandbox (MT-row in the checklist) → next.
Checklist **90 ✅ · 3 ⬜ · 47 ➖** (140 rows); §2, §3, §4 complete. Release only when asked.

### Shipped since 0.12.5 — the release-notes list, one line each (details: checklist rows)
- **4.7** never-picked chip starts on the CLI's default (`auto` on 2.1.260), `permissions.defaultMode`
  honoured; `Don't ask` displayed while current, never offered.
- **4.8** Always-allow is a split on every rule card: main half = the CLI's default destination,
  caret = `This session only` / `This project, shared` / `All projects` (`PermissionDestinations`
  forwards only the four offered — an unknown value makes the CLI DROP the grant silently).
- **2.12** content roots outside `project.basePath` are passed as `--add-dir` at launch
  (`WorkspaceRoots.extraDirs`); a root attached mid-session waits for the next New/resume.
- **3.7** "Tell Claude what to do instead" field inline after Reject on every ordinary card; deny
  only; Enter rejects with the note. Plus three fixes it exposed: no duplicate error OUT box for a
  denial the card sent (`cardDenies`), replay no longer counts a rejected edit as "1 file changed",
  and a replayed edit card quotes its note like the plan card.
- **3.8** the Bash command on a card is editable in place; Accept runs the edited text
  (`updatedInput`) and the card says "edited in the IDE before accepting"; Always allow follows
  the edit on every card (rules rewritten to the edited text, one per part of a compound —
  `EditProposals.splitCommand`); a compound card hides only its per-rule rows while edited.
  Live-only on replay; the model sees the original command beside the edited output (CLI design).
- **6.9 / 6.5** mention chips; Mention-from-the-IDE popups.
- Deferred, do not re-propose: **4.9** number-key answers on cards (user, 2026-09-05).

## Open investigations
- **Fold-verdict report NOT reproduced** (user's Windows screenshot 2026-09-04: a Read OUT box
  expanded, not collapsible). Every venue folds correctly here. WAITING on the user running the
  DevTools diagnostic snippet on the Windows box + Help→About. Do not guess-fix (conventions).
- **Title tooltips never show in the panel on Linux JCEF** (user, 2026-09-05, every titled control)
  — backlog § Next up: handle `CefDisplayHandler.onTooltip`. Never make a `title` the only carrier
  of a fact (gotchas § JCEF).

## Released — 0.12.5 (2026-09-04, `a77a565`, tag `v0.12.5`), Marketplace-Approved
Four first-impression fixes + the CSS split; CLI floor "2.1.200+" stated in README, plugin.xml and
the feed (measured cutoff; policy no-floor/no-shims, hint only). verifyPlugin ladder 8 IDEs.
Earlier: 0.12.4 (2026-09-01, `e644dce`). Change notes carry the last three versions.
- Checklist rules in force: `**id** mark [effort] **Name** — gist; facts`; **At a glance**
  hand-maintained (recount with a regex over `**N.N** mark`).
- Do not re-propose: effort chip suffix (2026-08-26); non-red destructive hovers (2026-08-29);
  Claude-side rewind/checkpoints or host git actions (2026-08-29); "Effort (High)" label / blue
  slider track (2026-08-29); Copilot-derived features other than terminal-output context
  (2026-08-29); old-CLI vocab translation/retry (2026-09-04); keyboard answers on cards (2026-09-05).

## Testing — the standing setup
- `python3 tools/live_harness.py` baseline **714** (fixtures to **81**); `./gradlew test` **160**.
- Sandbox **PhpStorm 2024.2.6**; start (from `plugin/`; background tasks start in the REPO ROOT):
  `cd plugin && ./gradlew runIde -PskipVerifierIdes -PjcefDebugPort=9222
  --args="$HOME/Sites/claude-brains-testing"`. **`runIde` DETACHES** — gradle's exit code says
  nothing; `pgrep -f 'idea.system.pat[h]'`; kill by pid, wait for CDP to vanish. Claude may start
  and kill the sandbox on its own (user, 2026-08-29). Never run the harness and a CDP injection
  concurrently — fixtures `__clear` the log and the injection vanishes.
- **Free negative control**: write the fixture while the sandbox still runs the PRE-change build,
  run it, see the discriminating asserts fail, THEN edit sources (a running sandbox does not pick
  up source edits) and restart. Verify the new build by content over CDP (`<style>` textContent is
  reliable; `document.querySelector('script')` is NOT the spliced script — a passing new fixture is
  the proof). Control builds restore the WHOLE `plugin/src/main/resources/webview/` directory.
- **The harness has no mid-frame JS hook**: a click between frames goes in the NEXT step's `setup`
  (fixture 79). A Bash tool line always has an IN row — count OUT rows by their `.io-k` text.
- **Settings-dependent CLI probes**: scratch `CLAUDE_CONFIG_DIR` with copied credentials + a
  trust-patched `.claude.json`, else project settings never load (gotchas § Testing); run cell loops
  under `bash -c` (zsh does not word-split `set -- $c`). Read the probe's STDERR.
- **PATH-dependent sandbox launches need `./gradlew --stop` first** (daemon caches env), and the
  **full harness baseline only counts against a real-CLI sandbox** (stub sandbox: 627/3) —
  gotchas § Testing. Old-CLI stub recipe: `_local/stubbin/claude` (5-line exit-1 script).
- Real end-to-end turns can be driven over CDP: `sendTurn('<prompt>', [])` in the panel scope;
  approve a permission card via `.card-b button.ok`. Screenshots: `tools/cdp.py --screenshot`.
- Fixture ids from page-lifetime counters (`sq1…`) must be read from the bridge tape, never
  written as literals (gotchas § Testing). A panel-supplied slash entry (`CMD_LOCAL`) changes
  every fixture that COUNTS rendered slash rows — recount when adding one.
- Sandbox-panel CLI sessions persist under
  `~/.claude/projects/-home-syncroze-Sites-claude-brains-testing/` — a real transcript from a hand
  test makes the best Kotlin test resource (`src/test/resources/fixtures/denied-edit.jsonl`, private
  records stripped).

## Next steps
- [x] **3.8 hand test** passed 2026-09-05 (MT-3.8 RESOLVED).
- [x] **3.8 grant-follows-the-edit hand test** passed 2026-09-05 (compound `;` edit → two per-part rules in the testing repo's `.claude/settings.json`; the user owes that file's deletion). Commit when asked — 3.8 is uncommitted.
- [ ] Next row: **1.28** `control_cancel_request` — probe over stdio FIRST whether the CLI sends
      the frame at all (interrupt a parked `can_use_tool`; the protocol doc lists it as a
      stdin-only type, the row says CLI → host — both may exist), then mark the card lapsed + drop
      it from `pendingPermissions`. Then **1.27** full IN/OUT in an editor tab, **1.26**
      banner-class `system` frames. Re-derive ids from `docs/feature-checklist.md`.
- [ ] **Waiting on the user**: Windows DevTools fold diagnostic + Help→About (§ Open
      investigations); testing-repo cleanup is done (both settings files removed 2026-09-05).
- [ ] SchemaStore watch (no action until it syncs past 2.1.251):
      `github.com/SchemaStore/schemastore/commits/master/src/schemas/json/claude-code-settings.json`.
- [ ] `update_settings` allowlist watch — during each CLI re-audit, not before (backlog § Next
      up; adopt for per-project chip persistence when `model`/`permissions`/`effortLevel` land).
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
- 5.6 keyboard-only comment pill; no keyboard shortcuts on any card (2026-08-16, 2026-09-05);
  `DiffReview.open` snapshot-only lookup parked; `/batch` verified at N=2 only.
- Note-caveat residual: sub-400 appended parenthetical at result end still renders as a note
  (accepted 2026-09-01, decisions.md).
- Decided Bash cards vanish on replay (deliberate); a Bash card's reject note is live-only.
  Diff and plan cards replay with their notes.
- A note typed before Accept on an ordinary card is dropped, not delivered (no wire for it).

## Which machine — check FIRST, both are real
2026-08-26 → 2026-09-05 sessions ran on **Linux** (`/home/syncroze/Sites/claude-brains`).
Paths for both boxes in overview.md § External references. Windows still owes the CRLF splice
check.

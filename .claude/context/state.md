# State

## Current focus
**2026-10-10 (eighteenth session, Linux): bold wrapping italic fixed — built, controlled,
harness-verified, committed.** User screenshot + transcript (`_local/2026-10-08/949f51ad-….jsonl`,
CLI 2.1.276): `**Garlic (*Allium sativum*)**` drew as the literal `**` around an italic species
name. Cause: `inlineMd()`'s bold regex (`plugin/src/main/resources/webview/js/20-markdown.js`) was
`\*\*([^*]+)\*\*`, so any single `*` inside the pair killed the bold and `)** and **C**` mis-paired
as a bold " and ". Fix: bold admits single `*` and stops at the first `**`; the italic pass then
pairs the inner asterisks. Fixture 90 (control 6/6 fail on the pre-change sandbox → 12/12),
harness **905/0**. Checklist 1.10 Read-more notes it; mockup gained one `<b>…<i>…</i></b>` item.
`./gradlew test` NOT run (JS-only change).
**2026-10-08 (seventeenth / sixteenth):** opened-thought trailing gap (`thinkBlock()` trims,
fixture 89) and the Default selection following the model the CLI serves (`defaultResolvedFromCli`
+ `reconcileCliModel()` in `30-menus.js`, chip "Default (<real>)", panel FOLLOWS and never sends
`set_model`, fixture 88, live-verified) — journal 2026-10-08, decisions 2026-10-08.
**FOUR fixes are now NOT installed in the real PhpStorm**: PATH lookup (2026-09-27), Default chip
and thinking trim (2026-10-08), bold-wrapping-italic (2026-10-10). Its panel runs the VS Code
extension's binary via the unfixed fallback. A build from `main` (`cd plugin && ./gradlew
buildPlugin`, zip in `plugin/build/distributions/`, restart) or a 0.14.1 release installs all
four; the user's call.
The newest `claude` CLI on this box is **2.1.295** (`~/.local/share/claude/versions/`); last
re-audit was at 2.1.270 (2026-09-13). The sandbox PhpStorm was left RUNNING on the fixed build
(CDP 9222).

## Open investigations
- **Where the user's Fable default comes from** — not on this Linux box (no `model` key in
  `~/.claude/settings.json`, none in the repo's `.claude/settings*.json`, no `ANTHROPIC_MODEL`);
  the session that hit the limit is not under `~/.claude/projects` here (Windows or another
  project). The panel now SHOWS it; it does not change what runs.
- **Model chip says "Fable (1M)" while the menu checks NO row** (2026-09-05; parked). Candidate:
  a persisted `fable[1m]` matching nothing on a tagless 2.1.28x roster. backlog § Next up.
- **The roster is per-PROCESS, not per-binary** (2026-10-08): a terminal stdio `initialize` of
  2.1.293 listed 11 rows (`haiku→claude-haiku-4-5-20251001`, no `fable` alias) while the sandbox
  panel's 2.1.293 process minutes later listed 13 (`haiku→claude-haiku-5-5`,
  `sonnet→claude-sonnet-5-5`, `fable→claude-fable-5-1`). Gate/flag driven; a re-audit lead.
- **Fold-verdict report NOT reproduced** (Windows, 2026-09-04). WAITING on the user's DevTools
  diagnostic + Help→About. Do not guess-fix.
- **Title tooltips never show in the panel on Linux JCEF** (2026-09-05) — backlog § Next up.

## 2.1.293 leads for the next re-audit (seen, NOT audited — runbook re-audit procedure)
- Roster values carry no `[1m]` tag; the panel's 1M switch reads OFF on every row — measure what
  `set_model` with a `[1m]` suffix does before touching `rosterFor`/`strip1m` (`30-menus.js`).
- `result.modelUsage[...].contextWindow` is 1,000,000 for `claude-haiku-5-5` and for
  `claude-opus-5-5` (taped 2026-10-08) — the CLI's number, which the gauge takes as authoritative.
- `chipName()` parses the version out of the DESCRIPTION; named rows no longer lead with it —
  the chip falls back to `displayName`, which happens to work.
- `initialize` keys not seen before 2.1.283: `fast_mode_disabled_reason`, `ide_rc_auto_enable_gate`,
  `remote_control_*`, `session_state`, `available_output_styles`, `user_output_styles_dir`,
  `analytics_disabled` (20 top-level keys on 2.1.293, listed in the protocol doc § models).
- `reference/claude-code-log` clone: `git pull` before the re-audit (was 2.1.261 on 2026-09-13).

## Testing — the standing setup
- `python3 tools/live_harness.py` baseline **905** (fixtures to **90**); `./gradlew test` **168**.
- Sandbox **PhpStorm 2024.2.6**; start (from `plugin/`; background tasks start in the REPO ROOT):
  `cd plugin && ./gradlew runIde -PskipVerifierIdes -PjcefDebugPort=9222
  --args="$HOME/Sites/claude-brains-testing"`. **`runIde` blocks until the IDE exits** (a kill reads
  as exit 1). Find the sandbox's CLI by command line (`pgrep -f 'permission-prompt-too[l] stdio'`,
  parent cmdline contains `transformed/PhpStorm-2024.2.6`) — `pgrep -x claude` misses the
  versioned binary. Kill through `pgrep -f '<pattern>' | xargs -r kill` — `kill $pids` fails in zsh
  even after `tr '\n' ' '` (no word-splitting of variables); wait for CDP to vanish
  (`until ! ss -ltn | grep -q ':9222'`, bound the loop). Never run the harness
  and a CDP injection concurrently. **`pkill -f`/`pgrep -f` patterns bracket one char.** A running
  sandbox does NOT block `test`/`buildPlugin`.
- **The harness is NOT side-effect free on the sandbox CLI**: fixture 55's `setModel` calls go
  through the REAL bridge, so a live end-to-end after a harness run inherits an explicit
  `set_model` (which beats settings). Start a fresh CLI first: `bridge({kind:'new'})` over CDP, then
  wait for a NEW pid by command line (gotchas § Testing).
- **Live turn over CDP**: `sendTurn('<prompt>', [])`; wait on `#log .blk` count or a taped
  `result` (wrap `onClaudeEvent`) — a poll on `#log .generating` never saw it on 2026-10-08. Read
  the wire shape BY KEY from the transcript found by `"version"` + first `timestamp`, never `ls -t`
  (an older file's mtime was newer twice on 2026-10-08).
- **PATH-dependent sandbox launches need `./gradlew --stop` first**; a stripped-PATH launch
  reproduces the desktop-launched IDE (gotchas § JCEF, ShellEnv bullet).
- **Free negative control**: write the fixture while the sandbox still runs the PRE-change build,
  run it, see the discriminating asserts fail, THEN edit sources and restart. Page-lifetime state
  needs an explicit reset in step 1 (fixture 88 learned it from fixture 67's `model:'<fixture>'`).
  Record the control's actual readings in the fixture `provenance`.
- **Stdio probes**: a bare `initialize` + one turn is one `printf | claude --input-format
  stream-json … --permission-prompt-tool stdio`, `--settings '{"model":"haiku"}'` makes it cheap;
  delete the transcript it writes under `~/.claude/projects/<enc-cwd>/` and check `lastSessionId`.
  **TUI behaviour** → pty driver, trusted dir only (gotchas § Testing; conventions § Workflow).
- **Releases**: `docs/release.md` end to end, `./gradlew test buildPlugin verifyPlugin` as ONE
  background run, then the step-6 gate with the FULL notes before commit/tag/push (gotchas § Build).
  A release starts only when the user asks (conventions).

## Next steps
- [x] Default selection follows the served model — fixture 88, docs, committed 2026-10-08.
- [x] Opened-thought trailing gap — `thinkBlock()` trim, fixture 89, committed 2026-10-08.
- [x] Bold wrapping italic — `inlineMd()` bold regex, fixture 90, committed 2026-10-10.
- [ ] Get all FOUR fixes (PATH lookup 2026-09-27, Default chip + thinking trim 2026-10-08, bold
      wrapping italic 2026-10-10) into the real PhpStorm: local zip install, or a 0.14.1 patch release (user's call; `verifyPlugin` on
      every release).
- [ ] Re-audit 2.1.270 → 2.1.293 (runbook), starting from the leads above; the 1M switch first;
      the per-process roster question second.
- [ ] Check the listing's Overview still reads plugin.xml's description text (user errand).
- [ ] Renderer follow-ups, all in backlog § Housekeeping (indent-only code blocks; `*` inside
      inline code — same `inlineMd()`, untouched by the 2026-10-10 fix; mid-line fence placeholder leak; `~~~` fences).
- [ ] **Model chip / menu mismatch** — parked; capture the chip title AND the persisted value vs
      the running roster when it recurs (the 9.1 reconcile may change the symptom's look).
- [ ] **Waiting on the user**: Windows DevTools fold diagnostic + Help→About; the Windows CRLF
      splice check (checklist 3.2 fold); Windows look of the 1.29 card header path; Windows
      `./gradlew test` + VFS click check.
- [ ] Testing repo carries hand-test leftovers (README.md, test-code.js, timestamp*.txt) and two
      sandbox transcripts from 2026-10-08 — the user's call whether to reset.
- [ ] SchemaStore watch (no action until it syncs past 2.1.251).

## Known gaps (deliberately left)
- **Before the first turn nothing on the wire names a settings `model` override** — the chip shows
  the roster's claim until `system/init` arrives (checklist 9.1 "Gap"; decision 2026-10-08).
- **The Thinking switch is INERT on Fable** — measured 2026-08-26, "document only" by decision.
- **Do not gate UI on roster capability flags** (9.10 ➖); only `supportsFastMode` is gated.
- The picker shows the CLI's whole roster, pinned previous versions included (decision 2026-09-27).
- Typing `/model` or `/clear` in the composer is refused; the chip / header New button are the
  only surfaces. No keyboard-only new conversation; no shortcuts on any card.
- Banner frames, task frames and withdrawn asks are live-only; replay draws the CLI's own auto-deny
  result. Decided Bash cards vanish on replay; a Bash card's reject note and edited command are
  live-only. A note typed before Accept on an ordinary card is dropped ("applies to Reject").
- Markdown: a bullet-character change does not split a list (forgiving on purpose); the offline
  highlighter colours its keyword set inside EVERY language, prose fences included (1.11).
- plugin.xml "Not there yet" list unchanged: dark UI only, one conversation, no auto-context.

## Which machine — check FIRST, both are real
2026-08-26 → 2026-10-10 sessions ran on **Linux** (`/home/syncroze/Sites/claude-brains`).
Paths for both boxes in overview.md § External references. Windows still owes the CRLF splice
check and the fold diagnostic. The PATH trap is Linux/macOS-shaped (desktop-launched IDE);
Windows has no shell PATH layer (`ShellEnv` is empty there by design).

# State

## Current focus
**2026-09-27 (fifteenth session, Linux): the panel had silently been running the VS Code
extension's bundled CLI 2.1.270 while the terminal ran 2.1.283 — fixed, verified, committed.**
Cause: `ClaudeSessionService.resolveExecutable()` walked the IDE's bare `System.getenv("PATH")`
(a snap PhpStorm has no `~/.local/bin`), missed the standalone install and fell back to
`~/.vscode/extensions/anthropic.claude-code-2.1.270-linux-x64/resources/native-binary/claude`,
while `ClaudeCli` spawned the child under the `EnvironmentUtil` + `ShellEnv` overlay. Fix:
`ShellEnv.overlay()` (one copy of the layering) feeds both the spawn and the lookup;
`ShellEnv.path()` is the searched PATH, `ShellEnv.which(exe, path)` the walk; `ShellEnvTest` (4).
`./gradlew test` **168/0**. Negative control: `runIde` launched with `~/.local/bin` stripped from
PATH (after `./gradlew --stop`) — pre-fix build spawned the VS Code 2.1.270 binary, fixed build
spawned `~/.local/share/claude/versions/2.1.283`; the user saw Opus 5 (1M) → Opus 5.5 in the
sandbox picker. Second question answered by measurement: 2.1.283's `initialize` roster has 11 rows
and 2.1.283's own TUI `/model` lists the same 11 — the panel is not inventing rows.
**Not yet released and not installed in the real PhpStorm** — the real IDE keeps its 2.1.270
CLI until it gets a build with this fix (`cd plugin && ./gradlew buildPlugin`, install the zip
from `plugin/build/distributions/`, restart). Whether that is a 0.14.1 patch or a local install
is the user's call.
CLI on this box **2.1.283** (terminal); last re-audit was at 2.1.270 (2026-09-13). Sandbox
state unknown — `pgrep -f 'permission-prompt-too[l] stdio'` + parent cmdline tells.

## Open investigations
- **Model chip says "Fable (1M)" while the menu checks NO row** (user screenshot 2026-09-05; parked).
  New inferred candidate 2026-09-27: 2.1.28x rosters have NO `[1m]` values, so a persisted
  `fable[1m]` matches nothing after a CLI update — exactly the symptom. backlog § Next up.
- **Fold-verdict report NOT reproduced** (Windows, 2026-09-04). WAITING on the user's DevTools
  diagnostic + Help→About. Do not guess-fix.
- **Title tooltips never show in the panel on Linux JCEF** (2026-09-05) — backlog § Next up.
- **The sandbox PhpStorm closed cleanly on its own TWICE on 2026-09-09** — if it recurs unattended,
  it is a real finding (gotchas § Testing).

## 2.1.283 leads for the next re-audit (seen, NOT audited — runbook re-audit procedure)
- Roster values carry no `[1m]` tag (Fable = `claude-fable-5-1`, Opus = `opus`, pinned rows
  `claude-opus-5`/`-4-8`/`-4-7`/`-4-6`, `claude-fable-5`, `claude-sonnet-4-6`): the panel's 1M
  switch (`rosterFor`/`strip1m`, `30-menus.js`) read OFF on 2.1.283; measure what `set_model` with
  a `[1m]` suffix does before touching it.
- `chipName()` parses the version out of the DESCRIPTION; named rows no longer lead with it
  ("Most capable for ambitious work") — the chip fell back to `displayName`, which happened to work.
- `initialize` keys not seen before (check against 2.1.270): `fast_mode_disabled_reason`,
  `ide_rc_auto_enable_gate`, `remote_control_available/_auto_enable/_auto_on_by_default/
  _auto_connect_default`, `session_state`, `available_output_styles`, `user_output_styles_dir`,
  `analytics_disabled`.
- `reference/claude-code-log` clone: `git pull` before the re-audit (was 2.1.261 on 2026-09-13).

## Testing — the standing setup
- `python3 tools/live_harness.py` baseline **870** (fixtures to **87**); `./gradlew test` **168**.
- Sandbox **PhpStorm 2024.2.6**; start (from `plugin/`; background tasks start in the REPO ROOT):
  `cd plugin && ./gradlew runIde -PskipVerifierIdes -PjcefDebugPort=9222
  --args="$HOME/Sites/claude-brains-testing"`. **`runIde` blocks until the IDE exits** and then
  says BUILD SUCCESSFUL (a kill reads as exit 1). Find the sandbox's CLI by command line
  (`pgrep -f 'permission-prompt-too[l] stdio'`, parent cmdline contains
  `transformed/PhpStorm-2024.2.6`) — `pgrep -x claude` misses the versioned binary (gotchas § Testing).
  Kill by pid, wait for CDP to vanish (`until ! ss -ltn | grep -q ':9222'`). Claude may start and
  kill the sandbox on its own (user, 2026-08-29). Never run the harness and a CDP injection
  concurrently. **`pkill -f`/`pgrep -f` with a pattern that also matches your own shell kills the
  shell (exit 144) — bracket one char.** A running sandbox does NOT block `test`/`buildPlugin`.
- **PATH-dependent sandbox launches need `./gradlew --stop` first** (the daemon caches env); a
  stripped-PATH launch (`PATH=$(echo "$PATH" | tr ':' '\n' | grep -v '/\.local/bin$' | paste -sd:)`)
  reproduces the desktop-launched IDE's environment (gotchas § JCEF, ShellEnv bullet).
- **Free negative control**: write the fixture while the sandbox still runs the PRE-change build,
  run it, see the discriminating asserts fail, THEN edit sources and restart. For Kotlin changes:
  `git stash -u`, launch, read, kill, `git stash pop`, `./gradlew --stop`, relaunch (2026-09-27).
  Control builds restore the WHOLE `plugin/src/main/resources/webview/`. Record the control's
  actual readings in the fixture `provenance` (fixtures 69, 85, 86).
- **Renderer-only changes can be prototyped in node first** (scratch `proto.js` wrapping
  `20-markdown.js` with stubs, 2026-09-09).
- **Background chains that wait on the CDP port can miss it**; a waiter that greps a parent's
  cmdline for `idea.system.path` never fired on 2026-09-27 — wait on a LOG LINE or on the CLI
  process by command line. A chain ending in `grep -c` reports exit 1 when the count is 0.
- **Stdio probes**: `tools/probe_stdio.py` (writes a real transcript under `~/.claude/projects/
  -tmp-…/` — delete afterwards; 14 old leftovers untouched on purpose). A bare `initialize` over
  stdio is one `printf | claude --input-format stream-json … --permission-prompt-tool stdio | grep
  -m1 '"models"'` (2026-09-27). **TUI behaviour** → pty driver, trusted dir only, clean up the
  transcript and `lastSessionId` (gotchas § Testing; conventions § Workflow).
- Real end-to-end turns over CDP: `sendTurn('<prompt>', [])`; poll `!!document.querySelector('#log
  .generating')` to `false`; `tools/cdp.py -f probe.js`; `--screenshot`. Then read the wire shape
  BY KEY (and its `"version"`) from `~/.claude/projects/-home-syncroze-Sites-claude-brains-testing/`.
- Fixture ids from page-lifetime counters (`sq1…`) must be read from the bridge tape; a
  panel-supplied slash entry (`CMD_LOCAL`) changes every fixture that COUNTS slash rows.
- **Releases**: `docs/release.md` end to end, `./gradlew test buildPlugin verifyPlugin` as ONE
  background run (~45 s warm, 8 verdict files), then the step-6 gate with the FULL notes before
  commit/tag/push (gotchas § Build). A release starts only when the user asks (conventions).

## Next steps
- [x] Executable lookup on the shell PATH — fixed, controlled, tested, committed 2026-09-27.
- [ ] Get the fix into the real PhpStorm: local zip install, or a 0.14.1 patch release (user's call;
      `verifyPlugin` on every release, no judgement call).
- [ ] Re-audit 2.1.270 → 2.1.283 (runbook), starting from the leads above; the 1M switch first.
- [ ] Check the listing's Overview still reads plugin.xml's description text (user errand).
- [ ] Renderer follow-ups, all in backlog § Housekeeping, all pre-existing and named in the 0.13.1
      release notes as known: indent-only code blocks; `*` inside inline code; mid-line fence
      placeholder leak; `~~~` fences.
- [ ] **Model chip / menu mismatch** — parked; capture the chip title id AND the persisted value vs
      the running roster when it recurs.
- [ ] **Waiting on the user**: Windows DevTools fold diagnostic + Help→About; the Windows CRLF
      splice check (checklist 3.2 fold); Windows look of the 1.29 card header path; Windows
      `./gradlew test` + VFS click check.
- [ ] Testing repo carries hand-test leftovers — the user's call whether to reset.
- [ ] SchemaStore watch (no action until it syncs past 2.1.251).

## Known gaps (deliberately left)
- **The Thinking switch is INERT on Fable** — measured 2026-08-26, "document only" by decision.
- **Do not gate UI on roster capability flags** (9.10 ➖); only `supportsFastMode` is gated.
- The picker shows the CLI's whole roster, pinned previous versions included (decision 2026-09-27);
  folding is backlog § Someday.
- Typing `/model` or `/clear` in the composer is refused; the chip / header New button are the
  only surfaces. No keyboard-only new conversation; no shortcuts on any card.
- Banner frames, task frames and withdrawn asks are live-only; replay draws the CLI's own auto-deny
  result. Decided Bash cards vanish on replay; a Bash card's reject note and edited command are
  live-only. A note typed before Accept on an ordinary card is dropped ("applies to Reject").
- Markdown: a bullet-character change does not split a list (forgiving on purpose); the offline
  highlighter colours its keyword set inside EVERY language, prose fences included (1.11, by design).
- plugin.xml "Not there yet" list unchanged: dark UI only, one conversation, no auto-context.

## Which machine — check FIRST, both are real
2026-08-26 → 2026-09-27 sessions ran on **Linux** (`/home/syncroze/Sites/claude-brains`).
Paths for both boxes in overview.md § External references. Windows still owes the CRLF splice
check and the fold diagnostic. The PATH trap is Linux/macOS-shaped (desktop-launched IDE);
Windows has no shell PATH layer (`ShellEnv` is empty there by design).

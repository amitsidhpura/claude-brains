# State

## Current focus
**2026-10-10 (twenty-third session, Linux): 11.7 — background task output + Run in background —
probed, built, verified; the user's "save / commit and push" closes the session.**
1. **11.7 built from a live probe.** Every roster row (`renderBgTasks`,
   `plugin/src/main/resources/webview/js/70-events.js`) toggles a `.bg-out` output pane: it polls
   `get_task_output{task_id}` once a second (one timer, one ask in flight per task, nothing while
   the popup is hidden), paints through `taskOutText` (reference client's rules: truncated head's
   partial first line dropped, `\r` honoured, + our ANSI strip and one trailing newline dropped),
   pinned to the tail, "Showing the end of N of output." / "No output yet." / "Output is not
   available." (error → polling ends for that task). A running FOREGROUND tool line grows a
   `.t-bg` "Run in background" button when `task_started{is_backgrounded:false}` arrives
   (`offerBackground`), sending `background_tasks{tool_use_id}`; `__backgrounded` true removes it,
   false / errors become a `.note` on the same element, the disabled error sets `bgRefused` for
   the CLI process. Kotlin: `ClaudeCli.getTaskOutput` / `backgroundTask` (callback), service
   wrappers, ChatPanel bridge kinds `taskOutput` / `backgroundTask` → `__taskOutput` /
   `__backgrounded` frames. Row evidence `docs/feature-checklist.md` 11.7 (✅, §11 ✅); wire facts
   `docs/ide-mcp-protocol.md` § 9c + § 12 (shell task order, `run_id`, the wake turn);
   `docs/limits.md` (9-line pane).
   **MEASURED 2026-10-10 on 2.1.296** (stdio, scratch `CLAUDE_CONFIG_DIR`, haiku, mode default):
   both control answers, the foreground `task_started` ~3 s in, the backgrounded tool_result
   text, the ended-task output tail. **UNMEASURED:** the disabled answer ("Background tasks are
   disabled in this session.", `strings`), a Monitor task, whether our `interrupt` leaves
   background agents running (1.7).
2. Evidence: fixture 95 (45 asserts; negative control on the pre-change sandbox: first assert
   failed, step aborted; the first run on the changed build caught the row click closing the
   popup — fixed, gotchas § Webview), full harness **1025/0** (a first full run had ONE
   environmental fail, fixture 40, the sandbox's own `__project` frame landing mid-fixture a
   minute after a fresh IDE — passed alone and on the warm rerun), `./gradlew test` **169**.
   Real-panel render over CDP (gallery): offer in `--blue`, note `--muted`, pane + chevrons as
   designed. **Not hand-tested live** against a real running shell — the probe proved the wire,
   fixture 95 the renderer; the two have not met in the sandbox panel.

**Two [DECIDE] rows remain: 9.9, 15.4** (At a glance: 98 ✅ · 1 ⬜ · 46 ➖).

**Pending in the real PhpStorm: NINE fixes** (PATH lookup 2026-09-27, Default chip + thinking trim
2026-10-08, bold-wrapping-italic + spaced @-mentions 2026-10-10, 1.30 + 1.31, 4.10, now 11.7).
The `plugin/build/distributions/claude-brains-0.14.0.zip` built 11:14 PREDATES all of 1.30 → 11.7 —
rebuild (`buildPlugin`) for a disk install, or a 0.14.1 release (user's call; `verifyPlugin` every
release).

**Cleanup owed, the user's (the session's permission classifier refused both, 2026-10-10 twenty-first):**
delete the three audit-probe transcripts `982ad0ee-aeab-4882-b005-5e34069eff71`,
`60d9bd11-f732-43af-854f-de084163b0ca`, `830d916b-f6a0-49b5-a4c4-793bba06622e` under
`~/.claude/projects/-home-syncroze-Sites-claude-brains-testing/` and set that project's
`lastSessionId` in `~/.claude.json` back to `8c92f59b-0b22-4152-95b9-1c6d4260ecab`. (The 11.7
probe wrote NOTHING there: its transcript lived under the scratch config dir, deleted with it.)
Also: `reference/anthropic-claude-code/` is still the 2.1.270 extraction — rsync it to 2.1.296
(runbook step 2) before the next audit.
The sandbox PhpStorm was left RUNNING on the final 11.7 build, panel cleared (CDP 9222; dies with
the Claude Code process that launched it — a `load` that finds `:9222` closed is normal).

## Open investigations
- **Where the user's Fable default comes from** — still unknown. A bare `initialize` on 2.1.296
  (this Max account) resolved `default` → `claude-sonnet-5-5`; the 2.1.270 binary said
  `claude-opus-5[1m]`. The panel shows what the CLI serves; it does not change what runs.
- **Model chip "Fable (1M)" with no ✓ row** (parked) — the 9.9 measurement (tagless roster, tag
  accepted and echoed) supports the "persisted `fable[1m]` matches no tagless row" candidate.
- **Roster per PROCESS**: the 2.1.293 terminal probe listed 11 rows (no `fable` alias); the 2.1.296
  terminal probe listed 13 WITH it — a flags-fetch timing lead, unproven.
- **A completed background shell wakes the model** (measured 2026-10-10): an extra turn with a
  `result` carrying an `origin` key answers the completion. The panel's onResult already treats a
  `local_bash` result as the true end; whether the wake turn draws anything odd is unchecked.
- **Fold-verdict report NOT reproduced** (Windows, 2026-09-04) — waiting on the user's diagnostic.
- **Title tooltips never show on Linux JCEF** — backlog § Next up.

## Testing — the standing setup
- `python3 tools/live_harness.py` baseline **1025** (fixtures to **95**); `./gradlew test` **169**.
- Sandbox PhpStorm 2024.2.6: `cd plugin && ./gradlew runIde -PskipVerifierIdes -PjcefDebugPort=9222
  --args="$HOME/Sites/claude-brains-testing"` (background, from the repo root). `runIde` blocks
  until the IDE exits. Stop it with `pgrep -f 'transformed/PhpStorm-2024.2.[6]' | xargs -r kill`
  (never `pgrep -x claude`), wait for `:9222` to vanish with `ss -ltn` in a bounded `until` loop
  (the bash `/dev/tcp` idiom is NOT a port check under this zsh — it always says closed). Never
  run the harness and a CDP injection concurrently. A running sandbox does NOT block
  `test`/`buildPlugin`/`compileKotlin`.
- **Free negative control**: launch the sandbox BEFORE touching `plugin/` (it packages the tree
  at launch; later edits don't reach it), write the fixture, run it, then edit — this session's
  order again. Pin list LENGTHS in button asserts; a `JSON.stringify(count)` is a STRING and fails
  `atLeast` (gotchas § Testing).
- **A fresh IDE needs a minute before the full harness**: its own `__project` frame can land
  mid-fixture (fixture 40's path negative control fails on it). Rerun alone / rerun warm.
- **Shell on this box** (gotchas § Testing): `grep` is `ugrep` (→ `/usr/bin/grep`), `ls` is `eza`,
  `comm` needs `LC_ALL=C sort -u`; Bash output past ~30 KB is persisted to a file with a 2 KB
  preview; bound minified-bundle greps or use a python `str.find` loop (instant).
- **Stdio probes that run a turn: use a scratch `CLAUDE_CONFIG_DIR`** (credentials copy + a
  `.claude.json` whose `projects` holds only the scratch cwd with `hasTrustDialogAccepted:true` +
  settings.json) so the transcript lands in the scratch dir and the real `~/.claude` stays clean —
  the 11.7 probe's recipe (`python3 -I`, strip every other `CLAUDE*` env var, `--model haiku`).
  Delete the dir afterwards. Without it the cleanup is the user's (gotchas § Testing).
- **The harness is NOT side-effect free on the sandbox CLI** (fixture 55 `setModel`) — start a
  fresh CLI (`bridge({kind:'new'})`) before a live end-to-end. Live turns over CDP:
  `sendTurn('<prompt>', [])` SENDS a real prompt; `addUserMessage(text, [], ts)` only draws.
- **Real-panel render for review**: `window.__gallery()` + `tg('bgMenu')` over `tools/cdp.py`,
  then `--screenshot`; measure computed colours against the `--blue`/`--muted` tokens.
- **Releases**: `docs/release.md` end to end, `./gradlew test buildPlugin verifyPlugin` as ONE
  background run, the step-6 gate with the FULL notes before commit/tag/push (gotchas § Build).
  A release starts only when the user asks (conventions).

## Next steps
- [x] 11.7 background task output + Run in background — built 2026-10-10 (fixture 95).
- [ ] **Hand-test 11.7 live in the sandbox**: a `run_in_background` shell → open the roster row's
      pane, watch it grow and end; a foreground `sleep 30` → the button appears ~3 s in → click →
      the roster lists it and the turn ends. Then check the wake turn's rendering.
- [ ] **Decide the two open rows**: 9.9 (retire / hide / keep the inert 1M switch), 15.4 (export +
      copy response, [XS]).
- [ ] Get all NINE fixes into the real PhpStorm — rebuild the zip or cut 0.14.1 (user's call).
- [ ] Cleanup owed (above): three probe transcripts + `lastSessionId`; rsync the extraction to
      2.1.296.
- [ ] When a real `auto_mode_server_fallback` frame ever lands (`window.__dialogSeen`, console
      warning), compare it with fixture 94's payloads and the 4.10 fold; fix the card if they differ.
- [ ] Check the listing's Overview still reads plugin.xml's description text (user errand).
- [ ] Leads from the audit, probe-first (backlog § Next up): on-demand diagnostics (2.3), the
      autosave 10-minute stall (2.10), `session_title_changed` (8.3), Ultracode toggle and
      `effortLevel` persistence (9.2), `claude_code_version` for the out-of-date hint,
      `turn_preempted` / `queued_turn_count` (1.9).
- [ ] Renderer follow-ups in backlog § Housekeeping (indent-only code blocks; `*` inside inline
      code; mid-line fence placeholder leak; `~~~` fences).
- [ ] **Model chip / menu mismatch** — parked; capture chip title + persisted value vs roster.
- [ ] **Waiting on the user**: Windows DevTools fold diagnostic + Help→About; Windows CRLF splice
      check; Windows look of the 1.29 card header; Windows `./gradlew test` + VFS click check.
- [ ] Testing repo hand-test leftovers (`hand test/…`, `plain-control.txt`, transcripts) — the
      user's call whether to reset.
- [ ] SchemaStore watch (no action until it syncs past 2.1.251).

## Known gaps (deliberately left)
- **11.7's pane lives with its roster row**: when the task ends the roster drops it and the pane
  goes (the timeline's task line says how it ended; the model usually reads the `output_file`).
  A sub-agent's row answers "Output is not available." on the first ask. Our note wording is
  shorter than the reference client's. Live-only, like every task frame.
- **4.10 is live-only and schema-built**: the card has never met a real frame. Only
  `auto_mode_server_fallback` is declared — the Fable overage consent (9.7) stays deferred.
- **Timestamps**: day labels are relative ("Today"/"Yesterday"), so a page left open past
  midnight reads stale until a clear/resume. Tool loops later in a turn get no stamp.
- **Before the first turn nothing on the wire names a settings `model` override** (9.1 gap).
- **The Thinking switch is INERT on Fable** — "document only". **Do not gate UI on roster
  capability flags** (9.10); only `supportsFastMode` is gated — disabled on Default by design.
- **The 1M switch is inert on 2.1.296** (9.9 [DECIDE]) — reads OFF on every row.
- The picker shows the CLI's whole roster, pinned previous versions included (eight at 2.1.296).
- Typing `/model` or `/clear` is refused; the chip / New button are the surfaces. No keyboard-only
  new conversation; no shortcuts on any card.
- Banner frames, task frames, withdrawn asks and host dialogs are live-only; replay draws the
  CLI's own auto-deny result. A note typed before Accept on an ordinary card is dropped.
- Markdown: a bullet-character change does not split a list; the offline highlighter colours its
  keyword set inside EVERY language (1.11).
- plugin.xml "Not there yet" list unchanged: dark UI only, one conversation, no auto-context.

## Which machine — check FIRST, both are real
2026-08-26 → 2026-10-10 sessions ran on **Linux** (`/home/syncroze/Sites/claude-brains`).
Paths for both boxes in overview.md § External references. Windows still owes the CRLF splice
check and the fold diagnostic. The PATH trap is Linux/macOS-shaped; Windows has no shell PATH layer.

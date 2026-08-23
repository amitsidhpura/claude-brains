# State

## Current focus
**2026-08-24 (twelfth session): phantom Enter traced to a FIXED upstream bug (sandbox bumped),
then a real replay bug found and fixed — a pending plan card claiming verdicts it never had.
NOT released.**
- **Phantom Enter CLOSED.** Root cause is **IJPL-161111** "JCEF: the keyboard on linux is
  broken" (dups JBR-7536/7547), fixed upstream in **2024.2.2 / 2024.3**; the sandbox pinned
  2024.2.0 — pre-fix. Bumped `plugin/build.gradle.kts` to `phpstorm("2024.2.6")` (build
  242.26775.23, JBR 21.0.5-b509.30). User re-tested many times: never reproduced. The old
  gotchas attribution (JBR-5348/5115) was stale — those are 2023 fixes already in our JBR.
- **Pending-plan replay honesty (user report, screenshots).** A plan card pending at reload
  replayed "✓ Approved" once, then "✗ Kept planning" quoting the CLI's own error. Three fixes:
  (1) `SessionStore` emits `undecided` for a plan tool_use with NO tool_result → replay draws
  "◌ Interrupted — no decision recorded" (`.und-t`, neutral, 55-replay.js); (2) the CLI's
  auto-deny text (`RenderLimits.PERMISSION_ABORT_PREFIX`) is filtered from `planFeedback` the
  way REJECT_MESSAGE is; (3) `ClaudeSessionService.stopForReplay()` — refresh/resume kill the
  CLI and wait BEFORE reading the transcript, so its dying flush lands in the same reload.
- **Reload speed regression, caught by the user and fixed the same session:** the wait made
  EVERY reload slow. Now conditional on `pendingPermissions.isNotEmpty()` — instant for
  ordinary reloads, still correct for the pending-card case. User confirmed reload "perfect".
- **Proof:** gradle test **115** · live harness **411** (new fixture 54; control run 406/5 —
  every new assert seen failing first) · `probe` on the user's real session parses honestly.

## Unreleased on `main` (a release starts only when the user asks)
0.8.0 (`dce3600`) remains shipped. Since: VFS open-path fix · compaction replay-order fix ·
2.1.241 checklist re-audit · anchored plan comments (5.6) · pending-plan replay honesty.
**Next release is a MINOR bump (feature 5.6).** Release blockers, in order:
`version = "0.8.0"` → 0.9.0 in `plugin/build.gradle.kts`; add a **0.9.0 `changeNotesHtml`
entry** (the `buildPlugin` gate hard-fails without it); flip `docs/feature-checklist.md:162`
5.6 off "unreleased"; then follow `docs/release.md`.

## Open work — ids verified against `docs/feature-checklist.md`
- 🟥 high rows left: **3.5** tweak-travel [LG] · **8.7** rewind/fork [LG] · **8.11** side
  question [MD, probe pre-paid] · **8.14** reloaded-webview log replay [LG] · **9.4** fast-mode
  toggle [SM, mechanism measured] · **11.3** kill-background-process [MD, `stop_task` accepted].
- **Nine [DECIDE] rows** await the user: 8.7, 8.10, 8.11, 9.4, 9.5, 12.3, 12.6, 13.2, 14.2.
- `/clear` grew a `[name]` hint in 2.1.241 → a menu pick INSERTS instead of running and the
  native branch drops the name — decision recorded on checklist 7.6.

## Testing — the standing setup
- `python3 tools/live_harness.py` baseline **411** (fixtures to 54); `./gradlew test` **115**.
- Webview iteration loop (fixture-first control → apply → runIde restart → verify BY CONTENT):
  `runbook.md`. jb.gg timeout → `-PskipVerifierIdes` (never release under it).
- **Sandbox is now PhpStorm 2024.2.6**; its dir is `build/idea-sandbox/PS-2024.2.6/`.
- **Restarting the sandbox: `pkill -f 'run[I]de'` only kills gradle** — the IDE orphan then
  swallows relaunches (hit again 2026-08-24: `runIde` returns BUILD SUCCESSFUL in ~6s with no
  window). Kill it by pid: `pgrep -f 'idea.system.pat[h]'`.
- Sandbox CDP pins to 9222 whatever the flag says; verify the build BY CONTENT before trusting
  a run. Control builds restore the WHOLE `plugin/src/main/resources/webview/` directory.
- Headless CLI probes run in `~/Sites/claude-brains-testing`.

## Next steps
- [ ] Release 0.9.0 when asked — blockers listed above under "Unreleased".
- [ ] Get the user's yes / later / no on the **nine [DECIDE]** rows in
      `docs/feature-checklist.md`.
- [ ] Decide `/clear [name]`: pass the name through as the new conversation's title, or pin
      pick-runs for native commands (checklist 7.6).
- [ ] Sync `docs/slash-commands.md` to 2.1.241 (still says 2.1.233; misses the `/clear` hint).
- [ ] Backlog order (`backlog.md` § Next up): plan-card keyboard shortcuts → reloaded-webview
      log replay (**8.14**) → kill-background-process (**11.3**) → tweak-travel (**3.5**).
      **9.4** fast-mode toggle [SM] is the cheapest 🟥 left.
- [ ] Sync the **Marketplace web description** (hand-edited; uploads don't refresh it).
- [ ] **Windows errand:** `./gradlew test` once (CRLF splice path never run there) + click a
      `Read` path for a fresh file to confirm the VFS fix on the box that reported it.
- [ ] Consolidation pass: `decisions.md` (~845) and `gotchas.md` (~860) far over the ~100-line
      target — promote, then cut. Flagged 2026-08-17, still pending and growing.

## Known gaps (deliberately left)
- 5.6: keyboard-only selection can't open the comment pill; interrupted-plan boilerplate quote
  (backlog). VS Code's editor-tab plan preview not replicated — the card body is our preview.
- **Confirm-card path wrapping DECLINED by the user 2026-08-24** — the card header path still
  wraps to a second line AND middle-ellipsises at narrow widths. Plan was written and rejected;
  do not re-propose unprompted.
- Compaction live-vs-resume divergences DECIDED (renderer-parity Audit 2). Plan-card keyboard
  shortcuts deferred 2026-08-16. `DiffReview.open` snapshot-only lookup parked.
- Paths in free prose not shortened; `/batch` verified at N=2 only; sub-agent WORK outcome not
  surfaced; `prefers-reduced-motion` OS propagation unverified.

## Which machine — check FIRST, both are real
This session ran on **Linux** (`/home/syncroze/Sites/claude-brains`). Paths for both boxes in
overview.md § External references. The Windows box still owes the CRLF splice check.

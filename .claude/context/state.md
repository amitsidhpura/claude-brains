# State

## Current focus
**2026-08-23 (eleventh session, three arcs): the 2.1.241 re-audit, then checklist 5.6 —
anchored plan comments — built and polished across ten rounds, then a phantom-Enter
investigation that ended in a full revert. All committed to `main`, NOT released.**
- **5.6 (`c0df900` + `92363ac`), the shipped shape:** select text in the plan card body →
  floating Comment pill → anchored rows between `.plan-sep` and `.plan-fb`. Deny sends the VS
  Code client's byte-exact format (`PLAN_DENY_PREFIX` / `PLAN_COMMENTS_HEADER` /
  `[Re: "anchor"] note`, constants in `RenderLimits.kt` → `window.LIMITS`); approve rides
  `PLAN_NOTES_MARKER`. Decided AND replayed cards keep their anchor highlights via ONE shared
  `highlightAnchors` (50-blocks.js); an ambiguous anchor carries `(2nd occurrence)` on the wire
  so both surfaces land on the text the user selected. USER'S CALL: the full approve surface
  stays available with comments pending (VS Code collapses to keep-planning-only).
- **Known, UNFIXED, deliberately reverted:** an intermittent JCEF Enter storm can commit a
  comment after one character (gotchas § Sandbox / JCEF; backlog for the options). Three guards
  were tried; the cadence guard broke real Enter and the user ordered everything removed. The
  composer is plain Enter-commits / Escape-cancels again, and the ⏎ button always works.
- **Proof:** gradle test **114** · live harness **406** (fixture 53: 45 asserts, eleven control
  runs) · `probe` on the real claude-vscode transcript replays a comment row. Iteration loop in
  `runbook.md`.

## Unreleased on `main` (a release starts only when the user asks)
0.8.0 (`dce3600`) remains shipped. Since: VFS open-path fix · compaction replay-order fix ·
2.1.241 checklist re-audit (`b154a75`) · anchored plan comments (`c0df900`, `92363ac`).
Next release is a MINOR bump (feature).

## Open work — ids verified against `docs/feature-checklist.md`
- 🟥 high rows left: **3.5** tweak-travel [LG] · **8.7** rewind/fork [LG] · **8.11** side
  question [MD, probe pre-paid] · **8.14** reloaded-webview log replay [LG] · **9.4** fast-mode
  toggle [SM, mechanism measured] · **11.3** kill-background-process [MD, `stop_task` accepted].
- **Nine [DECIDE] rows** await the user: 8.7, 8.10, 8.11, 9.4, 9.5, 12.3, 12.6, 13.2, 14.2.
- `/clear` grew a `[name]` hint in 2.1.241 → a menu pick INSERTS instead of running and the
  native branch drops the name — decision recorded on checklist 7.6.

## Testing — the standing setup
- `python3 tools/live_harness.py` baseline **406** (fixtures to 53); `./gradlew test` **114**.
- Webview iteration loop (fixture-first control → apply → runIde restart → verify BY CONTENT):
  `runbook.md`. jb.gg timeout → `-PskipVerifierIdes` (never release under it).
- **Restarting the sandbox: `pkill -f 'run[I]de'` only kills gradle** — the IDE orphan then
  swallows relaunches (gotchas). Kill it by pid: `pgrep -f 'idea.system.pat[h]'`.
- Sandbox CDP pins to 9222 whatever the flag says; verify the build BY CONTENT before trusting
  a run. Control builds restore the WHOLE `plugin/src/main/resources/webview/` directory.
- Headless CLI probes run in `~/Sites/claude-brains-testing`.

## Next steps
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
- [ ] Consolidation pass: `decisions.md` (~845) and `gotchas.md` (~840) far over the ~100-line
      target — promote, then cut. Flagged 2026-08-17, still pending and growing.

## Known gaps (deliberately left)
- 5.6: the JCEF phantom commit (above); keyboard-only selection can't open the pill;
  interrupted-plan boilerplate quote (backlog). VS Code's editor-tab plan preview not
  replicated — the card body is our preview.
- Compaction live-vs-resume divergences DECIDED (renderer-parity Audit 2). Plan-card keyboard
  shortcuts deferred 2026-08-16. `DiffReview.open` snapshot-only lookup parked.
- Paths in free prose not shortened; `/batch` verified at N=2 only; sub-agent WORK outcome not
  surfaced; `prefers-reduced-motion` OS propagation unverified.

## Which machine — check FIRST, both are real
This session ran on **Linux** (`/home/syncroze/Sites/claude-brains`). Paths for both boxes in
overview.md § External references. The Windows box still owes the CRLF splice check.

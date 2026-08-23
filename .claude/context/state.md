# State

## Current focus
**2026-08-23 (eleventh session, two arcs): the 2.1.241 re-audit (committed a.m.), then
checklist 5.6 — anchored plan comments — built, polished through seven user-driven rounds, and
committed (`c0df900`). All on `main`, NOT released.**
- **5.6 in one paragraph:** select text in the plan card's body → floating Comment pill →
  anchored rows between `.plan-sep` and `.plan-fb`. Deny sends the VS Code client's byte-exact
  format (`PLAN_DENY_PREFIX` / `PLAN_COMMENTS_HEADER` / `[Re: "anchor"] note` lines — constants
  in `RenderLimits.kt`, spliced as `window.LIMITS`); approve rides `PLAN_NOTES_MARKER`;
  `RenderLimits.parsePlanComments` splits them back for replay; `planCommentRows` (50-blocks.js)
  is the one row builder live + replay. USER'S CALL, diverging from the reference client: the
  full approve surface stays available with comments pending. Detail in decisions/journal
  2026-08-23; comment machinery in `plugin/src/main/resources/webview/js/85-cards.js`
  (`renderComments`, `finishComments`, pill/mark handling).
- **Proof:** gradle test **113** · live harness **398** (fixture 53: 37 asserts, EIGHT control
  runs — every assert seen failing pre-change) · `probe` on the real claude-vscode transcript
  replays the comment row. The loop used per polish round is in `runbook.md`.
- The user drove every round from their REAL IDE or the mockup; the mockup now carries a 9-state
  catalog of the feature (`design/mockup.html` § PLAN COMMENTS).

## Unreleased on `main` (a release starts only when the user asks)
0.8.0 (`dce3600`) remains shipped. Since then: VFS open-path fix (`findVFileOnDisk`) ·
compaction replay-order fix (`DisplacedAnchor`) · the 2.1.241 checklist re-audit (`b154a75`) ·
**anchored plan comments** (`c0df900`). The next release is a MINOR bump (feature).

## Open work — ids re-verified against `docs/feature-checklist.md`
- 🟥 high rows left: **3.5** tweak-travel [LG] · **8.7** rewind/fork [LG] · **8.11** side
  question [MD, probe pre-paid 2026-08-23] · **8.14** reloaded-webview log replay [LG] ·
  **9.4** fast-mode toggle [SM, mechanism measured] · **11.3** kill-background-process [MD,
  `stop_task` accepted over stdio].
- **Nine [DECIDE] rows** await the user: 8.7, 8.10, 8.11, 9.4, 9.5, 12.3, 12.6, 13.2, 14.2.
- `/clear` grew a `[name]` hint in 2.1.241 → a menu pick now INSERTS instead of running and the
  native branch drops the name — decision recorded on checklist 7.6.

## Testing — the standing setup
- Live harness: `python3 tools/live_harness.py`, baseline **398** (fixtures to 53); `./gradlew
  test` **113**. Compile clean. Headless CLI probes run in `~/Sites/claude-brains-testing`.
- Webview-iteration loop (fixture-first control → apply → runIde restart → verify BY CONTENT):
  `runbook.md`. jb.gg timeout → `-PskipVerifierIdes` (never release under it). Sandbox CDP
  pins to 9222 regardless of the flag; `pkill -f` self-match trap in gotchas.
- Control builds restore the WHOLE `plugin/src/main/resources/webview/` directory.
- `./gradlew probe --args="<proj> <sessionId> --json"` + `cmp` = SessionStore refactor safety.

## Next steps
- [ ] Get the user's yes / later / no on the **nine [DECIDE]** rows (8.7, 8.10, 8.11, 9.4, 9.5,
      12.3, 12.6, 13.2, 14.2) in `docs/feature-checklist.md`.
- [ ] Decide `/clear [name]`: pass the name through as the new conversation's title, or pin the
      old pick-runs behavior for native commands (checklist 7.6).
- [ ] Sync `docs/slash-commands.md` to 2.1.241 (still says 2.1.233; misses the `/clear` hint).
- [ ] Backlog order (`backlog.md` § Next up): plan-card keyboard shortcuts → reloaded-webview
      log replay (**8.14**) → kill-background-process (**11.3**, unblocked) → tweak-travel
      (**3.5**). **9.4** fast-mode toggle [SM] is the cheapest 🟥 left.
- [ ] Sync the **Marketplace web description** (hand-edited, uploads don't refresh it) to
      plugin.xml's slash-commands wording — the user edits it at plugins.jetbrains.com.
- [ ] **Windows errand:** `./gradlew test` once (CRLF splice path never run there) + click a
      `Read` path for a fresh file to confirm the VFS fix on the box that reported it.
- [ ] Consolidation pass: `decisions.md` (845) and `gotchas.md` (822) far over the ~100-line
      target — promote, then cut. Flagged 2026-08-17, still pending, growing.

## Known gaps (deliberately left)
- 5.6: keyboard-only selection can't trigger the pill; interrupted-plan boilerplate quote
  (backlog § 5.6 leftovers). VS Code's editor-tab plan preview deliberately not replicated.
- Compaction live-vs-resume divergences DECIDED (renderer-parity Audit 2). Plan-card keyboard
  shortcuts deferred by the user 2026-08-16. `DiffReview.open` snapshot-only lookup parked.
- Paths in free prose not shortened; `/batch` verified at N=2 only; sub-agent WORK outcome not
  surfaced; `prefers-reduced-motion` OS propagation unverified.

## Which machine — check FIRST, both are real
This session ran on **Linux** (`/home/syncroze/Sites/claude-brains`). Paths for both boxes in
overview.md § External references. The Windows box still owes the CRLF splice check.

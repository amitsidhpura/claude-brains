# State

## Current focus
**2026-08-21 (tenth session): compaction replay order fixed and generalized. Committed to
`main`, NOT released — 0.8.0 remains the shipped version; this and the VFS fix ride the next
release.**
- **The bug (user screenshots, live vs resumed):** replay drew the "Conversation compacted"
  marker ABOVE the `/compact` bubble that caused it. The CLI writes boundary + summary records at
  compaction END, physically BEFORE the /compact command records — which keep their typed-at
  timestamps (measured: boundary 13:20:19 at file pos 295, its command 13:18:11 at 298, real
  shopify transcript). Replay renders file order, so it inverted; live (arrival order) was right.
- **The fix, then the generalization (user asked "common solution?"):** `DisplacedAnchor` in
  `plugin/src/main/kotlin/io/github/amitsidhpura/claudebrains/session/SessionStore.kt` — arm an
  index+timestamp at the anchor record; a later-in-file record whose ts proves it earlier inserts
  at the anchor; eviction shifts/forgets it. Two instances: `apiErrAnchor` (the pre-existing
  2026-08-09 retry-storm reorder, refactored onto it) and `compactAnchor` (new;
  `clearOnAppend=true` — disarms once a bubble at/past the boundary appends). A global timestamp
  sort was rejected (decisions.md): ts-less records, per-block same-ts messages, the streaming
  eviction window, and "timestamps always win" is an unmeasured premise.
- **Proof:** real-session probe pre/post fix shows the inversion then the correction; refactor is
  byte-identical on `probe --json` (cmp clean); two order tests added (compact + late-flushed
  retries, the latter closing a gap — the old retry test pinned spellings only); negative control
  RUN by neutering the shared guard → exactly the 2 order tests fail. **gradle test 109** (was 107).
- Accepted divergences recorded in `docs/renderer-parity.md` Audit 2 (2026-08-21 entry): summary
  box resume-only, compaction-turn footer live-only. User confirmed the fix in their real IDE.
- Also this session: `-PskipVerifierIdes` needed again (jb.gg/teamcity config-time timeout,
  transient — host answered fine minutes later).

## Earlier 2026-08-21 (ninth session), also unreleased on main
- "File not found" on an existing path: `findVFile` reads the VFS snapshot, never disk. Fix
  `findVFileOnDisk` (Vfs.kt) on exactly two callers — `ClaudeSessionService.openFile` and
  bridge `IdeTools.openFile`; `readLocked` callers must NOT refresh (deadlock). Proven against
  the user's live PhpStorm + sandbox three-call discriminating run. Detail in decisions/gotchas.

## Open work — ids re-verified against `docs/feature-checklist.md` on 2026-08-19
- 🟥 high rows left: **3.5** tweak-travel [LG] · **8.7** rewind/fork [LG, undecided since
  2026-07-30] · **8.11** side question [MD, NEW] · **8.14** reloaded-webview **log** replay [LG]
  (roster half shipped as 7.10) · **9.4** fast-mode toggle [SM] · **11.3** kill-background-process [MD].
- **Nine [DECIDE] rows** await the user: 8.7, 8.10, 8.11, 9.4, 9.5, 12.3, 12.6, 13.2, 14.2.
- **Unreleased on `main`:** the VFS open-path fix + the compaction replay-order fix/refactor.
  **A release starts only when the user asks.**

**Releases so far — all Approved on the Marketplace the same day** (detail in `journal.md`;
process in `docs/release.md`): 0.8.0 (2026-08-19, `dce3600`), 0.7.2 (2026-08-17, `40bc060`),
0.7.1 (2026-08-16, `91a6ba5`), 0.7.0 (2026-08-16, `59d94fc`). Manual-test register: 0 open / 25
resolved.

## Testing — the standing setup
- Live harness: `python3 tools/live_harness.py`, baseline **361** (fixtures to 52); `./gradlew
  test` **109** (was 107; +2 replay-order tests 2026-08-21). Compile clean.
- If gradle fails with a bare "Connection timed out" at configuration: the jb.gg/teamcity
  verifier-list fetch (gotchas) — retry with `-PskipVerifierIdes`; never release under it.
- Sandbox debug port: the sandbox's hand-set Registry value keeps CDP on **9222** even when
  `-PjcefDebugPort=9223` is passed — verify which panel a port serves BY CONTENT, never assume.
- The live panel loads webview JS from the built jar; control builds restore the WHOLE
  `plugin/src/main/resources/webview/` directory (conventions.md).
- `./gradlew probe --args="<proj> <sessionId> --json"` + `cmp` is the cheap refactor-safety
  check for SessionStore: byte-identical output on a real session.
- Probe scripts live in session scratchpads (not committed); always run under `timeout N`.

## Next steps
Done items live in `journal.md`; this list is only what is still open.
- [ ] Get the user's yes / later / no on the **nine [DECIDE]** rows (8.7, 8.10, 8.11, 9.4, 9.5,
      12.3, 12.6, 13.2, 14.2) in `docs/feature-checklist.md`.
- [ ] Backlog order (`backlog.md` § Next up): plan-card keyboard shortcuts (Enter / Shift+Tab) →
      reloaded-webview **log** replay (**8.14**) → kill-background-process (**11.3**) →
      tweak-travel (**3.5**). **9.4** fast-mode toggle [SM] is the cheapest 🟥 left.
- [ ] Sync the **Marketplace web description** (hand-edited Markdown, NOT refreshed by uploads)
      to plugin.xml's new slash-commands wording — the user edits it at plugins.jetbrains.com.
- [ ] **Windows errand, two items in one trip:** run `./gradlew test` once (CRLF splice path
      never run there), and click a `Read` path for a freshly-created file to confirm the VFS fix
      on the box that reported it.
- [ ] Consolidation pass on the context files: `decisions.md` and `gotchas.md` are far over the
      ~100-line target — promote, then cut. Flagged 2026-08-17, still pending.

## Known gaps (deliberately left)
- Compaction live-vs-resume divergences are DECIDED, not drift (renderer-parity Audit 2,
  2026-08-21): summary box resume-only, turn footer live-only.
- Plan-card keyboard shortcuts (Enter = keep planning, Shift+Tab = approve with feedback)
  deferred by the user 2026-08-16; handlers slot into the same `done()` paths (backlog).
- Paths inside free prose still not shortened; `/batch` fan-out verified at N=2 only;
  queue-path `.q-row` sweep still inconclusive.
- Sub-agent WORK outcome not surfaced; no kill-background-process from the panel;
  `prefers-reduced-motion` unverified against the real OS setting.
- `DiffReview.open` still resolves its left pane through snapshot-only `findVFile` (parked,
  backlog); Marketplace updates may prompt an IDE restart (accepted 2026-08-19).

## Which machine — check FIRST, both are real
This session (2026-08-21) ran on **Linux** (`/home/syncroze/Sites/claude-brains`). Paths for both
boxes in overview.md § External references. Slash-menu fixtures + probe scratchpads + the
verifier's cached IDE ladder exist on Linux only. The Windows box still owes the CRLF splice
check above.

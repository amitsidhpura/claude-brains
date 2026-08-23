# State

## Current focus
**2026-08-23 (eleventh session): `docs/feature-checklist.md` re-audited 2.1.233 → 2.1.241 and
committed. No new feature surfaces on either side — what changed is measured wire fact.**
- Method: `vscode/` re-extracted at 2.1.241; the 2.1.233 vsix re-downloaded from the marketplace
  as the baseline (carries the native CLI binary); contributions + `case"…"` vocab + typed
  control subtypes diffed; then LIVE stdio probes. Full procedure now in `runbook.md`.
- Probes measured (all accepted over stdio, 2.1.241): `stop_task {task_id}` (11.3 unblocked —
  relay whitelist added it at 2.1.238), `side_question {question}` → real model reply (8.11),
  `apply_flag_settings {effortLevel|fastMode}` — effort takes effect, fastMode clears the
  `sdk_opt_in_required` gate (9.2 watch-item resolved, 9.4 mechanism known),
  `set_max_thinking_tokens` (9.5), `rename_session`, `get_settings`, `list_models`,
  `get_context_usage`. `rewind_files {user_message_id, dry_run}` schema recorded, NOT probed
  (mutates files). `initialize` now also reports `current_permission_mode`, `session_state`,
  `fast_mode_disabled_reason`.
- **Behavior drift to decide: `/clear` grew a `[name]` hint in 2.1.241** — a menu pick now
  INSERTS `/clear ` instead of running (the `cmdTakesArg` rule), and the panel's native branch
  drops a typed name (the TUI uses it to name the fresh session). Options in checklist 7.6.

## Unreleased on `main` (a release starts only when the user asks)
Both 2026-08-21 fixes — the VFS open-path fix (`findVFileOnDisk`, Vfs.kt; two callers only) and
the compaction replay-order fix (`DisplacedAnchor` in SessionStore.kt; global ts-sort rejected,
see decisions 2026-08-21) — plus the 2026-08-23 checklist re-audit. 0.8.0 remains shipped.
Compaction live-vs-resume divergences are DECIDED (renderer-parity Audit 2): summary box
resume-only, turn footer live-only.

## Open work — ids re-verified against `docs/feature-checklist.md` on 2026-08-19
- 🟥 high rows left: **3.5** tweak-travel [LG] · **8.7** rewind/fork [LG, undecided since
  2026-07-30] · **8.11** side question [MD, probe DONE 2026-08-23] · **8.14** reloaded-webview
  **log** replay [LG] (roster half shipped as 7.10) · **9.4** fast-mode toggle [SM, mechanism
  measured] · **11.3** kill-background-process [MD, protocol check DONE — `stop_task`].
- **Nine [DECIDE] rows** await the user: 8.7, 8.10, 8.11, 9.4, 9.5, 12.3, 12.6, 13.2, 14.2 —
  8.11/9.4/9.5/11.3-adjacent probes are now pre-paid (2026-08-23).

**Releases so far — all Approved on the Marketplace the same day** (detail in `journal.md`;
process in `docs/release.md`): 0.8.0 (2026-08-19, `dce3600`), 0.7.2 (2026-08-17, `40bc060`),
0.7.1 (2026-08-16, `91a6ba5`), 0.7.0 (2026-08-16, `59d94fc`). Manual-test register: 0 open / 25
resolved.

## Testing — the standing setup
- Live harness: `python3 tools/live_harness.py`, baseline **361** (fixtures to 52); `./gradlew
  test` **109**. Compile clean.
- If gradle fails with a bare "Connection timed out" at configuration: the jb.gg/teamcity
  verifier-list fetch (gotchas) — retry with `-PskipVerifierIdes`; never release under it.
- Sandbox debug port: the sandbox's hand-set Registry value keeps CDP on **9222** even when
  `-PjcefDebugPort=9223` is passed — verify which panel a port serves BY CONTENT, never assume.
- The live panel loads webview JS from the built jar; control builds restore the WHOLE
  `plugin/src/main/resources/webview/` directory (conventions.md).
- `./gradlew probe --args="<proj> <sessionId> --json"` + `cmp` is the cheap refactor-safety
  check for SessionStore: byte-identical output on a real session.
- Probe scripts live in session scratchpads (not committed); always run under `timeout N`.
  Headless CLI probes run in `~/Sites/claude-brains-testing` (this repo's /context skill shadows
  the built-in).

## Next steps
Done items live in `journal.md`; this list is only what is still open.
- [ ] Get the user's yes / later / no on the **nine [DECIDE]** rows (8.7, 8.10, 8.11, 9.4, 9.5,
      12.3, 12.6, 13.2, 14.2) in `docs/feature-checklist.md`.
- [ ] Decide `/clear [name]`: pass the name through as the new conversation's title, or pin the
      old pick-runs behavior for native commands (checklist 7.6; found 2026-08-23).
- [ ] Sync `docs/slash-commands.md` to 2.1.241 — it still says "measured … 2.1.233" and doesn't
      know the `/clear` hint; roster names/schema are unchanged, so it's a small edit.
- [ ] Backlog order (`backlog.md` § Next up): plan-card keyboard shortcuts (Enter / Shift+Tab) →
      reloaded-webview **log** replay (**8.14**) → kill-background-process (**11.3**, now
      unblocked) → tweak-travel (**3.5**). **9.4** fast-mode toggle [SM] is the cheapest 🟥 left.
- [ ] Sync the **Marketplace web description** (hand-edited Markdown, NOT refreshed by uploads)
      to plugin.xml's new slash-commands wording — the user edits it at plugins.jetbrains.com.
- [ ] **Windows errand, two items in one trip:** run `./gradlew test` once (CRLF splice path
      never run there), and click a `Read` path for a freshly-created file to confirm the VFS fix
      on the box that reported it.
- [ ] Consolidation pass on the context files: `decisions.md` and `gotchas.md` are far over the
      ~100-line target — promote, then cut. Flagged 2026-08-17, still pending.

## Known gaps (deliberately left)
- Plan-card keyboard shortcuts (Enter = keep planning, Shift+Tab = approve with feedback)
  deferred by the user 2026-08-16; handlers slot into the same `done()` paths (backlog).
- Paths inside free prose still not shortened; `/batch` fan-out verified at N=2 only;
  queue-path `.q-row` sweep still inconclusive.
- Sub-agent WORK outcome not surfaced; `prefers-reduced-motion` unverified against the real OS
  setting.
- `DiffReview.open` still resolves its left pane through snapshot-only `findVFile` (parked,
  backlog); Marketplace updates may prompt an IDE restart (accepted 2026-08-19).

## Which machine — check FIRST, both are real
This session (2026-08-23) ran on **Linux** (`/home/syncroze/Sites/claude-brains`). Paths for both
boxes in overview.md § External references. Slash-menu fixtures + probe scratchpads + the
verifier's cached IDE ladder exist on Linux only. The Windows box still owes the CRLF splice
check above.

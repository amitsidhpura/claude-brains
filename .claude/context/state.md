# State

## Current focus
**2026-08-21 (ninth session): fixed the panel's "File not found" on a path that exists. Committed
to `main`, NOT released — 0.8.0 remains the shipped version, so this rides the next release.**
- **The bug:** clicking the path on a `Read` line raised the "File not found" balloon for a file
  plainly on disk (user, Windows, project `D:\sites\metrobuildsuppliers`, a screenshot in
  `_local/`). Cause: `findVFile` → `LocalFileSystem.findFileByPath` reads the VFS SNAPSHOT, never
  the disk, so a file written behind the IDE's back does not resolve until its parent is
  refreshed. Platform-agnostic, not a Windows bug, not a path-spelling bug.
- **The fix:** `findVFileOnDisk` in
  `plugin/src/main/kotlin/io/github/amitsidhpura/claudebrains/Vfs.kt` (snapshot hit first, then
  `refreshAndFindFileByPath`), wired into exactly two callers — `ClaudeSessionService.openFile`
  (panel click) and `bridge/IdeTools.kt` `openFile` (CLI-facing). Everything under `readLocked`
  keeps plain `findVFile`: a synchronous refresh under a read lock deadlocks. Full rationale in
  `decisions.md`, the trap and the caller split in `gotchas.md` § IDE platform / VFS.
- **How it was proven** (both live IDEs, no GUI automation): reproduced against the user's own
  running PhpStorm through its MCP bridge with read-only `checkDocumentDirty`, then verified in a
  sandbox with a three-call discriminating run (snapshot "not found" / fixed "opened" / absent
  path "not found") plus the real path driven over CDP (`__bridge {kind:'open'}` →
  `getOpenEditors` confirms the open). gradle test 107, compile clean.
- Reviewed by a second model at the user's request (threading triage re-derived from the code,
  caller sweep, verdict ship). One cosmetic note left alone: a missing ABSOLUTE path now attempts
  two refreshes, because the pre-existing basePath fallback concatenates it anyway.

## Open work — ids re-verified against `docs/feature-checklist.md` on 2026-08-19
- 🟥 high rows left: **3.5** tweak-travel [LG] · **8.7** rewind/fork [LG, undecided since
  2026-07-30] · **8.11** side question [MD, NEW] · **8.14** reloaded-webview **log** replay [LG]
  (roster half shipped as 7.10) · **9.4** fast-mode toggle [SM] · **11.3** kill-background-process [MD].
- **Nine [DECIDE] rows** await the user: 8.7, 8.10, 8.11, 9.4, 9.5, 12.3, 12.6, 13.2, 14.2.
- **Unreleased on `main`: the 2026-08-21 VFS open-path fix.** 0.8.0 (`dce3600` / `v0.8.0`,
  2026-08-19) shipped 2.4, 2.10, 2.11, 7.7, 7.10, the webview split and the effort-label fix, and
  fixed `plugin.xml`'s stale "/compact + /clear only" description line. **A release starts only
  when the user asks.**

**Releases so far — all Approved on the Marketplace the same day** (detail in `journal.md`;
process in `docs/release.md`): 0.8.0 (2026-08-19, `dce3600`), 0.7.2 (2026-08-17, `40bc060`),
0.7.1 (2026-08-16, `91a6ba5`), 0.7.0 (2026-08-16, `59d94fc`). Manual-test register: 0 open / 25
resolved. New on the 0.8.0 verification page: JetBrains now also runs a live "IDE run with the
plugin installed" check (passed) on top of the verifier ladder.

## How plan feedback travels (probed on CLI 2.1.233 — traps in gotchas)
- **Deny** → typed text IS the control-response `message`, delivered VERBATIM as the ExitPlanMode
  tool_result. Empty → stock `RenderLimits.REJECT_MESSAGE` (one copy, shared with replay filter).
- **Approve** → text APPENDED TO THE APPROVED PLAN via `updatedInput.plan` under
  `RenderLimits.PLAN_NOTES_MARKER` — the model reads the note in the SAME message as the approval.
  Also lands in the saved plan file (deliberate trade). TUI-equivalent path unusable (gotchas).
- **Mode rows** park in `pendingPlanMode` (webview) and bridge only on the CLI's post-approval
  permissionMode broadcast — an immediate set always lost to the CLI's prePlanMode restore.
  Chip aliases broadcast `default` → `manual`.
- Wire: webview `respondPermission(id, allow, suggIdxs, text)` → ChatPanel "perm" →
  ClaudeSessionService → ClaudeCli. Webview is mechanism-blind.
- Replay: SessionStore captures `planFeedback` (deny = tool_result string filtered against stock;
  approve = parsed back out of `toolUseResult.plan` by the marker); mid-turn queued/prompt
  records map to user bubbles. Footers quote via one `fbQuote()` (72-char cut, live+replay).

## Testing — the standing setup
- Live harness: `python3 tools/live_harness.py`, baseline **361** (fixtures to 52; fixture 51 is
  now 2 steps); `./gradlew test` **107** (106 + WebviewAssets manifest test).
- Sandbox debug port: the sandbox's hand-set Registry value keeps CDP on **9222** even when
  `-PjcefDebugPort=9223` is passed — verify which panel a port serves BY CONTENT, never assume.
- The live panel loads webview JS from the built jar, so a passing harness also proves
  `webview/js/` packaging. Control builds restore the WHOLE `plugin/src/main/resources/webview/`
  directory (conventions.md).
- Probe scripts live in session scratchpads (not committed); always run under `timeout N`.

## Next steps
Done items live in `journal.md`; this list is only what is still open.
- [ ] Get the user's yes / later / no on the **nine [DECIDE]** rows (8.7, 8.10, 8.11, 9.4, 9.5,
      12.3, 12.6, 13.2, 14.2) in `docs/feature-checklist.md`.
- [ ] Backlog order (`backlog.md` § Next up): plan-card keyboard shortcuts (Enter / Shift+Tab) →
      reloaded-webview **log** replay (**8.14**) → kill-background-process (**11.3**) →
      tweak-travel (**3.5**). **9.4** fast-mode toggle [SM] is the cheapest 🟥 left.
- [ ] Sync the **Marketplace web description** (hand-edited Markdown, NOT refreshed by uploads)
      to plugin.xml's new slash-commands wording ("sixteen built-ins … plus auto-enabled custom
      commands, skills and MCP prompts") — the user edits it at plugins.jetbrains.com.
- [ ] **Windows errand, two items in one trip:** run `./gradlew test` once (the CRLF-checkout
      path of the `<!--JS-->` splice is reasoned, never run there), and click a `Read` path for a
      freshly-created file to confirm the 2026-08-21 VFS fix on the box that reported it. The
      proof ran on Linux; only backslash normalization is Windows-specific and that is unchanged.
- [ ] Consolidation pass on the context files: `decisions.md` and `gotchas.md` are far over the
      ~100-line target — promote, then cut. Flagged 2026-08-17, still pending.

## Known gaps (deliberately left)
- Plan-card keyboard shortcuts (Enter = keep planning, Shift+Tab = approve with feedback)
  deferred by the user 2026-08-16; handlers slot into the same `done()` paths (backlog).
- Feedback input surfaced on plan cards only; the wire plumbing is generic for other cards.
- Paths inside free prose still not shortened; `/batch` fan-out verified at N=2 only;
  queue-path `.q-row` sweep still inconclusive.
- Sub-agent WORK outcome not surfaced; no kill-background-process from the panel;
  `prefers-reduced-motion` unverified against the real OS setting.
- `DiffReview.open` still resolves its left pane through the snapshot-only `findVFile`: same
  latent staleness as the fixed open paths, but it degrades to showing an existing file as new,
  and the end-of-turn `refreshFromDisk` sweep covers the paths it receives. Parked (backlog).
- Marketplace updates of the plugin may prompt an IDE restart (download-cache race +
  JCEF unload) — accepted by the user 2026-08-19, not a defect to fix.

## Which machine — check FIRST, both are real
This session (2026-08-21) ran on **Linux** (`/home/syncroze/Sites/claude-brains`). Paths for both boxes in
overview.md § External references. Slash-menu fixtures + probe scratchpads + the verifier's
cached IDE ladder exist on Linux only. The Windows box still owes the CRLF splice check above.

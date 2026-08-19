# State

## Current focus
**2026-08-19 (eighth session): the webview split + effort-label fix shipped, then 0.8.0 RELEASED
and Approved the same day. Nothing in flight, nothing unreleased on main.**
- **`chat.html` is markup-only now (131 lines).** The JS lives in `webview/js/` as 14 numbered
  files (`00-core.js` … `90-gallery.js`, prefixes = load order, gaps for insertion), concatenated
  back into the page's single `<script>` block by `ui/WebviewAssets.kt` (`page()`, at the
  `<!--JS-->` marker — same trick as the CSS/LIMITS splices). One shared script scope, no modules;
  the assembled page was verified byte-identical to the pre-split file modulo the
  `/* ===== file: NN-name.js ===== */` banner lines that map DevTools line numbers to files.
  `WebviewAssets.JS_FILES` is the ONLY copy of the order; `RenderLimitsTest` asserts over the
  ASSEMBLED page + manifest ⇄ directory equality. Negative control was RUN (broken marker +
  dropped manifest entry → 4 discriminating failures). The user hand-verified a 7-step ladder in
  the sandbox covering every file (welcome, live turn, slash+alias, permission card, replay,
  __gallery, pickers/gauge/markdown).
- **Effort label fix** (`e06add1`): "Very High" wrapped to two lines in the mode-menu footer —
  JCEF sizes a text flex item's base a hair under max-content (53px vs 55; desktop Chrome does
  not, so the mockup could never show it). Fix: `white-space: nowrap` on `.ef-label` (chat.css).
  Fixture 51 gained a step (label → "Very High" via `setEffortUI`, asserts one line); control run
  against the still-running pre-fix sandbox (bH 37 → fail), fixed build 12/12.
- Marketplace-update restart question answered from the real IDE's log (PhpStorm 2026.2.1 snap):
  the 0.7.1 no-restart install died on `NoSuchFileException` for the download-cache zip → fell
  back to install-on-restart. IDE-side cache race, nothing in plugin.xml; JCEF/WS/process unload
  would likely force restarts anyway. **User accepted it — do not engineer around it.**

## Open work — ids re-verified against `docs/feature-checklist.md` on 2026-08-19
- 🟥 high rows left: **3.5** tweak-travel [LG] · **8.7** rewind/fork [LG, undecided since
  2026-07-30] · **8.11** side question [MD, NEW] · **8.14** reloaded-webview **log** replay [LG]
  (roster half shipped as 7.10) · **9.4** fast-mode toggle [SM] · **11.3** kill-background-process [MD].
- **Nine [DECIDE] rows** await the user: 8.7, 8.10, 8.11, 9.4, 9.5, 12.3, 12.6, 13.2, 14.2.
- Nothing unreleased: 0.8.0 (`dce3600` / `v0.8.0`, 2026-08-19) shipped 2.4, 2.10, 2.11, 7.7,
  7.10, the webview split and the effort-label fix, and fixed `plugin.xml`'s stale
  "/compact + /clear only" description line. **A release starts only when the user asks.**

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
- [ ] On the Windows box, run `./gradlew test` once — the CRLF-checkout path of the `<!--JS-->`
      splice (`WebviewAssets.page()` replaces marker text only) is reasoned, not yet run there.
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
- Marketplace updates of the plugin may prompt an IDE restart (download-cache race +
  JCEF unload) — accepted by the user 2026-08-19, not a defect to fix.

## Which machine — check FIRST, both are real
This session ran on **Linux** (`/home/syncroze/Sites/claude-brains`). Paths for both boxes in
overview.md § External references. Slash-menu fixtures + probe scratchpads + the verifier's
cached IDE ladder exist on Linux only. The Windows box still owes the CRLF splice check above.

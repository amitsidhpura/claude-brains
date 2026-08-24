# State

## Current focus
**2026-08-24/25 (thirteenth session): the model menu grew a footer — 1M context / Fast mode /
Thinking switches (checklist 9.9 / 9.4 / 9.5), plus two user-driven follow-ups.
Released as 0.10.0 at the session's end.**
- **The three switches** live in `#modelFooter` (chat.html) under the model list: `.tgl` toggle
  idiom (new; chat.css ~:1146), logic in `30-menus.js` (`syncModelFooter`, `rosterFor`,
  `shown1m`), frames `__fastMode`/`__thinking` (70-events), Kotlin `applyFlagSettings` /
  `setMaxThinkingTokens` (ClaudeCli), prefs `claudeCode.fastMode` / `claudeCode.thinkingOff`
  re-applied each CLI start (ClaudeSessionService ~:171).
- **1M switch: NO validity logic (user decision)** — flip anything; an unsupported combo errors
  on the next turn. It reconciles to the REAL window from `result.modelUsage[].contextWindow`
  (`reconcileFromResult`, 80-gauge.js), cleared per model change. Thinking ON = null (reset to
  default; deliberate divergence from VS Code's 31999), OFF = 0.
- **API-error double-render fixed**: the CLI echoes an error as a synthetic assistant message
  AND the result's is_error text (gotchas § Protocol). Live now dedupes by EXACT text equality
  only — everything else draws its own error block (hardened after the user asked "could this
  swallow a message?" — it could, fixture 56's second control proved it).
- Footer icons: circle-gauge / fast-forward / brain (user-picked).
- **Fast mode on this account**: opt-in clears `sdk_opt_in_required` but `extra_usage_disabled`
  remains — enable extra usage on claude.ai to actually get fast turns; ground truth per turn is
  `result.usage.speed`.

## Released — 0.10.0 (2026-08-25)
**0.10.0 is the shipped version** (tag `v0.10.0`, commit `55fdcb1`). Contents: the three
model-menu footer switches (9.9/9.4/9.5), the 1M reconcile-to-real-window, the API-error
single-render fix, new footer icons. `verifyPlugin` ran BEFORE the approval gate: Compatible on
all seven IDEs (242.26775.23 → 262.10315.32), no warnings. GitHub release + custom feed +
Marketplace upload all green; asset byte-identical; Marketplace **Approved** (user's dashboard,
all compatibility checks Success incl. the IDE-run check).
Nothing is unreleased on `main`. Next release is a fresh bump when work lands.

## Open work — ids verified against `docs/feature-checklist.md`
- 🟥 high rows left: **3.5** tweak-travel [LG] · **8.7** rewind/fork [LG] · **8.11** side
  question [MD, probe pre-paid] · **8.14** reloaded-webview log replay [LG] · **11.3**
  kill-background-process [MD, `stop_task` accepted]. (9.4 shipped 2026-08-24.)
- **Seven [DECIDE] rows** await the user: 8.7, 8.10, 8.11, 12.3, 12.6, 13.2, 14.2
  (9.4/9.5 decided + built 2026-08-24).
- `/clear [name]` decision still open (checklist 7.6); `docs/slash-commands.md` still documents
  CLI 2.1.233.

## Testing — the standing setup
- `python3 tools/live_harness.py` baseline **444** (fixtures to 56); `./gradlew test` **115**.
- Fixture 55 = the model-menu footer (28 asserts, 4 controls recorded); fixture 56 = the
  API-error dedupe (5 asserts, 2 controls). Fixture 51's rail selector is now scoped to
  `#modeMenu` — bare idiom-class selectors break when an idiom is duplicated (gotchas § CSS).
- Sandbox **PhpStorm 2024.2.6**, dir `build/idea-sandbox/PS-2024.2.6/`. Restart: `pkill -f
  'run[I]de'` kills only gradle — kill the IDE by pid via `pgrep -f 'idea.system.pat[h]'`.
  New quirk: a relaunch right after a clean kill can exit 0 in ~2s with no window and NO
  orphan — just retry once (gotchas § Testing).
- Verify builds BY CONTENT over CDP before trusting a run; control builds restore the WHOLE
  `plugin/src/main/resources/webview/` directory. Headless CLI probes run in
  `~/Sites/claude-brains-testing`.

## Next steps
- [ ] Get yes / later / no on the **seven [DECIDE]** rows (8.7, 8.10, 8.11, 12.3, 12.6, 13.2,
      14.2).
- [ ] Decide `/clear [name]` (checklist 7.6); sync `docs/slash-commands.md` to 2.1.241.
- [ ] Backlog order (`backlog.md` § Next up): plan-card keyboard shortcuts → reloaded-webview
      log replay (**8.14**) → kill-background-process (**11.3**) → tweak-travel (**3.5**).
- [ ] Sync the **Marketplace web description** (hand-edited; uploads don't refresh it).
- [ ] **User errands**: gist push of the context skill (`gh gist edit
      b2d033439ba4ca5bcd018f4fe5eef773 -f SKILL.md .claude/skills/context/SKILL.md`, check
      line 1 is exactly `---`); Windows `./gradlew test` + VFS click check.

## Known gaps (deliberately left)
- Effort/model conversation markers **dropped by the user 2026-08-24** (decisions) — do not
  re-propose. Confirm-card path wrapping declined 2026-08-24 (previous session) — same.
- 1M switch on Fable is a near-no-op by design (fable is natively 1M; the reconcile snaps it
  honest after the first turn). Fast-mode "· fast" turn-summary marker offered, parked
  (backlog).
- 5.6 keyboard-only comment pill; plan-card keyboard shortcuts deferred 2026-08-16;
  `DiffReview.open` snapshot-only lookup parked; `/batch` verified at N=2 only.

## Which machine — check FIRST, both are real
This session ran on **Linux** (`/home/syncroze/Sites/claude-brains`). Paths for both boxes in
overview.md § External references. The Windows box still owes the CRLF splice check.

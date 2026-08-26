# State

## Current focus
**2026-08-26 (fifteenth session): the effort slider moved into the MODEL menu, and its level is
now displayed on no chip at all. UNCOMMITTED — see "Ready to commit" below.**
- **Effort slider is the last row of `#modelFooter`** (`plugin/src/main/resources/webview/chat.html`),
  under the 1M context / Fast mode / Thinking switches. `#modeMenu` has no `.popup-f` any more —
  it holds modes only. Model popup header renamed "Select a model" → **Models**.
- **No chip shows the effort level.** `setModelChip` (30-menus.js) is byte-identical to its
  pre-session body; `syncModelChip`, `currentEffort` and `.chip-sep` were built and then removed.
  Chips read `✨ Default (Opus 5)` and `</> Auto`. Three suffix forms were rendered and rejected —
  decisions 2026-08-26. **Do not re-propose a chip suffix.**
- `.ef-row` is deliberately NOT `.tgl-row` (chat.css): the effort row carries a dot slider, not a
  switch, and separate names keep fixture 55's "exactly three toggle rows" count honest.
- `design/mockup.html` mirrored — markup AND its own copy of the slider JS (its `renderModeChip`
  was still appending `(High)` on click; static markup alone would have hidden that).

## Ready to commit (nothing else is in flight)
Working tree, all verified, **not committed**:
`plugin/src/main/resources/webview/{chat.html,chat.css,js/30-menus.js}` · `design/mockup.html` ·
`docs/{feature-checklist.md,manual-test.md}` · `.claude/context/gotchas.md` ·
`tools/fixtures/55-model-menu-toggles.json` · fixture 51 RENAMED
`51-mode-menu-effort-rail.json` → `51-model-menu-effort-rail.json` (use `git mv`/staged rename).
**0.11.0 is the shipped version** (tag `v0.11.0`, commit `4c8899b`); this work is unreleased and no
release was requested. Next release = a fresh bump when the user asks.

## Open work — ids verified against `docs/feature-checklist.md`
- 🟥 high rows left: **3.5** tweak-travel [LG] · **8.7** rewind/fork [LG] · **8.11** side
  question [MD, probe pre-paid] · **8.14** reloaded-webview log replay [LG] · **11.3**
  kill-background-process [MD, `stop_task` accepted].
- **Seven [DECIDE] rows** await the user: 8.7, 8.10, 8.11, 12.3, 12.6, 13.2, 14.2.
- `/clear [name]` decision still open (checklist 7.6); `docs/slash-commands.md` still documents
  CLI 2.1.233 — installed CLI is **2.1.246** now.

## Testing — the standing setup
- `python3 tools/live_harness.py` baseline **467** (was 462; fixture 51 gained 4 asserts, then
  step 3 was rewritten and gained 1 more); `./gradlew test` **116**.
- Fixture 51 = `51-model-menu-effort-rail.json`. Its old text-delta contract is DEAD (model rows
  have no `.pi-ic` to measure against — it would be vacuous). It now pins the moved row's icon
  against the 1M row's, inherits the model-title rail from fixture 55 by transitivity, and step 3
  pins the ABSENCE of the level on BOTH chips. Three negative controls are recorded in its
  provenance; read that before changing any assert.
- Sandbox **PhpStorm 2024.2.6**; start (must run from `plugin/`):
  `cd plugin && ./gradlew runIde -PskipVerifierIdes -PjcefDebugPort=9222
  --args="$HOME/Sites/claude-brains-testing"`. **`runIde` DETACHES** — gradle exits 0 or 1 while
  the IDE keeps running, so the background task "failing" says nothing; check
  `pgrep -f 'idea.system.pat[h]'`. Kill by pid, then wait for CDP to disappear, not just the pid.
- **ALWAYS verify the running build BY CONTENT over CDP before trusting a fixture run** — a
  relaunch served the PREVIOUS build's bytes while its own sandbox file was already correct
  (gotchas § Testing, added this session).
- Control builds restore the WHOLE `plugin/src/main/resources/webview/` directory.

## Next steps
- [ ] **Commit + push the working tree** (the user asked for this at the 2026-08-26 save).
- [ ] Get yes / later / no on the **seven [DECIDE]** rows (8.7, 8.10, 8.11, 12.3, 12.6, 13.2, 14.2).
- [ ] Decide `/clear [name]` (checklist 7.6); sync `docs/slash-commands.md` to 2.1.246.
- [ ] Backlog order (`backlog.md` § Next up): plan-card keyboard shortcuts → reloaded-webview
      log replay (**8.14**) → kill-background-process (**11.3**) → tweak-travel (**3.5**).
- [ ] Sync the **Marketplace web description** (hand-edited; uploads don't refresh it).
- [ ] **User errands**: gist push of the context skill (`gh gist edit
      b2d033439ba4ca5bcd018f4fe5eef773 -f SKILL.md .claude/skills/context/SKILL.md`, check
      line 1 is exactly `---`); Windows `./gradlew test` + VFS click check.

## Known gaps (deliberately left)
- **The Thinking switch is INERT on Fable** — `set_max_thinking_tokens 0` is accepted but fable
  still streams thinking (measured 2026-08-26). "Document only" by user decision; do not gate it.
- **Do not gate UI on roster capability flags.** haiku carries none of `supportsEffort` /
  `supportedEffortLevels` / `supportsAdaptiveThinking` yet both controls work there. Only
  `supportsFastMode` is gated, and only because delivery was probed. Gotchas § Protocol.
- Effort/model conversation MARKERS dropped 2026-08-24 (partly superseded 2026-08-25: /effort now
  shows the CLI's confirmation line). Confirm-card path wrapping declined — do not re-propose.
- Typing `/model` in the composer is refused (`cmdKind` → 'tui'); the chip is the only model surface.
- 1M switch carries NO client-side validity logic, by decision. Fast-mode "· fast" marker parked.
- 5.6 keyboard-only comment pill; plan-card keyboard shortcuts deferred 2026-08-16;
  `DiffReview.open` snapshot-only lookup parked; `/batch` verified at N=2 only.

## Which machine — check FIRST, both are real
This session ran on **Linux** (`/home/syncroze/Sites/claude-brains`). Paths for both boxes in
overview.md § External references. The Windows box still owes the CRLF splice check.

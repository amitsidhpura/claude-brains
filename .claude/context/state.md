# State

## Current focus
**2026-08-25 (fourteenth session): model-menu polish + command-turn parity — five fixes,
released as 0.11.0 at the session's end.**
- **Custom model rows show the selection ✓** (fixture 57): `renderModels()` (30-menus.js) emits
  `.pi-check` on EVERY row; a custom row's remove × lives INSIDE the check span, overlaying the
  ✓'s box (`inset`), with `#inputbar .model-del{padding:0}` killing the ID-rule padding squeeze.
  Hover swaps ✓ → × (`#modelMenu .popup-item.custom:hover .pi-check`). NO offset math near
  #inputbar — gotchas § Webview/CSS.
- **Chip/slider command turns draw ONE confirmation line, no bubble, live AND resumed**
  (decisions 2026-08-25): `cleanInjected` (SessionStore.kt) drops exactly {`/model`, `/effort`}
  wrappers (the CLI writes a command trio to disk for the chip's `set_model` and the slider's
  `/effort` — gotchas § Protocol); the `effortMuted` gate (70-events.js) draws the /effort
  confirmation from the CLI's SYNTHETIC assistant frame (the normal path stashes synthetic text
  into `syntheticEcho`, draws nothing). Titles can no longer become "/model haiku".
  Supersedes the 2026-07-30 /effort audit-trail acceptance.
- Docs synced: renderer-parity.md (two closed rows + superseded row), slash-commands.md § the
  /model-/effort paragraph, feature-checklist §16.2.

## Released — 0.11.0 (2026-08-25)
**0.11.0 is the shipped version** (tag `v0.11.0`, commit `4c8899b`). Contents: effort
confirmation line, custom-model checkmark + ×-overlay, /model-/effort resume parity + title
fix. `verifyPlugin` ran BEFORE the gate (Compatible ×7, 0 warnings — after catching a silent
non-run, gotchas § Build). GitHub release + feed + Marketplace all green; asset byte-identical;
Marketplace **Approved** (user's dashboard, all checks Success incl. the IDE-run check).
Nothing is unreleased on `main`. Next release is a fresh bump when work lands. (0.10.0 shipped
earlier the same day: footer switches, 1M reconcile, API-error single-render.)

## Open work — ids verified against `docs/feature-checklist.md`
- 🟥 high rows left: **3.5** tweak-travel [LG] · **8.7** rewind/fork [LG] · **8.11** side
  question [MD, probe pre-paid] · **8.14** reloaded-webview log replay [LG] · **11.3**
  kill-background-process [MD, `stop_task` accepted].
- **Seven [DECIDE] rows** await the user: 8.7, 8.10, 8.11, 12.3, 12.6, 13.2, 14.2.
- `/clear [name]` decision still open (checklist 7.6); `docs/slash-commands.md` still documents
  CLI 2.1.233 (installed CLI is **2.1.245** now).

## Testing — the standing setup
- `python3 tools/live_harness.py` baseline **462** (fixtures to 58); `./gradlew test` **116**.
- Fixture 57 = custom-row tick + ×/✓ overlay geometry (8 asserts incl. center AND size equality
  — symmetric shrink passes center asserts alone); fixture 58 = /effort confirmation through the
  effortMuted gate (6 asserts, VERBATIM measured frames). Negative controls all RUN.
- Unit test `model and effort changes replay as confirmation lines, not command bubbles`
  (SessionStoreTest) pins the wrapper drop list, the surviving confirmations, and the title.
- Sandbox **PhpStorm 2024.2.6**, dir `build/idea-sandbox/PS-2024.2.6/`; start:
  `cd plugin && ./gradlew runIde -PskipVerifierIdes -PjcefDebugPort=9222
  --args="$HOME/Sites/claude-brains-testing"`. Kill IDE by pid (`pgrep -f
  'idea.system.pat[h]'`), `pkill -f 'run[I]de'` gets only gradle; a relaunch right after a
  clean kill can exit 0 in ~2s — retry once (gotchas § Testing).
- Verify builds BY CONTENT over CDP before trusting a run; control builds restore the WHOLE
  `plugin/src/main/resources/webview/` directory. Headless CLI probes run in
  `~/Sites/claude-brains-testing` (this session: the /effort wire vs disk measurement).

## Next steps
- [ ] Get yes / later / no on the **seven [DECIDE]** rows (8.7, 8.10, 8.11, 12.3, 12.6, 13.2,
      14.2).
- [ ] Decide `/clear [name]` (checklist 7.6); sync `docs/slash-commands.md` to 2.1.245.
- [ ] Backlog order (`backlog.md` § Next up): plan-card keyboard shortcuts → reloaded-webview
      log replay (**8.14**) → kill-background-process (**11.3**) → tweak-travel (**3.5**).
- [ ] Sync the **Marketplace web description** (hand-edited; uploads don't refresh it).
- [ ] **User errands**: gist push of the context skill (`gh gist edit
      b2d033439ba4ca5bcd018f4fe5eef773 -f SKILL.md .claude/skills/context/SKILL.md`, check
      line 1 is exactly `---`); Windows `./gradlew test` + VFS click check.

## Known gaps (deliberately left)
- Effort/model conversation MARKERS dropped 2026-08-24 — superseded IN PART 2026-08-25: /effort
  now shows the CLI's confirmation line (user's own reversal); the marker-style display and the
  model half stay dropped. Confirm-card path wrapping declined 2026-08-24 — do not re-propose.
- Typing `/model` in the composer is refused (`cmdKind` → 'tui'); the chip is the only model
  surface. A typed `/model` in a TUI session resumes without its bubble too (stdout line stays).
- 1M switch on Fable is a near-no-op by design. Fast-mode "· fast" marker parked (backlog).
- 5.6 keyboard-only comment pill; plan-card keyboard shortcuts deferred 2026-08-16;
  `DiffReview.open` snapshot-only lookup parked; `/batch` verified at N=2 only.

## Which machine — check FIRST, both are real
This session ran on **Linux** (`/home/syncroze/Sites/claude-brains`). Paths for both boxes in
overview.md § External references. The Windows box still owes the CRLF splice check.

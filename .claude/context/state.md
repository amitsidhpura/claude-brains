# State

## Current focus
**Unreleased work in the tree: 3.1 + 9.10 shipped and user-verified, awaiting the next version
bump.** The defect register is at **0 open / 25 resolved** for the first time. What landed
2026-08-15: custom slash commands (project/user command files, skills, MCP prompts) auto-enable
in the `/` menu with muted source badges; `/reload-skills` enabled; and a command-wrapper title
leak fixed in `SessionStore.cleanInjected`. All committed on main (see journal 2026-08-15).

## How custom commands work now (the mechanism, for whoever touches cmdKind next)
- The wire marks custom entries with a description SUFFIX — " (project)" / " (user)" — on both
  the initialize roster and `commands_changed`. `markCustom()` (chat.html, near `slashCommands`)
  strips it into `c.src` + the `customCmds` map; `cmdKind` returns 'text' for map hits and
  `mcp__`-prefixed names, native/allowlist checked FIRST (a custom `clear.md` cannot shadow the
  IDE's /clear). Badges render via `.pi-src` (chat.css). No Kotlin in the detection path.
- The CLI watches the PROJECT commands dir (drop ≈2.5s, delete ≈1s → `commands_changed`, no turn
  needed); `~/.claude/commands` is NOT watched — `/reload-skills` is the manual lever. Full
  mechanism + re-captured 50-command roster (2.1.228, aliases column) in docs/slash-commands.md.
- Fixture 46 (25 assertions) pins it; its provenance records the negative-control run.

## Three contracts to know before touching `webview/chat.css`
- **Spacing:** `--block-gap` (18px, independent blocks) and `--attach-gap` (8px, a line and what
  hangs off it) — glossary.md for vocabulary, gotchas.md for which FORM per parent type.
- **The gutter dot:** ONE geometry rule serves `.blk`/`.think`/`.think-live`/`.tool-line`; add a
  state by setting `--dot-c`, never by copying geometry. `--pulse-period` (1.6s) is the shared
  live heartbeat.
- **Nothing may grow OUTSIDE the dot's 10px box** — `.turn-body`'s paint containment clips
  outward drawing (bitten twice). See gotchas before adding any effect with geometry.

## Testing — the standing setup
- Live harness: `python tools/live_harness.py` needs `./gradlew runIde` up + tool window open.
  Baseline **242/242** (fixture 46 added 25). `./gradlew test` **101 green**.
  Wire fixtures need a `.turn-body` built via the per-step `"setup"` hook (see fixture 45).
  **Harness side effects persist in the live panel** (fixture 46's empty-roster step wipes the
  real roster) — reload the webview (seedUi replays) before eyeballing the panel afterwards.
- The plugin's own MCP bridge is a free probe (`openFile` = "does the VFS know this file",
  `getDiagnostics` = "did the open editor reload") — details in gotchas.
- Manual re-test fixtures for the slash menu live in `~/Sites/claude-brains-testing/.claude/commands/`:
  `dummy-cmd.md` + `sub/nested-cmd.md` (deliberately kept; everything else cleaned up).

## Next steps
- [ ] **Release the next version** (`docs/release.md`): the in-flight dot fixes are already on
      main from 0.6.0; NEW since then: custom-command menu (3.1), 9.10, the title-leak fix.
      `changeNotesHtml` in `plugin/build.gradle.kts` must be updated or `buildPlugin` refuses.
- [ ] Then: backlog.md order — reloaded-webview log replay, kill-background-process from the
      panel, editor accept/reject v2 tweak-travel.

## Defect register (docs/manual-test.md)
**0 open / 25 RESOLVED, zero unticked checklist items.** First time at zero.

## Which machine — check this FIRST, both are real
2026-08-15 ran on **Linux** (`/home/syncroze/Sites/claude-brains`). Paths for both boxes in
overview.md § External references. The slash-menu fixtures (`dummy-cmd.md`, `sub/nested-cmd.md`)
exist on Linux only (`~/Sites/claude-brains-testing/`); recreate on Windows before re-testing.

## Known gaps (deliberately left)
- A webview reload replays the INITIALIZE-time roster, so mid-session-discovered commands vanish
  until the next `commands_changed` (backlog, flagged 2026-08-15).
- The **mcp** source badge is fixture-pinned only — no MCP server with prompts on this machine.
- `aliases` (new roster field) is display-only: the menu filter doesn't match them (documented
  as a non-feature in docs/slash-commands.md).
- A sub-agent's WORK outcome is not surfaced, and cannot reliably be (gotchas).
- A reloaded webview recovers its chrome but NOT its log (backlog).
- No way to kill a background process from the panel (backlog).
- `prefers-reduced-motion` unverified against the real OS setting on offscreen JCEF (backlog).
- Sidechain/subagent replay ordering untested; windowed replay DOM search only sees loaded blocks.
- Editor permission diff: tweak-travel is the remaining v2 half (backlog).
- A filename longer than the whole tool line is hard-clipped, no ellipsis (offered, declined).

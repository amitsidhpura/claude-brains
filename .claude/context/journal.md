# Journal

Dated session log, newest first. One compact entry per session: what was done, what was
learned, what's next. Entries older than ~10 sessions get digested (lessons promoted first).

## 2026-08-09 — the fixing session: register 19 → 13 open
- Removed all three webview keyboard chords (Ctrl+N / Ctrl+Alt+G / F12) instead of re-homing
  them as IDE actions — the plugin now binds NO shortcuts (decision logged). Every capability
  kept a route: New button / `/clear`, `window.__gallery()`, the DevTools Find Action.
- Re-tested 1.7 against the installed IDE: the Escape-reopen glitch is a SANDBOX artifact
  (CDP proved Escape and click-toggle leave identical DOM). Found + fixed two real Escape
  defects instead: slash-menu re-assert (`slashEscaped`) and card menus invisible to the
  Escape chain (`closeCardMenus()` rung). Register vocabulary unified: ISSUE = open,
  RESOLVED (date) — how = closed.
- 2.8 @-mentions: the menu opened INVISIBLY all along — `#mention`/`.mi` had zero CSS, so
  `position: static` ignored `openMenu()`'s viewport coords. CSS-only fix, verified by
  injecting into the live panel. Then built out the full dismissal contract from user
  reports: outside-click close, popup exclusivity both directions, soft-reopen on
  focus/click-return (Escape stays hard) — and gave the slash menu the identical contract
  via the `slashAuto()` extraction.
- New standing item 2.14: JCEF-Linux Delete key inserts keyChar 0x7F as a tofu char.
  Two-layer workaround: manual forward-delete on keydown + capture-phase control-char strip.
- 7.3 bg chip: `.chip-btn[hidden]{display:none}` (specificity defeat) + textContent clear;
  fixture 04 gained a computed-display assertion (the old `hidden`-property check was
  structurally blind to this bug). Verified END-TO-END with a real background task watched
  over CDP: appear "1 task" → full vanish when the sleep ended.
- 2.9 drag-drop: JCEF never delivers OS file drags to the DOM — added the AWT `DropTarget`
  delivery layer (`installFileDrop`, 25 MB cap) feeding the page's `__dropFiles`; page JS
  was proven correct all along. Hardware drag still needs the next runIde.
- Big technique win, now in gotchas: the spliced-chat.html headless harness (chat.css +
  live-captured `window.LIMITS` + `__bridge` stub, events via `onClaudeEvent`, assert via
  document.title) — used for every JS fix today; and `system/commands_changed` seeds the
  slash roster for future 3.1 testing.
- Next runIde carries the accumulated re-verification list: hardware drag, hardware Delete,
  Escape flags, mention menu under real JCEF. Then 10.5 openDiff accept-save (queue head).

## 2026-08-07/08 — the full manual-test pass
- Ran the entire standing checklist (`docs/manual-test.md`): 92/92 ticked over two days,
  19 ISSUE notes logged inline (register summarized in state.md). Every hard-to-trigger item
  was manufactured rather than skipped: network cut for the retry storm, auth-failure for
  error/Retry, hook-block via a temporary exit-2 settings hook, broken `.mcp.json` for the
  MCP notice, TUI `/rename` for custom titles, CDP fixture injection for rate-limit and
  refusal-fallback (render path only, provenance noted in the ticks).
- Built stitched synthetic sessions from REAL donor transcript records (turn-boundary
  slices, uuid remapping, `./gradlew probe` as the verification gate) to exercise replay
  richness, the 250-block window, the 4 MB image budget, and the orange gauge. Technique
  worth reusing; fixtures left in the testing project (see state.md).
- Verified the bridge end-to-end by speaking MCP-over-WS to it directly (lockfile token +
  "mcp" subprotocol) — which is how the openDiff accept-path bug (FILE_SAVED without a
  write) and the CLI 2.1.226 model-facing tool restriction were isolated from each other.
- Gallery verified via `window.__gallery()` over CDP after discovering all webview keyboard
  chords are dead on this machine.
- Corrected two prior beliefs: replayed image chips saying "file.jpg" is EXPECTED (the
  transcript persists the bare API image block, no filename); and the gauge not moving on a
  model switch was the >window→1M promotion rule working, not a bug.
- Sandbox note: `runIde` in a background shell needs `JAVA_HOME=~/.jdks/jdk-21.0.12+8`
  prefixed (the .zshrc export doesn't reach it).

## 2026-08-07
- Initialized `.claude/context/` as the project's portable memory (`/context init`).
- Migrated the root `CLAUDE.md` (427 lines) into overview / conventions / gotchas / decisions /
  state / backlog / glossary, then deleted it per the no-CLAUDE.md policy (recoverable from git
  history, last at commit `ee7e9fc`).
- Migrated the global auto-memory ("commit only when asked") into conventions.md.
- Un-ignored `.claude/context/` in `.gitignore` (the rest of `.claude/` stays ignored).
- Deliberately skipped `runbook.md` — release procedure already lives in `docs/release.md`.
- Noted: `.claude/skills/` (the `/context` skill itself) is still git-ignored, so the workflow
  doesn't travel to a fresh clone — un-ignore it if portability is wanted (open next step).
- Nothing committed yet; the whole migration awaits one commit (user commits on request only).

# Conventions

## Workflow
- **Commit only when asked.** No commits or pushes until the user says so; batch work into one
  meaningful commit. (Migrated from auto-memory 2026-08-07.)
- **Measure before believing.** Any claim about what the CLI sends gets checked against real
  transcripts BY KEY (not substring) or a live stream-json run BEFORE touching the renderer.
  Two of four client-parity "P0s" described bugs that didn't exist; the real one was
  mis-described — every time because the premise was never measured first.
- Enable slash commands one at a time, each verified in `runIde`, then ticked in
  `docs/slash-commands.md`. The menu is an allowlist — unconfirmed commands stay hidden.
- Test fixtures must state their `provenance`: a shape copied from our own handler proves
  self-consistency, NOT that the CLI emits it. New suites need a negative control (feed a wrong
  expectation, check it FAILS) — an all-green first run is what a vacuous suite also looks like.

## Scope vocabulary (from the Philosophy)
- Release/status prose splits **"By design"** (settings page, non-terminal login — the
  terminal's half, never build them, never list as gaps) from **"Not there yet"** (deferred:
  tabs, auto-include selection, voice). The two lists must not blur. Declined items
  (cost/usage display, 2026-08-06) are decisions, not queue positions.
- Feature bar: *is it reached many times an hour while writing code?* Yes → panel. No → terminal.

## Code & assets
- **Never bundle or redistribute** Anthropic's extension.js / webview / claude.exe; `vscode/`
  stays out of git. Personal use only.
- Styles live ONLY in `webview/chat.css`; editing chat.html markup? Mirror it in
  `design/mockup.html` too. No new hardcoded colours — add a token to `:root` and use
  `var(--x)` (for tints prefer `color-mix()` over a companion `-rgb` token).
- Any cap or output FORMAT produced by both the live renderer and the replay parser is stated
  once in `RenderLimits.kt` (spliced as `window.LIMITS`) and pinned by `RenderLimitsTest` —
  never a second copy in JS.
- Live and replay draw through the SAME shared block builders in chat.html
  (`ioRow/ioBox/toolLine/errorBlock/thinkBlock/planCardHtml/writeDiffHtml/askTabsHtml/…`)
  so they cannot drift. Keep it that way.
- Plugin Verifier target: 0 warnings on 242→262. Blocking reads via `readLocked {}`
  (Threads.kt); diagnostics via `DocumentMarkupModel` + `HighlightInfo.fromRangeHighlighter`;
  `FileSaverDescriptor` via reflection (see gotchas.md for why).

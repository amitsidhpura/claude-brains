# Conventions

Rules first, evidence second. Each rule is one line plus a pointer; the war stories behind them
live in `gotchas.md` (grep the section named) and `decisions.md`.

## Workflow
- **Commit only when asked**, one meaningful commit per unit of work. **Authorization does not
  carry forward**: "commit and push" covers THAT save; a later "fix X" ends with the change in the
  working tree and a report (a release-doc fix was committed unasked 2026-08-23 and reverted).
- **A release starts only when the user asks.** `docs/release.md` steps 1-5 being "fine to do
  proactively" licenses the mechanics, not the decision — never bump the version, edit
  `updatePlugins.xml` (served live off `main`: a bumped feed without an asset 404s every
  custom-repo user) or write changeNotes for a release nobody asked for (happened 2026-08-16).
- **`./gradlew verifyPlugin` on EVERY release, no judgement call**; read the per-IDE
  `verification-verdict.txt` files, never the log tail (0.9.0 shipped without it — gotchas § Build).
- **Say when you skip a documented step** — the call belongs to the user (0.9.0, 2026-08-23).
- **Measure before believing.** Any claim about what the CLI sends is checked against real
  transcripts BY KEY or a live stream-json run BEFORE touching the renderer (two of four
  client-parity "P0s" were bugs that didn't exist). Overview § "Which debug route".
- **Do not fix what you cannot reproduce** (user, 2026-08-12). A mechanism read out of the code or
  the CLI binary is a HYPOTHESIS; reproduce first, say which parts are proven vs inferred, and accept
  that the right outcome may be diagnostics and no change.
- **A failed reproduction is a STOP sign, not a licence to guess.** Three speculative guards for a
  phantom Enter broke Enter for the user (2026-08-23); the bug was a fixed upstream IDE defect the
  sandbox pinned (IJPL-161111) — check whether the environment is out of date before guarding
  anything environmental. gotchas § JCEF.
- **A third-party report is a SYMPTOM, not a diagnosis.** Ask what was on screen; take the exact
  wording as data ("dark bg on hover, lighter on leave" = two causes); **check the process tree /
  actual state before the code** ("chip says 2 tasks but there are none" — the chip was right).
- **"The CLI accepted it" is not "the panel rendered it."** User-facing claims are driven through
  the LIVE panel before being called verified; `docs/slash-commands.md` says which claim a tick
  stands for (a headless smoke run once "verified" 16 commands while `/context` rendered nothing).
- **When output is missing, tape the wire** (wrap `onClaudeEvent` — JCEF swallows throws silently)
  before blaming either side. It is what found the `/security-review` drop.
- Enable slash commands one at a time, each verified in `runIde`, then ticked in
  `docs/slash-commands.md`; the menu is an allowlist.
- Test fixtures state their `provenance` (a shape copied from our own handler proves
  self-consistency, not that the CLI emits it) and every new suite RUNS its negative control.

## Scope vocabulary (from the Philosophy in overview.md)
- **By design** (the terminal's half: settings page, login — never build, never list as gaps) vs
  **Not there yet** (deferred: tabs, auto-include selection, voice) vs **Declined** (cost/usage
  display 2026-08-06; Claude-side rewind/checkpoints and host git actions 2026-08-29 — git and
  Local History own undo). The lists must not blur (glossary.md).
- Feature bar: *is it reached many times an hour while writing code?* Yes → panel. No → terminal.

## Working style
- **UI changes go to `design/mockup.html` + `chat.css` FIRST**, shown as a render, wired into
  `chat.html` once agreed. The user hands back DevTools screenshots with exact values — apply them
  verbatim.
- **Open visual choices: render the candidates side by side**, in the REAL panel and real ancestor
  (the mockup and headless Chrome get glyph sizes wrong under `#inputbar svg{18px}`), and expect
  the pick to be provisional — the chip-label candidates ended with no label at all (2026-08-26).
- **Ship the safest candidate FIRST, then build the probe against it**, so each column varies one
  live token (`--attach-gap`, `--pulse-name`, 2026-08-13).
- **Sequence a fix so its negative control is free**: land the state-creating half, run the
  fixture, watch it fail, land the rest. Never run the control with neither half.
- **Negative controls run against the build that LACKS the fix** — `git checkout <fix-commit>~1 --
  <file>`, never HEAD-minus-my-commits (fixture 49 read as vacuous, gotchas § Testing). **Control
  builds restore the WHOLE `plugin/src/main/resources/webview/` directory** — a single-file restore
  builds a half-old page.
- **Two defects that mask each other need explicit state resets between fixture halves** (fixture
  44 went green on the pre-fix build). Run the whole fixture against pre-fix to learn which
  assertions discriminate.
- **A check that skips its own assertion looks like a pass** — audits print WHAT THEY CHECKED
  (the 2026-08-12 overflow audit never ran its main rule). gotchas § Testing.

## Docs
- **A document reformat is a design task — show ONE section first, get a yes, then do the file**
  (the 2026-08-28 checklist restructure was reverted as a "complete mess"). Docs are plain markdown;
  the one exception is `<details>` around re-audit paragraphs and §17 groups.
- **A doc outlives the decision that created it** — audit a doc's PREMISE when its subject changes,
  and that every relative link resolves (`release.md` said "no Marketplace" two weeks after listing).
- **Never copy ids out of a numbered register into a summary — re-derive them** (state.md cited
  8.5/8.9/8.13 for rows that were 8.7/8.11/8.14). The register is the source of truth.
- Release notes keep **By design / Declined / Deferred** apart; the README once listed a declined
  item as "deferred but wanted" — a reader takes that as a promise.
- The Marketplace listing's description comes from `plugin.xml` on upload (user's Marketplace
  setting, 2026-08-29) — edit plugin.xml only, then check the page after the next release.

## Code & assets
- **Never bundle or redistribute** Anthropic's extension.js / webview / claude.exe; `vscode/` stays
  out of git. Personal use only.
- Styles ONLY in `webview/chat.css`; chat.html markup changes are mirrored in `design/mockup.html`.
  No hardcoded colours, sizes or gaps: colours are `:root` tokens (`color-mix()` for tints),
  `font-size` is one of `--fs-base/-sm/-xs/-2xs` (13/12/11/10), spacing is `--block-gap` /
  `--attach-gap`. A literal px is a question waiting to be asked; a fifth size gets a token and a
  comment saying why.
- Popups copy the conversations-list idiom WHOLE: FIXED width + hover-only action gutter. Never
  reserve space for hover affordances (rejected 2026-08-09); never take half the idiom.
- Any cap or FORMAT shared by live renderer and replay parser lives once in `RenderLimits.kt`
  (spliced as `window.LIMITS`, pinned by `RenderLimitsTest`) — never a second copy in JS.
- Live and replay draw through the SAME block builders (`ioRow/ioBox/toolLine/errorBlock/
  thinkBlock/planCardHtml/writeDiffHtml/askTabsHtml/…`). Keep it that way.
- Plugin Verifier target: 0 warnings on 242→262. Blocking reads via `readLocked {}` (Threads.kt);
  diagnostics via `DocumentMarkupModel` + `HighlightInfo.fromRangeHighlighter`;
  `FileSaverDescriptor` via reflection (gotchas § IDE platform).

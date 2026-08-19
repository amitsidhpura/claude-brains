# Conventions

## Workflow
- **Commit only when asked.** No commits or pushes until the user says so; batch work into one
  meaningful commit. (Migrated from auto-memory 2026-08-07.)
- **A release starts only when the user asks for one.** `docs/release.md` calls steps 1-5 "local
  prep, fine to do proactively" — that licenses the mechanics, not the decision. On 2026-08-16 a
  request to "go ahead with the things you left" (two fixtures + a changelog line) was turned into
  a version bump, changeNotesHtml, an `updatePlugins.xml` edit, a zip and a verifier run; the user
  had never said they were ready to ship. Write the notes if asked for notes; leave the version and
  the feed alone. Note the specific hazard: `updatePlugins.xml` is served live off `main`, so a
  bumped feed without a published asset offers every custom-repo user an update that 404s.
- **Measure before believing.** Any claim about what the CLI sends gets checked against real
  transcripts BY KEY (not substring) or a live stream-json run BEFORE touching the renderer.
  Two of four client-parity "P0s" described bugs that didn't exist; the real one was
  mis-described — every time because the premise was never measured first.
- **Do not fix what you cannot reproduce** (user, 2026-08-12, after rejecting two plans built on a
  plausible story). A mechanism read out of the code or the CLI binary is a HYPOTHESIS, however
  well evidenced: it names something that CAN happen, not what DID. Reproduce first, then fix —
  and say plainly which parts are proven and which are inferred. Two things this caught: six
  different code paths produced the reported symptom and none had been ruled out, and the
  reproduction disproved the stated mechanism (see gotchas: transcript vs live wire). If it will
  not reproduce, the right outcome may be no change at all, just diagnostics for next time.
- **A third-party report describes a SYMPTOM, not a diagnosis, and often not even accurately.**
  "Submit button not disabled" was literally a non-bug (the button is a Send/Stop toggle and is
  never disabled) while pointing at two real defects. Ask what was on screen at the moment of the
  problem before choosing what to fix.
- Enable slash commands one at a time, each verified in `runIde`, then ticked in
  `docs/slash-commands.md`. The menu is an allowlist — unconfirmed commands stay hidden.
- **Take a user's exact wording as data, and check the world before the code.** "On hover it made
  dark bg and on going out it became lighter" was not loose description — the two states had two
  different causes, and reading it literally is what decomposed the bug. Likewise "the chip says 2
  tasks but there are none" turned out to be the chip telling the truth: **check the process tree /
  the actual state first**, because the answer decides which bug you are even looking at. Twice on
  2026-08-15 the reported premise was wrong in a way that mattered, in opposite directions.
- **"The CLI accepted it" is not "the panel rendered it."** A headless smoke run proves only the
  former, and on 2026-08-15 it marked 16 commands verified while `/context` rendered nothing at
  all. Anything user-facing gets driven through the LIVE panel before it is called verified, and
  the doc says which of the two claims a checkmark stands for.
- **When output is missing, tape the wire before blaming either side.** DOM evidence alone cannot
  separate "the CLI sent nothing" from "the panel dropped it", and a throw inside `onClaudeEvent`
  is swallowed by JCEF with no trace at all. Wrapping the handler is a few lines and turns a shrug
  into a diagnosis (it is what found the `/security-review` drop).
- Test fixtures must state their `provenance`: a shape copied from our own handler proves
  self-consistency, NOT that the CLI emits it. New suites need a negative control (feed a wrong
  expectation, check it FAILS) — an all-green first run is what a vacuous suite also looks like.

## Scope vocabulary (from the Philosophy)
- Release/status prose splits **"By design"** (settings page, non-terminal login — the
  terminal's half, never build them, never list as gaps) from **"Not there yet"** (deferred:
  tabs, auto-include selection, voice). The two lists must not blur. Declined items
  (cost/usage display, 2026-08-06) are decisions, not queue positions.
- Feature bar: *is it reached many times an hour while writing code?* Yes → panel. No → terminal.

## Working style (reinforced across the 2026-08-11/12 session)
- **UI changes go to `design/mockup.html` + `chat.css` FIRST**, shown as a render, and only wired
  into `chat.html` once the look is agreed. The user iterates visually and will hand back DevTools
  screenshots with the exact values they want — apply those verbatim rather than re-deciding.
- When a visual choice is genuinely open (size, thickness, padding), **render the candidates side
  by side** and let the user pick; do not iterate one guess at a time.
- **Ship the safest candidate FIRST, then build the probe against it.** A probe is only honest if its
  columns drive a rule that is genuinely live — otherwise it compares copies of one. Landing the
  boring option (one already used elsewhere in the panel) means nothing novel ships ahead of the
  pick, each column varies one token, and the pick is a one-line edit. Used for `--attach-gap`, then
  `--pulse-name` (2026-08-13).
- **Sequence a fix so its negative control is free.** Land the half that creates the state without
  the half that resolves it, run the new fixture, and the failures ARE the bug demonstrated — then
  land the rest and watch them go green. Costs nothing and proves which assertions discriminate.
  Do NOT run the control with neither half: every "must be 0" assertion then passes for the wrong
  reason, which is exactly what a vacuous suite looks like.
- Every new test suite gets its negative control RUN, not just written: assert a wrong value, or
  run the fixture against the build that actually LACKS the fix — `git checkout <fix-commit>~1 --
  <file>`, NOT `HEAD:`/"current minus my commits", which still contains anything fixed in an
  earlier session (that mistake made fixture 49 read as vacuous, 2026-08-17). Confirm it fails.
- **Control builds of the webview restore the whole directory, not one file.** Since the
  2026-08-19 split, "the webview" is `chat.html` (markup) + `webview/js/*.js` (spliced back into
  one script by `WebviewAssets`, manifest-checked by `RenderLimitsTest`) + `chat.css`. A control
  via stash/checkout must target `plugin/src/main/resources/webview/` — a single-file restore
  builds a half-old page (or, pre/post-split across the boundary, an empty script). For fixes
  older than the split, check out the pre-split single-file chat.html and the OLD ChatPanel
  together, or prefer asserting a wrong value instead.
- **When two defects can mask each other, a fixture that replays them in sequence pins only the
  first.** Fixture 44 went green against the PRE-FIX build on its second defect, because the first
  one left `busy` already true and the assertion read as satisfied. Reset the state explicitly
  between the halves so each step starts from a known baseline — and run the whole fixture against
  the pre-fix build to find out which assertions actually discriminate, not just that it passes.
- **A check that skips its own assertion is indistinguishable from a passing one.** A 2026-08-12
  audit reported "no failures" while never running its main rule: it read `overflow-x` after
  restoring the fold class, so folded elements looked "not scrollable" and fell out of the branch.
  When an audit filters what it inspects, print WHAT IT ACTUALLY CHECKED and confirm the list is
  the one you meant — an empty failure list proves nothing on its own.

## Docs
- **A doc outlives the decision that created it.** `docs/release.md` was still titled "Path B —
  no Marketplace" and the README still said "distributed rather than the JetBrains Marketplace"
  two weeks after the plugin was listed there; the README's Docs list linked a `CLAUDE.md` deleted
  on 2026-08-07. Audit the PREMISE of a doc, not just its details, whenever its subject changes —
  and check that every relative link still resolves.
- **Never copy ids out of a numbered register into a summary — re-derive them.** `state.md`'s 🟥
  list had cited 8.5 / 8.9 / 8.13 for rewind-fork, side-question and reload-log-replay when
  `docs/feature-checklist.md` said 8.7 / 8.11 / 8.14, and counted eight [DECIDE] rows when there
  were nine (found 2026-08-17 seventh, at load). The register is the source of truth; a paraphrase
  written from memory drifts silently and then gets copied forward every save.
- Release notes and status prose must keep **By design / Declined / Deferred** apart (glossary.md).
  The README had usage/token display under "deferred but wanted" when it was declined outright on
  2026-08-06 — a reader takes that as a promise.

## Code & assets
- **Never bundle or redistribute** Anthropic's extension.js / webview / claude.exe; `vscode/`
  stays out of git. Personal use only.
- Styles live ONLY in `webview/chat.css`; editing chat.html markup? Mirror it in
  `design/mockup.html` too. No new hardcoded colours — add a token to `:root` and use
  `var(--x)` (for tints prefer `color-mix()` over a companion `-rgb` token).
- Popups/dropdowns copy the conversations-list idiom WHOLE: a FIXED width plus a hover-only
  action gutter. Never reserve space for hover affordances (rejected 2026-08-09) — and never
  take half the idiom, since the hover gutter only stays jiggle-free because the width is fixed.
- Any cap or output FORMAT produced by both the live renderer and the replay parser is stated
  once in `RenderLimits.kt` (spliced as `window.LIMITS`) and pinned by `RenderLimitsTest` —
  never a second copy in JS.
- Live and replay draw through the SAME shared block builders in chat.html
  (`ioRow/ioBox/toolLine/errorBlock/thinkBlock/planCardHtml/writeDiffHtml/askTabsHtml/…`)
  so they cannot drift. Keep it that way.
- Plugin Verifier target: 0 warnings on 242→262. Blocking reads via `readLocked {}`
  (Threads.kt); diagnostics via `DocumentMarkupModel` + `HighlightInfo.fromRangeHighlighter`;
  `FileSaverDescriptor` via reflection (see gotchas.md for why).

# Decisions

Format: `## YYYY-MM-DD — <decision>`, newest first, with *why* and *alternatives rejected*.
Entries older than ~2 weeks are compressed into the **Digest** at the bottom — outcome, why, and the
key rejection, one entry each. Never delete; mark superseded.

## 2026-09-05 — MCP notice split by severity: needs-auth is a notice, failed is an error
- Why: a friend's fresh install read the combined red line as "the plugin is broken" — an
  unauthenticated claude.ai connector is an expected fresh-machine state with a known fix, not an
  incident. One `statusLine` per fault now: `failed` keeps `status err`, `needs-auth` the muted
  default dress; MCP_BAD table and the set-keyed dedupe untouched (fixture 72).
- Alternatives rejected: keeping one combined red line (the complaint itself); dropping the
  notice (13a exists because silently-missing tools are worse).

## 2026-09-05 — old-CLI `--permission-mode` rejection: deferred, and the direction is a plain error
- A pre-2.1.220 CLI spells the ask-mode `default` and rejects our `manual` → process exit 1,
  cryptic first impression. User REJECTED the self-healing design (parse the failure stderr's
  allowed-choices list, translate manual↔default, retry once, persist vocab) as too much
  machinery — when picked up, show a clear "Claude Brains needs claude 2.1.220+ (tested 2.1.25x),
  run `claude update`" message on the matching stderr instead. Parked in backlog § Immediate;
  do not re-propose the retry design.

## 2026-09-04 — `<synthetic>` frames drain by the RESULT's `is_error`, not by tag or subtype
- Why: the CLI now uses `model:'<synthetic>'` for BOTH API-error echoes and local built-ins'
  output (`/context`, `/list-agents` — drift arriving between 2.1.234 and 2.1.251), and a real
  API echo carries `subtype:'success'` WITH `is_error:true`, so is_error is the only signal that
  separates them. Success → the stash renders as prose; error → the red echo with exact-text
  dedupe, unchanged (fixtures 56 + 70).
- Alternative rejected: classifying at stash time in the whole-message branch — the frame alone
  cannot say which kind it is; only its result can.

## 2026-09-04 — webview CSS split into 10 manifest files (`webview/css/`, `CSS_FILES`)
- Why: 1295-line monolith; the 2026-08-19 JS split had already proven the concat-splice seam, the
  banner-per-file DevTools mapping, and the manifest-vs-directory test. User asked for "same as js".
- Shape: cut ONLY at existing top-level comment boundaries, order preserved — concatenation order
  IS cascade order (the file documents real specificity fights), verified byte-identical to the
  old chat.css modulo the top banner. `WebviewAssets.CSS_FILES` is the only copy of the order;
  the mockup mirrors it as `<link>` tags, pinned to the manifest by `RenderLimitsTest`.
- Alternatives rejected: regrouping rules by component while splitting (cascade risk for zero
  benefit — a rule moved past another flips ties); keeping chat.css as a generated artifact for
  the mockup (a second copy that drifts; ten `<link>` tags + the pinning test instead).

## 2026-09-01 (third) — files block: the `Review` span alone is the click target
- Why: user request from a live screenshot — the whole-block action meant any stray click on a
  file row opened the review, and the block-wide pointer cursor oversold what was clickable.
- Alternatives rejected: keeping whole-block click with rows opting out (inverted logic for the
  same result); making file rows open their FILE on click (would overload the block with two
  different actions and was not asked for — the abs path already rides the tooltip).

## 2026-09-01 (second) — files-changed block: one row per file, PROJECT-RELATIVE paths; bg popup gets the idiom's fixed width
- Why: the single comma-run wrapped into an unreadable blob (worst on Windows, where a basename
  bug showed full `D:\…` paths). User picked per-row + relative paths from three rendered
  candidates (compact filename line, per-row basenames, per-row relative). Rows draw through the
  SHARED `fillPath()` so tool lines and file rows cannot drift; counts right-aligned.
- Bg-tasks popup: long titles now one-line ellipsised with the full name on the tooltip, and
  `#bgMenu` is FIXED at 330px — the conversations-list idiom's stability comes from its fixed
  width (#histPanel/.card-menu are both fixed); nowrap without it would WIDEN the popup.
- Alternatives rejected: keeping the compact line with just the Windows fix (user preferred
  scannable rows); always-reserved ✕ gutter (standing 2026-08-09 rule: never reserve space for
  hover affordances); a second path-shortener for file rows (fillPath exists, fixture 40 pins it).

## 2026-09-01 — `(note: …)` caveat: bound by every structural property the real emitter has; over the bound, DROP, never truncate
- Why: the end-anchored regex alone misread large output containing a literal `(note:` and ending
  `)` as a caveat — the whole tail rendered as one giant amber `.t-note` (user sighting
  2026-08-30, reproduced on the real wire). Two guards, licensed by measuring every note template
  in the 2.1.252 binary with `strings`: `NOTE_MAX = 400` (all real notes ≤ ~200 chars, collapsed)
  and reject a match at position 0 of the trimmed result (all real notes are APPENDED after other
  text — three join with a leading space, Edit's escape-swap with `\n`). One copy each, in
  `RenderLimits.kt`, spliced as `LIM.noteMax`; JS mirrors the predicate.
- Alternatives rejected: TRUNCATING an over-bound capture (a slice of misread output shown in
  amber is noise dressed as a warning — the output is already in the OUT box, so drop is lossless
  on screen); a single-line-note rule (RenderLimitsTest pins multi-line notes as valid — the
  collapse exists for them); capping the permission-card `reason` (a dedicated field, never
  parsed from output, never observed large).
- Accepted residual, explicitly: a sub-400 parenthetical appended after text at the very end of a
  result is byte-identical to a real caveat and still renders — irreducible without the CLI
  marking notes structurally. Failure mode is one plausible-looking amber line, not a wall.
- If a future CLI ships a genuine note > 400 chars the panel silently drops its display (the
  model still receives it); the `strings` sweep of each CLI re-audit is the tripwire, and the fix
  is bumping `NOTE_MAX`.

## 2026-08-30 (fifth) — First-paint flash, second attempt: visible browser, load on ITS first bounds, CEF paints the dark bg
- Why: 0.12.2's fix made the flash DETERMINISTIC. It hid the JCEF child (`isVisible=false`) until
  `onLoadEnd`, but an invisible `BorderLayout` child gets NO bounds, so CEF kept its default
  ~300×180 surface, the page loaded at that size and was resized only when shown — the squashed
  frame on every open (user's screenshot in the real IDE). The correct mechanism: keep
  `browser.component` visible and laid out, run `loadUi()` on the BROWSER component's own first
  non-empty `componentResized` (the CEF surface tracks that component, so the viewport is the tool
  window's before the page lays out), and `browser.setPageBackgroundColor("#1a1a1a")` so the
  pre-load frame is dark instead of white.
- Simplified at the user's ask ("optimized to remove unnecessary last fix?"): the `JPanel` wrapper,
  the hide/show in `onLoadEnd`, the `java.awt.Color` mirror and three imports are gone;
  `component` is `get() = browser.component` again, `PAGE_BG` is a `const val "#1a1a1a"`. Net
  change vs pre-0.12.2: `loadUi()` moved from the constructor into a one-shot listener + one
  `setPageBackgroundColor` call. Verified by the user in the real IDE from a zip, twice (with and
  without the wrapper).
- Rejected: an opaque wrapper behind the browser (only a dark ground for a HIDDEN child; with the
  child visible, JCEF is heavyweight and covers it anyway).

## 2026-08-30 — Defer the webview load until the tool window has bounds; factory DumbAware
**SUPERSEDED (same day, below): the hidden-child half was the bug; the deferral and DumbAware stand.**
- Why: the first frame on project open was the page laid out at CEF's default ~300×180 surface
  (`loadHTML` ran in the `ChatPanel` constructor, before `addContent`). Loading on the wrapper's
  first non-empty resize and showing the JCEF child only at `onLoadEnd` makes the first visible
  frame the right one; the wrapper's `PAGE_BG` (= `--bg`) covers the gap. `DumbAware` because the
  platform otherwise hides the whole tool window behind an "indexes are built" placeholder — the
  panel never reads indexes.
- Accepted cost: the CLI process starts when the panel is first shown rather than at project open
  (a collapsed tool window loads nothing until clicked).
- Rejected: `JBCefBrowser.createBuilder().setOffScreenRendering/…` background tricks (the flash is
  a LAYOUT-size problem, not a colour one); keeping the constructor-time load and re-layout after
  attach (the wrong-size frame would still paint once).

## 2026-08-30 — Settings-schema staleness: wait for SchemaStore, no code
- Why: the IDE fetches Anthropic's schema from `json.schemastore.org/claude-code-settings.json`
  (13.2, nothing bundled); SchemaStore is hand-synced ~monthly and sat at 2.1.220, so 2.1.251's
  `PreModelSwitch`/`PostModelSwitch` show as a warning in `settings.local.json`. A warning, not an
  error; the hook works.
- Rejected: bundling the extension's `claude-code-settings.schema.json` (redistribution + a plugin
  release per CLI release, reversing 13.2's design); preferring the locally installed VS Code
  extension's copy with SchemaStore fallback ([SM], offered as the recommendation — user chose to wait;
  revisit if the lag becomes a nuisance).

## 2026-08-30 — 9.11 built the day it was found; optimistic switch kept, revert on error
- Why: a `PreModelSwitch` hook can now refuse the chip's `set_model` and the chip would lie. Kept
  the optimistic flip (instant chip, as before) and added the revert on the error answer instead of
  waiting for the response before flipping — no perceived latency in the common case.
- Rejected: pessimistic switch (chip waits for the control_response); a "model change silent"
  confirmation line of our own (the 2026-08-24 marker decision stands; the CLI's own echo still
  draws after the first turn).

## 2026-08-30 — Re-audit run at 2.1.251 on the user's ask, one version after 2.1.250
- Why: the user asked; the "wait a few versions" deferral (2026-08-29) was a plan, not a rule.
  Outcome justified it: a real behaviour change (`set_model` rejection) surfaced.
- Lesson recorded in gotchas § Protocol: a headless probe with no turn is not the panel's usual
  state — measure post-turn too before calling a frame "gone".

## 2026-08-29 — GitHub Copilot Chat audited once, adopted nothing but one probe; not a reference client
- Why: the user used Copilot and found it bloat; the audit (built-in 0.63.0: 9 participants, 38+~20
  tools, 191 contributed + ~330 hidden settings, four ways to run an agent) confirmed growth is
  horizontal — surfaces and experiments, not depth on the loop. Its local Claude Code harness was
  removed between 0.44 and 0.63, so a lean Claude Code panel is a niche it vacated. Copilot now reads
  `.claude/skills` and `.claude/settings.json` hooks — validates "configure in the terminal".
- Kept: **terminal last command/output as attachable context** — the only many-times-an-hour gap
  (only when the USER ran the command; the CLI already sees its own). Probe the JetBrains terminal API
  first (backlog § Next up). Not to be re-proposed from Copilot: worktree-per-session shape, model
  family aliasing, per-phase models, tool-result spill/cache probes, OTel traces, NES.
- Rejected: keeping Copilot extractions under `reference/` (deleted the same session); naming any
  third-party client other than `anthropic-claude-code` in docs or context procedures.

## 2026-08-29 — Third-party reference material lives under `reference/<vendor-product>/`
- Why: `vscode/` was a one-off name for Anthropic's extension; a second extraction needed a home and
  `/reference/` was already gitignored. Now `reference/anthropic-claude-code/`; runbook step 2 and
  the protocol doc's § 8 point there. Nothing else is kept there today.
- Rejected: leaving `vscode/` in place next to `reference/` (two conventions for one thing).

## 2026-08-29 — External links open in the system browser; the webview never navigates or pops up
- Why: the panel is an off-screen JCEF browser. `target="_blank"` asked CEF for a popup window that
  had no surface to draw on (blank PhpStorm windows, user report); a middle-click, which fires no
  popup, loaded the URL in the panel's own main frame. Three guards, each covering what the others
  cannot: JS `click`+`auxclick` delegate → `browse` frame → `BrowserUtil.browse` (honours the IDE's
  Web Browsers setting); `onBeforePopup` for `window.open` and the like; `onBeforeBrowse` cancelling
  any main-frame http(s) navigation. Bare URLs autolink while there.
- Rejected: opening in the webview (no back button, no chrome, kills the panel); a JCEF child
  window (same OSR problem); `setOpenLinksInExternalBrowser` (not in the 2024.2 platform API used).

## 2026-08-29 — Effort selector is a pill slider in the .tgl idiom (CSS only)
- Why: it sat under three `.tgl` switches drawn in a different idiom; the user brought a screenshot
  of a pill track with ticks and a knob. Same DOM/classes, so `setEffortUI` and fixtures 51/55/58
  are untouched; fill picked per level with `:has()`. Every stop is a fixed 12px slot so centres
  never move; knob inset 2px and fill 2px past the knob in every state — the switch's own numbers.
- Rejected: "Effort (High)" bracketed label (bold level kept); a blue track like the screenshot
  (accent token only, no new colour constants); JS-computed fill (CSS can do it).

## 2026-08-29 — Marketplace change notes carry the last THREE versions + a GitHub releases link
- `changeNotesHtml` had grown to 14 versions (0.4.0 → 0.11.1) under the rule "keep what users update
  FROM"; the What's New page showed the whole wall. User chose: last three, then a link to
  `github.com/amitsidhpura/claude-brains/releases` (every tag has full notes). Rule updated in the
  kdoc and release.md 1b: add at the top, drop the oldest, keep the link.

## 2026-08-29 — Undo and branching belong to git, not Claude: 8.7 NO, 14.2/14.4 NO, 14.1/14.3 later
- User's principle: "depend on concrete de facto git for such things rather than Claude". Rewind via
  `rewind_files` needs `CLAUDE_CODE_ENABLE_SDK_FILE_CHECKPOINTING=1` (bloats every transcript), only
  covers turns Claude made, only lives as long as the transcript — while `git diff` / `git checkout --`
  / PhpStorm Local History are universal and already habitual. A panel-side undo would also teach the
  habit of not committing before letting Claude loose. Fork: New button + `/resume` cover it.
- 14.2 host git actions and 14.4 `get_workspace_diff` are thin-client git for a webview without git;
  the IDE's git client and diff viewer are native → ➖ by design.
- 14.1 create worktree + 14.3 git-aware diff → deferred as ONE "worktrees" backlog bundle: they are
  panel-vs-terminal convenience, not Claude-vs-git, but a worktree is a new project window in JetBrains
  so the IDE-side design comes first. With this every checklist section is ✅ (82 ✅ · 46 ➖).
- Rejected: "rewind files → later" (my first suggestion) — dropped to NO on the user's principle.

## 2026-08-29 — `/clear` removed from the panel (7.6); the New button is the panel's /clear
- 2.1.241 gave `/clear` an optional `[name]` hint, so a menu pick INSERTED `/clear ` instead of
  running, and the native branch dropped the typed name. Options were A) pass the name through as
  the new conversation's title (needs a pending-title held in Kotlin until `system/init` gives the
  new session id, plus a fixture), B) pin pick-runs and ignore the name, or C) remove `/clear`.
- User chose C: `#newBtn` already sends the same `{kind:'new'}`, naming already lives in the header
  pencil (7.9), and `/clear` is a terminal command — same treatment as `/model` and `/rename`.
  `CMD_NATIVE` is now `{'btw'}`; typed `/clear`, `/new`, `/reset` get the "isn't available in the
  IDE" refusal. Known loss: no keyboard-only new conversation (the plugin binds no shortcuts).

## 2026-08-29 — 8.14 reloaded-webview log replay: NO (declined, not deferred)
- A webview reload happens only from a DevTools reload or a JCEF renderer crash; none observed in
  the wild since `seedUi()` (2026-08-13) began healing the chrome. The log half would need the
  transcript pushed WITHOUT restarting the CLI and reconciled against mid-turn frames ([LG]).
- User's call: "does not seem very important" — `refresh` / reopening the session already brings
  the conversation back after a crash. Removed from `backlog.md`; re-mark only if a real reload is
  ever seen. Leaves 8.7 as §8's sole open row.

## 2026-08-29 — Destructive hover stays red (roster ✕, history delete); everything else white
- The two hover-revealed DESTRUCTIVE controls (`.bg-x:hover`, `.hist-del:hover`) go `--red`;
  every other hover-revealed control (`.rm`, `.model-del`, `.t-edit`) goes `--fg`. User asked
  whether the red was a one-off, was shown it is a deliberate pair, and kept it. Do not
  re-propose unifying to white.

## 2026-08-29 — 8.11 side question built as a floating panel over the composer, opened by /btw
- Placement: a full-composer-width box anchored above the composer (not a right-side panel like
  history, not inline in the log) — the answer needs prose width, and "outside the log" is the
  whole point. User approved the mockup render before wiring. Bare `/btw` opens, `/btw q` asks;
  the panel has its own single-line input that grows (Ctrl+Enter; the 2-row first cut left dead space above Send — user, 2026-08-29), ✕ clear, close, Escape; a new/resumed
  conversation resets it (its answers were about the thread that went away).
- The roster carries no `/btw` (54 entries, 2.1.251 measured) — the panel supplies the entry
  (`CMD_LOCAL`), as the TUI and VS Code do; `cmdKind('btw')` = native.
- History is threaded by the client (`{question, response}` pairs of ANSWERED rows only); the
  CLI keeps none. Row ids are page-lifetime monotonic so a late answer can never land on a new
  row — fixture 66 therefore reads ids from its bridge tape, never literals.
- Rejected: a slash-command that sends the question as a hidden turn (would enter the transcript
  and burn a turn); rendering answers inline in the log (defeats the purpose).

## 2026-08-29 — §15 closed: 15.5 debugger hand-off later, 15.6 MCP toggles no
- **15.5** `ask_debugger_help` → ➖ later (backlog "Debugger MCP tools" [LG]). The VS Code message
  itself is a no-op ack; the substance is a per-debug-session MCP server exposing stack/variables/
  breakpoints. Buildable on `XDebuggerManager`, but a new tool surface nobody has asked for and
  untestable without an Xdebug sandbox. Rejected: build now.
- **15.6** Chrome/Jupyter MCP enable/disable → ➖ no, by design: MCP configuration is the
  terminal's half (Philosophy). Rejected: later (it is not a queue position, it is a scope rule).

## 2026-08-29 — Goal: every checklist section ✅ by 2026-08-30 EOD; sections carry one mark
- `## N. ✅|⬜ Title` in `docs/feature-checklist.md`; ✅ = every row ✅ or ➖. User rejected a
  richer form (a `— ✅ complete` / `⏳ pending (3 🟥 · 2 ⬜)` suffix + At-a-glance section line):
  "let's not complicate it". Work order: one section at a time, first to last, each row explained
  in plain language, user says build / later / no.
- Closed by decision this way: 1.25 later · 6.4, 6.5 later · 6.7 no · 12.3 no ("not needed") ·
  12.6 later ("not needed for now", mockup first if revived) · 9.7 later + WATCH.

## 2026-08-29 — 1.22 tool_progress declined on measurement, not opinion
- A foreground 12 s Bash under `-p --output-format stream-json` (2.1.251) emitted zero
  `tool_progress` frames; the binary's own SDK adapter drops the heartbeat kind. ➖ with the
  measurement in the row; revisit only if the wire changes.

## 2026-08-29 — 13.2 settings schema: point at SchemaStore, bundle nothing
- `ClaudeSettingsSchemaProviderFactory` maps `.claude/settings.json` + `settings.local.json` to
  `https://json.schemastore.org/claude-code-settings.json` (the `$schema` Anthropic's docs recommend).
  Why: bundling VS Code's `claude-code-settings.schema.json` would breach the never-redistribute
  rule; a hand-written schema (the first plan) would rot against 150+ keys per release. Rejected:
  doing nothing — the IDE's SchemaStore catalog matches only `settings.json`, never the local file.
- Packaging: optional depends on `com.intellij.modules.json` (2024.3+ split; core on 2024.2), the
  JCEF pattern. verifyPlugin Compatible ×7.

## 2026-08-29 — 9.7 Fable overage gate: watch first, build after a real frame
- No `supportedDialogKinds` declared → the consent never reaches the panel and the chip could
  keep the old model name after a fallback. Cannot be triggered on demand. Rejected: building the
  consent card from the binary's schema alone. Instead `system/model_fallback` → `window.__modelFallbackSeen`
  + one console warning (fixture 65); the build starts when one has been captured.

## 2026-08-29 — Error results: `errors[]` text before the subtype token
- Measured `--max-turns 1`: `result{subtype:error_max_turns, is_error:true, terminal_reason:max_turns,
  errors:[…]}` with NO `result` key. `onResult` now shows `errors.join('\n')` when `result` is absent;
  the raw subtype is the last resort. Found while testing 1.24, fixed under it.

## 2026-08-29 — Wrong-value negative controls accepted for fixtures 62–64's JS halves
- The sandbox had been relaunched after the edits, so no free pre-fix build existed and a stash
  control would have cost two restarts. Per conventions (either form is valid) the flipped-
  expectation copies were run (10/10 flipped asserts failed) and each fixture's provenance says
  exactly that. Later additions (63's geometry assert, 64's errors[] assert, 65) got true pre-fix
  controls because the sandbox happened to still serve the old bytes.

## 2026-08-29 — Checklist: 🚫 mark retired; ➖ = "not implemented", the row says why
- User's call after an audit found the two closed marks drifting: 9.6 / 12.4 / 8.12 sat on ➖
  while being decisions, and 6.6 / 8.9 / 15.2 / 5.5 sat on 🚫 while being "do last" deferrals.
  *Why:* one mark, reason in the row — whether it is the terminal's half, declined or deferred is
  prose, and all three can be revisited later. *Rejected:* a third mark for "deferred" (more
  taxonomy to keep straight); ⬜ for deferrals (understates "wanted"). The glossary's By design /
  Declined / Deferred distinction still governs release PROSE, just not the mark.

## 2026-08-29 — 11.6 extensibility status view: declined; 11.5 elicitation: decline ack, form deferred
- **11.6 ➖ declined** (user, after a plain-language walkthrough). *Why:* it is the terminal's half — looked
  at while configuring, not many times an hour; the `system/init` frame's `mcp_servers/agents/
  skills/plugins` are already used for the actionable half (11.2 failure notice, / menu); VS Code
  has no such view; a second copy drifts per CLI release. *Rejected:* "later" as an [SM] popup
  listing `server · status` from the init frame — revivable if ever wanted.
- **11.5**: `ClaudeCli.handleControlRequest` answers `elicitation` with `{action:"decline"}`
  (the old bare `{}` was schema-invalid — enum `accept|decline|cancel`). *Rejected for now:* an
  elicitation form; parked in backlog § Someday behind a stdio MCP probe server, only if a server
  the user uses ever elicits. VS Code passes no `onElicitation` handler.

## 2026-08-29 — The roster ✕ has no confirm step, and never removes a row itself
Kill a background task (11.3) from a hover-✕ on the roster row, copying the conversations-list
gutter idiom WHOLE (fixed popup width, gutter reserved only on hover) but NOT its arm/confirm step
— user's call after the hand-test. *Why*: a killed task costs one re-ask; a deleted conversation is
gone, which is what the confirm there is for. The row dims and stays until the CLI's next
`background_tasks_changed` REPLACES the set. *Rejected*: optimistic removal — `stop_task` answers
success for unknown ids too, so the response proves nothing; only the roster frame does.

## 2026-08-29 — 11.4 sub-agent work outcome is DECLINED on a measurement, not a hunch
An Explore agent whose Bash returned `is_error:true` and which replied "FAILED: …" ended
`task_updated{status:"completed"}` + `task_notification{status:"completed", summary:"FAILED: …"}`
(2.1.250, wire tape). Task status is the task's lifecycle (`completed|failed|stopped`; a panel kill
= `killed`/`stopped`), never the work's verdict; the summary prose is the only signal and colouring
from prose was rejected 2026-08-13. VS Code renders no outcome either. *Rejected*: re-building the
dot on the summary text; keying on the agent's last `is_error` tool result (it is the agent's, not
the task's, and a recovered failure would paint red).

## 2026-08-29 — `ambient` tasks are filtered on the schema's word, with a witness for the first one
2.1.250's roster item schema describes `ambient` as housekeeping "hosts should exclude from
activity indicators". Filtered from the roster AND the suspend count (an ambient non-`local_bash`
task would otherwise park the turn on Stop forever — the 2026-08-12 shell bug from a new source).
No live frame has carried the field, so the first one is stored verbatim as `window.__ambientSeen`
plus one console warning — measure-before-believing satisfied by instrumenting rather than waiting.
*Rejected*: ignoring the field until seen (the failure mode is a stuck panel, not a cosmetic one).

## 2026-08-28 — The card note's gap is `--attach-gap` (8px), not a literal
**Decision:** `.card .t-note { margin: var(--attach-gap) 0 0 2px }`. **Why:** the 6px first shipped
was eyeballed; the user asked how it was derived and it wasn't. The card's own rhythm is the token
(`.card-h` margin-bottom, `.card .blk` margin-top) and the note already sits at `--attach-gap` under
a tool line, so one token keeps the note-to-diff distance identical in both places. Approved on the
real panel 2026-08-28. **Rejected:** keeping 6px (unjustified); 10px to match `.card-b` (that is the
footer's step, not a content gap). The 2px left nudge stays by eye — it aligns ↳ with the box border.

## 2026-08-28 — Tweak-travel sends a WHOLE-FILE edit, and MultiEdit rides it too
**Decision:** an edited permission-diff pane goes back as `updatedInput` in VS Code's own shape —
Edit `{old_string: whole current file, new_string: whole pane}`, Write `content`, MultiEdit
`edits:[that one edit]` (`EditProposals.tweakedInput`). An untouched pane answers with the
ORIGINAL input (byte-identical to pre-3.5). The card redraws its diff from what ran and adds
"edited in the IDE before accepting"; replay derives the same flag by replaying input and
`toolUseResult` onto `originalFile`. **Why:** measured — VS Code 2.1.250's `rf(…,"single")` is a
100 000-line-context diff, i.e. the whole file, and the CLI applied our whole-file answer over
stdio without complaint (probe f63143c3); the whole file is trivially unique for `old_string`,
so no hunk-minimising diff code is needed on either path. **Rejected:** a minimal-hunk diff
(more code, same result, and a non-unique minimal old_string could FAIL the CLI's check);
leaving the pane read-only (the verdict already carried the pane text — it was being thrown
away); sending MultiEdit to the card only, as VS Code does (the schema accepts the whole-file
edit and the card path is unchanged).

## 2026-08-28 — 3.6 multi-file review: analysed, not started — data before UI
**Outcome (same day):** built as decided — probe showed `get_workspace_diff` is git HEAD vs
working tree (user edits included), so baselines come from the autosave PreToolUse hook; the
line is PER-TURN (what the hook sees cleanly), not per-session like VS Code; Review is live-only,
the resumed-session variant is parked in backlog. Hand-verified 2026-08-28.

**Decision:** build order for 3.6 is (1) probe `get_workspace_diff` headlessly and snapshot
per-file baselines in the existing PreToolUse Autosave hook, (2) only then a "Files changed (n)"
summary line opening a `SimpleDiffRequestChain`. **Why:** the UI half is nearly free
(`DiffReview` already opens a `ChainDiffVirtualFile`); what VS Code has and we lack is the
`{old,new}` map, fed there by a `file_updated` MCP notification to its in-process
`claude-vscode` sdkMcpServer (we use an external ws server via `--mcp-config`) plus a checkpoint
store on load. **Rejected for now:** `ChangeListManager` (working tree vs HEAD) as the source —
mixes the user's own edits with Claude's; adopting sdkMcpServers just to receive `file_updated`
(unprobed whether the CLI would notify a ws server at all).

## 2026-08-28 — Closed audit docs are deleted, not archived; their knowledge moves to the reference tier
**Decision:** `docs/verifier-matrix.md`, `docs/renderer-parity.md` and `docs/client-parity.md` deleted
(user, 2026-08-28). Rule going forward: a doc whose every item is closed is history — promote the
measured facts and standing traps (gotchas / `ide-mcp-protocol.md`), leave a `git show <sha>:path`
pointer where it was cited, delete the file. **Why:** the three carried 2,090 lines with zero open
work; a fresh session reading them as live registers would re-audit closed items, and the feature
checklist is the one register. **Rejected:** keeping them as "archives" in-tree (still read, still
drift), and deleting without promotion (the client-parity wire evidence — e.g. `staleRecovered` is
what fires, failed Bash `toolUseResult` is a string — exists nowhere else and cost real probes).

## 2026-08-26 — 0.11.1 is a PATCH, its notes get a "Changed" section, and the Fable caveat ships

The effort-slider relocation shipped as 0.11.1, not 0.12.0: `docs/release.md` reserves the minor
bump for new capability, and moving a control between two menus adds none. The GitHub notes used
a **🧭 Changed** heading in place of the template's ✨ New / 🐛 Fixes, because the release was
neither — filing a relocation under Fixes would have implied something was broken. And the
measured Fable thinking no-op went into ⚠️ Notes as a plain sentence.

*Why:* users read notes to learn what they will SEE; an honest heading and a stated limitation
beat a template fit. *Rejected:* 0.12.0 (offered at the gate, declined by omission); filing under
Fixes; leaving the Fable no-op out (it is real, measured, and users on Fable will hit it).
*Precedent:* the notes template in `docs/release.md` now has a third shape in practice — a
Changed-only release — without the doc being edited for it; edit the doc if it recurs.

## 2026-08-26 — Effort lives in the MODEL menu, and its level shows on NO chip

The slider moved out of `#modeMenu`'s `.popup-f` into `#modelFooter` as its last row, under the
1M / fast / thinking switches; the popup header became "Models". The level is displayed in exactly
one place — the footer's own `Effort <b id="efName">` label.

*Why:* the level applies to the model, so it belongs in the popup that picks one; a chip must label
the popup it opens, or it advertises a control that is not there.

*The suffix took three tries, all rendered before deciding.* Moving it to the model chip produced
`Default (Opus 5) (High)` — two bracket groups, rejected on sight, because the model label already
carries a parenthetical (`Default (Opus 5)`, `Opus (1M)`). Six candidates were then injected as real
`.chip-btn` nodes INSIDE `#inputbar` — so the 18px svg ID rule and the 6px inline-flex gap applied
exactly as on the live chip — and screenshotted over CDP: two-brackets / middot / merged-bracket /
muted-word / icon+level / none. The middot `Default (Opus 5) · High` was picked, built, verified…
and then also rejected: **"better is to hide effort"**. Final state drops the suffix entirely, and
`setModelChip` is byte-identical to its pre-session body.

*Rejected:* keeping `(High)` on the mode chip (points at a popup that no longer holds the control);
the middot (still noise on a chip that is better one fact wide); an icon+level group (busier still).

*Consequence:* `.ef-row` is deliberately NOT `.tgl-row` — it carries a dot slider, not a switch, and
separate class names are what let fixture 55 keep counting exactly three TOGGLE rows while fixture 51
measures the moved one.

## 2026-08-26 — The Thinking switch stays live on Fable, with the no-op documented instead of gated

`set_max_thinking_tokens {max_thinking_tokens: 0}` returns `success` on `claude-fable-5[1m]` and the
next turn STILL streams a thinking block — measured against a same-prompt control run that produced
an identical single block (CLI 2.1.246, headless probe with the panel's flags). The same 0 verifiably
kills thinking on the default/Opus model (9.5's 2026-08-24 check). So the switch is inert on Fable.

*Decision (user, "document only"):* no UI change. Recorded in `docs/feature-checklist.md` 9.5,
gotchas § Protocol (and the since-deleted `docs/manual-test.md` 3.5).

*Why not gate it:* no roster field discriminates Fable — `supportsAdaptiveThinking` is `true` for
sonnet, opus AND fable alike — so gating would need a model-name sniff, a new idiom bought for one
model's quirk. *Rejected:* disabling the switch on Fable; a warning-only tooltip.

## 2026-08-26 — Roster capability flags do NOT gate the effort slider or the Thinking switch

An article argued model-dependent controls would let users "toggle things that silently do nothing",
and the CLI does send `supportsEffort` / `supportedEffortLevels` / `supportsAdaptiveThinking` that we
ignore. Building the gate was proposed and then ABANDONED on evidence: with `haiku` selected — the
one roster entry carrying none of those three flags — `/effort max` is accepted with the CLI's full
confirmation line and the next turn emits a real thinking block (user's own screenshot, 2026-08-26).

*Why:* the flags say what a model menu should OFFER as a per-model sub-control; `/effort` is a
session-global setting ("this session only") that any model accepts. Gating on them would have
DISABLED two controls that visibly work — a regression dressed as a fix. `supportsFastMode` remains
the only flag we gate on, and only because delivery was probed separately.

*Rejected:* rendering the dot count from `supportedEffortLevels` (every non-haiku model returns all
five today; the shorter-list case could not be reproduced — no 4.6 models on this roster).
*Standing and unchanged:* the 1M switch carries no client-side validity logic (2026-08-24).

## 2026-08-25 — Chip/slider command turns show ONE confirmation line, no bubble, live and resumed
*Why*: the CLI writes a command trio (`isMeta` caveat + `<command-name>` wrapper +
stdout/local_command confirmation) to the transcript for BOTH the chip's `set_model` control
request and the slider's `/effort` turn (measured 2026-08-25, CLI 2.1.245) — so resume showed
`/model haiku` / `/effort high` bubbles live never drew, and a wrapper could become a derived
session TITLE. User picked hide-on-resume for /model (AskUserQuestion) and then asked for /effort
to "show like a model change": `cleanInjected` drops exactly {`/model`, `/effort`} wrappers; the
`effortMuted` gate draws the CLI's synthetic-assistant confirmation live (the normal path stashes
synthetic text into `syntheticEcho` and draws nothing). **Supersedes** the 2026-07-30 "/effort
bubble stays on resume as audit trail" acceptance — the audit trail is now the confirmation line
both paths render. Also corrects 2026-08-24's premise that model changes leave no transcript
record: on 2.1.245 they demonstrably do. *Rejected*: showing the bubble live too (chip clicks
would paint command bubbles); replay filtering by promptSource (no discriminating key exists);
a title-only fix (leaves the parity drift).

## 2026-08-25 — The custom row's × overlays the ✓ inside `.pi-check`; no offset arithmetic near #inputbar
*Why*: two rounds of stylesheet-derived offsets were falsified by ID rules (`#inputbar svg` 18px
resizes both glyphs; `#inputbar button {padding:4px}` beat `.model-del{padding:0}` and
flex-squeezed the × svg to 10px). Nesting the button in the check span with `inset` makes the
centers coincide by construction; an ID-scoped `#inputbar .model-del{padding:0}` kills the
squeeze. Corollary pinned in fixture 57: center-equality asserts pass under SYMMETRIC shrink —
size equality must be asserted separately. *Rejected*: `right`/`top` magic numbers (twice).

## 2026-08-24 — 1M / fast / thinking live as switches in the model-menu footer (9.9, 9.4, 9.5)
*Why*: "Sonnet with 1M context" had no surface — the `[1m]` opt-in existed only as a typed raw id.
All three are model-adjacent settings, so they stack in one `.popup-f pf-stack` footer under the
model list, as toggle switches (`.tgl` — the panel's first switch idiom; user picked switches over
checkbox/segmented). *Rejected*: separate composer chips (crowds the bar); VS Code's command-menu
placement (we have no command menu). 9.5 built despite the checklist's "likely redundant beside
effort" take — the user's explicit call.

## 2026-08-24 — The 1M switch carries NO client-side validity logic
*Why*: user decision at plan review ("I don't want any hardcoded logic"). `set_model` never rejects
(measured: `haiku[1m]` → success) so validity can't be asked of the CLI; a hardcoded haiku/fable
table would drift with every roster change. An unsupported combo fails on the NEXT turn with the
API's own error, rendered by the existing error path (measured: "400 The long context beta is not
yet available for this subscription"). *Rejected*: the plan's original disabled-states table
(haiku off+disabled, fable on+disabled, default disabled); erroring at toggle time (no CLI surface
for it). Corollary the user then asked for: the switch reconciles to the REAL window from
`result.modelUsage[].contextWindow` after each model's first turn (`reconcileFromResult`),
cleared per model change — CLI truth over both the tag and our seed.

## 2026-08-24 — Thinking ON = max_thinking_tokens null, not VS Code's 31999
*Why*: the CLI schema defines null as "reset to session default"; VS Code's `(31999,"summarized")`
pins a pre-adaptive budget that would silently cap models whose default differs. OFF = 0.
*Rejected*: byte-for-byte VS Code parity (a two-line change in ClaudeCli if ever wanted).

## 2026-08-24 — Effort/model conversation markers: dropped by the user
**Superseded in part 2026-08-25**: the user themselves asked for effort visibility — /effort now
draws the CLI's confirmation line on both paths (see the 2026-08-25 entry); the marker-style
display and the model half stay dropped.
*Why*: asked originally ("show effort in live and replay"), then withdrawn at plan review after
learning a model-switch marker could never replay (mid-session model changes leave NO transcript
record — measured by key). Do not re-propose unprompted. The muted `/effort` turn stays as is.

## 2026-08-24 — An API error draws ONCE: dedupe by exact text, never by kind
*Why*: the CLI echoes an error twice on the live wire (synthetic assistant message + result
is_error, identical strings) while the transcript keeps only the assistant record — replay was
always single (SessionStore already maps `isApiErrorMessage` → error item); live now stashes
synthetic texts and drops only a text the result's error block literally re-shows. Everything
else — differing text, second synthetic message, non-error result, interrupt — draws its own
error block. *Rejected*: dedupe by kind/flag alone (the user's "could this swallow a message?"
exposed that a differing text WAS silently dropped — fixture 56's second control proved it).

## 2026-08-24 — The context skill stays lean: rules only, and it is shared via the gist
The skill (`.claude/skills/context/SKILL.md`) is general-purpose — the user includes it in other
projects from gist `b2d033439ba4ca5bcd018f4fe5eef773` — so it carries no project-specific numbers,
and after the tiered-load change it was compressed 141 → 96 lines at the user's direction: dedupe
(the CLAUDE.md check stated once, in the policy section), no rationale paragraphs (a rule keeps at
most a one-line why), `decisions.md` prepend not append, gotchas under topic sections not dated
ones, Retention model as two budgets. **Stop point agreed:** compression ends when only normative
text remains — further cuts drop the why-half-clauses that make rules self-enforcing, which is
deferred information loss.
**Rejected:** keeping the measured 41k/29k load figures in the skill (project evidence belongs in
this file, not a shared skill); compressing below ~96 lines (nothing left but rules and the table).

## 2026-08-24 — `/context load` reads a briefing TIER, not the whole folder
The skill's load step now reads `state.md`, `overview.md`, `conventions.md` in full plus the
NEWEST journal entry, and explicitly does NOT read `decisions.md`, `gotchas.md`, `backlog.md`,
`glossary.md` or `runbook.md` — those are reference, consulted by `grep` when the work touches
them. `save` gained the matching obligation: `state.md` must carry forward the few traps and
backlog items that bear on the next steps, one line each, pointing at the file with the detail.
**Why:** a full-folder load measured **~41k tokens, of which ~29k was read and discarded** — the
user reported `/context load` alone filling 7% of the window. `decisions.md` (8.4k), `glossary.md`
and `runbook.md` contributed literally nothing to the briefing, and `gotchas.md` contributed two
lines out of 497. The tier costs ~8k, an 80% cut, with nothing deleted: the reference files are
unchanged and one grep away. Consolidation alone could never have fixed this — the waste was in
eager reading, not in file size.
**Rejected:** cutting the reference files further (the user had already declined fact loss, and
even a 100-line `gotchas.md` would still be read-and-discarded); splitting them into
load/reference directories (same outcome, but moves files and breaks every path already recorded);
summarising them into `state.md` at save time (a second copy that drifts — the thing this whole
memory design exists to avoid).

## 2026-08-24 — A replayed card may not claim a decision the transcript does not hold
Replay gained a THIRD plan-card state: `undecided` (SessionStore emits it when a plan tool_use has no
tool_result), drawn as neutral "◌ Interrupted — no decision recorded" (`.und-t`). The CLI's own
auto-deny text (`RenderLimits.PERMISSION_ABORT_PREFIX`) joins REJECT_MESSAGE as machinery filtered out
of `planFeedback`. `refresh`/`resume` call `ClaudeSessionService.stopForReplay()` before reading.
**Why:** a two-way `denied ? kept : approved` branch treats "no record" as approval. Measured on the
user's own session: a CLI killed with a permission pending flushes its auto-deny within ms, so a replay
read too early rendered "✓ Approved" for a decision never made, and the next reload quoted the CLI's
error as if the user had typed it. Absence of evidence must render as absence, never as consent.
**Rejected:** inferring from surrounding records (the transcript genuinely does not say); suppressing
the card when undecided (it is the record that Claude asked); waiting unconditionally (see below).

## 2026-08-24 — A correctness wait is scoped to the state that needs it
`stopForReplay()` waits (`awaitExit(1_500)`) only when `pendingPermissions.isNotEmpty()`.
**Why:** the wait exists for ONE measured record — a pending permission's auto-deny flush. Made
unconditional it charged every ordinary reload a few hundred ms for nothing, which the user noticed
within a day. The service already tracks the state, so the guard is free.
**Rejected:** dropping the wait (reintroduces the one-reload-late bug); a shorter blanket timeout
(still pays on the common path, still races on the rare one); reading asynchronously and patching the
card afterwards (a second render path for one edge case).

## 2026-08-24 — The sandbox tracks a PATCHED IDE build, not the .0 of its line
`plugin/build.gradle.kts` pins `phpstorm("2024.2.6")`.
**Why:** 2024.2.0's JCEF fabricates key-event storms in OSR on Linux (IJPL-161111, fixed in 2024.2.2+).
Pinning the .0 of a line means developing against every bug that line ever had — it cost three
speculative guards, a full revert, and a day chasing a defect no user could hit. `sinceBuild = "242"`
is unaffected; users were never exposed. The verifier ladder's own floor is 2024.2.6, so the dev
environment now matches the lowest build we have a compatibility guarantee for.
**Rejected:** 2024.3 (bigger jump, re-opens documented sandbox quirks); staying on 2024.2.0 and
guarding in-panel (guarding the dev environment inside shipped code); raising `sinceBuild` past the
buggy builds (locks out users over an intermittent glitch on one surface).

## 2026-08-24 — `verifyPlugin` runs on every release, and the docs say so once
`docs/release.md` step 3b makes it mandatory; the contradicting "not a per-release ritual" language in
gotchas and the Marketplace section is gone.
**Why:** the two docs disagreed, which licensed skipping it on 0.9.0 — that passed, but only by luck
confirmed after the version number was already spent. The Marketplace runs its own verifier on upload,
but that verdict lands too late to gate anything. The judgement "did this diff touch platform API?" is
exactly the one not to trust.
**Rejected:** keeping it discretionary for non-platform changes (the reasoning that failed).

## 2026-08-24 — The confirm-card path stays one-line-ellipsised (user call)
The permission card's header path continues to wrap to a second line AND middle-ellipsise at narrow
widths. A CSS-only plan to let it wrap and show the path whole was written and **rejected by the
user**; do not re-propose unprompted.

## 2026-08-23 — Plan comments live ON the plan card, and approval stays open with comments
Checklist 5.6 (`c0df900`): select text in the plan card's body → anchored comment rows.
**Why in-panel, not an editor tab:** VS Code renders the plan in a markdown preview tab
(`open_markdown_preview` + `plan_comment` webview↔extension internals with NO stdio counterpart); our
panel already renders the plan through `planCardHtml`, so the card IS the preview and the whole build
cost was the selection/comment UI. **Why the full decision surface stays** (the reference collapses to
keep-planning once a comment exists): the user's explicit call. Approve-with-comments rides the
existing `PLAN_NOTES_MARKER`; deny wears the VS Code client's byte-exact message (`PLAN_DENY_PREFIX` +
free text + `PLAN_COMMENTS_HEADER` + `[Re: "anchor"] note`, measured off its transcript), so the model
reads a format it already knows. Format strings live once in `RenderLimits`; rows render through one
shared builder (`planCommentRows`) live and replayed; decided cards keep the rows as the record.
**Rejected:** an IDE markdown-preview surface (second host for one feature; the plan file in
`~/.claude/plans/` + `get_plan` make it possible later); comments forcing keep-planning (reference
behaviour, overruled); inventing our own wire format (byte-compatibility costs nothing).

## 2026-08-21 — Displaced transcript records reorder by ANCHOR, never by a global sort
`DisplacedAnchor` (SessionStore.kt): arm an index+timestamp where a measured lie begins; a record that
follows in file order but provably precedes in time inserts at the anchor; eviction shifts/forgets it.
Two instances: `apiErrAnchor` (retry storms) and `compactAnchor` (the CLI writes a manual compaction's
boundary + summary BEFORE the /compact command records, so replay drew the marker above the bubble
that caused it). Pinned by two order tests; the refactor proved byte-identical via `probe --json` + cmp.
**Why:** file order is the backbone of the single-pass parser and is usually chronology; the CLI breaks
it only in measured places. The anchor makes each correction local and provable.
**Rejected:** a global stable sort by timestamp — records with NO timestamp; one API message persists as
several same-ts records whose only order IS file order; the streaming eviction window would need
whole-file buffering (the exact architecture the truncation rewrite removed); and "timestamps always
outrank file order" is an unmeasured premise, so new anchors must each be earned by a real transcript.
Also rejected: suppressing or reconstructing the live compaction footer (the marker is the compaction's
receipt; both divergences recorded as deliberate — now gotchas.md § Replay, item (d); renderer-parity.md deleted 2026-08-28).

## 2026-08-21 — "Open this path" resolves against DISK; locked readers stay on the snapshot
`Vfs.kt` gains `findVFileOnDisk` (snapshot hit first, then `refreshAndFindFileByPath`), wired into
exactly two callers: `ClaudeSessionService.openFile` and `IdeTools.openFile`.
**Why:** the snapshot is not the disk, so a file written behind the IDE's back reported "File not
found" on a click. Snapshot-first means the refresh is only paid when the answer would otherwise be a
wrong "no", and a miss still means genuinely absent.
**Rejected:** changing `findVFile` itself (a synchronous refresh under `readLocked` deadlocks);
refreshing in `Autosave`/`saveDocument` (a file the VFS never saw has no unsaved document); an async
refresh with a callback (`openFile` returns the Boolean that decides the balloon, so it must be
synchronous); `DiffReview.open` (same latent staleness, but a stale miss only mis-renders the left pane
and the end-of-turn sweep covers it — parked in backlog).

## 2026-08-19 — The webview JS lives in webview/js/, spliced back into one script scope
`chat.html` is markup only; the JS is 14 numbered files under `webview/js/` (prefix = load order),
concatenated in `WebviewAssets.JS_FILES` order into the page's single `<script>` block at `<!--JS-->`.
The manifest is the ONLY copy of the order; `RenderLimitsTest` asserts over the assembled page and pins
manifest ⇄ directory equality.
**Why:** 4542 lines was unreviewable; the concat splice reuses the seam already proven for chat.css and
window.LIMITS (loadHTML has no base URL), keeps ONE shared script scope so semantics are provably
unchanged (assembled page byte-identical, banners aside), and the banners give DevTools a way back to a
source file.
**Rejected:** real ES modules via a CefResourceHandler or file:// base (per-file scope, but adds a
resource-serving layer and deferred-load timing — `window.onClaudeEvent` would need explicit global
wiring before Kotlin's first push); leaving the file whole; splitting the markup too.

## Digest — decisions before 2026-08-18

- **2026-08-17** — a slash alias IS its command: `canonicalCmd()` resolves every roster alias for filter, row, gate and wire, and the turn is sent under the canonical name. Why: the CLI advertises `/review`, `/peers`, `/reset`, `/new`, so refusing one was the plugin contradicting the roster it displays; sending canonical removes any dependence on the CLI's alias expansion. Rejected: display-only aliases (the state that failed the first person to type `/review`); a hand-maintained allowlist (drifts every CLI update).
- **2026-08-17** — autosave rides the SDK hook lane, always on, four tools: ONE host hook on `initialize` (`PreToolUse Edit|Write|MultiEdit|Read → autosave`), answered after saving a dirty document, no toggle. Why: the reference's exact mechanism, and the only pre-tool moment that fires under acceptEdits/auto/saved rules AND for Read (no `can_use_tool` there); the plugin has no settings page by design. Rejected: saving from the permission card (misses pre-approved tools and Read); a Bash matcher (a command names no file); a toggle.
- **2026-08-17** — stale IDE locks are swept on every lock write, dead pid only: `IdeLockFile.write` deletes each `~/.claude/ide/*.lock` whose pid is not running (never its own, never an unreadable one). Why: `delete()` only runs on an orderly dispose, so killed sandboxes left 15 corpses, and the CLI applies the same rule only while enumerating IDEs — which our `--mcp-config` route never triggers. Rejected: matching `ideName`; a port probe; deleting unreadable locks (the CLI's lane).
- **2026-08-17** — the feature checklist is a numbered, colour-tiered register with STABLE `section.row` ids (retire by striking, never renumber), marks ✅/🟥/🟧/⬜/➖/🚫, `[XS|SM|MD|LG]` effort on open rows, meta in the header, measured against BOTH reference clients on one version. Why: the user refers to rows by id and wants importance at a glance with cost before choosing; the old file duplicated state and buried the key. Rejected: a separate open-decisions list (folded into **[DECIDE]**); a priority table (prose rows wrap badly); 🟨 for low. (Marks later simplified to ONE mark ➖ on 2026-08-29; 🚫 retired.)
- **2026-08-17** — `close_tab` closes the ONE review opened under that `tab_name` and replies `TAB_CLOSED` regardless; `closeAllDiffTabs` replies `CLOSED_<n>_DIFF_TABS`. Why: our sweep closed every diff, so with two proposals open closing one resolved both; reference-exact replies keep the CLI's consumer on its written path. Rejected: erroring on an unknown name (reference treats a miss as a benign no-op).
- **2026-08-16** — a menu click asks "does it TAKE an argument", not "does it REQUIRE one": any non-empty `argumentHint` inserts `/name ` and waits. Why: `[init | load | save]` is a menu of sub-modes — a command advertising choices is one you clicked in order to choose (the user picked `/context` to run `/context save` and it fired bare); only `/compact`, `/context`, `/goal` of 16 built-ins change behaviour. Rejected: respelling the skill's hint (makes the roster lie); a click/Shift-click split (a new idiom).
- **2026-08-16** — approve-with-notes rides `updatedInput.plan` under `PLAN_NOTES_MARKER`, not a steered message. Why: the ExitPlanMode tool_result echoes the approved plan, so the model reads the note in the SAME message as the approval, before its first implementation call; also recorded durably in the plan file. Rejected: a `feedback` field on the allow response (schema-whitelisted away, silently); stdin steering (raced the model call cycle in a real run); a queued turn (arrives after the whole turn).
- **2026-08-16** — plan-card mode rows PARK the switch in `pendingPlanMode` until the CLI's post-approval `permissionMode` broadcast. Why: the CLI restores `prePlanMode` when the approved ExitPlanMode executes, always after our immediate request, so an eager switch was overwritten every time. Rejected: immediate bridge (deterministic loss); sending at turn end (too late to cover the implementation).
- **2026-08-16** — the panel's two type-on-a-card fields share one dress (`--warn-field`), and the plan card gained a full-bleed `.plan-sep` hairline removed with the input on decide. Why: one control idiom (the `fillPath` argument applied to fields); colour user-picked over three mockup rounds. Rejected en route: `#1b1b1b` on `--panel`; a fully transparent field.
- **2026-08-16** — the chip aliases the broadcast `default` → Manual before the unknown-mode guard, and `attachment` (`queued_command`) records replay as user bubbles unless a plain user record carries the same text. Why: restoring a manual pre-plan mode broadcasts the CLI's internal `default`, which the guard dropped, stranding the chip on Plan; and measured across transcripts, a MID-TURN steered message persists only as the attachment record (queued-to-next-turn persists as both) — so replay otherwise lost text the model demonstrably acted on, or drew it twice.
- **2026-08-15** — ONE path renderer for every surface: both permission-card headers fill their `<code>` through `fillPath()`, absolute path on `dataset.path` + `title`. Why: the decision surface and the timeline named the same file two different ways, and a second renderer is a second thing to drift. Supersedes the backlog note that card paths were deliberately unclamped. Rejected: a card-only shortener; CSS-only clamping (flex shrinks proportionally, nibbling the parent on short paths).
- **2026-08-15** — `msgStreamed` is a TURN-level fact: set at `message_start`, cleared only at result/sendTurn/clearLogUI. Why: the CLI emits an `assistant` frame per CONTENT BLOCK, so clearing mid-turn let the second re-draw text the deltas had already rendered — every message after the first appeared twice in a `/security-review` run. Rejected: clearing at `message_stop` (tried, reverted the same hour — frames straddle the stop in both directions); a rendered-uuid set (the duplicate carries the drawing frame's uuid).
- **2026-08-15** — a failed local command is surfaced, not swallowed: `onUserEvent` handles STRING `content`, routing `<local-command-stderr>` → error block and `<local-command-stdout>` → answer block, live and on replay. Why: `/security-review` without `origin/HEAD` reports only through such a frame, which the `!Array.isArray(content)` guard dropped — a completed turn with nothing in it. Rejected: extending `cleanInjected`'s drop list (wrong in both directions).
- **2026-08-15** — custom commands are detected by the description SUFFIX (`… (project|user)`), not a disk scan. Why: the entry schema has no type field, but the suffix is a measured wire marker (present on every custom entry, absent from all 107 built-ins across two captures); webview-only, and covers plugin-sourced commands free. Rejected: the PLAN-APPROVED Kotlin disk scan (more moving parts, a rescan per `commands_changed`, blind to unscanned sources) — user approved the pivot mid-plan. Accepted risk: a future respelling hides custom commands (fail-closed).
- **2026-08-14** — the IDE is kept in step with the CLI's writes twice over: `CliFileSync` refreshes the one path when a write tool's `tool_use`/`tool_result` pair completes, then sweeps the project root at every `result` (async refresh; a newly created file walks up to the nearest VFS-known directory, recursively). Why measured, not designed: a real turn answered "create one file and overwrite another" with a SINGLE `Bash` call, so the per-file half the backlog scoped caught nothing — Bash names no file, and the sweep costs about what IntelliJ pays on window focus. Rejected: deriving paths from the Bash command (guesswork); sweeping per Bash call; refreshing at `tool_use` (not written yet); keying off the permission card (pre-approved tools produce none).

Outcome · why · key rejection. Full prose is in git history; these are the parts that still steer work.

**2026-08-13 — Sub-agent and in-flight state.** A running tool's dot is white and breathing and the
colour IS the verdict (`--dot-c` per element, one geometry rule for all four `::before` dots) — green
from the first frame asserted success before anything returned. A sub-agent settles when its TASK ends,
not when its launch ack arrives (`isInternalResult` recognises the ack), and goes red only on a
`failed`/`killed` TASK, never on failed WORK — the work outcome is unknowable across sandbox guards, so
a sub-task status dot was built and removed the same day. *Rejected:* keying on tool NAME (drifts);
colouring from summary prose (a text heuristic over an open-ended sentence).
**2026-08-13 — Three things built and withdrawn**, kept because each looks obviously right until
measured: an outward halo (`.turn-body` containment shaved it into a cut half-rectangle), lifting
`content-visibility` to save it (costs the live turn a real rendering property for decoration), and the
sub-task outcome dot above. Lessons live in gotchas.
**2026-08-13 — One gap for every block that hangs off the line above it.** `--block-gap: 18px` /
`--attach-gap: 8px`; followers use `calc(attach - block)` so the intent is legible and the family cannot
drift again (five had drifted to −6/−2/+2/+2/+2px, putting attached content FARTHER from its tool line
than an unrelated block). 8px user-picked from a probe because `.card-h` already used it. *Rejected:*
putting `.compact-sum` in the shared selector (its parent is an ordinary block — no flex gap to cancel).
**2026-08-13 — An unnamed thread re-reads its name once per turn, at `message_start`.** `pushTitle` had
one live-turn caller (`result`), so a new conversation showed "New conversation" beside a titled row for
its whole first turn — an hour in a real transcript. `message_start` is the earliest frame that can work
(the file does not exist at `system/init`). *Rejected:* an `onSessionId` callback (fires before the file
exists); deriving a provisional title in JS (a second derivation of a one-source value).
**2026-08-13 — The webview is seeded on EVERY load** (`seedUi()` at each `onLoadEnd`, `lastTitle` nulled
first) — everything used to sit behind a `started` guard, so a reload left the DOM at markup defaults
with a live CLI attached. *Rejected:* replaying the conversation into a reloaded page (needs the
transcript pushed without restarting the CLI, reconciled against frames still arriving; no reload has
ever been observed in the wild — parked as 8.14).
**2026-08-12 — One contract for every foldable block:** `8px 10px` padding, and whichever element
carries it ALSO scrolls; foldable AND scrollable ⇒ the scrollbar appears only when expanded (rule 2
falls out of rule 1). *Exception, deliberate:* `.io-row`'s left padding lives on its sticky `.io-k`,
since sticky clamps to the containing block. *Rejected:* `:has()` to reach the row (untestable outside
real JCEF).
**2026-08-12 — Busy is a fact about the STREAM, not about who sent the turn.** `message_start` sets busy
and resets the request-scoped counters, because `setBusy(true)` had one call site inside `sendTurn` — so
a turn the CLI started on its own streamed with the button on Send and Stop was unreachable. Measured: a
notification turn arrives with NO user frame on the wire. `pendingBgTasks` excludes `local_bash`, since
the CLI's own busy set does. *Rejected:* an allow-list filter (an unknown `task_type` must count as
suspending); hooking `system/status` (fires ~3x a turn).
**2026-08-12 — The replay window keeps the NEWEST blocks and says so at its top edge.** The old cap kept
the OLDEST, so a session resumed on the 12th replayed as though it had ended on the 6th. Cap 20,000,
eviction from the front at a `user` boundary, and a `truncated` head block carrying the COUNT. *Rejected:*
a bidirectional paging window (a mid-file range cannot be parsed in isolation — results patch earlier
blocks by id, tasks rebuild from increments, summaries link by parentUuid). Paired rule: a cap that trims
history must not do it silently at EITHER end (`#fade-top` off at `scrollTop <= 1`).
**2026-08-12 — A value that IS the work goes in the IN box, not the description.** `"function"` moved
from `DESC_KEYS` to `IN_KEYS` — measured at 230-2965 chars of multi-line JS, so `DESC_MAX` produced a
mid-token slice. The tooltip carries the post-`DESC_MAX` string deliberately: it reveals what the CLAMP
hid, never what the CAP dropped.
**2026-08-12 — Tool-line paths: project-relative, one line, a CHARACTER budget for the tail.** A budget
needs no layout measurement (the alternative forces a synchronous layout per tool line during a replay
that renders hundreds), and CSS alone cannot express "shrink the parent only once the prefix is gone".
*Rejected:* filename-only tail (loses the disambiguating folder); `direction: rtl` (`unicode-bidi`
cancels it).
**2026-08-12 — The rename editor dismisses on CAPTURE**, the one place the popup idiom does not fit:
every control beside the title `stopPropagation`s, so a bubbling listener never sees those clicks.
Discard rather than commit, since a stray click would append a `custom-title` record with no undo.
*Rejected:* a `blur` dismissal (there is zero blur handling in the webview).
**2026-08-12 — A conversation's title is derived from the WHOLE transcript**, since `custom-title` and
`ai-title` are appended where they happen — a rename on a 10,458-line file sat on the last four lines.
*Rejected:* a tail window (moves the blind spot); caching the name ourselves (a second source of truth).
**2026-08-12 — Rename writes the CLI's own `custom-title` record**, read verbatim from the binary, so a
rename here and a `/rename` in the terminal are one act. Safe on the LIVE session because the CLI opens
O_APPEND per write — unlike delete. *Rejected:* the `rename_session` control subtype (unreachable over
stream-json).
**2026-08-12 — Release notes are enforced by the build, not the checklist** (`buildPlugin` fails without
a `<b>X.Y.Z</b>` entry) and **the Marketplace upload is automated, but only the UPLOAD** — the workflow
re-posts the PUBLISHED asset so the zip users get is the zip that was smoke-tested. *Rejected:* a
configuration-time check (would fail `runIde` mid-feature); building the zip in CI (publishes an
artifact nobody ran).
**2026-08-12 — The context gauge is an SVG arc** with `pathLength="100"` and `currentColor`, drawn on
the composer's Lucide geometry so it measures identically to the glyph beside it. *Rejected:* a
conic-gradient pie (Chromium anti-aliases neither the sweep edge nor the mask).
**2026-08-09 — Deleting the LIVE conversation = leave it first, then delete** (restart on a fresh
conversation, bounded `awaitExit`, then remove the file) — the CLI reopens the transcript per write, so
a live delete truncates rather than removes, and a dying CLI can still flush a resurrecting write.
**2026-08-09 — Edit permissions are dual-surface, first answer wins; editor close is NOT a grant.** The
card and a real editor diff both open; `respondPermission`'s pending map is the arbiter. TAB_CLOSED
deliberately diverges from the bridge flow's accept-as-proposed: a permission must never be granted by a
window being tidied away. Panes are read-only because accept answers with the ORIGINAL input.
**2026-08-09 — openDiff never writes; every review's diff tab closes by a HELD handle.** The CALLER does
the disk write (measured on both halves of the reference), and `FileEditorManager.openFiles` does not
report diff editors, so any find-then-close closes nothing. Verdict UI is a bar UNDER the diff,
card-identical, combined grant only — partial grants stay on the card. *Rejected:* toolbar icons
(unidentifiable), text buttons (no warning-free API 242→262), a top banner.
**2026-08-09 — The model-facing IDE-tool allowlist is upstream policy** (getDiagnostics + executeCode),
byte-identical across three versions. *Rejected:* renaming the bridge server to dodge the prefix filter
— the CLI finds its IDE client BY the literal name `"ide"`.
**2026-08-09 — Wording lives once, codes travel.** Retry reasons and auth errors are chat.html tables;
Kotlin emits the raw CODE and only membership sets live in RenderLimits. An unknown code degrades to
showing itself. *Rejected:* duplicating wording into Kotlin.
**2026-08-09 — Model-facing tool results are suppressed by CONTENT, not tool name** — the same tool's
launch ack is bookkeeping while its completed result is the report, so name-keying cannot express the
split. The `[harness: …]` envelope is stripped at position 0 only, since the CLI escapes line-initial
forgeries.
**2026-08-09 — Live edit diffs render OPTIMISTICALLY from tool input, superseded by the card**, because
the live wire carries no diff data and pre-approved paths never send `can_use_tool`. Cost accepted: the
gutter line must be fetched PRE-apply and degrades to no line numbers.
**2026-08-09 — All keyboard chords removed; the plugin binds NO shortcuts.** All three were dead on this
setup (Ctrl+N swallowed by the IDE, the others never fired inside JCEF), and a shortcut that silently
does nothing is worse than none. Every capability keeps a route. *Rejected:* re-registering as IDE
actions (trades a dead chord for keymap collisions to hunt on every IDE and OS).
**2026-08-08 — Manual-test pass conventions:** tick when the behaviour was OBSERVED, with defects
recorded inline; unforceable events may be verified by CDP fixture injection or direct MCP-over-WS, with
provenance stated in the tick. Accepted, not bugs: `/model <x>` audit records becoming untitled-session
titles; replayed image chips reading "file.jpg <smaller>".
**2026-08-07 — Light theme declined; colour groundwork removed** (the docs duplicated what chat.css
answers, and groundwork for work that won't happen is reload budget spent on nothing).
**2026-08-07 — Project context lives in `.claude/context/`; CLAUDE.md abolished** — one portable
structured location instead of a monolith competing for the whole reload budget. *Rejected:* keeping
CLAUDE.md alongside (two sources that drift).
**2026-08-06 — Declined, not deferred:** cost display, token/usage panel, per-record metadata. A
decision, not a queue position — it keeps the panel on the many-times-an-hour loop.
**2026-08-03 — The mode switcher sends the CLI's own four modes** (`manual`/`acceptEdits`/`plan`/`auto`)
and the bypass machinery is gone: `auto` is safety-checked, while the old "Auto" was
`bypassPermissions` and needed a CLI relaunch.
**2026-07-31 — Renamed to "Claude Brains"; distribution is Path B** (custom repo), *partially superseded
the same day* — the plugin was also submitted to the Marketplace, so both channels ship the same id and
IDEs take the higher version.
**2026-07-30 — The slash menu is an allowlist**, since over stream-json only turn-producing commands
work; `/model` and `/effort` stay hidden because the composer has the chip and the slider. Effort rides
a muted `/effort` turn, and its reappearance on resume is ACCEPTED as an honest audit trail.
**2026-07-30 — Per-turn file rewind removed** — needs a checkpointing env var that bloats every
transcript, plus a git repo and client-supplied uuids. Revival notes in gotchas.
**Founding — "Develop in the IDE. Configure in the Terminal."** The scope rule. A settings page and
non-terminal login are the terminal's half and will NOT be built — "By design" absences, never gaps.
Rebuilding `~/.claude` config in the IDE is a second implementation that can only drift.

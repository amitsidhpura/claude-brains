# Decisions

Format: `## YYYY-MM-DD — <decision>`, newest first, with *why* and *alternatives rejected*.
Entries older than ~2 weeks are compressed into the **Digest** at the bottom — outcome, why, and the
key rejection, one entry each. Never delete; mark superseded.

## 2026-09-13 — The ordinary card's reject note sits ABOVE the buttons, placeholder "… · applies to Reject"
Supersedes the 2026-09-05 inline placement (3.7, VS Code's `rejectMessageInput` beside Reject).
**Why:** the field is 34px and the buttons 26px; as a flex item in `.card-b` (default stretch) it
grew every button on Bash/Write cards while the plan card, whose field is on its own line, kept
26px — two heights for one control family (user's screenshot). Moving the field up makes the
three cards one structure and gives the note the full width. Shrinking only the inline field was
rejected by the user (inconsistent with the plan and ask fields); growing every button (34px
footers on all cards) and `align-items: center` (fixes the stretch, leaves an uneven row) were
the other options. **Wording:** the user's "Tell Claude what to do instead · applies to Reject",
lower-case after the middle dot per the footer-suffix house style — needed because the field no
longer sits beside Reject and a note before Accept is dropped (no wire for it).

## 2026-09-13 — A plan-comment draft is settled by the next selection, never blocks it
While a comment composer is open, a new selection in the plan body commits a typed draft (or
cancels an empty one) and then shows the pill as usual; a bare click leaves the draft alone; a
decision button still commits it (since 2026-08-23). Placeholder states the rule. **Why:** the
old `if (composing) return` made a second selection silently do nothing — the user hit it
repeatedly and a newcomer has no way to learn "press Enter first". Committing is recoverable
(every row has ✕); ignoring is not discoverable. **Rejected:** several open composers at once
(one pending mark per row — the bookkeeping where the 2026-08-23 delete-while-composing bug
lived); a hint/flash explaining the block (leaves the dead end in place); commit-on-blur (fires
on stray clicks, and the decision-button case it would add is already covered); a labelled Add
button (the icon dress was the user's round-4 pick).

## 2026-09-13 — 1.29 Bash edit diff draws as resolved edit cards, no panel toggle for the CLI's gate
One card per changed file through `fillAppliedCard` ("Bash on <path>", the CLI's own hunks via
`patchRows`, "✓ Applied"), under the IN/OUT box, drawn on error results too (the edit happened), live
and replay through one `appendBashDiff`; `MAX_BASH_DIFF_FILES` = 10 with the CLI's `moreFiles` folded
into one ↳ note. **Why:** the 4.4 auto-approved-edit surface already says exactly this ("the record is
that the edit ran"), so no new CSS, no new wording, and the mockup/gallery parity cost is one example.
**The gate stays the CLI's** (auto/bypass by default, `bashEditDiffEnabled: true` elsewhere): a panel
switch would be a second implementation of a settings key — configure in the terminal; the release
notes say where to turn it on. **Rejected:** one combined card for all files (loses the per-file
path header that opens the file); a custom compact block (new CSS + mockup states for one feature);
hiding the diff on `is_error` (it is a record, not a verdict).

## 2026-09-13 — The public changelog is a re-audit LEAD source, never evidence
`reference/claude-code-log/` (clone of `anthropics/claude-code`, cloned 2026-09-05, unused until now) is read at
runbook step 3b before the binary/extension diffs. **Why:** the `case`-label and subtype diffs only catch new
identifiers; behaviour changes that reuse existing labels (chip X replacing Hide, prompt fold button, flat model
list) surface nowhere else, and By-design items (Hooks / Permission-rules / MCP dialogs) need only existence to
earn ➖ — no probe. **Boundary kept:** a note creates a candidate row or probe target; steps 4-7 still measure,
and the row cites the measurement. **Rejected:** keeping the blanket "nothing read from release notes" rule — it
had no recorded war story behind it (the gotchas section it cited never existed) and cost coverage.

## 2026-09-09 — 0.13.1 ships the two renderer fixes alone, as a patch
**Why**: both changes (3+-backtick fences, CommonMark lists) fix behaviour that already existed,
touch only `webview/js/20-markdown.js` + one CSS rule, and the user was seeing the list bug daily
— waiting to bundle more would hold a fix users hit constantly behind work nobody asked for.
Plain semver in `docs/release.md`: fixes with no new capability → patch digit.
**Rejected**: folding in the four known renderer gaps found on the way (indent-only code blocks,
`*` inside inline code, mid-line fence leak, `~~~`) — each is a separate parser change needing
its own fixture and control; they are named in the 0.13.1 notes as known instead.

## 2026-09-09 — Lists parse by CommonMark structure (content indent, loose/tight, `start`), not by consecutive marker lines
**Why**: numbered lists restarted at `1.` "so many times" (user, three screenshots 2026-09-09,
one of them the fix's own plan rendering as seven `1.`s). The old branches took consecutive
marker lines only and never wrote `start`, so a blank line, a wrapped line, a nested bullet or an
indented fence ended the list. The user refused the prompting workaround ("I cannot ask claude
every time to give output without blank line") — the renderer must take what the model writes.
Implemented in `20-markdown.js` as `mdList`: an item owns blank lines and lines indented ≥ its
content indent plus lazy continuation; a blank between items or between an item's blocks makes
the list loose (items keep `<p>`, `.blk li > p`), else tight (bare text); items re-enter
`mdBlocks` (nested lists, tables, fences free); `<ol start>` from the first marker. Fences
indented under an item drop their indent (CommonMark). Fixture 86, control 17/38 failed pre-fix.
**Rejected**: `start` only (fixes the number, still splits the list and leaves wrapped lines as
paragraphs between items); splitting a list on a bullet-character change (CommonMark does; the
model never mixes markers inside one list, and forgiving costs nothing); indented (no-fence)
code blocks and `~~~` fences (backlog — never seen from the model except once under a list item).

## 2026-09-08 — A code fence is a run of 3+ backticks and closes only on a run at least as long
**Why**: a ````markdown fence (the model's way to fence markdown or a ``` block) split at its first
three backticks, the leftover backtick made ` B0 \`` no longer a whole-line placeholder, and the
block (a whole table, 1k tokens) vanished while `B0 \`` printed (user screenshot 2026-09-08).
`(`{3,})…\1`*` in `20-markdown.js`; fixture 85, control 8/15 failed pre-fix; confirmed by key on a
real turn the same day. **Rejected**: anchoring fences to line start (would drop the inline
```x``` form, a separate backlog item); `~~~` fences (never emitted).

## 2026-09-05 — Checklist rows fold: gist on the first line, evidence under "Read more…"; folds hold no nested lists
**Why**: rows had grown to 2,300 chars of evidence; the user wanted "1-2 line description, Read
more collapsed". Shown on §3 first (conventions § Docs), approved, then the file. Only rows with
facts beyond the gist fold (68 of 140); the first line keeps the `**id** mark …` shape so the
At-a-glance regex and every id citation survive. The fold opens with `<!-- --><details>…` and no
blank lines, because a blank line inside a list item makes GitHub render every row as a spaced
paragraph. Folds hold paragraphs only: a nested list inside one made marked (the user's viewer)
close `</details>` inside the last bullet and outdent every later row.
**Rejected**: uniform folds on every row (72 short rows gain nothing); dates/fixtures on the first
line (evidence belongs under the fold); sub-bullets inside folds (the marked defect above).

## 2026-09-05 — 1.26: draw the whole banner family through one status renderer, REPL-only ones included; per-kind glyphs (option C)
**Why**: measured on 2.1.261 — only `vcs_state_changed` and `notification` are on this wire, four
more are engine-emitted but unforceable, six are REPL-only (call sites are TUI transcript
reducers; `stop_hook_summary` has no stream-json translator arm). One branch each against a
public schema costs nothing and the panel does not go blind the day a translator arm lands; the
first frame per subtype is kept in `window.__bannerSeen`. Glyphs: three candidates (bare / one
muted dot / per kind) rendered in the REAL panel side by side; the user picked per-kind, supplied
lucide link-2 for the PR line, kept the branch and bookmark as drawn. The notification's
"· ctrl+o to see" tail is a TUI hint and is stripped (measured text).
**Rejected**: bare lines (read as stray text between a card and the summary — my own first
recommendation, overruled by the render); drawing `turn_duration` (the ✻ summary has the timing);
per-kind icons for the six text-only subtypes (no natural glyph; they wear the open dot).

## 2026-09-05 — 1.28: a withdrawn ask settles the card, sends nothing, and the CLI's auto-deny box stays
**Why**: the frame is real BOTH ways (probed: interrupt over a parked ask → `control_cancel_request`
with the ask's id, before the auto-deny tool_result). Correctness first, as the row said: Kotlin
drops the pending entry before the panel hears of it, so a click racing the frame sends nothing;
the editor diff tab is dismissed like an answered card. VS Code's "Answering your earlier
questions" fold-in of late answers is not replicated. The CLI's own "The user doesn't want to
proceed…" OUT box above the withdrawn card stays: offered its suppression (the 3.7 `cardDenies`
idiom with the CLI's stock text), the user was "happy with the result".
**Rejected**: forwarding the raw frame to the webview only (Kotlin must forget the entry first);
VS Code's late-answer fold-in (no wire for it, and the row is about not lying).

## 2026-09-05 — 1.27: the cut MARKER opens the whole text, live from the page and replay from the transcript
**Why**: VS Code opens on a click anywhere in a body over 250 chars; ours cannot, because the
row's own click is the fold toggle, so the affordance is the marker ("— open in editor"). A live
row still holds its uncut text when it cuts (kept under a page-lifetime key, released on
`__clear`); a replayed row only ever had the capped copy, so replay blocks now carry `toolId` and
Kotlin scans the transcript (`SessionStore.toolText`) — keeping the wire capped, as docs/limits.md
promises. Read-only `LightVirtualFile`, plain text, titled "Bash command" / "<Tool> output".
**Rejected**: shipping full texts in replay frames (defeats the caps); live-only (the marker would
lie on replay); a card's cut command preview opening too (the card is not a box the row owns).

## 2026-09-05 — 3.8 editable command: the grant FOLLOWS the edit, split per part; model confusion accepted
**Why**: the user's calls, each probed before building. (1) A single-rule card keeps Always allow
while edited: the CLI persists a rewritten `ruleContent` verbatim and honours it. (2) A compound
card keeps Always allow + the three destinations and hides only its per-rule rows: a rule for the
whole compound string is persisted but NEVER matches (the CLI checks parts separately), so
`EditProposals.splitCommand` (&&, ||, ;, |, lone & outside quotes/parens) yields one exact rule per
part — the shape the CLI's own suggestions take; a split the CLI would do differently can only
re-ask, never widen. (3) The model seeing its original command beside the edited output is the
CLI's design (the transcript never records the edit); VS Code measured identical; the user: "ok
with model's confusion". Live-only on replay, like 3.5.
**Alternatives rejected**: hiding Always allow on any edit (first cut — safe but threw away a grant
the CLI accepts); a whole-string rule (measured useless); updating the tool line's IN box to the
edited command (offered, not asked for — a live-only cosmetic).

## 2026-09-05 — 3.7 reject-note field: inline after Reject, DENY only, Enter submits
**Why**: VS Code's placement (`rejectMessageInput` beside its reject button) costs no card height,
unlike the plan card's full-width row; the plan card keeps its own layout (5.2). Deny only because
the deny message reaches the model verbatim as the tool_result while an ordinary allow has no wire
for a note (`feedback` dropped, stdin steer arrives late — both probed 2.1.233); a note typed before
Accept is dropped, never quoted as if delivered. Enter rejects with the note: a text-field
convention, not a card shortcut (4.9 stays deferred). Three side defects found by the hand test were
fixed the same day rather than backlogged (user: "let's fix both", then the replay note): the
duplicate error OUT box, replay's "1 file changed" for a rejected edit, and the replayed edit card's
missing note — the plan card and the edit card now replay their notes the same way.
**Alternatives rejected**: field hidden until "Reject with a note" (extra click, and the plan card
shows its field always); note on Accept (undeliverable).

## 2026-09-05 — 2.12 extra content roots as `--add-dir`, read at launch only
**Why**: measured — a Read in an attached root asks on every touch ("Path is outside allowed working
directories"), with `--add-dir` it runs silently. `WorkspaceRoots.extraDirs` = content roots outside
`project.basePath`, nested ones dropped; one flag per root as VS Code's host does for every
workspace folder that is not the cwd. Roots attached mid-session wait for the next New/resume.
**Alternatives rejected**: the runtime `register_repo_root` control (follow-up only if a live attach
ever matters); "always allow" path rules as the workaround (one rule per pattern, saved into
permission settings, for a folder the IDE already calls part of the project).

## 2026-09-05 — 4.9 number-key card answers DEFERRED: no keyboard shortcuts on any card
**Why**: the user's call after the plain-language walk-through ("let's skip this, no keyboard
shortcuts for now"). It extends the 2026-08-16 plan-card rule to every card and sits beside the
2026-08-09 removal of all IDE-level chords (12.4). Nothing technical blocks it — a keydown handler
scoped to the focused card would work in JCEF — the user simply does not want keys answering cards.
**Alternatives rejected**: 1/2/3/Esc on the focused card as VS Code does (XS build).
**Reopen only if** the user asks for keyboard answers.

## 2026-09-04 — 4.8 builds as a SPLIT BUTTON: main half = the CLI's default destination, caret = the other targets
**Why**: the user's call after the plain-language walk-through. The default (the suggestion's own
`destination`, `localSettings` on every measured card) stays one click, so the hot path is unchanged;
the deliberate choices (session-only / project-shared / all projects) sit behind the caret, the same
idiom the compound-command partial grant already uses on the card. VS Code's picker (webview
`vY0`) cycles `Ss=[localSettings,userSettings,projectSettings,session]` (never `cliArg`), remembers
the last pick in localStorage, and on accept stamps the chosen destination on every NON-setMode
suggestion (`setMode` keeps its own). Labels: `fs0` short ("this project (just you)" / "all
projects" / "this project (shared)" / "this session"), `ys0` tooltips (file paths).
**Alternatives rejected**: decline + label-only (my recommendation: the default is right nearly
always and the rest is terminal config) — the user wants parity with one-click default kept;
a cycling link like VS Code (a click that silently changes the target reads as a mode toggle).
**Precondition**: probe which echoed `destination` values 2.1.260 honours BEFORE the UI.

## 2026-09-04 — 4.7 taken as VS Code's rule: honour `defaultMode` on a first run, DISPLAY dontAsk, never offer it
- The row said "a fifth mode in both clients; VS Code's picker lists six incl. bypass". The user's
  screenshot showed four — ours exactly. The extension builds the picker per session
  (`webview/index.js` `c4()`); `dontAsk` is unshifted ONLY while current, bypass only under
  `allowDangerouslySkipPermissions`. So "six" was the maximum, never a session.
- Measured on 2.1.260 before deciding: the flag beats the settings file, and we always passed it
  from `selectedMode()` — which cannot return `dontAsk` — so the panel could never BE in dontAsk
  and a display-only row would have been dead code. The defect underneath was bigger: a user's
  `permissions.defaultMode` was ignored on every launch.
- Taken (user: "So VS Code one is the recommended one right?" → "lets go for it"): **(a)** nothing
  persisted → NO `--permission-mode` flag, and the chip is seeded from the `initialize` response's
  `current_permission_mode` (`ChatPanel.pushInitMeta`); **(b)** a `Don't ask` row hidden unless
  current. Once a mode is picked, 4.5's persistence is unchanged and beats the file.
- Alternatives rejected: ➖ "unreachable by design" (defensible — VS Code offers it no more than we
  do — but it leaves the first-run bug for every mode, not just dontAsk); offering dontAsk as a
  fifth pick (goes beyond both official clients, and Plan already covers "explore, change nothing").
- Consequence to state in the next release notes: a never-picked chip now starts on the CLI's
  default (`auto` on 2.1.260) instead of a hardcoded Manual.

## 2026-09-04 — Full-surface audit: every gap gets built, small ones included; 6.9 and 6.5 first
- The audit (checklist details block "Full-surface audit 2026-09-04") found no missing feature
  AREA and ten small gaps, all filed [DECIDE]. The user's call: "I am planning to finish all even
  if small" — so the [DECIDE] gate is spent for all ten; they are built one at a time on the
  user's pick, each with the fixture-first free-control loop. Terminal's-half verdicts (config
  dialogs, session infrastructure, composer chrome the IDE owns, VS Code's hamburger/surveys)
  are written into that block so the next full audit does not re-judge them.
- 6.9 built as SENT-BUBBLE chips only. Why: a `<textarea>` cannot host a chip (VS Code's
  `inputMentionChip` rides a contenteditable), and the bubble is where the chip pays — the
  prompt is already sent. The chip keeps the typed `@` and the bubble's textContent stays
  byte-identical, so copy/select never lies. Rule: path-shaped token (slash or dotted extension),
  `@` at start or after whitespace, sentence-final punctuation outside. Rejected: a
  contenteditable composer (rewrites 6.1's picker and every caret rule); validating tokens against
  the `files` roster (replay could render before the roster arrives — one deterministic rule).
- 6.5 built from the user's screenshot as a CONTEXT-MENU action, first in both
  `ProjectViewPopupMenu` and `EditorPopupMenu`, no shortcut (12.4 stance; VS Code's Alt+K is the
  unbound action, mappable). Paths project-relative like the picker's own, folders `dir/`,
  outside-project absolute — the CLI reads all three. The list is PARKED in `ChatPanel` until
  `seedUi()` because a cold tool window's page has no `onClaudeEvent` yet and `pushEvent` would
  drop the frame silently. Rejected: inserting only into an already-open panel (the first use is
  exactly the cold one); reading file contents IDE-side (the CLI expands `@path` itself).

## 2026-09-04 — 13.3 per-project persistence DEFERRED: wait for the `update_settings` allowlist to grow
- The user's call ("wait for Anthropic — we already persist our way") after the implementation
  attempt hit a measured wall: the 2.1.260 `update_settings` control allows exactly ONE key —
  the binary's allowlist is `new Set(["outputStyle"])`, string values only, "deletion is not
  supported"; `model` and `permissions` are refused by name ("update_settings keys not
  allowed"). The outputStyle write itself works and file-merges cleanly (probed end-to-end).
- What stays true and makes the revival cheap: the CLI HONORS `model` and
  `permissions.defaultMode` from `.claude/settings.local.json` at spawn (measured 2026-09-04
  with the panel's flags; `--permission-mode` beats the file, `model` needs no flag at all), and
  `ChatPanel` seeds the chips from `session.selectedModel()`/`selectedMode()` — so adoption is a
  precedence change in those two functions plus a write path, whenever the channel opens.
- Alternatives rejected: the plugin writing `.claude/settings.local.json` itself via Kotlin
  read-merge-write ([MD], offered with a recommendation, not taken — the CLI also writes that
  file on every "always allow" grant, so simultaneous writes can drop each other's change);
  dropping the idea entirely (the binary's own description — "the scope host UIs need so their
  writes land exactly where /config's do" — signals the allowlist will grow; backlog watch-item:
  grep the binary for `update_settings keys not allowed` each re-audit).
- Checklist 13.3 re-marked ➖ with the full measured story; `PropertiesComponent` persistence
  (IDE-global) remains the mechanism of record.

## 2026-09-04 — Re-audit run at 2.1.260, nine versions after 2.1.251, on the user's ask
- Findings folded into `docs/feature-checklist.md` (details block "Re-audit 2026-09-04") and
  `docs/slash-commands.md`: VS Code's session sidebar grew (archive/unread/groups/filters;
  `delete_session` removed), CLI vocabulary +`cloud_session_delta` +`update_settings`, roster
  54 → 55 (+`/advisor` +`/reload-plugins` −`/artifact-design`), fable row now Fable 5.1 —
  roster-driven, no panel change needed. `set_model` response, IDE-MCP tool set, tengu gates,
  initialize keys: unchanged. One new row (13.3); §16 stale counts swept.
- Method note that saved the audit: the Marketplace vsix supplies BOTH sides — the new
  extension AND, via `extension/resources/native-binary/claude`, the OLD CLI baseline
  (2.1.251 was no longer under `~/.local/share/claude/versions/`). Runbook step 3 updated.
- Two same-day corrections after deeper measurement (both folded into the audit block): VS Code
  does NOT use `update_settings` (its `persist_session_permission_mode` writes extension
  `globalState`), and the control's generic description oversells a one-key allowlist. Lesson in
  gotchas § Protocol: subtype acceptance says nothing about key coverage.

## 2026-09-04 — CLI backward compat: no version floor, no shims — a muted hint on early death
- The policy, settled with the user: the plugin never branches on CLI version and never declares
  a minimum it hasn't tested ("we might be supporting still below but we have not tested, so
  let's not think of floor"). Instead, a non-zero exit BEFORE the process ever produced a
  parseable stream-json frame (`early:true` on the `__exit` frame, set from `ClaudeCli.sawFrame`)
  renders one muted line under the ERR box: "Your Claude CLI may be out of date — run
  `claude update` in a terminal." A mid-session crash never carries it. Fixture 73.
- The `manual` cutoff is now MEASURED, both sides, on real binaries: v2.1.200 (released
  2026-07-03) introduced `--permission-mode manual` (changelog: accepted alongside `default`) and
  the full panel works against it; v2.1.199 rejects it with the friend's exact error and the
  panel shows the new hint end-to-end. The friend's CLI simply predates 2026-07-03. Old binaries
  via `claude install <version>` (kept side-by-side in `~/.local/share/claude/versions/`).
- Alternatives rejected: a version floor with `claude --version` probing (untested ≠ unsupported,
  and 2.1.200 proved the panel works far below the audited range); stderr pattern-matching to
  gate the hint (early-death detection covers every shape without text matching); vocab
  translation stays rejected (2026-09-05 entry). A one-click "Update Claude Code" button
  (run `claude update` with the resolved binary, auto-restart on success) was designed and
  DEFERRED by the user — backlog § Next up.

## 2026-09-05 — MCP notice split by severity: needs-auth is a notice, failed is an error
- Why: a friend's fresh install read the combined red line as "the plugin is broken" — an
  unauthenticated claude.ai connector is an expected fresh-machine state with a known fix, not an
  incident. One `statusLine` per fault now: `failed` keeps `status err`, `needs-auth` the muted
  default dress; MCP_BAD table and the set-keyed dedupe untouched (fixture 72).
- Alternatives rejected: keeping one combined red line (the complaint itself); dropping the
  notice (13a exists because silently-missing tools are worse).

## 2026-09-05 — old-CLI `--permission-mode` rejection: deferred, and the direction is a plain error
**SUPERSEDED 2026-09-04 (the later session; this entry's date was written a day ahead):** the
hint shipped, and the cutoff is 2.1.200, not 2.1.220 — see "CLI backward compat" above. The
retry/vocab-translation rejection stands.
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

## Digest — decisions 2026-08-23 → 2026-08-30 (compressed 2026-09-13; full text via `git show 8831b12:.claude/context/decisions.md`)
- **2026-08-30 first-paint flash, final** — keep the JCEF child visible, `loadUi()` on the browser component's first non-empty resize, `setPageBackgroundColor("#1a1a1a")`; the 0.12.2 hide-until-load made the squashed frame deterministic (an invisible child has no bounds). Rejected: an opaque wrapper behind a hidden child. (The same-day "defer load + DumbAware" entry is SUPERSEDED in its hidden-child half; deferral and DumbAware stand — cost: the CLI starts on first show.)
- **2026-08-30 settings-schema staleness** — wait for SchemaStore, no code; rejected bundling the extension's schema (redistribution) and preferring the local VS Code copy (offered, user chose to wait).
- **2026-08-30 9.11** — optimistic `set_model` chip flip kept, revert on the error answer; rejected a pessimistic switch and a confirmation line of our own.
- **2026-08-30 re-audit at 2.1.251** on the user's ask one version after 2.1.250 — surfaced a real change (`set_model` rejection); lesson: a no-turn headless probe is not the panel's usual state, measure post-turn too.
- **2026-08-29 GitHub Copilot Chat** audited once, not a reference client; kept only "terminal last command/output as attachable context" as a probe idea; rejected keeping its extraction or naming it in procedures.
- **2026-08-29 `reference/<vendor-product>/`** for third-party material (was `vscode/`).
- **2026-08-29 external links** open in the system browser via three guards (JS click delegate → `BrowserUtil.browse`, `onBeforePopup`, `onBeforeBrowse` cancel); rejected in-webview navigation, a JCEF child window, `setOpenLinksInExternalBrowser` (not in 2024.2).
- **2026-08-29 effort selector** is a pill slider in the `.tgl` idiom, CSS only, 12px stops; rejected a bracketed label, a blue track, JS-computed fill.
- **2026-08-29 Marketplace change notes** carry the last THREE versions + a GitHub releases link (was 14).
- **2026-08-29 undo/branching belong to git** — 8.7 rewind NO, 14.2/14.4 host git NO, 14.1/14.3 worktrees later as one bundle; user's principle "de facto git, not Claude"; rejected "rewind → later".
- **2026-08-29 `/clear` removed** (7.6); the New button is the panel's /clear; `CMD_NATIVE = {btw}`; known loss: no keyboard-only new conversation.
- **2026-08-29 8.14 reloaded-webview replay: NO** (declined) — no real reload seen since `seedUi()`.
- **2026-08-29 destructive hover stays red** (roster ✕, history delete), every other hover control white — a deliberate pair, do not unify.
- **2026-08-29 8.11 side question** as a floating panel above the composer opened by `/btw` (panel-supplied `CMD_LOCAL` entry; client-threaded history; page-lifetime row ids); rejected a hidden turn and inline answers.
- **2026-08-29 §15 closed** — 15.5 debugger hand-off later ([LG] backlog), 15.6 MCP toggles NO (terminal's half).
- **2026-08-29 goal: every section ✅** by 2026-08-30 EOD; sections carry one mark; rejected a richer suffix ("let's not complicate it"). Closed: 1.25 later · 6.4/6.5 later · 6.7 no · 12.3 no · 12.6 later · 9.7 later + watch.
- **2026-08-29 1.22 tool_progress declined on measurement** — zero frames on a 12 s Bash under stream-json (2.1.251).
- **2026-08-29 13.2 settings schema** → SchemaStore URL for both `.claude/settings*.json`, nothing bundled; optional dep on `com.intellij.modules.json`.
- **2026-08-29 9.7 Fable overage gate** — watch first (`__modelFallbackSeen` + warning, fixture 65), build after a real frame.
- **2026-08-29 error results** — `errors[]` text before the subtype token (measured `--max-turns 1`).
- **2026-08-29 wrong-value negative controls** accepted for fixtures 62–64 (no pre-fix build available), provenance says so.
- **2026-08-29 checklist marks** — 🚫 retired; ➖ = not implemented, the row says why (terminal's half / declined / deferred); rejected a third mark.
- **2026-08-29 11.6 extensibility view declined; 11.5 elicitation** answered `{action:"decline"}` (bare `{}` was schema-invalid), form deferred behind a real eliciting server.
- **2026-08-29 roster ✕** — no confirm step, never removes a row itself (the `background_tasks_changed` frame does; `stop_task` succeeds for unknown ids).
- **2026-08-29 11.4 sub-agent outcome DECLINED on measurement** — task status is lifecycle, the summary prose the only signal; VS Code shows none either.
- **2026-08-29 `ambient` tasks filtered** on the schema's word, first live one stored as `window.__ambientSeen`.
- **2026-08-28 card note gap = `--attach-gap`** (8px), not a literal; the 2px left nudge stays by eye.
- **2026-08-28 tweak-travel sends a WHOLE-FILE edit** (`EditProposals.tweakedInput`, MultiEdit rides it); measured: VS Code's own shape and the CLI applies it; rejected a minimal-hunk diff.
- **2026-08-28 3.6 multi-file review** — data before UI: baselines from the autosave PreToolUse hook, per-TURN line, live-only; built the same day; rejected `ChangeListManager` (mixes user edits) and adopting sdkMcpServers for `file_updated`.
- **2026-08-28 closed audit docs are deleted**, their facts promoted to the reference tier, a `git show` pointer left.
- **2026-08-26 0.11.1 is a PATCH** with a "Changed" notes section and the Fable thinking caveat.
- **2026-08-26 effort lives in the MODEL menu**, level shown on NO chip (six suffix candidates rendered in the real bar, all rejected: "better is to hide effort"); `.ef-row` ≠ `.tgl-row` on purpose.
- **2026-08-26 Thinking switch stays live on Fable**, no-op documented, not gated (no discriminating roster field).
- **2026-08-26 roster capability flags do NOT gate** effort/Thinking — haiku (no flags) honours `/effort max` visibly; only `supportsFastMode` gates.
- **2026-08-25 chip/slider command turns show ONE confirmation line**, no bubble, live and resumed (`cleanInjected` drops `/model` + `/effort` wrappers; `effortMuted` draws the CLI's line). Supersedes 2026-07-30 and corrects 2026-08-24's premise.
- **2026-08-25 custom row's ×** overlays the ✓ inside `.pi-check`; ID-scoped padding reset; no offset arithmetic near `#inputbar`.
- **2026-08-24 1M / fast / thinking** as `.tgl` switches in the model-menu footer (9.9, 9.4, 9.5).
- **2026-08-24 the 1M switch has NO client-side validity logic** ("no hardcoded logic"); reconciles to `result.modelUsage[].contextWindow` after the first turn.
- **2026-08-24 Thinking ON = `max_thinking_tokens: null`**, not VS Code's 31999; OFF = 0.
- **2026-08-24 effort/model conversation markers dropped** (superseded in part 2026-08-25: /effort draws the CLI's line).
- **2026-08-24 API error draws ONCE**, dedupe by exact text never by kind (fixture 56's second control).
- **2026-08-24 the context skill stays lean** (rules only, shared via gist `b2d033439ba4ca5bcd018f4fe5eef773`, 96 lines) and **`/context load` reads a briefing TIER** — full-folder load measured ~41k tokens with ~29k discarded; rejected trimming reference files or summarising them into state.
- **2026-08-24 a replayed card may not claim a decision the transcript lacks** — third plan-card state `undecided` ("◌ Interrupted — no decision recorded"); `stopForReplay()` waits only while a permission is pending (a correctness wait scoped to its state).
- **2026-08-24 sandbox tracks a PATCHED IDE build** (2024.2.6; IJPL-161111 on .0); `sinceBuild 242` unchanged.
- **2026-08-24 `verifyPlugin` on every release**, docs say so once (0.9.0 skipped it and got lucky).
- **2026-08-24 confirm-card path stays one-line-ellipsised** — a wrap plan was rejected by the user; do not re-propose.
- **2026-08-23 plan comments live ON the plan card**, approval stays open with comments (user's call vs the reference's forced keep-planning); deny wears the VS Code client's byte-exact message; rows through one shared builder.

## Digest — decisions before 2026-08-22

- **2026-08-21** — displaced transcript records reorder by ANCHOR (`DisplacedAnchor`,
  SessionStore.kt: `apiErrAnchor`, `compactAnchor`), never by a global sort. Why: file order is
  the single-pass parser's backbone and the CLI breaks chronology only in measured places — each
  anchor is local and provable. Rejected: a global timestamp sort (records without timestamps;
  same-ts records whose only order IS file order; would need whole-file buffering). New anchors
  must each be earned by a real transcript.
- **2026-08-21** — "open this path" resolves against DISK (`findVFileOnDisk`: snapshot first,
  then refresh), wired into exactly the two `openFile` callers. Why: the VFS snapshot lags files
  written behind the IDE's back — clicks reported "File not found" on real files. Rejected:
  changing `findVFile` itself (sync refresh under `readLocked` deadlocks); an async callback
  (the caller's Boolean decides the balloon).
- **2026-08-19** — the webview JS lives in `webview/js/` (numbered files, load order = prefix),
  concatenated into ONE script scope at `<!--JS-->`; `WebviewAssets.JS_FILES` is the only copy of
  the order, pinned by `RenderLimitsTest`. Why: 4542 lines was unreviewable; the concat splice
  keeps semantics provably unchanged (assembled page byte-identical). Rejected: real ES modules
  (resource-serving layer + deferred-load timing for `window.onClaudeEvent`) — backlog "someday".

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
